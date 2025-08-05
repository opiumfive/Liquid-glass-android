package com.opiumfive.liquidglass;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Renderer {

    private static final int PBO_COUNT = 2;

    private final float cornerRadiusPx = LiquidGlassConfig.CORNER_RADIUS_PX;
    private final float eccentricFactor = LiquidGlassConfig.ECCENTRIC_FACTOR;
    private final float refractionHeightPx = LiquidGlassConfig.REFRACTION_HEIGHT_PX;
    private final float refractionAmountPx = LiquidGlassConfig.REFRACTION_AMOUNT_PX;
    private final float contrast = LiquidGlassConfig.CONTRAST;
    private final float whitePoint = LiquidGlassConfig.WHITE_POINT;
    private final float chromaMultiplier = LiquidGlassConfig.CHROMA_MULTIPLIER;
    private final int[] texPingPong = new int[2];
    private final int[] fboPingPong = new int[2];
    private final boolean[] texInit = new boolean[2];
    private final List<Integer> levelTex = new ArrayList<>();
    private final List<Integer> levelFbo = new ArrayList<>();
    private final List<Integer> levelW = new ArrayList<>();
    private final List<Integer> levelH = new ArrayList<>();
    private final int[] pboIds = new int[PBO_COUNT];
    float sigmaInUse = -1;
    int capW, capH;
    int viewW, viewH;
    private int vboQuad;
    private int progKawase, progLiquid;
    private int aQuadPos, aQuadTex, uTexKawase;
    private int aLiquidPos, aLiquidTex, uTexLiquid;
    private int uSize, uCornerRadius, uEccentricFactor, uRefractionHeight, uHalfSize, uInner,
            uViewSize, uViewHalf, uViewInner, uCapScale,
            uRefractionAmount, uContrast, uWhitePoint, uChroma;
    private int locKawaseInv;
    private int pboIdx = 0;
    private boolean usePbo = false;

    void setCapSize(int capW, int capH) {
        this.capW = capW;
        this.capH = capH;
    }

    void setViewSize(int viewW, int viewH) {
        this.viewW = viewW;
        this.viewH = viewH;
    }

    void onSurfaceCreate() {
        vboQuad = LiquidShaders.createQuadVbo();
        progKawase = LiquidShaders.buildKawaseProgram();
        progLiquid = LiquidShaders.buildLiquidProgram();

        aQuadPos = GLES20.glGetAttribLocation(progKawase, "a_Position");
        aQuadTex = GLES20.glGetAttribLocation(progKawase, "a_TexCoord");
        uTexKawase = GLES20.glGetUniformLocation(progKawase, "u_Texture");
        locKawaseInv = GLES20.glGetUniformLocation(progKawase, "uInvSrcRes");

        aLiquidPos = GLES20.glGetAttribLocation(progLiquid, "a_Position");
        aLiquidTex = GLES20.glGetAttribLocation(progLiquid, "a_TexCoord");
        uTexLiquid = GLES20.glGetUniformLocation(progLiquid, "u_Texture");

        uSize = GLES20.glGetUniformLocation(progLiquid, "uSize");
        uHalfSize = GLES20.glGetUniformLocation(progLiquid, "uHalfSize");
        uInner = GLES20.glGetUniformLocation(progLiquid, "uInner");
        uViewSize = GLES20.glGetUniformLocation(progLiquid, "uViewSize");
        uViewHalf = GLES20.glGetUniformLocation(progLiquid, "uViewHalf");
        uViewInner = GLES20.glGetUniformLocation(progLiquid, "uViewInner");
        uCapScale = GLES20.glGetUniformLocation(progLiquid, "uCapScale");
        uCornerRadius = GLES20.glGetUniformLocation(progLiquid, "uCornerRadius");
        uEccentricFactor = GLES20.glGetUniformLocation(progLiquid, "uEccentricFactor");
        uRefractionHeight = GLES20.glGetUniformLocation(progLiquid, "uRefractionHeight");
        uRefractionAmount = GLES20.glGetUniformLocation(progLiquid, "uRefractionAmount");
        uContrast = GLES20.glGetUniformLocation(progLiquid, "uContrast");
        uWhitePoint = GLES20.glGetUniformLocation(progLiquid, "uWhitePoint");
        uChroma = GLES20.glGetUniformLocation(progLiquid, "uChroma");

        if (LiquidGlassConfig.CAN_USE_PBO) {
            /* PBO support (ES3) */
            String v = GLES20.glGetString(GLES20.GL_VERSION);
            if (v != null && v.contains("OpenGL ES 3.")) {
                usePbo = true;
                GLES30.glGenBuffers(PBO_COUNT, pboIds, 0);
            }
        }

        recreateGlResources();
    }

    void recreateGlResources() {
        deleteGlResources();
        if (capW == 0 || capH == 0) return;

        GLES20.glGenTextures(2, texPingPong, 0);
        GLES20.glGenFramebuffers(2, fboPingPong, 0);
        for (int i = 0; i < 2; i++) {
            LiquidShaders.allocTexture(texPingPong[i], capW, capH);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPingPong[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texPingPong[i], 0);
        }

        levelTex.clear();
        levelFbo.clear();
        levelW.clear();
        levelH.clear();
        int w = capW, h = capH;
        while (w > capW / LiquidGlassConfig.MIN_SCALE_DENOM && h > capH / LiquidGlassConfig.MIN_SCALE_DENOM && levelTex.size() < LiquidGlassConfig.MAX_LEVELS) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            int[] t = new int[1];
            int[] f = new int[1];
            GLES20.glGenTextures(1, t, 0);
            LiquidShaders.allocTexture(t[0], w, h);
            GLES20.glGenFramebuffers(1, f, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, f[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, t[0], 0);
            levelTex.add(t[0]);
            levelFbo.add(f[0]);
            levelW.add(w);
            levelH.add(h);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    void deleteGlResources() {
        if (usePbo) GLES30.glDeleteBuffers(PBO_COUNT, pboIds, 0);
        if (texPingPong[0] != 0) GLES20.glDeleteTextures(2, texPingPong, 0);
        if (fboPingPong[0] != 0) GLES20.glDeleteFramebuffers(2, fboPingPong, 0);
        for (int t : levelTex) GLES20.glDeleteTextures(1, new int[]{t}, 0);
        for (int f : levelFbo) GLES20.glDeleteFramebuffers(1, new int[]{f}, 0);
        levelTex.clear();
        levelFbo.clear();
        levelW.clear();
        levelH.clear();
    }

    void uploadToTexture(int idx, Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) return;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPingPong[idx]);

        if (LiquidGlassConfig.USE_BITMAP_565) {
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 2);
        }

        if (!texInit[idx]) {
            if (LiquidGlassConfig.USE_BITMAP_565) {
                ByteBuffer buf = ByteBuffer.allocateDirect(bmp.getByteCount()).order(ByteOrder.nativeOrder());
                bmp.copyPixelsToBuffer(buf);
                buf.position(0);

                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0,
                        GLES20.GL_RGB,
                        capW, capH, 0,
                        GLES20.GL_RGB,
                        GLES20.GL_UNSIGNED_SHORT_5_6_5,
                        buf);
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            }
            texInit[idx] = true;
        } else if (usePbo) {
            try {
                int nextPbo = pboIds[pboIdx];
                int imgSize = LiquidGlassConfig.USE_BITMAP_565 ? capH * capW * 2 : bmp.getByteCount();
                GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, nextPbo);
                GLES30.glBufferData(GLES30.GL_PIXEL_UNPACK_BUFFER, imgSize, null, GLES30.GL_STREAM_COPY);
                ByteBuffer dst = (ByteBuffer) GLES30.glMapBufferRange(GLES30.GL_PIXEL_UNPACK_BUFFER, 0, imgSize, GLES30.GL_MAP_WRITE_BIT | GLES30.GL_MAP_INVALIDATE_BUFFER_BIT);
                bmp.copyPixelsToBuffer(dst);
                GLES30.glUnmapBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER);
                GLES30.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, capW, capH, LiquidGlassConfig.USE_BITMAP_565 ? GLES20.GL_RGB : GLES30.GL_RGBA, LiquidGlassConfig.USE_BITMAP_565 ? GLES20.GL_UNSIGNED_SHORT_5_6_5 : GLES30.GL_UNSIGNED_BYTE, null);
                GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, 0);
                pboIdx = (pboIdx + 1) % PBO_COUNT;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (LiquidGlassConfig.USE_BITMAP_565) {
                ByteBuffer buf = ByteBuffer.allocateDirect(bmp.getByteCount()).order(ByteOrder.nativeOrder());
                bmp.copyPixelsToBuffer(buf);
                buf.position(0);

                GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D, 0, 0, 0,
                        capW, capH,
                        GLES20.GL_RGB,
                        GLES20.GL_UNSIGNED_SHORT_5_6_5,
                        buf);
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bmp);
            }
        }
    }

    int dualKawaseBlur(int srcTex, int dstPingPongIdx) {
        GLES20.glUseProgram(progKawase);
        int curTex = srcTex;
        int curW = capW;
        int curH = capH;
        // down‑sample
        for (int lvl = 0; lvl < levelTex.size(); ++lvl) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, levelFbo.get(lvl));
            GLES20.glViewport(0, 0, levelW.get(lvl), levelH.get(lvl));
            GLES20.glUniform2f(locKawaseInv, 1f / curW, 1f / curH);
            drawQuadKawase(curTex);
            curTex = levelTex.get(lvl);
            curW = levelW.get(lvl);
            curH = levelH.get(lvl);
        }
        // up‑sample
        for (int lvl = levelTex.size() - 2; lvl >= 0; --lvl) {
            int dstTex = (lvl == 0) ? texPingPong[dstPingPongIdx] : levelTex.get(lvl);
            int dstFbo = (lvl == 0) ? fboPingPong[dstPingPongIdx] : levelFbo.get(lvl);
            int dstW = (lvl == 0) ? capW : levelW.get(lvl);
            int dstH = (lvl == 0) ? capH : levelH.get(lvl);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dstFbo);
            GLES20.glViewport(0, 0, dstW, dstH);
            GLES20.glUniform2f(locKawaseInv, 1f / dstW, 1f / dstH);
            drawQuadKawase(curTex);
            curTex = dstTex;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return texPingPong[dstPingPongIdx];
    }

    void drawQuadKawase(int texture) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboQuad);
        GLES20.glEnableVertexAttribArray(aQuadPos);
        GLES20.glEnableVertexAttribArray(aQuadTex);
        GLES20.glVertexAttribPointer(aQuadPos, 2, GLES20.GL_FLOAT, false, 4 * 4, 0);
        GLES20.glVertexAttribPointer(aQuadTex, 2, GLES20.GL_FLOAT, false, 4 * 4, 2 * 4);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uTexKawase, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aQuadPos);
        GLES20.glDisableVertexAttribArray(aQuadTex);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    void drawLiquid(int srcTex) {
        GLES20.glUseProgram(progLiquid);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboQuad);
        GLES20.glEnableVertexAttribArray(aLiquidPos);
        GLES20.glEnableVertexAttribArray(aLiquidTex);
        GLES20.glVertexAttribPointer(aLiquidPos, 2, GLES20.GL_FLOAT, false, 4 * 4, 0);
        GLES20.glVertexAttribPointer(aLiquidTex, 2, GLES20.GL_FLOAT, false, 4 * 4, 2 * 4);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex);
        GLES20.glUniform1i(uTexLiquid, 0);

        GLES20.glUniform2f(uSize, capW, capH);
        GLES20.glUniform2f(uHalfSize, capW / 2f, capH / 2f);
        GLES20.glUniform2f(uInner, capW / 2f - cornerRadiusPx, capH / 2f - cornerRadiusPx);

        float vw = (float) viewW;
        float vh = (float) viewH;
        float sx = (float) capW / vw;
        float sy = (float) capH / vh;

        GLES20.glUniform2f(uViewSize, vw, vh);
        GLES20.glUniform2f(uViewHalf, vw * 0.5f, vh * 0.5f);
        GLES20.glUniform2f(uViewInner, vw * 0.5f - cornerRadiusPx, vh * 0.5f - cornerRadiusPx);
        GLES20.glUniform2f(uCapScale, sx, sy);
        GLES20.glUniform1f(uCornerRadius, cornerRadiusPx);
        GLES20.glUniform1f(uEccentricFactor, eccentricFactor);
        GLES20.glUniform1f(uRefractionHeight, refractionHeightPx);
        GLES20.glUniform1f(uRefractionAmount, refractionAmountPx);
        GLES20.glUniform1f(uContrast, contrast);
        GLES20.glUniform1f(uWhitePoint, whitePoint);
        GLES20.glUniform1f(uChroma, chromaMultiplier);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aLiquidPos);
        GLES20.glDisableVertexAttribArray(aLiquidTex);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    void chooseLevelsForSigma(float sigma) {
        int want = Math.min(LiquidGlassConfig.MAX_LEVELS, sigma < 10 ? 3 : sigma < 25 ? 4 : sigma < 45 ? 5 : 6);
        while (levelTex.size() > want) {
            int last = levelTex.size() - 1;
            GLES20.glDeleteTextures(1, new int[]{levelTex.remove(last)}, 0);
            GLES20.glDeleteFramebuffers(1, new int[]{levelFbo.remove(last)}, 0);
            levelW.remove(last);
            levelH.remove(last);
        }
    }

    void drawLiquidGlass(int idx) {
        int blurred = (sigmaInUse >= LiquidGlassConfig.MIN_SIGMA_TO_BLUR)
                ? dualKawaseBlur(texPingPong[idx], 1 ^ (idx))
                : texPingPong[idx];

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, viewW, viewH);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        drawLiquid(blurred);
    }
}
