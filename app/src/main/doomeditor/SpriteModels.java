package com.retro.doomeditor;

import java.util.ArrayList;

/**
 * Built-in sprites are pre-rendered from procedural 3D models, the way 90s games pre-rendered CGI sprites.
 *
 * Models are signed distance fields: tapered limbs, ellipsoids, rounded boxes, cylinders and tori that blend
 * smoothly into one another inside a group (so a body reads as one sculpted surface), with hard edges between
 * groups (gear, weapons). Each sprite is ray-marched with physically-inspired lighting: warm key light with soft
 * shadows, sky/ground ambient with ambient occlusion, skin subsurface scattering, specular metals and plastics,
 * procedural fabric / hide / skin surface detail, a cool rim light, ACES tone mapping and anti-aliased edges.
 *
 * Model space: x = forward (the way the entity faces), y = its right, z = up, 1 unit = 1 grid square.
 */
final class SpriteModels {
    private SpriteModels(){}

    // ------------------------------------------------------------------ materials
    static final class Mat {
        final float[] alb=new float[3];   // linear albedo
        float spec,shin=30,sss,nScale=34,nAmp,bump,metal;boolean glow;final float[] emit=new float[3];int kind;
    }
    static final int K_PLAIN=0,K_CLOTH=1,K_SKIN=2,K_HIDE=3,K_METAL=4,K_WOOD=5;
    private static void lin(float[] d,int c){d[0]=(float)Math.pow(((c>>16)&255)/255f,2.2);d[1]=(float)Math.pow(((c>>8)&255)/255f,2.2);d[2]=(float)Math.pow((c&255)/255f,2.2);}

    // ------------------------------------------------------------------ primitives
    static final int ELL=0,CAP=1,BOX=2,CYL=3,RCONE=4,TORUS=5;
    static final class Prim {
        int type,mat,group;float k;               // smooth-blend radius inside its group
        final float[] c=new float[3],a=new float[3],b=new float[3];
        final float[][] ax=new float[3][3];final float[] h=new float[3];
        float r,r2,round;
        float bx,by,bz,br;                         // screen-space bound (per render)
    }
    static final int G_BODY=0,G_GEAR=1,G_FX=2,GROUPS=4;
    /** A posed model: primitives plus their materials. */
    static final class Model {
        final ArrayList<Prim> prims=new ArrayList<>();
        final ArrayList<Mat> mats=new ArrayList<>();
        int group=G_GEAR;float blend=0;
        /** Following primitives belong to {@code g} and melt into each other over {@code k} units. */
        Model in(int g,float k){group=g;blend=k;return this;}
        private Prim add(Prim p){p.group=group;p.k=blend;prims.add(p);return p;}
        private Mat newMat(int base){Mat m=new Mat();lin(m.alb,base);mats.add(m);return m;}
        /** Generic material (kept for item models): spec 0..1, grit = surface noise. */
        int mat(int base,float spec,float grit){Mat m=newMat(base);m.spec=spec*.55f;m.shin=16+spec*70;m.nAmp=grit*.9f;m.bump=grit*.5f;m.metal=spec>.6f?.5f:0;m.kind=spec>.6f?K_METAL:K_PLAIN;return mats.size()-1;}
        int cloth(int base){Mat m=newMat(base);m.spec=.04f;m.shin=8;m.nAmp=.14f;m.bump=.35f;m.nScale=60;m.kind=K_CLOTH;return mats.size()-1;}
        int leather(int base){Mat m=newMat(base);m.spec=.22f;m.shin=22;m.nAmp=.08f;m.bump=.2f;m.nScale=50;return mats.size()-1;}
        int plastic(int base,float spec){Mat m=newMat(base);m.spec=spec;m.shin=40;m.nAmp=.04f;m.bump=.05f;return mats.size()-1;}
        int metal(int base){Mat m=newMat(base);m.spec=.75f;m.shin=60;m.nAmp=.05f;m.bump=.04f;m.metal=.8f;m.kind=K_METAL;return mats.size()-1;}
        int wood(int base){Mat m=newMat(base);m.spec=.15f;m.shin=20;m.nAmp=.22f;m.bump=.1f;m.nScale=90;m.kind=K_WOOD;return mats.size()-1;}
        int skin(int base,float sss){Mat m=newMat(base);m.spec=.12f;m.shin=18;m.sss=sss;m.nAmp=.07f;m.bump=.12f;m.nScale=70;m.kind=K_SKIN;return mats.size()-1;}
        int hide(int base,float rough){Mat m=newMat(base);m.spec=.2f;m.shin=14;m.sss=.35f;m.nAmp=.22f*rough;m.bump=.6f*rough;m.nScale=38;m.kind=K_HIDE;return mats.size()-1;}
        int glass(int base){Mat m=newMat(base);m.spec=1.4f;m.shin=90;m.nAmp=0;return mats.size()-1;}
        int glow(int base){Mat m=newMat(base);m.glow=true;lin(m.emit,base);for(int i=0;i<3;i++)m.emit[i]*=1.6f;return mats.size()-1;}

        Prim ell(float[] c,float rx,float ry,float rz,int m){return ellAxes(c,X,Y,Z,rx,ry,rz,m);}
        /** Ellipsoid whose local x axis points along {@code dir}. */
        Prim ellDir(float[] c,float[] dir,float rx,float ry,float rz,int m){float[][] f=frame(dir);return ellAxes(c,f[0],f[1],f[2],rx,ry,rz,m);}
        Prim ellAxes(float[] c,float[] u,float[] v,float[] w,float rx,float ry,float rz,int m){
            Prim p=new Prim();p.type=ELL;p.mat=m;set(p.c,c);set(p.ax[0],u);set(p.ax[1],v);set(p.ax[2],w);p.h[0]=rx;p.h[1]=ry;p.h[2]=rz;return add(p);}
        Prim cap(float[] a,float[] b,float r,int m){return cone(a,b,r,r,m);}
        /** Tapered limb: radius r1 at a, r2 at b. */
        Prim cone(float[] a,float[] b,float r1,float r2,int m){Prim p=new Prim();p.type=RCONE;p.mat=m;set(p.a,a);set(p.b,b);p.r=r1;p.r2=r2;mid(p.c,a,b);return add(p);}
        Prim cyl(float[] a,float[] b,float r,int m){Prim p=new Prim();p.type=CYL;p.mat=m;set(p.a,a);set(p.b,b);p.r=r;mid(p.c,a,b);return add(p);}
        /** Rounded box whose local x axis points along {@code dir}. */
        Prim box(float[] c,float[] dir,float hx,float hy,float hz,int m){return rbox(c,dir,hx,hy,hz,Math.min(.012f,Math.min(hx,Math.min(hy,hz))*.35f),m);}
        Prim box(float[] c,float hx,float hy,float hz,int m){return box(c,X,hx,hy,hz,m);}
        Prim rbox(float[] c,float[] dir,float hx,float hy,float hz,float round,int m){float[][] f=frame(dir);Prim p=new Prim();p.type=BOX;p.mat=m;set(p.c,c);
            set(p.ax[0],f[0]);set(p.ax[1],f[1]);set(p.ax[2],f[2]);p.h[0]=hx;p.h[1]=hy;p.h[2]=hz;p.round=round;return add(p);}
        /** Ring around the local z axis (axis given by {@code up}). */
        Prim torus(float[] c,float[] up,float R,float r,int m){float[][] f=frame(up);Prim p=new Prim();p.type=TORUS;p.mat=m;set(p.c,c);
            // local z = up
            set(p.ax[0],f[1]);set(p.ax[1],f[2]);set(p.ax[2],f[0]);p.r=R;p.r2=r;return add(p);}
        /** Uniformly scales the whole model about the origin (feet), e.g. small goblins and big orcs. */
        void scale(float f){for(Prim p:prims){for(int i=0;i<3;i++){p.c[i]*=f;p.a[i]*=f;p.b[i]*=f;p.h[i]*=f;}p.r*=f;p.r2*=f;p.round*=f;p.k*=f;}}
        /** Rigidly rotates everything about the y axis through {@code pivot}, then moves it (used for corpses). */
        void rotateY(float[] pivot,float ang,float[] move){
            float cs=(float)Math.cos(ang),sn=(float)Math.sin(ang);
            for(Prim p:prims){rotY(p.c,pivot,cs,sn,move);rotY(p.a,pivot,cs,sn,move);rotY(p.b,pivot,cs,sn,move);
                for(float[] v:p.ax)rotY(v,null,cs,sn,null);}
        }
    }
    static final float[] X={1,0,0},Y={0,1,0},Z={0,0,1};
    private static void rotY(float[] p,float[] piv,float cs,float sn,float[] move){
        float px=piv==null?0:piv[0],pz=piv==null?0:piv[2];float x=p[0]-px,z=p[2]-pz;
        p[0]=px+x*cs+z*sn;p[2]=pz-x*sn+z*cs;if(move!=null){p[0]+=move[0];p[1]+=move[1];p[2]+=move[2];}
    }
    static float[] v(float x,float y,float z){return new float[]{x,y,z};}
    static float[] add(float[] a,float[] b){return v(a[0]+b[0],a[1]+b[1],a[2]+b[2]);}
    static float[] add(float[] a,float[] b,float s){return v(a[0]+b[0]*s,a[1]+b[1]*s,a[2]+b[2]*s);}
    static float[] sub(float[] a,float[] b){return v(a[0]-b[0],a[1]-b[1],a[2]-b[2]);}
    static float[] mul(float[] a,float s){return v(a[0]*s,a[1]*s,a[2]*s);}
    static float[] lerp(float[] a,float[] b,float t){return v(a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t);}
    static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static float[] cross(float[] a,float[] b){return v(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]);}
    static float len(float[] a){return (float)Math.sqrt(dot(a,a));}
    static float[] norm(float[] a){float l=len(a);return l<1e-9f?v(0,0,1):mul(a,1/l);}
    private static void set(float[] d,float[] s){d[0]=s[0];d[1]=s[1];d[2]=s[2];}
    private static void mid(float[] d,float[] a,float[] b){d[0]=(a[0]+b[0])*.5f;d[1]=(a[1]+b[1])*.5f;d[2]=(a[2]+b[2])*.5f;}
    /** Orthonormal frame with x along dir and z as close to up as possible. */
    static float[][] frame(float[] dir){
        float[] x=norm(dir);float[] hint=Math.abs(x[2])>.95f?X:Z;
        float[] y=norm(cross(hint,x));float[] z=cross(x,y);return new float[][]{x,y,z};
    }
    /** Direction from a pitch (0 = straight down, + = forward) and a sideways splay (+ = toward +y). */
    static float[] limbDir(float pitch,float splay){return norm(v((float)Math.sin(pitch),(float)Math.sin(splay),-(float)Math.cos(pitch)));}
    /** Two-bone IK: elbow/knee position reaching from {@code s} toward {@code t}, bending toward {@code pole}. */
    static float[] ik(float[] s,float[] t,float l1,float l2,float[] pole){
        float[] d=sub(t,s);float dl=Math.max(1e-4f,Math.min(l1+l2-1e-4f,len(d)));float[] dir=norm(d);
        float a=(l1*l1-l2*l2+dl*dl)/(2*dl),hh=(float)Math.sqrt(Math.max(0,l1*l1-a*a));
        float[] p=sub(pole,mul(dir,dot(pole,dir)));p=norm(p);
        return add(add(s,mul(dir,a)),mul(p,hh));
    }

    // ------------------------------------------------------------------ distance fields
    private static float sdf(Prim p,float x,float y,float z){
        switch(p.type){
            case ELL:{float dx=x-p.c[0],dy=y-p.c[1],dz=z-p.c[2];float[] u=p.ax[0],v=p.ax[1],w=p.ax[2];
                float lx=(dx*u[0]+dy*u[1]+dz*u[2]),ly=(dx*v[0]+dy*v[1]+dz*v[2]),lz=(dx*w[0]+dy*w[1]+dz*w[2]);
                float ax=lx/p.h[0],ay=ly/p.h[1],az=lz/p.h[2];float k0=(float)Math.sqrt(ax*ax+ay*ay+az*az);
                float bx=lx/(p.h[0]*p.h[0]),by=ly/(p.h[1]*p.h[1]),bz=lz/(p.h[2]*p.h[2]);float k1=(float)Math.sqrt(bx*bx+by*by+bz*bz);
                return k1<1e-9f?-Math.min(p.h[0],Math.min(p.h[1],p.h[2])):k0*(k0-1)/k1;}
            case RCONE:{ // Inigo Quilez's round cone
                float bax=p.b[0]-p.a[0],bay=p.b[1]-p.a[1],baz=p.b[2]-p.a[2];float l2=bax*bax+bay*bay+baz*baz;
                if(l2<1e-10f){float dx=x-p.a[0],dy=y-p.a[1],dz=z-p.a[2];return (float)Math.sqrt(dx*dx+dy*dy+dz*dz)-p.r;}
                float rr=p.r-p.r2,a2=l2-rr*rr,il2=1/l2;
                float pax=x-p.a[0],pay=y-p.a[1],paz=z-p.a[2];float yy=pax*bax+pay*bay+paz*baz,zz=yy-l2;
                float xx0=pax*l2-bax*yy,xx1=pay*l2-bay*yy,xx2=paz*l2-baz*yy;float x2=xx0*xx0+xx1*xx1+xx2*xx2,y2=yy*yy*l2,z2=zz*zz*l2;
                float k=Math.signum(rr)*rr*rr*x2;
                if(Math.signum(zz)*a2*z2>k)return (float)Math.sqrt(x2+z2)*il2-p.r2;
                if(Math.signum(yy)*a2*y2<k)return (float)Math.sqrt(x2+y2)*il2-p.r;
                return ((float)Math.sqrt(x2*a2*il2)+yy*rr)*il2-p.r;}
            case CYL:{float bax=p.b[0]-p.a[0],bay=p.b[1]-p.a[1],baz=p.b[2]-p.a[2],pax=x-p.a[0],pay=y-p.a[1],paz=z-p.a[2];
                float baba=bax*bax+bay*bay+baz*baz,paba=pax*bax+pay*bay+paz*baz;
                float qx=pax*baba-bax*paba,qy=pay*baba-bay*paba,qz=paz*baba-baz*paba;
                float dx=(float)Math.sqrt(qx*qx+qy*qy+qz*qz)-p.r*baba,dy=Math.abs(paba-baba*.5f)-baba*.5f;
                float x2=dx*dx,y2=dy*dy*baba;float d=Math.max(dx,dy)<0?-Math.min(x2,y2):((dx>0?x2:0)+(dy>0?y2:0));
                return Math.signum(d)*(float)Math.sqrt(Math.abs(d))/baba;}
            case TORUS:{float dx=x-p.c[0],dy=y-p.c[1],dz=z-p.c[2];float[] u=p.ax[0],v=p.ax[1],w=p.ax[2];
                float lx=dx*u[0]+dy*u[1]+dz*u[2],ly=dx*v[0]+dy*v[1]+dz*v[2],lz=dx*w[0]+dy*w[1]+dz*w[2];
                float q=(float)Math.sqrt(lx*lx+ly*ly)-p.r;return (float)Math.sqrt(q*q+lz*lz)-p.r2;}
            default:{float dx=x-p.c[0],dy=y-p.c[1],dz=z-p.c[2];float[] u=p.ax[0],v=p.ax[1],w=p.ax[2];
                float qx=Math.abs(dx*u[0]+dy*u[1]+dz*u[2])-p.h[0]+p.round,qy=Math.abs(dx*v[0]+dy*v[1]+dz*v[2])-p.h[1]+p.round,qz=Math.abs(dx*w[0]+dy*w[1]+dz*w[2])-p.h[2]+p.round;
                float mx=Math.max(qx,0),my=Math.max(qy,0),mz=Math.max(qz,0);
                return (float)Math.sqrt(mx*mx+my*my+mz*mz)+Math.min(Math.max(qx,Math.max(qy,qz)),0)-p.round;}
        }
    }
    private static float boundRadius(Prim p){
        switch(p.type){case ELL:return Math.max(p.h[0],Math.max(p.h[1],p.h[2]))+p.k;
            case BOX:return (float)Math.sqrt(p.h[0]*p.h[0]+p.h[1]*p.h[1]+p.h[2]*p.h[2])+p.k;
            case TORUS:return p.r+p.r2+p.k;
            case RCONE:return len(sub(p.b,p.a))*.5f+Math.max(p.r,p.r2)+p.k;
            default:return len(sub(p.b,p.a))*.5f+p.r+p.k;}
    }

    /** Per-render ray marcher. */
    private static final class Tracer {
        final Model m;final Prim[] all;final float[] u,up,w,L,Lr;final float ppu,far=6;
        final int[] cand=new int[256];int nc;final float[] gd=new float[GROUPS];int hitMat;
        final int[] near=new int[256];int nn;final int[] occl=new int[256];int no;
        Tracer(Model m,float[] u,float[] up,float[] w,float[] L,float[] Lr,float ppu){this.m=m;all=m.prims.toArray(new Prim[0]);this.u=u;this.up=up;this.w=w;this.L=L;this.Lr=Lr;this.ppu=ppu;}
        /** Scene distance using the primitives in {@code list} (count n); sets hitMat to the nearest primitive's material. */
        float map(float x,float y,float z,int[] list,int n,boolean useAll){
            for(int g=0;g<GROUPS;g++)gd[g]=1e9f;float best=1e9f;int bm=-1;
            int cnt=useAll?all.length:n;
            for(int i=0;i<cnt;i++){Prim p=useAll?all[i]:all[list[i]];float d=sdf(p,x,y,z);
                if(d<best){best=d;bm=p.mat;}
                float a=gd[p.group];
                if(p.k>0){float h=Math.max(p.k-Math.abs(a-d),0)/p.k;gd[p.group]=Math.min(a,d)-h*h*p.k*.25f;}else if(d<a)gd[p.group]=d;}
            hitMat=bm;float r=1e9f;for(int g=0;g<GROUPS;g++)r=Math.min(r,gd[g]);return r;
        }
        /** March one ray through screen point (sx,sy). Returns hit t or -1. */
        float march(float sx,float sy,float[] o){
            o[0]=u[0]*sx+up[0]*sy-w[0]*far;o[1]=u[1]*sx+up[1]*sy-w[1]*far;o[2]=u[2]*sx+up[2]*sy-w[2]*far;
            nc=0;float t0=1e9f,t1=-1e9f;
            for(int i=0;i<all.length&&nc<cand.length;i++){Prim p=all[i];float dx=sx-p.bx,dy=sy-p.by;if(dx*dx+dy*dy>p.br*p.br)continue;
                cand[nc++]=i;t0=Math.min(t0,far+p.bz-p.br);t1=Math.max(t1,far+p.bz+p.br);}
            if(nc==0)return -1;
            float t=Math.max(0,t0);
            for(int s=0;s<96&&t<t1;s++){float d=map(o[0]+w[0]*t,o[1]+w[1]*t,o[2]+w[2]*t,cand,nc,false);
                if(d<.0007f)return t;t+=Math.max(d*.9f,.0005f);}
            return -1;
        }
        final float[] n=new float[3];
        void normal(float x,float y,float z){
            float e=.0012f;
            float a=map(x+e,y-e,z-e,cand,nc,false),b=map(x-e,y-e,z+e,cand,nc,false),c=map(x-e,y+e,z-e,cand,nc,false),d=map(x+e,y+e,z+e,cand,nc,false);
            n[0]=a-b-c+d;n[1]=-a-b+c+d;n[2]=-a+b-c+d;float l=(float)Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);if(l>0){n[0]/=l;n[1]/=l;n[2]/=l;}
        }
        float ao(float x,float y,float z){
            float occ=0,sc=1;
            nn=0;for(int i=0;i<all.length;i++){Prim p=all[i];float dx=p.c[0]-x,dy=p.c[1]-y,dz=p.c[2]-z;float lim=p.br+.2f;if(dx*dx+dy*dy+dz*dz<lim*lim)near[nn++]=i;}
            for(int i=1;i<=4;i++){float hh=.015f+.04f*i;float d=map(x+n[0]*hh,y+n[1]*hh,z+n[2]*hh,near,nn,false);occ+=(hh-d)*sc;sc*=.7f;}
            return Math.max(0,Math.min(1,1-2.2f*occ));
        }
        float shadow(float x,float y,float z){
            float res=1,t=.02f,len=.8f;
            // only primitives near the shadow ray can block it
            no=0;for(int i=0;i<all.length;i++){Prim p=all[i];float dx=p.c[0]-x,dy=p.c[1]-y,dz=p.c[2]-z;float tt=Math.max(0,Math.min(len,dx*L[0]+dy*L[1]+dz*L[2]));
                float ex=dx-L[0]*tt,ey=dy-L[1]*tt,ez=dz-L[2]*tt;float lim=p.br+.02f;if(ex*ex+ey*ey+ez*ez<lim*lim)occl[no++]=i;}
            if(no==0)return 1;
            for(int i=0;i<14&&t<len;i++){float hh=map(x+L[0]*t,y+L[1]*t,z+L[2]*t,occl,no,false);if(hh<.0008f)return .12f;res=Math.min(res,7*hh/t);t+=Math.max(hh,.015f);}
            return Math.max(.12f,res);
        }
    }
    // value noise for surface detail (3D, so it sticks to the model in every view)
    private static float vnoise(float x,float y,float z,int seed){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y),iz=(int)Math.floor(z);float fx=x-ix,fy=y-iy,fz=z-iz;
        fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);fz=fz*fz*(3-2*fz);
        float r=0;
        for(int k=0;k<2;k++)for(int j=0;j<2;j++)for(int i=0;i<2;i++){float h=Textures.hash(ix+i,(iy+j)*57+(iz+k)*3119,seed);r+=h*(i==0?1-fx:fx)*(j==0?1-fy:fy)*(k==0?1-fz:fz);}
        return r;
    }
    private static float fbm(float x,float y,float z,int seed){return vnoise(x,y,z,seed)*.62f+vnoise(x*2.1f,y*2.1f,z*2.1f,seed+7)*.38f;}
    private static float aces(float x){x*=.95f;float v=(x*(2.51f*x+.03f))/(x*(2.43f*x+.59f)+.14f);return Math.max(0,Math.min(1,v));}
    private static int enc(float r,float g,float b,int a){
        return (a<<24)|((int)(Math.pow(aces(r),1/2.2)*255+.5f)<<16)|((int)(Math.pow(aces(g),1/2.2)*255+.5f)<<8)|(int)(Math.pow(aces(b),1/2.2)*255+.5f);}

    /** Shades a hit; returns linear colour in rgb[0..2]. */
    private static void shade(Tracer tr,float[] o,float t,float[] rgb,int seed){
        float x=o[0]+tr.w[0]*t,y=o[1]+tr.w[1]*t,z=o[2]+tr.w[2]*t;
        tr.map(x,y,z,tr.cand,tr.nc,false);Mat mt=tr.m.mats.get(Math.max(0,tr.hitMat));
        tr.normal(x,y,z);float[] n=tr.n;
        float vx=-tr.w[0],vy=-tr.w[1],vz=-tr.w[2];
        float nv=n[0]*vx+n[1]*vy+n[2]*vz;
        if(mt.glow){float f=.65f+.55f*Math.max(0,nv);rgb[0]=mt.emit[0]*f;rgb[1]=mt.emit[1]*f;rgb[2]=mt.emit[2]*f;return;}
        // surface detail: albedo variation + bumped normal
        float sc=mt.nScale;float nz0=fbm(x*sc,y*sc,z*sc,seed+mt.kind);
        if(mt.bump>0){float e=1f/sc*.5f;float gx=fbm((x+e)*sc,y*sc,z*sc,seed+mt.kind)-nz0,gy=fbm(x*sc,(y+e)*sc,z*sc,seed+mt.kind)-nz0,gz=fbm(x*sc,y*sc,(z+e)*sc,seed+mt.kind)-nz0;
            float bb=mt.bump*.9f;n[0]-=gx*bb;n[1]-=gy*bb;n[2]-=gz*bb;float l=(float)Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);n[0]/=l;n[1]/=l;n[2]/=l;nv=n[0]*vx+n[1]*vy+n[2]*vz;}
        float var=1+(nz0-.5f)*mt.nAmp*2;
        if(mt.kind==K_HIDE){float ridge=1-Math.abs(fbm(x*14,y*14,z*14,seed+31)*2-1);var*=1-.35f*(float)Math.pow(ridge,8);}   // veins / creases
        if(mt.kind==K_WOOD){var*=.85f+.3f*(float)(.5+.5*Math.sin((x+y)*160+nz0*8));}
        float ar=mt.alb[0]*var,ag=mt.alb[1]*var,ab=mt.alb[2]*var;
        float[] L=tr.L;float nl=n[0]*L[0]+n[1]*L[1]+n[2]*L[2];
        float wrap=mt.sss>0?.45f:0;float diff=Math.max(0,(nl+wrap)/(1+wrap));
        float sh=nl>-.2f?tr.shadow(x+n[0]*.004f,y+n[1]*.004f,z+n[2]*.004f):.12f;
        float ao=tr.ao(x,y,z);
        // lights (linear): warm key, sky/ground ambient, cool rim from behind
        float key=1.4f;float kr=1.0f*key,kg=.93f*key,kb=.82f*key;
        float sky=.5f+.5f*n[2];float amr=.30f*sky+.17f*(1-sky),amg=.33f*sky+.13f*(1-sky),amb=.40f*sky+.10f*(1-sky);
        float lit=diff*sh;
        float r=ar*(kr*lit+amr*ao),g=ag*(kg*lit+amg*ao),b=ab*(kb*lit+amb*ao);
        if(mt.sss>0){float s2=Math.max(0,diff-Math.max(0,nl))*mt.sss*sh;r+=s2*ar*1.1f;g+=s2*ag*.35f;b+=s2*ab*.25f;}   // light bleeding red through skin
        float hx=L[0]+vx,hy=L[1]+vy,hz=L[2]+vz;float hl=(float)Math.sqrt(hx*hx+hy*hy+hz*hz);float nh=Math.max(0,(n[0]*hx+n[1]*hy+n[2]*hz)/hl);
        float fres=.04f+.96f*(float)Math.pow(1-Math.max(0,nv),5);
        float spec=mt.spec*(float)Math.pow(nh,mt.shin)*sh*(.35f+fres)*(mt.shin+8)/24f;
        float sr=mt.metal>0?ar/(Math.max(.05f,(ar+ag+ab)/3)):1,sg=mt.metal>0?ag/(Math.max(.05f,(ar+ag+ab)/3)):1,sb=mt.metal>0?ab/(Math.max(.05f,(ar+ag+ab)/3)):1;
        r+=spec*kr*(mt.metal>0?Math.min(2,sr):1);g+=spec*kg*(mt.metal>0?Math.min(2,sg):1);b+=spec*kb*(mt.metal>0?Math.min(2,sb):1);
        float rim=(float)Math.pow(1-Math.max(0,nv),3)*Math.max(0,n[0]*tr.Lr[0]+n[1]*tr.Lr[1]+n[2]*tr.Lr[2])*.55f*ao;
        r+=rim*.42f*(.4f+ar);g+=rim*.5f*(.4f+ag);b+=rim*.65f*(.4f+ab);
        rgb[0]=r;rgb[1]=g;rgb[2]=b;
    }

    /**
     * Renders a model seen from {@code yaw} radians around it (0 = from the front, +PI/2 = from its right),
     * {@code elev} radians above the horizon. Cropped so the lowest pixel sits on the floor with the origin centred.
     */
    static synchronized Sprites.Sprite render(Model m,float yaw,float elev,float ppu,boolean bright,int seed){
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw),ce=(float)Math.cos(elev),se=(float)Math.sin(elev);
        float[] dH=v(-cy,-sy,0),u=v(sy,-cy,0),w=v(dH[0]*ce,dH[1]*ce,-se),up=v(dH[0]*se,dH[1]*se,ce);
        float[] L=norm(add(add(mul(u,-.55f),mul(up,.78f)),mul(w,-.42f)));   // key light: upper left, in front
        float[] Lr=norm(add(add(mul(u,.7f),mul(up,.35f)),mul(w,.6f)));      // rim light: behind, right
        float minx=1e9f,maxx=-1e9f,miny=1e9f,maxy=-1e9f;
        for(Prim p:m.prims){p.bx=dot(p.c,u);p.by=dot(p.c,up);p.bz=dot(p.c,w);p.br=boundRadius(p)+.004f;
            minx=Math.min(minx,p.bx-p.br);maxx=Math.max(maxx,p.bx+p.br);miny=Math.min(miny,p.by-p.br);maxy=Math.max(maxy,p.by+p.br);}
        float half=Math.max(Math.abs(minx),Math.abs(maxx))+2f/ppu;
        int W=Math.max(2,(int)Math.ceil(half*ppu)*2),top=(int)Math.ceil(maxy*ppu)+2,bottom=(int)Math.floor(miny*ppu)-2,H=Math.max(2,top-bottom);
        Tracer tr=new Tracer(m,u,up,w,L,Lr,ppu);
        int n=W*H;boolean[] hit=new boolean[n];int[] out=new int[n];float[] o=new float[3],rgb=new float[3];
        for(int py=0;py<H;py++){float syy=(top-py-.5f)/ppu;
            for(int px=0;px<W;px++){float sxx=(px+.5f-W*.5f)/ppu;
                float t=tr.march(sxx,syy,o);if(t<0)continue;
                shade(tr,o,t,rgb,seed);hit[py*W+px]=true;out[py*W+px]=enc(rgb[0],rgb[1],rgb[2],255);}}
        // anti-aliased silhouette: supersample pixels on the edge of the shape (4 sub-rays)
        int[] aa=out.clone();
        for(int py=0;py<H;py++)for(int px=0;px<W;px++){int i=py*W+px;
            boolean edge=false;for(int k=0;k<4&&!edge;k++){int qx=px+(k==0?1:k==1?-1:0),qy=py+(k==2?1:k==3?-1:0);
                if(qx<0||qy<0||qx>=W||qy>=H){edge=hit[i];continue;}if(hit[qy*W+qx]!=hit[i])edge=true;}
            if(!edge)continue;
            float sr=0,sg=0,sb=0;int cov=0;
            for(int k=0;k<4;k++){float ox=(k&1)==0?-.25f:.25f,oy=(k&2)==0?-.25f:.25f;
                float t=tr.march((px+.5f+ox-W*.5f)/ppu,(top-py-.5f-oy)/ppu,o);if(t<0)continue;
                shade(tr,o,t,rgb,seed);sr+=rgb[0];sg+=rgb[1];sb+=rgb[2];cov++;}
            if(cov==0){aa[i]=0;continue;}
            aa[i]=enc(sr/cov,sg/cov,sb/cov,cov*255/4);
        }
        // crop: lowest/highest visible rows, symmetric columns
        int r0=H,r1=-1,c0=W,c1=-1;
        for(int py=0;py<H;py++)for(int px=0;px<W;px++)if((aa[py*W+px]>>>24)>=64){r0=Math.min(r0,py);r1=Math.max(r1,py);c0=Math.min(c0,px);c1=Math.max(c1,px);}
        if(r1<0){Sprites.Sprite e=new Sprites.Sprite(2,2,bright);e.ppu=ppu;return e;}
        int halfW=Math.max(W/2-c0,c1+1-W/2),nw=halfW*2,nh=r1-r0+1,x0=W/2-halfW;
        Sprites.Sprite s=new Sprites.Sprite(nw,nh,bright);s.ppu=ppu;
        for(int py=0;py<nh;py++)for(int px=0;px<nw;px++){int sxp=x0+px;if(sxp<0||sxp>=W)continue;s.px[py*nw+px]=aa[(r0+py)*W+sxp];}
        return s;
    }

    // ------------------------------------------------------------------ characters
    /** Pose for one animation frame. Angles in radians. */
    static final class Pose {
        float lean,headPitch,bob;
        float lThigh,lKnee,rThigh,rKnee;
        float lArmSwing,rArmSwing;
        boolean attack,flash,open,dead;
    }
    static Pose pose(int frame){
        Pose p=new Pose();
        switch(frame){
            case Sprites.F_WALK1:p.lThigh=.42f;p.lKnee=.12f;p.rThigh=-.3f;p.rKnee=.5f;p.lArmSwing=-.35f;p.rArmSwing=.35f;p.bob=-.015f;p.lean=.05f;break;
            case Sprites.F_WALK2:p.rThigh=.42f;p.rKnee=.12f;p.lThigh=-.3f;p.lKnee=.5f;p.lArmSwing=.35f;p.rArmSwing=-.35f;p.bob=-.015f;p.lean=.05f;break;
            case Sprites.F_ATTACK:p.attack=true;p.flash=true;p.lThigh=.2f;p.rThigh=-.14f;p.rKnee=.14f;p.lKnee=.06f;p.lean=-.03f;break;
            case Sprites.F_PAIN:p.lean=-.28f;p.headPitch=-.3f;p.lThigh=.12f;p.rThigh=-.06f;p.lKnee=.22f;p.rKnee=.26f;p.lArmSwing=.6f;p.rArmSwing=.5f;break;
            case Sprites.F_DEAD:p.dead=true;p.lThigh=.05f;p.rThigh=-.1f;p.lArmSwing=.25f;p.rArmSwing=.15f;break;
            default:p.lThigh=.03f;p.rThigh=-.03f;p.lKnee=.05f;p.rKnee=.05f;break;
        }
        return p;
    }
    /** Proportions + materials for a humanoid. */
    static final class Body {
        float hipZ=.76f,hipW=.085f,thigh=.355f,shin=.34f,thighR=.083f,kneeR=.054f,ankleR=.038f;
        float pelvisZ=.81f,waistZ=.93f,chestZ=1.06f,chestF=.105f,chestW=.165f,chestH=.12f,shoulderZ=1.16f,shoulderW=.175f;
        float upper=.27f,fore=.25f,armR=.048f,elbowR=.038f,wristR=.03f;
        float headZ=1.315f,headR=.095f;
        int skin,legs,torso,boots,hands;float leanA;
        float bulk=1;   // scales torso/limb thickness
        float[] head,headF;
    }
    private static float[] leanPt(Body b,float[] p){
        float[] piv=v(0,0,b.hipZ);float cs=(float)Math.cos(b.leanA),sn=(float)Math.sin(b.leanA);float[] q=p.clone();rotY(q,piv,cs,sn,null);return q;
    }
    private static float[] leanDir(Body b){return v((float)Math.cos(b.leanA),0,-(float)Math.sin(b.leanA));}
    /** Legs with tapered thighs, calf muscles and boots/feet. Returns the ankle. */
    private static float[] buildLeg(Model m,Body b,Pose ps,float side,float thighA,float kneeA,int footMat,boolean boot){
        float[] hip=v(0,side*b.hipW,b.hipZ+ps.bob);
        float[] knee=add(hip,limbDir(thighA,side*.035f),b.thigh);
        float[] shinD=limbDir(thighA-kneeA,side*.015f);float[] ankle=add(knee,shinD,b.shin);
        if(ankle[2]<.06f)ankle[2]=.06f;
        m.in(G_BODY,.035f);
        m.cone(hip,knee,b.thighR*b.bulk,b.kneeR*b.bulk,b.legs);
        m.cone(knee,ankle,b.kneeR*b.bulk*.98f,b.ankleR*b.bulk,b.legs);
        m.ellDir(add(lerp(knee,ankle,.3f),v(-.022f,0,0)),shinD,.075f*b.bulk,.045f*b.bulk,.043f*b.bulk,b.legs);   // calf
        m.ellDir(add(lerp(hip,knee,.45f),v(.02f,0,0)),limbDir(thighA,0),.13f,.06f*b.bulk,.062f*b.bulk,b.legs);     // quad
        if(boot){m.in(G_BODY,.02f);
            m.cone(add(ankle,v(0,0,-.02f)),add(ankle,mul(shinD,-.13f)),.05f*b.bulk,.047f*b.bulk,footMat);   // boot shaft
            m.rbox(v(ankle[0]+.045f,ankle[1],.045f),X,.11f,.047f,.045f,.03f,footMat);
            m.in(G_GEAR,0);m.rbox(v(ankle[0]+.045f,ankle[1],.01f),X,.115f,.05f,.012f,.008f,footMat);}  // sole
        else{m.in(G_BODY,.03f);m.ellDir(v(ankle[0]+.06f,ankle[1],.035f),X,.11f,.045f,.035f,footMat);}
        return ankle;
    }
    /** Arm by IK toward {@code target} (or hanging with {@code swing}); returns the hand position. */
    private static float[] buildArm(Model m,Body b,Pose ps,float side,float[] target,float swing,int handMat,boolean claws,int clawMat){
        float[] sh=leanPt(b,v(0,side*b.shoulderW,b.shoulderZ+ps.bob));
        float[] hand=target!=null?target:add(sh,limbDir(swing,side*.12f),(b.upper+b.fore)*.95f);
        float[] elbow=ik(sh,hand,b.upper,b.fore,v(-.7f,side*.5f,-.3f));
        float[] fd=norm(sub(hand,elbow));
        m.in(G_BODY,.03f);
        m.ell(sh,.058f*b.bulk,.06f*b.bulk,.055f*b.bulk,b.torso);                                   // deltoid
        m.cone(sh,elbow,b.armR*b.bulk,b.elbowR*b.bulk,b.torso);
        m.ellDir(add(lerp(sh,elbow,.5f),v(.015f,0,0)),norm(sub(elbow,sh)),.09f,.04f*b.bulk,.04f*b.bulk,b.torso);   // bicep
        m.cone(elbow,sub(hand,mul(fd,.04f)),b.elbowR*b.bulk*1.05f,b.wristR*b.bulk,b.torso);
        m.ellDir(lerp(elbow,hand,.3f),fd,.07f,.037f*b.bulk,.035f*b.bulk,b.torso);                 // forearm muscle
        m.in(G_BODY,.015f);
        m.ellDir(add(hand,fd,.01f),fd,.048f,.028f,.042f,handMat);
        m.cone(add(hand,fd,-.01f),add(add(hand,fd,.03f),v(0,side*-.03f,-.01f)),.012f,.01f,handMat);       // thumb
        if(claws){m.in(G_GEAR,0);for(int k=-1;k<=1;k++){float[] cb=add(add(hand,fd,.045f),v(0,k*.018f,-.01f));m.cone(cb,add(add(cb,fd,.06f),v(0,k*.01f,-.03f)),.008f,.0015f,clawMat);}}
        return hand;
    }
    private static void buildTorso(Model m,Body b,Pose ps){
        b.leanA=ps.lean;float[] ld=leanDir(b);
        m.in(G_BODY,.06f);
        m.ellDir(v(0,0,b.pelvisZ+ps.bob),X,.1f*b.bulk,.145f*b.bulk,.09f,b.legs);
        m.ellDir(leanPt(b,v(0,0,b.waistZ+ps.bob)),ld,.088f*b.bulk,.128f*b.bulk,.1f,b.torso);
        m.ellDir(leanPt(b,v(.005f,0,b.chestZ+ps.bob)),ld,b.chestF*b.bulk,b.chestW*b.bulk,b.chestH,b.torso);
        for(int s=-1;s<=1;s+=2)m.ellDir(leanPt(b,v(.05f,s*.07f,b.chestZ+.03f+ps.bob)),ld,.06f*b.bulk,.075f*b.bulk,.06f,b.torso); // pecs
        m.ellDir(leanPt(b,v(-.03f,0,b.shoulderZ-.01f+ps.bob)),ld,.07f,.16f*b.bulk,.05f,b.torso);   // trapezius
        // neck + head position
        float[] nb=leanPt(b,v(0,0,b.shoulderZ+ps.bob)),nt=leanPt(b,v(.012f,0,b.headZ-b.headR*.85f+ps.bob));
        m.in(G_BODY,.03f);m.cone(nb,nt,.05f*b.bulk,.042f,b.skin);
        float hp=b.leanA+ps.headPitch;b.headF=norm(v((float)Math.cos(hp),0,-(float)Math.sin(hp)*.9f));
        b.head=add(nt,v((float)Math.sin(hp)*b.headR*.9f,0,(float)Math.cos(hp)*b.headR*.95f));
    }
    /** Human head with jaw, brow, nose, ears and eyes. */
    private static void buildFace(Model m,Body b,int eyeMat,boolean glowEyes){
        float[] h=b.head,f=b.headF;float[] up=norm(cross(f,norm(cross(Z,f))));if(up[2]<0)up=mul(up,-1);
        float[] side=norm(cross(up,f));
        m.in(G_BODY,.02f);
        m.ellDir(h,f,b.headR*1.0f,b.headR*.86f,b.headR*1.05f,b.skin);                                     // cranium
        m.ellDir(add(add(h,f,.035f),up,-.06f),f,.068f,.064f,.046f,b.skin);                                 // jaw
        m.in(G_BODY,.012f);
        m.ellDir(add(add(h,f,.074f),up,.022f),f,.028f,.072f,.02f,b.skin);                                   // brow ridge
        for(int s=-1;s<=1;s+=2)m.ellDir(add(add(add(h,f,.06f),side,s*.045f),up,-.02f),f,.03f,.022f,.022f,b.skin);   // cheekbones
        m.cone(add(add(h,f,.088f),up,.005f),add(add(h,f,.108f),up,-.028f),.011f,.016f,b.skin);           // nose
        for(int s=-1;s<=1;s+=2)m.ellDir(add(add(h,side,s*.083f),up,-.008f),side,.012f,.018f,.028f,b.skin);   // ears
        m.in(G_GEAR,0);
        for(int s=-1;s<=1;s+=2)m.ell(add(add(add(h,f,.071f),side,s*.032f),up,-.004f),.011f,.011f,.009f,eyeMat);
        m.ellDir(add(add(h,f,.086f),up,-.058f),f,.006f,.024f,.004f,m.plastic(0xff3a1c18,.1f));          // mouth
    }

    // ---- weapons held by characters
    private static void sword(Model m,float[] hand,float[] d,float len,int steel,int grip,int guard,boolean broad){
        float[][] f=frame(d);m.in(G_GEAR,0);
        m.cyl(add(hand,d,-.06f),add(hand,d,.05f),.016f,grip);                                   // grip
        m.ell(add(hand,d,-.075f),.024f,.024f,.024f,guard);                                       // pommel
        m.rbox(add(hand,d,.065f),d,.012f,.075f,.016f,.006f,guard);                               // crossguard
        m.rbox(add(hand,d,.075f+len*.5f),d,len*.5f,broad?.026f:.019f,.0075f,.005f,steel);        // blade
        m.cone(add(hand,d,.075f+len-.02f),add(hand,d,.075f+len+.03f),.016f,.002f,steel);          // tip
    }
    private static void roundShield(Model m,float[] c,float[] facing,float r,int wood,int metal){
        m.in(G_GEAR,0);float[] n=norm(facing);
        m.cyl(add(c,n,-.015f),add(c,n,.015f),r,wood);
        m.torus(c,n,r,.012f,metal);
        m.ell(add(c,n,.02f),.04f,.04f,.04f,metal);
    }
    private static void axe(Model m,float[] hand,float[] d,int wood,int steel,boolean great){
        m.in(G_GEAR,0);float[][] f=frame(d);float len=great?.85f:.5f;
        m.cyl(add(hand,d,-.2f),add(hand,d,len),great?.018f:.015f,wood);
        float[] head=add(hand,d,len-.08f);
        m.rbox(add(head,f[1],-.07f),d,great?.1f:.07f,.07f,.008f,.004f,steel);
        m.ell(add(head,f[1],-.14f),.003f,great?.09f:.06f,.01f,steel);
        m.cone(head,add(head,d,.08f),.02f,.004f,steel);
    }
    private static void mace(Model m,float[] hand,float[] d,int wood,int steel){
        m.in(G_GEAR,0);m.cyl(add(hand,d,-.08f),add(hand,d,.38f),.016f,wood);float[] h=add(hand,d,.42f);
        m.ell(h,.05f,.05f,.05f,steel);for(int k=0;k<6;k++){double a=k*Math.PI/3;float[][] f=frame(d);float[] o=add(mul(f[1],(float)Math.cos(a)),mul(f[2],(float)Math.sin(a)));m.cone(h,add(h,o,.07f),.015f,.003f,steel);}
    }

    /** The adventurer: chain mail under a tabard, cloak, sword and round shield. */
    static Model hero(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.skin(0xffc89878,.55f);b.legs=m.leather(0xff4a3624);b.torso=m.metal(0xff7c8288);b.boots=m.leather(0xff2e2016);b.hands=m.leather(0xff3a2a1c);
        int tabard=m.cloth(0xff2c4a8a),cloak=m.cloth(0xff7a1e1e),belt=m.leather(0xff3a2616),buckle=m.metal(0xffb89a50),hair=m.cloth(0xff4a2e18),
            steel=m.metal(0xffb8c0c8),grip=m.leather(0xff3a2416),brass=m.metal(0xffb08a40),wood=m.wood(0xff6a4424),eye=m.plastic(0xff2a2420,.6f);
        buildTorso(m,b,ps);
        buildLeg(m,b,ps,-1,ps.lThigh,ps.lKnee,b.boots,true);buildLeg(m,b,ps,1,ps.rThigh,ps.rKnee,b.boots,true);
        float[] ld=leanDir(b);
        // tabard front and back, belt, cloak
        m.in(G_BODY,.015f);
        m.rbox(leanPt(b,v(.1f,0,b.waistZ+.02f+ps.bob)),ld,.02f,.11f,.22f,.015f,tabard);
        m.rbox(leanPt(b,v(-.1f,0,b.waistZ+.02f+ps.bob)),ld,.02f,.11f,.22f,.015f,tabard);
        m.in(G_GEAR,0);
        m.ellDir(leanPt(b,v(0,0,b.waistZ-.06f+ps.bob)),ld,.1f,.14f,.025f,belt);m.rbox(leanPt(b,v(.1f,0,b.waistZ-.06f+ps.bob)),ld,.008f,.025f,.02f,.005f,buckle);
        if(!ps.dead){float sway=frame==Sprites.F_WALK1||frame==Sprites.F_WALK2?.08f:.03f;
            m.in(G_BODY,.02f);m.rbox(leanPt(b,v(-.15f-sway,0,b.waistZ+.02f+ps.bob)),norm(v(.15f,0,1)),.4f,.17f,.014f,.012f,cloak).ax[0][0]+=0;}
        buildFace(m,b,eye,false);
        float[] h=b.head,f=b.headF;m.in(G_GEAR,0);m.ellDir(add(add(h,f,-.015f),v(0,0,.03f)),f,.1f,.089f,.09f,hair);
        if(ps.dead){buildArm(m,b,ps,1,null,ps.rArmSwing,b.hands,false,0);buildArm(m,b,ps,-1,null,ps.lArmSwing,b.hands,false,0);corpse(m);
            sword(m,v(.25f,.42f,.03f),norm(v(1,.2f,0)),.7f,steel,grip,brass,true);roundShield(m,v(-.1f,-.5f,.03f),Z,.22f,wood,steel);return m;}
        // sword arm: ready (blade up) or mid-swing; shield on the off arm
        float[] rh=ps.attack?leanPt(b,v(.4f,.08f,1.05f)):leanPt(b,v(.2f,.2f,.88f+ps.bob));
        float[] sd=ps.attack?norm(v(.7f,-.55f,-.2f)):norm(v(.45f,.05f,.9f));
        if(frame==Sprites.F_PAIN){rh=leanPt(b,v(.05f,.32f,.95f));sd=norm(v(.2f,.4f,.9f));}
        sword(m,rh,sd,.64f,steel,grip,brass,true);
        float[] lh=leanPt(b,v(.22f,-.2f,.98f+ps.bob));
        buildArm(m,b,ps,1,rh,0,b.hands,false,0);buildArm(m,b,ps,-1,lh,0,b.hands,false,0);
        roundShield(m,add(lh,v(.05f,-.03f,.02f)),norm(v(1,-.25f,0)),.2f,wood,steel);
        return m;
    }
    /** Animated skeleton warrior: bones, ribcage, glowing eye sockets, rusty sword. */
    static Model skeleton(int frame){
        Model m=new Model();Pose ps=pose(frame);
        int bone=m.mat(0xffd8cfb4,.25f,.25f),dark=m.plastic(0xff1a1612,.1f),eye=m.glow(0xff7ad8ff),rust=m.mat(0xff7a5a3c,.4f,.5f),cloth=m.cloth(0xff4a3a2a);
        float bob=ps.bob;float lean=ps.lean+.05f;
        float[] pelvis=v(0,0,.78f+bob);
        m.in(G_BODY,.02f);
        m.ellDir(pelvis,X,.07f,.12f,.05f,bone);
        // spine and ribcage
        float[] chest=add(pelvis,norm(v((float)Math.sin(lean),0,(float)Math.cos(lean))),.3f);
        m.cone(pelvis,chest,.022f,.022f,bone);
        for(int k=0;k<5;k++){float t=.35f+k*.13f;float[] c=lerp(pelvis,chest,t);float rr=.07f+.04f*(float)Math.sin(Math.PI*(t-.2f));m.torus(add(c,v(.02f,0,0)),norm(v(-(float)Math.sin(lean),0,(float)Math.cos(lean))),rr,.011f,bone);}
        m.cone(add(chest,v(.02f,0,.06f)),add(chest,v(.03f,0,-.1f)),.015f,.012f,bone);              // sternum
        float[] neck=add(chest,v(.01f,0,.1f)),head=add(neck,v(.015f+(float)Math.sin(lean+ps.headPitch)*.05f,0,.1f));
        m.cone(chest,neck,.018f,.016f,bone);
        // skull
        m.in(G_BODY,.015f);
        m.ellDir(head,X,.085f,.07f,.09f,bone);m.ellDir(add(head,v(.045f,0,-.06f)),X,.05f,.05f,.03f,bone);
        m.in(G_GEAR,0);for(int s2=-1;s2<=1;s2+=2){m.ell(add(head,v(.07f,s2*.03f,.005f)),.02f,.019f,.019f,dark);m.ell(add(head,v(.085f,s2*.03f,.005f)),.006f,.006f,.006f,eye);}
        m.ell(add(head,v(.085f,0,-.03f)),.01f,.008f,.012f,dark);
        for(int k=-2;k<=2;k++)m.rbox(add(head,v(.088f,k*.012f,-.055f)),X,.005f,.005f,.008f,.002f,bone);
        // shoulders + arms
        float[] shR=add(chest,v(0,.15f,.03f)),shL=add(chest,v(0,-.15f,.03f));
        m.in(G_BODY,.01f);m.cone(shL,shR,.016f,.016f,bone);
        Body b=new Body();b.upper=.26f;b.fore=.25f;
        float[] rh=ps.attack?add(chest,v(.35f,.1f,.05f)):ps.dead?null:add(chest,v(.18f,.2f,-.2f));
        float[] lh=ps.dead?null:add(chest,v(.12f,-.24f,-.28f+ps.lArmSwing*.1f));
        for(int sd=-1;sd<=1;sd+=2){float[] sh=sd>0?shR:shL;float[] hand=sd>0?rh:lh;
            if(hand==null)hand=add(sh,limbDir(sd>0?ps.rArmSwing:ps.lArmSwing,sd*.15f),.48f);
            float[] el=ik(sh,hand,.26f,.25f,v(-.6f,sd*.5f,-.3f));
            m.in(G_BODY,.008f);m.cone(sh,el,.02f,.016f,bone);m.cone(el,hand,.017f,.014f,bone);m.ell(el,.025f,.025f,.025f,bone);m.ell(hand,.03f,.018f,.03f,bone);}
        // legs
        for(int sd=-1;sd<=1;sd+=2){float th=sd<0?ps.lThigh:ps.rThigh,kn=sd<0?ps.lKnee:ps.rKnee;
            float[] hip=add(pelvis,v(0,sd*.07f,-.02f)),knee=add(hip,limbDir(th,sd*.03f),.35f),ankle=add(knee,limbDir(th-kn,0),.34f);if(ankle[2]<.05f)ankle[2]=.05f;
            m.in(G_BODY,.008f);m.cone(hip,knee,.024f,.018f,bone);m.ell(knee,.03f,.028f,.03f,bone);m.cone(knee,ankle,.018f,.014f,bone);
            m.rbox(v(ankle[0]+.04f,ankle[1],.03f),X,.07f,.03f,.02f,.012f,bone);}
        m.in(G_GEAR,0);m.ellDir(add(pelvis,v(0,0,-.03f)),X,.09f,.13f,.06f,cloth);   // tattered loincloth
        if(ps.dead){corpse(m);sword(m,v(.2f,.4f,.02f),norm(v(1,.3f,0)),.55f,rust,cloth,rust,false);return m;}
        float[] sdir=ps.attack?norm(v(.8f,-.4f,-.3f)):norm(v(.6f,.1f,.8f));
        sword(m,rh,sdir,.55f,rust,cloth,rust,false);
        return m;
    }
    /** Hooded cultist in a long robe; conjures fire between its hands when attacking. */
    static Model cultist(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        int robe=m.cloth(0xff5a1414),trim=m.cloth(0xffb08a30),skin=m.skin(0xffb89a84,.5f),shadow=m.plastic(0xff0a0808,.05f),eye=m.glow(0xffff6a20),rope=m.cloth(0xff2a1a10);
        b.skin=skin;b.torso=robe;b.legs=robe;b.hands=skin;b.boots=robe;
        buildTorso(m,b,ps);
        float[] ld=leanDir(b);
        // robe: wide cone from the chest to the floor, swaying with the walk
        float sway=frame==Sprites.F_WALK1?.04f:frame==Sprites.F_WALK2?-.04f:0;
        m.in(G_BODY,.05f);m.cone(leanPt(b,v(0,0,1.0f+ps.bob)),v(sway*.6f,0,.42f),.16f,.24f,robe);
        m.cyl(v(sway,0,.02f),v(sway*.6f,0,.46f),.25f,robe);
        m.in(G_GEAR,0);m.torus(v(sway,0,.07f),Z,.25f,.02f,trim);
        m.ellDir(leanPt(b,v(0,0,b.waistZ+ps.bob)),ld,.12f,.17f,.025f,rope);
        // hood with the face in shadow and two glowing eyes
        float[] h=b.head,f=b.headF;
        m.in(G_BODY,.03f);m.ellDir(add(h,f,-.02f),f,.12f,.11f,.13f,robe);m.cone(add(add(h,f,-.08f),v(0,0,.08f)),add(add(h,f,-.15f),v(0,0,.15f)),.06f,.01f,robe);
        m.in(G_GEAR,0);m.ellDir(add(h,f,.07f),f,.04f,.075f,.085f,shadow);
        for(int s2=-1;s2<=1;s2+=2)m.ell(add(add(h,f,.095f),v(0,s2*.03f,.005f)),.011f,.011f,.008f,eye);
        // arms in wide sleeves
        float[] rh=ps.attack?leanPt(b,v(.36f,.08f,1.08f)):leanPt(b,v(.16f,.07f,.86f+ps.bob)),lh=ps.attack?leanPt(b,v(.36f,-.08f,1.08f)):leanPt(b,v(.16f,-.07f,.86f+ps.bob));
        if(ps.dead){rh=null;lh=null;}
        buildArm(m,b,ps,1,rh,ps.rArmSwing,skin,false,0);buildArm(m,b,ps,-1,lh,ps.lArmSwing,skin,false,0);
        for(int s2=-1;s2<=1;s2+=2){float[] sh=leanPt(b,v(0,s2*b.shoulderW,b.shoulderZ+ps.bob));float[] hand=s2>0?rh:lh;if(hand==null)hand=add(sh,limbDir(.1f,s2*.12f),.48f);
            float[] el=ik(sh,hand,b.upper,b.fore,v(-.7f,s2*.5f,-.3f));m.in(G_BODY,.03f);m.cone(el,add(lerp(el,hand,.75f),v(0,0,-.02f)),.05f,.075f,robe);}
        if(ps.attack){m.in(G_FX,0);float[] fc=lerp(rh,lh,.5f);fc=add(fc,v(.08f,0,.02f));m.ell(fc,.08f,.08f,.08f,m.glow(0xffd04010));m.ell(add(fc,v(.03f,0,0)),.05f,.05f,.05f,m.glow(0xffffb040));}
        if(ps.dead)corpse(m);
        return m;
    }
    /** Small, wiry goblin with big ears, a crude blade and a leather jerkin. */
    static Model goblin(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.skin(0xff6e8a3a,.35f);b.legs=m.cloth(0xff5a4a30);b.torso=m.leather(0xff5a3e24);b.boots=b.skin;b.hands=b.skin;
        int eye=m.glow(0xffffd030),tooth=m.plastic(0xffe8e0c0,.4f),rust=m.mat(0xff8a6a48,.45f,.5f),grip=m.leather(0xff3a2416),hair=m.cloth(0xff1e1a14);
        b.thighR=.075f;b.armR=.042f;b.headR=.11f;b.chestW=.15f;
        if(!ps.dead&&frame!=Sprites.F_PAIN)ps.lean+=.25f;
        buildTorso(m,b,ps);
        buildLeg(m,b,ps,-1,ps.lThigh+.15f,ps.lKnee+.3f,b.skin,false);buildLeg(m,b,ps,1,ps.rThigh+.15f,ps.rKnee+.3f,b.skin,false);
        float[] h=b.head,f=b.headF;float[] up=Z;float[] side=norm(cross(up,f));
        m.in(G_BODY,.02f);
        m.ellDir(h,f,b.headR*1.05f,b.headR*.9f,b.headR*.95f,b.skin);
        m.ellDir(add(add(h,f,.05f),v(0,0,-.05f)),f,.065f,.07f,.045f,b.skin);                        // jaw
        m.cone(add(add(h,f,.09f),v(0,0,-.005f)),add(add(h,f,.16f),v(0,0,-.03f)),.022f,.008f,b.skin);   // long nose
        for(int s2=-1;s2<=1;s2+=2){float[] e=add(h,side,s2*.09f);m.cone(e,add(add(e,side,s2*.13f),v(-.04f,0,.07f)),.035f,.004f,b.skin);}   // big ears
        m.in(G_GEAR,0);
        for(int s2=-1;s2<=1;s2+=2){m.ell(add(add(add(h,f,.085f),side,s2*.04f),v(0,0,.02f)),.014f,.014f,.011f,eye);
            m.cone(add(add(add(h,f,.09f),side,s2*.025f),v(0,0,-.075f)),add(add(add(h,f,.09f),side,s2*.025f),v(0,0,-.05f)),.006f,.001f,tooth);}
        m.ellDir(add(add(h,f,-.02f),v(0,0,.06f)),f,.07f,.06f,.04f,hair);
        float[] rh=ps.attack?leanPt(b,v(.42f,.05f,1.0f)):ps.dead?null:leanPt(b,v(.2f,.2f,.82f));
        float[] rr=buildArm(m,b,ps,1,rh,ps.rArmSwing+.3f,b.hands,true,tooth);buildArm(m,b,ps,-1,null,ps.lArmSwing+.4f,b.hands,true,tooth);
        if(ps.dead){corpse(m);m.scale(.74f);sword(m,v(.15f,.3f,.02f),norm(v(1,.3f,0)),.4f,rust,grip,rust,true);return m;}
        sword(m,rr,ps.attack?norm(v(.8f,-.3f,-.4f)):norm(v(.55f,.1f,.82f)),.42f,rust,grip,rust,true);
        m.scale(.74f);
        return m;
    }
    /** Hulking orc: tusks, war paint, iron pauldrons and a greataxe. */
    static Model orc(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.skin(0xff6a7a46,.3f);b.legs=m.leather(0xff3a2a1c);b.torso=b.skin;b.boots=m.leather(0xff241a12);b.hands=b.skin;
        int tusk=m.plastic(0xffeee6cc,.5f),iron=m.metal(0xff4a4c50),leather=m.leather(0xff4a3020),hair=m.cloth(0xff141210),wood=m.wood(0xff5a3a1e),steel=m.metal(0xff9aa0a8),eye=m.glow(0xffff4a20);
        b.bulk=1.22f;b.chestW=.19f;b.chestF=.12f;b.shoulderW=.21f;b.armR=.058f;b.headR=.1f;
        buildTorso(m,b,ps);
        buildLeg(m,b,ps,-1,ps.lThigh,ps.lKnee,b.boots,true);buildLeg(m,b,ps,1,ps.rThigh,ps.rKnee,b.boots,true);
        float[] ld=leanDir(b);
        m.in(G_GEAR,0);
        m.ellDir(leanPt(b,v(0,0,b.waistZ-.05f+ps.bob)),ld,.12f,.17f,.05f,leather);                        // belt / kilt
        for(int k=0;k<6;k++){double an=k*Math.PI/3;float cx=(float)Math.cos(an)*.12f,cy=(float)Math.sin(an)*.15f;
            m.rbox(v(cx,cy,b.pelvisZ-.12f+ps.bob),norm(v(cx,cy,0)),.012f,.06f,.12f,.01f,leather);}
        for(int s2=-1;s2<=1;s2+=2){float[] pp=leanPt(b,v(0,s2*(b.shoulderW+.02f),b.shoulderZ+.03f+ps.bob));m.ellDir(pp,ld,.09f,.085f,.06f,iron);m.cone(pp,add(pp,v(0,s2*.03f,.11f)),.02f,.003f,iron);}
        m.cone(leanPt(b,v(.02f,-.1f,b.shoulderZ+.03f+ps.bob)),leanPt(b,v(.1f,.12f,b.waistZ-.02f+ps.bob)),.02f,.02f,leather);   // baldric
        float[] h=b.head,f=b.headF;
        m.in(G_BODY,.02f);
        m.ellDir(h,f,b.headR*1.05f,b.headR*.95f,b.headR,b.skin);m.ellDir(add(add(h,f,.04f),v(0,0,-.06f)),f,.08f,.08f,.055f,b.skin);
        m.ellDir(add(add(h,f,.075f),v(0,0,.03f)),f,.03f,.08f,.022f,b.skin);m.ell(add(add(h,f,.1f),v(0,0,-.005f)),.025f,.028f,.02f,b.skin);
        m.in(G_GEAR,0);
        for(int s2=-1;s2<=1;s2+=2){m.ell(add(add(h,f,.088f),v(0,s2*.033f,.008f)),.011f,.011f,.009f,eye);
            m.cone(add(add(h,f,.095f),v(0,s2*.035f,-.07f)),add(add(h,f,.105f),v(0,s2*.04f,-.02f)),.012f,.002f,tusk);}
        m.cone(add(h,v(-.02f,0,.07f)),add(h,v(-.09f,0,.2f)),.035f,.012f,hair);   // topknot
        float[] rh,lh;float[] ad;
        if(ps.attack){rh=leanPt(b,v(.38f,.1f,1.15f));lh=leanPt(b,v(.3f,-.05f,1.02f));ad=norm(v(.8f,-.1f,-.4f));}
        else if(ps.dead){rh=null;lh=null;ad=X;}
        else{rh=leanPt(b,v(.18f,.22f,.85f+ps.bob));lh=leanPt(b,v(.22f,-.02f,1.05f+ps.bob));ad=norm(sub(lh,rh));}
        buildArm(m,b,ps,1,rh,ps.rArmSwing,b.hands,false,0);buildArm(m,b,ps,-1,lh,ps.lArmSwing,b.hands,false,0);
        if(ps.dead){corpse(m);axe(m,v(.2f,.5f,.03f),norm(v(1,.2f,0)),wood,steel,true);m.scale(1.08f);return m;}
        axe(m,rh,ad,wood,steel,true);
        m.scale(1.08f);
        return m;
    }
    /** Villagers (quest givers, merchants): tunic, apron, grey hair and beard. */
    static Model villager(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.skin(0xffd0a080,.55f);b.legs=m.cloth(0xff4a4034);b.torso=m.cloth(0xff7a5a34);b.boots=m.leather(0xff3a2a1c);b.hands=b.skin;
        int apron=m.cloth(0xffb8a888),hair=m.cloth(0xffb8b4ac),belt=m.leather(0xff3a2616),eye=m.plastic(0xff2a2420,.6f);
        if(ps.attack){ps.attack=false;ps.flash=false;}   // NPCs gesture instead of attacking
        buildTorso(m,b,ps);
        buildLeg(m,b,ps,-1,ps.lThigh,ps.lKnee,b.boots,true);buildLeg(m,b,ps,1,ps.rThigh,ps.rKnee,b.boots,true);
        float[] ld=leanDir(b);
        m.in(G_BODY,.03f);m.cone(leanPt(b,v(0,0,b.waistZ+ps.bob)),v(0,0,.62f+ps.bob),.14f,.17f,b.torso);m.cyl(v(0,0,.52f+ps.bob),v(0,0,.66f+ps.bob),.175f,b.torso);   // tunic skirt
        m.in(G_GEAR,0);m.rbox(leanPt(b,v(.12f,0,b.waistZ-.12f+ps.bob)),ld,.012f,.11f,.18f,.01f,apron);m.ellDir(leanPt(b,v(0,0,b.waistZ+ps.bob)),ld,.1f,.14f,.022f,belt);
        buildFace(m,b,eye,false);
        float[] h=b.head,f=b.headF;
        m.in(G_GEAR,0);m.ellDir(add(add(h,f,-.02f),v(0,0,.02f)),f,.1f,.09f,.09f,hair);
        m.in(G_BODY,.02f);m.ellDir(add(add(h,f,.06f),v(0,0,-.09f)),f,.05f,.06f,.06f,hair);m.cone(add(add(h,f,.07f),v(0,0,-.1f)),add(add(h,f,.06f),v(0,0,-.19f)),.04f,.01f,hair);   // beard
        float[] rh=frame==Sprites.F_ATTACK?leanPt(b,v(.25f,.25f,1.3f)):null;   // waves when "attacking"
        buildArm(m,b,ps,1,rh,ps.rArmSwing*.5f+.05f,b.hands,false,0);buildArm(m,b,ps,-1,null,ps.lArmSwing*.5f+.05f,b.hands,false,0);
        if(ps.dead)corpse(m);
        return m;
    }
    /** Corpses: the posed model tipped over backwards onto the floor, lying in a glossy pool of blood. */
    private static void corpse(Model m){
        m.rotateY(v(0,0,0),(float)(-Math.PI*.5),v(.68f,0,.12f));
        int blood=m.mat(0xff5a0606,.7f,.05f);m.in(G_GEAR,0);
        m.ellAxes(v(0,.02f,0),X,Y,Z,.62f,.4f,.02f,blood);m.ellAxes(v(.3f,-.18f,0),X,Y,Z,.25f,.2f,.018f,blood);
    }

    // ------------------------------------------------------------------ items and decorations (one view)
    static Model item(int id,int frame){
        Model m=new Model();m.in(G_GEAR,0);
        switch(id){
            case Sprites.MEDKIT:case Sprites.SHELLS:case Sprites.STIMPACK:{   // potions: glass flask, coloured liquid, cork
                boolean small=id==Sprites.STIMPACK;int liquid=m.glass(id==Sprites.SHELLS?0xff2a5ad8:0xffc81e2a),glass=m.glass(0xffd8e8f0),cork=m.wood(0xff8a6a44),cord=m.cloth(0xffc8b070);
                if(small){m.cyl(v(0,0,0),v(0,0,.2f),.045f,liquid);m.cyl(v(0,0,.2f),v(0,0,.24f),.025f,glass);m.cyl(v(0,0,.24f),v(0,0,.28f),.03f,cork);break;}
                float r=id==Sprites.SHELLS?.1f:.12f;m.in(G_BODY,.02f);m.ell(v(0,0,r),r,r,r*1.02f,liquid);m.cyl(v(0,0,r*1.8f),v(0,0,r*1.8f+.11f),.035f,glass);
                m.in(G_GEAR,0);m.torus(v(0,0,r*1.8f+.03f),Z,.037f,.008f,cord);m.cyl(v(0,0,r*1.8f+.1f),v(0,0,r*1.8f+.15f),.04f,cork);
                m.ell(v(.05f,-.04f,r*1.35f),.02f,.02f,.03f,m.glow(0xffffffff));break;}
            case Sprites.CLIP:{int steel=m.metal(0xffc0c8d0),grip=m.leather(0xff3a2416),brass=m.metal(0xffb08a40);sword(m,v(-.1f,0,.03f),norm(v(1,.15f,0)),.25f,steel,grip,brass,false);break;}
            case Sprites.ROCKETBOX:{int gold=m.metal(0xffe0b040),gem=m.glass(0xff30d050);   // golden idol on a little plinth
                m.rbox(v(0,0,.04f),X,.12f,.12f,.04f,.01f,m.mat(0xff7a7268,.2f,.4f));m.in(G_BODY,.03f);m.cone(v(0,0,.08f),v(0,0,.3f),.08f,.05f,gold);m.ell(v(0,0,.37f),.06f,.055f,.07f,gold);
                for(int s2=-1;s2<=1;s2+=2)m.cone(v(0,s2*.06f,.27f),v(.05f,s2*.09f,.18f),.022f,.018f,gold);m.in(G_GEAR,0);m.ell(v(.055f,0,.38f),.015f,.015f,.015f,gem);break;}
            case Sprites.ARMOR:{int chain=m.metal(0xff8a9098),leather=m.leather(0xff4a3020),wood=m.wood(0xff5a3a1e);   // chain shirt on a wooden stand
                m.cyl(v(0,0,0),v(0,0,.05f),.16f,wood);m.cyl(v(0,0,.05f),v(0,0,.85f),.02f,wood);m.cyl(v(0,-.2f,.75f),v(0,.2f,.75f),.018f,wood);
                m.in(G_BODY,.05f);m.ellDir(v(0,0,.55f),X,.1f,.17f,.24f,chain);m.ellDir(v(0,0,.72f),X,.11f,.21f,.08f,chain);for(int s2=-1;s2<=1;s2+=2)m.cone(v(0,s2*.2f,.72f),v(0,s2*.24f,.52f),.06f,.05f,chain);
                m.in(G_GEAR,0);m.ellDir(v(0,0,.36f),X,.105f,.17f,.025f,leather);break;}
            case Sprites.SHOTGUN_PICKUP:{int steel=m.metal(0xffc0c8d0),grip=m.leather(0xff3a2416),brass=m.metal(0xffb08a40);sword(m,v(-.25f,-.1f,.03f),norm(v(1,.35f,0)),.72f,steel,grip,brass,true);break;}
            case Sprites.CHAINGUN_PICKUP:{int wood=m.wood(0xff7a5030),str=m.cloth(0xffe8e0c8),leather=m.leather(0xff4a3020);   // longbow lying on the floor
                for(int k=0;k<10;k++){double a0=-1.1+k*.22,a1=a0+.22;float R=.55f;m.cone(v((float)Math.cos(a0)*R*.45f-.1f,(float)Math.sin(a0)*R,.03f),v((float)Math.cos(a1)*R*.45f-.1f,(float)Math.sin(a1)*R,.03f),.016f-Math.abs(k-4.5f)*.001f,.016f-Math.abs(k-3.5f)*.001f,wood);}
                m.cone(v((float)Math.cos(-1.1)*.55f*.45f-.1f,(float)Math.sin(-1.1)*.55f,.03f),v((float)Math.cos(1.1)*.55f*.45f-.1f,(float)Math.sin(1.1)*.55f,.03f),.004f,.004f,str);
                m.cyl(v(.14f,-.03f,.03f),v(.14f,.03f,.03f),.022f,leather);break;}
            case Sprites.LAUNCHER_PICKUP:{int wood=m.wood(0xff6a4428),gem=m.glow(0xff6ab8ff),metal=m.metal(0xffb08a40);   // wizard's staff
                float[] a=v(-.3f,-.25f,.03f),b=v(.45f,.3f,.03f);m.cyl(a,b,.02f,wood);m.torus(b,norm(sub(b,a)),.03f,.008f,metal);m.ell(add(b,norm(sub(b,a)),.05f),.04f,.04f,.04f,gem);break;}
            case Sprites.KEY_BLUE:case Sprites.KEY_YELLOW:case Sprites.KEY_RED:{
                int c=id==Sprites.KEY_BLUE?0xffb0703a:id==Sprites.KEY_YELLOW?0xffc8ccd4:0xffe8b830;int k=m.metal(c);
                m.torus(v(-.15f,0,.03f),Z,.06f,.016f,k);m.cyl(v(-.09f,0,.03f),v(.17f,0,.03f),.015f,k);
                m.rbox(v(.13f,.04f,.03f),X,.012f,.04f,.012f,.004f,k);m.rbox(v(.17f,.035f,.03f),X,.012f,.03f,.012f,.004f,k);
                m.ell(v(-.15f,0,.03f),.02f,.02f,.02f,m.glow(id==Sprites.KEY_BLUE?0xffff9040:id==Sprites.KEY_YELLOW?0xffd0e8ff:0xffffe060));break;}
            case Sprites.BARREL:{int stave=m.wood(0xff7a5232),hoop=m.metal(0xff3e3c3a),top=m.wood(0xff6a4628);
                m.in(G_BODY,.05f);m.cyl(v(0,0,.02f),v(0,0,.86f),.27f,stave);m.ell(v(0,0,.44f),.31f,.31f,.36f,stave);
                m.in(G_GEAR,0);m.cyl(v(0,0,.84f),v(0,0,.88f),.25f,top);for(float z:new float[]{.12f,.3f,.58f,.76f})m.torus(v(0,0,z),Z,z<.2f||z>.7f?.285f:.305f,.012f,hoop);break;}
            case Sprites.TORCH:{int pole=m.wood(0xff6a5440),iron=m.metal(0xff3c3c40);int fl=frame&1;
                m.cyl(v(0,0,0),v(0,0,1.0f),.035f,pole);m.cyl(v(0,0,0),v(0,0,.05f),.12f,iron);m.cyl(v(0,0,.98f),v(0,0,1.08f),.08f,iron);
                m.ell(v(0,0,1.2f),.1f,.1f,.12f,m.glow(0xffe0501a));
                m.cap(v(0,0,1.22f),v(fl*.02f,(fl*2-1)*.025f,1.42f+fl*.05f),.055f,m.glow(0xffff7a20));
                m.ell(v(.06f,0,1.2f),.06f,.06f,.09f,m.glow(0xffffc040));m.ell(v(.1f,0,1.18f),.03f,.03f,.05f,m.glow(0xfffff4c0));break;}
            case Sprites.COLUMN:{int stone=m.mat(0xff8a8680,.15f,.45f),dk=m.mat(0xff6a6660,.1f,.4f);
                m.rbox(v(0,0,.08f),X,.28f,.28f,.08f,.02f,dk);m.rbox(v(0,0,1.54f),X,.28f,.28f,.08f,.02f,dk);m.cyl(v(0,0,.16f),v(0,0,1.46f),.19f,stone);
                for(int k=0;k<8;k++){double a=k*Math.PI/4;m.cyl(v((float)Math.cos(a)*.18f,(float)Math.sin(a)*.18f,.18f),v((float)Math.cos(a)*.18f,(float)Math.sin(a)*.18f,1.44f),.03f,dk);}break;}
            case Sprites.SWITCH:case Sprites.SWITCH_ON:{int stone=m.mat(0xff7a7670,.1f,.45f),wood=m.wood(0xff6a4428),iron=m.metal(0xff4a4c50);boolean on=id==Sprites.SWITCH_ON;
                m.rbox(v(0,0,.35f),X,.16f,.2f,.35f,.03f,stone);m.rbox(v(.17f,0,.5f),X,.012f,.07f,.07f,.01f,iron);
                float[] base=v(.18f,0,.5f);float[] tip=add(base,norm(v(.5f,0,on?-.8f:.8f)),.38f);m.cyl(base,tip,.018f,wood);m.ell(tip,.04f,.04f,.04f,iron);break;}
            case Sprites.LAMP:{int iron=m.metal(0xff3a3836);   // brazier: tripod, bowl and fire
                for(int k=0;k<3;k++){double a=k*Math.PI*2/3+.5;m.cone(v((float)Math.cos(a)*.22f,(float)Math.sin(a)*.22f,0),v((float)Math.cos(a)*.1f,(float)Math.sin(a)*.1f,.75f),.02f,.018f,iron);}
                m.ell(v(0,0,.82f),.24f,.24f,.1f,iron);m.torus(v(0,0,.88f),Z,.23f,.02f,iron);
                m.ell(v(0,0,.95f),.17f,.17f,.12f,m.glow(0xffe0501a));m.cap(v(0,0,.98f),v((frame&1)*.03f,0,1.22f),.07f,m.glow(0xffff8a20));m.ell(v(.06f,0,1.0f),.07f,.07f,.07f,m.glow(0xffffd060));break;}
            case Sprites.SHIELD_PICKUP:{int wood=m.wood(0xff8a5a30),steel=m.metal(0xffa8b0b8),paint=m.cloth(0xff2c4a8a);roundShield(m,v(0,0,.27f),norm(v(1,0,.35f)),.26f,wood,steel);
                m.rbox(v(.035f,0,.29f),norm(v(1,0,.35f)),.004f,.16f,.025f,.004f,paint);break;}
            case Sprites.SCROLL:{int parch=m.mat(0xffe8dcb8,.05f,.25f),ribbon=m.cloth(0xffa01818),wax=m.plastic(0xffb01010,.5f);
                m.cyl(v(0,-.17f,.04f),v(0,.17f,.04f),.04f,parch);m.torus(v(0,0,.04f),Y,.043f,.007f,ribbon);m.cyl(v(.04f,0,.04f),v(.06f,0,.04f),.018f,wax);break;}
            case Sprites.CHEST:case Sprites.CHEST_OPEN:{int wood=m.wood(0xff6a4024),iron=m.metal(0xff3e3c3a),gold=m.metal(0xffe0b040);boolean open=id==Sprites.CHEST_OPEN;
                m.rbox(v(0,0,.2f),X,.22f,.34f,.2f,.02f,wood);
                if(open){m.rbox(v(-.32f,0,.48f),norm(v(-.15f,0,1)),.2f,.34f,.03f,.02f,wood);m.ellDir(v(0,0,.38f),X,.18f,.3f,.05f,gold);m.ell(v(.05f,.1f,.42f),.04f,.04f,.04f,m.glow(0xfffff0a0));}
                else{m.cyl(v(0,-.34f,.4f),v(0,.34f,.4f),.22f,wood);m.rbox(v(.22f,0,.38f),X,.015f,.04f,.05f,.008f,gold);}
                for(float y:new float[]{-.24f,.24f}){m.rbox(v(0,y,.2f),X,.225f,.02f,.205f,.01f,iron);if(!open)m.torus(v(0,y,.4f),Y,.22f,.012f,iron);}
                break;}
            case Sprites.GOLD:{int gold=m.metal(0xffe8b838);m.in(G_BODY,.02f);m.ell(v(0,0,.02f),.13f,.12f,.05f,gold);m.in(G_GEAR,0);
                for(int k=0;k<9;k++){double a=k*2.4;float r=.06f+(k%3)*.06f;float[] c=v((float)Math.cos(a)*r,(float)Math.sin(a)*r,.02f+(k%2)*.035f);m.cyl(c,add(c,norm(v((float)Math.cos(a)*.3f,.2f,1)),.012f),.045f,gold);}break;}
            default:m.box(v(0,0,.2f),.2f,.2f,.2f,m.mat(0xffff00ff,0,0));
        }
        return m;
    }

    // ------------------------------------------------------------------ first-person weapons
    /**
     * Hands and weapon as seen by the hero, drawn nearly straight-on so y is screen right and z is screen up:
     * gloved forearms rise from the bottom corners like the classic first-person view. One unit is ~60% of the screen.
     */
    static Model firstPerson(int w,boolean atk){
        Model m=new Model();
        int glove=m.leather(0xff4a3422),cuff=m.leather(0xff2e2016),mail=m.metal(0xff7c8288),skin=m.skin(0xffc89878,.5f),
            steel=m.metal(0xffc8d0d8),grip=m.leather(0xff3a2416),brass=m.metal(0xffb89a50),wood=m.wood(0xff6a4428);
        switch(w){
            case Rpg.FP_BOW:{
                // left hand grips the bow at the centre; right hand draws the string (released when attacking)
                float[] lh=v(0,-.08f,.28f),rh=atk?v(-.05f,.22f,.3f):v(-.12f,.12f,.3f);
                fpArm(m,v(-.2f,-.5f,-.35f),lh,mail,glove,cuff);fpArm(m,v(-.2f,.55f,-.3f),rh,mail,glove,cuff);
                m.in(G_GEAR,0);float R=.62f;float[] prev=null;
                for(int k=0;k<=12;k++){double a=-1.0+k*(2.0/12);float[] pt=add(lh,v(.02f,(float)(1-Math.cos(a))*.18f*-1+.0f,(float)Math.sin(a)*R));pt[1]=lh[1]+.03f-(float)(1-Math.cos(a))*.2f;
                    if(prev!=null)m.cone(prev,pt,.022f-Math.abs(k-6)*.0012f,.022f-Math.abs(k-5)*.0012f,wood);prev=pt;}
                float[] top=v(lh[0]+.02f,lh[1]+.03f-(float)(1-Math.cos(1.0))*.2f,lh[2]+(float)Math.sin(1.0)*R),bot=v(top[0],top[1],lh[2]-(float)Math.sin(1.0)*R);
                int str=m.cloth(0xffe8e0c8);float[] pull=atk?v(top[0],top[1],lh[2]):add(rh,v(.02f,-.02f,0));m.cone(top,pull,.004f,.004f,str);m.cone(pull,bot,.004f,.004f,str);
                if(!atk){m.cyl(add(rh,v(.02f,-.02f,0)),add(lh,v(.02f,-.05f,0)),.007f,wood);m.cone(add(lh,v(.02f,-.06f,0)),add(lh,v(.02f,-.13f,0)),.016f,.001f,steel);
                    m.rbox(add(rh,v(.02f,.03f,0)),Y,.03f,.004f,.02f,.002f,m.cloth(0xffc83030));}
                break;}
            case Rpg.FP_STAFF:{
                float[] rh=atk?v(0,.1f,.32f):v(0,.24f,.12f);fpArm(m,v(-.2f,.55f,-.32f),rh,m.cloth(0xff3a2a6a),skin,m.cloth(0xff2a1e50));
                float[] d=atk?norm(v(0,-.35f,.94f)):norm(v(0,-.18f,.98f));m.in(G_GEAR,0);m.cyl(add(rh,d,-.45f),add(rh,d,.62f),.03f,wood);
                float[] top=add(rh,d,.68f);m.torus(add(rh,d,.6f),d,.04f,.01f,brass);for(int k=0;k<3;k++){double a=k*2.1;m.cone(add(rh,d,.6f),add(top,v(0,(float)Math.cos(a)*.05f,.04f+(float)Math.sin(a)*.02f)),.01f,.004f,brass);}
                m.in(G_FX,0);m.ell(top,.055f,.055f,.055f,m.glow(atk?0xffa8d8ff:0xff6ab8ff));if(atk){m.ell(top,.12f,.12f,.12f,m.glow(0xff2a5aff));}
                break;}
            case Rpg.FP_FIST:{
                float[] rh=atk?v(0,.05f,.36f):v(0,.3f,.08f),lh=v(0,-.3f,.06f);
                fpArm(m,v(-.2f,.6f,-.32f),rh,mail,glove,cuff);fpArm(m,v(-.2f,-.6f,-.32f),lh,mail,glove,cuff);
                m.in(G_BODY,.015f);m.ell(add(rh,v(0,0,.03f)),.075f,.085f,.075f,glove);m.ell(add(lh,v(0,0,.03f)),.075f,.085f,.075f,glove);break;}
            default:{   // sword, dagger, mace, axe in the right hand
                float[] rh=atk?v(0,-.02f,.24f):v(0,.27f,.1f);
                float[] d=atk?norm(v(0,-.85f,-.25f)):norm(v(0,-.28f,.96f));
                fpArm(m,v(-.2f,.6f,-.32f),rh,mail,glove,cuff);
                if(w==Rpg.FP_MACE)mace(m,rh,d,wood,steel);else if(w==Rpg.FP_AXE)axe(m,rh,d,wood,steel,false);
                else{float len=w==Rpg.FP_DAGGER?.28f:.68f;float[][] f=frame(d);m.in(G_GEAR,0);
                    m.cyl(add(rh,d,-.08f),add(rh,d,.06f),.022f,grip);m.ell(add(rh,d,-.1f),.032f,.032f,.032f,brass);
                    m.rbox(add(rh,d,.08f),d,.016f,.11f,.022f,.008f,brass);
                    m.rbox(add(rh,d,.09f+len*.5f),d,len*.5f,.034f,.008f,.006f,steel);m.cone(add(rh,d,.09f+len-.03f),add(rh,d,.09f+len+.05f),.022f,.002f,steel);
                    m.rbox(add(add(rh,d,.09f+len*.45f),v(.009f,0,0)),d,len*.42f,.005f,.002f,.001f,m.metal(0xff8a9298));}   // fuller
                if(!atk)fpArm(m,v(-.2f,-.62f,-.34f),v(0,-.36f,-.02f),mail,glove,cuff);
            }
        }
        return m;
    }
    /** A forearm in chain mail with a leather cuff and a gloved fist. */
    private static void fpArm(Model m,float[] elbow,float[] hand,int sleeve,int glove,int cuff){
        float[] d=norm(sub(hand,elbow));m.in(G_BODY,.025f);
        m.cone(add(elbow,d,-.2f),sub(hand,mul(d,.14f)),.125f,.092f,sleeve);
        m.in(G_GEAR,0);m.torus(sub(hand,mul(d,.13f)),d,.092f,.026f,cuff);
        m.in(G_BODY,.025f);m.cone(sub(hand,mul(d,.12f)),hand,.085f,.075f,glove);
        m.ellDir(add(hand,d,.03f),d,.095f,.088f,.08f,glove);
        float[] side=norm(cross(d,X));m.cone(add(hand,side,.055f),add(add(hand,side,.08f),d,.05f),.03f,.027f,glove);   // thumb
    }
}
