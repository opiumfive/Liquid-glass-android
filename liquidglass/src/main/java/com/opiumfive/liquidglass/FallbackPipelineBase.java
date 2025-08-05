package com.opiumfive.liquidglass;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import java.util.concurrent.atomic.AtomicInteger;

abstract class FallbackPipelineBase implements Pipeline {

    protected final View host;
    protected final View target;
    protected final View glView;
    private final float scale;
    private final AtomicInteger frameIdGen = new AtomicInteger();
    private final int[] loc = new int[2];
    int[] tp = new int[2], hp = new int[2];
    private final Rect tmpRect = new Rect();
    protected volatile float sigmaPx;
    boolean pixelCopy;
    private Bitmap bmpA, bmpB, bmpC;
    private int front = 0;
    private Canvas captureCanvas;
    private boolean alive = true;
    private HandlerThread copyThread;
    private Handler copyHandler;

    public FallbackPipelineBase(View host, View target, float sigmaPx, float scale, boolean choosePixelCopy) {
        this.host = host;
        this.target = target;
        this.sigmaPx = sigmaPx;
        this.scale = Math.max(0.1f, Math.min(scale, 1f));
        this.pixelCopy = choosePixelCopy && Build.VERSION.SDK_INT >= 26;

        glView = createGlView();
        ((ViewGroup) host).addView(glView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (pixelCopy) {
            copyThread = new HandlerThread("BlurPixelCopy");
            copyThread.start();
            copyHandler = new Handler(copyThread.getLooper());
        }
    }

    abstract View createGlView();

    abstract void requestResize(int capW, int capH, int viewW, int viewH);

    abstract void requestExit();

    abstract void queueBitmap(int frameId, int idx, Bitmap bitmap);

    @Override
    public void onPreDraw() {
        if (!alive || bmpA == null) return;
        captureFrame();
    }

    @Override
    public void draw(Canvas c) {
    }

    @Override
    public void onSizeChanged(int w, int h) {
        if (w == 0 || h == 0) return;

        int bw = Math.max(1, Math.round(w * scale));
        int bh = Math.max(1, Math.round(h * scale));

        recycleBitmaps();

        Bitmap.Config config = LiquidGlassConfig.USE_BITMAP_565 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;

        bmpA = Bitmap.createBitmap(bw, bh, config);
        bmpB = Bitmap.createBitmap(bw, bh, config);
        if (pixelCopy) {
            bmpC = Bitmap.createBitmap(bw, bh, config);
        }
        captureCanvas = new Canvas();

        requestResize(bw, bh, w, h);
    }

    @Override
    public void dispose() {
        alive = false;
        recycleBitmaps();
        if (copyThread != null) copyThread.quitSafely();
        requestExit();
        if (glView.getParent() instanceof ViewGroup) {
            ((ViewGroup) glView.getParent()).removeView(glView);
        }
    }

    private void captureFrame() {
        if (!pixelCopy) {
            captureLegacyCanvas();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void captureWithPixelCopy() {
        if (!alive) return;
        int back = 1 ^ front;
        Bitmap dst = (back == 0) ? bmpA : back == 1 ? bmpB : bmpC;

        host.getLocationInWindow(loc);
        tmpRect.left = loc[0];
        tmpRect.top = loc[1];
        tmpRect.right = tmpRect.left + host.getWidth();
        tmpRect.bottom = tmpRect.top + host.getHeight();

        Activity act = (Activity) target.getContext();
        final int frameId = frameIdGen.incrementAndGet();
        try {
            PixelCopy.request(act.getWindow(), tmpRect, dst, result -> {
                if (result == PixelCopy.SUCCESS && alive) {
                    queueBitmap(frameId, back, dst);
                    front = back;
                }
            }, copyHandler);
        } catch (IllegalArgumentException e) {
        }
    }

    private void captureLegacyCanvas() {
        int back = 1 ^ front;
        Bitmap dst = (back == 0) ? bmpA : bmpB;

        captureCanvas.setBitmap(dst);
        captureCanvas.save();

        target.getLocationInWindow(tp);
        host.getLocationInWindow(hp);
        captureCanvas.scale(scale, scale);
        captureCanvas.translate(-(hp[0] - tp[0]), -(hp[1] - tp[1]));
        target.draw(captureCanvas);
        captureCanvas.restore();
        int frameId = frameIdGen.incrementAndGet();
        queueBitmap(frameId, back, dst);
        front = back;
    }

    private void recycleBitmaps() {
        if (bmpA != null) bmpA.recycle();
        if (bmpB != null) bmpB.recycle();
        if (bmpC != null) bmpC.recycle();
        bmpA = bmpB = bmpC = null;
    }

    protected static final class Task {
        final int id, idx;
        final Bitmap bmp;

        Task(int id, int idx, Bitmap bmp) {
            this.id = id;
            this.idx = idx;
            this.bmp = bmp;
        }
    }
}
