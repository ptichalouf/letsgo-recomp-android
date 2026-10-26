package com.letsgorecomp;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

// The phone's motion sensor aims the Poke Ball throws, like the Switch's own gyro in handheld mode (opt-in: the
// launcher's "gyro" setting, off by default). The game's six-axis sensor is emulated in runtime/hle/input.c; this
// sends it the phone's turn since the last sample (nativeGyroMotion), the way the mouse does on a computer.
//
// Sensor: the game rotation vector (gyroscope + accelerometer fusion, no magnetometer: no permission, no drift from
// a nearby magnet), else the rotation vector, else the bare gyroscope. Listened to at the fastest rate, on its own
// thread, only while the game reads its motion sensor (nativeGyroWanted, checked every 100 ms) and the activity is
// in front.
//
// Turn, in the screen's frame (x right, y up, z towards the player; the game runs in landscape, either way round):
//   yaw   = rotation about the vertical (gravity): turning on the spot aims left / right whatever the tilt;
//   pitch = change of elevation of the direction the back of the phone points at: tilting the top towards you aims up.
// Diagnosis (adb shell setprop): debug.larecomp.gyro_invert 1 = yaw, 2 = pitch, 3 = both inverted;
// debug.larecomp.gyro_sens <factor> (default 1: one degree of the phone = one degree of the console);
// debug.larecomp.gyro_force 1 = listen even when the game does not read the sensor (rate / rest-noise measurement).
final class GyroAim implements SensorEventListener {
    static final String TAG = "lasrecomp";
    static native void nativeGyroMotion(double yaw, double pitch);
    static native boolean nativeGyroWanted();

    static boolean enabled(Context c) {
        return c.getSharedPreferences("letsgo", Context.MODE_PRIVATE).getBoolean("gyro", false);
    }

    private final Activity act;
    private final SensorManager sm;
    private final Sensor sensor;
    private final boolean fused;  // a rotation vector (an orientation), not the bare gyroscope (a rate)
    private final String kind;
    private HandlerThread thread;
    private Handler handler;
    private boolean listening;
    private volatile int rotation = Surface.ROTATION_90;
    private double sens = 1;
    private int invert;
    private boolean force;

    // the previous sample: the screen frame in world coordinates (row-major 3x3), its timestamp
    private final double[] prev = new double[9], cur = new double[9];
    private boolean havePrev;
    private int prevRotation = -1;
    private long prevNs;
    // statistics of the current window (logged every 5 s while listening, and when it stops)
    private long statStartNs, statCount, statMaxGapNs, statFirstNs;
    private double statYaw, statPitch, statAbsYaw, statAbsPitch;
    private long statDropped;

    GyroAim(Activity a) {
        act = a;
        sm = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        String k = "game_rotation_vector";
        if (s == null && sm != null) { s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR); k = "rotation_vector"; }
        boolean f = s != null;
        if (s == null && sm != null) { s = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE); k = "gyroscope"; }
        sensor = s;
        fused = f;
        kind = s == null ? "none" : k;
        if (s == null) Log.w(TAG, "[gyro] no motion sensor on this phone: gyro aiming off");
        else Log.i(TAG, "[gyro] sensor: " + kind + " (" + s.getName() + ", " + s.getVendor() + "), min delay "
                + s.getMinDelay() + " us");
    }

    void resume() {
        if (sensor == null || thread != null) return;
        sens = prop("debug.larecomp.gyro_sens", 1);
        if (sens == 0) sens = 1;
        invert = (int) prop("debug.larecomp.gyro_invert", 0);
        force = prop("debug.larecomp.gyro_force", 0) != 0;
        if (sens != 1 || invert != 0 || force)
            Log.i(TAG, "[gyro] diagnosis: sens " + sens + ", invert " + invert + ", force " + force);
        thread = new HandlerThread("gyro-aim");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(poll);
    }

    void pause() {
        if (thread == null) return;
        final HandlerThread t = thread;
        handler.post(new Runnable() { public void run() { stopListening(); t.quit(); } });
        thread = null;
        handler = null;
    }

    private final Runnable poll = new Runnable() {
        public void run() {
            Handler h = handler;
            if (h == null) return;
            rotation = displayRotation();
            boolean want = force;
            if (!want) {
                try { want = nativeGyroWanted(); } catch (UnsatisfiedLinkError e) { want = false; }
            }
            if (want && !listening) {
                havePrev = false;
                listening = sm.registerListener(GyroAim.this, sensor, SensorManager.SENSOR_DELAY_FASTEST, h);
                statsReset(System.nanoTime());
                Log.i(TAG, "[gyro] listening: " + listening + " (" + kind + ", screen rotation " + rotation + ")");
            } else if (!want && listening) {
                stopListening();
            }
            h.postDelayed(this, 100);
        }
    };

    private void stopListening() {
        if (!listening) return;
        sm.unregisterListener(this);
        listening = false;
        statsLog("stopped");
    }

    private int displayRotation() {
        try {
            android.view.Display d = android.os.Build.VERSION.SDK_INT >= 30 ? act.getDisplay()
                    : act.getWindowManager().getDefaultDisplay();
            return d != null ? d.getRotation() : rotation;
        } catch (Exception e) {
            return rotation;
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) {}

    @Override public void onSensorChanged(SensorEvent e) {
        int rot = rotation;
        long ns = e.timestamp;
        double yaw, pitch;
        if (fused) {
            screenFrame(e.values, rot, cur);
            if (!havePrev || rot != prevRotation) {
                System.arraycopy(cur, 0, prev, 0, 9);
                havePrev = true; prevRotation = rot; prevNs = ns;
                return;
            }
            double[] d = turn(prev, cur);
            yaw = d[0]; pitch = d[1];
            System.arraycopy(cur, 0, prev, 0, 9);
        } else {
            // bare gyroscope (rad/s, the phone's own axes): the screen's up axis = yaw, its right axis = pitch
            if (!havePrev || rot != prevRotation) { havePrev = true; prevRotation = rot; prevNs = ns; return; }
            double dt = (ns - prevNs) / 1e9;
            if (dt <= 0 || dt > 0.1) { prevNs = ns; return; }
            double[] sx = new double[3], sy = new double[3];
            screenAxes(rot, sx, sy);
            yaw = (sy[0] * e.values[0] + sy[1] * e.values[1] + sy[2] * e.values[2]) * dt;
            pitch = (sx[0] * e.values[0] + sx[1] * e.values[1] + sx[2] * e.values[2]) * dt;
        }
        long gap = ns - prevNs;
        prevNs = ns;
        // (a jump of more than ~15 degrees in one sample is a sensor reset, not the player's hand)
        if (Math.abs(yaw) > 0.25 || Math.abs(pitch) > 0.25 || Double.isNaN(yaw) || Double.isNaN(pitch)) {
            statDropped++;
            return;
        }
        if ((invert & 1) != 0) yaw = -yaw;
        if ((invert & 2) != 0) pitch = -pitch;
        yaw *= sens; pitch *= sens;
        nativeGyroMotion(yaw, pitch);
        if (statCount == 0) statFirstNs = ns;
        else if (gap > statMaxGapNs) statMaxGapNs = gap;
        statCount++;
        statYaw += yaw; statPitch += pitch;
        statAbsYaw += Math.abs(yaw); statAbsPitch += Math.abs(pitch);
        long now = System.nanoTime();
        if (now - statStartNs > 5000000000L) { statsLog("listening"); statsReset(now); }
    }

    private void statsReset(long now) {
        statStartNs = now;
        statCount = statMaxGapNs = statDropped = 0;
        statYaw = statPitch = statAbsYaw = statAbsPitch = 0;
    }

    private void statsLog(String what) {
        double secs = (System.nanoTime() - statStartNs) / 1e9;
        double r2d = 180 / Math.PI;
        Log.i(TAG, String.format(java.util.Locale.ROOT,
                "[gyro] %s: %d samples in %.1f s = %.0f Hz (largest gap %.1f ms, %d dropped); turn yaw %+.3f pitch %+.3f deg, travelled %.3f / %.3f deg",
                what, statCount, secs, statCount / Math.max(secs, 1e-3), statMaxGapNs / 1e6, statDropped,
                statYaw * r2d, statPitch * r2d, statAbsYaw * r2d, statAbsPitch * r2d));
    }

    private static double prop(String key, double def) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            String v = (String) c.getMethod("get", String.class).invoke(null, key);
            return v == null || v.isEmpty() ? def : Double.parseDouble(v);
        } catch (Exception e) {
            return def;
        }
    }

    // ---- the math (static, no Android: tested on the build host, apk/test/GyroAimTest.java) ----

    // the screen's right (sx) and up (sy) axes in the phone's own sensor axes, for a display rotation
    static void screenAxes(int rot, double[] sx, double[] sy) {
        switch (rot) {
            case Surface.ROTATION_90:  set(sx, 0, 1, 0);  set(sy, -1, 0, 0); break;
            case Surface.ROTATION_180: set(sx, -1, 0, 0); set(sy, 0, -1, 0); break;
            case Surface.ROTATION_270: set(sx, 0, -1, 0); set(sy, 1, 0, 0);  break;
            default:                   set(sx, 1, 0, 0);  set(sy, 0, 1, 0);  break;
        }
    }
    private static void set(double[] v, double x, double y, double z) { v[0] = x; v[1] = y; v[2] = z; }

    // rotation vector (x, y, z[, w]) -> the screen frame in world coordinates: columns = the screen's x, y, z axes
    // (same formula as SensorManager.getRotationMatrixFromVector, which maps the phone's axes to the world's)
    static void screenFrame(float[] v, int rot, double[] out) {
        double q1 = v[0], q2 = v[1], q3 = v[2];
        double q0 = v.length >= 4 ? v[3] : 1 - q1 * q1 - q2 * q2 - q3 * q3;
        if (v.length < 4) q0 = q0 > 0 ? Math.sqrt(q0) : 0;
        double sq1 = 2 * q1 * q1, sq2 = 2 * q2 * q2, sq3 = 2 * q3 * q3;
        double q12 = 2 * q1 * q2, q30 = 2 * q3 * q0, q13 = 2 * q1 * q3, q20 = 2 * q2 * q0, q23 = 2 * q2 * q3, q10 = 2 * q1 * q0;
        double[] r = {
            1 - sq2 - sq3, q12 - q30, q13 + q20,
            q12 + q30, 1 - sq1 - sq3, q23 - q10,
            q13 - q20, q23 + q10, 1 - sq1 - sq2,
        };
        double[] sx = new double[3], sy = new double[3];
        screenAxes(rot, sx, sy);
        for (int i = 0; i < 3; i++) {
            out[i * 3 + 0] = r[i * 3] * sx[0] + r[i * 3 + 1] * sx[1] + r[i * 3 + 2] * sx[2];
            out[i * 3 + 1] = r[i * 3] * sy[0] + r[i * 3 + 1] * sy[1] + r[i * 3 + 2] * sy[2];
            out[i * 3 + 2] = r[i * 3 + 2];  // the screen's z is the phone's z
        }
    }

    // the turn from screen frame a to b: {yaw (about the vertical, > 0 = to the left), pitch (> 0 = aims up)}
    static double[] turn(double[] a, double[] b) {
        // world-frame rotation D = B A^T; its rotation vector's vertical part is the yaw
        double[] d = new double[9];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                d[i * 3 + j] = b[i * 3] * a[j * 3] + b[i * 3 + 1] * a[j * 3 + 1] + b[i * 3 + 2] * a[j * 3 + 2];
        double wx = (d[7] - d[5]) / 2, wy = (d[2] - d[6]) / 2, wz = (d[3] - d[1]) / 2;
        double s = Math.sqrt(wx * wx + wy * wy + wz * wz), c = (d[0] + d[4] + d[8] - 1) / 2;
        double angle = Math.atan2(s, c);
        double yaw = s > 1e-12 ? wz / s * angle : wz;
        // pitch: the elevation of the direction the back of the phone points at (-z), its change
        double pitch = elevation(b) - elevation(a);
        return new double[] {yaw, pitch};
    }
    static double elevation(double[] m) {
        double z = -m[8];
        return Math.asin(z > 1 ? 1 : z < -1 ? -1 : z);
    }
}
