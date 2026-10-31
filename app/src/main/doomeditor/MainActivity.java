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
        super.onCreate(state);
        String crash = getSharedPreferences(DoomApp.CRASH_PREFS, 0).getString(DoomApp.CRASH_KEY, null);
        if (crash != null) { showCrash(crash); return; }
        showTitle();
    }
    private void fullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
    }
    /** Title screen and project manager. */
    void showTitle() {
        fullscreen();
        if (editor != null) { editor.onPause(); editor = null; }
        title = new TitleView(this);
        title.host = this;
        title.listener = this::openProject;
        setContentView(title);
    }
    private void openProject(String id) {
        try { Project p = Project.isOpen() ? Project.get(this) : null; if (p == null || !id.equals(p.id)) Project.open(this, id); }
        catch (Exception e) { Toast.makeText(this, "Could not open that project", Toast.LENGTH_LONG).show(); if (title != null) title.refresh(); return; }
        startEditor();
    }
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
    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { prompt(this, title, hint, initial, numeric, done, editor::invalidate); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { choose(this, title, items, checked, done, editor::invalidate); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { confirm(this, title, message, ok, yes, editor::invalidate); }
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
        if (editor != null) { if (editor.exitPlay()) return; editor.save(false); showTitle(); return; }
        super.onBackPressed();
    }
}
