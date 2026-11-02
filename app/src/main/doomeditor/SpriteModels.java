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

    /** Marines, troopers and sergeants. style 0 = player, 1 = trooper, 2 = sergeant. */
    static Model soldier(int style,int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.skin(style==0?0xffc89878:0xffa8b098,style==0?.6f:.3f);
        b.legs=m.cloth(style==0?0xff5c5440:style==1?0xff6e6448:0xff3e4046);
        b.torso=m.cloth(style==0?0xff3e4a32:style==1?0xff5a5e40:0xff2e3036);
        b.boots=m.leather(0xff2e221a);b.hands=m.leather(style==0?0xff262422:style==1?0xff8e9480:0xff3a2620);
        int armour=m.plastic(style==0?0xff3a4f26:style==1?0xff59603c:0xff26282c,.22f);
        int webbing=m.cloth(0xff3c3424),buckle=m.metal(0xff9a9070);
        int gunmetal=m.metal(0xff3c4046),polymer=m.plastic(0xff1e2022,.25f),steel=m.metal(0xff7a8088),wood=m.wood(0xff6a3e1e);
        int eye=style==0?m.plastic(0xff2a2a2a,.6f):m.glow(0xffff2a10);
        if(style==2){b.bulk=1.12f;b.chestW=.18f;b.shoulderW=.19f;}
        buildTorso(m,b,ps);
        buildLeg(m,b,ps,-1,ps.lThigh,ps.lKnee,b.boots,true);buildLeg(m,b,ps,1,ps.rThigh,ps.rKnee,b.boots,true);
        float[] ld=leanDir(b);
        // body armour: front + back plates, shoulder guards (marine), webbing and belt with pouches
        m.in(G_BODY,.018f);
        m.rbox(leanPt(b,v(.085f*b.bulk,0,b.chestZ+.005f+ps.bob)),ld,.035f,.135f*b.bulk,.13f,.03f,armour);
        m.rbox(leanPt(b,v(-.085f*b.bulk,0,b.chestZ+.01f+ps.bob)),ld,.035f,.13f*b.bulk,.13f,.03f,armour);
        if(style==0)for(int s=-1;s<=1;s+=2){m.in(G_GEAR,0);m.ellDir(leanPt(b,v(0,s*(b.shoulderW+.012f),b.shoulderZ+.02f+ps.bob)),ld,.072f,.07f,.05f,armour);}
        m.in(G_GEAR,0);
        m.ellDir(leanPt(b,v(0,0,b.waistZ-.055f+ps.bob)),ld,.098f*b.bulk,.14f*b.bulk,.026f,webbing);      // belt
        m.rbox(leanPt(b,v(.097f*b.bulk,0,b.waistZ-.055f+ps.bob)),ld,.008f,.026f,.02f,.005f,buckle);
        for(int s=-1;s<=1;s+=2)m.rbox(leanPt(b,v(.07f*b.bulk,s*.085f,b.waistZ-.07f+ps.bob)),norm(v(1,s*.6f,0)),.03f,.035f,.042f,.012f,webbing);
        if(style==0){m.rbox(leanPt(b,v(-.15f,0,b.chestZ+.0f+ps.bob)),ld,.06f,.115f,.15f,.03f,m.cloth(0xff4a4a34));       // backpack
            for(int s=-1;s<=1;s+=2)m.cone(leanPt(b,v(.02f,s*.1f,b.shoulderZ+.02f+ps.bob)),leanPt(b,v(-.1f,s*.1f,b.shoulderZ-.04f+ps.bob)),.014f,.014f,webbing);}
        // head: face, plus helmet (marine) or close-cropped hair
        buildFace(m,b,eye,style!=0);
        float[] h=b.head,f=b.headF;
        m.in(G_GEAR,0);
        if(style==0){m.ellDir(add(add(h,f,-.01f),v(0,0,.028f)),f,.113f,.1f,.096f,armour);
            m.rbox(add(add(h,f,.088f),v(0,0,.012f)),f,.018f,.068f,.027f,.012f,m.glass(0xff10262c));}
        else m.ellDir(add(add(h,f,-.012f),v(0,0,.02f)),f,.098f,.086f,.094f,m.cloth(style==1?0xff2a2018:0xff151414));
        // weapon (rifle, or pump shotgun for the sergeant): ready hold, raised to the shoulder when firing
        boolean shotgun=style==2;
        if(ps.dead){
            buildArm(m,b,ps,1,null,ps.rArmSwing,b.hands,false,0);buildArm(m,b,ps,-1,null,ps.lArmSwing,b.hands,false,0);
            corpse(m);
            gun(m,v(.15f,-.48f,.035f),norm(v(.3f,1,0)),shotgun,gunmetal,polymer,steel,wood,false);
            return m;
        }
        float gz=(ps.attack?b.shoulderZ-.05f:b.waistZ+.02f)+ps.bob;
        float[] gd=ps.attack?X:norm(v(1,-.4f,-.1f));
        if(frame==Sprites.F_PAIN){gd=norm(v(.75f,-.25f,.35f));gz+=.05f;}
        float[] grip=v(ps.attack?.14f:.12f,.085f,gz);
        float[] fore=gun(m,grip,gd,shotgun,gunmetal,polymer,steel,wood,ps.flash);
        buildArm(m,b,ps,1,grip,0,b.hands,false,0);buildArm(m,b,ps,-1,fore,0,b.hands,false,0);
        return m;
    }
    /** Builds a long gun with its pistol grip at {@code grip}; returns where the support hand goes. */
    private static float[] gun(Model m,float[] grip,float[] d,boolean shotgun,int gunmetal,int polymer,int steel,int wood,boolean flash){
        float[][] f=frame(d);float[] zz=f[2];
        m.in(G_GEAR,0);
        float[] rc=add(add(grip,d,.07f),zz,.035f);   // receiver centre
        if(!shotgun){
            m.rbox(rc,d,.11f,.021f,.034f,.008f,gunmetal);                                         // receiver
            m.rbox(add(rc,d,.17f),d,.075f,.024f,.027f,.01f,polymer);                               // handguard
            m.cyl(add(rc,d,.24f),add(rc,d,.37f),.009f,steel);                                     // barrel
            m.cyl(add(rc,d,.35f),add(rc,d,.39f),.014f,gunmetal);                                  // muzzle brake
            m.rbox(add(add(rc,d,.02f),zz,-.07f),norm(add(d,zz,-.25f)),.022f,.015f,.05f,.006f,gunmetal);   // magazine
            m.rbox(add(add(rc,d,-.19f),zz,-.012f),d,.085f,.019f,.033f,.012f,polymer);             // stock
            m.rbox(add(rc,zz,.045f),d,.035f,.012f,.012f,.004f,gunmetal);                          // sight rail
        }else{
            m.rbox(rc,d,.09f,.022f,.032f,.008f,gunmetal);
            m.cyl(add(add(rc,d,.05f),zz,.012f),add(add(rc,d,.42f),zz,.012f),.013f,steel);           // barrel
            m.cyl(add(add(rc,d,.08f),zz,-.016f),add(add(rc,d,.38f),zz,-.016f),.012f,gunmetal);     // magazine tube
            m.rbox(add(add(rc,d,.24f),zz,-.018f),d,.075f,.024f,.022f,.012f,wood);                  // pump
            m.rbox(add(add(rc,d,-.2f),zz,-.02f),norm(add(d,zz,-.12f)),.11f,.022f,.038f,.014f,wood); // stock
        }
        m.rbox(add(add(grip,d,.0f),zz,-.005f),norm(add(d,zz,-1.4f)),.018f,.014f,.042f,.006f,shotgun?wood:polymer);   // pistol grip
        if(flash){m.in(G_FX,0);float[] mz=add(rc,d,shotgun?.47f:.43f);
            m.ellDir(add(mz,d,.04f),d,.07f,.045f,.045f,m.glow(0xffff9030));m.ellDir(add(mz,d,.02f),d,.035f,.025f,.025f,m.glow(0xfffff0c0));
            for(int k=0;k<3;k++){double a=k*2.1;m.cone(mz,add(add(mz,d,.04f),add(mul(f[1],(float)Math.cos(a)*.07f),mul(zz,(float)Math.sin(a)*.07f))),.016f,.003f,m.glow(0xffffb040));}}
        return add(add(rc,d,shotgun?.24f:.17f),zz,-.03f);
    }
    /** Imp: sinewy brown demon with bone spikes, claws and glowing eyes; throws fireballs. */
    static Model imp(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.hide(0xff7e4a2c,.75f);b.legs=b.skin;b.torso=b.skin;b.hands=b.skin;b.boots=b.skin;
        int bone=m.plastic(0xffd9cfb0,.35f),eye=m.glow(0xffffa01a),dark=m.hide(0xff5a3420,.8f),mouth=m.glow(0xffd04010);
        b.bulk=1.05f;b.chestW=.17f;b.shoulderW=.19f;b.headR=.1f;b.thighR=.075f;b.armR=.05f;b.upper=.28f;b.fore=.29f;
        if(!ps.dead&&frame!=Sprites.F_PAIN)ps.lean+=.32f;   // hunched predator stance
        buildTorso(m,b,ps);
        buildLeg(m,b,ps,-1,ps.lThigh+.12f,ps.lKnee+.25f,dark,false);buildLeg(m,b,ps,1,ps.rThigh+.12f,ps.rKnee+.25f,dark,false);
        float[] ld=leanDir(b);
        // abdominal muscles + ribs
        m.in(G_BODY,.02f);
        for(int k=0;k<3;k++)for(int s=-1;s<=1;s+=2)m.ellDir(leanPt(b,v(.075f,s*.035f,b.waistZ+.03f-k*.045f+ps.bob)),ld,.02f,.03f,.02f,b.skin);
        // spikes: shoulders, spine, elbows
        m.in(G_GEAR,0);
        for(int s=-1;s<=1;s+=2){float[] sh=leanPt(b,v(-.02f,s*(b.shoulderW+.02f),b.shoulderZ+.03f+ps.bob));m.cone(sh,add(sh,v(-.05f,s*.07f,.13f)),.022f,.002f,bone);
            float[] sh2=leanPt(b,v(-.05f,s*(b.shoulderW-.03f),b.shoulderZ+.02f+ps.bob));m.cone(sh2,add(sh2,v(-.08f,s*.04f,.09f)),.016f,.002f,bone);}
        for(int k=0;k<4;k++){float[] sp=leanPt(b,v(-.1f,0,b.chestZ+.1f-k*.075f+ps.bob));m.cone(sp,add(sp,v(-.09f,0,.05f)),.016f,.002f,bone);}
        buildFace(m,b,eye,true);
        float[] h=b.head,f=b.headF;
        // heavier brow, small horns, fangs
        m.in(G_BODY,.015f);m.ellDir(add(add(h,f,.068f),v(0,0,.03f)),f,.035f,.085f,.025f,dark);
        m.in(G_GEAR,0);
        for(int s=-1;s<=1;s+=2){float[] hb=add(h,v(-.01f,s*.06f,.07f));m.cone(hb,add(hb,v(-.07f,s*.04f,.08f)),.02f,.002f,bone);
            float[] fg=add(add(h,f,.085f),v(0,s*.018f,-.06f));m.cone(fg,add(fg,v(0,0,-.03f)),.006f,.001f,bone);}
        if(ps.attack)m.ellDir(add(add(h,f,.09f),v(0,0,-.06f)),f,.01f,.03f,.012f,mouth);
        float[] rHand=null,lHand=null;
        if(ps.attack){rHand=v(.1f,.2f,1.6f);lHand=leanPt(b,v(.3f,-.17f,.98f));
            m.in(G_FX,0);float[] fb=add(rHand,v(.04f,0,.13f));
            m.ell(fb,.12f,.12f,.12f,m.glow(0xffc0300c));m.ell(add(fb,v(.03f,0,0)),.085f,.085f,.085f,m.glow(0xffff8420));m.ell(add(fb,v(.06f,0,0)),.05f,.05f,.05f,m.glow(0xffffe890));}
        buildArm(m,b,ps,1,rHand,ps.rArmSwing+.35f,b.hands,true,bone);buildArm(m,b,ps,-1,lHand,ps.lArmSwing+.35f,b.hands,true,bone);
        if(ps.dead)corpse(m);
        return m;
    }
    /** Demon: hulking pink brute with a huge toothed jaw, horns and veined hide. */
    static Model demon(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.hide(0xffb85462,.55f);b.legs=b.skin;b.torso=b.skin;b.hands=b.skin;
        int teeth=m.plastic(0xffeee6cc,.6f),gum=m.skin(0xff6a1018,.4f),horn=m.plastic(0xffcfc19a,.35f),eye=m.glow(0xffff3a10),dark=m.hide(0xff8a3a46,.6f);
        b.hipZ=.56f;b.thigh=.27f;b.shin=.26f;b.thighR=.12f;b.kneeR=.085f;b.ankleR=.06f;b.hipW=.14f;b.pelvisZ=.6f;b.waistZ=.74f;
        b.chestZ=.9f;b.chestF=.2f;b.chestW=.26f;b.chestH=.22f;b.shoulderZ=1.02f;b.shoulderW=.3f;b.upper=.24f;b.fore=.23f;b.armR=.085f;b.elbowR=.068f;b.wristR=.055f;
        b.headZ=1.1f;b.headR=.13f;b.bulk=1.25f;
        if(ps.attack)ps.lean+=.28f;
        buildTorso(m,b,ps);
        buildLeg(m,b,ps,-1,ps.lThigh,ps.lKnee+.2f,dark,false);buildLeg(m,b,ps,1,ps.rThigh,ps.rKnee+.2f,dark,false);
        float[] ld=leanDir(b);
        m.in(G_BODY,.06f);
        m.ellDir(leanPt(b,v(-.06f,0,b.chestZ+.1f+ps.bob)),ld,.17f,.23f,.17f,b.skin);       // back hump
        m.in(G_BODY,.03f);
        for(int s2=-1;s2<=1;s2+=2)m.ellDir(leanPt(b,v(.15f,s2*.1f,b.chestZ+.04f+ps.bob)),ld,.07f,.11f,.09f,b.skin);   // pecs
        for(int k=0;k<3;k++)for(int s2=-1;s2<=1;s2+=2)m.ellDir(leanPt(b,v(.14f,s2*.05f,b.waistZ+.06f-k*.06f+ps.bob)),ld,.03f,.045f,.028f,b.skin);   // abs
        m.in(G_BODY,.06f);
        for(int s=-1;s<=1;s+=2)m.ellDir(leanPt(b,v(.0f,s*.26f,b.shoulderZ+.02f+ps.bob)),ld,.12f,.11f,.1f,b.skin);   // massive shoulders
        // head: forward-thrust skull, horns, eyes and a big jaw that opens wide on attack
        float[] fh=leanPt(b,v(b.chestF*.75f,0,b.headZ+ps.bob));
        m.in(G_BODY,.035f);m.ellDir(fh,X,.15f,.19f,.12f,b.skin);m.ellDir(add(fh,v(.1f,0,.06f)),X,.06f,.15f,.035f,dark);
        m.in(G_GEAR,0);
        for(int s=-1;s<=1;s+=2){m.ell(add(fh,v(.13f,s*.07f,.035f)),.02f,.02f,.016f,eye);
            float[] hb=add(fh,v(0,s*.14f,.07f));m.cone(hb,add(hb,v(.05f,s*.07f,.1f)),.03f,.003f,horn);}
        float open=ps.attack?.5f:ps.dead?.3f:.1f;
        float[] piv=add(fh,v(.03f,0,-.05f));float[] uj=add(piv,v(.16f,0,-.01f));
        float[] lowD=norm(v((float)Math.cos(open),0,-(float)Math.sin(open)));float[] lj=add(piv,lowD,.16f);
        m.in(G_BODY,.03f);m.ellDir(uj,X,.15f,.17f,.055f,b.skin);m.ellDir(lj,lowD,.15f,.15f,.05f,b.skin);
        m.in(G_GEAR,0);m.ellDir(add(lerp(uj,lj,.5f),v(-.03f,0,0)),X,.11f,.12f,.03f+open*.08f,gum);
        for(int k=-3;k<=3;k++){float yy=k*.04f;float fx=.11f-Math.abs(k)*.018f;
            m.cone(add(uj,v(fx,yy,-.04f)),add(uj,v(fx,yy,-.1f)),.013f,.002f,teeth);
            float[] lt=add(lj,lowD,fx-.02f);m.cone(add(lt,v(0,yy,.03f)),add(lt,v(0,yy,.085f)),.012f,.002f,teeth);}
        float[] rHand=ps.attack?leanPt(b,v(.36f,.24f,.74f)):null,lHand=ps.attack?leanPt(b,v(.36f,-.24f,.74f)):null;
        buildArm(m,b,ps,1,rHand,ps.rArmSwing*.6f+.2f,b.hands,true,horn);buildArm(m,b,ps,-1,lHand,ps.lArmSwing*.6f+.2f,b.hands,true,horn);
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
        Model m=new Model();
        switch(id){
            case Sprites.MEDKIT:{int w=m.mat(0xffe9e9e4,.35f,.06f),red=m.mat(0xffd8262a,.3f,.04f),gr=m.mat(0xff8e9296,.6f,.05f);
                m.box(v(0,0,.16f),.2f,.32f,.15f,w);m.box(v(.205f,0,.17f),.006f,.05f,.11f,red);m.box(v(.205f,0,.17f),.006f,.17f,.035f,red);
                m.box(v(0,0,.33f),.04f,.1f,.025f,gr);break;}
            case Sprites.STIMPACK:{int w=m.mat(0xffe0e0dc,.3f,.06f),red=m.mat(0xffd8262a,.3f,.04f);
                m.box(v(0,0,.11f),.11f,.16f,.11f,w);m.box(v(.115f,0,.12f),.006f,.03f,.075f,red);m.box(v(.115f,0,.12f),.006f,.1f,.022f,red);break;}
            case Sprites.CLIP:{int dk=m.mat(0xff4c5156,.7f,.05f),brass=m.mat(0xffd2a640,.9f,.03f);
                m.box(v(0,0,.18f),.06f,.11f,.18f,dk);for(int k=-1;k<=1;k++)m.cap(v(0,k*.045f,.36f),v(0,k*.045f,.42f),.02f,brass);break;}
            case Sprites.SHELLS:{int red=m.mat(0xffc4281c,.4f,.06f),brass=m.mat(0xffd8b050,.9f,.03f);
                for(int k=0;k<4;k++){float y=-.18f+k*.12f;m.cyl(v(0,y,0),v(0,y,.2f),.05f,red);m.cyl(v(0,y,0),v(0,y,.06f),.052f,brass);}break;}
            case Sprites.ROCKETBOX:{int ol=m.mat(0xff4f6034,.3f,.12f),dk=m.mat(0xff2e3524,.2f,.1f),tip=m.mat(0xffb03024,.5f,.05f),body=m.mat(0xff9aa0a4,.7f,.05f);
                m.box(v(0,0,.15f),.18f,.32f,.15f,ol);m.box(v(.185f,0,.15f),.005f,.25f,.03f,dk);
                for(int k=0;k<4;k++){float y=-.22f+k*.147f;m.cyl(v(0,y,.28f),v(0,y,.48f),.04f,body);m.cap(v(0,y,.48f),v(0,y,.52f),.035f,tip);}break;}
            case Sprites.ARMOR:{int g=m.mat(0xff3cb050,.6f,.08f),dg=m.mat(0xff22803a,.45f,.08f),strap=m.mat(0xff3a3020,.1f,.1f),hole=m.mat(0xff10200f,0,0);
                // armour vest standing on the floor: chest plate, shoulder guards, collar and straps
                m.box(v(0,0,.3f),.09f,.24f,.28f,g);m.ellDir(v(.02f,0,.32f),X,.1f,.25f,.3f,g);
                m.box(v(.1f,0,.36f),.015f,.16f,.16f,dg);m.box(v(.1f,0,.12f),.012f,.2f,.05f,dg);
                for(int s=-1;s<=1;s+=2){m.ell(v(0,s*.25f,.56f),.1f,.08f,.06f,dg);m.box(v(.09f,s*.17f,.3f),.012f,.02f,.26f,strap);}
                m.ellDir(v(.03f,0,.6f),X,.06f,.1f,.025f,hole);break;}
            case Sprites.SHOTGUN_PICKUP:case Sprites.CHAINGUN_PICKUP:case Sprites.LAUNCHER_PICKUP:{
                int metal=m.mat(0xff5c6268,.9f,.04f),dark=m.mat(0xff2b2d31,.5f,.03f),wood=m.mat(0xff7a4a26,.2f,.16f),ol=m.mat(0xff4c6a3a,.4f,.1f);
                float[] dir=Y; // lying sideways so the whole gun reads
                if(id==Sprites.SHOTGUN_PICKUP){m.box(v(0,-.12f,.06f),dir,.3f,.04f,.04f,wood);m.cyl(v(0,.1f,.08f),v(0,.62f,.08f),.03f,dark);m.box(v(0,.25f,.05f),dir,.12f,.035f,.03f,wood);}
                else if(id==Sprites.CHAINGUN_PICKUP){for(int k=0;k<3;k++){float z=.05f+k*.04f;m.cyl(v(0,.0f,z),v(0,.62f,z),.022f,metal);}m.box(v(0,-.08f,.1f),dir,.18f,.09f,.09f,dark);m.box(v(.08f,-.12f,.03f),dir,.05f,.05f,.06f,dark);}
                else{m.cyl(v(0,-.3f,.1f),v(0,.45f,.1f),.09f,ol);m.cyl(v(0,.42f,.1f),v(0,.47f,.1f),.1f,dark);m.box(v(.06f,-.05f,.02f),dir,.08f,.03f,.06f,dark);}
                break;}
            case Sprites.KEY_BLUE:case Sprites.KEY_YELLOW:case Sprites.KEY_RED:{
                int c=id==Sprites.KEY_BLUE?0xff3a70ff:id==Sprites.KEY_YELLOW?0xffffd030:0xffff3030;int k=m.glow(c),wh=m.mat(0xfff0f0f0,.5f,0);
                m.box(v(0,0,.3f),.025f,.16f,.24f,k);m.box(v(.027f,0,.4f),.004f,.09f,.05f,wh);m.box(v(.027f,0,.2f),.004f,.11f,.012f,m.mat(0xff202020,0,0));break;}
            case Sprites.BARREL:{int body=m.mat(0xff4e6644,.55f,.12f),band=m.mat(0xff36452f,.6f,.08f),slime=m.glow(0xff68e848),rust=m.mat(0xff6a5034,.2f,.3f);
                m.cyl(v(0,0,0),v(0,0,.9f),.3f,body);m.cyl(v(0,0,.2f),v(0,0,.26f),.305f,band);m.cyl(v(0,0,.66f),v(0,0,.72f),.305f,band);
                m.cyl(v(0,0,.88f),v(0,0,.92f),.27f,slime);m.ell(v(.27f,.08f,.45f),.04f,.08f,.06f,rust);break;}
            case Sprites.TORCH:{int pole=m.mat(0xff6a5440,.2f,.18f),iron=m.mat(0xff3c3c40,.7f,.06f);int fl=frame&1;
                m.cyl(v(0,0,0),v(0,0,1.0f),.035f,pole);m.cyl(v(0,0,0),v(0,0,.05f),.12f,iron);m.cyl(v(0,0,.98f),v(0,0,1.08f),.08f,iron);
                // layered flame: outer tongue, hot core and white heart pushed toward the viewer, flicker on alternate frames
                m.ell(v(0,0,1.2f),.1f,.1f,.12f,m.glow(0xffe0501a));
                m.cap(v(0,0,1.22f),v(fl*.02f,(fl*2-1)*.025f,1.42f+fl*.05f),.055f,m.glow(0xffff7a20));
                m.ell(v(.06f,0,1.2f),.06f,.06f,.09f,m.glow(0xffffc040));m.ell(v(.1f,0,1.18f),.03f,.03f,.05f,m.glow(0xfffff4c0));break;}
            case Sprites.COLUMN:{int mar=m.mat(0xffa8aeb0,.5f,.12f),dk=m.mat(0xff7e8688,.4f,.1f);
                m.box(v(0,0,.06f),.26f,.26f,.06f,dk);m.box(v(0,0,1.56f),.26f,.26f,.06f,dk);m.cyl(v(0,0,.12f),v(0,0,1.5f),.18f,mar);
                for(int k=0;k<8;k++){double a=k*Math.PI/4;m.cyl(v((float)Math.cos(a)*.17f,(float)Math.sin(a)*.17f,.14f),v((float)Math.cos(a)*.17f,(float)Math.sin(a)*.17f,1.48f),.03f,dk);}break;}
            case Sprites.SWITCH:case Sprites.SWITCH_ON:{int pan=m.mat(0xff5c6068,.5f,.1f),dk=m.mat(0xff2c3036,.3f,.05f),lev=m.mat(0xffa0a4a8,.8f,.04f);
                boolean on=id==Sprites.SWITCH_ON;int lamp=m.glow(on?0xff40e050:0xffe03424);
                m.box(v(0,0,.5f),.06f,.28f,.48f,pan);m.box(v(.065f,0,.52f),.01f,.18f,.26f,dk);
                float[] base=v(.08f,0,.52f);m.cap(base,add(base,v(.08f,0,on?-.18f:.18f)),.03f,lev);m.ell(add(base,v(.08f,0,on?-.2f:.2f)),.05f,.05f,.05f,lamp);break;}
            case Sprites.LAMP:{int post=m.mat(0xff606468,.7f,.06f),glass=m.glow(0xffeef2ff);
                m.cyl(v(0,0,0),v(0,0,.06f),.16f,post);m.cyl(v(0,0,.06f),v(0,0,1.1f),.04f,post);m.cyl(v(0,0,1.08f),v(0,0,1.14f),.12f,post);
                m.ell(v(0,0,1.28f),.15f,.15f,.16f,glass);m.ell(v(0,0,1.28f),.09f,.09f,.1f,m.glow(0xffffffff));break;}
            default:m.box(v(0,0,.2f),.2f,.2f,.2f,m.mat(0xffff00ff,0,0));
        }
        return m;
    }
}
