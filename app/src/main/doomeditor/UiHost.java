package com.retro.doomeditor;

/** Dialogs and navigation the hosting Activity provides to the custom views. */
interface UiHost {
    void prompt(String title,String hint,String initial,boolean numeric,java.util.function.Consumer<String> done);
    void choose(String title,String[] items,int checked,java.util.function.IntConsumer done);
    void confirm(String title,String message,String ok,Runnable yes);
    void openFsm(String defId);
}
