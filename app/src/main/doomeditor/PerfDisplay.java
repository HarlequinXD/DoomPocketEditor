package com.retro.doomeditor;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

/** Display-mode helper used by the activities (see {@link Perf}). */
final class PerfDisplay {
    private PerfDisplay(){}
    /** Ask for a 60 Hz display mode (same resolution) instead of 90/120 Hz. */
    static void limitRefreshRate(Activity a){
        try{
            WindowManager.LayoutParams lp=a.getWindow().getAttributes();
            lp.preferredRefreshRate=60f;
            if(Build.VERSION.SDK_INT>=23){
                Display d=a.getWindowManager().getDefaultDisplay();
                Display.Mode cur=d.getMode(),best=null;
                for(Display.Mode m:d.getSupportedModes()){
                    if(m.getPhysicalWidth()!=cur.getPhysicalWidth()||m.getPhysicalHeight()!=cur.getPhysicalHeight())continue;
                    float hz=m.getRefreshRate();if(hz<59f)continue;
                    if(best==null||Math.abs(hz-60f)<Math.abs(best.getRefreshRate()-60f))best=m;
                }
                if(best!=null)lp.preferredDisplayModeId=best.getModeId();
            }
            a.getWindow().setAttributes(lp);
        }catch(Throwable ignored){}
    }
}
