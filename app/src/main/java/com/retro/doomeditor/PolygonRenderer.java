package com.retro.doomeditor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Arrays;

/** 320x180 CPU polygon renderer with textured triangles and per-pixel Z buffer. */
public final class PolygonRenderer {
    private static final int W=320,H=180,T=32;
    private static final float NEAR=.08f,FOCAL=225f,EYE=1.35f;
    private static final int[] pixels=new int[W*H];
    private static final float[] zbuffer=new float[W*H];
    private static final int[] surfaceBuffer=new int[W*H];
    private static int currentSurface=-1,highlightSurface=-1;
    private static final java.util.HashMap<String,Integer> wallMaterials=new java.util.HashMap<>();
    private static final ArrayList<String> wallKeys=new ArrayList<>();
    private static String key(Level level,Level.LineDef line,int part){
        Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
        String aKey=a.x+","+a.y,bKey=b.x+","+b.y;
        return (aKey.compareTo(bKey)<0?aKey+"|"+bKey:bKey+"|"+aKey)+"|"+line.front+"|"+part;
    }
    public static void loadWallMaterials(android.content.Context ctx){
        wallMaterials.clear();
        try{
            org.json.JSONObject obj=new org.json.JSONObject(ctx.getSharedPreferences("project",0).getString("wallMaterials","{}"));
            java.util.Iterator<String> keys=obj.keys();
            while(keys.hasNext()){String k=keys.next();wallMaterials.put(k,obj.getInt(k));}
        }catch(Exception ignored){}
    }
    public static boolean applyWallMaterial(android.content.Context ctx,int hit,int material){
        int index=(hit-100000)/2,part=(hit-100000)%2;
        if(hit<100000||index<0||index>=wallKeys.size())return false;
        wallMaterials.put(wallKeys.get(index)+"|"+part,material);
        org.json.JSONObject json=new org.json.JSONObject();
        try{for(java.util.Map.Entry<String,Integer> entry:wallMaterials.entrySet())json.put(entry.getKey(),entry.getValue());}
        catch(Exception ignored){}
        ctx.getSharedPreferences("project",0).edit().putString("wallMaterials",json.toString()).apply();
        return true;
    }
    public static void setHighlightHit(int hit){highlightSurface=hit;}

    // IDs: sector*3 + 0 wall, +1 floor, +2 ceiling.
    public static int pick(float x,float y,float left,float top,float right,float bottom){
        if(x<left||x>=right||y<top||y>=bottom)return -1;
        int px=Math.max(0,Math.min(W-1,(int)((x-left)/(right-left)*W)));
        int py=Math.max(0,Math.min(H-1,(int)((y-top)/(bottom-top)*H)));
        return surfaceBuffer[py*W+px];
    }
    public static void setHighlight(int sector,int surface){highlightSurface=sector<0?-1:sector*3+surface;}
    public static int textureCount(){return textures.length;}
    public static Bitmap texturePreview(int material){
        int m=Math.max(0,Math.min(textures.length-1,material));
        Bitmap b=Bitmap.createBitmap(T,T,Bitmap.Config.ARGB_8888);
        b.setPixels(textures[m],0,T,0,0,T,T);return b;
    }
    private static final int[][] textures=new int[6][T*T];
    private static final int[] BASE={0xff657482,0xff8e7861,0xff747c85,0xff937c68,0xff88749b,0xff9e8a57};
    private static final Paint screenPaint=new Paint();
    private static Bitmap bitmap;
    private PolygonRenderer(){}

    private static class V {
        float x,z,y,u,v;
        V(float x,float z,float y,float u,float v){this.x=x;this.z=z;this.y=y;this.u=u;this.v=v;}
    }
    private static class P {
        float x,y,iz,uz,vz;
        P(V v){iz=1f/v.z;x=W*.5f+v.x*iz*FOCAL;y=H*.5f-(v.y-EYE)*iz*FOCAL;uz=v.u*iz;vz=v.v*iz;}
    }
    private static int shade(int c,float f){
        return Color.rgb(Math.min(255,Math.max(0,(int)(Color.red(c)*f))),
            Math.min(255,Math.max(0,(int)(Color.green(c)*f))),
            Math.min(255,Math.max(0,(int)(Color.blue(c)*f))));
    }
    static {
        for(int t=0;t<6;t++)for(int y=0;y<T;y++)for(int x=0;x<T;x++){
            int n=((x*73+y*151+x*y*19+t*41)^((x+y)*29))&15;
            float light=.83f+n*.018f;
            if(t==1||t==3){int shift=((y/8)&1)*8;if(y%8==0||(x+shift)%16==0)light=.43f;else if(t==3)light*=.86f;}
            else if(t==2){if(x%8==0||y%8==0)light=.55f;}
            else if(t==4){if(x%16==0||y%16==0)light=.55f;else if(x%16==2&&y%16==2)light=1.25f;}
            else if(t==5){if(x%8==0)light=.43f;else if(x%8==1)light=1.1f;}
            else if(x%16==0||y%16==0)light=.68f;
            textures[t][y*T+x]=shade(BASE[t],light);
        }
    }
    private static V camera(float x,float y,float h,float u,float v,float ox,float oy,float cs,float sn){
        float dx=x-ox,dy=y-oy;
        return new V(-dx*sn+dy*cs,dx*cs+dy*sn,h,u,v);
    }
    private static V intersect(V a,V b){
        float t=(NEAR-a.z)/(b.z-a.z);
        return new V(a.x+(b.x-a.x)*t,NEAR,a.y+(b.y-a.y)*t,
            a.u+(b.u-a.u)*t,a.v+(b.v-a.v)*t);
    }
    private static float edge(P a,P b,float x,float y){return (x-a.x)*(b.y-a.y)-(y-a.y)*(b.x-a.x);}
    private static void triangle(P a,P b,P c,int mat,float light){
        float area=edge(a,b,c.x,c.y);
        if(Math.abs(area)<.00001f)return;
        int x0=Math.max(0,(int)Math.floor(Math.min(a.x,Math.min(b.x,c.x))));
        int x1=Math.min(W-1,(int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))));
        int y0=Math.max(0,(int)Math.floor(Math.min(a.y,Math.min(b.y,c.y))));
        int y1=Math.min(H-1,(int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))));
        if(x0>x1||y0>y1)return;
        float inv=1f/area;
        int[] tex=textures[Math.max(0,Math.min(5,mat))];
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
            float sx=x+.5f,sy=y+.5f;
            float wa=edge(b,c,sx,sy)*inv,wb=edge(c,a,sx,sy)*inv,wc=1-wa-wb;
            if(wa<-.0001f||wb<-.0001f||wc<-.0001f)continue;
            float iz=wa*a.iz+wb*b.iz+wc*c.iz;
            int pos=y*W+x;
            if(iz<=zbuffer[pos])continue;
            float u=(wa*a.uz+wb*b.uz+wc*c.uz)/iz;
            float v=(wa*a.vz+wb*b.vz+wc*c.vz)/iz;
            int tx=((int)Math.floor(u*T))&(T-1),ty=((int)Math.floor(v*T))&(T-1);
            float fog=Math.max(.34f,1f-(1f/iz)*.022f);
            int shaded=shade(tex[ty*T+tx],light*fog);
            if(currentSurface==highlightSurface)shaded=Color.rgb(Math.min(255,Color.red(shaded)/2+40),Math.min(255,Color.green(shaded)/2+140),Math.min(255,Color.blue(shaded)/2+65));
            pixels[pos]=shaded;surfaceBuffer[pos]=currentSurface;
            zbuffer[pos]=iz;
        }
    }
    private static void polygon(ArrayList<V> input,int mat,float light){
        if(input.size()<3)return;
        ArrayList<V> clipped=new ArrayList<>();
        V prev=input.get(input.size()-1);
        for(V cur:input){
            boolean pa=prev.z>=NEAR,ca=cur.z>=NEAR;
            if(pa!=ca)clipped.add(intersect(prev,cur));
            if(ca)clipped.add(cur);
            prev=cur;
        }
        if(clipped.size()<3)return;
        P first=new P(clipped.get(0));
        for(int i=1;i+1<clipped.size();i++)triangle(first,new P(clipped.get(i)),new P(clipped.get(i+1)),mat,light);
    }
    private static void wall(Level.Vertex a,Level.Vertex b,float low,float high,int mat,float ox,float oy,float cs,float sn,float brightness){
        if(high<=low+.001f)return;
        float length=(float)Math.hypot(b.x-a.x,b.y-a.y);
        float light=(Math.abs(b.x-a.x)>Math.abs(b.y-a.y)?.83f:1f)*brightness;
        ArrayList<V> q=new ArrayList<>(4);
        q.add(camera(a.x,a.y,high,0,high,ox,oy,cs,sn));
        q.add(camera(b.x,b.y,high,length,high,ox,oy,cs,sn));
        q.add(camera(b.x,b.y,low,length,low,ox,oy,cs,sn));
        q.add(camera(a.x,a.y,low,0,low,ox,oy,cs,sn));
        polygon(q,mat,light);
    }
    private static float orient(Level.Vertex a,Level.Vertex b,Level.Vertex c){
        return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
    }
    private static boolean inTriangle(Level.Vertex p,Level.Vertex a,Level.Vertex b,Level.Vertex c,float winding){
        return orient(a,b,p)*winding>=-.00001f&&orient(b,c,p)*winding>=-.00001f&&orient(c,a,p)*winding>=-.00001f;
    }
    /** Ear-clipping triangulation supports concave simple sector polygons. */
    private static void plane(Level level,Level.Sector s,float h,int mat,float ox,float oy,float cs,float sn,float light){
        int n=s.loop.size();if(n<3)return;
        ArrayList<Integer> remaining=new ArrayList<>();
        float signed=0;
        for(int i=0;i<n;i++){
            int a=s.loop.get(i),b=s.loop.get((i+1)%n);
            if(a<0||b<0||a>=level.vertices.size()||b>=level.vertices.size())return;
            remaining.add(a);
            Level.Vertex va=level.vertices.get(a),vb=level.vertices.get(b);
            signed+=va.x*vb.y-vb.x*va.y;
        }
        float winding=signed>=0?1f:-1f;
        int guard=0;
        while(remaining.size()>=3&&guard++<n*n){
            boolean found=false;int count=remaining.size();
            for(int i=0;i<count;i++){
                Level.Vertex a=level.vertices.get(remaining.get((i+count-1)%count));
                Level.Vertex b=level.vertices.get(remaining.get(i));
                Level.Vertex c=level.vertices.get(remaining.get((i+1)%count));
                if(orient(a,b,c)*winding<=.00001f)continue;
                boolean contains=false;
                for(int j=0;j<count;j++){
                    if(j==i||j==(i+count-1)%count||j==(i+1)%count)continue;
                    Level.Vertex p=level.vertices.get(remaining.get(j));
                    if(inTriangle(p,a,b,c,winding)){contains=true;break;}
                }
                if(contains)continue;
                ArrayList<V> tri=new ArrayList<>(3);
                tri.add(camera(a.x,a.y,h,a.x*.5f,a.y*.5f,ox,oy,cs,sn));
                tri.add(camera(b.x,b.y,h,b.x*.5f,b.y*.5f,ox,oy,cs,sn));
                tri.add(camera(c.x,c.y,h,c.x*.5f,c.y*.5f,ox,oy,cs,sn));
                polygon(tri,mat,light);
                remaining.remove(i);found=true;break;
            }
            if(!found)break;
        }
    }
    public static void render(Canvas output,Level level,float left,float top,float right,float bottom,float ox,float oy,float angle){
        if(right<=left||bottom<=top)return;
        Arrays.fill(zbuffer,0f);Arrays.fill(surfaceBuffer,-1);wallKeys.clear();
        for(int y=0;y<H;y++)Arrays.fill(pixels,y*W,(y+1)*W,y<H/2?0xff39455b:0xff4b463d);
        float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);
        for(int si=0;si<level.sectors.size();si++){
            Level.Sector s=level.sectors.get(si);
            if(s.loop.size()<3)continue;
            currentSurface=si*3+1;plane(level,s,s.floor,s.floorMaterial,ox,oy,cs,sn,.85f*s.light/192f);
            currentSurface=si*3+2;plane(level,s,s.ceiling,s.ceilingMaterial,ox,oy,cs,sn,.65f*s.light/192f);
        }
        for(Level.LineDef line:PortalTopology.build(level)){
            if(line.from<0||line.to<0||line.from>=level.vertices.size()||line.to>=level.vertices.size()||line.front<0||line.front>=level.sectors.size())continue;
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
            Level.Sector front=level.sectors.get(line.front);
            int index=wallKeys.size();
            wallKeys.add(key(level,line,0).substring(0,key(level,line,0).length()-2)); // geometry + front, suffix added per wall part
            String base=wallKeys.get(index);
            int lower=wallMaterials.containsKey(base+"|0")?wallMaterials.get(base+"|0"):front.wallMaterial;
            int upper=wallMaterials.containsKey(base+"|1")?wallMaterials.get(base+"|1"):front.wallMaterial;
            if(line.back>=0&&line.back<level.sectors.size()){
                Level.Sector back=level.sectors.get(line.back);
                currentSurface=100000+index*2;
                wall(a,b,Math.min(front.floor,back.floor),Math.max(front.floor,back.floor),lower,ox,oy,cs,sn,front.light/192f);
                currentSurface=100000+index*2+1;
                wall(a,b,Math.min(front.ceiling,back.ceiling),Math.max(front.ceiling,back.ceiling),upper,ox,oy,cs,sn,front.light/192f);
            }else{
                currentSurface=100000+index*2;
                wall(a,b,front.floor,front.ceiling,lower,ox,oy,cs,sn,front.light/192f);
            }
        }
        if(bitmap==null)bitmap=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels,0,W,0,0,W,H);
        screenPaint.setFilterBitmap(false);
        output.drawBitmap(bitmap,new Rect(0,0,W,H),new RectF(left,top,right,bottom),screenPaint);
    }
}
