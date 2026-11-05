package com.retro.doomeditor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;

/** Hands exported game files to other apps (Share): read-only, only files in the app's cache "share" folder. */
public class ShareProvider extends ContentProvider {
    static final String AUTHORITY = "com.retro.doomeditor.share";
    static File dir(android.content.Context c) { File d = new File(c.getCacheDir(), "share"); if (!d.exists()) d.mkdirs(); return d; }
    static Uri uriFor(File f) { return Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(f.getName())); }
    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) return null;
        File f = new File(dir(getContext()), name);
        return f.exists() ? f : null;
    }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        File f = fileFor(uri);
        if (f == null || !"r".equals(mode)) throw new java.io.FileNotFoundException("Not shared: " + uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f = fileFor(uri); if (f == null) return null;
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor c = new MatrixCursor(cols); Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) row[i] = OpenableColumns.DISPLAY_NAME.equals(cols[i]) ? f.getName() : OpenableColumns.SIZE.equals(cols[i]) ? f.length() : null;
        c.addRow(row); return c;
    }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
