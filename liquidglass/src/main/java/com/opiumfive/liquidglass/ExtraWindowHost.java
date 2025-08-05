package com.opiumfive.liquidglass;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

@SuppressLint("StaticFieldLeak")
public final class ExtraWindowHost {

    private static ViewGroup sOverlayRoot;

    /** Obtain (or lazily create) the full‑screen overlay container. */
    public static ViewGroup obtain(Activity activity) {
        if (sOverlayRoot != null) {
            boolean attached = sOverlayRoot.isAttachedToWindow();
            IBinder oldToken = ((WindowManager.LayoutParams) sOverlayRoot.getLayoutParams()).token;
            IBinder newToken = activity.getWindow().getDecorView().getWindowToken();
            if (!attached || oldToken != newToken) {
                activity.getWindowManager().removeViewImmediate(sOverlayRoot);
                sOverlayRoot = null;
            }
        }

        if (sOverlayRoot == null) {
            WindowManager wm = activity.getWindowManager();
            FrameLayout container = new FrameLayout(activity);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);

            lp.token   = activity.getWindow().getDecorView().getWindowToken();
            lp.gravity = Gravity.TOP | Gravity.START;

            wm.addView(container, lp);

            sOverlayRoot = container;
        }
        return sOverlayRoot;
    }

    /** Call from `Activity.onDestroy()` to tidy up. */
    public static void release(Activity activity) {
        if (sOverlayRoot != null) {
            activity.getWindowManager().removeViewImmediate(sOverlayRoot);
            sOverlayRoot = null;
        }
    }
}
