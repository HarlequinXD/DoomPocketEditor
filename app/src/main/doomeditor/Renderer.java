package com.retro.doomeditor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Software renderer in the spirit of GZDoom's software mode: perspective-correct textured polygons,
 * Doom-style light diminishing, sky, pitch (y-shearing), billboard sprites and a first-person weapon.
 * Geometry (topology + floor triangulation) is cached and rebuilt only when the map shape changes;
 * heights, textures and light are read live, so moving doors and lifts cost nothing extra.
 */
final class Renderer {
    static final int ID_FLOOR=1,ID_CEIL=2,ID_WALL=3,ID_SPRITE=4;
    static int kind(int id){return id<0?0:id>>>24;}
    static int value(int id){return id&0xffffff;}
    static int id(int kind,int value){return (kind<<24)|(value&0xffffff);}

    static final class SpriteDraw {float x,y,z;Sprites.Sprite spr;int pickId=-1;boolean bright;
        SpriteDraw set(float x,float y,float z,Sprites.Sprite s,int pickId,boolean bright){this.x=x;this.y=y;this.z=z;this.spr=s;this.pickId=pickId;this.bright=bright;return this;}}

    private static final float NEAR=.06f;
    int W,H;
    private int[] pix;private float[] zb;int[] ids;private Bitmap bmp;
    // per-pixel lighting: unlit surface colour and the sector (ambient) light it was drawn with, so coloured
    // lights add to the ambient light instead of brightening the already-shaded pixel (only filled when lights exist)
    private int[] alb;private short[] amb;private boolean litFrame;
    private static final short[] LM=new short[32];static{for(int i=0;i<32;i++)LM[i]=(short)Textures.lightMul(i);}
    private float focal,horizon,eye,camX,camY,cs,sn,kx;
    private final Paint blit=new Paint();
    // geometry cache
    private Level geoLevel;private long geoSig;
    ArrayList<Level.LineDef> lines=new ArrayList<>();
    int[][] tris=new int[0][];
    private float[] vx=new float[0],vz=new float[0];
    private int[] skyU=new int[0];
    // scratch polygon buffers (camera space in, clipped out)
    private final float[][] pa=new float[5][16],pb=new float[5][16];
    private final float[] sx=new float[16],sy=new float[16],siz=new float[16],suz=new float[16],svz=new float[16];
    int highlight=-1;
    float fadeK=1.35f;

    /** GPU back end (null = software). */
    private GpuRenderer gpu;
    /** Settings: draw the 3D scene on the GPU, and how many lines tall its picture is. */
    static boolean useGpu=true;static int gpuLines=270;
    static void loadPrefs(android.content.Context c){try{android.content.SharedPreferences p=c.getSharedPreferences("app",0);useGpu=p.getBoolean("gpu",true);gpuLines=p.getInt("gpuLines",270);GpuRenderer.relief=p.getBoolean("relief",true);GpuRenderer.pbr=p.getBoolean("pbr",true);GpuRenderer.gi=p.getBoolean("gi",true);GpuRenderer.shadows=p.getBoolean("shadows",true);}catch(Throwable ignored){}}
    static void savePrefs(android.content.Context c){try{c.getSharedPreferences("app",0).edit().putBoolean("gpu",useGpu).putInt("gpuLines",gpuLines).putBoolean("relief",GpuRenderer.relief).putBoolean("pbr",GpuRenderer.pbr).putBoolean("gi",GpuRenderer.gi).putBoolean("shadows",GpuRenderer.shadows).apply();}catch(Throwable ignored){}}
    boolean onGpu(){return gpu!=null;}
    /** One-line state for crash diagnostics. */
    String stats(){return (gpu!=null?"GPU "+W+"x"+H+" (size step "+dynLevel+"), last frame "+Math.round(frameAvg)+" ms":"software "+W+"x"+H)+", lights "+lightCount;}
    // dynamic resolution: if frames get slow (big fights, lots of lights) the 3D picture drops a size until it's
    // smooth again, then climbs back; a very slow frame drops straight to the smallest size so the phone never stalls
    private static final float[] DYN={1f,.85f,.72f,.6f,.5f};
    private int dynLevel;private long gpuStart;private float frameAvg;private int calm;
    private void adapt(float ms){
        if(ms>220){dynLevel=DYN.length-1;frameAvg=0;calm=0;return;}
        frameAvg=frameAvg==0?ms:frameAvg*.9f+ms*.1f;
        if(frameAvg>28&&dynLevel<DYN.length-1){dynLevel++;frameAvg=0;calm=0;}
        else if(frameAvg<14&&dynLevel>0){if(++calm>90){dynLevel--;frameAvg=0;calm=0;}}
        else calm=0;
    }

    Renderer(){blit.setFilterBitmap(false);blit.setAntiAlias(false);Textures.init();Sprites.init();}
    /** Switches to the GPU when it's turned on and works on this phone; otherwise stays in software. */
    private boolean gpuReady(){
        if(!useGpu){gpu=null;return false;}
        if(!GpuRenderer.available()){gpu=null;return false;}
        if(gpu==null)gpu=new GpuRenderer(this);
        return true;
    }

    /** Picks an internal resolution that matches the viewport's shape (about 200 lines tall). */
    private void setup(float vw,float vh){
        int h=200,w=Math.max(160,Math.min(560,Math.round(h*vw/Math.max(1,vh))));
        if(w==W&&h==H&&pix!=null)return;
        W=w;H=h;pix=new int[W*H];zb=new float[W*H];ids=new int[W*H];skyPix=new boolean[W*H];alb=new int[W*H];amb=new short[W*H];bmp=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);skyU=new int[W];
    }
    static long signature(Level l){
        long h=l.vertices.size()*1000003L+l.sectors.size();
        for(Level.Vertex v:l.vertices)h=h*31+Float.floatToIntBits(v.x)*17+Float.floatToIntBits(v.y);
        for(Level.Sector s:l.sectors){h=h*131+s.loop.size();for(int id:s.loop)h=h*37+id;}
        return h;
    }
    private void geometry(Level l){
        long sig=signature(l);
        if(l==geoLevel&&sig==geoSig)return;
        geoLevel=l;geoSig=sig;
        lines=PortalTopology.build(l);
        tris=new int[l.sectors.size()][];
        for(int i=0;i<l.sectors.size();i++)tris[i]=triangulate(l,l.sectors.get(i));
    }
    void invalidate(){geoLevel=null;}
    static String wallKey(Level level,Level.LineDef line,int part){
        Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
        String aKey=a.x+","+a.y,bKey=b.x+","+b.y;
        return (aKey.compareTo(bKey)<0?aKey+"|"+bKey:bKey+"|"+aKey)+"|"+line.front+"|"+part;
    }
    /** Ear clipping; falls back to a fan for degenerate loops. */
    private static int[] triangulate(Level l,Level.Sector s){
        int n=s.loop.size();if(n<3)return new int[0];
        for(int id:s.loop)if(id<0||id>=l.vertices.size())return new int[0];
        ArrayList<Integer> rem=new ArrayList<>(s.loop);
        float area=0;for(int i=0;i<n;i++){Level.Vertex a=l.vertices.get(s.loop.get(i)),b=l.vertices.get(s.loop.get((i+1)%n));area+=a.x*b.y-b.x*a.y;}
        float wind=area>=0?1:-1;
        int[] out=new int[(n-2)*3];int k=0,guard=0;
        while(rem.size()>3&&guard++<n*n){
            boolean found=false;int c=rem.size();
            for(int i=0;i<c;i++){
                Level.Vertex a=l.vertices.get(rem.get((i+c-1)%c)),b=l.vertices.get(rem.get(i)),d=l.vertices.get(rem.get((i+1)%c));
                if(orient(a,b,d)*wind<=1e-6f)continue;
                boolean inside=false;
                for(int j=0;j<c&&!inside;j++){if(j==i||j==(i+c-1)%c||j==(i+1)%c)continue;Level.Vertex p=l.vertices.get(rem.get(j));
                    inside=orient(a,b,p)*wind>=-1e-6f&&orient(b,d,p)*wind>=-1e-6f&&orient(d,a,p)*wind>=-1e-6f;}
                if(inside)continue;
                out[k++]=rem.get((i+c-1)%c);out[k++]=rem.get(i);out[k++]=rem.get((i+1)%c);rem.remove(i);found=true;break;
            }
            if(!found)break;
        }
        if(rem.size()==3){out[k++]=rem.get(0);out[k++]=rem.get(1);out[k++]=rem.get(2);}
        else for(int i=1;i+1<rem.size()&&k+3<=out.length;i++){out[k++]=rem.get(0);out[k++]=rem.get(i);out[k++]=rem.get(i+1);}
        return k==out.length?out:Arrays.copyOf(out,k);
    }
    private static float orient(Level.Vertex a,Level.Vertex b,Level.Vertex c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}

    // ------------------------------------------------------------------ frame
    /** Renders the scene into dst. pitch is in radians (positive looks up). */
    void render(Canvas out,Level level,float x,float y,float eyeZ,float angle,float pitch,RectF dst,List<SpriteDraw> sprites){
        renderScene(level,x,y,eyeZ,angle,pitch,dst.width(),dst.height(),sprites);
        present(out,dst);
    }
    void renderScene(Level level,float x,float y,float eyeZ,float angle,float pitch,float vw,float vh,List<SpriteDraw> sprites){
        if(gpuReady()){
            try{
                gpuStart=System.nanoTime();
                int h=Math.max(120,Math.min(720,Math.round(gpuLines*DYN[dynLevel]))),w=Math.max(160,Math.min(1600,Math.round(h*vw/Math.max(1,vh))));
                gpu.setup(w,h);W=w;H=h;geometry(level);
                camX=x;camY=y;eye=eyeZ;cs=(float)Math.cos(angle);sn=(float)Math.sin(angle);
                focal=W*.5f;kx=(W*.5f)/focal*1.04f;horizon=H*.5f+(float)Math.tan(Math.max(-.6f,Math.min(.6f,pitch)))*focal;
                gpu.renderScene(level,x,y,eyeZ,angle,pitch,sprites);
                lastCamX=camX;lastCamY=camY;lightCount=0;return;
            }catch(Throwable e){android.util.Log.w("Renderer","GPU frame failed, switching to software",e);gpu=null;useGpu=false;W=H=0;pix=null;}
        }
        setup(vw,vh);geometry(level);
        camX=x;camY=y;eye=eyeZ;cs=(float)Math.cos(angle);sn=(float)Math.sin(angle);
        focal=W*.5f;kx=(W*.5f)/focal*1.04f;
        horizon=H*.5f+(float)Math.tan(Math.max(-.6f,Math.min(.6f,pitch)))*focal;
        // background: sky if any sector uses it, else dark
        boolean sky=false;for(Level.Sector s:level.sectors)if(s.ceilingMaterial==Textures.SKY){sky=true;break;}
        Arrays.fill(zb,0f);Arrays.fill(ids,-1);Arrays.fill(skyPix,false);
        litFrame=lightCount>0;if(litFrame)Arrays.fill(amb,(short)0);
        if(sky)drawSky(angle);else Arrays.fill(pix,0xff0c0c10);
        // camera-space vertices
        int nv=level.vertices.size();if(vx.length<nv){vx=new float[nv];vz=new float[nv];}
        for(int i=0;i<nv;i++){Level.Vertex v=level.vertices.get(i);float dx=v.x-camX,dy=v.y-camY;vx[i]=-dx*sn+dy*cs;vz[i]=dx*cs+dy*sn;}
        // floors & ceilings
        for(int si=0;si<level.sectors.size()&&si<tris.length;si++){
            Level.Sector s=level.sectors.get(si);int[] t=tris[si];
            int L=light(s.light);
            for(int k=0;k+2<t.length;k+=3){
                if(s.floor<eye)plane(level,t[k],t[k+1],t[k+2],s.floor,s.floorMaterial,L,id(ID_FLOOR,si),false);
                if(s.ceiling>eye)plane(level,t[k],t[k+1],t[k+2],s.ceiling,s.ceilingMaterial,L,id(ID_CEIL,si),s.ceilingMaterial==Textures.SKY);
            }
        }
        // walls
        for(int li=0;li<lines.size();li++){
            Level.LineDef l=lines.get(li);
            if(l.from>=nv||l.to>=nv||l.front<0||l.front>=level.sectors.size())continue;
            Level.Sector f=level.sectors.get(l.front);
            Level.Vertex a=level.vertices.get(l.from),b=level.vertices.get(l.to);
            float len=(float)Math.hypot(b.x-a.x,b.y-a.y);
            int L=light(f.light)+(Math.abs(b.x-a.x)>Math.abs(b.y-a.y)?-1:1);
            if(l.back<0||l.back>=level.sectors.size()){
                wall(l,len,f.floor,f.ceiling,material(level,l,0,f.wallMaterial),L,id(ID_WALL,li<<1));
            }else{
                Level.Sector k=level.sectors.get(l.back);
                // doors and lifts carry their wall texture with them: pegged to the moving edge
                boolean lift=f.special==Level.SP_LIFT||k.special==Level.SP_LIFT,door=f.special==Level.SP_DOOR||k.special==Level.SP_DOOR;
                if(f.floor!=k.floor){float lo=Math.min(f.floor,k.floor),hi=Math.max(f.floor,k.floor);wall(l,len,lo,hi,material(level,l,0,f.wallMaterial),L,id(ID_WALL,li<<1),lift?hi:0);}
                boolean bothSky=f.ceilingMaterial==Textures.SKY&&k.ceilingMaterial==Textures.SKY;
                if(f.ceiling!=k.ceiling&&!bothSky){float lo=Math.min(f.ceiling,k.ceiling),hi=Math.max(f.ceiling,k.ceiling);wall(l,len,lo,hi,material(level,l,1,f.wallMaterial),L,id(ID_WALL,(li<<1)|1),door?lo:0);}
            }
        }
        if(fx!=null&&!fx.splats.isEmpty())drawSplats(level);
        if(sprites!=null)for(SpriteDraw s:sprites)sprite(level,s);
        if(fx!=null&&!fx.drops.isEmpty())drawDrops(level);
        if(particles!=null&&particles.n>0)drawParticles();
        atmospherePass();
        if(highlight>=0)tintHighlight();
    }
    int materialFor(Level level,Level.LineDef l,int part,int fallback){return material(level,l,part,fallback);}
    static int lightLevel(int sectorLight){return light(sectorLight);}
    private int material(Level level,Level.LineDef l,int part,int fallback){
        if(level.wallOverrides.isEmpty())return fallback;Integer m=level.wallOverrides.get(wallKey(level,l,part));return m==null?fallback:m;
    }
    private static int light(int sectorLight){return Math.max(0,Math.min(31,Math.round(sectorLight/255f*31f)));}
    void present(Canvas out,RectF dst){
        if(gpu!=null){try{out.drawBitmap(gpu.finish(),null,dst,blit);if(gpuStart!=0){adapt((System.nanoTime()-gpuStart)/1e6f);gpuStart=0;}return;}catch(Throwable e){gpu=null;useGpu=false;return;}}
        if(pix==null)return;bmp.setPixels(pix,0,W,0,0,W,H);out.drawBitmap(bmp,null,dst,blit);}

    private void drawSky(float angle){
        int[] sk=Textures.sky;int sw=Textures.SKY_W,sh=Textures.SKY_H;
        for(int x=0;x<W;x++){double a=angle+Math.atan((x+.5f-W*.5f)/focal);int u=(int)Math.floor(a/(2*Math.PI)*sw*2);skyU[x]=Math.floorMod(u,sw);}
        float vs=sh/(H*1.15f);
        for(int y=0;y<H;y++){int v=(int)(sh*.78f+(y-horizon)*vs);v=v<0?0:v>=sh?sh-1:v;int row=v*sw,o=y*W;for(int x=0;x<W;x++)pix[o+x]=sk[row+skyU[x]];}
    }
    private void plane(Level level,int a,int b,int c,float h,int mat,int L,int pid,boolean sky){
        float[][] p=pa;
        int[] idx={a,b,c};
        for(int i=0;i<3;i++){int v=idx[i];Level.Vertex w=level.vertices.get(v);p[0][i]=vx[v];p[1][i]=vz[v];p[2][i]=h;p[3][i]=w.x*.5f;p[4][i]=w.y*.5f;}
        polygon(3,mat,L,pid,sky);
    }
    private void wall(Level.LineDef l,float len,float lo,float hi,int mat,int L,int pid){wall(l,len,lo,hi,mat,L,pid,0);}
    /** {@code peg}: height the texture is fixed to (0 = world-aligned); a door's wall uses its bottom edge so it rises with the door. */
    private void wall(Level.LineDef l,float len,float lo,float hi,int mat,int L,int pid,float peg){
        if(hi-lo<.001f)return;
        float[][] p=pa;int A=l.from,B=l.to;
        p[0][0]=vx[A];p[1][0]=vz[A];p[2][0]=hi;p[3][0]=0;p[4][0]=-(hi-peg)*.5f;
        p[0][1]=vx[B];p[1][1]=vz[B];p[2][1]=hi;p[3][1]=len*.5f;p[4][1]=-(hi-peg)*.5f;
        p[0][2]=vx[B];p[1][2]=vz[B];p[2][2]=lo;p[3][2]=len*.5f;p[4][2]=-(lo-peg)*.5f;
        p[0][3]=vx[A];p[1][3]=vz[A];p[2][3]=lo;p[3][3]=0;p[4][3]=-(lo-peg)*.5f;
        polygon(4,mat,L,pid,false);
    }
    /** Clips the camera-space polygon in pa against the near and side planes. */
    private int clip(int n){
        float[][] in=pa,out=pb;
        for(int plane=0;plane<3;plane++){
            if(n<3)return 0;
            int m=0;
            for(int i=0;i<n;i++){
                int j=(i+1)%n;
                float di=dist(plane,in[0][i],in[1][i]),dj=dist(plane,in[0][j],in[1][j]);
                if(di>=0){if(m>=16)return 0;for(int k=0;k<5;k++)out[k][m]=in[k][i];m++;}
                if((di>=0)!=(dj>=0)){if(m>=16)return 0;float t=di/(di-dj);for(int k=0;k<5;k++)out[k][m]=in[k][i]+(in[k][j]-in[k][i])*t;m++;}
            }
            float[][] tmp=in;in=out;out=tmp;n=m;
        }
        if(in!=pa)for(int k=0;k<5;k++)System.arraycopy(in[k],0,pa[k],0,n);
        return n;
    }
    private float dist(int plane,float x,float z){return plane==0?z-NEAR:plane==1?z*kx-x:z*kx+x;}

    private void polygon(int n,int mat,int L,int pid,boolean sky){
        n=clip(n);if(n<3)return;
        float minY=1e9f,maxY=-1e9f;
        for(int i=0;i<n;i++){
            float z=pa[1][i],iz=1f/z;
            sx[i]=W*.5f+pa[0][i]*focal*iz;sy[i]=horizon-(pa[2][i]-eye)*focal*iz;
            siz[i]=iz;suz[i]=pa[3][i]*iz;svz[i]=pa[4][i]*iz;
            if(sy[i]<minY)minY=sy[i];if(sy[i]>maxY)maxY=sy[i];
        }
        // gradients from the largest-area vertex triple
        int i1=1,i2=2;float best=0;
        for(int a=1;a<n-1;a++)for(int b=a+1;b<n;b++){float ar=Math.abs((sx[a]-sx[0])*(sy[b]-sy[0])-(sx[b]-sx[0])*(sy[a]-sy[0]));if(ar>best){best=ar;i1=a;i2=b;}}
        if(best<1e-4f)return;
        float x10=sx[i1]-sx[0],y10=sy[i1]-sy[0],x20=sx[i2]-sx[0],y20=sy[i2]-sy[0];
        float det=x10*y20-x20*y10;
        float dIzdx=((siz[i1]-siz[0])*y20-(siz[i2]-siz[0])*y10)/det,dIzdy=((siz[i2]-siz[0])*x10-(siz[i1]-siz[0])*x20)/det;
        float dUdx=((suz[i1]-suz[0])*y20-(suz[i2]-suz[0])*y10)/det,dUdy=((suz[i2]-suz[0])*x10-(suz[i1]-suz[0])*x20)/det;
        float dVdx=((svz[i1]-svz[0])*y20-(svz[i2]-svz[0])*y10)/det,dVdy=((svz[i2]-svz[0])*x10-(svz[i1]-svz[0])*x20)/det;
        int y0=Math.max(0,(int)Math.ceil(minY-.5f)),y1=Math.min(H-1,(int)Math.floor(maxY-.5f));
        if(y0>y1)return;
        int m=Math.max(0,Math.min(Textures.COUNT-1,mat));
        int[][] table=Textures.table(m);boolean bright=Textures.BRIGHT[m];int[] src=Textures.base[m];boolean lit=litFrame&&src!=null;
        float fk=fadeK;int minL=Math.max(1,L/4);
        for(int y=y0;y<=y1;y++){
            float yc=y+.5f,xl=1e9f,xr=-1e9f;
            for(int i=0;i<n;i++){int j=i+1==n?0:i+1;float ya=sy[i],yb=sy[j];
                if((ya<=yc&&yb>yc)||(yb<=yc&&ya>yc)){float xx=sx[i]+(yc-ya)/(yb-ya)*(sx[j]-sx[i]);if(xx<xl)xl=xx;if(xx>xr)xr=xx;}}
            int xs=Math.max(0,(int)Math.ceil(xl-.5f)),xe=Math.min(W-1,(int)Math.floor(xr-.5f));
            if(xs>xe)continue;
            float ox=xs+.5f-sx[0],oy=yc-sy[0];
            float iz=siz[0]+dIzdx*ox+dIzdy*oy,uz=suz[0]+dUdx*ox+dUdy*oy,vz=svz[0]+dVdx*ox+dVdy*oy;
            int o=y*W+xs,end=y*W+xe;
            if(sky){for(;o<=end;o++){if(iz>zb[o]){zb[o]=iz;ids[o]=pid;skyPix[o]=true;}iz+=dIzdx;}continue;}
            for(;o<=end;o++){
                if(iz>zb[o]){
                    float z=1f/iz;
                    int tu=((int)Math.floor(uz*z*64f))&63,tv=((int)Math.floor(vz*z*64f))&63;
                    int li;if(bright)li=31;else{li=(int)(L+3-z*fk);if(li<minL)li=minL;else if(li>31)li=31;}
                    int ti=(tv<<6)|tu;pix[o]=table[li][ti];zb[o]=iz;ids[o]=pid;
                    if(lit){alb[o]=src[ti];amb[o]=bright?256:LM[li];}
                }
                iz+=dIzdx;uz+=dUdx;vz+=dVdx;
            }
        }
    }
    private void sprite(Level level,SpriteDraw d){
        Sprites.Sprite s=d.spr;if(s==null)return;
        float dx=d.x-camX,dy=d.y-camY,cx=-dx*sn+dy*cs,cz=dx*cs+dy*sn;
        if(cz<NEAR*2)return;
        float scale=focal/cz,ww=s.w/s.ppu*scale,hh=s.h/s.ppu*scale;
        float x0=W*.5f+cx*scale-ww*.5f,top=horizon-(d.z+s.h/s.ppu-eye)*scale;
        int xa=Math.max(0,(int)Math.ceil(x0-.5f)),xb=Math.min(W-1,(int)Math.floor(x0+ww-.5f));
        int ya=Math.max(0,(int)Math.ceil(top-.5f)),yb=Math.min(H-1,(int)Math.floor(top+hh-.5f));
        if(xa>xb||ya>yb)return;
        float iz=1f/cz;
        int mulv;
        if(d.bright||s.bright)mulv=256;else{int si=level.sectorAt(d.x,d.y);int L=si<0?24:light(level.sectors.get(si).light);int li=(int)(L+3-cz*fadeK);li=Math.max(Math.max(1,L/4),Math.min(31,li));mulv=Textures.lightMul(li);}
        float du=s.w/ww,dv=s.h/hh;
        for(int x=xa;x<=xb;x++){
            int u=(int)((x+.5f-x0)*du);if(u<0||u>=s.w)continue;
            for(int y=ya;y<=yb;y++){
                int o=y*W+x;if(iz<=zb[o])continue;
                int v=(int)((y+.5f-top)*dv);if(v<0||v>=s.h)continue;
                int c=s.px[v*s.w+u];int al=c>>>24;if(al==0)continue;
                int lit=mulv>=256?c:Textures.mul(c,mulv);
                if(al<250){ // anti-aliased sprite edge: blend over what's behind it
                    int b=pix[o],ia=255-al;
                    pix[o]=0xff000000|((((lit>>16)&255)*al+((b>>16)&255)*ia)/255<<16)|((((lit>>8)&255)*al+((b>>8)&255)*ia)/255<<8)|(((lit&255)*al+(b&255)*ia)/255);
                    if(litFrame){int ab=alb[o];alb[o]=0xff000000|((((c>>16)&255)*al+((ab>>16)&255)*ia)/255<<16)|((((c>>8)&255)*al+((ab>>8)&255)*ia)/255<<8)|(((c&255)*al+(ab&255)*ia)/255);amb[o]=(short)((Math.min(256,mulv)*al+amb[o]*ia)/255);}
                    if(al<128)continue;
                }else{pix[o]=lit|0xff000000;if(litFrame){alb[o]=c;amb[o]=(short)Math.min(256,mulv);}}
                zb[o]=iz;if(d.pickId>=0)ids[o]=d.pickId;
            }
        }
    }
    // ------------------------------------------------------------------ atmosphere (Atmosphere.java)
    private boolean[] skyPix=new boolean[0];
    private static final int MAX_LIGHTS=16;
    final float[] lx=new float[MAX_LIGHTS],ly=new float[MAX_LIGHTS],lz=new float[MAX_LIGHTS],lr2=new float[MAX_LIGHTS],lcr=new float[MAX_LIGHTS],lcg=new float[MAX_LIGHTS],lcb=new float[MAX_LIGHTS];
    int lightCount;
    int fogR,fogG,fogB;float fogStart,fogEnd,fogAmt;
    int tintR=255,tintG=255,tintB=255;float tintAmt;
    /** World-space particles drawn this frame (null for none). */
    Atmosphere.Particles particles;
    /** Adds a light for the next frame; the nearest lights to the camera are kept. */
    void addLight(float x,float y,float z,float radius,float r,float g,float b){
        float d=(x-lastCamX)*(x-lastCamX)+(y-lastCamY)*(y-lastCamY);if(d>(radius+24)*(radius+24))return;
        int slot=lightCount;
        if(lightCount>=MAX_LIGHTS){float worst=-1;slot=-1;for(int i=0;i<MAX_LIGHTS;i++){float di=(lx[i]-lastCamX)*(lx[i]-lastCamX)+(ly[i]-lastCamY)*(ly[i]-lastCamY);if(di>worst){worst=di;slot=i;}}if(worst<=d)return;}
        else lightCount++;
        lx[slot]=x;ly[slot]=y;lz[slot]=z;lr2[slot]=radius*radius;lcr[slot]=r;lcg[slot]=g;lcb[slot]=b;
    }
    void clearLights(){lightCount=0;}
    /** Fog colour (0 = no fog), the distances where it starts and is full, and how thick it gets. */
    void setFog(int color,float start,float end,float amount){if(color==0||amount<=0){fogAmt=0;return;}fogR=(color>>16)&255;fogG=(color>>8)&255;fogB=color&255;fogStart=start;fogEnd=Math.max(start+.5f,end);fogAmt=Math.min(1,amount);}
    void setTint(int color,float amount){tintAmt=color==0?0:Math.max(0,Math.min(1,amount));tintR=(color>>16)&255;tintG=(color>>8)&255;tintB=color&255;}
    /** Camera from the previous frame, used to cull far lights as they are added. */
    private float lastCamX,lastCamY;
    private void atmospherePass(){
        lastCamX=camX;lastCamY=camY;
        int nl=lightCount;lightCount=0;
        if(nl==0&&fogAmt<=0&&tintAmt<=0)return;
        float fs=fogStart,fe=fogEnd,fa=fogAmt,inv=1f/(fe-fs),ta=tintAmt;
        float tr=1-ta+ta*tintR/255f,tg=1-ta+ta*tintG/255f,tb=1-ta+ta*tintB/255f;
        float hw=W*.5f,invF=1f/focal;
        for(int y=0;y<H;y++){
            float ry=(horizon-(y+.5f))*invF;int o=y*W;
            for(int x=0;x<W;x++,o++){
                int c=pix[o];float R=(c>>16)&255,G=(c>>8)&255,B=c&255;
                float iz=zb[o];boolean sky=iz<=0||skyPix[o];
                if(!sky){
                    float z=1f/iz;
                    if(nl>0){
                        float cx=(x+.5f-hw)*invF*z,wz=eye+ry*z,wx=camX+z*cs-cx*sn,wy=camY+z*sn+cx*cs;
                        float ar=0,ag=0,ab=0;
                        for(int k=0;k<nl;k++){float dx=wx-lx[k],dy=wy-ly[k],dz=wz-lz[k],d2=dx*dx+dy*dy+dz*dz;if(d2<lr2[k]){float f=1-d2/lr2[k];f*=f;ar+=lcr[k]*f;ag+=lcg[k]*f;ab+=lcb[k]*f;}}
                        if(ar+ag+ab>0){int m=amb[o];
                            if(m>0){   // light = sector ambient + coloured lights, applied to the unlit surface colour
                                int a0=alb[o];float k=m/256f,A=(a0>>16)&255,Gc=(a0>>8)&255,Bc=a0&255;
                                R=A*Math.min(1.7f,k+ar*1.35f);G=Gc*Math.min(1.7f,k+ag*1.35f);B=Bc*Math.min(1.7f,k+ab*1.35f);}
                            else{R+=R*ar*1.6f+ar*30;G+=G*ag*1.6f+ag*30;B+=B*ab*1.6f+ab*30;}}
                    }
                    if(fa>0&&z>fs){float f=Math.min(1,(z-fs)*inv)*fa;R+=(fogR-R)*f;G+=(fogG-G)*f;B+=(fogB-B)*f;}
                }else if(fa>0){float f=fa*.6f;R+=(fogR-R)*f;G+=(fogG-G)*f;B+=(fogB-B)*f;}
                if(ta>0){R*=tr;G*=tg;B*=tb;}
                pix[o]=0xff000000|((int)Math.min(255,R)<<16)|((int)Math.min(255,G)<<8)|(int)Math.min(255,B);
            }
        }
    }
    private void drawParticles(){
        Atmosphere.Particles ps=particles;
        for(int i=0;i<ps.n;i++){
            float dx=ps.x[i]-camX,dy=ps.y[i]-camY,cx=-dx*sn+dy*cs,cz=dx*cs+dy*sn;if(cz<NEAR*2)continue;
            float sc=focal/cz;int sxp=(int)(W*.5f+cx*sc),syp=(int)(horizon-(ps.z[i]-eye)*sc);int rad=Math.max(0,(int)(ps.size[i]*sc*.5f));
            float fade=Math.min(1,ps.life[i]/Math.max(.01f,ps.max[i])*2.2f);float iz=1f/cz;int col=ps.col[i];
            for(int yy=syp-rad;yy<=syp+rad;yy++){if(yy<0||yy>=H)continue;for(int xx=sxp-rad;xx<=sxp+rad;xx++){if(xx<0||xx>=W)continue;int o=yy*W+xx;if(iz<=zb[o])continue;
                int b=pix[o];float a=fade*(rad==0?.85f:.75f);
                pix[o]=0xff000000|((int)(((col>>16)&255)*a+((b>>16)&255)*(1-a))<<16)|((int)(((col>>8)&255)*a+((b>>8)&255)*(1-a))<<8)|(int)((col&255)*a+(b&255)*(1-a));amb[o]=0;}}
        }
    }
    /** World whose blood droplets and splats are drawn this frame (null in the editor views). */
    World fx;
    private final float[] cX=new float[4],cY=new float[4],cQ=new float[4];
    private final int[] lightRow=new int[32];
    private static final int MS=32;private static final float SPAN=1.9f,MS_SCALE=MS/(2*SPAN);
    /** Blood splats on floors and walls: each pixel is ray-cast onto the splat's plane and drawn only where that surface is visible. */
    private void drawSplats(Level level){
        java.util.ArrayList<World.Splat> list=fx.splats;
        for(int li=0;li<32;li++)lightRow[li]=Textures.lightMul(li);
        for(int i=0,n=list.size();i<n;i++)splat(level,list.get(i));
    }
    private void splat(Level level,World.Splat s){
        float nx=s.nx,ny=s.ny,nz=s.nz;
        if((camX-s.x)*nx+(camY-s.y)*ny+(eye-s.z)*nz<=0)return;           // seen from behind
        float dx=s.x-camX,dy=s.y-camY,ccz=dx*cs+dy*sn,R=s.r*SPAN;
        if(ccz<-R||ccz>40)return;
        float ux,uy,uz,vx2,vy2,vz2;
        if(nz>.5f){ux=1;uy=0;uz=0;vx2=0;vy2=1;vz2=0;}else{ux=-ny;uy=nx;uz=0;vx2=0;vy2=0;vz2=1;}
        // splat quad in camera space, clipped to the near plane, gives a tight screen box
        int m=0;float[] qx=cX,qz=cY;float[] qy=cQ;
        for(int k=0;k<4;k++){float a=(k==0||k==3)?-R:R,b=k<2?-R:R;
            float px=s.x+ux*a+vx2*b-camX,py=s.y+uy*a+vy2*b-camY;qy[k]=s.z+uz*a+vz2*b;qx[k]=-px*sn+py*cs;qz[k]=px*cs+py*sn;}
        float minX=1e9f,maxX=-1e9f,minY=1e9f,maxY=-1e9f;
        for(int k=0;k<4;k++){int j=(k+1)&3;
            if(qz[k]>=NEAR){float X=W*.5f+qx[k]*focal/qz[k],Y=horizon-(qy[k]-eye)*focal/qz[k];minX=Math.min(minX,X);maxX=Math.max(maxX,X);minY=Math.min(minY,Y);maxY=Math.max(maxY,Y);m++;}
            if((qz[k]>=NEAR)!=(qz[j]>=NEAR)){float t=(NEAR-qz[k])/(qz[j]-qz[k]),ix=qx[k]+(qx[j]-qx[k])*t,iy=qy[k]+(qy[j]-qy[k])*t;
                float X=W*.5f+ix*focal/NEAR,Y=horizon-(iy-eye)*focal/NEAR;minX=Math.min(minX,X);maxX=Math.max(maxX,X);minY=Math.min(minY,Y);maxY=Math.max(maxY,Y);m++;}}
        if(m==0)return;
        if(maxX-minX<.6f&&maxY-minY<.6f)return;
        int x0=Math.max(0,(int)minX),x1=Math.min(W-1,(int)Math.ceil(maxX)),y0=Math.max(0,(int)minY),y1=Math.min(H-1,(int)Math.ceil(maxY));
        if(x0>x1||y0>y1)return;
        int si=level.sectorAt(s.x+nx*.02f,s.y+ny*.02f);int L=si<0?24:light(level.sectors.get(si).light);int minL=Math.max(1,L/4);
        float pd=dx*nx+dy*ny+(s.z-eye)*nz,inv=1f/focal,ir=1f/s.r;
        byte[] mask=s.mask;if(mask==null){mask=SplatDesigns.mask(s.type,s.ang,nz<.5f,s.seed,MS,SPAN);s.mask=mask;}
        int[] lt=lightRow;
        if(nz>.5f){   // floor splat: each screen row is one distance, so everything but the mask lookup is per row
            for(int y=y0;y<=y1;y++){float ry=(horizon-(y+.5f))*inv;if(ry>-1e-5f)continue;
                float t=pd/ry;if(t<NEAR)continue;float iz=1f/t,tol=iz*.035f+1e-4f;
                int li=(int)(L+3-t*fadeK);if(li<minL)li=minL;else if(li>31)li=31;int lm=lt[li];
                float rx0=(x0+.5f-W*.5f)*inv,a=(cs-rx0*sn)*t-dx,b=(sn+rx0*cs)*t-dy,da=-sn*t*inv,db=cs*t*inv;
                float mu=(a*ir+SPAN)*MS_SCALE,mv=(b*ir+SPAN)*MS_SCALE,dmu=da*ir*MS_SCALE,dmv=db*ir*MS_SCALE;
                int c1=Textures.mul(s.color,lm),c2=Textures.mul(Textures.mul(s.color,190),lm),c3=Textures.mul(Textures.mul(s.color,330),lm),c4=Textures.mul(Textures.mul(s.color,150),lm);
                for(int x=x0,o=y*W+x0;x<=x1;x++,o++,mu+=dmu,mv+=dmv){
                    if(mu<0||mv<0||mu>=MS||mv>=MS)continue;
                    int shade=mask[(int)mv*MS+(int)mu];if(shade==0)continue;
                    float zz=zb[o];if(iz-zz>tol||zz-iz>tol)continue;
                    pix[o]=shade==1?c1:shade==2?c2:shade==3?c3:c4;amb[o]=0;}
            }
            return;
        }
        if(Math.abs(nz)<.01f){   // wall splat: each screen column is one distance
            for(int x=x0;x<=x1;x++){float rx=(x+.5f-W*.5f)*inv;float Dx=cs-rx*sn,Dy=sn+rx*cs,den=Dx*nx+Dy*ny;if(den>-1e-5f)continue;
                float t=pd/den;if(t<NEAR)continue;float iz=1f/t,tol=iz*.035f+1e-4f;
                float a=(Dx*t-dx)*ux+(Dy*t-dy)*uy;float mu=(a*ir+SPAN)*MS_SCALE;if(mu<0||mu>=MS)continue;int imu=(int)mu;
                int li=(int)(L+3-t*fadeK);if(li<minL)li=minL;else if(li>31)li=31;int lm=lt[li];
                int c1=Textures.mul(s.color,lm),c2=Textures.mul(Textures.mul(s.color,190),lm),c3=Textures.mul(Textures.mul(s.color,330),lm),c4=Textures.mul(Textures.mul(s.color,150),lm);
                float ry0=(horizon-(y0+.5f))*inv,b=eye+ry0*t-s.z,db=-inv*t;float mv=(b*ir+SPAN)*MS_SCALE,dmv=db*ir*MS_SCALE;
                for(int y=y0,o=y0*W+x;y<=y1;y++,o+=W,mv+=dmv){
                    if(mv<0||mv>=MS)continue;int shade=mask[(int)mv*MS+imu];if(shade==0)continue;
                    float zz=zb[o];if(iz-zz>tol||zz-iz>tol)continue;
                    pix[o]=shade==1?c1:shade==2?c2:shade==3?c3:c4;amb[o]=0;}
            }
            return;
        }
        for(int y=y0;y<=y1;y++){float ry=(horizon-(y+.5f))*inv;
            for(int x=x0;x<=x1;x++){float rx=(x+.5f-W*.5f)*inv;
                float Dx=cs-rx*sn,Dy=sn+rx*cs,den=Dx*nx+Dy*ny+ry*nz;if(den>-1e-5f)continue;
                float t=pd/den;if(t<NEAR)continue;
                int o=y*W+x;float iz=1f/t,zz=zb[o];if(Math.abs(iz-zz)>zz*.035f+1e-4f)continue;   // only on the surface that's actually there
                float lx=Dx*t-dx,ly=Dy*t-dy,lz=eye+ry*t-s.z;
                float a=lx*ux+ly*uy+lz*uz,b=lx*vx2+ly*vy2+lz*vz2;
                int mu=(int)((a*ir+SPAN)*MS_SCALE),mv=(int)((b*ir+SPAN)*MS_SCALE);if(mu<0||mv<0||mu>=MS||mv>=MS)continue;
                int shade=mask[mv*MS+mu];if(shade==0)continue;
                int li=(int)(L+3-t*fadeK);if(li<minL)li=minL;else if(li>31)li=31;
                int c=shade==2?Textures.mul(s.color,190):shade==3?Textures.mul(s.color,330):shade==4?Textures.mul(s.color,150):s.color;
                pix[o]=Textures.mul(c,lt[li]);amb[o]=0;
            }
        }
    }
    /** Flying droplets: little squares, depth-tested against everything already drawn. */
    private void drawDrops(Level level){
        java.util.ArrayList<World.Drop> list=fx.drops;
        for(int i=0,n=list.size();i<n;i++){World.Drop d=list.get(i);
            float dx=d.x-camX,dy=d.y-camY,cx=-dx*sn+dy*cs,cz=dx*cs+dy*sn;if(cz<NEAR*2)continue;
            float sc=focal/cz,X=W*.5f+cx*sc,Y=horizon-(d.z-eye)*sc;int sz=Math.max(1,Math.round(d.size*sc));
            int xa=Math.max(0,(int)(X-sz*.5f)),xb=Math.min(W-1,xa+sz-1),ya=Math.max(0,(int)(Y-sz*.5f)),yb=Math.min(H-1,ya+sz-1);if(xa>xb||ya>yb)continue;
            int si=level.sectorAt(d.x,d.y);int L=si<0?24:light(level.sectors.get(si).light);int li=Math.max(Math.max(1,L/4),Math.min(31,(int)(L+3-cz*fadeK)));
            int c=Textures.mul(d.color,Textures.lightMul(li)),hl=Textures.mul(c,330);float iz=1f/cz;
            for(int y=ya;y<=yb;y++)for(int x=xa;x<=xb;x++){int o=y*W+x;if(iz>zb[o])pix[o]=(sz>1&&x==xa&&y==ya)?hl:c;}
        }
    }
    /** Projects a world point into the destination rectangle used by present(); false if it is behind the camera. */
    boolean project(float x,float y,float z,RectF dst,float[] out){
        float dx=x-camX,dy=y-camY,cx=-dx*sn+dy*cs,cz=dx*cs+dy*sn;if(cz<NEAR*2)return false;float sc=focal/cz;
        out[0]=dst.left+(W*.5f+cx*sc)/W*dst.width();out[1]=dst.top+(horizon-(z-eye)*sc)/H*dst.height();out[2]=sc/H;return true;
    }
    private void tintHighlight(){
        for(int i=0;i<pix.length;i++)if(ids[i]==highlight){int c=pix[i];int r=(c>>16)&255,g=(c>>8)&255,b=c&255;pix[i]=0xff000000|((r*5/8+30)<<16)|((g*5/8+96)<<8)|(b*5/8+60);}
    }
    /** Draws the first-person weapon (and muzzle flash) into the frame. */
    void weapon(int weapon,float bobX,float bobY,float raise,boolean attacking,int light){
        if(pix==null&&gpu==null)return;
        Sprites.Sprite s=Sprites.fpSprite(weapon,attacking);
        // model sprites are sized by their real dimensions: one world unit is ~60% of the screen height
        float sc=H*.62f/s.ppu;int w=Math.round(s.w*sc),h=Math.round(s.h*sc);
        int x0=Math.round(W*.5f-w*.5f+bobX*W*.04f),y0=Math.round(H-h+Math.abs(bobY)*H*.05f+raise*h*.8f+H*.02f);
        int mulv=s.bright||attacking&&weapon==Rpg.FP_STAFF?256:Textures.lightMul(Math.max(10,Math.min(31,light(light)+4)));
        if(gpu!=null){try{gpu.weapon(s,x0,y0,w,h,mulv);}catch(Throwable ignored){}return;}
        blitSprite(s,x0,y0,w,h,mulv);
    }
    private void blitSprite(Sprites.Sprite s,int x0,int y0,int w,int h,int mulv){
        for(int y=Math.max(0,y0);y<Math.min(H,y0+h);y++){int v=(y-y0)*s.h/h;
            for(int x=Math.max(0,x0);x<Math.min(W,x0+w);x++){int u=(x-x0)*s.w/w;int c=s.px[v*s.w+u];int al=c>>>24;if(al==0)continue;int lit=mulv>=256?c:Textures.mul(c,mulv);
                if(al<250){int b=pix[y*W+x],ia=255-al;lit=0xff000000|((((lit>>16)&255)*al+((b>>16)&255)*ia)/255<<16)|((((lit>>8)&255)*al+((b>>8)&255)*ia)/255<<8)|(((lit&255)*al+(b&255)*ia)/255);}
                pix[y*W+x]=lit|0xff000000;}}
    }
    /** Full-screen colour tint for pain / pickup flashes. */
    void tint(int color,float amount){
        if(amount<=0.01f)return;
        if(gpu!=null){try{gpu.tint(color,Math.min(.7f,amount));}catch(Throwable ignored){}return;}
        if(pix==null)return;int a=Math.round(Math.min(.7f,amount)*256),ia=256-a;
        int cr=(color>>16)&255,cg=(color>>8)&255,cb=color&255;
        for(int i=0;i<pix.length;i++){int c=pix[i];pix[i]=0xff000000|((((c>>16)&255)*ia+cr*a)>>8<<16)|((((c>>8)&255)*ia+cg*a)>>8<<8)|(((c&255)*ia+cb*a)>>8);}
    }
    /** Returns the pick id under a view-space point, or -1. */
    int pick(float x,float y,RectF dst){
        if(gpu!=null){if(!dst.contains(x,y)||W==0)return -1;
            try{return gpu.pick(Math.max(0,Math.min(W-1,(int)((x-dst.left)/dst.width()*W))),Math.max(0,Math.min(H-1,(int)((y-dst.top)/dst.height()*H))));}catch(Throwable e){return -1;}}
        if(ids==null||!dst.contains(x,y))return -1;
        int px=Math.max(0,Math.min(W-1,(int)((x-dst.left)/dst.width()*W))),py=Math.max(0,Math.min(H-1,(int)((y-dst.top)/dst.height()*H)));
        return ids[py*W+px];
    }
}
