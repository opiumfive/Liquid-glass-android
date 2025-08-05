package com.opiumfive.liquidglass;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class FallbackPipelineSurface extends FallbackPipelineBase {

    private final GLRenderer renderer;

    public FallbackPipelineSurface(View host, View target, float sigmaPx, float scale, boolean choosePixelCopy) {
        super(host, target, sigmaPx, scale, choosePixelCopy);

        renderer = new GLRenderer();
        if (glView instanceof BlurGLSurfaceView) {
            BlurGLSurfaceView surfaceView = (BlurGLSurfaceView) glView;
            surfaceView.setRenderer(renderer);
            surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
    }

    @Override
    View createGlView() {
        return new BlurGLSurfaceView(target.getContext());
    }

    @Override
    void requestResize(int capW, int capH, int viewW, int viewH) {
        renderer.requestResize(capW, capH, viewW, viewH);
    }

    @Override
    void requestExit() {
        renderer.requestExit();
    }

    @Override
    void queueBitmap(int frameId, int idx, Bitmap bitmap) {
        renderer.queueBitmap(frameId, idx, bitmap);
    }

    /*────────────────────────── GLSurfaceView wrapper ───────────────*/
    private static class BlurGLSurfaceView extends GLSurfaceView {
        BlurGLSurfaceView(Context c) {
            super(c);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            setZOrderOnTop(true);
            getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        }
    }

    private final class GLRenderer implements GLSurfaceView.Renderer {

        private final Object queueLock = new Object();
        private Task pending = null;
        private int  latestFrameIdSeen = -1;

        private final AtomicBoolean exitRequested = new AtomicBoolean(false);
        private final Renderer renderer = new Renderer();


        void queueBitmap(int frameId, int idx, Bitmap bmp) {
            synchronized (queueLock) {
                if (frameId > latestFrameIdSeen) {
                    pending = new Task(frameId, idx, bmp);
                    latestFrameIdSeen  = frameId;
                }
            }
        }

        void requestResize(int capW, int capH, int viewW, int viewH) {
            renderer.setCapSize(capW, capH);
            renderer.setViewSize(viewW, viewH);
            renderer.recreateGlResources();
        }

        void requestExit() {
            exitRequested.set(true);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            renderer.onSurfaceCreate();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            renderer.setViewSize(width, height);
        }

        private int currentIdx = 0;
        long lastCaptureTs = 0;

        @Override
        public void onDrawFrame(GL10 gl) {
            if (exitRequested.get()) return;

            long now = SystemClock.uptimeMillis();
            boolean needCapture = now - lastCaptureTs > LiquidGlassConfig.PC_CAPTURE_INTERVAL_MS;
            if (pixelCopy && needCapture) {
                captureWithPixelCopy();
                lastCaptureTs = now;
            }

            float wantSigma = FallbackPipelineSurface.this.sigmaPx;
            if (wantSigma != renderer.sigmaInUse) {
                renderer.chooseLevelsForSigma(wantSigma);
                renderer.sigmaInUse = wantSigma;
            }

            Task t;
            synchronized (queueLock) {
                t = pending;
                pending = null;
            }
            if (t != null) {
                renderer.uploadToTexture(t.idx, t.bmp);
                currentIdx = t.idx;
            }

            renderer.drawLiquidGlass(currentIdx);
        }
    }
}
