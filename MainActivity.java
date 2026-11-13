package com.mestro.ac;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class MainActivity extends Activity {
    private TextView fridaLog, dumpStatus, apkStatus, classDumpStatus;
    private EditText processSearch, adbInput, libName, libCommand, autoPackage, classDumpInput;
    private ListView processList, libraryList;
    private TextView adbOutput;
    private String selectedPackage = null;
    private String selectedScriptPath = null;
    private String autoInjectPackage = null;
    private boolean autoInjectEnabled = false;
    private String pendingPath = null;
    private boolean pendingIsFolder = false;
    private List<String> allProcesses = new ArrayList<>();
    private AuthManager auth;
    private static final String HEADER = "MESTRO_AC\nIS THE BEST\n\n";
    private int currentTab = 0;
    private FrameLayout contentFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthManager(this);
        setContentView(R.layout.activity_splash);
        new Handler().postDelayed(this::checkAuth, 2500);
    }

    private void checkAuth() {
        if (auth.isFullyAuthed() || !auth.isTrialExpired()) showMain();
        else showAuthScreen();
    }

    private void showAuthScreen() {
        setContentView(R.layout.activity_auth);
        EditText tokenInput = findViewById(R.id.auth_token);
        EditText discordInput = findViewById(R.id.auth_discord);
        findViewById(R.id.auth_submit).setOnClickListener(v -> {
            String token = tokenInput.getText().toString().trim();
            String discord = discordInput.getText().toString().trim();
            if (token.isEmpty() || discord.isEmpty()) return;
            auth.setFullAuth(token, discord, "");
            showMain();
        });
        findViewById(R.id.auth_trial).setOnClickListener(v -> { auth.startTrial(); showMain(); });
    }

    private void showMain() {
        setContentView(R.layout.activity_main);
        contentFrame = findViewById(R.id.content_frame);
        setupTabs();
        switchTab(0);
        loadDummyProcesses();
    }

    private void setupTabs() {
        findViewById(R.id.tab_frida).setOnClickListener(v -> switchTab(0));
        findViewById(R.id.tab_process).setOnClickListener(v -> switchTab(1));
        findViewById(R.id.tab_adb).setOnClickListener(v -> switchTab(2));
        findViewById(R.id.tab_library).setOnClickListener(v -> switchTab(3));
        findViewById(R.id.tab_dump).setOnClickListener(v -> switchTab(4));
        findViewById(R.id.tab_apk).setOnClickListener(v -> switchTab(5));
        findViewById(R.id.tab_class).setOnClickListener(v -> switchTab(6));
    }

    private void switchTab(int tab) {
        currentTab = tab;
        contentFrame.removeAllViews();
        View v = null;
        switch (tab) {
            case 0: v = getLayoutInflater().inflate(R.layout.tab_frida, contentFrame, false); break;
            case 1: v = getLayoutInflater().inflate(R.layout.tab_process, contentFrame, false); break;
            case 2: v = getLayoutInflater().inflate(R.layout.tab_adb, contentFrame, false); break;
            case 3: v = getLayoutInflater().inflate(R.layout.tab_library, contentFrame, false); break;
            case 4: v = getLayoutInflater().inflate(R.layout.tab_dump, contentFrame, false); break;
            case 5: v = getLayoutInflater().inflate(R.layout.tab_apk, contentFrame, false); break;
            case 6: v = getLayoutInflater().inflate(R.layout.tab_class, contentFrame, false); break;
        }
        if (v != null) contentFrame.addView(v);
        bindCurrentTab();
    }

    private void bindCurrentTab() {
        if (currentTab == 0) {
            fridaLog = contentFrame.findViewById(R.id.frida_log);
            contentFrame.findViewById(R.id.clear_log_btn).setOnClickListener(v -> { fridaLog.setText(HEADER); log("cleared"); });
            contentFrame.findViewById(R.id.download_log_btn).setOnClickListener(v -> downloadLogs());
            contentFrame.findViewById(R.id.choose_file_btn).setOnClickListener(v -> openPicker(false));
            contentFrame.findViewById(R.id.choose_folder_btn).setOnClickListener(v -> openPicker(true));
            initLog();
        }
        if (currentTab == 1) {
            processSearch = contentFrame.findViewById(R.id.process_search);
            processList = contentFrame.findViewById(R.id.process_list);
            processSearch.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) { filterProcesses(s.toString()); }
                public void afterTextChanged(android.text.Editable s) {}
            });
            processList.setOnItemClickListener((p, view, pos, id) -> {
                selectedPackage = (String) p.getItemAtPosition(pos);
                log("selected " + selectedPackage);
            });
            filterProcesses("");
        }
        if (currentTab == 2) {
            adbInput = contentFrame.findViewById(R.id.adb_input);
            adbOutput = contentFrame.findViewById(R.id.adb_output);
            contentFrame.findViewById(R.id.adb_run_btn).setOnClickListener(v -> runAdb(adbInput.getText().toString()));
        }
        if (currentTab == 3) {
            libName = contentFrame.findViewById(R.id.lib_name);
            libCommand = contentFrame.findViewById(R.id.lib_command);
            libraryList = contentFrame.findViewById(R.id.library_list);
            contentFrame.findViewById(R.id.lib_pick_script).setOnClickListener(v -> openPicker(false));
            contentFrame.findViewById(R.id.lib_pick_folder).setOnClickListener(v -> openPicker(true));
            contentFrame.findViewById(R.id.lib_save).setOnClickListener(v -> saveLibrary());
            libraryList.setOnItemClickListener((p, v, pos, id) -> runLibrary(ScriptLibrary.getAll().get(pos)));
            refreshLibrary();
        }
        if (currentTab == 4) {
            dumpStatus = contentFrame.findViewById(R.id.dump_status);
            contentFrame.findViewById(R.id.dump_game_btn).setOnClickListener(v -> { if (selectedPackage != null) dumpGame(selectedPackage); });
        }
        if (currentTab == 5) {
            apkStatus = contentFrame.findViewById(R.id.apk_status);
            contentFrame.findViewById(R.id.get_apk_btn).setOnClickListener(v -> { if (selectedPackage != null) getCleanApk(selectedPackage); });
        }
        if (currentTab == 6) {
            classDumpStatus = contentFrame.findViewById(R.id.class_dump_status);
            classDumpInput = contentFrame.findViewById(R.id.class_dump_input);
            contentFrame.findViewById(R.id.live_class_dump_btn).setOnClickListener(v -> liveClassDump());
        }
    }

    private void initLog() { if (fridaLog != null) fridaLog.setText(HEADER); }

    private void log(String msg) {
        if (fridaLog == null) return;
        if (!fridaLog.getText().toString().startsWith("MESTRO_AC")) fridaLog.setText(HEADER);
        fridaLog.append(msg + "\n");
    }

    private void downloadLogs() {
        try {
            File f = new File(getExternalFilesDir(null), "mestro_log.txt");
            Files.write(f.toPath(), fridaLog.getText().toString().getBytes());
            shareFile(f, "text/plain");
        } catch (Exception e) {}
    }

    private void openPicker(boolean folder) {
        Intent i = new Intent(folder ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);
        if (!folder) i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, folder ? 1002 : 1001);
    }

    private void filterProcesses(String q) {
        if (processList == null) return;
        List<String> f = new ArrayList<>();
        for (String p : allProcesses) if (p.toLowerCase().contains(q.toLowerCase().trim())) f.add(p);
        processList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, f));
    }

    private void loadDummyProcesses() {
        allProcesses.clear();
        allProcesses.addAll(Arrays.asList("com.example.game", "com.gorilla.tag", "com.meta.horizon", "com.oculus.browser"));
        filterProcesses("");
    }

    private void runAdb(String cmd) {
        if (cmd.trim().isEmpty() || adbOutput == null) return;
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
                p.waitFor();
                runOnUiThread(() -> adbOutput.append("$ " + cmd + "\n" + sb));
            } catch (Exception e) {}
        }).start();
    }

    private void saveLibrary() {
        String name = libName.getText().toString().trim();
        String cmd = libCommand.getText().toString().trim();
        if (name.isEmpty() || cmd.isEmpty() || pendingPath == null) return;
        ScriptLibrary.add(new ScriptLibrary.Entry(name, pendingPath, cmd, pendingIsFolder));
        refreshLibrary();
        libName.setText(""); libCommand.setText("");
    }

    private void refreshLibrary() {
        if (libraryList == null) return;
        libraryList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ScriptLibrary.getAll()));
    }

    private void runLibrary(ScriptLibrary.Entry e) {
        log("Library: " + e.name);
        if (adbInput != null) adbInput.setText(e.command);
        inject(e.path, selectedPackage != null ? selectedPackage : "target");
    }

    private void inject(String path, String pkg) { log("Injecting into " + pkg); }

    private void dumpGame(String pkg) {
        if (dumpStatus == null) return;
        dumpStatus.setText("Dumping...");
        new Thread(() -> {
            try {
                String local = getExternalFilesDir(null) + "/dumps/" + pkg;
                new File(local).mkdirs();
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "cp -r /sdcard/Android/data/" + pkg + " " + local}).waitFor();
                String zip = getExternalFilesDir(null) + "/" + pkg + "_dump.zip";
                zipFolder(local, zip);
                runOnUiThread(() -> { dumpStatus.setText("Ready"); shareFile(new File(zip), "application/zip"); });
            } catch (Exception e) { runOnUiThread(() -> dumpStatus.setText("Failed")); }
        }).start();
    }

    private void getCleanApk(String pkg) {
        if (apkStatus == null) return;
        apkStatus.setText("Pulling...");
        new Thread(() -> {
            try {
                Process path = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm path " + pkg});
                String line = new BufferedReader(new InputStreamReader(path.getInputStream())).readLine();
                path.waitFor();
                if (line == null) return;
                String remote = line.replace("package:", "").trim();
                String local = getExternalFilesDir(null) + "/" + pkg + ".apk";
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "cp " + remote + " " + local}).waitFor();
                runOnUiThread(() -> { apkStatus.setText("Ready"); shareFile(new File(local), "application/vnd.android.package-archive"); });
            } catch (Exception e) { runOnUiThread(() -> apkStatus.setText("Failed")); }
        }).start();
    }

    private void liveClassDump() {
        if (selectedPackage == null || classDumpStatus == null) { if (classDumpStatus != null) classDumpStatus.setText("Select process"); return; }
        classDumpStatus.setText("Dumping...");
        new Thread(() -> {
            try {
                File f = new File(getExternalFilesDir(null), selectedPackage + "_classes.txt");
                Files.write(f.toPath(), ("// Live dump\n").getBytes());
                runOnUiThread(() -> { classDumpStatus.setText("Ready"); shareFile(f, "text/plain"); });
            } catch (Exception e) { runOnUiThread(() -> classDumpStatus.setText("Failed")); }
        }).start();
    }

    private void shareFile(File file, String mime) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(this, getPackageName()+".provider", file));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Save"));
    }

    private void zipFolder(String src, String dest) throws Exception {
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(dest));
        zipDir(new File(src), new File(src).getName(), zos);
        zos.close();
    }

    private void zipDir(File dir, String base, java.util.zip.ZipOutputStream zos) throws Exception {
        for (File f : dir.listFiles()) {
            String entry = base + "/" + f.getName();
            if (f.isDirectory()) zipDir(f, entry, zos);
            else {
                zos.putNextEntry(new java.util.zip.ZipEntry(entry));
                Files.copy(f.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        pendingPath = uri.toString();
        pendingIsFolder = code == 1002;
        if (!pendingIsFolder) selectedScriptPath = pendingPath;
    }
}