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
    private LinearGradient shade;private float shadeW;
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
    void begin(){GameSettings.load(ctx);state=SPLASH;splashTime=0;paused=false;refreshSaves();startMenuMusic();}
    void openPause(){state=MAIN;paused=true;refreshSaves();}
    boolean active(){return state!=OFF;}
    private void startMenuMusic(){
        sfx.start();
        sfx.useProject(project);applyVolumes();
        project.ensureMaps();Song m=project.song(project.mapMusic(project.map(project.startMap)));
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
        splashTime+=dt;
        c.drawColor(0xff000000);
        float t=splashTime,a=Math.min(1,Math.min(t/.6f,(SPLASH_SECONDS-t)/.6f));a=Math.max(0,a);
        float s=Math.min(w,h)*.34f,cx=w*.5f,cy=h*.46f;
        ui.logo(c,cx-s*.5f,cy-s*.5f,s);
        int al=(int)(a*255);
        ui.text(c,"Powered by",cx,cy+s*.5f+dp(30),12,(al<<24)|0xa6b0bd,1,0);
        ui.text(c,"RPG Pocket Editor",cx,cy+s*.5f+dp(56),20,(al<<24)|(GOLD&0xffffff),2,0);
        // fade the logo with a black veil (the logo bitmap itself is drawn opaque)
        if(a<1)ui.rect(c,cx-s*.5f-2,cy-s*.5f-2,cx+s*.5f+2,cy+s*.5f+2,((int)((1-a)*255))<<24);
        ui.hit(0,0,w,h,H_SPLASH);
        if(splashTime>=SPLASH_SECONDS)state=MAIN;
    }
    private void drawBackdrop(Canvas c,float w,float h){
        RectF full=new RectF(0,0,w,h);
        project.ensureMaps();project.syncCurrent();Project.GameMap sm=project.map(project.startMap);Level level=sm!=null?sm.level:project.level;   // the start map
        if(paused){renderer.present(c,full);}
        else{
            int si=level.sectorAt(level.playerX,level.playerY);
            if(si<0)c.drawColor(0xff120c1a);
            else{
                Level.Sector sec=level.sectors.get(si);
                float ang=level.facing+(float)Math.sin(time*.12f)*.9f;
                sprites.clear();int k=0;
                for(Level.Thing th:level.things){EntityDef d=project.def(th.def);if(d==null||d.kind==EntityDef.PLAYER)continue;
                    while(pool.size()<=k)pool.add(new Renderer.SpriteDraw());
                    int s=level.sectorAt(th.x,th.y);float z=s<0?0:level.sectors.get(s).floor;
                    sprites.add(pool.get(k++).set(th.x,th.y,z,project.spriteFor(d,d.sprite,Sprites.F_IDLE,time,0,World.angleDiff((float)Math.atan2(level.playerY-th.y,level.playerX-th.x),th.angle)),-1,false));}
                renderer.highlight=-1;renderer.fx=null;
                renderer.render(c,level,level.playerX,level.playerY,Math.min(sec.ceiling-.1f,sec.floor+World.EYE),ang,0,full,sprites);
            }
        }
        // darken towards the left so the title and buttons read clearly
        if(shade==null||shadeW!=w){shadeW=w;shade=new LinearGradient(0,0,w*.75f,0,0xe0080510,0x20080510,Shader.TileMode.CLAMP);}
        p.setShader(shade);p.setColor(0xffffffff);c.drawRect(0,0,w,h,p);p.setShader(null);
    }
    private void drawMain(Canvas c,float w,float h){
        float L=dp(36),y=dp(56);
        String title=project.name==null||project.name.isEmpty()?"Untitled Adventure":project.name;
        float size=Math.min(34,Math.max(20,(w*.55f)/Math.max(1,title.length())/ui.dp(1)*1.7f));
        ui.text(c,ui.ellipsize(title,size,2,w*.6f),L+dp(2),y+dp(2),size,0xc0000000,2,-1);
        int tc=UiTheme.luma(th().accent)<.35f?0xffe8c070:th().accent;ui.text(c,ui.ellipsize(title,size,2,w*.6f),L,y,size,tc,2,-1);
        ui.rect(c,L,y+dp(12),L+Math.min(w*.4f,dp(220)),y+dp(14),UiTheme.alpha(tc,.5f));
        // buttons: pause menu or title menu; Continue/Load appear once there is a save
        java.util.ArrayList<Object[]> b=new java.util.ArrayList<>();
        if(paused){b.add(new Object[]{Icons.PLAY,"Continue",H_CONTINUE});b.add(new Object[]{Icons.SAVE,"Save game",H_SAVE});
            if(latest!=null)b.add(new Object[]{Icons.FOLDER,"Load game",H_LOAD});b.add(new Object[]{Icons.FLAG,"Restart level",H_NEW});}
        else{if(latest!=null)b.add(new Object[]{Icons.PLAY,"Continue",H_CONTINUE_SAVE});b.add(new Object[]{Icons.FLAG,"New Game",H_NEW});
            if(latest!=null)b.add(new Object[]{Icons.FOLDER,"Load game",H_LOAD});}
        b.add(new Object[]{Icons.MORE,"Options",H_OPTIONS});b.add(new Object[]{Icons.ARROW_LEFT,playerMode?"Quit":"Exit to editor",H_EXIT});
        float by=y+dp(36),bw=Math.min(dp(280),w*.4f),gap=dp(8),bh=Math.max(dp(34),Math.min(dp(50),(h-by-dp(48))/b.size()-gap));
        for(int i=0;i<b.size();i++){Object[] o=b.get(i);menuButton(c,L,by,bw,bh,(Integer)o[0],(String)o[1],(Integer)o[2],i==0);by+=bh+gap;}
        if(!paused&&latest!=null){ui.text(c,ui.ellipsize(latestLabel(latest),11,1,w-L-bw-dp(40)),L+bw+dp(14),y+dp(36)+bh*.5f+dp(4),11,0xc0e8ecf1,1,-1);}
        // small credit, bottom-left
        float ls=dp(22);ui.logo(c,L,h-dp(16)-ls,ls);
        ui.textMid(c,"Powered by RPG Pocket Editor",L+ls+dp(8),h-dp(16)-ls*.5f,10.5f,0xb0e8ecf1,1,-1);
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
    private void menuButton(Canvas c,float l,float t,float bw,float bh,int icon,String label,int id,boolean primary){
        r.set(l,t,l+bw,t+bh);
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
