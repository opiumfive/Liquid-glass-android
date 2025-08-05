package com.opiumfive.liquidglass;

import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public final class AGSLPipeline implements Pipeline {

    private final View host;
    private final View target;
    private final RenderNode node;
    private final int[] tp = new int[2];
    private final int[] hp = new int[2];

    private float cornerRadiusPx = LiquidGlassConfig.CORNER_RADIUS_PX;
    private float eccentricFactor = LiquidGlassConfig.ECCENTRIC_FACTOR;
    private float refractionHeight = LiquidGlassConfig.REFRACTION_HEIGHT_PX;
    private float refractionAmount = LiquidGlassConfig.REFRACTION_AMOUNT_PX;
    private float contrast = LiquidGlassConfig.CONTRAST;
    private float whitePoint = LiquidGlassConfig.WHITE_POINT;
    private float chromaMultiplier = LiquidGlassConfig.CHROMA_MULTIPLIER;

    private final RuntimeShader refractionShader;
    private final RuntimeShader materialShader;

    public AGSLPipeline(View host, View target) {
        this.host = host;
        this.target = target;
        this.node = new RenderNode("LiquidGlassNode");

        this.refractionShader = new RuntimeShader(LiquidShaders.refraction);
        this.materialShader = new RuntimeShader(LiquidShaders.material);

        applyRenderEffect();
    }

    @Override
    public void onSizeChanged(int w, int h) {
        node.setPosition(0, 0, w, h);
        record();
        applyRenderEffect();
    }

    @Override
    public void onPreDraw() {
        record();
    }

    private void record() {
        int w = target.getWidth(), h = target.getHeight();
        if (w == 0 || h == 0) return;

        Canvas rec = node.beginRecording(w, h);
        target.getLocationInWindow(tp);
        host.getLocationInWindow(hp);
        rec.translate(-(hp[0] - tp[0]), -(hp[1] - tp[1]));
        target.draw(rec);
        node.endRecording();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRenderNode(node);
    }

    @Override
    public void dispose() {
    }

    private void applyRenderEffect() {
        int width = target.getWidth();
        int height = target.getHeight();
        if (width == 0 || height == 0) return;

        float[] size = new float[] {LiquidGlassConfig.WIDTH, LiquidGlassConfig.HEIGHT};

        RenderEffect contentEffect = RenderEffect.createBlurEffect(LiquidGlassConfig.SIGMA, LiquidGlassConfig.SIGMA, Shader.TileMode.CLAMP);

        refractionShader.setFloatUniform("size", size);
        refractionShader.setFloatUniform("cornerRadius", cornerRadiusPx);
        refractionShader.setFloatUniform("eccentricFactor", eccentricFactor);
        refractionShader.setFloatUniform("refractionHeight", refractionHeight);
        refractionShader.setFloatUniform("refractionAmount", refractionAmount);

        RenderEffect refraction = RenderEffect.createRuntimeShaderEffect(refractionShader, "image");
        RenderEffect refractionChain = RenderEffect.createChainEffect(refraction, contentEffect);

        materialShader.setFloatUniform("contrast", contrast);
        materialShader.setFloatUniform("whitePoint", whitePoint);
        materialShader.setFloatUniform("chromaMultiplier", chromaMultiplier);
        RenderEffect material = RenderEffect.createRuntimeShaderEffect(materialShader, "image");
        RenderEffect finalChain = RenderEffect.createChainEffect(material, refractionChain);

        node.setRenderEffect(finalChain);
        host.invalidate();
    }
}
