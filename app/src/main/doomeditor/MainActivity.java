package com.retro.doomeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements UiHost {
    static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    private EditorView editor;
    private TitleView title;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); Renderer.loadPrefs(this);
        String crash = getSharedPreferences(DoomApp.CRASH_PREFS, 0).getString(DoomApp.CRASH_KEY, null);
        if (crash != null) { showCrash(crash); return; }
        if (state == null) { String exit = Diag.previousExit(this); if (exit != null) { showCrash(exit); return; } }
        if (state != null) { showTitle(); handleViewIntent(getIntent()); return; }
        // fresh launch: loading screen, then the project manager
        fullscreen();
        setContentView(new LoadingView(this, () -> { if (isFinishing()) return; showTitle(); handleViewIntent(getIntent()); }));
    }
    private void fullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        PerfDisplay.limitRefreshRate(this);   // 60 Hz is plenty for this app and saves battery on 120 Hz screens
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
    }
    /** Title screen and project manager. */
    void showTitle() {
        fullscreen();
        if (editor != null) { editor.onPause(); editor = null; }
        title = new TitleView(this);
        title.host = this;
        title.listener = new TitleView.Listener() {
            @Override public void openProject(String id) { MainActivity.this.openProject(id); }
            @Override public void playProject(String id) { MainActivity.this.playProject(id); }
            @Override public void exportProject(String id, boolean share) { MainActivity.this.exportProject(id, share); }
            @Override public void importProject() { MainActivity.this.pickImport(); }
        };
        setContentView(title);
    }
    private void openProject(String id) {
        try { Project p = Project.isOpen() ? Project.get(this) : null; if (p == null || !id.equals(p.id)) Project.open(this, id); }
        catch (Exception e) { Toast.makeText(this, "Could not open that project", Toast.LENGTH_LONG).show(); if (title != null) title.refresh(); return; }
        startEditor();
    }
    // ------------------------------------------------------------------ play, export, import
    private static final int REQ_EXPORT = 51, REQ_IMPORT = 52;
    private String pendingExport;
    /** Opens a project straight into its game (splash and main menu); quitting returns here. */
    void playProject(String id) {
        try { Project p = Project.isOpen() ? Project.get(this) : null; if (p == null || !id.equals(p.id)) Project.open(this, id); }
        catch (Exception e) { Toast.makeText(this, "Could not open that game", Toast.LENGTH_LONG).show(); return; }
        startEditor(); editor.startPlayerMode();
    }
    private static String fileName(String name) { String n = name.replaceAll("[^A-Za-z0-9 _-]", "").trim(); return (n.isEmpty() ? "game" : n) + ".rpgpocket"; }
    void exportProject(String id, boolean share) {
        String name = "game"; for (ProjectStore.Meta m : ProjectStore.list(this)) if (m.id.equals(id)) name = m.name;
        if (share) {
            try {
                java.io.File f = new java.io.File(ShareProvider.dir(this), fileName(name));
                try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), "UTF-8")) { w.write(ProjectStore.exportPackage(this, id)); }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/octet-stream");
                send.putExtra(Intent.EXTRA_STREAM, ShareProvider.uriFor(f));
                send.putExtra(Intent.EXTRA_SUBJECT, name + " – an RPG Pocket Editor game");
                send.putExtra(Intent.EXTRA_TEXT, "Open this file with RPG Pocket Editor to play " + name + ".");
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Share " + name));
            } catch (Exception e) { Toast.makeText(this, "Couldn't prepare the game file", Toast.LENGTH_LONG).show(); }
            return;
        }
        pendingExport = id;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/octet-stream"); i.putExtra(Intent.EXTRA_TITLE, fileName(name));
        try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { Toast.makeText(this, "Can't save files on this device", Toast.LENGTH_LONG).show(); }
    }
    void pickImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        try { startActivityForResult(i, REQ_IMPORT); }
        catch (Exception e) { Intent g = new Intent(Intent.ACTION_GET_CONTENT); g.setType("*/*"); g.addCategory(Intent.CATEGORY_OPENABLE); try { startActivityForResult(g, REQ_IMPORT); } catch (Exception e2) { Toast.makeText(this, "No file picker available", Toast.LENGTH_LONG).show(); } }
    }
    private void importFrom(android.net.Uri uri) {
        new Thread(() -> {
            try {
                StringBuilder b = new StringBuilder(); char[] buf = new char[16384];
                try (java.io.InputStreamReader r = new java.io.InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8")) { int n; while ((n = r.read(buf)) > 0) { b.append(buf, 0, n); if (b.length() > 80_000_000) throw new java.io.IOException("File too large"); } }
                String id = ProjectStore.importPackage(this, b.toString());
                String name = id; for (ProjectStore.Meta m : ProjectStore.list(this)) if (m.id.equals(id)) name = m.name;
                final String nm = name;
                runOnUiThread(() -> {
                    if (title != null) title.refresh();
                    choose(this, "Imported \u201c" + nm + "\u201d", new String[]{"Play it now", "Open it in the editor", "Later"}, 0, k -> { if (k == 0) playProject(id); else if (k == 1) openProject(id); }, this::refreshView);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this, "Couldn't import that file: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), Toast.LENGTH_LONG).show());
            }
        }, "ImportGame").start();
    }
    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) { pendingExport = null; return; }
        android.net.Uri uri = data.getData();
        if (req == REQ_IMPORT) importFrom(uri);
        else if (req == REQ_EXPORT && pendingExport != null) {
            String id = pendingExport; pendingExport = null;
            try (java.io.OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(ProjectStore.exportPackage(this, id).getBytes("UTF-8")); Toast.makeText(this, "Game file saved", Toast.LENGTH_SHORT).show(); }
            catch (Exception e) { Toast.makeText(this, "Couldn't save the game file", Toast.LENGTH_LONG).show(); }
        }
    }
    /** A game file opened from another app (file manager, Drive, a chat attachment). */
    private boolean handleViewIntent(Intent i) {
        if (i == null) return false;
        android.net.Uri uri = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) uri = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) uri = i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) return false;
        i.setAction(Intent.ACTION_MAIN); i.setData(null);
        importFrom(uri); return true;
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); if (handleViewIntent(i) && editor != null) { editor.save(false); showTitle(); } }

    private void startEditor() {
        fullscreen();
        title = null;
        editor = new EditorView(this);
        editor.host = this;
        editor.onExitToTitle = this::showTitle;
        setContentView(editor);
    }
    @Override protected void onPause() { super.onPause(); if (editor != null) editor.onPause(); }
    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        if (editor != null) editor.onResume();
        if (title != null) title.refresh();
    }

    // ------------------------------------------------------------------ UiHost
    static void prompt(Activity a, String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done, Runnable after) {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setInputType(numeric ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED)
                : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        input.setText(initial == null ? "" : initial);
        input.setSelection(input.getText().length());
        FrameLayout box = new FrameLayout(a);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        AlertDialog.Builder d = new AlertDialog.Builder(a, DIALOG_THEME).setTitle(title).setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (dialog, which) -> { done.accept(input.getText().toString()); if (after != null) after.run(); });
        if (hint != null) d.setMessage(hint);
        d.show();
    }
    static void choose(Activity a, String title, String[] items, int checked, java.util.function.IntConsumer done, Runnable after) {
        new AlertDialog.Builder(a, DIALOG_THEME).setTitle(title)
                .setSingleChoiceItems(items, checked, (d, i) -> { d.dismiss(); done.accept(i); if (after != null) after.run(); })
                .setNegativeButton("Cancel", null).show();
    }
    static void confirm(Activity a, String title, String message, String ok, Runnable yes, Runnable after) {
        new AlertDialog.Builder(a, DIALOG_THEME).setTitle(title).setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(ok, (d, w) -> { yes.run(); if (after != null) after.run(); }).show();
    }
    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { prompt(this, title, hint, initial, numeric, done, this::refreshView); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { choose(this, title, items, checked, done, this::refreshView); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { confirm(this, title, message, ok, yes, this::refreshView); }
    /** Redraws whichever screen is showing (title screen or editor). */
    private void refreshView() { if (editor != null) editor.invalidate(); if (title != null) title.refresh(); }
    @Override public void openFsm(String defId) {
        Intent i = new Intent(this, FSMEditorActivity.class);
        if (defId != null) i.putExtra("def", defId);
        startActivity(i);
    }
    @Override public void openSprites(String defId) {
        Intent i = new Intent(this, SpriteEditorActivity.class);
        if (defId != null) i.putExtra("def", defId);
        startActivity(i);
    }

    @Override public void openDialogue(int tab) { Intent i = new Intent(this, DialogueEditorActivity.class); i.putExtra("tab", tab); startActivity(i); }
    @Override public void openContent(int tab) { Intent i = new Intent(this, ContentEditorActivity.class); i.putExtra("tab", tab); startActivity(i); }
    @Override public void openUi() { startActivity(new Intent(this, UiEditorActivity.class)); }
    @Override public void openSound(int tab) {
        Intent i = new Intent(this, SoundEditorActivity.class);
        i.putExtra("tab", tab);
        startActivity(i);
    }

    /** Shows the last crash report with options to copy it, wipe saved data, or carry on. */
    private void showCrash(String report) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff151b23);
        root.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this);
        title.setText("The editor hit an error last time. Details:");
        title.setTextColor(0xffffbd6e);
        title.setTextSize(18);
        root.addView(title);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = new Button(this); copy.setText("Copy report");
        Button reset = new Button(this); reset.setText("Back to project list");
        Button go = new Button(this); go.setText("Continue");
        buttons.addView(copy); buttons.addView(reset); buttons.addView(go);
        root.addView(buttons);
        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(report); body.setTextColor(Color.WHITE); body.setTextSize(12); body.setTextIsSelectable(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", report));
            Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show();
        });
        reset.setOnClickListener(v -> {
            getSharedPreferences("app", 0).edit().remove("current").commit();
            Project.reset(); clearCrash(); showTitle();
        });
        go.setOnClickListener(v -> { clearCrash(); showTitle(); });
        setContentView(root);
    }
    private void clearCrash() { getSharedPreferences(DoomApp.CRASH_PREFS, 0).edit().remove(DoomApp.CRASH_KEY).commit(); }

    @Override public void onBackPressed() {
        if (editor != null) { if (editor.goBack()) return; editor.save(false); showTitle(); return; }
        super.onBackPressed();
    }
}
