package com.letsgorecomp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.File;

// Main launcher: status, Play, and the log viewer/exporter.
// The SDL game runs in LetsGoActivity; if it crashes, this launcher survives
// (separate activity) and can still read the crashed run's logcat.
public class LetsGoLauncherActivity extends Activity {
    private static final int REQ_GAME_DIR = 1001;
    private static final int REQ_IMPORT_DRIVER = 1004;
    private static final String PREFS = "letsgo";
    private TextView status;
    private Button play, pick, forget, access, rebuild;

    /** The user-picked game folder, or null if not set (GameDir: kept across app updates). */
    public static String gameDir(android.content.Context ctx) {
        return GameDir.path(ctx);
    }

    /** The game folder's state (GameFolder.inspect): the chosen folder, or the app's own files when an in-app
     *  import of an earlier version wrote there. The app does not import game packages: the player brings the
     *  game already extracted (extracted/exefs/ + extracted/romfs/ or extracted/romfs.bin). */
    static GameFolder.Check gameFolder(android.content.Context ctx) {
        String dir = gameDir(ctx);
        File base = dir != null ? new File(dir) : ctx.getFilesDir();
        if (dir == null && !new File(base, "extracted").isDirectory()) return GameFolder.inspect(null);
        return GameFolder.inspect(base);
    }

    /** True only when the game can start: the game code in the APK, all-files access to the folder, and a
     *  complete game folder (GameFolder: exefs of Let's Go, and a whole romfs.bin or the romfs/ folder). */
    public static boolean gameDataReady(android.content.Context ctx) {
        File gameLib = new File(ctx.getApplicationInfo().nativeLibraryDir, "liblasgame.so");
        return gameLib.exists() && !GameDir.needsAllFilesAccess(ctx, gameDir(ctx)) && gameFolder(ctx).playable();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // Request full storage access (needed for direct fopen() of game files)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    Intent si = new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(si);
                } catch (Exception ignored) {}
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(20, 22, 28));
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Let's Go Recomp");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2));

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.rgb(180, 190, 200));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(24));
        root.addView(status, lp(-1, -2));

        play = new Button(this);
        play.setText("Jogar");
        play.setTextSize(18);
        play.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                // Never launch into a native crash: without complete game data
                // the runtime FATALs (see main.c boot()). Tell the user instead.
                if (!gameDataReady(LetsGoLauncherActivity.this)) {
                    refreshStatus();
                    android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                        "O jogo ainda não está pronto: veja o estado da pasta acima.",
                        android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                startActivity(new Intent(LetsGoLauncherActivity.this,
                                         LetsGoActivity.class));
            }
        });
        root.addView(play, lp(-1, -2));

        pick = new Button(this);
        pick.setText("Escolher a pasta do jogo");
        pick.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { pickGameDir(); }
        });
        LinearLayout.LayoutParams plp = lp(-1, -2);
        plp.topMargin = dp(12);
        root.addView(pick, plp);

        // All-files access: the runtime opens the game folder by path (fopen), the folder picker alone is not enough.
        access = new Button(this);
        access.setText("Permitir acesso a todos os arquivos");
        access.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { requestAllFilesAccess(); }
        });
        root.addView(access, lp(-1, -2));

        // A romfs.bin cut short (the first boot builds it from romfs/; stopping it midway leaves a short file that
        // the runtime would take as done): remove it so the next boot builds it again. Only offered with romfs/.
        rebuild = new Button(this);
        rebuild.setText("Refazer romfs.bin a partir de romfs/");
        rebuild.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { confirmRebuild(); }
        });
        root.addView(rebuild, lp(-1, -2));

        // Reversible: forget the folder (preference + persisted permission); the files stay where they are.
        forget = new Button(this);
        forget.setText("Esquecer esta pasta");
        forget.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                GameDir.forget(LetsGoLauncherActivity.this);
                refreshStatus();
                android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                    "Pasta esquecida. Os arquivos não foram apagados.", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        root.addView(forget, lp(-1, -2));

        Button log = new Button(this);
        log.setText("Ver log");
        log.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                startActivity(new Intent(LetsGoLauncherActivity.this,
                                         LogActivity.class));
            }
        });
        LinearLayout.LayoutParams llp = lp(-1, -2);
        llp.topMargin = dp(12);
        root.addView(log, llp);

        // ---- GPU driver (Turnip via adrenotools, like the Wonder port) ----
        android.widget.TextView drvTitle = new android.widget.TextView(this);
        drvTitle.setText("Driver GPU (Turnip)");
        drvTitle.setTextSize(16);
        drvTitle.setTextColor(Color.rgb(255, 255, 255));
        drvTitle.setPadding(0, dp(16), 0, dp(4));
        root.addView(drvTitle, lp(-1, -2));

        final android.widget.TextView drvStatus = new android.widget.TextView(this);
        drvStatus.setTextSize(13);
        drvStatus.setTextColor(Color.rgb(180, 190, 200));
        root.addView(drvStatus, lp(-1, -2));

        final Runnable refreshDrivers = new Runnable() {
            @Override public void run() {
                GpuDrivers.Driver sel = GpuDrivers.selected(LetsGoLauncherActivity.this);
                StringBuilder sb = new StringBuilder();
                if (sel != null) {
                    sb.append("Em uso: ").append(sel.name).append(" (").append(sel.driverVersion).append(")\n");
                } else {
                    sb.append("Em uso: driver do sistema\n");
                }
                java.util.List<GpuDrivers.Driver> all = GpuDrivers.list(LetsGoLauncherActivity.this);
                if (all.isEmpty()) {
                    sb.append("Nenhum driver Turnip instalado.");
                } else {
                    sb.append("Instalados: ");
                    for (int i = 0; i < all.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(all.get(i).name);
                    }
                }
                drvStatus.setText(sb.toString());
            }
        };
        refreshDrivers.run();

        // Install the bundled Turnip on first run (like Wonder).
        GpuDrivers.installBundled(this);
        refreshDrivers.run();

        Button importDrv = new Button(this);
        importDrv.setText("Importar driver Turnip (.zip)");
        importDrv.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                startActivityForResult(i, REQ_IMPORT_DRIVER);
            }
        });
        LinearLayout.LayoutParams idlp = lp(-1, -2);
        idlp.topMargin = dp(8);
        root.addView(importDrv, idlp);

        Button useSystem = new Button(this);
        useSystem.setText("Usar driver do sistema");
        useSystem.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                GpuDrivers.select(LetsGoLauncherActivity.this, null);
                refreshDrivers.run();
                android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                    "Driver do sistema selecionado.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(useSystem, lp(-1, -2));

        // Long-press a driver name in the status to select it (simple UI).
        drvStatus.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                java.util.List<GpuDrivers.Driver> all = GpuDrivers.list(LetsGoLauncherActivity.this);
                if (all.isEmpty()) return;
                // Cycle to the next driver (simple single-tap selection).
                GpuDrivers.Driver sel = GpuDrivers.selected(LetsGoLauncherActivity.this);
                int idx = -1;
                for (int i = 0; i < all.size(); i++) {
                    if (sel != null && all.get(i).slug.equals(sel.slug)) { idx = i; break; }
                }
                GpuDrivers.Driver next = all.get((idx + 1) % all.size());
                GpuDrivers.select(LetsGoLauncherActivity.this, next);
                refreshDrivers.run();
                android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                    "Driver selecionado: " + next.name, android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // ---- Game settings (written to larecomp.ini in the game folder) ----
        TextView cfgTitle = new TextView(this);
        cfgTitle.setText("Configurações");
        cfgTitle.setTextSize(18);
        cfgTitle.setTextColor(Color.WHITE);
        cfgTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(cfgTitle, lp(-1, -2));

        final CheckBox fps60 = new CheckBox(this);
        fps60.setText("60 fps (experimental)");
        fps60.setTextColor(Color.rgb(200, 210, 220));
        root.addView(fps60, lp(-1, -2));

        final CheckBox smaa = new CheckBox(this);
        smaa.setText("Suavizar serrilhado (SMAA)");
        smaa.setTextColor(Color.rgb(200, 210, 220));
        smaa.setChecked(true);
        root.addView(smaa, lp(-1, -2));

        // Resolution scale selector (emulator-style)
        TextView resLabel = new TextView(this);
        resLabel.setText("Resolução interna:");
        resLabel.setTextColor(Color.rgb(200, 210, 220));
        root.addView(resLabel, lp(-1, -2));

        final android.widget.Spinner resSpinner = new android.widget.Spinner(this);
        final String[] resNames = {"0.5x (480p) - Performance", "0.75x (540p) - Equilibrado", "1x (720p) - Padrão", "2x (1080p) - Qualidade"};
        final String[] resValues = {"0.4444", "0.5", "0.6667", "1.0"};
        android.widget.ArrayAdapter<String> resAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, resNames);
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resSpinner.setAdapter(resAdapter);
        // Load saved preference (default 1x = 720p)
        String savedRes = getSharedPreferences("letsgo", MODE_PRIVATE).getString("resolution_scale", "0.6667");
        for (int i = 0; i < resValues.length; i++) {
            if (resValues[i].equals(savedRes)) {
                resSpinner.setSelection(i);
                break;
            }
        }
        resSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                getSharedPreferences("letsgo", MODE_PRIVATE).edit()
                        .putString("resolution_scale", resValues[position]).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(resSpinner, lp(-1, -2));

        // Game language: the game's RomFS has every language's texts (bin/message/<language>/: English, French,
        // Spanish...), and a dump from any region is the same game. The runtime tells the game larecomp.ini's
        // game_lang (runtime/mods.c game_language); empty = English, which is also what a fan translation that
        // replaces the English/ folder (PT-BR) needs. An installed mod that carries its own language still wins.
        TextView langLabel = new TextView(this);
        langLabel.setText("Idioma do jogo:");
        langLabel.setTextColor(Color.rgb(200, 210, 220));
        root.addView(langLabel, lp(-1, -2));

        final android.widget.Spinner langSpinner = new android.widget.Spinner(this);
        final String[] langNames = {"Padrão (inglês / tradução PT-BR)", "English", "Français", "Español", "Deutsch",
                                    "Italiano", "日本語", "한국어", "简体中文", "繁體中文"};
        final String[] langValues = {"", "en-US", "fr", "es", "de", "it", "ja", "ko", "zh-CN", "zh-TW"};
        android.widget.ArrayAdapter<String> langAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, langNames);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(langAdapter);
        String savedLang = readIniValue("game_lang");
        for (int i = 0; i < langValues.length; i++) {
            if (langValues[i].equals(savedLang == null ? "" : savedLang)) {
                langSpinner.setSelection(i);
                break;
            }
        }
        langSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String cur = readIniValue("game_lang");
                if (langValues[position].equals(cur == null ? "" : cur)) return;  // (the initial selection)
                java.util.LinkedHashMap<String, String> kv = new java.util.LinkedHashMap<>();
                kv.put("game_lang", langValues[position]);
                updateIni(kv);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(langSpinner, lp(-1, -2));

        final CheckBox wild = new CheckBox(this);
        wild.setText("Batalhas selvagens (experimental)");
        wild.setTextColor(Color.rgb(200, 210, 220));
        root.addView(wild, lp(-1, -2));

        final CheckBox touch = new CheckBox(this);
        touch.setText("Controles touch na tela");
        touch.setTextColor(Color.rgb(200, 210, 220));
        touch.setChecked(getSharedPreferences("letsgo", MODE_PRIVATE).getBoolean("touch", true));
        root.addView(touch, lp(-1, -2));
        touch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean c) {
                getSharedPreferences("letsgo", MODE_PRIVATE).edit().putBoolean("touch", c).apply();
            }
        });

        // load saved settings
        String[] ini = readIni();
        if (ini != null) {
            fps60.setChecked("60".equals(ini[0]));
            smaa.setChecked("1".equals(ini[1]));
            wild.setChecked("1".equals(ini[2]));
        }

        android.view.View.OnClickListener saveIni = new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                writeIni(fps60.isChecked() ? "60" : "30",
                         smaa.isChecked() ? "1" : "0",
                         wild.isChecked() ? "1" : "0");
            }
        };
        fps60.setOnClickListener(saveIni);
        smaa.setOnClickListener(saveIni);
        wild.setOnClickListener(saveIni);

        TextView hint = new TextView(this);
        hint.setText("Se o jogo fechar ao abrir, use \"Ver log\" e me envie o conteúdo.");
        hint.setTextSize(13);
        hint.setTextColor(Color.rgb(140, 150, 160));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(24), 0, 0);
        root.addView(hint, lp(-1, -2));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // the state can change outside the app: files copied, all-files access granted in Settings, card removed
        refreshStatus();
    }

    /** The system folder picker, opened on the current folder when there is one. */
    private void pickGameDir() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        android.net.Uri cur = GameDir.uri(this);
        if (cur != null && android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    android.provider.DocumentsContract.buildDocumentUriUsingTree(cur,
                        android.provider.DocumentsContract.getTreeDocumentId(cur)));
            } catch (RuntimeException ignored) {}
        }
        try {
            startActivityForResult(i, REQ_GAME_DIR);
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "Este aparelho não tem seletor de pastas.",
                android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void requestAllFilesAccess() {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void confirmRebuild() {
        final GameFolder.Check c = gameFolder(this);
        if (c.state != GameFolder.State.ROMFS_BIN_BROKEN || !c.hasRomfsDir) { refreshStatus(); return; }
        final File bin = new File(c.root, "extracted/romfs.bin");
        new android.app.AlertDialog.Builder(this)
            .setTitle("Refazer romfs.bin")
            .setMessage("romfs.bin está incompleto (" + mib(c.romfsBinBytes) + " de " + mib(c.romfsBinNeeded)
                + "). Ele será apagado e refeito a partir da pasta romfs/ ao abrir o jogo (alguns minutos). "
                + "A pasta romfs/ e o resto não são tocados.")
            .setPositiveButton("Apagar e refazer", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    boolean ok = bin.delete();
                    android.util.Log.i("lasrecomp", "[folder] removed short romfs.bin (" + c.romfsBinBytes + " of "
                        + c.romfsBinNeeded + " bytes): " + ok);
                    refreshStatus();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private static String mib(long b) {
        return String.format(java.util.Locale.ROOT, "%.0f MiB", b / 1048576.0);
    }

    private void refreshStatus() {
        if (status == null) return;
        String dir = gameDir(this);
        GameFolder.Check c = gameFolder(this);
        boolean needAccess = GameDir.needsAllFilesAccess(this, dir);
        boolean gameLib = new File(getApplicationInfo().nativeLibraryDir, "liblasgame.so").exists();
        String where = dir != null ? "Pasta: " + dir : "";
        String text;
        if (needAccess) {
            text = "O app precisa do acesso a todos os arquivos para ler a pasta do jogo.\n" + where;
        } else {
            text = describe(c) + (where.isEmpty() ? "" : "\n" + where);
        }
        if (!gameLib) text += "\nEste APK não tem o código do jogo (liblasgame.so).";
        status.setText(text);
        boolean ready = gameLib && !needAccess && c.playable();
        android.util.Log.i("lasrecomp", "[folder] state " + c.state + (needAccess ? " (no all-files access)" : "")
            + " dir=" + dir + " persisted=" + persistedTrees());
        if (play != null) {
            play.setEnabled(ready);
            play.setAlpha(ready ? 1f : 0.4f);
        }
        if (pick != null) pick.setText(dir == null ? "Escolher a pasta do jogo" : "Escolher outra pasta");
        if (forget != null) forget.setVisibility(dir != null ? android.view.View.VISIBLE : android.view.View.GONE);
        if (access != null) access.setVisibility(needAccess ? android.view.View.VISIBLE : android.view.View.GONE);
        if (rebuild != null) rebuild.setVisibility(!needAccess && c.state == GameFolder.State.ROMFS_BIN_BROKEN
            && c.hasRomfsDir ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    /** What the player sees for each state of the folder: what is wrong and what to do. */
    static String describe(GameFolder.Check c) {
        switch (c.state) {
            case NOT_SET:
                return "Escolha a pasta do jogo já extraído: a pasta que contém extracted/exefs/ e "
                    + "extracted/romfs/ (ou extracted/romfs.bin).";
            case MISSING:
                return "A pasta escolhida não existe mais (movida, renomeada ou cartão retirado). "
                    + "Escolha-a de novo.";
            case UNREADABLE:
                return "A pasta escolhida não pode ser lida.";
            case NO_EXTRACTED:
                return "Esta pasta não tem extracted/. Escolha a pasta que contém extracted/exefs/ e "
                    + "extracted/romfs/.";
            case LOOSE:
                return "exefs/ e romfs/ estão direto nesta pasta: coloque-os numa subpasta chamada extracted/ "
                    + "(extracted/exefs/, extracted/romfs/) e escolha esta pasta de novo.";
            case NO_EXEFS:
                return "extracted/exefs/ está incompleto: faltam main e main.npdm (ou main não é um NSO).";
            case UNKNOWN_GAME:
                return "extracted/exefs/ não é de Pokémon: Let's Go, Eevee! nem Pikachu!.";
            case IMPORT_PARTIAL:
                return "Uma importação foi interrompida nesta pasta (.import/ sem .import_ok): os arquivos "
                    + "podem estar incompletos.";
            case NO_ROMFS:
                return (c.game != null ? c.game + ": " : "") + "falta a RomFS. Copie extracted/romfs/ "
                    + "(com bin/message dentro) ou extracted/romfs.bin.";
            case ROMFS_DIR_INCOMPLETE:
                return (c.game != null ? c.game + ": " : "") + "extracted/romfs/ está incompleto (falta "
                    + c.romfsMissing + "/). Copie a pasta romfs/ inteira de novo.";
            case ROMFS_BIN_BROKEN:
                return (c.game != null ? c.game + ": " : "") + "extracted/romfs.bin está incompleto ("
                    + mib(Math.max(0, c.romfsBinBytes)) + (c.romfsBinNeeded > 0 ? " de " + mib(c.romfsBinNeeded) : "")
                    + "). " + (c.hasRomfsDir ? "Ele pode ser refeito a partir de romfs/."
                    : "Copie-o de novo.");
            case READ_ONLY:
                return "A pasta não pode ser gravada (saves, configurações, cache). Escolha uma pasta do "
                    + "armazenamento interno.";
            case READY:
                return "Jogo pronto para iniciar: " + c.game + ".";
            case READY_FIRST_RUN:
                return "Jogo pronto: " + c.game + ". Na primeira abertura, romfs.bin é montado a partir de "
                    + "romfs/ (alguns minutos, ~4,5 GB livres).";
        }
        return String.valueOf(c.state);
    }

    private String persistedTrees() {
        StringBuilder b = new StringBuilder("[");
        try {
            for (android.content.UriPermission up : getContentResolver().getPersistedUriPermissions())
                b.append(b.length() > 1 ? ", " : "").append(GameDir.pathOf(up.getUri()))
                 .append(up.isWritePermission() ? " rw" : " r");
        } catch (RuntimeException ignored) {}
        return b.append("]").toString();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_GAME_DIR && res == RESULT_OK && data != null
                && data.getData() != null) {
            android.net.Uri uri = data.getData();
            // keep the folder across restarts and app updates (GameDir: preference + persisted permission)
            String path = GameDir.choose(this, uri, data.getFlags());
            if (path == null) {
                android.widget.Toast.makeText(this, "Escolha a pasta pelo armazenamento do aparelho "
                    + "(armazenamento interno ou cartão SD), não por \"Recentes\" nem por um app de nuvem.",
                    android.widget.Toast.LENGTH_LONG).show();
            }
            refreshStatus();
            if (path != null && !gameDataReady(this))
                android.widget.Toast.makeText(this, status.getText(), android.widget.Toast.LENGTH_LONG).show();
        } else if (req == REQ_IMPORT_DRIVER && res == RESULT_OK
                && data != null && data.getData() != null) {
            GpuDrivers.ImportResult r = GpuDrivers.importZip(this, data.getData());
            if (r.error != null) {
                android.widget.Toast.makeText(this, "Erro: " + r.error,
                        android.widget.Toast.LENGTH_LONG).show();
            } else {
                GpuDrivers.select(this, r.driver);
                android.widget.Toast.makeText(this, "Driver instalado: " + r.driver.name,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            // Refresh the driver status (recreate is simplest).
            recreate();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private File iniFile() {
        String dir = gameDir(this);
        return new File(dir != null ? dir : getFilesDir().getAbsolutePath(), "larecomp.ini");
    }

    /** One key's value in larecomp.ini ("key = value", the runtime's format), or null if absent. */
    private String readIniValue(String key) {
        try {
            File ini = iniFile();
            if (!ini.exists()) return null;
            String val = null;
            java.util.Scanner sc = new java.util.Scanner(ini, "UTF-8");
            while (sc.hasNextLine()) {
                String l = sc.nextLine().trim();
                int eq = l.indexOf('=');
                if (eq > 0 && l.substring(0, eq).trim().equals(key)) val = l.substring(eq + 1).trim();
            }
            sc.close();
            return val;
        } catch (Exception e) { return null; }
    }

    /** Read fps/smaa/wild from larecomp.ini in the game folder, or null. */
    private String[] readIni() {
        if (!iniFile().exists()) return null;
        String fps = readIniValue("fps"), smaa = readIniValue("smaa"), wild = readIniValue("wild_battle");
        return new String[]{fps != null ? fps : "30", smaa != null ? smaa : "1", wild != null ? wild : "0"};
    }

    /** Write the launcher's game settings to larecomp.ini in the game folder. */
    private void writeIni(String fps, String smaa, String wild) {
        java.util.LinkedHashMap<String, String> kv = new java.util.LinkedHashMap<>();
        kv.put("fps", fps);
        kv.put("smaa", smaa);
        kv.put("wild_battle", wild);
        kv.put("fullscreen", "0");
        updateIni(kv);
    }

    /** Set some keys of larecomp.ini, keeping every other line (game_lang, mod, render_height, controller
     *  bindings... the runtime writes them too): the file used to be rewritten with only the launcher's keys. */
    private void updateIni(java.util.Map<String, String> kv) {
        try {
            File ini = iniFile();
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (ini.exists()) {
                java.util.Scanner sc = new java.util.Scanner(ini, "UTF-8");
                while (sc.hasNextLine()) lines.add(sc.nextLine());
                sc.close();
            } else {
                lines.add("# Let's Go Recomp settings (Android launcher)");
            }
            java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(ini), "UTF-8");
            for (String l : mergeIni(lines, kv)) w.write(l + "\n");
            w.close();
        } catch (Exception ignored) {}
    }

    /** lines with kv's keys set ("key = value", in place; the missing ones appended), every other line unchanged. */
    static java.util.List<String> mergeIni(java.util.List<String> lines, java.util.Map<String, String> kv) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.Set<String> done = new java.util.HashSet<>();
        for (String l : lines) {
            int eq = l.indexOf('=');
            String k = eq > 0 ? l.substring(0, eq).trim() : null;
            if (k != null && kv.containsKey(k)) {
                if (done.add(k)) out.add(k + " = " + kv.get(k));  // (a duplicated key: kept once)
            } else {
                out.add(l);
            }
        }
        for (java.util.Map.Entry<String, String> e : kv.entrySet())
            if (!done.contains(e.getKey())) out.add(e.getKey() + " = " + e.getValue());
        return out;
    }
}
