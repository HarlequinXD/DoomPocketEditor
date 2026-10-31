package com.example.doomeditor;
import android.content.*;import android.graphics.*;import android.view.*;
/** CPU ray-cast wall preview from sector polygon edges; no OpenGL dependency. */
public class RendererView extends View {
 public World world;private final Paint p=new Paint();private Bitmap buffer;private int[] pixels;private int bw=320,bh=180;
 public RendererView(Context c,World w){super(c);world=w;}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(buffer==null){buffer=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);pixels=new int[bw*bh];}for(int y=0;y<bh;y++){int col=y<bh/2?0xff526477:0xff393a3d;for(int x=0;x<bw;x++)pixels[y*bw+x]=col;}
 for(int x=0;x<bw;x++){double a=world.angle+Math.atan((x-bw*.5)/(bw*.80));double dx=Math.cos(a),dy=Math.sin(a),best=1000;int wall=0xffa0a8b2;for(World.Sector s:world.sectors){for(int i=0;i<s.points.size();i++){float[] q=s.points.get(i),r=s.points.get((i+1)%s.points.size());double sx=r[0]-q[0],sy=r[1]-q[1],den=dx*sy-dy*sx;if(Math.abs(den)<1e-7)continue;double ax=q[0]-world.px,ay=q[1]-world.py;double t=(ax*sy-ay*sx)/den,u=(ax*dy-ay*dx)/den;if(t>.02&&u>=0&&u<=1&&t<best){best=t;int shade=Math.max(45,Math.min(235,s.light-(int)(t*7)));if(Math.abs(sx)>Math.abs(sy))shade=(int)(shade*.77);wall=Color.rgb(shade,shade,(int)Math.min(255,shade*1.08));}}}
 if(best<1000){double dist=best*Math.cos(a-world.angle);int h=(int)Math.min(bh*4,bh*1.5/Math.max(.1,dist));int top=Math.max(0,bh/2-h/2),bottom=Math.min(bh,bh/2+h/2);for(int y=top;y<bottom;y++)pixels[y*bw+x]=wall;}}
 buffer.setPixels(pixels,0,bw,0,0,bw,bh);canvas.drawBitmap(buffer,null,new Rect(0,0,getWidth(),getHeight()),p);p.setColor(0xffffffff);p.setStrokeWidth(2);canvas.drawLine(getWidth()/2f-9,getHeight()/2f,getWidth()/2f+9,getHeight()/2f,p);canvas.drawLine(getWidth()/2f,getHeight()/2f-9,getWidth()/2f,getHeight()/2f+9,p);}
 public void move(float forward,float strafe){float a=world.angle,nx=world.px+(float)Math.cos(a)*forward-(float)Math.sin(a)*strafe,ny=world.py+(float)Math.sin(a)*forward+(float)Math.cos(a)*strafe;if(world.inside(nx,ny)){world.px=nx;world.py=ny;}invalidate();}public void turn(float radians){world.angle+=radians;invalidate();}
}
