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
    @Override public void prompt(String title, String hint, String initial, boolean numeric, java.util.function.Consumer<String> done) { MainActivity.prompt(this, title, hint, initial, numeric, done, view::afterEdit); }
    @Override public void choose(String title, String[] items, int checked, java.util.function.IntConsumer done) { MainActivity.choose(this, title, items, checked, done, view::afterEdit); }
    @Override public void confirm(String title, String message, String ok, Runnable yes) { MainActivity.confirm(this, title, message, ok, yes, view::afterEdit); }
    @Override public void openFsm(String defId) {}
    // picture picker for the main menu background and studio logo
    private static final int REQ_IMAGE = 61;
    private java.util.function.Consumer<android.graphics.Bitmap> imageDone;
    @Override public void pickImage(java.util.function.Consumer<android.graphics.Bitmap> done) {
        imageDone = done;
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE); i.setType("image/*");
        try { startActivityForResult(i, REQ_IMAGE); } catch (Exception e) { Toast.makeText(this, "No picture picker available", Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null || imageDone == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inSampleSize = 1;
            byte[] bytes = readAll(in);
            o.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int side = Math.max(o.outWidth, o.outHeight); o.inSampleSize = 1; while (side / o.inSampleSize > 2048) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (b == null) { Toast.makeText(this, "That picture couldn't be read", Toast.LENGTH_SHORT).show(); return; }
            imageDone.accept(b); view.afterEdit();
        } catch (Exception e) { Toast.makeText(this, "That picture couldn't be read", Toast.LENGTH_SHORT).show(); }
    }
    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n); return out.toByteArray();
    }
}
