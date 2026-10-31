package com.retro.doomeditor;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONObject;
import java.util.ArrayList;

/** Touch-first polygon sector editor. All 3D modes use PolygonRenderer. */
public class EditorView extends View {
    private static final int MAP=0, VIEW3D=1, THINGS=2, PLAY=3, POLY=4, POLY3D=5;
    private final Paint p=new Paint(3);
    private final Level levelDefault=new Level();
    private Level level=levelDefault;
    private int mode=POLY, brush=1, thingType=0, polygonTool=0, selectedSector=-1,toolPage=0,materialSurface=0,selectedEdge=0;
    private final ArrayList<float[]> pending=new ArrayList<>();
    private SectorEditor sectorEditor;
    // 3D editor controls and selected surface (0 wall, 1 floor, 2 ceiling).
    private int selectedSurface=-1,selectedMaterial=0,selectedWallHit=-1;
    private float joyOriginX,joyOriginY,joyX,joyY,lookLastX,lookLastY;
    private boolean joyVisible=false;
    private long joyReleaseTime=0,last3DFrame=System.nanoTime(),lastTapTime=0;
    private float lastTapX,lastTapY;
    private final android.graphics.Bitmap[] materialPreviews=new android.graphics.Bitmap[6];
    private float browserWidth(){return Math.min(170f,Math.max(105f,w*.20f));}
    private boolean in3DEdit(){return mode==VIEW3D||mode==POLY3D;}
    private float viewLeft(){return in3DEdit()?browserWidth():0;}
    private float viewRight(){return in3DEdit()?w:w*.78f;}
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
        float radius=Math.min(100f,Math.max(65f,h*.22f));
        float dx=x-joyOriginX,dy=y-joyOriginY;
        float len=(float)Math.hypot(dx,dy),limit=radius*.68f;
        float scale=len>limit?limit/len:1;
        joyX=joyOriginX+dx*scale;joyY=joyOriginY+dy*scale;
        moveX=(joyX-joyOriginX)/limit;moveY=(joyY-joyOriginY)/limit;
    }
    private void drawFloatingJoystick(Canvas c){
        float alpha=joyVisible?1f:Math.max(0f,1f-(System.currentTimeMillis()-joyReleaseTime)/240f);
        if(alpha<=0)return;
        int a=(int)(alpha*255);
        float radius=Math.min(100f,Math.max(65f,h*.22f));
        color(((a/3)<<24)|0x90a6b9);
        c.drawCircle(joyOriginX,joyOriginY,radius,p);
        p.setColor((a<<24)|0x8fa9b9);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
        c.drawCircle(joyOriginX,joyOriginY,radius,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor((a<<24)|0x9bd9ce);
        c.drawCircle(joyX,joyY,radius*.42f,p);
        if(!joyVisible)postInvalidateDelayed(32);
    }
    private void draw3DOverlay(Canvas c){
        float left=browserWidth();
        panel(c,0,toolbarH,left,h,0xee202a35);
        label(c,"TEXTURES",12,toolbarH+25,Math.min(15,left*.12f),0xffe6eef4);
        for(int i=0;i<6;i++){
            int col=i%2,row=i/2;
            float size=Math.min((left-24)/2f,Math.max(40,(h-toolbarH-75)/3f-8));
            float tx=8+col*(size+6),ty=toolbarH+39+row*(size+10);
            if(materialPreviews[i]==null)materialPreviews[i]=PolygonRenderer.texturePreview(i);
            p.setColor(Color.WHITE);p.setAlpha(255);
            c.drawBitmap(materialPreviews[i],null,new RectF(tx,ty,tx+size,ty+size),p);
            p.setColor(i==selectedMaterial?0xff66eec2:0xff637382);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(i==selectedMaterial?3:1);
            c.drawRect(tx,ty,tx+size,ty+size,p);p.setStyle(Paint.Style.FILL);
        }
        label(c,"Tap texture, then",9,h-39,Math.min(11,left*.095f),0xffcad9e1);
        label(c,"double-tap surface",9,h-24,Math.min(11,left*.095f),0xffcad9e1);
        drawFloatingJoystick(c);
        if(selectedSector>=0&&selectedSurface>=0){
            String kind=selectedSurface==0?"LINEDEF WALL":selectedSurface==1?"FLOOR":"CEILING";
            round(c,left+12,h-43,Math.min(w-15,left+255),h-9,0xdd1a2530);
            label(c,kind+"  |  SECTOR "+(selectedSector+1),left+22,h-22,12,0xffa8f3cb);
            if(selectedSurface!=0){
                float bx=w-90,cy=h*.5f;
                round(c,bx-2,cy-113,w-7,cy-12,0xdd324353);
                round(c,bx-2,cy+12,w-7,cy+113,0xdd324353);
                label(c,"▲",bx+25,cy-42,47,Color.WHITE);
                label(c,"▼",bx+25,cy+70,47,Color.WHITE);
            }
        }
    }
    private boolean handle3DTouch(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex(),id=e.getPointerId(index);
        float x=e.getX(index),y=e.getY(index),left=mode==PLAY?0:browserWidth();
        if(mode==PLAY){
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                if(y<toolbarH)return false;
                // FIRE stays separate from the right-hand look area.
                if(x>w-140&&y>h-150){fireDown=true;fire();fireDown=false;invalidate();return true;}
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
            if(x<left){
                float size=Math.min((left-24)/2f,Math.max(40,(h-toolbarH-75)/3f-8));
                int col=(int)((x-8)/(size+6)),row=(int)((y-toolbarH-39)/(size+10));
                if(x>=8&&y>=toolbarH+39&&col>=0&&col<2&&row>=0&&row<3){selectedMaterial=row*2+col;hint("Material "+selectedMaterial+" selected");}
                return true;
            }
            if(selectedSurface>0&&selectedSector>=0&&x>w-100&&y>h*.5f-115&&y<h*.5f+115){
                float delta=y<h*.5f?0.25f:-0.25f;
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
                int pid=e.getPointerId(i),px=(int)e.getX(i),py=(int)e.getY(i);
                if(pid==activePointer)moveJoystick(px,py);
                if(pid==lookPointer){camAngle+=(px-lookLastX)*.007f;lookLastX=px;lookLastY=py;}
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            if(id==lookPointer){
                float tx=x,ty=y;
                if(action!=MotionEvent.ACTION_CANCEL&&Math.hypot(tx-downX,ty-downY)<14){
                    int hit=PolygonRenderer.pick(tx,ty,left,toolbarH,w,h);
                    if(hit>=0){
                        int sector=hit>=100000?selectedSector:hit/3,surface=hit>=100000?0:hit%3;
                        if(hit>=100000){
                            java.util.ArrayList<Level.LineDef> lines=PortalTopology.build(level);
                            int li=(hit-100000)/2;
                            if(li>=0&&li<lines.size())sector=lines.get(li).front;
                        }
                        boolean twice=System.currentTimeMillis()-lastTapTime<380&&Math.hypot(tx-lastTapX,ty-lastTapY)<34;
                        selectedSector=sector;selectedSurface=surface;selectedWallHit=hit>=100000?hit:-1;
                        if(twice){
                            boolean ok=hit>=100000?PolygonRenderer.applyWallMaterial(getContext(),hit,selectedMaterial):sectorEditor.setMaterial(sector,surface,selectedMaterial);
                            if(ok){autoSave();hint("Texture applied");}else hint(sectorEditor.error);
                        }else hint((surface==0?"Wall":surface==1?"Floor":"Ceiling")+" selected");
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
    // D-pad controlled 2D editing cursor. Camera follows by eased pan.
    private int cursorX=5,cursorY=5,rectStartX=-1,rectStartY=-1;
    private float followX=0,followY=0;
    private int dpadPointer=-1,dpadDirection=-1;
    private long nextDpadRepeat=0;
    private float dpadCX(){return Math.max(175f,Math.min(215f,w*.22f));}
    private float dpadCY(){return h-Math.max(165f,Math.min(205f,h*.33f));}
    private float applyCX(){return w*.68f;}
    private float applyCY(){return h-96f;}
    private int dpadHit(float x,float y){
        float dx=x-dpadCX(),dy=y-dpadCY();
        float spacing=108f, radius=54f;
        for(int i=0;i<4;i++){
            float bx=(i==1?spacing:i==3?-spacing:0);
            float by=(i==2?spacing:i==0?-spacing:0);
            if(Math.abs(dx-bx)<=radius&&Math.abs(dy-by)<=radius)return i;
        }
        return -1;
    }
    private void cursorStep(int direction){
        if(direction==0)cursorY=Math.max(0,cursorY-1);
        if(direction==1)cursorX=Math.min(Level.W,cursorX+1);
        if(direction==2)cursorY=Math.min(Level.H,cursorY+1);
        if(direction==3)cursorX=Math.max(0,cursorX-1);
        invalidate();
    }
    private void cursorApply(){
        float x=mapLeft+cursorX*cell,y=mapTop+cursorY*cell;
        if(mode==THINGS){
            // Entities are placed at sector interiors, with half-cell offset.
            applyMap(x+cell*.5f,y+cell*.5f);
            return;
        }
        if(mode!=POLY)return;
        if(polygonTool==2){
            if(rectStartX<0){rectStartX=cursorX;rectStartY=cursorY;hint("Move cursor to opposite corner; APPLY again");}
            else {if(sectorEditor.rectangle(rectStartX,rectStartY,cursorX,cursorY)){selectedSector=level.sectors.size()-1;autoSave();hint("Rectangle created");}else hint(sectorEditor.error);
                rectStartX=rectStartY=-1;}
        }else if(polygonTool==1){
            selectedSector=level.sectorAt(cursorX+.25f,cursorY+.25f);
            hint(selectedSector<0?"No sector under cursor":"Sector "+(selectedSector+1)+" selected");
        }else if(polygonTool==0){
            float[] pt=sectorEditor.snapPoint(cursorX,cursorY,1f);
            if(pending.size()>=3&&Math.hypot(pt[0]-pending.get(0)[0],pt[1]-pending.get(0)[1])<.01){closeSector();return;}
            for(float[] v:pending)if(Math.hypot(v[0]-pt[0],v[1]-pt[1])<.01){hint("Corner already added");return;}
            pending.add(pt);hint("Corner "+pending.size()+" added; CLOSE to finish");
        }else if(polygonTool==3){hint("Select wall in sector tools, then use wall movement");}
    }
    private void drawCursorControls(Canvas c){
        if(mode!=POLY&&mode!=THINGS)return;
        float cx=dpadCX(),cy=dpadCY(),r=52;
        for(int i=0;i<4;i++){
            float bx=cx+(i==1?108:i==3?-108:0),by=cy+(i==2?108:i==0?-108:0);
            round(c,bx-r,by-r,bx+r,by+r,0xee3e5b6c);
            label(c,new String[]{"▲","▶","▼","◀"}[i],bx-19,by+20,46,Color.WHITE);
        }
        round(c,applyCX()-56,applyCY()-48,applyCX()+56,applyCY()+48,0xff286d67);
        label(c,"APPLY",applyCX()-43,applyCY()+9,23,Color.WHITE);
    }
    private int dragVertex=-1;
    private float touchStartX,touchStartY;
    private float w,h,toolbarH,cell,mapLeft,mapTop;
    private float camX,camY,camAngle,moveX,moveY,lastX,lastY,downX,downY;
    private boolean moving,looking,dragging,fireDown;
    private int activePointer=-1,lookPointer=-1,health=100,ammo=30,kills=0;
    private long lastTime=System.nanoTime(),messageUntil;
    private String message="Tap DRAW to create a polygon sector";
    private float zoom=1f,panX=0,panY=0,pinchDistance=0;
    private final int[] palette={0xff333c4b,0xff827761,0xff7d4140,0xff56756c,0xff75639a,0xffa18743};

    EditorView(Context ctx){super(ctx);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        try{String saved=ctx.getSharedPreferences("project",0).getString("level",null);if(saved!=null)level=Level.fromJson(new JSONObject(saved));}catch(Exception ignored){}
        sectorEditor=new SectorEditor(level);PolygonRenderer.loadWallMaterials(ctx);camX=level.playerX;camY=level.playerY;camAngle=level.facing;setFocusable(true);
    }
    boolean exitPlay(){if(mode!=PLAY)return false;mode=POLY3D;moving=looking=false;invalidate();return true;}
    private void color(int col){p.setColor(col);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    private void rect(Canvas c,float l,float t,float r,float b,int col){color(col);c.drawRect(l,t,r,b,p);}
    private void round(Canvas c,float l,float t,float r,float b,int col){
        color(col);p.setShader(new LinearGradient(l,t,l,b,brighten(col,1.24f),brighten(col,.69f),Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(l,t,r,b),9,9,p);p.setShader(null);
    }
    private static int brighten(int c,float amount){return Color.argb(Color.alpha(c),Math.min(255,(int)(Color.red(c)*amount)),Math.min(255,(int)(Color.green(c)*amount)),Math.min(255,(int)(Color.blue(c)*amount)));}
    private void header(Canvas c,float l,float t,float r,float b,int col){
        color(col);p.setShader(new LinearGradient(l,t,l,b,brighten(col,1.40f),brighten(col,.64f),Shader.TileMode.CLAMP));c.drawRect(l,t,r,b,p);p.setShader(null);
    }
    private void panel(Canvas c,float l,float t,float r,float b,int col){
        color(col);float cx=(l+r)*.5f,cy=(t+b)*.5f,rad=(float)Math.hypot(r-l,b-t)*.57f;
        p.setShader(new RadialGradient(cx,cy,Math.max(1,rad),new int[]{brighten(col,1.42f),col,brighten(col,.58f)},null,Shader.TileMode.CLAMP));c.drawRect(l,t,r,b,p);p.setShader(null);
    }
    private void label(Canvas c,String s,float x,float y,float size,int col){color(col);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));p.setTextSize(size);c.drawText(s,x,y,p);}
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2500;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);w=getWidth();h=getHeight();toolbarH=Math.max(52,h*.12f);
        rect(c,0,0,w,h,0xff151b23);
        if(mode==THINGS||mode==POLY)drawMap(c);
        else {
            if(in3DEdit())update3DCamera();
            PolygonRenderer.setHighlight(in3DEdit()?selectedSector:-1,selectedSurface);
            if(in3DEdit()&&selectedWallHit>=100000)PolygonRenderer.setHighlightHit(selectedWallHit);
            PolygonRenderer.render(c,level,viewLeft(),toolbarH,mode==PLAY?w:in3DEdit()?w:w*.78f,h,camX,camY,camAngle);
        }
        drawToolbar(c);
        if(mode==PLAY){drawPlayControls(c);updateGame();postInvalidateDelayed(16);}
        else if(in3DEdit()){draw3DOverlay(c);if(joyVisible)postInvalidateDelayed(16);}
        else {drawTools(c);drawCursorControls(c);}
        if(System.currentTimeMillis()<messageUntil){round(c,w*.20f,h-36,w*.78f,h-6,0xcc121820);label(c,message,w*.22f,h-17,Math.min(15,w*.024f),Color.WHITE);}
    }

    /** Font-independent icons. Coordinates are normalized to a 24-unit vector canvas. */
    private void drawIcon(Canvas c,int type,float cx,float cy,float size,int tint){
        c.save();
        c.translate(cx,cy);
        c.scale(size/24f,size/24f);
        p.setColor(tint);p.setStrokeWidth(2.2f);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        switch(type){
            case 0: // grid
                for(int i=-1;i<=1;i++){
                    float v=i*7f;c.drawLine(v,-10,v,10,p);c.drawLine(-10,v,10,v,p);
                }break;
            case 1: // cube
                Path cube=new Path();cube.moveTo(0,-10);cube.lineTo(9,-5);
                cube.lineTo(9,5);cube.lineTo(0,10);cube.lineTo(-9,5);
                cube.lineTo(-9,-5);cube.close();c.drawPath(cube,p);
                c.drawLine(-9,-5,0,0,p);c.drawLine(9,-5,0,0,p);
                c.drawLine(0,0,0,10,p);break;
            case 2: // entity
                c.drawCircle(0,-6,4,p);c.drawRoundRect(new RectF(-8,0,8,10),3,3,p);break;
            case 3: // play
                Path play=new Path();play.moveTo(-6,-10);play.lineTo(10,0);
                play.lineTo(-6,10);play.close();p.setStyle(Paint.Style.FILL);
                c.drawPath(play,p);break;
            case 4: // sector
                Path hex=new Path();for(int i=0;i<6;i++){
                    double a=i*Math.PI/3-Math.PI/2;
                    float x=(float)Math.cos(a)*10,y=(float)Math.sin(a)*10;
                    if(i==0)hex.moveTo(x,y);else hex.lineTo(x,y);
                }hex.close();c.drawPath(hex,p);break;
            case 5: // viewport monitor
                c.drawRoundRect(new RectF(-11,-9,11,7),2,2,p);
                c.drawLine(0,7,0,11,p);c.drawLine(-6,11,6,11,p);break;
            case 6: // circle
                c.drawCircle(0,0,8,p);break;
            case 7: // pencil
                c.drawLine(-8,8,7,-7,p);c.drawLine(-10,11,-5,10,p);
                c.drawLine(6,-9,10,-5,p);break;
            case 8: // pointer
                Path cursor=new Path();cursor.moveTo(-8,-10);cursor.lineTo(8,1);
                cursor.lineTo(1,3);cursor.lineTo(4,10);cursor.lineTo(0,11);
                cursor.lineTo(-3,4);cursor.lineTo(-8,9);cursor.close();c.drawPath(cursor,p);break;
            case 9: // undo
                c.drawArc(new RectF(-9,-8,9,9),-75,255,false,p);
                c.drawLine(-10,-5,-10,3,p);c.drawLine(-10,3,-3,1,p);break;
            case 10: // x
                c.drawLine(-8,-8,8,8,p);c.drawLine(8,-8,-8,8,p);break;
            case 11: // plus
                c.drawLine(-9,0,9,0,p);c.drawLine(0,-9,0,9,p);break;
            case 12: // minus
                c.drawLine(-9,0,9,0,p);break;
            case 13: // target
                c.drawCircle(0,0,8,p);c.drawCircle(0,0,2,p);
                c.drawLine(-12,0,-6,0,p);c.drawLine(6,0,12,0,p);break;
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }
    private void drawToolbar(Canvas c){
        header(c,0,0,w,toolbarH,0xff20252d);
        float titleH=Math.min(23,toolbarH*.34f);
        header(c,0,0,w,titleH,0xff171b21);
        label(c,"DOOM POCKET  /  EDITOR",12,titleH*.72f,
              Math.min(12,w*.018f),0xffd7e3ed);
        label(c,"UNTITLED MAP",w*.46f,titleH*.72f,
              Math.min(11,w*.016f),0xff8d9ead);

        String[] names={"SECTORS","ENTITIES","3D MODE","PLAY"};
        int[] modes={POLY,THINGS,POLY3D,PLAY};
        int[] icons={4,2,1,3};
        float bw=(w-175)/4f;
        float rowTop=titleH+2,rowBottom=toolbarH-5;
        for(int i=0;i<4;i++){
            float x=5+i*bw;
            round(c,x,rowTop,x+bw-3,rowBottom,mode==modes[i]?0xff344d57:0xff2a333e);
            if(mode==modes[i])rect(c,x,toolbarH-3,x+bw-3,toolbarH,0xff58d2c5);
            float iconSize=Math.min(30,Math.max(20,bw*.23f));
            drawIcon(c,icons[i],x+iconSize*.68f,(rowTop+rowBottom)*.5f,iconSize,mode==modes[i]?0xff79e8dc:0xffb5c9d9);
            label(c,names[i],x+iconSize*1.36f,rowTop+(rowBottom-rowTop)*.68f,Math.max(12,Math.min(19,bw*.145f)),0xffedf3f7);
        }
        round(c,w-166,rowTop,w-87,rowBottom,0xff286d67);
        label(c,"SAVE",w-150,rowTop+(rowBottom-rowTop)*.66f,
              12,Color.WHITE);
        round(c,w-82,rowTop,w-5,rowBottom,0xff513e47);
        label(c,"RESET",w-70,rowTop+(rowBottom-rowTop)*.66f,
              12,0xffffd2d5);
    }
    private void mapBounds(){float availW=w*.77f,availH=h-toolbarH-14;
        cell=Math.min(availW/Level.W,availH/Level.H)*zoom;
        float baseLeft=(availW-Level.W*cell)*.5f+panX+5;
        float baseTop=toolbarH+(availH-Level.H*cell)*.5f+panY+7;
        if(mode==POLY||mode==THINGS){
            float targetX=availW*.50f-(baseLeft+cursorX*cell);
            float targetY=toolbarH+(h-toolbarH)*.46f-(baseTop+cursorY*cell);
            followX+=(targetX-followX)*.17f;
            followY+=(targetY-followY)*.17f;
            if(Math.abs(targetX-followX)>0.35f||Math.abs(targetY-followY)>0.35f)postInvalidateDelayed(16);
        }
        mapLeft=baseLeft+followX;
        mapTop=baseTop+followY;
    }
    private void drawMap(Canvas c){mapBounds();c.save();c.clipRect(0,toolbarH,w*.78f,h);
        panel(c,mapLeft,mapTop,mapLeft+Level.W*cell,mapTop+Level.H*cell,0xff1b2732);
        if(mode==MAP){for(int y=0;y<Level.H;y++)for(int x=0;x<Level.W;x++){
            int col=level.cells[y][x]==1?palette[Math.max(0,Math.min(5,level.wallTex[y][x]))]:palette[0];
            rect(c,mapLeft+x*cell+1,mapTop+y*cell+1,mapLeft+(x+1)*cell-1,mapTop+(y+1)*cell-1,col);
        }}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x557d98a7);
        for(int i=0;i<=Level.W;i++)c.drawLine(mapLeft+i*cell,mapTop,mapLeft+i*cell,mapTop+Level.H*cell,p);
        for(int i=0;i<=Level.H;i++)c.drawLine(mapLeft,mapTop+i*cell,mapLeft+Level.W*cell,mapTop+i*cell,p);
        p.setStyle(Paint.Style.FILL);
        if(mode==POLY||mode==THINGS){for(int k=0;k<level.sectors.size();k++){
            Level.Sector s=level.sectors.get(k);if(s.loop.size()<3)continue;Path path=new Path();
            for(int j=0;j<s.loop.size();j++){Level.Vertex v=level.vertices.get(s.loop.get(j));float x=mapLeft+v.x*cell,y=mapTop+v.y*cell;if(j==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();
            color(k==selectedSector?0x996ab9aa:mode==THINGS?0x554a7c91:0x77657b98);c.drawPath(path,p);
        }
        for(Level.LineDef line:PortalTopology.build(level)){
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
            p.setColor(line.back>=0?0xff65e5a7:0xffffbd6e);p.setStrokeWidth(3);
            c.drawLine(mapLeft+a.x*cell,mapTop+a.y*cell,mapLeft+b.x*cell,mapTop+b.y*cell,p);
        }
        for(Level.Vertex v:level.vertices){color(0xffffe4a0);c.drawCircle(mapLeft+v.x*cell,mapTop+v.y*cell,Math.max(3,cell*.13f),p);}
        if(!pending.isEmpty()){Path line=new Path();for(int i=0;i<pending.size();i++){
            float[] v=pending.get(i);float x=mapLeft+v[0]*cell,y=mapTop+v[1]*cell;
            if(i==0)line.moveTo(x,y);else line.lineTo(x,y);
            color(i==0?0xff55ffb6:0xffff8888);c.drawCircle(x,y,Math.max(5,cell*.23f),p);
        }p.setColor(0xfff3d88d);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);}
        }
        if(mode==THINGS){for(Level.Thing t:level.things){color(t.type==0?0xffff6565:0xff74e7a6);c.drawCircle(mapLeft+t.x*cell,mapTop+t.y*cell,Math.max(5,cell*.27f),p);}}
        color(0xff45e8d5);float x=mapLeft+level.playerX*cell,y=mapTop+level.playerY*cell;c.drawCircle(x,y,Math.max(4,cell*.20f),p);
        c.drawLine(x,y,x+(float)Math.cos(level.facing)*cell*.75f,y+(float)Math.sin(level.facing)*cell*.75f,p);
        if(mode==POLY||mode==THINGS){
            float px=mapLeft+cursorX*cell,py=mapTop+cursorY*cell;
            // High-contrast halo and precise crosshair, at a whole-grid intersection.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5f);p.setColor(0xff16232b);
            c.drawCircle(px,py,14f,p);
            p.setStrokeWidth(2.8f);p.setColor(0xffffe36a);
            c.drawCircle(px,py,14f,p);
            c.drawLine(px-23,py,px-6,py,p);c.drawLine(px+6,py,px+23,py,p);
            c.drawLine(px,py-23,px,py-6,p);c.drawLine(px,py+6,px,py+23,p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xffffffff);
            c.drawCircle(px,py,3.5f,p);
            if(rectStartX>=0&&mode==POLY){
                p.setStyle(Paint.Style.STROKE);p.setColor(0xfff6d17b);p.setStrokeWidth(2.5f);
                c.drawRect(mapLeft+Math.min(rectStartX,cursorX)*cell,mapTop+Math.min(rectStartY,cursorY)*cell,
                    mapLeft+Math.max(rectStartX,cursorX)*cell,mapTop+Math.max(rectStartY,cursorY)*cell,p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        c.restore();
        rect(c,0,h-24,w*.78f,h,0xff202933);label(c,"CURSOR "+cursorX+", "+cursorY+"   |   SNAP 1.0   |   SECTORS: "+level.sectors.size(),10,h-8,Math.max(11,Math.min(15,w*.019f)),0xffe1e9ee);
    }
    private static final String[][] SECTOR_TOOLS={
        {"DRAW","SELECT","RECT","CLOSE","UNDO PT","CANCEL","FLOOR +","FLOOR -","CEIL +","CEIL -","UNDO","NEXT >"},
        {"REDO","VALIDATE","CLEAN","DUPLICATE","DELETE","SPLIT EDGE","EXTRUDE","MOVE WALL","STAIRS","DOOR","PREFAB","NEXT >"},
        {"WALL MAT","FLOOR MAT","CEIL MAT","MAT +","MAT -","PLATFORM","LIGHT +","LIGHT -","SNAP INFO","3D PICK","HELP","NEXT >"}
    };
    private float toolTop(){return toolbarH+43;}
    private float toolRowHeight(){return Math.max(27f,Math.min(44f,(h-toolTop()-28f)/6f));}
    private void button(Canvas c,int row,String name,boolean active){
        float left=w*.79f,usable=w-left-9,half=usable*.5f;
        int col=row%2,r=row/2;float x=left+col*half,y=toolTop()+r*toolRowHeight();
        float right=x+half-3,bottom=y+toolRowHeight()-3;
        round(c,x+2,y,right,bottom,active?0xff286d69:0xff35404d);
        if(active)rect(c,x+2,y+3,x+5,bottom-3,0xff6ce8d7);
        int icon=6;
        if(name.equals("DRAW"))icon=7;else if(name.equals("SELECT"))icon=8;
        else if(name.contains("UNDO"))icon=9;else if(name.equals("CANCEL")||name.equals("DELETE"))icon=10;
        else if(name.contains("+"))icon=11;else if(name.contains("-"))icon=12;
        else if(name.equals("WALL")||name.equals("RECT"))icon=0;else if(name.equals("SPAWN"))icon=13;
        else if(name.equals("ENEMY"))icon=2;else if(name.equals("CLOSE"))icon=4;
        float cy=(y+bottom)*.5f;
        if(half>66)drawIcon(c,icon,x+14,cy,Math.min(25,toolRowHeight()*.70f),0xffb8e4df);
        label(c,name,x+(half>66?29:5),cy+5,Math.max(11,Math.min(16,half*.14f)),0xfff0f4f7);
    }

    private void drawTools(Canvas c){
        float x=w*.785f;
        panel(c,x,toolbarH,w,h,0xff222a34);
        rect(c,x,toolbarH,x+2,h,0xff40505e);
        header(c,x+2,toolbarH,w,toolbarH+42,0xff2e3945);
        label(c,mode==POLY?"SECTOR INSPECTOR":
              mode==MAP?"GRID TOOLS":
              mode==THINGS?"ENTITY TOOLS":"3D VIEWPORT",
              x+9,toolbarH+24,Math.max(14,Math.min(20,w*.025f)),
              0xffe6eef4);
        if(mode==POLY){
            String[] names=SECTOR_TOOLS[toolPage];
            for(int i=0;i<names.length;i++)button(c,i,names[i],toolPage==0&&((i==0&&polygonTool==0)||(i==1&&polygonTool==1)||(i==2&&polygonTool==2)));
            label(c,"PAGE "+(toolPage+1)+"/3  |  SECTORS "+level.sectors.size(),x+7,h-10,Math.min(10,w*.014f),0xffa9bfcd);
        }else if(mode==MAP){
            button(c,0,"WALL",brush==1);
            button(c,1,"ERASE",brush==0);
            button(c,2,"SPAWN",brush==2);
        }else if(mode==THINGS){
            button(c,0,"ENEMY",thingType==0);
            button(c,1,"MEDKIT",thingType==1);
            button(c,2,"REMOVE",thingType==2);
        }else{
            label(c,"RENDERER",x+10,toolbarH+76,12,0xff7bd9ca);
            label(c,"Software / CPU",x+10,toolbarH+102,12,0xffd1dce6);
            label(c,"320 x 180",x+10,toolbarH+128,12,0xff9caebc);
            label(c,"Swipe to look",x+10,toolbarH+158,12,0xff9caebc);
        }
    }
    private void drawPlayControls(Canvas c){
        drawFloatingJoystick(c);
        round(c,w-140,h-150,w-12,h-16,0xffa26335);
        label(c,"FIRE",w-116,h-73,26,Color.WHITE);
        label(c,"HP "+health+"   AMMO "+ammo+"   KILLS "+kills,w*.36f,toolbarH+28,19,Color.WHITE);
        p.setColor(Color.WHITE);p.setStrokeWidth(2);
        c.drawLine(w*.5f-8,h*.5f,w*.5f+8,h*.5f,p);
        c.drawLine(w*.5f,h*.5f-8,w*.5f,h*.5f+8,p);
    }
    private void save(){try{SharedPreferences pref=getContext().getSharedPreferences("project",0);pref.edit().putString("level",level.toJson().toString()).apply();hint("Saved on phone");}catch(Exception e){hint("Save failed");}}
    private void changeMode(int next){mode=next;pending.clear();moving=looking=false;moveX=moveY=0;activePointer=lookPointer=-1;joyVisible=false;last3DFrame=System.nanoTime();
        if(next==PLAY){camX=level.playerX;camY=level.playerY;camAngle=level.facing;health=100;ammo=30;kills=0;lastTime=System.nanoTime();}
        hint(next==PLAY?"Left stick move; right swipe look":next==POLY?"Tap points to draw a sector":"Polygon renderer active");}
    private boolean inMap(float x,float y){return x>=mapLeft&&y>=mapTop&&x<mapLeft+Level.W*cell&&y<mapTop+Level.H*cell&&x<w*.78f;}
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
        if(row==11){toolPage=(toolPage+1)%3;hint("Tools page "+(toolPage+1));return;}
        if(id==0||id==1||id==2){polygonTool=id;pending.clear();hint(id==2?"Drag to draw rectangle":id==1?"Select vertex or sector":"Tap corners to draw");return;}
        if(id==3){closeSector();return;}
        if(id==4){if(!pending.isEmpty()){pending.remove(pending.size()-1);hint("Point removed");}else if(sectorEditor.undo()){restoreEditor();hint("Undo");}return;}
        if(id==5){pending.clear();hint("Cancelled");return;}
        if(id==10){if(sectorEditor.undo()){restoreEditor();hint("Undo");}return;}
        if(id==12){if(sectorEditor.redo()){restoreEditor();hint("Redo");}return;}
        if(id==13){hint(sectorEditor.validateMap());return;}
        if(id==14){int n=sectorEditor.cleanOrphans();if(n>0)autoSave();hint("Cleaned "+n+" unused vertices");return;}
        if(id==32){hint("Snap: whole grid corners only");return;}
        if(id==33){changeMode(POLY3D);hint("Tap 3D view to select visible sector");return;}
        if(id==34){hint("Select a sector, then choose an editing action");return;}
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
        if(id==19){polygonTool=3;hint("Drag a wall to move it on grid corners");return;}
        if(id==20){changed=sectorEditor.setHeights(selectedSector,sec.floor+.5f,sec.ceiling);}
        if(id==21){changed=sectorEditor.setHeights(selectedSector,sec.floor,sec.floor+1.5f);}
        if(id==22){changed=sectorEditor.rectangle(2,13,6,17);if(!changed)hint("Prefab location occupied; use rectangle tool");}
        if(id==24){materialSurface=0;hint("Wall material selected");return;}
        if(id==25){materialSurface=1;hint("Floor material selected");return;}
        if(id==26){materialSurface=2;hint("Ceiling material selected");return;}
        if(id==27||id==28){int current=materialSurface==0?sec.wallMaterial:materialSurface==1?sec.floorMaterial:sec.ceilingMaterial;
            changed=sectorEditor.setMaterial(selectedSector,materialSurface,(current+(id==27?1:5))%6);}
        if(id==29)changed=sectorEditor.setHeights(selectedSector,sec.floor+.25f,sec.ceiling);
        if(id==30||id==31){hint("Per-sector lighting needs a Level format upgrade");return;}
        if(changed){autoSave();hint("Map updated");}else if(!sectorEditor.error.isEmpty())hint(sectorEditor.error);
    }
    private void applyMap(float x,float y){
        if(!inMap(x,y)||mode!=THINGS)return;
        float px=(x-mapLeft)/cell,py=(y-mapTop)/cell;
        int sector=level.sectorAt(px,py);
        if(sector<0){hint("Place entities inside a sector");return;}
        if(thingType==2){Level.Thing nearest=null;float best=.85f;
            for(Level.Thing t:level.things){float d=(float)Math.hypot(t.x-px,t.y-py);if(d<best){best=d;nearest=t;}}
            if(nearest!=null){level.things.remove(nearest);autoSave();hint("Entity removed");}
        }else{
            for(Level.Thing t:level.things)if(Math.hypot(t.x-px,t.y-py)<.35f){hint("Entity too close");return;}
            level.things.add(new Level.Thing(px,py,thingType));autoSave();hint("Entity placed in sector "+(sector+1));
        }
    }
    private void stick(float x,float y){float dx=x-80,dy=y-(h-80),r=Math.min(58,h*.12f),d=(float)Math.hypot(dx,dy);
        moveX=d>r?dx/d:dx/r;moveY=d>r?dy/d:dy/r;}
    private boolean walkable(float x,float y,float nx,float ny){return PortalTopology.canCross(level,x,y,nx,ny,.6f,1.5f);}
    private void updateGame(){long now=System.nanoTime();float dt=Math.min(.05f,(now-lastTime)*1e-9f);lastTime=now;
        if(moving){float f=-moveY*3f*dt,st=moveX*3f*dt;
            float nx=camX+(float)Math.cos(camAngle)*f-(float)Math.sin(camAngle)*st;
            float ny=camY+(float)Math.sin(camAngle)*f+(float)Math.cos(camAngle)*st;
            if(walkable(camX,camY,nx,camY))camX=nx;if(walkable(camX,camY,camX,ny))camY=ny;}
        if(fireDown){fireDown=false;fire();}}
    private void fire(){if(ammo<=0){hint("No ammo");return;}ammo--;
        Level.Thing target=null;float best=15;
        for(Level.Thing t:level.things){if(t.type!=0)continue;float dx=t.x-camX,dy=t.y-camY,dist=(float)Math.hypot(dx,dy);
            float delta=(float)Math.atan2(dy,dx)-camAngle;while(delta>Math.PI)delta-=2*Math.PI;while(delta<-Math.PI)delta+=2*Math.PI;
            if(Math.abs(delta)<.15f&&dist<best){best=dist;target=t;}}
        if(target!=null){level.things.remove(target);kills++;hint("Enemy eliminated");}}
    @Override public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked(),idx=e.getActionIndex();float x=e.getX(idx),y=e.getY(idx);
        if((mode==POLY||mode==THINGS)&&action==MotionEvent.ACTION_DOWN&&y>=toolbarH){
            int direction=dpadHit(x,y);
            if(direction>=0){dpadPointer=e.getPointerId(idx);dpadDirection=direction;cursorStep(direction);nextDpadRepeat=System.currentTimeMillis()+270;return true;}
            if(Math.abs(x-applyCX())<65&&Math.abs(y-applyCY())<55){cursorApply();invalidate();return true;}
        }
        if((mode==POLY||mode==THINGS)&&action==MotionEvent.ACTION_MOVE&&dpadPointer>=0){
            int direction=dpadHit(e.getX(0),e.getY(0));
            if(direction>=0&&System.currentTimeMillis()>=nextDpadRepeat){cursorStep(direction);nextDpadRepeat=System.currentTimeMillis()+95;}
            return true;
        }
        if((mode==POLY||mode==THINGS)&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&dpadPointer>=0){
            dpadPointer=-1;dpadDirection=-1;return true;
        }
        if((in3DEdit()||mode==PLAY)&&(y>=toolbarH||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL))return handle3DTouch(e);
        if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2&&(mode==POLY||mode==THINGS)){
            pinchDistance=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;dragging=true;return true;}
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=x;downY=lastY=y;dragging=false;touchStartX=x;touchStartY=y;
            if(y<toolbarH){float bw=(w-175)/4f;int[] modes={POLY,THINGS,POLY3D,PLAY};for(int i=0;i<4;i++)if(x>=5+i*bw&&x<5+(i+1)*bw){changeMode(modes[i]);return true;}
                if(x>=w-166&&x<w-86){save();return true;}
                if(x>=w-82){level=new Level();sectorEditor=new SectorEditor(level);pending.clear();selectedSector=-1;camX=level.playerX;camY=level.playerY;camAngle=level.facing;hint("Level reset");return true;}}
            if(mode==PLAY){if(x<155&&y>h-175){moving=true;activePointer=e.getPointerId(idx);stick(x,y);}else if(x>w-145&&y>h-160){fireDown=true;}else{looking=true;lookPointer=e.getPointerId(idx);}invalidate();return true;}
            if(x>w*.78f){if(mode==POLY){int row=(int)((y-toolTop())/toolRowHeight());int col=x>=w*.79f+(w-w*.79f-9)*.5f?1:0;if(y>=toolTop()&&row>=0&&row<6)sectorAction(row*2+col);}
                else if(mode==THINGS){int row=(int)((y-toolTop())/toolRowHeight())*2+(x>=w*.79f+(w-w*.79f-9)*.5f?1:0);if(y>=toolTop()&&row>=0&&row<=2){thingType=row;}}
                invalidate();return true;}
            if(mode==POLY&&polygonTool==3){
                if(selectedSector>=0&&selectedSector<level.sectors.size()){
                    Level.Sector sec=level.sectors.get(selectedSector);float best=30f;
                    for(int j=0;j<sec.loop.size();j++){
                        Level.Vertex a=level.vertices.get(sec.loop.get(j)),b=level.vertices.get(sec.loop.get((j+1)%sec.loop.size()));
                        float ax=mapLeft+a.x*cell,ay=mapTop+a.y*cell,bx=mapLeft+b.x*cell,by=mapTop+b.y*cell;
                        float dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
                        float t=den<.001?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/den));
                        float d=(float)Math.hypot(x-ax-t*dx,y-ay-t*dy);
                        if(d<best){best=d;selectedEdge=j;}
                    }
                    hint("Drag selected wall on whole grid");
                }
            }else if(mode==POLY&&polygonTool==1){
                dragVertex=-1;float best=22f;
                for(int i=0;i<level.vertices.size();i++){
                    Level.Vertex v=level.vertices.get(i);float d=(float)Math.hypot(mapLeft+v.x*cell-x,mapTop+v.y*cell-y);
                    if(d<best){best=d;dragVertex=i;}
                }
                if(dragVertex<0)polygonTap(x,y);
            }else if(mode==POLY&&polygonTool==2){hint("Drag rectangle to opposite corner");}else if(mode==POLY||mode==THINGS){hint("Use D-pad and APPLY to edit");}else{looking=true;lookPointer=e.getPointerId(idx);}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if((mode==POLY||mode==THINGS)&&e.getPointerCount()>=2){float ax=e.getX(0),ay=e.getY(0),bx=e.getX(1),by=e.getY(1);
                float dist=(float)Math.hypot(ax-bx,ay-by);if(pinchDistance>1){float factor=dist/pinchDistance;zoom=Math.max(.5f,Math.min(4f,zoom*factor));}pinchDistance=dist;
                float mx=(ax+bx)*.5f,my=(ay+by)*.5f;panX+=(mx-lastX);panY+=(my-lastY);lastX=mx;lastY=my;dragging=true;
            }else if(mode==PLAY){for(int i=0;i<e.getPointerCount();i++){int id=e.getPointerId(i);if(id==activePointer)stick(e.getX(i),e.getY(i));if(id==lookPointer){camAngle+=(e.getX(i)-lastX)*.008f;lastX=e.getX(i);}}}
            else if(mode==VIEW3D||mode==POLY3D){level.facing+=(e.getX(0)-lastX)*.008f;lastX=e.getX(0);}
            
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP){int id=e.getPointerId(idx);if(id==activePointer){moving=false;activePointer=-1;moveX=moveY=0;}if(id==lookPointer){looking=false;lookPointer=-1;}pinchDistance=0;dragVertex=-1;invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(action==MotionEvent.ACTION_UP&&mode==POLY&&polygonTool==3&&downX<w*.78f&&selectedSector>=0){
                float dx=Math.round((e.getX()-downX)/cell),dy=Math.round((e.getY()-downY)/cell);
                if((dx!=0||dy!=0)&&sectorEditor.moveEdge(selectedSector,selectedEdge,dx,dy)){autoSave();hint("Wall moved");}
                else if(dx!=0||dy!=0)hint(sectorEditor.error);
            }
            if(action==MotionEvent.ACTION_UP&&mode==POLY&&polygonTool==2&&!dragging&&downX<w*.78f){
                int ax=Math.round(wx(downX)),ay=Math.round(wy(downY)),bx=Math.round(wx(e.getX())),by=Math.round(wy(e.getY()));
                if(sectorEditor.rectangle(ax,ay,bx,by)){selectedSector=level.sectors.size()-1;autoSave();hint("Rectangle created");}
                else hint(sectorEditor.error);
            }
            if(action==MotionEvent.ACTION_UP&&mode==POLY&&dragVertex>=0&&e.getPointerCount()==1){
                float ex=e.getX(),ey=e.getY();
                if(Math.hypot(ex-touchStartX,ey-touchStartY)>12){
                    float[] pt=sectorEditor.snapPoint(wx(ex),wy(ey),1f);
                    if(sectorEditor.moveVertex(dragVertex,pt[0],pt[1])){autoSave();hint("Vertex moved");}
                    else hint(sectorEditor.error);
                }else{for(int k=0;k<level.sectors.size();k++)if(level.sectors.get(k).loop.contains(dragVertex)){selectedSector=k;break;}}
                dragVertex=-1;
            }
            if(action==MotionEvent.ACTION_UP&&(mode==POLY3D||mode==VIEW3D)&&Math.hypot(e.getX()-downX,e.getY()-downY)<12){
                int hit=-1;for(float dist=.2f;dist<18;dist+=.2f){float px=level.playerX+(float)Math.cos(level.facing)*dist,py=level.playerY+(float)Math.sin(level.facing)*dist;
                    int si=level.sectorAt(px,py);if(si>=0){hit=si;break;}}
                if(hit>=0){selectedSector=hit;hint("Sector "+(hit+1)+" selected; edit in SECTORS");}
            }
            moving=looking=false;activePointer=lookPointer=-1;moveX=moveY=0;pinchDistance=0;invalidate();return true;}
        return true;
    }
}
