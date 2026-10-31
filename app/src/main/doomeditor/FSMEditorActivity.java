package com.retro.doomeditor;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/** Hosts the FSM editor for the project's entity types and provides its dialogs. */
public class FSMEditorActivity extends Activity implements FSMGraphView.Host {
    private FSMGraphView view;
    private Project project;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        project = Project.get(this);
        String def = getIntent() != null ? getIntent().getStringExtra("def") : null;
        view = new FSMGraphView(this, project, def, this);
        setContentView(view);
    }
    @Override protected void onResume() { super.onResume(); getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512); }
    @Override protected void onPause() { super.onPause(); view.stopRun(); project.save(this); }
    @Override public void saved() { project.save(this); Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show(); }
    @Override public void exit() { project.save(this); finish(); }
    @Override public void onBackPressed() {
        if (view.browserOpen) { view.browserOpen = false; view.invalidate(); return; }
        if (view.linkFrom != null) { view.linkFrom = null; view.invalidate(); return; }
        if (view.selectedAction >= 0) { view.selectedAction = -1; view.invalidate(); return; }
        exit();
    }

    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { MainActivity.prompt(this, title, hint, initial, numeric, done, view::invalidate); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { MainActivity.choose(this, title, items, checked, done, view::invalidate); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { MainActivity.confirm(this, title, message, ok, yes, view::invalidate); }
    @Override public void openFsm(String defId) { EntityDef d = project.def(defId); if (d != null) view.selectDef(d); }
}
