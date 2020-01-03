package com.example.permissionhelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.example.permissionhelper.exception.PermissionNotDefined;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public final class PermissionHelper {

    /**
     * When you make a request by call {@link #requestPermission(int, List)},
     * some permissions(1) in permission request list have been granted before.
     * <p>
     * By default, i will filter these request and treat it granted. So i don't need request it again.
     * But when system show a dialog to user to let them grant permissions or not, they can open Setting app to turn
     * permission in (1) off and then return to your app and grant the rest(You can know it by onStop call back).
     * <p>
     * When request finish, i also check permissions in (1) still granted or not.
     * But when user turn it off, it is NG to continue request. By default i will return these permissions as not granted.
     * <p>
     * You can change default behavior to:
     * - {@link #REQUEST}: Also request granted permissions.
     * - {@link #REQUEST_AFTER}: After request finish, if permission in (1) was turn off by user, i will request it.
     */
    public enum GrantedBeforeBehavior {
        REQUEST,
        REQUEST_AFTER,
        DEFAULT;

        public boolean isRequest() {
            return this.ordinal() == REQUEST.ordinal();
        }

        public boolean isRequestAfter() {
            return this.ordinal() == REQUEST_AFTER.ordinal();
        }

        public boolean isDefault() {
            return this.ordinal() == DEFAULT.ordinal();
        }
    }

    private static final String TAG = PermissionHelper.class.getSimpleName();
    private static final int INIT_SIZE = 10;
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
    /**
     * Contain permission was denied
     */
    private final List<String> mPermissionsDenied;
    private int mRequestCode;

    // Call back
    private RationaleCallback mRationale;
    private ResultCallback mResult;

    // State
    private boolean mIsWaiting;

    // Behavior
    private GrantedBeforeBehavior mGrantedBeforeBehavior;

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

    private PermissionHelper() {
        mPermissions = new ArrayList<>(INIT_SIZE);
        mPermissionsRequest = new ArrayList<>(INIT_SIZE);
        mPermissionsGranted = new ArrayList<>(INIT_SIZE);
        mPermissionsDeniedForever = new ArrayList<>(INIT_SIZE);
        mPermissionsDenied = new ArrayList<>(INIT_SIZE);
        mRequestCode = DEFAULT_REQUEST_CODE;
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

    @NonNull
    public GrantedBeforeBehavior getGrantedBeforeBehavior() {
        return mGrantedBeforeBehavior;
    }

    public void setGrantedBeforeBehavior(@NonNull GrantedBeforeBehavior mGrantedBeforeBehavior) {
        this.mGrantedBeforeBehavior = mGrantedBeforeBehavior;
    }

    //endregion

    public void requestPermission(int requestCode, @NonNull final String permissions) {
        requestPermission(requestCode, Collections.singletonList(permissions));
    }

    public void requestPermission(int requestCode, @NonNull final List<String> permissions) {
        if (!Utils.isSubList(APP_PERMISSIONS, permissions)) {
            throw new PermissionNotDefined("Some request permissions did not defined in manifest");
        } else {
            resetData();
        }
        mRequestCode = requestCode;
        mPermissions.addAll(permissions);
        request();
    }

    /**
     * Delete old data and state
     */
    private void resetData() {
        mPermissions.clear();
        mPermissionsRequest.clear();
        mPermissionsGranted.clear();
        mPermissionsDeniedForever.clear();
        mPermissionsDenied.clear();
        mRequestCode = DEFAULT_REQUEST_CODE;
        mIsWaiting = false;
    }

    /**
     * Classify permissions are granted before.
     * If permission was granted, put them in {@code mPermissionsGranted} list.
     * If they not, put them in {@code mPermissionsRequest} list to request later.
     */
    private void request() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mPermissionsGranted.addAll(mPermissions);
            callback();
        } else {
            for (String permission : mPermissions) {
                if (PermissionUtil.isPermissionGranted(permission) && mGrantedBeforeBehavior.isRequest()) {
                    mPermissionsGranted.add(permission);
                } else {
                    mPermissionsRequest.add(permission);
                }
            }
            if (mPermissionsRequest.isEmpty()) {
                callback();
            } else {
                startPermissionActivity();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startPermissionActivity() {
        PermissionActivity.start(App.context(), PermissionActivity.TYPE_RUNTIME);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void reStartPermissionActivity() {
        if (mIsWaiting) {
            startPermissionActivity();
        } else {
            // todo: Does multi instance make memory leak, when whe has PermissionActivity?
            Log.e(TAG, "Can not reStartPermissionActivity, some class has clear it data");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean needRationale(@NonNull final Activity activity) {
        boolean isRationale = false;
        // If {@code mRationale} not null, caller want to has a callback of permissions that denied forever
        if (mRationale != null) {
            if (PermissionUtil.hasPermissionDeniedForever(activity, mPermissionsRequest)) {
                mPermissionsDeniedForever = PermissionUtil.getPermissionsDeniedForever(activity, mPermissionsRequest);
                mRationale.rationale(mRequestCode, again -> {
                    if (again) {
                        reStartPermissionActivity();
                    } else {
                        reCallback();
                    }
                }, mPermissionsDeniedForever, mPermissions);
                mIsWaiting = true;
                isRationale = true;
            }
            // On next request, caller surely want to ignore denied permission, so we must reset this call back
            mRationale = null;
        }
        return isRationale;
    }

    private void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == mRequestCode) {
            String p;
            for (int i = 0; i < permissions.length; i++) {
                p = permissions[i];
                if (grantResults[i] == PERMISSION_GRANTED) {
                    mPermissionsGranted.add(p);
                } else {
                    mPermissionsDenied.add(p);
                }
            }
            callback();
        }
    }

    /**
     * Call back to caller
     */
    private void callback() {
        if (mResult != null) {
            if (mPermissions.size() == mPermissionsGranted.size()) {
                mResult.onAllGranted(mRequestCode, mPermissions);
            } else if (mPermissions.size() == mPermissionsDenied.size()) {
                mResult.onAllDenied(mRequestCode, mPermissions);
            } else {
                mResult.onDenied(mRequestCode, mPermissionsDenied, mPermissionsGranted);
            }
        }
    }


    private void reCallback() {
        if (mIsWaiting) {
            callback();
        } else {
            // todo: Does multi instance make memory leak, when whe has PermissionActivity?
            Log.e(TAG, "Can not reCallback, some class has clear it data");
        }
    }

    /**
     * Reset data, release unused memory and functional interface
     */
    private void finish() {
        resetData();
    }

    public static class Builder {
        private RationaleCallback mRational;
        private ResultCallback mResult;
        private GrantedBeforeBehavior mBehavior;

        public Builder rational(RationaleCallback rational) {
            this.mRational = rational;
            return this;
        }

        public Builder result(ResultCallback result) {
            this.mResult = result;
            return this;
        }

        public Builder behavior(GrantedBeforeBehavior behavior) {
            this.mBehavior = behavior;
            return this;
        }

        public PermissionHelper build() {
            PermissionHelper instance = new PermissionHelper();
            instance.setGrantedBeforeBehavior(mBehavior);
            instance.setRationale(mRational);
            instance.setResult(mResult);
            return instance;
        }
    }

    // Inner class ---------------------------------------------------------------------------------

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static class PermissionActivity extends Activity {

        private static final String TYPE = "TYPE";
        public static final int TYPE_RUNTIME = 0x01;
        public static final int TYPE_WRITE_SETTINGS = 0x02;
        public static final int TYPE_DRAW_OVERLAYS = 0x03;

        public static void start(@NonNull final Context context, int type) {
            Intent intent = new Intent(context, PermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(TYPE, type);
            context.startActivity(intent);
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            super.onCreate(savedInstanceState);
            int byteExtra = getIntent().getIntExtra(TYPE, TYPE_RUNTIME);

            if (byteExtra == TYPE_RUNTIME) {
                if (getInstance().needRationale(this)) {
                    finish();
                    return;
                }
                ActivityCompat.requestPermissions(this, getInstance().mPermissionsRequest.toArray(new String[0]), getInstance().mRequestCode);
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
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            getInstance().onRequestPermissionsResult(requestCode, permissions, grantResults);
            finish();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            finish();
            return true;
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

        /**
         * User had choose deny forever {@code permissionsDeniedForever}. So we can't request these
         * {@code permissionsDeniedForever}
         * Implement class should show a dialog explain why need these permissions and guide user
         * to System Setting app to turn these {@code permissionsDeniedForever} on.
         * <br/>
         * After that, implement class should call {@link PermissionPredicate#continues(boolean)} and pass
         * {@code true} to continues request.
         * <br/>
         * Or pass {@code false} to cancel request.
         *
         * @param requestCode              Request code
         * @param permissionPredicate      Hold reference to object to continue request
         * @param permissionsDeniedForever List of permissions was denied forever
         * @param permissions              List of request permissions
         */
        void rationale(int requestCode, @NonNull PermissionPredicate permissionPredicate,
                       @NonNull List<String> permissionsDeniedForever, @NonNull List<String> permissions);
    }

    public abstract class SimpleResultCallback implements ResultCallback {

        @Override
        public void onAllGranted(int requestCode, @NonNull List<String> permissions) {
            onGranted(requestCode);
        }

        @Override
        public void onDenied(int requestCode, @NonNull List<String> permissionsDenied, @NonNull List<String> permissionsGranted) {
            onDenied(requestCode);
        }

        @Override
        public void onAllDenied(int requestCode, @NonNull List<String> permissions) {
            onDenied(requestCode);
        }

        /**
         * All permission request was granted
         *
         * @param requestCode Request code
         */
        abstract void onGranted(int requestCode);

        /**
         * At least 1 permission was denied
         *
         * @param requestCode Request code
         */
        abstract void onDenied(int requestCode);
    }

    public interface ResultCallback {
        void onAllGranted(int requestCode, @NonNull List<String> permissions);

        void onDenied(int requestCode, @NonNull List<String> permissionsDenied, @NonNull List<String> permissionsGranted);

        void onAllDenied(int requestCode, @NonNull List<String> permissions);
    }
}
