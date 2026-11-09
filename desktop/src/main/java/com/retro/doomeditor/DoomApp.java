package com.retro.doomeditor;

/** PC: errors caught by the editor are written to crash.txt in the app's data folder. */
final class DoomApp {
    private DoomApp(){}
    static java.io.File home;
    static void record(android.content.Context c,Throwable t){
        try{java.io.File dir=home!=null?home:c.getFilesDir();java.io.StringWriter sw=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(sw));
            try(java.io.Writer w=new java.io.OutputStreamWriter(new java.io.FileOutputStream(new java.io.File(dir,"crash.txt"),true),"UTF-8")){w.write(new java.util.Date()+"\n"+sw+"\n");}}
        catch(Throwable ignored){}
        t.printStackTrace();
    }
}
