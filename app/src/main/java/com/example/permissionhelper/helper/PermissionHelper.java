package com.example.permissionhelper.helper;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.example.permissionhelper.helper.exception.PermissionNotDefined;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public final class PermissionHelper implements Serializable {

    private static final int INIT_SIZE = 40;
    private static final List<String> APP_PERMISSIONS = PermissionUtil.getAppPermissions();
    public static final int DEFAULT_REQUEST_CODE = 0;

    /**
     * Contains permissions param
     */
    private final List<String> mPermissions;
    /**
     * Contain permissions that was not grant
     */
    private final List<String> mPermissionsRequest;
    /**
     * Contain permissions was granted
     */
    private final List<String> mPermissionsGranted;
    /**
     * Contain permission was deny forever
     */
    private final List<String> mPermissionsDeniedForever;
    private final List<String> mPermissionsRationale;
    /**
     * Contain permission was denied
     */
    private final List<String> mPermissionsDenied;
    private int mRequestCode;
    private boolean mExplain;
    @NonNull
    private WeakReference<FragmentActivity> mActivityWeakReference;

    // Call back
    private RationaleCallback mRationale;
    private ResultCallback mResult;

    // State
    private boolean mIsWaitingRationale;

    // todo: Implement for write setting and draw overlay permissions
  /*     private static SimpleResultCallback sSimpleCallback4WriteSettings;
       private static SimpleResultCallback sSimpleCallback4DrawOverlays;
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void requestWriteSettings(final SimpleResultCallback callback) {
        if (isGrantedWriteSettings()) {
            if (callback != null) callback.onGranted();
            return;
        }
        sSimpleCallback4WriteSettings = callback;
        PermissionActivity.start(Utils.getApp(), PermissionActivity.TYPE_WRITE_SETTINGS);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void startWriteSettingsActivity(final Activity activity, final int requestCode) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + Utils.getApp().getPackageName()));
        if (!isIntentAvailable(intent)) {
            launchAppDetailsSettings();
            return;
        }
        activity.startActivityForResult(intent, requestCode);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void requestDrawOverlays(final SimpleResultCallback callback) {
        if (isGrantedDrawOverlays()) {
            if (callback != null) callback.onGranted();
            return;
        }
        sSimpleCallback4DrawOverlays = callback;
        PermissionActivity.start(Utils.getApp(), PermissionActivity.TYPE_DRAW_OVERLAYS);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void startOverlayPermissionActivity(final Activity activity, final int requestCode) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + Utils.getApp().getPackageName()));
        if (!isIntentAvailable(intent)) {
            launchAppDetailsSettings();
            return;
        }
        activity.startActivityForResult(intent, requestCode);
    }*/

    private PermissionHelper(@NonNull FragmentActivity activity) {
        mPermissions = new ArrayList<>(INIT_SIZE);
        mPermissionsRequest = new ArrayList<>(INIT_SIZE);
        mPermissionsGranted = new ArrayList<>(INIT_SIZE);
        mPermissionsDeniedForever = new ArrayList<>(INIT_SIZE);
        mPermissionsRationale = new ArrayList<>(INIT_SIZE);
        mPermissionsDenied = new ArrayList<>(INIT_SIZE);
        mRequestCode = DEFAULT_REQUEST_CODE;
        mActivityWeakReference = new WeakReference<>(activity);
    }

    //region Getter, setter

    public void setRationale(RationaleCallback rationale) {
        this.mRationale = rationale;
    }

    @Nullable
    public RationaleCallback getRationale() {
        return this.mRationale;
    }

    public void setResult(ResultCallback result) {
        this.mResult = result;
    }

    @Nullable
    public ResultCallback getResult() {
        return this.mResult;
    }

    public boolean isExplain() {
        return mExplain;
    }

    public void setExplain(boolean mExplain) {
        this.mExplain = mExplain;
    }

    //endregion

    public void requestPermission(int requestCode, @NonNull final String permissions) throws PermissionNotDefined {
        requestPermission(requestCode, Collections.singletonList(permissions));
    }

    public void requestPermission(int requestCode, @NonNull final List<String> permissions) throws PermissionNotDefined {
        if (!Utils.isSubList(APP_PERMISSIONS, permissions)) {
            throw new PermissionNotDefined("Some request permissions did not defined in manifest");
        } else {
            resetData();
        }
        mRequestCode = requestCode;
        mPermissions.addAll(permissions);
        filter();
    }

    /**
     * Delete old data and state
     */
    private void resetData() {
        mPermissions.clear();
        mPermissionsRequest.clear();
        mPermissionsGranted.clear();
        mPermissionsDeniedForever.clear();
        mPermissionsRationale.clear();
        mPermissionsDenied.clear();
        mRequestCode = DEFAULT_REQUEST_CODE;
        mIsWaitingRationale = false;
    }

    /**
     * Classify permissions are granted before.
     * If permission was granted, put them in {@code mPermissionsGranted} list.
     * If they not, put them in {@code mPermissionsRequest} list to request later.
     */
    private void filter() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Don't need request
            mPermissionsGranted.addAll(mPermissions);
            callback();
        } else {

            FragmentActivity activity = mActivityWeakReference.get();
            if (activity == null) return;

            for (String permission : mPermissions) {
                if (!PermissionUtil.isPermissionGranted(permission)) {
                    mPermissionsRequest.add(permission);
                    if (mExplain || PermissionUtil.shouldRationale(activity, permission)) {
                        mPermissionsRationale.add(permission);
                    }
                } else {
                    mPermissionsGranted.add(permission);
                }
            }

            checkList();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkList() {
        if (mPermissionsRequest.isEmpty()) {
            // All permissions was granted
            callback();
        } else if (mRationale != null
                && mPermissionsRationale.size() > 0) {
            // Rationale
            mRationale.rationale(mRequestCode, this::continues, mPermissionsRationale);
            mIsWaitingRationale = true;
        } else {
            // Request
            startRequest();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startRequest() {
        FragmentActivity activity = mActivityWeakReference.get();
        if (activity == null) return;

        PermissionFragment.start(PermissionFragment.TYPE_RUNTIME, this, activity);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void cStartRequest() {
        if (mIsWaitingRationale) {
            startRequest();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != mRequestCode) {
            return;
        }

        // Request result
        FragmentActivity activity = mActivityWeakReference.get();
        if (activity == null) return;

        String p;
        for (int i = 0; i < permissions.length; i++) {
            p = permissions[i];
            if (mPermissionsRequest.contains(p)) {
                if (grantResults[i] == PERMISSION_GRANTED) {
                    mPermissionsGranted.add(p);
                } else {
                    // Denied
                    if (PermissionUtil.shouldRationale(activity, p)) {
                        mPermissionsDenied.add(p);
                    } else {
                        mPermissionsDeniedForever.add(p);
                    }
                }
            }
        }
        callback();
    }

    private void callback() {
        if (mResult != null) {
            int grantedSize = mPermissionsGranted.size();
            int deniedSize = mPermissionsDenied.size();
            int deniedForeverSize = mPermissionsDeniedForever.size();
            int size = mPermissions.size();

            if (size == grantedSize) {
                mResult.onAllGranted(mRequestCode, mPermissionsGranted);

            } else if (size == deniedSize + deniedForeverSize) {
                mResult.onAllDenied(mRequestCode, mPermissionsDenied, mPermissionsDeniedForever);

            } else {
                mResult.onDenied(mRequestCode, mPermissionsDenied, mPermissionsGranted, mPermissionsDeniedForever);
            }
        }
        resetData();
    }

    private void cCallback() {
        if (mIsWaitingRationale) {
            callback();
        }
    }

    /**
     * Continues request when rationale call back invoke
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void continues(int requestCode, boolean continues) {
        if (requestCode == mRequestCode) {
            if (continues) {
                cStartRequest();
            } else {
                cCallback();
            }
        }
    }

    public static class Builder {
        private RationaleCallback mRational;
        private ResultCallback mResult;
        private FragmentActivity mActivity;
        private boolean mExplain;

        public Builder(FragmentActivity activity) {
            this.mActivity = activity;
        }

        public Builder rational(RationaleCallback rational) {
            this.mRational = rational;
            return this;
        }

        public Builder result(ResultCallback result) {
            this.mResult = result;
            return this;
        }

        public Builder explain() {
            this.mExplain = true;
            return this;
        }

        public PermissionHelper build() {
            PermissionHelper instance = new PermissionHelper(mActivity);
            instance.setRationale(mRational);
            instance.setResult(mResult);
            instance.setExplain(mExplain);
            return instance;
        }
    }

// Inner class ---------------------------------------------------------------------------------

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static class PermissionFragment extends Fragment implements LifecycleObserver {

        public static final String TAG = PermissionFragment.class.getSimpleName();
        public static final String TYPE = "TYPE";
        public static final String HELPER = "HELPER";
        private static final int TYPE_RUNTIME = 0x01;
        /*public static final int TYPE_WRITE_SETTINGS = 0x02;
               public static final int TYPE_DRAW_OVERLAYS = 0x03;*/

        private PermissionHelper mPermissionHelper;
        private int mType;
        private FragmentActivity mActivity;
        private boolean mNeedRemove = false;

        public static void start(int type, @NonNull PermissionHelper helper, @NonNull FragmentActivity activity) {
            if (Utils.isGoodTimeTrans(activity)) {
                PermissionFragment fragment = new PermissionFragment();
                Bundle bundle = new Bundle();
                bundle.putInt(TYPE, type);
                bundle.putSerializable(HELPER, helper);
                fragment.setArguments(bundle);
                activity.getSupportFragmentManager().beginTransaction().add(fragment, TAG).commit();
            }
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            mActivity = (FragmentActivity) context;
            mActivity.getLifecycle().addObserver(this);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            if (savedInstanceState != null) {
                // Don't do request when this instance is restore
                if (Utils.isGoodTimeTrans(mActivity)) {
                    removeMySelf();
                } else {
                    mNeedRemove = true;
                }
                return;
            }

            Bundle bundle = getArguments();
            if (bundle != null) {
                mType = bundle.getInt(TYPE);
                mPermissionHelper = (PermissionHelper) bundle.getSerializable(HELPER);
            }

            // We use this flag to prevent user touch to activity when this fragment request permissions
            //todo: Watch this. This may lead activity to not touchable
            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            super.onCreate(null);

            if (mType == TYPE_RUNTIME) {
                requestPermissions(mPermissionHelper.mPermissionsRequest.toArray(new String[0]), mPermissionHelper.mRequestCode);
            }
            /*else if (byteExtra == TYPE_WRITE_SETTINGS) {
                super.onCreate(savedInstanceState);
                startWriteSettingsActivity(this, TYPE_WRITE_SETTINGS);
            } else if (byteExtra == TYPE_DRAW_OVERLAYS) {
                super.onCreate(savedInstanceState);
                startOverlayPermissionActivity(this, TYPE_DRAW_OVERLAYS);
            }*/
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                               @NonNull int[] grantResults) {
            mPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (Utils.isGoodTimeTrans(mActivity)) {
                removeMySelf();
            } else {
                mNeedRemove = true;
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            //todo: Watch this. This may lead activity to not touchable
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        public void onActivityResumed() {
            if (mNeedRemove) {
                removeMySelf();
                mNeedRemove = false;
            }
        }

        private void removeMySelf() {
            mActivity.getSupportFragmentManager().beginTransaction().remove(this).commit();
        }
        
        /*@Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == TYPE_WRITE_SETTINGS) {
                if (sSimpleCallback4WriteSettings == null) return;
                if (isGrantedWriteSettings()) {
                    sSimpleCallback4WriteSettings.onGranted();
                } else {
                    sSimpleCallback4WriteSettings.onDenied();
                }
                sSimpleCallback4WriteSettings = null;
            } else if (requestCode == TYPE_DRAW_OVERLAYS) {
                if (sSimpleCallback4DrawOverlays == null) return;
                Utils.runOnUiThreadDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isGrantedDrawOverlays()) {
                            sSimpleCallback4DrawOverlays.onGranted();
                        } else {
                            sSimpleCallback4DrawOverlays.onDenied();
                        }
                        sSimpleCallback4DrawOverlays = null;
                    }
                }, 100);
            }
            finish();
        }*/
    }

// Call back -----------------------------------------------------------------------------------

    public interface RationaleCallback {
        void rationale(int requestCode, @NonNull PermissionPredicate predicate, @NonNull List<String> rationale);
    }

    @FunctionalInterface
    public interface PermissionPredicate {
        void continues(int requestCode, boolean continues);
    }

    public interface SimpleResultCallback extends ResultCallback {

        @Override
        default void onAllGranted(int requestCode, @NonNull List<String> granted) {
            onGranted(requestCode);
        }

        @Override
        default void onAllDenied(int requestCode, @NonNull List<String> denied, @NonNull List<String> deniedForever) {
            onDenied(requestCode);
        }

        @Override
        default void onDenied(int requestCode, @NonNull List<String> denied, @NonNull List<String> granted,
                              @NonNull List<String> deniedForever) {
            onDenied(requestCode);
        }

        /**
         * All permission request was granted
         *
         * @param requestCode Request code
         */
        void onGranted(int requestCode);

        /**
         * At least 1 permission was denied
         *
         * @param requestCode Request code
         */
        void onDenied(int requestCode);
    }

    public interface ResultCallback {
        void onAllGranted(int requestCode, @NonNull List<String> granted);

        void onAllDenied(int requestCode, @NonNull List<String> denied, @NonNull List<String> deniedForever);

        void onDenied(int requestCode, @NonNull List<String> denied, @NonNull List<String> granted,
                      @NonNull List<String> deniedForever);
    }
}
