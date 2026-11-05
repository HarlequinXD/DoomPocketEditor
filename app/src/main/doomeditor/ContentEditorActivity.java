package com.retro.doomeditor;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/** Hosts the Content editor; items, spells, classes, loot and shops are stored in the project file. */
public class ContentEditorActivity extends Activity implements ContentEditorView.Host {
    private ContentEditorView view;
    private Project project;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        PerfDisplay.limitRefreshRate(this);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        project = Project.get(this);
        int tab = getIntent() != null ? getIntent().getIntExtra("tab", 0) : 0;
        view = new ContentEditorView(this, project, tab, this);
        setContentView(view);
    }
    @Override protected void onResume() { super.onResume(); getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512); view.invalidate(); }
    @Override protected void onPause() { super.onPause(); project.saveNow(this); }
    @Override public void saved() { project.save(this); Toast.makeText(this, "Content saved to project", Toast.LENGTH_SHORT).show(); }
    @Override public void exit() { project.save(this); finish(); }
    @Override public void onBackPressed() { exit(); }
    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { MainActivity.prompt(this, title, hint, initial, numeric, done, view::afterEdit); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { MainActivity.choose(this, title, items, checked, done, view::afterEdit); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { MainActivity.confirm(this, title, message, ok, yes, view::afterEdit); }
    @Override public void openFsm(String defId) {}
}
