package com.retro.doomeditor;

/** Dialogs and navigation the hosting Activity provides to the custom views. */
interface UiHost {
    void prompt(String title,String hint,String initial,boolean numeric,java.util.function.Consumer<String> done);
    void choose(String title,String[] items,int checked,java.util.function.IntConsumer done);
    void confirm(String title,String message,String ok,Runnable yes);
    void openFsm(String defId);
    default void openSprites(String defId){}
    /** Opens the Sound & Music editor on a tab (0 sounds, 1 music, 2 game audio). */
    default void openSound(int tab){}
    /** Opens the UI Editor (HUD & controls layout). */
    default void openUi(){}
    /** Opens the Content editor (0 items, 1 spells, 2 classes, 3 loot, 4 shops). */
    default void openContent(int tab){}
    /** Opens the Dialogue editor (0 conversations, 1 quests). */
    default void openDialogue(int tab){}
}
