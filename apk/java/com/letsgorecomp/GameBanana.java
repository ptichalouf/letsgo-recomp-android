package com.letsgorecomp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// GameBanana's public API (apiv11, no account) for the mod catalogue of Pokémon: Let's Go (game 6773, Pikachu and
// Eevee share it). Nothing is hosted by this app: the list comes from GameBanana, each file is downloaded from
// GameBanana's own link (/dl/<file id>, which redirects to its file cache), checked against the size and MD5 the API
// gives, and the mod's page stays one tap away (author, readme, licence, comments).
// See docs/MODS.md for the choice between this and sending the player to the site.
final class GameBanana {
    static final int GAME_ID = 6773;
    static final String SITE = "https://gamebanana.com";
    static final String UA = "LetsGoRecomp-Android (mods)";
    static final long MAX_FILE = 2L << 30;  // larger than any file of this game's mods (biggest seen: ~1.1 GB)

    static final class Mod {
        long id;
        String name, author, category, profileUrl, visibility, version;
        boolean contentRatings, obsolete;
        long updated;  // unix seconds

        /** In-app install is offered only for mods GameBanana shows openly: no content rating (sensitive content),
         *  initial visibility "show". The others open their page in the browser, where GameBanana asks. */
        boolean installableInApp() {
            return !contentRatings && "show".equals(visibility);
        }
    }

    static final class FileItem {
        long id, size;
        String name, md5, av, analysis, url, description;
        boolean archived;

        /** null when the app may download and install this file, else why not (shown under the file). */
        String problem() { return fileProblem(name, size, av, analysis, archived); }
    }

    /** The checks on a file before an in-app install (pure, tested in ModStoreTest). */
    static String fileProblem(String name, long size, String av, String analysis, boolean archived) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!ModUnpack.hasExtension(n)) return "format";
        if (archived) return "archived";
        if (av == null || !av.equals("clean")) return "antivirus";
        if (analysis != null && !analysis.isEmpty() && !analysis.equals("ok")) return "analysis";
        if (size <= 0 || size > MAX_FILE) return "size";
        return null;
    }

    static String profileUrl(long modId) { return SITE + "/mods/" + modId; }

    // ------------------------------------------------------------------ network

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("GameBanana HTTP " + code);
            InputStream in = c.getInputStream();
            try {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0;) {
                    b.write(buf, 0, n);
                    if (b.size() > (8 << 20)) throw new IOException("answer too large");
                }
                return b.toString("UTF-8");
            } finally { in.close(); }
        } finally { c.disconnect(); }
    }

    /** Every mod of the game, most downloaded first (pages of 50 until the API says it is complete). */
    static List<Mod> mods() throws IOException {
        List<Mod> out = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            String url = SITE + "/apiv11/Mod/Index?_nPage=" + page + "&_nPerpage=50&_aFilters%5BGeneric_Game%5D=" + GAME_ID
                + "&_sSort=Generic_MostDownloaded";
            try {
                org.json.JSONObject o = new org.json.JSONObject(get(url));
                org.json.JSONArray recs = o.optJSONArray("_aRecords");
                if (recs != null) for (int i = 0; i < recs.length(); i++) out.add(mod(recs.getJSONObject(i)));
                org.json.JSONObject meta = o.optJSONObject("_aMetadata");
                if (recs == null || recs.length() == 0 || meta == null || meta.optBoolean("_bIsComplete", true)) break;
            } catch (org.json.JSONException e) {
                throw new IOException("GameBanana answer unreadable: " + e.getMessage());
            }
        }
        return out;
    }

    static Mod mod(org.json.JSONObject r) {
        Mod m = new Mod();
        m.id = r.optLong("_idRow");
        m.name = r.optString("_sName", "?");
        org.json.JSONObject sub = r.optJSONObject("_aSubmitter");
        m.author = sub != null ? sub.optString("_sName", "") : "";
        org.json.JSONObject cat = r.optJSONObject("_aRootCategory");
        m.category = cat != null ? cat.optString("_sName", "") : "";
        m.profileUrl = r.optString("_sProfileUrl", profileUrl(m.id));
        if (!m.profileUrl.startsWith(SITE + "/")) m.profileUrl = profileUrl(m.id);
        m.visibility = r.optString("_sInitialVisibility", "show");
        m.contentRatings = r.optBoolean("_bHasContentRatings", false);
        m.obsolete = r.optBoolean("_bIsObsolete", false);
        m.version = r.optString("_sVersion", "");
        m.updated = r.optLong("_tsDateUpdated", r.optLong("_tsDateModified", 0));
        return m;
    }

    /** The files of a mod (its download page). */
    static List<FileItem> files(long modId) throws IOException {
        List<FileItem> out = new ArrayList<>();
        try {
            org.json.JSONObject o = new org.json.JSONObject(get(SITE + "/apiv11/Mod/" + modId + "/DownloadPage"));
            if (o.optBoolean("_bIsTrashed") || o.optBoolean("_bIsWithheld")) return out;
            org.json.JSONArray fs = o.optJSONArray("_aFiles");
            if (fs != null) for (int i = 0; i < fs.length(); i++) {
                org.json.JSONObject f = fs.getJSONObject(i);
                FileItem it = new FileItem();
                it.id = f.optLong("_idRow");
                it.name = f.optString("_sFile", "");
                it.size = f.optLong("_nFilesize");
                it.md5 = f.optString("_sMd5Checksum", "");
                it.av = f.optString("_sAvResult", "");
                it.analysis = f.optString("_sAnalysisResult", "");
                it.archived = f.optBoolean("_bIsArchived", false);
                it.description = f.optString("_sDescription", "");
                it.url = SITE + "/dl/" + it.id;  // (always GameBanana's own link, whatever the answer says)
                out.add(it);
            }
        } catch (org.json.JSONException e) {
            throw new IOException("GameBanana answer unreadable: " + e.getMessage());
        }
        return out;
    }

    interface Progress {
        /** bytes so far of total; true cancels. */
        boolean onBytes(long done, long total);
    }

    /** Download a file to dest, refusing anything not served by gamebanana.com, of another size, or another MD5. */
    static void download(FileItem f, File dest, Progress p) throws IOException {
        HttpURLConnection c = open(f.url);
        try {
            int code = c.getResponseCode();
            String host = c.getURL().getHost().toLowerCase(Locale.ROOT);
            if (!c.getURL().getProtocol().equals("https") || !(host.equals("gamebanana.com") || host.endsWith(".gamebanana.com")))
                throw new IOException("download left GameBanana (" + host + ")");
            if (code != 200) throw new IOException("GameBanana HTTP " + code);
            java.security.MessageDigest md5;
            try { md5 = java.security.MessageDigest.getInstance("MD5"); } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
            long done = 0;
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0;) {
                    done += n;
                    if (done > f.size) throw new IOException("larger than announced");
                    out.write(buf, 0, n);
                    md5.update(buf, 0, n);
                    if (p != null && p.onBytes(done, f.size)) throw new IOException("cancelled");
                }
            } finally { out.close(); in.close(); }
            if (done != f.size) throw new IOException("incomplete download (" + done + " / " + f.size + ")");
            StringBuilder hex = new StringBuilder();
            for (byte b : md5.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            if (f.md5 != null && !f.md5.isEmpty() && !hex.toString().equalsIgnoreCase(f.md5))
                throw new IOException("MD5 differs from GameBanana's");
        } finally { c.disconnect(); }
    }
}
