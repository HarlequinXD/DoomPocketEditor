package com.retro.doomeditor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditorView editor;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String crash = getSharedPreferences(DoomApp.CRASH_PREFS, 0).getString(DoomApp.CRASH_KEY, null);
        if (crash != null) { showCrash(crash); return; }
        startEditor();
    }

    private void startEditor() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        editor = new EditorView(this);
        setContentView(editor);
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
        Button reset = new Button(this); reset.setText("Reset saved map & continue");
        Button go = new Button(this); go.setText("Continue");
        buttons.addView(copy); buttons.addView(reset); buttons.addView(go);
        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(report);
        body.setTextColor(Color.WHITE);
        body.setTextSize(12);
        body.setTextIsSelectable(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", report));
            Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show();
        });
        reset.setOnClickListener(v -> {
            getSharedPreferences("project", 0).edit().clear().commit();
            clearCrash();
            startEditor();
        });
        go.setOnClickListener(v -> { clearCrash(); startEditor(); });
        setContentView(root);
    }

    private void clearCrash() {
        getSharedPreferences(DoomApp.CRASH_PREFS, 0).edit().remove(DoomApp.CRASH_KEY).commit();
    }

    @Override public void onBackPressed() {
        if (editor != null && editor.exitPlay()) return;
        super.onBackPressed();
    }
}
