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
    private int mode=POLY, brush=1, thingType=0, polygonTool=0, selectedSector=-1,toolPage=0,materialSurface=0,selectedEdge=0;
    private final ArrayList<float[]> pending=new ArrayList<>();
    private SectorEditor sectorEditor;
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
        sectorEditor=new SectorEditor(level);camX=level.playerX;camY=level.playerY;camAngle=level.facing;setFocusable(true);
    }
    boolean exitPlay(){if(mode!=PLAY)return false;mode=POLY3D;moving=looking=false;invalidate();return true;}
    private void color(int col){p.setColor(col);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    private void rect(Canvas c,float l,float t,float r,float b,int col){color(col);c.drawRect(l,t,r,b,p);}
    private void round(Canvas c,float l,float t,float r,float b,int col){color(col);c.drawRoundRect(new RectF(l,t,r,b),8,8,p);}
    private void label(Canvas c,String s,float x,float y,float size,int col){color(col);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));p.setTextSize(size);c.drawText(s,x,y,p);}
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2500;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);w=getWidth();h=getHeight();toolbarH=Math.max(52,h*.12f);
        rect(c,0,0,w,h,0xff151b23);
        if(mode==MAP||mode==THINGS||mode==POLY)drawMap(c);
        else PolygonRenderer.render(c,level,0,toolbarH,mode==PLAY?w:w*.78f,h,mode==PLAY?camX:level.playerX,mode==PLAY?camY:level.playerY,mode==PLAY?camAngle:level.facing);
        drawToolbar(c);
        if(mode==PLAY){drawPlayControls(c);updateGame();postInvalidateDelayed(16);}else drawTools(c);
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
        rect(c,0,0,w,toolbarH,0xff20252d);
        float titleH=Math.min(23,toolbarH*.34f);
        rect(c,0,0,w,titleH,0xff171b21);
        label(c,"DOOM POCKET  /  EDITOR",12,titleH*.72f,
              Math.min(12,w*.018f),0xffd7e3ed);
        label(c,"UNTITLED MAP",w*.46f,titleH*.72f,
              Math.min(11,w*.016f),0xff8d9ead);

        String[] names={"GRID","3D VIEW","ENTITIES","PLAY","SECTORS","PS1 VIEW"};
        float bw=(w-175)/6f;
        float rowTop=titleH+2, rowBottom=toolbarH-5;
        for(int i=0;i<6;i++){
            float x=5+i*bw;
            round(c,x,rowTop,x+bw-3,rowBottom,
                  mode==i?0xff344d57:0xff2a333e);
            if(mode==i)rect(c,x,toolbarH-3,x+bw-3,toolbarH,0xff58d2c5);
            drawIcon(c,i,x+14,(rowTop+rowBottom)*.5f,
                     Math.min(18,Math.max(11,bw*.19f)),
                     mode==i?0xff79e8dc:0xffa3b5c5);
            label(c,names[i],x+Math.min(28,bw*.34f),
                  rowTop+(rowBottom-rowTop)*.66f,
                  Math.max(8,Math.min(12,bw*.12f)),0xffedf3f7);
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
        }else{for(Level.Thing t:level.things){color(t.type==0?0xffff6565:0xff74e7a6);c.drawCircle(mapLeft+t.x*cell,mapTop+t.y*cell,Math.max(4,cell*.25f),p);}}
        color(0xff45e8d5);float x=mapLeft+level.playerX*cell,y=mapTop+level.playerY*cell;c.drawCircle(x,y,Math.max(4,cell*.20f),p);
        c.drawLine(x,y,x+(float)Math.cos(level.facing)*cell*.75f,y+(float)Math.sin(level.facing)*cell*.75f,p);
        c.restore();
        rect(c,0,h-24,w*.78f,h,0xff202933);label(c,"GRID 1.0   |   PINCH ZOOM   |   TWO-FINGER PAN   |   SECTORS: "+level.sectors.size(),10,h-8,Math.min(11,w*.015f),0xffa7bbc9);
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
        if(half>75)drawIcon(c,icon,x+11,cy,Math.min(15,toolRowHeight()*.45f),0xffb8e4df);
        label(c,name,x+(half>75?22:5),cy+4,Math.max(8,Math.min(11,half*.105f)),0xfff0f4f7);
    }

    private void drawTools(Canvas c){
        float x=w*.785f;
        rect(c,x,toolbarH,w,h,0xff222a34);
        rect(c,x,toolbarH,x+2,h,0xff40505e);
        rect(c,x+2,toolbarH,w,toolbarH+37,0xff2e3945);
        label(c,mode==POLY?"SECTOR INSPECTOR":
              mode==MAP?"GRID TOOLS":
              mode==THINGS?"ENTITY TOOLS":"3D VIEWPORT",
              x+9,toolbarH+24,Math.max(10,Math.min(15,w*.020f)),
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
        if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2&&(mode==POLY||mode==MAP||mode==THINGS)){
            pinchDistance=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;dragging=true;return true;}
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=x;downY=lastY=y;dragging=false;touchStartX=x;touchStartY=y;
            if(y<toolbarH){float bw=(w-175)/6f;for(int i=0;i<6;i++)if(x>=5+i*bw&&x<5+(i+1)*bw){changeMode(i);return true;}
                if(x>=w-166&&x<w-86){save();return true;}
                if(x>=w-82){level=new Level();sectorEditor=new SectorEditor(level);pending.clear();selectedSector=-1;camX=level.playerX;camY=level.playerY;camAngle=level.facing;hint("Level reset");return true;}}
            if(mode==PLAY){if(x<155&&y>h-175){moving=true;activePointer=e.getPointerId(idx);stick(x,y);}else if(x>w-145&&y>h-160){fireDown=true;}else{looking=true;lookPointer=e.getPointerId(idx);}invalidate();return true;}
            if(x>w*.78f){if(mode==POLY){int row=(int)((y-toolTop())/toolRowHeight());int col=x>=w*.79f+(w-w*.79f-9)*.5f?1:0;if(y>=toolTop()&&row>=0&&row<6)sectorAction(row*2+col);}
                else if(mode==MAP||mode==THINGS){int row=(int)((y-toolTop())/toolRowHeight())*2+(x>=w*.79f+(w-w*.79f-9)*.5f?1:0);if(y>=toolTop()&&row>=0&&row<=2){if(mode==MAP)brush=row==0?1:row==1?0:2;else thingType=row;}}
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
            }else if(mode==POLY&&polygonTool==2){hint("Drag rectangle to opposite corner");}else if(mode==POLY)polygonTap(x,y);else if(mode==MAP||mode==THINGS)applyMap(x,y);else{looking=true;lookPointer=e.getPointerId(idx);}
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
