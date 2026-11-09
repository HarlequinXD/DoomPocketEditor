package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * Shown when the editor launches: warms up textures, sprites, sounds and the action library on a
 * background thread, with a progress bar and a line saying what is loading, then hands over.
 */
final class LoadingView extends View implements Pointer.Aware {
    @Override public Ui pointerUi(){return ui;}

    private final Ui ui;
    private volatile int step;private volatile String label="Starting up";
    private float shown;private long last,start;
    private final Runnable done;private boolean finished;
    private static final String[] STEPS={"Reading settings","Painting wall and floor textures","Drawing sprites","Synthesising sound effects","Loading FSM actions","Finding your projects","Building 3D models","Opening the editor"};

    LoadingView(Context c,Runnable done){
        super(c);ui=new Ui(c);this.done=done;start=last=System.nanoTime();
        final Context app=c.getApplicationContext();
        Thread t=new Thread(()->{
            try{
                run(0,()->{GameSettings.load(app);Perf.saver(app);Renderer.loadPrefs(app);});
                run(1,Textures::init);
                run(2,Sprites::init);
                run(3,()->{for(int i=0;i<SoundFx.COUNT;i++)SoundFx.builtin(i);});
                run(4,()->Actions.visibleCount());
                run(5,()->ProjectStore.list(app));
                run(6,MeshModels::warm);
            }catch(Throwable ignored){}   // anything skipped here simply loads on first use
            label=STEPS[STEPS.length-1];step=STEPS.length;
        },"loader");
        t.setPriority(Thread.NORM_PRIORITY);t.start();
    }
    private void run(int i,Runnable r){label=STEPS[i];step=i;long t0=System.nanoTime();
        try{r.run();}catch(Throwable ignored){}
        long spent=(System.nanoTime()-t0)/1000000;if(spent<140)try{Thread.sleep(140-spent);}catch(InterruptedException ignored){}   // long enough to read
    }
    private float dp(float v){return ui.dp(v);}

    @Override protected void onDraw(Canvas c){
        long now=System.nanoTime();float dt=Math.min(.05f,(now-last)*1e-9f);last=now;
        float w=getWidth(),h=getHeight();
        c.drawColor(0xff101215);
        // soft glow behind the logo
        ui.p.setShader(new android.graphics.RadialGradient(w*.5f,h*.46f,Math.max(w,h)*.5f,0x2ae8c070,0x00000000,android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ui.p);ui.p.setShader(null);
        // title, top left
        float ls=dp(34);ui.logo(c,dp(20),dp(18),ls);
        ui.textMid(c,"RPG Pocket Editor",dp(20)+ls+dp(12),dp(18)+ls*.5f,18,0xffe8ecf1,2,-1);
        // centre logo
        float s=Math.min(w,h)*.26f;ui.logo(c,w*.5f-s*.5f,h*.44f-s*.5f,s);
        // progress
        float target=Math.min(1,step/(float)STEPS.length);shown+=(target-shown)*Math.min(1,dt*7);if(target>=1&&shown>.995f)shown=1;
        float bw=Math.min(w*.62f,dp(460)),bl=w*.5f-bw*.5f,by=h*.44f+s*.5f+dp(42),bh=dp(10);
        ui.round(c,bl,by,bl+bw,by+bh,bh*.5f,0xff23272d);
        ui.round(c,bl,by,bl+Math.max(bh,bw*shown),by+bh,bh*.5f,0xffe8c070);
        ui.text(c,label+"…",bl,by+bh+dp(22),12.5f,0xffb4bcc8,1,-1);
        ui.text(c,Math.round(shown*100)+"%",bl+bw,by+bh+dp(22),12.5f,0xffe8c070,2,1);
        ui.text(c,"Fantasy RPG maker",w-dp(20),h-dp(18),10.5f,0x80a6b0bd,1,1);
        if(shown>=1&&!finished&&(now-start)>900_000_000L){finished=true;post(done);return;}
        postInvalidateOnAnimation();
    }
}
