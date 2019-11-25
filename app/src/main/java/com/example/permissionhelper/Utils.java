package com.example.permissionhelper;

import androidx.annotation.NonNull;

import java.util.List;

public class Utils {
    public static <T> boolean isContain(@NonNull final List<T> container, @NonNull final List<T> sublist) {
        if (container.size() == 0 || sublist.size() == 0) return false;
        else {
            for (T item : sublist) {
                if (!container.contains(item)) {
                    return false;
                }
            }
            return true;
        }
    }
}
