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

public class EditorView extends View {
    private static final int MAP=0, VIEW3D=1, THINGS=2, PLAY=3, POLY=4, POLY3D=5;
    private final Paint p=new Paint(3);
    private final RectF r=new RectF();
    private Level level=new Level();
    private int mode=MAP, brush=1, texture=1, thingType=0, surface=0;
    private final ArrayList<Integer> pending=new ArrayList<>();
    private int polygonTool=0,selectedSector=0;
    private float w,h, toolbarH, cell, mapLeft,mapTop;
    private float px,py,angle, moveX,moveY,lookLastX;
    private boolean moving=false, looking=false, shooting=false;
    private long lastTime=System.nanoTime();
    private String message="Draw walls with your finger";
    private long messageUntil=0;
    private float selectionX=-1,selectionY=-1;
    private int health=100, ammo=30, kills=0;
    private final int[] palette={0xff333c4b,0xff827761,0xff7d4140,0xff56756c,0xff75639a,0xffa18743};
    private final ArrayList<Level.Thing> removed=new ArrayList<>();
    EditorView(Context ctx){super(ctx);setLayerType(View.LAYER_TYPE_SOFTWARE,null);try {String saved=ctx.getSharedPreferences("project",0).getString("level",null);if(saved!=null)level=Level.fromJson(new JSONObject(saved));}catch(Exception ignored){} px=level.playerX;py=level.playerY;angle=level.facing;setFocusable(true);}
    boolean exitPlay(){if(mode==PLAY){mode=VIEW3D;moving=false;looking=false;invalidate();return true;}return false;}
    private void paint(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    private void text(Canvas c,String s,float x,float y,float size,int color){paint(color);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));c.drawText(s,x,y,p);}
    private void rect(Canvas c,float l,float t,float rr,float b,int color){paint(color);c.drawRect(l,t,rr,b,p);}
    private void rounded(Canvas c,float l,float t,float rr,float b,int color,float rad){paint(color);r.set(l,t,rr,b);c.drawRoundRect(r,rad,rad,p);}
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2300;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);w=getWidth();h=getHeight();toolbarH=Math.max(54,h*.13f);rect(c,0,0,w,h,0xff151920);
        if(mode==POLY)drawPolygonMap(c);else if(mode==POLY3D)drawPolygonWorld(c);else if(mode==MAP||mode==THINGS)drawMap(c);else drawWorld(c);
        drawToolbar(c); if(mode==PLAY)drawControls(c);else drawTools(c);
        if(System.currentTimeMillis()<messageUntil||mode==MAP&&messageUntil==0) {rounded(c,w*.34f,h-35,w*.66f,h-7,0xbb101820,9);text(c,message,w*.35f,h-16,Math.min(15,h*.027f),Color.WHITE);}
        if(mode==PLAY){updateGame();postInvalidateDelayed(16);}
    }
    private void drawToolbar(Canvas c){rect(c,0,0,w,toolbarH,0xff242a32);String[] tabs={"2D MAP","3D TEX","THINGS","PLAY","POLYGONS","POLY 3D"};float tabW=(w-205)/6.5f;for(int i=0;i<6;i++){float x=10+i*(tabW+5);rounded(c,x,7,x+tabW,toolbarH-7,mode==i?0xff1a9b99:0xff3b4550,8);text(c,tabs[i],x+9,toolbarH*.63f,Math.min(15,tabW*.105f),Color.WHITE);}rounded(c,w-190,7,w-104,toolbarH-7,0xff415263,8);text(c,"SAVE",w-173,toolbarH*.63f,15,Color.WHITE);rounded(c,w-97,7,w-8,toolbarH-7,0xff415263,8);text(c,"RESET",w-84,toolbarH*.63f,14,Color.WHITE);}
    private void drawMap(Canvas c){float usableW=w*.77f,usableH=h-toolbarH-13;cell=Math.min(usableW/Level.W,usableH/Level.H);mapLeft=(usableW-Level.W*cell)/2+8;mapTop=toolbarH+(usableH-Level.H*cell)/2+7;
        rect(c,mapLeft,mapTop,mapLeft+Level.W*cell,mapTop+Level.H*cell,0xff212932);
        for(int y=0;y<Level.H;y++)for(int x=0;x<Level.W;x++){int color=level.cells[y][x]==1?palette[Math.min(5,level.wallTex[y][x])]:palette[0];rect(c,mapLeft+x*cell+1,mapTop+y*cell+1,mapLeft+(x+1)*cell-1,mapTop+(y+1)*cell-1,color);}
        p.setStyle(Paint.Style.STROKE);p.setColor(0x405f8395);p.setStrokeWidth(.8f);for(int i=0;i<=Level.W;i++){float x=mapLeft+i*cell;c.drawLine(x,mapTop,x,mapTop+Level.H*cell,p);}for(int i=0;i<=Level.H;i++){float y=mapTop+i*cell;c.drawLine(mapLeft,y,mapLeft+Level.W*cell,y,p);}p.setStyle(Paint.Style.FILL);
        for(Level.Thing t:level.things){float x=mapLeft+t.x*cell,y=mapTop+t.y*cell;paint(t.type==0?0xffff6961:0xffe6cf65);c.drawCircle(x,y,Math.max(4,cell*.29f),p);}
        float sx=mapLeft+level.playerX*cell,sy=mapTop+level.playerY*cell;paint(0xff4ef5db);c.drawCircle(sx,sy,Math.max(4,cell*.25f),p);p.setStrokeWidth(3);c.drawLine(sx,sy,sx+(float)Math.cos(level.facing)*cell*.7f,sy+(float)Math.sin(level.facing)*cell*.7f,p);
    }
    private void drawTools(Canvas c){float x=w*.79f;rounded(c,x,toolbarH+8,w-7,h-8,0xff242d37,10);text(c,mode==MAP?"BUILD TOOLS":mode==THINGS?"PLACE THINGS":mode==POLY?"POLYGON TOOLS":mode==POLY3D?"SECTOR VIEW":"SURFACE TOOLS",x+12,toolbarH+32,18,0xffe9f1f4);
        if(mode==POLY){toolButton(c,x,0,"DRAW SECTOR",polygonTool==0);toolButton(c,x,1,"SELECT",polygonTool==1);toolButton(c,x,2,"FLOOR +",false);toolButton(c,x,3,"FLOOR -",false);toolButton(c,x,4,"CEILING +",false);toolButton(c,x,5,"CEILING -",false);toolButton(c,x,6,"DELETE",false);text(c,"Tap first point to close",x+8,h-15,12,Color.WHITE);}
        else if(mode==POLY3D){text(c,"Swipe to rotate",x+8,toolbarH+70,14,Color.WHITE);text(c,"Edit in POLYGONS",x+8,toolbarH+98,13,Color.WHITE);}
        else if(mode==MAP){toolButton(c,x,0,"WALL",brush==1);toolButton(c,x,1,"ERASE",brush==0);toolButton(c,x,2,"SPAWN",brush==2);text(c,"Drag to paint",x+9,toolbarH+225,14,0xffb6c6d4);text(c,"Grid: 24 x 24",x+9,toolbarH+249,13,0xffb6c6d4);}
        else if(mode==THINGS){toolButton(c,x,0,"ENEMY",thingType==0);toolButton(c,x,1,"MEDKIT",thingType==1);toolButton(c,x,2,"REMOVE",thingType==2);text(c,"Tap the map",x+9,toolbarH+225,14,0xffb6c6d4);}
        else{toolButton(c,x,0,"WALL",surface==0);toolButton(c,x,1,"FLOOR",surface==1);toolButton(c,x,2,"CEILING",surface==2);text(c,"TEXTURE",x+9,toolbarH+218,14,0xffb6c6d4);for(int i=1;i<=5;i++){float bx=x+8+((i-1)%3)*((w-x-24)/3),by=toolbarH+231+((i-1)/3)*46;rounded(c,bx,by,bx+37,by+36,palette[i],5);if(texture==i){p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRect(bx-2,by-2,bx+39,by+38,p);p.setStyle(Paint.Style.FILL);}}text(c,"Tap 3D to paint",x+8,h-19,12,Color.WHITE);}
    }
    private void toolButton(Canvas c,float x,int row,String s,boolean active){float top=toolbarH+46+row*(mode==POLY?42:53);rounded(c,x+7,top,w-16,top+(mode==POLY?37:45),active?0xff188e9d:0xff3b4653,7);text(c,s,x+16,top+29,15,Color.WHITE);}
    private float cast(float ox,float oy,float ray,int[] hit){float dx=(float)Math.cos(ray),dy=(float)Math.sin(ray);int mx=(int)Math.floor(ox),my=(int)Math.floor(oy);double ddx=Math.abs(1.0/Math.max(0.0000001,Math.abs(dx))),ddy=Math.abs(1.0/Math.max(0.0000001,Math.abs(dy)));int stepX=dx<0?-1:1,stepY=dy<0?-1:1;double sideX=(dx<0?ox-mx:mx+1-ox)*ddx,sideY=(dy<0?oy-my:my+1-oy)*ddy;int side=0;for(int k=0;k<50;k++){if(sideX<sideY){sideX+=ddx;mx+=stepX;side=0;}else{sideY+=ddy;my+=stepY;side=1;}if(mx<0||my<0||mx>=Level.W||my>=Level.H||level.cells[my][mx]==1){hit[0]=Math.max(0,Math.min(Level.W-1,mx));hit[1]=Math.max(0,Math.min(Level.H-1,my));hit[2]=side;double length=side==0?sideX-ddx:sideY-ddy;return (float)Math.max(.05,length);}}hit[0]=mx;hit[1]=my;hit[2]=side;return 40;}
    private void drawWorld(Canvas c){float viewW=mode==VIEW3D?w*.78f:w;float top=toolbarH,bottom=h,mid=(top+bottom)*.5f;int baseCeil=palette[2],baseFloor=palette[1];rect(c,0,top,viewW,mid,baseCeil);rect(c,0,mid,viewW,bottom,baseFloor);float ox=mode==PLAY?px:level.playerX,oy=mode==PLAY?py:level.playerY,dir=mode==PLAY?angle:level.facing;float fov=(float)Math.toRadians(72);float step=3;int[] hit=new int[3];float[] depths=new float[(int)(viewW/step)+2];for(int i=0;i<depths.length;i++){float screenX=i*step;float a=dir+(screenX/viewW-.5f)*fov;float d=cast(ox,oy,a,hit);float correction=Math.max(.15f,(float)Math.cos(a-dir));float z=d*correction;depths[i]=z;float height=(bottom-top)*.76f/z;float y1=Math.max(top,mid-height*.5f),y2=Math.min(bottom,mid+height*.5f);int tex=level.wallTex[hit[1]][hit[0]],col=palette[Math.min(5,tex)];float shade=(hit[2]==1?.68f:1f)*Math.max(.34f,1f-z*.025f);col=shade(col,shade);rect(c,screenX,y1,Math.min(viewW,screenX+step+1),y2,col);if(i%3==0){rect(c,screenX,y1,screenX+1,y2,shade(col,.77f));}}
        // Perspective floor/ceiling scanlines sampling sector surface textures.
        for(float yy=top;yy<bottom;yy+=4){float rowDist=yy<mid?mid-yy:yy-mid;if(rowDist<2)continue;float dist=(bottom-top)*.42f/rowDist;float leftRay=dir-fov*.5f,rightRay=dir+fov*.5f;for(float xx=0;xx<viewW;xx+=7){float t=xx/viewW;float ray=leftRay+(rightRay-leftRay)*t;float fx=ox+(float)Math.cos(ray)*dist,fy=oy+(float)Math.sin(ray)*dist;int ix=(int)Math.floor(fx),iy=(int)Math.floor(fy);if(ix<0||iy<0||ix>=Level.W||iy>=Level.H)continue;int tx=yy<mid?level.ceilingTex[iy][ix]:level.floorTex[iy][ix];int col=palette[Math.max(0,Math.min(5,tx))];boolean check=(((int)(fx*4)+(int)(fy*4))&1)==0;float sh=Math.max(.45f,1-dist*.017f)*(check?1f:.81f);rect(c,xx,yy,Math.min(xx+8,viewW),Math.min(yy+4,bottom),shade(col,sh));}}
        // Repaint occluding walls after surface rendering to preserve depth order.
        for(int i=0;i<depths.length;i++){float screenX=i*step;float a=dir+(screenX/viewW-.5f)*fov;float d=cast(ox,oy,a,hit),z=d*Math.max(.15f,(float)Math.cos(a-dir));float ht=(bottom-top)*.76f/z;float y1=Math.max(top,mid-ht*.5f),y2=Math.min(bottom,mid+ht*.5f);int tex=level.wallTex[hit[1]][hit[0]];float sh=(hit[2]==1?.70f:1f)*Math.max(.35f,1f-z*.025f);int col=shade(palette[Math.min(5,tex)],sh);rect(c,screenX,y1,Math.min(viewW,screenX+step+1),y2,col);}
        for(Level.Thing thing:level.things){float dx=thing.x-ox,dy=thing.y-oy;float distance=(float)Math.hypot(dx,dy);float relative=(float)Math.atan2(dy,dx)-dir;while(relative>Math.PI)relative-=Math.PI*2;while(relative<-Math.PI)relative+=Math.PI*2;if(Math.abs(relative)>fov*.6f||distance<.1f)continue;float z=distance*(float)Math.cos(relative);float sx=(.5f+relative/fov)*viewW;int di=Math.max(0,Math.min(depths.length-1,(int)(sx/step)));if(z>depths[di]+.3f)continue;float size=Math.min(bottom-top,(bottom-top)*.52f/z);int color=thing.type==0?0xffef5051:0xff57e784;paint(color);c.drawOval(sx-size*.25f,mid-size*.25f,sx+size*.25f,mid+size*.45f,p);paint(0xff15191e);c.drawCircle(sx,mid-size*.13f,size*.12f,p);}
        if(mode==VIEW3D){text(c,"Swipe left/right to look • Tap to paint selected surface",14,toolbarH+24,14,Color.WHITE);}
    }
    private int shade(int color,float f){return Color.rgb((int)(Color.red(color)*f),(int)(Color.green(color)*f),(int)(Color.blue(color)*f));}
    private void drawControls(Canvas c){float cy=h-74,rad=Math.min(55,h*.115f);paint(0x99677e91);c.drawCircle(80,cy,rad,p);paint(0x998faec1);c.drawCircle(80+moveX*rad*.6f,cy+moveY*rad*.6f,rad*.35f,p);rounded(c,w-118,h-127,w-22,h-31,shooting?0xffba4242:0xffa36837,48);text(c,"FIRE",w-97,h-73,18,Color.WHITE);text(c,"Drag right side to LOOK",w*.51f,h-14,14,Color.WHITE);text(c,"HP "+health+"     AMMO "+ammo+"     KILLS "+kills,w*.38f,toolbarH+25,16,Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setColor(0xeef8f8f8);p.setStrokeWidth(2);c.drawLine(w*.5f-8,h*.52f,w*.5f+8,h*.52f,p);c.drawLine(w*.5f,h*.52f-8,w*.5f,h*.52f+8,p);p.setStyle(Paint.Style.FILL);}
    private void updateGame(){long now=System.nanoTime();float dt=Math.min(.05f,(now-lastTime)/1000000000f);lastTime=now;if(moving){float forward=-moveY*3.5f*dt, strafe=moveX*3.5f*dt;float nx=px+(float)(Math.cos(angle)*forward-Math.sin(angle)*strafe),ny=py+(float)(Math.sin(angle)*forward+Math.cos(angle)*strafe);if(level.canMove(nx,py))px=nx;if(level.canMove(px,ny))py=ny;}if(shooting){shooting=false;fire();}}
    private void fire(){if(ammo<=0){hint("Out of ammo");return;}ammo--;Level.Thing target=null;float best=20;for(Level.Thing t:level.things){if(t.type!=0)continue;float dx=t.x-px,dy=t.y-py;float dist=(float)Math.hypot(dx,dy);float dif=(float)Math.atan2(dy,dx)-angle;while(dif>Math.PI)dif-=2*Math.PI;while(dif<-Math.PI)dif+=2*Math.PI;if(Math.abs(dif)<.15f&&dist<best){int[] hit=new int[3];if(cast(px,py,(float)Math.atan2(dy,dx),hit)>dist-.3f){target=t;best=dist;}}}if(target!=null){level.things.remove(target);kills++;hint("Enemy eliminated!");}}
    private void save(){try{SharedPreferences pref=getContext().getSharedPreferences("project",0);pref.edit().putString("level",level.toJson().toString()).apply();hint("Level saved locally");}catch(Exception e){hint("Save failed");}}
    private void enterMode(int next){mode=next;selectionX=-1; if(next==PLAY){px=level.playerX;py=level.playerY;angle=level.facing;health=100;ammo=30;kills=0;lastTime=System.nanoTime();hint("Explore your level");}else if(next==VIEW3D){hint("Tap walls or floor/ceiling to paint");}else if(next==THINGS){hint("Tap to place enemies or pickups");}else if(next==POLY){hint("Tap vertices; tap first to close sector");}else if(next==POLY3D){hint("3D polygon sector preview");}invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();int act=e.getActionMasked();if(act==MotionEvent.ACTION_DOWN){if(y<toolbarH){float tabW=(w-205)/6.5f;for(int i=0;i<6;i++){float xx=10+i*(tabW+5);if(x>=xx&&x<=xx+tabW){enterMode(i);return true;}}if(x>w-190&&x<w-104){save();return true;}if(x>w-97){level=new Level();px=level.playerX;py=level.playerY;angle=0;hint("Map reset");invalidate();return true;}}
            if(mode==PLAY){if(x<150&&y>h-160){moving=true;moveStick(x,y);}else if(x>w-140&&y>h-160){shooting=true;}else if(x>w*.34f){looking=true;lookLastX=x;}invalidate();return true;}
            if(x>w*.78f){if(mode==POLY){int row=(int)((y-toolbarH-46)/42);if(row==0||row==1)polygonTool=row;else if(selectedSector>=0&&selectedSector<level.sectors.size()){Level.Sector sec=level.sectors.get(selectedSector);if(row==2)sec.floor=Math.min(sec.ceiling-.5f,sec.floor+.25f);if(row==3)sec.floor-=.25f;if(row==4)sec.ceiling+=.25f;if(row==5)sec.ceiling=Math.max(sec.floor+.5f,sec.ceiling-.25f);if(row==6){level.sectors.remove(selectedSector);selectedSector=Math.min(selectedSector,level.sectors.size()-1);}}invalidate();return true;}if(mode==POLY3D)return true;if(mode==VIEW3D){if(y>toolbarH+230){int col=(int)((x-(w*.79f+8))/((w*.21f-24)/3));int row=(int)((y-toolbarH-231)/46);int id=1+row*3+col;if(id>=1&&id<=5)texture=id;}else{int row=(int)((y-toolbarH-46)/53);if(row>=0&&row<=2)surface=row;}}else{int row=(int)((y-toolbarH-46)/53);if(row>=0&&row<=2){if(mode==MAP)brush=row==0?1:row==1?0:2;else thingType=row;}}invalidate();return true;}
            if(mode==POLY){polygonTap(x,y);return true;}
            if(mode==POLY3D){looking=true;lookLastX=x;return true;}
            if(mode==VIEW3D){selectionX=x;selectionY=y;lookLastX=x;looking=true;return true;}
            applyMap(x,y);return true;
        }else if(act==MotionEvent.ACTION_MOVE){if(mode==PLAY){if(moving)moveStick(x,y);else if(looking){angle+=(x-lookLastX)*.007f;lookLastX=x;}}else if(mode==POLY3D&&looking){level.facing+=(x-lookLastX)*.009f;lookLastX=x;}else if(mode==VIEW3D&&looking){float dx=x-lookLastX;if(Math.abs(dx)>5)selectionX=-999;level.facing+=dx*.009f;lookLastX=x;}else if(mode==MAP)applyMap(x,y);invalidate();return true;
        }else if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_CANCEL){if(mode==VIEW3D&&selectionX>=0&&Math.abs(x-selectionX)<15){paintSurface(x,y);}if(mode==PLAY){moving=false;looking=false;moveX=moveY=0;}else looking=false;invalidate();return true;}return true;}
    private void moveStick(float x,float y){float dx=x-80,dy=y-(h-74),rad=Math.min(55,h*.115f);float len=(float)Math.hypot(dx,dy);moveX=len>rad?dx/len:dx/rad;moveY=len>rad?dy/len:dy/rad;}
    private void applyMap(float x,float y){int cx=(int)((x-mapLeft)/cell),cy=(int)((y-mapTop)/cell);if(cx<0||cy<0||cx>=Level.W||cy>=Level.H||x<mapLeft||y<mapTop)return;if(mode==MAP){if(brush==2){if(level.cells[cy][cx]==0){level.playerX=cx+.5f;level.playerY=cy+.5f;}}else if(cx>0&&cy>0&&cx<Level.W-1&&cy<Level.H-1){level.cells[cy][cx]=brush;}}else if(mode==THINGS){if(level.cells[cy][cx]==1)return;if(thingType==2){Level.Thing nearest=null;float d=.7f;for(Level.Thing t:level.things){float dst=(float)Math.hypot(t.x-cx-.5,t.y-cy-.5);if(dst<d){d=dst;nearest=t;}}if(nearest!=null)level.things.remove(nearest);}else{for(Level.Thing t:level.things)if((int)t.x==cx&&(int)t.y==cy)return;level.things.add(new Level.Thing(cx+.5f,cy+.5f,thingType));}}invalidate();}
    private void paintSurface(float x,float y){float top=toolbarH,mid=(top+h)/2f;if(surface==0){float ray=level.facing+(x/(w*.78f)-.5f)*(float)Math.toRadians(72);int[] hit=new int[3];cast(level.playerX,level.playerY,ray,hit);level.wallTex[hit[1]][hit[0]]=texture;hint("Wall texture applied");}else{float rowDist=Math.abs(y-mid);if(rowDist<3){hint("Tap away from horizon");return;}float distance=(h-top)*.42f/rowDist;float ray=level.facing+(x/(w*.78f)-.5f)*(float)Math.toRadians(72);int ix=(int)(level.playerX+Math.cos(ray)*distance),iy=(int)(level.playerY+Math.sin(ray)*distance);if(ix>=0&&iy>=0&&ix<Level.W&&iy<Level.H){if(surface==1)level.floorTex[iy][ix]=texture;else level.ceilingTex[iy][ix]=texture;hint(surface==1?"Floor texture applied":"Ceiling texture applied");}}}

    private void drawPolygonMap(Canvas c){
        float usableW=w*.77f,usableH=h-toolbarH-13;cell=Math.min(usableW/Level.W,usableH/Level.H);
        mapLeft=(usableW-Level.W*cell)/2+8;mapTop=toolbarH+(usableH-Level.H*cell)/2+7;
        rect(c,mapLeft,mapTop,mapLeft+Level.W*cell,mapTop+Level.H*cell,0xff1c252e);
        p.setStyle(Paint.Style.STROKE);p.setColor(0x405f8395);p.setStrokeWidth(1);
        for(int i=0;i<=Level.W;i++)c.drawLine(mapLeft+i*cell,mapTop,mapLeft+i*cell,mapTop+Level.H*cell,p);
        for(int i=0;i<=Level.H;i++)c.drawLine(mapLeft,mapTop+i*cell,mapLeft+Level.W*cell,mapTop+i*cell,p);
        p.setStyle(Paint.Style.FILL);
        for(int k=0;k<level.sectors.size();k++){Level.Sector sec=level.sectors.get(k);Path path=new Path();for(int i=0;i<sec.loop.size();i++){Level.Vertex v=level.vertices.get(sec.loop.get(i));float x=mapLeft+v.x*cell,y=mapTop+v.y*cell;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();paint(k==selectedSector?0x8869bcb1:0x665f7e9c);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff9ec8d5);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
        for(Level.LineDef line:level.linedefs()){Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);p.setColor(line.back>=0?0xff63edbb:0xfff2c17b);p.setStrokeWidth(3);c.drawLine(mapLeft+a.x*cell,mapTop+a.y*cell,mapLeft+b.x*cell,mapTop+b.y*cell,p);}
        for(Level.Vertex v:level.vertices){paint(0xfff7e7a0);c.drawCircle(mapLeft+v.x*cell,mapTop+v.y*cell,Math.max(3,cell*.17f),p);}
        for(int id:pending){Level.Vertex v=level.vertices.get(id);paint(0xffe77a7a);c.drawCircle(mapLeft+v.x*cell,mapTop+v.y*cell,Math.max(5,cell*.25f),p);}
        text(c,"Sectors: "+level.sectors.size()+" | Vertices: "+level.vertices.size()+" | Linedefs: "+level.linedefs().size(),12,h-14,14,Color.WHITE);
    }
    private void polygonTap(float x,float y){if(x<mapLeft||y<mapTop||x>=mapLeft+Level.W*cell||y>=mapTop+Level.H*cell)return;
        float wx=Math.round((x-mapLeft)/cell*2)/2f,wy=Math.round((y-mapTop)/cell*2)/2f;
        if(polygonTool==1){selectedSector=level.sectorAt(wx,wy);hint(selectedSector>=0?"Sector "+(selectedSector+1)+" selected":"No sector here");invalidate();return;}
        int id=level.addVertex(wx,wy);
        if(pending.size()>=3&&id==pending.get(0)){Level.Sector sec=new Level.Sector();sec.loop.addAll(pending);level.sectors.add(sec);selectedSector=level.sectors.size()-1;pending.clear();hint("Polygon sector created");}
        else if(!pending.contains(id)){pending.add(id);hint("Vertex "+pending.size()+" added");}else hint("Tap first vertex to close");invalidate();
    }
    private void drawPolygonWorld(Canvas c){
        float vw=w*.78f,top=toolbarH,bottom=h,mid=(top+bottom)*.5f;
        rect(c,0,top,vw,mid,0xff3e485f);rect(c,0,mid,vw,bottom,0xff514c40);
        float ox=level.playerX,oy=level.playerY,dir=level.facing;
        // Clip each polygon edge against the near camera plane and draw wall quads.
        // Depth-sort painter rendering is sufficient for the simple sector preview.
        ArrayList<float[]> walls=new ArrayList<>();
        for(Level.LineDef line:level.linedefs()){
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
            Level.Sector front=level.sectors.get(line.front);
            float lo=front.floor,hi=front.ceiling;
            if(line.back>=0){Level.Sector back=level.sectors.get(line.back);
                if(back.floor>lo)addProjectedWall(walls,a,b,lo,back.floor,ox,oy,dir,front.wallMaterial,vw,mid,bottom-top);
                if(back.ceiling<hi)addProjectedWall(walls,a,b,back.ceiling,hi,ox,oy,dir,front.wallMaterial,vw,mid,bottom-top);
            }else addProjectedWall(walls,a,b,lo,hi,ox,oy,dir,front.wallMaterial,vw,mid,bottom-top);
        }
        java.util.Collections.sort(walls,(a,b)->Float.compare(b[0],a[0]));
        for(float[] wall:walls){Path path=new Path();path.moveTo(wall[1],wall[2]);path.lineTo(wall[3],wall[4]);path.lineTo(wall[5],wall[6]);path.lineTo(wall[7],wall[8]);path.close();paint(shade(palette[(int)wall[9]],Math.max(.35f,1-wall[0]*.025f)));c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x88666666);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
        text(c,"Polygon walls + portal height differences | Swipe to look",12,toolbarH+24,14,Color.WHITE);
    }
    private void addProjectedWall(ArrayList<float[]> walls,Level.Vertex a,Level.Vertex b,float lo,float hi,float ox,float oy,float dir,int tex,float vw,float mid,float vh){
        float sn=(float)Math.sin(dir),cs=(float)Math.cos(dir);
        float ax=(a.x-ox)*(-sn)+(a.y-oy)*cs,az=(a.x-ox)*cs+(a.y-oy)*sn;
        float bx=(b.x-ox)*(-sn)+(b.y-oy)*cs,bz=(b.x-ox)*cs+(b.y-oy)*sn;
        float near=.08f;if(az<=near&&bz<=near)return;
        if(az<near){float t=(near-az)/(bz-az);ax+=(bx-ax)*t;az=near;}
        if(bz<near){float t=(near-bz)/(az-bz);bx+=(ax-bx)*t;bz=near;}
        float focal=vw*.68f;
        float x1=vw*.5f+ax/az*focal,x2=vw*.5f+bx/bz*focal;
        if(Math.max(x1,x2)<0||Math.min(x1,x2)>vw)return;
        float eye=1.4f,yTop1=mid-(hi-eye)/az*focal,yBottom1=mid-(lo-eye)/az*focal;
        float yTop2=mid-(hi-eye)/bz*focal,yBottom2=mid-(lo-eye)/bz*focal;
        walls.add(new float[]{(az+bz)*.5f,x1,yTop1,x2,yTop2,x2,yBottom2,x1,yBottom1,Math.max(1,Math.min(5,tex))});
    }

}
