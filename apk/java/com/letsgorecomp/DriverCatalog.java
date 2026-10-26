package com.letsgorecomp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which Turnip build suits this phone, and where to get it. Nothing is shipped in the APK: the launcher asks GitHub,
 * on the player's request, for the latest releases of three Turnip packagers, picks with the rules DroidDeck publishes
 * (https://github.com/Droid-Deck/Drivers-CI, auto-drivers.json: first rule matching the GPU family / model wins, its
 * "prefer" keys are tried in order), downloads the chosen .zip and checks it against the sha256 GitHub publishes for
 * that release asset.
 *
 * Trust: the download URLs come from the GitHub releases API only (never from the rules file), they must point to
 * github.com/&lt;that repository&gt;/releases/download/, and redirects may only lead to *.githubusercontent.com. A file
 * whose sha256 differs from the published digest is deleted.
 */
final class DriverCatalog {
    private static final String TAG = "lasrecomp";
    static final String RULES_URL = "https://raw.githubusercontent.com/Droid-Deck/Drivers-CI/main/auto-drivers.json";
    private static final long MAX_DOWNLOAD = 64L << 20;

    static final class Source {
        final String label, repo;
        Source(String label, String repo) { this.label = label; this.repo = repo; }
    }

    static final List<Source> SOURCES = Collections.unmodifiableList(Arrays.asList(
            new Source("Banners-Turnip", "The412Banner/Banners-Turnip"),
            new Source("WinNative", "WinNative-Emu/Drivers"),
            new Source("DroidDeck", "Droid-Deck/Drivers-CI")));

    /** One downloadable package: the newest release asset for a rules key. */
    static final class Candidate {
        final String key, sourceLabel, repo, tag, asset, url, sha256, publishedAt;
        final long size;

        Candidate(String key, String sourceLabel, String repo, String tag, String asset, String url, long size,
                  String sha256, String publishedAt) {
            this.key = key; this.sourceLabel = sourceLabel; this.repo = repo; this.tag = tag; this.asset = asset;
            this.url = url; this.size = size; this.sha256 = sha256; this.publishedAt = publishedAt;
        }

        String title() { return label(key) + " " + tag; }

        String details() {
            return String.format(Locale.US, Txt.t("c_details", "%s\nFonte: github.com/%s (publicação %s%s)\nArquivo: %s (%.1f MB)\nsha256: %s"),
                    label(key), repo, tag, publishedAt.length() >= 10 ? ", " + publishedAt.substring(0, 10) : "",
                    asset, size / 1048576.0, sha256);
        }
    }

    /** The rules file's entries (schemaVersion 1). */
    static final class Rule {
        final Set<GpuInfo.Family> families;
        final Set<Integer> models;
        final Boolean oneUi8Gen2;
        final int minSdk, blobMin, blobMax;
        final List<String> prefer;

        Rule(Set<GpuInfo.Family> families, Set<Integer> models, Boolean oneUi8Gen2, int minSdk, int blobMin, int blobMax,
             List<String> prefer) {
            this.families = families; this.models = models; this.oneUi8Gen2 = oneUi8Gen2; this.minSdk = minSdk;
            this.blobMin = blobMin; this.blobMax = blobMax; this.prefer = prefer;
        }

        boolean matches(GpuInfo g) {
            boolean blobFree = blobMin == 0 && blobMax == Integer.MAX_VALUE;
            return families.contains(g.family)
                    && (models.isEmpty() || models.contains(g.model))
                    && (oneUi8Gen2 == null || oneUi8Gen2 == g.oneUi8Gen2)
                    && g.sdk >= minSdk
                    && (blobFree || (g.blob > 0 && g.blob >= blobMin && g.blob <= blobMax));
        }
    }

    /** The same decisions as DroidDeck's auto-drivers.json of 26/10/2026, for when that file cannot be read. */
    static final String BUILT_IN_RULES = "{\"schemaVersion\":1,\"rules\":["
            + "{\"families\":[\"A8XX\"],\"models\":[840],\"prefer\":[\"dd-turnip\",\"wn-b\"]},"
            + "{\"families\":[\"A8XX\"],\"prefer\":[\"wn-b\"]},"
            + "{\"families\":[\"A7XX_LOW\"],\"prefer\":[\"banner-710-720-test\"]},"
            + "{\"families\":[\"A7XX\"],\"oneUi8Gen2\":true,\"prefer\":[\"banner-8g2-oneui\"]},"
            + "{\"families\":[\"A7XX\",\"A6XX\"],\"prefer\":[\"banner\"]},"
            + "{\"families\":[\"ADRENO_UNKNOWN\"],\"minSdk\":36,\"prefer\":[\"wn-b\"]},"
            + "{\"families\":[\"ADRENO_UNKNOWN\"],\"prefer\":[\"banner\"]}]}";

    /** A check: the rules in force and the newest package per key. */
    static final class Check {
        final List<Rule> rules;
        final boolean rulesFromGitHub;
        final Map<String, Candidate> byKey;
        final List<String> failedSources;

        Check(List<Rule> rules, boolean rulesFromGitHub, Map<String, Candidate> byKey, List<String> failedSources) {
            this.rules = rules; this.rulesFromGitHub = rulesFromGitHub; this.byKey = byKey;
            this.failedSources = failedSources;
        }

        /** The keys the first matching rule prefers (built-in rules when the remote ones match nothing). */
        List<String> preferences(GpuInfo g) {
            List<String> p = DriverCatalog.preferences(g, rules);
            return p.isEmpty() ? DriverCatalog.preferences(g, parseRules(BUILT_IN_RULES)) : p;
        }

        /** Packages the rules recommend for this phone, best first, without those that already failed here. */
        List<Candidate> plan(GpuInfo g, Set<String> badSha256) {
            return pick(preferences(g), badSha256, Collections.<Candidate>emptyList());
        }

        /** Other packages built for this GPU family, outside the rules: offered once the recommended ones failed. */
        List<Candidate> alternatives(GpuInfo g, Set<String> badSha256) {
            return pick(DriverCatalog.alternatives(g.family), badSha256, plan(g, badSha256));
        }

        private List<Candidate> pick(List<String> keys, Set<String> bad, List<Candidate> exclude) {
            List<Candidate> out = new ArrayList<>();
            for (String key : keys) {
                Candidate c = byKey.get(key);
                if (c != null && !bad.contains(c.sha256) && !exclude.contains(c) && !out.contains(c)) out.add(c);
            }
            return out;
        }
    }

    static List<String> alternatives(GpuInfo.Family f) {
        switch (f) {
            case A8XX: return Arrays.asList("wn-b", "banner-a8xx", "dd-turnip", "wn-p");
            case A7XX: return Arrays.asList("banner", "wn-b", "dd-turnip", "wn-p");
            case A6XX: return Arrays.asList("banner", "wn-b", "dd-turnip");
            case A7XX_LOW: return Arrays.asList("banner-710-720-test", "wn-b");
            case ADRENO_UNKNOWN: return Arrays.asList("wn-b", "banner");
            default: return Collections.emptyList();
        }
    }

    private DriverCatalog() {
    }

    static String label(String key) {
        switch (key) {
            case "banner": return "Banners-Turnip · Adreno 6xx/7xx";
            case "banner-a8xx": return "Banners-Turnip · Adreno 8xx";
            case "banner-8g2-oneui": return Txt.t("c_8g2", "Banners-Turnip · 8 Gen 2 com One UI");
            case "banner-710-720-test": return Txt.t("c_710", "Banners-Turnip · Adreno 710/720 (teste)");
            case "wn-b": return Txt.t("c_wn_bal", "WinNative Turnip · Balanced (todas as Adreno)");
            case "wn-p": return Txt.t("c_wn_perf", "WinNative Turnip · Performance (todas as Adreno)");
            case "dd-turnip": return Txt.t("c_dd", "DroidDeck DD-Turnip (todas as Adreno)");
            default: return key;
        }
    }

    static List<String> preferences(GpuInfo g, List<Rule> rules) {
        if (g.family == GpuInfo.Family.NOT_ADRENO) return Collections.emptyList();
        for (Rule r : rules) if (r.matches(g)) return r.prefer;
        return Collections.emptyList();
    }

    static List<Rule> parseRules(String text) {
        List<Rule> out = new ArrayList<>();
        try {
            JSONObject j = new JSONObject(text);
            if (j.optInt("schemaVersion") != 1) return out;
            JSONArray rules = j.getJSONArray("rules");
            for (int i = 0; i < rules.length(); i++) {
                JSONObject o = rules.getJSONObject(i);
                Set<GpuInfo.Family> fam = new HashSet<>();
                JSONArray fa = o.optJSONArray("families");
                for (int k = 0; fa != null && k < fa.length(); k++) {
                    for (GpuInfo.Family f : GpuInfo.Family.values()) if (f.name().equals(fa.optString(k))) fam.add(f);
                }
                List<String> prefer = new ArrayList<>();
                JSONArray pa = o.optJSONArray("prefer");
                for (int k = 0; pa != null && k < pa.length(); k++) prefer.add(pa.optString(k));
                if (fam.isEmpty() || prefer.isEmpty()) continue;
                Set<Integer> models = new HashSet<>();
                JSONArray ma = o.optJSONArray("models");
                for (int k = 0; ma != null && k < ma.length(); k++) models.add(ma.optInt(k));
                out.add(new Rule(fam, models, o.has("oneUi8Gen2") ? o.optBoolean("oneUi8Gen2") : null,
                        o.optInt("minSdk", 0), o.optInt("blobMin", 0), o.optInt("blobMax", Integer.MAX_VALUE), prefer));
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    /** Rules key of a release asset, or null for files that are not an Android driver package. */
    static String classify(String repo, String name, String tag) {
        if (!name.endsWith(".zip")) return null;
        switch (repo) {
            case "The412Banner/Banners-Turnip": {
                String prefix = "Turnip-" + tag;
                if (!name.startsWith(prefix)) return null;
                String variant = name.substring(prefix.length(), name.length() - 4);
                if (variant.endsWith("-Linux") || variant.endsWith("-Wayland")) return null;  // desktop builds
                return "banner" + variant.toLowerCase(Locale.US);
            }
            case "WinNative-Emu/Drivers": {
                Matcher m = Pattern.compile("^WN-Turnip-[^-]+-([a-z]+)_Axxx\\.zip$").matcher(name);
                return m.matches() ? "wn-" + m.group(1) : null;
            }
            case "Droid-Deck/Drivers-CI":
                return name.matches("^DD-Turnip-v\\d+\\.\\d+\\.\\d+\\.zip$") ? "dd-turnip" : null;
            default:
                return null;
        }
    }

    /** Network: rules + the releases of every source. Throws only when no source answered. */
    static Check fetch() throws IOException {
        List<Rule> rules = Collections.emptyList();
        boolean remote = false;
        try {
            rules = parseRules(new String(get(RULES_URL, 1 << 20), "UTF-8"));
            remote = !rules.isEmpty();
        } catch (IOException e) {
            Log.w(TAG, "[gpu] auto-drivers.json: " + e.getMessage());
        }
        if (!remote) rules = parseRules(BUILT_IN_RULES);
        Map<String, Candidate> byKey = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        IOException last = null;
        for (Source s : SOURCES) {
            try {
                JSONArray releases = new JSONArray(new String(
                        get("https://api.github.com/repos/" + s.repo + "/releases?per_page=15", 8 << 20), "UTF-8"));
                for (int i = 0; i < releases.length(); i++) {  // newest first: the first asset per key wins
                    JSONObject r = releases.getJSONObject(i);
                    if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue;
                    String tag = r.optString("tag_name");
                    JSONArray assets = r.optJSONArray("assets");
                    for (int k = 0; assets != null && k < assets.length(); k++) {
                        JSONObject a = assets.getJSONObject(k);
                        String name = a.optString("name");
                        String key = classify(s.repo, name, tag);
                        if (key == null || byKey.containsKey(key)) continue;
                        String digest = a.optString("digest", "");
                        if (!digest.matches("^sha256:[0-9a-f]{64}$")) continue;  // no published checksum: not offered
                        String url = a.optString("browser_download_url");
                        if (!url.startsWith("https://github.com/" + s.repo + "/releases/download/")) continue;
                        byKey.put(key, new Candidate(key, s.label, s.repo, tag, name, url, a.optLong("size"),
                                digest.substring(7), r.optString("published_at", "")));
                    }
                }
            } catch (Exception e) {
                failed.add(s.label);
                last = e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
                Log.w(TAG, "[gpu] releases of " + s.repo + ": " + e.getMessage());
            }
        }
        if (failed.size() == SOURCES.size()) throw last != null ? last : new IOException(Txt.t("c_noresp", "GitHub não respondeu"));
        return new Check(rules, remote, byKey, failed);
    }

    interface Progress { void update(long done, long total); }

    /** Downloads a candidate to the cache dir and checks its sha256 against GitHub's digest. */
    static File download(Context c, Candidate cand, Progress p) throws IOException {
        return download(new File(c.getCacheDir(), "gpu_downloads"), cand, p);
    }

    static File download(File dir, Candidate cand, Progress p) throws IOException {
        if (cand.size > MAX_DOWNLOAD) throw new IOException(Txt.t("c_big1", "arquivo grande demais (") + cand.size + " bytes)");
        dir.mkdirs();
        File part = new File(dir, cand.sha256 + ".part");
        File done = new File(dir, cand.sha256 + ".zip");
        HttpURLConnection con = open(cand.url);
        try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[65536];
            long n = 0, total = con.getContentLengthLong() > 0 ? con.getContentLengthLong() : cand.size;
            int r;
            while ((r = in.read(b)) > 0) {
                n += r;
                if (n > MAX_DOWNLOAD) throw new IOException(Txt.t("c_big", "arquivo grande demais"));
                md.update(b, 0, r);
                out.write(b, 0, r);
                if (p != null) p.update(n, total);
            }
            String got = hex(md.digest());
            if (!got.equals(cand.sha256))
                throw new IOException(Txt.t("c_sha1", "sha256 diferente do publicado no GitHub (esperado ") + cand.sha256.substring(0, 12)
                        + Txt.t("c_sha2", "…, recebido ") + got.substring(0, 12) + "…)");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e.getMessage());
        } catch (IOException e) {
            part.delete();
            throw e;
        } finally {
            con.disconnect();
        }
        if (!part.renameTo(done)) { part.delete(); throw new IOException(Txt.t("c_write", "não deu para gravar o arquivo")); }
        return done;
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format(Locale.US, "%02x", x));
        return sb.toString();
    }

    private static boolean allowedHost(String host, boolean first) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        if (host.equals("github.com") || host.equals("api.github.com") || host.equals("raw.githubusercontent.com"))
            return true;
        return !first && host.endsWith(".githubusercontent.com");  // release asset storage, only via a redirect
    }

    /** Opens an https URL on GitHub, following redirects by hand so that each hop is checked. */
    private static HttpURLConnection open(String url) throws IOException {
        for (int hop = 0; hop < 6; hop++) {
            URL u = new URL(url);
            if (!"https".equals(u.getProtocol()) || !allowedHost(u.getHost(), hop == 0))
                throw new IOException(Txt.t("c_refused", "endereço recusado: ") + u.getHost());
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "LetsGoRecomp-Android");
            if (u.getHost().equals("api.github.com")) c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException(Txt.t("c_redir0", "redirecionamento sem destino"));
                url = new URL(u, loc).toString();
                continue;
            }
            if (code == 403 || code == 429) {
                c.disconnect();
                throw new IOException(Txt.t("c_rate", "limite de consultas do GitHub atingido; tente de novo em uma hora"));
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException(Txt.t("c_http", "GitHub respondeu HTTP ") + code);
            }
            return c;
        }
        throw new IOException(Txt.t("c_redirs", "redirecionamentos demais"));
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int r;
            while ((r = in.read(b)) > 0) {
                o.write(b, 0, r);
                if (o.size() > max) throw new IOException(Txt.t("c_resp_big", "resposta grande demais"));
            }
            return o.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
