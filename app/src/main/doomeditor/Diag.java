package com.retro.doomeditor;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Finds out why the app was closed when it wasn't an ordinary Java crash: Android (11+) keeps the reason when it
 * kills an app for freezing (ANR), a native/graphics-driver crash or running out of memory. A once-a-second note
 * of what the game was doing (frame time, lights, blood, enemies) is saved so the report shows the moment before.
 */
final class Diag {
    private Diag(){}
    private static long lastNote;
    /** Remembers the current game state, at most once a second (cheap). */
    static void note(Context c,String state){
        long now=System.currentTimeMillis();if(now-lastNote<1000)return;lastNote=now;
        try{c.getSharedPreferences("diag",0).edit().putString("last",new java.util.Date(now)+"  "+state).apply();}catch(Throwable ignored){}
    }
    static void noteState(Context c,EditorView v){long now=System.currentTimeMillis();if(now-lastNote<1000)return;note(c,v.diagState());}
    /** A report if the previous run was killed by the system for a reason worth showing, else null. */
    static String previousExit(Context c){
        if(Build.VERSION.SDK_INT<30)return null;
        try{
            ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> list=am.getHistoricalProcessExitReasons(c.getPackageName(),0,1);
            if(list==null||list.isEmpty())return null;
            ApplicationExitInfo e=list.get(0);
            android.content.SharedPreferences p=c.getSharedPreferences("diag",0);
            long seen=p.getLong("exitSeen",0);if(e.getTimestamp()<=seen)return null;
            p.edit().putLong("exitSeen",e.getTimestamp()).apply();
            int r=e.getReason();String name;
            switch(r){
                case ApplicationExitInfo.REASON_ANR:name="Froze (Android closed it: app not responding)";break;
                case ApplicationExitInfo.REASON_CRASH_NATIVE:name="Native crash (graphics driver or system library)";break;
                case ApplicationExitInfo.REASON_LOW_MEMORY:name="Closed for low memory";break;
                case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:name="Closed for using too much CPU or memory";break;
                case ApplicationExitInfo.REASON_SIGNALED:name="Killed by a system signal";break;
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:name="Failed to start";break;
                default:return null;   // closed normally, swiped away, Java crash (reported separately) …
            }
            StringBuilder b=new StringBuilder();
            b.append(name).append("\n").append(new java.util.Date(e.getTimestamp())).append("\n");
            if(e.getDescription()!=null)b.append("Details: ").append(e.getDescription()).append("\n");
            b.append("Memory: ").append(e.getPss()/1024).append(" MB PSS, ").append(e.getRss()/1024).append(" MB RSS\n");
            b.append("Renderer: ").append(Renderer.useGpu?"GPU "+Renderer.gpuLines+" lines":"software").append("\n");
            String last=p.getString("last",null);if(last!=null)b.append("Last game state: ").append(last).append("\n");
            if(r==ApplicationExitInfo.REASON_ANR||r==ApplicationExitInfo.REASON_CRASH_NATIVE){
                try(InputStream in=e.getTraceInputStream()){
                    if(in!=null){b.append("\nTrace:\n");BufferedReader rd=new BufferedReader(new InputStreamReader(in));String line;int n=0;boolean main=r!=ApplicationExitInfo.REASON_ANR;
                        while((line=rd.readLine())!=null&&n<140){if(!main&&line.startsWith("\"main\""))main=true;if(main){b.append(line).append("\n");n++;}}}
                }catch(Throwable ignored){}
            }
            return b.toString();
        }catch(Throwable t){return null;}
    }
}
