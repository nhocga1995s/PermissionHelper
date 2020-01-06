package com.example.permissionhelper.helper.exception;

import androidx.annotation.NonNull;

public class PermissionNotDefined extends PermissionException {
    private String mMessage;

    public PermissionNotDefined(@NonNull final String message) {
        super(message);
        mMessage = message;
    }

    @NonNull
    @Override
    public String getMessage() {
        return mMessage;
    }
}
