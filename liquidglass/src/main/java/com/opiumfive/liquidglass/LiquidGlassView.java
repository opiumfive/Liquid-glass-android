package com.opiumfive.liquidglass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

public class LiquidGlassView extends FrameLayout {

    private Pipeline pipeline;
    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        if (pipeline != null) pipeline.onPreDraw();
        return true;
    };
    private View target;

    private boolean listenerAdded = false;

    public LiquidGlassView(Context c) {
        super(c);
        init();
    }

    public void init(View target) {
        if (this.target != null) removePreDrawListener();

        this.target = target;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pipeline = new AGSLPipeline(this, target);
        } else {
            if (LiquidGlassConfig.SURFACE_VIEW) {
                pipeline = new FallbackPipelineSurface(this, target, LiquidGlassConfig.SIGMA, LiquidGlassConfig.CAPTURE_SCALE, LiquidGlassConfig.PIXEL_COPY);
            } else {
                pipeline = new FallbackPipelineTexture(this, target, LiquidGlassConfig.SIGMA, LiquidGlassConfig.CAPTURE_SCALE, LiquidGlassConfig.PIXEL_COPY);
            }
        }

        addPreDrawListener();
        requestLayout();
        invalidate();
    }

    private void init() {
        setWillNotDraw(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && LiquidGlassConfig.CORNER_RADIUS_PX > 1) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), LiquidGlassConfig.CORNER_RADIUS_PX);
                }
            });
            setClipToOutline(true);
            invalidateOutline();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pipeline != null) pipeline.draw(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (pipeline != null) pipeline.onSizeChanged(w, h);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addPreDrawListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        removePreDrawListener();
        if (pipeline != null) pipeline.dispose();
        super.onDetachedFromWindow();
    }

    private void addPreDrawListener() {
        if (target != null && !listenerAdded) {
            target.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            listenerAdded = true;
        }
    }

    private void removePreDrawListener() {
        if (target != null && listenerAdded) {
            target.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            listenerAdded = false;
        }
    }
}
