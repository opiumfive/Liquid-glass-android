package com.opiumfive.lg_sample;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.opiumfive.liquidglass.ExtraWindowHost;
import com.opiumfive.liquidglass.LiquidGlassConfig;
import com.opiumfive.liquidglass.LiquidGlassView;

public class LiquidGlassActivity extends AppCompatActivity {
    ImageView imageView;
    FrameLayout frameLayout;
    FrameLayout mainFrameLayout;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int t = getIntent().getIntExtra("t", 1);

        mainFrameLayout = new FrameLayout(this);
        frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(Color.WHITE);
        mainFrameLayout.addView(frameLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(mainFrameLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        imageView = new ImageView(this);
        imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.back));

        imageView.setScaleX(1.33f);
        imageView.setScaleY(1.33f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.leftMargin = 0;
        params.rightMargin = 0;
        params.topMargin = 0;
        frameLayout.addView(imageView, params);

        float moveDistance = AndroidUtilities.dpf2(70);
        ObjectAnimator animator = ObjectAnimator.ofFloat(imageView, "translationY", 0f, -moveDistance, 0f, moveDistance, 0f);
        animator.setDuration(2000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.start();

        LottieAnimationView lottie = new LottieAnimationView(this);
        lottie.setAnimation(R.raw.lottie);
        lottie.setRepeatCount(100);
        lottie.playAnimation();
        FrameLayout.LayoutParams params2 = new FrameLayout.LayoutParams(500, 500);
        params2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params2.leftMargin = 80;
        params2.rightMargin = 80;
        params2.bottomMargin = 160;
        frameLayout.addView(lottie, params2);

        ProgressBar progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams params3 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params3.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params3.bottomMargin = 20;
        frameLayout.addView(progressBar, params3);

        imageView.setVisibility(View.VISIBLE);

        imageView.post(() -> {
            boolean pixelCopy = t == 3 || t == 4;
            boolean surface = t == 1 || t == 3;
            boolean withAboveView = t == 5 || t == 6;

            LiquidGlassConfig.configure(LiquidGlassActivity.this, new LiquidGlassConfig.Overrides()
                    .pixelCopy(pixelCopy)
                    .surfaceView(surface)
                    .cornerRadiusPx(AndroidUtilities.dpf2(50))
                    .refractionHeightPx(AndroidUtilities.dpf2(30))
                    .refractionAmountPx(-AndroidUtilities.dpf2(60))
                    .size(AndroidUtilities.getDeviceWidthPx() - (int)AndroidUtilities.dpf2(32), (int) AndroidUtilities.dpf2(100)));

            LiquidGlassView liquidGlassView = new LiquidGlassView(LiquidGlassActivity.this);

            if (Build.VERSION.SDK_INT >= 33) {
                mainFrameLayout.addView(liquidGlassView, new FrameLayout.LayoutParams(LiquidGlassConfig.WIDTH, LiquidGlassConfig.HEIGHT));
            } else {
                if (LiquidGlassConfig.PIXEL_COPY && !LiquidGlassConfig.SURFACE_VIEW) {
                    ViewGroup vg = ExtraWindowHost.obtain(this);
                    vg.addView(liquidGlassView, new ViewGroup.LayoutParams(LiquidGlassConfig.WIDTH, LiquidGlassConfig.HEIGHT));
                } else {
                    mainFrameLayout.addView(liquidGlassView, new FrameLayout.LayoutParams(LiquidGlassConfig.WIDTH, LiquidGlassConfig.HEIGHT));
                }
            }
            liquidGlassView.init(frameLayout);

            FrameLayout oppa;
            if (withAboveView) {
                TextView textView = new TextView(LiquidGlassActivity.this);
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(17);
                textView.setText("Above view");
                textView.setPadding(80, 80, 80, 80);
                textView.setBackgroundColor(0x3f000000);

                if (surface) {
                    ViewGroup vg = ExtraWindowHost.obtain(this);
                    oppa = new FrameLayout(LiquidGlassActivity.this);
                    FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(LiquidGlassConfig.WIDTH, LiquidGlassConfig.HEIGHT);
                    pp.topMargin = (int) AndroidUtilities.dpf2(80);
                    vg.addView(oppa, pp);
                    oppa.addView(textView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                } else {
                    oppa = null;
                    liquidGlassView.addView(textView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                }

                liquidGlassView.setTranslationX(AndroidUtilities.dpf2(16));
                liquidGlassView.setTranslationY(AndroidUtilities.dpf2(16));
                if (oppa != null) {
                    oppa.setTranslationX(liquidGlassView.getTranslationX());
                    oppa.setTranslationY(liquidGlassView.getTranslationY());
                }
            } else {
                oppa = null;
            }

            mainFrameLayout.setOnTouchListener((view, motionEvent) -> {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (liquidGlassView != null) {
                            liquidGlassView.setTranslationX(motionEvent.getX() - LiquidGlassConfig.WIDTH / 2f);
                            liquidGlassView.setTranslationY(motionEvent.getY() - LiquidGlassConfig.HEIGHT / 2f);
                            if (oppa != null) {
                                oppa.setTranslationX(liquidGlassView.getTranslationX());
                                oppa.setTranslationY(liquidGlassView.getTranslationY());
                            }
                        }
                }
                return false;
            });
        });
    }

    @Override
    protected void onDestroy() {
        ExtraWindowHost.release(this);
        super.onDestroy();
    }
}
