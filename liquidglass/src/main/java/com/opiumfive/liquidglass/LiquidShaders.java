package com.opiumfive.liquidglass;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class LiquidShaders {

    static int buildKawaseProgram() {
        String vs =
                "attribute vec4 a_Position;\n" +
                "attribute vec2 a_TexCoord;\n" +
                "varying   vec2 v_T;\n" +
                "void main() {\n" +
                "    gl_Position = a_Position;\n" +
                "    v_T = a_TexCoord;\n" +
                "}";

        String fs =
                "precision mediump float;\n" +
                "uniform sampler2D u_Texture;\n" +
                "uniform vec2 uInvSrcRes;\n" +
                "varying vec2 v_T;\n" +
                "vec4 T(vec2 o) {\n" +
                "    return texture2D(u_Texture, v_T + o * uInvSrcRes);\n" +
                "}\n" +
                "void main() {\n" +
                "    gl_FragColor = 0.25 * (\n" +
                "          T(vec2(-2.5, -1.5)) + T(vec2( 2.5, -1.5))\n" +
                "        + T(vec2(-1.5,  2.5)) + T(vec2( 1.5,  2.5))\n" +
                "    );\n" +
                "}";

        return linkProgram(vs, fs);
    }

    static int buildLiquidProgram() {
        String vs =
                "attribute vec4 a_Position;\n" +
                "attribute vec2 a_TexCoord;\n" +
                "varying   vec2 v_T;\n" +
                "void main() {\n" +
                "    gl_Position = a_Position;\n" +
                "    v_T = vec2(a_TexCoord.x, 1.0 - a_TexCoord.y);\n" +
                "}";

        String fs =
                "precision lowp float;\n" +
                "\n" +
                "/* ───── Uniforms ───── */\n" +
                "uniform sampler2D u_Texture;\n" +
                "uniform vec2  uSize;\n" +
                "uniform vec2  uHalfSize;          // pre‑computed on CPU\n" +
                "uniform vec2  uInner;             // pre‑computed on CPU\n" +
                "uniform float uCornerRadius, uEccentricFactor,\n" +
                "             uRefractionHeight, uRefractionAmount;\n" +
                "uniform float uContrast,       uWhitePoint, uChroma;\n" +
                "uniform vec2  uViewSize;\n" +
                "uniform vec2  uViewHalf;\n" +
                "uniform vec2  uViewInner;\n" +
                "uniform vec2  uCapScale;" +
                "\n" +
                "/* ───── Varyings ───── */\n" +
                "varying vec2 v_T;\n" +
                "\n" +
                "/* ───── Helpers ───── */\n" +
                "float sdRect(vec2 p, vec2 h) {\n" +
                "    vec2 d = abs(p) - h;\n" +
                "    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);\n" +
                "}\n" +
                "\n" +
                "float sdRound(vec2 p) { return sdRect(p, uInner) - uCornerRadius; }\n" +
                "float sdRoundView(vec2 p) { return sdRect(p, uViewInner) - uCornerRadius; }\n" +
                "vec2  gradSdRoundView(vec2 p) {\n" +
                "    vec2 q = abs(p) - uViewInner;\n" +
                "    return (q.x >= 0. && q.y >= 0.)\n" +
                "         ? sign(p) * normalize(q)\n" +
                "         : sign(p) * ((-q.x < -q.y) ? vec2(1.,0.) : vec2(0.,1.));\n" +
                "}\n" +
                "\n" +
                "vec2 gradSdRound(vec2 p) {\n" +
                "    vec2 q = abs(p) - uInner;\n" +
                "    return (q.x >= 0. && q.y >= 0.)\n" +
                "         ? sign(p) * normalize(q)\n" +
                "         : sign(p) * ((-q.x < -q.y) ? vec2(1.,0.) : vec2(0.,1.));\n" +
                "}\n" +
                "\n" +
                "/* inline circle(): 1−sqrt(1−x²) */\n" +
                "float circleInline(float x) { return 1.0 - sqrt(1.0 - x * x); }\n" +
                "\n" +
                "/* Refraction helper */\n" +
                "vec4 refr(vec2 px) {\n" +
                "    vec2  cp = px - uHalfSize;\n" +
                "    float sd = min(sdRound(cp), 0.0);\n" +
                "\n" +
                "    if (sd <= 0. && -sd <= uRefractionHeight) {\n" +
                "        float gr  = min(uCornerRadius * 1.5,\n" +
                "                       max(max(uHalfSize.x, uHalfSize.y), uCornerRadius));\n" +
                "        vec2 n    = gradSdRound(cp);\n" +
                "        float dst = circleInline(1.0 - (-sd) / uRefractionHeight)\n" +
                "                    * uRefractionAmount;\n" +
                "        vec2 dir  = normalize(n + uEccentricFactor * normalize(cp));\n" +
                "        vec2 rp   = px + dst * dir;\n" +
                "\n" +
                "        if (rp.x < 0. || rp.y < 0. || rp.x >= uSize.x || rp.y >= uSize.y)\n" +
                "            return vec4(0.);\n" +
                "\n" +
                "        return texture2D(u_Texture, rp / uSize);\n" +
                "    }\n" +
                "    return texture2D(u_Texture, px / uSize);\n" +
                "}\n" +
                "\n" +
                "/* ───── Main ───── */\n" +
                "void main() {\n" +
                "    vec2  pxV = v_T * uViewSize;\n" +
                "    vec2  cpV = pxV - uViewHalf;\n" +
                "    float sd = sdRoundView(cpV);\n" +
                "\n" +
                "    /* “Kill discard” – write transparent instead */\n" +
                "    if (sd > 0.0) {\n" +
                "        gl_FragColor = vec4(0.0);\n" +
                "        return;\n" +
                "    }\n" +
                "\n" +
                "    vec2  pxC = pxV * uCapScale;\n" +

                "    vec4  c;\n" +
                "    if (sd <= 0.0 && -sd <= uRefractionHeight) {\n" +
                "        vec2 n   = gradSdRoundView(cpV);\n" +
                "        float dst= circleInline(1.0 - (-sd) / uRefractionHeight) * uRefractionAmount;\n" +
                "        vec2 dir = normalize(n + uEccentricFactor * normalize(cpV));\n" +
                "        vec2 rpC = (pxV + dst * dir) * uCapScale;\n" +
                "        if (rpC.x < 0. || rpC.y < 0. || rpC.x >= uSize.x || rpC.y >= uSize.y)\n" +
                "            c = vec4(0.0);\n" +
                "        else\n" +
                "            c = texture2D(u_Texture, rpC / uSize);\n" +
                "    } else {\n" +
                "        c = texture2D(u_Texture, pxC / uSize);\n" +
                "    }\n" +
                "\n" +
                "    /* ── Grade & contrast ──  (branchless) */\n" +
                "    // Chrominance‑desat (still uses full luma)\n" +
                "    const vec3 rgbToY = vec3(0.2126, 0.7152, 0.0722);\n" +
                "    float y  = dot(c.rgb, rgbToY);\n" +
                "    c.rgb = mix(vec3(y), c.rgb, uChroma);\n" +
                "\n" +
                "    // White‑point target   (vec3(0) if uWhitePoint<0, vec3(1) if ≥0)\n" +
                "    float wpSel = step(0.0, uWhitePoint);      // 0 or 1\n" +
                "    vec3  tgt   = vec3(wpSel);\n" +
                "    c.rgb = mix(c.rgb, tgt, abs(uWhitePoint));\n" +
                "\n" +
                "    // Contrast\n" +
                "    c.rgb = (c.rgb - 0.5) * (1.0 + uContrast) + 0.5;\n" +
                "\n" +
                "    gl_FragColor = c;\n" +
                "}\n";

        return linkProgram(vs, fs);
    }


    private static int linkProgram(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("link error: " + GLES20.glGetProgramInfoLog(p));
        return p;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("compile error: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    static int createQuadVbo() {
        float[] v = {
                -1, -1, 0, 0,
                1, -1, 1, 0,
                -1,  1, 0, 1,
                1,  1, 1, 1
        };
        FloatBuffer fb = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(v).position(0);
        int[] id = new int[1];
        GLES20.glGenBuffers(1, id, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, v.length * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return id[0];
    }

    static void allocTexture(int tex, int w, int h) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);

        int fmt = LiquidGlassConfig.USE_BITMAP_565 ? GLES20.GL_RGB : GLES20.GL_RGBA;
        int type = LiquidGlassConfig.USE_BITMAP_565 ? GLES20.GL_UNSIGNED_SHORT_5_6_5 : GLES20.GL_UNSIGNED_BYTE;

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, fmt, w, h, 0, fmt, type, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,   GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,   GLES20.GL_CLAMP_TO_EDGE);
    }

    private static final String rgbToY =
            "const half3 rgbToY = half3(0.2126, 0.7152, 0.0722);\n" +
            "float luma(half4 color) {\n" +
            "    return dot(toLinearSrgb(color.rgb), rgbToY);\n" +
            "}\n";

    private static final String sdUtils =
            "float sdRectangle(float2 coord, float2 halfSize) {\n" +
            "    float2 d = abs(coord) - halfSize;\n" +
            "    float outside = length(max(d, 0.0));\n" +
            "    float inside = min(max(d.x, d.y), 0.0);\n" +
            "    return outside + inside;\n" +
            "}\n" +
            "float sdRoundedRectangle(float2 coord, float2 halfSize, float r) {\n" +
            "    float2 inner = halfSize - float2(r);\n" +
            "    return sdRectangle(coord, inner) - r;\n" +
            "}\n" +
            "float2 gradSdRoundedRectangle(float2 coord, float2 halfSize, float r) {\n" +
            "    float2 inner = halfSize - float2(r);\n" +
            "    float2 cornerCoord = abs(coord) - inner;\n" +
            "    if (cornerCoord.x >= 0.0 && cornerCoord.y >= 0.0) {\n" +
            "        return sign(coord) * normalize(cornerCoord);\n" +
            "    } else {\n" +
            "        return sign(coord) * ((-cornerCoord.x < -cornerCoord.y) ? float2(1.0, 0.0) : float2(0.0, 1.0));\n" +
            "    }\n" +
            "}\n" +
            "float circleMap(float x) {\n" +
            "    return 1.0 - sqrt(1.0 - x * x);\n" +
            "}\n";

    private static final String refractionFn =
            "half4 refractionColor(float2 coord, float2 size, float cornerRadius, float eccentricFactor, float height, float amount) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = coord - halfSize;\n" +
            "    float sd = sdRoundedRectangle(centeredCoord, halfSize, cornerRadius);\n" +
            "    sd = min(sd, 0.0);\n" +
            "    if (sd <= 0.0 && -sd <= height) {\n" +
            "        float maxGradRadius = max(min(halfSize.x, halfSize.y), cornerRadius);\n" +
            "        float gradRadius = min(cornerRadius * 1.5, maxGradRadius);\n" +
            "        float2 normal = gradSdRoundedRectangle(centeredCoord, halfSize, gradRadius);\n" +
            "        float refractedDistance = circleMap(1.0 - -sd / height) * amount;\n" +
            "        float2 refractedDirection = normalize(normal + eccentricFactor * normalize(centeredCoord));\n" +
            "        float2 refractedCoord = coord + refractedDistance * refractedDirection;\n" +
            "        if (refractedCoord.x < 0.0 || refractedCoord.x >= size.x || refractedCoord.y < 0.0 || refractedCoord.y >= size.y) {\n" +
            "            return half4(0.0, 0.0, 0.0, 1.0);\n" +
            "        }\n" +
            "        return image.eval(refractedCoord);\n" +
            "    } else {\n" +
            "        return image.eval(coord);\n" +
            "    }\n" +
            "}\n";

    static final String refraction =
            "uniform shader image;\n" +
            "uniform float2 size;\n" +
            "uniform float cornerRadius;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float eccentricFactor;\n" +
            rgbToY + sdUtils + refractionFn +
            "half4 main(float2 coord) {\n" +
            "    half4 color = refractionColor(coord, size, cornerRadius, eccentricFactor, refractionHeight, refractionAmount);\n" +
            "    return color;\n" +
            "}\n";

    static final String material =
            "uniform shader image;\n" +
            "uniform float contrast;\n" +
            "uniform float whitePoint;\n" +
            "uniform float chromaMultiplier;\n" +
            rgbToY +
            "half4 saturateColor(half4 color, float amount) {\n" +
            "    half3 lin = toLinearSrgb(color.rgb);\n" +
            "    float y = dot(lin, rgbToY);\n" +
            "    half3 gray = half3(y);\n" +
            "    half3 sat = fromLinearSrgb(mix(gray, lin, amount));\n" +
            "    return half4(sat, color.a);\n" +
            "}\n" +
            "half4 main(float2 coord) {\n" +
            "    half4 color = image.eval(coord);\n" +
            "    color = saturateColor(color, chromaMultiplier);\n" +
            "    float3 target = (whitePoint > 0.0) ? float3(1.0) : float3(0.0);\n" +
            "    color.rgb = mix(color.rgb, target, abs(whitePoint));\n" +
            "    color.rgb = (color.rgb - 0.5) * (1.0 + contrast) + 0.5;\n" +
            "    return color;\n" +
            "}\n";
}
