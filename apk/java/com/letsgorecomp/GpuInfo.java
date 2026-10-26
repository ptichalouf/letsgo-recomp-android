package com.letsgorecomp;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the driver choice depends on: the Adreno model (kernel name, else the SoC), its family, Android's API level,
 * Samsung's 8 Gen 2 case, and the system driver's version (Adreno "V@0800.64": the blob, 800).
 */
final class GpuInfo {
    enum Family { A8XX, A7XX_LOW, A7XX, A6XX, ADRENO_UNKNOWN, NOT_ADRENO }

    final boolean adreno;
    final int model;            // 830, 740, 730... 0 when unknown
    final String modelSource;   // "kernel", "soc" or ""
    final Family family;
    final boolean oneUi8Gen2;   // Samsung + Adreno 740: its drivers want the UBWC hint build
    final String kgslName;      // /sys/class/kgsl/kgsl-3d0/gpu_model, e.g. "Adreno830v2"
    final String soc;           // Build.SOC_MODEL (API 31+) or the board platform
    final int sdk;
    final String systemDriver;  // GL_VERSION of the system driver, e.g. "OpenGL ES 3.2 V@0800.64 ..." ("" if unknown)
    final int blob;             // 800 for V@0800.64; 0 when unknown

    GpuInfo(boolean adreno, int model, String modelSource, Family family, boolean oneUi8Gen2,
                    String kgslName, String soc, int sdk, String systemDriver, int blob) {
        this.adreno = adreno;
        this.model = model;
        this.modelSource = modelSource;
        this.family = family;
        this.oneUi8Gen2 = oneUi8Gen2;
        this.kgslName = kgslName;
        this.soc = soc;
        this.sdk = sdk;
        this.systemDriver = systemDriver;
        this.blob = blob;
    }

    private static final Map<String, Integer> SOC_GPU = new HashMap<>();
    static {
        // Snapdragon part numbers and board platforms -> Adreno model (a fallback when the kernel name is unreadable).
        SOC_GPU.put("SM8750", 830); SOC_GPU.put("sun", 830);
        SOC_GPU.put("SM8650", 750); SOC_GPU.put("pineapple", 750);
        SOC_GPU.put("SM8550", 740); SOC_GPU.put("kalama", 740);
        SOC_GPU.put("SM8475", 730); SOC_GPU.put("SM8450", 730); SOC_GPU.put("taro", 730); SOC_GPU.put("cape", 730);
        SOC_GPU.put("SM8350", 660); SOC_GPU.put("lahaina", 660);
        SOC_GPU.put("SM8250", 650); SOC_GPU.put("kona", 650);
        SOC_GPU.put("SM8150", 640); SOC_GPU.put("msmnile", 640);
    }

    /** Detects the phone. Creates a throwaway EGL context to read the system driver's version: not on the UI thread. */
    static GpuInfo detect() {
        String kgsl = readLine("/sys/class/kgsl/kgsl-3d0/gpu_model");
        boolean adreno = new File("/sys/class/kgsl/kgsl-3d0").exists()
                || new File("/vendor/lib64/hw/vulkan.adreno.so").exists();
        String soc = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "";
        if (soc == null || soc.isEmpty() || "unknown".equalsIgnoreCase(soc)) soc = Build.BOARD;
        String glVersion = systemGlVersion();
        int model = adrenoNumber(kgsl);
        String source = model > 0 ? "kernel" : "";
        if (model == 0) {
            Integer m = SOC_GPU.get(soc);
            if (m == null) m = SOC_GPU.get(Build.BOARD);
            if (m == null) m = SOC_GPU.get(Build.HARDWARE);
            if (m != null && adreno) { model = m; source = "soc"; }
        }
        if (!adreno && glVersion.contains("V@")) adreno = true;  // sysfs hidden by SELinux, but it is an Adreno blob
        return new GpuInfo(adreno, model, source, familyOf(adreno, model),
                "samsung".equalsIgnoreCase(Build.MANUFACTURER) && model == 740,
                kgsl, soc == null ? "" : soc, Build.VERSION.SDK_INT, glVersion, blobOf(glVersion));
    }

    static Family familyOf(boolean adreno, int model) {
        if (!adreno) return Family.NOT_ADRENO;
        if (model >= 800) return Family.A8XX;
        if (model == 710 || model == 720 || model == 722) return Family.A7XX_LOW;
        if (model >= 700) return Family.A7XX;
        if (model >= 600) return Family.A6XX;
        return Family.ADRENO_UNKNOWN;
    }

    /** "Adreno830v2" / "Adreno (TM) 740" -> 830 / 740. */
    static int adrenoNumber(String s) {
        if (s == null) return 0;
        Matcher m = Pattern.compile("(?i)adreno\\s*(?:\\(TM\\)\\s*)?(\\d{3})(?!\\d)").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** "OpenGL ES 3.2 V@0800.64 (GIT@...)" -> 800. */
    static int blobOf(String gl) {
        Matcher m = Pattern.compile("V@0*(\\d+)\\.(\\d+)").matcher(gl == null ? "" : gl);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(Build.MANUFACTURER).append(' ').append(Build.MODEL);
        if (!soc.isEmpty()) sb.append(" · ").append(soc);
        if (!adreno) sb.append(" · GPU não Adreno");
        else sb.append(" · Adreno ").append(model > 0 ? String.valueOf(model) : "?").append(" (").append(family).append(')');
        sb.append(" · Android API ").append(sdk);
        Matcher m = Pattern.compile("V@[0-9.]+").matcher(systemDriver);
        if (m.find()) sb.append(" · driver do sistema ").append(m.group());
        return sb.toString();
    }

    private static String readLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l = r.readLine();
            return l == null ? "" : l.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String systemGlVersion() {
        EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
        EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        EGLSurface surf = EGL14.EGL_NO_SURFACE;
        try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            if (dpy == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return "";
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            int[] attrs = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE,
                           EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            if (!EGL14.eglChooseConfig(dpy, attrs, 0, cfg, 0, 1, n, 0) || n[0] < 1) return "";
            ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            surf = EGL14.eglCreatePbufferSurface(dpy, cfg[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1,
                                                                         EGL14.EGL_NONE}, 0);
            if (ctx == EGL14.EGL_NO_CONTEXT || surf == EGL14.EGL_NO_SURFACE
                    || !EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return "";
            String v = GLES20.glGetString(GLES20.GL_VERSION);
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        } finally {
            if (dpy != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf);
                if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx);
                // No eglTerminate: the default display is shared with the UI's own renderer in this process.
            }
        }
    }

    @Override public String toString() {
        return String.format(Locale.US, "GpuInfo{kgsl=%s model=%d(%s) family=%s oneUi8Gen2=%b soc=%s sdk=%d blob=%d gl=%s}",
                kgslName, model, modelSource, family, oneUi8Gen2, soc, sdk, blob, systemDriver);
    }
}
