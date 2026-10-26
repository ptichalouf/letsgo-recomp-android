package com.letsgorecomp;

// Host test of GyroAim's math (no phone): javac -cp android.jar, java -cp android.jar:. com.letsgorecomp.GyroAimTest
// Poses are built in the world frame (x east, y north, z up), turned into the rotation vector a phone would report,
// and the computed turn checked against the turn applied.
public class GyroAimTest {
    static int fails;

    // rotation about a world unit axis (Rodrigues), row-major
    static double[] rot(double ax, double ay, double az, double deg) {
        double t = Math.toRadians(deg), c = Math.cos(t), s = Math.sin(t), v = 1 - c;
        return new double[] {
            c + ax * ax * v, ax * ay * v - az * s, ax * az * v + ay * s,
            ay * ax * v + az * s, c + ay * ay * v, ay * az * v - ax * s,
            az * ax * v - ay * s, az * ay * v + ax * s, c + az * az * v,
        };
    }
    static double[] mul(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
            for (int k = 0; k < 3; k++) r[i * 3 + j] += a[i * 3 + k] * b[k * 3 + j];
        return r;
    }
    // world-from-device matrix -> rotation vector (x, y, z, w), Shepperd's method (any angle)
    static float[] quat(double[] m) {
        double tr = m[0] + m[4] + m[8], x, y, z, w;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1) * 2;
            w = s / 4; x = (m[7] - m[5]) / s; y = (m[2] - m[6]) / s; z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2;
            w = (m[7] - m[5]) / s; x = s / 4; y = (m[1] + m[3]) / s; z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2;
            w = (m[2] - m[6]) / s; x = (m[1] + m[3]) / s; y = s / 4; z = (m[5] + m[7]) / s;
        } else {
            double s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2;
            w = (m[3] - m[1]) / s; x = (m[2] + m[6]) / s; y = (m[5] + m[7]) / s; z = s / 4;
        }
        if (w < 0) { x = -x; y = -y; z = -z; w = -w; }  // (Android reports w >= 0)
        return new float[] {(float) x, (float) y, (float) z, (float) w};
    }
    // the phone held upright in landscape in front of the player facing north, for a display rotation:
    // screen x = east, screen y = up, screen z (towards the player) = south. Returns world-from-device.
    static double[] upright(int rot) {
        double[] sx = new double[3], sy = new double[3];
        GyroAim.screenAxes(rot, sx, sy);
        double[] X = {1, 0, 0}, Y = {0, 0, 1}, Z = {0, -1, 0};
        // device axis k in world = sum over screen axes: device = sx_k * X + sy_k * Y + (sx x sy)_k * Z
        double[] sz = {sx[1] * sy[2] - sx[2] * sy[1], sx[2] * sy[0] - sx[0] * sy[2], sx[0] * sy[1] - sx[1] * sy[0]};
        double[] m = new double[9];
        for (int k = 0; k < 3; k++)
            for (int i = 0; i < 3; i++) m[i * 3 + k] = sx[k] * X[i] + sy[k] * Y[i] + sz[k] * Z[i];
        return m;
    }
    static void check(String what, int rot, double[] before, double[] after, double yaw, double pitch) {
        double[] a = new double[9], b = new double[9];
        GyroAim.screenFrame(quat(before), rot, a);
        GyroAim.screenFrame(quat(after), rot, b);
        double[] d = GyroAim.turn(a, b);
        double gy = Math.toDegrees(d[0]), gp = Math.toDegrees(d[1]);
        boolean ok = Math.abs(gy - yaw) < 0.05 && Math.abs(gp - pitch) < 0.05;
        if (!ok) fails++;
        System.out.printf(java.util.Locale.ROOT, "%s rot %d %-40s yaw %+7.3f (want %+6.2f) pitch %+7.3f (want %+6.2f)%n",
                ok ? "ok  " : "FAIL", rot, what, gy, yaw, gp, pitch);
    }
    public static void main(String[] args) {
        double[] east = {1, 0, 0};
        for (int rot = 0; rot < 4; rot++) {
            double[] p = upright(rot);
            check("turn left 10 (about vertical)", rot, p, mul(rot(0, 0, 1, 10), p), 10, 0);
            check("turn right 5", rot, p, mul(rot(0, 0, 1, -5), p), -5, 0);
            // back of the phone up = the top towards the player: about the east axis (the screen's x), + = top towards south
            check("aim up 8 (top towards the player)", rot, p, mul(rot(east[0], east[1], east[2], 8), p), 0, 8);
            check("aim down 3", rot, p, mul(rot(1, 0, 0, -3), p), 0, -3);
            // already tilted 30 degrees up: turning on the spot is still pure yaw
            double[] up30 = mul(rot(1, 0, 0, 30), p);
            check("tilted 30 up, turn left 6", rot, up30, mul(rot(0, 0, 1, 6), up30), 6, 0);
            // the screen's own roll (steering-wheel turn) moves neither
            double[] roll = mul(rot(0, -1, 0, 4), p);
            check("roll 4 (steering wheel)", rot, p, roll, 0, 0);
            // small per-sample step at 200 Hz: 0.2 degree
            check("small step left 0.2", rot, up30, mul(rot(0, 0, 1, 0.2), up30), 0.2, 0);
            // a facing other than north, and the 3-value rotation vector (w left out, as older sensors send it)
            double[] west = mul(rot(0, 0, 1, 90), up30);
            double[] a3 = new double[9], b3 = new double[9];
            float[] qa = quat(west), qb = quat(mul(rot(0, 0, 1, -7), west));
            GyroAim.screenFrame(new float[] {qa[0], qa[1], qa[2]}, rot, a3);
            GyroAim.screenFrame(new float[] {qb[0], qb[1], qb[2]}, rot, b3);
            double[] d3 = GyroAim.turn(a3, b3);
            boolean ok3 = Math.abs(Math.toDegrees(d3[0]) + 7) < 0.05 && Math.abs(Math.toDegrees(d3[1])) < 0.05;
            if (!ok3) fails++;
            System.out.printf(java.util.Locale.ROOT, "%s rot %d %-40s yaw %+7.3f (want  -7.00) pitch %+7.3f%n",
                    ok3 ? "ok  " : "FAIL", rot, "facing west, 3 values, turn right 7", Math.toDegrees(d3[0]), Math.toDegrees(d3[1]));
        }
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
