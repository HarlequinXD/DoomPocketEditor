package com.retro.doomeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Hosts the PlayMaker-style FSM graph and provides its dialogs. */
public class FSMEditorActivity extends Activity implements FSMGraphView.Host {
    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    private FSMGraph graph;
    private FSMGraphView view;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        graph = FSMGraph.fromJson(getSharedPreferences("project", 0).getString("fsm_graph", ""));
        if (graph.nodes.isEmpty()) {
            FSMGraph.Node idle = graph.add("Idle", 0, 40), active = graph.add("Active", 280, 40);
            idle.transitions.put("FIRE", active.id);
            active.action = "WAIT"; active.argument = "1.5";
            active.transitions.put("FINISHED", idle.id);
        }
        view = new FSMGraphView(this, graph, this);
        setContentView(view);
    }

    private boolean persist() {
        try { getSharedPreferences("project", 0).edit().putString("fsm_graph", graph.toJson().toString()).apply(); return true; }
        catch (Exception e) { return false; }
    }
    @Override protected void onPause() { super.onPause(); persist(); }
    @Override public void save() { Toast.makeText(this, persist() ? "FSM saved" : "Save failed", Toast.LENGTH_SHORT).show(); }
    @Override public void exit() { persist(); finish(); }
    @Override public void onBackPressed() {
        if (view.linkFrom != null) { view.linkFrom = null; view.invalidate(); return; }
        persist(); super.onBackPressed();
    }

    private void prompt(String title, String message, String initial, java.util.function.Consumer<String> done) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(initial);
        input.setSelection(input.getText().length());
        FrameLayout box = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        AlertDialog.Builder d = new AlertDialog.Builder(this, DIALOG_THEME).setTitle(title).setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (dialog, which) -> { done.accept(input.getText().toString().trim()); view.invalidate(); });
        if (message != null) d.setMessage(message);
        d.show();
    }

    @Override public void rename(FSMGraph.Node node) {
        prompt("Rename state", null, node.name, t -> { if (!t.isEmpty()) node.name = t; });
    }
    @Override public void chooseAction(FSMGraph.Node node) {
        String[] titles = new String[FSMGraphView.ACTIONS.length];
        int current = 0;
        for (int i = 0; i < titles.length; i++) { titles[i] = FSMGraphView.actionTitle(FSMGraphView.ACTIONS[i]); if (FSMGraphView.ACTIONS[i].equals(node.action)) current = i; }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("Action")
                .setSingleChoiceItems(titles, current, (d, i) -> {
                    String chosen = FSMGraphView.ACTIONS[i];
                    if (!chosen.equals(node.action)) {
                        node.action = chosen;
                        switch (chosen) {
                            case "WAIT": node.argument = "1"; break;
                            case "SET_LIGHT": node.argument = "0, 255"; break;
                            case "SET_VARIABLE": node.argument = "alarm, 1"; break;
                            case "CHECK_VARIABLE": node.argument = "alarm, 1"; break;
                            default: node.argument = "";
                        }
                    }
                    d.dismiss(); view.invalidate();
                }).setNegativeButton("Cancel", null).show();
    }
    @Override public void editArgument(FSMGraph.Node node) {
        prompt(FSMGraphView.actionTitle(node.action), FSMGraphView.argumentHint(node.action), node.argument, t -> node.argument = t);
    }
    @Override public void chooseEvent(FSMGraph.Node from, FSMGraph.Node to) {
        String[] events = {"FINISHED", "FIRE", "TRUE", "Custom event…"};
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("Transition to " + to.name)
                .setItems(events, (d, i) -> {
                    if (i < 3) addTransition(from, to, events[i]);
                    else prompt("Custom event", "Event name, e.g. DOOR_OPEN", "", t -> { if (!t.isEmpty()) addTransition(from, to, t.toUpperCase(java.util.Locale.US).replace(' ', '_')); });
                }).setNegativeButton("Cancel", null).show();
    }
    private void addTransition(FSMGraph.Node from, FSMGraph.Node to, String event) {
        boolean replaced = from.transitions.containsKey(event);
        from.transitions.put(event, to.id);
        view.selected = from; view.invalidate();
        Toast.makeText(this, (replaced ? "Updated " : "Added ") + event + " → " + to.name, Toast.LENGTH_SHORT).show();
    }
}
