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
    private TextView drvDevice;
    private TextView drvStatus;
    private GpuInfo gpu;            // detected in the background at start
    private boolean driverBusy;     // a check or a download is running

    /** The user-picked game folder, or null if not set (GameDir: kept across app updates). */
    public static String gameDir(android.content.Context ctx) {
        return GameDir.path(ctx);
    }

    /** The game folder's state (GameFolder.inspect): the chosen folder, or the app's own files when an in-app
     *  import of an earlier version wrote there. The app does not import game packages: the player brings the
     *  game already extracted (extracted/exefs/ + extracted/romfs/ or extracted/romfs.bin). */
    static GameFolder.Check gameFolder(android.content.Context ctx) {
        return gameFolder(ctx, gameDir(ctx));
    }

    private static GameFolder.Check gameFolder(android.content.Context ctx, String dir) {
        File base = dir != null ? new File(dir) : ctx.getFilesDir();
        if (dir == null && !new File(base, "extracted").isDirectory()) return GameFolder.inspect(null);
        return GameFolder.inspect(base);
    }

    /** Everything that decides whether "Play" can start the game, computed once. The launcher's screen, the Play
     *  button, the log line and the game's data root (LetsGoActivity -> LARECOMP_DATA_ROOT) all read this one
     *  object, so the screen cannot say "choose the folder" while the runtime is handed a ready folder. */
    static final class Launch {
        final String dir;              // the chosen folder (null: none chosen)
        final String dataRoot;         // what the runtime gets: dir, or the app's files when none is chosen
        final GameFolder.Check folder;
        final boolean needAccess, gameLib, ready;

        Launch(android.content.Context ctx) {
            dir = gameDir(ctx);
            dataRoot = dir != null ? dir : ctx.getFilesDir().getAbsolutePath();
            folder = gameFolder(ctx, dir);
            needAccess = GameDir.needsAllFilesAccess(ctx, dir);
            gameLib = new File(ctx.getApplicationInfo().nativeLibraryDir, "liblasgame.so").exists();
            ready = gameLib && !needAccess && folder.playable();
        }

        String summary() {
            return "state " + folder.state + " ready=" + ready + " gameLib=" + gameLib + " allFiles=" + !needAccess
                + " dir=" + dir + " root=" + dataRoot;
        }
    }

    static Launch launch(android.content.Context ctx) { return new Launch(ctx); }

    /** True only when the game can start: the game code in the APK, all-files access to the folder, and a
     *  complete game folder (GameFolder: exefs of Let's Go, and a whole romfs.bin or the romfs/ folder). */
    public static boolean gameDataReady(android.content.Context ctx) {
        return launch(ctx).ready;
    }

    // Every launcher on screen (the system can keep more than one: a launch from the home screen over a task that
    // already has one, a fold/unfold). Whatever changes the folder refreshes all of them, not just the one that asked.
    private static final java.util.Set<LetsGoLauncherActivity> LIVE =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<LetsGoLauncherActivity, Boolean>());

    static void refreshAll() {
        for (LetsGoLauncherActivity a : new java.util.ArrayList<>(LIVE)) a.refreshStatus();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Opened from the home screen while the task already has a launcher (or the game): bring that one back
        // instead of stacking a second launcher whose screen would lag behind the first.
        if (!isTaskRoot() && getIntent() != null && Intent.ACTION_MAIN.equals(getIntent().getAction())
                && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)) {
            android.util.Log.i("lasrecomp", "[folder] launcher already open in this task: not stacking another");
            finish();
            return;
        }
        LIVE.add(this);
        Txt.init(this);  // the phone's language for every word shown (res/values-en, values-fr; PT by default)

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
        play.setText(Txt.t("play", "Jogar"));
        play.setTextSize(18);
        play.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                // Never launch into a native crash: without complete game data
                // the runtime FATALs (see main.c boot()). Tell the user instead.
                Launch st = refreshStatus();
                if (st == null || !st.ready) {
                    android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                        Txt.t("not_ready", "O jogo ainda não está pronto: veja o estado da pasta acima."),
                        android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                startActivity(new Intent(LetsGoLauncherActivity.this,
                                         LetsGoActivity.class));
            }
        });
        root.addView(play, lp(-1, -2));

        pick = new Button(this);
        pick.setText(Txt.t("sel_folder", "Escolher a pasta do jogo"));
        pick.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { pickGameDir(); }
        });
        LinearLayout.LayoutParams plp = lp(-1, -2);
        plp.topMargin = dp(12);
        root.addView(pick, plp);

        // All-files access: the runtime opens the game folder by path (fopen), the folder picker alone is not enough.
        access = new Button(this);
        access.setText(Txt.t("allow_all", "Permitir acesso a todos os arquivos"));
        access.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { requestAllFilesAccess(); }
        });
        root.addView(access, lp(-1, -2));

        // A romfs.bin cut short (the first boot builds it from romfs/; stopping it midway leaves a short file that
        // the runtime would take as done): remove it so the next boot builds it again. Only offered with romfs/.
        rebuild = new Button(this);
        rebuild.setText(Txt.t("rebuild_btn", "Refazer romfs.bin a partir de romfs/"));
        rebuild.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { confirmRebuild(); }
        });
        root.addView(rebuild, lp(-1, -2));

        // Reversible: forget the folder (preference + persisted permission); the files stay where they are.
        forget = new Button(this);
        forget.setText(Txt.t("forget", "Esquecer esta pasta"));
        forget.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                GameDir.forget(LetsGoLauncherActivity.this);
                refreshAll();
                android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                    Txt.t("forgot", "Pasta esquecida. Os arquivos não foram apagados."), android.widget.Toast.LENGTH_LONG).show();
            }
        });
        root.addView(forget, lp(-1, -2));

        Button log = new Button(this);
        log.setText(Txt.t("ver_log", "Ver log"));
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
        // Nothing is shipped in the APK: the launcher detects the phone and, when asked, downloads the package the
        // published rules recommend for it from the packager's GitHub releases (DriverCatalog), checks its sha256,
        // and puts it on trial until it has brought the game to its first frames (GpuDrivers). Importing a .zip by
        // hand stays (no network, or a preferred build).
        android.widget.TextView drvTitle = new android.widget.TextView(this);
        drvTitle.setText(Txt.t("gpu", "Driver GPU (Turnip)"));
        drvTitle.setTextSize(16);
        drvTitle.setTextColor(Color.rgb(255, 255, 255));
        drvTitle.setPadding(0, dp(16), 0, dp(4));
        root.addView(drvTitle, lp(-1, -2));

        drvDevice = new android.widget.TextView(this);
        drvDevice.setTextSize(12);
        drvDevice.setTextColor(Color.rgb(150, 160, 170));
        drvDevice.setText(Txt.t("detecting", "Detectando o aparelho..."));
        root.addView(drvDevice, lp(-1, -2));

        drvStatus = new android.widget.TextView(this);
        drvStatus.setTextSize(13);
        drvStatus.setTextColor(Color.rgb(180, 190, 200));
        root.addView(drvStatus, lp(-1, -2));
        refreshDrivers();
        new Thread(new Runnable() {
            @Override public void run() {
                final GpuInfo g = GpuInfo.detect();
                android.util.Log.i("lasrecomp", "[gpu] " + g);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        gpu = g;
                        drvDevice.setText(g.summary());
                    }
                });
            }
        }, "gpu-detect").start();

        Button autoDrv = new Button(this);
        autoDrv.setText(Txt.t("dl_recommended", "Baixar o driver recomendado para este aparelho"));
        autoDrv.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                recommendDriver(false);
            }
        });
        LinearLayout.LayoutParams adlp = lp(-1, -2);
        adlp.topMargin = dp(8);
        root.addView(autoDrv, adlp);

        Button importDrv = new Button(this);
        importDrv.setText(Txt.t("import_driver", "Importar driver Turnip (.zip)"));
        importDrv.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                startActivityForResult(i, REQ_IMPORT_DRIVER);
            }
        });
        root.addView(importDrv, lp(-1, -2));

        Button useSystem = new Button(this);
        useSystem.setText(Txt.t("use_system", "Usar driver do sistema"));
        useSystem.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                GpuDrivers.select(LetsGoLauncherActivity.this, null);
                refreshDrivers();
                android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                    Txt.t("driver_sys", "Driver do sistema selecionado."), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(useSystem, lp(-1, -2));

        // Tap the status to cycle through the installed drivers (simple selection UI).
        drvStatus.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                java.util.List<GpuDrivers.Driver> all = GpuDrivers.list(LetsGoLauncherActivity.this);
                if (all.isEmpty()) return;
                GpuDrivers.Driver sel = GpuDrivers.selected(LetsGoLauncherActivity.this);
                int idx = -1;
                for (int i = 0; i < all.size(); i++) {
                    if (sel != null && all.get(i).slug.equals(sel.slug)) { idx = i; break; }
                }
                GpuDrivers.Driver next = all.get((idx + 1) % all.size());
                GpuDrivers.select(LetsGoLauncherActivity.this, next);
                refreshDrivers();
                android.widget.Toast.makeText(LetsGoLauncherActivity.this,
                    Txt.t("driver_set", "Driver selecionado: ") + next.name, android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // ---- Game settings (written to larecomp.ini in the game folder) ----
        TextView cfgTitle = new TextView(this);
        cfgTitle.setText(Txt.t("cfg", "Configurações"));
        cfgTitle.setTextSize(18);
        cfgTitle.setTextColor(Color.WHITE);
        cfgTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(cfgTitle, lp(-1, -2));

        final CheckBox fps60 = new CheckBox(this);
        fps60.setText(Txt.t("fps60", "60 fps (experimental)"));
        fps60.setTextColor(Color.rgb(200, 210, 220));
        root.addView(fps60, lp(-1, -2));

        final CheckBox smaa = new CheckBox(this);
        smaa.setText(Txt.t("smaa", "Suavizar serrilhado (SMAA)"));
        smaa.setTextColor(Color.rgb(200, 210, 220));
        smaa.setChecked(true);
        root.addView(smaa, lp(-1, -2));

        // Resolution scale selector (emulator-style)
        TextView resLabel = new TextView(this);
        resLabel.setText(Txt.t("res", "Resolução interna:"));
        resLabel.setTextColor(Color.rgb(200, 210, 220));
        root.addView(resLabel, lp(-1, -2));

        // The render HEIGHT (LARECOMP_SCALE = height / 1080, runtime/nvn/output_scale.c); the width follows the image
        // format below. "native" = the screen's own pixels; "custom" = a width x height typed by the player (its
        // ratio becomes the format: LetsGoActivity).
        final android.widget.Spinner resSpinner = new android.widget.Spinner(this);
        final String[] resNames = {Txt.t("res_05", "480p - Desempenho"), Txt.t("res_075", "540p - Equilibrado"),
                                   Txt.t("res_1x", "720p - Padrão"), Txt.t("res_900", "900p"),
                                   Txt.t("res_2x", "1080p - Qualidade"), Txt.t("res_1440", "1440p - Máxima (GPU forte)"),
                                   Txt.t("res_native", "Nativa da tela"),
                                   Txt.t("res_custom", "Personalizada (largura x altura)")};
        final String[] resValues = {"0.4444", "0.5", "0.6667", "0.8333", "1.0", "1.3333", "native", "custom"};
        android.widget.ArrayAdapter<String> resAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, resNames);
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resSpinner.setAdapter(resAdapter);
        final android.content.SharedPreferences prefs = getSharedPreferences("letsgo", MODE_PRIVATE);
        // Load saved preference (default 720p)
        String savedRes = prefs.getString("resolution_scale", "0.6667");
        for (int i = 0; i < resValues.length; i++) {
            if (resValues[i].equals(savedRes)) {
                resSpinner.setSelection(i);
                break;
            }
        }
        root.addView(resSpinner, lp(-1, -2));

        final android.widget.EditText customRes = new android.widget.EditText(this);
        customRes.setSingleLine(true);
        customRes.setHint(Txt.t("res_custom_hint", "ex.: 1440x1080"));
        customRes.setText(prefs.getString("custom_resolution", "1440x1080"));
        customRes.setTextColor(Color.WHITE);
        customRes.setHintTextColor(Color.rgb(120, 130, 140));
        customRes.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        customRes.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                prefs.edit().putString("custom_resolution", s.toString().trim()).apply();
            }
        });
        customRes.setVisibility("custom".equals(savedRes) ? android.view.View.VISIBLE : android.view.View.GONE);
        root.addView(customRes, lp(-1, -2));

        // Image format: the game's 16:9 with black bars, another fixed shape, or the screen's own shape. Any shape
        // but 16:9 changes the game camera's field of view (runtime/hle/camera_aspect.c): the 3D comes out right, but
        // the game draws its 2D interface into the same full-screen image, so it is stretched to the new shape (measured
        // on the Fold4: dialogue box 1.48x taller in "fill"). Experimental until the interface is kept in a 16:9 box.
        TextView fmtLabel = new TextView(this);
        fmtLabel.setText(Txt.t("fmt", "Formato da imagem:"));
        fmtLabel.setTextColor(Color.rgb(200, 210, 220));
        root.addView(fmtLabel, lp(-1, -2));
        final android.widget.Spinner fmtSpinner = new android.widget.Spinner(this);
        final String[] fmtNames = {Txt.t("fmt_169", "16:9 do jogo (faixas pretas)"), Txt.t("fmt_43", "4:3 (experimental: interface esticada)"),
                                   Txt.t("fmt_fill", "Preencher a tela (experimental: interface esticada)")};
        final String[] fmtValues = {"", "4:3", "fill"};
        android.widget.ArrayAdapter<String> fmtAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, fmtNames);
        fmtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fmtSpinner.setAdapter(fmtAdapter);
        String savedFmt = prefs.getString("aspect", "");
        for (int i = 0; i < fmtValues.length; i++) if (fmtValues[i].equals(savedFmt)) fmtSpinner.setSelection(i);
        fmtSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                prefs.edit().putString("aspect", fmtValues[position]).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        fmtSpinner.setEnabled(!"custom".equals(savedRes));  // (a typed width x height carries its own shape)
        root.addView(fmtSpinner, lp(-1, -2));

        resSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                prefs.edit().putString("resolution_scale", resValues[position]).apply();
                boolean custom = "custom".equals(resValues[position]);
                customRes.setVisibility(custom ? android.view.View.VISIBLE : android.view.View.GONE);
                fmtSpinner.setEnabled(!custom);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Game language: the game's RomFS has every language's texts (bin/message/<language>/: English, French,
        // Spanish...), and a dump from any region is the same game. The runtime tells the game larecomp.ini's
        // game_lang (runtime/mods.c game_language); empty = English, which is also what a fan translation that
        // replaces the English/ folder (PT-BR) needs. An installed mod that carries its own language still wins.
        TextView langLabel = new TextView(this);
        langLabel.setText(Txt.t("lang", "Idioma do jogo:"));
        langLabel.setTextColor(Color.rgb(200, 210, 220));
        root.addView(langLabel, lp(-1, -2));

        final android.widget.Spinner langSpinner = new android.widget.Spinner(this);
        final String[] langNames = {Txt.t("lang_default", "Padrão (inglês / tradução PT-BR)"), "English", "Français", "Español", "Deutsch",
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
        wild.setText(Txt.t("wild", "Batalhas selvagens (experimental)"));
        wild.setTextColor(Color.rgb(200, 210, 220));
        root.addView(wild, lp(-1, -2));

        // The phone's motion sensor aims the Poke Ball throws, like the Switch in handheld mode (GyroAim.java).
        // Off by default: the touch controls aim as before; checked, both work together.
        final CheckBox gyroAim = new CheckBox(this);
        gyroAim.setText(Txt.t("gyro", "Mirar com o giroscópio (inclinar o telefone)"));
        gyroAim.setTextColor(Color.rgb(200, 210, 220));
        gyroAim.setChecked(getSharedPreferences("letsgo", MODE_PRIVATE).getBoolean("gyro", false));
        root.addView(gyroAim, lp(-1, -2));
        gyroAim.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean c) {
                getSharedPreferences("letsgo", MODE_PRIVATE).edit().putBoolean("gyro", c).apply();
            }
        });

        final CheckBox touch = new CheckBox(this);
        touch.setText(Txt.t("touch", "Controles touch na tela"));
        touch.setTextColor(Color.rgb(200, 210, 220));
        touch.setChecked(getSharedPreferences("letsgo", MODE_PRIVATE).getBoolean("touch", true));
        root.addView(touch, lp(-1, -2));
        touch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean c) {
                getSharedPreferences("letsgo", MODE_PRIVATE).edit().putBoolean("touch", c).apply();
            }
        });
        // default: minimal controls in the black bars (A, B, X, "..." + floating stick); checked: the old full layout
        final CheckBox touchFull = new CheckBox(this);
        touchFull.setText(Txt.t("touch_full", "Layout touch completo (antigo)"));
        touchFull.setTextColor(Color.rgb(200, 210, 220));
        touchFull.setChecked("classic".equals(getSharedPreferences("letsgo", MODE_PRIVATE).getString("touch_layout", "minimal")));
        root.addView(touchFull, lp(-1, -2));
        touchFull.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean c) {
                getSharedPreferences("letsgo", MODE_PRIVATE).edit().putString("touch_layout", c ? "classic" : "minimal").apply();
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
        hint.setText(Txt.t("hint_log", "Se o jogo fechar ao abrir, use \"Ver log\" e me envie o conteúdo."));
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
            android.widget.Toast.makeText(this, Txt.t("no_picker", "Este aparelho não tem seletor de pastas."),
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
            .setTitle(Txt.t("rebuild_title", "Refazer romfs.bin"))
            .setMessage(Txt.t("rebuild_msg1", "romfs.bin está incompleto (") + mib(c.romfsBinBytes) + Txt.t("of", " de ") + mib(c.romfsBinNeeded)
                + Txt.t("rebuild_msg2", "). Ele será apagado e refeito a partir da pasta romfs/ ao abrir o jogo (alguns minutos). ")
                + Txt.t("rebuild_msg3", "A pasta romfs/ e o resto não são tocados."))
            .setPositiveButton(Txt.t("rebuild_ok", "Apagar e refazer"), new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    boolean ok = bin.delete();
                    android.util.Log.i("lasrecomp", "[folder] removed short romfs.bin (" + c.romfsBinBytes + " of "
                        + c.romfsBinNeeded + " bytes): " + ok);
                    refreshStatus();
                }
            })
            .setNegativeButton(Txt.t("cancel", "Cancelar"), null)
            .show();
    }

    private static String mib(long b) {
        return String.format(java.util.Locale.ROOT, "%.0f MiB", b / 1048576.0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshStatus();  // back from the folder picker, Settings or a dialog
    }

    @Override
    protected void onDestroy() {
        LIVE.remove(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        // Verdict on a driver that was on trial: the game got to its first frames with it, or it did not and the
        // launcher rolls it back (the driver that last worked here, or none: said so, never silently).
        GpuDrivers.TrialOutcome t = GpuDrivers.checkTrial(this, LetsGoActivity.running);
        if (t != null && t.ok) {
            android.widget.Toast.makeText(this, Txt.t("driver_word", "Driver ") + t.driver.name + Txt.t("worked_here", " funcionou neste aparelho."),
                    android.widget.Toast.LENGTH_LONG).show();
        } else if (t != null) {
            showTrialFailure(t);
        }
        if (drvStatus != null) refreshDrivers();
        // the state can change outside the app: files copied, all-files access granted in Settings, card removed
        refreshStatus();
    }

    private void refreshDrivers() {
        GpuDrivers.Driver sel = GpuDrivers.selected(this);
        java.util.List<GpuDrivers.Driver> all = GpuDrivers.list(this);
        StringBuilder sb = new StringBuilder();
        // "Selected" is what the next Play hands the game; "last start" is what the game was really handed
        // (LetsGoActivity records it, and the runtime loads exactly that file or stops).
        if (sel != null) {
            sb.append(Txt.t("selected_driver", "Selecionado: ")).append(label(sel, all));
            sb.append(Txt.t("origin", "\n  origem: ")).append(sel.origin());
            sb.append(sel.started ? Txt.t("worked_before", " · já funcionou aqui") : Txt.t("on_trial", " · ainda não testado: em teste no próximo Jogar"));
            sb.append("\n");
        } else {
            sb.append(Txt.t("selected_system", "Selecionado: driver do sistema\n"));
        }
        String last = GpuDrivers.lastLaunch(this);
        if (last != null) {
            String lastName = Txt.t("system_driver", "driver do sistema");
            if (!last.isEmpty()) {
                lastName = last;
                for (GpuDrivers.Driver d : GpuDrivers.list(GpuDrivers.driversDir(this)))
                    if (last.equals(new File(new File(GpuDrivers.driversDir(this), d.slug), d.libraryName).getAbsolutePath()))
                        lastName = label(d, all);
            }
            boolean same = sel == null ? last.isEmpty()
                : last.equals(new File(new File(GpuDrivers.driversDir(this), sel.slug), sel.libraryName).getAbsolutePath());
            if (!same) sb.append(Txt.t("last_start", "Último início do jogo: ")).append(lastName).append("\n");
        }
        if (all.isEmpty()) {
            sb.append(Txt.t("drivers_none", "Nenhum driver Turnip instalado: baixe o recomendado ou importe um .zip."));
        } else {
            sb.append(Txt.t("installed", "Instalados (toque para trocar): "));
            for (GpuDrivers.Driver d : all) {
                sb.append("\n  • ").append(label(d, all));
                if (sel != null && d.slug.equals(sel.slug)) sb.append(" ✓");
                if (d.failed && !d.started) sb.append(Txt.t("did_not_start", " [não iniciou]"));
            }
        }
        drvStatus.setText(sb.toString());
        android.util.Log.i("lasrecomp", "[gpu] launcher: selected=" + (sel == null ? "system" : sel.slug)
            + " last_start=" + last + " packages=" + all.size());
    }

    /** A driver's name, with its version, and its folder when another package has the same name and version. */
    private static String label(GpuDrivers.Driver d, java.util.List<GpuDrivers.Driver> all) {
        String l = d.name + (d.driverVersion.isEmpty() ? "" : " (" + d.driverVersion + ")");
        for (GpuDrivers.Driver o : all)
            if (o != d && !o.slug.equals(d.slug) && o.name.equals(d.name) && o.driverVersion.equals(d.driverVersion))
                return l + " [" + d.slug + "]";
        return l;
    }

    private void showTrialFailure(GpuDrivers.TrialOutcome t) {
        String now = t.restored != null
                ? Txt.t("back_to", "Voltei para ") + t.restored.name + Txt.t("back_to2", ", que já funcionou neste aparelho.")
                : Txt.t("none_selected", "Nenhum driver Turnip está selecionado agora: o próximo Jogar usaria o driver do sistema.");
        new android.app.AlertDialog.Builder(this)
                .setTitle(Txt.t("trial_fail_title", "O driver não iniciou o jogo"))
                .setMessage(t.driver.name + " (" + t.driver.origin() + Txt.t("trial_fail1", ") não chegou às primeiras imagens do jogo ")
                        + Txt.t("trial_fail2", "neste aparelho.\n\nMotivo: ") + t.reason + "\n\n" + now
                        + Txt.t("trial_fail3", "\n\nEste pacote não será mais recomendado aqui. Você pode baixar o próximo da lista."))
                .setPositiveButton(Txt.t("dl_next", "Baixar o próximo"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        recommendDriver(true);
                    }
                })
                .setNegativeButton(Txt.t("close", "Fechar"), null)
                .show();
    }

    /** Detect, ask GitHub, and offer the package for this phone (never downloads before the player confirms). */
    private void recommendDriver(final boolean afterFailure) {
        if (driverBusy) return;
        driverBusy = true;
        drvStatus.setText(Txt.t("asking_github", "Consultando as publicações no GitHub..."));
        new Thread(new Runnable() {
            @Override public void run() {
                GpuInfo g = gpu != null ? gpu : GpuInfo.detect();
                DriverCatalog.Check check = null;
                String error = null;
                try {
                    check = DriverCatalog.fetch();
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final GpuInfo fg = g;
                final DriverCatalog.Check fc = check;
                final String fe = error;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        driverBusy = false;
                        gpu = fg;
                        drvDevice.setText(fg.summary());
                        refreshDrivers();
                        offerDriver(fg, fc, fe, afterFailure);
                    }
                });
            }
        }, "gpu-catalog").start();
    }

    private void offerDriver(final GpuInfo g, DriverCatalog.Check check, String error, boolean afterFailure) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this).setTitle(Txt.t("recommended", "Driver recomendado"));
        if (check == null) {
            b.setMessage(Txt.t("github_fail", "Não deu para consultar o GitHub: ") + error
                    + Txt.t("github_fail2", "\n\nSem rede, importe um .zip de driver pelo outro botão."))
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (g.family == GpuInfo.Family.NOT_ADRENO) {
            b.setMessage(g.summary() + Txt.t("not_adreno", "\n\nEste aparelho não tem GPU Adreno: drivers Turnip não se aplicam. ")
                    + Txt.t("not_adreno2", "O jogo usa o driver do sistema.")).setPositiveButton("OK", null).show();
            return;
        }
        java.util.Set<String> bad = GpuDrivers.badPackages(this);
        final java.util.List<DriverCatalog.Candidate> plan = new java.util.ArrayList<>(check.plan(g, bad));
        boolean outsideRules = false;
        if (plan.isEmpty()) {
            plan.addAll(check.alternatives(g, bad));
            outsideRules = !plan.isEmpty();
        }
        StringBuilder m = new StringBuilder();
        m.append(Txt.t("device", "Aparelho: ")).append(g.summary()).append("\n");
        m.append(Txt.t("rules", "Regras: ")).append(check.rulesFromGitHub
                ? Txt.t("rules_github", "auto-drivers.json de github.com/Droid-Deck/Drivers-CI") : Txt.t("rules_builtin", "tabela interna (regras do GitHub ilegíveis)"));
        m.append(" → ").append(android.text.TextUtils.join(", ", check.preferences(g))).append("\n");
        if (!check.failedSources.isEmpty())
            m.append(Txt.t("no_answer", "Sem resposta de: ")).append(android.text.TextUtils.join(", ", check.failedSources)).append("\n");
        if (plan.isEmpty()) {
            b.setMessage(m + Txt.t("no_package", "\nNenhum pacote disponível para este aparelho")
                    + (bad.isEmpty() ? "." : Txt.t("no_package2", " além dos que já falharam aqui."))
                    + Txt.t("no_package3", " Importe um .zip, ou use o driver do sistema.")).setPositiveButton("OK", null).show();
            return;
        }
        if (outsideRules)
            m.append(Txt.t("outside_rules", "\nOs pacotes recomendados já falharam aqui. Fora da regra, para esta família de GPU:\n"));
        m.append(Txt.t("will_download", "\nVai baixar:\n")).append(plan.get(0).details());
        if (plan.size() > 1) {
            m.append(Txt.t("then", "\n\nSe o download falhar, depois: "));
            for (int i = 1; i < plan.size(); i++) m.append(i > 1 ? ", " : "").append(plan.get(i).title());
        }
        m.append(Txt.t("trial_note1", "\n\nO driver fica em teste até o jogo mostrar as primeiras imagens; se não iniciar, o app volta ")
                + Txt.t("trial_note2", "atrás e propõe o próximo."));
        b.setMessage(m.toString())
                .setPositiveButton(Txt.t("dl_use", "Baixar e usar"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        downloadPlan(plan, 0, new StringBuilder());
                    }
                })
                .setNegativeButton(Txt.t("cancel", "Cancelar"), null)
                .show();
    }

    /** Download, verify and install plan[i]; on failure move to plan[i+1] (the player asked for a download). */
    private void downloadPlan(final java.util.List<DriverCatalog.Candidate> plan, final int i, final StringBuilder errors) {
        if (i >= plan.size()) {
            driverBusy = false;
            refreshDrivers();
            new android.app.AlertDialog.Builder(this).setTitle(Txt.t("dl_failed", "Download do driver falhou"))
                    .setMessage(errors + Txt.t("nothing_changed", "\nNada foi alterado: o driver em uso continua o mesmo."))
                    .setPositiveButton("OK", null).show();
            return;
        }
        driverBusy = true;
        final DriverCatalog.Candidate c = plan.get(i);
        drvStatus.setText(Txt.t("downloading", "Baixando ") + c.title() + "...");
        new Thread(new Runnable() {
            @Override public void run() {
                GpuDrivers.ImportResult r = null;
                String err = null;
                // Already installed (same package): no download.
                for (GpuDrivers.Driver d : GpuDrivers.list(LetsGoLauncherActivity.this))
                    if (c.sha256.equals(d.packageSha256())) r = new GpuDrivers.ImportResult(d, null);
                if (r == null) {
                    java.io.File zip = null;
                    try {
                        zip = DriverCatalog.download(LetsGoLauncherActivity.this, c, new DriverCatalog.Progress() {
                            long shown = -1;
                            @Override public void update(final long done, final long total) {
                                final long pct = total > 0 ? 100 * done / total : -1;
                                if (pct == shown) return;
                                shown = pct;
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        drvStatus.setText(Txt.t("downloading", "Baixando ") + c.title() + (pct >= 0 ? " " + pct + "%" : ""));
                                    }
                                });
                            }
                        });
                        org.json.JSONObject src = new org.json.JSONObject()
                                .put("key", c.key).put("repo", c.repo).put("tag", c.tag).put("asset", c.asset)
                                .put("url", c.url).put("sha256", c.sha256).put("publishedAt", c.publishedAt);
                        r = GpuDrivers.installZip(LetsGoLauncherActivity.this, zip, src);
                    } catch (Exception e) {
                        err = e.getMessage();
                    } finally {
                        if (zip != null) zip.delete();
                    }
                }
                if (r != null && r.error != null) { err = r.error; r = null; }
                final GpuDrivers.ImportResult fr = r;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fr == null) {
                            android.util.Log.w("lasrecomp", "[gpu] " + c.asset + ": " + fe);
                            errors.append(c.title()).append(": ").append(fe).append("\n");
                            if (i + 1 < plan.size())
                                android.widget.Toast.makeText(LetsGoLauncherActivity.this, c.title() + Txt.t("failed_paren", " falhou (")
                                        + fe + Txt.t("trying", "); tentando ") + plan.get(i + 1).title(),
                                        android.widget.Toast.LENGTH_LONG).show();
                            downloadPlan(plan, i + 1, errors);
                            return;
                        }
                        driverBusy = false;
                        GpuDrivers.select(LetsGoLauncherActivity.this, fr.driver);
                        refreshDrivers();
                        android.widget.Toast.makeText(LetsGoLauncherActivity.this, Txt.t("installed_ok1", "Driver instalado e verificado ")
                                + Txt.t("installed_ok2", "(sha256 conferido): ") + fr.driver.name
                                + (fr.driver.started ? "" : Txt.t("installed_ok3", ". Será testado no próximo Jogar.")),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "gpu-download").start();
    }

    private Launch refreshStatus() {
        if (status == null) return null;
        Launch st = launch(this);
        GameFolder.Check c = st.folder;
        String dir = st.dir;
        boolean needAccess = st.needAccess;
        String where = dir != null ? Txt.t("folder", "Pasta: ") + dir : "";
        String text;
        if (needAccess) {
            text = Txt.t("need_access", "O app precisa do acesso a todos os arquivos para ler a pasta do jogo.\n") + where;
        } else {
            text = describe(c) + (where.isEmpty() ? "" : "\n" + where);
        }
        if (!st.gameLib) text += Txt.t("no_gamelib", "\nEste APK não tem o código do jogo (liblasgame.so).");
        status.setText(text);
        if (play != null) {
            play.setEnabled(st.ready);
            play.setAlpha(st.ready ? 1f : 0.4f);
        }
        if (pick != null) pick.setText(dir == null ? Txt.t("sel_folder", "Escolher a pasta do jogo") : Txt.t("retry", "Escolher outra pasta"));
        if (forget != null) forget.setVisibility(dir != null ? android.view.View.VISIBLE : android.view.View.GONE);
        if (access != null) access.setVisibility(needAccess ? android.view.View.VISIBLE : android.view.View.GONE);
        if (rebuild != null) rebuild.setVisibility(!needAccess && c.state == GameFolder.State.ROMFS_BIN_BROKEN
            && c.hasRomfsDir ? android.view.View.VISIBLE : android.view.View.GONE);
        // what this screen now shows, from the same object: the log and the screen cannot disagree
        android.util.Log.i("lasrecomp", "[folder] " + st.summary() + (needAccess ? " (no all-files access)" : "")
            + " persisted=" + persistedTrees() + " | screen #" + Integer.toHexString(System.identityHashCode(this))
            + ": play " + (play != null && play.isEnabled() ? "ENABLED" : "disabled"));
        return st;
    }

    /** What the player sees for each state of the folder: what is wrong and what to do. */
    static String describe(GameFolder.Check c) {
        switch (c.state) {
            case NOT_SET:
                return Txt.t("st_not_set1", "Escolha a pasta do jogo já extraído: a pasta que contém extracted/exefs/ e ")
                    + Txt.t("st_not_set2", "extracted/romfs/ (ou extracted/romfs.bin).");
            case MISSING:
                return Txt.t("st_missing1", "A pasta escolhida não existe mais (movida, renomeada ou cartão retirado). ")
                    + Txt.t("st_missing2", "Escolha-a de novo.");
            case UNREADABLE:
                return Txt.t("st_unreadable", "A pasta escolhida não pode ser lida.");
            case NO_EXTRACTED:
                return Txt.t("st_no_extracted1", "Esta pasta não tem extracted/. Escolha a pasta que contém extracted/exefs/ e ")
                    + "extracted/romfs/.";
            case LOOSE:
                return Txt.t("st_loose1", "exefs/ e romfs/ estão direto nesta pasta: coloque-os numa subpasta chamada extracted/ ")
                    + Txt.t("st_loose2", "(extracted/exefs/, extracted/romfs/) e escolha esta pasta de novo.");
            case NO_EXEFS:
                return Txt.t("st_no_exefs", "extracted/exefs/ está incompleto: faltam main e main.npdm (ou main não é um NSO).");
            case UNKNOWN_GAME:
                return Txt.t("st_unknown", "extracted/exefs/ não é de Pokémon: Let's Go, Eevee! nem Pikachu!.");
            case IMPORT_PARTIAL:
                return Txt.t("st_partial1", "Uma importação foi interrompida nesta pasta (.import/ sem .import_ok): os arquivos ")
                    + Txt.t("st_partial2", "podem estar incompletos.");
            case NO_ROMFS:
                return (c.game != null ? c.game + ": " : "") + Txt.t("st_no_romfs1", "falta a RomFS. Copie extracted/romfs/ ")
                    + Txt.t("st_no_romfs2", "(com bin/message dentro) ou extracted/romfs.bin.");
            case ROMFS_DIR_INCOMPLETE:
                return (c.game != null ? c.game + ": " : "") + Txt.t("st_romfs_dir1", "extracted/romfs/ está incompleto (falta ")
                    + c.romfsMissing + Txt.t("st_romfs_dir2", "/). Copie a pasta romfs/ inteira de novo.");
            case ROMFS_BIN_BROKEN:
                return (c.game != null ? c.game + ": " : "") + Txt.t("st_romfs_bin1", "extracted/romfs.bin está incompleto (")
                    + mib(Math.max(0, c.romfsBinBytes)) + (c.romfsBinNeeded > 0 ? Txt.t("of", " de ") + mib(c.romfsBinNeeded) : "")
                    + "). " + (c.hasRomfsDir ? Txt.t("st_romfs_bin2", "Ele pode ser refeito a partir de romfs/.")
                    : Txt.t("st_romfs_bin3", "Copie-o de novo."));
            case READ_ONLY:
                return Txt.t("st_ro1", "A pasta não pode ser gravada (saves, configurações, cache). Escolha uma pasta do ")
                    + Txt.t("st_ro2", "armazenamento interno.");
            case READY:
                return Txt.t("st_ready", "Jogo pronto para iniciar: ") + c.game + ".";
            case READY_FIRST_RUN:
                return Txt.t("st_ready_first1", "Jogo pronto: ") + c.game + Txt.t("st_ready_first2", ". Na primeira abertura, romfs.bin é montado a partir de ")
                    + Txt.t("st_ready_first3", "romfs/ (alguns minutos, ~4,5 GB livres).");
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
            refreshAll();
            if (path == null) {
                android.widget.Toast.makeText(this, Txt.t("pick_storage1", "Escolha a pasta pelo armazenamento do aparelho ")
                    + Txt.t("pick_storage2", "(armazenamento interno ou cartão SD), não por \"Recentes\" nem por um app de nuvem."),
                    android.widget.Toast.LENGTH_LONG).show();
            }
            refreshStatus();
            if (path != null && !gameDataReady(this))
                android.widget.Toast.makeText(this, status.getText(), android.widget.Toast.LENGTH_LONG).show();
        } else if (req == REQ_IMPORT_DRIVER && res == RESULT_OK
                && data != null && data.getData() != null) {
            GpuDrivers.ImportResult r = GpuDrivers.importZip(this, data.getData());
            if (r.error != null) {
                android.widget.Toast.makeText(this, Txt.t("error", "Erro: ") + r.error,
                        android.widget.Toast.LENGTH_LONG).show();
            } else {
                GpuDrivers.select(this, r.driver);
                android.widget.Toast.makeText(this, Txt.t("driver_new", "Driver instalado: ") + r.driver.name
                        + (r.driver.started ? "" : Txt.t("tested_next", " (será testado no próximo Jogar)")),
                        android.widget.Toast.LENGTH_LONG).show();
            }
            refreshDrivers();
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
