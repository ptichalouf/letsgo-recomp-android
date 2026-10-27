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
//   - the installed mods in the player's LOAD ORDER (mods/.load-order, ModStore.order), one row each: on/off, up,
//     down, "Remove". Several can be on; a file two active mods both have is served from the higher one (mods.c
//     mod_romfs_find), and the screen names every such file and the two mods (ModStore.conflicts);
//   - one amber button, the SLOT (screen spec §3), whose action is NOT decided here: FILE (add a downloaded
//     .zip/.rar/.7z, the default), LINK (GameBanana in the browser) or CATALOGUE (GameBanana's list in the app).
//     Alexandre decides; to try one without rebuilding: adb shell setprop debug.letsgo.mods_primary link|catalogue
//   - installing = ModUnpack (libmodunpack.so) into <filesDir>/mods, the folder the runtime reads (LARECOMP_MODS_DIR);
//     the active mods, in order, are handed to the runtime as LARECOMP_MODS="a|b" (LetsGoActivity, activeList).
//     The player's game folder is never written.
final class ModsPanel {
    static final int REQ_MOD_FILE = 1005;
    static final String PREF_MOD = "mod";  // (the one-mod screen's choice: read once, to seed the order)
    enum Primary { FILE, LINK, CATALOGUE }


    private final Activity a;
    private LinearLayout list, slot;
    private boolean busy;
    private String failedFile, failedMessage;

    ModsPanel(Activity a) { this.a = a; }

    static File modsDir(android.content.Context c) { return ModUnpack.dir(c); }

    /** The installed mods in the player's order, each on or off (mods/.load-order). */
    static List<ModStore.Entry> order(android.content.Context c) {
        return ModStore.order(modsDir(c).getParentFile(), c.getSharedPreferences("letsgo", Activity.MODE_PRIVATE).getString(PREF_MOD, ""));
    }

    /** The active mods, highest priority first ("a|b", "" = none): what LetsGoActivity hands over as LARECOMP_MODS. */
    static String activeList(android.content.Context c) { return ModStore.activeList(order(c)); }

    private void save(List<ModStore.Entry> l) {
        try { ModStore.saveOrder(modsDir(a).getParentFile(), l); }
        catch (java.io.IOException e) { android.util.Log.w("lasrecomp", "[mods] order not saved: " + e); }
        android.util.Log.i("lasrecomp", "[mods] order = " + l + " -> LARECOMP_MODS='" + ModStore.activeList(l) + "'");
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
        section.addView(text(Txt.t("mods_what", "Um mod troca imagens, modelos ou textos do jogo, sem mexer na sua gravação."), Theme.color(a, "text2"), 13));
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        section.addView(list, lp());
        section.addView(text(Txt.t("mods_order_rule", "Ordem de carregamento: se dois mods ativos trocam o mesmo arquivo, vale o que está mais acima. Use ▲ ▼ para mudar."), Theme.color(a, "text3"), 12));
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
            slot.addView(text(Txt.t("mods_gb_then", "Baixe o arquivo e volte aqui para adicioná-lo."), Theme.color(a, "text3"), 12));
        } else {
            slot.addView(amber(Txt.t("mods_browse", "Explorar mods"), new Runnable() { @Override public void run() { openCatalogue(); } }));
            slot.addView(button(Txt.t("mods_add_file", "Adicionar um arquivo baixado"), addFile));
        }
        if (p != Primary.LINK) {
            slot.addView(text(Txt.t("mods_formats", "Arquivo .zip, .rar ou .7z baixado, ou pasta já descompactada."), Theme.color(a, "text3"), 12));
            Button link = button(Txt.t("mods_gb_link", "Página do jogo no GameBanana"), site);
            link.setBackground(Theme.background(a, Color.TRANSPARENT, Theme.R_CTL, false));
            link.setTextColor(Theme.color(a, "primary_text"));
            slot.addView(link);
        }
    }

    void refresh() {
        if (list == null) return;
        list.removeAllViews();
        File root = modsDir(a).getParentFile();
        final List<ModStore.Entry> order = order(a);
        long used = 0;
        for (ModStore.Entry item : order) used += ModStore.size(new File(modsDir(a), item.name));
        LinearLayout summary = Theme.statusBar(a, String.format(Txt.t("mods_summary", ""), order.size(),
                android.text.format.Formatter.formatShortFileSize(a, used),
                android.text.format.Formatter.formatShortFileSize(a, a.getFilesDir().getUsableSpace())), null);
        list.addView(summary, lp());
        if (order.isEmpty()) list.addView(text(Txt.t("mods_empty", "Nenhum mod instalado."), Theme.color(a, "text2"), 14));
        int rank = 0;
        for (int i = 0; i < order.size(); i++) {
            final ModStore.Entry e = order.get(i);
            final String m = e.name;
            java.util.Properties src = ModStore.source(root, m);
            String author = src.getProperty("author", "");
            String meta = (e.on ? String.format(Txt.t("mods_on_rank", "Ativo · nº %1$d"), ++rank) : Txt.t("mods_off", "Inativo")) + " · "
                + (author.isEmpty() ? Txt.t("mods_by_unknown", "autor não informado") : String.format(Txt.t("mods_by", "por %1$s"), author))
                + " · " + (src.getProperty("gamebanana.mod", "").isEmpty() ? Txt.t("mods_src_file", "arquivo importado") : Txt.t("mods_src_gb", "GameBanana"));
            LinearLayout tile = Theme.tile(a);
            LinearLayout row = new LinearLayout(a);
            row.setMinimumHeight(dp(64));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            Switch sw = new Switch(a);
            sw.setText(m + "\n" + meta + " · " + android.text.format.Formatter.formatShortFileSize(a, ModStore.size(new File(modsDir(a), m))));
            sw.setTextColor(Theme.color(a, "text"));
            Theme.toggle(a, sw);
            sw.setChecked(e.on);
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                    e.on = on;
                    save(order);
                    toast(Txt.t("mods_next_play", "Vale a partir do próximo Jogar."));
                    list.post(new Runnable() { @Override public void run() { refresh(); } });
                }
            });
            row.addView(sw, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(arrow("▲", String.format(Txt.t("mods_up", "Subir %1$s"), m), i > 0, m, -1, order), new LinearLayout.LayoutParams(dp(48), -2));
            row.addView(arrow("▼", String.format(Txt.t("mods_down", "Descer %1$s"), m), i < order.size() - 1, m, 1, order), new LinearLayout.LayoutParams(dp(48), -2));
            Button rm = button(Txt.t("mods_remove", "Remover"), new Runnable() { @Override public void run() { confirmRemove(m); } });
            rm.setTextColor(Theme.color(a, "error"));
            rm.setBackground(Theme.background(a, Color.TRANSPARENT, Theme.R_CTL, false));
            row.addView(rm, new LinearLayout.LayoutParams(-2, -2));
            // Stack actions below the name on narrow screens; a long author must not hide the switch.
            if (a.getResources().getConfiguration().screenWidthDp < 600) {
                row.removeView(sw);
                tile.addView(sw, lp());
            }
            tile.addView(row, lp());
            LinearLayout.LayoutParams tileParams = lp();
            tileParams.bottomMargin = dp(Theme.S2);
            list.addView(tile, tileParams);
        }
        if (failedMessage != null) {
            LinearLayout failed = Theme.tile(a);
            failed.addView(Theme.sectionHeader(a, Txt.t("mods_failed", "")), lp());
            failed.addView(Theme.label(a, failedFile + "\n" + failedMessage, Theme.BODY, "error", false), lp());
            failed.addView(button(Txt.t("mods_dismiss", ""), () -> { failedMessage = null; refresh(); }), lp());
            list.addView(failed, lp());
        }
        // Two active mods that write the same file: say which file, which two, and which one the game gets.
        List<ModStore.Conflict> cs = ModStore.conflicts(root, ModStore.active(order));
        if (!cs.isEmpty()) {
            list.addView(text(String.format(Txt.t("mods_conflicts", "Conflitos: %1$d arquivo(s) trocado(s) por dois mods ativos"), cs.size()), Theme.color(a, "primary_text"), 14));
            for (int i = 0; i < cs.size() && i < 6; i++) {
                ModStore.Conflict c = cs.get(i);
                android.util.Log.i("lasrecomp", "[mods] conflict on screen: " + c.path + ": '" + c.winner + "' over '" + c.loser + "'");
                list.addView(text(String.format(Txt.t("mods_conflict", "%1$s: está em %2$s e em %3$s. Vale %2$s (mais acima)."), c.path, c.winner, c.loser), Theme.color(a, "primary_text"), 12));
            }
            if (cs.size() > 6) list.addView(text(String.format(Txt.t("mods_conflicts_more", "… e mais %1$d."), cs.size() - 6), Theme.color(a, "primary_text"), 12));
        }
    }

    private Button arrow(String glyph, String desc, boolean enabled, final String name, final int delta, final List<ModStore.Entry> order) {
        Button b = button(glyph, new Runnable() {
            @Override public void run() {
                if (ModStore.move(order, name, delta)) { save(order); refresh(); }
            }
        });
        b.setContentDescription(desc);
        b.setPadding(0, 0, 0, 0);
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.3f);
        b.setBackground(Theme.background(a, Color.TRANSPARENT, Theme.R_CTL, false));
        b.setTextColor(Theme.color(a, "text"));
        return b;
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
                boolean installed = false;
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
                        installed = true;
                        List<ModStore.Entry> order = order(a);
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
                        ModStore.addInstalled(order, r.mods.get(0));  // (on, last when new; the other variants stay off)
                        save(order);
                        verdict = String.format(Txt.t("mods_installed_on", "%1$s instalado e ativo."), r.mods.get(0));
                        if (ModStore.active(order).size() > 1) verdict += "\n" + String.format(Txt.t("mods_installed_rank",
                            "Ele fica em nº %1$d na ordem: os mods acima dele valem nos arquivos em comum."), ModStore.active(order).indexOf(r.mods.get(0)) + 1);
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
                final boolean success = installed;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        dlg.dismiss();
                        busy = false;
                        if (!success) { failedFile = fileName; failedMessage = msg; }
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
                    save(order(a));  // (gone from the order; the others keep their places relative to each other)
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
        bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Theme.color(a, "primary")));
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
        Button b = Theme.secondaryButton(a, s);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(Theme.TOUCH_MIN));
        b.setOnClickListener(new android.view.View.OnClickListener() { @Override public void onClick(android.view.View v) { r.run(); } });
        return b;
    }

    private Button amber(String s, Runnable r) {
        Button b = button(s, r);
        b.setBackground(Theme.background(a, Theme.color(a, "primary"), Theme.R_CTL, false));
        b.setTextColor(Theme.textColors(a, Theme.color(a, "on_primary")));
        return b;
    }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    private static LinearLayout.LayoutParams lp() { return new LinearLayout.LayoutParams(-1, -2); }
}
