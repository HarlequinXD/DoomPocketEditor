package com.retro.doomeditor;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/** Hosts the UI Editor; the layout is stored inside the project file. */
public class UiEditorActivity extends Activity implements UiEditorView.Host {
    private UiEditorView view;
    private Project project;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        PerfDisplay.limitRefreshRate(this);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        project = Project.get(this);
        view = new UiEditorView(this, project, this);
        setContentView(view);
    }
    @Override protected void onResume() { super.onResume(); getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512); view.invalidate(); }
    @Override protected void onPause() { super.onPause(); project.saveNow(this); }
    @Override public void saved() { project.save(this); Toast.makeText(this, "Layout saved to project", Toast.LENGTH_SHORT).show(); }
    @Override public void exit() { project.save(this); finish(); }
    @Override public void onBackPressed() { if (view.back()) return; exit(); }
    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { MainActivity.prompt(this, title, hint, initial, numeric, done, view::invalidate); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { MainActivity.choose(this, title, items, checked, done, view::invalidate); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { MainActivity.confirm(this, title, message, ok, yes, view::invalidate); }
    @Override public void openFsm(String defId) {}
}
