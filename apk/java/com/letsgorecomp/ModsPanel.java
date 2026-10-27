package com.letsgorecomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.Locale;

// Settings > Mods: what is common to every way of getting a mod (work/perso/letsgo-recomp-android/mods/ECRAN-MODS.md).
//   - the installed mods, one row each that turns it on or off (the runtime runs ONE: turning one on turns the other
//     off), and "Remove";
//   - one amber button, the SLOT (screen spec §3), whose action is NOT decided here: FILE (add a downloaded
//     .zip/.rar/.7z, the default), LINK (GameBanana in the browser) or CATALOGUE (GameBanana's list in the app).
//     Alexandre decides; to try one without rebuilding: adb shell setprop debug.letsgo.mods_primary link|catalogue
//   - installing = ModUnpack (libmodunpack.so) into <filesDir>/mods, the folder the runtime reads (LARECOMP_MODS_DIR);
//     the active mod is kept in the launcher's preferences and handed to the runtime as LARECOMP_MOD (LetsGoActivity).
//     The player's game folder is never written.
final class ModsPanel {
    static final int REQ_MOD_FILE = 1005;
    static final String PREF_MOD = "mod";
    enum Primary { FILE, LINK, CATALOGUE }
    private static final int TEXT2 = 0xFFB3BAC5, TEXT3 = 0xFF9AA2AE, ERROR = 0xFFF19A8F, AMBER = 0xFFE8A95B, ON_AMBER = 0xFF22170A;

    private final Activity a;
    private LinearLayout list, slot;
    private boolean busy;

    ModsPanel(Activity a) { this.a = a; }

    static File modsDir(android.content.Context c) { return ModUnpack.dir(c); }

    /** The active mod's folder ("" = none): what LetsGoActivity hands to the runtime as LARECOMP_MOD. */
    static String active(android.content.Context c) {
        return c.getSharedPreferences("letsgo", Activity.MODE_PRIVATE).getString(PREF_MOD, "");
    }

    private void setActive(String name) {
        a.getSharedPreferences("letsgo", Activity.MODE_PRIVATE).edit().putString(PREF_MOD, name).apply();
        android.util.Log.i("lasrecomp", "[mods] active = '" + name + "'");
    }

    static Primary primaryFromProperty() {
        String v = "";
        try {
            v = (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class)
                .invoke(null, "debug.letsgo.mods_primary", "");
        } catch (Throwable ignored) {}
        if ("link".equals(v)) return Primary.LINK;
        if ("catalogue".equals(v)) return Primary.CATALOGUE;
        return Primary.FILE;
    }

    void build(LinearLayout section) {
        section.addView(text(Txt.t("mods_what", "Um mod troca imagens, modelos ou textos do jogo, sem mexer na sua gravação."), TEXT2, 13));
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        section.addView(list, lp());
        section.addView(text(Txt.t("mods_one_at_a_time", "Um mod ativo por vez: ativar um desativa o outro."), TEXT3, 12));
        slot = new LinearLayout(a);
        slot.setOrientation(LinearLayout.VERTICAL);
        section.addView(slot, lp());
        setPrimary(primaryFromProperty());
        refresh();
    }

    /** Rebuild only the slot (amber button + its secondary actions); the list does not move. */
    void setPrimary(Primary p) {
        slot.removeAllViews();
        Runnable addFile = new Runnable() { @Override public void run() { pickFile(); } };
        Runnable site = new Runnable() { @Override public void run() { openUrl(GameBanana.SITE + "/games/" + GameBanana.GAME_ID); } };
        if (p == Primary.FILE) {
            slot.addView(amber(Txt.t("mods_add", "Adicionar um mod"), addFile));
        } else if (p == Primary.LINK) {
            slot.addView(amber(Txt.t("mods_gb_browse", "Procurar mods no GameBanana"), site));
            slot.addView(button(Txt.t("mods_add_file", "Adicionar um arquivo baixado"), addFile));
            slot.addView(text(Txt.t("mods_gb_then", "Baixe o arquivo e volte aqui para adicioná-lo."), TEXT3, 12));
        } else {
            slot.addView(amber(Txt.t("mods_browse", "Explorar mods"), new Runnable() { @Override public void run() { openCatalogue(); } }));
            slot.addView(button(Txt.t("mods_add_file", "Adicionar um arquivo baixado"), addFile));
        }
        if (p != Primary.LINK) {
            slot.addView(text(Txt.t("mods_formats", "Arquivo .zip, .rar ou .7z baixado, ou pasta já descompactada."), TEXT3, 12));
            Button link = button(Txt.t("mods_gb_link", "Página do jogo no GameBanana"), site);
            link.setBackgroundColor(Color.TRANSPARENT);
            link.setTextColor(0xFFF0B872);
            slot.addView(link);
        }
    }

    void refresh() {
        if (list == null) return;
        list.removeAllViews();
        File root = modsDir(a).getParentFile();
        List<String> mods = ModStore.installed(root);
        String active = active(a);
        if (!active.isEmpty() && !mods.contains(active)) { setActive(""); active = ""; }  // (gone: nothing to hand over)
        if (mods.isEmpty()) list.addView(text(Txt.t("mods_empty", "Nenhum mod instalado."), TEXT2, 14));
        for (final String m : mods) {
            java.util.Properties src = ModStore.source(root, m);
            String author = src.getProperty("author", "");
            String meta = (m.equals(active) ? Txt.t("mods_on", "Ativo") : Txt.t("mods_off", "Inativo")) + " · "
                + (author.isEmpty() ? Txt.t("mods_by_unknown", "autor não informado") : String.format(Txt.t("mods_by", "por %1$s"), author))
                + " · " + (src.getProperty("gamebanana.mod", "").isEmpty() ? Txt.t("mods_src_file", "arquivo importado") : Txt.t("mods_src_gb", "GameBanana"));
            LinearLayout row = new LinearLayout(a);
            row.setMinimumHeight(dp(64));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            Switch sw = new Switch(a);
            sw.setText(m + "\n" + meta);
            sw.setTextColor(Color.rgb(200, 210, 220));
            sw.setMinHeight(dp(56));
            sw.setChecked(m.equals(active));
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                    String was = active(a);
                    setActive(on ? m : "");
                    String msg = Txt.t("mods_next_play", "Vale a partir do próximo Jogar.");
                    if (on && !was.isEmpty() && !was.equals(m)) msg = String.format(Txt.t("mods_switched_off", "%1$s foi desativado."), was) + " " + msg;
                    toast(msg);
                    list.post(new Runnable() { @Override public void run() { refresh(); } });
                }
            });
            row.addView(sw, new LinearLayout.LayoutParams(0, -2, 1f));
            Button rm = button(Txt.t("mods_remove", "Remover"), new Runnable() { @Override public void run() { confirmRemove(m); } });
            rm.setTextColor(ERROR);
            rm.setBackgroundColor(Color.TRANSPARENT);
            row.addView(rm, new LinearLayout.LayoutParams(-2, -2));
            list.addView(row, lp());
        }
    }

    // ------------------------------------------------------------------ adding a file (every slot has it)

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        // Every type ModUnpack opens, plus the unknown type: a picker greys out whatever is not listed (a .rar is
        // application/rar on Android 16). An unknown-type file is then admitted by its extension only (accepts()).
        i.putExtra(Intent.EXTRA_MIME_TYPES, ModUnpack.pickerTypes());
        try { a.startActivityForResult(i, REQ_MOD_FILE); }
        catch (android.content.ActivityNotFoundException e) { toast(Txt.t("no_picker", "Este aparelho não tem seletor de pastas.")); }
    }

    /** The launcher forwards its results here; true = it was ours. */
    boolean onActivityResult(int req, int res, Intent data) {
        if (req != REQ_MOD_FILE) return false;
        if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String name = displayName(uri), type = a.getContentResolver().getType(uri);
            android.util.Log.i("lasrecomp", "[mods] picked '" + name + "' type=" + type + " accepted=" + ModUnpack.accepts(name, type));
            if (ModUnpack.accepts(name, type)) install(name, uri, null, null, null);
            else new AlertDialog.Builder(a).setTitle(name).setMessage(String.format(Txt.t("mods_fail_format",
                "O formato .%1$s ainda não é suportado: descompacte o arquivo e adicione a pasta."), ext(name)))
                .setPositiveButton(android.R.string.ok, null).show();
        }
        return true;
    }

    private String displayName(Uri uri) {
        String n = null;
        try {
            android.database.Cursor c = a.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) { if (c.moveToFirst()) n = c.getString(0); c.close(); }
        } catch (RuntimeException ignored) {}
        return n != null ? n : String.valueOf(uri.getLastPathSegment());
    }

    /** Unpack (ModUnpack) a picked document (uri) or a GameBanana download (mod, file), then turn it on. */
    private void install(final String fileName, final Uri uri, final String title, final GameBanana.Mod mod, final GameBanana.FileItem file) {
        if (busy) return;
        busy = true;
        final AlertDialog dlg = progress(Txt.t("mods_reading", "Lendo o arquivo…"));
        new Thread(new Runnable() {
            @Override public void run() {
                String verdict;
                File tmp = null;
                try {
                    File dir = modsDir(a);
                    dir.mkdirs();
                    String name = ModStore.safeName(title != null ? title : ModStore.stripExt(fileName));
                    String result;
                    if (file != null) {
                        tmp = new File(a.getCacheDir(), "mod-" + file.id + ".bin");
                        GameBanana.download(file, tmp, null);
                        result = ModUnpack.unpack(tmp.getAbsolutePath(), -1, dir, name);
                    } else {
                        android.os.ParcelFileDescriptor pfd = a.getContentResolver().openFileDescriptor(uri, "r");
                        if (pfd == null) throw new java.io.IOException("cannot read the file");
                        result = ModUnpack.unpack(null, pfd.detachFd(), dir, name);
                    }
                    android.util.Log.i("lasrecomp", "[mods] unpack '" + fileName + "' -> " + result);
                    ModStore.Unpacked r = ModStore.Unpacked.parse(result);
                    if (r.ok && !r.mods.isEmpty()) {
                        for (String m : r.mods) {
                            java.util.Properties src = new java.util.Properties();
                            src.setProperty("file", fileName);
                            if (mod != null) {
                                src.setProperty("gamebanana.mod", String.valueOf(mod.id));
                                src.setProperty("gamebanana.file", String.valueOf(file.id));
                                src.setProperty("author", mod.author);
                                src.setProperty("page", mod.profileUrl);
                            }
                            ModStore.writeSource(dir.getParentFile(), m, src);
                        }
                        setActive(r.mods.get(0));
                        verdict = String.format(Txt.t("mods_installed_on", "%1$s instalado e ativo."), r.mods.get(0));
                        if (r.mods.size() > 1) verdict += "\n" + String.format(Txt.t("mods_variants", "Este mod tem %1$d versões"), r.mods.size());
                        if (r.code > 0) verdict += "\n" + Txt.t("mods_code_part", "Uma parte deste mod modifica o programa do jogo: essa parte não pode ser aplicada aqui. O resto (imagens, modelos, textos) será.");
                    } else {
                        verdict = failure(r, fileName);
                    }
                } catch (Throwable e) {
                    android.util.Log.w("lasrecomp", "[mods] install failed: " + e);
                    verdict = Txt.t("mods_fail_unreadable", "Arquivo ilegível: download incompleto, arquivo danificado ou protegido por senha. Baixe de novo.");
                } finally {
                    if (tmp != null) tmp.delete();
                }
                final String msg = verdict;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        dlg.dismiss();
                        busy = false;
                        refresh();
                        new AlertDialog.Builder(a).setTitle(fileName).setMessage(msg).setPositiveButton(android.R.string.ok, null).show();
                    }
                });
            }
        }, "mod-install").start();
    }

    private static String ext(String fileName) {
        return fileName != null && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** ModUnpack's refusal, said the screen spec's way (ECRAN-MODS.md §5). */
    private String failure(ModStore.Unpacked r, String fileName) {
        if (r.err == 2) return r.code > 0
            ? Txt.t("mods_fail_code_only", "Este mod só modifica o programa do jogo, e isso este port não sabe aplicar.")
            : String.format(Txt.t("mods_fail_not_mod", "Nada aqui substitui arquivos do jogo (contém: %1$s). Talvez seja uma gravação ou um guia."), fileName);
        if (r.err == 4) {
            android.os.StatFs fs = new android.os.StatFs(a.getFilesDir().getPath());
            return String.format(Txt.t("mods_fail_space", "Espaço insuficiente: precisa de %1$s, restam %2$s. Libere espaço e tente de novo."),
                "?", android.text.format.Formatter.formatShortFileSize(a, fs.getAvailableBytes()));
        }
        String ext = ext(fileName);
        if (r.err == 1 && !ext.isEmpty() && !ModUnpack.hasExtension(fileName))
            return String.format(Txt.t("mods_fail_format", "O formato .%1$s ainda não é suportado: descompacte o arquivo e adicione a pasta."), ext);
        return Txt.t("mods_fail_unreadable", "Arquivo ilegível: download incompleto, arquivo danificado ou protegido por senha. Baixe de novo.");
    }

    private void confirmRemove(final String name) {
        final File root = modsDir(a).getParentFile();
        long size = ModStore.size(new File(modsDir(a), name));
        new AlertDialog.Builder(a).setTitle(String.format(Txt.t("mods_remove_title", "Remover %1$s?"), name))
            .setMessage(String.format(Txt.t("mods_remove_body", "Os arquivos do mod são apagados do aparelho (%1$s liberados). Sua gravação não é alterada. Para usá-lo de novo, adicione o arquivo outra vez."),
                android.text.format.Formatter.formatShortFileSize(a, size)))
            .setPositiveButton(Txt.t("mods_remove", "Remover"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    boolean ok = ModStore.remove(root, name);
                    if (name.equals(active(a))) setActive("");
                    android.util.Log.i("lasrecomp", "[mods] removed '" + name + "': " + ok);
                    toast(String.format(Txt.t("mods_removed", "%1$s removido."), name));
                    refresh();
                }
            })
            .setNegativeButton(Txt.t("cancel", "Cancelar"), null).show();
    }

    // ------------------------------------------------------------------ CATALOGUE slot (GameBanana's list in the app)

    private void openCatalogue() {
        final AlertDialog wait = progress(Txt.t("mods_reading", "Lendo o arquivo…"));
        new Thread(new Runnable() {
            @Override public void run() {
                List<GameBanana.Mod> l = null;
                try { l = GameBanana.mods(); } catch (Exception e) { android.util.Log.w("lasrecomp", "[mods] catalogue: " + e); }
                final List<GameBanana.Mod> fl = l;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        wait.dismiss();
                        if (fl == null) { openUrl(GameBanana.SITE + "/games/" + GameBanana.GAME_ID); return; }
                        String[] rows = new String[fl.size()];
                        for (int i = 0; i < rows.length; i++) rows[i] = fl.get(i).name + "\n" + String.format(Txt.t("mods_by", "por %1$s"), fl.get(i).author);
                        new AlertDialog.Builder(a).setTitle(Txt.t("mods_browse", "Explorar mods"))
                            .setItems(rows, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) { showFiles(fl.get(w)); }
                            }).setNegativeButton(Txt.t("close", "Fechar"), null).show();
                    }
                });
            }
        }, "gb-list").start();
    }

    private void showFiles(final GameBanana.Mod m) {
        if (!m.installableInApp()) { openUrl(m.profileUrl); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                List<GameBanana.FileItem> f = null;
                try { f = GameBanana.files(m.id); } catch (Exception e) { android.util.Log.w("lasrecomp", "[mods] files: " + e); }
                final List<GameBanana.FileItem> ff = f;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ff == null || ff.isEmpty()) { openUrl(m.profileUrl); return; }
                        String[] rows = new String[ff.size()];
                        for (int i = 0; i < rows.length; i++) rows[i] = ff.get(i).name + "  ·  " + android.text.format.Formatter.formatShortFileSize(a, ff.get(i).size);
                        new AlertDialog.Builder(a).setTitle(m.name)
                            .setItems(rows, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    GameBanana.FileItem it = ff.get(w);
                                    if (it.problem() != null) { openUrl(m.profileUrl); return; }
                                    install(it.name, null, ff.size() > 1 ? m.name + " - " + ModStore.stripExt(it.name) : m.name, m, it);
                                }
                            })
                            .setNeutralButton(Txt.t("mods_open_gb", "Ver no GameBanana"), new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) { openUrl(m.profileUrl); }
                            })
                            .setNegativeButton(Txt.t("close", "Fechar"), null).show();
                    }
                });
            }
        }, "gb-files").start();
    }

    // ------------------------------------------------------------------ helpers

    private AlertDialog progress(String msg) {
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        LinearLayout v = new LinearLayout(a);
        v.setPadding(dp(24), dp(8), dp(24), dp(8));
        v.addView(bar, lp());
        AlertDialog d = new AlertDialog.Builder(a).setMessage(msg).setView(v).setCancelable(false).create();
        d.show();
        return d;
    }

    private void openUrl(String url) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException e) { toast(url); }
    }

    private void toast(String s) { android.widget.Toast.makeText(a, s, android.widget.Toast.LENGTH_LONG).show(); }

    private TextView text(String s, int color, int sp) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String s, final Runnable r) {
        Button b = new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setOnClickListener(new android.view.View.OnClickListener() { @Override public void onClick(android.view.View v) { r.run(); } });
        return b;
    }

    private Button amber(String s, Runnable r) {
        Button b = button(s, r);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(AMBER);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setTextColor(ON_AMBER);
        return b;
    }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    private static LinearLayout.LayoutParams lp() { return new LinearLayout.LayoutParams(-1, -2); }
}
