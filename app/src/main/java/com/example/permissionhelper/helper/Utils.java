package com.example.permissionhelper.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.versionedparcelable.NonParcelField;

import java.util.List;

public class Utils {

    public static <T> boolean isSubList(@NonNull final List<T> container, @NonNull final List<T> sublist) {
        if (container.size() == 0
                || sublist.size() == 0
                || container.size() < sublist.size()) return false;
        else {
            int[] indexArr = new int[container.size()];
            int index, i;
            for (T item : sublist) {
                // find index
                index = -1;
                for (i = 0; i < container.size(); i++) {
                    if (indexArr[i] == 0
                            && item.equals(container.get(i))) {
                        index = i;
                        break;
                    }
                }

                // check contain
                if (index < 0) return false;
                else indexArr[index]++;
            }
            return true;
        }
    }

    public static void startSetting(@NonNull Activity activity, int requestCode){
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivityForResult(intent, requestCode);
    }
}
