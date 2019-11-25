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
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public final class PermissionHelper {

    private static final String TAG = PermissionHelper.class.getSimpleName();
    private static final int INIT_SIZE = 10;
    private static final List<String> APP_PERMISSIONS = PermissionUtil.getAppPermissions();

    /**
     * Contains permissions param
     */
    private List<String> mPermissions;
    /**
     * Contain permissions that was not grant
     */
    private List<String> mPermissionsRequest;
    /**
     * Contain permissions was granted
     */
    private List<String> mPermissionsGranted;
    /**
     * Contain permission was deny forever
     */
    private List<String> mPermissionsDeniedForever;
    /**
     * Contain permission was denied
     */
    private List<String> mPermissionsDenied;
    private int mRequestCode;

    private OnRationaleCallback mOnRationaleCallback;
    private SimpleCallback mSimpleCallback;
    private FullCallback mFullCallback;

    // State
    private boolean mIsWaitingContinue;

    // todo: Implement for write setting and draw overlay permissions

    /*   private static SimpleCallback sSimpleCallback4WriteSettings;
       private static SimpleCallback sSimpleCallback4DrawOverlays;*/
   /* @RequiresApi(api = Build.VERSION_CODES.M)
    public static void requestWriteSettings(final SimpleCallback callback) {
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
    public static void requestDrawOverlays(final SimpleCallback callback) {
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
        mRequestCode = 0;
    }

    public static PermissionHelper getInstance() {
        return Singleton.INSTANCE;
    }

    public void requestPermission(int requestCode, @NonNull final List<String> permissions, @Nullable final CallBackBuilder callBackBuilder) {
        if (!Utils.isContain(APP_PERMISSIONS, permissions)) {
            throw new PermissionNotDefined("Some permissions in " + permissions.toString() + " did not defined in manifest");
        } else {
            resetData();
        }
        mRequestCode = requestCode;
        mPermissions.addAll(permissions);

        if (callBackBuilder != null) {
            mOnRationaleCallback = callBackBuilder.mOnRationaleCallback;
            mSimpleCallback = callBackBuilder.mSimpleCallback;
            mFullCallback = callBackBuilder.mFullCallback;
            if (mSimpleCallback != null && mFullCallback != null) {
                mSimpleCallback = null;
            }
        }
        request();
    }

    private void resetData() {
        mPermissions.clear();
        mPermissionsRequest.clear();
        mPermissionsGranted.clear();
        mPermissionsDeniedForever.clear();
        mPermissionsDenied.clear();
        mRequestCode = 0;

        mOnRationaleCallback = null;
        mSimpleCallback = null;
        mFullCallback = null;

        mIsWaitingContinue = false;
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
                if (PermissionUtil.isPermissionGranted(permission)) {
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
        PermissionActivity.start(App.getAppContext(), PermissionActivity.TYPE_RUNTIME);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void reStartPermissionActivity() {
        if (mIsWaitingContinue) {
            startPermissionActivity();
        } else {
            // todo: Does multi instance make memory leak, when whe has PermissionActivity?
            Log.e(TAG, "Can not reStartPermissionActivity, some class has clear it data");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean needRationale(@NonNull final Activity activity) {
        boolean isRationale = false;
        // If {@code mOnRationaleCallback} not null, caller want to has a callback of permissions that denied forever
        if (mOnRationaleCallback != null) {
            if (PermissionUtil.hasPermissionDeniedForever(activity, mPermissionsRequest)) {
                mPermissionsDeniedForever = PermissionUtil.getPermissionsDeniedForever(activity, mPermissionsRequest);
                mOnRationaleCallback.rationale(mRequestCode, again -> {
                    if (again) {
                        reStartPermissionActivity();
                    } else {
                        reCallback();
                    }
                }, mPermissionsDeniedForever, mPermissions);
                mIsWaitingContinue = true;
                isRationale = true;
            }
            // On next request, caller surely want to ignore denied permission, so we must reset this call back
            mOnRationaleCallback = null;
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
        if (mSimpleCallback != null) {
            simpleCallBack();
        } else if (mFullCallback != null) {
            fullCallback();
        }
    }

    private void reCallback() {
        if (mIsWaitingContinue) {
            callback();
        } else {
            // todo: Does multi instance make memory leak, when whe has PermissionActivity?
            Log.e(TAG, "Can not reCallback, some class has clear it data");
        }
    }

    private void simpleCallBack() {
        if (mSimpleCallback != null) {
            if (mPermissions.size() == mPermissionsGranted.size()) {
                mSimpleCallback.onGranted(mRequestCode);
            } else {
                mSimpleCallback.onDenied(mRequestCode);
            }
        }
    }

    private void fullCallback() {
        if (mFullCallback != null) {
            if (mPermissions.size() == mPermissionsGranted.size()) {
                mFullCallback.onAllGranted(mRequestCode, mPermissions);
            } else if (mPermissions.size() == mPermissionsDenied.size()) {
                mFullCallback.onAllDenied(mRequestCode, mPermissions);
            } else {
                mFullCallback.onDenied(mRequestCode, mPermissionsDenied, mPermissionsGranted);
            }
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

    private static class Singleton {
        private static final PermissionHelper INSTANCE = new PermissionHelper();
    }

    public class CallBackBuilder {
        private OnRationaleCallback mOnRationaleCallback;
        private SimpleCallback mSimpleCallback;
        private FullCallback mFullCallback;

        public CallBackBuilder() {
        }

        public CallBackBuilder rationaleCallback(OnRationaleCallback mOnRationaleCallback) {
            this.mOnRationaleCallback = mOnRationaleCallback;
            return this;
        }

        public CallBackBuilder setSimpleCallback(SimpleCallback mSimpleCallback) {
            this.mSimpleCallback = mSimpleCallback;
            return this;
        }

        public CallBackBuilder setFullCallback(FullCallback mFullCallback) {
            this.mFullCallback = mFullCallback;
            return this;
        }
    }

    // Interface -----------------------------------------------------------------------------------

    public interface OnRationaleCallback {

        /**
         * Implement class can show a dialog explain why need these permissions.
         * And guide user to System Setting app to turn on permissions in {@code permissionsDeniedForever}.
         * <br/>
         * Or you can call {@link ShouldRequest#continuesRequest(boolean)}
         * , that will ignore permission in {@code permissionsDeniedForever}
         *
         * @param requestCode              Request code
         * @param shouldRequest            Hold reference to object to continue request
         * @param permissionsDeniedForever List of permissions was denied forever
         * @param permissions              List of request permissions
         */
        void rationale(int requestCode, @NonNull ShouldRequest shouldRequest,
                       @NonNull List<String> permissionsDeniedForever, @NonNull List<String> permissions);

        interface ShouldRequest {
            void continuesRequest(boolean again);
        }
    }

    public interface SimpleCallback {
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

    public interface FullCallback {
        void onAllGranted(int requestCode, @NonNull List<String> permissions);

        void onDenied(int requestCode, @NonNull List<String> permissionsDenied, @NonNull List<String> permissionsGranted);

        void onAllDenied(int requestCode, @NonNull List<String> permissions);
    }
}
