package com.retro.doomeditor;

/** Hooks the PC build fills in (all null on Android): the display mode setting and quitting to the desktop. */
final class Platform {
    private Platform(){}
    /** Opens the display mode choice (full screen, window). */
    static Runnable display;
    /** The current display mode's name. */
    static java.util.function.Supplier<String> displayName;
    /** Closes the app. */
    static Runnable quit;
    static boolean pc(){return quit!=null;}
}
