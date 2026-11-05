package com.retro.doomeditor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.OutputStream;

/** Hosts the Sound & Music editor; sounds and songs are stored inside the project file. */
public class SoundEditorActivity extends Activity implements SoundEditorView.Host {
    private static final int REQ_IMPORT=41,REQ_EXPORT=42;
    private SoundEditorView view;
    private Project project;
    private short[] pendingExport;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        PerfDisplay.limitRefreshRate(this);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        project = Project.get(this);
        int tab = getIntent() != null ? getIntent().getIntExtra("tab", 0) : 0;
        view = new SoundEditorView(this, project, tab, this);
        setContentView(view);
    }
    @Override protected void onResume() { super.onResume(); getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512); view.onResume(); }
    @Override protected void onPause() { super.onPause(); view.onPause(); project.saveNow(this); }   // written straight away so closing the app can't lose it
    @Override public void saved() { project.save(this); Toast.makeText(this, "Sounds and music saved to project", Toast.LENGTH_SHORT).show(); }
    @Override public void exit() { project.save(this); finish(); }
    @Override public void onBackPressed() { if (view.back()) return; exit(); }

    @Override public void importAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "application/ogg"});
        try { startActivityForResult(i, REQ_IMPORT); }
        catch (Exception e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT); g.addCategory(Intent.CATEGORY_OPENABLE); g.setType("audio/*");
            try { startActivityForResult(g, REQ_IMPORT); } catch (Exception e2) { Toast.makeText(this, "No file picker available", Toast.LENGTH_LONG).show(); }
        }
    }
    @Override public void exportWav(short[] pcm, String name) {
        if (pcm == null || pcm.length < 2) return;
        pendingExport = pcm;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/wav");
        i.putExtra(Intent.EXTRA_TITLE, name.replaceAll("[^A-Za-z0-9 _-]", "").trim() + ".wav");
        try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { Toast.makeText(this, "Can't save files on this device", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) { pendingExport = null; return; }
        final Uri uri = data.getData();
        if (req == REQ_IMPORT) {
            Toast.makeText(this, "Loading audio…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try { AudioIO.Result r = AudioIO.load(this, uri); runOnUiThread(() -> { view.imported(r); project.save(this); }); }
                catch (Throwable t) { runOnUiThread(() -> Toast.makeText(this, "Couldn't load that file: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), Toast.LENGTH_LONG).show()); }
            }, "AudioImport").start();
        } else if (req == REQ_EXPORT && pendingExport != null) {
            short[] pcm = pendingExport; pendingExport = null;
            try (OutputStream o = getContentResolver().openOutputStream(uri)) { AudioIO.writeWav(o, pcm); Toast.makeText(this, "WAV saved", Toast.LENGTH_SHORT).show(); }
            catch (Exception e) { Toast.makeText(this, "Couldn't save the WAV file", Toast.LENGTH_LONG).show(); }
        }
    }

    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { MainActivity.prompt(this, title, hint, initial, numeric, done, view::afterEdit); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { MainActivity.choose(this, title, items, checked, done, view::afterEdit); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { MainActivity.confirm(this, title, message, ok, yes, view::afterEdit); }
    @Override public void openFsm(String defId) {}
}
