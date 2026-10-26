package com.letsgorecomp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.File;

// Where the player's game folder is, and keeping it across app updates.
//
// The runtime reads the folder with plain fopen() (all-files access), so what it needs is a path. The launcher keeps
// two records of the choice:
//   - the path, in the "letsgo" preferences (game_dir), plus the tree URI it came from (game_dir_uri);
//   - the persisted permission on that tree URI (takePersistableUriPermission). Android keeps it across app updates
//     (adb install -r, store updates) and only drops it on uninstall, "clear data" or releasePersistableUriPermission.
// When the preference is missing but a persisted tree is still there, the folder is taken back from the tree, so
// an update never sends the player back to the folder picker. Forgetting the folder drops both (files untouched).
final class GameDir {
    static final String PREFS = "letsgo";
    static final String KEY_PATH = "game_dir";
    static final String KEY_URI = "game_dir_uri";
    private static final String TAG = "lasrecomp";

    /** The game folder (the runtime's data root), or null if none was chosen. */
    static String path(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = p.getString(KEY_PATH, null);
        if (path != null) return path;
        return restore(ctx);
    }

    /** The tree URI the folder was chosen with, or null. */
    static Uri uri(Context ctx) {
        String u = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URI, null);
        return u != null ? Uri.parse(u) : null;
    }

    /** No preference: the newest persisted folder that still looks like a game folder (extracted/ inside). */
    private static String restore(Context ctx) {
        try {
            UriPermission best = null;
            String bestPath = null;
            for (UriPermission up : ctx.getContentResolver().getPersistedUriPermissions()) {
                if (!up.isReadPermission()) continue;
                String path = pathOf(up.getUri());
                if (path == null) continue;
                File root = GameFolder.dataRoot(new File(path));
                if (!new File(root, "extracted").isDirectory()) continue;
                if (best == null || up.getPersistedTime() > best.getPersistedTime()) {
                    best = up;
                    bestPath = root.getAbsolutePath();
                }
            }
            if (best == null) return null;
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PATH, bestPath).putString(KEY_URI, best.getUri().toString()).commit();
            Log.i(TAG, "[folder] restored from the persisted folder permission: " + bestPath);
            return bestPath;
        } catch (RuntimeException e) {
            Log.w(TAG, "[folder] restore: " + e);
            return null;
        }
    }

    /** Remember the folder the picker returned. The path, or null when the folder is not on a storage volume the
     *  runtime can open by path (the picker's "Recent" or a cloud provider): nothing is changed then. */
    static String choose(Context ctx, Uri tree, int grantFlags) {
        String path = pathOf(tree);
        if (path == null) {
            Log.w(TAG, "[folder] not a storage folder: " + tree);
            return null;
        }
        path = GameFolder.dataRoot(new File(path)).getAbsolutePath();
        int flags = grantFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (flags == 0) flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            ctx.getContentResolver().takePersistableUriPermission(tree, flags);
        } catch (SecurityException e) {
            Log.w(TAG, "[folder] persistable permission refused (" + e.getMessage() + "): kept by path only");
        }
        Uri old = uri(ctx);
        // commit(), not apply(): the choice is on disk before anything can stop the process (an update, the game)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PATH, path).putString(KEY_URI, tree.toString()).commit();
        if (old != null && !old.equals(tree)) release(ctx, old);
        Log.i(TAG, "[folder] chosen: " + path + " (" + tree + ")");
        return path;
    }

    /** Forget the folder: the preference and the persisted permission(s) on it. The files are not touched. */
    static void forget(Context ctx) {
        String path = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PATH, null);
        Uri u = uri(ctx);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PATH).remove(KEY_URI).commit();
        if (u != null) release(ctx, u);
        // and every other persisted tree on the same folder, or restore() would bring it back
        try {
            for (UriPermission up : ctx.getContentResolver().getPersistedUriPermissions()) {
                String p = pathOf(up.getUri());
                if (p != null && path != null
                        && GameFolder.dataRoot(new File(p)).getAbsolutePath().equals(path))
                    release(ctx, up.getUri());
            }
        } catch (RuntimeException ignored) {}
        Log.i(TAG, "[folder] forgotten: " + path);
    }

    private static void release(Context ctx, Uri u) {
        try {
            ctx.getContentResolver().releasePersistableUriPermission(u,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            try {
                ctx.getContentResolver().releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}
        }
    }

    /** A storage tree URI as a path: "primary:LetsGo" -> /storage/emulated/0/LetsGo, "1234-ABCD:Games" ->
     *  /storage/1234-ABCD/Games (an SD card), "raw:/storage/..." as is. null for anything else. */
    static String pathOf(Uri tree) {
        if (tree == null || !"com.android.externalstorage.documents".equals(tree.getAuthority())
                && !"com.android.providers.downloads.documents".equals(tree.getAuthority())) return null;
        String id;
        try {
            id = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException e) {
            return null;
        }
        return pathOfDocId(id);
    }

    static String pathOfDocId(String id) {
        if (id == null) return null;
        if (id.startsWith("raw:/")) return id.substring(4);
        int c = id.indexOf(':');
        if (c <= 0) return null;
        String vol = id.substring(0, c), rel = id.substring(c + 1);
        while (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
        String base = vol.equals("primary") ? "/storage/emulated/0" : vol.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
                ? "/storage/" + vol : null;
        if (base == null) return null;
        return rel.isEmpty() ? base : base + "/" + rel;
    }

    /** The runtime opens the folder by path: on Android 11+ that needs "all files access" outside the app's dirs. */
    static boolean needsAllFilesAccess(Context ctx, String path) {
        if (android.os.Build.VERSION.SDK_INT < 30 || path == null) return false;
        if (android.os.Environment.isExternalStorageManager()) return false;
        String own = ctx.getFilesDir().getAbsolutePath();
        File ext = ctx.getExternalFilesDir(null);
        return !(path.startsWith(own) || (ext != null && path.startsWith(ext.getAbsolutePath())));
    }

    private GameDir() {}
}
