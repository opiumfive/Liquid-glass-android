package com.opiumfive.lg_sample;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.WindowMetrics;

public class AndroidUtilities {

    public static int getDeviceWidthPx() {
        Context context = Appli.gContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager wm = context.getSystemService(WindowManager.class);
            if (wm != null) {
                WindowMetrics metrics = wm.getCurrentWindowMetrics();
                Rect bounds = metrics.getBounds();
                return bounds.width();
            }
        }

        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        return dm.widthPixels;
    }

    public static float dpf2(float value) {
        if (value == 0) {
            return 0;
        }
        float density = Appli.gContext.getResources().getDisplayMetrics().density;
        return density * value;
    }
}
