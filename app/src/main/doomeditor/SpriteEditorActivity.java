package com.retro.doomeditor;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/** Hosts the pixel art sprite editor; sprites are stored inside the project file. */
public class SpriteEditorActivity extends Activity implements SpriteEditorView.Host {
    private SpriteEditorView view;
    private Project project;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        project = Project.get(this);
        String def = getIntent() != null ? getIntent().getStringExtra("def") : null;
        view = new SpriteEditorView(this, project, def, this);
        setContentView(view);
    }
    @Override protected void onResume() { super.onResume(); getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512); }
    @Override protected void onPause() { super.onPause(); project.save(this); }
    @Override public void saved() { project.save(this); Toast.makeText(this, "Sprite saved to project", Toast.LENGTH_SHORT).show(); }
    @Override public void exit() { project.save(this); finish(); }
    @Override public void onBackPressed() {
        if (view.pickerOpen) { view.pickerOpen = false; view.invalidate(); return; }
        exit();
    }
    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { MainActivity.prompt(this, title, hint, initial, numeric, done, view::invalidate); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { MainActivity.choose(this, title, items, checked, done, view::invalidate); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { MainActivity.confirm(this, title, message, ok, yes, view::invalidate); }
    @Override public void openFsm(String defId) {}
}
