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
    private float focal,horizon,eye,camX,camY,cs,sn,kx;
    private final Paint blit=new Paint();
    // geometry cache
    private Level geoLevel;private long geoSig;
    ArrayList<Level.LineDef> lines=new ArrayList<>();
    private int[][] tris=new int[0][];
    private float[] vx=new float[0],vz=new float[0];
    private int[] skyU=new int[0];
    // scratch polygon buffers (camera space in, clipped out)
    private final float[][] pa=new float[5][16],pb=new float[5][16];
    private final float[] sx=new float[16],sy=new float[16],siz=new float[16],suz=new float[16],svz=new float[16];
    int highlight=-1;
    float fadeK=1.35f;

    Renderer(){blit.setFilterBitmap(false);blit.setAntiAlias(false);Textures.init();Sprites.init();}

    /** Picks an internal resolution that matches the viewport's shape (about 200 lines tall). */
    private void setup(float vw,float vh){
        int h=200,w=Math.max(160,Math.min(560,Math.round(h*vw/Math.max(1,vh))));
        if(w==W&&h==H&&pix!=null)return;
        W=w;H=h;pix=new int[W*H];zb=new float[W*H];ids=new int[W*H];bmp=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);skyU=new int[W];
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
        setup(vw,vh);geometry(level);
        camX=x;camY=y;eye=eyeZ;cs=(float)Math.cos(angle);sn=(float)Math.sin(angle);
        focal=W*.5f;kx=(W*.5f)/focal*1.04f;
        horizon=H*.5f+(float)Math.tan(Math.max(-.6f,Math.min(.6f,pitch)))*focal;
        // background: sky if any sector uses it, else dark
        boolean sky=false;for(Level.Sector s:level.sectors)if(s.ceilingMaterial==Textures.SKY){sky=true;break;}
        Arrays.fill(zb,0f);Arrays.fill(ids,-1);
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
                if(f.floor!=k.floor)wall(l,len,Math.min(f.floor,k.floor),Math.max(f.floor,k.floor),material(level,l,0,f.wallMaterial),L,id(ID_WALL,li<<1));
                boolean bothSky=f.ceilingMaterial==Textures.SKY&&k.ceilingMaterial==Textures.SKY;
                if(f.ceiling!=k.ceiling&&!bothSky)wall(l,len,Math.min(f.ceiling,k.ceiling),Math.max(f.ceiling,k.ceiling),material(level,l,1,f.wallMaterial),L,id(ID_WALL,(li<<1)|1));
            }
        }
        if(sprites!=null)for(SpriteDraw s:sprites)sprite(level,s);
        if(highlight>=0)tintHighlight();
    }
    private int material(Level level,Level.LineDef l,int part,int fallback){
        if(level.wallOverrides.isEmpty())return fallback;Integer m=level.wallOverrides.get(wallKey(level,l,part));return m==null?fallback:m;
    }
    private static int light(int sectorLight){return Math.max(0,Math.min(31,Math.round(sectorLight/255f*31f)));}
    void present(Canvas out,RectF dst){bmp.setPixels(pix,0,W,0,0,W,H);out.drawBitmap(bmp,null,dst,blit);}

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
    private void wall(Level.LineDef l,float len,float lo,float hi,int mat,int L,int pid){
        if(hi-lo<.001f)return;
        float[][] p=pa;int A=l.from,B=l.to;
        p[0][0]=vx[A];p[1][0]=vz[A];p[2][0]=hi;p[3][0]=0;p[4][0]=-hi*.5f;
        p[0][1]=vx[B];p[1][1]=vz[B];p[2][1]=hi;p[3][1]=len*.5f;p[4][1]=-hi*.5f;
        p[0][2]=vx[B];p[1][2]=vz[B];p[2][2]=lo;p[3][2]=len*.5f;p[4][2]=-lo*.5f;
        p[0][3]=vx[A];p[1][3]=vz[A];p[2][3]=lo;p[3][3]=0;p[4][3]=-lo*.5f;
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
        int[][] table=Textures.shaded[m];boolean bright=Textures.BRIGHT[m];
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
            if(sky){for(;o<=end;o++){if(iz>zb[o]){zb[o]=iz;ids[o]=pid;}iz+=dIzdx;}continue;}
            for(;o<=end;o++){
                if(iz>zb[o]){
                    float z=1f/iz;
                    int tu=((int)Math.floor(uz*z*64f))&63,tv=((int)Math.floor(vz*z*64f))&63;
                    int li;if(bright)li=31;else{li=(int)(L+3-z*fk);if(li<minL)li=minL;else if(li>31)li=31;}
                    pix[o]=table[li][(tv<<6)|tu];zb[o]=iz;ids[o]=pid;
                }
                iz+=dIzdx;uz+=dUdx;vz+=dVdx;
            }
        }
    }
    private void sprite(Level level,SpriteDraw d){
        Sprites.Sprite s=d.spr;if(s==null)return;
        float dx=d.x-camX,dy=d.y-camY,cx=-dx*sn+dy*cs,cz=dx*cs+dy*sn;
        if(cz<NEAR*2)return;
        float scale=focal/cz,ww=s.w/Sprites.PPU*scale,hh=s.h/Sprites.PPU*scale;
        float x0=W*.5f+cx*scale-ww*.5f,top=horizon-(d.z+s.h/Sprites.PPU-eye)*scale;
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
                int c=s.px[v*s.w+u];if((c>>>24)==0)continue;
                pix[o]=mulv>=256?c:Textures.mul(c,mulv);zb[o]=iz;if(d.pickId>=0)ids[o]=d.pickId;
            }
        }
    }
    private void tintHighlight(){
        for(int i=0;i<pix.length;i++)if(ids[i]==highlight){int c=pix[i];int r=(c>>16)&255,g=(c>>8)&255,b=c&255;pix[i]=0xff000000|((r*5/8+30)<<16)|((g*5/8+96)<<8)|(b*5/8+60);}
    }
    /** Draws the first-person weapon (and muzzle flash) into the frame. */
    void weapon(int weapon,float bobX,float bobY,float raise,boolean flash,int light){
        if(pix==null)return;
        Sprites.Sprite s=Sprites.weaponSprite(weapon);
        float sc=H/118f;int w=Math.round(s.w*sc),h=Math.round(s.h*sc);
        int x0=Math.round(W*.5f-w*.5f+bobX*W*.04f),y0=Math.round(H-h+Math.abs(bobY)*H*.05f+raise*h+H*.02f);
        int mulv=Textures.lightMul(Math.max(10,Math.min(31,light(light)+4)));
        if(flash){mulv=256;Sprites.Sprite f=Sprites.flashSprite(weapon);int fw=Math.round(f.w*sc),fh=Math.round(f.h*sc);blitSprite(f,x0+w/2-fw/2,y0-fh*2/3,fw,fh,256);}
        blitSprite(s,x0,y0,w,h,mulv);
    }
    private void blitSprite(Sprites.Sprite s,int x0,int y0,int w,int h,int mulv){
        for(int y=Math.max(0,y0);y<Math.min(H,y0+h);y++){int v=(y-y0)*s.h/h;
            for(int x=Math.max(0,x0);x<Math.min(W,x0+w);x++){int u=(x-x0)*s.w/w;int c=s.px[v*s.w+u];if((c>>>24)==0)continue;pix[y*W+x]=mulv>=256?c:Textures.mul(c,mulv);}}
    }
    /** Full-screen colour tint for pain / pickup flashes. */
    void tint(int color,float amount){
        if(amount<=0.01f||pix==null)return;int a=Math.round(Math.min(.7f,amount)*256),ia=256-a;
        int cr=(color>>16)&255,cg=(color>>8)&255,cb=color&255;
        for(int i=0;i<pix.length;i++){int c=pix[i];pix[i]=0xff000000|((((c>>16)&255)*ia+cr*a)>>8<<16)|((((c>>8)&255)*ia+cg*a)>>8<<8)|(((c&255)*ia+cb*a)>>8);}
    }
    /** Returns the pick id under a view-space point, or -1. */
    int pick(float x,float y,RectF dst){
        if(ids==null||!dst.contains(x,y))return -1;
        int px=Math.max(0,Math.min(W-1,(int)((x-dst.left)/dst.width()*W))),py=Math.max(0,Math.min(H-1,(int)((y-dst.top)/dst.height()*H)));
        return ids[py*W+px];
    }
}
