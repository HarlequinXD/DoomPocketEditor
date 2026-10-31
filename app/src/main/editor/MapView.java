package com.example.doomeditor;
import android.content.*;import android.graphics.*;import android.view.*;
/** Touch-friendly grid cursor editor. D-pad moves the cursor; APPLY adds a vertex. */
public class MapView extends View {
 public World world;public float cx=2,cy=2;public float zoom=38,ox=0,oy=0;private final Paint p=new Paint(3);private float lastX,lastY;private boolean dragging;
 public MapView(Context c,World w){super(c);world=w;setBackgroundColor(0xff141d28);}
 void stroke(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setColor(color);p.setStrokeWidth(width);p.setStyle(Paint.Style.STROKE);c.drawLine(x,y,xx,yy,p);p.setStyle(Paint.Style.FILL);}
 void dot(Canvas c,float x,float y,int color,float rad){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,rad,p);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(0xff141d28);c.save();c.translate(getWidth()/2f+ox,getHeight()/2f+oy);c.scale(zoom,zoom);float left=(-getWidth()/2f-ox)/zoom-1,right=(getWidth()/2f-ox)/zoom+1,top=(-getHeight()/2f-oy)/zoom-1,bottom=(getHeight()/2f-oy)/zoom+1;
 for(int x=(int)Math.floor(left);x<=right;x++)stroke(c,x,top,x,bottom,x==0?0xff58718b:0xff2a3544,1/zoom);for(int y=(int)Math.floor(top);y<=bottom;y++)stroke(c,left,y,right,y,y==0?0xff58718b:0xff2a3544,1/zoom);
 for(int k=0;k<world.sectors.size();k++){World.Sector s=world.sectors.get(k);Path path=new Path();for(int i=0;i<s.points.size();i++){float[] v=s.points.get(i);if(i==0)path.moveTo(v[0],v[1]);else path.lineTo(v[0],v[1]);}path.close();p.setColor(k==world.selected?0x665ca5e7:0x55467c83);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);p.setColor(0xff85c6f1);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2/zoom);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);for(float[] v:s.points)dot(c,v[0],v[1],0xffc1e7fa,4/zoom);}
 for(int i=0;i<world.draft.size();i++){float[] v=world.draft.get(i);dot(c,v[0],v[1],0xffffc47c,5/zoom);if(i>0){float[] q=world.draft.get(i-1);stroke(c,q[0],q[1],v[0],v[1],0xffffc47c,2/zoom);}}
 stroke(c,cx-.32f,cy,cx+.32f,cy,0xff00ffb3,3/zoom);stroke(c,cx,cy-.32f,cx,cy+.32f,0xff00ffb3,3/zoom);dot(c,world.px,world.py,0xffff8d80,6/zoom);stroke(c,world.px,world.py,world.px+(float)Math.cos(world.angle)*.6f,world.py+(float)Math.sin(world.angle)*.6f,0xffff8d80,3/zoom);c.restore();}
 public void step(int dx,int dy){cx+=dx;cy+=dy;invalidate();}public void apply(){world.addPoint(cx,cy);invalidate();}
 @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();dragging=true;return true;case MotionEvent.ACTION_MOVE:if(dragging){ox+=e.getX()-lastX;oy+=e.getY()-lastY;lastX=e.getX();lastY=e.getY();invalidate();}return true;case MotionEvent.ACTION_UP:dragging=false;return true;}return true;}
}
