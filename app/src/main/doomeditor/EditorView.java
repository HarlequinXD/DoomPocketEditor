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
    private static final int THINGS=2, PLAY=3, POLY=4, POLY3D=5;
    private static final int[] MODES={POLY,THINGS,POLY3D,PLAY};
    private static final String[] MODE_NAMES={"Sectors","Entities","3D View","Play"};
    private static final int[] MODE_ICONS={Icons.SECTORS,Icons.ENTITIES,Icons.CUBE,Icons.PLAY};
    // Touch target ids registered while drawing.
    private static final int H_MODE=100,H_FSM=110,H_SAVE=111,H_RESET=112,H_PAGE=120,H_TOOL=200,H_APPLY=520,H_MAT=400,
            H_RAISE=500,H_LOWER=501,H_LIGHTUP=502,H_LIGHTDN=503,H_FLY=504,H_UP=505,H_DOWN=506,H_EDITFSM3D=507,H_START3D=508,
            H_FIRE=510,H_USE=511,H_JUMP=512,H_WEAPON=513,H_EXITPLAY=514,H_RETRY=530,H_BACK=531,
            H_NEWDEF=590,H_DEF=600,H_PROP=700,H_ETOOL=800,H_EDITFSM=810,H_DUPDEF=811,H_DELDEF=812;
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
    private int mode=POLY,toolPage=0,tool=DRAW,stage=0;
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
    private String selDef="trooper";private int selThing=-1,entityTool=0,entStage=0;private float defScroll,propScroll,matScroll;
    private int scrollTarget=0; // 1 def list, 2 property panel, 3 texture browser
    // 3D
    private World walker;private boolean fly;private int sel3d=-1,selectedMaterial=0;private boolean flyUp,flyDown;
    private float joyOriginX,joyOriginY,joyX,joyY,moveX,moveY;private boolean joyVisible;private long joyReleaseTime;
    private int joyPointer=-1,lookPointer=-1,upPointer=-1,downPointer=-1;private float lookLastX,lookLastY,lookDownX,lookDownY;
    private long last3DTapTime;private float last3DTapX,last3DTapY;
    // play
    private World world;private final int[] buttonPointer={-1,-1,-1,-1,-1};
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
    boolean exitPlay(){if(mode!=PLAY)return false;changeMode(POLY3D);return true;}
    void onPause(){sfx.stop();save(false);}
    void onResume(){if(mode==PLAY)sfx.start();renderer.invalidate();invalidate();}

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

    // ---------------------------------------------------------------- frame
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        if(drawError!=null){c.drawColor(Ui.BG);ui.text(c,"Something went wrong drawing the editor. Tap to reset the view.",dp(20),dp(40),15,Ui.AMBER,1,-1);ui.text(c,drawError,dp(20),dp(66),12,Ui.TEXT_2);return;}
        int save=c.save();
        try{drawFrame(c);}catch(RuntimeException ex){
            c.restoreToCount(save);DoomApp.record(getContext(),ex);
            drawError=ex.getClass().getSimpleName()+": "+ex.getMessage();invalidate();
        }
    }
    private void drawFrame(Canvas c){
        w=getWidth();h=getHeight();toolbarH=mode==PLAY?0:dp(48);statusH=in2D()?dp(26):0;
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
        if(mode!=PLAY)drawTopBar(c);
        drawToast(c);
    }

    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,toolbarH,Ui.SURFACE);ui.hline(c,0,w,toolbarH-dp(1),Ui.BORDER);
        float cy=toolbarH*.5f,lx=dp(12),ls=dp(28);
        ui.round(c,lx,cy-ls*.5f,lx+ls,cy+ls*.5f,dp(7),Ui.ACCENT);ui.icon(c,Icons.LOGO,lx+ls*.5f,cy,17,0xffffffff);
        float tx=lx+ls+dp(10);boolean wide=w>dp(760);float brandEnd=tx;
        if(wide){ui.textMid(c,"Doom Pocket",tx,cy,14,Ui.TEXT,1,-1);brandEnd=tx+ui.measure("Doom Pocket",14,1);}
        float bh=dp(32),top=cy-bh*.5f,x=w-dp(10);
        boolean armed=System.currentTimeMillis()<resetArmedUntil;float resetW=armed?dp(96):bh;
        r.set(x-resetW,top,x,top+bh);ui.button(c,r,Icons.RESET,armed?"Confirm":null,Ui.DANGER);ui.hit(r,H_RESET);x-=resetW+dp(8);
        float saveW=wide?dp(84):bh;r.set(x-saveW,top,x,top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);x-=saveW+dp(8);
        float fsmW=wide?dp(118):dp(74);r.set(x-fsmW,top,x,top+bh);ui.button(c,r,Icons.GRAPH,wide?"FSM Graph":"FSM",Ui.NORMAL);ui.hit(r,H_FSM);
        float availL=brandEnd+dp(20),availR=x-fsmW-dp(20);
        float segW=Math.min(dp(124),(availR-availL)/4f),total=segW*4,sxx=availL+((availR-availL)-total)*.5f,sh=dp(36),st=cy-sh*.5f;
        ui.round(c,sxx,st,sxx+total,st+sh,dp(9),Ui.BG);ui.outline(c,sxx,st,sxx+total,st+sh,dp(9),Ui.DIVIDER,dp(1));
        boolean labels=segW>=dp(78);
        for(int i=0;i<4;i++){boolean on=mode==MODES[i];float l=sxx+i*segW+dp(3),rr=sxx+(i+1)*segW-dp(3);
            if(on)ui.round(c,l,st+dp(3),rr,st+sh-dp(3),dp(7),Ui.SURFACE_3);
            int ic=on?0xff8fbcff:Ui.TEXT_3,tc=on?Ui.TEXT:Ui.TEXT_2;
            if(labels){float tw=ui.measure(MODE_NAMES[i],12.5f,1),iw=dp(16),gap=dp(7),start=(l+rr)*.5f-(iw+gap+tw)*.5f;
                ui.icon(c,MODE_ICONS[i],start+iw*.5f,cy,16,ic);ui.textMid(c,MODE_NAMES[i],start+iw+gap,cy,12.5f,tc,1,-1);}
            else ui.icon(c,MODE_ICONS[i],(l+rr)*.5f,cy,18,ic);
            ui.hit(sxx+i*segW,0,sxx+(i+1)*segW,toolbarH,H_MODE+i);}
    }
    private void drawToast(Canvas c){
        if(System.currentTimeMillis()>=messageUntil||message.isEmpty())return;
        float cl=mode==POLY3D?browserW():in2D()?mapL():0,cr=in2D()?mapR():w,maxW=cr-cl-dp(40);
        String t=ui.ellipsize(message,12.5f,1,maxW-dp(52));
        float tw=ui.measure(t,12.5f,1)+dp(52),cx=(cl+cr)*.5f,top=toolbarH+dp(12)+(mode==PLAY?dp(40):0),ph=dp(34);
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
        for(Level.LineDef line:PortalTopology.build(level)){
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
        Bitmap b=spriteThumb(d.sprite);float hh=Math.max(dp(16),Math.min(dp(44),cell*1.1f)),ww=hh*b.getWidth()/b.getHeight();
        if(ww>hh*1.4f){ww=hh*1.4f;hh=ww*b.getHeight()/b.getWidth();}
        r.set(x-ww*.5f,y-hh*.5f,x+ww*.5f,y+hh*.5f);c.drawBitmap(b,null,r,pixelPaint);
    }
    private static int kindColor(int k){switch(k){case EntityDef.PLAYER:return Ui.GREEN;case EntityDef.MONSTER:return Ui.RED;case EntityDef.PICKUP:return Ui.AMBER;case EntityDef.TRIGGER:return 0xffb07cff;default:return Ui.TEXT_2;}}
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
        ui.textMid(c,Math.round(zoom*100)+"%",R-dp(12),cy,11,Ui.TEXT_3,1,1);
    }
    private float statusItem(Canvas c,float x,float cy,int icon,String text){ui.icon(c,icon,x+dp(6),cy,12,Ui.TEXT_3);ui.textMid(c,text,x+dp(17),cy,11,Ui.TEXT_2,0,-1);return x+dp(17)+ui.measure(text,11,0)+dp(18);}
    static String fmt(float v){return Math.abs(v-Math.round(v))<.001f?String.valueOf(Math.round(v)):String.format(java.util.Locale.US,"%.2f",v).replaceAll("0+$","");}

    // ---------------------------------------------------------------- inspector (Sectors) and property panel (Entities)
    private void drawInspector(Canvas c){
        float x0=mapR(),pad=dp(10),l=x0+pad,rr=w-pad;
        ui.rect(c,x0,toolbarH,w,h,Ui.SURFACE);ui.vline(c,x0,toolbarH,h,Ui.BORDER);
        if(mode==POLY){
            float tt=toolbarH+dp(10),th=dp(30),tw=(rr-l)/3f;
            ui.round(c,l,tt,rr,tt+th,dp(8),Ui.BG);
            for(int i=0;i<3;i++){float a=l+i*tw,b=a+tw;if(i==toolPage)ui.round(c,a+dp(3),tt+dp(3),b-dp(3),tt+th-dp(3),dp(6),Ui.SURFACE_3);
                ui.textMid(c,PAGE_NAMES[i],(a+b)*.5f,tt+th*.5f,12,i==toolPage?Ui.TEXT:Ui.TEXT_3,1,0);ui.hit(a,tt,b,tt+th,H_PAGE+i);}
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
            Bitmap b=spriteThumb(d.sprite);float th=dp(28),tw=th*b.getWidth()/b.getHeight();if(tw>dp(30)){tw=dp(30);th=tw*b.getHeight()/b.getWidth();}
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
    private String[] propLabels(EntityDef d){return new String[]{"Name","Type","Sprite","Health","Speed","Radius","Height","Solid","Shootable"};}
    private String propValue(EntityDef d,int i){switch(i){case 0:return d.name;case 1:return EntityDef.KIND_NAMES[d.kind];case 2:return Sprites.NAMES[Math.max(0,Math.min(Sprites.COUNT-1,d.sprite))];
        case 3:return String.valueOf(d.health);case 4:return fmt(d.speed);case 5:return fmt(d.radius);case 6:return fmt(d.height);case 7:return d.solid?"Yes":"No";default:return d.shootable?"Yes":"No";}}
    private void drawPropertyPanel(Canvas c,float x0,float l,float rr){
        EntityDef d=currentDef();if(d==null)return;
        float top=toolbarH,bottom=h;
        int save=c.save();c.clipRect(x0+dp(1),top,w,bottom);
        float y=top+dp(12)-propScroll;
        // header
        ui.round(c,l,y,l+dp(52),y+dp(52),dp(8),Ui.BG);
        Bitmap b=spriteThumb(d.sprite);float th=dp(44),tw=th*b.getWidth()/b.getHeight();if(tw>dp(46)){tw=dp(46);th=tw*b.getHeight()/b.getWidth();}
        r.set(l+dp(26)-tw*.5f,y+dp(26)-th*.5f,l+dp(26)+tw*.5f,y+dp(26)+th*.5f);c.drawBitmap(b,null,r,pixelPaint);
        ui.text(c,ui.ellipsize(d.name,14,1,rr-l-dp(62)),l+dp(62),y+dp(22),14,Ui.TEXT,1,-1);
        ui.text(c,EntityDef.KIND_NAMES[d.kind]+" · "+d.fsm.nodes.size()+" states",l+dp(62),y+dp(40),11,Ui.TEXT_3);
        y+=dp(62);
        r.set(l,y,rr,y+dp(34));ui.button(c,r,Icons.GRAPH,"Edit FSM",Ui.PRIMARY);clipHit(r,H_EDITFSM,top,bottom);y+=dp(44);
        ui.caption(c,"Place with cursor",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
        float tw2=(rr-l)/5f;
        for(int i=0;i<5;i++){r.set(l+i*tw2+dp(1),y,l+(i+1)*tw2-dp(1),y+dp(34));ui.button(c,r,ENTITY_TOOL_ICONS[i],null,entityTool==i?Ui.ACTIVE:i==4?Ui.DANGER:Ui.NORMAL);clipHit(r,H_ETOOL+i,top,bottom);}
        y+=dp(38);ui.text(c,ENTITY_TOOLS[entityTool]+(entityTool==0?" "+d.name:" entity"),l,y+dp(10),11,Ui.TEXT_2);y+=dp(22);
        ui.caption(c,"Properties",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
        String[] labels=propLabels(d);
        for(int i=0;i<labels.length;i++){
            r.set(l,y,rr,y+dp(32));ui.round(c,r,dp(6),i%2==0?Ui.SURFACE_2:0x00000000);
            ui.textMid(c,labels[i],l+dp(10),y+dp(16),12,Ui.TEXT_2,0,-1);
            String v=propValue(d,i);boolean toggle=i>=7;
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
                sectorEditor.setMaterial(s,surf,(cur+1)%(Textures.COUNT-1));break;}
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
        if(host==null)return;
        switch(i){
            case 0:host.prompt("Entity name",null,d.name,false,t->{if(!t.trim().isEmpty()){d.name=t.trim();changed(null);}});break;
            case 1:if(d.kind==EntityDef.PLAYER){hint("The player type can't be changed");return;}
                String[] kinds={"Monster","Pickup","Decoration","Trigger"};host.choose("Type",kinds,Math.max(0,d.kind-1),k->{d.kind=k+1;if(d.kind==EntityDef.MONSTER){d.countKill=true;d.shootable=true;}if(d.kind==EntityDef.PICKUP){d.countItem=true;d.solid=false;}changed(null);});break;
            case 2:host.choose("Sprite",Sprites.NAMES,d.sprite,k->{d.sprite=k;changed(null);});break;
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
            spriteList.add(sd(pool++).set(t.x,t.y,floorAtLevel(t.x,t.y),Sprites.get(d.sprite,Sprites.F_IDLE),Renderer.id(Renderer.ID_SPRITE,i),false));}
        renderer.render(c,level,cam.x,cam.y,walker.viewZ,cam.angle,cam.pitch,view3d,spriteList);
        // crosshair
        float cx=view3d.centerX(),cy=view3d.centerY();p.setColor(0xccffffff);c.drawCircle(cx,cy,dp(2),p);
        drawTextureBrowser(c,left);
        drawJoystick(c);
        // viewport chips
        float bh=dp(32),top=toolbarH+dp(10),x=w-dp(10);
        float fw2=dp(96);r.set(x-fw2,top,x,top+bh);ui.button(c,r,fly?Icons.LAYERS:Icons.ENTITIES,fly?"Fly":"Walk",fly?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_FLY);x-=fw2+dp(8);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.FLAG,null,Ui.NORMAL);ui.hit(r,H_START3D);
        if(fly){float bx=w-dp(14)-dp(56),by=h-dp(16)-dp(56);
            r.set(bx,by-dp(64),bx+dp(56),by-dp(8));ui.stackButton(c,r,Icons.ARROW_UP,"Up",flyUp?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_UP);
            r.set(bx,by,bx+dp(56),by+dp(56));ui.stackButton(c,r,Icons.ARROW_DOWN,"Down",flyDown?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_DOWN);}
        drawSelection3D(c,left);
        if(joyVisible||flyUp||flyDown||Math.abs(cam.vz)>.01f||Math.hypot(cam.vx,cam.vy)>.01f||walker.viewZ!=cam.z+World.EYE)postInvalidateOnAnimation();
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
        float tw=ui.measure(t,12.5f,1)+dp(50),cx=(left+w)*.5f,bt=h-dp(50);
        if(k==Renderer.ID_SPRITE)tw+=dp(100);
        ui.pill(c,cx-tw*.5f,bt,cx+tw*.5f,bt+dp(36),0xf2262c34);
        ui.icon(c,icon,cx-tw*.5f+dp(20),bt+dp(18),16,0xff8fbcff);ui.textMid(c,t,cx-tw*.5f+dp(36),bt+dp(18),12.5f,Ui.TEXT,1,-1);
        if(k==Renderer.ID_SPRITE){r.set(cx+tw*.5f-dp(100),bt+dp(5),cx+tw*.5f-dp(6),bt+dp(31));ui.button(c,r,Icons.GRAPH,"Edit FSM",Ui.PRIMARY);ui.hit(r,H_EDITFSM3D);}
        if(sector>=0){
            float bw=dp(60),bh=dp(54),bx=w-dp(14)-bw,cy=(toolbarH+h)*.5f-dp(10);
            if(k!=Renderer.ID_WALL){r.set(bx,cy-bh*2-dp(12),bx+bw,cy-bh-dp(12));ui.stackButton(c,r,Icons.ARROW_UP,"Raise",Ui.NORMAL);ui.hit(r,H_RAISE);
                r.set(bx,cy-bh-dp(4),bx+bw,cy-dp(4));ui.stackButton(c,r,Icons.ARROW_DOWN,"Lower",Ui.NORMAL);ui.hit(r,H_LOWER);}
            r.set(bx,cy+dp(4),bx+bw,cy+bh+dp(4));ui.stackButton(c,r,Icons.SUN,"Light +",Ui.NORMAL);ui.hit(r,H_LIGHTUP);
            r.set(bx,cy+bh+dp(12),bx+bw,cy+bh*2+dp(12));ui.stackButton(c,r,Icons.SUN_DIM,"Light −",Ui.NORMAL);ui.hit(r,H_LIGHTDN);
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
    private void startPlay(){world=new World(project,sfx);sfx.start();for(int i=0;i<buttonPointer.length;i++)buttonPointer[i]=-1;joyPointer=lookPointer=-1;joyVisible=false;}
    private void drawPlay(Canvas c,float dt){
        if(world==null)startPlay();
        if(world.restartRequested){startPlay();hint("Level restarted");}
        Input in=world.input;
        in.moveX=joyVisible?moveX:0;in.moveY=joyVisible?moveY:0;
        world.tick(dt);
        Actor pl=world.player;
        spriteList.clear();int pool=0;
        for(Actor a:world.actors){if(a==pl||!a.visible||a.removed)continue;
            Sprites.Sprite s;boolean bright=false;
            if(a.projectileType>=0){s=Sprites.effect(a.projectileType==1?Sprites.FX_ROCKET:a.projectileType==2?Sprites.FX_PLASMA:Sprites.FX_FIREBALL,0,true);bright=true;}
            else{int f=a.def!=null&&(a.def.kind==EntityDef.MONSTER||a.def.kind==EntityDef.PLAYER)?a.frame():a.sprite==Sprites.TORCH?((int)(world.time*8)&1):Sprites.F_IDLE;s=Sprites.get(a.sprite,f);}
            spriteList.add(sd(pool++).set(a.x,a.y,a.z+a.bob-(a.projectileType>=0?.2f:0),s,-1,bright));}
        for(World.Effect e:world.effects){int f=Math.min(2,(int)(e.t/e.life*3));spriteList.add(sd(pool++).set(e.x,e.y,e.z-.2f,Sprites.effect(e.type,f,true),-1,e.type!=Sprites.FX_BLOOD));}
        view3d.set(0,0,w,h);
        renderer.highlight=-1;
        renderer.renderScene(world.level,world.camX(),world.camY(),world.camZ(),pl.angle,pl.pitch,w,h,spriteList);
        if(pl.alive){float b=world.bob();int si=world.sectorIndex(pl);renderer.weapon(world.weapon,(float)Math.cos(world.bobPhase*Math.PI*.5f)*Math.min(1,(float)Math.hypot(pl.vx,pl.vy)/6f),b*8,world.weaponRaise,world.weaponFlash>0,si<0?160:world.level.sectors.get(si).light);}
        if(world.damageFlash>0)renderer.tint(0xffff2020,world.damageFlash*.6f);
        if(world.pickupFlash>0)renderer.tint(world.flashColor,world.pickupFlash*.35f);
        if(!pl.alive)renderer.tint(0xff800000,.35f);
        renderer.present(c,view3d);
        drawHud(c);
        if(!world.levelComplete){drawJoystick(c);drawPlayButtons(c);}
        else drawComplete(c);
        postInvalidateOnAnimation();
    }
    private void drawPlayButtons(Canvas c){
        float bar=dp(44);
        float fr=dp(38),fx=w-dp(22)-fr,fy=h-bar-dp(18)-fr;
        playButton(c,fx,fy,fr,0xffe0533d,Icons.CROSSHAIR,"FIRE",H_FIRE,buttonPointer[Input.FIRE]>=0);
        playButton(c,fx-fr-dp(40),fy+dp(8),dp(26),0xcc2a3038,Icons.DOOR,"USE",H_USE,buttonPointer[Input.USE]>=0);
        playButton(c,fx,fy-fr-dp(36),dp(24),0xcc2a3038,Icons.ARROW_UP,"JUMP",H_JUMP,buttonPointer[Input.JUMP]>=0);
        playButton(c,fx-fr-dp(36),fy-fr-dp(22),dp(24),0xcc2a3038,Icons.CHEVRON_RIGHT,"WPN",H_WEAPON,buttonPointer[Input.WEAPON]>=0);
        float er=dp(18);p.setColor(0x99101318);c.drawCircle(dp(14)+er,dp(14)+er,er,p);ui.icon(c,Icons.CANCEL,dp(14)+er,dp(14)+er,18,0xddffffff);ui.hit(dp(6),dp(6),dp(22)+er*2,dp(22)+er*2,H_EXITPLAY);
    }
    private void playButton(Canvas c,float x,float y,float rad,int col,int icon,String label,int hid,boolean down){
        p.setStyle(Paint.Style.FILL);p.setColor(0x55000000);c.drawCircle(x,y+dp(3),rad,p);p.setColor(down?Ui.brighten(col):col);c.drawCircle(x,y,rad,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x40ffffff);c.drawCircle(x,y,rad-dp(.5f),p);p.setStyle(Paint.Style.FILL);
        ui.icon(c,icon,x,y-(rad>dp(30)?dp(5):dp(3)),rad>dp(30)?24:16,0xffffffff);ui.text(c,label,x,y+rad*.62f,rad>dp(30)?10:8,0xffffffff,2,0);
        ui.hit(x-rad-dp(4),y-rad-dp(4),x+rad+dp(4),y+rad+dp(4),hid);
    }
    private void drawHud(Canvas c){
        float bar=dp(44),top=h-bar;
        ui.rect(c,0,top,w,h,0xee15181c);ui.hline(c,0,w,top,0xff3a3f46);
        String[] labels={"AMMO","HEALTH","ARMS","ARMOR"};
        int at=World.W_AMMO[world.weapon];
        String[] vals={at<0?"—":String.valueOf(world.ammo[at]),Math.max(0,world.player.health)+"%",World.WEAPON_NAMES[world.weapon].toUpperCase(),world.armor+"%"};
        float cw=Math.min(dp(150),(w-dp(120))/4f),x=w*.5f-cw*2-dp(40);
        for(int i=0;i<4;i++){float cx=x+cw*i+cw*.5f;
            int col=i==1?(world.player.health<=25?0xffff5050:0xffff3b30):i==3?0xff4cd964:i==0?0xffffcc33:Ui.TEXT;
            ui.text(c,vals[i],cx,top+dp(26),i==2?13:20,col,2,0);ui.text(c,labels[i],cx,top+dp(39),8.5f,Ui.TEXT_3,1,0);
            if(i>0)ui.rect(c,x+cw*i,top+dp(8),x+cw*i+dp(1),h-dp(8),0x33ffffff);}
        float kx=x+cw*4+dp(20);int[] kc={0xff3060e0,0xffe0c020,0xffd02020};
        for(int k=0;k<3;k++){float ky=top+dp(8)+k*dp(10);ui.round(c,kx,ky,kx+dp(16),ky+dp(8),dp(2),world.keys[k]?kc[k]:0x22ffffff);}
        // stats
        ui.text(c,"K "+world.kills+"/"+world.totalKills+"   I "+world.items+"/"+world.totalItems+"   S "+world.secrets+"/"+world.totalSecrets,w-dp(14),dp(26),10.5f,0xccffffff,1,1);
        if(world.messageTime>0){float a=Math.min(1,world.messageTime*2);ui.text(c,world.message,dp(62)+dp(1),dp(30)+dp(1),13,((int)(a*200)<<24),1,-1);ui.text(c,world.message,dp(62),dp(30),13,((int)(a*255)<<24)|0xffe2e2,1,-1);}
        if(world.centerTime>0&&!world.center.isEmpty()){float a=Math.min(1,world.centerTime*2);ui.text(c,world.center,w*.5f+dp(2),h*.38f+dp(2),26,((int)(a*200)<<24),2,0);ui.text(c,world.center,w*.5f,h*.38f,26,((int)(a*255)<<24)|0xffffff,2,0);}
    }
    private void drawComplete(Canvas c){
        ui.rect(c,0,0,w,h,0xcc0a0b0d);
        float cx=w*.5f,y=h*.24f;
        ui.text(c,"LEVEL COMPLETE",cx,y,26,Ui.AMBER,2,0);
        String[] rows={"Kills","Items","Secrets","Time"};
        String[] vals={pct(world.kills,world.totalKills),pct(world.items,world.totalItems),pct(world.secrets,world.totalSecrets),String.format(java.util.Locale.US,"%d:%02d",(int)(world.time-world.completeTime)/60,(int)(world.time-world.completeTime)%60)};
        for(int i=0;i<4;i++){ui.text(c,rows[i],cx-dp(20),y+dp(46)+i*dp(28),16,Ui.TEXT_2,1,1);ui.text(c,vals[i],cx+dp(20),y+dp(46)+i*dp(28),16,Ui.TEXT,2,-1);}
        float by=y+dp(170);
        r.set(cx-dp(170),by,cx-dp(10),by+dp(40));ui.button(c,r,Icons.RESET,"Play again",Ui.PRIMARY);ui.hit(r,H_RETRY);
        r.set(cx+dp(10),by,cx+dp(170),by+dp(40));ui.button(c,r,Icons.ARROW_LEFT,"Back to editor",Ui.NORMAL);ui.hit(r,H_BACK);
    }
    private static String pct(int a,int b){return b==0?"—":Math.round(a*100f/b)+"%";}

    // ---------------------------------------------------------------- save/mode
    void save(boolean showHint){
        project.save(getContext());
        if(showHint){ArrayList<String> errors=MapValidator.check(level);hint(errors.isEmpty()?"Saved · map is valid":"Saved · "+errors.size()+" map warning(s)");}
    }
    private void autoSave(){project.save(getContext());}
    private void resetLevel(){project.level=new Level();level=project.level;sectorEditor=new SectorEditor(level);pending.clear();selKind=0;walker=null;cancelStage();changed("Level reset");}
    private void changeMode(int next){
        if(mode==PLAY&&next!=PLAY){sfx.stop();world=null;}
        cancelStage();entStage=0;
        mode=next;pending.clear();joyVisible=false;joyPointer=lookPointer=upPointer=downPointer=-1;moveX=moveY=0;flyUp=flyDown=false;dpadPointer=-1;
        lastFrame=System.nanoTime();
        if(next==PLAY)startPlay();
        hint(next==PLAY?"Left: move · right: look · Fire, Use, Jump, Weapon on the right":next==POLY?"Pick a tool, move the cursor, tap Apply":next==THINGS?"Pick an entity type, then Apply to place it":"Left: move · right: look · double-tap a surface to texture it");
    }

    // ---------------------------------------------------------------- touch
    @Override public boolean onTouchEvent(MotionEvent e){
        if(drawError!=null){if(e.getActionMasked()==MotionEvent.ACTION_UP){drawError=null;changeMode(POLY);invalidate();}return true;}
        try{return handleTouch(e);}catch(RuntimeException ex){DoomApp.record(getContext(),ex);drawError=ex.getClass().getSimpleName()+": "+ex.getMessage();invalidate();return true;}
    }
    private boolean toolbarTap(int hid){
        if(hid>=H_MODE&&hid<H_MODE+4){changeMode(MODES[hid-H_MODE]);return true;}
        if(hid==H_FSM){openFsm(mode==THINGS?selDef:null);return true;}
        if(hid==H_SAVE){save(true);return true;}
        if(hid==H_RESET){if(System.currentTimeMillis()<resetArmedUntil){resetArmedUntil=0;resetLevel();}else{resetArmedUntil=System.currentTimeMillis()+3000;hint("Tap Confirm to reset the whole level");postInvalidateDelayed(3010);}return true;}
        return false;
    }
    private void openFsm(String defId){save(false);if(host!=null)host.openFsm(defId);}
    private boolean handleTouch(MotionEvent e){
        int a=e.getActionMasked(),idx=e.getActionIndex();float x=e.getX(idx),y=e.getY(idx);
        if(mode==PLAY)return playTouch(e,a,idx,x,y);
        if(a==MotionEvent.ACTION_DOWN&&y<toolbarH){toolbarTap(ui.hitAt(x,y));invalidate();return true;}
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
                panning=true;return true;
            }
            if(mode==THINGS&&x<entPanelW()){scrollTarget=1;return true;}
            if(x>=mapR()){scrollTarget=mode==THINGS?2:0;return true;}
            return true;
        }
        if(a==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2&&panning){
            pinchDist=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;dragged=true;return true;}
        if(a==MotionEvent.ACTION_MOVE){
            if(dpadPointer>=0){int dir=dpadHit(e.getX(0),e.getY(0));if(dir!=dpadDirection){dpadDirection=dir;if(dir>=0){cursorStep(dir);nextDpadRepeat=System.currentTimeMillis()+270;}invalidate();}return true;}
            if(e.getPointerCount()>=2&&pinchDist>0){
                float d=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1)),mx=(e.getX(0)+e.getX(1))*.5f,my=(e.getY(0)+e.getY(1))*.5f;
                float wxm=wx(mx),wym=wy(my);zoom=Math.max(.35f,Math.min(4f,zoom*d/pinchDist));pinchDist=d;cell=(mapB()-mapT())/14f*zoom;
                viewX=wxm-(mx-(mapL()+mapR())*.5f)/cell;viewY=wym-(my-(mapT()+mapB())*.5f)/cell;
                viewX-=(mx-lastX)/cell;viewY-=(my-lastY)/cell;lastX=mx;lastY=my;followCursor=false;invalidate();return true;}
            if(Math.abs(x-downX)+Math.abs(y-downY)>dp(8))dragged=true;
            if(dragged){
                if(panning){viewX-=(x-lastX)/cell;viewY-=(y-lastY)/cell;followCursor=false;}
                else if(scrollTarget==1)defScroll-=(y-lastY);
                else if(scrollTarget==2)propScroll-=(y-lastY);
                invalidate();}
            lastX=x;lastY=y;return true;
        }
        if(a==MotionEvent.ACTION_POINTER_UP){pinchDist=0;return true;}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
            if(dpadPointer>=0){dpadPointer=-1;dpadDirection=-1;invalidate();return true;}
            if(a==MotionEvent.ACTION_UP&&!dragged){
                if(panning&&inMap(x,y)){
                    float s=snap();cursorX=Math.round(wx(x)/s)*s;cursorY=Math.round(wy(y)/s)*s;
                    if(stage==1&&tool==MOVE&&mode==POLY)previewMove();
                    long now=System.currentTimeMillis();
                    if(now-lastTapTime<330&&Math.hypot(x-lastTapX,y-lastTapY)<dp(24)){cursorApply();lastTapTime=0;}else{lastTapTime=now;lastTapX=x;lastTapY=y;}
                }else{
                    int hid=ui.hitAt(x,y);
                    if(hid>=H_PAGE&&hid<H_PAGE+3){toolPage=hid-H_PAGE;}
                    else if(hid>=H_TOOL&&hid<H_TOOL+11&&mode==POLY){sectorAction(PAGES[toolPage][hid-H_TOOL]);}
                    else if(hid>=H_DEF&&hid<H_DEF+500){int i=hid-H_DEF;if(i<project.defs.size()){selDef=project.defs.get(i).id;selThing=-1;propScroll=0;if(entityTool!=0){entityTool=0;}hint(project.defs.get(i).name+" · Apply to place");}}
                    else if(hid==H_NEWDEF)newEntityType();
                    else if(hid>=H_PROP&&hid<H_PROP+20)propertyTap(hid-H_PROP);
                    else if(hid>=H_ETOOL&&hid<H_ETOOL+5){entityTool=hid-H_ETOOL;entStage=0;hint(ENTITY_TOOLS[entityTool]+" · move the cursor and tap Apply");}
                    else if(hid==H_EDITFSM)openFsm(selDef);
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
            if(world.levelComplete){if(hid==H_RETRY)startPlay();else if(hid==H_BACK)changeMode(POLY3D);return true;}
            if(hid==H_EXITPLAY){changeMode(POLY3D);return true;}
            int b=hid==H_FIRE?Input.FIRE:hid==H_USE?Input.USE:hid==H_JUMP?Input.JUMP:hid==H_WEAPON?Input.WEAPON:-1;
            if(b>=0){buttonPointer[b]=id;world.input.setButton(b,true);return true;}
            if(x<w*.45f){if(joyPointer<0){joyPointer=id;joyVisible=true;joyOriginX=joyX=x;joyOriginY=joyY=y;moveX=moveY=0;}}
            else if(lookPointer<0){lookPointer=id;lookLastX=x;lookLastY=y;}
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
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
