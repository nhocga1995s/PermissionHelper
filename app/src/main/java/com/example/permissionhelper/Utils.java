package com.example.permissionhelper;

import androidx.annotation.NonNull;

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
}
