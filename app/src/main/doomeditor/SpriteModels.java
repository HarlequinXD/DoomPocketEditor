package com.retro.doomeditor;

import java.util.ArrayList;

/**
 * Built-in sprites are pre-rendered from small 3D models, the way Doom's sprites were photographed from
 * clay models: each character is built from capsules, ellipsoids, boxes and cylinders on a simple
 * skeleton, posed per animation frame and ray-cast from 8 directions with pixel-art shading
 * (6-tone colour ramps with warm highlights and cool shadows, ordered dithering, surface grit,
 * crisp dark outlines and inner edge lines). Everything is procedural, so nothing ships as images.
 *
 * Model space: x = forward (the way the entity faces), y = its right, z = up, 1 unit = 1 grid square.
 */
final class SpriteModels {
    private SpriteModels(){}

    // ------------------------------------------------------------------ materials
    static final class Mat {
        final int[] ramp;final boolean glow;final float spec,grit;
        Mat(int base,float spec,float grit,boolean glow){ramp=ramp(base,glow);this.spec=spec;this.grit=grit;this.glow=glow;}
    }
    private static final int TONES=6;
    private static final float[] TONE_MUL={.30f,.48f,.66f,.84f,1f,1.2f};
    /** 6-tone ramp: shadows drift toward cool violet, highlights toward warm cream (classic pixel-art hue shifting). */
    static int[] ramp(int base,boolean glow){
        int[] r=new int[TONES];int br=(base>>16)&255,bg=(base>>8)&255,bb=base&255;
        for(int i=0;i<TONES;i++){
            float m=glow?.75f+i*.08f:TONE_MUL[i];
            float cr=br*m,cg=bg*m,cb=bb*m;
            if(m<1){float k=(1-m)*.35f;cr+=(40*m-cr)*k*.4f;cg+=(32*m-cg)*k*.4f;cb+=(70*m-cb)*k*.6f;cb+=10*(1-m);}
            else{float k=(m-1)*.9f;cr+=(255-cr)*k;cg+=(244-cg)*k;cb+=(210-cb)*k;}
            r[i]=Textures.rgb(cr,cg,cb);
        }
        return r;
    }

    // ------------------------------------------------------------------ primitives
    static final int ELL=0,CAP=1,BOX=2,CYL=3;
    static final class Prim {
        int type,mat;
        final float[] c=new float[3],a=new float[3],b=new float[3];   // centre / segment ends
        final float[][] ax=new float[3][3];final float[] h=new float[3]; // local axes + radii/half sizes
        float r;
        // per-render bounding circle in screen space
        float bx,by,br;
    }
    /** A posed model: primitives plus their materials. */
    static final class Model {
        final ArrayList<Prim> prims=new ArrayList<>();
        final ArrayList<Mat> mats=new ArrayList<>();
        int mat(int base,float spec,float grit){mats.add(new Mat(base,spec,grit,false));return mats.size()-1;}
        int glow(int base){mats.add(new Mat(base,0,0,true));return mats.size()-1;}
        Prim ell(float[] c,float rx,float ry,float rz,int m){return ellAxes(c,X,Y,Z,rx,ry,rz,m);}
        /** Ellipsoid whose local x axis points along {@code dir}. */
        Prim ellDir(float[] c,float[] dir,float rx,float ry,float rz,int m){float[][] f=frame(dir);return ellAxes(c,f[0],f[1],f[2],rx,ry,rz,m);}
        Prim ellAxes(float[] c,float[] u,float[] v,float[] w,float rx,float ry,float rz,int m){
            Prim p=new Prim();p.type=ELL;p.mat=m;set(p.c,c);set(p.ax[0],u);set(p.ax[1],v);set(p.ax[2],w);p.h[0]=rx;p.h[1]=ry;p.h[2]=rz;prims.add(p);return p;}
        Prim cap(float[] a,float[] b,float r,int m){Prim p=new Prim();p.type=CAP;p.mat=m;set(p.a,a);set(p.b,b);p.r=r;mid(p.c,a,b);prims.add(p);return p;}
        Prim cyl(float[] a,float[] b,float r,int m){Prim p=new Prim();p.type=CYL;p.mat=m;set(p.a,a);set(p.b,b);p.r=r;mid(p.c,a,b);prims.add(p);return p;}
        /** Box whose local x axis points along {@code dir}. */
        Prim box(float[] c,float[] dir,float hx,float hy,float hz,int m){float[][] f=frame(dir);Prim p=new Prim();p.type=BOX;p.mat=m;set(p.c,c);
            set(p.ax[0],f[0]);set(p.ax[1],f[1]);set(p.ax[2],f[2]);p.h[0]=hx;p.h[1]=hy;p.h[2]=hz;prims.add(p);return p;}
        Prim box(float[] c,float hx,float hy,float hz,int m){return box(c,X,hx,hy,hz,m);}
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

    // ------------------------------------------------------------------ ray casting
    private static final float[] tmpN=new float[3];
    /** Returns hit distance (normal in tmpN) or -1. */
    private static float hit(Prim p,float[] o,float[] d){
        switch(p.type){
            case ELL:{
                float ox=o[0]-p.c[0],oy=o[1]-p.c[1],oz=o[2]-p.c[2];
                float[] u=p.ax[0],vv=p.ax[1],w=p.ax[2];
                float lo0=(ox*u[0]+oy*u[1]+oz*u[2])/p.h[0],lo1=(ox*vv[0]+oy*vv[1]+oz*vv[2])/p.h[1],lo2=(ox*w[0]+oy*w[1]+oz*w[2])/p.h[2];
                float ld0=(d[0]*u[0]+d[1]*u[1]+d[2]*u[2])/p.h[0],ld1=(d[0]*vv[0]+d[1]*vv[1]+d[2]*vv[2])/p.h[1],ld2=(d[0]*w[0]+d[1]*w[1]+d[2]*w[2])/p.h[2];
                float A=ld0*ld0+ld1*ld1+ld2*ld2,B=lo0*ld0+lo1*ld1+lo2*ld2,C=lo0*lo0+lo1*lo1+lo2*lo2-1,H=B*B-A*C;
                if(H<0)return -1;float t=(-B-(float)Math.sqrt(H))/A;if(t<0)return -1;
                float q0=(lo0+ld0*t)/p.h[0],q1=(lo1+ld1*t)/p.h[1],q2=(lo2+ld2*t)/p.h[2];
                tmpN[0]=q0*u[0]+q1*vv[0]+q2*w[0];tmpN[1]=q0*u[1]+q1*vv[1]+q2*w[1];tmpN[2]=q0*u[2]+q1*vv[2]+q2*w[2];normalize(tmpN);return t;}
            case CAP:{
                float bax=p.b[0]-p.a[0],bay=p.b[1]-p.a[1],baz=p.b[2]-p.a[2],oax=o[0]-p.a[0],oay=o[1]-p.a[1],oaz=o[2]-p.a[2];
                float baba=bax*bax+bay*bay+baz*baz,bard=bax*d[0]+bay*d[1]+baz*d[2],baoa=bax*oax+bay*oay+baz*oaz,rdoa=d[0]*oax+d[1]*oay+d[2]*oaz,oaoa=oax*oax+oay*oay+oaz*oaz;
                float a=baba-bard*bard,b=baba*rdoa-baoa*bard,c=baba*oaoa-baoa*baoa-p.r*p.r*baba,hh=b*b-a*c;
                if(hh>=0&&a>1e-8f){float t=(-b-(float)Math.sqrt(hh))/a,y=baoa+t*bard;
                    if(y>0&&y<baba&&t>0){float k=y/baba;tmpN[0]=oax+t*d[0]-bax*k;tmpN[1]=oay+t*d[1]-bay*k;tmpN[2]=oaz+t*d[2]-baz*k;normalize(tmpN);return t;}}
                // end caps
                float best=-1;
                for(int e=0;e<2;e++){float[] cc=e==0?p.a:p.b;float ocx=o[0]-cc[0],ocy=o[1]-cc[1],ocz=o[2]-cc[2];
                    float bb=d[0]*ocx+d[1]*ocy+d[2]*ocz,c2=ocx*ocx+ocy*ocy+ocz*ocz-p.r*p.r,h2=bb*bb-c2;if(h2<0)continue;
                    float t=-bb-(float)Math.sqrt(h2);if(t>0&&(best<0||t<best)){best=t;tmpN[0]=ocx+t*d[0];tmpN[1]=ocy+t*d[1];tmpN[2]=ocz+t*d[2];}}
                if(best>0)normalize(tmpN);return best;}
            case CYL:{
                float bax=p.b[0]-p.a[0],bay=p.b[1]-p.a[1],baz=p.b[2]-p.a[2],ocx=o[0]-p.a[0],ocy=o[1]-p.a[1],ocz=o[2]-p.a[2];
                float baba=bax*bax+bay*bay+baz*baz,bard=bax*d[0]+bay*d[1]+baz*d[2],baoc=bax*ocx+bay*ocy+baz*ocz;
                float k2=baba-bard*bard,k1=baba*(ocx*d[0]+ocy*d[1]+ocz*d[2])-baoc*bard,k0=baba*(ocx*ocx+ocy*ocy+ocz*ocz)-baoc*baoc-p.r*p.r*baba;
                float hh=k1*k1-k2*k0;if(hh<0)return -1;hh=(float)Math.sqrt(hh);
                if(k2>1e-8f){float t=(-k1-hh)/k2,y=baoc+t*bard;
                    if(y>0&&y<baba&&t>0){float k=y/baba;tmpN[0]=ocx+t*d[0]-bax*k;tmpN[1]=ocy+t*d[1]-bay*k;tmpN[2]=ocz+t*d[2]-baz*k;normalize(tmpN);return t;}
                    float t2=((y<0?0:baba)-baoc)/bard;
                    if(Math.abs(k1+k2*t2)<hh&&t2>0){float s=y<0?-1:1;float l=(float)Math.sqrt(baba);tmpN[0]=bax*s/l;tmpN[1]=bay*s/l;tmpN[2]=baz*s/l;return t2;}}
                else if(Math.abs(bard)>1e-6f){ // looking straight down the axis: hit a cap disc
                    float t2=((bard>0?0:baba)-baoc)/bard;if(t2>0){float px=ocx+d[0]*t2,py=ocy+d[1]*t2,pz=ocz+d[2]*t2,y=(px*bax+py*bay+pz*baz)/baba;
                        float qx=px-bax*y,qy=py-bay*y,qz=pz-baz*y;if(qx*qx+qy*qy+qz*qz<p.r*p.r){float s=bard>0?-1:1,l=(float)Math.sqrt(baba);tmpN[0]=bax*s/l;tmpN[1]=bay*s/l;tmpN[2]=baz*s/l;return t2;}}}
                return -1;}
            default:{ // oriented box
                float ox=o[0]-p.c[0],oy=o[1]-p.c[1],oz=o[2]-p.c[2];float tn=-1e9f,tf=1e9f;int axis=0;float sgn=1;
                for(int i=0;i<3;i++){float[] u=p.ax[i];float lo=ox*u[0]+oy*u[1]+oz*u[2],ld=d[0]*u[0]+d[1]*u[1]+d[2]*u[2];
                    if(Math.abs(ld)<1e-9f){if(Math.abs(lo)>p.h[i])return -1;continue;}
                    float t1=(-p.h[i]-lo)/ld,t2=(p.h[i]-lo)/ld;float s=-1;if(t1>t2){float tt=t1;t1=t2;t2=tt;s=1;}
                    if(t1>tn){tn=t1;axis=i;sgn=ld>0?-1:1;}if(t2<tf)tf=t2;if(tn>tf)return -1;}
                if(tn<0)return -1;float[] u=p.ax[axis];tmpN[0]=u[0]*sgn;tmpN[1]=u[1]*sgn;tmpN[2]=u[2]*sgn;return tn;}
        }
    }
    private static void normalize(float[] n){float l=(float)Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);if(l>1e-9f){n[0]/=l;n[1]/=l;n[2]/=l;}}
    private static float boundRadius(Prim p){
        switch(p.type){case ELL:case BOX:{float m=Math.max(p.h[0],Math.max(p.h[1],p.h[2]));return p.type==BOX?m*1.75f:m;}
            default:return len(sub(p.b,p.a))*.5f+p.r;}
    }
    private static final int[] BAYER={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};

    /**
     * Renders a model seen from {@code yaw} radians around it (0 = from the front, +PI/2 = from its right),
     * {@code elev} radians above the horizon. The result is cropped so the lowest pixel sits on the floor and
     * the model's origin stays horizontally centred.
     */
    static synchronized Sprites.Sprite render(Model m,float yaw,float elev,float ppu,boolean bright,int seed){
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw),ce=(float)Math.cos(elev),se=(float)Math.sin(elev);
        float[] dH=v(-cy,-sy,0);                       // horizontal look direction (toward the model)
        float[] u=v(sy,-cy,0);                          // screen right
        float[] w=v(dH[0]*ce,dH[1]*ce,-se);            // view direction (looking slightly down)
        float[] up=v(dH[0]*se,dH[1]*se,ce);            // screen up
        float[] L=norm(add(add(mul(u,-.55f),mul(up,.72f)),mul(w,-.5f)));   // key light: upper left, in front
        float[] Hh=norm(sub(L,w));
        // screen-space extent
        float minx=1e9f,maxx=-1e9f,miny=1e9f,maxy=-1e9f;
        for(Prim p:m.prims){float px=dot(p.c,u),py=dot(p.c,up),br=boundRadius(p);p.bx=px;p.by=py;p.br=br;
            minx=Math.min(minx,px-br);maxx=Math.max(maxx,px+br);miny=Math.min(miny,py-br);maxy=Math.max(maxy,py+br);}
        float half=Math.max(Math.abs(minx),Math.abs(maxx))+2f/ppu;
        int W=Math.max(2,(int)Math.ceil(half*ppu)*2),top=(int)Math.ceil(maxy*ppu)+2,bottom=(int)Math.floor(miny*ppu)-2,H=Math.max(2,top-bottom);
        int n=W*H;float[] depth=new float[n];int[] mat=new int[n];int[] tone=new int[n];java.util.Arrays.fill(mat,-1);
        float[] o=new float[3];float far=6;
        for(int py=0;py<H;py++){float sy2=(top-py-.5f)/ppu;
            for(int px=0;px<W;px++){float sx=(px+.5f-W*.5f)/ppu;
                o[0]=u[0]*sx+up[0]*sy2-w[0]*far;o[1]=u[1]*sx+up[1]*sy2-w[1]*far;o[2]=u[2]*sx+up[2]*sy2-w[2]*far;
                float best=1e9f;int bm=-1;float nx=0,ny=0,nz=0;
                for(int i=0,sz=m.prims.size();i<sz;i++){Prim p=m.prims.get(i);float ddx=sx-p.bx,ddy=sy2-p.by;if(ddx*ddx+ddy*ddy>p.br*p.br)continue;
                    float t=hit(p,o,w);if(t>0&&t<best){best=t;bm=p.mat;nx=tmpN[0];ny=tmpN[1];nz=tmpN[2];}}
                if(bm<0)continue;
                int idx=py*W+px;depth[idx]=best;mat[idx]=bm;
                Mat mt=m.mats.get(bm);
                if(mt.glow){tone[idx]=TONES-1-(int)(Math.max(0,-(nx*w[0]+ny*w[1]+nz*w[2]))<.5f?1:0);continue;}
                float diff=Math.max(0,nx*L[0]+ny*L[1]+nz*L[2]);
                float hz0=o[2]+w[2]*best;
                float I=.18f+.92f*diff;
                I*=.78f+.22f*Math.min(1,Math.max(0,hz0/.55f));   // soft occlusion toward the floor
                float facing=Math.abs(nx*w[0]+ny*w[1]+nz*w[2]);I+=.10f*(float)Math.pow(1-facing,3);
                if(mt.spec>0){float sp=Math.max(0,nx*Hh[0]+ny*Hh[1]+nz*Hh[2]);I+=mt.spec*(float)Math.pow(sp,18);}
                // grit stuck to the model surface (same on every view)
                float hx=o[0]+w[0]*best,hy=o[1]+w[1]*best,hz=hz0;
                if(mt.grit>0)I+=(Textures.hash((int)Math.floor(hx*ppu*.5f),(int)Math.floor(hy*ppu*.5f)*73+(int)Math.floor(hz*ppu*.5f)*131,seed+bm)-.5f)*mt.grit;
                float tv=I*(TONES-1)+(BAYER[(py&3)*4+(px&3)]/16f-.47f)*.42f;
                tone[idx]=Math.max(0,Math.min(TONES-1,Math.round(tv)));
            }}
        // inner edges: darken the farther side of a depth step so limbs read against the body
        int[] t2=tone.clone();
        for(int py=0;py<H;py++)for(int px=0;px<W;px++){int i=py*W+px;if(mat[i]<0||m.mats.get(mat[i]).glow)continue;
            for(int k=0;k<2;k++){int j=k==0?(px+1<W?i+1:-1):(py+1<H?i+W:-1);if(j<0||mat[j]<0)continue;
                float dd=depth[j]-depth[i];
                if(dd>.09f)t2[j]=Math.max(0,tone[j]-2);else if(dd<-.09f)t2[i]=Math.max(0,tone[i]-2);}}
        // compose + crisp outline
        int[] col=new int[n];
        for(int i=0;i<n;i++)if(mat[i]>=0)col[i]=m.mats.get(mat[i]).ramp[t2[i]];
        int[] out=col.clone();
        for(int py=0;py<H;py++)for(int px=0;px<W;px++){int i=py*W+px;if(mat[i]>=0)continue;
            int nb=-1;if(px>0&&mat[i-1]>=0)nb=i-1;else if(px<W-1&&mat[i+1]>=0)nb=i+1;else if(py>0&&mat[i-W]>=0)nb=i-W;else if(py<H-1&&mat[i+W]>=0)nb=i+W;
            if(nb>=0){Mat mt=m.mats.get(mat[nb]);out[i]=mt.glow?0:shade(mt.ramp[0],.42f)|0xff000000;}}
        // crop: lowest/highest opaque rows, symmetric columns
        int r0=H,r1=-1,c0=W,c1=-1;
        for(int py=0;py<H;py++)for(int px=0;px<W;px++)if((out[py*W+px]>>>24)!=0){r0=Math.min(r0,py);r1=Math.max(r1,py);c0=Math.min(c0,px);c1=Math.max(c1,px);}
        if(r1<0){Sprites.Sprite e=new Sprites.Sprite(2,2,bright);e.ppu=ppu;return e;}
        int halfW=Math.max(W/2-c0,c1+1-W/2),nw=halfW*2,nh=r1-r0+1,x0=W/2-halfW;
        Sprites.Sprite s=new Sprites.Sprite(nw,nh,bright);s.ppu=ppu;
        for(int py=0;py<nh;py++)for(int px=0;px<nw;px++){int sxp=x0+px;if(sxp<0||sxp>=W)continue;s.px[py*nw+px]=out[(r0+py)*W+sxp];}
        return s;
    }
    private static int shade(int c,float f){return Textures.rgb(((c>>16)&255)*f,((c>>8)&255)*f,(c&255)*f);}

    // ------------------------------------------------------------------ characters
    /** Pose for one animation frame. Angles in radians. */
    static final class Pose {
        float lean,headPitch,headTurn,crouch,bob;
        float lThigh,lKnee,rThigh,rKnee;                 // + = forward swing / bend
        float[] lHand,rHand;                              // hand targets (null = hang)
        float lArmSwing,rArmSwing;
        boolean attack,flash,open;
    }
    static Pose pose(int frame){
        Pose p=new Pose();
        switch(frame){
            case Sprites.F_WALK1:p.lThigh=.45f;p.lKnee=.15f;p.rThigh=-.35f;p.rKnee=.55f;p.lArmSwing=-.4f;p.rArmSwing=.4f;p.bob=-.02f;p.lean=.06f;break;
            case Sprites.F_WALK2:p.rThigh=.45f;p.rKnee=.15f;p.lThigh=-.35f;p.lKnee=.55f;p.lArmSwing=.4f;p.rArmSwing=-.4f;p.bob=-.02f;p.lean=.06f;break;
            case Sprites.F_ATTACK:p.attack=true;p.flash=true;p.lThigh=.18f;p.rThigh=-.12f;p.rKnee=.12f;p.lean=-.04f;break;
            case Sprites.F_PAIN:p.lean=-.32f;p.headPitch=-.35f;p.lThigh=.1f;p.rThigh=-.05f;p.lKnee=.2f;p.rKnee=.25f;p.lArmSwing=.7f;p.rArmSwing=.6f;break;
            default:p.lThigh=.04f;p.rThigh=-.04f;p.lKnee=.06f;p.rKnee=.06f;break;
        }
        return p;
    }
    /** Shared humanoid build: proportions and materials vary per character. */
    static final class Body {
        float hipZ=.70f,hipW=.11f,thigh=.36f,shin=.36f,legR=.075f,footL=.13f;
        float torsoZ=.97f,torsoF=.13f,torsoW=.2f,torsoH=.24f,shoulderZ=1.13f,shoulderW=.22f,upper=.27f,fore=.25f,armR=.058f,handR=.052f;
        float headZ=1.3f,headR=.105f;
        int skin,pants,top,boots,glove,eye;      // material ids
        float[] hipC,chest,neck,head;float leanA;
    }
    private static float[] leanPt(Body b,float[] p){
        // rotate the upper body around the hips (lean forward/back)
        float[] piv=v(0,0,b.hipZ);float cs=(float)Math.cos(-b.leanA),sn=(float)Math.sin(-b.leanA);float[] q=p.clone();rotY(q,piv,cs,sn,null);return q;
    }
    private static float[] buildLegs(Model m,Body b,Pose ps,float side,float thighA,float kneeA){
        float[] hip=v(0,side*b.hipW,b.hipZ+ps.bob);
        float[] kneeDir=limbDir(thighA,side*.04f);float[] knee=add(hip,mul(kneeDir,b.thigh));
        float[] shinDir=limbDir(thighA-kneeA,side*.02f);float[] ankle=add(knee,mul(shinDir,b.shin));
        float lift=Math.max(0,-ankle[2]+.05f);if(ankle[2]<.05f){ankle[2]=.05f;}
        m.cap(hip,knee,b.legR,b.pants);m.cap(knee,ankle,b.legR*.88f,b.pants);
        m.ell(add(knee,v(.035f,0,0)),b.legR*.85f,b.legR*.95f,b.legR*.9f,b.pants);   // knee
        m.ellDir(add(ankle,v(.05f,0,-.02f)),X,b.footL,.055f,.05f,b.boots);
        m.cap(add(ankle,v(0,0,.02f)),add(ankle,v(0,0,.13f)),b.legR*.95f,b.boots);   // boot shaft
        return ankle;
    }
    private static void buildArm(Model m,Body b,Pose ps,float side,float[] handTarget,float swing){buildArmGet(m,b,ps,side,handTarget,swing);}
    private static float[] buildArmGet(Model m,Body b,Pose ps,float side,float[] handTarget,float swing){
        float[] sh=leanPt(b,v(0,side*b.shoulderW,b.shoulderZ+ps.bob));
        float[] hand;
        if(handTarget!=null)hand=handTarget;
        else{float[] d=limbDir(swing,side*.12f);hand=add(sh,mul(d,(b.upper+b.fore)*.94f));}
        float[] elbow=ik(sh,hand,b.upper,b.fore,v(-.6f,side*.6f,-.4f));
        m.cap(sh,elbow,b.armR,b.top);m.cap(elbow,hand,b.armR*.9f,b.glove==b.skin?b.skin:b.top);
        m.ell(hand,b.handR,b.handR,b.handR,b.glove);
        m.ell(sh,b.armR*1.5f,b.armR*1.5f,b.armR*1.35f,b.top);   // shoulder
        return hand;
    }
    private static void buildTorso(Model m,Body b,Pose ps){
        b.leanA=ps.lean;
        float[] pelvis=v(0,0,b.hipZ+.04f+ps.bob);
        m.ell(pelvis,b.torsoF*.95f,b.hipW+.07f,.1f,b.pants);
        b.chest=leanPt(b,v(0,0,b.torsoZ+ps.bob));
        m.ellDir(b.chest,leanDir(b),b.torsoF,b.torsoW,b.torsoH,b.top);
        b.neck=leanPt(b,v(.01f,0,b.headZ-b.headR-.02f+ps.bob));
        m.cap(leanPt(b,v(0,0,b.shoulderZ-.04f+ps.bob)),b.neck,.05f,b.skin);
        float hp=ps.headPitch;
        b.head=add(b.neck,v((float)Math.sin(b.leanA+hp)*b.headR*1.1f,0,(float)Math.cos(b.leanA+hp)*b.headR*1.1f));
    }
    private static float[] leanDir(Body b){return v((float)Math.cos(b.leanA),0,-(float)Math.sin(b.leanA));}
    /** Forward look direction of the head (pitch includes the body lean). */
    private static float[] headDir(Body b,Pose ps){float a=b.leanA+ps.headPitch;return v((float)Math.cos(a),0,-(float)Math.sin(a)*.6f);}

    /** Marines, troopers and sergeants: soldier body with a long gun. style 0 = player, 1 = trooper, 2 = sergeant. */
    static Model soldier(int style,int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        // distinct materials so the silhouette reads: armour vs trousers vs boots vs skin
        int armorC=style==0?0xff4f9c3a:style==1?0xff59663e:0xff34363c, pantsC=style==0?0xff6e6040:style==1?0xff8a7650:0xff55585e;
        b.skin=m.mat(style==0?0xffd0a07a:0xffb4bb9a,0,.08f);b.pants=m.mat(pantsC,0,.14f);b.top=m.mat(armorC,.25f,.12f);
        b.boots=m.mat(0xff3e2a1c,.15f,.08f);b.glove=m.mat(style==2?0xff6a2a22:0xff2e2a26,0,.06f);b.eye=m.glow(style==0?0xff80e0ff:0xffff3018);
        int metal=m.mat(0xff5a6066,.9f,.04f),dark=m.mat(0xff2a2c30,.5f,.03f),wood=m.mat(0xff7a4a26,.2f,.16f),belt=m.mat(0xff4a3a24,.1f,.1f),
            plate=m.mat(style==0?0xff63b24a:style==1?0xff6f7c4a:0xff4c4f58,.55f,.08f);
        if(style==2){b.torsoW=.23f;b.shoulderW=.245f;b.armR=.064f;}
        buildTorso(m,b,ps);
        buildLegs(m,b,ps,-1,ps.lThigh,ps.lKnee);buildLegs(m,b,ps,1,ps.rThigh,ps.rKnee);
        // belt + pouches
        m.ellDir(leanPt(b,v(0,0,b.hipZ+.11f+ps.bob)),leanDir(b),b.torsoF*1.02f,b.torsoW*.98f,.035f,belt);
        m.box(leanPt(b,v(.1f,-.09f,b.hipZ+.11f+ps.bob)),.03f,.035f,.04f,belt);m.box(leanPt(b,v(.1f,.09f,b.hipZ+.11f+ps.bob)),.03f,.035f,.04f,belt);
        // chest plate + shoulder pads
        m.box(leanPt(b,v(b.torsoF*.72f,0,b.torsoZ+.05f+ps.bob)),leanDir(b),.035f,b.torsoW*.72f,b.torsoH*.55f,plate);
        for(int s=-1;s<=1;s+=2)m.ell(leanPt(b,v(0,s*(b.shoulderW+.01f),b.shoulderZ+.02f+ps.bob)),.085f,.075f,.06f,plate);
        if(style==0)m.box(leanPt(b,v(-b.torsoF-.05f,0,b.torsoZ+.03f+ps.bob)),leanDir(b),.055f,.13f,.15f,m.mat(0xff4c5236,.1f,.12f)); // backpack
        // head
        float[] hd=headDir(b,ps);
        m.ellDir(b.head,hd,b.headR*.95f,b.headR*.92f,b.headR*1.05f,b.skin);
        if(style==0){
            m.ellDir(add(b.head,v(-.012f,0,.025f)),hd,b.headR*1.12f,b.headR*1.12f,b.headR*.98f,plate);        // helmet
            m.box(add(b.head,add(mul(hd,b.headR*.86f),v(0,0,.0f))),hd,.022f,.075f,.03f,m.mat(0xff1c3036,1.2f,0)); // visor
        }else{
            m.ellDir(add(b.head,v(-.01f,0,.03f)),hd,b.headR*.98f,b.headR*.96f,b.headR*.82f,m.mat(style==1?0xff3a3020:0xff2a2622,0,.2f)); // hair / cap
            for(int s=-1;s<=1;s+=2)m.ell(add(add(b.head,mul(hd,b.headR*.85f)),v(0,s*.035f,.01f)),.014f,.014f,.012f,b.eye);
            m.ell(add(add(b.head,mul(hd,b.headR*.8f)),v(0,0,-.05f)),.03f,.05f,.018f,m.mat(0xff5a3a30,0,0));   // mouth shadow
        }
        // gun: rifle (player/trooper) or shotgun (sergeant), held at the hip, raised to the shoulder when firing
        float gz=ps.attack?b.shoulderZ-.06f:b.torsoZ-.03f,gx=ps.attack?.2f:.16f;gz+=ps.bob;
        float[] gunDir=ps.attack?X:norm(v(1,-.42f,-.12f));   // diagonal "ready" hold so the gun reads from the front
        if(frame==Sprites.F_PAIN){gunDir=norm(v(.8f,-.2f,.35f));gz+=.06f;}
        float[] grip=v(gx,.09f,gz),gunC=add(grip,mul(gunDir,.14f));
        boolean shotgun=style==2;
        if(frame==Sprites.F_DEAD){   // corpse: arms limp, gun dropped on the floor beside the body
            buildArm(m,b,ps,1,null,.1f);buildArm(m,b,ps,-1,null,.1f);
            finishPose(m,frame);
            float[] gd=norm(v(.3f,1,0));float[] gc=v(.1f,-.45f,.05f);
            m.box(gc,gd,.25f,.038f,.028f,shotgun?wood:metal);m.cyl(add(gc,mul(gd,.1f)),add(gc,mul(gd,.42f)),shotgun?.03f:.022f,dark);
            return m;
        }
        m.box(gunC,gunDir,.25f,.028f,.038f,shotgun?wood:metal);
        m.cyl(add(gunC,mul(gunDir,.1f)),add(gunC,mul(gunDir,.42f)),shotgun?.03f:.022f,dark);           // barrel
        m.box(add(grip,v(-.05f,0,-.04f)),gunDir,.035f,.022f,.05f,shotgun?wood:dark);                    // grip/stock
        if(!shotgun)m.box(add(gunC,add(mul(gunDir,.02f),v(0,0,-.07f))),gunDir,.025f,.02f,.05f,dark);    // magazine
        if(ps.flash)for(int k=0;k<2;k++)m.ellDir(add(gunC,mul(gunDir,.5f+k*.06f)),gunDir,.07f-k*.025f,.05f-k*.015f,.05f-k*.015f,m.glow(k==0?0xffff9a30:0xffffeaa0));
        buildArm(m,b,ps,1,grip,0);buildArm(m,b,ps,-1,add(gunC,mul(gunDir,.14f)),0);
        return finishPose(m,frame);
    }
    /** Imp: hunched brown demon with bone spikes and claws; throws fireballs. */
    static Model imp(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.mat(0xff8f5c38,.15f,.22f);b.pants=m.mat(0xff7a4c2c,.1f,.22f);b.top=b.skin;b.boots=m.mat(0xff5a3a24,.1f,.2f);b.glove=b.skin;
        b.eye=m.glow(0xffffa020);int bone=m.mat(0xffe6dcc0,.4f,.08f),belly=m.mat(0xffa8784e,.1f,.18f),mouth=m.glow(0xffff5010);
        b.legR=.075f;b.headR=.12f;b.torsoW=.21f;b.torsoF=.15f;b.armR=.06f;b.handR=.05f;b.shoulderW=.24f;b.upper=.28f;b.fore=.28f;
        if(frame!=Sprites.F_PAIN&&frame!=Sprites.F_DEAD)ps.lean+=.3f; // hunched predator stance
        buildTorso(m,b,ps);
        buildLegs(m,b,ps,-1,ps.lThigh+.12f,ps.lKnee+.2f);buildLegs(m,b,ps,1,ps.rThigh+.12f,ps.rKnee+.2f);
        m.ellDir(leanPt(b,v(b.torsoF*.55f,0,b.torsoZ-.04f+ps.bob)),leanDir(b),.06f,b.torsoW*.7f,b.torsoH*.7f,belly);
        // spikes: shoulders and spine
        for(int s=-1;s<=1;s+=2){float[] sh=leanPt(b,v(-.02f,s*b.shoulderW,b.shoulderZ+.02f+ps.bob));m.cap(sh,add(sh,v(-.05f,s*.06f,.12f)),.018f,bone);}
        for(int k=0;k<3;k++){float[] sp=leanPt(b,v(-b.torsoF+.01f,0,b.torsoZ+.12f-k*.1f+ps.bob));m.cap(sp,add(sp,v(-.08f,0,.04f)),.016f,bone);}
        float[] hd=headDir(b,ps);
        m.ellDir(b.head,hd,b.headR*1.05f,b.headR*.9f,b.headR,b.skin);
        m.ellDir(add(b.head,add(mul(hd,b.headR*.55f),v(0,0,-.04f))),hd,.06f,.06f,.04f,b.skin);   // snout
        for(int s=-1;s<=1;s+=2){m.ell(add(add(b.head,mul(hd,b.headR*.8f)),v(0,s*.04f,.025f)),.016f,.016f,.013f,b.eye);
            float[] hb=add(b.head,v(-.02f,s*.06f,.06f));m.cap(hb,add(hb,v(-.05f,s*.03f,.07f)),.014f,bone);}
        if(ps.attack||ps.open)m.ell(add(add(b.head,mul(hd,b.headR*1.02f)),v(0,0,-.06f)),.02f,.035f,.018f,mouth);
        // arms: attack raises the right arm overhead with a fireball
        // muscular shoulders and forearm spikes
        for(int s2=-1;s2<=1;s2+=2)m.ell(leanPt(b,v(0,s2*(b.shoulderW-.02f),b.shoulderZ-.01f+ps.bob)),.09f,.08f,.08f,b.skin);
        float[] rHand=null,lHand=null;
        if(ps.attack){rHand=v(.08f,.2f,1.62f);lHand=leanPt(b,v(.3f,-.18f,1.0f));
            for(int k=0;k<3;k++)m.ell(add(rHand,v(.03f,0,.13f)),.13f-k*.035f,.13f-k*.035f,.13f-k*.035f,m.glow(k==0?0xffd04010:k==1?0xffff9020:0xffffe880));}
        float[] rh=buildArmGet(m,b,ps,1,rHand,ps.rArmSwing+.3f),lh=buildArmGet(m,b,ps,-1,lHand,ps.lArmSwing+.3f);
        for(float[] hnd:new float[][]{rh,lh})for(int k=-1;k<=1;k++){float[] cb=add(hnd,v(.03f,k*.025f,-.02f));m.cap(cb,add(cb,v(.07f,k*.02f,-.06f)),.011f,bone);}
        return finishPose(m,frame);
    }
    /** Demon: bulky pink brute with a huge toothy jaw. */
    static Model demon(int frame){
        Model m=new Model();Pose ps=pose(frame);Body b=new Body();
        b.skin=m.mat(0xffd8707e,.3f,.2f);b.pants=m.mat(0xffb55a68,.2f,.22f);b.top=b.skin;b.boots=m.mat(0xff8a4450,.1f,.2f);b.glove=b.skin;
        int teeth=m.mat(0xfff2ecd8,.5f,.04f),gum=m.mat(0xff7a1c24,.1f,.1f),horn=m.mat(0xffd8cca8,.4f,.1f),eye=m.glow(0xffff4020),vein=m.mat(0xffb85868,.1f,.25f);
        b.hipZ=.55f;b.thigh=.3f;b.shin=.28f;b.legR=.11f;b.hipW=.15f;b.footL=.15f;
        b.torsoZ=.86f;b.torsoF=.3f;b.torsoW=.32f;b.torsoH=.32f;b.shoulderZ=1.0f;b.shoulderW=.3f;b.upper=.22f;b.fore=.22f;b.armR=.075f;b.handR=.07f;
        b.headZ=1.12f;b.headR=.16f;
        if(ps.attack)ps.lean+=.25f;
        buildTorso(m,b,ps);
        buildLegs(m,b,ps,-1,ps.lThigh,ps.lKnee+.2f);buildLegs(m,b,ps,1,ps.rThigh,ps.rKnee+.2f);
        // back hump with ridges
        m.ellDir(leanPt(b,v(-.08f,0,b.torsoZ+.14f+ps.bob)),leanDir(b),.22f,.26f,.2f,b.skin);
        for(int k=0;k<4;k++)m.ellDir(leanPt(b,v(-.16f+k*.03f,0,b.torsoZ+.3f-k*.08f+ps.bob)),leanDir(b),.05f,.13f,.025f,vein);
        // head: brow, eyes, horns, and a big jaw that opens wide when attacking
        float[] hd=headDir(b,ps);float[] fh=leanPt(b,v(b.torsoF*.7f,0,b.headZ+ps.bob));
        m.ellDir(fh,hd,.15f,.2f,.11f,b.skin);
        for(int s=-1;s<=1;s+=2){m.ell(add(fh,v(.11f,s*.08f,.03f)),.022f,.022f,.018f,eye);float[] hb=add(fh,v(0,s*.15f,.06f));m.cap(hb,add(hb,v(.04f,s*.06f,.08f)),.022f,horn);}
        float open=ps.attack?.42f:ps.open?.2f:.08f;
        float[] jawPiv=add(fh,v(.02f,0,-.06f));
        float[] upper=add(jawPiv,v(.17f,0,-.01f));m.ellDir(upper,X,.15f,.18f,.06f,b.skin);
        float[] lowDir=norm(v((float)Math.cos(open),0,-(float)Math.sin(open)));float[] lower=add(jawPiv,mul(lowDir,.17f));
        m.ellDir(lower,lowDir,.15f,.16f,.055f,b.skin);
        m.ellDir(add(lerp(upper,lower,.5f),v(-.02f,0,0)),X,.1f,.13f,.04f+open*.1f,gum);
        for(int k=-2;k<=2;k++){float yy=k*.055f;m.cap(add(upper,v(.1f-Math.abs(k)*.02f,yy,-.05f)),add(upper,v(.1f-Math.abs(k)*.02f,yy,-.1f)),.016f,teeth);
            float[] lt=add(lower,mul(lowDir,.08f-Math.abs(k)*.02f));m.cap(add(lt,v(0,yy,.04f)),add(lt,v(0,yy,.09f)),.015f,teeth);}
        float[] rHand=ps.attack?leanPt(b,v(.35f,.22f,.8f)):null,lHand=ps.attack?leanPt(b,v(.35f,-.22f,.8f)):null;
        buildArm(m,b,ps,1,rHand,ps.rArmSwing*.6f+.2f);buildArm(m,b,ps,-1,lHand,ps.lArmSwing*.6f+.2f);
        return finishPose(m,frame);
    }
    /** Corpses: the standing model tipped over backwards onto the floor with a pool of blood. */
    private static Model finishPose(Model m,int frame){
        if(frame!=Sprites.F_DEAD)return m;
        m.rotateY(v(0,0,0),(float)(-Math.PI*.5),v(.65f,0,.13f));
        int blood=m.mat(0xff7a0a0a,.6f,.1f);m.ellAxes(v(0,0,0.0f),X,Y,Z,.62f,.42f,.03f,blood);
        return m;
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
