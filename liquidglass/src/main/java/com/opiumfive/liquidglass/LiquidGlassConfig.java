package com.opiumfive.liquidglass;

import android.app.ActivityManager;
import android.content.Context;
import android.opengl.GLES20;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.RandomAccessFile;

public class LiquidGlassConfig {
    public static volatile Tier DEVICE_TIER = Tier.MID;
    public static int WIDTH = 100;
    public static int HEIGHT = 100;
    static int MAX_LEVELS = 6;
    static int MIN_SCALE_DENOM = 4;
    static float MIN_SIGMA_TO_BLUR = 5f;
    static float CAPTURE_SCALE = 0.5f;
    static boolean USE_BITMAP_565 = false;
    static int PC_CAPTURE_INTERVAL_MS = 8;
    static boolean CAN_USE_PBO = false;
    public static boolean PIXEL_COPY = false;
    public static boolean SURFACE_VIEW = false;
    static volatile float CORNER_RADIUS_PX = 0;
    static volatile float ECCENTRIC_FACTOR = 1f;
    static volatile float REFRACTION_HEIGHT_PX = 0;
    static volatile float REFRACTION_AMOUNT_PX = 0;
    static volatile float CONTRAST = 0.1f;
    static volatile float WHITE_POINT = 0.05f;
    static volatile float CHROMA_MULTIPLIER = 2.5f;
    static volatile float SIGMA = -1f;

    public static void configure(@NonNull Context ctx) {
        configure(ctx, null);
    }

    public static void configure(@NonNull Context ctx, @Nullable Overrides overrides) {

        DEVICE_TIER = detectTier(ctx);

        float forceSigma = -1;

        switch (DEVICE_TIER) {
            case HIGH: {
                MAX_LEVELS = 6;
                MIN_SCALE_DENOM = 4;
                MIN_SIGMA_TO_BLUR = 5f;
                CAPTURE_SCALE = 0.50f;
                USE_BITMAP_565 = false;
                PC_CAPTURE_INTERVAL_MS = 8;
                CAN_USE_PBO = true;
                PIXEL_COPY = false;
                SURFACE_VIEW = false;
                break;
            }
            case MID: {
                MAX_LEVELS = 5;
                MIN_SCALE_DENOM = 4;
                MIN_SIGMA_TO_BLUR = 5f;
                CAPTURE_SCALE = 0.40f;
                USE_BITMAP_565 = false;
                PC_CAPTURE_INTERVAL_MS = 12;
                CAN_USE_PBO = true;
                PIXEL_COPY = false;
                SURFACE_VIEW = false;
                break;
            }
            case LOW:
            default: {
                MAX_LEVELS = 4;
                MIN_SCALE_DENOM = 4;
                MIN_SIGMA_TO_BLUR = 5f;
                CAPTURE_SCALE = 0.33f;
                USE_BITMAP_565 = false;
                PC_CAPTURE_INTERVAL_MS = 16;
                CAN_USE_PBO = false;
                PIXEL_COPY = false;
                SURFACE_VIEW = false;
                forceSigma = 7f;
            }
        }

        CORNER_RADIUS_PX = 0;
        ECCENTRIC_FACTOR = 1.0f;
        REFRACTION_HEIGHT_PX = 0;
        REFRACTION_AMOUNT_PX = 0;
        CONTRAST = 0.10f;
        WHITE_POINT = 0.05f;
        CHROMA_MULTIPLIER = 2.50f;
        SIGMA = -1f;

        if (overrides != null) overrides.apply();

        if (SIGMA < forceSigma) {
            SIGMA = forceSigma;
        }
    }

    private static final int PERFORMANCE_CLASS_LOW = 0;
    private static final int PERFORMANCE_CLASS_AVERAGE = 1;
    private static final int PERFORMANCE_CLASS_HIGH = 2;

    private static Tier detectTier(Context ctx) {
        if (isEmulator()) return Tier.HIGH;
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null && am.isLowRamDevice()) return Tier.LOW;
        try {
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            if (renderer != null) {
                if (renderer.matches(".*(Adreno \\(TM\\) (7|6)\\d\\d|Mali-G7\\d|Immortalis).*"))
                    return Tier.HIGH;
                if (renderer.matches(".*(Adreno \\(TM\\) 3\\d\\d|Mali-4\\d|PowerVR SGX).*"))
                    return Tier.LOW;
            }
        } catch (Exception ignored) {
        }

        int telegramPC = scoreWithTelegramRules(ctx);
        if (telegramPC == PERFORMANCE_CLASS_LOW) return Tier.LOW;
        if (telegramPC == PERFORMANCE_CLASS_HIGH) return Tier.HIGH;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) return Tier.LOW;
        return Tier.MID;
    }

    private static boolean isEmulator() {
        return (Build.FINGERPRINT != null &&
                (Build.FINGERPRINT.startsWith("generic")
                        || Build.FINGERPRINT.contains("emulator")
                        || Build.FINGERPRINT.contains("emu64")
                        || Build.FINGERPRINT.startsWith("unknown")))
                || (Build.MODEL != null && Build.MODEL.contains("Emulator"))
                || (Build.MODEL != null && Build.MODEL.contains("Android SDK built for"))
                || (Build.MANUFACTURER != null && Build.MANUFACTURER.contains("Genymotion"))
                || (Build.BRAND != null && Build.BRAND.startsWith("generic") &&
                Build.DEVICE != null && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    private static int scoreWithTelegramRules(Context ctx) {
        final int androidVersion = Build.VERSION.SDK_INT;
        final int cpuCount = Runtime.getRuntime().availableProcessors();

        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        final int memoryClass = am != null ? am.getMemoryClass() : 128;

        int totalFreq = 0, coresMeasured = 0;
        for (int i = 0; i < cpuCount; i++) {
            try (RandomAccessFile rf = new RandomAccessFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq", "r")) {
                String line = rf.readLine();
                if (line != null) {
                    totalFreq += Integer.parseInt(line.trim()) / 1000;
                    coresMeasured++;
                }
            } catch (Throwable ignore) {
            }
        }
        final int maxCpuFreqMHz = coresMeasured == 0 ? -1 : (int) Math.ceil(totalFreq / (float) coresMeasured);

        long totalRam = -1;
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            totalRam = mi.totalMem;
        } catch (Throwable ignore) {
        }

        boolean isLow =
                androidVersion < 21 ||
                        cpuCount <= 2 ||
                        memoryClass <= 100 ||
                        (cpuCount <= 4 && maxCpuFreqMHz != -1 && maxCpuFreqMHz <= 1250) ||
                        (cpuCount <= 4 && maxCpuFreqMHz <= 1600 && memoryClass <= 128 && androidVersion <= 21) ||
                        (cpuCount <= 4 && maxCpuFreqMHz <= 1300 && memoryClass <= 128 && androidVersion <= 24) ||
                        (totalRam != -1 && totalRam < 2L * 1024L * 1024L * 1024L);

        if (isLow) return PERFORMANCE_CLASS_LOW;

        boolean isAverage =
                cpuCount < 8 ||
                        memoryClass <= 160 ||
                        (maxCpuFreqMHz != -1 && maxCpuFreqMHz <= 2055) ||
                        (maxCpuFreqMHz == -1 && cpuCount == 8 && androidVersion <= 23);

        if (isAverage) return PERFORMANCE_CLASS_AVERAGE;

        return PERFORMANCE_CLASS_HIGH;
    }

    public enum Tier {HIGH, MID, LOW}

    public static final class Overrides {
        Integer maxLevels;
        Integer minScaleDenom;
        Float minSigmaToBlur;
        Float captureScale;
        Boolean useBitmap565;
        Integer pcCaptureIntervalMs;
        Boolean canUsePbo;
        Boolean pixelCopy;
        Boolean surfaceView;

        Float cornerRadiusPx;
        Float eccentricFactor;
        Float refractionHeightPx;
        Float refractionAmountPx;
        Float contrast;
        Float whitePoint;
        Float chromaMultiplier;
        Float sigma;

        Integer width, height;

        public Overrides maxLevels(int v) {
            maxLevels = v;
            return this;
        }

        public Overrides minScaleDenom(int v) {
            minScaleDenom = v;
            return this;
        }

        public Overrides minSigmaToBlur(float v) {
            minSigmaToBlur = v;
            return this;
        }

        public Overrides captureScale(float v) {
            captureScale = v;
            return this;
        }

        /**
         * 565 with pixel copy doesn't work on some gpus
         * using 565 somehow makes low performance on low devices
         */
        public Overrides useBitmap565(boolean v) {
            useBitmap565 = v;
            return this;
        }

        public Overrides pcCaptureIntervalMs(int v) {
            pcCaptureIntervalMs = v;
            return this;
        }

        /**
         * can be slower on low devices
         */
        public Overrides canUsePbo(boolean v) {
            canUsePbo = v;
            return this;
        }

        /**
         * Use only if you truly need pixel copy, can be ineffective and laggy for some drivers
         */
        public Overrides pixelCopy(boolean v) {
            pixelCopy = v;
            return this;
        }

        /**
         * Use only if you truly need surfaceview, placing views on top of it would be not effective
         */
        public Overrides surfaceView(boolean v) {
            surfaceView = v;
            return this;
        }

        public Overrides cornerRadiusPx(float v) {
            cornerRadiusPx = v;
            return this;
        }

        public Overrides eccentricFactor(float v) {
            eccentricFactor = v;
            return this;
        }

        public Overrides refractionHeightPx(float v) {
            refractionHeightPx = v;
            return this;
        }

        public Overrides refractionAmountPx(float v) {
            refractionAmountPx = v;
            return this;
        }

        public Overrides contrast(float v) {
            contrast = v;
            return this;
        }

        public Overrides whitePoint(float v) {
            whitePoint = v;
            return this;
        }

        public Overrides chromaMultiplier(float v) {
            chromaMultiplier = v;
            return this;
        }

        public Overrides sigma(float v) {
            sigma = v;
            return this;
        }

        public Overrides size(int w, int h) {
            width = w;
            height = h;
            return this;
        }

        void apply() {
            if (maxLevels != null) MAX_LEVELS = maxLevels;
            if (minScaleDenom != null) MIN_SCALE_DENOM = minScaleDenom;
            if (minSigmaToBlur != null) MIN_SIGMA_TO_BLUR = minSigmaToBlur;
            if (captureScale != null) CAPTURE_SCALE = captureScale;
            if (useBitmap565 != null) USE_BITMAP_565 = useBitmap565;
            if (pcCaptureIntervalMs != null) PC_CAPTURE_INTERVAL_MS = pcCaptureIntervalMs;
            if (canUsePbo != null) CAN_USE_PBO = canUsePbo;
            if (pixelCopy != null) PIXEL_COPY = pixelCopy;
            if (surfaceView != null) SURFACE_VIEW = surfaceView;

            if (cornerRadiusPx != null) CORNER_RADIUS_PX = cornerRadiusPx;
            if (eccentricFactor != null) ECCENTRIC_FACTOR = eccentricFactor;
            if (refractionHeightPx != null) REFRACTION_HEIGHT_PX = refractionHeightPx;
            if (refractionAmountPx != null) REFRACTION_AMOUNT_PX = refractionAmountPx;
            if (contrast != null) CONTRAST = contrast;
            if (whitePoint != null) WHITE_POINT = whitePoint;
            if (chromaMultiplier != null) CHROMA_MULTIPLIER = chromaMultiplier;
            if (sigma != null) SIGMA = sigma;
            if (width != null) WIDTH = width;
            if (height != null) HEIGHT = height;
        }
    }
}
