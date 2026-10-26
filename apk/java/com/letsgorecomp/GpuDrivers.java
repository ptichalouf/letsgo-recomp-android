package com.letsgorecomp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Custom GPU drivers (Turnip) for the Driver section. Each driver is a folder under the app-private files dir
 * (never on shared storage: dlopen refuses libraries other apps could tamper with):
 *   &lt;files&gt;/gpu_drivers/&lt;slug&gt;/{meta.json, &lt;libraryName&gt;.so, source.json, .started, .start_failed}
 * The launcher stores the choice in the letsgo prefs (LARECOMP_GPU_DRIVER=&lt;slug&gt;, LARECOMP_GPU_DRIVER_LIB=&lt;.so&gt;); the
 * game loads it through adrenotools. No choice = the system driver.
 *
 * The APK ships no driver. A driver comes either from a .zip the player imports, or from a package the launcher
 * downloads from the packager's GitHub releases (DriverCatalog), checked against the published sha256.
 *
 * Start trial: a driver that has never brought the game to its first frames on this phone is "on trial". The runtime
 * writes .started after 120 presented frames, or .start_failed (with the reason) when it refuses the driver. Back in the
 * launcher, a trial whose process died without .started, or that left .start_failed, is rolled back: the driver that
 * last worked here is selected again (or none, said so on screen) and the package is not recommended again.
 */
final class GpuDrivers {
    static final String KEY_DRIVER = "LARECOMP_GPU_DRIVER";
    static final String KEY_LIB = "LARECOMP_GPU_DRIVER_LIB";
    private static final String PREFS = "letsgo";
    private static final String TRIAL = "gpu_trial";
    private static final String KEY_LAST_GOOD = "gpu_last_good";
    private static final String KEY_BAD = "gpu_bad_sha256";
    static final String STARTED = ".started";
    static final String START_FAILED = ".start_failed";
    private static final long MAX_ZIP = 64L << 20;

    static final class Driver {
        final String slug;  // folder name under gpu_drivers/
        final String name;
        final String description;
        final String driverVersion;
        final String libraryName;
        final JSONObject source;  // provenance of a downloaded package (null: imported by hand)
        final boolean started;    // has brought the game to its first frames on this phone
        final boolean failed;     // was rolled back: never started here

        Driver(String slug, String name, String description, String driverVersion, String libraryName,
               JSONObject source, boolean started, boolean failed) {
            this.slug = slug;
            this.name = name;
            this.description = description;
            this.driverVersion = driverVersion;
            this.libraryName = libraryName;
            this.source = source;
            this.started = started;
            this.failed = failed;
        }

        String packageSha256() { return source == null ? "" : source.optString("sha256", ""); }

        String origin() {
            if (source == null) return "importado à mão";
            return "github.com/" + source.optString("repo") + " " + source.optString("tag");
        }
    }

    /** Result of an import: either a driver or a human-readable error. */
    static final class ImportResult {
        final Driver driver;
        final String error;

        ImportResult(Driver driver, String error) {
            this.driver = driver;
            this.error = error;
        }
    }

    /** What the launcher has to say about the last start with a driver on trial. */
    static final class TrialOutcome {
        final Driver driver;      // the driver that was on trial
        final boolean ok;
        final String reason;      // why it is considered failed (null when ok)
        final Driver restored;    // selected again after a failure; null = no custom driver selected now

        TrialOutcome(Driver driver, boolean ok, String reason, Driver restored) {
            this.driver = driver;
            this.ok = ok;
            this.reason = reason;
            this.restored = restored;
        }
    }

    private GpuDrivers() {
    }

    static File driversDir(Context c) {
        return new File(c.getFilesDir(), "gpu_drivers");
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** All installed drivers whose .so is present, in no particular order. */
    static List<Driver> list(Context c) {
        return list(driversDir(c));
    }

    static List<Driver> list(File driversDir) {
        List<Driver> out = new ArrayList<>();
        File[] dirs = driversDir.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            if (!d.isDirectory()) continue;
            Driver drv = read(d);
            if (drv != null) out.add(drv);
        }
        return out;
    }

    static Driver bySlug(Context c, String slug) {
        if (slug == null || slug.isEmpty() || slug.contains("/")) return null;
        return read(new File(driversDir(c), slug));
    }

    /** The selected driver, or null for the system driver (also null when the selection points nowhere). */
    static Driver selected(Context c) {
        Driver d = bySlug(c, prefs(c).getString(KEY_DRIVER, null));
        if (d == null) return null;  // stale selection: the folder is gone
        if (!d.libraryName.equals(prefs(c).getString(KEY_LIB, null))) return null;
        return d;
    }

    /** Select a driver, or null for the system driver. */
    static void select(Context c, Driver d) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (d == null) {
            e.remove(KEY_DRIVER);
            e.remove(KEY_LIB);
        } else {
            e.putString(KEY_DRIVER, d.slug);
            e.putString(KEY_LIB, d.libraryName);
        }
        e.commit();
    }

    static void delete(Context c, Driver d) {
        deleteTree(new File(driversDir(c), d.slug));
        Driver sel = selected(c);
        if (sel == null && d.slug.equals(prefs(c).getString(KEY_DRIVER, null))) select(c, null);
    }

    /** sha256 of the packages that failed to start on this phone (not recommended again). */
    static Set<String> badPackages(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY_BAD, new HashSet<String>()));
    }

    // ---- start trial ----------------------------------------------------------------------------------------------

    /**
     * Called by the game activity before the native runtime loads. A driver that has not started here yet is put on
     * trial (synchronously written: a crash in the driver must not lose it).
     */
    static void beginLaunch(Context c) {
        Driver d = selected(c);
        SharedPreferences.Editor e = c.getSharedPreferences(TRIAL, Context.MODE_PRIVATE).edit().clear();
        if (d != null && !d.started) {
            new File(new File(driversDir(c), d.slug), START_FAILED).delete();
            e.putString("slug", d.slug).putInt("pid", Process.myPid()).putLong("at", System.currentTimeMillis());
            Log.i("lasrecomp", "[gpu] driver on trial: " + d.slug);
        } else if (d != null) {
            prefs(c).edit().putString(KEY_LAST_GOOD, d.slug).apply();
        }
        e.commit();
    }

    /** Called when the game activity is destroyed normally: a trial without verdict (left early) is dropped. */
    static void gameClosed(Context c) {
        SharedPreferences t = c.getSharedPreferences(TRIAL, Context.MODE_PRIVATE);
        String slug = t.getString("slug", null);
        if (slug == null || t.getInt("pid", -1) != Process.myPid()) return;
        File dir = new File(driversDir(c), slug);
        if (!new File(dir, STARTED).exists() && !new File(dir, START_FAILED).exists()) t.edit().clear().commit();
    }

    /**
     * The verdict on the driver on trial, if there is one: null while there is none (or the game is still running).
     * A failure is rolled back here.
     */
    static TrialOutcome checkTrial(Context c, boolean gameRunning) {
        SharedPreferences t = c.getSharedPreferences(TRIAL, Context.MODE_PRIVATE);
        String slug = t.getString("slug", null);
        if (slug == null) return null;
        File dir = new File(driversDir(c), slug);
        Driver d = read(dir);
        if (d == null) { t.edit().clear().commit(); return null; }
        if (d.started) {
            t.edit().clear().commit();
            prefs(c).edit().putString(KEY_LAST_GOOD, slug).apply();
            Log.i("lasrecomp", "[gpu] driver started the game: " + slug);
            return new TrialOutcome(d, true, null, null);
        }
        String reason = null;
        File failed = new File(dir, START_FAILED);
        if (failed.exists()) {
            reason = readText(failed).trim();
            if (reason.isEmpty()) reason = "o jogo recusou o driver";
        } else if (t.getInt("pid", -1) != Process.myPid()) {
            reason = "o app fechou antes de o jogo mostrar as primeiras imagens com este driver";
        } else if (gameRunning) {
            return null;  // still starting
        } else {
            return null;  // same process, no verdict yet (gameClosed drops it when the game is left early)
        }
        return rollBack(c, d, reason);
    }

    private static TrialOutcome rollBack(Context c, Driver d, String reason) {
        c.getSharedPreferences(TRIAL, Context.MODE_PRIVATE).edit().clear().commit();
        try {
            write(new File(new File(driversDir(c), d.slug), START_FAILED), reason.getBytes("UTF-8"));
        } catch (IOException ignored) {
        }
        String sha = d.packageSha256();
        if (!sha.isEmpty()) {
            Set<String> bad = badPackages(c);
            bad.add(sha);
            prefs(c).edit().putStringSet(KEY_BAD, bad).commit();
        }
        Driver back = bySlug(c, prefs(c).getString(KEY_LAST_GOOD, null));
        if (back != null && (!back.started || back.slug.equals(d.slug))) back = null;
        Driver sel = selected(c);
        if (sel == null || sel.slug.equals(d.slug)) select(c, back);
        Log.w("lasrecomp", "[gpu] driver " + d.slug + " rolled back (" + reason + "); now: "
                + (back != null ? back.slug : "no custom driver"));
        return new TrialOutcome(read(new File(driversDir(c), d.slug)), false, reason, back);
    }

    // ---- install --------------------------------------------------------------------------------------------------

    /** Imports a .zip picked by the player (copied to the cache first: ZipFile needs a file). */
    static ImportResult importZip(Context c, Uri uri) {
        File tmp = new File(c.getCacheDir(), "driver-import.zip");
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return new ImportResult(null, "Não deu para abrir o arquivo");
            byte[] b = new byte[65536];
            long n = 0;
            int r;
            while ((r = in.read(b)) > 0) {
                n += r;
                if (n > MAX_ZIP) return new ImportResult(null, "Arquivo grande demais para um driver");
                out.write(b, 0, r);
            }
        } catch (IOException ex) {
            tmp.delete();
            return new ImportResult(null, "Não deu para ler o zip: " + ex.getMessage());
        }
        try {
            return installZip(c, tmp, null);
        } finally {
            tmp.delete();
        }
    }

    /**
     * Installs a driver package: a meta.json (with libraryName and minApi) and that library, either at the zip's top
     * level (AdrenoTools layout) or under android/ (DroidDeck bundle; its linux/ half is ignored). When the zip carries
     * a manifest.json with the Android library's sha256, the library must match it.
     */
    static ImportResult installZip(Context c, File zip, JSONObject source) {
        return installZip(driversDir(c), zip, source);
    }

    static ImportResult installZip(File driversDir, File zip, JSONObject source) {
        try (ZipFile z = new ZipFile(zip)) {
            String prefix = null;
            if (z.getEntry("android/meta.json") != null) {
                prefix = "android/";
            } else {
                for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                    String n = en.nextElement().getName();
                    if (n.startsWith("linux/") || n.contains("..") || !(n.equals("meta.json") || n.endsWith("/meta.json")))
                        continue;
                    String p = n.substring(0, n.length() - "meta.json".length());
                    if (prefix == null || p.length() < prefix.length()) prefix = p;
                }
            }
            if (prefix == null) return new ImportResult(null, "O zip não tem meta.json");
            JSONObject meta;
            byte[] metaBytes = readEntry(z, prefix + "meta.json", 1 << 16);
            try {
                meta = new JSONObject(new String(metaBytes, "UTF-8"));
            } catch (Exception ex) {
                return new ImportResult(null, "meta.json não é um JSON válido");
            }
            String libraryName = meta.optString("libraryName", "");
            if (libraryName.isEmpty() || libraryName.contains("/") || libraryName.startsWith("."))
                return new ImportResult(null, "meta.json não tem libraryName utilizável");
            int minApi = meta.optInt("minApi", 0);
            if (minApi > Build.VERSION.SDK_INT)
                return new ImportResult(null, String.format(Locale.US,
                        "O driver precisa da API %d do Android; este aparelho é API %d", minApi, Build.VERSION.SDK_INT));
            if (z.getEntry(prefix + libraryName) == null)
                return new ImportResult(null, "O zip não tem " + prefix + libraryName);
            byte[] so = readEntry(z, prefix + libraryName, (int) MAX_ZIP);
            if (so.length < 1024 || so[0] != 0x7f || so[1] != 'E' || so[2] != 'L' || so[3] != 'F')
                return new ImportResult(null, libraryName + " não é uma biblioteca ELF");
            String libSha = DriverCatalog.hex(MessageDigest.getInstance("SHA-256").digest(so));
            ZipEntry man = z.getEntry("manifest.json");
            if (man != null) {
                JSONObject m = new JSONObject(new String(readEntry(z, "manifest.json", 1 << 16), "UTF-8"));
                JSONObject a = m.optJSONObject("drivers") == null ? null : m.optJSONObject("drivers").optJSONObject("android");
                if (a != null && (prefix + libraryName).equals(a.optString("library"))) {
                    if (!libSha.equalsIgnoreCase(a.optString("sha256")))
                        return new ImportResult(null, "A biblioteca não confere com o sha256 do manifest.json do pacote");
                } else if (prefix.equals("android/")) {
                    return new ImportResult(null, "manifest.json do pacote não descreve " + prefix + libraryName);
                }
            }
            // The same library already installed: reuse it (its trial history stays).
            for (Driver d : list(driversDir)) {
                if (d.source != null && libSha.equals(d.source.optString("libSha256"))
                        || libSha.equals(readText(new File(new File(driversDir, d.slug), ".libsha256")).trim()))
                    return new ImportResult(d, null);
            }
            String slug = slug(meta.optString("name", libraryName));
            File dir = new File(driversDir, slug);
            int dup = 1;
            while (dir.exists()) dir = new File(driversDir, slug + "-" + (++dup));
            dir.mkdirs();
            try {
                write(new File(dir, "meta.json"), metaBytes);
                write(new File(dir, libraryName), so);
                write(new File(dir, ".libsha256"), libSha.getBytes("UTF-8"));
                if (source != null) {
                    source.put("libSha256", libSha).put("installedAt", System.currentTimeMillis());
                    write(new File(dir, "source.json"), source.toString(2).getBytes("UTF-8"));
                }
            } catch (Exception ex) {
                deleteTree(dir);
                return new ImportResult(null, "Não deu para instalar o driver: " + ex.getMessage());
            }
            Driver d = read(dir);
            if (d == null) {
                deleteTree(dir);
                return new ImportResult(null, "O driver não pôde ser lido de volta");
            }
            Log.i("lasrecomp", "[gpu] driver installed: " + d.slug + " (" + d.libraryName + ", sha256 " + libSha
                    + ", " + d.origin() + ")");
            return new ImportResult(d, null);
        } catch (Exception ex) {
            return new ImportResult(null, "Não deu para ler o zip: " + ex.getMessage());
        }
    }

    /** Reads an installed driver folder; null when it is not a valid driver. */
    private static Driver read(File dir) {
        File meta = new File(dir, "meta.json");
        if (!meta.isFile()) return null;
        try {
            JSONObject j = new JSONObject(readText(meta));
            String libraryName = j.optString("libraryName", "");
            if (libraryName.isEmpty() || !new File(dir, libraryName).isFile()) return null;
            JSONObject src = null;
            File s = new File(dir, "source.json");
            if (s.isFile()) try { src = new JSONObject(readText(s)); } catch (Exception ignored) { }
            return new Driver(dir.getName(), j.optString("name", dir.getName()), j.optString("description", ""),
                    j.optString("driverVersion", ""), libraryName, src,
                    new File(dir, STARTED).exists(), new File(dir, START_FAILED).exists());
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readEntry(ZipFile z, String name, int max) throws IOException {
        ZipEntry e = z.getEntry(name);
        if (e == null) throw new IOException(name + " ausente");
        try (InputStream in = z.getInputStream(e)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) > 0) {
                o.write(b, 0, r);
                if (o.size() > max) throw new IOException(name + " grande demais");
            }
            return o.toByteArray();
        }
    }

    private static String readText(File f) {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) o.write(b, 0, r);
            return new String(o.toByteArray(), "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private static void deleteTree(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    /** Folder-safe slug from a display name. */
    private static String slug(String name) {
        String s = name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "driver" : (s.length() > 40 ? s.substring(0, 40) : s);
    }

    private static void write(File f, byte[] b) throws IOException {
        File tmp = new File(f.getParentFile(), f.getName() + ".part");
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write(b);
        }
        if (!tmp.renameTo(f)) {
            tmp.delete();
            throw new IOException(f.getName());
        }
    }
}
