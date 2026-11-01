package com.retro.doomeditor;

import android.content.Context;

/**
 * Battery and performance settings shared by every screen.
 * <ul>
 *   <li>The display is asked to run at 60 Hz: phones with 120 Hz screens would otherwise draw
 *       every animation (and the game) twice as often for no visible gain in a retro renderer.</li>
 *   <li>Battery saver (in the project ⋯ menu) caps the game and the 3D view at 30 fps and stops
 *       the decorative 3D backdrops on the title and Home screens.</li>
 * </ul>
 */
final class Perf {
    private Perf(){}
    private static Boolean saver;

    static boolean saver(Context c){
        if(saver==null){try{saver=c.getSharedPreferences("app",0).getBoolean("saver",false);}catch(Throwable t){saver=false;}}
        return saver;
    }
    static void setSaver(Context c,boolean on){saver=on;try{c.getSharedPreferences("app",0).edit().putBoolean("saver",on).apply();}catch(Throwable ignored){}}

    /** Frame interval in ms for continuously animating game/3D screens. */
    static long gameFrameMs(Context c){return saver(c)?33:16;}

}
