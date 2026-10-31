package com.retro.doomeditor;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONObject;
import java.util.ArrayList;

/** Touch-first polygon sector editor. All 3D modes use PolygonRenderer. */
public class EditorView extends View {
    private static final int MAP=0, VIEW3D=1, THINGS=2, PLAY=3, POLY=4, POLY3D=5;
    // Touch target ids registered while drawing.
    private static final int H_MODE=100,H_FSM=110,H_SAVE=111,H_RESET=112,H_PAGE=120,H_TOOL=200,H_THING=300,H_MAT=400,H_RAISE=500,H_LOWER=501,H_FIRE=510,H_APPLY=520;
    private static final int[] MODES={POLY,THINGS,POLY3D,PLAY};
    private static final String[] MODE_NAMES={"Sectors","Entities","3D View","Play"};
    private static final int[] MODE_ICONS={Icons.SECTORS,Icons.ENTITIES,Icons.CUBE,Icons.PLAY};
    private static final String[] PAGE_NAMES={"Draw","Edit","Surface"};
    private static final String[][] TOOL_NAMES={
        {"Draw","Select","Rectangle","Close","Undo pt","Cancel","Floor +","Floor −","Ceiling +","Ceiling −","Undo"},
        {"Redo","Validate","Clean up","Duplicate","Delete","Split edge","Extrude","Move wall","Stairs","Door","Prefab"},
        {"Wall tex","Floor tex","Ceiling tex","Next tex","Prev tex","Platform","Light +","Light −","Snap info","3D pick","Help"}};
    private static final int[][] TOOL_ICONS={
        {Icons.PEN,Icons.POINTER,Icons.FRAME,Icons.CLOSE_SHAPE,Icons.BACKSPACE,Icons.CANCEL,Icons.FLOOR_UP,Icons.FLOOR_DOWN,Icons.CEIL_UP,Icons.CEIL_DOWN,Icons.UNDO},
        {Icons.REDO,Icons.VALIDATE,Icons.SPARKLE,Icons.COPY,Icons.TRASH,Icons.SCISSORS,Icons.EXTRUDE,Icons.MOVE,Icons.STAIRS,Icons.DOOR,Icons.PACKAGE},
        {Icons.WALL_TEX,Icons.FLOOR_TEX,Icons.CEIL_TEX,Icons.NEXT_TEX,Icons.PREV_TEX,Icons.LAYERS,Icons.SUN,Icons.SUN_DIM,Icons.GRID,Icons.CROSSHAIR,Icons.HELP}};

    private final Ui ui;
    private final Paint p;
    private final Paint pixelPaint=new Paint();
    private final Path path=new Path();
    private final RectF r=new RectF();
    private final Level levelDefault=new Level();
    private Level level=levelDefault;
    private int mode=POLY, thingType=0, polygonTool=0, selectedSector=-1,toolPage=0,materialSurface=0,selectedEdge=0;
    private final ArrayList<float[]> pending=new ArrayList<>();
    private SectorEditor sectorEditor;
    // 3D editor controls and selected surface (0 wall, 1 floor, 2 ceiling).
    private int selectedSurface=-1,selectedMaterial=0,selectedWallHit=-1;
    private float joyOriginX,joyOriginY,joyX,joyY,lookLastX,lookLastY;
    private boolean joyVisible=false;
    private long joyReleaseTime=0,last3DFrame=System.nanoTime(),lastTapTime=0,resetArmedUntil=0;
    private float lastTapX,lastTapY;
    private final Bitmap[] materialPreviews=new Bitmap[6];
    // D-pad controlled 2D editing cursor. Camera follows by eased pan.
    private int cursorX=5,cursorY=5,rectStartX=-1,rectStartY=-1;
    private float followX=0,followY=0;
    private int dpadPointer=-1,dpadDirection=-1;
    private long nextDpadRepeat=0;
    private int dragVertex=-1;
    private float touchStartX,touchStartY;
    private float w,h,toolbarH,statusH,cell,mapLeft,mapTop;
    private float camX,camY,camAngle,moveX,moveY,lastX,lastY,downX,downY;
    private boolean moving,looking,dragging,fireDown;
    private int activePointer=-1,lookPointer=-1,health=100,ammo=30,kills=0;
    private long lastTime=System.nanoTime(),messageUntil;
    private String message="";
    private float zoom=1f,panX=0,panY=0,pinchDistance=0;
    private String drawError;
    private FSMGraph runtimeGraph;

    EditorView(Context ctx){super(ctx);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        ui=new Ui(ctx);p=ui.p;pixelPaint.setFilterBitmap(false);
        String saved=null;
        try{saved=ctx.getSharedPreferences("project",0).getString("level",null);
            if(saved!=null){Level loaded=Level.fromJson(new JSONObject(saved));if(loaded.isConsistent())level=loaded;else throw new IllegalStateException("inconsistent map");}
        }catch(Throwable t){
            // Saved map from an older build is unreadable: keep a copy and start from the default map.
            level=levelDefault;
            if(saved!=null)ctx.getSharedPreferences("project",0).edit().putString("level_unreadable",saved).remove("level").apply();
            message="Saved map could not be loaded; started a new one";messageUntil=System.currentTimeMillis()+5000;
        }
        sectorEditor=new SectorEditor(level);PolygonRenderer.loadWallMaterials(ctx);camX=level.playerX;camY=level.playerY;camAngle=level.facing;setFocusable(true);
    }
    boolean exitPlay(){if(mode!=PLAY)return false;changeMode(POLY3D);return true;}

    // ---------------------------------------------------------------- layout
    private float dp(float v){return ui.dp(v);}
    private boolean in3DEdit(){return mode==VIEW3D||mode==POLY3D;}
    private boolean in2D(){return mode==POLY||mode==THINGS;}
    private float inspectorW(){return Math.max(dp(208),Math.min(dp(290),w*.26f));}
    private float mapRight(){return w-inspectorW();}
    private float mapBottom(){return h-statusH;}
    private float browserWidth(){return Math.max(dp(112),Math.min(dp(150),w*.16f));}
    private float viewLeft(){return in3DEdit()?browserWidth():0;}
    private float contentLeft(){return in3DEdit()?browserWidth():0;}
    private float contentRight(){return in2D()?mapRight():w;}
    private float joyRadius(){return dp(56);}
    private float dpadR(){return dp(60);}
    private float dpadCX(){return dp(22)+dpadR();}
    private float dpadCY(){return mapBottom()-dp(18)-dpadR();}
    private float applyR(){return dp(32);}
    private float applyCX(){return mapRight()-dp(26)-applyR();}
    private float applyCY(){return dpadCY()+dp(8);}

    // ---------------------------------------------------------------- 3D camera & joystick
    private void update3DCamera(){
        long now=System.nanoTime();float dt=Math.min(.05f,(now-last3DFrame)*1e-9f);last3DFrame=now;
        if(!in3DEdit()||!joyVisible)return;
        float forward=-moveY*3.4f*dt,strafe=moveX*3.4f*dt;
        float nx=camX+(float)Math.cos(camAngle)*forward-(float)Math.sin(camAngle)*strafe;
        float ny=camY+(float)Math.sin(camAngle)*forward+(float)Math.cos(camAngle)*strafe;
        if(PortalTopology.canCross(level,camX,camY,nx,camY,.6f,1.5f))camX=nx;
        if(PortalTopology.canCross(level,camX,camY,camX,ny,.6f,1.5f))camY=ny;
    }
    private void moveJoystick(float x,float y){
        float dx=x-joyOriginX,dy=y-joyOriginY;
        float len=(float)Math.hypot(dx,dy),limit=joyRadius()*.68f;
        float scale=len>limit?limit/len:1;
        joyX=joyOriginX+dx*scale;joyY=joyOriginY+dy*scale;
        moveX=(joyX-joyOriginX)/limit;moveY=(joyY-joyOriginY)/limit;
    }
    private void drawFloatingJoystick(Canvas c){
        float alpha=joyVisible?1f:Math.max(0f,1f-(System.currentTimeMillis()-joyReleaseTime)/240f);
        if(alpha<=0)return;
        float radius=joyRadius();
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(((int)(alpha*110)<<24)|0x101318);c.drawCircle(joyOriginX,joyOriginY,radius,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(((int)(alpha*90)<<24)|0xffffff);c.drawCircle(joyOriginX,joyOriginY,radius,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(((int)(alpha*70)<<24));c.drawCircle(joyX,joyY+dp(2),radius*.42f,p);
        p.setColor(((int)(alpha*235)<<24)|0xe8ecf1);c.drawCircle(joyX,joyY,radius*.40f,p);
        if(!joyVisible)postInvalidateDelayed(32);
    }

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        if(drawError!=null){c.drawColor(Ui.BG);ui.text(c,"Something went wrong drawing the editor. Tap to reset the view.",dp(20),dp(40),15,Ui.AMBER,1,-1);ui.text(c,drawError,dp(20),dp(66),12,Ui.TEXT_2);return;}
        int save=c.save();
        try{drawFrame(c);}catch(RuntimeException ex){
            c.restoreToCount(save);DoomApp.record(getContext(),ex);
            drawError=ex.getClass().getSimpleName()+": "+ex.getMessage();invalidate();
        }
    }
    private void drawFrame(Canvas c){w=getWidth();h=getHeight();toolbarH=dp(48);statusH=in2D()?dp(26):0;
        ui.clearHits();
        c.drawColor(Ui.BG);
        if(in2D()){drawMap(c);drawInspector(c);drawCursorControls(c);drawStatusBar(c);}
        else {
            if(in3DEdit())update3DCamera();
            PolygonRenderer.setHighlight(in3DEdit()?selectedSector:-1,selectedSurface);
            if(in3DEdit()&&selectedWallHit>=100000)PolygonRenderer.setHighlightHit(selectedWallHit);
            PolygonRenderer.render(c,level,viewLeft(),toolbarH,w,h,camX,camY,camAngle);
            if(mode==PLAY){drawPlayControls(c);updateGame();postInvalidateDelayed(16);}
            else {draw3DOverlay(c);if(joyVisible)postInvalidateDelayed(16);}
        }
        drawTopBar(c);
        drawToast(c);
    }

    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,toolbarH,Ui.SURFACE);ui.hline(c,0,w,toolbarH-dp(1),Ui.BORDER);
        float cy=toolbarH*.5f;
        // Brand
        float lx=dp(12),ls=dp(28);
        ui.round(c,lx,cy-ls*.5f,lx+ls,cy+ls*.5f,dp(7),Ui.ACCENT);
        ui.icon(c,Icons.LOGO,lx+ls*.5f,cy,17,0xffffffff);
        float tx=lx+ls+dp(10);
        boolean wide=w>dp(760);
        float brandEnd=tx;
        if(wide){ui.textMid(c,"Doom Pocket",tx,cy,14,Ui.TEXT,1,-1);brandEnd=tx+ui.measure("Doom Pocket",14,1);}
        // Right-hand actions
        float bh=dp(32),top=cy-bh*.5f,x=w-dp(10);
        boolean armed=System.currentTimeMillis()<resetArmedUntil;
        float resetW=armed?dp(96):bh;
        r.set(x-resetW,top,x,top+bh);ui.button(c,r,Icons.RESET,armed?"Confirm":null,Ui.DANGER);ui.hit(r,H_RESET);x-=resetW+dp(8);
        float saveW=wide?dp(84):bh;
        r.set(x-saveW,top,x,top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);x-=saveW+dp(8);
        float fsmW=wide?dp(118):dp(74);
        r.set(x-fsmW,top,x,top+bh);ui.button(c,r,Icons.GRAPH,wide?"FSM Graph":"FSM",Ui.NORMAL);ui.hit(r,H_FSM);
        float actionsLeft=x-fsmW;
        // Mode segmented control
        float availL=brandEnd+dp(20),availR=actionsLeft-dp(20);
        float segW=Math.min(dp(124),(availR-availL)/4f);
        float total=segW*4,sx=availL+((availR-availL)-total)*.5f;
        float sh=dp(36),st=cy-sh*.5f;
        ui.round(c,sx,st,sx+total,st+sh,dp(9),Ui.BG);ui.outline(c,sx,st,sx+total,st+sh,dp(9),Ui.DIVIDER,dp(1));
        boolean labels=segW>=dp(78);
        for(int i=0;i<4;i++){
            boolean on=mode==MODES[i]||(i==2&&mode==VIEW3D);
            float l=sx+i*segW+dp(3),rr=sx+(i+1)*segW-dp(3);
            if(on){ui.round(c,l,st+dp(3),rr,st+sh-dp(3),dp(7),Ui.SURFACE_3);}
            int ic=on?0xff8fbcff:Ui.TEXT_3,tc=on?Ui.TEXT:Ui.TEXT_2;
            if(labels){
                float tw=ui.measure(MODE_NAMES[i],12.5f,1),iw=dp(16),gap=dp(7),start=(l+rr)*.5f-(iw+gap+tw)*.5f;
                ui.icon(c,MODE_ICONS[i],start+iw*.5f,cy,16,ic);ui.textMid(c,MODE_NAMES[i],start+iw+gap,cy,12.5f,tc,1,-1);
            }else ui.icon(c,MODE_ICONS[i],(l+rr)*.5f,cy,18,ic);
            ui.hit(sx+i*segW,0,sx+(i+1)*segW,toolbarH,H_MODE+i);
        }
    }

    private void drawToast(Canvas c){
        if(System.currentTimeMillis()>=messageUntil||message.isEmpty())return;
        float cl=contentLeft(),cr=contentRight(),maxW=cr-cl-dp(40);
        String t=ui.ellipsize(message,12.5f,1,maxW-dp(52));
        float tw=ui.measure(t,12.5f,1)+dp(52),cx=(cl+cr)*.5f,top=toolbarH+dp(12),ph=dp(34);
        ui.pill(c,cx-tw*.5f,top,cx+tw*.5f,top+ph,0xf2262c34);
        ui.icon(c,Icons.INFO,cx-tw*.5f+dp(20),top+ph*.5f,16,0xff8fbcff);
        ui.textMid(c,t,cx-tw*.5f+dp(36),top+ph*.5f,12.5f,Ui.TEXT,1,-1);
        postInvalidateDelayed(Math.max(16,messageUntil-System.currentTimeMillis()));
    }

    private void mapBounds(){float availW=mapRight(),availH=mapBottom()-toolbarH;
        cell=Math.min(availW/Level.W,availH/Level.H)*zoom;
        float baseLeft=(availW-Level.W*cell)*.5f+panX;
        float baseTop=toolbarH+(availH-Level.H*cell)*.5f+panY;
        if(in2D()){
            float targetX=availW*.50f-(baseLeft+cursorX*cell);
            float targetY=toolbarH+availH*.46f-(baseTop+cursorY*cell);
            followX+=(targetX-followX)*.17f;
            followY+=(targetY-followY)*.17f;
            if(Math.abs(targetX-followX)>0.35f||Math.abs(targetY-followY)>0.35f)postInvalidateDelayed(16);
        }
        mapLeft=baseLeft+followX;
        mapTop=baseTop+followY;
    }
    private float sx(float wx){return mapLeft+wx*cell;}
    private float sy(float wy){return mapTop+wy*cell;}

    private void drawMap(Canvas c){mapBounds();
        float right=mapRight(),bottom=mapBottom();
        int save=c.save();c.clipRect(0,toolbarH,right,bottom);
        ui.rect(c,0,toolbarH,right,bottom,Ui.CANVAS);
        // Grid: minor lines every cell, major every 4, axes highlighted.
        int minGX=(int)Math.floor((0-mapLeft)/cell)-1,maxGX=(int)Math.ceil((right-mapLeft)/cell)+1;
        int minGY=(int)Math.floor((toolbarH-mapTop)/cell)-1,maxGY=(int)Math.ceil((bottom-mapTop)/cell)+1;
        float lw=Math.max(1,dp(.75f));
        for(int i=minGX;i<=maxGX;i++){int col=i==0?0xff39424e:i%4==0?0xff2a3139:0xff21262c;if(col==0xff21262c&&cell<dp(7))continue;ui.rect(c,sx(i),toolbarH,sx(i)+lw,bottom,col);}
        for(int i=minGY;i<=maxGY;i++){int col=i==0?0xff39424e:i%4==0?0xff2a3139:0xff21262c;if(col==0xff21262c&&cell<dp(7))continue;ui.rect(c,0,sy(i),right,sy(i)+lw,col);}
        // Sector fills
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        for(int k=0;k<level.sectors.size();k++){
            Level.Sector s=level.sectors.get(k);if(s.loop.size()<3)continue;path.reset();
            for(int j=0;j<s.loop.size();j++){Level.Vertex v=level.vertices.get(s.loop.get(j));if(j==0)path.moveTo(sx(v.x),sy(v.y));else path.lineTo(sx(v.x),sy(v.y));}path.close();
            p.setColor(k==selectedSector?0x473d8bfd:mode==THINGS?0x1c8fa3b8:0x268fa3b8);c.drawPath(path,p);
        }
        // Linedefs: solid walls light, two-sided portals amber.
        p.setStrokeCap(Paint.Cap.ROUND);
        for(Level.LineDef line:PortalTopology.build(level)){
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
            boolean portal=line.back>=0;
            p.setColor(portal?0xffd99a3e:0xffd6dce4);p.setStrokeWidth(portal?dp(1.5f):dp(2));
            c.drawLine(sx(a.x),sy(a.y),sx(b.x),sy(b.y),p);
        }
        if(selectedSector>=0&&selectedSector<level.sectors.size()){
            Level.Sector s=level.sectors.get(selectedSector);p.setColor(Ui.ACCENT);p.setStrokeWidth(dp(2.5f));
            for(int j=0;j<s.loop.size();j++){Level.Vertex a=level.vertices.get(s.loop.get(j)),b=level.vertices.get(s.loop.get((j+1)%s.loop.size()));c.drawLine(sx(a.x),sy(a.y),sx(b.x),sy(b.y),p);}
            if(polygonTool==3&&s.loop.size()>0){int e=selectedEdge%s.loop.size();Level.Vertex a=level.vertices.get(s.loop.get(e)),b=level.vertices.get(s.loop.get((e+1)%s.loop.size()));
                p.setColor(Ui.ORANGE);p.setStrokeWidth(dp(4));c.drawLine(sx(a.x),sy(a.y),sx(b.x),sy(b.y),p);}
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        // Vertices as small squares.
        float vs=Math.max(dp(2.5f),Math.min(dp(4),cell*.09f));
        for(int i=0;i<level.vertices.size();i++){Level.Vertex v=level.vertices.get(i);
            ui.rect(c,sx(v.x)-vs-dp(1),sy(v.y)-vs-dp(1),sx(v.x)+vs+dp(1),sy(v.y)+vs+dp(1),0xff15181c);
            ui.rect(c,sx(v.x)-vs,sy(v.y)-vs,sx(v.x)+vs,sy(v.y)+vs,i==dragVertex?Ui.ORANGE:0xffeef2f6);}
        // Shape being drawn, with a dashed segment to the cursor.
        if(mode==POLY&&!pending.isEmpty()){
            p.setStyle(Paint.Style.STROKE);p.setColor(Ui.ACCENT);p.setStrokeWidth(dp(2));path.reset();
            for(int i=0;i<pending.size();i++){float[] v=pending.get(i);if(i==0)path.moveTo(sx(v[0]),sy(v[1]));else path.lineTo(sx(v[0]),sy(v[1]));}
            c.drawPath(path,p);
            float[] last=pending.get(pending.size()-1);
            p.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(4)},0));p.setColor(0xaa3d8bfd);
            c.drawLine(sx(last[0]),sy(last[1]),sx(cursorX),sy(cursorY),p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
            for(int i=0;i<pending.size();i++){float[] v=pending.get(i);
                p.setColor(0xff15181c);c.drawCircle(sx(v[0]),sy(v[1]),dp(5.5f),p);
                p.setColor(i==0?Ui.GREEN:Ui.ACCENT);c.drawCircle(sx(v[0]),sy(v[1]),dp(4),p);}
        }
        // Entities
        if(mode==THINGS)for(Level.Thing t:level.things){float x=sx(t.x),y=sy(t.y),rad=Math.max(dp(6),Math.min(dp(10),cell*.3f));
            p.setColor(0xff15181c);c.drawCircle(x,y,rad+dp(1.5f),p);
            if(t.type==0){p.setColor(Ui.RED);c.drawCircle(x,y,rad,p);ui.icon(c,Icons.SKULL,x,y,rad*1.3f/ui.dp,0xffffffff);}
            else{ui.round(c,x-rad,y-rad,x+rad,y+rad,dp(3),Ui.GREEN);ui.icon(c,Icons.PLUS,x,y,rad*1.4f/ui.dp,0xffffffff);}
        }
        drawPlayerStart(c);
        // Cursor
        float px=sx(cursorX),py=sy(cursorY);
        if(rectStartX>=0&&mode==POLY){
            float l=sx(Math.min(rectStartX,cursorX)),t=sy(Math.min(rectStartY,cursorY)),rr=sx(Math.max(rectStartX,cursorX)),b=sy(Math.max(rectStartY,cursorY));
            ui.rect(c,l,t,rr,b,0x223d8bfd);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(Ui.ACCENT);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));
            c.drawRect(l,t,rr,b,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(4));p.setColor(0x9915181c);c.drawCircle(px,py,dp(10),p);
        p.setStrokeWidth(dp(1.75f));p.setColor(Ui.AMBER);c.drawCircle(px,py,dp(10),p);
        float g=dp(5),len=dp(16);
        c.drawLine(px-len,py,px-g,py,p);c.drawLine(px+g,py,px+len,py,p);c.drawLine(px,py-len,px,py-g,p);c.drawLine(px,py+g,px,py+len,p);
        p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);p.setColor(Ui.AMBER);c.drawCircle(px,py,dp(2),p);
        c.restoreToCount(save);
    }
    private void drawPlayerStart(Canvas c){
        float x=sx(level.playerX),y=sy(level.playerY),s=Math.max(dp(8),Math.min(dp(12),cell*.36f));
        float cs=(float)Math.cos(level.facing),sn=(float)Math.sin(level.facing);
        path.reset();path.moveTo(x+cs*s,y+sn*s);path.lineTo(x-cs*s*.7f-sn*s*.7f,y-sn*s*.7f+cs*s*.7f);path.lineTo(x-cs*s*.3f,y-sn*s*.3f);path.lineTo(x-cs*s*.7f+sn*s*.7f,y-sn*s*.7f-cs*s*.7f);path.close();
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(dp(3));p.setColor(0xff15181c);c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);p.setColor(Ui.GREEN);c.drawPath(path,p);p.setStrokeJoin(Paint.Join.MITER);
    }

    private void drawStatusBar(Canvas c){
        float right=mapRight(),top=mapBottom(),cy=top+statusH*.5f;
        ui.rect(c,0,top,right,h,Ui.SURFACE);ui.hline(c,0,right,top,Ui.BORDER);
        float x=dp(12);
        x=statusItem(c,x,cy,Icons.CROSSHAIR,"X "+cursorX+"   Y "+cursorY);
        x=statusItem(c,x,cy,Icons.GRID,"Snap 1.0");
        x=statusItem(c,x,cy,Icons.SECTORS,level.sectors.size()+" sectors");
        x=statusItem(c,x,cy,Icons.ENTITIES,level.things.size()+" entities");
        ui.textMid(c,Math.round(zoom*100)+"%",right-dp(12),cy,11,Ui.TEXT_3,1,1);
    }
    private float statusItem(Canvas c,float x,float cy,int icon,String text){
        ui.icon(c,icon,x+dp(6),cy,12,Ui.TEXT_3);ui.textMid(c,text,x+dp(17),cy,11,Ui.TEXT_2,0,-1);
        return x+dp(17)+ui.measure(text,11,0)+dp(18);
    }

    private void drawInspector(Canvas c){
        float x0=mapRight(),pad=dp(10),l=x0+pad,rr=w-pad;
        ui.rect(c,x0,toolbarH,w,h,Ui.SURFACE);ui.vline(c,x0,toolbarH,h,Ui.BORDER);
        if(mode==POLY){
            // Page tabs
            float tt=toolbarH+dp(10),th=dp(30),tw=(rr-l)/3f;
            ui.round(c,l,tt,rr,tt+th,dp(8),Ui.BG);
            for(int i=0;i<3;i++){float a=l+i*tw,b=a+tw;
                if(i==toolPage)ui.round(c,a+dp(3),tt+dp(3),b-dp(3),tt+th-dp(3),dp(6),Ui.SURFACE_3);
                ui.textMid(c,PAGE_NAMES[i],(a+b)*.5f,tt+th*.5f,12,i==toolPage?Ui.TEXT:Ui.TEXT_3,1,0);
                ui.hit(a,tt,b,tt+th,H_PAGE+i);}
            // Tool grid
            float cardH=dp(70),gridTop=tt+th+dp(10),gridBottom=h-cardH-dp(18);
            float rowH=Math.min(dp(38),(gridBottom-gridTop)/6f),colW=(rr-l)/2f,gap=dp(3);
            String[] names=TOOL_NAMES[toolPage];int[] icons=TOOL_ICONS[toolPage];
            for(int i=0;i<names.length;i++){
                int col=i%2,row=i/2;r.set(l+col*colW+(col==0?0:gap),gridTop+row*rowH,l+(col+1)*colW-(col==0?gap:0),gridTop+(row+1)*rowH-gap*2);
                boolean active=(toolPage==0&&i<=2&&polygonTool==i)||(toolPage==1&&i==7&&polygonTool==3)||(toolPage==2&&i<=2&&materialSurface==i);
                int style=active?Ui.ACTIVE:(toolPage==1&&i==4)?Ui.DANGER:Ui.NORMAL;
                ui.button(c,r,icons[i],names[i],style);ui.hit(r,H_TOOL+i);
            }
            // Selected sector card
            float ct=h-cardH-dp(10);
            ui.round(c,l,ct,rr,h-dp(10),dp(8),Ui.SURFACE_2);ui.outline(c,l,ct,rr,h-dp(10),dp(8),0x14ffffff,dp(1));
            if(selectedSector>=0&&selectedSector<level.sectors.size()){
                Level.Sector s=level.sectors.get(selectedSector);
                ui.caption(c,"Sector "+(selectedSector+1),l+dp(10),ct+dp(18),Ui.TEXT_3);
                float cw=(rr-l-dp(20))/3f;
                stat(c,l+dp(10),ct+dp(46),fmt(s.floor),"Floor");stat(c,l+dp(10)+cw,ct+dp(46),fmt(s.ceiling),"Ceiling");stat(c,l+dp(10)+cw*2,ct+dp(46),String.valueOf(s.light),"Light");
            }else{
                ui.caption(c,"No sector selected",l+dp(10),ct+dp(18),Ui.TEXT_3);
                ui.text(c,"Use Select, then Apply on a sector",l+dp(10),ct+dp(42),11.5f,Ui.TEXT_2);
                ui.text(c,"to edit heights and surfaces.",l+dp(10),ct+dp(58),11.5f,Ui.TEXT_2);
            }
        }else if(mode==THINGS){
            ui.caption(c,"Place entity",l,toolbarH+dp(26),Ui.TEXT_3);
            String[] names={"Enemy","Medkit","Remove"};int[] icons={Icons.SKULL,Icons.MEDKIT,Icons.TRASH};
            for(int i=0;i<3;i++){r.set(l,toolbarH+dp(36)+i*dp(46),rr,toolbarH+dp(36)+i*dp(46)+dp(40));
                ui.button(c,r,icons[i],names[i],thingType==i?Ui.ACTIVE:i==2?Ui.DANGER:Ui.NORMAL);ui.hit(r,H_THING+i);}
            float ty=toolbarH+dp(36)+3*dp(46)+dp(14);
            ui.text(c,"Move the cursor with the D-pad,",l,ty,11.5f,Ui.TEXT_2);
            ui.text(c,"then tap Apply to place or remove.",l,ty+dp(17),11.5f,Ui.TEXT_2);
            ui.text(c,"Entities must sit inside a sector.",l,ty+dp(34),11.5f,Ui.TEXT_3);
        }
    }
    private static String fmt(float v){return Math.abs(v-Math.round(v))<.001f?String.valueOf(Math.round(v)):String.format(java.util.Locale.US,"%.2f",v);}
    private void stat(Canvas c,float x,float y,String value,String label){ui.text(c,value,x,y,15,Ui.TEXT,1,-1);ui.text(c,label,x,y+dp(14),10.5f,Ui.TEXT_3);}

    private int dpadHit(float x,float y){
        float dx=x-dpadCX(),dy=y-dpadCY(),d=(float)Math.hypot(dx,dy);
        if(d>dpadR()*1.1f||d<dpadR()*.2f)return -1;
        return Math.abs(dx)>Math.abs(dy)?(dx>0?1:3):(dy>0?2:0);
    }
    private void drawCursorControls(Canvas c){
        float cx=dpadCX(),cy=dpadCY(),R=dpadR();
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);c.drawCircle(cx,cy+dp(3),R,p);
        p.setColor(0xe6252a31);c.drawCircle(cx,cy,R,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x24ffffff);c.drawCircle(cx,cy,R,p);c.drawCircle(cx,cy,R*.34f,p);
        p.setStyle(Paint.Style.FILL);
        if(dpadDirection>=0){p.setColor(0x403d8bfd);r.set(cx-R,cy-R,cx+R,cy+R);c.drawArc(r,new float[]{225,315,45,135}[dpadDirection],90,true,p);p.setColor(0xe6252a31);c.drawCircle(cx,cy,R*.34f,p);}
        int[] icons={Icons.CHEVRON_UP,Icons.CHEVRON_RIGHT,Icons.CHEVRON_DOWN,Icons.CHEVRON_LEFT};
        for(int i=0;i<4;i++){float ox=i==1?1:i==3?-1:0,oy=i==2?1:i==0?-1:0;
            ui.icon(c,icons[i],cx+ox*R*.67f,cy+oy*R*.67f,22,i==dpadDirection?0xff8fbcff:Ui.TEXT);}
        p.setColor(0x33ffffff);c.drawCircle(cx,cy,dp(3),p);
        // Apply button with contextual action label.
        float ax=applyCX(),ay=applyCY(),ar=applyR();
        p.setColor(0x66000000);c.drawCircle(ax,ay+dp(3),ar,p);
        p.setColor(Ui.ACCENT);c.drawCircle(ax,ay,ar,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x33ffffff);c.drawCircle(ax,ay,ar-dp(.5f),p);p.setStyle(Paint.Style.FILL);
        ui.icon(c,Icons.CHECK,ax,ay,26,0xffffffff);
        ui.hit(ax-ar-dp(6),ay-ar-dp(6),ax+ar+dp(6),ay+ar+dp(6),H_APPLY);
        String action=applyActionLabel();
        float tw=ui.measure(action,11.5f,1)+dp(22),pt=ay-ar-dp(40);
        ui.pill(c,ax+ar-tw,pt,ax+ar,pt+dp(26),0xf2262c34);
        ui.textMid(c,action,ax+ar-tw*.5f,pt+dp(13),11.5f,Ui.TEXT,1,0);
    }
    private String applyActionLabel(){
        if(mode==THINGS)return thingType==0?"Place enemy":thingType==1?"Place medkit":"Remove entity";
        switch(polygonTool){
            case 0: return pending.size()>=3?"Add corner · "+pending.size():pending.isEmpty()?"Add first corner":"Add corner · "+pending.size();
            case 1: return "Select here";
            case 2: return rectStartX<0?"Set first corner":"Set opposite corner";
            default: return "Drag a wall";
        }
    }

    private void draw3DOverlay(Canvas c){
        float left=browserWidth(),pad=dp(10);
        ui.rect(c,0,toolbarH,left,h,Ui.SURFACE);ui.vline(c,left-dp(1),toolbarH,h,Ui.BORDER);
        ui.caption(c,"Textures",pad,toolbarH+dp(24),Ui.TEXT_3);
        float size=(left-pad*2-dp(8))/2f;
        for(int i=0;i<6;i++){
            int col=i%2,row=i/2;
            float tx=pad+col*(size+dp(8)),ty=toolbarH+dp(36)+row*(size+dp(8));
            if(materialPreviews[i]==null)materialPreviews[i]=PolygonRenderer.texturePreview(i);
            int save=c.save();path.reset();r.set(tx,ty,tx+size,ty+size);path.addRoundRect(r,dp(6),dp(6),Path.Direction.CW);c.clipPath(path);
            c.drawBitmap(materialPreviews[i],null,r,pixelPaint);c.restoreToCount(save);
            boolean on=i==selectedMaterial;
            ui.outline(c,tx,ty,tx+size,ty+size,dp(6),on?Ui.ACCENT:0x26ffffff,on?dp(2.5f):dp(1));
            if(on){p.setColor(Ui.ACCENT);c.drawCircle(tx+size-dp(9),ty+dp(9),dp(7),p);ui.icon(c,Icons.CHECK,tx+size-dp(9),ty+dp(9),10,0xffffffff);}
            ui.hit(tx,ty,tx+size,ty+size,H_MAT+i);
        }
        ui.text(c,"Pick a texture, then",pad,h-dp(30),11,Ui.TEXT_3);
        ui.text(c,"double-tap a surface.",pad,h-dp(14),11,Ui.TEXT_3);
        drawFloatingJoystick(c);
        if(selectedSector>=0&&selectedSurface>=0){
            String kind=selectedSurface==0?"Wall":selectedSurface==1?"Floor":"Ceiling";
            int icon=selectedSurface==0?Icons.WALL_TEX:selectedSurface==1?Icons.FLOOR_TEX:Icons.CEIL_TEX;
            String t=kind+"  ·  Sector "+(selectedSector+1);
            float tw=ui.measure(t,12.5f,1)+dp(50),cx=(left+w)*.5f,bt=h-dp(48);
            ui.pill(c,cx-tw*.5f,bt,cx+tw*.5f,bt+dp(34),0xf2262c34);
            ui.icon(c,icon,cx-tw*.5f+dp(20),bt+dp(17),16,0xff8fbcff);ui.textMid(c,t,cx-tw*.5f+dp(36),bt+dp(17),12.5f,Ui.TEXT,1,-1);
            if(selectedSurface!=0){
                float bw=dp(64),bh=dp(58),bx=w-dp(14)-bw,cy=(toolbarH+h)*.5f;
                r.set(bx,cy-bh-dp(5),bx+bw,cy-dp(5));ui.stackButton(c,r,Icons.ARROW_UP,"Raise",Ui.NORMAL);ui.hit(r,H_RAISE);
                r.set(bx,cy+dp(5),bx+bw,cy+bh+dp(5));ui.stackButton(c,r,Icons.ARROW_DOWN,"Lower",Ui.NORMAL);ui.hit(r,H_LOWER);
            }
        }
    }

    private void drawPlayControls(Canvas c){
        drawFloatingJoystick(c);
        // Fire
        float fr=dp(40),fx=w-dp(28)-fr,fy=h-dp(28)-fr;
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);c.drawCircle(fx,fy+dp(3),fr,p);
        p.setColor(0xffe0533d);c.drawCircle(fx,fy,fr,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x40ffffff);c.drawCircle(fx,fy,fr-dp(.5f),p);p.setStyle(Paint.Style.FILL);
        ui.icon(c,Icons.CROSSHAIR,fx,fy-dp(5),26,0xffffffff);ui.text(c,"FIRE",fx,fy+dp(22),10,0xffffffff,2,0);
        ui.hit(fx-fr,fy-fr,fx+fr,fy+fr,H_FIRE);
        // HUD
        String[] vals={String.valueOf(health),String.valueOf(ammo),String.valueOf(kills)};int[] icons={Icons.HEART,Icons.ZAP,Icons.SKULL};int[] cols={0xffff7a7a,Ui.AMBER,Ui.TEXT_2};
        float itemW=dp(78),total=itemW*3,hx=w*.5f-total*.5f,ht=h-dp(52);
        ui.pill(c,hx,ht,hx+total,ht+dp(36),0xe61a1e23);
        for(int i=0;i<3;i++){float ix=hx+i*itemW;ui.icon(c,icons[i],ix+dp(24),ht+dp(18),16,cols[i]);ui.textMid(c,vals[i],ix+dp(38),ht+dp(18),15,Ui.TEXT,2,-1);
            if(i>0)ui.rect(c,ix,ht+dp(10),ix+dp(1),ht+dp(26),0x22ffffff);}
        // Crosshair
        float cx=w*.5f,cy=(toolbarH+h)*.5f,g=dp(4),len=dp(11);
        p.setStrokeCap(Paint.Cap.ROUND);
        for(int pass=0;pass<2;pass++){p.setColor(pass==0?0x99000000:0xffffffff);p.setStrokeWidth(pass==0?dp(3.5f):dp(1.75f));
            c.drawLine(cx-len,cy,cx-g,cy,p);c.drawLine(cx+g,cy,cx+len,cy,p);c.drawLine(cx,cy-len,cx,cy-g,p);c.drawLine(cx,cy+g,cx,cy+len,p);}
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    // ---------------------------------------------------------------- editing logic
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2500;invalidate();}
    private void cursorStep(int direction){
        if(direction==0)cursorY=Math.max(-1000000,cursorY-1);
        if(direction==1)cursorX=Math.min(1000000,cursorX+1);
        if(direction==2)cursorY=Math.min(1000000,cursorY+1);
        if(direction==3)cursorX=Math.max(-1000000,cursorX-1);
        invalidate();
    }
    private void cursorApply(){
        float x=mapLeft+cursorX*cell,y=mapTop+cursorY*cell;
        if(mode==THINGS){applyMap(x+cell*.5f,y+cell*.5f);return;}
        if(mode!=POLY)return;
        if(polygonTool==2){
            if(rectStartX<0){rectStartX=cursorX;rectStartY=cursorY;hint("Move to the opposite corner, then Apply");}
            else {if(sectorEditor.rectangle(rectStartX,rectStartY,cursorX,cursorY)){selectedSector=level.sectors.size()-1;autoSave();hint("Rectangle created");}else hint(sectorEditor.error);
                rectStartX=rectStartY=-1;}
        }else if(polygonTool==1){
            selectedSector=level.sectorAt(cursorX+.25f,cursorY+.25f);
            int nearest=-1;float best=.3f;
            for(int i=0;i<level.vertices.size();i++){
                Level.Vertex v=level.vertices.get(i);
                float d=(float)Math.hypot(v.x-cursorX,v.y-cursorY);
                if(d<best){best=d;nearest=i;}
            }
            if(nearest>=0){
                dragVertex=nearest;
                for(int k=0;k<level.sectors.size();k++)
                    if(level.sectors.get(k).loop.contains(nearest)){selectedSector=k;break;}
                hint("Vertex "+(nearest+1)+" selected");
            }else hint(selectedSector<0?"No sector under the cursor":"Sector "+(selectedSector+1)+" selected");
        }else if(polygonTool==0){
            float[] pt=sectorEditor.snapPoint(cursorX,cursorY,1f);
            if(pending.size()>=3&&Math.hypot(pt[0]-pending.get(0)[0],pt[1]-pending.get(0)[1])<.01){closeSector();return;}
            for(float[] v:pending)if(Math.hypot(v[0]-pt[0],v[1]-pt[1])<.01){hint("Corner already added");return;}
            pending.add(pt);hint(pending.size()<3?"Corner "+pending.size()+" added":"Corner "+pending.size()+" added · Close shape to finish");
        }else if(polygonTool==3){hint("Drag a wall of the selected sector to move it");}
    }
    private void save(){try{
        SharedPreferences pref=getContext().getSharedPreferences("project",0);
        String previous=pref.getString("level",null);
        SharedPreferences.Editor edit=pref.edit();
        if(previous!=null)edit.putString("level_backup",previous);
        edit.putString("level",level.toJson().toString()).apply();
        java.util.ArrayList<String> errors=MapValidator.check(level);
        hint(errors.isEmpty()?"Saved · map is valid":"Saved · "+errors.size()+" map warning(s)");
    }catch(Exception e){hint("Save failed");}}
    private void resetLevel(){level=new Level();sectorEditor=new SectorEditor(level);pending.clear();selectedSector=-1;camX=level.playerX;camY=level.playerY;camAngle=level.facing;hint("Level reset");}
    private void changeMode(int next){mode=next;pending.clear();rectStartX=rectStartY=-1;moving=looking=false;moveX=moveY=0;activePointer=lookPointer=-1;joyVisible=false;last3DFrame=System.nanoTime();
        if(next==PLAY){runtimeGraph=FSMGraph.fromJson(getContext().getSharedPreferences("project",0).getString("fsm_graph",""));runtimeGraph.reset();camX=level.playerX;camY=level.playerY;camAngle=level.facing;health=100;ammo=30;kills=0;lastTime=System.nanoTime();}
        hint(next==PLAY?"Left side to move · right side to look":next==POLY?"Move the cursor and tap Apply to draw":next==THINGS?"Choose an entity, then Apply to place it":"Drag left to move · drag right to look");}
    private boolean inMap(float x,float y){return x>=0&&y>=toolbarH&&x<mapRight()&&y<mapBottom();}
    private float wx(float x){return Math.round((x-mapLeft)/cell);}
    private float wy(float y){return Math.round((y-mapTop)/cell);}
    private void closeSector(){
        if(sectorEditor.addSector(pending)){
            selectedSector=level.sectors.size()-1;pending.clear();hint("Sector created");autoSave();
        }else hint(sectorEditor.error);
    }
    private void autoSave(){try{getContext().getSharedPreferences("project",0).edit().putString("level",level.toJson().toString()).apply();}catch(Exception ignored){}}
    private void restoreEditor(){level=sectorEditor.level;selectedSector=-1;pending.clear();autoSave();invalidate();}
    private void polygonTap(float x,float y){if(!inMap(x,y))return;
        float gx=wx(x),gy=wy(y);float[] point=sectorEditor.snapPoint(gx,gy,1f);
        if(polygonTool==1){selectedSector=level.sectorAt(point[0],point[1]);hint(selectedSector>=0?"Sector "+(selectedSector+1)+" selected":"No sector selected");return;}
        if(pending.size()>=3&&Math.hypot(point[0]-pending.get(0)[0],point[1]-pending.get(0)[1])<.1){closeSector();return;}
        for(float[] v:pending)if(Math.hypot(point[0]-v[0],point[1]-v[1])<.08){hint("Point already in drawing");return;}
        pending.add(point);hint("Point "+pending.size()+" added");
    }
    private void sectorAction(int row){
        int id=toolPage*12+row;
        if(id==0||id==1||id==2){polygonTool=id;pending.clear();rectStartX=rectStartY=-1;hint(id==2?"Apply on the first corner, then the opposite corner":id==1?"Move the cursor and Apply to select":"Move the cursor and Apply on each corner");return;}
        if(id==3){closeSector();return;}
        if(id==4){if(!pending.isEmpty()){pending.remove(pending.size()-1);hint("Point removed");}else if(sectorEditor.undo()){restoreEditor();hint("Undo");}return;}
        if(id==5){pending.clear();rectStartX=rectStartY=-1;hint("Cancelled");return;}
        if(id==10){if(sectorEditor.undo()){restoreEditor();hint("Undo");}else hint("Nothing to undo");return;}
        if(id==12){if(sectorEditor.redo()){restoreEditor();hint("Redo");}else hint("Nothing to redo");return;}
        if(id==13){
            java.util.ArrayList<String> issues=MapValidator.check(level);
            hint(issues.isEmpty()?"Map validation: no issues":issues.size()+" issue(s): "+issues.get(0));return;}
        if(id==14){int n=sectorEditor.cleanOrphans();if(n>0)autoSave();hint("Cleaned "+n+" unused vertices");return;}
        if(id==32){hint("Snap: whole grid corners only");return;}
        if(id==33){changeMode(POLY3D);hint("Tap the 3D view to select a surface");return;}
        if(id==34){hint("Select a sector, then choose an editing action");return;}
        if(id==24||id==25||id==26){materialSurface=id-24;hint((id==24?"Wall":id==25?"Floor":"Ceiling")+" texture selected");return;}
        if(selectedSector<0||selectedSector>=level.sectors.size()){hint("Select a sector first");return;}
        Level.Sector sec=level.sectors.get(selectedSector);boolean changed=false;
        if(id==6)changed=sectorEditor.changeHeight(selectedSector,.25f,0);
        if(id==7)changed=sectorEditor.changeHeight(selectedSector,-.25f,0);
        if(id==8)changed=sectorEditor.changeHeight(selectedSector,0,.25f);
        if(id==9)changed=sectorEditor.changeHeight(selectedSector,0,-.25f);
        if(id==15)changed=sectorEditor.duplicateSector(selectedSector,1,1);
        if(id==16){changed=sectorEditor.deleteSector(selectedSector);selectedSector=-1;}
        if(id==17){Level.Vertex a=level.vertices.get(sec.loop.get(selectedEdge%sec.loop.size())),b=level.vertices.get(sec.loop.get((selectedEdge+1)%sec.loop.size()));
            float mx=(a.x+b.x)*.5f,my=(a.y+b.y)*.5f;
            if(Math.abs(mx-Math.round(mx))<.001&&Math.abs(my-Math.round(my))<.001)changed=sectorEditor.splitWall(selectedSector,selectedEdge%sec.loop.size(),mx,my);
            else hint("Wall midpoint is not on a grid corner");}
        if(id==18)changed=sectorEditor.extrude(selectedSector,selectedEdge%sec.loop.size(),2);
        if(id==19){polygonTool=3;hint("Tap near a wall, then drag to move it");return;}
        if(id==20){changed=sectorEditor.setHeights(selectedSector,sec.floor+.5f,sec.ceiling);}
        if(id==21){changed=sectorEditor.setHeights(selectedSector,sec.floor,sec.floor+1.5f);}
        if(id==22){changed=sectorEditor.rectangle(2,13,6,17);if(!changed)hint("Prefab location occupied; use the rectangle tool");}
        if(id==27||id==28){int current=materialSurface==0?sec.wallMaterial:materialSurface==1?sec.floorMaterial:sec.ceilingMaterial;
            changed=sectorEditor.setMaterial(selectedSector,materialSurface,(current+(id==27?1:5))%6);}
        if(id==29)changed=sectorEditor.setHeights(selectedSector,sec.floor+.25f,sec.ceiling);
        if(id==30||id==31){
            sec.light=Math.max(16,Math.min(255,sec.light+(id==30?16:-16)));
            autoSave();hint("Sector brightness "+sec.light+"/255");invalidate();return;}
        if(changed){autoSave();hint("Map updated");}else if(!sectorEditor.error.isEmpty())hint(sectorEditor.error);
    }
    private void applyMap(float x,float y){
        if(!inMap(x,y)||mode!=THINGS)return;
        float px=(x-mapLeft)/cell,py=(y-mapTop)/cell;
        int sector=level.sectorAt(px,py);
        if(sector<0){hint("Place entities inside a sector");return;}
        if(thingType==2){Level.Thing nearest=null;float best=.85f;
            for(Level.Thing t:level.things){float d=(float)Math.hypot(t.x-px,t.y-py);if(d<best){best=d;nearest=t;}}
            if(nearest!=null){level.things.remove(nearest);autoSave();hint("Entity removed");}else hint("No entity here");
        }else{
            for(Level.Thing t:level.things)if(Math.hypot(t.x-px,t.y-py)<.35f){hint("Entity too close");return;}
            level.things.add(new Level.Thing(px,py,thingType));autoSave();hint("Entity placed in sector "+(sector+1));
        }
    }
    private boolean walkable(float x,float y,float nx,float ny){return PortalTopology.canCross(level,x,y,nx,ny,.6f,1.5f);}
    private void updateGame(){long now=System.nanoTime();float dt=Math.min(.05f,(now-lastTime)*1e-9f);lastTime=now;
        if(runtimeGraph!=null)runtimeGraph.tick(dt,level);
        if(moving){float f=-moveY*3f*dt,st=moveX*3f*dt;
            float nx=camX+(float)Math.cos(camAngle)*f-(float)Math.sin(camAngle)*st;
            float ny=camY+(float)Math.sin(camAngle)*f+(float)Math.cos(camAngle)*st;
            if(walkable(camX,camY,nx,camY))camX=nx;if(walkable(camX,camY,camX,ny))camY=ny;}
        if(fireDown){fireDown=false;fire();}}
    private void fire(){if(runtimeGraph!=null&&mode==PLAY)runtimeGraph.event("FIRE");if(ammo<=0){hint("No ammo");return;}ammo--;
        Level.Thing target=null;float best=15;
        for(Level.Thing t:level.things){if(t.type!=0)continue;float dx=t.x-camX,dy=t.y-camY,dist=(float)Math.hypot(dx,dy);
            float delta=(float)Math.atan2(dy,dx)-camAngle;while(delta>Math.PI)delta-=2*Math.PI;while(delta<-Math.PI)delta+=2*Math.PI;
            if(Math.abs(delta)<.15f&&dist<best){best=dist;target=t;}}
        if(target!=null){level.things.remove(target);kills++;hint("Enemy eliminated");}}

    // ---------------------------------------------------------------- touch
    @Override public boolean onTouchEvent(MotionEvent e){
        if(drawError!=null){if(e.getActionMasked()==MotionEvent.ACTION_UP){drawError=null;mode=POLY;selectedSector=-1;pending.clear();invalidate();}return true;}
        try{return handleTouch(e);}catch(RuntimeException ex){DoomApp.record(getContext(),ex);drawError=ex.getClass().getSimpleName()+": "+ex.getMessage();invalidate();return true;}
    }
    private boolean toolbarTap(int hid){
        if(hid>=H_MODE&&hid<H_MODE+4){changeMode(MODES[hid-H_MODE]);return true;}
        if(hid==H_FSM){try{getContext().startActivity(new android.content.Intent(getContext(),FSMEditorActivity.class));}catch(RuntimeException ex){hint("Could not open the FSM editor");}return true;}
        if(hid==H_SAVE){save();return true;}
        if(hid==H_RESET){
            if(System.currentTimeMillis()<resetArmedUntil){resetArmedUntil=0;resetLevel();}
            else{resetArmedUntil=System.currentTimeMillis()+3000;hint("Tap Confirm to reset the whole level");postInvalidateDelayed(3010);}
            return true;}
        return false;
    }
    private boolean handle3DTouch(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex(),id=e.getPointerId(index);
        float x=e.getX(index),y=e.getY(index),left=mode==PLAY?0:browserWidth();
        if(mode==PLAY){
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                if(y<toolbarH)return false;
                // FIRE stays separate from the right-hand look area.
                if(ui.hitAt(x,y)==H_FIRE){fireDown=true;fire();fireDown=false;invalidate();return true;}
                if(x<w*.5f){
                    if(activePointer<0){activePointer=id;joyVisible=true;joyOriginX=joyX=x;joyOriginY=joyY=y;moveX=moveY=0;moving=true;}
                }else if(lookPointer<0){lookPointer=id;lookLastX=x;lookLastY=y;}
                invalidate();return true;
            }
            if(action==MotionEvent.ACTION_MOVE){
                for(int i=0;i<e.getPointerCount();i++){
                    int pid=e.getPointerId(i);
                    if(pid==activePointer)moveJoystick(e.getX(i),e.getY(i));
                    if(pid==lookPointer){camAngle+=(e.getX(i)-lookLastX)*.007f;lookLastX=e.getX(i);lookLastY=e.getY(i);}
                }
                invalidate();return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
                if(id==activePointer){activePointer=-1;joyVisible=false;joyReleaseTime=System.currentTimeMillis();moveX=moveY=0;moving=false;}
                if(id==lookPointer)lookPointer=-1;
                invalidate();return true;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(y<toolbarH)return false;
            int hid=ui.hitAt(x,y);
            if(hid>=H_MAT&&hid<H_MAT+6){selectedMaterial=hid-H_MAT;hint("Texture "+(selectedMaterial+1)+" selected");return true;}
            if(x<left)return true;
            if((hid==H_RAISE||hid==H_LOWER)&&selectedSurface>0&&selectedSector>=0){
                float delta=hid==H_RAISE?0.25f:-0.25f;
                boolean ok=selectedSurface==1?sectorEditor.changeHeight(selectedSector,delta,0):sectorEditor.changeHeight(selectedSector,0,delta);
                if(ok){autoSave();hint("Height updated");}else hint(sectorEditor.error);
                return true;
            }
            if(x<left+(w-left)*.47f){
                if(activePointer<0){activePointer=id;joyVisible=true;joyOriginX=joyX=x;joyOriginY=joyY=y;moveX=moveY=0;}
            }else if(lookPointer<0){lookPointer=id;lookLastX=x;lookLastY=y;downX=x;downY=y;}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<e.getPointerCount();i++){
                int pid=e.getPointerId(i);float px=e.getX(i),py=e.getY(i);
                if(pid==activePointer)moveJoystick(px,py);
                if(pid==lookPointer){camAngle+=(px-lookLastX)*.007f;lookLastX=px;lookLastY=py;}
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            if(id==lookPointer){
                float tx=x,ty=y;
                if(action!=MotionEvent.ACTION_CANCEL&&Math.hypot(tx-downX,ty-downY)<dp(10)){
                    int hit=PolygonRenderer.pick(tx,ty,left,toolbarH,w,h);
                    if(hit>=0){
                        int sector=hit>=100000?selectedSector:hit/3,surface=hit>=100000?0:hit%3;
                        if(hit>=100000){
                            java.util.ArrayList<Level.LineDef> lines=PortalTopology.build(level);
                            int li=(hit-100000)/2;
                            if(li>=0&&li<lines.size())sector=lines.get(li).front;
                        }
                        boolean twice=System.currentTimeMillis()-lastTapTime<380&&Math.hypot(tx-lastTapX,ty-lastTapY)<dp(14);
                        selectedSector=sector;selectedSurface=surface;selectedWallHit=hit>=100000?hit:-1;
                        if(twice){
                            boolean ok=hit>=100000?PolygonRenderer.applyWallMaterial(getContext(),hit,selectedMaterial):sectorEditor.setMaterial(sector,surface,selectedMaterial);
                            if(ok){autoSave();hint("Texture applied");}else hint(sectorEditor.error);
                        }else hint((surface==0?"Wall":surface==1?"Floor":"Ceiling")+" selected · double-tap to apply texture");
                        lastTapTime=System.currentTimeMillis();lastTapX=tx;lastTapY=ty;
                    }else{selectedSector=-1;selectedSurface=-1;}
                }
                lookPointer=-1;
            }
            if(id==activePointer){activePointer=-1;joyVisible=false;joyReleaseTime=System.currentTimeMillis();moveX=moveY=0;}
            invalidate();return true;
        }
        return true;
    }
    private boolean handleTouch(MotionEvent e){int action=e.getActionMasked(),idx=e.getActionIndex();float x=e.getX(idx),y=e.getY(idx);
        if(in2D()&&action==MotionEvent.ACTION_DOWN&&y>=toolbarH&&x<mapRight()){
            int direction=dpadHit(x,y);
            if(direction>=0){dpadPointer=e.getPointerId(idx);dpadDirection=direction;cursorStep(direction);nextDpadRepeat=System.currentTimeMillis()+270;return true;}
            if(ui.hitAt(x,y)==H_APPLY){cursorApply();invalidate();return true;}
        }
        if(in2D()&&action==MotionEvent.ACTION_MOVE&&dpadPointer>=0){
            int direction=dpadHit(e.getX(0),e.getY(0));
            if(direction!=dpadDirection){dpadDirection=direction;invalidate();}
            if(direction>=0&&System.currentTimeMillis()>=nextDpadRepeat){cursorStep(direction);nextDpadRepeat=System.currentTimeMillis()+95;}
            return true;
        }
        if(in2D()&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&dpadPointer>=0){
            dpadPointer=-1;dpadDirection=-1;invalidate();return true;
        }
        if((in3DEdit()||mode==PLAY)&&(y>=toolbarH||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL))return handle3DTouch(e);
        if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2&&in2D()){
            pinchDistance=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;dragging=true;return true;}
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=x;downY=lastY=y;dragging=false;touchStartX=x;touchStartY=y;
            if(y<toolbarH){toolbarTap(ui.hitAt(x,y));invalidate();return true;}
            if(in2D()&&x>=mapRight()){
                int hid=ui.hitAt(x,y);
                if(hid>=H_PAGE&&hid<H_PAGE+3){toolPage=hid-H_PAGE;}
                else if(hid>=H_TOOL&&hid<H_TOOL+11&&mode==POLY){sectorAction(hid-H_TOOL);}
                else if(hid>=H_THING&&hid<H_THING+3&&mode==THINGS){thingType=hid-H_THING;}
                invalidate();return true;}
            if(mode==POLY&&polygonTool==3){
                if(selectedSector>=0&&selectedSector<level.sectors.size()){
                    Level.Sector sec=level.sectors.get(selectedSector);float best=dp(24);
                    for(int j=0;j<sec.loop.size();j++){
                        Level.Vertex a=level.vertices.get(sec.loop.get(j)),b=level.vertices.get(sec.loop.get((j+1)%sec.loop.size()));
                        float ax=sx(a.x),ay=sy(a.y),bx=sx(b.x),by=sy(b.y);
                        float dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
                        float t=den<.001?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/den));
                        float d=(float)Math.hypot(x-ax-t*dx,y-ay-t*dy);
                        if(d<best){best=d;selectedEdge=j;}
                    }
                    hint("Drag the highlighted wall");
                }else hint("Select a sector first");
            }else if(mode==POLY&&polygonTool==1){
                dragVertex=-1;float best=dp(16);
                for(int i=0;i<level.vertices.size();i++){
                    Level.Vertex v=level.vertices.get(i);float d=(float)Math.hypot(sx(v.x)-x,sy(v.y)-y);
                    if(d<best){best=d;dragVertex=i;}
                }
                if(dragVertex<0)polygonTap(x,y);
            }else if(mode==POLY&&polygonTool==2){hint("Drag to the opposite corner");}else if(in2D()){hint("Use the D-pad and Apply to edit");}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(in2D()&&e.getPointerCount()>=2){float ax=e.getX(0),ay=e.getY(0),bx=e.getX(1),by=e.getY(1);
                float dist=(float)Math.hypot(ax-bx,ay-by);if(pinchDistance>1){float factor=dist/pinchDistance;zoom=Math.max(.5f,Math.min(4f,zoom*factor));}pinchDistance=dist;
                float mx=(ax+bx)*.5f,my=(ay+by)*.5f;panX+=(mx-lastX);panY+=(my-lastY);lastX=mx;lastY=my;dragging=true;
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP){pinchDistance=0;dragVertex=-1;invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(action==MotionEvent.ACTION_UP&&mode==POLY&&polygonTool==3&&downX<mapRight()&&selectedSector>=0){
                float dx=Math.round((e.getX()-downX)/cell),dy=Math.round((e.getY()-downY)/cell);
                if((dx!=0||dy!=0)&&sectorEditor.moveEdge(selectedSector,selectedEdge,dx,dy)){autoSave();hint("Wall moved");}
                else if(dx!=0||dy!=0)hint(sectorEditor.error);
            }
            if(action==MotionEvent.ACTION_UP&&mode==POLY&&polygonTool==2&&!dragging&&downX<mapRight()&&downY>toolbarH){
                int ax=Math.round(wx(downX)),ay=Math.round(wy(downY)),bx=Math.round(wx(e.getX())),by=Math.round(wy(e.getY()));
                if(ax!=bx&&ay!=by){if(sectorEditor.rectangle(ax,ay,bx,by)){selectedSector=level.sectors.size()-1;autoSave();hint("Rectangle created");}else hint(sectorEditor.error);}
            }
            if(action==MotionEvent.ACTION_UP&&mode==POLY&&dragVertex>=0&&e.getPointerCount()==1){
                float ex=e.getX(),ey=e.getY();
                if(Math.hypot(ex-touchStartX,ey-touchStartY)>dp(6)){
                    float[] pt=sectorEditor.snapPoint(wx(ex),wy(ey),1f);
                    if(sectorEditor.moveVertex(dragVertex,pt[0],pt[1])){autoSave();hint("Vertex moved");}
                    else hint(sectorEditor.error);
                }else{for(int k=0;k<level.sectors.size();k++)if(level.sectors.get(k).loop.contains(dragVertex)){selectedSector=k;break;}}
                dragVertex=-1;
            }
            moving=looking=false;activePointer=lookPointer=-1;moveX=moveY=0;pinchDistance=0;invalidate();return true;}
        return true;
    }
}
