package com.retro.doomeditor;

/**
 * The first-person view model: the hero's real 3D hands and forearms holding the equipped weapon, rebuilt every
 * frame from an animated pose (so nothing is a sprite). Geometry is in view space: x right, y up, z forward from the
 * eye, in world units (the eye is 1.15 above the floor).
 *
 * Hands are modelled properly: a palm, knuckles, four fingers of three segments each and a two-segment thumb, all
 * curling around the weapon's grip; gloves, leather cuffs and mail sleeves (robed sleeves and bare hands for a
 * caster with a staff). Weapons are built in "weapon space" (W along the blade or shaft, S toward the cutting edge,
 * N across the flat) with the grip centred on the origin, and dropped into the fist.
 *
 * Materials come from a small procedural atlas: colour, smoothness / metalness / AO, and emissive (the staff's orb
 * and casting glow), so the PBR renderer lights them like everything else.
 */
final class ViewModel {
    // ------------------------------------------------------------------ materials (atlas tiles, 4 x 4 of 32 px)
    static final int M_GLOVE=0,M_MAIL=1,M_STEEL=2,M_GRIP=3,M_WOOD=4,M_BRASS=5,M_SKIN=6,M_ROBE=7,M_ORB=8,M_CUFF=9,M_STRING=10,M_FLETCH=11,M_DARK=12,M_NAIL=13,M_GLOW=14;
    static final int ATLAS=128,TILE=32;
    static int[] color,material,emissive;
    static synchronized void initAtlas(){
        if(color!=null)return;
        int n=ATLAS*ATLAS;int[] c=new int[n],m=new int[n],e=new int[n];
        for(int t=0;t<16;t++){int tx=(t%4)*TILE,ty=(t/4)*TILE;
            for(int y=0;y<TILE;y++)for(int x=0;x<TILE;x++){int i=(ty+y)*ATLAS+tx+x;float[] r=texel(t,x,y);
                c[i]=0xff000000|(cl(r[0])<<16)|(cl(r[1])<<8)|cl(r[2]);
                m[i]=0xff000000|(cl(r[3])<<16)|(cl(r[4])<<8)|cl(r[5]);
                e[i]=0xff000000|(cl(r[6])<<16)|(cl(r[7])<<8)|cl(r[8]);}}
        color=c;material=m;emissive=e;
    }
    private static int cl(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
    private static float hs(int x,int y,int s){return Textures.hash(x,y,s);}
    private static float nz(int x,int y,int s){return Textures.noise(x/8f,y/8f,4,s);}
    /** One texel: r,g,b, smoothness, metalness, AO, emissive r,g,b. */
    private static float[] texel(int t,int x,int y){
        float n=nz(x,y,t*13+5),g=hs(x,y,t+40)*.08f-.04f;
        switch(t){
            case M_GLOVE:{float st=(x%11==5&&y%3!=0)?.75f:1;float s=(.82f+n*.3f+g)*st;return f(.36f*s,.24f*s,.15f*s,.32f+n*.15f,0,1,0,0,0);}
            case M_MAIL:{int u=x%4,v=(y+(x/4%2)*2)%4;float ring=(u==0||v==0)?.45f:(u==2&&v==2)?.6f:1f;float s=(.7f+n*.25f+g)*ring;return f(.5f*s,.53f*s,.56f*s,.55f,.9f,ring<.5f?.6f:1,0,0,0);}
            case M_STEEL:{float br=.86f+(hs(0,y,77)*.1f)+n*.06f;return f(.72f*br,.75f*br,.79f*br,.82f,1,1,0,0,0);}
            case M_GRIP:{float wrap=((x+y*2)%7<2)?.6f:1;float s=(.7f+n*.2f+g)*wrap;return f(.24f*s,.15f*s,.09f*s,.3f,0,wrap<1?.7f:1,0,0,0);}
            case M_WOOD:{float gr=.75f+.25f*(float)Math.sin(x*.9f+n*6)+g;return f(.42f*gr,.27f*gr,.15f*gr,.38f,0,1,0,0,0);}
            case M_BRASS:{float s=.85f+n*.2f+g;return f(.8f*s,.64f*s,.3f*s,.72f,1,1,0,0,0);}
            case M_SKIN:{float s=.92f+n*.12f+g*.5f;return f(.86f*s,.62f*s,.5f*s,.42f,0,1,0,0,0);}
            case M_ROBE:{float w=((x+y)%3==0)?.85f:1;float s=(.8f+n*.3f+g)*w;return f(.27f*s,.18f*s,.5f*s,.15f,0,1,0,0,0);}
            case M_ORB:{float s=.8f+n*.4f;return f(.55f*s,.8f*s,1f,.95f,0,1,.35f*s,.62f*s,1f*s);}
            case M_CUFF:{float s=(.75f+n*.3f+g)*((y%10==1)?.6f:1);return f(.2f*s,.13f*s,.08f*s,.3f,0,1,0,0,0);}
            case M_STRING:{float s=.9f+g;return f(.86f*s,.82f*s,.7f*s,.3f,0,1,0,0,0);}
            case M_FLETCH:{float s=.8f+n*.3f;return f(.75f*s,.18f*s,.14f*s,.2f,0,1,0,0,0);}
            case M_DARK:{float s=.8f+n*.2f+g;return f(.22f*s,.23f*s,.25f*s,.6f,.9f,1,0,0,0);}
            case M_NAIL:{return f(.78f,.6f,.52f,.6f,0,1,0,0,0);}
            case M_GLOW:{float s=.85f+n*.3f;return f(.75f*s,.9f*s,1f,.9f,0,1,.5f*s,.75f*s,1f*s);}
            default:return f(.5f,.5f,.5f,.3f,0,1,0,0,0);
        }
    }
    private static float[] f(float... v){return v;}

    // ------------------------------------------------------------------ the mesh (view space), rebuilt each frame
    /** Floats per vertex: position 3, normal 3, atlas uv 2. */
    static final int VS=8;
    float[] v=new float[VS*9000];int count;
    private int mat;
    // current transform: origin + 3 axes (columns), and a mirror flag for left hands
    private final float[] o=new float[3],ax=new float[3],ay=new float[3],az=new float[3];
    private void ident(){o[0]=o[1]=o[2]=0;set(ax,1,0,0);set(ay,0,1,0);set(az,0,0,1);}
    private static void set(float[] a,float x,float y,float z){a[0]=x;a[1]=y;a[2]=z;}
    private void frame(float[] org,float[] X,float[] Y,float[] Z){System.arraycopy(org,0,o,0,3);System.arraycopy(X,0,ax,0,3);System.arraycopy(Y,0,ay,0,3);System.arraycopy(Z,0,az,0,3);}
    private void emit(float px,float py,float pz,float nx,float ny,float nz,float fu,float fv){
        if((count+1)*VS>v.length)v=java.util.Arrays.copyOf(v,v.length*2);
        int k=count*VS;
        v[k]=o[0]+ax[0]*px+ay[0]*py+az[0]*pz;v[k+1]=o[1]+ax[1]*px+ay[1]*py+az[1]*pz;v[k+2]=o[2]+ax[2]*px+ay[2]*py+az[2]*pz;
        float wx=ax[0]*nx+ay[0]*ny+az[0]*nz,wy=ax[1]*nx+ay[1]*ny+az[1]*nz,wz=ax[2]*nx+ay[2]*ny+az[2]*nz,l=(float)Math.sqrt(wx*wx+wy*wy+wz*wz);if(l<1e-6f)l=1;
        v[k+3]=wx/l;v[k+4]=wy/l;v[k+5]=wz/l;
        int tx=(mat%4)*TILE,ty=(mat/4)*TILE;fu=Math.max(0,Math.min(1,fu));fv=Math.max(0,Math.min(1,fv));
        v[k+6]=(tx+1.5f+fu*(TILE-3))/ATLAS;v[k+7]=(ty+1.5f+fv*(TILE-3))/ATLAS;count++;
    }
    // --- primitives (in the current frame)
    /** A tapered tube from a to b, radius ra to rb (elliptical by squash on the second axis), rounded caps optional. */
    private void tube(float[] a,float[] b,float ra,float rb,float squash,int seg,boolean capA,boolean capB,float uScale){
        float[] d=sub(b,a);float len=len(d);if(len<1e-6f)return;d=mul(d,1/len);float[][] fr=basis(d);float[] p=fr[0],q=fr[1];
        int rings=capA||capB?2:1;
        for(int i=0;i<seg;i++){float a0=i*6.2831853f/seg,a1=(i+1)*6.2831853f/seg;
            float c0=(float)Math.cos(a0),s0=(float)Math.sin(a0),c1=(float)Math.cos(a1),s1=(float)Math.sin(a1);
            float[] n0=add(mul(p,c0),mul(q,s0*squash)),n1=add(mul(p,c1),mul(q,s1*squash));
            float[] v00=add(a,mul(n0,ra)),v01=add(a,mul(n1,ra)),v10=add(b,mul(n0,rb)),v11=add(b,mul(n1,rb));
            float[] m0=add(mul(p,c0),mul(q,s0/Math.max(.2f,squash))),m1=add(mul(p,c1),mul(q,s1/Math.max(.2f,squash)));
            float slope=(ra-rb)/len;m0=add(m0,mul(d,slope));m1=add(m1,mul(d,slope));
            float u0=i/(float)seg,u1=(i+1)/(float)seg;
            quad(v00,v01,v11,v10,m0,m1,m1,m0,u0,0,u1,0,u1,uScale,u0,uScale);
        }
        if(capA)cap(a,mul(d,-1),p,q,ra,squash,seg);
        if(capB)cap(b,d,p,q,rb,squash,seg);
    }
    private void cap(float[] c,float[] d,float[] p,float[] q,float r,float squash,int seg){
        int st=3;
        for(int j=0;j<st;j++){float b0=j*1.5707963f/st,b1=(j+1)*1.5707963f/st;
            for(int i=0;i<seg;i++){float a0=i*6.2831853f/seg,a1=(i+1)*6.2831853f/seg;
                float[] e00=hemi(p,q,d,a0,b0,squash),e01=hemi(p,q,d,a1,b0,squash),e10=hemi(p,q,d,a0,b1,squash),e11=hemi(p,q,d,a1,b1,squash);
                quad(add(c,mul(e00,r)),add(c,mul(e01,r)),add(c,mul(e11,r)),add(c,mul(e10,r)),e00,e01,e11,e10,i/(float)seg,.5f+j*.15f,(i+1f)/seg,.5f+j*.15f,(i+1f)/seg,.65f+j*.15f,i/(float)seg,.65f+j*.15f);}}
    }
    private static float[] hemi(float[] p,float[] q,float[] d,float a,float b,float squash){
        float cb=(float)Math.cos(b),sb=(float)Math.sin(b);return add(add(mul(p,(float)Math.cos(a)*cb),mul(q,(float)Math.sin(a)*cb*squash)),mul(d,sb));}
    /** Ellipsoid with radii along three axes (in the current frame). */
    private void ell(float[] c,float[] X,float[] Y,float[] Z,float rx,float ry,float rz,int seg,int st){
        for(int j=0;j<st;j++){float b0=-1.5707963f+j*3.1415927f/st,b1=-1.5707963f+(j+1)*3.1415927f/st;
            for(int i=0;i<seg;i++){float a0=i*6.2831853f/seg,a1=(i+1)*6.2831853f/seg;
                float[][] pp=new float[4][],nn=new float[4][];float[] as={a0,a1,a1,a0},bs={b0,b0,b1,b1};
                for(int k=0;k<4;k++){float ca=(float)Math.cos(as[k]),sa=(float)Math.sin(as[k]),cb=(float)Math.cos(bs[k]),sb=(float)Math.sin(bs[k]);
                    float ux=ca*cb,uy=sa*cb,uz=sb;pp[k]=add(c,add(add(mul(X,ux*rx),mul(Y,uy*ry)),mul(Z,uz*rz)));nn[k]=add(add(mul(X,ux/rx),mul(Y,uy/ry)),mul(Z,uz/rz));}
                quad(pp[0],pp[1],pp[2],pp[3],nn[0],nn[1],nn[2],nn[3],i/(float)seg,j/(float)st,(i+1f)/seg,j/(float)st,(i+1f)/seg,(j+1f)/st,i/(float)seg,(j+1f)/st);}}
    }
    /** A box with bevelled edges (centre, axes, half sizes, bevel). */
    private void box(float[] c,float[] X,float[] Y,float[] Z,float hx,float hy,float hz,float bev){
        // 6 faces, each inset by the bevel, plus the bevel strips (normals averaged) — enough to catch a highlight
        float[][] A={X,Y,Z};float[] H={hx,hy,hz};
        for(int f=0;f<3;f++)for(int sgn=-1;sgn<=1;sgn+=2){float[] n=mul(A[f],sgn);float[] U=A[(f+1)%3],V=A[(f+2)%3];float hu=H[(f+1)%3]-bev,hv=H[(f+2)%3]-bev;
            float[] fc=add(c,mul(n,H[f]));
            float[] p0=add(fc,add(mul(U,-hu),mul(V,-hv))),p1=add(fc,add(mul(U,hu),mul(V,-hv))),p2=add(fc,add(mul(U,hu),mul(V,hv))),p3=add(fc,add(mul(U,-hu),mul(V,hv)));
            quad(p0,p1,p2,p3,n,n,n,n,0,0,1,0,1,1,0,1);
            if(bev>0)for(int e=0;e<4;e++){float[] eu=e<2?U:V,ev=e<2?V:U;float s2=(e%2==0)?-1:1;float he=e<2?hu:hv,ho=e<2?hv:hu;
                float[] en=norm(add(n,mul(eu,s2)));float[] a0=add(fc,add(mul(eu,s2*he),mul(ev,-ho))),a1=add(fc,add(mul(eu,s2*he),mul(ev,ho)));
                float[] b0=add(add(c,mul(n,H[f]-bev)),add(mul(eu,s2*(he+bev)),mul(ev,-ho))),b1=add(add(c,mul(n,H[f]-bev)),add(mul(eu,s2*(he+bev)),mul(ev,ho)));
                quad(a0,a1,b1,b0,en,en,en,en,0,0,1,0,1,.2f,0,.2f);}}
    }
    private void quad(float[] a,float[] b,float[] c,float[] d,float[] na,float[] nb,float[] nc,float[] nd,float ua,float va,float ub,float vb,float uc,float vc,float ud,float vd){
        tri(a,b,c,na,nb,nc,ua,va,ub,vb,uc,vc);tri(a,c,d,na,nc,nd,ua,va,uc,vc,ud,vd);}
    private void tri(float[] a,float[] b,float[] c,float[] na,float[] nb,float[] nc,float ua,float va,float ub,float vb,float uc,float vc){
        emit(a[0],a[1],a[2],na[0],na[1],na[2],ua,va);emit(b[0],b[1],b[2],nb[0],nb[1],nb[2],ub,vb);emit(c[0],c[1],c[2],nc[0],nc[1],nc[2],uc,vc);}

    // ------------------------------------------------------------------ vector helpers
    static float[] v3(float x,float y,float z){return new float[]{x,y,z};}
    static float[] add(float[] a,float[] b){return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    static float[] sub(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    static float[] mul(float[] a,float s){return new float[]{a[0]*s,a[1]*s,a[2]*s};}
    static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static float len(float[] a){return (float)Math.sqrt(dot(a,a));}
    static float[] norm(float[] a){float l=len(a);return l<1e-9f?v3(0,0,1):mul(a,1/l);}
    static float[] mix(float[] a,float[] b,float t){return new float[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};}
    private static float[][] basis(float[] d){float[] up=Math.abs(d[1])<.9f?v3(0,1,0):v3(1,0,0);float[] p=norm(cross(up,d));float[] q=cross(d,p);return new float[][]{p,q};}
    /** Rotates v about unit axis k by angle a (Rodrigues). */
    static float[] rot(float[] v,float[] k,float a){float c=(float)Math.cos(a),s=(float)Math.sin(a);return add(add(mul(v,c),mul(cross(k,v),s)),mul(k,dot(k,v)*(1-c)));}

    // ------------------------------------------------------------------ hands
    /** How a hand is posed: finger curl (0 open, 1 tight fist around a grip), thumb wrap, spread. */
    static final class Grip{float curl,thumb,spread;Grip(float c,float t,float s){curl=c;thumb=t;spread=s;}}
    static final Grip FIST=new Grip(1f,1f,0),HOLD=new Grip(.92f,.85f,0),RELAX=new Grip(.45f,.35f,.12f),OPEN=new Grip(.08f,.1f,.35f),PINCH=new Grip(.7f,.55f,.05f);
    /**
     * A hand in its own frame (wrist at the origin, fingers along +z, back of the hand +y, thumb on -x for a right
     * hand) placed at wrist position w with fingers toward F and the back of the hand toward U. Left hands mirror x.
     */
    private void hand(float[] w,float[] X,float[] Y,float[] Z,boolean left,Grip g,boolean bare){
        float sx=left?-1:1;frame(w,mul(X,sx),Y,Z);
        int skin=bare?M_SKIN:M_GLOVE;
        // palm and the back of the hand: a flattened block with the knuckle ridge
        mat=skin;box(v3(0,0,.046f),v3(1,0,0),v3(0,1,0),v3(0,0,1),.04f,.0155f,.045f,.009f);
        ell(v3(.006f,-.008f,.03f),v3(1,0,0),v3(0,1,0),v3(0,0,1),.034f,.013f,.032f,8,4);   // heel of the palm
        ell(v3(0,-.003f,.003f),v3(1,0,0),v3(0,1,0),v3(0,0,1),.034f,.018f,.016f,8,4);      // wrist
        // fingers: index (thumb side) to little finger
        float[] fx={-.028f,-.0095f,.0095f,.027f};float[][] L={{.040f,.026f,.020f},{.044f,.028f,.021f},{.041f,.026f,.020f},{.032f,.020f,.017f}};
        float[] R=bare?new float[]{.0092f,.0096f,.0092f,.008f}:new float[]{.0108f,.0112f,.0108f,.0096f};
        for(int fi=0;fi<4;fi++){
            float spread=(fi-1.5f)*g.spread*.09f;float[] dir=norm(v3((float)Math.sin(spread),0,(float)Math.cos(spread)));
            float[] p=v3(fx[fi],.002f,.084f);float[] kx=v3(1,0,0);
            mat=skin;ell(p,v3(1,0,0),v3(0,1,0),v3(0,0,1),R[fi]*1.15f,R[fi]*1.1f,R[fi]*1.1f,6,4);   // knuckle
            float[] ang={1.25f,1.55f,1.0f};
            for(int s=0;s<3;s++){float a=g.curl*ang[s]*(fi==3?1.05f:1)+(g.curl<.3f?-.08f:0);dir=rot(dir,kx,a);   // curl toward the palm (-y)
                float[] q=add(p,mul(dir,L[fi][s]));float r0=R[fi]*(1-s*.12f),r1=R[fi]*(1-(s+1)*.12f);
                mat=skin;tube(p,q,r0,r1,.85f,8,s==0,s==2,1);
                if(s<2){ell(q,v3(1,0,0),v3(0,1,0),v3(0,0,1),r1*1.08f,r1*1.02f,r1*1.08f,6,4);}   // knuckle
                if(s==2&&bare){mat=M_NAIL;float[] up=norm(cross(dir,kx));ell(add(sub(q,mul(dir,.007f)),mul(up,-r1*.75f)),kx,up,dir,r1*.7f,.0025f,.008f,6,3);}
                p=q;}
        }
        // thumb: from the side of the palm; open it reaches out sideways, closed it wraps over the front of the grip
        float[] tb=v3(-.034f,-.011f,.024f);float k=g.thumb;
        float[] t1=mix(v3(-.068f,-.016f,.052f),v3(-.03f,-.05f,.066f),k),t2=mix(v3(-.09f,-.02f,.078f),v3(-.008f,-.062f,.09f),k);
        mat=skin;ell(add(tb,v3(.006f,-.002f,.008f)),v3(1,0,0),v3(0,1,0),v3(0,0,1),.019f,.016f,.026f,8,4);   // the thumb's muscle
        tube(tb,t1,.0128f,.0115f,.9f,8,true,false,1);ell(t1,v3(1,0,0),v3(0,1,0),v3(0,0,1),.012f,.0115f,.012f,6,4);
        tube(t1,t2,.0115f,.0098f,.9f,8,false,true,1);
        if(bare){mat=M_NAIL;float[] d=norm(sub(t2,t1));float[] up=norm(cross(d,v3(0,0,1)));if(up[1]<0)up=mul(up,-1);ell(add(sub(t2,mul(d,.006f)),mul(up,.0075f)),cross(up,d),up,d,.0075f,.0025f,.008f,6,3);}
        if(!bare){mat=M_GLOVE;}
    }
    /** The forearm from the elbow (off-screen) to the wrist: a mail sleeve with a leather cuff (or a robe sleeve). */
    private void forearm(float[] elbow,float[] wrist,boolean robe){
        ident();float[] d=norm(sub(wrist,elbow));
        if(robe){mat=M_ROBE;tube(add(elbow,mul(d,-.2f)),sub(wrist,mul(d,.055f)),.07f,.058f,1,12,false,false,2);
            tube(sub(wrist,mul(d,.06f)),sub(wrist,mul(d,.0f)),.062f,.06f,1,12,false,false,.3f);   // hanging hem
            mat=M_SKIN;tube(sub(wrist,mul(d,.06f)),add(wrist,mul(d,.012f)),.03f,.027f,.85f,10,false,false,1);return;}
        mat=M_MAIL;tube(add(elbow,mul(d,-.2f)),sub(wrist,mul(d,.06f)),.058f,.042f,1,12,false,false,3);
        mat=M_CUFF;tube(sub(wrist,mul(d,.075f)),sub(wrist,mul(d,.0f)),.044f,.039f,.95f,12,false,false,1);
        tube(sub(wrist,mul(d,.083f)),sub(wrist,mul(d,.07f)),.047f,.047f,.95f,12,false,false,.3f);
        mat=M_GLOVE;tube(sub(wrist,mul(d,.01f)),add(wrist,mul(d,.02f)),.034f,.032f,.8f,10,false,false,1);
    }

    // ------------------------------------------------------------------ weapons (weapon space: W blade, S edge, N flat)
    private void weapon(int w,float[] grip,float[] W,float[] S,float[] N,float glow){
        frame(grip,N,S,W);   // build with x = N, y = S, z = W
        float[] X=v3(1,0,0),Y=v3(0,1,0),Z=v3(0,0,1);
        switch(w){
            case Rpg.FP_SWORD:case Rpg.FP_DAGGER:{float blade=w==Rpg.FP_DAGGER?.27f:.68f;
                mat=M_GRIP;tube(v3(0,0,-.085f),v3(0,0,.045f),.0145f,.0135f,1,10,false,false,4);
                mat=M_BRASS;ell(v3(0,0,-.1f),X,Y,Z,.022f,.022f,.02f,10,6);tube(v3(0,0,-.088f),v3(0,0,-.08f),.017f,.017f,1,10,false,false,1);
                box(v3(0,0,.055f),X,Y,Z,.012f,w==Rpg.FP_DAGGER?.05f:.078f,.009f,.003f);
                ell(v3(0,w==Rpg.FP_DAGGER?.05f:.078f,.055f),X,Y,Z,.011f,.011f,.011f,6,4);ell(v3(0,w==Rpg.FP_DAGGER?-.05f:-.078f,.055f),X,Y,Z,.011f,.011f,.011f,6,4);
                // the blade: diamond section tapering to a point, with a darker fuller down the middle
                mat=M_STEEL;float z0=.066f,z1=z0+blade;int segs=6;
                for(int k=0;k<segs;k++){float t0=k/(float)segs,t1=(k+1f)/segs;float wd0=bw(t0,w),wd1=bw(t1,w),th0=.0085f*(1-t0*.6f),th1=.0085f*(1-t1*.6f);float za=z0+blade*t0,zb=z0+blade*t1;
                    for(int sgn=-1;sgn<=1;sgn+=2)for(int e=-1;e<=1;e+=2){
                        float[] a0=v3(0,0,za),b0=v3(0,0,zb),a1=v3(sgn*th0,0,za),b1=v3(sgn*th1,0,zb),a2=v3(0,e*wd0,za),b2=v3(0,e*wd1,zb);
                        float[] nn=norm(v3(sgn*wd0,e*th0,0));quad(a1,b1,b2,a2,nn,nn,nn,nn,t0,.5f,t1,.5f,t1,e>0?1:0,t0,e>0?1:0);}}
                float tipZ=z1+(w==Rpg.FP_DAGGER?.045f:.07f);float wt=bw(1,w),tt=.0085f*.4f;
                for(int sgn=-1;sgn<=1;sgn+=2)for(int e=-1;e<=1;e+=2){float[] nn=norm(v3(sgn*wt,e*tt,wt*.4f));tri(v3(sgn*tt,0,z1),v3(0,e*wt,z1),v3(0,0,tipZ),nn,nn,nn,0,.5f,1,.5f,1,1);}
                mat=M_DARK;box(v3(0,0,z0+blade*.4f),X,Y,Z,.0086f,.0035f,blade*.34f,0);
                break;}
            case Rpg.FP_MACE:{
                mat=M_WOOD;tube(v3(0,0,-.12f),v3(0,0,.4f),.0165f,.0155f,1,10,true,false,6);
                mat=M_GRIP;tube(v3(0,0,-.09f),v3(0,0,.05f),.0185f,.0185f,1,10,false,false,4);
                mat=M_DARK;ell(v3(0,0,.45f),X,Y,Z,.045f,.045f,.05f,10,8);
                mat=M_STEEL;for(int k=0;k<7;k++){double a=k*Math.PI*2/7;float[] r=v3((float)Math.cos(a),(float)Math.sin(a),0);float[] t=cross(Z,r);
                    box(add(v3(0,0,.45f),mul(r,.048f)),r,t,Z,.022f,.0045f,.045f,.002f);}
                ell(v3(0,0,.505f),X,Y,Z,.018f,.018f,.016f,8,4);
                break;}
            case Rpg.FP_AXE:{
                mat=M_WOOD;tube(v3(0,0,-.14f),v3(0,0,.52f),.017f,.015f,1,10,true,true,7);
                mat=M_GRIP;tube(v3(0,0,-.1f),v3(0,0,.04f),.019f,.019f,1,10,false,false,4);
                mat=M_DARK;box(v3(0,0,.46f),X,Y,Z,.02f,.03f,.045f,.004f);
                // a bearded blade toward +S: a wedge widening to a curved edge
                mat=M_STEEL;int segs=8;float[] prevT=null,prevB=null;
                for(int k=0;k<=segs;k++){float t=k/(float)segs;float yy=.03f+t*.16f;float half=.04f+t*t*.07f;float zc=.46f-t*t*.025f;float th=.012f*(1-t*.8f);
                    float[] top=v3(0,yy,zc+half),bot=v3(0,yy,zc-half*1.25f);
                    if(prevT!=null){for(int sgn=-1;sgn<=1;sgn+=2){float[] n=v3(sgn,0,0);float[] pt=add(prevT,v3(sgn*th,0,0)),pb=add(prevB,v3(sgn*th,0,0)),ct=add(top,v3(sgn*th,0,0)),cb=add(bot,v3(sgn*th,0,0));
                        quad(pb,pt,ct,cb,n,n,n,n,t,0,t,1,t,1,t,0);}
                        quad(add(prevT,v3(-th,0,0)),add(prevT,v3(th,0,0)),add(top,v3(th,0,0)),add(top,v3(-th,0,0)),Z,Z,Z,Z,0,0,1,0,1,.1f,0,.1f);
                        float[] nz2=mul(Z,-1);quad(add(prevB,v3(-th,0,0)),add(prevB,v3(th,0,0)),add(bot,v3(th,0,0)),add(bot,v3(-th,0,0)),nz2,nz2,nz2,nz2,0,0,1,0,1,.1f,0,.1f);}
                    prevT=top;prevB=bot;}
                {float[] n=v3(0,1,0);quad(add(prevB,v3(-.0025f,0,0)),add(prevB,v3(.0025f,0,0)),add(prevT,v3(.0025f,0,0)),add(prevT,v3(-.0025f,0,0)),n,n,n,n,0,0,1,0,1,1,0,1);}   // the edge
                mat=M_DARK;box(v3(0,-.04f,.46f),X,Y,Z,.012f,.018f,.022f,.003f);   // back spike
                break;}
            case Rpg.FP_STAFF:{
                mat=M_WOOD;tube(v3(0,0,-1.110f),v3(0,0,0.400f),.021f,.019f,1,10,true,false,10);
                mat=M_BRASS;for(float z:new float[]{-.2f,-.36f,.34f})tube(v3(0,0,z-.012f),v3(0,0,z+.012f),.026f,.026f,1,10,false,false,1);
                for(int k=0;k<3;k++){double a=k*Math.PI*2/3;float[] r=v3((float)Math.cos(a),(float)Math.sin(a),0);
                    float[] p0=add(v3(0,0,0.380f),mul(r,.018f)),p1=add(v3(0,0,0.460f),mul(r,.05f)),p2=add(v3(0,0,0.540f),mul(r,.022f));
                    tube(p0,p1,.008f,.006f,1,6,false,false,1);tube(p1,p2,.006f,.004f,1,6,false,true,1);}
                mat=glow>0.5f?M_GLOW:M_ORB;ell(v3(0,0,0.495f),X,Y,Z,.045f,.045f,.045f,12,8);
                break;}
            default:break;
        }
    }
    private static float bw(float t,int w){return (w==Rpg.FP_DAGGER?.022f:.026f)*(1-t*.25f);}
    /** The bow (in the left hand): curved limbs on W, the string drawn back to `pull` (view space). */
    private void bow(float[] grip,float[] W,float[] S,float[] N,float[] pull,boolean arrow){
        frame(grip,N,S,W);float[] X=v3(1,0,0),Y=v3(0,1,0),Z=v3(0,0,1);
        mat=M_GRIP;tube(v3(0,0,-.05f),v3(0,0,.05f),.019f,.019f,1,10,false,false,3);
        mat=M_WOOD;float[] prev=null;float[] tipT=null,tipB=null;
        for(int k=-10;k<=10;k++){float t=k/10f;float z=t*.62f;float bend=-(1-(float)Math.cos(t*1.25f))*.17f;float[] p=v3(0,bend,z);
            if(prev!=null){float r=.014f-Math.abs(t)*.007f;tube(prev,p,r+.001f,r,1,8,false,false,1);}prev=p;if(k==-10)tipB=p;if(k==10)tipT=p;}
        mat=M_BRASS;ell(tipT,X,Y,Z,.01f,.01f,.012f,6,4);ell(tipB,X,Y,Z,.01f,.01f,.012f,6,4);
        // string and arrow in view space
        float[] wt=add(grip,add(add(mul(N,tipT[0]),mul(S,tipT[1])),mul(W,tipT[2]))),wb=add(grip,add(add(mul(N,tipB[0]),mul(S,tipB[1])),mul(W,tipB[2])));
        ident();mat=M_STRING;tube(wt,pull,.0022f,.0022f,1,5,false,false,1);tube(pull,wb,.0022f,.0022f,1,5,false,false,1);
        if(arrow){float[] head=add(grip,mul(S,.11f));head=add(head,mul(N,.012f));float[] d=norm(sub(head,pull));
            mat=M_WOOD;tube(sub(pull,mul(d,.01f)),head,.0045f,.0045f,1,6,false,false,4);
            mat=M_STEEL;float[][] fr=basis(d);for(int s=0;s<2;s++){float[] side=s==0?fr[0]:fr[1];tri(add(head,mul(side,.012f)),add(head,mul(side,-.012f)),add(head,mul(d,.045f)),side,side,side,0,0,1,0,.5f,1);}
            mat=M_FLETCH;for(int s=0;s<3;s++){float[] side=rot(fr[0],d,s*2.094f);float[] a=add(pull,mul(d,.01f)),b=add(pull,mul(d,.07f));
                quad(a,b,add(b,mul(side,.012f)),add(a,mul(side,.018f)),fr[1],fr[1],fr[1],fr[1],0,0,1,0,1,1,0,1);}}
    }

    // ------------------------------------------------------------------ cached parts
    /** Hands and weapons are built once in their own space and only placed each frame (no garbage per frame). */
    private static final java.util.HashMap<Integer,float[]> parts=new java.util.HashMap<>();
    private float[] part(int key,Runnable build){
        float[] got;synchronized(parts){got=parts.get(key);}if(got!=null)return got;
        float[] keepV=v;int keepN=count;v=new float[VS*4096];count=0;ident();build.run();got=java.util.Arrays.copyOf(v,count*VS);v=keepV;count=keepN;
        synchronized(parts){parts.put(key,got);}return got;
    }
    /** Appends a cached part at org with axes X, Y, Z (a mirrored X gives a left hand). */
    private void place(float[] pt,float[] org,float[] X,float[] Y,float[] Z){
        int n=pt.length/VS;if((count+n)*VS>v.length)v=java.util.Arrays.copyOf(v,Math.max(v.length*2,(count+n)*VS));
        for(int i=0;i<n;i++){int a=i*VS,k=(count+i)*VS;float px=pt[a],py=pt[a+1],pz=pt[a+2],nx=pt[a+3],ny=pt[a+4],nz=pt[a+5];
            v[k]=org[0]+X[0]*px+Y[0]*py+Z[0]*pz;v[k+1]=org[1]+X[1]*px+Y[1]*py+Z[1]*pz;v[k+2]=org[2]+X[2]*px+Y[2]*py+Z[2]*pz;
            v[k+3]=X[0]*nx+Y[0]*ny+Z[0]*nz;v[k+4]=X[1]*nx+Y[1]*ny+Z[1]*nz;v[k+5]=X[2]*nx+Y[2]*ny+Z[2]*nz;v[k+6]=pt[a+6];v[k+7]=pt[a+7];}
        count+=n;
    }
    private static final Grip[] GRIPS={FIST,HOLD,RELAX,OPEN,PINCH};
    private void placeHand(float[] w,float[] X,float[] Y,float[] Z,boolean left,Grip g,boolean bare){
        int gi=0;for(int i=0;i<GRIPS.length;i++)if(GRIPS[i]==g)gi=i;final int fgi=gi;
        float[] pt=part(100+gi*2+(bare?1:0),()->hand(v3(0,0,0),v3(1,0,0),v3(0,1,0),v3(0,0,1),false,GRIPS[fgi],bare));
        place(pt,w,left?mul(X,-1):X,Y,Z);
    }
    private void placeWeapon(int w,float[] grip,float[] W,float[] S,float[] N,float glow){
        final boolean gl=glow>.5f;float[] pt=part(200+w*2+(gl?1:0),()->weapon(w,v3(0,0,0),v3(0,0,1),v3(0,1,0),v3(1,0,0),gl?1:0));
        place(pt,grip,N,S,W);
    }

    // ------------------------------------------------------------------ poses and animation
    /** A hand placement: wrist position, finger direction, back-of-hand direction (view space). */
    static final class Pose{float[] w,F,U;Pose(float[] w,float[] F,float[] U){this.w=w;this.F=F;this.U=U;}}
    /**
     * A pose with the wrist at screen fraction (sx, sy) of the half-width at depth z, fingers toward F (before they
     * curl) and the grip axis toward A (out of the thumb side of the fist: where a held blade or shaft points).
     */
    static Pose P(float sx,float sy,float z,float fx,float fy,float fz,float ax,float ay,float az){z*=.8f;return new Pose(v3(sx*z,sy*.5625f*z,z),norm(v3(fx,fy,fz)),norm(v3(ax,ay,az)));}
    /**
     * Blends two poses as one rotation (quaternion slerp along the shortest way), so a hand and its weapon turn
     * smoothly through a swing instead of their directions being blended separately, which could flip the blade over.
     */
    static Pose lerp(Pose a,Pose b,float t){
        float[] qa=quat(a),qb=quat(b);float d=qa[0]*qb[0]+qa[1]*qb[1]+qa[2]*qb[2]+qa[3]*qb[3];
        if(d<0){for(int i=0;i<4;i++)qb[i]=-qb[i];d=-d;}
        float[] q=new float[4];
        if(d>.9995f){for(int i=0;i<4;i++)q[i]=qa[i]+(qb[i]-qa[i])*t;}
        else{float th=(float)Math.acos(d),sn=(float)Math.sin(th),ka=(float)Math.sin((1-t)*th)/sn,kb=(float)Math.sin(t*th)/sn;for(int i=0;i<4;i++)q[i]=qa[i]*ka+qb[i]*kb;}
        float l=(float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);for(int i=0;i<4;i++)q[i]/=l;
        float w=q[0],x=q[1],y=q[2],z=q[3];
        float[] X=v3(1-2*(y*y+z*z),2*(x*y+w*z),2*(x*z-w*y)),Z=v3(2*(x*z+w*y),2*(y*z-w*x),1-2*(x*x+y*y));
        return new Pose(mix(a.w,b.w,t),Z,mul(X,-1));
    }
    /** The pose's hand rotation as a quaternion (w, x, y, z) from its axes (x = -grip axis, z = fingers). */
    private static float[] quat(Pose p){
        float[][] ax=axes(p,false);float[] X=ax[0],Y=ax[1],Z=ax[2];
        float m00=X[0],m11=Y[1],m22=Z[2],tr=m00+m11+m22;float w,x,y,z;
        // matrix with columns X, Y, Z: m[r][c]
        if(tr>0){float s=(float)Math.sqrt(tr+1)*2;w=.25f*s;x=(Y[2]-Z[1])/s;y=(Z[0]-X[2])/s;z=(X[1]-Y[0])/s;}
        else if(m00>m11&&m00>m22){float s=(float)Math.sqrt(1+m00-m11-m22)*2;w=(Y[2]-Z[1])/s;x=.25f*s;y=(Y[0]+X[1])/s;z=(Z[0]+X[2])/s;}
        else if(m11>m22){float s=(float)Math.sqrt(1+m11-m00-m22)*2;w=(Z[0]-X[2])/s;x=(Y[0]+X[1])/s;y=.25f*s;z=(Z[1]+Y[2])/s;}
        else{float s=(float)Math.sqrt(1+m22-m00-m11)*2;w=(X[1]-Y[0])/s;x=(Z[0]+X[2])/s;y=(Z[1]+Y[2])/s;z=.25f*s;}
        return new float[]{w,x,y,z};
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    /** Pose along keyframes {time, pose} at t (0..1). */
    private static Pose track(float t,float[] times,Pose[] keys){
        if(t<=times[0])return keys[0];
        for(int i=0;i+1<times.length;i++)if(t<=times[i+1])return lerp(keys[i],keys[i+1],smooth((t-times[i])/(times[i+1]-times[i])));
        return keys[keys.length-1];
    }
    /** Hand axes from a pose: x across the palm (the thumb side is -x), y the back of the hand, z the fingers (mirrored for a left hand). */
    private static float[][] axes(Pose p,boolean left){float[] z=p.F;float[] na=mul(p.U,-1);float[] x=norm(sub(na,mul(z,dot(na,z))));float[] y=cross(z,x);
        if(left)return new float[][]{mul(x,-1),mul(y,-1),z};return new float[][]{x,y,z};}
    /** The elbow: below, outside and behind the wrist, so forearms rise from the bottom corners. */
    private static float[] elbow(Pose p,float side){float[] d=forearmDir(p.w,side);float[] e=sub(p.w,mul(d,.3f));e[2]=Math.max(.08f,e[2]);return e;}
    /** Where the forearm points: from the shoulder (below and outside the view, just behind the eye) to the wrist. */
    private static float[] forearmDir(float[] wrist,float side){return norm(sub(wrist,v3(side*.21f,-.44f,-.06f)));}
    /**
     * Keeps the wrist nearly straight: the hand carries on along the forearm (cocked up slightly) and the arm does the
     * swinging. The grip / blade direction keeps the pose's roll, so turning the forearm rotates the weapon.
     */
    private static Pose armAligned(Pose p,float side,float bend){
        float[] d=forearmDir(p.w,side);float[] F=norm(add(mix(d,p.F,bend),v3(0,.08f,0)));return new Pose(p.w,F,p.U);
    }

    /**
     * Builds the frame's geometry. weapon: Rpg.FP_*. attack: 0..1 through a swing / shot / punch (or -1 at rest);
     * cast: 0..1 through a spell cast (or -1). bobX, bobY: walk sway; raise: 0..1 lowered while switching; time: seconds.
     */
    void build(int weapon,float attack,float cast,float bobX,float bobY,float raise,float time){
        count=0;
        float sway=(float)Math.sin(time*1.3f)*.004f,breathe=(float)Math.sin(time*2.1f)*.003f;
        float[] off=v3(bobX*.02f+sway,-Math.abs(bobY)*.025f-raise*.42f+breathe,0);
        boolean staff=weapon==Rpg.FP_STAFF;
        // ---- right hand (weapon hand)
        Pose rp;Grip rg=HOLD;
        switch(weapon){
            case Rpg.FP_FIST:{Pose rest=P(.34f,-.72f,.46f,-.25f,.35f,.9f,-.6f,.8f,0);rg=FIST;
                Pose hit=P(.05f,-.3f,.62f,-.08f,.1f,1,-.45f,.9f,0);
                rp=attack<0?rest:track(attack,new float[]{0,.18f,.42f,1},new Pose[]{rest,hit,hit,rest});break;}
            case Rpg.FP_STAFF:{Pose rest=P(.42f,-1.0f,.5f,-.85f,0,.5f,-.35f,.8f,.45f);
                Pose thrust=P(.2f,-.6f,.62f,-.85f,.1f,.5f,-.05f,.85f,.5f);
                rp=attack<0?rest:track(attack,new float[]{0,.25f,.5f,1},new Pose[]{rest,thrust,thrust,rest});break;}
            case Rpg.FP_BOW:{Pose drawn=P(.3f,-.6f,.42f,-1,0,.25f,0,1,0),loose=P(.16f,-.5f,.58f,-.9f,0,.45f,0,1,0);rg=PINCH;
                rp=attack<0?drawn:track(attack,new float[]{0,.1f,.55f,1},new Pose[]{drawn,P(.42f,-.55f,.38f,-1,.1f,.2f,0,1,0),loose,drawn});break;}
            case Rpg.FP_MACE:case Rpg.FP_AXE:{Pose rest=P(.36f,-.86f,.5f,-.55f,.25f,.8f,-.45f,.35f,.85f);
                Pose up=P(.42f,-.15f,.42f,-.3f,.7f,.6f,-.15f,.45f,-.9f),hit=P(.05f,-.85f,.6f,-.6f,-.75f,.25f,-.1f,0,1);
                rp=attack<0?rest:track(attack,new float[]{0,.2f,.45f,.6f,1},new Pose[]{rest,up,hit,hit,rest});break;}
            default:{   // sword, dagger: a diagonal slash right to left
                Pose rest=P(.34f,-.8f,.48f,-.55f,.25f,.8f,-.25f,.95f,.25f);
                // one continuous diagonal cut, upper right to lower left, the edge leading and the blade never rolling over
                Pose wind=P(.5f,-.42f,.44f,-.6f,.1f,.8f,.15f,.9f,-.4f),mid=P(.14f,-.42f,.55f,-.5f,-.75f,.3f,-.75f,.55f,.35f),cut=P(-.2f,-.55f,.55f,-.3f,-.85f,-.2f,-.85f,-.12f,.55f);
                if(weapon==Rpg.FP_DAGGER){rest=P(.32f,-.82f,.46f,-.6f,.2f,.75f,-.15f,.9f,.35f);wind=P(.36f,-.75f,.4f,-.7f,.1f,.6f,-.1f,.3f,1);cut=P(.08f,-.45f,.62f,-.8f,-.5f,.1f,-.05f,.1f,1);}
                if(weapon==Rpg.FP_DAGGER)rp=attack<0?rest:track(attack,new float[]{0,.22f,.5f,.62f,1},new Pose[]{rest,wind,cut,cut,rest});
                else rp=attack<0?rest:track(attack,new float[]{0,.2f,.36f,.52f,.64f,1},new Pose[]{rest,wind,mid,cut,cut,rest});}
        }
        rp=armAligned(new Pose(add(rp.w,off),rp.F,rp.U),1,weapon==Rpg.FP_BOW?.6f:.18f);
        float[][] ra=axes(rp,false);
        float[] relbow=elbow(rp,1);
        forearm(relbow,rp.w,staff);
        placeHand(rp.w,ra[0],ra[1],ra[2],false,rg,staff);
        // the weapon in the fist: the grip runs across the palm, the blade out of the thumb side
        float[] grip=add(rp.w,add(mul(ra[1],-.034f),mul(ra[2],.084f)));
        if(weapon!=Rpg.FP_FIST&&weapon!=Rpg.FP_BOW){
            float[] W=mul(ra[0],-1),S=ra[2],N=ra[1];
            placeWeapon(weapon,grip,W,S,N,staff&&(attack>=0||cast>=0)?1:0);}
        // ---- left hand: a guard fist, the bow, or the spell hand
        Pose lp;Grip lg=RELAX;
        if(weapon==Rpg.FP_BOW){lp=P(-.14f,-.38f,.62f,.9f,.1f,.4f,-.1f,1,0);lg=HOLD;}
        else if(weapon==Rpg.FP_FIST){lp=P(-.34f,-.72f,.46f,.25f,.35f,.9f,.6f,.8f,0);lg=FIST;}
        else lp=P(-.46f,-1.35f,.45f,.3f,.3f,.9f,.4f,.8f,.2f);lg=HOLD;
        if(cast>=0){Pose out=P(-.16f,-.3f,.55f,.1f,.95f,.3f,1,0,0);lg=cast>.15f&&cast<.75f?OPEN:RELAX;
            lp=track(cast,new float[]{0,.2f,.6f,1},new Pose[]{lp,out,out,lp});}
        lp=armAligned(new Pose(add(lp.w,off),lp.F,lp.U),-1,weapon==Rpg.FP_BOW?.5f:.25f);
        float[][] la=axes(lp,true);
        boolean lHidden=weapon!=Rpg.FP_BOW&&weapon!=Rpg.FP_FIST&&cast<0&&raise<.01f;
        if(!lHidden||true){
            float[] lelbow=elbow(lp,-1);
            forearm(lelbow,lp.w,staff);placeHand(lp.w,la[0],la[1],la[2],true,lg,staff);
            if(cast>.12f&&cast<.8f){ident();mat=M_GLOW;float k=(float)Math.sin((cast-.12f)/.68f*Math.PI);
                float[] palm=add(lp.w,add(mul(la[1],-.05f),mul(la[2],.06f)));ell(palm,v3(1,0,0),v3(0,1,0),v3(0,0,1),.03f*k,.03f*k,.03f*k,10,6);}
        }
        if(weapon==Rpg.FP_BOW){
            float[] W=norm(v3(-.1f,1,0)),S=norm(v3(.05f,0,1)),N=norm(cross(W,S));S=cross(N,W);
            float[] bg=add(lp.w,add(mul(la[1],-.034f),mul(la[2],.084f)));
            float[] pull=add(rp.w,add(mul(ra[2],.07f),mul(ra[1],-.02f)));
            boolean loosed=attack>=.12f&&attack<.8f;
            if(loosed)pull=add(bg,mul(S,-.04f));
            bow(bg,W,S,N,pull,!loosed);
        }
    }
}
