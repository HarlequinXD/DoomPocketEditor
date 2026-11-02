package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

/**
 * Sound & Music editor. Three tabs:
 *  Sounds     - generate retro sound effects, import audio files, trim/pitch/fade/echo them, preview and export.
 *  Music      - songs made in a step-sequencer piano roll with lead/bass/pad/drum tracks and instruments.
 *  Game Audio - level music, volumes, and replacing the engine's built-in sounds with project sounds.
 * Project sounds and songs are what FSM actions such as Play Sound and Play Music pick from.
 */
public class SoundEditorView extends View {
    interface Host extends UiHost {void exit();void saved();void importAudio();void exportWav(short[] pcm,String name);}
    static final int TAB_SOUNDS=0,TAB_MUSIC=1,TAB_GAME=2;
    private static final String[] TABS={"Sounds","Music","Game Audio"};
    private static final int[] TAB_ICONS={Icons.WAVE,Icons.MUSIC,Icons.SPEAKER};
    private static final int[] TRACK_COLORS={0xff3d8bfd,0xfff0a030,0xff9b6dff,0xff3ecf8e,0xffef6b8a,0xff2fb8c8};
    private static final int[] BRUSHES={1,2,4,8,16};

    private static final int H_BACK=1,H_SAVE=2,H_NEW_SOUND=3,H_PLAY=4,H_STOP=5,H_AUTOPLAY=6,H_RESET_EDITS=7,H_RENAME=8,H_DUP=9,H_DELETE=10,H_EXPORT=11,
            H_REPLACE=12,H_WAVE=13,H_BUILTIN_COPY=14,H_BUILTIN_ASSIGN=15,H_NEW_SONG=16,H_MPLAY=17,H_INST=18,H_CLEAR=19,H_ADD_TRACK=20,H_TRACK_NAME=21,
            H_TRACK_SOUND=22,H_TRACK_DEL=23,H_TRACK_PREVIEW=24,H_LEVEL_MUSIC=25,H_SONG_RENAME=26,H_SONG_DUP=27,H_SONG_DEL=28,H_SONG_EXPORT=29,H_ROLL=30,H_KEYS=31,H_IMPORT=32,H_LIB=33,H_LIB_CAT=900,H_LIB_PLAY=3000,H_LIB_ADD=3100,
            H_TAB=100,H_SOUND=200,H_BUILTIN=400,H_PRESET=450,H_SONG=500,H_TRACK=600,H_MUTE=650,H_BRUSH=700,H_GAME=800,H_GAME_PLAY=850,H_SLIDER=10000;

    final Project project;
    private final Host host;
    private final Ui ui;
    private final Paint p;
    private final RectF r=new RectF();
    final SoundFx sfx=new SoundFx();
    private final Random rng=new Random();
    int tab=TAB_SOUNDS;
    SoundAsset sel;int selBuiltin=-1;
    Song song;int track;boolean instOpen,libOpen,libForLevel;private int libCat=-1;
    private int brush=2;private boolean autoPlay=true;
    String playingSong;
    SoundAsset replaceTarget;
    private float w,h,toolbarH,leftScroll,mainScroll,rollX,rollY=-1,leftContent,mainContent;
    private String hint;private long hintUntil;
    // sliders registered while drawing
    private static final class SliderRef {SoundAsset.P p;float[] arr;int idx;Runnable after;final RectF track=new RectF();final RectF value=new RectF();}
    private final ArrayList<SliderRef> sliders=new ArrayList<>(),sliderPool=new ArrayList<>();
    // touch
    private float downX,downY,lastX,lastY;private boolean moved,sliderDrag;private int downHit=-1,zone,trimHandle=-1;
    private final RectF waveRect=new RectF(),rollRect=new RectF(),gridRect=new RectF(),leftRect=new RectF(),mainRect=new RectF();
    // waveform cache
    private short[] peakClip;private int peakW;private float[] peaks;

    SoundEditorView(Context c,Project project,int startTab,Host host){
        super(c);this.project=project;this.host=host;ui=new Ui(c);p=ui.p;tab=startTab;
        if(!project.sounds.isEmpty())sel=project.sounds.get(0);
        if(!project.songs.isEmpty())song=project.songs.get(0);
    }
    void onResume(){sfx.start();invalidate();}
    void onPause(){sfx.stopMusic(0);sfx.stopSounds();sfx.stop();playingSong=null;}
    private float dp(float v){return ui.dp(v);}
    private float leftW(){return Math.max(dp(170),Math.min(dp(240),w*.24f));}
    private void hint(String s){hint=s;hintUntil=System.currentTimeMillis()+2600;invalidate();}
    boolean back(){if(libOpen){libOpen=false;mainScroll=0;invalidate();return true;}if(instOpen){instOpen=false;invalidate();return true;}return false;}

    // ================================================================== drawing
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();toolbarH=dp(48);
        ui.clearHits();sliderPool.addAll(sliders);sliders.clear();
        c.drawColor(Ui.BG);
        boolean animate=false;
        if(tab==TAB_SOUNDS)animate=drawSounds(c);else if(tab==TAB_MUSIC)animate=drawMusic(c);else drawGame(c);
        drawTopBar(c);
        if(hint!=null&&System.currentTimeMillis()<hintUntil){float tw=ui.measure(hint,12,1)+dp(32);float cx=w*.5f,b=h-dp(16);
            ui.pill(c,cx-tw*.5f,b-dp(32),cx+tw*.5f,b,0xf02a313a);ui.textMid(c,hint,cx,b-dp(16),12,Ui.TEXT,1,0);postInvalidateDelayed(300);}
        if(animate)postInvalidateOnAnimation();
    }
    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,toolbarH,Ui.SURFACE);ui.hline(c,0,w,toolbarH-dp(1),Ui.BORDER);
        float cy=toolbarH*.5f,bh=dp(34),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        float ib=dp(28);ui.round(c,dp(50),cy-ib*.5f,dp(50)+ib,cy+ib*.5f,dp(7),0xffe0679a);ui.icon(c,Icons.MUSIC,dp(50)+ib*.5f,cy,16,0xffffffff);
        boolean wide=w>dp(760);
        if(wide)ui.textMid(c,"Sound & Music",dp(88),cy,15,Ui.TEXT,1,-1);
        float saveW=wide?dp(84):bh;r.set(w-dp(10)-saveW,top,w-dp(10),top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);
        float tabW=Math.min(dp(132),(w-dp(230)-saveW)/3f),tl=wide?Math.max(dp(220),(w-tabW*3)*.5f):dp(88);
        for(int i=0;i<3;i++){r.set(tl+i*tabW,top,tl+(i+1)*tabW-dp(4),top+bh);ui.button(c,r,TAB_ICONS[i],TABS[i],i==tab?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_TAB+i);}
    }
    /** Starts a scrollable region; returns the y where content starts. */
    private float beginScroll(Canvas c,RectF area,float scroll){c.save();c.clipRect(area);return area.top-scroll;}
    private void hitIn(RectF rr,int id,RectF area){if(rr.bottom<area.top||rr.top>area.bottom)return;ui.hit(rr.left,Math.max(rr.top,area.top),rr.right,Math.min(rr.bottom,area.bottom),id);}
    private float clampScroll(float s,float content,RectF area){float max=Math.max(0,content-area.height());if(s>max||s<0){invalidate();return Math.max(0,Math.min(max,s));}return s;}
    private void caption(Canvas c,float x,float y,String s){ui.caption(c,s,x,y,Ui.TEXT_3);}
    private void listRow(Canvas c,RectF rr,int icon,int iconColor,String title,String sub,boolean on,int id,RectF area){
        ui.round(c,rr,dp(8),on?0xff26324a:Ui.SURFACE_2);if(on)ui.outline(c,rr,dp(8),Ui.ACCENT,dp(1.5f));
        float ib=dp(26);ui.round(c,rr.left+dp(8),rr.centerY()-ib*.5f,rr.left+dp(8)+ib,rr.centerY()+ib*.5f,dp(6),iconColor);ui.icon(c,icon,rr.left+dp(8)+ib*.5f,rr.centerY(),15,0xffffffff);
        float tx=rr.left+dp(42),tw=rr.right-tx-dp(8);
        ui.text(c,ui.ellipsize(title,12.5f,1,tw),tx,rr.centerY()-dp(2),12.5f,Ui.TEXT,1,-1);
        if(sub!=null)ui.text(c,ui.ellipsize(sub,10,0,tw),tx,rr.centerY()+dp(12),10,Ui.TEXT_3,0,-1);
        hitIn(rr,id,area);
    }
    private SliderRef slider(Canvas c,float l,float t,float rr,SoundAsset.P sp,float[] arr,int idx,Runnable after,RectF area){
        SliderRef s=sliderPool.isEmpty()?new SliderRef():sliderPool.remove(sliderPool.size()-1);
        s.p=sp;s.arr=arr;s.idx=idx;s.after=after;sliders.add(s);int id=H_SLIDER+sliders.size()-1;
        float v=arr[idx];
        r.set(l,t,rr,t+dp(46));ui.round(c,r,dp(7),Ui.SURFACE_2);
        ui.text(c,ui.ellipsize(sp.label,10.5f,1,rr-l-dp(90)),l+dp(10),t+dp(16),10.5f,Ui.TEXT_3,1,-1);
        String val=sp.format(v);
        if(sp.choice()&&sp.options.length==2&&sp.options[0].equals("Off")){
            boolean on=v>=.5f;float tx=rr-dp(44),ty=t+dp(15);ui.round(c,tx,ty,tx+dp(32),ty+dp(16),dp(8),on?Ui.ACCENT:Ui.SURFACE_3);p.setColor(0xffffffff);c.drawCircle(on?tx+dp(24):tx+dp(8),ty+dp(8),dp(6),p);
            ui.text(c,on?"On":"Off",l+dp(10),t+dp(36),12.5f,Ui.TEXT,1,-1);s.track.set(0,0,0,0);s.value.set(r);
        }else if(sp.choice()){
            ui.text(c,ui.ellipsize(val,12.5f,1,rr-l-dp(40)),l+dp(10),t+dp(36),12.5f,Ui.TEXT,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,rr-dp(16),t+dp(23),13,Ui.TEXT_3);
            s.track.set(0,0,0,0);s.value.set(r);
        }else{
            ui.text(c,val,rr-dp(10),t+dp(16),11.5f,Ui.TEXT,1,1);
            float tl=l+dp(12),tr=rr-dp(12),ty=t+dp(32),n=Math.max(0,Math.min(1,sp.norm(v)));
            ui.round(c,tl,ty-dp(2),tr,ty+dp(2),dp(2),Ui.SURFACE_3);
            float zero=sp.min<0?Math.max(0,Math.min(1,sp.norm(0))):0;float a=tl+(tr-tl)*Math.min(zero,n),b=tl+(tr-tl)*Math.max(zero,n);
            ui.round(c,a,ty-dp(2),b,ty+dp(2),dp(2),Ui.ACCENT);
            p.setColor(0xffffffff);c.drawCircle(tl+(tr-tl)*n,ty,dp(7),p);
            s.track.set(tl,t,tr,t+dp(46));s.value.set(rr-dp(80),t,rr,t+dp(22));
        }
        hitIn(r,id,area);return s;
    }
    /** Lays sliders in a grid; returns the y below them. */
    private float sliderGrid(Canvas c,float l,float rr,float y,SoundAsset.P[] ps,float[] arr,Runnable after,RectF area,int skip){
        int cols=rr-l>dp(560)?3:rr-l>dp(380)?2:1;float gap=dp(8),cw=(rr-l-gap*(cols-1))/cols;int k=0;
        for(int i=0;i<ps.length;i++){if(i==skip)continue;int col=k%cols,row=k/cols;float x=l+col*(cw+gap),yy=y+row*dp(52);slider(c,x,yy,x+cw,ps[i],arr,i,after,area);k++;}
        return y+((k+cols-1)/cols)*dp(52);
    }
    private float[] peaks(short[] clip,int width){
        if(clip==peakClip&&width==peakW&&peaks!=null)return peaks;
        peakClip=clip;peakW=width;peaks=new float[width*2];int n=clip.length;
        for(int x=0;x<width;x++){int a=(int)((long)x*n/width),b=Math.max(a+1,(int)((long)(x+1)*n/width));float lo=0,hi=0;
            for(int i=a;i<b&&i<n;i+=Math.max(1,(b-a)/64)){float v=clip[i]/32768f;if(v<lo)lo=v;if(v>hi)hi=v;}peaks[x*2]=lo;peaks[x*2+1]=hi;}
        return peaks;
    }
    private void drawWave(Canvas c,RectF rr,short[] clip,float t0,float t1,float play,boolean handles){
        ui.round(c,rr,dp(8),0xff101317);ui.outline(c,rr,dp(8),Ui.BORDER,dp(1));
        int W=Math.max(2,(int)(rr.width()-dp(8)));float[] pk=peaks(clip,W);float mid=rr.centerY(),amp=rr.height()*.42f,x0=rr.left+dp(4);
        ui.hline(c,rr.left+dp(4),rr.right-dp(4),mid,0xff252b33);
        for(int x=0;x<W;x++){float f=x/(float)W;boolean in=f>=t0&&f<=t1;
            ui.rect(c,x0+x,mid-Math.max(dp(.5f),pk[x*2+1]*amp),x0+x+1,mid+Math.max(dp(.5f),-pk[x*2]*amp),in?0xff5fa8ff:0xff3a4350);}
        if(handles){
            float a=x0+W*t0,b=x0+W*t1;
            ui.rect(c,rr.left,rr.top,a,rr.bottom,0x66000000);ui.rect(c,b,rr.top,rr.right,rr.bottom,0x66000000);
            ui.rect(c,a-dp(1),rr.top,a+dp(1),rr.bottom,Ui.ORANGE);ui.rect(c,b-dp(1),rr.top,b+dp(1),rr.bottom,Ui.ORANGE);
            ui.round(c,a-dp(5),rr.top+dp(2),a+dp(5),rr.top+dp(16),dp(3),Ui.ORANGE);ui.round(c,b-dp(5),rr.bottom-dp(16),b+dp(5),rr.bottom-dp(2),dp(3),Ui.ORANGE);
        }
        if(play>=0){float x=handles?x0+W*(t0+(t1-t0)*play):x0+W*play;ui.rect(c,x-dp(1),rr.top+dp(3),x+dp(1),rr.bottom-dp(3),0xffffffff);}
    }

    // ------------------------------------------------------------------ Sounds tab
    private boolean drawSounds(Canvas c){
        float L=dp(10),T=toolbarH+dp(10),B=h-dp(10),lw=leftW();
        leftRect.set(L,T,L+lw,B);ui.panel(c,L-dp(2),T-dp(2),L+lw+dp(2),B+dp(2),dp(10));
        float y=beginScroll(c,leftRect,leftScroll)+dp(8),il=L+dp(8),ir=L+lw-dp(8);
        r.set(il,y,ir,y+dp(36));ui.button(c,r,Icons.PLUS,"New sound",Ui.PRIMARY);hitIn(r,H_NEW_SOUND,leftRect);y+=dp(44);
        r.set(il,y,ir,y+dp(32));ui.button(c,r,Icons.FOLDER,"Import audio file",Ui.NORMAL);hitIn(r,H_IMPORT,leftRect);y+=dp(44);
        caption(c,il,y+dp(4),"Project sounds ("+project.sounds.size()+")");y+=dp(12);
        if(project.sounds.isEmpty()){ui.text(c,"None yet",il,y+dp(16),11,Ui.TEXT_3);y+=dp(26);}
        for(int i=0;i<project.sounds.size();i++){SoundAsset a=project.sounds.get(i);r.set(il,y,ir,y+dp(44));
            listRow(c,r,a.source==SoundAsset.SAMPLE?Icons.FOLDER:Icons.WAVE,a.source==SoundAsset.SAMPLE?0xff2fb8c8:0xffe0679a,a.name,
                    (a.source==SoundAsset.SAMPLE?"Sample":"Synth")+" · "+String.format(java.util.Locale.US,"%.2f s",a.seconds()),a==sel,H_SOUND+i,leftRect);y+=dp(50);}
        y+=dp(6);caption(c,il,y+dp(4),"Built-in sounds");y+=dp(12);
        for(int i=0;i<SoundFx.COUNT;i++){r.set(il,y,ir,y+dp(36));boolean on=sel==null&&selBuiltin==i;
            ui.round(c,r,dp(7),on?0xff26324a:Ui.SURFACE_2);if(on)ui.outline(c,r,dp(7),Ui.ACCENT,dp(1.5f));
            ui.icon(c,Icons.PLAY,il+dp(16),r.centerY(),12,Ui.TEXT_3);ui.textMid(c,ui.ellipsize(SoundFx.NAMES[i],12,0,ir-il-dp(40)),il+dp(32),r.centerY(),12,Ui.TEXT,0,-1);
            if(project.soundOverride(SoundFx.NAMES[i])!=null)ui.icon(c,Icons.LINK,ir-dp(14),r.centerY(),12,Ui.ORANGE);
            hitIn(r,H_BUILTIN+i,leftRect);y+=dp(40);}
        leftContent=y+leftScroll-leftRect.top+dp(8);c.restore();leftScroll=clampScroll(leftScroll,leftContent,leftRect);

        float ml=L+lw+dp(14),mr=w-dp(12);boolean anim=false;
        if(sel!=null)anim=drawSoundEditor(c,ml,T,mr,B);
        else if(selBuiltin>=0){
            short[] clip=SoundFx.builtin(selBuiltin);
            ui.text(c,SoundFx.NAMES[selBuiltin],ml,T+dp(20),17,Ui.TEXT,1,-1);ui.text(c,"Built-in engine sound · "+String.format(java.util.Locale.US,"%.2f s",clip.length/(float)SoundFx.RATE),ml,T+dp(38),11,Ui.TEXT_3,0,-1);
            waveRect.set(ml,T+dp(50),mr,T+dp(150));float pr=sfx.progress(clip);drawWave(c,waveRect,clip,0,1,pr,false);anim=pr>=0;
            float by=T+dp(162),bw=Math.min(dp(190),(mr-ml-dp(16))/3f);
            r.set(ml,by,ml+bw,by+dp(38));ui.button(c,r,Icons.PLAY,"Play",Ui.PRIMARY);ui.hit(r,H_PLAY);
            r.set(ml+bw+dp(8),by,ml+bw*2+dp(8),by+dp(38));ui.button(c,r,Icons.COPY,"Edit a copy",Ui.NORMAL);ui.hit(r,H_BUILTIN_COPY);
            r.set(ml+bw*2+dp(16),by,ml+bw*3+dp(16),by+dp(38));ui.button(c,r,Icons.LINK,"Replace in game",Ui.NORMAL);ui.hit(r,H_BUILTIN_ASSIGN);
            String ov=project.soundOverride(SoundFx.NAMES[selBuiltin]);
            float ty=by+dp(64);
            for(String line:new String[]{ov!=null?"In your game this is replaced by \""+project.soundLabel(ov)+"\".":"The engine plays this for "+builtinUse(selBuiltin)+".",
                    "FSM actions like Play Sound can use it by name. Edit a copy to change how it sounds."}){ui.text(c,ui.ellipsize(line,12,0,mr-ml),ml,ty,12,Ui.TEXT_2);ty+=dp(20);}
        }else{
            float cy=(T+B)*.5f;ui.icon(c,Icons.WAVE,(ml+mr)*.5f,cy-dp(50),40,Ui.TEXT_3);
            ui.text(c,"Make a sound effect",(ml+mr)*.5f,cy,17,Ui.TEXT,1,0);
            ui.text(c,"Generate one with the synth, import a WAV/MP3/OGG file, or start from a built-in sound.",(ml+mr)*.5f,cy+dp(22),12,Ui.TEXT_2,0,0);
            r.set((ml+mr)*.5f-dp(160),cy+dp(40),(ml+mr)*.5f-dp(4),cy+dp(78));ui.button(c,r,Icons.SPARKLE,"Generate",Ui.PRIMARY);ui.hit(r,H_PRESET);
            r.set((ml+mr)*.5f+dp(4),cy+dp(40),(ml+mr)*.5f+dp(160),cy+dp(78));ui.button(c,r,Icons.FOLDER,"Import file",Ui.NORMAL);ui.hit(r,H_IMPORT);
        }
        return anim;
    }
    private static String builtinUse(int i){
        switch(i){case SoundFx.PISTOL:case SoundFx.SHOTGUN:case SoundFx.CHAINGUN:case SoundFx.ROCKET:return "that weapon's shots";case SoundFx.EXPLODE:return "rocket explosions";
            case SoundFx.PUNCH:return "fist and melee hits";case SoundFx.DOOR:return "doors and lifts";case SoundFx.SWITCH:return "level exits";case SoundFx.PLAYER_PAIN:return "the player getting hurt";
            case SoundFx.PLAYER_DEATH:return "the player dying";case SoundFx.FIREBALL:return "fireballs";case SoundFx.TELEPORT:return "teleports";case SoundFx.SECRET:return "secrets";
            case SoundFx.OOF:return "bumping into walls, hard landings and empty guns";default:return "pickups and FSM actions that name it";}
    }
    private boolean drawSoundEditor(Canvas c,float ml,float T,float mr,float B){
        SoundAsset a=sel;
        float bh=dp(34),x=mr;
        int[] icons={Icons.TRASH,Icons.COPY,Icons.SAVE,Icons.PEN};int[] ids={H_DELETE,H_DUP,H_EXPORT,H_RENAME};
        for(int i=0;i<icons.length;i++){r.set(x-bh,T,x,T+bh);ui.button(c,r,icons[i],null,i==0?Ui.DANGER:Ui.NORMAL);ui.hit(r,ids[i]);x-=bh+dp(6);}
        if(a.source==SoundAsset.SAMPLE){r.set(x-dp(96),T,x,T+bh);ui.button(c,r,Icons.FOLDER,"Replace",Ui.NORMAL);ui.hit(r,H_REPLACE);x-=dp(102);}
        ui.text(c,ui.ellipsize(a.name,17,1,x-ml-dp(8)),ml,T+dp(17),17,Ui.TEXT,1,-1);r.set(ml,T,x,T+dp(24));ui.hit(r,H_RENAME);
        String sub=(a.source==SoundAsset.SAMPLE?"Sample"+(a.origin.isEmpty()?"":" · "+a.origin):"Synth sound")+" · "+String.format(java.util.Locale.US,"%.2f s",a.seconds())+" · id "+a.id;
        ui.text(c,ui.ellipsize(sub,11,0,x-ml-dp(8)),ml,T+dp(33),11,Ui.TEXT_3,0,-1);
        // waveform of the source with trim handles; playhead from the rendered clip
        waveRect.set(ml,T+dp(44),mr,T+dp(44)+Math.max(dp(70),Math.min(dp(110),(B-T)*.24f)));
        short[] out=a.render();float pr=sfx.progress(out);
        float t0=a.edit[SoundAsset.E_TRIM_START],t1=a.edit[SoundAsset.E_TRIM_END];
        if(a.edit[SoundAsset.E_REVERSE]>=.5f&&pr>=0)pr=1-pr;
        drawWave(c,waveRect,a.baseClip(),t0,t1,pr,true);ui.hit(waveRect,H_WAVE);
        ui.text(c,"Drag the orange handles to trim",waveRect.right-dp(8),waveRect.bottom-dp(6),9.5f,Ui.TEXT_3,0,1);
        float by=waveRect.bottom+dp(8);float bw=Math.min(dp(120),(mr-ml-dp(24))/4f);
        r.set(ml,by,ml+bw,by+dp(36));ui.button(c,r,Icons.PLAY,"Play",Ui.PRIMARY);ui.hit(r,H_PLAY);
        r.set(ml+bw+dp(8),by,ml+bw*2+dp(8),by+dp(36));ui.button(c,r,Icons.STOP,"Stop",Ui.NORMAL);ui.hit(r,H_STOP);
        r.set(ml+bw*2+dp(16),by,ml+bw*3+dp(16),by+dp(36));ui.button(c,r,Icons.ZAP,"Auto-play",autoPlay?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_AUTOPLAY);
        r.set(ml+bw*3+dp(24),by,ml+bw*4+dp(24),by+dp(36));ui.button(c,r,Icons.RESET,"Reset edits",Ui.NORMAL);ui.hit(r,H_RESET_EDITS);
        mainRect.set(ml,by+dp(44),mr,B);
        float y=beginScroll(c,mainRect,mainScroll);
        Runnable changed=()->{a.changed();};
        if(a.source==SoundAsset.SYNTH){
            caption(c,ml,y+dp(10),"Generate · tap a style, then fine-tune below");y+=dp(18);
            float px=ml,pw=Math.max(dp(74),Math.min(dp(104),(mr-ml)/6f-dp(6)));
            for(int i=0;i<SoundAsset.PRESETS.length;i++){if(px+pw>mr+1){px=ml;y+=dp(38);}
                r.set(px,y,px+pw,y+dp(32));ui.button(c,r,i==0?Icons.SPARKLE:i==1?Icons.RESET:-1,SoundAsset.PRESETS[i],i<2?Ui.ACTIVE:Ui.NORMAL);hitIn(r,H_PRESET+i,mainRect);px+=pw+dp(6);}
            y+=dp(44);
            caption(c,ml,y+dp(4),"Synth");y+=dp(12);
            y=sliderGrid(c,ml,mr,y,SoundAsset.SYNTH_PARAMS,a.synth,changed,mainRect,-1)+dp(6);
        }
        caption(c,ml,y+dp(4),"Edit");y+=dp(12);
        y=sliderGrid(c,ml,mr,y,SoundAsset.EDIT_PARAMS,a.edit,changed,mainRect,-1)+dp(8);
        mainContent=y+mainScroll-mainRect.top;c.restore();mainScroll=clampScroll(mainScroll,mainContent,mainRect);
        return pr>=0;
    }

    // ------------------------------------------------------------------ Music tab
    private boolean drawMusic(Canvas c){
        float L=dp(10),T=toolbarH+dp(10),B=h-dp(10),lw=leftW();
        leftRect.set(L,T,L+lw,B);ui.panel(c,L-dp(2),T-dp(2),L+lw+dp(2),B+dp(2),dp(10));
        float y=beginScroll(c,leftRect,leftScroll)+dp(8),il=L+dp(8),ir=L+lw-dp(8);
        r.set(il,y,ir,y+dp(36));ui.button(c,r,Icons.PLUS,"New song",Ui.PRIMARY);hitIn(r,H_NEW_SONG,leftRect);y+=dp(44);
        r.set(il,y,ir,y+dp(36));ui.button(c,r,Icons.LIST,"Music library ("+MusicLibrary.ALL.size()+")",libOpen?Ui.ACTIVE:Ui.NORMAL);hitIn(r,H_LIB,leftRect);y+=dp(44);
        caption(c,il,y+dp(4),"Songs ("+project.songs.size()+")");y+=dp(12);
        for(int i=0;i<project.songs.size();i++){Song s=project.songs.get(i);r.set(il,y,ir,y+dp(44));
            boolean lv=s.id.equals(project.levelMusic);
            listRow(c,r,Icons.MUSIC,lv?0xff3ecf8e:0xff9b6dff,s.name,s.bpm()+" BPM · "+s.bars()+" bars"+(lv?" · level music":""),s==song,H_SONG+i,leftRect);y+=dp(50);}
        if(song!=null){
            y+=dp(4);caption(c,il,y+dp(4),"Song");y+=dp(12);
            Song s=song;Runnable ch=this::songChanged;
            for(int i=0;i<Song.SONG_PARAMS.length;i++){slider(c,il,y,ir,Song.SONG_PARAMS[i],s.v,i,ch,leftRect);y+=dp(52);}
            float bw=(ir-il-dp(18))/4f;int[] ic={Icons.PEN,Icons.COPY,Icons.SAVE,Icons.TRASH};int[] id={H_SONG_RENAME,H_SONG_DUP,H_SONG_EXPORT,H_SONG_DEL};
            for(int i=0;i<4;i++){r.set(il+i*(bw+dp(6)),y,il+i*(bw+dp(6))+bw,y+dp(32));ui.button(c,r,ic[i],null,i==3?Ui.DANGER:Ui.NORMAL);hitIn(r,id[i],leftRect);}y+=dp(40);
            caption(c,il,y+dp(4),"Tracks");y+=dp(12);
            for(int i=0;i<s.tracks.size();i++){Song.Track t=s.tracks.get(i);r.set(il,y,ir,y+dp(44));
                listRow(c,r,t.drums()?Icons.GRID:Icons.PIANO,TRACK_COLORS[i%TRACK_COLORS.length],t.name,Song.TRACK_PARAMS[0].format(t.v[0])+" · "+t.notes.size()+" notes",i==track,H_TRACK+i,leftRect);
                RectF m=new RectF(ir-dp(36),y+dp(6),ir-dp(4),y+dp(38));ui.button(c,m,t.mute?Icons.CANCEL:Icons.SPEAKER,null,t.mute?Ui.DANGER:Ui.GHOST);hitIn(m,H_MUTE+i,leftRect);y+=dp(50);}
            if(s.tracks.size()<Song.MAX_TRACKS){r.set(il,y,ir,y+dp(32));ui.button(c,r,Icons.PLUS,"Add track",Ui.NORMAL);hitIn(r,H_ADD_TRACK,leftRect);y+=dp(40);}
        }
        leftContent=y+leftScroll-leftRect.top+dp(8);c.restore();leftScroll=clampScroll(leftScroll,leftContent,leftRect);

        float ml=L+lw+dp(14),mr=w-dp(12);
        if(libOpen)return drawLibrary(c,ml,T,mr,B);
        if(song==null){float cy=(T+B)*.5f;ui.icon(c,Icons.MUSIC,(ml+mr)*.5f,cy-dp(50),40,Ui.TEXT_3);
            ui.text(c,"Make some music",(ml+mr)*.5f,cy,17,Ui.TEXT,1,0);
            ui.text(c,"Songs play as level music or from the Play Music FSM action.",(ml+mr)*.5f,cy+dp(22),12,Ui.TEXT_2,0,0);
            r.set((ml+mr)*.5f-dp(90),cy+dp(40),(ml+mr)*.5f+dp(90),cy+dp(78));ui.button(c,r,Icons.PLUS,"New song",Ui.PRIMARY);ui.hit(r,H_NEW_SONG);
            r.set((ml+mr)*.5f-dp(90),cy+dp(86),(ml+mr)*.5f+dp(90),cy+dp(124));ui.button(c,r,Icons.LIST,"Music library",Ui.NORMAL);ui.hit(r,H_LIB);return false;}
        if(track>=song.tracks.size())track=0;
        Song.Track t=song.tracks.get(track);
        boolean playing=sfx.musicPlaying()&&song.id.equals(playingSong);
        float bh=dp(36),x=ml;
        r.set(x,T,x+dp(92),T+bh);ui.button(c,r,playing?Icons.STOP:Icons.PLAY,playing?"Stop":"Play",playing?Ui.SUCCESS:Ui.PRIMARY);ui.hit(r,H_MPLAY);x+=dp(98);
        r.set(x,T,x+dp(128),T+bh);ui.button(c,r,Icons.PIANO,instOpen?"Notes":"Instrument",instOpen?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_INST);x+=dp(134);
        if(!instOpen){
            boolean room=mr-x>dp(300);
            if(room){ui.textMid(c,"Length",x+dp(2),T+bh*.5f,11,Ui.TEXT_3,1,-1);x+=dp(48);}
            for(int i=0;i<BRUSHES.length&&!t.drums();i++){r.set(x,T,x+dp(32),T+bh);ui.button(c,r,-1,String.valueOf(BRUSHES[i]),brush==BRUSHES[i]?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_BRUSH+i);x+=dp(36);}
            r.set(mr-bh,T,mr,T+bh);ui.button(c,r,Icons.TRASH,null,Ui.DANGER);ui.hit(r,H_CLEAR);
        }
        rollRect.set(ml,T+bh+dp(8),mr,B);
        if(instOpen){drawInstrument(c,t);return playing;}
        drawRoll(c,t,playing);
        return playing;
    }
    /** Browser for the built-in fantasy songs: preview them and add copies to the project. */
    private boolean drawLibrary(Canvas c,float ml,float T,float mr,float B){
        mainRect.set(ml,T,mr,B);
        float y=beginScroll(c,mainRect,mainScroll);
        ui.text(c,"Fantasy music library",ml,y+dp(20),17,Ui.TEXT,1,-1);
        ui.text(c,ui.ellipsize(MusicLibrary.ALL.size()+" generated tracks. Preview one, then add a copy to your project to use it as level music, in Play Music actions, or to edit its notes.",11,0,mr-ml-dp(110)),ml,y+dp(38),11,Ui.TEXT_3,0,-1);
        r.set(mr-dp(100),y+dp(4),mr,y+dp(38));ui.button(c,r,Icons.CANCEL,"Close",Ui.NORMAL);hitIn(r,H_LIB,mainRect);
        y+=dp(50);
        float px=ml;
        for(int i=-1;i<MusicLibrary.CATEGORIES.length;i++){String label=i<0?"All ("+MusicLibrary.ALL.size()+")":MusicLibrary.CATEGORIES[i]+" ("+MusicLibrary.count(i)+")";
            float bw=ui.measure(label,11.5f,1)+dp(24);if(px+bw>mr+1){px=ml;y+=dp(36);}
            r.set(px,y,px+bw,y+dp(30));ui.button(c,r,-1,label,libCat==i?Ui.ACTIVE:Ui.NORMAL);hitIn(r,H_LIB_CAT+i+1,mainRect);px+=bw+dp(6);}
        y+=dp(42);
        int cols=mr-ml>dp(780)?2:1;float gap=dp(8),cw=(mr-ml-gap*(cols-1))/cols;int k=0;boolean anim=false;
        for(int i=0;i<MusicLibrary.ALL.size();i++){MusicLibrary.Entry e=MusicLibrary.ALL.get(i);if(libCat>=0&&e.category!=libCat)continue;
            float x=ml+(k%cols)*(cw+gap),yy=y+(k/cols)*dp(64);k++;
            boolean playing=sfx.musicPlaying()&&("lib_"+i).equals(playingSong);if(playing)anim=true;
            boolean added=false;for(Song s:project.songs)if(s.name.equals(e.name)){added=true;break;}
            r.set(x,yy,x+cw,yy+dp(58));ui.round(c,r,dp(9),playing?0xff1f3a2e:Ui.SURFACE_2);ui.outline(c,r,dp(9),playing?Ui.GREEN:Ui.BORDER,dp(1));
            RectF ab=new RectF(x+cw-dp(84),yy+dp(13),x+cw-dp(8),yy+dp(45));ui.button(c,ab,added?Icons.CHECK:Icons.PLUS,added?"Added":"Add",added?Ui.NORMAL:Ui.ACTIVE);hitIn(ab,H_LIB_ADD+i,mainRect);
            RectF pb=new RectF(ab.left-dp(84),yy+dp(13),ab.left-dp(6),yy+dp(45));ui.button(c,pb,playing?Icons.STOP:Icons.PLAY,playing?"Stop":"Play",playing?Ui.SUCCESS:Ui.PRIMARY);hitIn(pb,H_LIB_PLAY+i,mainRect);
            ui.round(c,x+dp(10),yy+dp(15),x+dp(38),yy+dp(43),dp(7),playing?Ui.GREEN:0xff9b6dff);ui.icon(c,Icons.MUSIC,x+dp(24),yy+dp(29),15,0xffffffff);
            float tx=x+dp(48),tw=cw-dp(228);
            ui.text(c,ui.ellipsize(e.name,13,1,tw),tx,yy+dp(25),13,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize(MusicLibrary.CATEGORIES[e.category]+" · "+e.info(),10,0,tw),tx,yy+dp(42),10,Ui.TEXT_3,0,-1);
            if(playing&&sfx.musicStep>=0){Song s=MusicLibrary.song(i);float f=(sfx.musicStep+1)/(float)s.steps();ui.rect(c,x+dp(10),yy+dp(53),x+dp(10)+(cw-dp(20))*f,yy+dp(55),Ui.GREEN);}
        }
        y+=((k+cols-1)/cols)*dp(64)+dp(8);
        mainContent=y+mainScroll-mainRect.top;c.restore();mainScroll=clampScroll(mainScroll,mainContent,mainRect);
        return anim;
    }
    private void addLibrarySong(int i){
        MusicLibrary.Entry e=MusicLibrary.ALL.get(i);
        Runnable go=()->{Song s=MusicLibrary.copyFor(project,i);project.songs.add(s);
            if(project.levelMusic.isEmpty()||libForLevel){libForLevel=false;project.levelMusic=s.id;hint("Added \""+e.name+"\" and set it as the level music");}else hint("Added \""+e.name+"\" to your songs");
            if(song==null)selectSong(s);libOpen=true;invalidate();};
        boolean added=false;for(Song s:project.songs)if(s.name.equals(e.name)){added=true;break;}
        if(added&&host!=null)host.confirm("Add another copy?","\""+e.name+"\" is already in your songs. Add a second copy you can change separately?","Add copy",go);else go.run();
    }
    private float rowH(Song.Track t){return t.drums()?dp(28):dp(17);}
    private float stepW(){return dp(22);}
    private int rows(Song.Track t){return t.drums()?Song.DRUMS.length:Song.MAX_NOTE-Song.MIN_NOTE+1;}
    private int pitchOfRow(Song.Track t,int row){return t.drums()?row:Song.MAX_NOTE-row;}
    private void drawRoll(Canvas c,Song.Track t,boolean playing){
        RectF R=rollRect;float kw=t.drums()?dp(78):dp(44),head=dp(18);
        gridRect.set(R.left+kw,R.top+head,R.right,R.bottom);
        float rh=rowH(t),sw=stepW();int rows=rows(t),steps=song.steps();
        if(rollY<0)rollY=t.drums()?0:(Song.MAX_NOTE-72)*rh;
        rollX=Math.max(0,Math.min(Math.max(0,steps*sw-gridRect.width()),rollX));
        rollY=Math.max(0,Math.min(Math.max(0,rows*rh-gridRect.height()),rollY));
        ui.round(c,R,dp(8),0xff121519);
        c.save();c.clipRect(gridRect);
        int r0=(int)(rollY/rh),r1=Math.min(rows-1,(int)((rollY+gridRect.height())/rh)+1);
        for(int row=r0;row<=r1;row++){int pitch=pitchOfRow(t,row);float y=gridRect.top+row*rh-rollY;
            boolean black=!t.drums()&&isBlack(pitch);
            ui.rect(c,gridRect.left,y,gridRect.right,y+rh,black?0xff15191e:0xff1b2026);
            ui.hline(c,gridRect.left,gridRect.right,y+rh-1,!t.drums()&&pitch%12==0?0xff2e3640:0xff20262d);}
        int s0=(int)(rollX/sw),s1=Math.min(steps-1,(int)((rollX+gridRect.width())/sw)+1);
        for(int s=s0;s<=s1+1&&s<=steps;s++){float x=gridRect.left+s*sw-rollX;
            ui.rect(c,x,gridRect.top,x+Math.max(1,dp(s%16==0?1.5f:.75f)),gridRect.bottom,s%16==0?0xff4a5563:s%4==0?0xff2c343e:0xff20262d);}
        float endX=gridRect.left+steps*sw-rollX;if(endX<gridRect.right)ui.rect(c,endX,gridRect.top,gridRect.right,gridRect.bottom,0xa0000000);
        if(playing&&sfx.musicStep>=0){float x=gridRect.left+sfx.musicStep*sw-rollX;ui.rect(c,x,gridRect.top,x+sw,gridRect.bottom,0x223ecf8e);}
        // other tracks as ghosts so parts line up
        if(!t.drums())for(int k=0;k<song.tracks.size();k++){Song.Track o=song.tracks.get(k);if(k==track||o.drums())continue;
            for(Song.Note n:o.notes){int row=Song.MAX_NOTE-n.pitch;if(row<r0||row>r1)continue;float x=gridRect.left+n.step*sw-rollX,y=gridRect.top+row*rh-rollY;
                ui.outline(c,x+dp(1),y+dp(2),x+n.len*sw-dp(1),y+rh-dp(2),dp(3),(TRACK_COLORS[k%TRACK_COLORS.length]&0x00ffffff)|0x70000000,dp(1));}}
        int col=TRACK_COLORS[track%TRACK_COLORS.length];
        for(Song.Note n:t.notes){int row=t.drums()?n.pitch:Song.MAX_NOTE-n.pitch;if(row<r0||row>r1)continue;
            float x=gridRect.left+n.step*sw-rollX,y=gridRect.top+row*rh-rollY;if(x>gridRect.right||x+n.len*sw<gridRect.left)continue;
            ui.round(c,x+dp(1),y+dp(1.5f),x+n.len*sw-dp(1),y+rh-dp(1.5f),dp(3),col);
            ui.rect(c,x+dp(1),y+dp(1.5f),x+dp(3),y+rh-dp(1.5f),Ui.brighten(col));}
        c.restore();
        ui.hit(gridRect,H_ROLL);
        // header with bar numbers
        c.save();c.clipRect(gridRect.left,R.top,R.right,gridRect.top);
        ui.rect(c,gridRect.left,R.top,R.right,gridRect.top,0xff1a1e23);
        for(int b=0;b<song.bars();b++){float x=gridRect.left+b*16*sw-rollX;ui.text(c,String.valueOf(b+1),x+dp(4),R.top+dp(13),10,Ui.TEXT_2,1,-1);}
        if(playing&&sfx.musicStep>=0){float x=gridRect.left+sfx.musicStep*sw-rollX;ui.rect(c,x,R.top+dp(14),x+sw,gridRect.top,Ui.GREEN);}
        c.restore();
        // keyboard
        c.save();c.clipRect(R.left,gridRect.top,gridRect.left,R.bottom);
        for(int row=r0;row<=r1;row++){int pitch=pitchOfRow(t,row);float y=gridRect.top+row*rh-rollY;
            if(t.drums()){ui.rect(c,R.left,y,gridRect.left-dp(2),y+rh-1,0xff232930);ui.textMid(c,ui.ellipsize(Song.DRUMS[row],10.5f,1,kw-dp(10)),R.left+dp(6),y+rh*.5f,10.5f,Ui.TEXT,1,-1);}
            else{boolean black=isBlack(pitch);ui.rect(c,R.left,y,gridRect.left-dp(2),y+rh-1,black?0xff23272d:0xffd9dde2);
                if(pitch%12==0||rh>=dp(16))ui.textMid(c,Song.noteName(pitch),gridRect.left-dp(6),y+rh*.5f,pitch%12==0?9.5f:8,black?Ui.TEXT_3:pitch%12==0?0xff1a1e23:0xff6e7987,pitch%12==0?2:0,1);}}
        c.restore();
        ui.hit(R.left,gridRect.top,gridRect.left,R.bottom,H_KEYS);
        ui.rect(c,R.left,R.top,gridRect.left,gridRect.top,0xff1a1e23);
        ui.textMid(c,ui.ellipsize(t.name,10,1,kw-dp(8)),R.left+dp(6),R.top+head*.5f,10,TRACK_COLORS[track%TRACK_COLORS.length],1,-1);
        ui.outline(c,R,dp(8),Ui.BORDER,dp(1));
        if(t.notes.isEmpty()){String s=t.drums()?"Tap the grid to place drum hits":"Tap the grid to place notes · tap a note to remove it · drag to scroll";
            float tw=ui.measure(s,11.5f,1)+dp(24),cx=gridRect.centerX(),cy=gridRect.centerY();ui.pill(c,cx-tw*.5f,cy-dp(14),cx+tw*.5f,cy+dp(14),0xe0232a33);ui.textMid(c,s,cx,cy,11.5f,Ui.TEXT_2,1,0);}
    }
    private static boolean isBlack(int pitch){int n=((pitch%12)+12)%12;return n==1||n==3||n==6||n==8||n==10;}
    private void drawInstrument(Canvas c,Song.Track t){
        mainRect.set(rollRect);float ml=mainRect.left,mr=mainRect.right;
        float y=beginScroll(c,mainRect,mainScroll);
        ui.text(c,ui.ellipsize(t.name,16,1,mr-ml-dp(200)),ml,y+dp(20),16,Ui.TEXT,1,-1);
        ui.text(c,"Instrument for this track. Changes apply while the song plays.",ml,y+dp(38),11,Ui.TEXT_3,0,-1);
        r.set(mr-dp(96),y+dp(4),mr,y+dp(38));ui.button(c,r,Icons.PEN,"Rename",Ui.NORMAL);hitIn(r,H_TRACK_NAME,mainRect);
        r.set(mr-dp(198),y+dp(4),mr-dp(102),y+dp(38));ui.button(c,r,Icons.PLAY,"Preview",Ui.NORMAL);hitIn(r,H_TRACK_PREVIEW,mainRect);
        y+=dp(52);
        Runnable ch=this::songChanged;
        y=sliderGrid(c,ml,mr,y,Song.TRACK_PARAMS,t.v,ch,mainRect,-1)+dp(6);
        if(t.inst()==Song.I_SAMPLE){r.set(ml,y,mr,y+dp(46));ui.round(c,r,dp(7),Ui.SURFACE_2);
            ui.text(c,"Sound to play (pitched by the note, C4 = original)",ml+dp(10),y+dp(16),10.5f,Ui.TEXT_3,1,-1);
            SoundAsset a=project.sound(t.sound);ui.text(c,a==null?"Choose a project sound…":a.name,ml+dp(10),y+dp(36),12.5f,a==null?Ui.AMBER:Ui.TEXT,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,mr-dp(16),y+dp(23),13,Ui.TEXT_3);
            hitIn(r,H_TRACK_SOUND,mainRect);y+=dp(54);}
        if(t.drums()){ui.text(c,"Drum kit: each row of the grid is one drum (kick, snare, hats, clap, toms, crash).",ml,y+dp(14),11.5f,Ui.TEXT_2);y+=dp(26);}
        if(song.tracks.size()>1){r.set(ml,y+dp(6),ml+dp(150),y+dp(40));ui.button(c,r,Icons.TRASH,"Delete track",Ui.DANGER);hitIn(r,H_TRACK_DEL,mainRect);y+=dp(48);}
        mainContent=y+mainScroll-mainRect.top;c.restore();mainScroll=clampScroll(mainScroll,mainContent,mainRect);
    }

    // ------------------------------------------------------------------ Game Audio tab
    private final float[] volumes=new float[2];
    private static final SoundAsset.P[] VOLUME_PARAMS={new SoundAsset.P("music","Music volume","%",0,1,.8f,false),new SoundAsset.P("sfx","Sound effects volume","%",0,1,1,false)};
    private void drawGame(Canvas c){
        float ml=dp(14),mr=w-dp(14),T=toolbarH+dp(10),B=h-dp(10);
        mainRect.set(ml,T,mr,B);
        float y=beginScroll(c,mainRect,mainScroll);
        int cols=mr-ml>dp(760)?3:mr-ml>dp(480)?2:1;float gap=dp(8),cw=(mr-ml-gap*(cols-1))/cols;
        caption(c,ml,y+dp(12),"Level");y+=dp(20);
        r.set(ml,y,ml+cw,y+dp(46));ui.round(c,r,dp(7),Ui.SURFACE_2);
        ui.text(c,"Music when the level starts",ml+dp(10),y+dp(16),10.5f,Ui.TEXT_3,1,-1);
        ui.text(c,ui.ellipsize(project.songLabel(project.levelMusic),12.5f,1,cw-dp(40)),ml+dp(10),y+dp(36),12.5f,project.levelMusic.isEmpty()?Ui.TEXT_2:Ui.GREEN,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,ml+cw-dp(16),y+dp(23),13,Ui.TEXT_3);
        hitIn(r,H_LEVEL_MUSIC,mainRect);
        volumes[0]=project.musicVolume;volumes[1]=project.sfxVolume;
        Runnable vch=()->{project.musicVolume=volumes[0];project.sfxVolume=volumes[1];sfx.musicVolume=volumes[0];sfx.sfxVolume=volumes[1];};
        for(int i=0;i<2;i++){int k=i+1;float x=ml+(k%cols)*(cw+gap),yy=y+(k/cols)*dp(52);slider(c,x,yy,x+cw,VOLUME_PARAMS[i],volumes,i,vch,mainRect);}
        y+=((3+cols-1)/cols)*dp(52)+dp(10);
        caption(c,ml,y+dp(4),"Game sounds · replace any built-in sound with one of yours");y+=dp(14);
        ui.text(c,"The engine plays these for weapons, doors, pain and pickups. FSM actions that name a built-in sound use the replacement too.",ml,y+dp(12),11,Ui.TEXT_3);y+=dp(22);
        for(int i=0;i<SoundFx.COUNT;i++){int col=i%cols,row=i/cols;float x=ml+col*(cw+gap),yy=y+row*dp(52);
            String ov=project.soundOverride(SoundFx.NAMES[i]);SoundAsset a=project.sound(ov);
            r.set(x,yy,x+cw,yy+dp(46));ui.round(c,r,dp(7),Ui.SURFACE_2);if(a!=null)ui.outline(c,r,dp(7),0x80f0a030,dp(1));
            ui.text(c,SoundFx.NAMES[i],x+dp(10),yy+dp(16),10.5f,Ui.TEXT_3,1,-1);
            ui.text(c,ui.ellipsize(a!=null?a.name:"Built-in",12.5f,1,cw-dp(70)),x+dp(10),yy+dp(36),12.5f,a!=null?Ui.ORANGE:Ui.TEXT_2,1,-1);
            hitIn(r,H_GAME+i,mainRect);
            RectF pb=new RectF(x+cw-dp(42),yy+dp(7),x+cw-dp(8),yy+dp(39));ui.button(c,pb,Icons.PLAY,null,Ui.GHOST);hitIn(pb,H_GAME_PLAY+i,mainRect);}
        y+=((SoundFx.COUNT+cols-1)/cols)*dp(52)+dp(8);
        mainContent=y+mainScroll-mainRect.top;c.restore();mainScroll=clampScroll(mainScroll,mainContent,mainRect);
    }

    // ================================================================== touch
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){
            downX=lastX=x;downY=lastY=y;moved=false;sliderDrag=false;trimHandle=-1;
            downHit=ui.hitAt(x,y);zone=0;
            if(downHit==H_WAVE&&sel!=null){zone=3;trimHandle=nearestHandle(x);dragTrim(x);invalidate();return true;}
            if(downHit==H_ROLL||downHit==H_KEYS)zone=4;
            else if(leftRect.contains(x,y)&&tab!=TAB_GAME)zone=1;
            else if(mainRect.contains(x,y)&&(tab!=TAB_MUSIC||instOpen||libOpen))zone=2;
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            float dx=x-downX,dy=y-downY;
            if(!moved&&Math.abs(dx)+Math.abs(dy)>dp(8)){moved=true;if(downHit>=H_SLIDER&&Math.abs(dx)>Math.abs(dy)){SliderRef s=sliderFor(downHit);sliderDrag=s!=null&&s.track.width()>0;}}
            if(zone==3){dragTrim(x);}
            else if(moved){
                if(sliderDrag){setSlider(sliderFor(downHit),x);}
                else if(zone==1)leftScroll-=y-lastY;
                else if(zone==2)mainScroll-=y-lastY;
                else if(zone==4){rollX-=x-lastX;rollY-=y-lastY;}
            }
            lastX=x;lastY=y;invalidate();return true;
        }
        if(a==MotionEvent.ACTION_UP){
            if(zone==3){if(autoPlay)playSel();}
            else if(sliderDrag){SliderRef s=sliderFor(downHit);if(s!=null)afterSlider(s);}
            else if(!moved){int hid=ui.hitAt(x,y);if(hid==downHit&&hid>=0)tap(hid,x,y);}
            zone=0;downHit=-1;sliderDrag=false;invalidate();return true;
        }
        if(a==MotionEvent.ACTION_CANCEL){zone=0;downHit=-1;sliderDrag=false;invalidate();}
        return true;
    }
    private SliderRef sliderFor(int hid){int i=hid-H_SLIDER;return i>=0&&i<sliders.size()?sliders.get(i):null;}
    private void setSlider(SliderRef s,float x){if(s==null||s.track.width()<=0)return;s.arr[s.idx]=s.p.fromNorm((x-s.track.left)/s.track.width());if(s.after!=null)s.after.run();}
    private void afterSlider(SliderRef s){
        if(tab==TAB_SOUNDS&&autoPlay)playSel();
        if(tab==TAB_MUSIC&&instOpen&&s.arr==currentTrackVals())previewTrack(60);
    }
    private float[] currentTrackVals(){return song!=null&&track<song.tracks.size()?song.tracks.get(track).v:null;}
    private int nearestHandle(float x){float w0=waveRect.left+dp(4),ww=waveRect.width()-dp(8);float a=w0+ww*sel.edit[SoundAsset.E_TRIM_START],b=w0+ww*sel.edit[SoundAsset.E_TRIM_END];return Math.abs(x-a)<=Math.abs(x-b)?0:1;}
    private void dragTrim(float x){
        float f=Math.max(0,Math.min(1,(x-waveRect.left-dp(4))/(waveRect.width()-dp(8))));
        if(trimHandle==0)sel.edit[SoundAsset.E_TRIM_START]=Math.min(f,sel.edit[SoundAsset.E_TRIM_END]-.01f);
        else sel.edit[SoundAsset.E_TRIM_END]=Math.max(f,sel.edit[SoundAsset.E_TRIM_START]+.01f);
        sel.changed();
    }
    private void tap(int hid,float x,float y){
        if(hid==H_BACK){if(host!=null)host.exit();return;}
        if(hid==H_SAVE){if(host!=null)host.saved();return;}
        if(hid>=H_TAB&&hid<H_TAB+3){tab=hid-H_TAB;mainScroll=0;leftScroll=0;invalidate();return;}
        if(hid>=H_SLIDER){SliderRef s=sliderFor(hid);if(s==null)return;
            if(s.p.choice()){
                if(s.p.options.length==2&&s.p.options[0].equals("Off")){s.arr[s.idx]=s.arr[s.idx]>=.5f?0:1;if(s.after!=null)s.after.run();afterSlider(s);}
                else if(host!=null)host.choose(s.p.label,s.p.options,Math.round(s.arr[s.idx]),k->{s.arr[s.idx]=k;if(s.after!=null)s.after.run();afterSlider(s);});
                return;}
            if(s.value.contains(x,y)&&host!=null){host.prompt(s.p.label,"From "+s.p.format(s.p.min)+" to "+s.p.format(s.p.max),trimNum(s.arr[s.idx]),true,t->{
                try{s.arr[s.idx]=s.p.clamp(Float.parseFloat(t.trim()));if(s.after!=null)s.after.run();afterSlider(s);}catch(NumberFormatException ignored){}});return;}
            setSlider(s,x);afterSlider(s);return;}
        switch(tab){case TAB_SOUNDS:tapSounds(hid);break;case TAB_MUSIC:tapMusic(hid,x,y);break;default:tapGame(hid);}
        invalidate();
    }
    private static String trimNum(float v){String s=String.format(java.util.Locale.US,"%.3f",v);while(s.contains(".")&&(s.endsWith("0")||s.endsWith(".")))s=s.substring(0,s.length()-1);return s;}

    // ------------------------------------------------------------------ Sounds actions
    private void playSel(){
        sfx.stopSounds();
        if(sel!=null)sfx.play(sel.render(),1,1);else if(selBuiltin>=0)sfx.play(SoundFx.builtin(selBuiltin),1,1);
        invalidate();
    }
    private SoundAsset addSound(String name){SoundAsset a=new SoundAsset();a.name=name;a.id=project.uniqueId("snd_"+name);project.sounds.add(a);sel=a;selBuiltin=-1;mainScroll=0;return a;}
    private void tapSounds(int hid){
        if(hid>=H_SOUND&&hid<H_SOUND+project.sounds.size()){sel=project.sounds.get(hid-H_SOUND);selBuiltin=-1;mainScroll=0;playSel();return;}
        if(hid>=H_BUILTIN&&hid<H_BUILTIN+SoundFx.COUNT){sel=null;selBuiltin=hid-H_BUILTIN;playSel();return;}
        if(hid>=H_PRESET&&hid<H_PRESET+SoundAsset.PRESETS.length){
            SoundAsset a=sel;if(a==null||a.source!=SoundAsset.SYNTH)a=addSound(hid==H_PRESET?"Sound":SoundAsset.PRESETS[hid-H_PRESET]);
            a.preset(hid-H_PRESET,rng);a.changed();playSel();return;}
        switch(hid){
            case H_NEW_SOUND:if(host==null)return;
                host.choose("New sound",new String[]{"Generate with the synth","Import an audio file (WAV, MP3, OGG…)","Edit a copy of a built-in sound"},-1,k->{
                    if(k==0){SoundAsset a=addSound("Sound "+(project.sounds.size()+1));a.preset(2,rng);playSel();}
                    else if(k==1){replaceTarget=null;host.importAudio();}
                    else host.choose("Copy which sound?",SoundFx.NAMES,-1,b->{copyBuiltin(b);invalidate();});
                    invalidate();});break;
            case H_IMPORT:replaceTarget=null;if(host!=null)host.importAudio();break;
            case H_REPLACE:replaceTarget=sel;if(host!=null)host.importAudio();break;
            case H_PLAY:playSel();break;
            case H_STOP:sfx.stopSounds();break;
            case H_AUTOPLAY:autoPlay=!autoPlay;hint(autoPlay?"Sounds play after every change":"Auto-play off");break;
            case H_RESET_EDITS:if(sel!=null){sel.resetEdits();if(autoPlay)playSel();}break;
            case H_RENAME:if(sel!=null&&host!=null){SoundAsset a=sel;host.prompt("Rename sound",null,a.name,false,t->{if(!t.trim().isEmpty())a.name=t.trim();});}break;
            case H_DUP:if(sel!=null){SoundAsset c=sel.copy();c.name=sel.name+" copy";c.id=project.uniqueId("snd_"+c.name);project.sounds.add(project.sounds.indexOf(sel)+1,c);sel=c;hint("Duplicated");}break;
            case H_EXPORT:if(sel!=null&&host!=null)host.exportWav(sel.render(),sel.name);break;
            case H_DELETE:if(sel!=null&&host!=null){SoundAsset a=sel;int uses=uses(a.id);
                host.confirm("Delete \""+a.name+"\"?",uses>0?"It is used in "+uses+" place"+(uses==1?"":"s")+" (FSM actions, game sounds or instruments); those will go silent or fall back to the built-in.":"This can't be undone.","Delete",()->{
                    project.sounds.remove(a);project.soundOverrides.values().removeIf(v->v.equals(a.id));sel=project.sounds.isEmpty()?null:project.sounds.get(0);invalidate();});}break;
            case H_BUILTIN_COPY:if(selBuiltin>=0)copyBuiltin(selBuiltin);break;
            case H_BUILTIN_ASSIGN:if(selBuiltin>=0)assignOverride(selBuiltin);break;
        }
    }
    private SoundAsset copyBuiltin(int b){
        SoundAsset a=addSound(SoundFx.NAMES[b]+" (edit)");a.source=SoundAsset.SAMPLE;a.setData(SoundFx.builtin(b).clone());a.origin="built-in "+SoundFx.NAMES[b];
        playSel();hint("Copied – use Game Audio to make the game play it");return a;
    }
    private int uses(String id){
        int n=0;for(String v:project.soundOverrides.values())if(v.equals(id))n++;
        for(Song s:project.songs)for(Song.Track t:s.tracks)if(t.inst()==Song.I_SAMPLE&&id.equals(t.sound))n++;
        for(EntityDef d:project.defs)for(FSMGraph.Node nd:d.fsm.nodes)for(FSMGraph.Action ac:nd.actions)for(String v:ac.params.values())if(id.equals(v))n++;
        return n;
    }
    /** Called by the activity with a decoded file. */
    void imported(AudioIO.Result res){
        String base=res.name.replaceAll("\\.[A-Za-z0-9]{2,5}$","");
        SoundAsset a=replaceTarget!=null&&project.sounds.contains(replaceTarget)?replaceTarget:addSound(base.isEmpty()?"Imported":base);
        a.source=SoundAsset.SAMPLE;a.setData(res.pcm);a.origin=res.name;a.resetEdits();sel=a;selBuiltin=-1;tab=TAB_SOUNDS;replaceTarget=null;
        playSel();hint(String.format(java.util.Locale.US,"Imported %.1f s%s",res.pcm.length/(float)SoundFx.RATE,res.cut?" (cut to "+SoundAsset.MAX_SECONDS+" s)":""));
    }
    private void assignOverride(int b){
        if(host==null)return;String name=SoundFx.NAMES[b];
        String[] items=new String[project.sounds.size()+3];items[0]="Built-in (default)";int checked=0;String cur=project.soundOverride(name);
        for(int i=0;i<project.sounds.size();i++){items[i+1]=project.sounds.get(i).name;if(project.sounds.get(i).id.equals(cur))checked=i+1;}
        items[items.length-2]="+ New: edit a copy of the built-in";items[items.length-1]="+ New: synth sound";
        host.choose("Game sound: "+name,items,checked,k->{
            if(k==0)project.soundOverrides.remove(name);
            else if(k==items.length-2){SoundAsset a=copyBuiltin(b);project.soundOverrides.put(name,a.id);tab=TAB_SOUNDS;}
            else if(k==items.length-1){SoundAsset a=addSound(name+" (new)");a.preset(b==SoundFx.EXPLODE?4:b<=SoundFx.ROCKET?3:b==SoundFx.PUNCH?6:2,rng);project.soundOverrides.put(name,a.id);tab=TAB_SOUNDS;playSel();}
            else project.soundOverrides.put(name,project.sounds.get(k-1).id);
            sfx.useProject(project);invalidate();});
    }

    // ------------------------------------------------------------------ Music actions
    private void songChanged(){
        if(song==null)return;
        if(sfx.musicPlaying()&&song.id.equals(playingSong))sfx.updateMusic(song.compile(project));
    }
    private void selectSong(Song s){song=s;track=0;rollX=0;rollY=-1;instOpen=false;mainScroll=0;}
    private void previewTrack(int pitch){if(song==null)return;Song.Track t=song.tracks.get(track);sfx.note(Song.instFor(t,project),t.drums()?pitch:pitch,t.drums()?1:.35f);}
    private void tapMusic(int hid,float x,float y){
        if(hid>=H_SONG&&hid<H_SONG+project.songs.size()){Song s=project.songs.get(hid-H_SONG);if(s!=song||libOpen){if(sfx.musicPlaying())sfx.stopMusic(0);playingSong=null;selectSong(s);libOpen=false;}return;}
        if(song!=null&&hid>=H_TRACK&&hid<H_TRACK+song.tracks.size()){int t=hid-H_TRACK;if(t!=track){track=t;rollY=-1;}else instOpen=!instOpen;mainScroll=0;return;}
        if(song!=null&&hid>=H_MUTE&&hid<H_MUTE+song.tracks.size()){Song.Track t=song.tracks.get(hid-H_MUTE);t.mute=!t.mute;songChanged();hint(t.name+(t.mute?" muted":" on"));return;}
        if(hid>=H_BRUSH&&hid<H_BRUSH+BRUSHES.length){brush=BRUSHES[hid-H_BRUSH];return;}
        if(hid>=H_LIB_CAT&&hid<=H_LIB_CAT+MusicLibrary.CATEGORIES.length){libCat=hid-H_LIB_CAT-1;mainScroll=0;return;}
        if(hid>=H_LIB_PLAY&&hid<H_LIB_PLAY+MusicLibrary.ALL.size()){int i=hid-H_LIB_PLAY;String id="lib_"+i;
            if(sfx.musicPlaying()&&id.equals(playingSong)){sfx.stopMusic(0);playingSong=null;}
            else{sfx.playMusic(MusicLibrary.song(i).compile(project),true,0);playingSong=id;}return;}
        if(hid>=H_LIB_ADD&&hid<H_LIB_ADD+MusicLibrary.ALL.size()){addLibrarySong(hid-H_LIB_ADD);return;}
        if(hid==H_LIB){libOpen=!libOpen;mainScroll=0;if(!libOpen&&playingSong!=null&&playingSong.startsWith("lib_")){sfx.stopMusic(0);playingSong=null;}return;}
        switch(hid){
            case H_NEW_SONG:if(host==null)return;
                host.choose("New song",new String[]{"Starter riff (drums, bass, lead and pad)","Empty song","Pick from the fantasy music library…"},0,k->{
                    if(k==2){libOpen=true;mainScroll=0;invalidate();return;}
                    String name="Song "+(project.songs.size()+1);Song s=k==0?Song.starter(name):Song.blank(name);s.id=project.uniqueId("mus_"+name);project.songs.add(s);selectSong(s);
                    if(project.levelMusic.isEmpty()&&project.songs.size()==1){project.levelMusic=s.id;hint("Set as the level music (change it in Game Audio)");}
                    invalidate();});break;
            case H_MPLAY:if(song==null)break;
                if(sfx.musicPlaying()&&song.id.equals(playingSong)){sfx.stopMusic(0);playingSong=null;}
                else{sfx.playMusic(song.compile(project),true,0);playingSong=song.id;}break;
            case H_INST:instOpen=!instOpen;mainScroll=0;break;
            case H_CLEAR:if(song!=null&&host!=null){Song.Track t=song.tracks.get(track);if(t.notes.isEmpty())break;host.confirm("Clear "+t.name+"?","Removes all "+t.notes.size()+" notes from this track.","Clear",()->{t.notes.clear();songChanged();invalidate();});}break;
            case H_ADD_TRACK:if(song!=null&&host!=null)host.choose("New track instrument",Song.TRACK_PARAMS[0].options,0,k->{Song.Track t=new Song.Track(Song.TRACK_PARAMS[0].options[k],k);song.tracks.add(t);track=song.tracks.size()-1;rollY=-1;songChanged();invalidate();});break;
            case H_TRACK_NAME:if(song!=null&&host!=null){Song.Track t=song.tracks.get(track);host.prompt("Track name",null,t.name,false,s->{if(!s.trim().isEmpty())t.name=s.trim();});}break;
            case H_TRACK_PREVIEW:previewTrack(60);break;
            case H_TRACK_SOUND:if(song!=null&&host!=null){Song.Track t=song.tracks.get(track);
                if(project.sounds.isEmpty()){hint("Make or import a sound in the Sounds tab first");break;}
                String[] items=new String[project.sounds.size()];int ch=-1;for(int i=0;i<items.length;i++){items[i]=project.sounds.get(i).name;if(project.sounds.get(i).id.equals(t.sound))ch=i;}
                host.choose("Instrument sound",items,ch,k->{t.sound=project.sounds.get(k).id;songChanged();previewTrack(60);});}break;
            case H_TRACK_DEL:if(song!=null&&host!=null&&song.tracks.size()>1){Song.Track t=song.tracks.get(track);host.confirm("Delete "+t.name+"?","Removes the track and its "+t.notes.size()+" notes.","Delete",()->{song.tracks.remove(t);track=0;instOpen=false;songChanged();invalidate();});}break;
            case H_SONG_RENAME:if(song!=null&&host!=null){Song s=song;host.prompt("Rename song",null,s.name,false,t->{if(!t.trim().isEmpty())s.name=t.trim();});}break;
            case H_SONG_DUP:if(song!=null){Song s=song.copy();s.name=song.name+" copy";s.id=project.uniqueId("mus_"+s.name);project.songs.add(project.songs.indexOf(song)+1,s);selectSong(s);hint("Duplicated");}break;
            case H_SONG_EXPORT:if(song!=null&&host!=null)host.exportWav(AudioIO.renderSong(song,project),song.name);break;
            case H_SONG_DEL:if(song!=null&&host!=null){Song s=song;host.confirm("Delete \""+s.name+"\"?","Play Music actions that use it will do nothing"+(s.id.equals(project.levelMusic)?" and the level will have no music":"")+".","Delete",()->{
                if(s.id.equals(playingSong)){sfx.stopMusic(0);playingSong=null;}project.songs.remove(s);if(s.id.equals(project.levelMusic))project.levelMusic="";
                selectSong(project.songs.isEmpty()?null:project.songs.get(0));invalidate();});}break;
            case H_KEYS:if(song!=null){Song.Track t=song.tracks.get(track);int row=(int)((y-gridRect.top+rollY)/rowH(t));if(row>=0&&row<rows(t))previewTrack(pitchOfRow(t,row));}break;
            case H_ROLL:if(song!=null)tapRoll(x,y);break;
        }
    }
    private void tapRoll(float x,float y){
        Song.Track t=song.tracks.get(track);
        int step=(int)((x-gridRect.left+rollX)/stepW()),row=(int)((y-gridRect.top+rollY)/rowH(t));
        if(step<0||step>=song.steps()||row<0||row>=rows(t))return;
        int pitch=pitchOfRow(t,row);
        Song.Note n=t.noteAt(step,pitch);
        if(n!=null)t.notes.remove(n);
        else{int len=t.drums()?1:Math.min(brush,song.steps()-step);
            for(int i=t.notes.size()-1;i>=0;i--){Song.Note o=t.notes.get(i);if(o.pitch==pitch&&o.step>step&&o.step<step+len)len=o.step-step;}
            t.add(step,pitch,Math.max(1,len));previewTrack(pitch);}
        songChanged();
    }

    // ------------------------------------------------------------------ Game Audio actions
    private void tapGame(int hid){
        if(hid>=H_GAME_PLAY&&hid<H_GAME_PLAY+SoundFx.COUNT){int b=hid-H_GAME_PLAY;sfx.useProject(project);sfx.stopSounds();sfx.play(b,1);return;}
        if(hid>=H_GAME&&hid<H_GAME+SoundFx.COUNT){assignOverride(hid-H_GAME);return;}
        if(hid==H_LEVEL_MUSIC&&host!=null){
            String[] items=new String[project.songs.size()+2];items[0]="No music";int ch=0;   // + library entry at the end
            for(int i=0;i<project.songs.size();i++){items[i+1]=project.songs.get(i).name;if(project.songs.get(i).id.equals(project.levelMusic))ch=i+1;}
            items[items.length-1]="+ Pick from the fantasy music library…";
            host.choose("Level music",items,ch,k->{
                if(k==0)project.levelMusic="";
                else if(k==items.length-1){tab=TAB_MUSIC;libOpen=true;libForLevel=true;mainScroll=0;hint("The track you add becomes the level music");}
                else project.levelMusic=project.songs.get(k-1).id;
                invalidate();});
        }
    }
}
