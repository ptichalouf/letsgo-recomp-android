package com.letsgorecomp;

import android.os.Bundle;
import org.libsdl.app.SDLActivity;

// Entry point of the Android port: SDLActivity with our native libraries, plus an
// early native setup (before SDL starts its thread in super.onCreate) telling the
// runtime where its files live (LARECOMP_DATA_ROOT) and where the prebuilt game
// library is (LARECOMP_GAME_LIB). See runtime/platform/android.c.
public class LetsGoActivity extends SDLActivity {
    @Override
    protected String[] getLibraries() {
        return new String[] {
            "SDL3",
            "lasrecomp",
        };
    }

    private void applyFullscreen() {
        // Simplified: rely on theme (@style/Fullscreen) + SDL_SetWindowFullscreen.
        // SDLActivity handles COMMAND_CHANGE_WINDOW_STYLE via WindowInsetsController.
        // Manual calls here were conflicting with SDL's handler.
        android.util.Log.i("lasrecomp", "[fs] applyFullscreen: delegating to SDL/theme");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Txt.init(this);  // the phone's language for every word shown (res/values-en, values-fr; PT by default)
        // Force landscape: locked (not sensor) to avoid SDL resize confusion
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // Resolution scale (user-selectable, emulator-style): set before native loads
        try {
            android.content.SharedPreferences p = getSharedPreferences("letsgo", MODE_PRIVATE);
            String scale = p.getString("resolution_scale", "0.6667");
            String aspect = p.getString("aspect", "");
            if ("custom".equals(scale)) {
                // a width x height typed in the launcher ("1440x1080", "1440 x 1080", "1440:1080"): its height gives the
                // scale, its ratio the image format (runtime/nvn/output_scale.c)
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{3,5})\\s*[xX×:*]\\s*(\\d{3,5})\\s*$")
                        .matcher(p.getString("custom_resolution", "1440x1080"));  // (the launcher field's default, saved only once edited)
                if (m.matches() && Integer.parseInt(m.group(2)) >= 240 && Integer.parseInt(m.group(2)) <= 4320) {
                    scale = String.format(java.util.Locale.ROOT, "%.4f", Integer.parseInt(m.group(2)) / 1080.0);
                    aspect = m.group(1) + ":" + m.group(2);
                } else {
                    scale = "0.6667";
                    android.util.Log.w("lasrecomp", "[res] custom resolution unreadable, 720p");
                }
            }
            android.system.Os.setenv("LARECOMP_SCALE", scale, true);
            if (aspect.isEmpty()) android.system.Os.unsetenv("LARECOMP_ASPECT");
            else android.system.Os.setenv("LARECOMP_ASPECT", aspect, true);
            android.util.Log.i("lasrecomp", "[res] scale: " + scale + " format: " + (aspect.isEmpty() ? "16:9" : aspect));
        } catch (Exception e) {
            android.util.Log.w("lasrecomp", "[res] scale setup: " + e);
        }
        // DIAGNÓSTICO Teste 1: V-flip para texto (v0062) - DESLIGADO por padrão após regressão
        // Para ativar manualmente: adb shell setprop debug.larecomp.text_vflip 1 (não implementado - usar build específica)
        // Mantido desligado para não quebrar sampler2DArray/sampler2DShadow
        try {
            // NÃO setar por padrão - regressão confirmada em v0062
            // android.system.Os.setenv("LARECOMP_TEXT_VFLIP", "1", true);
            android.util.Log.i("lasrecomp", "[text] V-flip DISABLED (regression fix)");
        } catch (Exception e) {
            android.util.Log.w("lasrecomp", "[text] vflip setup: " + e);
        }
        // Sondas de diagnóstico do texto: DESLIGADAS por padrão. Elas pintam o texto de magenta,
        // forçam quads e inundam o log ([gcopy]/[gcopy-diag]), o que deixa o diálogo ilegível e
        // o jogo lento. Um único interruptor as liga todas: `adb shell setprop debug.larecomp.diag 1`
        // (ou LARECOMP_DIAG=1 no ambiente), e só vale para o próximo lançamento do jogo.
        boolean diag = false;
        try {
            String dflt = System.getenv("LARECOMP_DIAG") == null ? "0" : System.getenv("LARECOMP_DIAG");
            Object v = Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class, String.class)
                .invoke(null, "debug.larecomp.diag", dflt);
            diag = "1".equals(String.valueOf(v).trim());
        } catch (Throwable ignored) { }
        String[] probes = {"LARECOMP_DUMP_FONT_GLSL", "LARECOMP_TEXT_DIAG", "LARECOMP_VS_RANGE_DIAG",
                           "LARECOMP_GCOPY_DIAG"};
        for (String k : probes) {
            try {
                if (diag) android.system.Os.setenv(k, "1", true);
                else android.system.Os.unsetenv(k);
            } catch (Exception e) {
                android.util.Log.w("lasrecomp", "[diag] " + k + ": " + e);
            }
        }
        android.util.Log.i("lasrecomp", "[diag] text diagnostics " + (diag ? "ON" : "OFF")
                + " (debug.larecomp.diag=" + (diag ? "1" : "0") + ")");
        // v0088: VS_DIAG DESATIVADO — o quad forçado da v0087 mascararia o fix
        // real (A0_FIX em exec.c). O código VS_DIAG permanece no runtime, gated
        // pela env, mas sem ela setada fica inerte. TEXT_DIAG continua ativo:
        // se o A0_FIX funcionar, o texto aparece como glifos magenta reais nas
        // posições do diálogo (não o blob centralizado da v0087).
        // Custom GPU driver (Turnip via adrenotools): set the env before the native
        // runtime loads, so load_gipa() in device.c picks it up. Null = system driver.
        // The launcher and the game share one process, and the environment outlives an
        // Activity: the variable must be cleared when the system driver is chosen, or a
        // driver selected for an earlier launch keeps running while the launcher shows
        // "system driver". A selected driver whose .so is missing is still passed on:
        // the runtime then stops with a message instead of drawing with another driver.
        // A driver that has not yet started the game on this phone goes on trial here, before the runtime loads:
        // the runtime marks it .started after its first frames, or .start_failed when it refuses it, and the
        // launcher rolls back a trial that ended without .started (GpuDrivers.checkTrial).
        running = true;
        try {
            GpuDrivers.beginLaunch(this);
            GpuDrivers.Driver drv = GpuDrivers.selected(this);
            if (drv != null) {
                java.io.File so = new java.io.File(
                        new java.io.File(GpuDrivers.driversDir(this), drv.slug), drv.libraryName);
                android.system.Os.setenv("LARECOMP_GPU_DRIVER", so.getAbsolutePath(), true);
                android.system.Os.setenv("LARECOMP_NATIVE_LIB_DIR",
                        getApplicationInfo().nativeLibraryDir, true);
                android.util.Log.i("lasrecomp", "[gpu] custom driver: " + so.getAbsolutePath()
                        + (so.isFile() ? "" : " (MISSING)"));
                GpuDrivers.recordLaunch(this, so.getAbsolutePath());
            } else {
                android.system.Os.unsetenv("LARECOMP_GPU_DRIVER");
                android.util.Log.i("lasrecomp", "[gpu] system driver (no custom driver selected)");
                GpuDrivers.recordLaunch(this, null);
            }
        } catch (Exception e) {
            android.util.Log.w("lasrecomp", "[gpu] driver setup: " + e);
        }
        System.loadLibrary("lasrecomp");
        // The user-picked game folder (via the launcher), or the app's files
        // dir as a fallback. The native runtime reads LARECOMP_DATA_ROOT.
        // The same object the launcher's screen and Play button come from (LetsGoLauncherActivity.Launch).
        LetsGoLauncherActivity.Launch st = LetsGoLauncherActivity.launch(this);
        String gameDir = st.dataRoot;
        android.util.Log.i("lasrecomp", "[folder] game start: " + st.summary());
        // Copy bundled DejaVuSans.ttf to the game folder for the system font.
        // Diagnosed copy: logs success/failure, validates size, cleans up partial files.
        try {
            java.io.File fontDir = new java.io.File(gameDir, "fonts");
            fontDir.mkdirs();
            java.io.File fontFile = new java.io.File(fontDir, "DejaVuSans.ttf");
            if (fontFile.exists() && fontFile.length() > 100000) {
                android.util.Log.i("lasrecomp", "[font] bundled DejaVuSans.ttf already present (" + fontFile.length() + " bytes)");
            } else {
                // Remove partial/corrupt file before retrying.
                if (fontFile.exists()) fontFile.delete();
                java.io.InputStream in = null;
                java.io.OutputStream out = null;
                try {
                    in = getAssets().open("fonts/DejaVuSans.ttf");
                    out = new java.io.FileOutputStream(fontFile);
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
                    out.flush();
                    android.util.Log.i("lasrecomp", "[font] copied bundled DejaVuSans.ttf (" + total + " bytes) to " + fontFile.getAbsolutePath());
                    if (total < 100000) {
                        android.util.Log.w("lasrecomp", "[font] WARNING: copied font is suspiciously small (" + total + " bytes), deleting");
                        fontFile.delete();
                    }
                } finally {
                    if (in != null) try { in.close(); } catch (Exception ignored) {}
                    if (out != null) try { out.close(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            android.util.Log.e("lasrecomp", "[font] FAILED to copy bundled font: " + e.getMessage());
        }
        // Copy bundled pre-compiled SPIR-V shaders to the shader cache dir
        // (<gameDir>/cache/spirv/), so first runs don't stutter compiling.
        // Only copies files that are not already cached (runtime may have newer ones).
        try {
            String[] spirvAssets = getAssets().list("spirv");
            if (spirvAssets != null && spirvAssets.length > 0) {
                java.io.File cacheDir = new java.io.File(gameDir, "cache/spirv");
                cacheDir.mkdirs();
                int copied = 0, skipped = 0;
                for (String name : spirvAssets) {
                    java.io.File dst = new java.io.File(cacheDir, name);
                    if (dst.exists() && dst.length() > 0) { skipped++; continue; }
                    java.io.InputStream in = null;
                    java.io.OutputStream out = null;
                    try {
                        in = getAssets().open("spirv/" + name);
                        out = new java.io.FileOutputStream(dst);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.flush();
                        copied++;
                    } finally {
                        if (in != null) try { in.close(); } catch (Exception ignored) {}
                        if (out != null) try { out.close(); } catch (Exception ignored) {}
                    }
                }
                android.util.Log.i("lasrecomp", "[shader] bundled SPIR-V: " + copied + " copied, " + skipped + " already cached (" + spirvAssets.length + " in APK)");
            } else {
                android.util.Log.i("lasrecomp", "[shader] no bundled SPIR-V in APK");
            }
        } catch (Exception e) {
            android.util.Log.e("lasrecomp", "[shader] FAILED to copy bundled SPIR-V: " + e.getMessage());
        }
        nativeSetup(gameDir, getApplicationInfo().nativeLibraryDir);
        super.onCreate(savedInstanceState);
        // Apply fullscreen AFTER super.onCreate (SDLActivity resets UI visibility)
        applyFullscreen();
        // Touch controls - toggleable in launcher settings. Default: the minimal layout in the black bars
        // (TouchControls); "Layout completo" in the launcher brings back the old full one (GamepadOverlay).
        android.content.SharedPreferences prefs = getSharedPreferences("letsgo", MODE_PRIVATE);
        boolean touchEnabled = prefs.getBoolean("touch", true);
        if (touchEnabled) {
            boolean classic = "classic".equals(prefs.getString("touch_layout", "minimal"));
            android.util.Log.i("lasrecomp", "[touch] layout " + (classic ? "classic" : "minimal"));
            addContentView(classic ? new GamepadOverlay(this) : new TouchControls(this),
                new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /** The game activity is alive in this process (a driver trial is then still running, not failed). */
    static volatile boolean running;

    @Override
    protected void onDestroy() {
        running = false;
        GpuDrivers.gameClosed(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fullscreen handled by theme + SDL_SetWindowFullscreen (like Wonder).
        // No manual intervention to avoid conflicts with SDLActivity.
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // the phone's motion sensor aims the Poke Balls (launcher setting, off by default: GyroAim.java)
        if (gyro == null && GyroAim.enabled(this)) gyro = new GyroAim(this);
        if (gyro != null) gyro.resume();
    }

    private GyroAim gyro;

    @Override
    protected void onPause() {
        if (gyro != null) gyro.pause();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Fullscreen handled by theme + SDL. No manual re-apply.
    }

    // Force landscape: SDL's native code calls setOrientationBis() which overrides
    // our setRequestedOrientation(). By overriding it here, we force landscape always.
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    public static native void nativeSetup(String filesDir, String libDir);
    public static native void nativeSetButton(int button, boolean pressed);
    public static native void nativeSetStick(int stick, int x, int y);
    public static native double nativeGetFps();
    public static native double nativeGetFrameMs();
}
