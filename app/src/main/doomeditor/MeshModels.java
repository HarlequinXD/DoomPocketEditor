package com.retro.doomeditor;

import java.util.ArrayList;

/**
 * Turns the built-in characters and items (SpriteModels: rounded shapes per material) into PS1-style low-poly
 * meshes, about 300 triangles per character, with a texture atlas painted from each material: skin with pores and
 * blotches, woven cloth, scaly hide, brushed and scratched metal, wood grain. Every animation frame becomes one
 * mesh with the same vertex order, so the renderer can blend frames like Quake/PS1 vertex animation.
 *
 * Model space: x = forward, y = right, z = up, 1 unit = 1 grid square (the same as the game world).
 */
final class MeshModels {
    private MeshModels(){}
    static final int TILE=64;

    /** One animation frame: per vertex x,y,z, normal x,y,z, u,v, glow (0/1). */
    static final class Frame {float[] v;int count;}
    /** All frames of one built-in sprite plus its texture atlases (colour, relief = normal+height, material). */
    static final class Mesh {Frame[] frames=new Frame[Sprites.FRAMES];int atlas;int[] color,relief,mat;}

    private static final Mesh[] cache=new Mesh[Sprites.COUNT];private static final boolean[] failed=new boolean[Sprites.COUNT];
    /** The mesh for a built-in sprite id (built on first use, then kept); null if it has none. */
    static Mesh get(int id){
        if(id<0||id>=Sprites.COUNT||failed[id])return null;
        synchronized(cache){if(cache[id]!=null)return cache[id];}
        Mesh m=null;try{m=build(id);}catch(Throwable t){m=null;}
        synchronized(cache){if(m==null){failed[id]=true;return null;}if(cache[id]==null)cache[id]=m;return cache[id];}
    }
    /** Builds the character meshes ahead of time (loading screen), so the first fight doesn't hitch. */
    static void warm(){for(int id:new int[]{Sprites.HERO,Sprites.SKELETON,Sprites.GOBLIN,Sprites.ORC,Sprites.CULTIST,Sprites.VILLAGER,Sprites.HERO_RANGER,Sprites.HERO_ROGUE,Sprites.HERO_WIZARD,Sprites.HERO_CLERIC})get(id);}
    // ------------------------------------------------------------------ build
    static final int BUDGET=500;   // triangles per character (items get 320)
    private static int budget=BUDGET;
    static Mesh build(int id){
        if(BoxMesh.has(id))return BoxMesh.build(id);   // characters: hand box-modelled
        if(PropMesh.has(id))return PropMesh.build(id);   // objects and decorations: hand box-modelled
        Mesh out=new Mesh();budget=id==Sprites.HERO||id==Sprites.SKELETON||id==Sprites.GOBLIN||id==Sprites.ORC||id==Sprites.CULTIST||id==Sprites.VILLAGER||(id>=Sprites.HERO_RANGER&&id<=Sprites.HERO_CLERIC)?500:320;
        SpriteModels.Model first=model(id,0);if(first==null)return null;
        int[] lv=plan(first);
        int n=Math.max(1,first.mats.size());int g=1;while(g*g<n)g++;out.atlas=g;
        for(int f=0;f<Sprites.FRAMES;f++){SpriteModels.Model m=f==0?first:model(id,f);if(m==null)m=first;out.frames[f]=mesh(m,lv,g);}
        paint(out,first,g);
        return out;
    }
    private static SpriteModels.Model model(int id,int frame){
        try{
            switch(id){
                case Sprites.HERO:return SpriteModels.hero(frame);
                case Sprites.SKELETON:return SpriteModels.skeleton(frame);
                case Sprites.CULTIST:return SpriteModels.cultist(frame);
                case Sprites.GOBLIN:return SpriteModels.goblin(frame);
                case Sprites.ORC:return SpriteModels.orc(frame);
                case Sprites.VILLAGER:return SpriteModels.villager(frame);
                case Sprites.HERO_RANGER:case Sprites.HERO_ROGUE:case Sprites.HERO_WIZARD:case Sprites.HERO_CLERIC:return SpriteModels.hero(frame,id-Sprites.HERO_RANGER+1);
                default:return SpriteModels.item(id,frame);
            }
        }catch(Throwable t){return null;}
    }
    static float size(SpriteModels.Prim p){
        switch(p.type){case SpriteModels.ELL:case SpriteModels.BOX:return Math.max(p.h[0],Math.max(p.h[1],p.h[2]));case SpriteModels.TORUS:return p.r+p.r2;
            default:return Math.max(Math.max(p.r,p.r2),dist(p.a,p.b)*.25f);}
    }
    /** Triangles for a primitive at detail level 0..3 (-1 = left out). */
    static int cost(SpriteModels.Prim p,int l){
        if(l<0)return 0;
        switch(p.type){case SpriteModels.ELL:return new int[]{8,16,30,48}[l];case SpriteModels.BOX:return 12;case SpriteModels.TORUS:return new int[]{24,24,36,64}[l];
            default:return 4*(3+l);}
    }
    /**
     * Spends the triangle budget like a PS1 artist: every part starts as the simplest shape (8-sided gem, 3-sided
     * prism, box); the smallest details are dropped if even that is too much; then the biggest, most visible parts
     * get rounder first.
     */
    static int[] plan(SpriteModels.Model m){
        int n=m.prims.size();int[] lv=new int[n];Integer[] ord=new Integer[n];for(int i=0;i<n;i++)ord[i]=i;
        java.util.Arrays.sort(ord,(a,b)->Float.compare(size(m.prims.get(a)),size(m.prims.get(b))));
        int total=0;for(int i=0;i<n;i++)total+=cost(m.prims.get(i),0);
        for(int k=0;k<n&&total>budget*.85f;k++){int i=ord[k];SpriteModels.Prim p=m.prims.get(i);if(size(p)>=.05f)break;total-=cost(p,0);lv[i]=-1;}
        for(int level=1;level<=3;level++)for(int k=n-1;k>=0;k--){int i=ord[k];if(lv[i]!=level-1)continue;SpriteModels.Prim p=m.prims.get(i);
            int d=cost(p,level)-cost(p,level-1);if(total+d<=budget){total+=d;lv[i]=level;}}
        return lv;
    }

    // ------------------------------------------------------------------ tessellation
    private static float[] buf;private static int nv;
    private static Frame mesh(SpriteModels.Model m,int[] lv,int g){
        buf=new float[9*3*400];nv=0;
        for(int pi=0;pi<m.prims.size();pi++){SpriteModels.Prim p=m.prims.get(pi);int l=pi<lv.length?lv[pi]:0;if(l<0)continue;
            int mi=Math.max(0,Math.min(m.mats.size()-1,p.mat));SpriteModels.Mat mat=m.mats.isEmpty()?null:m.mats.get(mi);
            float glow=mat!=null&&mat.glow?1:0;int tx=mi%g,ty=mi/g;
            switch(p.type){
                case SpriteModels.ELL:if(l==0)octa(p,glow,tx,ty,g);else ellipsoid(p,new int[]{0,4,5,6}[l],new int[]{0,3,4,5}[l],glow,tx,ty,g);break;
                case SpriteModels.RCONE:tube(p,3+l,p.r,p.r2,l>0,glow,tx,ty,g);break;
                case SpriteModels.CYL:tube(p,3+l,p.r,p.r,false,glow,tx,ty,g);break;
                case SpriteModels.BOX:box(p,glow,tx,ty,g);break;
                case SpriteModels.TORUS:torus(p,l>=3?8:l==2?6:4,l>=3?4:3,glow,tx,ty,g);break;
            }
        }
        Frame f=new Frame();f.v=java.util.Arrays.copyOf(buf,nv*9);f.count=nv;return f;
    }
    private static void put(float x,float y,float z,float nx,float ny,float nz,float u,float v,float glow,int tx,int ty,int g){
        if((nv+1)*9>buf.length)buf=java.util.Arrays.copyOf(buf,buf.length*2);
        float l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(l<1e-6f){nx=0;ny=0;nz=1;l=1;}
        // atlas: keep a 2-texel border inside each tile so nearest sampling never bleeds into the next material
        float in=2f/TILE;u=in+(u-(float)Math.floor(u))*(1-2*in);v=in+Math.max(0,Math.min(1,v))*(1-2*in);
        int o=nv*9;buf[o]=x;buf[o+1]=y;buf[o+2]=z;buf[o+3]=nx/l;buf[o+4]=ny/l;buf[o+5]=nz/l;buf[o+6]=(tx+u)/g;buf[o+7]=(ty+v)/g;buf[o+8]=glow;nv++;
    }
    private static void tri(float[] a,float[] b,float[] c,float glow,int tx,int ty,int g){
        put(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],glow,tx,ty,g);put(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],glow,tx,ty,g);put(c[0],c[1],c[2],c[3],c[4],c[5],c[6],c[7],glow,tx,ty,g);
    }
    private static float[] P(float x,float y,float z,float nx,float ny,float nz,float u,float v){return new float[]{x,y,z,nx,ny,nz,u,v};}
    private static void ellipsoid(SpriteModels.Prim p,int A,int Rn,float glow,int tx,int ty,int g){
        float[][] grid=new float[(A+1)*(Rn+1)][];
        for(int r=0;r<=Rn;r++)for(int a=0;a<=A;a++){
            double th=Math.PI*r/Rn,ph=2*Math.PI*a/A;float sx=(float)(Math.sin(th)*Math.cos(ph)),sy=(float)(Math.sin(th)*Math.sin(ph)),sz=(float)Math.cos(th);
            float[] u=p.ax[0],v=p.ax[1],w=p.ax[2];float hx=p.h[0],hy=p.h[1],hz=p.h[2];
            float x=p.c[0]+u[0]*sx*hx+v[0]*sy*hy+w[0]*sz*hz,y=p.c[1]+u[1]*sx*hx+v[1]*sy*hy+w[1]*sz*hz,z=p.c[2]+u[2]*sx*hx+v[2]*sy*hy+w[2]*sz*hz;
            float nx=u[0]*sx/hx+v[0]*sy/hy+w[0]*sz/hz,ny=u[1]*sx/hx+v[1]*sy/hy+w[1]*sz/hz,nz=u[2]*sx/hx+v[2]*sy/hy+w[2]*sz/hz;
            grid[r*(A+1)+a]=P(x,y,z,nx,ny,nz,(float)a/A,(float)r/Rn);}
        for(int r=0;r<Rn;r++)for(int a=0;a<A;a++){float[] p00=grid[r*(A+1)+a],p01=grid[r*(A+1)+a+1],p10=grid[(r+1)*(A+1)+a],p11=grid[(r+1)*(A+1)+a+1];
            if(r>0)tri(p00,p10,p01,glow,tx,ty,g);if(r<Rn-1)tri(p01,p10,p11,glow,tx,ty,g);}
    }
    private static void tube(SpriteModels.Prim p,int A,float r1,float r2,boolean round,float glow,int tx,int ty,int g){
        float[] d=sub(p.b,p.a);float L=len(d);if(L<1e-5f){d=new float[]{0,0,1};L=1e-5f;}
        float[][] f=SpriteModels.frame(d);float[] ax=f[0],e1=f[1],e2=f[2];
        float[][] ra=new float[A+1][],rb=new float[A+1][];
        float slope=(r1-r2)/L;
        for(int a=0;a<=A;a++){double ph=2*Math.PI*a/A;float c=(float)Math.cos(ph),s=(float)Math.sin(ph);
            float nx=e1[0]*c+e2[0]*s,ny=e1[1]*c+e2[1]*s,nz=e1[2]*c+e2[2]*s;float nnx=nx+ax[0]*slope,nny=ny+ax[1]*slope,nnz=nz+ax[2]*slope;
            ra[a]=P(p.a[0]+nx*r1,p.a[1]+ny*r1,p.a[2]+nz*r1,nnx,nny,nnz,(float)a/A,.15f);
            rb[a]=P(p.b[0]+nx*r2,p.b[1]+ny*r2,p.b[2]+nz*r2,nnx,nny,nnz,(float)a/A,.85f);}
        for(int a=0;a<A;a++){tri(ra[a],rb[a],ra[a+1],glow,tx,ty,g);tri(ra[a+1],rb[a],rb[a+1],glow,tx,ty,g);}
        float ka=round?r1*.85f:0,kb=round?r2*.85f:0;
        float[] capA=P(p.a[0]-ax[0]*ka,p.a[1]-ax[1]*ka,p.a[2]-ax[2]*ka,-ax[0],-ax[1],-ax[2],.5f,0),capB=P(p.b[0]+ax[0]*kb,p.b[1]+ax[1]*kb,p.b[2]+ax[2]*kb,ax[0],ax[1],ax[2],.5f,1);
        for(int a=0;a<A;a++){float[] x0=ra[a].clone(),x1=ra[a+1].clone(),y0=rb[a].clone(),y1=rb[a+1].clone();
            if(!round){for(float[] q:new float[][]{x0,x1}){q[3]=-ax[0];q[4]=-ax[1];q[5]=-ax[2];}for(float[] q:new float[][]{y0,y1}){q[3]=ax[0];q[4]=ax[1];q[5]=ax[2];}}
            tri(capA,x1,x0,glow,tx,ty,g);tri(capB,y0,y1,glow,tx,ty,g);}
    }
    private static void box(SpriteModels.Prim p,float glow,int tx,int ty,int g){
        float[] u=p.ax[0],v=p.ax[1],w=p.ax[2];float hx=p.h[0],hy=p.h[1],hz=p.h[2];
        int[][] faces={{0,1},{0,-1},{1,1},{1,-1},{2,1},{2,-1}};
        float[][] axes={u,v,w};float[] hs={hx,hy,hz};
        for(int[] fc:faces){int i=fc[0];int s=fc[1];float[] n=axes[i];float[] a1=axes[(i+1)%3],a2=axes[(i+2)%3];float h1=hs[(i+1)%3],h2=hs[(i+2)%3];
            float[][] q=new float[4][];int k=0;
            for(int[] cc:new int[][]{{-1,-1},{1,-1},{1,1},{-1,1}}){float x=p.c[0]+n[0]*hs[i]*s+a1[0]*h1*cc[0]+a2[0]*h2*cc[1],y=p.c[1]+n[1]*hs[i]*s+a1[1]*h1*cc[0]+a2[1]*h2*cc[1],z=p.c[2]+n[2]*hs[i]*s+a1[2]*h1*cc[0]+a2[2]*h2*cc[1];
                q[k++]=P(x,y,z,n[0]*s,n[1]*s,n[2]*s,(cc[0]+1)*.5f,(cc[1]+1)*.5f);}
            tri(q[0],q[1],q[2],glow,tx,ty,g);tri(q[0],q[2],q[3],glow,tx,ty,g);}
    }
    private static void torus(SpriteModels.Prim p,int M,int m,float glow,int tx,int ty,int g){
        float[] u=p.ax[0],v=p.ax[1],w=p.ax[2];float[][] grid=new float[(M+1)*(m+1)][];
        for(int i=0;i<=M;i++)for(int j=0;j<=m;j++){double a=2*Math.PI*i/M,b=2*Math.PI*j/m;float ca=(float)Math.cos(a),sa=(float)Math.sin(a),cb=(float)Math.cos(b),sb=(float)Math.sin(b);
            float rx=ca*(p.r+p.r2*cb),ry=sa*(p.r+p.r2*cb),rz=p.r2*sb;float nx=ca*cb,ny=sa*cb,nz=sb;
            grid[i*(m+1)+j]=P(p.c[0]+u[0]*rx+v[0]*ry+w[0]*rz,p.c[1]+u[1]*rx+v[1]*ry+w[1]*rz,p.c[2]+u[2]*rx+v[2]*ry+w[2]*rz,u[0]*nx+v[0]*ny+w[0]*nz,u[1]*nx+v[1]*ny+w[1]*nz,u[2]*nx+v[2]*ny+w[2]*nz,(float)i/M,(float)j/m);}
        for(int i=0;i<M;i++)for(int j=0;j<m;j++){float[] a=grid[i*(m+1)+j],b=grid[(i+1)*(m+1)+j],c=grid[i*(m+1)+j+1],d=grid[(i+1)*(m+1)+j+1];tri(a,b,c,glow,tx,ty,g);tri(c,b,d,glow,tx,ty,g);}
    }
    private static void octa(SpriteModels.Prim p,float glow,int tx,int ty,int g){
        float r;float[] c=p.c;switch(p.type){case SpriteModels.ELL:r=Math.max(p.h[0],Math.max(p.h[1],p.h[2]));break;case SpriteModels.BOX:r=Math.max(p.h[0],Math.max(p.h[1],p.h[2]));break;default:r=Math.max(p.r,Math.max(p.r2,dist(p.a,p.b)*.5f));}
        float[][] d={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};int[][] f={{0,2,4},{2,1,4},{1,3,4},{3,0,4},{2,0,5},{1,2,5},{3,1,5},{0,3,5}};
        boolean axes=p.type==SpriteModels.ELL;
        for(int[] t:f){float[][] q=new float[3][];for(int k=0;k<3;k++){float[] e=d[t[k]];float x,y,z,nx,ny,nz;
            if(axes){float[] u=p.ax[0],v=p.ax[1],w=p.ax[2];float ex=e[0]*p.h[0],ey=e[1]*p.h[1],ez=e[2]*p.h[2];x=c[0]+u[0]*ex+v[0]*ey+w[0]*ez;y=c[1]+u[1]*ex+v[1]*ey+w[1]*ez;z=c[2]+u[2]*ex+v[2]*ey+w[2]*ez;
                nx=u[0]*e[0]+v[0]*e[1]+w[0]*e[2];ny=u[1]*e[0]+v[1]*e[1]+w[1]*e[2];nz=u[2]*e[0]+v[2]*e[1]+w[2]*e[2];}
            else{x=c[0]+e[0]*r;y=c[1]+e[1]*r;z=c[2]+e[2]*r;nx=e[0];ny=e[1];nz=e[2];}
            q[k]=P(x,y,z,nx,ny,nz,.5f+e[1]*.45f,.5f-e[2]*.45f);}tri(q[0],q[1],q[2],glow,tx,ty,g);}
    }
    private static float[] sub(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float len(float[] a){return (float)Math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]);}
    private static float dist(float[] a,float[] b){return len(sub(a,b));}

    // ------------------------------------------------------------------ textures: "photographic" detail painted per material
    private static float hash(int x,int y,int s){int h=x*374761393+y*668265263+s*1274126177;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0xffffff)/(float)0xffffff;}
    private static float vn(float x,float y,int s){int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float xf=x-xi,yf=y-yi,u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf);
        int P=TILE;   // tiles wrap so seams around a limb don't show
        float a=hash(Math.floorMod(xi,P),Math.floorMod(yi,P),s),b=hash(Math.floorMod(xi+1,P),Math.floorMod(yi,P),s),c=hash(Math.floorMod(xi,P),Math.floorMod(yi+1,P),s),d=hash(Math.floorMod(xi+1,P),Math.floorMod(yi+1,P),s);
        return a+(b-a)*u+(c-a)*v+(a-b-c+d)*u*v;}
    private static float fbm(float x,float y,int s,int oct,int period){float t=0,a=.5f,f=1;for(int i=0;i<oct;i++){t+=a*vnw(x*f,y*f,s+i*31,(int)(period*f));f*=2;a*=.5f;}return t;}
    private static float vnw(float x,float y,int s,int P){int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float xf=x-xi,yf=y-yi,u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf);P=Math.max(1,P);
        float a=hash(Math.floorMod(xi,P),Math.floorMod(yi,P),s),b=hash(Math.floorMod(xi+1,P),Math.floorMod(yi,P),s),c=hash(Math.floorMod(xi,P),Math.floorMod(yi+1,P),s),d=hash(Math.floorMod(xi+1,P),Math.floorMod(yi+1,P),s);
        return a+(b-a)*u+(c-a)*v+(a-b-c+d)*u*v;}
    /** Cellular pattern (distance to the nearest of a few feature points), wrapping per tile: scales, cracks, pebbles. */
    private static float cell(int x,int y,int cells,int s){float cs=TILE/(float)cells;int cx=(int)(x/cs),cy=(int)(y/cs);float best=9,second=9;
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){int gx=cx+i,gy=cy+j;float fx=(gx+hash(Math.floorMod(gx,cells),Math.floorMod(gy,cells),s))*cs,fy=(gy+hash(Math.floorMod(gx,cells),Math.floorMod(gy,cells),s+7))*cs;
            float d=(float)Math.hypot(x-fx,y-fy)/cs;if(d<best){second=best;best=d;}else if(d<second)second=d;}
        return second-best;}
    static void paint(Mesh out,SpriteModels.Model m,int g){
        int S=g*TILE;int[] col=new int[S*S],mat=new int[S*S];float[] H=new float[S*S];
        for(int mi=0;mi<Math.max(1,m.mats.size());mi++){SpriteModels.Mat mt=m.mats.isEmpty()?null:m.mats.get(mi);int ox=(mi%g)*TILE,oy=(mi/g)*TILE;
            float br=mt==null?.6f:(float)Math.pow(mt.alb[0],1/2.2),bg=mt==null?.6f:(float)Math.pow(mt.alb[1],1/2.2),bb=mt==null?.6f:(float)Math.pow(mt.alb[2],1/2.2);
            int kind=mt==null?0:mt.kind;boolean metal=mt!=null&&(mt.metal>.3f||kind==SpriteModels.K_METAL),glow=mt!=null&&mt.glow;int s=mi*101+7;
            for(int y=0;y<TILE;y++)for(int x=0;x<TILE;x++){
                float shade,h,smooth;
                if(glow){shade=1.05f+fbm(x/8f,y/8f,s,3,8)*.25f;h=.5f;smooth=.3f;}
                else switch(kind){
                    case SpriteModels.K_SKIN:{float blot=fbm(x/10f,y/10f,s,4,6),pore=hash(x,y,s)>.86f?-.07f:0;shade=.88f+blot*.22f+pore;h=.55f+blot*.3f+pore*2;smooth=.35f+blot*.1f;
                        if(fbm(x/6f,y/6f,s+3,2,10)>.62f){shade*=1.0f;}break;}
                    case SpriteModels.K_CLOTH:{boolean warp=((x>>1)+(y>>1))%2==0;float fib=hash(x,y,s)*.12f,wr=fbm(x/14f,y/5f,s,3,4)*.35f;shade=.78f+(warp?.1f:-.02f)+fib+wr*.4f-(x%2==0&&y%4==0?.08f:0);h=(warp?.6f:.35f)+wr*.4f;smooth=.12f;break;}
                    case SpriteModels.K_HIDE:{float c=cell(x,y,6,s),n=fbm(x/5f,y/5f,s,3,12);shade=.7f+Math.min(1,c*2.2f)*.35f+n*.15f;h=Math.min(1,c*2.5f)*.8f+n*.2f;smooth=.25f;break;}
                    case SpriteModels.K_METAL:{float br2=fbm(x/40f,y/2f,s,3,1)*.3f,scr=(hash(x/2,y,s)>.985f)?.25f:0,dirt=Math.max(0,fbm(x/9f,y/9f,s+5,4,6)-.55f)*1.4f;shade=.82f+br2+scr-dirt;h=.6f-dirt*.6f+scr*.4f;smooth=Math.max(.15f,.75f-dirt);break;}
                    case SpriteModels.K_WOOD:{float ring=(float)Math.sin(x*.55f+fbm(x/16f,y/32f,s,3,2)*7)*.5f+.5f,kn=Math.max(0,.18f-cell(x,y,3,s))*3;shade=.72f+ring*.32f-kn*.4f+hash(x,y,s)*.06f;h=.4f+ring*.4f-kn;smooth=.35f;break;}
                    default:{float n=fbm(x/8f,y/8f,s,4,8),grime=Math.max(0,fbm(x/12f,y/12f,s+9,3,5)-.5f);shade=.82f+n*.3f-grime*.5f+hash(x,y,s)*.06f;h=.4f+n*.5f-grime;smooth=metal?.6f:.3f;}
                }
                int o=(oy+y)*S+ox+x;
                col[o]=0xff000000|(c255(br*shade)<<16)|(c255(bg*shade)<<8)|c255(bb*shade);
                H[o]=Math.max(0,Math.min(1,h));
                mat[o]=0xff000000|(c255(smooth)<<16)|(c255(metal?.85f:0)<<8)|c255(1f-Math.max(0,.55f-h)*.6f);
            }
        }
        out.color=col;   // PS1: colour texture only
    }
    /**
     * Draws one frame of a mesh into a sprite (for thumbnails, the sprite editor and the software renderer): seen from
     * {@code yaw} and looking down by {@code elev}, lit from the upper left, nearest-texel colour like the GPU path.
     * Centred horizontally, bottom row on the floor.
     */
    static Sprites.Sprite raster(Mesh m,int frame,float yaw,float elev,float ppu,boolean bright){
        Frame fr=m.frames[Math.max(0,Math.min(m.frames.length-1,frame))];if(fr==null)fr=m.frames[0];
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw),ce=(float)Math.cos(elev),se=(float)Math.sin(elev);
        float[] dH={-cy,-sy,0},u={sy,-cy,0},w={dH[0]*ce,dH[1]*ce,-se},up={dH[0]*se,dH[1]*se,ce};
        float lx=-.55f*u[0]+.78f*up[0]-.42f*w[0],ly=-.55f*u[1]+.78f*up[1]-.42f*w[1],lz=-.55f*u[2]+.78f*up[2]-.42f*w[2];float ll=(float)Math.sqrt(lx*lx+ly*ly+lz*lz);lx/=ll;ly/=ll;lz/=ll;
        int n=fr.count;float[] X=new float[n],Y=new float[n],Z=new float[n];float half=0,top=-1e9f,bot=1e9f;
        for(int i=0;i<n;i++){int o=i*9;float px=fr.v[o],py=fr.v[o+1],pz=fr.v[o+2];
            X[i]=px*u[0]+py*u[1]+pz*u[2];Y[i]=px*up[0]+py*up[1]+pz*up[2];Z[i]=px*w[0]+py*w[1]+pz*w[2];
            half=Math.max(half,Math.abs(X[i]));top=Math.max(top,Y[i]);bot=Math.min(bot,Y[i]);}
        int W=Math.max(2,(int)Math.ceil(half*ppu)*2+2),H=Math.max(2,(int)Math.ceil((top-bot)*ppu)+2);
        Sprites.Sprite sp=new Sprites.Sprite(W,H,bright);sp.ppu=ppu;float[] zb=new float[W*H];java.util.Arrays.fill(zb,1e9f);
        int S=m.atlas*TILE;
        for(int t=0;t+2<n;t+=3){
            float[] sxv=new float[3],syv=new float[3];
            for(int k=0;k<3;k++){sxv[k]=W*.5f+X[t+k]*ppu;syv[k]=H-1-(Y[t+k]-bot)*ppu;}
            float area=(sxv[1]-sxv[0])*(syv[2]-syv[0])-(sxv[2]-sxv[0])*(syv[1]-syv[0]);if(Math.abs(area)<1e-6f)continue;
            int o=t*9;float nx=fr.v[o+3]+fr.v[o+12]+fr.v[o+21],ny=fr.v[o+4]+fr.v[o+13]+fr.v[o+22],nz=fr.v[o+5]+fr.v[o+14]+fr.v[o+23];
            float nl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(nl>0){nx/=nl;ny/=nl;nz/=nl;}
            if(nx*w[0]+ny*w[1]+nz*w[2]>0){nx=-nx;ny=-ny;nz=-nz;}
            boolean glow=fr.v[o+8]>.5f;float shade=glow?1.1f:.42f+.68f*Math.max(0,nx*lx+ny*ly+nz*lz);
            int x0=Math.max(0,(int)Math.floor(Math.min(sxv[0],Math.min(sxv[1],sxv[2])))),x1=Math.min(W-1,(int)Math.ceil(Math.max(sxv[0],Math.max(sxv[1],sxv[2]))));
            int y0=Math.max(0,(int)Math.floor(Math.min(syv[0],Math.min(syv[1],syv[2])))),y1=Math.min(H-1,(int)Math.ceil(Math.max(syv[0],Math.max(syv[1],syv[2]))));
            for(int py=y0;py<=y1;py++)for(int px=x0;px<=x1;px++){float qx=px+.5f,qy=py+.5f;
                float b0=((sxv[1]-qx)*(syv[2]-qy)-(sxv[2]-qx)*(syv[1]-qy))/area,b1=((sxv[2]-qx)*(syv[0]-qy)-(sxv[0]-qx)*(syv[2]-qy))/area,b2=1-b0-b1;
                if(b0<-1e-4f||b1<-1e-4f||b2<-1e-4f)continue;
                float z=b0*Z[t]+b1*Z[t+1]+b2*Z[t+2];int pi=py*W+px;if(z>=zb[pi])continue;zb[pi]=z;
                float tu=b0*fr.v[o+6]+b1*fr.v[o+15]+b2*fr.v[o+24],tv=b0*fr.v[o+7]+b1*fr.v[o+16]+b2*fr.v[o+25];
                int c=m.color[Math.min(S-1,Math.max(0,(int)(tv*S)))*S+Math.min(S-1,Math.max(0,(int)(tu*S)))];
                sp.px[pi]=0xff000000|(c255(((c>>16)&255)/255f*shade)<<16)|(c255(((c>>8)&255)/255f*shade)<<8)|c255((c&255)/255f*shade);}
        }
        return sp;
    }
    private static int c255(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
}
