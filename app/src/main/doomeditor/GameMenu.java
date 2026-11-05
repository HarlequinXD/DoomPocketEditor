package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.ArrayList;

/**
 * The game's front end in Play mode: a "Powered by RPG Pocket Editor" splash, then a main menu (game title
 * top-left over a slowly panning view of the level) with New Game / Options / Exit, and an Options screen
 * with game and audio settings. The same menu doubles as the pause menu, with Continue on top.
 */
final class GameMenu {
    interface Listener {void newGame();void resume();void exitToEditor();void restart();boolean saveGame(String slot);boolean loadGame(String slot);}
    static final int OFF=0,SPLASH=1,MAIN=2,OPTIONS=3,SLOTS=4;
    private static final float SPLASH_SECONDS=2.8f;
    private static final int GOLD=0xffe8c070,GOLD_DARK=0xff8a6a2a;
    private static final int H_CONTINUE=1,H_NEW=2,H_OPTIONS=3,H_EXIT=4,H_BACK=5,H_TAB=10,H_DIFF=20,H_ROW=40,H_SPLASH=99,H_SAVE=6,H_LOAD=7,H_CONTINUE_SAVE=8,H_SLOT=60;
    // save slots
    private boolean slotSave;private String confirmSlot;private GameSave.Info[] slotInfo=new GameSave.Info[GameSave.SLOTS.length];private GameSave.Info latest;
    private String note;private long noteUntil;
    private void note(String s){note=s;noteUntil=System.currentTimeMillis()+2200;}
    void refreshSaves(){for(int i=0;i<GameSave.SLOTS.length;i++)slotInfo[i]=GameSave.info(ctx,project,GameSave.SLOTS[i]);latest=null;for(GameSave.Info i:slotInfo)if(i.exists&&(latest==null||i.time>latest.time))latest=i;}

    int state=OFF;
    boolean paused;
    /** Player mode (a shared game opened with Play): the exit button quits to the title screen. */
    boolean playerMode;   // opened from inside a running game: Continue is offered, the frozen game is the backdrop
    private final Ui ui;private final Paint p;private final Context ctx;
    private final Project project;private final Renderer renderer;private final SoundFx sfx;private final Listener listener;
    private float time,splashTime;private int optTab;
    private final RectF r=new RectF();
    private final ArrayList<Renderer.SpriteDraw> sprites=new ArrayList<>(),pool=new ArrayList<>();
    private LinearGradient shade;private float shadeW;private int shadeKey=-1;
    // option rows registered while drawing (sliders need their track for dragging)
    private static final int ROW_LOOK=0,ROW_INVERT=1,ROW_TEXT=2,ROW_SHAKE=3,ROW_MUSIC=4,ROW_SOUND=5;
    private final RectF[] track=new RectF[8];
    private int dragRow=-1;

    GameMenu(Context ctx,Ui ui,Project project,Renderer renderer,SoundFx sfx,Listener listener){
        this.ctx=ctx;this.ui=ui;p=ui.p;this.project=project;this.renderer=renderer;this.sfx=sfx;this.listener=listener;
        for(int i=0;i<track.length;i++)track[i]=new RectF();
        GameSettings.load(ctx);
    }
    private float dp(float v){return ui.dp(v);}
    /** Entering Play: splash first, then the main menu with the level music. */
    void begin(){GameSettings.load(ctx);MenuStyle m=project.menu;state=m.splash||hasStudio()?SPLASH:MAIN;splashTime=0;paused=false;refreshSaves();startMenuMusic();}
    /** UI Editor preview: the title menu without music or a splash. */
    void preview(){state=MAIN;paused=false;refreshSaves();}
    /** UI Editor preview of the splash, restarting it. */
    void previewSplash(){state=SPLASH;splashTime=0;}
    private boolean hasStudio(){return !project.menu.studio.trim().isEmpty()||!project.menu.studioPic.isEmpty();}
    void openPause(){state=MAIN;paused=true;refreshSaves();}
    boolean active(){return state!=OFF;}
    private void startMenuMusic(){
        sfx.start();
        sfx.useProject(project);applyVolumes();
        project.ensureMaps();String mm=project.menu.music;if(mm.equals("none"))return;
        Song m=project.song(mm.isEmpty()?project.mapMusic(project.map(project.startMap)):mm);
        if(m!=null&&!sfx.musicPlaying())sfx.playMusic(m.compile(project),true,1.5f);
    }
    void applyVolumes(){sfx.musicVolume=project.musicVolume*GameSettings.musicVolume;sfx.sfxVolume=project.sfxVolume*GameSettings.soundVolume;}
    /** Android back: options → menu, splash → menu; returns false when the main menu should leave Play. */
    boolean back(){
        if(state==OPTIONS){state=MAIN;GameSettings.save(ctx);return true;}
        if(state==SLOTS){state=MAIN;return true;}
        if(state==SPLASH){state=MAIN;return true;}
        if(state==MAIN&&paused){state=OFF;listener.resume();return true;}
        return false;
    }

    // ================================================================== drawing
    /** Draws the current screen; returns true while it needs more frames. */
    boolean draw(Canvas c,float w,float h,float dt){
        time+=dt;ui.clearHits();
        if(state==SPLASH){drawSplash(c,w,h,dt);return true;}
        drawBackdrop(c,w,h);
        if(state==OPTIONS)drawOptions(c,w,h);else if(state==SLOTS)drawSlots(c,w,h);else drawMain(c,w,h);
        if(note!=null&&System.currentTimeMillis()<noteUntil){float tw=ui.measure(note,12.5f,1)+dp(36);ui.pill(c,w*.5f-tw*.5f,h-dp(56),w*.5f+tw*.5f,h-dp(22),0xf0262a31);ui.textMid(c,note,w*.5f,h-dp(39),12.5f,0xffe8ecf1,1,0);}
        return true;
    }
    private void drawSplash(Canvas c,float w,float h,float dt){
        splashTime+=dt;MenuStyle m=project.menu;float sec=m.splashSeconds;
        boolean studio=hasStudio();float total=(studio?sec:0)+(m.splash?sec:0);
        c.drawColor(0xff000000);
        float t=splashTime;boolean first=studio&&t<sec;float local=first?t:t-(studio?sec:0);
        float a=Math.max(0,Math.min(1,Math.min(local/.6f,(sec-local)/.6f)));int al=(int)(a*255);
        float s=Math.min(w,h)*.34f,cx=w*.5f,cy=h*.46f;
        if(first){   // the game maker's own studio card
            android.graphics.Bitmap b=m.studioPicture();float ty=cy;
            if(b!=null){float bw=s*1.6f,bh=bw*b.getHeight()/Math.max(1,b.getWidth());if(bh>s){bh=s;bw=bh*b.getWidth()/Math.max(1,b.getHeight());}
                r.set(cx-bw*.5f,cy-bh*.5f,cx+bw*.5f,cy+bh*.5f);p.setAlpha(al);c.drawBitmap(b,null,r,p);p.setAlpha(255);ty=cy+bh*.5f+dp(34);}
            if(!m.studio.trim().isEmpty())ui.text(c,m.studio.trim(),cx,ty,24,(al<<24)|0xf0f0f0,2,0);
            ui.text(c,"presents",cx,ty+dp(26),12,(al<<24)|0xa6b0bd,1,0);
        }else{
            ui.logo(c,cx-s*.5f,cy-s*.5f,s);
            ui.text(c,"Powered by",cx,cy+s*.5f+dp(30),12,(al<<24)|0xa6b0bd,1,0);
            ui.text(c,"RPG Pocket Editor",cx,cy+s*.5f+dp(56),20,(al<<24)|(GOLD&0xffffff),2,0);
            // fade the logo with a black veil (the logo bitmap itself is drawn opaque)
            if(a<1)ui.rect(c,cx-s*.5f-2,cy-s*.5f-2,cx+s*.5f+2,cy+s*.5f+2,((int)((1-a)*255))<<24);
        }
        ui.hit(0,0,w,h,H_SPLASH);
        if(splashTime>=total)state=MAIN;
    }
    private void drawBackdrop(Canvas c,float w,float h){
        RectF full=new RectF(0,0,w,h);
        project.ensureMaps();project.syncCurrent();Project.GameMap sm=project.map(project.startMap);Level level=sm!=null?sm.level:project.level;   // the start map
        MenuStyle ms=project.menu;
        if(paused){renderer.present(c,full);}
        else if(ms.background==MenuStyle.BG_PICTURE&&ms.picture()!=null){android.graphics.Bitmap b=ms.picture();
            float sc=Math.max(w/b.getWidth(),h/b.getHeight()),bw=b.getWidth()*sc,bh=b.getHeight()*sc,drift=ms.pan>0?(float)Math.sin(time*.05f*ms.pan)*Math.max(0,bw-w)*.5f:0;
            r.set((w-bw)*.5f+drift,(h-bh)*.5f,(w+bw)*.5f+drift,(h+bh)*.5f);p.setFilterBitmap(true);c.drawBitmap(b,null,r,p);}
        else if(ms.background==MenuStyle.BG_GRADIENT){int[] g=MenuStyle.GRADIENT_COLS[ms.gradient];
            p.setShader(new LinearGradient(0,0,w*.3f,h,g[0],g[1],Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}
        else if(ms.background==MenuStyle.BG_THEME){th().backdrop(c,p,w,h,ui.dp);}
        else{
            int si=level.sectorAt(level.playerX,level.playerY);
            if(si<0)c.drawColor(0xff120c1a);
            else{
                Level.Sector sec=level.sectors.get(si);
                float ang=level.facing+(ms.background==MenuStyle.BG_STILL?0:(float)Math.sin(time*.12f*ms.pan)*.9f);
                sprites.clear();int k=0;
                for(Level.Thing th:level.things){EntityDef d=project.def(th.def);if(d==null||d.kind==EntityDef.PLAYER)continue;
                    while(pool.size()<=k)pool.add(new Renderer.SpriteDraw());
                    int s=level.sectorAt(th.x,th.y);float z=s<0?0:level.sectors.get(s).floor;
                    sprites.add(pool.get(k++).set(th.x,th.y,z,project.spriteFor(d,d.sprite,Sprites.F_IDLE,time,0,World.angleDiff((float)Math.atan2(level.playerY-th.y,level.playerX-th.x),th.angle)),-1,false));}
                renderer.highlight=-1;renderer.fx=null;Atmosphere.prepareStatic(renderer,project,sm,level,time);
                renderer.render(c,level,level.playerX,level.playerY,Math.min(sec.ceiling-.1f,sec.floor+World.EYE),ang,0,full,sprites);
            }
        }
        // darken towards the left so the title and buttons read clearly
        int strong=((int)(Math.min(1,ms.dim*1.4f)*255)<<24)|0x080510,weak=((int)(ms.dim*.25f*255)<<24)|0x080510;
        int lay=paused?0:ms.layout;
        if(lay==1){p.setColor(((int)(ms.dim*.8f*255)<<24)|0x080510);c.drawRect(0,0,w,h,p);}
        else{int key=lay*100000+(int)(ms.dim*1000);
            if(shade==null||shadeW!=w||shadeKey!=key){shadeW=w;shadeKey=key;
                shade=lay==3?new LinearGradient(w,0,w*.25f,0,strong,weak,Shader.TileMode.CLAMP):lay==2?new LinearGradient(0,h,0,h*.2f,strong,weak,Shader.TileMode.CLAMP):new LinearGradient(0,0,w*.75f,0,strong,weak,Shader.TileMode.CLAMP);}
            p.setShader(shade);p.setColor(0xffffffff);c.drawRect(0,0,w,h,p);p.setShader(null);}
    }
    private void drawMain(Canvas c,float w,float h){
        MenuStyle ms=project.menu;int lay=paused?0:ms.layout;
        String title=ms.titleFor(project);
        float bw=Math.min(dp(280),w*.4f)*ms.buttonScale,size=ms.titleSize>0?ms.titleSize:Math.min(34,Math.max(20,(w*.55f)/Math.max(1,title.length())/ui.dp(1)*1.7f));
        java.util.ArrayList<Object[]> b=new java.util.ArrayList<>();
        if(paused){b.add(new Object[]{Icons.PLAY,"Continue",H_CONTINUE});b.add(new Object[]{Icons.SAVE,"Save game",H_SAVE});
            if(latest!=null)b.add(new Object[]{Icons.FOLDER,ms.lLoad,H_LOAD});b.add(new Object[]{Icons.FLAG,"Restart level",H_NEW});}
        else{if(latest!=null)b.add(new Object[]{Icons.PLAY,ms.lContinue,H_CONTINUE_SAVE});b.add(new Object[]{Icons.FLAG,ms.lNew,H_NEW});
            if(latest!=null&&ms.showLoad)b.add(new Object[]{Icons.FOLDER,ms.lLoad,H_LOAD});}
        if(ms.showOptions||paused)b.add(new Object[]{Icons.MORE,ms.lOptions,H_OPTIONS});b.add(new Object[]{Icons.ARROW_LEFT,ms.quitLabel(playerMode),H_EXIT});
        float gap=dp(8)*ms.buttonScale,bh=Math.max(dp(34),Math.min(dp(50)*ms.buttonScale,(h*.6f)/b.size()-gap)),listH=b.size()*(bh+gap)-gap;
        boolean sub=!ms.subtitle.trim().isEmpty()&&!paused;float titleBlock=dp(14)+(sub?dp(22):0);
        // where things go
        float L,y,by;int align;
        switch(lay){
            case 1:L=w*.5f-bw*.5f;align=0;y=Math.max(dp(48),(h-listH-titleBlock-dp(40))*.42f+size*ui.dp(1)*.4f);by=y+titleBlock+dp(28);break;
            case 2:L=dp(36);align=-1;by=h-dp(52)-listH;y=Math.max(dp(48),by-titleBlock-dp(18));break;
            case 3:L=w-dp(36)-bw;align=1;y=dp(56);by=y+titleBlock+dp(22);break;
            default:L=dp(36);align=-1;y=dp(56);by=y+titleBlock+dp(22);
        }
        float tx=align==0?w*.5f:align==1?w-dp(36):L;int tc=ms.titleColor(th());
        String shown=ui.ellipsize(title,size,2,w*(lay==1?.9f:.6f));
        switch(ms.titleStyle){
            case 0:ui.text(c,shown,tx+dp(2),y+dp(2),size,0xc0000000,2,align);break;
            case 1:{float o=dp(1.4f);for(int k=0;k<8;k++){double an=k*Math.PI/4;ui.text(c,shown,tx+(float)Math.cos(an)*o,y+(float)Math.sin(an)*o,size,0xe0000000,2,align);}break;}
            case 2:{p.setShadowLayer(dp(10),0,0,UiTheme.alpha(tc,.9f));ui.text(c,shown,tx,y,size,tc,2,align);p.clearShadowLayer();break;}
            default:
        }
        ui.text(c,shown,tx,y,size,tc,2,align);
        float lw=Math.min(w*.4f,dp(220)),ll=align==0?w*.5f-lw*.5f:align==1?tx-lw:L;ui.rect(c,ll,y+dp(12),ll+lw,y+dp(14),UiTheme.alpha(tc,.5f));
        if(sub)ui.text(c,ui.ellipsize(ms.subtitle.trim(),13,1,w*.8f),tx,y+dp(34),13,0xd0e8ecf1,1,align);
        for(int i=0;i<b.size();i++){Object[] o=b.get(i);menuButton(c,L,by,bw,bh,(Integer)o[0],(String)o[1],(Integer)o[2],i==0,ms.buttonStyle,align);by+=bh+gap;}
        if(!paused&&latest!=null&&lay!=1){String lab=ui.ellipsize(latestLabel(latest),11,1,w-bw-dp(80));float lx=lay==3?L-dp(14):L+bw+dp(14);
            ui.text(c,lab,lx,(lay==2?h-dp(52)-listH:by-(b.size())*(bh+gap))+bh*.5f+dp(4),11,0xc0e8ecf1,1,lay==3?1:-1);}
        // small credit, bottom-left
        if(ms.credit||paused){float ls=dp(22),cl=lay==2?w-dp(36)-ls-ui.measure("Powered by RPG Pocket Editor",10.5f,1)-dp(8):dp(36);ui.logo(c,cl,h-dp(16)-ls,ls);
            ui.textMid(c,"Powered by RPG Pocket Editor",cl+ls+dp(8),h-dp(16)-ls*.5f,10.5f,0xb0e8ecf1,1,-1);}
    }
    private static String latestLabel(GameSave.Info i){return i.className+" · level "+i.level+(i.mapName.isEmpty()?"":" · "+i.mapName);}
    private void drawSlots(Canvas c,float w,float h){
        float pw=Math.min(w-dp(40),dp(560)),ph=Math.min(h-dp(32),dp(340)),L=(w-pw)*.5f,T=(h-ph)*.5f,R=L+pw,B=T+ph;
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(16),0x90000000);th().panel(c,p,L,T,R,B,ui.dp,.97f,false);
        ui.text(c,slotSave?"Save game":"Load game",L+dp(22),T+dp(36),20,acc(),2,-1);
        r.set(R-dp(110),T+dp(14),R-dp(16),T+dp(48));th().rectButton(c,p,ui,r,Icons.ARROW_LEFT,"Back",UiTheme.BTN_NORMAL,ui.dp);ui.hit(r,H_BACK);
        float y=T+dp(60),rowH=Math.min(dp(60),(B-y-dp(14))/(slotSave?3:4)-dp(6));
        for(int i=0;i<GameSave.SLOTS.length;i++){String slot=GameSave.SLOTS[i];if(slotSave&&slot.equals("auto"))continue;GameSave.Info in=slotInfo[i];
            r.set(L+dp(18),y,R-dp(18),y+rowH);boolean confirm=slot.equals(confirmSlot);th().card(c,p,ui,r,confirm,ui.dp);
            ui.icon(c,slot.equals("auto")?Icons.RESET:Icons.SAVE,r.left+dp(22),r.centerY(),16,acc());
            ui.text(c,GameSave.slotName(slot),r.left+dp(44),r.centerY()-dp(4),14,th().text,2,-1);
            String sub=confirm?"Tap again to overwrite this save":in!=null&&in.exists?latestLabel(in)+"  ·  played "+GameSave.formatTime(in.playTime)+"  ·  "+android.text.format.DateFormat.format("d MMM HH:mm",in.time):slotSave?"Empty":"Empty – nothing to load";
            ui.text(c,ui.ellipsize(sub,10.5f,1,r.width()-dp(56)),r.left+dp(44),r.centerY()+dp(13),10.5f,confirm?acc():th().textDim(),1,-1);
            ui.hit(r,H_SLOT+i);y+=rowH+dp(6);}
    }
    private void slotTap(int i){
        String slot=GameSave.SLOTS[i];GameSave.Info in=slotInfo[i];
        if(slotSave){
            if(in!=null&&in.exists&&!slot.equals(confirmSlot)){confirmSlot=slot;return;}
            confirmSlot=null;boolean ok=listener.saveGame(slot);refreshSaves();note(ok?"Saved to "+GameSave.slotName(slot):"Couldn't save the game");if(ok)state=MAIN;
        }else{
            if(in==null||!in.exists)return;
            if(listener.loadGame(slot)){state=OFF;paused=false;}else note("That save couldn't be loaded");
        }
    }
    private UiTheme th(){return project.theme;}
    /** Accent that reads on the panel (dark accents are lifted on dark panels and vice versa). */
    private int acc(){UiTheme t=th();return t.lightPanel()?(UiTheme.luma(t.accent)>.55f?UiTheme.shade(t.accent,.5f):t.accent):(UiTheme.luma(t.accent)<.35f?0xffe8c070:t.accent);}
    private void menuButton(Canvas c,float l,float t,float bw,float bh,int icon,String label,int id,boolean primary){menuButton(c,l,t,bw,bh,icon,label,id,primary,0,-1);}
    private void menuButton(Canvas c,float l,float t,float bw,float bh,int icon,String label,int id,boolean primary,int style,int align){
        r.set(l,t,l+bw,t+bh);
        if(style==1){th().rectButton(c,p,ui,r,icon,label,primary?UiTheme.BTN_PRIMARY:UiTheme.BTN_NORMAL,ui.dp);ui.hit(r,id);return;}
        if(style==2){int fg=primary?acc():th().text;float fs=Math.min(20,bh/ui.dp(1)*.42f);
            float x=align==0?r.centerX():align==1?r.right:r.left;ui.text(c,label,x+dp(1.5f),r.centerY()+fs*ui.dp(1)*.36f+dp(1.5f),fs,0xb0000000,2,align);
            ui.text(c,label,x,r.centerY()+fs*ui.dp(1)*.36f,fs,fg,2,align);ui.hit(r,id);return;}
        ui.round(c,r.left,r.top+dp(3),r.right,r.bottom+dp(3),dp(10),0x70000000);
        th().panel(c,p,r.left,r.top,r.right,r.bottom,ui.dp,primary?1f:.88f,false);
        if(primary){p.setStyle(Paint.Style.FILL);p.setColor(UiTheme.alpha(th().accent,.16f));c.drawRect(r.left+dp(3),r.top+dp(3),r.right-dp(3),r.bottom-dp(3),p);}
        int fg=primary?acc():th().text;
        ui.icon(c,icon,r.left+dp(26),r.centerY(),18,fg);
        ui.textMid(c,label,r.left+dp(50),r.centerY(),16,fg,2,-1);
        ui.hit(r,id);
    }
    private void drawOptions(Canvas c,float w,float h){
        float pw=Math.min(w-dp(40),dp(560)),ph=Math.min(h-dp(32),dp(360)),L=(w-pw)*.5f,T=(h-ph)*.5f,R=L+pw,B=T+ph;
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(16),0x90000000);th().panel(c,p,L,T,R,B,ui.dp,.97f,false);
        ui.text(c,"Options",L+dp(22),T+dp(36),20,acc(),2,-1);
        r.set(R-dp(110),T+dp(14),R-dp(16),T+dp(48));th().rectButton(c,p,ui,r,Icons.ARROW_LEFT,"Back",UiTheme.BTN_NORMAL,ui.dp);ui.hit(r,H_BACK);
        String[] tabs={"Game","Audio"};float tx=L+dp(22);
        for(int i=0;i<2;i++){r.set(tx,T+dp(58),tx+dp(110),T+dp(90));th().rectButton(c,p,ui,r,i==0?Icons.PERSON:Icons.SPEAKER,tabs[i],optTab==i?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,ui.dp);ui.hit(r,H_TAB+i);tx+=dp(118);}
        float y=T+dp(104),l=L+dp(22),rr=R-dp(22);
        if(optTab==0){
            ui.textMid(c,"Difficulty",l,y+dp(17),13,th().text,1,-1);
            float sw=(rr-l-dp(140))/3f;
            for(int i=0;i<3;i++){r.set(l+dp(140)+i*sw,y,l+dp(140)+(i+1)*sw-dp(6),y+dp(34));th().rectButton(c,p,ui,r,-1,GameSettings.DIFFICULTIES[i],GameSettings.difficulty==i?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,ui.dp);ui.hit(r,H_DIFF+i);}
            y+=dp(44);
            y=slider(c,l,rr,y,ROW_LOOK,"Look sensitivity",GameSettings.lookSensitivity,.25f,3f,String.format(java.util.Locale.US,"%.2fx",GameSettings.lookSensitivity));
            y=toggle(c,l,rr,y,ROW_INVERT,"Invert look up/down",GameSettings.invertLook);
            y=toggle(c,l,rr,y,ROW_TEXT,"Damage numbers and combat text",GameSettings.combatText);
            toggle(c,l,rr,y,ROW_SHAKE,"Camera shake",GameSettings.cameraShake);
        }else{
            y=slider(c,l,rr,y,ROW_MUSIC,"Music volume",GameSettings.musicVolume,0,1,Math.round(GameSettings.musicVolume*100)+"%");
            y=slider(c,l,rr,y,ROW_SOUND,"Sound effects volume",GameSettings.soundVolume,0,1,Math.round(GameSettings.soundVolume*100)+"%");
            ui.text(c,project.levelMusic.isEmpty()?"This game has no level music yet.":"Music: "+project.songLabel(project.levelMusic),l,y+dp(16),11.5f,th().textFaint());
        }
    }
    private float slider(Canvas c,float l,float rr,float y,int row,String label,float v,float min,float max,String shown){
        ui.textMid(c,label,l,y+dp(14),13,th().text,1,-1);ui.textMid(c,shown,rr,y+dp(14),12.5f,acc(),1,1);
        float tl=l,tr=rr,ty=y+dp(36),n=(v-min)/(max-min);
        ui.round(c,tl,ty-dp(3),tr,ty+dp(3),dp(3),UiTheme.alpha(0xff000000,.35f));ui.round(c,tl,ty-dp(3),tl+(tr-tl)*n,ty+dp(3),dp(3),UiTheme.shade(acc(),.6f));
        p.setColor(acc());c.drawCircle(tl+(tr-tl)*n,ty,dp(9),p);
        track[row].set(tl,y,tr,y+dp(50));ui.hit(track[row],H_ROW+row);
        return y+dp(56);
    }
    private float toggle(Canvas c,float l,float rr,float y,int row,String label,boolean on){
        r.set(l,y,rr,y+dp(38));ui.textMid(c,label,l,y+dp(19),13,th().text,1,-1);
        th().toggle(c,p,ui,rr-dp(44),y+dp(11),on,ui.dp);
        track[row].setEmpty();ui.hit(r,H_ROW+row);
        return y+dp(42);
    }

    // ================================================================== touch
    void down(float x,float y){
        int hid=ui.hitAt(x,y);dragRow=-1;
        if(state==SPLASH){state=MAIN;return;}
        if(hid>=H_ROW&&hid<H_ROW+track.length&&!track[hid-H_ROW].isEmpty()){dragRow=hid-H_ROW;setSlider(dragRow,x);return;}
        tap(hid);
    }
    void move(float x,float y){if(dragRow>=0)setSlider(dragRow,x);}
    void up(){
        if(dragRow==ROW_SOUND){applyVolumes();sfx.play(SoundFx.PICKUP,1);}
        if(dragRow>=0)GameSettings.save(ctx);
        dragRow=-1;
    }
    private void setSlider(int row,float x){
        RectF t=track[row];float n=Math.max(0,Math.min(1,(x-t.left)/Math.max(1,t.width())));
        if(row==ROW_LOOK)GameSettings.lookSensitivity=Math.round((.25f+n*2.75f)*20)/20f;
        else if(row==ROW_MUSIC){GameSettings.musicVolume=Math.round(n*20)/20f;applyVolumes();}
        else if(row==ROW_SOUND){GameSettings.soundVolume=Math.round(n*20)/20f;applyVolumes();}
    }
    private void tap(int hid){
        switch(hid){
            case H_CONTINUE:state=OFF;listener.resume();return;
            case H_CONTINUE_SAVE:if(latest!=null){if(listener.loadGame(latest.slot)){state=OFF;paused=false;}else note("That save couldn't be loaded");}return;
            case H_SAVE:slotSave=true;confirmSlot=null;refreshSaves();state=SLOTS;return;
            case H_LOAD:slotSave=false;confirmSlot=null;refreshSaves();state=SLOTS;return;
            case H_NEW:state=OFF;boolean wasPaused=paused;paused=false;if(wasPaused)listener.restart();else listener.newGame();return;
            case H_OPTIONS:state=OPTIONS;return;
            case H_EXIT:state=OFF;GameSettings.save(ctx);listener.exitToEditor();return;
            case H_BACK:state=MAIN;confirmSlot=null;GameSettings.save(ctx);return;
            case H_ROW+ROW_INVERT:GameSettings.invertLook=!GameSettings.invertLook;GameSettings.save(ctx);return;
            case H_ROW+ROW_TEXT:GameSettings.combatText=!GameSettings.combatText;GameSettings.save(ctx);return;
            case H_ROW+ROW_SHAKE:GameSettings.cameraShake=!GameSettings.cameraShake;GameSettings.save(ctx);return;
        }
        if(hid>=H_SLOT&&hid<H_SLOT+GameSave.SLOTS.length){slotTap(hid-H_SLOT);return;}
        if(hid>=H_TAB&&hid<H_TAB+2){optTab=hid-H_TAB;return;}
        if(hid>=H_DIFF&&hid<H_DIFF+3){GameSettings.difficulty=hid-H_DIFF;GameSettings.save(ctx);}
    }
}
