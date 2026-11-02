package com.retro.doomeditor;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Player-facing options from the game's Options menu (difficulty, controls, audio). They belong to the
 * player's device, not to a project, so they are kept in app preferences and shared by every game.
 */
final class GameSettings {
    private GameSettings(){}
    static final String[] DIFFICULTIES={"Easy","Normal","Hard"};
    static int difficulty=1;
    static float lookSensitivity=1f,musicVolume=1f,soundVolume=1f;
    static boolean invertLook,combatText=true,cameraShake=true;
    /** Editor preference: show the splash screen and main menu when entering Play (off = straight into the level). */
    static boolean frontEnd=true;
    private static boolean loaded;

    static void load(Context c){
        if(loaded)return;loaded=true;
        try{SharedPreferences p=c.getSharedPreferences("game_settings",0);
            difficulty=Math.max(0,Math.min(2,p.getInt("difficulty",1)));lookSensitivity=p.getFloat("look",1f);musicVolume=p.getFloat("music",1f);soundVolume=p.getFloat("sound",1f);
            invertLook=p.getBoolean("invert",false);combatText=p.getBoolean("combatText",true);cameraShake=p.getBoolean("shake",true);frontEnd=p.getBoolean("frontEnd",true);
        }catch(Throwable ignored){}
    }
    static void save(Context c){
        try{c.getSharedPreferences("game_settings",0).edit().putInt("difficulty",difficulty).putFloat("look",lookSensitivity).putFloat("music",musicVolume).putFloat("sound",soundVolume)
                .putBoolean("invert",invertLook).putBoolean("combatText",combatText).putBoolean("shake",cameraShake).putBoolean("frontEnd",frontEnd).apply();}catch(Throwable ignored){}
    }
    /** Multiplier on damage the hero takes. */
    static float damageTaken(){return difficulty==0?.5f:difficulty==2?1.5f:1f;}
}
