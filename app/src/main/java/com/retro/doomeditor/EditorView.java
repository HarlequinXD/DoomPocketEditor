package com.retro.doomeditor;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
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
    private final Paint p=new Paint(3);
    private final Level levelDefault=new Level();
    private Level level=levelDefault;
    private int mode=POLY, brush=1, thingType=0, polygonTool=0, selectedSector=-1;
    private final ArrayList<Integer> pending=new ArrayList<>();
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
        camX=level.playerX;camY=level.playerY;camAngle=level.facing;setFocusable(true);
    }
    boolean exitPlay(){if(mode!=PLAY)return false;mode=POLY3D;moving=looking=false;invalidate();return true;}
    private void color(int col){p.setColor(col);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    private void rect(Canvas c,float l,float t,float r,float b,int col){color(col);c.drawRect(l,t,r,b,p);}
    private void round(Canvas c,float l,float t,float r,float b,int col){color(col);c.drawRoundRect(new RectF(l,t,r,b),8,8,p);}
    private void label(Canvas c,String s,float x,float y,float size,int col){color(col);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));p.setTextSize(size);c.drawText(s,x,y,p);}
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2500;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);w=getWidth();h=getHeight();toolbarH=Math.max(52,h*.12f);
        rect(c,0,0,w,h,0xff141b23);
        if(mode==MAP||mode==THINGS||mode==POLY)drawMap(c);
        else PolygonRenderer.render(c,level,0,toolbarH,mode==PLAY?w:w*.78f,h,mode==PLAY?camX:level.playerX,mode==PLAY?camY:level.playerY,mode==PLAY?camAngle:level.facing);
        drawToolbar(c);
        if(mode==PLAY){drawPlayControls(c);updateGame();postInvalidateDelayed(16);}else drawTools(c);
        if(System.currentTimeMillis()<messageUntil){round(c,w*.20f,h-36,w*.78f,h-6,0xcc121820);label(c,message,w*.22f,h-17,Math.min(15,w*.024f),Color.WHITE);}
    }
    private void drawToolbar(Canvas c){rect(c,0,0,w,toolbarH,0xff26303a);
        String[] names={"GRID","3D","THINGS","PLAY","SECTORS","PS1 3D"};
        float bw=(w-175)/6f;
        for(int i=0;i<6;i++){float x=5+i*bw;round(c,x,6,x+bw-3,toolbarH-6,mode==i?0xff188f9a:0xff3d4c5b);label(c,names[i],x+4,toolbarH*.62f,Math.max(9,Math.min(14,bw*.17f)),Color.WHITE);}
        round(c,w-166,6,w-86,toolbarH-6,0xff476072);label(c,"SAVE",w-153,toolbarH*.62f,13,Color.WHITE);
        round(c,w-82,6,w-5,toolbarH-6,0xff754e50);label(c,"RESET",w-75,toolbarH*.62f,12,Color.WHITE);
    }
    private void mapBounds(){float availW=w*.77f,availH=h-toolbarH-14;
        cell=Math.min(availW/Level.W,availH/Level.H)*zoom;
        mapLeft=(availW-Level.W*cell)*.5f+panX+5;
        mapTop=toolbarH+(availH-Level.H*cell)*.5f+panY+7;
    }
    private void drawMap(Canvas c){mapBounds();c.save();c.clipRect(0,toolbarH,w*.78f,h);
        rect(c,mapLeft,mapTop,mapLeft+Level.W*cell,mapTop+Level.H*cell,0xff1b2732);
        if(mode!=POLY){for(int y=0;y<Level.H;y++)for(int x=0;x<Level.W;x++){
            int col=level.cells[y][x]==1?palette[Math.max(0,Math.min(5,level.wallTex[y][x]))]:palette[0];
            rect(c,mapLeft+x*cell+1,mapTop+y*cell+1,mapLeft+(x+1)*cell-1,mapTop+(y+1)*cell-1,col);
        }}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x557d98a7);
        for(int i=0;i<=Level.W;i++)c.drawLine(mapLeft+i*cell,mapTop,mapLeft+i*cell,mapTop+Level.H*cell,p);
        for(int i=0;i<=Level.H;i++)c.drawLine(mapLeft,mapTop+i*cell,mapLeft+Level.W*cell,mapTop+i*cell,p);
        p.setStyle(Paint.Style.FILL);
        if(mode==POLY){for(int k=0;k<level.sectors.size();k++){
            Level.Sector s=level.sectors.get(k);if(s.loop.size()<3)continue;Path path=new Path();
            for(int j=0;j<s.loop.size();j++){Level.Vertex v=level.vertices.get(s.loop.get(j));float x=mapLeft+v.x*cell,y=mapTop+v.y*cell;if(j==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();
            color(k==selectedSector?0x996ab9aa:0x77657b98);c.drawPath(path,p);
        }
        for(Level.LineDef line:level.linedefs()){
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
            p.setColor(line.back>=0?0xff65e5a7:0xffffbd6e);p.setStrokeWidth(3);
            c.drawLine(mapLeft+a.x*cell,mapTop+a.y*cell,mapLeft+b.x*cell,mapTop+b.y*cell,p);
        }
        for(Level.Vertex v:level.vertices){color(0xffffe4a0);c.drawCircle(mapLeft+v.x*cell,mapTop+v.y*cell,Math.max(3,cell*.13f),p);}
        if(!pending.isEmpty()){Path line=new Path();for(int i=0;i<pending.size();i++){
            Level.Vertex v=level.vertices.get(pending.get(i));float x=mapLeft+v.x*cell,y=mapTop+v.y*cell;
            if(i==0)line.moveTo(x,y);else line.lineTo(x,y);
            color(i==0?0xff55ffb6:0xffff8888);c.drawCircle(x,y,Math.max(5,cell*.23f),p);
        }p.setColor(0xfff3d88d);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);}
        }else{for(Level.Thing t:level.things){color(t.type==0?0xffff6565:0xff74e7a6);c.drawCircle(mapLeft+t.x*cell,mapTop+t.y*cell,Math.max(4,cell*.25f),p);}}
        color(0xff45e8d5);float x=mapLeft+level.playerX*cell,y=mapTop+level.playerY*cell;c.drawCircle(x,y,Math.max(4,cell*.20f),p);
        c.drawLine(x,y,x+(float)Math.cos(level.facing)*cell*.75f,y+(float)Math.sin(level.facing)*cell*.75f,p);
        c.restore();
        label(c,"Pinch zoom  |  Two-finger pan",10,h-14,12,0xffb6c6d4);
    }
    private void button(Canvas c,int row,String s,boolean active){float x=w*.79f,y=toolbarH+44+row*43;
        round(c,x+4,y,w-9,y+38,active?0xff168f9e:0xff3b4958);
        label(c,s,x+11,y+25,Math.max(10,Math.min(14,w*.022f)),Color.WHITE);
    }
    private void drawTools(Canvas c){round(c,w*.785f,toolbarH+5,w-4,h-5,0xff26313b);
        label(c,mode==POLY?"SECTOR TOOLS":mode==MAP?"GRID TOOLS":mode==THINGS?"THINGS":"3D VIEW",w*.80f,toolbarH+28,Math.min(16,w*.024f),Color.WHITE);
        if(mode==POLY){button(c,0,"DRAW",polygonTool==0);button(c,1,"SELECT",polygonTool==1);
            button(c,2,"CLOSE",false);button(c,3,"UNDO PT",false);button(c,4,"CANCEL",false);
            button(c,5,"FLOOR +",false);button(c,6,"FLOOR -",false);
            button(c,7,"CEIL +",false);button(c,8,"CEIL -",false);button(c,9,"DELETE",false);
            label(c,"Vertices: "+pending.size(),w*.80f,h-16,12,Color.WHITE);
        }else if(mode==MAP){button(c,0,"WALL",brush==1);button(c,1,"ERASE",brush==0);button(c,2,"SPAWN",brush==2);
        }else if(mode==THINGS){button(c,0,"ENEMY",thingType==0);button(c,1,"MEDKIT",thingType==1);button(c,2,"REMOVE",thingType==2);
        }else{label(c,"Swipe to look",w*.80f,toolbarH+75,13,Color.WHITE);label(c,"Polygon CPU",w*.80f,toolbarH+103,13,Color.WHITE);label(c,"320 x 180",w*.80f,toolbarH+131,13,Color.WHITE);}
    }
    private void drawPlayControls(Canvas c){float cy=h-80,rad=Math.min(58,h*.12f);
        color(0x997c91a5);c.drawCircle(80,cy,rad,p);color(0xff9ec6dc);c.drawCircle(80+moveX*rad*.55f,cy+moveY*rad*.55f,rad*.32f,p);
        round(c,w-122,h-133,w-18,h-32,0xffa26335);label(c,"FIRE",w-97,h-77,19,Color.WHITE);
        label(c,"HP "+health+"   AMMO "+ammo+"   KILLS "+kills,w*.36f,toolbarH+24,15,Color.WHITE);
        p.setColor(Color.WHITE);p.setStrokeWidth(2);c.drawLine(w*.5f-8,h*.5f,w*.5f+8,h*.5f,p);c.drawLine(w*.5f,h*.5f-8,w*.5f,h*.5f+8,p);
    }
    private void save(){try{SharedPreferences pref=getContext().getSharedPreferences("project",0);pref.edit().putString("level",level.toJson().toString()).apply();hint("Saved on phone");}catch(Exception e){hint("Save failed");}}
    private void changeMode(int next){mode=next;pending.clear();moving=looking=false;moveX=moveY=0;
        if(next==PLAY){camX=level.playerX;camY=level.playerY;camAngle=level.facing;health=100;ammo=30;kills=0;lastTime=System.nanoTime();}
        hint(next==PLAY?"Left stick move; right swipe look":next==POLY?"Tap points to draw a sector":"Polygon renderer active");}
    private boolean inMap(float x,float y){return x>=mapLeft&&y>=mapTop&&x<mapLeft+Level.W*cell&&y<mapTop+Level.H*cell&&x<w*.78f;}
    private float wx(float x){return Math.round((x-mapLeft)/cell*2f)*.5f;}
    private float wy(float y){return Math.round((y-mapTop)/cell*2f)*.5f;}
    private void closeSector(){if(pending.size()<3){hint("Need at least 3 different points");return;}
        float area=0;for(int i=0;i<pending.size();i++){Level.Vertex a=level.vertices.get(pending.get(i)),b=level.vertices.get(pending.get((i+1)%pending.size()));area+=a.x*b.y-b.x*a.y;}
        if(Math.abs(area)<.05f){hint("Sector has no area");return;}
        Level.Sector s=new Level.Sector();s.loop.addAll(pending);level.sectors.add(s);selectedSector=level.sectors.size()-1;pending.clear();hint("Sector created");}
    private void polygonTap(float x,float y){if(!inMap(x,y))return;float gx=wx(x),gy=wy(y);
        if(polygonTool==1){selectedSector=level.sectorAt(gx,gy);hint(selectedSector>=0?"Sector "+(selectedSector+1)+" selected":"No sector selected");return;}
        int id=level.addVertex(gx,gy);
        if(!pending.isEmpty()&&id==pending.get(0)&&pending.size()>=3){closeSector();return;}
        if(!pending.contains(id)){pending.add(id);hint("Point "+pending.size()+" added");}else hint("Point already in sector");}
    private void sectorAction(int row){if(row==0||row==1){polygonTool=row;pending.clear();hint(row==0?"Draw sector":"Tap a sector to select");return;}
        if(row==2){closeSector();return;}if(row==3){if(!pending.isEmpty()){pending.remove(pending.size()-1);hint("Last point undone");}return;}
        if(row==4){pending.clear();hint("Drawing cancelled");return;}
        if(selectedSector<0||selectedSector>=level.sectors.size()){hint("Select a sector first");return;}
        Level.Sector s=level.sectors.get(selectedSector);
        if(row==5)s.floor=Math.min(s.ceiling-.5f,s.floor+.25f);
        if(row==6)s.floor-=.25f;
        if(row==7)s.ceiling+=.25f;
        if(row==8)s.ceiling=Math.max(s.floor+.5f,s.ceiling-.25f);
        if(row==9){level.sectors.remove(selectedSector);selectedSector=-1;hint("Sector deleted");return;}
        hint("Floor "+s.floor+" / Ceiling "+s.ceiling);
    }
    private void applyMap(float x,float y){if(!inMap(x,y))return;int cx=(int)((x-mapLeft)/cell),cy=(int)((y-mapTop)/cell);
        if(cx<0||cy<0||cx>=Level.W||cy>=Level.H)return;
        if(mode==MAP){if(brush==2){if(level.cells[cy][cx]==0){level.playerX=cx+.5f;level.playerY=cy+.5f;hint("Spawn placed");}}
            else if(cx>0&&cy>0&&cx<Level.W-1&&cy<Level.H-1)level.cells[cy][cx]=brush;
        }else if(mode==THINGS){if(level.cells[cy][cx]==1)return;
            if(thingType==2){Level.Thing nearest=null;float best=.75f;for(Level.Thing t:level.things){float d=(float)Math.hypot(t.x-cx-.5,t.y-cy-.5);if(d<best){best=d;nearest=t;}}if(nearest!=null)level.things.remove(nearest);}
            else{for(Level.Thing t:level.things)if((int)t.x==cx&&(int)t.y==cy)return;level.things.add(new Level.Thing(cx+.5f,cy+.5f,thingType));}
        }}
    private void stick(float x,float y){float dx=x-80,dy=y-(h-80),r=Math.min(58,h*.12f),d=(float)Math.hypot(dx,dy);
        moveX=d>r?dx/d:dx/r;moveY=d>r?dy/d:dy/r;}
    private boolean walkable(float x,float y){int si=level.sectorAt(x,y);if(si<0)return false;Level.Sector s=level.sectors.get(si);return s.ceiling-s.floor>=1.5f&&s.floor<=1.1f;}
    private void updateGame(){long now=System.nanoTime();float dt=Math.min(.05f,(now-lastTime)*1e-9f);lastTime=now;
        if(moving){float f=-moveY*3f*dt,st=moveX*3f*dt;
            float nx=camX+(float)Math.cos(camAngle)*f-(float)Math.sin(camAngle)*st;
            float ny=camY+(float)Math.sin(camAngle)*f+(float)Math.cos(camAngle)*st;
            if(walkable(nx,camY))camX=nx;if(walkable(camX,ny))camY=ny;}
        if(fireDown){fireDown=false;fire();}}
    private void fire(){if(ammo<=0){hint("No ammo");return;}ammo--;
        Level.Thing target=null;float best=15;
        for(Level.Thing t:level.things){if(t.type!=0)continue;float dx=t.x-camX,dy=t.y-camY,dist=(float)Math.hypot(dx,dy);
            float delta=(float)Math.atan2(dy,dx)-camAngle;while(delta>Math.PI)delta-=2*Math.PI;while(delta<-Math.PI)delta+=2*Math.PI;
            if(Math.abs(delta)<.15f&&dist<best){best=dist;target=t;}}
        if(target!=null){level.things.remove(target);kills++;hint("Enemy eliminated");}}
    @Override public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked(),idx=e.getActionIndex();float x=e.getX(idx),y=e.getY(idx);
        if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2&&(mode==POLY||mode==MAP||mode==THINGS)){
            pinchDistance=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;dragging=true;return true;}
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=x;downY=lastY=y;dragging=false;
            if(y<toolbarH){float bw=(w-175)/6f;for(int i=0;i<6;i++)if(x>=5+i*bw&&x<5+(i+1)*bw){changeMode(i);return true;}
                if(x>=w-166&&x<w-86){save();return true;}
                if(x>=w-82){level=new Level();pending.clear();selectedSector=-1;camX=level.playerX;camY=level.playerY;camAngle=level.facing;hint("Level reset");return true;}}
            if(mode==PLAY){if(x<155&&y>h-175){moving=true;activePointer=e.getPointerId(idx);stick(x,y);}else if(x>w-145&&y>h-160){fireDown=true;}else{looking=true;lookPointer=e.getPointerId(idx);}invalidate();return true;}
            if(x>w*.78f){if(mode==POLY){int row=(int)((y-toolbarH-44)/43);if(y>=toolbarH+44&&row>=0&&row<=9)sectorAction(row);}
                else if(mode==MAP||mode==THINGS){int row=(int)((y-toolbarH-44)/43);if(row>=0&&row<=2){if(mode==MAP)brush=row==0?1:row==1?0:2;else thingType=row;}}
                invalidate();return true;}
            if(mode==POLY)polygonTap(x,y);else if(mode==MAP||mode==THINGS)applyMap(x,y);else{looking=true;lookPointer=e.getPointerId(idx);}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if((mode==POLY||mode==MAP||mode==THINGS)&&e.getPointerCount()>=2){float ax=e.getX(0),ay=e.getY(0),bx=e.getX(1),by=e.getY(1);
                float dist=(float)Math.hypot(ax-bx,ay-by);if(pinchDistance>1){float factor=dist/pinchDistance;zoom=Math.max(.5f,Math.min(4f,zoom*factor));}pinchDistance=dist;
                float mx=(ax+bx)*.5f,my=(ay+by)*.5f;panX+=(mx-lastX);panY+=(my-lastY);lastX=mx;lastY=my;dragging=true;
            }else if(mode==PLAY){for(int i=0;i<e.getPointerCount();i++){int id=e.getPointerId(i);if(id==activePointer)stick(e.getX(i),e.getY(i));if(id==lookPointer){camAngle+=(e.getX(i)-lastX)*.008f;lastX=e.getX(i);}}}
            else if(mode==VIEW3D||mode==POLY3D){level.facing+=(e.getX(0)-lastX)*.008f;lastX=e.getX(0);}
            else if(mode==MAP&&!dragging){applyMap(e.getX(0),e.getY(0));}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP){int id=e.getPointerId(idx);if(id==activePointer){moving=false;activePointer=-1;moveX=moveY=0;}if(id==lookPointer){looking=false;lookPointer=-1;}pinchDistance=0;invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){moving=looking=false;activePointer=lookPointer=-1;moveX=moveY=0;pinchDistance=0;invalidate();return true;}
        return true;
    }
}
