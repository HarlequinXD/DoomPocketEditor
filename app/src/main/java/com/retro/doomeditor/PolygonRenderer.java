package com.retro.doomeditor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;

/** Low-resolution CPU polygon renderer for Doom-style sectors. */
public final class PolygonRenderer {
    private static final int RW = 320, RH = 180;
    private static final float NEAR = .08f, FOCAL = 225f, EYE = 1.35f;
    private static final int[] COLORS = {0xff687786,0xff8e7861,0xff68728a,0xff927c68,0xff87749c,0xff9e8a57};
    private static Bitmap buffer;
    private static Canvas offscreen;
    private static final Paint paint = new Paint();
    private static final Paint screenPaint = new Paint();
    private PolygonRenderer() {}

    private static class Point3 {
        float x, z, height;
        Point3(float x,float z,float height){this.x=x;this.z=z;this.height=height;}
    }
    private static class Face {
        ArrayList<Point3> pts;
        int color;
        float depth;
        Face(ArrayList<Point3> pts,int color,float depth){this.pts=pts;this.color=color;this.depth=depth;}
    }
    private static Point3 cameraPoint(float x,float y,float height,float ox,float oy,float cs,float sn){
        float dx=x-ox,dy=y-oy;
        return new Point3(-dx*sn+dy*cs,dx*cs+dy*sn,height);
    }
    private static ArrayList<Point3> clipNear(ArrayList<Point3> input){
        ArrayList<Point3> out=new ArrayList<>();
        if(input.isEmpty())return out;
        Point3 prev=input.get(input.size()-1);
        for(Point3 cur:input){
            boolean a=prev.z>=NEAR,b=cur.z>=NEAR;
            if(a!=b){
                float t=(NEAR-prev.z)/(cur.z-prev.z);
                out.add(new Point3(prev.x+t*(cur.x-prev.x),NEAR,prev.height+t*(cur.height-prev.height)));
            }
            if(b)out.add(cur);
            prev=cur;
        }
        return out;
    }
    private static void addFace(ArrayList<Face> faces,ArrayList<Point3> input,int color){
        ArrayList<Point3> clipped=clipNear(input);
        if(clipped.size()<3)return;
        float depth=0;
        for(Point3 pt:clipped)depth+=pt.z;
        faces.add(new Face(clipped,color,depth/clipped.size()));
    }
    private static void quad(ArrayList<Face> faces,Level.Vertex a,Level.Vertex b,float low,float high,
                             float ox,float oy,float cs,float sn,int color){
        if(high<=low+.001f)return;
        ArrayList<Point3> pts=new ArrayList<>();
        pts.add(cameraPoint(a.x,a.y,high,ox,oy,cs,sn));
        pts.add(cameraPoint(b.x,b.y,high,ox,oy,cs,sn));
        pts.add(cameraPoint(b.x,b.y,low,ox,oy,cs,sn));
        pts.add(cameraPoint(a.x,a.y,low,ox,oy,cs,sn));
        addFace(faces,pts,color);
    }
    private static int shade(int col,float factor){
        return Color.rgb(Math.min(255,(int)(Color.red(col)*factor)),
                Math.min(255,(int)(Color.green(col)*factor)),
                Math.min(255,(int)(Color.blue(col)*factor)));
    }
    private static int material(int id){return COLORS[Math.max(0,Math.min(COLORS.length-1,id))];}
    private static void plane(ArrayList<Face> faces,Level level,Level.Sector sec,float height,
                              float ox,float oy,float cs,float sn,int color){
        ArrayList<Point3> pts=new ArrayList<>();
        for(int id:sec.loop){
            if(id<0||id>=level.vertices.size())return;
            Level.Vertex v=level.vertices.get(id);
            pts.add(cameraPoint(v.x,v.y,height,ox,oy,cs,sn));
        }
        addFace(faces,pts,color);
    }
    public static void render(Canvas output,Level level,float left,float top,float right,float bottom,
                              float ox,float oy,float angle){
        if(right<=left||bottom<=top)return;
        if(buffer==null){
            buffer=Bitmap.createBitmap(RW,RH,Bitmap.Config.ARGB_8888);
            offscreen=new Canvas(buffer);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff39455b);offscreen.drawRect(0,0,RW,RH/2f,paint);
        paint.setColor(0xff4b463d);offscreen.drawRect(0,RH/2f,RW,RH,paint);
        float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);
        ArrayList<Face> faces=new ArrayList<>();
        for(Level.Sector sec:level.sectors){
            if(sec.loop.size()<3)continue;
            plane(faces,level,sec,sec.floor,ox,oy,cs,sn,shade(material(sec.floorMaterial),.75f));
            plane(faces,level,sec,sec.ceiling,ox,oy,cs,sn,shade(material(sec.ceilingMaterial),.60f));
        }
        for(Level.LineDef line:level.linedefs()){
            if(line.from<0||line.to<0||line.from>=level.vertices.size()||line.to>=level.vertices.size())continue;
            if(line.front<0||line.front>=level.sectors.size())continue;
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
            Level.Sector front=level.sectors.get(line.front);
            int color=material(front.wallMaterial);
            float edgeShade=(Math.abs(a.x-b.x)>Math.abs(a.y-b.y))?.85f:1f;
            color=shade(color,edgeShade);
            if(line.back>=0&&line.back<level.sectors.size()){
                Level.Sector back=level.sectors.get(line.back);
                quad(faces,a,b,Math.min(front.floor,back.floor),Math.max(front.floor,back.floor),ox,oy,cs,sn,color);
                quad(faces,a,b,Math.min(front.ceiling,back.ceiling),Math.max(front.ceiling,back.ceiling),ox,oy,cs,sn,color);
            }else quad(faces,a,b,front.floor,front.ceiling,ox,oy,cs,sn,color);
        }
        // Painter sorting is intentionally simple; overlapping geometry can require a depth buffer later.
        Collections.sort(faces,(a,b)->Float.compare(b.depth,a.depth));
        for(Face face:faces){
            Path path=new Path();boolean first=true;
            for(Point3 pt:face.pts){
                float sx=RW*.5f+pt.x/pt.z*FOCAL;
                float sy=RH*.5f-(pt.height-EYE)/pt.z*FOCAL;
                if(first){path.moveTo(sx,sy);first=false;}else path.lineTo(sx,sy);
            }
            path.close();
            paint.setColor(shade(face.color,Math.max(.35f,1f-face.depth*.028f)));
            paint.setStyle(Paint.Style.FILL);offscreen.drawPath(path,paint);
        }
        // Nearest-neighbour enlargement produces crisp, low-resolution PS1-like pixels.
        screenPaint.setFilterBitmap(false);
        output.drawBitmap(buffer,new Rect(0,0,RW,RH),new RectF(left,top,right,bottom),screenPaint);
    }
}
