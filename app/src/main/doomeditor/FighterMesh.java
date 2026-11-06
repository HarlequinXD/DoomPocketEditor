package com.retro.doomeditor;

import static com.retro.doomeditor.SpriteModels.v;
import static com.retro.doomeditor.SpriteModels.add;
import static com.retro.doomeditor.SpriteModels.sub;
import static com.retro.doomeditor.SpriteModels.mul;
import static com.retro.doomeditor.SpriteModels.cross;
import static com.retro.doomeditor.SpriteModels.norm;
import static com.retro.doomeditor.SpriteModels.len;

/**
 * The Fighter, box-modelled by hand like a PS1 character (about 400 triangles):
 * <ul>
 *   <li>Upper arm and forearm: boxes shaped into a bicep and forearm. Each end face is cut corner to corner both
 *       ways, and the centre vertex pushed out into a low pyramid, so the ends look rounded where they meet.</li>
 *   <li>Fist: a box with the same pyramid end where it joins the forearm.</li>
 *   <li>Thigh and shin: 5-sided cylinders; each cap is a fan of triangles to a centre vertex pushed out a little.</li>
 *   <li>Torso and pelvis: shaped boxes. Neck: a box. Feet: a box loop-cut halfway, stretched into a foot, toe end
 *       scaled in for a mild point.</li>
 *   <li>Head: one cube subdivided once. Front and sides flat, top rounded; at the bottom the back faces lift up and
 *       the front faces drop and narrow into a jaw. The face is painted on the front.</li>
 *   <li>Cape: a single flat sheet (not a box). Sword and round shield in hand.</li>
 * </ul>
 * Model space: x = forward, y = right, z = up, 1 unit = 1 grid square.
 */
final class FighterMesh {
    private FighterMesh(){}
    // atlas tiles
    private static final int SKIN=0,FACE=1,HAIR=2,PLATE=3,TABARD=4,LEGS=5,BOOTS=6,GLOVE=7,CLOAK=8,STEEL=9,BRASS=10,WOOD=11;
    private static int G;
    private static float[] buf;private static int nv;

    static MeshModels.Mesh build(){
        SpriteModels.Model m=new SpriteModels.Model();
        m.skin(0xffc89878,.55f);m.skin(0xffc89878,.55f);m.cloth(0xff4a2e18);m.metal(0xff8a9098);m.cloth(0xff2c4a8a);m.leather(0xff4a3624);
        m.leather(0xff2e2016);m.leather(0xff3a2a1c);m.cloth(0xff7a1e1e);m.metal(0xffb8c0c8);m.metal(0xffb08a40);m.wood(0xff6a4424);
        MeshModels.Mesh out=new MeshModels.Mesh();G=4;out.atlas=G;
        for(int f=0;f<Sprites.FRAMES;f++)out.frames[f]=frame(f);
        MeshModels.paint(out,m,G);
        paintFace(out);
        return out;
    }

    // ------------------------------------------------------------------ posing
    private static float[] rotY(float[] p,float[] pv,float a){float dx=p[0]-pv[0],dz=p[2]-pv[2],c=(float)Math.cos(a),s=(float)Math.sin(a);return v(pv[0]+dx*c+dz*s,p[1],pv[2]-dx*s+dz*c);}
    private static float[] legDir(float a){return v((float)Math.sin(a),0,-(float)Math.cos(a));}
    private static MeshModels.Frame frame(int f){
        buf=new float[9*3*520];nv=0;
        SpriteModels.Pose ps=SpriteModels.pose(f);SpriteModels.Body b=new SpriteModels.Body();
        float bob=ps.bob,lean=ps.lean;
        float[] pelvis=v(0,0,b.hipZ+bob);
        // ---- legs: 5-sided cylinders, feet
        for(int s=-1;s<=1;s+=2){float th=s<0?ps.lThigh:ps.rThigh,kn=s<0?ps.lKnee:ps.rKnee;
            float[] hip=v(0,s*b.hipW,b.hipZ+bob),knee=add(hip,legDir(th),b.thigh),ankle=add(knee,legDir(th-kn),b.shin);
            cyl5(hip,knee,.072f,.052f,.018f,.016f,LEGS);
            cyl5(knee,ankle,.052f,.036f,.014f,.012f,LEGS);
            foot(ankle,BOOTS);}
        // ---- body (leans about the pelvis)
        float[] waist=rotY(v(0,0,b.waistZ+bob),pelvis,lean),chest=rotY(v(0,0,b.chestZ+bob),pelvis,lean),shTop=rotY(v(0,0,b.shoulderZ+.035f+bob),pelvis,lean);
        float[] up=norm(sub(shTop,waist));
        pelvisBox(v(0,0,b.hipZ-.03f+bob),waist,b,up);
        torso(waist,chest,shTop,b,up);
        float[] neckB=add(shTop,up,-.005f),neckT=add(shTop,up,.07f);
        box(neckB,neckT,.04f,.042f,SKIN,up);
        float hp=ps.headPitch+lean*.4f;
        head(add(neckT,up,.085f),lean+hp,b);
        // ---- arms (two-bone IK to the hands, elbows bend back and out)
        float[] rS=rotY(v(0,b.shoulderW,b.shoulderZ+bob),pelvis,lean),lS=rotY(v(0,-b.shoulderW,b.shoulderZ+bob),pelvis,lean);
        float[] rh,sd,lh;
        if(ps.dead){rh=v(.1f,.42f,b.shoulderZ-.2f);sd=norm(v(1,.3f,.1f));lh=v(.05f,-.42f,b.shoulderZ-.25f);}
        else{rh=ps.attack?v(.4f,.08f,1.05f):v(.2f,.2f,.88f+bob);sd=ps.attack?norm(v(.7f,-.55f,-.2f)):norm(v(.45f,.05f,.9f));
            if(f==Sprites.F_PAIN){rh=v(.05f,.32f,.95f);sd=norm(v(.2f,.4f,.9f));}
            lh=v(.22f,-.2f,.98f+bob);rh=rotY(rh,pelvis,lean);lh=rotY(lh,pelvis,lean);}
        float[] rE=SpriteModels.ik(rS,rh,b.upper,b.fore,v(-1,.5f,-.4f)),lE=SpriteModels.ik(lS,lh,b.upper,b.fore,v(-1,-.5f,-.4f));
        arm(rS,rE,rh,1);arm(lS,lE,lh,-1);
        sword(rh,sd);
        shield(add(lh,v(.05f,-.03f,.02f)),norm(v(1,-.25f,0)));
        // ---- cape: one flat sheet from the shoulder blades, flaring and trailing behind when walking
        float trail=(f==Sprites.F_WALK1||f==Sprites.F_WALK2)?.12f:ps.attack?.06f:.03f;
        float[] back=mul(norm(v((float)-Math.cos(lean),0,(float)Math.sin(lean))),1);
        cape(add(rotY(v(0,0,b.shoulderZ+bob),pelvis,lean),back,.11f),b,trail,up);
        MeshModels.Frame fr=new MeshModels.Frame();fr.v=java.util.Arrays.copyOf(buf,nv*9);fr.count=nv;
        if(ps.dead)layDown(fr);
        return fr;
    }
    /** Death: tip the whole body over backwards onto the floor. */
    private static void layDown(MeshModels.Frame fr){
        float a=-1.45f,c=(float)Math.cos(a),s=(float)Math.sin(a);float minZ=9;
        for(int i=0;i<fr.count;i++){int o=i*9;float x=fr.v[o],z=fr.v[o+2];fr.v[o]=x*c+z*s;fr.v[o+2]=-x*s+z*c;float nx=fr.v[o+3],nz=fr.v[o+5];fr.v[o+3]=nx*c+nz*s;fr.v[o+5]=-nx*s+nz*c;minZ=Math.min(minZ,fr.v[o+2]);}
        for(int i=0;i<fr.count;i++)fr.v[i*9+2]-=minZ-.01f;
    }

    // ------------------------------------------------------------------ vertex output
    private static void put(float[] p,float[] n,int tile,float u,float vv){
        if((nv+1)*9>buf.length)buf=java.util.Arrays.copyOf(buf,buf.length*2);
        float in=2f/MeshModels.TILE;u=in+Math.max(0,Math.min(1,u))*(1-2*in);vv=in+Math.max(0,Math.min(1,vv))*(1-2*in);
        float[] nn=norm(n);int o=nv*9;buf[o]=p[0];buf[o+1]=p[1];buf[o+2]=p[2];buf[o+3]=nn[0];buf[o+4]=nn[1];buf[o+5]=nn[2];
        buf[o+6]=((tile%G)+u)/G;buf[o+7]=((tile/G)+vv)/G;buf[o+8]=0;nv++;
    }
    /** Triangle with per-vertex normals and tile UVs {u0,v0,u1,v1,u2,v2}. */
    private static void tri(float[] a,float[] b,float[] c,float[] na,float[] nb,float[] nc,int tile,float[] uv){put(a,na,tile,uv[0],uv[1]);put(b,nb,tile,uv[2],uv[3]);put(c,nc,tile,uv[4],uv[5]);}
    private static void flat(float[] a,float[] b,float[] c,int tile,float[] uv){float[] n=cross(sub(b,a),sub(c,a));tri(a,b,c,n,n,n,tile,uv);}
    private static void quad(float[] a,float[] b,float[] c,float[] d,float[] na,float[] nb,float[] nc,float[] nd,int tile,float u0,float v0,float u1,float v1){
        tri(a,b,c,na,nb,nc,tile,new float[]{u0,v0,u1,v0,u1,v1});tri(a,c,d,na,nc,nd,tile,new float[]{u0,v0,u1,v1,u0,v1});}
    /** Two axes across a segment: s (sideways) and f (front), preferring model forward for f. */
    private static float[][] across(float[] ax,float[] fwdHint){float[] f=sub(fwdHint,mul(ax,SpriteModels.dot(fwdHint,ax)));if(len(f)<1e-3f)f=sub(v(0,0,1),mul(ax,ax[2]));f=norm(f);float[] s=norm(cross(ax,f));return new float[][]{s,f};}

    // ------------------------------------------------------------------ parts
    /**
     * Box from a to b with a w x d cross-section at each end (tapered), and pyramid ends: each end face is cut into 4
     * triangles meeting at a centre vertex pushed out by domeA / domeB (0 = flat end, two triangles).
     */
    private static void seg(float[] a,float[] b,float wa,float da,float wb,float db,float domeA,float domeB,int tile,float[] fwd){
        float[] ax=norm(sub(b,a));float[][] sf=across(ax,fwd);float[] s=sf[0],f=sf[1];
        float[][] A=new float[4][],B=new float[4][],N=new float[4][];
        int[][] cc={{1,1},{-1,1},{-1,-1},{1,-1}};
        for(int i=0;i<4;i++){float cs=cc[i][0],cf=cc[i][1];A[i]=add(add(a,s,cs*wa),f,cf*da);B[i]=add(add(b,s,cs*wb),f,cf*db);N[i]=add(mul(s,cs),f,cf);}
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(A[i],A[j],B[j],B[i],N[i],N[j],N[j],N[i],tile,i/4f,0,(i+1)/4f,1);}
        cap(A,a,mul(ax,-1),domeA,tile,true);cap(B,b,ax,domeB,tile,false);
    }
    private static void cap(float[][] ring,float[] c,float[] out,float dome,int tile,boolean flip){
        int n=ring.length;
        if(dome<=0&&n==4){float[] nn=out;if(flip){tri(ring[0],ring[2],ring[1],nn,nn,nn,tile,new float[]{0,0,1,1,1,0});tri(ring[0],ring[3],ring[2],nn,nn,nn,tile,new float[]{0,0,0,1,1,1});}
            else{tri(ring[0],ring[1],ring[2],nn,nn,nn,tile,new float[]{0,0,1,0,1,1});tri(ring[0],ring[2],ring[3],nn,nn,nn,tile,new float[]{0,0,1,1,0,1});}return;}
        float[] tip=add(c,out,dome);
        for(int i=0;i<n;i++){int j=(i+1)%n;float a0=(float)(i*2*Math.PI/n),a1=(float)(j*2*Math.PI/n);
            float[] uv={.5f+.45f*(float)Math.cos(a0),.5f+.45f*(float)Math.sin(a0),.5f+.45f*(float)Math.cos(a1),.5f+.45f*(float)Math.sin(a1),.5f,.5f};
            float[] n1=add(norm(sub(ring[i],c)),out,1.2f),n2=add(norm(sub(ring[j],c)),out,1.2f);
            if(flip)tri(ring[j],ring[i],tip,n2,n1,out,tile,new float[]{uv[2],uv[3],uv[0],uv[1],.5f,.5f});else tri(ring[i],ring[j],tip,n1,n2,out,tile,uv);}
    }
    /** 5-sided cylinder a→b with radii ra/rb; caps are fans to a centre vertex pushed out by domeA / domeB. */
    private static void cyl5(float[] a,float[] b,float ra,float rb,float domeA,float domeB,int tile){
        float[] ax=norm(sub(b,a));float[][] sf=across(ax,v(1,0,0));float[] s=sf[0],f=sf[1];
        float[][] A=new float[5][],B=new float[5][],N=new float[5][];
        for(int i=0;i<5;i++){double t=i*2*Math.PI/5+Math.PI/2;float[] d=add(mul(f,(float)Math.sin(t)),s,(float)Math.cos(t));N[i]=d;A[i]=add(a,d,ra);B[i]=add(b,d,rb);}
        for(int i=0;i<5;i++){int j=(i+1)%5;quad(A[i],A[j],B[j],B[i],N[i],N[j],N[j],N[i],tile,i/5f,0,(i+1)/5f,1);}
        cap(A,a,mul(ax,-1),domeA,tile,true);cap(B,b,ax,domeB,tile,false);
    }
    private static void box(float[] a,float[] b,float w,float d,int tile,float[] fwdHint){seg(a,b,w,d,w,d,0,0,tile,v(1,0,0));}
    private static void arm(float[] sh,float[] el,float[] hand,int side){
        float[] fw=v(1,0,0);
        // bicep: thick in the middle of the upper arm, shaped box with rounded (pyramid) ends at the shoulder and elbow
        seg(sh,el,.05f,.052f,.04f,.042f,.03f,.022f,PLATE,fw);
        // forearm: wider below the elbow, narrowing to the wrist, rounded both ends
        float[] dir=norm(sub(hand,el));float[] wrist=add(hand,dir,-.035f);
        seg(el,wrist,.042f,.044f,.031f,.033f,.02f,.016f,GLOVE,fw);
        // fist: a box, pyramid end where it meets the forearm, flat knuckles
        seg(wrist,add(wrist,dir,.085f),.032f,.04f,.03f,.038f,.018f,0,GLOVE,fw);
    }
    /** Foot: box loop-cut halfway, stretched forward from the heel; the toe end narrowed for a mild point. */
    private static void foot(float[] ankle,int tile){
        float sole=Math.max(0,ankle[2]-.07f);float[] heel=v(ankle[0]-.045f,ankle[1],sole),mid=v(ankle[0]+.05f,ankle[1],sole),toe=v(ankle[0]+.15f,ankle[1],sole);
        float[][] R0=ring(heel,.042f,.075f),R1=ring(mid,.048f,.065f),R2=ring(toe,.032f,.035f);
        float[][] N={v(0,1,0),v(0,0,1),v(0,-1,0),v(0,0,-1)};
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(R0[i],R0[j],R1[j],R1[i],N[i],N[j],N[j],N[i],tile,i/4f,0,(i+1)/4f,.5f);quad(R1[i],R1[j],R2[j],R2[i],N[i],N[j],N[j],N[i],tile,i/4f,.5f,(i+1)/4f,1);}
        cap(R0,heel,v(-1,0,0),0,tile,true);cap(R2,toe,v(1,0,0),0,tile,false);
    }
    /** Cross-section of the foot at x: 4 corners (right-bottom, right-top, left-top, left-bottom) from the sole up to height h. */
    private static float[][] ring(float[] c,float halfW,float h){return new float[][]{v(c[0],c[1]+halfW,c[2]),v(c[0],c[1]+halfW,c[2]+h),v(c[0],c[1]-halfW,c[2]+h),v(c[0],c[1]-halfW,c[2])};}
    private static void pelvisBox(float[] bottom,float[] top,SpriteModels.Body b,float[] up){
        seg(bottom,top,b.hipW*1.75f,.085f,b.chestW*.82f,.09f,.012f,0,TABARD,v(1,0,0));
    }
    /** Torso: waist → chest → shoulders, a box with one loop cut at the chest, shaped wider across the chest. */
    private static void torso(float[] waist,float[] chest,float[] top,SpriteModels.Body b,float[] up){
        float[][] sf=across(up,v(1,0,0));float[] s=sf[0],f=sf[1];
        float[][] R0=new float[4][],R1=new float[4][],R2=new float[4][],N=new float[4][];int[][] cc={{1,1},{-1,1},{-1,-1},{1,-1}};
        for(int i=0;i<4;i++){float cs=cc[i][0],cf=cc[i][1];
            R0[i]=add(add(waist,s,cs*b.chestW*.82f),f,cf*.09f);R1[i]=add(add(chest,s,cs*b.chestW*1.05f),f,cf*(cf>0?.115f:.095f));R2[i]=add(add(top,s,cs*b.shoulderW*1.1f),f,cf*.085f);N[i]=add(mul(s,cs),f,cf);}
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(R0[i],R0[j],R1[j],R1[i],N[i],N[j],N[j],N[i],PLATE,i/4f,0,(i+1)/4f,.5f);quad(R1[i],R1[j],R2[j],R2[i],N[i],N[j],N[j],N[i],PLATE,i/4f,.5f,(i+1)/4f,1);}
        cap(R0,waist,mul(up,-1),0,PLATE,true);cap(R2,top,up,.025f,PLATE,false);
    }
    /**
     * Head: a cube subdivided once (3 x 3 x 3 lattice, 24 quads). Front and sides stay flat, the top is rounded,
     * the back of the bottom lifts toward the neck and the front of the bottom drops and narrows into a jaw.
     */
    private static void head(float[] c,float pitch,SpriteModels.Body b){
        float hx=.098f,hy=.083f,hz=.105f;
        float[][][][] P=new float[3][3][3][];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int k=0;k<3;k++){float x=i-1,y=j-1,z=k-1;
            if(k==2){boolean cx=Math.abs(x)==1,cy=Math.abs(y)==1;   // rounded top
                if(cx&&cy){z=.55f;x*=.86f;y*=.86f;}else if(cx||cy){z=.86f;if(cx)x*=.93f;if(cy)y*=.93f;}else z=1.02f;}
            if(k==0){   // jaw: back of the bottom lifts, front drops and narrows
                if(i==0){z=-.38f;y*=.9f;}else if(i==1){z=-.88f;y*=.92f;}else{z=-1.16f;y*=.68f;x*=.98f;}}
            if(k==1&&i==0)y*=.97f;
            float[] p=v(x*hx,y*hy,z*hz);
            // tilt with the neck, then place
            float cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);P[i][j][k]=v(c[0]+p[0]*cp+p[2]*sp,c[1]+p[1],c[2]-p[0]*sp+p[2]*cp);}
        // faces of the subdivided cube: each made of 2x2 quads
        for(int a=0;a<2;a++)for(int d=0;d<2;d++){
            faceQuad(P[2][a][d+1],P[2][a+1][d+1],P[2][a+1][d],P[2][a][d],FACE,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);   // front (+x): the face
            faceQuad(P[0][a+1][d+1],P[0][a][d+1],P[0][a][d],P[0][a+1][d],HAIR,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);   // back of the head
            faceQuad(P[a][2][d+1],P[a+1][2][d+1],P[a+1][2][d],P[a][2][d],d==1?HAIR:SKIN,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);   // right side
            faceQuad(P[a+1][0][d+1],P[a][0][d+1],P[a][0][d],P[a+1][0][d],d==1?HAIR:SKIN,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);   // left side
            faceQuad(P[a][d][2],P[a][d+1][2],P[a+1][d+1][2],P[a+1][d][2],HAIR,a/2f,d/2f,(a+1)/2f,(d+1)/2f);   // top
            faceQuad(P[a+1][d][0],P[a+1][d+1][0],P[a][d+1][0],P[a][d][0],SKIN,a/2f,d/2f,(a+1)/2f,(d+1)/2f);   // under the jaw
        }
    }
    private static void faceQuad(float[] a,float[] b,float[] c,float[] d,int tile,float u0,float v0,float u1,float v1){
        float[] n=norm(cross(sub(b,a),sub(d,a)));quad(a,b,c,d,n,n,n,n,tile,u0,v0,u1,v1);
    }
    private static void sword(float[] hand,float[] dir){
        float[] base=add(hand,dir,-.07f),gTop=add(hand,dir,.06f);
        seg(base,gTop,.014f,.014f,.014f,.014f,.008f,0,GLOVE,v(0,0,1));                       // grip
        float[] side=norm(cross(dir,v(0,0,1)));if(len(cross(dir,v(0,0,1)))<1e-3f)side=v(0,1,0);
        seg(add(gTop,side,-.1f),add(gTop,side,.1f),.016f,.016f,.016f,.016f,0,0,BRASS,dir);   // cross-guard
        seg(add(base,dir,-.025f),base,.022f,.022f,.022f,.022f,.012f,0,BRASS,v(0,0,1));         // pommel
        seg(add(gTop,dir,.012f),add(gTop,dir,.6f),.028f,.006f,.024f,.006f,0,.07f,STEEL,side);  // blade, pyramid point
    }
    /** Round shield: an 8-sided disc, the front fan's centre pushed out into a boss. */
    private static void shield(float[] c,float[] facing){
        float[][] sf=across(facing,v(0,0,1));float[] s=sf[0],u=sf[1];float R=.21f,T=.018f;
        float[] front=add(c,facing,T),back=add(c,facing,-T);float[][] F=new float[8][],B=new float[8][],N=new float[8][];
        for(int i=0;i<8;i++){double a=i*Math.PI/4+Math.PI/8;float[] d=add(mul(s,(float)Math.cos(a)),u,(float)Math.sin(a));N[i]=d;F[i]=add(front,d,R);B[i]=add(back,d,R);}
        for(int i=0;i<8;i++){int j=(i+1)%8;quad(F[i],F[j],B[j],B[i],N[i],N[j],N[j],N[i],BRASS,i/8f,0,(i+1)/8f,1);}
        cap(F,front,facing,.05f,WOOD,false);cap(B,back,mul(facing,-1),0,WOOD,true);
    }
    /** Cape: one flat sheet (3 x 2 quads), hanging from the shoulders and trailing back. */
    private static void cape(float[] top,SpriteModels.Body b,float trail,float[] up){
        int cols=2,rows=3;float w0=b.shoulderW*.95f,w1=b.shoulderW*1.35f,h=b.shoulderZ-.32f;
        float[][][] P=new float[rows+1][cols+1][];
        for(int r=0;r<=rows;r++)for(int cI=0;cI<=cols;cI++){float t=r/(float)rows,w=w0+(w1-w0)*t,y=(cI/(float)cols*2-1)*w;
            float back=-t*t*trail*2-t*.06f-Math.abs(y)*.06f*t;P[r][cI]=v(top[0]+back,top[1]+y,top[2]-t*h);}
        for(int r=0;r<rows;r++)for(int cI=0;cI<cols;cI++){float[] a=P[r][cI],bb=P[r][cI+1],cc=P[r+1][cI+1],d=P[r+1][cI];float[] n=norm(cross(sub(d,a),sub(bb,a)));
            quad(a,bb,cc,d,n,n,n,n,CLOAK,cI/(float)cols,r/(float)rows,(cI+1)/(float)cols,(r+1)/(float)rows);}
    }

    // ------------------------------------------------------------------ face paint (front of the head)
    private static void paintFace(MeshModels.Mesh m){
        int S=G*MeshModels.TILE,T=MeshModels.TILE,ox=(FACE%G)*T,oy=(FACE/G)*T;int[] px=m.color;
        int skin=px[oy*S+ox+T/2];
        int r0=(skin>>16)&255,g0=(skin>>8)&255,b0=skin&255;
        for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*S+ox+x;int c=px[o];int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            float u=x/(float)T,v=y/(float)T;   // u: left→right across the face, v: top→bottom
            if(v<.2f){int hc=0xff3a2414;float n=(float)Math.random()*.15f;r=(int)(58*(1-n));g=(int)(36*(1-n));b=(int)(22*(1-n));}   // hairline
            float eyeY=.43f;
            for(int s=-1;s<=1;s+=2){float ex=.5f+s*.19f;
                if(Math.abs(v-(eyeY-.075f))<.022f&&Math.abs(u-ex)<.1f){r=60;g=38;b=24;}                          // brows
                if(Math.abs(v-eyeY)<.035f&&Math.abs(u-ex)<.075f){r=235;g=230;b=222;if(Math.abs(u-ex)<.035f){r=52;g=80;b=96;if(Math.abs(u-ex)<.015f){r=12;g=12;b=12;}}}}
            if(u>.45f&&u<.55f&&v>.45f&&v<.66f){r=(int)(r*.82f);g=(int)(g*.8f);b=(int)(b*.8f);}                // nose shadow
            if(v>.63f&&v<.67f&&u>.44f&&u<.56f){r=(int)(r*.7f);g=(int)(g*.68f);b=(int)(b*.68f);}
            if(Math.abs(v-.77f)<.018f&&Math.abs(u-.5f)<.11f){r=118;g=52;b=48;}                                   // mouth
            if(v>.7f&&(u<.2f||u>.8f||v>.86f)){r=(int)(r*.55f);g=(int)(g*.5f);b=(int)(b*.45f);}                   // stubble along the jaw
            px[o]=0xff000000|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);}
    }
}
