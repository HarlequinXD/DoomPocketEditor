package com.retro.doomeditor;

import android.graphics.Canvas;
import android.graphics.RectF;
import java.util.ArrayDeque;

/**
 * Snapshot undo/redo shared by the editors. Each editor gives a snapshot function (JSON text of
 * what it edits) and a restore function. {@link #commit()} is called after every touch and dialog;
 * if the snapshot changed, the previous one becomes an undo step. Restores happen in place so
 * open selections can be found again by id.
 */
final class Undo {
    interface Snap {String take()throws Exception;}
    interface Restore {void apply(String s)throws Exception;}
    static final int MAX=60;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final Snap snap;private final Restore restore;private String last;

    Undo(Snap snap,Restore restore){this.snap=snap;this.restore=restore;}
    private String take(){try{return snap.take();}catch(Exception e){return null;}}
    /** Forgets the history and starts from what is there now (e.g. after opening something else). */
    void reset(){undo.clear();redo.clear();last=take();}
    /** Records a step if anything changed since the last call. */
    boolean commit(){
        String s=take();if(s==null)return false;
        if(last==null){last=s;return false;}
        if(s.equals(last))return false;
        undo.push(last);if(undo.size()>MAX)undo.removeLast();redo.clear();last=s;return true;
    }
    boolean canUndo(){return !undo.isEmpty();}
    boolean canRedo(){return !redo.isEmpty();}
    boolean undo(){commit();if(undo.isEmpty())return false;redo.push(last);last=undo.pop();return apply(last);}
    boolean redo(){commit();if(redo.isEmpty())return false;undo.push(last);last=redo.pop();return apply(last);}
    private boolean apply(String s){try{restore.apply(s);return true;}catch(Exception e){return false;}}

    /** Draws Undo and Redo buttons ending at {@code right}; returns the x left of them. */
    static float buttons(Ui ui,Canvas c,RectF r,Undo u,float right,float top,float bh,int hUndo,int hRedo){
        r.set(right-bh,top,right,top+bh);ui.button(c,r,Icons.REDO,null,u.canRedo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,hRedo);right-=bh+ui.dp(4);
        r.set(right-bh,top,right,top+bh);ui.button(c,r,Icons.UNDO,null,u.canUndo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,hUndo);right-=bh+ui.dp(8);
        return right;
    }
}
