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
    static final String[] EDITOR_NAMES={"Sectors","Entities","3D View","Play","FSM","Sprites","Sound","UI Editor","Content","Dialogue"};
    static final int[] EDITOR_ICONS={Icons.SECTORS,Icons.ENTITIES,Icons.CUBE,Icons.PLAY,Icons.GRAPH,Icons.PIXEL,Icons.MUSIC,Icons.GAMEPAD,Icons.BACKPACK,Icons.CHAT};
    static final int[] EDITOR_COLORS={0xff3d8bfd,0xfff0a030,0xff9b6dff,0xff3ecf8e,0xff2fb8c8,0xffef6b8a,0xffe0679a,0xffd8a830,0xffc0703a,0xff5a8a4a};
    static final String[] EDITOR_DESC={"Rooms, doors & stairs","Monsters, items & types","Walk & paint surfaces","Test with the real engine","Behaviour & actions","Pixel art & animation","Sound effects & music","Controls & HUD layout","Items, spells, classes, loot & shops","Conversations & quests"};
    private static final int[] EDITOR_MODES={POLY,THINGS,POLY3D,PLAY,-1,-1,-1,-1,-1,-1};
    private static final int H_ED=3000,H_SWITCH=3010,H_MORE=3011,H_LAUNCH=3020,H_LAUNCH_HOME=3037,H_LAUNCH_CLOSE=3038,H_TILE=3040,H_HOMEPLAY=3050,H_HOME3D=3051,H_HOMEFIX=3052;
    private boolean launcherOpen;
    // Touch target ids registered while drawing.
    private static final int H_MODE=100,H_FSM=110,H_SAVE=111,H_RESET=112,H_PAGE=120,H_TOOL=200,H_APPLY=520,H_MAT=4000,
            H_RAISE=500,H_LOWER=501,H_LIGHTUP=502,H_LIGHTDN=503,H_FLY=504,H_UP=505,H_DOWN=506,H_EDITFSM3D=507,H_START3D=508,
            H_FIRE=510,H_USE=511,H_JUMP=512,H_WEAPON=513,H_EXITPLAY=514,H_SPELL=515,H_POTION=516,H_BAG=517,H_DLG=3200,H_INV_ITEM=3300,H_INV_SPELL=3600,H_INV_CLOSE=3700,H_INV_TAB=3710,H_RETRY=530,H_BACK=531,
            H_NEWDEF=590,H_LOGO=593,H_SPRITES=594,H_EDITSPRITE=813,H_ZOOMIN=591,H_ZOOMOUT=592,H_DEF=2000,H_PROP=700,H_ETOOL=800,H_EDITFSM=810,H_DUPDEF=811,H_DELDEF=812,H_EUNDO=814,H_EREDO=815;
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

    private static String toolName(int t){switch(t){case DRAW:return "Draw";case RECT:return "Rectangle";case PREFAB:return "Prefab";case STAIRS:return "Stairs";
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
    // prefab tool: the chunk to stamp, its quarter turns, and whether Apply is marking an area to save
    private Prefab curPrefab=Prefab.builtins().get(0);private int prefabRot;private boolean prefabSaving;
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
        project=Project.get(ctx);level=project.level;entityUndo=new Undo(()->project.section(Project.SEC_ENTITIES),t->project.restoreSection(Project.SEC_ENTITIES,t));sectorEditor=new SectorEditor(level);entityUndo.reset();
        if(project.loadWarning!=null){message=project.loadWarning;messageUntil=System.currentTimeMillis()+5000;}
        cursorX=Math.round(level.playerX);cursorY=Math.round(level.playerY);viewX=cursorX;viewY=cursorY;
        setFocusable(true);
    }
    boolean exitPlay(){if(mode!=PLAY)return false;changeMode(returnMode);return true;}
    void onPause(){sfx.stop();flushSave();setKeepScreenOn(false);}
    /** Undo for entity types and placed entities on this map; sector shapes use the SectorEditor's own history. */
    private Undo entityUndo;
    private void entityUndoRedo(boolean back){
        if(back?entityUndo.undo():entityUndo.redo()){
            if(project.def(selDef)==null)selDef=project.defs.size()>1?project.defs.get(1).id:project.defs.get(0).id;
            if(selThing>=level.things.size())selThing=-1;entStage=0;
            autoSave();renderer.invalidate();hint(back?"Undo":"Redo");}
        else hint(back?"Nothing to undo":"Nothing to redo");invalidate();}
    void onResume(){entityUndo.reset();if(mode==PLAY){sfx.start();setKeepScreenOn(true);}lastInteraction=System.currentTimeMillis();renderer.invalidate();invalidate();}

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
        if(level!=project.level){level=project.level;sectorEditor=new SectorEditor(level);entityUndo.reset();walker=null;}
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
        // Editor shortcut icons get whatever room is left after the title and the map chip; the rest are
        // in the title's editor list. Sectors, Entities, 3D and Play come first, then the other editors.
        float tl=dp(50);
        float titleW=ed>=0?Math.min(dp(190),ui.measure(EDITOR_NAMES[ed],15,1)+dp(78)):Math.min(dp(200),dp(40)+Math.max(ui.measure(project.name,15,1),ui.measure("Project · Home",10.5f,0)));
        float chipW=Math.min(dp(190),ui.measure(project.current().name,12,1)+dp(76));
        int fit=Math.max(0,(int)((x-(tl+titleW+dp(10)+chipW+dp(14)))/(bh+gap)));
        boolean[] show=new boolean[EDITOR_NAMES.length];int shown=0;
        if(ed>=0&&fit>0){show[ed]=true;shown++;}
        for(int i=0;i<EDITOR_NAMES.length&&shown<fit;i++)if(!show[i]){show[i]=true;shown++;}
        for(int i=EDITOR_NAMES.length-1;i>=0;i--){if(!show[i])continue;r.set(x-bh,top,x,top+bh);ui.button(c,r,EDITOR_ICONS[i],null,i==ed?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_ED+i);x-=bh+gap;}
        float right=x-dp(8);
        // title: the editor switcher (icon, name, chevron) or the project on the home screen
        if(ed>=0){
            String nm=EDITOR_NAMES[ed];float tw=Math.min(right-tl,ui.measure(nm,15,1)+dp(78));
            r.set(tl,top,tl+tw,top+bh);
            ui.round(c,r,dp(8),Ui.SURFACE_2);ui.outline(c,r,dp(8),0x14ffffff,dp(1));
            float ib=dp(24);ui.round(c,r.left+dp(5),cy-ib*.5f,r.left+dp(5)+ib,cy+ib*.5f,dp(6),EDITOR_COLORS[ed]);ui.icon(c,EDITOR_ICONS[ed],r.left+dp(5)+ib*.5f,cy,15,0xffffffff);
            ui.textMid(c,ui.ellipsize(nm,15,1,tw-dp(70)),r.left+dp(38),cy,15,Ui.TEXT,1,-1);
            ui.icon(c,Icons.CHEVRON_DOWN,r.right-dp(16),cy,15,Ui.TEXT_2);ui.hit(r,H_SWITCH);
            float px=r.right+dp(10);
            if(right-px>dp(70))mapChip(c,px,top,right,bh,cy);
        }else{
            float ib=dp(30);ui.logo(c,tl,cy-ib*.5f,ib);
            float tx=tl+ib+dp(10);
            ui.text(c,ui.ellipsize(project.name,15,1,right-tx),tx,cy-dp(1),15,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize((project.template==null||project.template.isEmpty()?"Project":project.template)+" · Home",10.5f,0,right-tx),tx,cy+dp(13),10.5f,Ui.TEXT_3,0,-1);
            r.set(tl,top,Math.min(right,tx+dp(160)),top+bh);ui.hit(r,H_SWITCH);
            float px=Math.max(tx+ui.measure(project.name,15,1),tx+ui.measure("Project · Home",10.5f,0))+dp(16);
            if(right-px>dp(70))mapChip(c,px,top,right,bh,cy);
        }
    }
    private static final int H_MAPS=3070;
    /** "Map: Town ▾" chip in the header: switch, add and manage the game's maps. */
    private void mapChip(Canvas c,float px,float top,float right,float bh,float cy){
        Project.GameMap gm=project.current();String label=gm.name+(project.maps.size()>1?"  ·  "+(project.maps.indexOf(gm)+1)+"/"+project.maps.size():"");
        float cw=Math.min(right-px,ui.measure(label,12,1)+dp(58));
        r.set(px,top+dp(3),px+cw,top+bh-dp(3));ui.round(c,r,dp(8),0xff1c2a24);ui.outline(c,r,dp(8),0x553ecf8e,dp(1));
        ui.icon(c,Icons.SECTORS,r.left+dp(15),cy,13,Ui.GREEN);
        ui.textMid(c,ui.ellipsize(label,12,1,cw-dp(52)),r.left+dp(28),cy,12,Ui.TEXT,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,r.right-dp(13),cy,13,Ui.TEXT_2);
        ui.hit(r,H_MAPS);
    }
    private void openMapLocal(int num){
        save(false);project.switchMap(num);level=project.level;sectorEditor=new SectorEditor(level);entityUndo.reset();walker=null;
        selThing=-1;selKind=0;pending.clear();renderer.invalidate();autoSave();hint("Editing map: "+project.mapName(num));invalidate();
    }
    private void mapsMenu(){
        if(host==null)return;project.ensureMaps();project.syncCurrent();
        java.util.ArrayList<String> items=new java.util.ArrayList<>();
        for(Project.GameMap gm:project.maps)items.add((gm.num==project.currentMap?"● ":"   ")+gm.name+(gm.num==project.startMap?"  (start map)":""));
        int base=project.maps.size();
        String[] acts={"+ New map…","Duplicate this map","Rename this map","Music for this map…","Make this the start map","Play this map now","Delete this map…","Atmosphere for this map…"};
        for(String a:acts)items.add(a);
        host.choose("Maps · "+project.name,items.toArray(new String[0]),project.maps.indexOf(project.current()),k->{
            if(k<base){openMapLocal(project.maps.get(k).num);return;}
            Project.GameMap cur=project.current();
            switch(k-base){
                case 0:host.choose("New map",new String[]{"Empty map","Two rooms with people","Open arena","Courtyard","Copy of this map"},0,t->{
                    Level l=t==0?Templates.empty():t==1?new Level():t==2?Templates.arena():t==3?Templates.courtyard():cur.level.copy();
                    host.prompt("Map name",null,"Map "+(project.maps.size()+1),false,n->{Project.GameMap gm=project.addMap(n.trim().isEmpty()?"Map "+(project.maps.size()+1):n.trim(),l);openMapLocal(gm.num);
                        hint("New map · link it with an Exit sector or a Map exit entity");});});break;
                case 1:{Project.GameMap gm=project.addMap(cur.name+" copy",cur.level.copy());gm.music=cur.music;openMapLocal(gm.num);break;}
                case 2:host.prompt("Rename map",null,cur.name,false,n->{if(!n.trim().isEmpty())cur.name=n.trim();autoSave();invalidate();});break;
                case 3:{String[] songs=new String[project.songs.size()+1];songs[0]="Project level music";int ch=0;
                    for(int i=0;i<project.songs.size();i++){songs[i+1]=project.songs.get(i).name;if(project.songs.get(i).id.equals(cur.music))ch=i+1;}
                    host.choose("Music for "+cur.name,songs,ch,i->{cur.music=i==0?"":project.songs.get(i-1).id;autoSave();hint(i==0?"Uses the project's level music":"Music set");});break;}
                case 7:host.choose("Atmosphere · "+cur.name,new String[]{"Fog: "+Atmosphere.FOGS[cur.fog],"Colour tint: "+Atmosphere.TINTS[cur.tint],"Weather: "+Atmosphere.WEATHERS[cur.weather]},0,a->{
                    if(a==0)host.choose("Fog",Atmosphere.FOGS,cur.fog,v->{cur.fog=v;autoSave();invalidate();hint("Fog: "+Atmosphere.FOGS[v]);});
                    else if(a==1)host.choose("Colour tint",Atmosphere.TINTS,cur.tint,v->{cur.tint=v;autoSave();invalidate();hint("Tint: "+Atmosphere.TINTS[v]);});
                    else host.choose("Weather",Atmosphere.WEATHERS,cur.weather,v->{cur.weather=v;autoSave();hint("Weather: "+Atmosphere.WEATHERS[v]+" · shows in Play");});});break;
                case 4:project.startMap=cur.num;autoSave();hint(cur.name+" is now where New Game starts");break;
                case 5:playThisMap=cur.num;changeMode(PLAY);break;
                case 6:if(project.maps.size()<=1){hint("A game needs at least one map");break;}
                    host.confirm("Delete "+cur.name+"?","Its sectors and placed entities are removed. Exits leading here will finish the game instead.","Delete",()->{
                        int num=cur.num;project.maps.remove(cur);
                        for(Project.GameMap gm:project.maps)for(Level.Sector sc:gm.level.sectors)if(sc.special==Level.SP_EXIT&&sc.tag==num)sc.tag=0;
                        if(project.startMap==num)project.startMap=project.maps.get(0).num;
                        Project.GameMap nx=project.maps.get(0);project.currentMap=nx.num;project.level=nx.level;level=project.level;sectorEditor=new SectorEditor(level);entityUndo.reset();walker=null;selThing=-1;selKind=0;pending.clear();renderer.invalidate();
                        autoSave();hint("Map deleted");invalidate();});break;
            }
            invalidate();});
    }
    /** Opens an editor by index (see EDITOR_NAMES); -1 is the home screen. */
    private void openEditor(int i){
        launcherOpen=false;
        if(i<0){if(mode!=HOME)changeMode(HOME);return;}
        if(i==4){openFsm(mode==THINGS?selDef:null);return;}
        if(i==5){openSprites(mode==THINGS?selDef:null);return;}
        if(i==6){save(false);if(host!=null)host.openSound(0);return;}
        if(i==7){save(false);if(host!=null)host.openUi();return;}
        if(i==8){save(false);if(host!=null)host.openContent(0);return;}
        if(i==9){save(false);if(host!=null)host.openDialogue(0);return;}
        if(EDITOR_MODES[i]!=mode)changeMode(EDITOR_MODES[i]);
    }
    /** Android back: close the launcher, leave Play, leave an editor for Home; false means leave the project. */
    boolean goBack(){
        if(launcherOpen){launcherOpen=false;invalidate();return true;}
        if(mode==PLAY&&menu!=null&&menu.active()){if(!menu.back())leavePlay();invalidate();return true;}
        if(mode==PLAY&&world!=null&&debug!=null&&debug.open){debug.close(world);invalidate();return true;}
        if(mode==PLAY&&world!=null&&world.shop!=null){world.closeShop();invalidate();return true;}
        if(mode==PLAY&&bagOpen){closeBag();invalidate();return true;}
        if(mode==PLAY){pauseToMenu();invalidate();return true;}
        if(mode!=HOME){changeMode(HOME);invalidate();return true;}
        return false;
    }
    private void moreMenu(){
        if(host==null)return;
        ArrayList<String> errs=MapValidator.check(level);
        boolean sv=Perf.saver(getContext());
        host.choose(project.name,new String[]{"Check map ("+(errs.isEmpty()?"no problems":errs.size()+" warning"+(errs.size()==1?"":"s"))+")","Save now",
                sv?"Battery saver: ON (tap to turn off)":"Battery saver: off (30 fps, still backdrops)",
                Sprites.pixel?"Built-in sprites: Pixel art (tap for HD)":"Built-in sprites: HD (tap for pixel art)","Reset level…","Back to projects",
                frontEnd()?"Play: splash + main menu ON (tap to start straight in the level)":"Play: starts straight in the level (tap for splash + main menu)",
                "Play from the cursor (testing)","Maps… (switch, add, rename, delete)","Search the project…","3D renderer: "+rendererLabel()},-1,k->{
            if(k==0)showMapCheck();
            else if(k==1)save(true);
            else if(k==2){Perf.setSaver(getContext(),!sv);hint(!sv?"Battery saver on · 30 fps game and 3D, still previews":"Battery saver off · 60 fps");lastInteraction=System.currentTimeMillis();}
            else if(k==4)host.confirm("Reset the level?","This clears every sector and placed entity. Entity types, FSMs and sprites are kept.","Reset level",()->{resetLevel();invalidate();});
            else if(k==5){save(false);if(onExitToTitle!=null)onExitToTitle.run();}
            else if(k==8){mapsMenu();return;}
            else if(k==9){searchProject();return;}
            else if(k==10){chooseRenderer();return;}
            else if(k==7){playFrom=new float[]{cursorX,cursorY,level.facing};playThisMap=project.currentMap;
                if(level.sectorAt(cursorX,cursorY)<0)hint("The cursor is outside every sector · starting at the player start");changeMode(PLAY);}
            else if(k==6){GameSettings.frontEnd=!GameSettings.frontEnd;GameSettings.save(getContext());hint(GameSettings.frontEnd?"Play opens with the splash screen and main menu":"Play starts straight in the level");}
            else if(k==3){boolean hd=Sprites.pixel;Perf.setHdSprites(getContext(),hd);java.util.Arrays.fill(spriteThumbs,null);hint(hd?"Built-in sprites: HD":"Built-in sprites: pixel art");}
            invalidate();});
    }
    // ---------------------------------------------------------------- project search
    private static boolean has(String text,String q){return text!=null&&text.toLowerCase().contains(q);}
    private void searchProject(){
        if(host==null)return;
        host.prompt("Search the project","Entities, states, actions, items, spells, conversations, quests, sounds, maps…",lastSearch,false,t->{String q=t.trim();if(q.isEmpty())return;lastSearch=q;showSearch(q.toLowerCase());});
    }
    private String lastSearch="";
    // ---------------------------------------------------------------- renderer choice
    private static final String[] RENDERERS={"GPU · retro pixels (200 lines)","GPU · sharp (270 lines)","GPU · high detail (360 lines)","GPU · full HD (540 lines)","Software (CPU, the old renderer)"};
    private static final int[] RENDER_LINES={200,270,360,540};
    private String rendererLabel(){if(!Renderer.useGpu)return "Software";if(!renderer.onGpu()&&GpuRenderer.available()==false)return "Software (GPU not available)";return "GPU, "+Renderer.gpuLines+" lines";}
    private void chooseRenderer(){
        int cur=4;if(Renderer.useGpu)for(int i=0;i<RENDER_LINES.length;i++)if(RENDER_LINES[i]==Renderer.gpuLines)cur=i;
        String[] items=java.util.Arrays.copyOf(RENDERERS,RENDERERS.length+5);
        items[RENDERERS.length]="Normal + parallax mapping: "+(GpuRenderer.relief?"ON (tap to turn off)":"off (tap to turn on)");
        items[RENDERERS.length+1]="PS1 look (wobble, warped textures, dithering): "+(GpuRenderer.ps1?"ON":"off");
        items[RENDERERS.length+2]="Sky light + bounce lighting: "+(GpuRenderer.gi?"ON":"off");
        items[RENDERERS.length+3]="Dynamic shadows from lights: "+(GpuRenderer.shadows?"ON":"off");
        items[RENDERERS.length+4]="3D models for characters and items: "+(GpuRenderer.models3d?"ON":"off (sprites)");
        host.choose("3D renderer",items,cur,k->{
            if(k==RENDERERS.length+3){GpuRenderer.shadows=!GpuRenderer.shadows;Renderer.savePrefs(getContext());hint(GpuRenderer.shadows?"Shadows on · walls, doors and monsters block torchlight":"Shadows off");invalidate();return;}
            if(k==RENDERERS.length+1){GpuRenderer.ps1=!GpuRenderer.ps1;Renderer.savePrefs(getContext());hint(GpuRenderer.ps1?"PS1 look on · wobbling vertices, warped textures, 15-bit dithered colour":"PS1 look off · steady, smooth picture");invalidate();return;}
            if(k==RENDERERS.length+4){GpuRenderer.models3d=!GpuRenderer.models3d;Renderer.savePrefs(getContext());hint(GpuRenderer.models3d?"3D models on · built-in characters and items are low-poly models":"3D models off · sprites");invalidate();return;}
            if(k==RENDERERS.length+2){GpuRenderer.gi=!GpuRenderer.gi;Renderer.savePrefs(getContext());hint(GpuRenderer.gi?"Sky light on · open-sky sectors light up and light spills through doorways":"Sky light off");invalidate();return;}
            if(k==RENDERERS.length){GpuRenderer.relief=!GpuRenderer.relief;Renderer.savePrefs(getContext());hint(GpuRenderer.relief?"Relief on · textures get depth and react to lights (GPU only)":"Relief off · flat textures");invalidate();return;}
            if(k==4)Renderer.useGpu=false;else{Renderer.useGpu=true;Renderer.gpuLines=RENDER_LINES[k];}
            Renderer.savePrefs(getContext());renderer.invalidate();
            hint(k==4?"Software renderer · lights and fog are drawn by the CPU":GpuRenderer.available()?"GPU renderer · "+RENDER_LINES[k]+" lines":"This phone's GPU couldn't start · using software");invalidate();});
    }
    private void showSearch(String q){
        ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> go=new ArrayList<>();
        project.syncCurrent();
        // entity types, their FSM states and actions
        for(EntityDef d:project.defs){
            if(has(d.name,q)||has(d.id,q)){labels.add("Entity · "+d.name+" ("+EntityDef.KIND_NAMES[d.kind]+")");go.add(()->{changeMode(THINGS);selDef=d.id;selThing=-1;propScroll=0;hint(d.name+" selected");});}
            for(FSMGraph.Node n:d.fsm.nodes){
                if(has(n.name,q)){labels.add("FSM state · "+d.name+" → "+n.name);go.add(()->openFsm(d.id));}
                for(FSMGraph.Action a:n.actions){Actions.Spec sp=Actions.get(a.type);String an=sp==null?a.type:sp.name;boolean hit=has(an,q);
                    for(String v:a.params.values())if(has(v,q))hit=true;
                    if(hit){labels.add("FSM action · "+d.name+" → "+n.name+" → "+an);go.add(()->openFsm(d.id));}}
                for(String ev:n.transitions.keySet())if(has(ev,q)){labels.add("FSM event · "+d.name+" → "+n.name+" on "+ev);go.add(()->openFsm(d.id));}}}
        // placed entities on every map
        for(Project.GameMap gm:project.maps){Level lv=gm.level;
            for(int i=0;i<lv.things.size();i++){Level.Thing th=lv.things.get(i);EntityDef d=project.def(th.def);if(d==null||!(has(d.name,q)||has(d.id,q)))continue;
                final int idx=i;final int num=gm.num;
                labels.add("Placed · "+d.name+" on "+gm.name+" at "+Math.round(th.x)+","+Math.round(th.y));
                go.add(()->{if(num!=project.currentMap)openMapLocal(num);if(idx>=level.things.size())return;Level.Thing t2=level.things.get(idx);
                    changeMode(THINGS);selDef=t2.def;selThing=idx;cursorX=t2.x;cursorY=t2.y;viewX=cursorX;viewY=cursorY;followCursor=true;hint(project.def(t2.def).name+" · cursor moved to it");});}}
        // maps
        for(Project.GameMap gm:project.maps)if(has(gm.name,q)){final int num=gm.num;labels.add("Map · "+gm.name);go.add(()->openMapLocal(num));}
        // content
        project.ensureContent();
        for(Rpg.Item it:project.items)if(has(it.name,q)||has(it.id,q)){labels.add("Item · "+it.name);go.add(()->openContentTab(0));}
        for(Rpg.Spell sp:project.spells)if(has(sp.name,q)||has(sp.id,q)||has(sp.desc,q)){labels.add("Spell · "+sp.name);go.add(()->openContentTab(1));}
        for(Rpg.HeroClass hc:project.classes)if(has(hc.name,q)||has(hc.desc,q)){labels.add("Class · "+hc.name);go.add(()->openContentTab(2));}
        for(Rpg.LootTable lt:project.loot)if(has(lt.name,q)||has(lt.id,q)){labels.add("Loot table · "+lt.name);go.add(()->openContentTab(3));}
        for(Rpg.Shop sh:project.shops)if(has(sh.name,q)||has(sh.id,q)||has(sh.greeting,q)){labels.add("Shop · "+sh.name);go.add(()->openContentTab(4));}
        // conversations and quests
        for(Dialogue.Def dd:project.dialogues){boolean hit=has(dd.name,q)||has(dd.speaker,q);String line=null;
            for(Dialogue.Node n:dd.nodes){if(line==null&&(has(n.text,q)||has(n.speaker,q)))line=n.text;for(Dialogue.Choice ch:n.choices)if(line==null&&has(ch.text,q))line=ch.text;}
            if(hit||line!=null){labels.add("Conversation · "+dd.name+(line!=null?" — “"+ui.ellipsize(line,12,1,dp(220))+"”":""));go.add(()->{save(false);host.openDialogue(0);});}}
        for(Dialogue.QuestDef qd:project.quests){boolean hit=has(qd.title,q);for(String st:qd.stages)if(has(st,q))hit=true;
            if(hit){labels.add("Quest · "+qd.title);go.add(()->{save(false);host.openDialogue(1);});}}
        // audio and sprites
        for(SoundAsset sa:project.sounds)if(has(sa.name,q)){labels.add("Sound · "+sa.name);go.add(()->{save(false);host.openSound(0);});}
        for(Song sg:project.songs)if(has(sg.name,q)){labels.add("Song · "+sg.name);go.add(()->{save(false);host.openSound(1);});}
        for(CustomSprite cs:project.sprites)if(has(cs.name,q)){labels.add("Sprite · "+cs.name);go.add(()->openSprites(null));}
        for(Prefab pf:project.prefabs)if(has(pf.name,q)){labels.add("Prefab · "+pf.name);go.add(()->{changeMode(POLY);toolPage=0;setTool(PREFAB);curPrefab=pf;prefabRot=0;hint("Apply stamps "+pf.name);});}
        if(labels.isEmpty()){hint("Nothing found for “"+q+"”");return;}
        int max=Math.min(labels.size(),80);String[] items=new String[max];for(int i=0;i<max;i++)items[i]=labels.get(i);
        host.choose(labels.size()+(labels.size()==1?" result":" results")+" for “"+q+"”"+(labels.size()>max?" (first "+max+")":""),items,-1,k->{go.get(k).run();invalidate();});
    }
    private void openContentTab(int tab){save(false);if(host!=null)host.openContent(tab);}
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
            case 0:return level.sectors.size()+(level.sectors.size()==1?" sector":" sectors")+(project.maps.size()>1?" · "+project.maps.size()+" maps":"");
            case 1:return level.things.size()+" placed · "+project.defs.size()+" types";
            case 2:return "First-person editing";
            case 3:return statWarnings==0?"Ready to play":statWarnings+" map warning"+(statWarnings==1?"":"s");
            case 4:return statStates+" states · "+project.defs.size()+" machines";
            case 5:return project.sprites.size()+(project.sprites.size()==1?" custom sprite":" custom sprites");
            case 9:return project.dialogues.size()+(project.dialogues.size()==1?" conversation · ":" conversations · ")+project.quests.size()+(project.quests.size()==1?" quest":" quests");
            case 8:{project.ensureContent();return project.items.size()+" items · "+project.spells.size()+" spells · "+project.classes.size()+" classes";}
            case 7:{int moved=0;for(HudLayout.Elem el:project.hud.e)if(el.custom||el.scale!=1||el.alpha!=1||!el.visible)moved++;return moved==0?"Default layout":moved+" control"+(moved==1?"":"s")+" customised";}
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
        {int r2=(EDITOR_NAMES.length+1)/2;if(cols==2&&(B-gt-gap*(r2-1))/r2<dp(60))cols=3;}   // short screens: 3 columns of compact tiles
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
            Renderer.SpriteDraw d3=sd(pool++).set(th.x,th.y,floorAtLevel(th.x,th.y),project.spriteFor(d,d.sprite,Sprites.F_IDLE,t,0,World.angleDiff((float)Math.atan2(level.playerY-th.y,level.playerX-th.x),th.angle)),-1,false);
            if(project.builtinLook(d,d.sprite))d3.model(d.sprite,Sprites.F_IDLE,Sprites.F_IDLE,0,th.angle);homeSprites.add(d3);}
        renderer.highlight=-1;
        Atmosphere.prepareStatic(renderer,project,project.current(),level,t);
        renderer.fx=null;renderer.render(c,level,level.playerX,level.playerY,Math.min(sec.ceiling-.1f,sec.floor+World.EYE),ang,0,rr,homeSprites);
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
        int n=EDITOR_NAMES.length+1;int cols=w>dp(680)?4:3,rows=(n+cols-1)/cols;
        float gap=dp(10),pw=Math.min(w-dp(40),dp(cols==4?860:640)),tileH=Math.max(dp(48),Math.min(dp(78),(h-dp(24)-dp(82)-(rows-1)*gap)/rows));
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
                float ib=dp(36),ix=rr.left+dp(12),iy=rr.centerY()-ib*.5f;ui.logo(c,ix,iy,ib);
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
        if(mode==POLY&&tool==PREFAB&&!prefabSaving){
            java.util.ArrayList<java.util.ArrayList<float[]>> polys=curPrefab.polys(prefabRot,Math.round(cursorX),Math.round(cursorY));
            p.setStrokeWidth(dp(1.5f));p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));
            for(java.util.ArrayList<float[]> poly:polys){path.reset();for(int j=0;j<poly.size();j++){float[] v=poly.get(j);if(j==0)path.moveTo(sx(v[0]),sy(v[1]));else path.lineTo(sx(v[0]),sy(v[1]));}path.close();
                p.setStyle(Paint.Style.FILL);p.setColor(0x1c3d8bfd);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setColor(Ui.ACCENT);c.drawPath(path,p);}
            p.setPathEffect(null);p.setStyle(Paint.Style.FILL);float[] pt=new float[2];
            for(Prefab.Th t:curPrefab.things){curPrefab.place(t.x,t.y,prefabRot,Math.round(cursorX),Math.round(cursorY),pt);p.setColor(0xaaf2b84b);c.drawCircle(sx(pt[0]),sy(pt[1]),dp(3.5f),p);}
        }
        if(mode==POLY&&stage==1&&(tool==RECT||tool==DUPLICATE||tool==EXTRUDE||tool==PREFAB&&prefabSaving)){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(Ui.ACCENT);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));
            if(tool==RECT||tool==PREFAB){float l=sx(Math.min(anchorX,cursorX)),t=sy(Math.min(anchorY,cursorY)),rr=sx(Math.max(anchorX,cursorX)),b=sy(Math.max(anchorY,cursorY));p.setStyle(Paint.Style.FILL);p.setColor(0x223d8bfd);p.setPathEffect(null);c.drawRect(l,t,rr,b,p);p.setStyle(Paint.Style.STROKE);p.setColor(Ui.ACCENT);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));c.drawRect(l,t,rr,b,p);}
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
            ui.caption(c,(selKind==1?"Sector ":"Under cursor · sector ")+(s+1)+(sec.special!=0?" · "+Level.SPECIAL_NAMES[sec.special]+(sec.special==Level.SP_EXIT&&sec.tag>0?" → "+project.mapName(sec.tag):""):""),l+dp(10),ct+dp(18),Ui.TEXT_3);
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
    private String[] propLabels(EntityDef d){
        if(d.kind==EntityDef.PLAYER)return new String[]{"Name","Type","Sprite","Health","Speed","Radius","Height","Solid","Shootable","Animations","Class","Starting level","Starting gold","Light","Light radius","Particles"};
        return new String[]{"Name","Type","Sprite","Health","Speed","Radius","Height","Solid","Shootable","Animations","Light","Light radius","Particles"};}
    private String propValue(EntityDef d,int i){if(d.kind!=EntityDef.PLAYER&&i>=10)i+=3;switch(i){
        case 13:return Atmosphere.GLOWS[d.glow].replaceAll(" \\(.*\\)","");case 14:return d.glowRadius>0?fmt(d.glowRadius):"Preset";case 15:return Atmosphere.PARTICLES[d.particles];case 0:return d.name;case 1:return EntityDef.KIND_NAMES[d.kind];case 2:{CustomSprite cs=project.sprite(d.customSprite);return cs!=null?cs.name+" (custom)":Sprites.NAMES[Math.max(0,Math.min(Sprites.COUNT-1,d.sprite))];}
        case 10:return Rpg.heroClass(d.heroClass).name;case 11:return String.valueOf(Math.max(1,Math.min(Rpg.MAX_LEVEL,d.level)));case 12:return d.startGold+" gp";
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
        r.set(l,y,l+hw,y+dp(32));ui.button(c,r,Icons.UNDO,"Undo",entityUndo.canUndo()?Ui.NORMAL:Ui.DISABLED);clipHit(r,H_EUNDO,top,bottom);
        r.set(l+hw+dp(6),y,rr,y+dp(32));ui.button(c,r,Icons.REDO,"Redo",entityUndo.canRedo()?Ui.NORMAL:Ui.DISABLED);clipHit(r,H_EREDO,top,bottom);y+=dp(42);
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
            case PREFAB:return prefabSaving?(stage==0?"Area: first corner":"Area: opposite corner"):"Stamp "+curPrefab.name;case STAIRS:return "Make stairs";case SELECT:return "Select";
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
            case PREFAB:prefabSaving=false;hint("Apply stamps "+curPrefab.name+" · tap Prefab again to pick another");break;case STAIRS:hint("Apply on a rectangular sector; stairs rise away from the cursor");break;
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
                if(prefabSaving){
                    if(stage==0){anchorX=cx;anchorY=cy;stage=1;hint("Move to the opposite corner of the area, then Apply");return;}
                    stage=0;Prefab pf=Prefab.capture(level,anchorX,anchorY,cx,cy);
                    if(pf==null){hint("No whole sector inside that area");return;}
                    if(host==null){savePrefab(pf,"Prefab "+(project.prefabs.size()+1));return;}
                    host.prompt("Prefab name",pf.secs.size()+" sectors · "+pf.things.size()+" entities","Prefab "+(project.prefabs.size()+1),false,n->savePrefab(pf,n.trim().isEmpty()?"Prefab":n.trim()));return;}
                stampPrefab(Math.round(cx),Math.round(cy));return;
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
    // ---------------------------------------------------------------- prefabs
    private void savePrefab(Prefab pf,String name){
        pf.name=name;pf.id="prefab_"+System.currentTimeMillis();project.prefabs.add(pf);curPrefab=pf;prefabRot=0;prefabSaving=false;
        autoSave();hint("Saved prefab "+name+" · Apply to stamp it");invalidate();
    }
    private void stampPrefab(float ox,float oy){
        Prefab pf=curPrefab;
        if(!sectorEditor.stamp(pf,prefabRot,ox,oy)){hint(sectorEditor.error);invalidate();return;}
        float[] pt=new float[2];int placed=0,missing=0;
        for(Prefab.Th t:pf.things){if(project.def(t.def)==null){missing++;continue;}
            pf.place(t.x,t.y,prefabRot,ox,oy,pt);Level.Thing th=new Level.Thing(pt[0],pt[1],t.def);th.angle=t.angle+prefabRot*(float)Math.PI*.5f;level.things.add(th);placed++;}
        selKind=0;changed(pf.name+" placed"+(placed>0?" with "+placed+" entit"+(placed==1?"y":"ies"):"")+(missing>0?" · "+missing+" missing entity types skipped":""));
    }
    private void prefabMenu(){
        if(host==null)return;
        java.util.ArrayList<Prefab> all=Prefab.builtins();int nb=all.size();all.addAll(project.prefabs);
        java.util.ArrayList<String> items=new java.util.ArrayList<>();int cur=-1;
        for(int i=0;i<all.size();i++){Prefab pf=all.get(i);boolean on=pf.id!=null&&pf.id.equals(curPrefab.id);if(on)cur=i;
            items.add((i<nb?"Built-in: ":"")+pf.name+"  ("+Math.round(pf.w)+"×"+Math.round(pf.h)+(pf.things.isEmpty()?"":", "+pf.things.size()+" entities")+")");}
        int base=items.size();
        items.add("Turn 90°  (now "+prefabRot*90+"°)");items.add("Save an area of this map as a prefab…");items.add("Rename or delete a saved prefab…");
        host.choose("Prefabs",items.toArray(new String[0]),cur,k->{
            if(k<base){curPrefab=all.get(k);prefabSaving=false;stage=0;hint("Apply stamps "+curPrefab.name+" with its top-left corner at the cursor");}
            else if(k==base){prefabRot=(prefabRot+1)&3;hint(curPrefab.name+" turned to "+prefabRot*90+"°");}
            else if(k==base+1){prefabSaving=true;stage=0;hint("Apply on one corner of the area to save, then the opposite corner");}
            else managePrefabs();
            invalidate();});
    }
    private void managePrefabs(){
        if(project.prefabs.isEmpty()){hint("No saved prefabs yet · use Save an area");return;}
        String[] names=new String[project.prefabs.size()];for(int i=0;i<names.length;i++)names[i]=project.prefabs.get(i).name;
        host.choose("Saved prefabs",names,-1,i->{Prefab pf=project.prefabs.get(i);
            host.choose(pf.name,new String[]{"Rename…","Delete"},-1,a->{
                if(a==0)host.prompt("Rename prefab",null,pf.name,false,n->{if(!n.trim().isEmpty()){pf.name=n.trim();autoSave();invalidate();}});
                else host.confirm("Delete "+pf.name+"?","Maps where it was stamped keep their copy.","Delete",()->{project.prefabs.remove(pf);if(curPrefab==pf){curPrefab=Prefab.builtins().get(0);prefabRot=0;}autoSave();hint("Prefab deleted");});});});
    }
    private void selectSector(int s){selKind=s>=0?1:0;selSector=s;}
    private void chooseSpecial(int s){
        if(host==null){Level.Sector sec=level.sectors.get(s);sec.special=(sec.special+1)%Level.SPECIAL_NAMES.length;changed(Level.SPECIAL_NAMES[sec.special]);return;}
        Level.Sector sec=level.sectors.get(s);
        host.choose("Sector "+(s+1)+" special",Level.SPECIAL_NAMES,sec.special,i->{sec.special=i;
            if(i==Level.SP_DOOR){host.choose("Door needs a key?",Level.KEY_NAMES,sec.tag,k->{sec.tag=k;changed("Door"+(k>0?" ("+Level.KEY_NAMES[k]+")":""));});}
            else if(i==Level.SP_EXIT){project.ensureMaps();java.util.ArrayList<Project.GameMap> others=new java.util.ArrayList<>();for(Project.GameMap gm:project.maps)if(gm.num!=project.currentMap)others.add(gm);
                String[] items=new String[others.size()+2];items[0]="Finish the game (results screen)";for(int k=0;k<others.size();k++)items[k+1]="Go to map: "+others.get(k).name;items[items.length-1]="+ New map to go to…";
                host.choose("Exit leads to",items,0,k->{
                    if(k==0){sec.tag=0;changed("Exit · finishes the game");}
                    else if(k==items.length-1){Project.GameMap gm=project.addMap("Map "+(project.maps.size()+1),Templates.empty());sec.tag=gm.num;changed("Exit → "+gm.name+" (new, empty)");}
                    else{sec.tag=others.get(k-1).num;changed("Exit → "+others.get(k-1).name);}});}
            else{sec.tag=0;changed(Level.SPECIAL_NAMES[i]);}});
    }
    private void changed(String msg){entityUndo.commit();autoSave();if(msg!=null)hint(msg);renderer.invalidate();invalidate();}
    private void closeSector(){if(sectorEditor.addSector(pending)){selectSector(level.sectors.size()-1);pending.clear();changed("Sector created");}else hint(sectorEditor.error);}
    private void sectorAction(int t){
        if(t==PREFAB&&tool==PREFAB){prefabMenu();return;}
        if(t<100){if(tool==t&&stage==0&&t!=DRAW)hint(toolName(t)+" is active");setTool(t);if(t==PREFAB)prefabMenu();return;}
        switch(t){
            case CLOSE:if(pending.size()<3)hint("Add at least three corners first");else closeSector();return;
            case UNDO_PT:if(!pending.isEmpty()){pending.remove(pending.size()-1);hint("Corner removed");}else hint("No corners to remove");return;
            case CANCEL:pending.clear();cancelStage();hint("Cancelled");return;
            case UNDO:case REDO:{cancelStage();java.util.ArrayList<Level.Thing> keep=new java.util.ArrayList<>(level.things);   // placed entities have their own undo
                if(t==UNDO?sectorEditor.undo():sectorEditor.redo()){Level nl=sectorEditor.level;nl.things.clear();nl.things.addAll(keep);nl.things.removeIf(th->nl.sectorAt(th.x,th.y)<0);restoreEditor();hint(t==UNDO?"Undo":"Redo");}else hint(t==UNDO?"Nothing to undo":"Nothing to redo");return;}
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
        if(d.kind!=EntityDef.PLAYER&&i>=10)i+=3;
        switch(i){
            case 13:host.choose("Light for "+d.name,Atmosphere.GLOWS,d.glow,k->{d.glow=k;changed(k==0?"Torches and braziers glow automatically":null);});break;
            case 14:host.prompt("Light radius","0 = the preset's size",fmt(d.glowRadius),true,t->{try{d.glowRadius=Math.max(0,Math.min(20,Float.parseFloat(t)));changed(null);}catch(NumberFormatException ignored){}});break;
            case 15:host.choose("Particles from "+d.name,Atmosphere.PARTICLES,d.particles,k->{d.particles=k;changed(null);});break;
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
            case 10:{String[] items=new String[Rpg.CLASSES.size()];int cur=0;
                for(int k=0;k<items.length;k++){Rpg.HeroClass hc=Rpg.CLASSES.get(k);items[k]=hc.name+" – "+hc.desc;if(hc.name.equalsIgnoreCase(d.heroClass))cur=k;}
                host.choose("Hero class",items,cur,k->{d.heroClass=Rpg.CLASSES.get(k).name;renderer.invalidate();changed("Hero is now a "+d.heroClass+": class gear, spells and look apply in Play");});break;}
            case 11:host.prompt("Starting level","1 to "+Rpg.MAX_LEVEL,String.valueOf(d.level),true,t->{try{d.level=Math.max(1,Math.min(Rpg.MAX_LEVEL,Math.round(Float.parseFloat(t))));changed(null);}catch(NumberFormatException ignored){}});break;
            case 12:host.prompt("Starting gold",null,String.valueOf(d.startGold),true,t->{try{d.startGold=Math.max(0,Math.round(Float.parseFloat(t)));changed(null);}catch(NumberFormatException ignored){}});break;
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
            Renderer.SpriteDraw d3=sd(pool++).set(t.x,t.y,floorAtLevel(t.x,t.y),project.spriteFor(d,d.sprite,Sprites.F_IDLE,walker.time,0,World.angleDiff((float)Math.atan2(cam.y-t.y,cam.x-t.x),t.angle)),Renderer.id(Renderer.ID_SPRITE,i),false);
            if(project.builtinLook(d,d.sprite))d3.model(d.sprite,Sprites.F_IDLE,Sprites.F_IDLE,0,t.angle);spriteList.add(d3);}
        Atmosphere.prepareStatic(renderer,project,project.current(),level,walker.time);
        renderer.fx=null;renderer.render(c,level,cam.x,cam.y,walker.viewZ,cam.angle,cam.pitch,view3d,spriteList);
        // crosshair
        float cx=view3d.centerX(),cy=view3d.centerY();p.setColor(0xccffffff);c.drawCircle(cx,cy,dp(2),p);
        drawTextureBrowser(c,left);
        drawJoystick(c);
        // viewport chips
        float bh=dp(32),top=toolbarH+dp(10),x=w-dp(10);
        float fw2=dp(96);r.set(x-fw2,top,x,top+bh);ui.button(c,r,fly?Icons.LAYERS:Icons.ENTITIES,fly?"Fly":"Walk",fly?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_FLY);x-=fw2+dp(8);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.FLAG,null,Ui.NORMAL);ui.hit(r,H_START3D);x-=bh+dp(8);
        float pw2=dp(118);r.set(x-pw2,top,x,top+bh);ui.button(c,r,Icons.PLAY,"Play here",Ui.SUCCESS);ui.hit(r,H_PLAYHERE);
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
    private void moveJoystick(float x,float y){float dx=x-joyOriginX,dy=y-joyOriginY,len=(float)Math.hypot(dx,dy),limit=(mode==PLAY?project.hud.radius(HudLayout.JOYSTICK,ui.dp):dp(56))*.68f,scale=len>limit?limit/len:1;
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
    // ---------------------------------------------------------------- game front end (splash, main menu, options, pause)
    private GameMenu menu;
    private GameMenu menu(){
        if(menu==null)menu=new GameMenu(getContext(),ui,project,renderer,sfx,new GameMenu.Listener(){
            @Override public void newGame(){startPlay();hint("Left: move · right: look · Attack, Cast, Use and Potion on the right · Bag top right");}
            @Override public void resume(){if(world==null)startPlay();else{world.paused=bagOpen;}}
            @Override public void restart(){if(world!=null)startPlay(world.mapNum);else startPlay();}
            @Override public boolean saveGame(String slot){return world!=null&&GameSave.save(getContext(),project,world,slot);}
            @Override public boolean loadGame(String slot){
                project.syncCurrent();World w=GameSave.load(getContext(),project,sfx,slot);if(w==null)return false;
                sfx.start();world=w;bagOpen=false;mapFade=1f;for(int i=0;i<buttonPointer.length;i++)buttonPointer[i]=-1;joyPointer=lookPointer=-1;joyVisible=false;
                startCard();return true;}
            @Override public void exitToEditor(){leavePlay();}
        });
        return menu;
    }
    private boolean frontEnd(){GameSettings.load(getContext());return playerMode||GameSettings.frontEnd;}
    /** Player mode: the project was opened with Play game, so only the game is shown and leaving goes to the title. */
    boolean playerMode;
    void startPlayerMode(){playerMode=true;menu().playerMode=true;project.syncCurrent();changeMode(PLAY);}
    private void leavePlay(){
        if(playerMode){sfx.stopMusic(0);sfx.stopSounds();sfx.stop();world=null;if(menu!=null)menu.state=GameMenu.OFF;if(onExitToTitle!=null)onExitToTitle.run();return;}
        changeMode(returnMode);
    }
    /** In-game Back / close button: the pause menu (or straight back to the editor when the front end is off). */
    private void pauseToMenu(){
        if(!frontEnd()||world==null){leavePlay();return;}
        world.paused=true;world.input.clear();for(int i=0;i<buttonPointer.length;i++)buttonPointer[i]=-1;joyPointer=lookPointer=-1;joyVisible=false;moveX=moveY=0;
        menu().openPause();
    }
    private float mapFade;private int playThisMap=-1;private final Atmosphere.Weather weather=new Atmosphere.Weather();
    private void startPlay(){startPlay(project.startMap);}
    // story screens (UI Editor → Screens): title card on each map start, death screen
    private float cardTime=-1,deathTime;private final RectF sr=new RectF();
    private static final int H_DRETRY=547,H_DLOAD=548,H_DBACK=549;
    private void startCard(){if(world==null)return;cardTime=0;if(project.screens.cardStyle==ScreenStyle.CARD_OFF)world.centerPrint(project.mapName(world.mapNum),2f);}
    private void drawStoryScreens(Canvas c,float dt){
        ScreenStyle ss=project.screens;Actor pl=world.player;
        if(cardTime>=0&&!world.levelComplete){cardTime+=dt;Project.GameMap gm=project.map(world.mapNum);
            if(!ss.drawCard(c,p,ui,project.theme,w,h,project.mapName(world.mapNum),gm==null?"":gm.card,project.maps.indexOf(gm),cardTime))cardTime=-1;else nextFrame();}
        if(pl!=null&&!pl.alive&&ss.death&&!world.levelComplete){deathTime+=dt;if(deathTime>ss.deathDelay)world.centerTime=0;   // the death screen replaces "You have fallen" text
            ss.drawDeath(c,p,ui,project.theme,w,h,deathTime,deathSave!=null,frontEnd()?"Main menu":playerMode?"Quit":"Back to editor",H_DRETRY,H_DLOAD,H_DBACK,sr);nextFrame();}
        else{deathTime=0;}
        if(pl!=null&&pl.alive)deathSave=null;else if(deathTime>0&&deathTime<dt*1.5f)deathSave=GameSave.latest(getContext(),project);
    }
    private GameSave.Info deathSave;
    String diagState(){if(world==null)return "";int alive=0;for(Actor a:world.actors)if(a.alive&&!a.removed&&!a.isPlayer)alive++;
        return renderer.stats()+", actors "+world.actors.size()+" ("+alive+" alive), drops "+world.drops.size()+", splats "+world.splats.size()+", particles "+(world.particles==null?0:world.particles.n);}
    private static final int H_POPUP=560;
    /** Show Popup action: a themed box with the title, wrapped text and one or two buttons. */
    private void drawPopup(Canvas c){
        World.Popup pp=world.popup;UiTheme th=project.theme;float D=ui.dp;
        ui.rect(c,0,0,w,h,0x80000000);
        float pw=Math.min(w-dp(40),dp(440));ArrayList<String> lines=wrap(pp.text,14,1,pw-dp(44));while(lines.size()>8)lines.remove(lines.size()-1);
        boolean hasTitle=!pp.title.isEmpty();float ph=dp(24)+(hasTitle?dp(34):0)+lines.size()*dp(21)+dp(70),pl=(w-pw)*.5f,pt=(h-ph)*.45f;
        ui.round(c,pl,pt+dp(4),pl+pw,pt+ph+dp(4),dp(14),0x80000000);th.panel(c,p,pl,pt,pl+pw,pt+ph,D,.98f,false);
        float y=pt+dp(24);
        if(hasTitle){ui.icon(c,Icons.SCROLL,pl+dp(30),y+dp(8),18,th.accentOnPanel());ui.text(c,ui.ellipsize(pp.title,18,2,pw-dp(70)),pl+dp(48),y+dp(15),18,th.accentOnPanel(),2,-1);y+=dp(34);}
        for(String ln:lines){y+=dp(21);ui.text(c,ln,pl+dp(22),y-dp(4),14,th.text,1,-1);}
        float bw=Math.min(dp(160),(pw-dp(44)-(pp.buttons.length-1)*dp(10))/pp.buttons.length),by=pt+ph-dp(56),bx=pl+pw-dp(22)-pp.buttons.length*bw-(pp.buttons.length-1)*dp(10);
        for(int i=0;i<pp.buttons.length;i++){r.set(bx,by,bx+bw,by+dp(40));th.rectButton(c,p,ui,r,-1,pp.buttons[i],i==0?UiTheme.BTN_PRIMARY:UiTheme.BTN_NORMAL,D);ui.hit(r,H_POPUP+i);bx+=bw+dp(10);}
    }
    private boolean deathTap(int hid){
        if(world==null||world.player==null||world.player.alive||!project.screens.death||deathTime<project.screens.deathDelay+.8f)return false;
        if(hid==H_DRETRY){startPlay(world.mapNum);startCard();return true;}
        if(hid==H_DLOAD&&deathSave!=null){if(!menu().listenerLoad(deathSave.slot))hint("That save couldn't be loaded");return true;}
        if(hid==H_DBACK){backFromEnd();return true;}
        return true;   // swallow other taps while the death screen is up
    }
    private void backFromEnd(){if(frontEnd()){world=null;menu().paused=false;menu().state=GameMenu.MAIN;}else leavePlay();}
    private void startPlay(int mapNum){bagOpen=false;mapFade=0;cardTime=-1;deathTime=0;deathSave=null;world=new World(project,sfx,mapNum,null,"");sfx.start();for(int i=0;i<buttonPointer.length;i++)buttonPointer[i]=-1;joyPointer=lookPointer=-1;joyVisible=false;}
    private void drawPlay(Canvas c,float dt){
        if(menu!=null&&menu.active()){menu.draw(c,w,h,dt);nextFrame();return;}
        if(world==null)startPlay();
        if(world.restartRequested){startPlay(world.mapNum);hint("Level restarted");}
        Input in=world.input;
        in.moveX=joyVisible?moveX:0;in.moveY=joyVisible?moveY:0;
        world.tick(dt);
        if(world.pendingMap>0){   // exit sector or Go To Map: load the next map, carrying the hero over
            World next=new World(project,sfx,world.pendingMap,world,world.pendingArrival);world=next;mapFade=1f;bagOpen=false;
            startCard();in=world.input;
            GameSave.save(getContext(),project,world,"auto");}   // autosave on every map change
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
                s=project.actorSprite(a,f,world.time,view);
                Renderer.SpriteDraw d3=sd(pool++).set(a.x,a.y,a.z+a.bob,s,-1,bright);if(project.builtinLook(a.def,a.sprite))project.modelPose(d3,a,f);spriteList.add(d3);continue;}
            spriteList.add(sd(pool++).set(a.x,a.y,a.z+a.bob-(a.projectileType>=0?.2f:0),s,-1,bright));}
        for(World.Effect e:world.effects){int f=Math.min(2,(int)(e.t/e.life*3));spriteList.add(sd(pool++).set(e.x,e.y,e.z-.2f,Sprites.effect(e.type,f,true),-1,e.type!=Sprites.FX_BLOOD));}
        view3d.set(0,0,w,h);
        renderer.highlight=-1;
        // atmosphere: map fog/tint, entity and projectile lights, particles
        Atmosphere.apply(renderer,world.fog,world.tint);renderer.clearLights();
        for(Actor a:world.actors){if(a.removed)continue;
            if(a.projectileType>=0){renderer.addLight(a.x,a.y,a.z,3.5f,a.projectileType==2?.35f:1f,a.projectileType==2?.55f:.55f,a.projectileType==2?1f:.2f);continue;}
            if(a.def==null||(!a.visible&&!a.isPlayer))continue;float[] g=Atmosphere.glowFor(a.def,a.sprite,a.lightOverride);if(g!=null)Atmosphere.addLight(renderer,a.x,a.y,a.z+a.height*.8f,g,world.time,a.thingIndex*7+3);}
        renderer.particles=world.particles;
        renderer.fx=world;renderer.renderScene(world.level,cx,cy,world.camZ(),world.camAngle(),world.camPitch(),w,h,spriteList);
        if(pl.alive&&!tp){float b=world.bob();int si=world.sectorIndex(pl);renderer.weapon(world.weapon,(float)Math.cos(world.bobPhase*Math.PI*.5f)*Math.min(1,(float)Math.hypot(pl.vx,pl.vy)/6f),b*8,world.weaponRaise,world.weaponFlash>0,si<0?160:world.level.sectors.get(si).light);}
        if(world.damageFlash>0)renderer.tint(0xffff2020,world.damageFlash*.6f);
        if(world.pickupFlash>0)renderer.tint(world.flashColor,world.pickupFlash*.35f);
        if(!pl.alive)renderer.tint(0xff800000,.35f);
        renderer.present(c,view3d);
        Diag.noteState(getContext(),this);
        if(world.weather>0)weather.draw(c,p,world.weather,w,h,Math.min(dt,.05f),world.camAngle(),ui.dp);
        if(mapFade>0){ui.rect(c,0,0,w,h,((int)(Math.min(1,mapFade)*255))<<24);mapFade-=dt*2.2f;}
        if(tp&&pl.alive){float mx=w*.5f,my=h*.5f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(0xccffffff);
            c.drawCircle(mx,my,dp(6),p);p.setStyle(Paint.Style.FILL);c.drawCircle(mx,my,dp(1.5f),p);}
        drawFloaters(c);
        hud().drawStatus(c,w,h,world,false);
        if(world.menuRequested){world.menuRequested=false;openBag();}
        drawStoryScreens(c,dt);
        if(world.popup!=null)drawPopup(c);
        if(world.levelComplete)drawComplete(c);
        else if(world.shop!=null)drawShop(c);
        else if(bagOpen)drawBag(c);
        else if(world.dialogue!=null)drawDialogue(c);
        else drawPlayControls(c);
        if(!playerMode)drawDebugLayer(c,dt);
        nextFrame();
    }
    // ---------------------------------------------------------------- testing tools (editor testing only)
    private static final int H_DEBUG=545,H_PLAYHERE=546;
    private PlayDebug debug;private float fpsAvg=60;private float[] playFrom;
    private PlayDebug debug(){
        if(debug==null)debug=new PlayDebug(ui,project,new PlayDebug.Host(){
            @Override public UiHost ui(){return host;}
            @Override public void restartMap(){if(world!=null)startPlay(world.mapNum);}
            @Override public void hint(String s){EditorView.this.hint(s);}
        });
        return debug;
    }
    private void drawDebugLayer(Canvas c,float dt){
        if(dt>0)fpsAvg=fpsAvg*.92f+(1f/Math.max(.001f,dt))*.08f;
        if(PlayDebug.showStates)for(Actor a:world.actors){if(a.isPlayer||a.removed||a.fsm==null||a.fsm.state==null||a.def==null)continue;
            if(Math.hypot(a.x-world.player.x,a.y-world.player.y)>18||!renderer.project(a.x,a.y,a.z+a.height+.35f,view3d,proj))continue;
            String s=a.fsm.state.name;float tw=ui.measure(s,10,1)+dp(12);ui.round(c,proj[0]-tw*.5f,proj[1]-dp(9),proj[0]+tw*.5f,proj[1]+dp(9),dp(9),0xc0101318);ui.textMid(c,s,proj[0],proj[1],10,0xff8ad06a,1,0);}
        if(PlayDebug.showFps)ui.text(c,Math.round(fpsAvg)+" fps",w*.5f+dp(52),dp(26),11,0xffffff80,2,-1);
        boolean busy=world.levelComplete||world.shop!=null||bagOpen||world.dialogue!=null;
        if(debug().open)debug.draw(c,w,h,world);
        else if(!busy){r.set(w*.5f-dp(40),dp(10),w*.5f+dp(40),dp(36));ui.round(c,r,dp(13),0xa08a3030);ui.textMid(c,"DEBUG",r.centerX(),r.centerY(),10.5f,0xffffffff,2,0);ui.hit(r.left-dp(6),r.top-dp(6),r.right+dp(6),r.bottom+dp(6),H_DEBUG);}
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
    // ---------------------------------------------------------------- HUD from the UI Editor layout
    private GameHud hud;private final boolean[] hudDown=new boolean[HudLayout.N];
    private GameHud hud(){
        if(hud==null){hud=new GameHud(ui,project.hud);hud.hitIds=new int[]{H_FIRE,H_SPELL,H_JUMP,H_USE,H_POTION,H_BAG,H_EXITPLAY,-1,-1,-1,-1};}
        hud.layout=project.hud;hud.theme=project.theme;return hud;
    }
    private void drawPlayControls(Canvas c){
        hudDown[HudLayout.ATTACK]=buttonPointer[Input.FIRE]>=0;hudDown[HudLayout.CAST]=buttonPointer[Input.SPELL]>=0;hudDown[HudLayout.JUMP]=buttonPointer[Input.JUMP]>=0;
        hudDown[HudLayout.USE]=buttonPointer[Input.USE]>=0;hudDown[HudLayout.POTION]=buttonPointer[Input.POTION]>=0;
        GameHud g=hud();
        // joystick: floating fades out after release; a fixed one always shows its base
        if(g.layout.joyFixed){float[] jc=g.joyCenter(w,h);
            if(joyVisible)g.drawJoystick(c,joyOriginX,joyOriginY,joyX,joyY,1f);else g.drawJoystick(c,jc[0],jc[1],jc[0],jc[1],.55f);}
        else{float alpha=joyVisible?1f:Math.max(0f,1f-(System.currentTimeMillis()-joyReleaseTime)/240f);
            if(alpha>0)g.drawJoystick(c,joyOriginX,joyOriginY,joyX,joyY,alpha);if(!joyVisible&&alpha>0)postInvalidateDelayed(32);}
        g.drawControls(c,w,h,world.hero,hudDown,false);
    }
    /** A labelled bar (HP, SP, XP). */
    private void bar(Canvas c,float l,float t,float r,float b,float frac,int col,String text){
        ui.round(c,l,t,r,b,(b-t)*.5f,0xff0e1013);float f=Math.max(0,Math.min(1,frac));
        if(f>0)ui.round(c,l,t,l+Math.max(b-t,(r-l)*f),b,(b-t)*.5f,col);
        ui.outline(c,l,t,r,b,(b-t)*.5f,0x30ffffff,dp(1));
        if(text!=null)ui.textMid(c,text,(l+r)*.5f,(t+b)*.5f,10,0xffffffff,2,0);
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
        UiTheme th=project.theme;
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(14),0x80000000);th.panel(c,p,L,T,R,B,ui.dp,.97f,false);
        ui.round(c,L+dp(16),T-dp(14),L+dp(16)+ui.measure(d.speaker,13,2)+dp(28),T+dp(14),dp(14),UiTheme.shade(th.accent,.42f));ui.outline(c,L+dp(16),T-dp(14),L+dp(16)+ui.measure(d.speaker,13,2)+dp(28),T+dp(14),dp(14),th.accent,dp(1.2f));
        ui.textMid(c,d.speaker,L+dp(30),T,13,0xffffffff,2,-1);
        float y=T+dp(36);for(String ln:lines){ui.text(c,ln,L+dp(22),y,14,th.text,1,-1);y+=dp(21);}
        y+=dp(6);
        for(int i=0;i<d.choices.length;i++){r.set(L+dp(18),y,R-dp(18),y+ah);th.card(c,p,ui,r,false,ui.dp);
            ui.textMid(c,(i+1)+".",r.left+dp(16),r.centerY(),13,th.accentOnPanel(),2,-1);ui.textMid(c,ui.ellipsize(d.choices[i],13.5f,1,r.width()-dp(56)),r.left+dp(36),r.centerY(),13.5f,th.text,1,-1);
            ui.hit(r,H_DLG+i);y+=ah+gap;}
    }
    /** Character sheet, inventory, spells and quest journal (the game is paused). */
    private void drawBag(Canvas c){
        Hero hero=world.hero;if(hero==null){closeBag();return;}
        UiTheme th=project.theme;float D=ui.dp;
        th.backdrop(c,p,w,h,D);
        float pad=dp(16),L=pad,R=w-pad,T=pad,B=h-dp(52)-pad;
        // header: tabs + close
        String[] tabs={"Inventory","Spells","Journal"};float tx=L;
        ui.text(c,hero.cls.name+" · level "+hero.level,L,T+dp(18),17,th.text,2,-1);
        float hx=L+ui.measure(hero.cls.name+" · level "+hero.level,17,2)+dp(24);
        for(int i=0;i<tabs.length;i++){float tw2=ui.measure(tabs[i],12.5f,1)+dp(30);r.set(hx,T,hx+tw2,T+dp(32));th.rectButton(c,p,ui,r,-1,tabs[i],bagTab==i?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,D);ui.hit(r,H_INV_TAB+i);hx+=tw2+dp(6);}
        r.set(R-dp(40),T,R,T+dp(32));th.rectButton(c,p,ui,r,Icons.CANCEL,null,UiTheme.BTN_NORMAL,D);ui.hit(r,H_INV_CLOSE);
        float top=T+dp(46);
        // left column: character sheet
        float cw=Math.min(dp(250),w*.3f);
        th.panel(c,p,L,top,L+cw,B,D,.97f,false);
        float y=top+dp(22),x=L+dp(14);
        ui.text(c,"CHARACTER",x,y,9.5f,th.textFaint(),2,-1);y+=dp(18);
        th.bar(c,p,ui,x,y,L+cw-dp(14),y+dp(14),world.player.health/(float)Math.max(1,hero.maxHp),0xffb8282a,world.player.health+" / "+hero.maxHp+" HP",D,1);y+=dp(19);
        if(hero.maxSp>0){th.bar(c,p,ui,x,y,L+cw-dp(14),y+dp(14),hero.sp/(float)Math.max(1,hero.maxSp),0xff3a5ad0,hero.sp+" / "+hero.maxSp+" SP",D,1);y+=dp(19);}
        th.bar(c,p,ui,x,y,L+cw-dp(14),y+dp(10),hero.xpFraction(),0xffd8a830,null,D,1);y+=dp(22);
        ui.text(c,hero.xp+" / "+Rpg.xpForNext(hero.level)+" XP",x,y,10.5f,th.textDim(),1,-1);y+=dp(20);
        Rpg.Item wpn=hero.weaponOrFists();
        String[][] rows={{"Armour class",String.valueOf(hero.ac())},{"Attack",Rpg.signed(hero.attackBonus(wpn))+" · "+wpn.damage+Rpg.signed(hero.damageBonus(wpn))},{"Proficiency",Rpg.signed(hero.prof())},{"Gold",String.valueOf(hero.gold)}};
        for(String[] rw:rows){ui.text(c,rw[0],x,y,11.5f,th.textDim(),1,-1);ui.text(c,rw[1],L+cw-dp(14),y,11.5f,th.text,2,1);y+=dp(18);}
        y+=dp(6);float aw=(cw-dp(28))/3f;
        for(int i=0;i<6;i++){float ax=x+(i%3)*aw,ay=y+(i/3)*dp(44);r.set(ax+dp(2),ay,ax+aw-dp(2),ay+dp(40));th.card(c,p,ui,r,false,D);
            ui.textMid(c,Rpg.ABILITIES[i],r.centerX(),ay+dp(10),8.5f,th.textFaint(),2,0);ui.textMid(c,Rpg.signed(hero.mod(i)),r.centerX(),ay+dp(24),14,th.text,2,0);ui.textMid(c,String.valueOf(hero.scores[i]),r.centerX(),ay+dp(35),8.5f,th.textFaint(),1,0);}
        // right: tab content
        float l2=L+cw+dp(12),r2=R;
        th.panel(c,p,l2,top,r2,B,D,.97f,false);
        int save=c.save();c.clipRect(l2,top,r2,B);
        float rowH=dp(52),yy=top+dp(10)-bagScroll;
        if(bagTab==0){
            if(hero.inventory.isEmpty())ui.text(c,"Your pack is empty.",l2+dp(16),top+dp(30),13,th.textFaint(),1,-1);
            for(int i=0;i<hero.inventory.size();i++){Hero.Stack st=hero.inventory.get(i);Rpg.Item it=st.item;
                r.set(l2+dp(10),yy,r2-dp(10),yy+rowH-dp(6));boolean eq=hero.isEquipped(it)&&(i==firstIndex(hero,it));
                th.card(c,p,ui,r,eq,D);
                if(it.sprite>=0){Bitmap tb=thumbFor(it.sprite);float thH=rowH-dp(16),tw=Math.min(thH*1.4f,thH*tb.getWidth()/(float)Math.max(1,tb.getHeight()));
                    RectF d=new RectF(r.left+dp(8)+(thH*1.4f-tw)*.5f,r.top+dp(5),r.left+dp(8)+(thH*1.4f-tw)*.5f+tw,r.top+dp(5)+thH);c.drawBitmap(tb,null,d,null);}
                float ix=r.left+dp(8)+(rowH-dp(16))*1.4f+dp(10);
                ui.text(c,it.name+(st.count>1?"  ×"+st.count:""),ix,r.top+dp(19),13,th.text,2,-1);
                ui.text(c,ui.ellipsize(Rpg.ITEM_KINDS[it.kind]+" · "+it.stats(),10.5f,1,r.right-ix-dp(90)),ix,r.top+dp(36),10.5f,th.textFaint(),1,-1);
                String act=it.kind==Rpg.I_POTION?"Drink":it.kind==Rpg.I_SCROLL?"Read":it.kind<=Rpg.I_SHIELD?(eq?"Equipped":"Equip"):"";
                if(!act.isEmpty()){float bw2=ui.measure(act,11.5f,1)+dp(24);RectF b2=new RectF(r.right-dp(8)-bw2,r.centerY()-dp(14),r.right-dp(8),r.centerY()+dp(14));th.rectButton(c,p,ui,b2,-1,act,eq?UiTheme.BTN_ACTIVE:UiTheme.BTN_PRIMARY,D);}
                if(r.bottom>top&&r.top<B)ui.hit(Math.max(l2,r.left),Math.max(top,r.top),r.right,Math.min(B,r.bottom),H_INV_ITEM+i);
                yy+=rowH;}
        }else if(bagTab==1){
            if(hero.spells.isEmpty())ui.text(c,hero.cls.name+"s don't cast spells.",l2+dp(16),top+dp(30),13,th.textFaint(),1,-1);
            for(int i=0;i<hero.spells.size();i++){Rpg.Spell sp=hero.spells.get(i);boolean sel=i==hero.selectedSpell;
                r.set(l2+dp(10),yy,r2-dp(10),yy+rowH-dp(6));th.card(c,p,ui,r,sel,D);
                ui.icon(c,Icons.SPARKLE,r.left+dp(22),r.centerY(),18,sel?th.accentOnPanel():th.textDim());
                ui.text(c,sp.name+"  ·  "+(sp.level==0?"cantrip":"level "+sp.level+" · "+Rpg.spellCost(sp.level)+" SP"),r.left+dp(42),r.top+dp(19),13,th.text,2,-1);
                ui.text(c,ui.ellipsize(sp.desc+" ("+sp.dice+" "+Rpg.DAMAGE_TYPES[sp.damageType].toLowerCase()+")",10.5f,1,r.width()-dp(56)),r.left+dp(42),r.top+dp(36),10.5f,th.textFaint(),1,-1);
                ui.hit(r,H_INV_SPELL+i);yy+=rowH;}
            if(!hero.spells.isEmpty())ui.text(c,"Tap a spell to ready it for the CAST button. Save DC "+hero.saveDC()+" · spell attack "+Rpg.signed(hero.spellAttack()),l2+dp(14),yy+dp(16),10.5f,th.textFaint(),1,-1);
        }else{
            if(world.quests.isEmpty())ui.text(c,"No quests yet. Talk to people you meet.",l2+dp(16),top+dp(30),13,th.textFaint(),1,-1);
            for(World.Quest q:world.quests.values()){if(q.state==World.Q_INACTIVE)continue;
                r.set(l2+dp(10),yy,r2-dp(10),yy+rowH-dp(6));th.card(c,p,ui,r,false,D);
                int col=q.state==World.Q_DONE?Ui.GREEN:q.state==World.Q_FAILED?Ui.RED:th.accentOnPanel();
                ui.icon(c,q.state==World.Q_DONE?Icons.CHECK:q.state==World.Q_FAILED?Icons.CANCEL:Icons.SCROLL,r.left+dp(22),r.centerY(),16,col);
                ui.text(c,q.title+(q.state==World.Q_DONE?"  (complete)":q.state==World.Q_FAILED?"  (failed)":""),r.left+dp(42),r.top+dp(19),13,th.text,2,-1);
                ui.text(c,ui.ellipsize(q.text,10.5f,1,r.width()-dp(56)),r.left+dp(42),r.top+dp(36),10.5f,th.textFaint(),1,-1);yy+=rowH;}
        }
        c.restoreToCount(save);
        if(System.currentTimeMillis()<bagNoteUntil){float nw=ui.measure(bagNote,12.5f,1)+dp(40);ui.pill(c,w*.5f-nw*.5f,B-dp(48),w*.5f+nw*.5f,B-dp(14),0xf2262c34);ui.textMid(c,bagNote,w*.5f,B-dp(31),12.5f,0xffe8ecf1,1,0);postInvalidateDelayed(200);}
    }
    // ---------------------------------------------------------------- shop (Open Shop action)
    private static final int H_SHOP_CLOSE=3800,H_SHOP_TAB=3810,H_SHOP_ROW=3900;
    private int shopTab,shopDownHit=-1,debugDownHit=-1;private float shopScroll;
    private void drawShop(Canvas c){
        Hero hero=world.hero;Rpg.Shop sh=world.shop;if(hero==null){world.closeShop();return;}
        UiTheme th=project.theme;float D=ui.dp;
        ui.rect(c,0,0,w,h,0x99000000);
        float pw=Math.min(w-dp(32),dp(640)),L=(w-pw)*.5f,R=L+pw,T=dp(16),B=h-dp(16);
        th.panel(c,p,L,T,R,B,D,.97f,false);
        ui.text(c,ui.ellipsize(sh.name,18,2,pw-dp(260)),L+dp(20),T+dp(32),18,th.accentOnPanel(),2,-1);
        if(sh.greeting!=null&&!sh.greeting.isEmpty())ui.text(c,ui.ellipsize(sh.greeting,11.5f,1,pw-dp(260)),L+dp(20),T+dp(50),11.5f,th.textDim(),1,-1);
        String g=hero.gold+" gold";ui.icon(c,Icons.COIN,R-dp(160)-ui.measure(g,14,2),T+dp(28),16,0xffffd040);ui.text(c,g,R-dp(150),T+dp(33),14,th.lightPanel()?0xff8a5a10:0xffffd040,2,1);
        String[] tabs={"Buy","Sell"};
        r.set(R-dp(52),T+dp(12),R-dp(14),T+dp(48));th.rectButton(c,p,ui,r,Icons.CANCEL,null,UiTheme.BTN_NORMAL,D);ui.hit(r,H_SHOP_CLOSE);
        float tx=L+dp(20),ty=T+dp(62);
        for(int i=0;i<2;i++){r.set(tx+i*dp(96),ty,tx+i*dp(96)+dp(90),ty+dp(32));th.rectButton(c,p,ui,r,i==0?Icons.PACKAGE:Icons.COIN,tabs[i],shopTab==i?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,D);ui.hit(r,H_SHOP_TAB+i);}
        float top=ty+dp(42),bottom=B-dp(12),rowH=dp(54);
        int save=c.save();c.clipRect(L+dp(8),top,R-dp(8),bottom);
        float y=top-shopScroll;int rows=0;
        if(shopTab==0){
            for(int i=0;i<sh.stock.size();i++){Rpg.ShopEntry e=sh.stock.get(i);Rpg.Item it=Rpg.item(e.item);if(it==null)continue;rows++;
                int left=world.stockLeft(sh,i),price=Rpg.price(e);boolean can=left!=0&&hero.gold>=price;
                shopRow(c,th,L,R,y,rowH,it,(left>0?left+" left · ":left==0?"sold out · ":"")+Rpg.ITEM_KINDS[it.kind]+" · "+it.stats(),price+" gold",left==0?"Sold out":"Buy",can,H_SHOP_ROW+i,top,bottom);y+=rowH;}
            if(rows==0)ui.text(c,"Nothing for sale.",L+dp(24),top+dp(24),13,th.textFaint(),1,-1);
        }else{
            java.util.ArrayList<Rpg.Item> seen=new java.util.ArrayList<>();
            for(int i=0;i<hero.inventory.size();i++){Rpg.Item it=hero.inventory.get(i).item;if(seen.contains(it)||it.kind==Rpg.I_KEY)continue;seen.add(it);rows++;
                int price=world.sellPrice(sh,it),n=hero.count(it);boolean eq=hero.isEquipped(it)&&n<=1;
                shopRow(c,th,L,R,y,rowH,it,(n>1?"×"+n+" · ":"")+(eq?"equipped · ":"")+it.stats(),price>0?price+" gold":"—",eq?"In use":"Sell",price>0&&!eq,H_SHOP_ROW+i,top,bottom);y+=rowH;}
            if(rows==0)ui.text(c,"You have nothing to sell.",L+dp(24),top+dp(24),13,th.textFaint(),1,-1);
        }
        c.restoreToCount(save);
        float max=Math.max(0,rows*rowH-(bottom-top));if(shopScroll>max)shopScroll=max;
        if(System.currentTimeMillis()<bagNoteUntil){float nw=ui.measure(bagNote,12.5f,1)+dp(40);ui.pill(c,w*.5f-nw*.5f,B-dp(52),w*.5f+nw*.5f,B-dp(18),0xf2262c34);ui.textMid(c,bagNote,w*.5f,B-dp(35),12.5f,0xffe8ecf1,1,0);postInvalidateDelayed(200);}
    }
    private void shopRow(Canvas c,UiTheme th,float L,float R,float y,float rowH,Rpg.Item it,String sub,String price,String act,boolean enabled,int hid,float top,float bottom){
        float D=ui.dp;r.set(L+dp(16),y,R-dp(16),y+rowH-dp(6));th.card(c,p,ui,r,false,D);
        if(it.sprite>=0){Bitmap tb=thumbFor(it.sprite);float s=rowH-dp(18),tw=Math.min(s*1.3f,s*tb.getWidth()/(float)Math.max(1,tb.getHeight()));c.drawBitmap(tb,null,new RectF(r.left+dp(8),r.top+dp(6),r.left+dp(8)+tw,r.top+dp(6)+s),null);}
        float ix=r.left+dp(56),bw=dp(84);
        ui.text(c,ui.ellipsize(it.name,13,2,r.right-ix-bw-dp(90)),ix,r.top+dp(19),13,th.text,2,-1);
        ui.text(c,ui.ellipsize(sub,10.5f,1,r.right-ix-bw-dp(90)),ix,r.top+dp(36),10.5f,th.textFaint(),1,-1);
        ui.text(c,price,r.right-bw-dp(18),r.centerY()+dp(5),12.5f,th.lightPanel()?0xff8a5a10:0xffffd040,2,1);
        RectF b=new RectF(r.right-bw-dp(8),r.centerY()-dp(15),r.right-dp(8),r.centerY()+dp(15));th.rectButton(c,p,ui,b,-1,act,enabled?UiTheme.BTN_PRIMARY:UiTheme.BTN_NORMAL,D);
        if(enabled&&b.bottom>top&&b.top<bottom)ui.hit(b,hid);
    }
    private void shopTap(int hid){
        Rpg.Shop sh=world.shop;if(sh==null)return;
        if(hid==H_SHOP_CLOSE){world.closeShop();return;}
        if(hid>=H_SHOP_TAB&&hid<H_SHOP_TAB+2){shopTab=hid-H_SHOP_TAB;shopScroll=0;return;}
        if(hid>=H_SHOP_ROW&&hid<H_SHOP_ROW+400){int i=hid-H_SHOP_ROW;
            if(shopTab==0)bagNote=world.buy(sh,i);
            else if(world.hero!=null&&i<world.hero.inventory.size())bagNote=world.sell(sh,world.hero.inventory.get(i).item);
            bagNoteUntil=System.currentTimeMillis()+2000;}
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
        String hero=world.hero==null?null:"Level "+world.hero.level+" "+world.hero.cls.name.toLowerCase()+" · "+world.hero.xp+" XP · "+world.hero.gold+" gold";
        String[] rows={"Foes slain","Treasure","Secrets","Time"};
        String[] vals={pct(world.kills,world.totalKills),pct(world.items,world.totalItems),pct(world.secrets,world.totalSecrets),String.format(java.util.Locale.US,"%d:%02d",(int)(world.time-world.completeTime)/60,(int)(world.time-world.completeTime)%60)};
        project.screens.drawVictory(c,p,ui,project.theme,w,h,hero,rows,vals,frontEnd()?"Main menu":playerMode?"Quit":"Back to editor",H_RETRY,H_BACK,r);
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
    private void resetLevel(){project.level=new Level();level=project.level;sectorEditor=new SectorEditor(level);entityUndo.reset();pending.clear();selKind=0;walker=null;cancelStage();changed("Level reset");}
    private void changeMode(int next){
        if(mode==PLAY&&next!=PLAY){sfx.stopMusic(0);sfx.stopSounds();sfx.stop();world=null;if(menu!=null)menu.state=GameMenu.OFF;}
        if(next==PLAY&&mode!=PLAY)returnMode=mode;
        if(next==HOME)save(false);
        launcherOpen=false;
        cancelStage();entStage=0;
        mode=next;pending.clear();joyVisible=false;joyPointer=lookPointer=upPointer=downPointer=-1;moveX=moveY=0;flyUp=flyDown=false;dpadPointer=-1;
        lastFrame=System.nanoTime();
        if(next==PLAY){project.syncCurrent();if(playThisMap>0){int m=playThisMap;playThisMap=-1;if(menu!=null)menu.state=GameMenu.OFF;startPlay(m);
                if(playFrom!=null&&world.level.sectorAt(playFrom[0],playFrom[1])>=0){world.player.x=playFrom[0];world.player.y=playFrom[1];world.player.angle=playFrom[2];world.player.z=world.floorAt(playFrom[0],playFrom[1]);world.syncView();}
                playFrom=null;}
            else if(frontEnd()){world=null;menu().begin();}else startPlay(project.currentMap);}
        setKeepScreenOn(next==PLAY);   // the screen may sleep normally while editing
        if(next!=HOME&&!(next==PLAY&&frontEnd()))hint(next==PLAY?"Left: move · right: look · Attack, Cast, Use and Potion on the right · Bag top right":next==POLY?"Pick a tool, move the cursor, tap Apply":next==THINGS?"Pick an entity type, then Apply to place it":"Left: move · right: look · double-tap a surface to texture it");
    }

    // ---------------------------------------------------------------- touch
    @Override public boolean onTouchEvent(MotionEvent e){
        if(drawError!=null){if(e.getActionMasked()==MotionEvent.ACTION_UP){drawError=null;changeMode(POLY);invalidate();}return true;}
        try{return handleTouch(e);}catch(RuntimeException ex){DoomApp.record(getContext(),ex);drawError=ex.getClass().getSimpleName()+": "+ex.getMessage();invalidate();return true;}
    }
    private boolean toolbarTap(int hid){
        if(hid>=H_ED&&hid<H_ED+EDITOR_NAMES.length){openEditor(hid-H_ED);return true;}
        if(hid==H_SWITCH){launcherOpen=true;return true;}
        if(hid==H_MAPS){mapsMenu();return true;}
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
                    else if(hid==H_EUNDO||hid==H_EREDO)entityUndoRedo(hid==H_EUNDO);
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
            if(hid==H_PLAYHERE&&walker!=null){playFrom=new float[]{walker.player.x,walker.player.y,walker.player.angle};playThisMap=project.currentMap;changeMode(PLAY);hint("Testing from the camera position · DEBUG at the top for cheats");return true;}
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
        if(menu!=null&&menu.active()){
            if(a==MotionEvent.ACTION_DOWN)menu.down(x,y);else if(a==MotionEvent.ACTION_MOVE)menu.move(x,y);else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)menu.up();
            invalidate();return true;}
        if(world==null)return true;
        int id=e.getPointerId(idx);
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            int hid=ui.hitAt(x,y);
            if(debug!=null&&debug.open){debugDownHit=hid;bagDragY=y;bagDragged=false;return true;}
            if(hid==H_DEBUG&&!playerMode){debug().toggle(world);for(int i=0;i<buttonPointer.length;i++)buttonPointer[i]=-1;world.input.clear();joyPointer=lookPointer=-1;joyVisible=false;moveX=moveY=0;return true;}
            if(world.levelComplete){if(hid==H_RETRY){startPlay(world.mapNum);startCard();}else if(hid==H_BACK)backFromEnd();return true;}
            if(deathTap(hid))return true;
            if(world.popup!=null){if(hid>=H_POPUP&&hid<H_POPUP+3)world.choosePopup(hid-H_POPUP);return true;}
            if(world.shop!=null){shopDownHit=hid;bagDragY=y;bagDragged=false;return true;}
            if(bagOpen){bagTap(hid);bagDragY=y;bagDragged=false;return true;}
            if(world.dialogue!=null){if(hid>=H_DLG&&hid<H_DLG+8)world.chooseDialogue(hid-H_DLG);return true;}
            if(hid==H_EXITPLAY){pauseToMenu();return true;}
            if(hid==H_BAG){openBag();return true;}
            int b=hid==H_FIRE?Input.FIRE:hid==H_USE?Input.USE:hid==H_JUMP?Input.JUMP:hid==H_WEAPON?Input.WEAPON:hid==H_SPELL?Input.SPELL:hid==H_POTION?Input.POTION:-1;
            if(b>=0){buttonPointer[b]=id;world.input.setButton(b,true);return true;}
            GameHud g=hud();boolean joyHit;
            if(g.layout.joyFixed){float[] jc=g.joyCenter(w,h);joyHit=Math.hypot(x-jc[0],y-jc[1])<g.layout.radius(HudLayout.JOYSTICK,ui.dp)*1.6f;
                if(joyHit&&joyPointer<0){joyPointer=id;joyVisible=true;joyOriginX=jc[0];joyOriginY=jc[1];moveX=moveY=0;moveJoystick(x,y);}}
            else{joyHit=g.joyOnRight(w,h)?x>w*.55f:x<w*.45f;
                if(joyHit&&joyPointer<0){joyPointer=id;joyVisible=true;joyOriginX=joyX=x;joyOriginY=joyY=y;moveX=moveY=0;}}
            if(joyHit){}
            else if(lookPointer<0){lookPointer=id;lookLastX=x;lookLastY=y;}
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(debug!=null&&debug.open){if(Math.abs(y-bagDragY)>dp(4)||bagDragged){bagDragged=true;debug.scrollBy(y-bagDragY);bagDragY=y;}return true;}
            if(world.shop!=null){if(Math.abs(y-bagDragY)>dp(4)||bagDragged){bagDragged=true;shopScroll=Math.max(0,shopScroll-(y-bagDragY));bagDragY=y;}return true;}
            if(bagOpen){bagScroll=Math.max(0,bagScroll-(y-bagDragY));bagDragY=y;return true;}
            for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float px=e.getX(i),py=e.getY(i);
                if(pid==joyPointer)moveJoystick(px,py);
                if(pid==lookPointer){float sens=GameSettings.lookSensitivity;world.input.lookX+=(px-lookLastX)*sens;world.input.lookY+=(py-lookLastY)*sens*(GameSettings.invertLook?-1:1);lookLastX=px;lookLastY=py;}}
            return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_CANCEL){
            if(debug!=null&&debug.open){if(a==MotionEvent.ACTION_UP&&!bagDragged){int hid=ui.hitAt(x,y);if(hid==debugDownHit)debug.tap(hid,world);}debugDownHit=-1;return true;}
            if(world.shop!=null){if(a==MotionEvent.ACTION_UP&&!bagDragged){int hid=ui.hitAt(x,y);if(hid==shopDownHit)shopTap(hid);}shopDownHit=-1;return true;}
            for(int b=0;b<buttonPointer.length;b++)if(buttonPointer[b]==id||a!=MotionEvent.ACTION_POINTER_UP&&buttonPointer[b]>=0){buttonPointer[b]=-1;world.input.setButton(b,false);}
            if(id==joyPointer||a!=MotionEvent.ACTION_POINTER_UP){if(joyPointer>=0){joyReleaseTime=System.currentTimeMillis();}joyPointer=-1;joyVisible=false;moveX=moveY=0;}
            if(id==lookPointer||a!=MotionEvent.ACTION_POINTER_UP)lookPointer=-1;
            return true;
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();renderer.dispose();}
}
