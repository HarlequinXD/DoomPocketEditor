package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/**
 * Main editor surface: Sectors (cursor-driven geometry tools), Entities (entity manager + placement),
 * 3D View (grounded walk/fly camera with surface editing) and Play (the Doom game framework).
 */
public class EditorView extends View {
    private static final int THINGS=2, PLAY=3, POLY=4, POLY3D=5, HOME=6;
    private static final int[] MODES={POLY,THINGS,POLY3D,PLAY};
    private static final String[] MODE_NAMES={"Sectors","Entities","3D View","Play"};
    private static final int[] MODE_ICONS={Icons.SECTORS,Icons.ENTITIES,Icons.CUBE,Icons.PLAY};
    // Every editor in the app, in header / launcher order. Index 4 and 5 open their own screens.
    static final String[] EDITOR_NAMES={"Sectors","Entities","3D View","Play","FSM","Sprites","Sound"};
    static final int[] EDITOR_ICONS={Icons.SECTORS,Icons.ENTITIES,Icons.CUBE,Icons.PLAY,Icons.GRAPH,Icons.PIXEL,Icons.MUSIC};
    static final int[] EDITOR_COLORS={0xff3d8bfd,0xfff0a030,0xff9b6dff,0xff3ecf8e,0xff2fb8c8,0xffef6b8a,0xffe0679a};
    static final String[] EDITOR_DESC={"Rooms, doors & stairs","Monsters, items & types","Walk & paint surfaces","Test with the real engine","Behaviour & actions","Pixel art & animation","Sound effects & music"};
    private static final int[] EDITOR_MODES={POLY,THINGS,POLY3D,PLAY,-1,-1,-1};
    private static final int H_ED=3000,H_SWITCH=3010,H_MORE=3011,H_LAUNCH=3020,H_LAUNCH_HOME=3027,H_LAUNCH_CLOSE=3028,H_TILE=3040,H_HOMEPLAY=3050,H_HOME3D=3051,H_HOMEFIX=3052;
    private boolean launcherOpen;
    // Touch target ids registered while drawing.
    private static final int H_MODE=100,H_FSM=110,H_SAVE=111,H_RESET=112,H_PAGE=120,H_TOOL=200,H_APPLY=520,H_MAT=4000,
            H_RAISE=500,H_LOWER=501,H_LIGHTUP=502,H_LIGHTDN=503,H_FLY=504,H_UP=505,H_DOWN=506,H_EDITFSM3D=507,H_START3D=508,
            H_FIRE=510,H_USE=511,H_JUMP=512,H_WEAPON=513,H_EXITPLAY=514,H_SPELL=515,H_POTION=516,H_BAG=517,H_DLG=3200,H_INV_ITEM=3300,H_INV_SPELL=3600,H_INV_CLOSE=3700,H_INV_TAB=3710,H_RETRY=530,H_BACK=531,
            H_NEWDEF=590,H_LOGO=593,H_SPRITES=594,H_EDITSPRITE=813,H_ZOOMIN=591,H_ZOOMOUT=592,H_DEF=2000,H_PROP=700,H_ETOOL=800,H_EDITFSM=810,H_DUPDEF=811,H_DELDEF=812;
    // Sector tools (cursor + Apply) and immediate actions.
    private static final int DRAW=1,RECT=2,PREFAB=3,STAIRS=4,SELECT=5,MOVE=6,SPLIT=7,EXTRUDE=8,DUPLICATE=9,DELETE=10,SPECIAL=11,START=12;
    private static final int CLOSE=101,UNDO_PT=102,CANCEL=103,UNDO=104,REDO=105,VALIDATE=106,CLEAN=107,DESELECT=108,
            FLOOR_UP=110,FLOOR_DN=111,CEIL_UP=112,CEIL_DN=113,LIGHT_UP=114,LIGHT_DN=115,FLOOR_TEX=116,CEIL_TEX=117,WALL_TEX=118,SKY=119,GO3D=120;
    private static final String[] PAGE_NAMES={"Draw","Edit","Surface"};
    private static final int[][] PAGES={
        {DRAW,RECT,PREFAB,STAIRS,CLOSE,UNDO_PT,CANCEL,UNDO,REDO,VALIDATE,CLEAN},
        {SELECT,MOVE,SPLIT,EXTRUDE,DUPLICATE,DELETE,SPECIAL,START,UNDO,REDO,DESELECT},
        {FLOOR_UP,FLOOR_DN,CEIL_UP,CEIL_DN,LIGHT_UP,LIGHT_DN,FLOOR_TEX,CEIL_TEX,WALL_TEX,SKY,GO3D}};
    private static final String[] ENTITY_TOOLS={"Place","Select","Move","Rotate","Delete"};
    private static final int[] ENTITY_TOOL_ICONS={Icons.PLUS_SQUARE,Icons.POINTER,Icons.MOVE,Icons.RESET,Icons.TRASH};

    private static String toolName(int t){switch(t){case DRAW:return "Draw";case RECT:return "Rectangle";case PREFAB:return "Prefab room";case STAIRS:return "Stairs";
        case SELECT:return "Select";case MOVE:return "Move";case SPLIT:return "Split edge";case EXTRUDE:return "Extrude";case DUPLICATE:return "Duplicate";case DELETE:return "Delete";
        case SPECIAL:return "Special";case START:return "Player start";case CLOSE:return "Close";case UNDO_PT:return "Undo pt";case CANCEL:return "Cancel";case UNDO:return "Undo";
        case REDO:return "Redo";case VALIDATE:return "Validate";case CLEAN:return "Clean up";case DESELECT:return "Deselect";case FLOOR_UP:return "Floor +";case FLOOR_DN:return "Floor −";
        case CEIL_UP:return "Ceiling +";case CEIL_DN:return "Ceiling −";case LIGHT_UP:return "Light +";case LIGHT_DN:return "Light −";case FLOOR_TEX:return "Floor tex";
        case CEIL_TEX:return "Ceiling tex";case WALL_TEX:return "Wall tex";case SKY:return "Sky";default:return "Edit in 3D";}}
    private static int toolIcon(int t){switch(t){case DRAW:return Icons.PEN;case RECT:return Icons.FRAME;case PREFAB:return Icons.PACKAGE;case STAIRS:return Icons.STAIRS;
        case SELECT:return Icons.POINTER;case MOVE:return Icons.MOVE;case SPLIT:return Icons.SCISSORS;case EXTRUDE:return Icons.EXTRUDE;case DUPLICATE:return Icons.COPY;case DELETE:return Icons.TRASH;
        case SPECIAL:return Icons.DOOR;case START:return Icons.FLAG;case CLOSE:return Icons.CLOSE_SHAPE;case UNDO_PT:return Icons.BACKSPACE;case CANCEL:return Icons.CANCEL;case UNDO:return Icons.UNDO;
        case REDO:return Icons.REDO;case VALIDATE:return Icons.VALIDATE;case CLEAN:return Icons.SPARKLE;case DESELECT:return Icons.CANCEL;case FLOOR_UP:return Icons.FLOOR_UP;case FLOOR_DN:return Icons.FLOOR_DOWN;
        case CEIL_UP:return Icons.CEIL_UP;case CEIL_DN:return Icons.CEIL_DOWN;case LIGHT_UP:return Icons.SUN;case LIGHT_DN:return Icons.SUN_DIM;case FLOOR_TEX:return Icons.FLOOR_TEX;
        case CEIL_TEX:return Icons.CEIL_TEX;case WALL_TEX:return Icons.WALL_TEX;case SKY:return Icons.CLOUD;default:return Icons.CUBE;}}

    private final Ui ui;
    private final Paint p,pixelPaint=new Paint();
    private final Path path=new Path();
    private final RectF r=new RectF(),view3d=new RectF();
    private final Project project;
    private Level level;
    private SectorEditor sectorEditor;
    private final Renderer renderer=new Renderer();
    private final SoundFx sfx=new SoundFx();
    UiHost host;
    Runnable onExitToTitle;
    private int mode=HOME,returnMode=POLY3D,toolPage=0,tool=DRAW,stage=0;
    private final ArrayList<float[]> pending=new ArrayList<>();
    // selection (Sectors)
    private int selKind=0,selSector=-1,selEdge=-1,selVertex=-1; // kind: 0 none,1 sector,2 edge,3 vertex
    private int pickSector=-1,pickEdge=-1;private float anchorX,anchorY;
    private int[] moveIds=new int[0];private float[] moveOrig=new float[0];
    // cursor & map view
    private float cursorX=6,cursorY=6,viewX=10,viewY=7,zoom=1f;
    private boolean followCursor=true;
    private int dpadPointer=-1,dpadDirection=-1;private long nextDpadRepeat;
    private float w,h,toolbarH,statusH,cell;
    private float downX,downY,lastX,lastY,pinchDist;private boolean dragged,panning;private long lastTapTime;private float lastTapX,lastTapY;
    // entities
    private String selDef="skeleton";private int selThing=-1,entityTool=0,entStage=0;private float defScroll,propScroll,matScroll;
    private int scrollTarget=0; // 1 def list, 2 property panel, 3 texture browser
    // 3D
    private ArrayList<Level.LineDef> mapLines;private long mapLinesSig;private Level mapLinesLevel;   // cached wall topology for the 2D map
    private float dockL;   // left edge of the 3D view's right-hand buttons
    private World walker;private boolean fly;private int sel3d=-1,selectedMaterial=0;private boolean flyUp,flyDown;
    private float joyOriginX,joyOriginY,joyX,joyY,moveX,moveY;private boolean joyVisible;private long joyReleaseTime;
    private int joyPointer=-1,lookPointer=-1,upPointer=-1,downPointer=-1;private float lookLastX,lookLastY,lookDownX,lookDownY;
    private long last3DTapTime;private float last3DTapX,last3DTapY;
    // play
    private World world;private final int[] buttonPointer={-1,-1,-1,-1,-1,-1,-1,-1};
    private long lastFrame=System.nanoTime(),messageUntil,resetArmedUntil;
    private String message="";
    private String drawError;
    private final ArrayList<Renderer.SpriteDraw> spriteList=new ArrayList<>();
    private final ArrayList<Renderer.SpriteDraw> spritePool=new ArrayList<>();
    private final Bitmap[] spriteThumbs=new Bitmap[Sprites.COUNT],texThumbs=new Bitmap[Textures.COUNT];

    EditorView(Context ctx){super(ctx);
        ui=new Ui(ctx);p=ui.p;pixelPaint.setFilterBitmap(false);
        project=Project.get(ctx);level=project.level;sectorEditor=new SectorEditor(level);
        if(project.loadWarning!=null){message=project.loadWarning;messageUntil=System.currentTimeMillis()+5000;}
        cursorX=Math.round(level.playerX);cursorY=Math.round(level.playerY);viewX=cursorX;viewY=cursorY;
        setFocusable(true);
    }
    boolean exitPlay(){if(mode!=PLAY)return false;changeMode(returnMode);return true;}
    void onPause(){sfx.stop();flushSave();setKeepScreenOn(false);}
    void onResume(){if(mode==PLAY){sfx.start();setKeepScreenOn(true);}lastInteraction=System.currentTimeMillis();renderer.invalidate();invalidate();}

    // ---------------------------------------------------------------- layout
    private float dp(float v){return ui.dp(v);}
    private boolean in2D(){return mode==POLY||mode==THINGS;}
    private float inspectorW(){return Math.max(dp(208),Math.min(dp(290),w*.26f));}
    private float entPanelW(){return Math.max(dp(164),Math.min(dp(230),w*.2f));}
    private float mapL(){return mode==THINGS?entPanelW():0;}
    private float mapR(){return w-inspectorW();}
    private float mapT(){return toolbarH;}
    private float mapB(){return h-statusH;}
    private float browserW(){return Math.max(dp(116),Math.min(dp(156),w*.17f));}
    private float snap(){return mode==THINGS?.5f:1f;}
    private float dpadR(){return dp(58);}
    private float dpadCX(){return mapL()+dp(20)+dpadR();}
    private float dpadCY(){return mapB()-dp(16)-dpadR();}
    private float applyR(){return dp(32);}
    private float applyCX(){return mapR()-dp(24)-applyR();}
    private float applyCY(){return dpadCY()+dp(8);}
    private float sx(float wx){return (mapL()+mapR())*.5f+(wx-viewX)*cell;}
    private float sy(float wy){return (mapT()+mapB())*.5f+(wy-viewY)*cell;}
    private float wx(float x){return viewX+(x-(mapL()+mapR())*.5f)/cell;}
    private float wy(float y){return viewY+(y-(mapT()+mapB())*.5f)/cell;}
    private boolean inMap(float x,float y){return x>=mapL()&&x<mapR()&&y>=mapT()&&y<mapB();}
    private boolean inStatusBar(float x,float y){return in2D()&&x>=mapL()&&x<mapR()&&y>=mapB();}

    // ---------------------------------------------------------------- frame
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        if(drawError!=null){c.drawColor(Ui.BG);ui.text(c,"Something went wrong drawing the editor. Tap to reset the view.",dp(20),dp(40),15,Ui.AMBER,1,-1);ui.text(c,drawError,dp(20),dp(66),12,Ui.TEXT_2);return;}
        int save=c.save();
        try{drawFrame(c);}catch(RuntimeException ex){
            c.restoreToCount(save);DoomApp.record(getContext(),ex);
            drawError=ex.getClass().getSimpleName()+": "+ex.getMessage();invalidate();
        }
    }
    private long frameStart;
    /** Schedules the next animation frame: every vsync (60 Hz), or every 33 ms in battery saver. */
    private void nextFrame(){
        if(Perf.saver(getContext())){long spent=(System.nanoTime()-frameStart)/1_000_000L;postInvalidateDelayed(Math.max(1,Perf.gameFrameMs(getContext())-spent));}
        else postInvalidateOnAnimation();
    }
    private void drawFrame(Canvas c){
        frameStart=System.nanoTime();
        w=getWidth();h=getHeight();toolbarH=mode==PLAY?0:dp(52);statusH=in2D()?dp(26):0;
        long now=System.nanoTime();float dt=Math.min(.05f,(now-lastFrame)*1e-9f);lastFrame=now;
        if(level!=project.level){level=project.level;sectorEditor=new SectorEditor(level);walker=null;}
        ui.clearHits();
        c.drawColor(Ui.BG);
        if(in2D()){
            drawMap(c);
            if(mode==THINGS)drawEntityManager(c);
            drawInspector(c);drawCursorControls(c);drawStatusBar(c);
        }else if(mode==POLY3D){draw3D(c,dt);}
        else if(mode==PLAY){drawPlay(c,dt);}
        else if(mode==HOME){drawHome(c,dt);}
        if(mode!=PLAY)drawTopBar(c);
        drawToast(c);
        if(launcherOpen)drawLauncher(c);
    }

    /** Index of the editor currently showing in this view, or -1 on the home screen. */
    private int currentEditor(){for(int i=0;i<4;i++)if(EDITOR_MODES[i]==mode)return i;return -1;}
    /**
     * Shared header, the same pattern as the FSM and Sprite editors: back, a title that opens the editor
     * launcher, then one icon button per editor and Save on the right.
     */
    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,toolbarH,Ui.SURFACE);ui.hline(c,0,w,toolbarH-dp(1),Ui.BORDER);
        float cy=toolbarH*.5f,bh=dp(34),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_LOGO);
        int ed=currentEditor();
        // right-hand side first: Save, more, then the editor buttons
        float x=w-dp(10);boolean wide=w>dp(820);
        float saveW=wide?dp(84):bh;r.set(x-saveW,top,x,top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);x-=saveW+dp(6);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.MORE,null,Ui.GHOST);ui.hit(r,H_MORE);x-=bh+dp(10);
        ui.vline(c,x+dp(4),top+dp(5),top+bh-dp(5),Ui.BORDER);x-=dp(6);
        float gap=dp(6);
        for(int i=EDITOR_NAMES.length-1;i>=0;i--){r.set(x-bh,top,x,top+bh);ui.button(c,r,EDITOR_ICONS[i],null,i==ed?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_ED+i);x-=bh+gap;}
        float right=x-dp(8);
        // title: the editor switcher (icon, name, chevron) or the project on the home screen
        float tl=dp(50);
        if(ed>=0){
            String nm=EDITOR_NAMES[ed];float tw=Math.min(right-tl,ui.measure(nm,15,1)+dp(78));
            r.set(tl,top,tl+tw,top+bh);
            ui.round(c,r,dp(8),Ui.SURFACE_2);ui.outline(c,r,dp(8),0x14ffffff,dp(1));
            float ib=dp(24);ui.round(c,r.left+dp(5),cy-ib*.5f,r.left+dp(5)+ib,cy+ib*.5f,dp(6),EDITOR_COLORS[ed]);ui.icon(c,EDITOR_ICONS[ed],r.left+dp(5)+ib*.5f,cy,15,0xffffffff);
            ui.textMid(c,ui.ellipsize(nm,15,1,tw-dp(70)),r.left+dp(38),cy,15,Ui.TEXT,1,-1);
            ui.icon(c,Icons.CHEVRON_DOWN,r.right-dp(16),cy,15,Ui.TEXT_2);ui.hit(r,H_SWITCH);
            float px=r.right+dp(12);String pn=ui.ellipsize(project.name,12,0,right-px);
            if(right-px>dp(60))ui.textMid(c,pn,px,cy,12,Ui.TEXT_3,0,-1);
        }else{
            float ib=dp(30);ui.round(c,tl,cy-ib*.5f,tl+ib,cy+ib*.5f,dp(8),Ui.ACCENT);ui.icon(c,Icons.LOGO,tl+ib*.5f,cy,18,0xffffffff);
            float tx=tl+ib+dp(10);
            ui.text(c,ui.ellipsize(project.name,15,1,right-tx),tx,cy-dp(1),15,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize((project.template==null||project.template.isEmpty()?"Project":project.template)+" · Home",10.5f,0,right-tx),tx,cy+dp(13),10.5f,Ui.TEXT_3,0,-1);
            r.set(tl,top,Math.min(right,tx+dp(160)),top+bh);ui.hit(r,H_SWITCH);
        }
    }
    /** Opens an editor by index (see EDITOR_NAMES); -1 is the home screen. */
    private void openEditor(int i){
        launcherOpen=false;
        if(i<0){if(mode!=HOME)changeMode(HOME);return;}
        if(i==4){openFsm(mode==THINGS?selDef:null);return;}
        if(i==5){openSprites(mode==THINGS?selDef:null);return;}
        if(i==6){save(false);if(host!=null)host.openSound(0);return;}
        if(EDITOR_MODES[i]!=mode)changeMode(EDITOR_MODES[i]);
    }
    /** Android back: close the launcher, leave Play, leave an editor for Home; false means leave the project. */
    boolean goBack(){
        if(launcherOpen){launcherOpen=false;invalidate();return true;}
        if(mode==PLAY&&bagOpen){closeBag();invalidate();return true;}
        if(mode==PLAY){changeMode(returnMode);invalidate();return true;}
        if(mode!=HOME){changeMode(HOME);invalidate();return true;}
        return false;
    }
    private void moreMenu(){
        if(host==null)return;
        ArrayList<String> errs=MapValidator.check(level);
        boolean sv=Perf.saver(getContext());
        host.choose(project.name,new String[]{"Check map ("+(errs.isEmpty()?"no problems":errs.size()+" warning"+(errs.size()==1?"":"s"))+")","Save now",
                sv?"Battery saver: ON (tap to turn off)":"Battery saver: off (30 fps, still backdrops)","Reset level…","Back to projects"},-1,k->{
            if(k==0)showMapCheck();
            else if(k==1)save(true);
            else if(k==2){Perf.setSaver(getContext(),!sv);hint(!sv?"Battery saver on · 30 fps game and 3D, still previews":"Battery saver off · 60 fps");lastInteraction=System.currentTimeMillis();}
            else if(k==3)host.confirm("Reset the level?","This clears every sector and placed entity. Entity types, FSMs and sprites are kept.","Reset level",()->{resetLevel();invalidate();});
            else if(k==4){save(false);if(onExitToTitle!=null)onExitToTitle.run();}
            invalidate();});
    }
    private void showMapCheck(){
        ArrayList<String> errs=MapValidator.check(level);
        if(errs.isEmpty()){hint("Map check: no problems found");return;}
        if(host!=null)host.choose("Map warnings",errs.toArray(new String[0]),-1,k->{});
    }

    // ---------------------------------------------------------------- Home
    private long lastInteraction=System.currentTimeMillis();
    private boolean homeAnimating(){return !Perf.saver(getContext())&&System.currentTimeMillis()-lastInteraction<30000;}
    private android.graphics.LinearGradient homeShade;private float homeShadeTop=-1;
    private long statsSig=-1;private int statWarnings,statStates;
    private final ArrayList<Renderer.SpriteDraw> homeSprites=new ArrayList<>();
    private final RectF homePreview=new RectF();private long homeClock,homeLast=System.nanoTime();
    private void homeStats(){
        long sig=Renderer.signature(level)*31+level.things.size()*7+project.defs.size();
        if(sig==statsSig)return;statsSig=sig;
        statWarnings=MapValidator.check(level).size();
        statStates=0;for(EntityDef d:project.defs)statStates+=d.fsm.nodes.size();
    }
    private String tileStat(int i){
        switch(i){
            case 0:return level.sectors.size()+(level.sectors.size()==1?" sector":" sectors");
            case 1:return level.things.size()+" placed · "+project.defs.size()+" types";
            case 2:return "First-person editing";
            case 3:return statWarnings==0?"Ready to play":statWarnings+" map warning"+(statWarnings==1?"":"s");
            case 4:return statStates+" states · "+project.defs.size()+" machines";
            case 5:return project.sprites.size()+(project.sprites.size()==1?" custom sprite":" custom sprites");
            default:return project.sounds.size()+(project.sounds.size()==1?" sound · ":" sounds · ")+project.songs.size()+(project.songs.size()==1?" song":" songs");
        }
    }
    private void drawHome(Canvas c,float dt){
        homeStats();
        float pad=dp(18),T=toolbarH+pad,B=h-pad,L=pad,R=w-pad;
        float split=L+(R-L)*.46f;
        // ----- left: live level preview card
        ui.panel(c,L,T,split-dp(8),B,dp(14));
        float cl=L+dp(14),cr=split-dp(22);
        ui.caption(c,"Level",cl,T+dp(24),Ui.TEXT_3);
        String st=statWarnings==0?"Map OK":statWarnings+" warning"+(statWarnings==1?"":"s");int sc=statWarnings==0?Ui.GREEN:Ui.AMBER;
        float sw=ui.measure(st,10.5f,1)+dp(30);r.set(cr-sw,T+dp(10),cr,T+dp(32));ui.round(c,r,dp(11),(sc&0x00ffffff)|0x26000000);
        p.setColor(sc);c.drawCircle(r.left+dp(11),r.centerY(),dp(3.5f),p);ui.textMid(c,st,r.left+dp(20),r.centerY(),10.5f,sc,1,-1);ui.hit(r,H_HOMEFIX);
        float bottomH=dp(118);
        homePreview.set(cl,T+dp(40),cr,B-bottomH);
        if(homePreview.height()>dp(50))drawHomePreview(c,homePreview);
        // stats row
        float sy=B-bottomH+dp(14);
        String[] sv={String.valueOf(level.sectors.size()),String.valueOf(level.things.size()),String.valueOf(project.defs.size()),String.valueOf(project.sprites.size())};
        String[] sl={"Sectors","Placed","Types","Sprites"};
        float cw=(cr-cl)/4f;
        for(int i=0;i<4;i++){float x=cl+i*cw;ui.text(c,sv[i],x,sy+dp(18),18,Ui.TEXT,1,-1);ui.text(c,sl[i],x,sy+dp(34),10.5f,Ui.TEXT_3,0,-1);
            if(i>0)ui.vline(c,x-dp(8),sy+dp(2),sy+dp(36),Ui.DIVIDER);}
        float by=B-dp(50),bh=dp(38);
        float half=(cr-cl-dp(10))*.5f;
        r.set(cl,by,cl+half,by+bh);ui.button(c,r,Icons.PLAY,"Play level",Ui.PRIMARY);ui.hit(r,H_HOMEPLAY);
        r.set(cl+half+dp(10),by,cr,by+bh);ui.button(c,r,Icons.CUBE,"Walk in 3D",Ui.NORMAL);ui.hit(r,H_HOME3D);
        // ----- right: editors
        float gl=split+dp(8);boolean shortScreen=B-T<dp(300);
        ui.text(c,"Editors",gl+dp(2),T+dp(16),15,Ui.TEXT,1,-1);
        if(!shortScreen)ui.text(c,ui.ellipsize("Tap one to open it · use the icons above or the title menu to switch at any time",11,0,R-gl),gl+dp(2),T+dp(34),11,Ui.TEXT_3,0,-1);
        float gt=shortScreen?T+dp(26):T+dp(48),gap=dp(10);int cols=w>dp(700)?2:1;
        if(cols==2&&(B-gt-gap*3)/4f<dp(60))cols=3;   // short screens: 3 columns of compact tiles
        int rows=(EDITOR_NAMES.length+cols-1)/cols;
        float tw=(R-gl-gap*(cols-1))/cols,th=Math.max(dp(44),Math.min(dp(96),(B-gt-gap*(rows-1))/rows));
        for(int i=0;i<EDITOR_NAMES.length;i++){int col=i%cols,row=i/cols;float x=gl+col*(tw+gap),y=gt+row*(th+gap);
            r.set(x,y,x+tw,y+th);drawEditorTile(c,r,i,H_TILE+i,false);}
        // Live preview runs every vsync (60 Hz) so it stays smooth, but settles after 30 s without a touch (or straight away in battery saver).
        if(homeAnimating())postInvalidateOnAnimation();
    }
    private void drawEditorTile(Canvas c,RectF rr,int i,int hid,boolean current){
        ui.round(c,rr,dp(12),current?0xff26324a:Ui.SURFACE);ui.outline(c,rr,dp(12),current?Ui.ACCENT:Ui.BORDER,current?dp(2):dp(1));
        if(rr.width()<dp(190)){   // narrow tile: icon above the name
            float ib=Math.min(dp(34),rr.height()*.4f),iy=rr.top+Math.max(dp(8),(rr.height()-ib-dp(34))*.5f);
            ui.round(c,rr.centerX()-ib*.5f,iy,rr.centerX()+ib*.5f,iy+ib,dp(9),EDITOR_COLORS[i]);ui.icon(c,EDITOR_ICONS[i],rr.centerX(),iy+ib*.5f,ib*.55f/ui.dp(1),0xffffffff);
            ui.text(c,ui.ellipsize(EDITOR_NAMES[i],13,1,rr.width()-dp(12)),rr.centerX(),iy+ib+dp(17),13,Ui.TEXT,1,0);
            if(rr.bottom-(iy+ib+dp(17))>dp(18))ui.text(c,ui.ellipsize(tileStat(i),10,0,rr.width()-dp(12)),rr.centerX(),iy+ib+dp(31),10,i==3&&statWarnings>0?Ui.AMBER:Ui.TEXT_3,0,0);
            ui.hit(rr,hid);return;}
        // accent stripe + icon badge
        float ib=Math.max(dp(26),Math.min(dp(42),rr.height()-dp(24))),ix=rr.left+dp(rr.height()<dp(60)?10:14),iy=rr.centerY()-ib*.5f;
        ui.round(c,ix,iy,ix+ib,iy+ib,dp(10),EDITOR_COLORS[i]);
        ui.icon(c,EDITOR_ICONS[i],ix+ib*.5f,iy+ib*.5f,ib*.5f/ui.dp(1),0xffffffff);
        float tx=ix+ib+dp(14),tr=rr.right-dp(30);
        boolean roomy=rr.height()>dp(74)&&rr.width()>dp(200);tx=ix+ib+dp(roomy?14:10);
        ui.text(c,ui.ellipsize(EDITOR_NAMES[i],14,1,tr-tx),tx,rr.centerY()-(roomy?dp(12):dp(2)),14,Ui.TEXT,1,-1);
        if(roomy)ui.text(c,ui.ellipsize(EDITOR_DESC[i],11,0,tr-tx),tx,rr.centerY()+dp(5),11,Ui.TEXT_2,0,-1);
        ui.text(c,ui.ellipsize(tileStat(i),10.5f,1,tr-tx),tx,rr.centerY()+(roomy?dp(22):dp(14)),10.5f,i==3&&statWarnings>0?Ui.AMBER:Ui.TEXT_3,1,-1);
        ui.icon(c,Icons.CHEVRON_RIGHT,rr.right-dp(18),rr.centerY(),16,Ui.TEXT_3);
        ui.hit(rr,hid);
    }
    /** Live 3D look around from the player start (a slow pan), or a prompt if there is no level yet. */
    private void drawHomePreview(Canvas c,RectF rr){
        int save=c.save();path.reset();path.addRoundRect(rr,dp(10),dp(10),Path.Direction.CW);c.clipPath(path);
        int si=level.sectorAt(level.playerX,level.playerY);
        if(si<0){ui.rect(c,rr.left,rr.top,rr.right,rr.bottom,0xff101317);
            ui.icon(c,Icons.SECTORS,rr.centerX(),rr.centerY()-dp(16),32,Ui.TEXT_3);
            ui.textMid(c,level.sectors.isEmpty()?"No level yet — open Sectors to draw your first room":"Set a player start inside a sector",rr.centerX(),rr.centerY()+dp(18),12,Ui.TEXT_2,1,0);
            c.restoreToCount(save);return;}
        // time only advances while animating, so a settled preview stays exactly where it stopped
        long now=System.nanoTime();if(homeAnimating())homeClock+=Math.min(100_000_000L,now-homeLast);homeLast=now;
        float t=homeClock*1e-9f;
        float ang=level.facing+(float)Math.sin(t*.22f)*1.1f;
        Level.Sector sec=level.sectors.get(si);
        homeSprites.clear();int pool=0;
        for(int i=0;i<level.things.size();i++){Level.Thing th=level.things.get(i);EntityDef d=project.def(th.def);if(d==null||d.kind==EntityDef.PLAYER)continue;
            homeSprites.add(sd(pool++).set(th.x,th.y,floorAtLevel(th.x,th.y),project.spriteFor(d,d.sprite,Sprites.F_IDLE,t,0,World.angleDiff((float)Math.atan2(level.playerY-th.y,level.playerX-th.x),th.angle)),-1,false));}
        renderer.highlight=-1;
        renderer.render(c,level,level.playerX,level.playerY,Math.min(sec.ceiling-.1f,sec.floor+World.EYE),ang,0,rr,homeSprites);
        // soft vignette at the bottom for the label
        if(homeShade==null||homeShadeTop!=rr.bottom){homeShadeTop=rr.bottom;homeShade=new android.graphics.LinearGradient(0,rr.bottom-dp(60),0,rr.bottom,0x00000000,0xb0000000,android.graphics.Shader.TileMode.CLAMP);}
        p.setShader(homeShade);p.setColor(0xffffffff);
        c.drawRect(rr.left,rr.bottom-dp(60),rr.right,rr.bottom,p);p.setShader(null);
        ui.icon(c,Icons.CAMERA,rr.left+dp(18),rr.bottom-dp(16),14,0xccffffff);
        ui.textMid(c,"Live preview from the player start",rr.left+dp(32),rr.bottom-dp(16),11,0xddffffff,1,-1);
        c.restoreToCount(save);
        ui.outline(c,rr,dp(10),0x1effffff,dp(1));
    }
    /** Pop-up that lists every editor, from the header title. */
    private void drawLauncher(Canvas c){
        ui.clearHits();
        ui.rect(c,0,0,w,h,0xb3000000);ui.hit(0,0,w,h,H_LAUNCH_CLOSE);
        int n=EDITOR_NAMES.length+1;int cols=w>dp(760)?4:3,rows=(n+cols-1)/cols;
        float pw=Math.min(w-dp(40),dp(cols==4?860:640)),tileH=dp(78),gap=dp(10);
        float ph=dp(64)+rows*tileH+(rows-1)*gap+dp(18);ph=Math.min(ph,h-dp(24));
        float L=(w-pw)*.5f,T=Math.max(dp(12),(h-ph)*.5f),R=L+pw,B=T+ph;
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(16),0x80000000);ui.panel(c,L,T,R,B,dp(16));ui.hit(L,T,R,B,-2);
        ui.text(c,"Open editor",L+dp(20),T+dp(32),17,Ui.TEXT,1,-1);
        ui.text(c,project.name,L+dp(20),T+dp(50),11,Ui.TEXT_3,0,-1);
        r.set(R-dp(46),T+dp(12),R-dp(12),T+dp(46));ui.button(c,r,Icons.CANCEL,null,Ui.GHOST);ui.hit(r,H_LAUNCH_CLOSE);
        float tw=(pw-dp(40)-gap*(cols-1))/cols;int ed=currentEditor();
        for(int k=0;k<n;k++){int col=k%cols,row=k/cols;float x=L+dp(20)+col*(tw+gap),y=T+dp(64)+row*(tileH+gap);
            RectF rr=new RectF(x,y,x+tw,y+tileH);
            if(k==0){boolean on=mode==HOME;ui.round(c,rr,dp(12),on?0xff26324a:Ui.SURFACE_2);ui.outline(c,rr,dp(12),on?Ui.ACCENT:Ui.BORDER,on?dp(2):dp(1));
                float ib=dp(36),ix=rr.left+dp(12),iy=rr.centerY()-ib*.5f;ui.round(c,ix,iy,ix+ib,iy+ib,dp(9),Ui.SURFACE_3);ui.icon(c,Icons.LOGO,ix+ib*.5f,iy+ib*.5f,20,Ui.TEXT);
                ui.text(c,"Home",ix+ib+dp(12),rr.centerY()-dp(2),14,Ui.TEXT,1,-1);ui.text(c,"Project overview",ix+ib+dp(12),rr.centerY()+dp(14),10.5f,Ui.TEXT_3,0,-1);
                ui.hit(rr,H_LAUNCH_HOME);continue;}
            int i=k-1;boolean on=i==ed;
            ui.round(c,rr,dp(12),on?0xff26324a:Ui.SURFACE_2);ui.outline(c,rr,dp(12),on?Ui.ACCENT:Ui.BORDER,on?dp(2):dp(1));
            float ib=dp(36),ix=rr.left+dp(12),iy=rr.centerY()-ib*.5f;ui.round(c,ix,iy,ix+ib,iy+ib,dp(9),EDITOR_COLORS[i]);ui.icon(c,EDITOR_ICONS[i],ix+ib*.5f,iy+ib*.5f,20,0xffffffff);
            ui.text(c,EDITOR_NAMES[i],ix+ib+dp(12),rr.centerY()-dp(2),14,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize(tileStat(i),10.5f,0,rr.right-ix-ib-dp(20)),ix+ib+dp(12),rr.centerY()+dp(14),10.5f,Ui.TEXT_3,0,-1);
            ui.hit(rr,H_LAUNCH+i);}
    }
    private boolean launcherTap(int hid){
        if(hid==H_LAUNCH_HOME){openEditor(-1);return true;}
        if(hid>=H_LAUNCH&&hid<H_LAUNCH+EDITOR_NAMES.length){openEditor(hid-H_LAUNCH);return true;}
        if(hid==H_LAUNCH_CLOSE){launcherOpen=false;return true;}
        return true;
    }
    private boolean homeTap(int hid){
        if(hid>=H_TILE&&hid<H_TILE+EDITOR_NAMES.length){openEditor(hid-H_TILE);return true;}
        if(hid==H_HOMEPLAY){openEditor(3);return true;}
        if(hid==H_HOME3D){openEditor(2);return true;}
        if(hid==H_HOMEFIX){showMapCheck();return true;}
        return false;
    }
    private void drawToast(Canvas c){
        if(System.currentTimeMillis()>=messageUntil||message.isEmpty())return;
        if(mode==PLAY&&world!=null&&(bagOpen||world.dialogue!=null))return;
        float cl=mode==POLY3D?browserW():in2D()?mapL():0,cr=in2D()?mapR():w,maxW=cr-cl-dp(40);
        String t=ui.ellipsize(message,12.5f,1,maxW-dp(52));
        float tw=ui.measure(t,12.5f,1)+dp(52),cx=(cl+cr)*.5f,ph=dp(34),top=mode==PLAY?h-dp(52)-dp(12)-ph:toolbarH+dp(12);
        ui.pill(c,cx-tw*.5f,top,cx+tw*.5f,top+ph,0xf2262c34);
        ui.icon(c,Icons.INFO,cx-tw*.5f+dp(20),top+ph*.5f,16,0xff8fbcff);ui.textMid(c,t,cx-tw*.5f+dp(36),top+ph*.5f,12.5f,Ui.TEXT,1,-1);
        postInvalidateDelayed(Math.max(16,messageUntil-System.currentTimeMillis()));
    }
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2600;invalidate();}

    // ---------------------------------------------------------------- 2D map
    private void updateView(){
        cell=(mapB()-mapT())/14f*zoom;
        if(followCursor){
            float cx=sx(cursorX),cy=sy(cursorY),mx=(mapR()-mapL())*.28f,my=(mapB()-mapT())*.28f,ox=(mapL()+mapR())*.5f,oy=(mapT()+mapB())*.5f;
            float tx=viewX,ty=viewY;
            if(Math.abs(cx-ox)>mx)tx=cursorX-Math.signum(cx-ox)*mx/cell;if(Math.abs(cy-oy)>my)ty=cursorY-Math.signum(cy-oy)*my/cell;
            viewX+=(tx-viewX)*.2f;viewY+=(ty-viewY)*.2f;
            if(Math.abs(tx-viewX)>.01f||Math.abs(ty-viewY)>.01f)postInvalidateOnAnimation();
        }
    }
    private void drawMap(Canvas c){
        updateView();
        float L=mapL(),R=mapR(),T=mapT(),B=mapB();
        int save=c.save();c.clipRect(L,T,R,B);
        ui.rect(c,L,T,R,B,Ui.CANVAS);
        int x0=(int)Math.floor(wx(L))-1,x1=(int)Math.ceil(wx(R))+1,y0=(int)Math.floor(wy(T))-1,y1=(int)Math.ceil(wy(B))+1;
        float lw=Math.max(1,dp(.75f));
        for(int i=x0;i<=x1;i++){int col=i==0?0xff39424e:i%4==0?0xff2a3139:0xff21262c;if(col==0xff21262c&&cell<dp(7))continue;ui.rect(c,sx(i),T,sx(i)+lw,B,col);}
        for(int i=y0;i<=y1;i++){int col=i==0?0xff39424e:i%4==0?0xff2a3139:0xff21262c;if(col==0xff21262c&&cell<dp(7))continue;ui.rect(c,L,sy(i),R,sy(i)+lw,col);}
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        int curSec=sectorUnderCursor();
        for(int k=0;k<level.sectors.size();k++){Level.Sector s=level.sectors.get(k);if(s.loop.size()<3)continue;path.reset();
            for(int j=0;j<s.loop.size();j++){Level.Vertex v=level.vertices.get(s.loop.get(j));if(j==0)path.moveTo(sx(v.x),sy(v.y));else path.lineTo(sx(v.x),sy(v.y));}path.close();
            boolean sel=mode==POLY&&selKind==1&&k==selSector;
            int col=sel?0x473d8bfd:s.special==Level.SP_DOOR?0x30f2b84b:s.special==Level.SP_LIFT?0x303ecf8e:s.special==Level.SP_DAMAGE||s.special==Level.SP_EXIT?0x30ef5b5b:mode==THINGS?0x1c8fa3b8:0x268fa3b8;
            if(mode==POLY&&k==curSec&&!sel&&(tool==SELECT||tool==DELETE||tool==SPECIAL||tool==STAIRS||(tool==DUPLICATE&&stage==0)||(tool==MOVE&&stage==0)||toolPage==2))col=0x3a8fa3b8;
            p.setColor(col);c.drawPath(path,p);
            if(s.special!=Level.SP_NONE&&cell>dp(14)){float[] ce=centroid(s);ui.icon(c,s.special==Level.SP_DOOR?Icons.DOOR:s.special==Level.SP_LIFT?Icons.LAYERS:s.special==Level.SP_EXIT?Icons.FLAG:s.special==Level.SP_SECRET?Icons.HELP:Icons.ZAP,sx(ce[0]),sy(ce[1]),14,0x99ffffff);}
        }
        p.setStrokeCap(Paint.Cap.ROUND);
        long ts=Renderer.signature(level);if(mapLines==null||ts!=mapLinesSig||mapLinesLevel!=level){mapLines=PortalTopology.build(level);mapLinesSig=ts;mapLinesLevel=level;}
        for(Level.LineDef line:mapLines){
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);boolean portal=line.back>=0;
            p.setColor(portal?0xffd99a3e:0xffd6dce4);p.setStrokeWidth(portal?dp(1.5f):dp(2));c.drawLine(sx(a.x),sy(a.y),sx(b.x),sy(b.y),p);
        }
        if(mode==POLY)drawSelection(c);
        p.setStrokeCap(Paint.Cap.BUTT);
        float vs=Math.max(dp(2.5f),Math.min(dp(4),cell*.09f));
        for(int i=0;i<level.vertices.size();i++){Level.Vertex v=level.vertices.get(i);
            boolean hot=mode==POLY&&((selKind==3&&i==selVertex)||contains(moveIds,i)&&stage==1);
            ui.rect(c,sx(v.x)-vs-dp(1),sy(v.y)-vs-dp(1),sx(v.x)+vs+dp(1),sy(v.y)+vs+dp(1),0xff15181c);
            ui.rect(c,sx(v.x)-vs,sy(v.y)-vs,sx(v.x)+vs,sy(v.y)+vs,hot?Ui.ORANGE:0xffeef2f6);}
        if(mode==POLY&&!pending.isEmpty()){
            p.setStyle(Paint.Style.STROKE);p.setColor(Ui.ACCENT);p.setStrokeWidth(dp(2));path.reset();
            for(int i=0;i<pending.size();i++){float[] v=pending.get(i);if(i==0)path.moveTo(sx(v[0]),sy(v[1]));else path.lineTo(sx(v[0]),sy(v[1]));}
            c.drawPath(path,p);float[] last=pending.get(pending.size()-1);
            p.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(4)},0));p.setColor(0xaa3d8bfd);c.drawLine(sx(last[0]),sy(last[1]),sx(cursorX),sy(cursorY),p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
            for(int i=0;i<pending.size();i++){float[] v=pending.get(i);p.setColor(0xff15181c);c.drawCircle(sx(v[0]),sy(v[1]),dp(5.5f),p);p.setColor(i==0?Ui.GREEN:Ui.ACCENT);c.drawCircle(sx(v[0]),sy(v[1]),dp(4),p);}
        }
        drawThings(c);
        // previews for two-stage tools
        if(mode==POLY&&stage==1&&(tool==RECT||tool==DUPLICATE||tool==EXTRUDE)){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(Ui.ACCENT);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));
            if(tool==RECT){float l=sx(Math.min(anchorX,cursorX)),t=sy(Math.min(anchorY,cursorY)),rr=sx(Math.max(anchorX,cursorX)),b=sy(Math.max(anchorY,cursorY));p.setStyle(Paint.Style.FILL);p.setColor(0x223d8bfd);p.setPathEffect(null);c.drawRect(l,t,rr,b,p);p.setStyle(Paint.Style.STROKE);p.setColor(Ui.ACCENT);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));c.drawRect(l,t,rr,b,p);}
            else if(tool==DUPLICATE&&pickSector>=0&&pickSector<level.sectors.size()){Level.Sector s=level.sectors.get(pickSector);path.reset();float dx=cursorX-anchorX,dy=cursorY-anchorY;
                for(int j=0;j<s.loop.size();j++){Level.Vertex v=level.vertices.get(s.loop.get(j));if(j==0)path.moveTo(sx(v.x+dx),sy(v.y+dy));else path.lineTo(sx(v.x+dx),sy(v.y+dy));}path.close();c.drawPath(path,p);}
            else if(tool==EXTRUDE&&pickSector>=0){float[] e=edgeCoords(pickSector,pickEdge);if(e!=null){float d=extrudeDistance(e);float[] nrm=edgeNormal(pickSector,pickEdge);
                path.reset();path.moveTo(sx(e[0]),sy(e[1]));path.lineTo(sx(e[2]),sy(e[3]));path.lineTo(sx(e[2]+nrm[0]*d),sy(e[3]+nrm[1]*d));path.lineTo(sx(e[0]+nrm[0]*d),sy(e[1]+nrm[1]*d));path.close();c.drawPath(path,p);}}
            p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        }
        drawCursor(c);
        c.restoreToCount(save);
    }
    private static boolean contains(int[] a,int v){for(int x:a)if(x==v)return true;return false;}
    private float[] centroid(Level.Sector s){float x=0,y=0;for(int id:s.loop){x+=level.vertices.get(id).x;y+=level.vertices.get(id).y;}return new float[]{x/s.loop.size(),y/s.loop.size()};}
    private void drawSelection(Canvas c){
        p.setStyle(Paint.Style.STROKE);
        if(selKind==1&&selSector>=0&&selSector<level.sectors.size()){Level.Sector s=level.sectors.get(selSector);p.setColor(Ui.ACCENT);p.setStrokeWidth(dp(2.5f));
            for(int j=0;j<s.loop.size();j++){Level.Vertex a=level.vertices.get(s.loop.get(j)),b=level.vertices.get(s.loop.get((j+1)%s.loop.size()));c.drawLine(sx(a.x),sy(a.y),sx(b.x),sy(b.y),p);}}
        int es=selKind==2?selSector:(stage==1&&(tool==EXTRUDE||tool==MOVE)&&pickEdge>=0)?pickSector:-1,ee=selKind==2?selEdge:pickEdge;
        if(es>=0){float[] e=edgeCoords(es,ee);if(e!=null){p.setColor(Ui.ORANGE);p.setStrokeWidth(dp(4));c.drawLine(sx(e[0]),sy(e[1]),sx(e[2]),sy(e[3]),p);}}
        p.setStyle(Paint.Style.FILL);
    }
    private void drawThings(Canvas c){
        boolean full=mode==THINGS;
        for(int i=0;i<level.things.size();i++){Level.Thing t=level.things.get(i);EntityDef d=project.def(t.def);
            float x=sx(t.x),y=sy(t.y);
            if(!full){p.setColor(d!=null&&d.kind==EntityDef.MONSTER?0xccef5b5b:0xcc3ecf8e);c.drawCircle(x,y,dp(3),p);continue;}
            drawThingIcon(c,d,x,y,t.angle,i==selThing);}
        // player start
        EntityDef pd=project.playerDef();
        if(full)drawThingIcon(c,pd,sx(level.playerX),sy(level.playerY),level.facing,selThing==-2);
        else{float x=sx(level.playerX),y=sy(level.playerY),s=dp(8);float cs=(float)Math.cos(level.facing),sn=(float)Math.sin(level.facing);
            path.reset();path.moveTo(x+cs*s,y+sn*s);path.lineTo(x-cs*s*.7f-sn*s*.7f,y-sn*s*.7f+cs*s*.7f);path.lineTo(x-cs*s*.3f,y-sn*s*.3f);path.lineTo(x-cs*s*.7f+sn*s*.7f,y-sn*s*.7f-cs*s*.7f);path.close();
            p.setColor(Ui.GREEN);c.drawPath(path,p);}
    }
    private void drawThingIcon(Canvas c,EntityDef d,float x,float y,float angle,boolean sel){
        if(d==null)return;
        float rad=Math.max(dp(5),d.radius*cell);
        int kc=kindColor(d.kind);
        p.setStyle(Paint.Style.FILL);p.setColor((kc&0xffffff)|0x30000000);c.drawCircle(x,y,rad,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sel?dp(2.5f):dp(1.25f));p.setColor(sel?Ui.ACCENT:kc);c.drawCircle(x,y,rad,p);
        if(d.kind==EntityDef.MONSTER||d.kind==EntityDef.PLAYER){float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);p.setStrokeWidth(dp(2));c.drawLine(x+cs*rad*.6f,y+sn*rad*.6f,x+cs*(rad+dp(6)),y+sn*(rad+dp(6)),p);}
        p.setStyle(Paint.Style.FILL);
        Bitmap b=defThumb(d);float hh=Math.max(dp(16),Math.min(dp(44),cell*1.1f)),ww=hh*b.getWidth()/b.getHeight();
        if(ww>hh*1.4f){ww=hh*1.4f;hh=ww*b.getHeight()/b.getWidth();}
        r.set(x-ww*.5f,y-hh*.5f,x+ww*.5f,y+hh*.5f);c.drawBitmap(b,null,r,pixelPaint);
    }
    private static int kindColor(int k){switch(k){case EntityDef.PLAYER:return Ui.GREEN;case EntityDef.MONSTER:return Ui.RED;case EntityDef.PICKUP:return Ui.AMBER;case EntityDef.TRIGGER:return 0xffb07cff;case EntityDef.NPC:return 0xff5ab8ff;default:return Ui.TEXT_2;}}
    private final java.util.HashMap<String,Bitmap> customThumbs=new java.util.HashMap<>();
    /** Thumbnail for an entity type: its custom sprite if it has one, otherwise the built-in sprite. */
    private Bitmap defThumb(EntityDef d){
        CustomSprite cs=project.sprite(d.customSprite);
        if(cs!=null){String key=cs.id+"#"+cs.version+"#"+cs.w+"x"+cs.h;Bitmap b=customThumbs.get(key);if(b==null){b=cs.thumbnail();customThumbs.put(key,b);}return b;}
        return spriteThumb(d.lookSprite());
    }
    private Bitmap spriteThumb(int id){int i=Math.max(0,Math.min(Sprites.COUNT-1,id));if(spriteThumbs[i]==null)spriteThumbs[i]=Sprites.preview(i);return spriteThumbs[i];}
    private Bitmap texThumb(int id){int i=Math.max(0,Math.min(Textures.COUNT-1,id));if(texThumbs[i]==null)texThumbs[i]=Textures.preview(i);return texThumbs[i];}
    private void drawCursor(Canvas c){
        float px=sx(cursorX),py=sy(cursorY);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(4));p.setColor(0x9915181c);c.drawCircle(px,py,dp(10),p);
        p.setStrokeWidth(dp(1.75f));p.setColor(Ui.AMBER);c.drawCircle(px,py,dp(10),p);
        float g=dp(5),len=dp(16);c.drawLine(px-len,py,px-g,py,p);c.drawLine(px+g,py,px+len,py,p);c.drawLine(px,py-len,px,py-g,p);c.drawLine(px,py+g,px,py+len,p);
        p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);p.setColor(Ui.AMBER);c.drawCircle(px,py,dp(2),p);
    }
    private void drawStatusBar(Canvas c){
        float L=mapL(),R=mapR(),top=mapB(),cy=top+statusH*.5f;
        ui.rect(c,L,top,R,h,Ui.SURFACE);ui.hline(c,L,R,top,Ui.BORDER);
        float x=L+dp(12);
        x=statusItem(c,x,cy,Icons.CROSSHAIR,fmt(cursorX)+", "+fmt(cursorY));
        x=statusItem(c,x,cy,Icons.GRID,"Snap "+fmt(snap()));
        if(R-L>dp(420)){x=statusItem(c,x,cy,Icons.SECTORS,level.sectors.size()+" sectors");x=statusItem(c,x,cy,Icons.ENTITIES,level.things.size()+" entities");}
        float bw=dp(34),bt=top+dp(3),bb=h-dp(3);
        r.set(R-dp(6)-bw,bt,R-dp(6),bb);ui.round(c,r,dp(6),Ui.SURFACE_2);ui.icon(c,Icons.PLUS,r.centerX(),r.centerY(),15,Ui.TEXT);ui.hit(r.left,top,r.right,h,H_ZOOMIN);
        ui.textMid(c,Math.round(zoom*100)+"%",R-dp(12)-bw-dp(24),cy,11,Ui.TEXT_2,1,0);
        r.set(R-dp(18)-bw*2-dp(36),bt,R-dp(18)-bw-dp(36),bb);ui.round(c,r,dp(6),Ui.SURFACE_2);ui.icon(c,Icons.MINUS,r.centerX(),r.centerY(),15,Ui.TEXT);ui.hit(r.left,top,r.right,h,H_ZOOMOUT);
    }
    private float statusItem(Canvas c,float x,float cy,int icon,String text){ui.icon(c,icon,x+dp(6),cy,12,Ui.TEXT_3);ui.textMid(c,text,x+dp(17),cy,11,Ui.TEXT_2,0,-1);return x+dp(17)+ui.measure(text,11,0)+dp(18);}
    static String fmt(float v){return Math.abs(v-Math.round(v))<.001f?String.valueOf(Math.round(v)):String.format(java.util.Locale.US,"%.2f",v).replaceAll("0+$","");}

    // ---------------------------------------------------------------- inspector (Sectors) and property panel (Entities)
    private void drawInspector(Canvas c){
        float x0=mapR(),pad=dp(10),l=x0+pad,rr=w-pad;
        ui.rect(c,x0,toolbarH,w,h,Ui.SURFACE);ui.vline(c,x0,toolbarH,h,Ui.BORDER);
        if(mode==POLY){
            float tt=toolbarH+dp(10),th=dp(30),tg=dp(5),tw=(rr-l-tg*2)/3f;
            for(int i=0;i<3;i++){float a=l+i*(tw+tg);r.set(a,tt,a+tw,tt+th);ui.button(c,r,-1,PAGE_NAMES[i],i==toolPage?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_PAGE+i);}
            float cardH=dp(70),gridTop=tt+th+dp(10),gridBottom=h-cardH-dp(18);
            float rowH=Math.min(dp(38),(gridBottom-gridTop)/6f),colW=(rr-l)/2f,gap=dp(3);
            int[] tools=PAGES[toolPage];
            for(int i=0;i<tools.length;i++){int t=tools[i],col=i%2,row=i/2;
                r.set(l+col*colW+(col==0?0:gap),gridTop+row*rowH,l+(col+1)*colW-(col==0?gap:0),gridTop+(row+1)*rowH-gap*2);
                boolean active=t==tool&&t<100;int style=active?Ui.ACTIVE:t==DELETE?Ui.DANGER:Ui.NORMAL;
                ui.button(c,r,toolIcon(t),toolName(t),style);ui.hit(r,H_TOOL+i);}
            float ct=h-cardH-dp(10);
            ui.round(c,l,ct,rr,h-dp(10),dp(8),Ui.SURFACE_2);ui.outline(c,l,ct,rr,h-dp(10),dp(8),0x14ffffff,dp(1));
            drawSelectionCard(c,l,ct,rr);
        }else if(mode==THINGS)drawPropertyPanel(c,x0,l,rr);
    }
    private void drawSelectionCard(Canvas c,float l,float ct,float rr){
        int s=targetSector();
        if(selKind==3&&selVertex>=0&&selVertex<level.vertices.size()){Level.Vertex v=level.vertices.get(selVertex);
            ui.caption(c,"Vertex "+(selVertex+1),l+dp(10),ct+dp(18),Ui.TEXT_3);float cw=(rr-l-dp(20))/3f;
            stat(c,l+dp(10),ct+dp(46),fmt(v.x),"X");stat(c,l+dp(10)+cw,ct+dp(46),fmt(v.y),"Y");return;}
        if(selKind==2){float[] e=edgeCoords(selSector,selEdge);if(e!=null){ui.caption(c,"Edge · sector "+(selSector+1),l+dp(10),ct+dp(18),Ui.TEXT_3);
            stat(c,l+dp(10),ct+dp(46),fmt((float)Math.hypot(e[2]-e[0],e[3]-e[1])),"Length");return;}}
        if(s>=0){Level.Sector sec=level.sectors.get(s);
            ui.caption(c,(selKind==1?"Sector ":"Under cursor · sector ")+(s+1)+(sec.special!=0?" · "+Level.SPECIAL_NAMES[sec.special]:""),l+dp(10),ct+dp(18),Ui.TEXT_3);
            float cw=(rr-l-dp(20))/3f;
            stat(c,l+dp(10),ct+dp(46),fmt(sec.floor),"Floor");stat(c,l+dp(10)+cw,ct+dp(46),fmt(sec.ceiling),"Ceiling");stat(c,l+dp(10)+cw*2,ct+dp(46),String.valueOf(sec.light),"Light");
        }else{ui.caption(c,"Nothing selected",l+dp(10),ct+dp(18),Ui.TEXT_3);
            ui.text(c,"Move the cursor onto a sector, or",l+dp(10),ct+dp(42),11.5f,Ui.TEXT_2);ui.text(c,"use Select then Apply.",l+dp(10),ct+dp(58),11.5f,Ui.TEXT_2);}
    }
    private void stat(Canvas c,float x,float y,String value,String label){ui.text(c,value,x,y,15,Ui.TEXT,1,-1);ui.text(c,label,x,y+dp(14),10.5f,Ui.TEXT_3);}

    private EntityDef currentDef(){EntityDef d=project.def(selDef);if(d==null&&!project.defs.isEmpty()){d=project.defs.get(Math.min(1,project.defs.size()-1));selDef=d.id;}return d;}
    private void drawEntityManager(Canvas c){
        float pw=entPanelW(),pad=dp(10);
        ui.rect(c,0,toolbarH,pw,h,Ui.SURFACE);ui.vline(c,pw-dp(1),toolbarH,h,Ui.BORDER);
        float ht=toolbarH+dp(10);
        ui.caption(c,"Entity types",pad,ht+dp(17),Ui.TEXT_3);
        r.set(pw-pad-dp(30),ht+dp(2),pw-pad,ht+dp(30));ui.button(c,r,Icons.PLUS,null,Ui.PRIMARY);ui.hit(r,H_NEWDEF);
        float top=ht+dp(40),bottom=h,rowH=dp(48);
        float maxScroll=Math.max(0,project.defs.size()*rowH-(bottom-top)+dp(8));defScroll=Math.max(0,Math.min(maxScroll,defScroll));
        int save=c.save();c.clipRect(0,top,pw-dp(1),bottom);
        for(int i=0;i<project.defs.size();i++){EntityDef d=project.defs.get(i);float y=top+i*rowH-defScroll;if(y+rowH<top||y>bottom)continue;
            boolean sel=d.id.equals(selDef);
            if(sel){ui.round(c,dp(6),y+dp(2),pw-dp(6),y+rowH-dp(2),dp(8),Ui.ACCENT_SOFT);ui.outline(c,dp(6),y+dp(2),pw-dp(6),y+rowH-dp(2),dp(8),0x993d8bfd,dp(1));}
            ui.round(c,dp(12),y+dp(7),dp(46),y+rowH-dp(7),dp(6),Ui.BG);
            Bitmap b=defThumb(d);float th=dp(28),tw=th*b.getWidth()/b.getHeight();if(tw>dp(30)){tw=dp(30);th=tw*b.getHeight()/b.getWidth();}
            r.set(dp(29)-tw*.5f,y+rowH*.5f-th*.5f,dp(29)+tw*.5f,y+rowH*.5f+th*.5f);c.drawBitmap(b,null,r,pixelPaint);
            ui.text(c,ui.ellipsize(d.name,12.5f,1,pw-dp(64)),dp(54),y+dp(21),12.5f,sel?Ui.TEXT:Ui.TEXT,1,-1);
            int n=d.kind==EntityDef.PLAYER?1:project.countPlaced(d.id);
            p.setColor(kindColor(d.kind));c.drawCircle(dp(57),y+dp(32),dp(3),p);
            ui.text(c,EntityDef.KIND_NAMES[d.kind]+" · "+n+" placed",dp(64),y+dp(36),10.5f,Ui.TEXT_3);
            float vt=Math.max(top,y),vb=Math.min(bottom,y+rowH);if(vb>vt)ui.hit(0,vt,pw,vb,H_DEF+i);
        }
        c.restoreToCount(save);
        if(maxScroll>0){float bar=(bottom-top)*(bottom-top)/(bottom-top+maxScroll);float by=top+(bottom-top-bar)*defScroll/maxScroll;ui.round(c,pw-dp(5),by,pw-dp(2),by+bar,dp(2),0x33ffffff);}
    }
    private String[] propLabels(EntityDef d){return new String[]{"Name","Type","Sprite","Health","Speed","Radius","Height","Solid","Shootable","Animations"};}
    private String propValue(EntityDef d,int i){switch(i){case 0:return d.name;case 1:return EntityDef.KIND_NAMES[d.kind];case 2:{CustomSprite cs=project.sprite(d.customSprite);return cs!=null?cs.name+" (custom)":Sprites.NAMES[Math.max(0,Math.min(Sprites.COUNT-1,d.sprite))];}
        case 3:return String.valueOf(d.health);case 4:return fmt(d.speed);case 5:return fmt(d.radius);case 6:return fmt(d.height);case 7:return d.solid?"Yes":"No";case 8:return d.shootable?"Yes":"No";default:{CustomSprite cs=project.sprite(d.customSprite);return cs==null?"Built-in poses":cs.clips.size()+" clips";}}}
    private void drawPropertyPanel(Canvas c,float x0,float l,float rr){
        EntityDef d=currentDef();if(d==null)return;
        float top=toolbarH,bottom=h;
        int save=c.save();c.clipRect(x0+dp(1),top,w,bottom);
        float y=top+dp(12)-propScroll;
        // header
        ui.round(c,l,y,l+dp(52),y+dp(52),dp(8),Ui.BG);
        Bitmap b=defThumb(d);float th=dp(44),tw=th*b.getWidth()/b.getHeight();if(tw>dp(46)){tw=dp(46);th=tw*b.getHeight()/b.getWidth();}
        r.set(l+dp(26)-tw*.5f,y+dp(26)-th*.5f,l+dp(26)+tw*.5f,y+dp(26)+th*.5f);c.drawBitmap(b,null,r,pixelPaint);
        ui.text(c,ui.ellipsize(d.name,14,1,rr-l-dp(62)),l+dp(62),y+dp(22),14,Ui.TEXT,1,-1);
        ui.text(c,EntityDef.KIND_NAMES[d.kind]+" · "+d.fsm.nodes.size()+" states",l+dp(62),y+dp(40),11,Ui.TEXT_3);
        y+=dp(62);
        float hw=(rr-l-dp(6))/2f;
        r.set(l,y,l+hw,y+dp(34));ui.button(c,r,Icons.GRAPH,"Edit FSM",Ui.PRIMARY);clipHit(r,H_EDITFSM,top,bottom);
        r.set(l+hw+dp(6),y,rr,y+dp(34));ui.button(c,r,Icons.PIXEL,"Sprite",Ui.NORMAL);clipHit(r,H_EDITSPRITE,top,bottom);y+=dp(44);
        ui.caption(c,"Place with cursor",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
        float tw2=(rr-l)/5f;
        for(int i=0;i<5;i++){r.set(l+i*tw2+dp(1),y,l+(i+1)*tw2-dp(1),y+dp(34));ui.button(c,r,ENTITY_TOOL_ICONS[i],null,entityTool==i?Ui.ACTIVE:i==4?Ui.DANGER:Ui.NORMAL);clipHit(r,H_ETOOL+i,top,bottom);}
        y+=dp(38);ui.text(c,ENTITY_TOOLS[entityTool]+(entityTool==0?" "+d.name:" entity"),l,y+dp(10),11,Ui.TEXT_2);y+=dp(22);
        ui.caption(c,"Properties",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
        String[] labels=propLabels(d);
        for(int i=0;i<labels.length;i++){
            r.set(l,y,rr,y+dp(32));ui.round(c,r,dp(6),i%2==0?Ui.SURFACE_2:0x00000000);
            ui.textMid(c,labels[i],l+dp(10),y+dp(16),12,Ui.TEXT_2,0,-1);
            String v=propValue(d,i);boolean toggle=i==7||i==8;
            if(toggle){float tx=rr-dp(42),ty=y+dp(8);boolean on=v.equals("Yes");ui.round(c,tx,ty,tx+dp(32),ty+dp(16),dp(8),on?Ui.ACCENT:Ui.SURFACE_3);p.setColor(0xffffffff);c.drawCircle(on?tx+dp(24):tx+dp(8),ty+dp(8),dp(6),p);}
            else ui.textMid(c,ui.ellipsize(v,12,1,(rr-l)*.5f),rr-dp(10),y+dp(16),12,Ui.TEXT,1,1);
            clipHit(r,H_PROP+i,top,bottom);y+=dp(34);
        }
        y+=dp(6);
        float half=(rr-l-dp(6))/2f;
        r.set(l,y,l+half,y+dp(32));ui.button(c,r,Icons.COPY,"Duplicate",Ui.NORMAL);clipHit(r,H_DUPDEF,top,bottom);
        r.set(l+half+dp(6),y,rr,y+dp(32));ui.button(c,r,Icons.TRASH,"Delete",d.kind==EntityDef.PLAYER?Ui.DISABLED:Ui.DANGER);clipHit(r,H_DELDEF,top,bottom);
        y+=dp(44);
        if(selThing>=0&&selThing<level.things.size()){Level.Thing t=level.things.get(selThing);ui.caption(c,"Selected entity",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
            EntityDef td=project.def(t.def);ui.text(c,(td==null?t.def:td.name)+" at "+fmt(t.x)+", "+fmt(t.y)+" · "+Math.round(Math.toDegrees(t.angle))+"°",l,y+dp(10),11.5f,Ui.TEXT_2);y+=dp(24);}
        else if(selThing==-2){ui.caption(c,"Selected",l,y+dp(8),Ui.TEXT_3);y+=dp(16);ui.text(c,"Player start at "+fmt(level.playerX)+", "+fmt(level.playerY),l,y+dp(10),11.5f,Ui.TEXT_2);y+=dp(24);}
        float content=y+propScroll-top+dp(10);
        c.restoreToCount(save);
        float maxScroll=Math.max(0,content-(bottom-top));propScroll=Math.max(0,Math.min(maxScroll,propScroll));
    }
    private void clipHit(RectF rr,int id,float top,float bottom){float t=Math.max(top,rr.top),b=Math.min(bottom,rr.bottom);if(b>t)ui.hit(rr.left,t,rr.right,b,id);}

    private int dpadHit(float x,float y){
        float dx=x-dpadCX(),dy=y-dpadCY(),d=(float)Math.hypot(dx,dy);
        if(d>dpadR()*1.1f||d<dpadR()*.2f)return -1;
        return Math.abs(dx)>Math.abs(dy)?(dx>0?1:3):(dy>0?2:0);
    }
    private void drawCursorControls(Canvas c){
        float cx=dpadCX(),cy=dpadCY(),R=dpadR();
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);c.drawCircle(cx,cy+dp(3),R,p);p.setColor(0xe6252a31);c.drawCircle(cx,cy,R,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x24ffffff);c.drawCircle(cx,cy,R,p);c.drawCircle(cx,cy,R*.34f,p);p.setStyle(Paint.Style.FILL);
        if(dpadDirection>=0){p.setColor(0x403d8bfd);r.set(cx-R,cy-R,cx+R,cy+R);c.drawArc(r,new float[]{225,315,45,135}[dpadDirection],90,true,p);p.setColor(0xe6252a31);c.drawCircle(cx,cy,R*.34f,p);}
        int[] icons={Icons.CHEVRON_UP,Icons.CHEVRON_RIGHT,Icons.CHEVRON_DOWN,Icons.CHEVRON_LEFT};
        for(int i=0;i<4;i++){float ox=i==1?1:i==3?-1:0,oy=i==2?1:i==0?-1:0;ui.icon(c,icons[i],cx+ox*R*.67f,cy+oy*R*.67f,22,i==dpadDirection?0xff8fbcff:Ui.TEXT);}
        p.setColor(0x33ffffff);c.drawCircle(cx,cy,dp(3),p);
        float ax=applyCX(),ay=applyCY(),ar=applyR();
        p.setColor(0x66000000);c.drawCircle(ax,ay+dp(3),ar,p);p.setColor(Ui.ACCENT);c.drawCircle(ax,ay,ar,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x33ffffff);c.drawCircle(ax,ay,ar-dp(.5f),p);p.setStyle(Paint.Style.FILL);
        ui.icon(c,Icons.CHECK,ax,ay,26,0xffffffff);ui.hit(ax-ar-dp(6),ay-ar-dp(6),ax+ar+dp(6),ay+ar+dp(6),H_APPLY);
        String action=applyLabel();float tw=ui.measure(action,11.5f,1)+dp(22),pt=ay-ar-dp(40);
        ui.pill(c,ax+ar-tw,pt,ax+ar,pt+dp(26),0xf2262c34);ui.textMid(c,action,ax+ar-tw*.5f,pt+dp(13),11.5f,Ui.TEXT,1,0);
    }
    private String applyLabel(){
        if(mode==THINGS){EntityDef d=currentDef();switch(entityTool){case 0:return "Place "+(d==null?"":d.name);case 1:return "Select entity";case 2:return entStage==0?"Pick entity":"Drop here";case 3:return "Rotate 45°";default:return "Delete entity";}}
        switch(tool){
            case DRAW:return pending.isEmpty()?"Add first corner":"Add corner · "+pending.size();
            case RECT:return stage==0?"First corner":"Opposite corner";
            case PREFAB:return "Place 4×4 room";case STAIRS:return "Make stairs";case SELECT:return "Select";
            case MOVE:return stage==0?"Pick to move":"Drop here";case SPLIT:return "Split edge here";
            case EXTRUDE:return stage==0?"Pick edge":"Extrude "+(pickSector>=0&&edgeCoords(pickSector,pickEdge)!=null?fmt(extrudeDistance(edgeCoords(pickSector,pickEdge))):"");
            case DUPLICATE:return stage==0?"Pick sector":"Place copy";case DELETE:return "Delete sector";case SPECIAL:return "Set special";case START:return "Set player start";
            default:return "Apply";
        }
    }

    // ---------------------------------------------------------------- geometry queries
    private int sectorUnderCursor(){return sectorNear(cursorX,cursorY);}
    private int sectorNear(float x,float y){int s=level.sectorAt(x,y);if(s>=0)return s;float[][] off={{.15f,.15f},{-.15f,.15f},{.15f,-.15f},{-.15f,-.15f}};for(float[] o:off){s=level.sectorAt(x+o[0],y+o[1]);if(s>=0)return s;}return -1;}
    private int targetSector(){if((selKind==1||selKind==2)&&selSector>=0&&selSector<level.sectors.size())return selSector;return sectorUnderCursor();}
    private int vertexNear(float x,float y,float max){int best=-1;float bd=max;for(int i=0;i<level.vertices.size();i++){Level.Vertex v=level.vertices.get(i);float d=(float)Math.hypot(v.x-x,v.y-y);if(d<bd){bd=d;best=i;}}return best;}
    /** Nearest edge as {sector, edgeIndex}, preferring the sector under the point. */
    private int[] edgeNear(float x,float y,float max){
        int bs=-1,be=-1;float bd=max;int under=sectorNear(x,y);
        for(int s=0;s<level.sectors.size();s++){Level.Sector sec=level.sectors.get(s);
            for(int j=0;j<sec.loop.size();j++){Level.Vertex a=level.vertices.get(sec.loop.get(j)),b=level.vertices.get(sec.loop.get((j+1)%sec.loop.size()));
                float d=World.segDist(x,y,a.x,a.y,b.x,b.y)-(s==under?.001f:0);if(d<bd){bd=d;bs=s;be=j;}}}
        return bs<0?null:new int[]{bs,be};
    }
    private float[] edgeCoords(int s,int e){if(s<0||s>=level.sectors.size())return null;Level.Sector sec=level.sectors.get(s);if(e<0||e>=sec.loop.size())return null;
        Level.Vertex a=level.vertices.get(sec.loop.get(e)),b=level.vertices.get(sec.loop.get((e+1)%sec.loop.size()));return new float[]{a.x,a.y,b.x,b.y};}
    private float[] edgeNormal(int s,int e){float[] c=edgeCoords(s,e);Level.Sector sec=level.sectors.get(s);float area=0;
        for(int i=0;i<sec.loop.size();i++){Level.Vertex a=level.vertices.get(sec.loop.get(i)),b=level.vertices.get(sec.loop.get((i+1)%sec.loop.size()));area+=a.x*b.y-a.y*b.x;}
        float dx=c[2]-c[0],dy=c[3]-c[1],len=(float)Math.hypot(dx,dy),sign=area>0?1:-1;return new float[]{sign*dy/len,-sign*dx/len};}
    private float extrudeDistance(float[] e){float[] n=edgeNormal(pickSector,pickEdge);float d=(cursorX-e[0])*n[0]+(cursorY-e[1])*n[1];return Math.max(1,Math.round(Math.abs(d)));}

    // ---------------------------------------------------------------- Sectors: cursor tools
    private void setTool(int t){cancelStage();tool=t;
        switch(t){case DRAW:hint("Move the cursor and Apply on each corner");break;case RECT:hint("Apply on one corner, then the opposite corner");break;
            case PREFAB:hint("Apply to place a 4×4 room at the cursor");break;case STAIRS:hint("Apply on a rectangular sector; stairs rise away from the cursor");break;
            case SELECT:hint("Apply selects the vertex, edge or sector under the cursor");break;case MOVE:hint("Apply to pick a vertex, edge or sector, move, then Apply to drop");break;
            case SPLIT:hint("Put the cursor on an edge and Apply to split it");break;case EXTRUDE:hint("Apply on an edge, move outwards, Apply again");break;
            case DUPLICATE:hint("Apply on a sector, move, Apply to place the copy");break;case DELETE:hint("Apply on a sector to delete it");break;
            case SPECIAL:hint("Apply on a sector to make it a door, lift, exit…");break;case START:hint("Apply to move the player start; Apply again to turn it");break;}}
    private void cancelStage(){
        if(stage==1&&tool==MOVE)restoreMove();
        stage=0;pickSector=-1;pickEdge=-1;moveIds=new int[0];
    }
    private void restoreMove(){for(int i=0;i<moveIds.length;i++){if(moveIds[i]>=level.vertices.size())continue;Level.Vertex v=level.vertices.get(moveIds[i]);v.x=moveOrig[i*2];v.y=moveOrig[i*2+1];}}
    private void previewMove(){float dx=cursorX-anchorX,dy=cursorY-anchorY;for(int i=0;i<moveIds.length;i++){if(moveIds[i]>=level.vertices.size())continue;Level.Vertex v=level.vertices.get(moveIds[i]);v.x=moveOrig[i*2]+dx;v.y=moveOrig[i*2+1]+dy;}}
    private void cursorApply(){
        if(mode==THINGS){entityApply();return;}
        float cx=cursorX,cy=cursorY;
        switch(tool){
            case DRAW:{
                float[] pt=sectorEditor.snapPoint(cx,cy,1f);
                if(pending.size()>=3&&Math.hypot(pt[0]-pending.get(0)[0],pt[1]-pending.get(0)[1])<.01){closeSector();return;}
                for(float[] v:pending)if(Math.hypot(v[0]-pt[0],v[1]-pt[1])<.01){hint("Corner already added");return;}
                pending.add(pt);hint(pending.size()<3?"Corner "+pending.size()+" added":"Corner "+pending.size()+" added · Apply on the first corner or tap Close");return;}
            case RECT:
                if(stage==0){anchorX=cx;anchorY=cy;stage=1;hint("Move to the opposite corner, then Apply");}
                else{stage=0;if(sectorEditor.rectangle(Math.round(anchorX),Math.round(anchorY),Math.round(cx),Math.round(cy))){selectSector(level.sectors.size()-1);changed("Rectangle created");}else hint(sectorEditor.error);}
                return;
            case PREFAB:
                if(sectorEditor.rectangle(Math.round(cx),Math.round(cy),Math.round(cx)+4,Math.round(cy)+4)){selectSector(level.sectors.size()-1);changed("Room created");}else hint(sectorEditor.error);return;
            case STAIRS:{int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}
                float[] ce=centroid(level.sectors.get(s));if(sectorEditor.makeStairs(s,cx+(cx-ce[0])*.01f,cy+(cy-ce[1])*.01f,.25f)){selKind=0;changed("Stairs built");}else hint(sectorEditor.error);return;}
            case SELECT:{
                int v=vertexNear(cx,cy,.3f);if(v>=0){selKind=3;selVertex=v;hint("Vertex "+(v+1)+" selected");return;}
                int[] e=edgeNear(cx,cy,.3f);if(e!=null&&level.sectorAt(cx,cy)<0||e!=null&&World.segDist(cx,cy,edgeCoords(e[0],e[1])[0],edgeCoords(e[0],e[1])[1],edgeCoords(e[0],e[1])[2],edgeCoords(e[0],e[1])[3])<.05f){selKind=2;selSector=e[0];selEdge=e[1];hint("Edge selected");return;}
                int s=sectorUnderCursor();if(s>=0){selectSector(s);hint("Sector "+(s+1)+" selected");}else{selKind=0;hint("Nothing under the cursor");}return;}
            case MOVE:{
                if(stage==1){int[] ids=moveIds;float dx=cursorX-anchorX,dy=cursorY-anchorY;restoreMove();stage=0;
                    if(dx==0&&dy==0){hint("Move cancelled");return;}
                    if(sectorEditor.moveVertices(ids,dx,dy))changed("Moved");else hint(sectorEditor.error.isEmpty()?"Can't move there":sectorEditor.error);return;}
                int v=vertexNear(cx,cy,.3f);int[] ids;
                if(v>=0){ids=new int[]{v};selKind=3;selVertex=v;}
                else{int[] e=edgeNear(cx,cy,.25f);
                    if(e!=null&&World.segDist(cx,cy,edgeCoords(e[0],e[1])[0],edgeCoords(e[0],e[1])[1],edgeCoords(e[0],e[1])[2],edgeCoords(e[0],e[1])[3])<.05f){Level.Sector sec=level.sectors.get(e[0]);ids=new int[]{sec.loop.get(e[1]),sec.loop.get((e[1]+1)%sec.loop.size())};pickSector=e[0];pickEdge=e[1];}
                    else{int s=sectorUnderCursor();if(s<0){hint("Nothing to move here");return;}Level.Sector sec=level.sectors.get(s);ids=new int[sec.loop.size()];for(int i=0;i<ids.length;i++)ids[i]=sec.loop.get(i);selectSector(s);}}
                moveIds=ids;moveOrig=new float[ids.length*2];for(int i=0;i<ids.length;i++){Level.Vertex vv=level.vertices.get(ids[i]);moveOrig[i*2]=vv.x;moveOrig[i*2+1]=vv.y;}
                anchorX=cx;anchorY=cy;stage=1;hint("Move the cursor, then Apply to drop");return;}
            case SPLIT:{int[] e=edgeNear(cx,cy,.05f);if(e==null){hint("Put the cursor exactly on an edge");return;}
                if(sectorEditor.splitWall(e[0],e[1],cx,cy))changed("Edge split");else hint(sectorEditor.error);return;}
            case EXTRUDE:
                if(stage==0){int[] e=edgeNear(cx,cy,.3f);if(e==null){hint("Move the cursor onto an edge");return;}pickSector=e[0];pickEdge=e[1];stage=1;hint("Move outwards, then Apply");}
                else{int d=Math.round(extrudeDistance(edgeCoords(pickSector,pickEdge)));stage=0;if(sectorEditor.extrude(pickSector,pickEdge,d)){selectSector(level.sectors.size()-1);changed("Extruded");}else hint(sectorEditor.error.isEmpty()?"Can't extrude there":sectorEditor.error);pickSector=-1;}
                return;
            case DUPLICATE:
                if(stage==0){int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}pickSector=s;anchorX=cx;anchorY=cy;stage=1;hint("Move to the new place, then Apply");}
                else{stage=0;if(sectorEditor.duplicateSector(pickSector,cx-anchorX,cy-anchorY)){selectSector(level.sectors.size()-1);changed("Sector duplicated");}else hint(sectorEditor.error.isEmpty()?"Copy would overlap":sectorEditor.error);pickSector=-1;}
                return;
            case DELETE:{int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}if(sectorEditor.deleteSector(s)){selKind=0;changed("Sector deleted");}return;}
            case SPECIAL:{int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}selectSector(s);chooseSpecial(s);return;}
            case START:{if(level.sectorAt(cx,cy)<0&&sectorNear(cx,cy)<0){hint("The player start must be inside a sector");return;}
                float px=cx+.5f*(level.sectorAt(cx+.5f,cy+.5f)>=0?1:0),py=cy+.5f*(level.sectorAt(cx+.5f,cy+.5f)>=0?1:0);
                if(Math.hypot(level.playerX-px,level.playerY-py)<.01f){level.facing+=(float)Math.PI/4;changed("Player start turned");}else{level.playerX=px;level.playerY=py;changed("Player start moved");}return;}
        }
    }
    private void selectSector(int s){selKind=s>=0?1:0;selSector=s;}
    private void chooseSpecial(int s){
        if(host==null){Level.Sector sec=level.sectors.get(s);sec.special=(sec.special+1)%Level.SPECIAL_NAMES.length;changed(Level.SPECIAL_NAMES[sec.special]);return;}
        Level.Sector sec=level.sectors.get(s);
        host.choose("Sector "+(s+1)+" special",Level.SPECIAL_NAMES,sec.special,i->{sec.special=i;
            if(i==Level.SP_DOOR){host.choose("Door needs a key?",Level.KEY_NAMES,sec.tag,k->{sec.tag=k;changed("Door"+(k>0?" ("+Level.KEY_NAMES[k]+")":""));});}
            else{sec.tag=0;changed(Level.SPECIAL_NAMES[i]);}});
    }
    private void changed(String msg){autoSave();if(msg!=null)hint(msg);renderer.invalidate();invalidate();}
    private void closeSector(){if(sectorEditor.addSector(pending)){selectSector(level.sectors.size()-1);pending.clear();changed("Sector created");}else hint(sectorEditor.error);}
    private void sectorAction(int t){
        if(t<100){if(tool==t&&stage==0&&t!=DRAW)hint(toolName(t)+" is active");setTool(t);return;}
        switch(t){
            case CLOSE:if(pending.size()<3)hint("Add at least three corners first");else closeSector();return;
            case UNDO_PT:if(!pending.isEmpty()){pending.remove(pending.size()-1);hint("Corner removed");}else hint("No corners to remove");return;
            case CANCEL:pending.clear();cancelStage();hint("Cancelled");return;
            case UNDO:cancelStage();if(sectorEditor.undo()){restoreEditor();hint("Undo");}else hint("Nothing to undo");return;
            case REDO:cancelStage();if(sectorEditor.redo()){restoreEditor();hint("Redo");}else hint("Nothing to redo");return;
            case VALIDATE:{ArrayList<String> issues=MapValidator.check(level);hint(issues.isEmpty()?"Map validation: no issues":issues.size()+" issue(s): "+issues.get(0));return;}
            case CLEAN:{int n=sectorEditor.cleanOrphans();if(n>0)changed(null);hint("Cleaned "+n+" unused vertices");return;}
            case DESELECT:selKind=0;hint("Selection cleared");return;
            case GO3D:{int s=targetSector();changeMode(POLY3D);if(s>=0)sel3d=Renderer.id(Renderer.ID_FLOOR,s);return;}
        }
        int s=targetSector();
        if(s<0){hint("Move the cursor onto a sector, or select one");return;}
        Level.Sector sec=level.sectors.get(s);boolean ok=true;
        switch(t){
            case FLOOR_UP:ok=sectorEditor.changeHeight(s,.25f,0);break;case FLOOR_DN:ok=sectorEditor.changeHeight(s,-.25f,0);break;
            case CEIL_UP:ok=sectorEditor.changeHeight(s,0,.25f);break;case CEIL_DN:ok=sectorEditor.changeHeight(s,0,-.25f);break;
            case LIGHT_UP:sec.light=Math.min(255,sec.light+16);break;case LIGHT_DN:sec.light=Math.max(16,sec.light-16);break;
            case SKY:sectorEditor.setMaterial(s,2,sec.ceilingMaterial==Textures.SKY?2:Textures.SKY);break;
            case FLOOR_TEX:case CEIL_TEX:case WALL_TEX:{int surf=t==FLOOR_TEX?1:t==CEIL_TEX?2:0;int cur=surf==1?sec.floorMaterial:surf==2?sec.ceilingMaterial:sec.wallMaterial;
                if(host!=null){host.choose(toolName(t)+" · sector "+(s+1),Textures.NAMES,cur,i->{if(i==Textures.SKY&&surf!=2){hint("Sky only works on ceilings");return;}sectorEditor.setMaterial(s,surf,i);changed(Textures.NAMES[i]);});return;}
                {int nx=(cur+1)%Textures.COUNT;if(nx==Textures.SKY)nx++;sectorEditor.setMaterial(s,surf,nx%Textures.COUNT);}break;}
        }
        if(ok)changed(toolName(t)+" · sector "+(s+1)+": floor "+fmt(sec.floor)+", ceiling "+fmt(sec.ceiling)+", light "+sec.light);else hint(sectorEditor.error);
    }
    private void restoreEditor(){project.level=sectorEditor.level;level=project.level;selKind=0;pending.clear();walker=null;autoSave();renderer.invalidate();invalidate();}

    // ---------------------------------------------------------------- Entities: cursor tools
    private int thingNear(float x,float y,float max){int best=-1;float bd=max;for(int i=0;i<level.things.size();i++){Level.Thing t=level.things.get(i);float d=(float)Math.hypot(t.x-x,t.y-y);if(d<bd){bd=d;best=i;}}
        if(Math.hypot(level.playerX-x,level.playerY-y)<Math.min(bd,max))return -2;return best;}
    private void entityApply(){
        float cx=cursorX,cy=cursorY;EntityDef d=currentDef();
        switch(entityTool){
            case 0:{if(d==null)return;if(level.sectorAt(cx,cy)<0){hint("Place entities inside a sector");return;}
                if(d.kind==EntityDef.PLAYER){level.playerX=cx;level.playerY=cy;selThing=-2;changed("Player start moved");return;}
                for(Level.Thing t:level.things)if(Math.hypot(t.x-cx,t.y-cy)<.3f){hint("Another entity is already here");return;}
                Level.Thing t=new Level.Thing(cx,cy,d.id);level.things.add(t);selThing=level.things.size()-1;changed(d.name+" placed");return;}
            case 1:{int i=thingNear(cx,cy,.6f);selThing=i==-1?-1:i;if(i>=0){selDef=level.things.get(i).def;hint(project.def(selDef)!=null?project.def(selDef).name+" selected":"Selected");}else if(i==-2){selDef=project.playerDef().id;hint("Player start selected");}else hint("No entity under the cursor");return;}
            case 2:{
                if(entStage==0){int i=thingNear(cx,cy,.6f);if(i==-1){hint("No entity under the cursor");return;}selThing=i;entStage=1;hint("Move the cursor, then Apply to drop");return;}
                entStage=0;if(level.sectorAt(cx,cy)<0){hint("Entities must be inside a sector");return;}
                if(selThing==-2){level.playerX=cx;level.playerY=cy;}else if(selThing>=0&&selThing<level.things.size()){Level.Thing t=level.things.get(selThing);t.x=cx;t.y=cy;}
                changed("Entity moved");return;}
            case 3:{int i=thingNear(cx,cy,.6f);if(i==-1){hint("No entity under the cursor");return;}selThing=i;
                if(i==-2)level.facing+=(float)Math.PI/4;else level.things.get(i).angle+=(float)Math.PI/4;changed("Turned 45°");return;}
            default:{int i=thingNear(cx,cy,.6f);if(i<0){hint(i==-2?"The player start can't be deleted":"No entity under the cursor");return;}
                level.things.remove(i);selThing=-1;changed("Entity deleted");}
        }
    }
    private void propertyTap(int i){
        EntityDef d=currentDef();if(d==null)return;
        if(i==7){d.solid=!d.solid;changed(null);return;}
        if(i==8){d.shootable=!d.shootable;changed(null);return;}
        if(i==9){openSprites(d.id);return;}
        if(host==null)return;
        switch(i){
            case 0:host.prompt("Entity name",null,d.name,false,t->{if(!t.trim().isEmpty()){d.name=t.trim();changed(null);}});break;
            case 1:if(d.kind==EntityDef.PLAYER){hint("The player type can't be changed");return;}
                String[] kinds={"Monster","Pickup","Decoration","Trigger","NPC"};host.choose("Type",kinds,Math.max(0,d.kind-1),k->{d.kind=k+1;if(d.kind==EntityDef.MONSTER){d.countKill=true;d.shootable=true;}if(d.kind==EntityDef.PICKUP){d.countItem=true;d.solid=false;}changed(null);});break;
            case 2:{String[] items=new String[Sprites.COUNT+project.sprites.size()+1];
                for(int k=0;k<project.sprites.size();k++)items[k]="Custom: "+project.sprites.get(k).name;
                for(int k=0;k<Sprites.COUNT;k++)items[project.sprites.size()+k]="Built-in: "+Sprites.NAMES[k];
                items[items.length-1]="Draw a new sprite…";
                CustomSprite cur=project.sprite(d.customSprite);int checked=cur!=null?project.sprites.indexOf(cur):project.sprites.size()+d.sprite;
                host.choose("Sprite for "+d.name,items,checked,k->{
                    if(k<project.sprites.size())d.customSprite=project.sprites.get(k).id;
                    else if(k<items.length-1){d.customSprite="";d.sprite=k-project.sprites.size();}
                    else{openSprites(d.id);return;}
                    changed(null);});break;}
            case 3:host.prompt("Health",null,String.valueOf(d.health),true,t->{try{d.health=Math.max(1,Math.round(Float.parseFloat(t)));changed(null);}catch(NumberFormatException ignored){}});break;
            case 4:host.prompt("Speed (units per second)",null,fmt(d.speed),true,t->{try{d.speed=Math.max(0,Float.parseFloat(t));changed(null);}catch(NumberFormatException ignored){}});break;
            case 5:host.prompt("Radius",null,fmt(d.radius),true,t->{try{d.radius=Math.max(.1f,Math.min(2,Float.parseFloat(t)));changed(null);}catch(NumberFormatException ignored){}});break;
            case 6:host.prompt("Height",null,fmt(d.height),true,t->{try{d.height=Math.max(.2f,Math.min(4,Float.parseFloat(t)));changed(null);}catch(NumberFormatException ignored){}});break;
        }
    }
    private void newEntityType(){
        if(host==null){EntityDef d=DefaultContent.fromTemplate(0,project.uniqueId("monster"),"New Monster");project.defs.add(d);selDef=d.id;changed("Entity type created");return;}
        host.choose("New entity from template",DefaultContent.TEMPLATES,0,t->host.prompt("Name",null,DefaultContent.TEMPLATES[t].replaceAll(" \\(.*\\)","").replace(" / trigger",""),false,name->{
            String n=name.trim().isEmpty()?"Entity":name.trim();EntityDef d=DefaultContent.fromTemplate(t,project.uniqueId(n),n);project.defs.add(d);selDef=d.id;entityTool=0;
            defScroll=1e9f;changed(n+" created · Apply to place it");}));
    }
    private void deleteEntityType(){
        EntityDef d=currentDef();if(d==null||d.kind==EntityDef.PLAYER){hint("The player type can't be deleted");return;}
        int placed=project.countPlaced(d.id);
        Runnable go=()->{project.defs.remove(d);level.things.removeIf(t->d.id.equals(t.def));selDef=project.defs.size()>1?project.defs.get(1).id:project.defs.get(0).id;selThing=-1;changed(d.name+" deleted");};
        if(host!=null)host.confirm("Delete "+d.name+"?",placed>0?placed+" placed in the level will be removed too.":"This entity type and its FSM will be removed.","Delete",go);else go.run();
    }

    // ---------------------------------------------------------------- 3D view
    private void ensureWalker(){
        if(walker==null||walker.level!=project.level){
            float x=level.playerX,y=level.playerY,a=level.facing,pt=0;
            if(walker!=null){x=walker.player.x;y=walker.player.y;a=walker.player.angle;pt=walker.player.pitch;}
            walker=World.walker(project);walker.player.x=x;walker.player.y=y;walker.player.angle=a;walker.player.pitch=pt;
            walker.player.z=walker.floorAt(x,y);walker.viewZ=walker.player.z+World.EYE;
        }
    }
    private void draw3D(Canvas c,float dt){
        ensureWalker();Actor cam=walker.player;
        cam.noGravity=fly;
        float spd=fly?6f:5f;
        float cs=(float)Math.cos(cam.angle),sn=(float)Math.sin(cam.angle),fw=-moveY,st=moveX;
        cam.wishX=(cs*fw-sn*st)*spd;cam.wishY=(sn*fw+cs*st)*spd;cam.wishActive=joyVisible;
        if(fly)cam.vz=flyUp?4f:flyDown?-4f:0;
        walker.walkerTick(dt);
        float left=browserW();view3d.set(left,toolbarH,w,h);
        renderer.highlight=sel3d;
        spriteList.clear();int pool=0;
        for(int i=0;i<level.things.size();i++){Level.Thing t=level.things.get(i);EntityDef d=project.def(t.def);if(d==null)continue;
            spriteList.add(sd(pool++).set(t.x,t.y,floorAtLevel(t.x,t.y),project.spriteFor(d,d.sprite,Sprites.F_IDLE,walker.time,0,World.angleDiff((float)Math.atan2(cam.y-t.y,cam.x-t.x),t.angle)),Renderer.id(Renderer.ID_SPRITE,i),false));}
        renderer.render(c,level,cam.x,cam.y,walker.viewZ,cam.angle,cam.pitch,view3d,spriteList);
        // crosshair
        float cx=view3d.centerX(),cy=view3d.centerY();p.setColor(0xccffffff);c.drawCircle(cx,cy,dp(2),p);
        drawTextureBrowser(c,left);
        drawJoystick(c);
        // viewport chips
        float bh=dp(32),top=toolbarH+dp(10),x=w-dp(10);
        float fw2=dp(96);r.set(x-fw2,top,x,top+bh);ui.button(c,r,fly?Icons.LAYERS:Icons.ENTITIES,fly?"Fly":"Walk",fly?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_FLY);x-=fw2+dp(8);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.FLAG,null,Ui.NORMAL);ui.hit(r,H_START3D);
        // Right-hand dock: the sector panel (Raise/Lower/Light) takes the outer column; the fly
        // Up/Down buttons move one column in when it is showing, so the two never overlap.
        int sk=Renderer.kind(sel3d);boolean sectorPanel=sel3d>=0&&(sk==Renderer.ID_FLOOR||sk==Renderer.ID_CEIL||sk==Renderer.ID_WALL);
        dockL=sectorPanel?w-dp(14)-dp(60):w;
        if(fly){float bx=(sectorPanel?dockL-dp(12):w-dp(14))-dp(56),by=h-dp(16)-dp(56);dockL=bx;
            r.set(bx,by-dp(64),bx+dp(56),by-dp(8));ui.stackButton(c,r,Icons.ARROW_UP,"Up",flyUp?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_UP);
            r.set(bx,by,bx+dp(56),by+dp(56));ui.stackButton(c,r,Icons.ARROW_DOWN,"Down",flyDown?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_DOWN);}
        drawSelection3D(c,left);
        if(joyVisible||flyUp||flyDown||Math.abs(cam.vz)>.01f||Math.hypot(cam.vx,cam.vy)>.01f||walker.viewZ!=cam.z+World.EYE)nextFrame();
    }
    private float floorAtLevel(float x,float y){int s=level.sectorAt(x,y);return s<0?0:level.sectors.get(s).floor;}
    private Renderer.SpriteDraw sd(int i){while(spritePool.size()<=i)spritePool.add(new Renderer.SpriteDraw());return spritePool.get(i);}
    private void drawTextureBrowser(Canvas c,float left){
        float pad=dp(10);
        ui.rect(c,0,toolbarH,left,h,Ui.SURFACE);ui.vline(c,left-dp(1),toolbarH,h,Ui.BORDER);
        ui.caption(c,"Textures",pad,toolbarH+dp(24),Ui.TEXT_3);
        float size=(left-pad*2-dp(8))/2f,top=toolbarH+dp(34),bottom=h-dp(44);
        int rows=(Textures.COUNT+1)/2;float content=rows*(size+dp(8));
        float maxScroll=Math.max(0,content-(bottom-top));matScroll=Math.max(0,Math.min(maxScroll,matScroll));
        int save=c.save();c.clipRect(0,top,left-dp(1),bottom);
        for(int i=0;i<Textures.COUNT;i++){int col=i%2,row=i/2;float tx=pad+col*(size+dp(8)),ty=top+row*(size+dp(8))-matScroll;
            if(ty+size<top||ty>bottom)continue;
            int sv=c.save();path.reset();r.set(tx,ty,tx+size,ty+size);path.addRoundRect(r,dp(6),dp(6),Path.Direction.CW);c.clipPath(path);c.drawBitmap(texThumb(i),null,r,pixelPaint);c.restoreToCount(sv);
            boolean on=i==selectedMaterial;ui.outline(c,tx,ty,tx+size,ty+size,dp(6),on?Ui.ACCENT:0x26ffffff,on?dp(2.5f):dp(1));
            if(on){p.setColor(Ui.ACCENT);c.drawCircle(tx+size-dp(9),ty+dp(9),dp(7),p);ui.icon(c,Icons.CHECK,tx+size-dp(9),ty+dp(9),10,0xffffffff);}
            r.set(tx,ty,tx+size,ty+size);clipHit(r,H_MAT+i,top,bottom);}
        c.restoreToCount(save);
        ui.text(c,ui.ellipsize(Textures.NAMES[selectedMaterial],11.5f,1,left-pad*2),pad,h-dp(28),11.5f,Ui.TEXT,1,-1);
        ui.text(c,"Double-tap a surface",pad,h-dp(12),10.5f,Ui.TEXT_3);
    }
    private void drawJoystick(Canvas c){
        float alpha=joyVisible?1f:Math.max(0f,1f-(System.currentTimeMillis()-joyReleaseTime)/240f);if(alpha<=0)return;
        float radius=dp(56);p.setStyle(Paint.Style.FILL);
        p.setColor(((int)(alpha*110)<<24)|0x101318);c.drawCircle(joyOriginX,joyOriginY,radius,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(((int)(alpha*90)<<24)|0xffffff);c.drawCircle(joyOriginX,joyOriginY,radius,p);p.setStyle(Paint.Style.FILL);
        p.setColor(((int)(alpha*235)<<24)|0xe8ecf1);c.drawCircle(joyX,joyY,radius*.4f,p);
        if(!joyVisible)postInvalidateDelayed(32);
    }
    private void moveJoystick(float x,float y){float dx=x-joyOriginX,dy=y-joyOriginY,len=(float)Math.hypot(dx,dy),limit=dp(56)*.68f,scale=len>limit?limit/len:1;
        joyX=joyOriginX+dx*scale;joyY=joyOriginY+dy*scale;moveX=(joyX-joyOriginX)/limit;moveY=(joyY-joyOriginY)/limit;}
    private void drawSelection3D(Canvas c,float left){
        int k=Renderer.kind(sel3d),v=Renderer.value(sel3d);
        if(sel3d<0||k==0)return;
        String t;int icon;int sector=-1;
        if(k==Renderer.ID_SPRITE){if(v>=level.things.size()){sel3d=-1;return;}EntityDef d=project.def(level.things.get(v).def);t=(d==null?"Entity":d.name)+" · "+(d==null?"":EntityDef.KIND_NAMES[d.kind]);icon=Icons.ENTITIES;}
        else if(k==Renderer.ID_WALL){int li=v>>1;if(li>=renderer.lines.size()){sel3d=-1;return;}Level.LineDef l=renderer.lines.get(li);sector=l.front;
            Integer m=level.wallOverrides.get(Renderer.wallKey(level,l,v&1));int mat=m!=null?m:level.sectors.get(l.front).wallMaterial;t=((v&1)==1?"Upper wall":"Wall")+" · "+Textures.NAMES[Math.max(0,Math.min(Textures.COUNT-1,mat))];icon=Icons.WALL_TEX;}
        else{sector=v;if(sector>=level.sectors.size()){sel3d=-1;return;}Level.Sector s=level.sectors.get(sector);boolean fl=k==Renderer.ID_FLOOR;
            t=(fl?"Floor ":"Ceiling ")+fmt(fl?s.floor:s.ceiling)+" · sector "+(sector+1)+" · light "+s.light;icon=fl?Icons.FLOOR_TEX:Icons.CEIL_TEX;}
        float pr=Math.min(w,dockL-dp(10));   // keep the info pill clear of the right-hand buttons
        float maxTw=pr-left-dp(24);
        if(k==Renderer.ID_SPRITE)maxTw-=dp(100);
        t=ui.ellipsize(t,12.5f,1,maxTw-dp(50));
        float tw=ui.measure(t,12.5f,1)+dp(50),cx=(left+pr)*.5f,bt=h-dp(50);
        if(k==Renderer.ID_SPRITE)tw+=dp(100);
        ui.pill(c,cx-tw*.5f,bt,cx+tw*.5f,bt+dp(36),0xf2262c34);
        ui.icon(c,icon,cx-tw*.5f+dp(20),bt+dp(18),16,0xff8fbcff);ui.textMid(c,t,cx-tw*.5f+dp(36),bt+dp(18),12.5f,Ui.TEXT,1,-1);
        if(k==Renderer.ID_SPRITE){r.set(cx+tw*.5f-dp(100),bt+dp(5),cx+tw*.5f-dp(6),bt+dp(31));ui.button(c,r,Icons.GRAPH,"Edit FSM",Ui.PRIMARY);ui.hit(r,H_EDITFSM3D);}
        if(sector>=0){
            // one column under the Fly/Start chips, shrunk to fit the screen height
            int n=k!=Renderer.ID_WALL?4:2;float gap=dp(8),bw=dp(60),bx=w-dp(14)-bw,top=toolbarH+dp(10)+dp(32)+dp(14);
            float bh=Math.max(dp(40),Math.min(dp(54),(h-dp(16)-top-gap*(n-1))/n)),y=top;
            if(k!=Renderer.ID_WALL){r.set(bx,y,bx+bw,y+bh);ui.stackButton(c,r,Icons.ARROW_UP,"Raise",Ui.NORMAL);ui.hit(r,H_RAISE);y+=bh+gap;
                r.set(bx,y,bx+bw,y+bh);ui.stackButton(c,r,Icons.ARROW_DOWN,"Lower",Ui.NORMAL);ui.hit(r,H_LOWER);y+=bh+gap;}
            r.set(bx,y,bx+bw,y+bh);ui.stackButton(c,r,Icons.SUN,"Light +",Ui.NORMAL);ui.hit(r,H_LIGHTUP);y+=bh+gap;
            r.set(bx,y,bx+bw,y+bh);ui.stackButton(c,r,Icons.SUN_DIM,"Light −",Ui.NORMAL);ui.hit(r,H_LIGHTDN);
        }
    }
    private void apply3DTexture(){
        int k=Renderer.kind(sel3d),v=Renderer.value(sel3d);
        if(k==Renderer.ID_FLOOR||k==Renderer.ID_CEIL){if(selectedMaterial==Textures.SKY&&k==Renderer.ID_FLOOR){hint("Sky only works on ceilings");return;}sectorEditor.setMaterial(v,k==Renderer.ID_FLOOR?1:2,selectedMaterial);changed(Textures.NAMES[selectedMaterial]+" applied");}
        else if(k==Renderer.ID_WALL){if(selectedMaterial==Textures.SKY){hint("Sky only works on ceilings");return;}int li=v>>1;if(li<renderer.lines.size()){level.wallOverrides.put(Renderer.wallKey(level,renderer.lines.get(li),v&1),selectedMaterial);changed(Textures.NAMES[selectedMaterial]+" applied");}}
    }
    private void adjust3D(int hid){
        int k=Renderer.kind(sel3d),v=Renderer.value(sel3d);int s=k==Renderer.ID_WALL?(v>>1<renderer.lines.size()?renderer.lines.get(v>>1).front:-1):v;
        if(s<0||s>=level.sectors.size())return;Level.Sector sec=level.sectors.get(s);
        if(hid==H_LIGHTUP||hid==H_LIGHTDN){sec.light=Math.max(16,Math.min(255,sec.light+(hid==H_LIGHTUP?16:-16)));changed("Light "+sec.light);return;}
        float d=hid==H_RAISE?.25f:-.25f;boolean ok=k==Renderer.ID_FLOOR?sectorEditor.changeHeight(s,d,0):sectorEditor.changeHeight(s,0,d);
        if(ok)changed((k==Renderer.ID_FLOOR?"Floor ":"Ceiling ")+fmt(k==Renderer.ID_FLOOR?sec.floor:sec.ceiling));else hint(sectorEditor.error);
    }

    // ---------------------------------------------------------------- Play
    private void startPlay(){bagOpen=false;world=new World(project,sfx);sfx.start();for(int i=0;i<buttonPointer.length;i++)buttonPointer[i]=-1;joyPointer=lookPointer=-1;joyVisible=false;}
    private void drawPlay(Canvas c,float dt){
        if(world==null)startPlay();
        if(world.restartRequested){startPlay();hint("Level restarted");}
        Input in=world.input;
        in.moveX=joyVisible?moveX:0;in.moveY=joyVisible?moveY:0;
        world.tick(dt);
        Actor pl=world.player;
        spriteList.clear();int pool=0;
        boolean tp=world.thirdPerson();
        float cx=world.camX(),cy=world.camY();
        for(Actor a:world.actors){if((a==pl&&!tp)||!a.visible||a.removed)continue;
            Sprites.Sprite s;boolean bright=false;
            if(a.projectileType>=0){s=Sprites.effect(a.projectileType==1?Sprites.FX_ROCKET:a.projectileType==2?Sprites.FX_PLASMA:Sprites.FX_FIREBALL,0,true);bright=true;}
            else{int f=a.def!=null&&a.def.animated()?a.frame():a.sprite==Sprites.TORCH?((int)(world.time*8)&1):a.alive?Sprites.F_IDLE:Sprites.F_DEAD;
                // which side of the actor the camera sees (sprite angles / rear views)
                float view=World.angleDiff((float)Math.atan2(cy-a.y,cx-a.x),a.angle);
                s=project.actorSprite(a,f,world.time,view);}
            spriteList.add(sd(pool++).set(a.x,a.y,a.z+a.bob-(a.projectileType>=0?.2f:0),s,-1,bright));}
        for(World.Effect e:world.effects){int f=Math.min(2,(int)(e.t/e.life*3));spriteList.add(sd(pool++).set(e.x,e.y,e.z-.2f,Sprites.effect(e.type,f,true),-1,e.type!=Sprites.FX_BLOOD));}
        view3d.set(0,0,w,h);
        renderer.highlight=-1;
        renderer.renderScene(world.level,cx,cy,world.camZ(),world.camAngle(),world.camPitch(),w,h,spriteList);
        if(pl.alive&&!tp){float b=world.bob();int si=world.sectorIndex(pl);renderer.weapon(world.weapon,(float)Math.cos(world.bobPhase*Math.PI*.5f)*Math.min(1,(float)Math.hypot(pl.vx,pl.vy)/6f),b*8,world.weaponRaise,world.weaponFlash>0,si<0?160:world.level.sectors.get(si).light);}
        if(world.damageFlash>0)renderer.tint(0xffff2020,world.damageFlash*.6f);
        if(world.pickupFlash>0)renderer.tint(world.flashColor,world.pickupFlash*.35f);
        if(!pl.alive)renderer.tint(0xff800000,.35f);
        renderer.present(c,view3d);
        if(tp&&pl.alive){float mx=w*.5f,my=h*.5f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(0xccffffff);
            c.drawCircle(mx,my,dp(6),p);p.setStyle(Paint.Style.FILL);c.drawCircle(mx,my,dp(1.5f),p);}
        drawFloaters(c);
        drawHud(c);
        if(world.menuRequested){world.menuRequested=false;openBag();}
        if(world.levelComplete)drawComplete(c);
        else if(bagOpen)drawBag(c);
        else if(world.dialogue!=null)drawDialogue(c);
        else{drawJoystick(c);drawPlayButtons(c);}
        nextFrame();
    }
    // ---------------------------------------------------------------- RPG play UI
    private float bagDragY;private boolean bagDragged;
    private boolean bagOpen;private int bagTab;private float bagScroll;private String bagNote="";private long bagNoteUntil;
    private final java.util.HashMap<Integer,Bitmap> itemThumbs=new java.util.HashMap<>();
    private final float[] proj=new float[3];
    private Bitmap thumbFor(int sprite){Bitmap b=itemThumbs.get(sprite);if(b==null){b=Sprites.preview(sprite);itemThumbs.put(sprite,b);}return b;}
    private void openBag(){bagOpen=true;bagTab=0;bagScroll=0;world.paused=true;for(int i=0;i<buttonPointer.length;i++)buttonPointer[i]=-1;world.input.clear();joyPointer=lookPointer=-1;joyVisible=false;moveX=moveY=0;}
    private void closeBag(){bagOpen=false;world.paused=false;}
    private void drawFloaters(Canvas c){
        for(World.Floater f:world.floaters){if(!renderer.project(f.x,f.y,f.z,view3d,proj))continue;
            float a=Math.max(0,Math.min(1,(1.1f-f.t)*2.5f));float sz=Math.max(11,Math.min(22,proj[2]*h/ui.dp(1)*.32f));
            int al=(int)(a*255);ui.text(c,f.text,proj[0]+dp(1),proj[1]+dp(1),sz,(al<<24),2,0);ui.text(c,f.text,proj[0],proj[1],sz,(al<<24)|(f.color&0xffffff),2,0);}
    }
    private void drawPlayButtons(Canvas c){
        Hero hero=world.hero;float bar=dp(52);
        float fr=dp(38),fx=w-dp(24)-fr,fy=h-bar-dp(16)-fr;
        playButton(c,fx,fy,fr,0xffc8402e,Icons.SWORD,"ATTACK",H_FIRE,buttonPointer[Input.FIRE]>=0);
        boolean caster=hero!=null&&!hero.spells.isEmpty();
        if(caster){Rpg.Spell sp=hero.currentSpell();boolean can=hero.sp>=Rpg.spellCost(sp.level);
            playButton(c,fx-fr-dp(38),fy-fr*.15f,dp(28),can?0xdd6a3ab8:0x88403a50,Icons.SPARKLE,"CAST",H_SPELL,buttonPointer[Input.SPELL]>=0);}
        playButton(c,fx-fr*.2f,fy-fr-dp(40),dp(24),0xcc2a3038,Icons.ARROW_UP,"JUMP",H_JUMP,buttonPointer[Input.JUMP]>=0);
        playButton(c,fx-fr-dp(caster?94:40),fy+dp(caster?18:8),dp(25),0xcc2a3038,Icons.DOOR,"USE",H_USE,buttonPointer[Input.USE]>=0);
        int pots=hero==null?0:hero.potionCount();
        playButton(c,fx-fr-dp(caster?62:18),fy-fr-dp(caster?44:30),dp(24),pots>0?0xcc8a2030:0x882a3038,Icons.POTION,pots>0?"x"+pots:"0",H_POTION,buttonPointer[Input.POTION]>=0);
        // bag (top right) and exit (top left)
        float br=dp(22);float bx=w-dp(16)-br,by=dp(16)+br;p.setColor(0xcc2a3038);c.drawCircle(bx,by,br,p);ui.icon(c,Icons.BACKPACK,bx,by,20,0xffffffff);ui.hit(bx-br,by-br,bx+br,by+br,H_BAG);
        float er=dp(18);p.setColor(0x99101318);c.drawCircle(dp(14)+er,dp(14)+er,er,p);ui.icon(c,Icons.CANCEL,dp(14)+er,dp(14)+er,18,0xddffffff);ui.hit(dp(6),dp(6),dp(22)+er*2,dp(22)+er*2,H_EXITPLAY);
    }
    private void playButton(Canvas c,float x,float y,float rad,int col,int icon,String label,int hid,boolean down){
        p.setStyle(Paint.Style.FILL);p.setColor(0x55000000);c.drawCircle(x,y+dp(3),rad,p);p.setColor(down?Ui.brighten(col):col);c.drawCircle(x,y,rad,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x40ffffff);c.drawCircle(x,y,rad-dp(.5f),p);p.setStyle(Paint.Style.FILL);
        ui.icon(c,icon,x,y-(rad>dp(30)?dp(5):dp(3)),rad>dp(30)?24:16,0xffffffff);ui.text(c,label,x,y+rad*.62f,rad>dp(30)?10:8,0xffffffff,2,0);
        ui.hit(x-rad-dp(4),y-rad-dp(4),x+rad+dp(4),y+rad+dp(4),hid);
    }
    /** A labelled bar (HP, SP, XP). */
    private void bar(Canvas c,float l,float t,float r,float b,float frac,int col,String text){
        ui.round(c,l,t,r,b,(b-t)*.5f,0xff0e1013);float f=Math.max(0,Math.min(1,frac));
        if(f>0)ui.round(c,l,t,l+Math.max(b-t,(r-l)*f),b,(b-t)*.5f,col);
        ui.outline(c,l,t,r,b,(b-t)*.5f,0x30ffffff,dp(1));
        if(text!=null)ui.textMid(c,text,(l+r)*.5f,(t+b)*.5f,10,0xffffffff,2,0);
    }
    private void drawHud(Canvas c){
        Hero hero=world.hero;if(hero==null)return;
        float barH=dp(52),top=h-barH;
        ui.rect(c,0,top,w,h,0xf0141619);ui.hline(c,0,w,top,0xff3a3f46);
        float x=dp(12),cy=top+barH*.5f;
        // class badge
        float bs=dp(36);ui.round(c,x,cy-bs*.5f,x+bs,cy+bs*.5f,dp(8),0xff2a3a5a);ui.textMid(c,String.valueOf(hero.level),x+bs*.5f,cy-dp(3),15,0xffffffff,2,0);ui.textMid(c,"LV",x+bs*.5f,cy+dp(11),7.5f,0xffa8b8d8,2,0);
        x+=bs+dp(10);
        float bw=Math.min(dp(170),w*.2f);
        ui.text(c,hero.cls.name.toUpperCase(),x,top+dp(13),9,Ui.TEXT_3,2,-1);
        bar(c,x,top+dp(17),x+bw,top+dp(31),world.player.health/(float)Math.max(1,hero.maxHp),world.player.health*4<=hero.maxHp?0xffe0402a:0xffb8282a,Math.max(0,world.player.health)+" / "+hero.maxHp+" HP");
        if(hero.maxSp>0)bar(c,x,top+dp(34),x+bw,top+dp(44),hero.sp/(float)Math.max(1,hero.maxSp),0xff3a5ad0,hero.sp+" / "+hero.maxSp+" SP");
        else{ui.text(c,"AC "+hero.ac()+(hero.wardTime>0?" (shielded)":""),x,top+dp(43),11,Ui.TEXT_2,2,-1);}
        // XP strip along the top edge of the bar
        float xf=hero.xpFraction();ui.rect(c,0,top-dp(3),w,top,0xff0e1013);ui.rect(c,0,top-dp(3),w*xf,top,0xffd8a830);
        x+=bw+dp(18);
        // weapon + spell
        Rpg.Item wpn=hero.weaponOrFists();
        ui.text(c,"WEAPON",x,top+dp(13),9,Ui.TEXT_3,2,-1);ui.text(c,wpn.name,x,top+dp(29),13,Ui.TEXT,2,-1);
        ui.text(c,wpn.damage+Rpg.signed(hero.damageBonus(wpn))+" "+Rpg.DAMAGE_TYPES[wpn.damageType].toLowerCase()+"  ·  "+Rpg.signed(hero.attackBonus(wpn))+" to hit",x,top+dp(43),10,Ui.TEXT_2,1,-1);
        if(!hero.spells.isEmpty()&&w>dp(720)){float sx=x+Math.max(dp(170),ui.measure(wpn.name,13,2)+dp(20));Rpg.Spell sp=hero.currentSpell();
            ui.text(c,"SPELL",sx,top+dp(13),9,Ui.TEXT_3,2,-1);ui.text(c,sp.name,sx,top+dp(29),13,0xffc8b0ff,2,-1);ui.text(c,(sp.level==0?"cantrip":Rpg.spellCost(sp.level)+" SP")+"  ·  "+sp.dice,sx,top+dp(43),10,Ui.TEXT_2,1,-1);}
        // gold + keys (right)
        float rx=w-dp(14);
        int[] kc={0xffc87a3a,0xffd0d4dc,0xffe8b830};
        for(int k=2;k>=0;k--){boolean has=hero.hasKey(k);float kx=rx-dp(14);ui.icon(c,Icons.TAG,kx,cy,15,has?kc[k]:0x26ffffff);rx-=dp(20);}
        rx-=dp(8);String g=String.valueOf(hero.gold);ui.text(c,g,rx,cy+dp(5),15,0xffffd040,2,1);float gw=ui.measure(g,15,2);ui.icon(c,Icons.COIN,rx-gw-dp(12),cy,16,0xffffd040);
        // active quest tracker
        World.Quest q=null;for(World.Quest qq:world.quests.values())if(qq.state==World.Q_ACTIVE)q=qq;
        if(q!=null){float qw=Math.min(dp(260),w*.32f),qx=w-dp(70)-qw,qy=dp(14);ui.round(c,qx,qy,qx+qw,qy+dp(44),dp(8),0xb0101216);
            ui.icon(c,Icons.SCROLL,qx+dp(16),qy+dp(15),13,0xffd8a830);ui.text(c,ui.ellipsize(q.title,11.5f,2,qw-dp(36)),qx+dp(28),qy+dp(19),11.5f,0xffffe0a0,2,-1);
            ui.text(c,ui.ellipsize(q.text,10,1,qw-dp(20)),qx+dp(10),qy+dp(35),10,Ui.TEXT_2,1,-1);}
        if(world.messageTime>0){float a=Math.min(1,world.messageTime*2);ui.text(c,world.message,dp(62)+dp(1),dp(30)+dp(1),13,((int)(a*200)<<24),1,-1);ui.text(c,world.message,dp(62),dp(30),13,((int)(a*255)<<24)|0xffe2e2,1,-1);}
        if(world.centerTime>0&&!world.center.isEmpty()){float a=Math.min(1,world.centerTime*2);ui.text(c,world.center,w*.5f+dp(2),h*.36f+dp(2),26,((int)(a*200)<<24),2,0);ui.text(c,world.center,w*.5f,h*.36f,26,((int)(a*255)<<24)|0xffe8b0,2,0);}
    }
    /** Word-wraps text to a width. */
    private ArrayList<String> wrap(String text,float size,int weight,float max){
        ArrayList<String> out=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(String word:text.split(" ")){String t=line.length()==0?word:line+" "+word;if(ui.measure(t,size,weight)>max&&line.length()>0){out.add(line.toString());line=new StringBuilder(word);}else{line=new StringBuilder(t);}}
        if(line.length()>0)out.add(line.toString());return out;
    }
    private void drawDialogue(Canvas c){
        World.Dialogue d=world.dialogue;
        ui.rect(c,0,0,w,h,0x66000000);
        float pw=Math.min(w-dp(40),dp(720)),L=(w-pw)*.5f,R=L+pw;
        ArrayList<String> lines=wrap(d.text,14,1,pw-dp(44));
        float ah=dp(40),gap=dp(8),ph=dp(56)+lines.size()*dp(21)+d.choices.length*(ah+gap)+dp(8),B=h-dp(64),T=B-ph;
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(14),0x80000000);ui.round(c,L,T,R,B,dp(14),0xf21a1d22);ui.outline(c,L,T,R,B,dp(14),0xff8a6a3a,dp(1.5f));
        ui.round(c,L+dp(16),T-dp(14),L+dp(16)+ui.measure(d.speaker,13,2)+dp(28),T+dp(14),dp(14),0xff6a4a24);ui.textMid(c,d.speaker,L+dp(30),T,13,0xffffe8c0,2,-1);
        float y=T+dp(36);for(String ln:lines){ui.text(c,ln,L+dp(22),y,14,Ui.TEXT,1,-1);y+=dp(21);}
        y+=dp(6);
        for(int i=0;i<d.choices.length;i++){r.set(L+dp(18),y,R-dp(18),y+ah);ui.round(c,r,dp(10),0xff262a31);ui.outline(c,r,dp(10),0x30ffffff,dp(1));
            ui.textMid(c,(i+1)+".",r.left+dp(16),r.centerY(),13,0xffd8a830,2,-1);ui.textMid(c,ui.ellipsize(d.choices[i],13.5f,1,r.width()-dp(56)),r.left+dp(36),r.centerY(),13.5f,Ui.TEXT,1,-1);
            ui.hit(r,H_DLG+i);y+=ah+gap;}
    }
    /** Character sheet, inventory, spells and quest journal (the game is paused). */
    private void drawBag(Canvas c){
        Hero hero=world.hero;if(hero==null){closeBag();return;}
        ui.rect(c,0,0,w,h,0xff0c0d10);
        float pad=dp(16),L=pad,R=w-pad,T=pad,B=h-dp(52)-pad;
        // header: tabs + close
        String[] tabs={"Inventory","Spells","Journal"};float tx=L;
        ui.text(c,hero.cls.name+" · level "+hero.level,L,T+dp(18),17,Ui.TEXT,2,-1);
        float hx=L+ui.measure(hero.cls.name+" · level "+hero.level,17,2)+dp(24);
        for(int i=0;i<tabs.length;i++){float tw2=ui.measure(tabs[i],12.5f,1)+dp(30);r.set(hx,T,hx+tw2,T+dp(32));ui.button(c,r,-1,tabs[i],bagTab==i?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_INV_TAB+i);hx+=tw2+dp(6);}
        r.set(R-dp(40),T,R,T+dp(32));ui.button(c,r,Icons.CANCEL,null,Ui.GHOST);ui.hit(r,H_INV_CLOSE);
        float top=T+dp(46);
        // left column: character sheet
        float cw=Math.min(dp(250),w*.3f);
        ui.round(c,L,top,L+cw,B,dp(12),0xff181b20);
        float y=top+dp(22),x=L+dp(14);
        ui.text(c,"CHARACTER",x,y,9.5f,Ui.TEXT_3,2,-1);y+=dp(18);
        bar(c,x,y,L+cw-dp(14),y+dp(14),world.player.health/(float)Math.max(1,hero.maxHp),0xffb8282a,world.player.health+" / "+hero.maxHp+" HP");y+=dp(19);
        if(hero.maxSp>0){bar(c,x,y,L+cw-dp(14),y+dp(14),hero.sp/(float)Math.max(1,hero.maxSp),0xff3a5ad0,hero.sp+" / "+hero.maxSp+" SP");y+=dp(19);}
        bar(c,x,y,L+cw-dp(14),y+dp(10),hero.xpFraction(),0xffd8a830,null);y+=dp(22);
        ui.text(c,hero.xp+" / "+Rpg.xpForNext(hero.level)+" XP",x,y,10.5f,Ui.TEXT_2,1,-1);y+=dp(20);
        Rpg.Item wpn=hero.weaponOrFists();
        String[][] rows={{"Armour class",String.valueOf(hero.ac())},{"Attack",Rpg.signed(hero.attackBonus(wpn))+" · "+wpn.damage+Rpg.signed(hero.damageBonus(wpn))},{"Proficiency",Rpg.signed(hero.prof())},{"Gold",String.valueOf(hero.gold)}};
        for(String[] rw:rows){ui.text(c,rw[0],x,y,11.5f,Ui.TEXT_2,1,-1);ui.text(c,rw[1],L+cw-dp(14),y,11.5f,Ui.TEXT,2,1);y+=dp(18);}
        y+=dp(6);float aw=(cw-dp(28))/3f;
        for(int i=0;i<6;i++){float ax=x+(i%3)*aw,ay=y+(i/3)*dp(44);r.set(ax+dp(2),ay,ax+aw-dp(2),ay+dp(40));ui.round(c,r,dp(8),0xff22262c);
            ui.textMid(c,Rpg.ABILITIES[i],r.centerX(),ay+dp(10),8.5f,Ui.TEXT_3,2,0);ui.textMid(c,Rpg.signed(hero.mod(i)),r.centerX(),ay+dp(24),14,Ui.TEXT,2,0);ui.textMid(c,String.valueOf(hero.scores[i]),r.centerX(),ay+dp(35),8.5f,Ui.TEXT_3,1,0);}
        // right: tab content
        float l2=L+cw+dp(12),r2=R;
        ui.round(c,l2,top,r2,B,dp(12),0xff181b20);
        int save=c.save();c.clipRect(l2,top,r2,B);
        float rowH=dp(52),yy=top+dp(10)-bagScroll;
        if(bagTab==0){
            if(hero.inventory.isEmpty())ui.text(c,"Your pack is empty.",l2+dp(16),top+dp(30),13,Ui.TEXT_3,1,-1);
            for(int i=0;i<hero.inventory.size();i++){Hero.Stack st=hero.inventory.get(i);Rpg.Item it=st.item;
                r.set(l2+dp(10),yy,r2-dp(10),yy+rowH-dp(6));boolean eq=hero.isEquipped(it)&&(i==firstIndex(hero,it));
                ui.round(c,r,dp(10),eq?0xff26324a:0xff22262c);if(eq)ui.outline(c,r,dp(10),Ui.ACCENT,dp(1.5f));
                if(it.sprite>=0){Bitmap tb=thumbFor(it.sprite);float th=rowH-dp(16),tw=Math.min(th*1.4f,th*tb.getWidth()/(float)Math.max(1,tb.getHeight()));
                    RectF d=new RectF(r.left+dp(8)+(th*1.4f-tw)*.5f,r.top+dp(5),r.left+dp(8)+(th*1.4f-tw)*.5f+tw,r.top+dp(5)+th);c.drawBitmap(tb,null,d,null);}
                float ix=r.left+dp(8)+(rowH-dp(16))*1.4f+dp(10);
                ui.text(c,it.name+(st.count>1?"  ×"+st.count:""),ix,r.top+dp(19),13,Ui.TEXT,2,-1);
                ui.text(c,ui.ellipsize(Rpg.ITEM_KINDS[it.kind]+" · "+it.stats(),10.5f,1,r.right-ix-dp(90)),ix,r.top+dp(36),10.5f,Ui.TEXT_3,1,-1);
                String act=it.kind==Rpg.I_POTION?"Drink":it.kind==Rpg.I_SCROLL?"Read":it.kind<=Rpg.I_SHIELD?(eq?"Equipped":"Equip"):"";
                if(!act.isEmpty()){float bw2=ui.measure(act,11.5f,1)+dp(24);RectF b2=new RectF(r.right-dp(8)-bw2,r.centerY()-dp(14),r.right-dp(8),r.centerY()+dp(14));ui.button(c,b2,-1,act,eq?Ui.ACTIVE:Ui.NORMAL);}
                if(r.bottom>top&&r.top<B)ui.hit(Math.max(l2,r.left),Math.max(top,r.top),r.right,Math.min(B,r.bottom),H_INV_ITEM+i);
                yy+=rowH;}
        }else if(bagTab==1){
            if(hero.spells.isEmpty())ui.text(c,hero.cls.name+"s don't cast spells.",l2+dp(16),top+dp(30),13,Ui.TEXT_3,1,-1);
            for(int i=0;i<hero.spells.size();i++){Rpg.Spell sp=hero.spells.get(i);boolean sel=i==hero.selectedSpell;
                r.set(l2+dp(10),yy,r2-dp(10),yy+rowH-dp(6));ui.round(c,r,dp(10),sel?0xff2e2448:0xff22262c);if(sel)ui.outline(c,r,dp(10),0xff9a7aff,dp(1.5f));
                ui.icon(c,Icons.SPARKLE,r.left+dp(22),r.centerY(),18,sel?0xffc8b0ff:Ui.TEXT_2);
                ui.text(c,sp.name+"  ·  "+(sp.level==0?"cantrip":"level "+sp.level+" · "+Rpg.spellCost(sp.level)+" SP"),r.left+dp(42),r.top+dp(19),13,Ui.TEXT,2,-1);
                ui.text(c,ui.ellipsize(sp.desc+" ("+sp.dice+" "+Rpg.DAMAGE_TYPES[sp.damageType].toLowerCase()+")",10.5f,1,r.width()-dp(56)),r.left+dp(42),r.top+dp(36),10.5f,Ui.TEXT_3,1,-1);
                ui.hit(r,H_INV_SPELL+i);yy+=rowH;}
            if(!hero.spells.isEmpty())ui.text(c,"Tap a spell to ready it for the CAST button. Save DC "+hero.saveDC()+" · spell attack "+Rpg.signed(hero.spellAttack()),l2+dp(14),yy+dp(16),10.5f,Ui.TEXT_3,1,-1);
        }else{
            if(world.quests.isEmpty())ui.text(c,"No quests yet. Talk to people you meet.",l2+dp(16),top+dp(30),13,Ui.TEXT_3,1,-1);
            for(World.Quest q:world.quests.values()){if(q.state==World.Q_INACTIVE)continue;
                r.set(l2+dp(10),yy,r2-dp(10),yy+rowH-dp(6));ui.round(c,r,dp(10),0xff22262c);
                int col=q.state==World.Q_DONE?Ui.GREEN:q.state==World.Q_FAILED?Ui.RED:0xffd8a830;
                ui.icon(c,q.state==World.Q_DONE?Icons.CHECK:q.state==World.Q_FAILED?Icons.CANCEL:Icons.SCROLL,r.left+dp(22),r.centerY(),16,col);
                ui.text(c,q.title+(q.state==World.Q_DONE?"  (complete)":q.state==World.Q_FAILED?"  (failed)":""),r.left+dp(42),r.top+dp(19),13,Ui.TEXT,2,-1);
                ui.text(c,ui.ellipsize(q.text,10.5f,1,r.width()-dp(56)),r.left+dp(42),r.top+dp(36),10.5f,Ui.TEXT_3,1,-1);yy+=rowH;}
        }
        c.restoreToCount(save);
        if(System.currentTimeMillis()<bagNoteUntil){float nw=ui.measure(bagNote,12.5f,1)+dp(40);ui.pill(c,w*.5f-nw*.5f,B-dp(48),w*.5f+nw*.5f,B-dp(14),0xf2262c34);ui.textMid(c,bagNote,w*.5f,B-dp(31),12.5f,Ui.TEXT,1,0);postInvalidateDelayed(200);}
    }
    private static int firstIndex(Hero h,Rpg.Item it){for(int i=0;i<h.inventory.size();i++)if(h.inventory.get(i).item==it)return i;return -1;}
    private boolean bagTap(int hid){
        Hero hero=world.hero;if(hero==null)return true;
        if(hid==H_INV_CLOSE){closeBag();return true;}
        if(hid>=H_INV_TAB&&hid<H_INV_TAB+3){bagTab=hid-H_INV_TAB;bagScroll=0;return true;}
        if(hid>=H_INV_SPELL&&hid<H_INV_SPELL+50){hero.selectedSpell=hid-H_INV_SPELL;bagNote="Readied "+hero.currentSpell().name;bagNoteUntil=System.currentTimeMillis()+1800;return true;}
        if(hid>=H_INV_ITEM&&hid<H_INV_ITEM+300){int i=hid-H_INV_ITEM;if(i<hero.inventory.size()){Rpg.Item it=hero.inventory.get(i).item;
            boolean wasScroll=it.kind==Rpg.I_SCROLL;bagNote=world.useItem(it);bagNoteUntil=System.currentTimeMillis()+2200;if(wasScroll)closeBag();}return true;}
        return true;
    }
    private void drawComplete(Canvas c){
        ui.rect(c,0,0,w,h,0xcc0a0b0d);
        float cx=w*.5f,y=h*.24f;
        ui.text(c,"QUEST STAGE COMPLETE",cx,y,26,Ui.AMBER,2,0);if(world.hero!=null)ui.text(c,"Level "+world.hero.level+" "+world.hero.cls.name.toLowerCase()+" · "+world.hero.xp+" XP · "+world.hero.gold+" gold",cx,y+dp(22),12,Ui.TEXT_2,1,0);
        String[] rows={"Foes slain","Treasure","Secrets","Time"};
        String[] vals={pct(world.kills,world.totalKills),pct(world.items,world.totalItems),pct(world.secrets,world.totalSecrets),String.format(java.util.Locale.US,"%d:%02d",(int)(world.time-world.completeTime)/60,(int)(world.time-world.completeTime)%60)};
        for(int i=0;i<4;i++){ui.text(c,rows[i],cx-dp(20),y+dp(46)+i*dp(28),16,Ui.TEXT_2,1,1);ui.text(c,vals[i],cx+dp(20),y+dp(46)+i*dp(28),16,Ui.TEXT,2,-1);}
        float by=y+dp(170);
        r.set(cx-dp(170),by,cx-dp(10),by+dp(40));ui.button(c,r,Icons.RESET,"Play again",Ui.PRIMARY);ui.hit(r,H_RETRY);
        r.set(cx+dp(10),by,cx+dp(170),by+dp(40));ui.button(c,r,Icons.ARROW_LEFT,"Back to editor",Ui.NORMAL);ui.hit(r,H_BACK);
    }
    private static String pct(int a,int b){return b==0?"—":Math.round(a*100f/b)+"%";}

    // ---------------------------------------------------------------- save/mode
    void save(boolean showHint){
        flushSave();
        if(showHint){ArrayList<String> errors=MapValidator.check(level);hint(errors.isEmpty()?"Saved · map is valid":"Saved · "+errors.size()+" map warning(s)");}
    }
    /**
     * Edits are saved 1.5 s after the last change instead of after every single one: a save serialises
     * the whole project (including sprite pixels), so rapid edits used to rewrite it many times a second.
     * Leaving the editor, pausing the app or saving by hand always writes immediately.
     */
    private boolean saveQueued;
    private final Runnable saveTask=this::runQueuedSave;
    private void runQueuedSave(){if(saveQueued){saveQueued=false;project.save(getContext());}}
    private void autoSave(){saveQueued=true;removeCallbacks(saveTask);postDelayed(saveTask,1500);}
    private void flushSave(){removeCallbacks(saveTask);saveQueued=false;project.save(getContext());}
    private void resetLevel(){project.level=new Level();level=project.level;sectorEditor=new SectorEditor(level);pending.clear();selKind=0;walker=null;cancelStage();changed("Level reset");}
    private void changeMode(int next){
        if(mode==PLAY&&next!=PLAY){sfx.stopMusic(0);sfx.stopSounds();sfx.stop();world=null;}
        if(next==PLAY&&mode!=PLAY)returnMode=mode;
        if(next==HOME)save(false);
        launcherOpen=false;
        cancelStage();entStage=0;
        mode=next;pending.clear();joyVisible=false;joyPointer=lookPointer=upPointer=downPointer=-1;moveX=moveY=0;flyUp=flyDown=false;dpadPointer=-1;
        lastFrame=System.nanoTime();
        if(next==PLAY)startPlay();
        setKeepScreenOn(next==PLAY);   // the screen may sleep normally while editing
        if(next!=HOME)hint(next==PLAY?"Left: move · right: look · Attack, Cast, Use and Potion on the right · Bag top right":next==POLY?"Pick a tool, move the cursor, tap Apply":next==THINGS?"Pick an entity type, then Apply to place it":"Left: move · right: look · double-tap a surface to texture it");
    }

    // ---------------------------------------------------------------- touch
    @Override public boolean onTouchEvent(MotionEvent e){
        if(drawError!=null){if(e.getActionMasked()==MotionEvent.ACTION_UP){drawError=null;changeMode(POLY);invalidate();}return true;}
        try{return handleTouch(e);}catch(RuntimeException ex){DoomApp.record(getContext(),ex);drawError=ex.getClass().getSimpleName()+": "+ex.getMessage();invalidate();return true;}
    }
    private boolean toolbarTap(int hid){
        if(hid>=H_ED&&hid<H_ED+EDITOR_NAMES.length){openEditor(hid-H_ED);return true;}
        if(hid==H_SWITCH){launcherOpen=true;return true;}
        if(hid==H_MORE){moreMenu();return true;}
        if(hid==H_LOGO){if(mode!=HOME){changeMode(HOME);return true;}save(false);if(onExitToTitle!=null)onExitToTitle.run();return true;}
        if(hid==H_SAVE){save(true);return true;}
        if(hid==H_RESET){if(System.currentTimeMillis()<resetArmedUntil){resetArmedUntil=0;resetLevel();}else{resetArmedUntil=System.currentTimeMillis()+3000;hint("Tap Confirm to reset the whole level");postInvalidateDelayed(3010);}return true;}
        return false;
    }
    private void openFsm(String defId){save(false);if(host!=null)host.openFsm(defId);}
    private void openSprites(String defId){save(false);if(host!=null)host.openSprites(defId);}
    private boolean handleTouch(MotionEvent e){
        int a=e.getActionMasked(),idx=e.getActionIndex();float x=e.getX(idx),y=e.getY(idx);
        lastInteraction=System.currentTimeMillis();
        if(launcherOpen){if(a==MotionEvent.ACTION_DOWN)launcherTap(ui.hitAt(x,y));invalidate();return true;}
        if(mode==PLAY)return playTouch(e,a,idx,x,y);
        if(a==MotionEvent.ACTION_DOWN&&y<toolbarH){toolbarTap(ui.hitAt(x,y));invalidate();return true;}
        if(mode==HOME){if(a==MotionEvent.ACTION_DOWN){homeTap(ui.hitAt(x,y));invalidate();}return true;}
        if(mode==POLY3D)return touch3D(e,a,idx,x,y);
        return touch2D(e,a,idx,x,y);
    }
    private boolean touch2D(MotionEvent e,int a,int idx,float x,float y){
        if(a==MotionEvent.ACTION_DOWN){
            downX=lastX=x;downY=lastY=y;dragged=false;panning=false;scrollTarget=0;pinchDist=0;
            int hid=ui.hitAt(x,y);
            if(inMap(x,y)){
                int dir=dpadHit(x,y);
                if(dir>=0){dpadPointer=e.getPointerId(idx);dpadDirection=dir;cursorStep(dir);nextDpadRepeat=System.currentTimeMillis()+270;postDpadRepeat();return true;}
                if(hid==H_APPLY){cursorApply();invalidate();return true;}
                return true; // cursor-only editing: other touches on the canvas are ignored
            }
            if(mode==THINGS&&x<entPanelW()){scrollTarget=1;return true;}
            if(x>=mapR()){scrollTarget=mode==THINGS?2:0;return true;}
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(dpadPointer>=0){int dir=dpadHit(e.getX(0),e.getY(0));if(dir!=dpadDirection){dpadDirection=dir;if(dir>=0){cursorStep(dir);nextDpadRepeat=System.currentTimeMillis()+270;}invalidate();}return true;}
            if(Math.abs(x-downX)+Math.abs(y-downY)>dp(8))dragged=true;
            if(dragged){
                if(scrollTarget==1)defScroll-=(y-lastY);
                else if(scrollTarget==2)propScroll-=(y-lastY);
                invalidate();}
            lastX=x;lastY=y;return true;
        }
        if(a==MotionEvent.ACTION_POINTER_UP){pinchDist=0;return true;}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
            if(dpadPointer>=0){dpadPointer=-1;dpadDirection=-1;invalidate();return true;}
            if(a==MotionEvent.ACTION_UP&&!dragged){
                {
                    int hid=ui.hitAt(x,y);
                    if(hid==H_ZOOMIN||hid==H_ZOOMOUT){zoom=Math.max(.35f,Math.min(4f,zoom*(hid==H_ZOOMIN?1.25f:.8f)));followCursor=true;}
                    else if(hid>=H_PAGE&&hid<H_PAGE+3){toolPage=hid-H_PAGE;}
                    else if(hid>=H_TOOL&&hid<H_TOOL+11&&mode==POLY){sectorAction(PAGES[toolPage][hid-H_TOOL]);}
                    else if(hid>=H_DEF&&hid<H_DEF+1000){int i=hid-H_DEF;if(i<project.defs.size()){selDef=project.defs.get(i).id;selThing=-1;propScroll=0;if(entityTool!=0){entityTool=0;}hint(project.defs.get(i).name+" · Apply to place");}}
                    else if(hid==H_NEWDEF)newEntityType();
                    else if(hid>=H_PROP&&hid<H_PROP+20)propertyTap(hid-H_PROP);
                    else if(hid>=H_ETOOL&&hid<H_ETOOL+5){entityTool=hid-H_ETOOL;entStage=0;hint(ENTITY_TOOLS[entityTool]+" · move the cursor and tap Apply");}
                    else if(hid==H_EDITFSM)openFsm(selDef);
                    else if(hid==H_EDITSPRITE)openSprites(selDef);
                    else if(hid==H_DUPDEF){EntityDef d=currentDef();if(d!=null){EntityDef c=d.copy(project.uniqueId(d.name+" copy"),d.name+" copy");if(c.kind==EntityDef.PLAYER)c.kind=EntityDef.DECORATION;project.defs.add(c);selDef=c.id;defScroll=1e9f;changed(c.name+" created");}}
                    else if(hid==H_DELDEF)deleteEntityType();
                }
            }
            panning=false;scrollTarget=0;invalidate();return true;
        }
        return true;
    }
    private void postDpadRepeat(){
        postDelayed(new Runnable(){public void run(){
            if(dpadPointer<0||dpadDirection<0)return;
            if(System.currentTimeMillis()>=nextDpadRepeat){cursorStep(dpadDirection);nextDpadRepeat=System.currentTimeMillis()+90;}
            postDelayed(this,30);}},30);
    }
    private void cursorStep(int direction){
        float s=snap();
        if(direction==0)cursorY-=s;if(direction==1)cursorX+=s;if(direction==2)cursorY+=s;if(direction==3)cursorX-=s;
        cursorX=Math.max(-10000,Math.min(10000,cursorX));cursorY=Math.max(-10000,Math.min(10000,cursorY));
        followCursor=true;
        if(stage==1&&tool==MOVE&&mode==POLY)previewMove();
        invalidate();
    }
    private boolean touch3D(MotionEvent e,int a,int idx,float x,float y){
        int id=e.getPointerId(idx);float left=browserW();
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            int hid=ui.hitAt(x,y);
            if(hid>=H_MAT&&hid<H_MAT+Textures.COUNT){selectedMaterial=hid-H_MAT;hint(Textures.NAMES[selectedMaterial]+" · double-tap a surface to apply");scrollTarget=3;downY=lastY=y;dragged=false;return true;}
            if(x<left){scrollTarget=3;downY=lastY=y;dragged=false;return true;}
            if(hid==H_FLY){fly=!fly;if(walker!=null)walker.player.vz=0;hint(fly?"Fly mode · use Up and Down":"Walk mode · gravity and steps");invalidate();return true;}
            if(hid==H_START3D){ensureWalker();walker.player.x=level.playerX;walker.player.y=level.playerY;walker.player.angle=level.facing;walker.player.pitch=0;walker.player.z=walker.floorAt(level.playerX,level.playerY);walker.viewZ=walker.player.z+World.EYE;hint("Back at the player start");invalidate();return true;}
            if(hid==H_UP){flyUp=true;upPointer=id;invalidate();return true;}
            if(hid==H_DOWN){flyDown=true;downPointer=id;invalidate();return true;}
            if(hid==H_RAISE||hid==H_LOWER||hid==H_LIGHTUP||hid==H_LIGHTDN){adjust3D(hid);return true;}
            if(hid==H_EDITFSM3D){int v=Renderer.value(sel3d);if(v<level.things.size())openFsm(level.things.get(v).def);return true;}
            if(x<left+(w-left)*.45f){if(joyPointer<0){joyPointer=id;joyVisible=true;joyOriginX=joyX=x;joyOriginY=joyY=y;moveX=moveY=0;}}
            else if(lookPointer<0){lookPointer=id;lookLastX=x;lookLastY=y;lookDownX=x;lookDownY=y;}
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(scrollTarget==3){float yy=e.getY(0);if(Math.abs(yy-downY)>dp(6))dragged=true;matScroll-=(yy-lastY);lastY=yy;invalidate();return true;}
            for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float px=e.getX(i),py=e.getY(i);
                if(pid==joyPointer)moveJoystick(px,py);
                if(pid==lookPointer&&walker!=null){walker.player.angle+=(px-lookLastX)*.0065f;walker.player.pitch=Math.max(-.55f,Math.min(.55f,walker.player.pitch-(py-lookLastY)*.0045f));lookLastX=px;lookLastY=py;}}
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_CANCEL){
            if(scrollTarget==3&&a!=MotionEvent.ACTION_POINTER_UP){scrollTarget=0;return true;}
            if(id==upPointer){flyUp=false;upPointer=-1;}
            if(id==downPointer){flyDown=false;downPointer=-1;}
            if(id==lookPointer){
                if(a!=MotionEvent.ACTION_CANCEL&&Math.hypot(x-lookDownX,y-lookDownY)<dp(10)){
                    int hit=renderer.pick(x,y,view3d);
                    long now=System.currentTimeMillis();boolean twice=now-last3DTapTime<380&&Math.hypot(x-last3DTapX,y-last3DTapY)<dp(16)&&hit==sel3d;
                    sel3d=hit;
                    if(hit>=0&&twice&&Renderer.kind(hit)!=Renderer.ID_SPRITE)apply3DTexture();
                    else if(hit>=0&&Renderer.kind(hit)!=Renderer.ID_SPRITE)hint("Selected · double-tap to apply "+Textures.NAMES[selectedMaterial]);
                    last3DTapTime=now;last3DTapX=x;last3DTapY=y;}
                lookPointer=-1;}
            if(id==joyPointer){joyPointer=-1;joyVisible=false;joyReleaseTime=System.currentTimeMillis();moveX=moveY=0;}
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){joyPointer=lookPointer=upPointer=downPointer=-1;joyVisible=false;flyUp=flyDown=false;moveX=moveY=0;}
            invalidate();return true;
        }
        return true;
    }
    private boolean playTouch(MotionEvent e,int a,int idx,float x,float y){
        if(world==null)return true;
        int id=e.getPointerId(idx);
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            int hid=ui.hitAt(x,y);
            if(world.levelComplete){if(hid==H_RETRY)startPlay();else if(hid==H_BACK)changeMode(returnMode);return true;}
            if(bagOpen){bagTap(hid);bagDragY=y;bagDragged=false;return true;}
            if(world.dialogue!=null){if(hid>=H_DLG&&hid<H_DLG+3)world.chooseDialogue(hid-H_DLG);return true;}
            if(hid==H_EXITPLAY){changeMode(returnMode);return true;}
            if(hid==H_BAG){openBag();return true;}
            int b=hid==H_FIRE?Input.FIRE:hid==H_USE?Input.USE:hid==H_JUMP?Input.JUMP:hid==H_WEAPON?Input.WEAPON:hid==H_SPELL?Input.SPELL:hid==H_POTION?Input.POTION:-1;
            if(b>=0){buttonPointer[b]=id;world.input.setButton(b,true);return true;}
            if(x<w*.45f){if(joyPointer<0){joyPointer=id;joyVisible=true;joyOriginX=joyX=x;joyOriginY=joyY=y;moveX=moveY=0;}}
            else if(lookPointer<0){lookPointer=id;lookLastX=x;lookLastY=y;}
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(bagOpen){bagScroll=Math.max(0,bagScroll-(y-bagDragY));bagDragY=y;return true;}
            for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float px=e.getX(i),py=e.getY(i);
                if(pid==joyPointer)moveJoystick(px,py);
                if(pid==lookPointer){world.input.lookX+=px-lookLastX;world.input.lookY+=py-lookLastY;lookLastX=px;lookLastY=py;}}
            return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_CANCEL){
            for(int b=0;b<buttonPointer.length;b++)if(buttonPointer[b]==id||a!=MotionEvent.ACTION_POINTER_UP&&buttonPointer[b]>=0){buttonPointer[b]=-1;world.input.setButton(b,false);}
            if(id==joyPointer||a!=MotionEvent.ACTION_POINTER_UP){if(joyPointer>=0){joyReleaseTime=System.currentTimeMillis();}joyPointer=-1;joyVisible=false;moveX=moveY=0;}
            if(id==lookPointer||a!=MotionEvent.ACTION_POINTER_UP)lookPointer=-1;
            return true;
        }
        return true;
    }
}
