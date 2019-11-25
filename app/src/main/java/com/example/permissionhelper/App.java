package com.example.permissionhelper;

import android.app.Application;
import android.content.Context;

public class App extends Application {
    private static App mInstance;

    public static Context getAppContext(){
        return mInstance.getApplicationContext();
    }

    @Override
    public void onCreate() {
        mInstance = this;
        super.onCreate();
    }
}
