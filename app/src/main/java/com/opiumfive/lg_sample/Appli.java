package com.opiumfive.lg_sample;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

public class Appli extends Application {

    public static Appli gContext = null;
    public static volatile Handler applicationHandler;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        gContext = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        applicationHandler = new Handler(getApplicationContext().getMainLooper());
    }
}
