package com.example.permissionhelper.helper;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.IntDef;
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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public final class PermissionHelper implements Serializable {

    @IntDef({TYPE_WRITE_SETTINGS, TYPE_DRAW_OVERLAYS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SpecialPermissions {
    }

    @IntDef({TYPE_RUNTIME, TYPE_WRITE_SETTINGS, TYPE_DRAW_OVERLAYS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }

    public static final int DEFAULT_REQUEST_CODE = 0;
    public static final int TYPE_RUNTIME = 0x01;
    public static final int TYPE_WRITE_SETTINGS = 0x02;
    public static final int TYPE_DRAW_OVERLAYS = 0x03;

    private static final int INIT_SIZE = 40;
    private static final List<String> APP_PERMISSIONS = PermissionUtil.getAppPermissions();

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
    private @SpecialPermissions
    int mSpecialType;
    @NonNull
    private WeakReference<FragmentActivity> mActivityWeakReference;

    // Call back
    private RationaleCallback mRationale;
    private BaseResultCallBack mResult;

    // State
    private boolean mIsWaitingRationale;

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

    public void setResult(BaseResultCallBack result) {
        this.mResult = result;
    }

    @Nullable
    public BaseResultCallBack getResult() {
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
        filterRuntime();
    }

    public void requestSpecialPermission(int requestCode, @SpecialPermissions int type) throws PermissionNotDefined {
        String p = getSpecialPermission(type);
        if (p == null
                || !APP_PERMISSIONS.contains(p)) {
            throw new PermissionNotDefined("Some request permissions did not defined in manifest");
        } else {
            resetData();
        }

        mRequestCode = requestCode;
        mSpecialType = type;
        filterSpecial();
    }

    @Nullable
    private String getSpecialPermission(@SpecialPermissions int type) {
        switch (type) {
            case TYPE_WRITE_SETTINGS:
                return Manifest.permission.WRITE_SETTINGS;
            case TYPE_DRAW_OVERLAYS:
                return Manifest.permission.SYSTEM_ALERT_WINDOW;
            default:
                return null;
        }
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
        mSpecialType = -1;
    }

    /**
     * Classify permissions are granted before.
     * If permission was granted, put them in {@code mPermissionsGranted} list.
     * If they not, put them in {@code mPermissionsRequest} list to request later.
     */
    private void filterRuntime() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Don't need request
            mPermissionsGranted.addAll(mPermissions);
            callback(TYPE_RUNTIME);
        } else {
            // Filter
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
            callback(TYPE_RUNTIME);
        } else if (mRationale != null
                && mPermissionsRationale.size() > 0) {
            // Rationale
            mRationale.rationale(mRequestCode, this::continues, mPermissionsRationale);
            mIsWaitingRationale = true;
        } else {
            // Request
            startRequest(TYPE_RUNTIME);
        }
    }

    private void filterSpecial() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Don't need request
            callback(mSpecialType);
        } else {
            boolean isGranted = false;
            switch (mSpecialType) {
                case TYPE_WRITE_SETTINGS:
                    isGranted = PermissionUtil.isGrantedWriteSettings();
                    break;
                case TYPE_DRAW_OVERLAYS:
                    isGranted = PermissionUtil.isGrantedDrawOverlays();
                    break;
            }

            String p = getSpecialPermission(mSpecialType);
            if (isGranted) callback(mSpecialType);
            else if (p != null && mExplain && mRationale != null) {
                mRationale.rationale(mRequestCode, this::continues, Collections.singletonList(p));
                mIsWaitingRationale = true;
            } else {
                // Need Request
                startRequest(mSpecialType);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startRequest(@Type int type) {
        FragmentActivity activity = mActivityWeakReference.get();
        if (activity == null) return;
        PermissionFragment.start(type, this, activity);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void cStartRequest() {
        if (mIsWaitingRationale) {
            if (mSpecialType == -1) startRequest(TYPE_RUNTIME);
            else startRequest(mSpecialType);
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
        callback(TYPE_RUNTIME);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void onSpecialPermissions(int requestCode) {
        if (requestCode != mRequestCode) return;
        callback(mSpecialType);
    }

    private void callback(@Type int type) {
        if (mResult != null) {
            boolean isGranted;
            switch (type) {
                case TYPE_RUNTIME:
                    mResult.onRuntimeResult(mRequestCode, mPermissions, mPermissionsGranted, mPermissionsDenied,
                            mPermissionsDeniedForever);
                    break;
                case TYPE_DRAW_OVERLAYS:
                    isGranted = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        isGranted = PermissionUtil.isGrantedDrawOverlays();
                    }
                    mResult.onSpecialResult(mRequestCode, TYPE_DRAW_OVERLAYS, isGranted);
                    break;
                case TYPE_WRITE_SETTINGS:
                    isGranted = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        isGranted = PermissionUtil.isGrantedWriteSettings();
                    }
                    mResult.onSpecialResult(mRequestCode, TYPE_WRITE_SETTINGS, isGranted);
                    break;
            }

        }
        resetData();
    }


    private void cCallback() {
        if (mIsWaitingRationale) {
            if (mSpecialType == -1) callback(TYPE_RUNTIME);
            else callback(mSpecialType);
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
        private BaseResultCallBack mResult;
        @NonNull
        private FragmentActivity mActivity;
        private boolean mExplain;

        public Builder(@NonNull FragmentActivity activity) {
            this.mActivity = activity;
        }

        public Builder(@NonNull Fragment fragment) {
            this.mActivity = Objects.requireNonNull(fragment.getActivity());
        }

        public Builder rational(RationaleCallback rational) {
            this.mRational = rational;
            return this;
        }

        public Builder result(BaseResultCallBack result) {
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

        private PermissionHelper mPermissionHelper;
        private int mType;
        private FragmentActivity mActivity;
        private boolean mNeedRemove = false;

        public static void start(@Type int type, @NonNull PermissionHelper helper, @NonNull FragmentActivity activity) {
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
            } else if (mType == TYPE_WRITE_SETTINGS) {
                PermissionUtil.requestWriteSettingPermission(this, TYPE_WRITE_SETTINGS);
            } else if (mType == TYPE_DRAW_OVERLAYS) {
                PermissionUtil.requestOverlayPermission(this, TYPE_DRAW_OVERLAYS);
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

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mPermissionHelper.onSpecialPermissions(requestCode);
            if (Utils.isGoodTimeTrans(mActivity)) {
                removeMySelf();
            } else {
                mNeedRemove = true;
            }
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
    }

// Call back -----------------------------------------------------------------------------------

    public interface RationaleCallback {
        void rationale(int requestCode, @NonNull PermissionPredicate predicate, @NonNull List<String> rationale);
    }

    @FunctionalInterface
    public interface PermissionPredicate {
        void continues(int requestCode, boolean continues);
    }

    public interface SpecialCallback extends BaseResultCallBack {
        @Override
        default void onRuntimeResult(int requestCode, @NonNull List<String> request, @NonNull List<String> granted,
                                     @NonNull List<String> denied, @NonNull List<String> deniedForever) {
        }
    }

    public interface SimpleRuntimeCallback extends RuntimeCallBack {

        @Override
        default void onRuntimeResult(int requestCode, @NonNull List<String> request, @NonNull List<String> granted,
                                     @NonNull List<String> denied, @NonNull List<String> deniedForever) {
            int size = request.size();
            int grantedSize = granted.size();
            if (grantedSize == size) {
                onGranted(requestCode);
            } else {
                onDenied(requestCode);
            }
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

    public interface RuntimeCallBack extends BaseResultCallBack {
        @Override
        default void onSpecialResult(int requestCode, int type, boolean hasPermission) {
        }
    }

    public interface BaseResultCallBack {
        void onRuntimeResult(int requestCode, @NonNull List<String> request, @NonNull List<String> granted,
                             @NonNull List<String> denied, @NonNull List<String> deniedForever);

        void onSpecialResult(int requestCode, int type, boolean isGranted);
    }
}
