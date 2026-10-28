package com.letsgorecomp;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.io.*;
import org.json.JSONObject;

/** One build per app; START_NOT_STICKY never silently restarts after a force stop. */
public final class GameBuildService extends Service {
    private static final Object BUILD_LOCK = new Object();
    private Thread worker;
    private volatile boolean stopping;
    private final Handler h = new Handler();
    private PowerManager.WakeLock wake;
    private Notification notice(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, GameBuild.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "gamebuild").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Préparation du jeu").setContentText(text).setContentIntent(open).setOngoing(true).build();
    }
    private final Runnable tick = new Runnable() { public void run() {
        try {
            JSONObject s = GameBuild.json(new File(GameBuild.work(GameBuildService.this), "state.json"));
            getSystemService(NotificationManager.class).notify(41, notice(s.optString("state") + " " + s.optInt("done") + "/" + s.optInt("total")));
        } catch (Exception ignored) { }
        h.postDelayed(this, 2000);
    }};
    public int onStartCommand(Intent i, int flags, int id) {
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("gamebuild", "Préparation locale", NotificationManager.IMPORTANCE_LOW));
        startForeground(41, notice("Préparation / reprise…"));
        if (worker != null) return START_NOT_STICKY;
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        final String root = i.getStringExtra("root");
        if (root == null) { stopSelf(); return START_NOT_STICKY; }
        getSharedPreferences("gamebuild", 0).edit().putString("root", root).commit();
        wake = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "letsgo:build");
        wake.acquire(3 * 60 * 60 * 1000L); h.post(tick);
        worker = new Thread(() -> {
            synchronized (BUILD_LOCK) {
            try {
                if (stopping) return;
                GameBuild.begin();
                File work = GameBuild.work(this); work.mkdirs();
                // Replace kit every run: APK update invalidates recipe/object hashes.
                GameBuild.unpack(this, "buildkit", new File(work, "kit"));
                if (!stopping) {
                    String lib = getApplicationInfo().nativeLibraryDir;
                    int rc = GameBuild.execute(GameBuild.utf8(lib + "/liblaspython.so"), GameBuild.utf8(new File(work, "kit/ondevice.py").toString()), GameBuild.utf8(root), GameBuild.utf8(work.toString()), GameBuild.utf8(lib));
                    android.util.Log.i("GameBuild", "builder exit=" + rc);
                    if (rc != 0 && !"failed".equals(GameBuild.json(new File(work, "state.json")).optString("state"))) {
                        try (FileWriter out = new FileWriter(new File(work, "state.json"))) {
                            out.write("{\"state\":\"interrupted\",\"error\":\"Reprendre la préparation ; les objets vérifiés sont conservés\"}");
                        }
                    }
                }
            } catch (Exception e) {
                try (FileWriter out = new FileWriter(new File(GameBuild.work(this), "state.json"))) {
                    out.write("{\"state\":\"failed\",\"error\":\"Préparation impossible ; vérifier stockage et dossier\"}");
                } catch (Exception ignored) { }
            } finally { h.post(() -> {
                h.removeCallbacks(tick); if (wake.isHeld()) wake.release();
                if (!stopping) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
            }); }
            }
        }, "gamebuild");
        worker.start();
        return START_NOT_STICKY;
    }
    public void onDestroy() {
        stopping = true; GameBuild.cancel(); h.removeCallbacks(tick);
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }
    public IBinder onBind(Intent i) { return null; }
}
