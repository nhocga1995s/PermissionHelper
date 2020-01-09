package com.example.permissionhelper.helper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.ArraySet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PermissionUtil {
    @NonNull
    public static List<String> getAppPermissions() {
        PackageManager pm = App.context().getPackageManager();
        try {
            String[] permissions = pm.getPackageInfo(App.context().getPackageName(),
                    PackageManager.GET_PERMISSIONS).requestedPermissions;
            if (permissions == null) return Collections.emptyList();
            return Arrays.asList(permissions);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isGrantedWriteSettings() {
        return Settings.System.canWrite(App.context());
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isGrantedDrawOverlays() {
        return Settings.canDrawOverlays(App.context());
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void requestOverlayPermission(@NonNull Fragment f, int resultCode) {
        Intent myIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        f.startActivityForResult(myIntent, resultCode);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void requestWriteSettingPermission(@NonNull Fragment f, int resultCode) {
        Intent myIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        f.startActivityForResult(myIntent, resultCode);
    }

    public static void openAppDetailsSettings(@NonNull Activity activity, int requestCode) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivityForResult(intent, requestCode);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static boolean isPermissionGranted(@NonNull final String permission) {
        return ContextCompat.checkSelfPermission(App.context(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    public static CharSequence getPermissionGroupName(String permission) {
        CharSequence groupName = null;
        try {
            PackageManager packageManager = App.context().getPackageManager();
            PermissionInfo permissionInfo = packageManager.getPermissionInfo(permission, 0);
            if (permissionInfo.group != null) {
                PermissionGroupInfo permissionGroupInfo = packageManager.getPermissionGroupInfo(permissionInfo.group, 0);
                groupName = permissionGroupInfo.loadLabel(packageManager);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return groupName;
    }

    @NonNull
    public static ArraySet<CharSequence> getPermissionsGroupName(@NonNull List<String> permissions) {
        CharSequence c;
        ArraySet<CharSequence> set = new ArraySet<>();
        for (String p : permissions) {
            c = PermissionUtil.getPermissionGroupName(p);
            if (c != null) {
                set.add(c);
            }
        }
        return set;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static boolean shouldRationale(@NonNull final Activity activity, @NonNull final String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
}
