package com.retro.doomeditor;

import android.app.Application;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Records any uncaught crash so MainActivity can show the stack trace on the next launch. */
public class DoomApp extends Application {
    static final String CRASH_PREFS = "crash";
    static final String CRASH_KEY = "last";

    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            record(this, error);
            if (previous != null) previous.uncaughtException(thread, error);
            else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
        });
    }

    static void record(android.content.Context ctx, Throwable error) {
        try {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            String text = "Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + "), "
                    + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n\n" + sw;
            // commit() rather than apply(): the process is about to die.
            ctx.getSharedPreferences(CRASH_PREFS, 0).edit().putString(CRASH_KEY, text).commit();
        } catch (Throwable ignored) {}
    }
}
