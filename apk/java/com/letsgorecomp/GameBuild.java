package com.letsgorecomp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Palier 1: compile the chosen, already extracted folder; no packages/keys accepted. */
public final class GameBuild extends Activity {
    static { System.loadLibrary("gamebuilder"); }
    static native int execute(byte[] executable, byte[] script, byte[] root, byte[] work, byte[] lib);
    static byte[] utf8(String s) {
        if (s.indexOf('\u0000') >= 0) throw new IllegalArgumentException("NUL path");
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.util.Arrays.copyOf(b, b.length + 1);
    }
    static native void begin();
    static native void cancel();
    static File work(Context c) { return new File(c.getFilesDir(), "gamebuild"); }
    static JSONObject json(File p) throws Exception {
        try (InputStream in = new FileInputStream(p); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) { if (b.size() + n > 1048576) throw new IOException("manifest too large"); b.write(buf, 0, n); }
            return new JSONObject(b.toString("UTF-8"));
        }
    }
    static JSONObject config(Context c) throws Exception {
        try (InputStream in = c.getAssets().open("buildkit/config.json"); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
            return new JSONObject(b.toString("UTF-8"));
        }
    }
    static String hash(File p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(p)) {
            byte[] b = new byte[1048576]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n);
        }
        StringBuilder s = new StringBuilder(); for (byte b : md.digest()) s.append(String.format("%02x", b & 255));
        return s.toString();
    }
    /** Validate immediately before launch; only app-private output is loadable. */
    static String library(Context c, String root) {
        try {
            JSONObject r = json(new File(work(c), "ready.json"));
            JSONObject cfg = config(c);
            File lib = new File(r.getString("lib"));
            if (!root.equals(r.getString("root")) || !cfg.getString("recipe").equals(r.getString("recipe"))
                    || !cfg.getString("gamelib_id").equals(r.getString("gamelib_id"))
                    || !lib.getCanonicalPath().startsWith(work(c).getCanonicalPath() + "/")
                    || !hash(lib).equals(r.getString("sha256"))) return null;
            for (String m : new String[]{"main", "sdk", "subsdk0", "subsdk1"})
                if (!hash(new File(root, "extracted/exefs/" + m)).equals(r.getJSONObject("inputs").getString(m))) return null;
            return lib.getAbsolutePath();
        } catch (Exception e) { return null; }
    }
    static boolean hasReady(Context c, String root) {
        try {
            JSONObject r = json(new File(work(c), "ready.json"));
            JSONObject cfg = config(c);
            return root != null && root.equals(r.getString("root"))
                && cfg.getString("recipe").equals(r.getString("recipe")) && new File(r.getString("lib")).isFile();
        }
        catch (Exception e) { return false; }
    }
    static void unpack(Context c, String path, File dst) throws Exception {
        String[] names = c.getAssets().list(path);
        if (names.length > 0) {
            if (!dst.isDirectory() && !dst.mkdirs()) throw new IOException("kit directory");
            for (String n : names) unpack(c, path + "/" + n, new File(dst, n));
        } else {
            dst.getParentFile().mkdirs();
            File tmp = new File(dst + ".part");
            try (InputStream in = c.getAssets().open(path); OutputStream out = new FileOutputStream(tmp)) {
                byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
            android.system.Os.rename(tmp.toString(), dst.toString());
        }
    }
    private final android.os.Handler handler = new android.os.Handler();
    private TextView status;
    private final Runnable refresh = new Runnable() { public void run() {
        try {
            JSONObject s = json(new File(work(GameBuild.this), "state.json"));
            status.setText(s.optString("state") + "  " + s.optInt("done") + "/" + s.optInt("total")
                + " · " + s.optDouble("elapsed_s") + " s\n" + s.optString("error"));
        } catch (Exception e) { status.setText("Dossier déjà extrait requis. Aucun jeu n’est inclus.\nLa reprise conserve les objets vérifiés."); }
        handler.postDelayed(this, 1000);
    }};
    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout v = new LinearLayout(this); v.setOrientation(1); v.setPadding(24,24,24,24);
        status = new TextView(this); status.setTextSize(18); v.addView(status);
        Button run = new Button(this); run.setText("Importer mon jeu — préparer / reprendre"); v.addView(run);
        run.setOnClickListener(x -> {
            String root = LetsGoLauncherActivity.gameDir(this);
            if (root == null || !GameFolder.inspect(new File(root)).playable()) {
                status.setText("Choisissez d’abord un dossier extrait complet dans le lanceur."); return;
            }
            Intent i = new Intent(this, GameBuildService.class).putExtra("root", root);
            startForegroundService(i);
        });
        Button stop = new Button(this); stop.setText("Interrompre (reprise possible)"); v.addView(stop);
        stop.setOnClickListener(x -> stopService(new Intent(this, GameBuildService.class)));
        Button play = new Button(this); play.setText("Jouer"); v.addView(play);
        play.setOnClickListener(x -> {
            String root = LetsGoLauncherActivity.gameDir(this);
            if (root != null && library(this, root) != null) startActivity(new Intent(this, LetsGoActivity.class));
            else status.setText("Compilation incomplète ou données modifiées : reprenez la préparation.");
        });
        setContentView(v);
    }
    protected void onResume() { super.onResume(); handler.post(refresh); }
    protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
