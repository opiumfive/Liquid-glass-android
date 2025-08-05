package com.opiumfive.liquidglass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.TextureView;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

final class FallbackPipelineTexture extends FallbackPipelineBase {

    private final RenderThread glThread;

    FallbackPipelineTexture(View host, View target, float sigmaPx, float scale, boolean choosePixelCopy) {
        super(host, target, sigmaPx, scale, choosePixelCopy);

        glThread = new RenderThread();
        if (glView instanceof BlurTextureView) {
            BlurTextureView texture = (BlurTextureView) glView;
            texture.setSurfaceTextureListener(glThread);
            texture.setOpaque(false);
        }
        glThread.start();
    }

    @Override
    View createGlView() {
        return new BlurTextureView(target.getContext());
    }

    @Override
    void requestResize(int capW, int capH, int viewW, int viewH) {
        glThread.requestResize(capW, capH, viewW, viewH);
    }

    @Override
    void requestExit() {
        glThread.requestExit();
    }

    @Override
    void queueBitmap(int frameId, int idx, Bitmap bitmap) {
        glThread.queueBitmap(frameId, idx, bitmap);
    }

    private static final class BlurTextureView extends TextureView {
        BlurTextureView(Context c) {
            super(c);
        }
    }

    private final class RenderThread extends Thread implements TextureView.SurfaceTextureListener {
        private final Object queueLock = new Object();
        /* resize info */
        private final AtomicBoolean resizeReq = new AtomicBoolean();
        private Task pending = null;
        private int  latestFrameIdSeen = -1;
        /* EGL */
        private EGL10 egl;
        private EGLDisplay dpy;
        private EGLContext ctx;
        private EGLSurface surf;
        private SurfaceTexture surfaceTex;

        private final Renderer renderer = new Renderer();

        private long fpsWindowStartMs = 0;
        private int  drawsInWindow   = 0;
        private int  uploadsInWindow = 0;

        private Handler glHandler;

        long lastCaptureTs = 0;

        private int currentTexIdx = 0;


        private static final int MSG_DRAW = 1;

        private final Choreographer choreographer = Choreographer.getInstance();
        private final Choreographer.FrameCallback cb = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                drawHandler.obtainMessage(MSG_DRAW, (int) frameTimeNanos, 0).sendToTarget();
            }
        };

        private final Runnable drawRunnable = () -> glHandler.obtainMessage(MSG_DRAW).sendToTarget();

        private boolean firstFrameReady = false;

        private Handler drawHandler;

        void queueBitmap(int frameId, int idx, Bitmap bmp) {
            synchronized (queueLock) {
                if (frameId > latestFrameIdSeen) {
                    pending = new Task(frameId, idx, bmp);
                    latestFrameIdSeen = frameId;
                }
            }
            if (glHandler != null) {
                glHandler.post(drawRunnable);
            }
        }

        void requestResize(int cw, int ch, int vw, int vh) {
            renderer.setCapSize(cw, ch);
            renderer.setViewSize(vw, vh);
            resizeReq.set(true);
        }

        void requestExit() {
            interrupt();
            try {
                Looper l = Looper.myLooper();
                if (l != null) l.quitSafely();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void startVsync() {
            choreographer.postFrameCallback(cb);
        }

        @Override
        public void run() {
            Looper.prepare();
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
            glHandler = new Handler(Looper.myLooper());
            drawHandler = new Handler(Looper.myLooper(), msg -> {
                if (msg.what != MSG_DRAW) return false;

                long now = SystemClock.uptimeMillis();
                boolean needCapture = now - lastCaptureTs > LiquidGlassConfig.PC_CAPTURE_INTERVAL_MS;
                if (pixelCopy && needCapture) {
                    captureWithPixelCopy();
                    lastCaptureTs = now;
                }

                float wantSigma = FallbackPipelineTexture.this.sigmaPx;
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
                    currentTexIdx   = t.idx;
                    uploadsInWindow++;
                    firstFrameReady = true;
                }
                if (firstFrameReady) {
                    renderer.drawLiquidGlass(currentTexIdx);

                    egl.eglSwapBuffers(dpy, surf);   // blocks ≈0–2ms on 60Hz
                    drawsInWindow++;

                    long nowMs = SystemClock.uptimeMillis();
                    if (fpsWindowStartMs == 0) fpsWindowStartMs = nowMs;

                    if (nowMs - fpsWindowStartMs >= 1000) {           // one-second window
                        float drawFps   = 1000f * drawsInWindow   / (nowMs - fpsWindowStartMs);
                        float uploadFps = 1000f * uploadsInWindow / (nowMs - fpsWindowStartMs);
                        android.util.Log.d("LiquidGlass", String.format("FPS  draw=%.1f  upload=%.1f", drawFps, uploadFps));

                        fpsWindowStartMs = nowMs;
                        drawsInWindow    = 0;
                        uploadsInWindow  = 0;
                    }
                }

                choreographer.postFrameCallback(cb);

                return true;
            });
            waitForSurface();
            initEglAndGl();
            surfaceTex.setDefaultBufferSize(renderer.viewW, renderer.viewH);

            final AtomicBoolean quit = new AtomicBoolean(false);

            startVsync();

            Looper.loop();
            quit.set(true);
            cleanup();
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
            surfaceTex = st;
            renderer.setViewSize(w, h);

            synchronized (this) {
                notify();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
            requestResize(renderer.capW, renderer.capH, w, h);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
            requestExit();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture st) {
        }

        private void waitForSurface() {
            synchronized (this) {
                while (surfaceTex == null && !isInterrupted()) try {
                    wait();
                } catch (InterruptedException ignored) {
                }
            }
        }

        private void initEglAndGl() {
            egl = (EGL10) EGLContext.getEGL();
            dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            egl.eglInitialize(dpy, null);

            int[] cfgSpec = {EGL10.EGL_RENDERABLE_TYPE, 4, EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_NONE};
            EGLConfig[] cfgOut = new EGLConfig[1];
            egl.eglChooseConfig(dpy, cfgSpec, cfgOut, 1, new int[1]);
            int[] ctxAttr = {0x3098, 2, EGL10.EGL_NONE};
            ctx = egl.eglCreateContext(dpy, cfgOut[0], EGL10.EGL_NO_CONTEXT, ctxAttr);
            surf = egl.eglCreateWindowSurface(dpy, cfgOut[0], surfaceTex, null);
            egl.eglMakeCurrent(dpy, surf, surf, ctx);

            renderer.onSurfaceCreate();
        }

        private void cleanup() {
            renderer.deleteGlResources();
            if (egl != null) {
                egl.eglMakeCurrent(dpy, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl.eglDestroySurface(dpy, surf);
                egl.eglDestroyContext(dpy, ctx);
                egl.eglTerminate(dpy);
            }
        }
    }
}
