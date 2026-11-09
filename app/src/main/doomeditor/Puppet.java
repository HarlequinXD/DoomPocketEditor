package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Characters as 2D puppets, like PS1 Die Hard Trilogy: every body part is a 32 x 32 pixel-art sprite drawn from 8
 * angles, hung on a skeleton and animated by moving the joints.
 *
 * <p>The art comes from the box-modelled characters (BoxMesh): each part (head, torso, hips, upper arm, forearm, hand,
 * thigh, calf, foot, weapon, off-hand item) is cut out of the model in its idle pose, turned to stand along its bone,
 * rendered from 8 angles with its painted texture and proper lighting (a key light from the upper left fixed to the
 * screen, soft fill from the sky, a rim of light round the edge, highlights on metal), shrunk to 32 x 32 from 4 x 4
 * samples per pixel, reduced to the character's own palette with a 4 x 4 ordered dither, and given a dark outline.
 * Left arms and legs use the right side's pictures mirrored.</p>
 *
 * <p>In play the skeleton comes from the same animation poses as the models (walk and run cycles, jumps, three-stage
 * attacks, hits and death), blended between poses; the parts are drawn back to front along their bones into one
 * sprite (64 px per unit), which the renderer shows like any other sprite.</p>
 */
final class Puppet {
    private Puppet(){}

    /** Off: built-in characters are drawn as before (raster pictures of the 3D models). */
    static volatile boolean enabled=true;
    static final int ART=32,SS=4,VIEWS=8;
    /** Density of the finished sprite. */
    static final float COMP_PPU=64f;
    /** The second breathing pose (BoxMesh.A_IDLE2), for previews that breathe. */
    static final int A_IDLE2_FRAME=6;

    // ---- body parts as built (BoxMesh tags each vertex with one)
    static final int P_HEAD=0,P_TORSO=1,P_PELVIS=2,P_UPPER_R=3,P_FORE_R=4,P_HAND_R=5,P_THIGH_R=6,P_CALF_R=7,P_FOOT_R=8,
            P_UPPER_L=9,P_FORE_L=10,P_HAND_L=11,P_THIGH_L=12,P_CALF_L=13,P_FOOT_L=14,P_WEAPON=15,P_OFFHAND=16,P_BACK=17,PARTS=18;
    // ---- the drawings: one per kind of part (left limbs reuse the right ones mirrored)
    static final int A_HEAD=0,A_TORSO=1,A_PELVIS=2,A_UPPER=3,A_FORE=4,A_HAND=5,A_THIGH=6,A_CALF=7,A_FOOT=8,A_WEAPON=9,A_OFFHAND=10,A_BACK=11,ARTS=12;
    static final String[] ART_NAMES={"Head","Torso","Hips","Upper arm","Forearm","Hand","Thigh","Calf","Foot","Weapon","Off-hand","Cape / back"};
    /** Drawing used by each part, and whether it is the mirror image. */
    static final int[] ART_OF={A_HEAD,A_TORSO,A_PELVIS,A_UPPER,A_FORE,A_HAND,A_THIGH,A_CALF,A_FOOT,A_UPPER,A_FORE,A_HAND,A_THIGH,A_CALF,A_FOOT,A_WEAPON,A_OFFHAND,A_BACK};
    static final boolean[] MIRROR={false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,false,false,false};
    // ---- joints (BoxMesh records them for every pose)
    static final int J_NECK=0,J_CROWN=1,J_WAIST=2,J_CROTCH=3,J_SH_R=4,J_EL_R=5,J_WR_R=6,J_FIST_R=7,J_SH_L=8,J_EL_L=9,J_WR_L=10,J_FIST_L=11,
            J_HIP_R=12,J_KN_R=13,J_AN_R=14,J_SOLE_R=15,J_HIP_L=16,J_KN_L=17,J_AN_L=18,J_SOLE_L=19,J_GRIP=20,J_WTIP=21,J_OFF=22,J_OFFB=23,J_BACK=24,JOINTS=25;
    /** Each part's bone: from joint A (where its picture hangs) to joint B. The head stands up from the neck. */
    static final int[][] BONE={{J_NECK,J_CROWN},{J_NECK,J_WAIST},{J_WAIST,J_CROTCH},{J_SH_R,J_EL_R},{J_EL_R,J_WR_R},{J_WR_R,J_FIST_R},
            {J_HIP_R,J_KN_R},{J_KN_R,J_AN_R},{J_AN_R,J_SOLE_R},{J_SH_L,J_EL_L},{J_EL_L,J_WR_L},{J_WR_L,J_FIST_L},
            {J_HIP_L,J_KN_L},{J_KN_L,J_AN_L},{J_AN_L,J_SOLE_L},{J_GRIP,J_WTIP},{J_OFF,J_OFFB},{J_NECK,J_WAIST}};
    /** Parts that may shorten when they point at the camera (limbs and weapons); the rest only turn. */
    private static final boolean[] STRETCH={false,false,false,true,true,true,true,true,false,true,true,true,true,true,false,true,false,false};
    /** Draw-order nudges (units, + = further back): legs go behind the hips and skirt, the head in front of the body. */
    private static final float[] BIAS={-.05f,-.01f,0,-.02f,-.03f,-.04f,.09f,.07f,.05f,-.02f,-.03f,-.04f,.09f,.07f,.05f,-.05f,-.045f,0};

    /** What BoxMesh hands over to make a puppet. */
    static final class Source {
        float[] rest;int[] parts;int count;          // the idle pose: 9 floats per vertex, a part per vertex
        float[][][] joints;                          // [anim frame][joint][xyz]
        int[] color;int grid,tile=MeshModels.TILE;SpriteModels.Model mats;
    }
    /** One drawing: 8 views of 32 x 32, the joint's pixel and the bone's length in pixels. */
    static final class Art {final int[][] px=new int[VIEWS][];int w=ART,h=ART;float ppu,jx=ART/2f,jy,len;boolean up,empty=true;}
    /** Long things (staffs, capes, robes) get taller pictures, up to 128 pixels, so they keep about this many pixels per unit. */
    static final float MIN_PPU=64f;static final int MAX_ART=128;
    static final class Rig {final Art[] art=new Art[ARTS];float[][][] joints;}

    // ------------------------------------------------------------------ rigs: built in the background, kept in memory and on disk
    private static final Map<String,Rig> rigs=new java.util.HashMap<>();
    private static final java.util.HashSet<String> building=new java.util.HashSet<>();
    private static final Object lock=new Object();
    static String key(int id){return id==Sprites.CREATED?"look_"+CharLook.current.key():"char_"+id;}
    /** The rig for a built-in character, or null while it is being made (it then appears a moment later). */
    static Rig rig(int id){
        if(!BoxMesh.has(id))return null;String k=key(id);
        synchronized(lock){Rig r=rigs.get(k);if(r!=null)return r;if(building.contains(k))return null;building.add(k);}
        final int fid=id;final String fk=k;final CharLook look=id==Sprites.CREATED?CharLook.current.copy():null;
        Thread t=new Thread(()->{Rig r=null;try{r=build(fk,()->look!=null?BoxMesh.sourceLook(look,false):BoxMesh.source(fid,false),()->look!=null?BoxMesh.sourceLook(look,true):BoxMesh.source(fid,true));}catch(Throwable e){r=null;}
            synchronized(lock){if(r!=null)rigs.put(fk,r);building.remove(fk);}},"puppet");
        t.setPriority(Thread.NORM_PRIORITY-1);t.start();
        return null;
    }
    /** Rig for any look (the character creator), built on the calling thread. */
    static Rig rigForLook(CharLook l){String k="look_"+l.key();synchronized(lock){Rig r=rigs.get(k);if(r!=null)return r;}
        Rig r=build(k,()->BoxMesh.sourceLook(l,false),()->BoxMesh.sourceLook(l,true));synchronized(lock){if(r!=null)rigs.put(k,r);}return r;}
    /** Builds every built-in character's rig ahead of time (loading screen), waiting for them. */
    static void warm(){for(int id:Sprites.CHARACTERS){String k=key(id);synchronized(lock){if(rigs.containsKey(k))continue;}
        final int fid=id;try{Rig r=build(k,()->BoxMesh.source(fid,false),()->BoxMesh.source(fid,true));synchronized(lock){if(r!=null)rigs.put(k,r);}}catch(Throwable ignored){}}}
    static void invalidate(int id){synchronized(lock){rigs.remove(key(id));}synchronized(sprites){sprites.clear();}}

    // ------------------------------------------------------------------ making the drawings
    /** {@code poses}: the joints only (quick); {@code painted}: everything, needed only when the pictures aren't on disk yet. */
    private static Rig build(String key,java.util.function.Supplier<Source> poses,java.util.function.Supplier<Source> painted){
        Source p0=poses.get();if(p0==null)return null;
        Rig r=new Rig();r.joints=p0.joints;
        if(loadArt(key,r))return r;
        return build(key,painted.get());
    }
    private static Rig build(String key,Source src){
        if(src==null)return null;
        Rig r=new Rig();r.joints=src.joints;
        float[][] J=src.joints[0];
        float[][][] rgb=new float[ARTS][VIEWS][];boolean[][][] cov=new boolean[ARTS][VIEWS][];boolean[][][] glow=new boolean[ARTS][VIEWS][];
        for(int a=0;a<ARTS;a++){int part=partFor(a);r.art[a]=new Art();renderArt(src,J,part,r.art[a],rgb[a],cov[a],glow[a]);}
        // the character's palette from all its pixels, then dither every drawing to it and outline it
        int[] pal=palette(rgb,cov,glow,40);
        for(int a=0;a<ARTS;a++)for(int v=0;v<VIEWS;v++)if(rgb[a][v]!=null)r.art[a].px[v]=finish(rgb[a][v],cov[a][v],glow[a][v],pal,r.art[a].w,r.art[a].h);
        saveArt(key,r);
        return r;
    }
    private static int partFor(int art){for(int p=0;p<PARTS;p++)if(ART_OF[p]==art&&!MIRROR[p])return p;return 0;}

    /** Renders one part from 8 angles, standing along its bone, framed to fill the 32 x 32 tile (same zoom in every view). */
    private static void renderArt(Source src,float[][] J,int part,Art art,float[][] outRgb,boolean[][] outCov,boolean[][] outGlow){
        float[] A=J[BONE[part][0]],B=J[BONE[part][1]];boolean up=part==P_HEAD;art.up=up;
        float[] bone=sub(B,A);float len=len(bone);if(len<1e-4f){bone=new float[]{0,0,-1};len=.1f;}
        float[] ez=up?mul(bone,1/len):mul(bone,-1/len);
        float[] ex=sub(new float[]{1,0,0},mul(ez,ez[0]));if(len(ex)<.2f)ex=sub(new float[]{0,0,1},mul(ez,ez[2]));ex=mul(ex,1/len(ex));
        float[] ey=cross(ez,ex);
        // the part's triangles in its own space
        ArrayList<float[]> tris=new ArrayList<>();
        float rmax=0,zmin=1e9f,zmax=-1e9f;
        for(int i=0;i+2<src.count;i+=3){if(src.parts[i]!=part)continue;
            float[] t=new float[27];boolean degenerate=true;
            for(int k=0;k<3;k++){int o=(i+k)*9;float[] p={src.rest[o]-A[0],src.rest[o+1]-A[1],src.rest[o+2]-A[2]},n={src.rest[o+3],src.rest[o+4],src.rest[o+5]};
                float x=dot(p,ex),y=dot(p,ey),z=dot(p,ez);t[k*9]=x;t[k*9+1]=y;t[k*9+2]=z;t[k*9+3]=dot(n,ex);t[k*9+4]=dot(n,ey);t[k*9+5]=dot(n,ez);
                t[k*9+6]=src.rest[o+6];t[k*9+7]=src.rest[o+7];t[k*9+8]=src.rest[o+8];}
            for(int k=0;k<3&&degenerate;k++)for(int m=k+1;m<3;m++)if(Math.abs(t[k*9]-t[m*9])+Math.abs(t[k*9+1]-t[m*9+1])+Math.abs(t[k*9+2]-t[m*9+2])>1e-5f){degenerate=false;break;}
            if(degenerate)continue;
            tris.add(t);
            for(int k=0;k<3;k++){rmax=Math.max(rmax,(float)Math.hypot(t[k*9],t[k*9+1]));zmin=Math.min(zmin,t[k*9+2]);zmax=Math.max(zmax,t[k*9+2]);}}
        if(tris.isEmpty())return;
        art.empty=false;
        // 32 x 32, unless that would make the part coarser than MIN_PPU: then wider and taller as needed, up to 128
        float sw=2*rmax*1.08f+.004f,sht=(zmax-zmin)*1.08f+.004f;float ppu=ART/Math.max(sw,sht);int w=ART,h=ART;
        if(ppu<MIN_PPU){ppu=Math.min(MIN_PPU,Math.min(MAX_ART/sw,MAX_ART/sht));w=Math.max(ART,Math.min(MAX_ART,(int)Math.ceil(sw*ppu)));h=Math.max(ART,Math.min(MAX_ART,(int)Math.ceil(sht*ppu)));}
        float spanX=w/ppu,spanY=h/ppu,top=zmax+(spanY-(zmax-zmin))/2;
        art.w=w;art.h=h;art.jx=w/2f;art.ppu=ppu;art.jy=top*ppu;art.len=len*ppu;
        int NW=w*SS,NH=h*SS,S=src.grid*src.tile;
        for(int v=0;v<VIEWS;v++){
            double a=v*Math.PI/4;float cx=(float)Math.cos(a),cy=(float)Math.sin(a);   // camera direction (from the part to the camera)
            float ux=cy,uy=-cx;                                                          // screen right
            float[] col=new float[NW*NH*3];float[] zb=new float[NW*NH];java.util.Arrays.fill(zb,-1e9f);boolean[] gl=new boolean[NW*NH];boolean[] hit=new boolean[NW*NH];
            // light fixed to the screen: from the upper left, a little in front
            float lx=-.55f*ux+.45f*cx,ly=-.55f*uy+.45f*cy,lz=.75f;float ll=(float)Math.sqrt(lx*lx+ly*ly+lz*lz);lx/=ll;ly/=ll;lz/=ll;
            float hx=lx+cx,hy=ly+cy,hz=lz;float hl=(float)Math.sqrt(hx*hx+hy*hy+hz*hz);hx/=hl;hy/=hl;hz/=hl;
            for(float[] t:tris){
                float[] sx=new float[3],sy=new float[3],dz=new float[3];
                for(int k=0;k<3;k++){float x=t[k*9],y=t[k*9+1],z=t[k*9+2];sx[k]=(x*ux+y*uy+spanX/2)*ppu*SS;sy[k]=(top-z)*ppu*SS;dz[k]=x*cx+y*cy;}
                float area=(sx[1]-sx[0])*(sy[2]-sy[0])-(sx[2]-sx[0])*(sy[1]-sy[0]);if(Math.abs(area)<1e-6f)continue;
                int x0=Math.max(0,(int)Math.floor(Math.min(sx[0],Math.min(sx[1],sx[2])))),x1=Math.min(NW-1,(int)Math.ceil(Math.max(sx[0],Math.max(sx[1],sx[2]))));
                int y0=Math.max(0,(int)Math.floor(Math.min(sy[0],Math.min(sy[1],sy[2])))),y1=Math.min(NH-1,(int)Math.ceil(Math.max(sy[0],Math.max(sy[1],sy[2]))));
                boolean glow=t[8]>.5f;
                for(int py=y0;py<=y1;py++)for(int px=x0;px<=x1;px++){float qx=px+.5f,qy=py+.5f;
                    float b0=((sx[1]-qx)*(sy[2]-qy)-(sx[2]-qx)*(sy[1]-qy))/area,b1=((sx[2]-qx)*(sy[0]-qy)-(sx[0]-qx)*(sy[2]-qy))/area,b2=1-b0-b1;
                    if(b0<-1e-4f||b1<-1e-4f||b2<-1e-4f)continue;
                    float d=b0*dz[0]+b1*dz[1]+b2*dz[2];int q=py*NW+px;if(d<=zb[q])continue;zb[q]=d;hit[q]=true;
                    float tu=b0*t[6]+b1*t[15]+b2*t[24],tv=b0*t[7]+b1*t[16]+b2*t[25];
                    int c=src.color[Math.min(S-1,Math.max(0,(int)(tv*S)))*S+Math.min(S-1,Math.max(0,(int)(tu*S)))];
                    float r=((c>>16)&255),g=((c>>8)&255),bb=(c&255);
                    if(glow){col[q*3]=Math.min(255,r*1.1f);col[q*3+1]=Math.min(255,g*1.1f);col[q*3+2]=Math.min(255,bb*1.1f);gl[q]=true;continue;}
                    float nx=b0*t[3]+b1*t[12]+b2*t[21],ny=b0*t[4]+b1*t[13]+b2*t[22],nz=b0*t[5]+b1*t[14]+b2*t[23];
                    float nl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(nl>0){nx/=nl;ny/=nl;nz/=nl;}
                    if(nx*cx+ny*cy<0&&Math.abs(nz)<.95f){nx=-nx;ny=-ny;}     // open sheets (capes, skirts) lit from the side we see
                    float lam=Math.max(0,(nx*lx+ny*ly+nz*lz+.15f)/1.15f),sky=.5f+.5f*nz,facing=Math.max(0,nx*cx+ny*cy);
                    float rim=(float)Math.pow(1-facing,3)*.22f;
                    float sh=.34f+.16f*sky+.72f*lam;
                    float spec=0;int tile=Math.min(src.mats.mats.size()-1,(int)(tv*src.grid)*src.grid+(int)(tu*src.grid));
                    SpriteModels.Mat mt=tile>=0&&tile<src.mats.mats.size()?src.mats.mats.get(tile):null;
                    if(mt!=null&&(mt.kind==SpriteModels.K_METAL||mt.metal>.3f))spec=(float)Math.pow(Math.max(0,nx*hx+ny*hy+nz*hz),20)*.75f;
                    else if(mt!=null&&mt.kind==SpriteModels.K_LEATHER)spec=(float)Math.pow(Math.max(0,nx*hx+ny*hy+nz*hz),12)*.18f;
                    col[q*3]=r*sh+255*(spec+rim*.9f);col[q*3+1]=g*sh+255*(spec*.96f+rim*.55f);col[q*3+2]=bb*sh+255*(spec*.9f+rim*.45f);}
            }
            // 4 x 4 samples -> one pixel
            float[] o=new float[w*h*3];boolean[] cv=new boolean[w*h],gg=new boolean[w*h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int n=0,ng=0;float r=0,g=0,b=0;
                for(int j=0;j<SS;j++)for(int i=0;i<SS;i++){int q=(y*SS+j)*NW+x*SS+i;if(!hit[q])continue;n++;r+=col[q*3];g+=col[q*3+1];b+=col[q*3+2];if(gl[q])ng++;}
                int k=y*w+x;if(n*2>=SS*SS*.9f){cv[k]=true;o[k*3]=r/n;o[k*3+1]=g/n;o[k*3+2]=b/n;gg[k]=ng*2>n;}}
            outRgb[v]=o;outCov[v]=cv;outGlow[v]=gg;
        }
    }

    private static final float[] BAYER={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
    /** Ordered dither to the palette, then a dark outline round the shape (each outline pixel a deep shade of its neighbour). */
    private static int[] finish(float[] rgb,boolean[] cov,boolean[] glow,int[] pal,int W,int H){
        cov=cov.clone();
        {int[] lab=new int[W*H];int[] stack=new int[W*H];   // drop specks: pieces of fewer than 4 pixels
            for(int s0=0;s0<W*H;s0++){if(!cov[s0]||lab[s0]!=0)continue;int n=0,sp=0;stack[sp++]=s0;lab[s0]=1;java.util.ArrayList<Integer> comp=new java.util.ArrayList<>();
                while(sp>0){int q=stack[--sp];comp.add(q);n++;int x=q%W,y=q/W;
                    int[] nb={x>0?q-1:-1,x<W-1?q+1:-1,y>0?q-W:-1,y<H-1?q+W:-1};for(int w:nb)if(w>=0&&cov[w]&&lab[w]==0){lab[w]=1;stack[sp++]=w;}}
                if(n<4)for(int q:comp)cov[q]=false;}}
        int[] px=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int k=y*W+x;if(!cov[k])continue;
            float d=glow[k]?0:((BAYER[(y&3)*4+(x&3)]+.5f)/16f-.5f)*26f;
            px[k]=nearest(pal,rgb[k*3]+d,rgb[k*3+1]+d,rgb[k*3+2]+d);}
        int[] out=px.clone();
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int k=y*W+x;if(cov[k])continue;float r=0,g=0,b=0;int n=0;
            for(int[] dd:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int xx=x+dd[0],yy=y+dd[1];if(xx<0||yy<0||xx>=W||yy>=H)continue;int q=yy*W+xx;if(!cov[q])continue;
                int c=px[q];r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;n++;}
            if(n>0)out[k]=0xff000000|((int)(r/n*.3f)<<16)|((int)(g/n*.28f)<<8)|(int)(b/n*.32f);}
        return out;
    }
    private static int nearest(int[] pal,float r,float g,float b){int best=0;float bd=1e30f;
        for(int i=0;i<pal.length;i++){int c=pal[i];float dr=((c>>16)&255)-r,dg=((c>>8)&255)-g,db=(c&255)-b;float d=dr*dr*.3f+dg*dg*.59f+db*db*.11f;if(d<bd){bd=d;best=i;}}
        return pal[best];}
    /** Median-cut palette of {@code n} colours from every covered pixel (glowing pixels keep their own colours). */
    private static int[] palette(float[][][] rgb,boolean[][][] cov,boolean[][][] glow,int n){
        ArrayList<int[]> all=new ArrayList<>();java.util.LinkedHashSet<Integer> glows=new java.util.LinkedHashSet<>();
        for(int a=0;a<rgb.length;a++)for(int v=0;v<VIEWS;v++){float[] c=rgb[a][v];if(c==null)continue;
            for(int k=0;k<cov[a][v].length;k++)if(cov[a][v][k]){int r=cl(c[k*3]),g=cl(c[k*3+1]),b=cl(c[k*3+2]);
                if(glow[a][v][k])glows.add(0xff000000|(r>>3<<19)|(g>>3<<11)|(b>>3<<3));else all.add(new int[]{r,g,b});}}
        ArrayList<ArrayList<int[]>> boxes=new ArrayList<>();boxes.add(all);
        while(boxes.size()<n){int bi=-1,bc=-1,range=-1;
            for(int i=0;i<boxes.size();i++){ArrayList<int[]> bx=boxes.get(i);if(bx.size()<2)continue;
                for(int ch=0;ch<3;ch++){int lo=255,hi=0;for(int[] p:bx){lo=Math.min(lo,p[ch]);hi=Math.max(hi,p[ch]);}if(hi-lo>range){range=hi-lo;bi=i;bc=ch;}}}
            if(bi<0||range<=2)break;final int ch=bc;ArrayList<int[]> bx=boxes.remove(bi);bx.sort((p,q)->p[ch]-q[ch]);
            int m=bx.size()/2;boxes.add(new ArrayList<>(bx.subList(0,m)));boxes.add(new ArrayList<>(bx.subList(m,bx.size())));}
        ArrayList<Integer> pal=new ArrayList<>();
        for(ArrayList<int[]> bx:boxes){if(bx.isEmpty())continue;long r=0,g=0,b=0;for(int[] p:bx){r+=p[0];g+=p[1];b+=p[2];}int k=bx.size();pal.add(0xff000000|((int)(r/k)<<16)|((int)(g/k)<<8)|(int)(b/k));}
        int i=0;for(int gc:glows){if(i++>=8)break;pal.add(gc);}
        if(pal.isEmpty())pal.add(0xff808080);
        int[] out=new int[pal.size()];for(int j=0;j<out.length;j++)out[j]=pal.get(j);return out;
    }
    private static int cl(float v){return Math.max(0,Math.min(255,Math.round(v)));}

    // ------------------------------------------------------------------ putting the puppet together
    private static final LinkedHashMap<String,Sprites.Sprite> sprites=new LinkedHashMap<String,Sprites.Sprite>(256,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Sprites.Sprite> e){return size()>360;}};
    /**
     * The character {@code id} in a pose blended from animation frames {@code fa} → {@code fb} by {@code t}, seen from
     * {@code yaw} (radians from its facing to the camera, + = camera on its right). Null while its rig is being made.
     */
    static Sprites.Sprite sprite(int id,int fa,int fb,float t,float yaw){
        if(!enabled)return null;Rig r=rig(id);if(r==null)return null;
        return composed(key(id),r,fa,fb,t,yaw);
    }
    static Sprites.Sprite composed(String rigKey,Rig r,int fa,int fb,float t,float yaw){
        int nf=r.joints.length;fa=Math.max(0,Math.min(nf-1,fa));fb=Math.max(0,Math.min(nf-1,fb));
        int tq=Math.round(Math.max(0,Math.min(1,t))*6);if(fa==fb||tq==0){fb=fa;tq=0;}if(tq==6){fa=fb;tq=0;}
        int y16=((int)Math.round(yaw/(Math.PI/8))%16+16)%16;
        String k=rigKey+"|"+fa+"|"+fb+"|"+tq+"|"+y16;
        synchronized(sprites){Sprites.Sprite s=sprites.get(k);if(s!=null)return s;}
        Sprites.Sprite s=compose(r,fa,fb,tq/6f,(float)(y16*Math.PI/8));
        synchronized(sprites){sprites.put(k,s);}
        return s;
    }
    static Sprites.Sprite compose(Rig r,int fa,int fb,float t,float yaw){
        float[][] JA=r.joints[fa],JB=r.joints[fb];float[][] J=new float[JOINTS][];
        for(int i=0;i<JOINTS;i++)J[i]=new float[]{JA[i][0]+(JB[i][0]-JA[i][0])*t,JA[i][1]+(JB[i][1]-JA[i][1])*t,JA[i][2]+(JB[i][2]-JA[i][2])*t};
        float cx=(float)Math.cos(yaw),cy=(float)Math.sin(yaw),ux=cy,uy=-cx;
        int view=((int)Math.round(yaw/(Math.PI/4))%8+8)%8;
        // parts back to front
        Integer[] order=new Integer[PARTS];float[] depth=new float[PARTS];
        for(int p=0;p<PARTS;p++){order[p]=p;float[] a=J[BONE[p][0]],b=J[BONE[p][1]];depth[p]=-(((a[0]+b[0])*.5f)*cx+((a[1]+b[1])*.5f)*cy)+BIAS[p];}
        depth[P_BACK]=-(J[J_BACK][0]*cx+J[J_BACK][1]*cy);   // a cape or quiver sorts by where it hangs: behind the body, or in front when seen from behind
        java.util.Arrays.sort(order,(p,q)->Float.compare(depth[q],depth[p]));
        // each part's placement (in units: x across the screen, z up), then the sprite's bounds from its tile's corners
        float[][] T=new float[PARTS][];float minX=0,maxX=0,maxZ=0;
        for(int pi=0;pi<PARTS;pi++){Art art=r.art[ART_OF[pi]];if(art==null||art.empty)continue;
            float[] a=J[BONE[pi][0]],b=J[BONE[pi][1]];
            float ax=a[0]*ux+a[1]*uy,az=a[2],bx=b[0]*ux+b[1]*uy,bz=b[2];
            float sdx=bx-ax,sdy=-(bz-az),sl=(float)Math.sqrt(sdx*sdx+sdy*sdy);   // screen y points down
            float k=1/art.ppu;   // units per picture pixel
            float il=art.len,idy=art.up?-1:1;
            float fs=STRETCH[pi]&&il>1?Math.max(.4f,Math.min(1.25f,sl/(il*k))):1;
            float ang=sl>1e-4f?(float)(Math.atan2(sdy,sdx)-Math.atan2(idy,0)):0;
            if(!STRETCH[pi]&&pi!=P_WEAPON)ang=clampAngle(ang,1.7f);
            boolean mir=MIRROR[pi];float jx=mir?art.w-art.jx:art.jx,jy=art.jy;
            float ca=(float)Math.cos(ang),sa=(float)Math.sin(ang);
            T[pi]=new float[]{ax,az,k,fs,ca,sa,jx,jy};
            for(int c=0;c<4;c++){float lx=((c&1)==0?0:art.w)-jx,ly=((c&2)==0?0:art.h)-jy;lx*=k;ly*=k*fs;
                float dx=lx*ca-ly*sa,dy=lx*sa+ly*ca;   // screen offset (y down)
                minX=Math.min(minX,ax+dx);maxX=Math.max(maxX,ax+dx);maxZ=Math.max(maxZ,az-dy);}}
        float half=Math.max(-minX,maxX);int W=Math.max(2,(int)Math.ceil(half*COMP_PPU)*2+2),H=Math.max(2,(int)Math.ceil(maxZ*COMP_PPU)+2);
        Sprites.Sprite out=new Sprites.Sprite(W,H,false);out.ppu=COMP_PPU;int[] dst=out.px;
        for(int pi:order){float[] tf=T[pi];if(tf==null)continue;Art art=r.art[ART_OF[pi]];
            boolean mir=MIRROR[pi];int v=mir?(8-view)%8:view;int[] src=art.px[v];if(src==null)continue;
            float ax=W*.5f+tf[0]*COMP_PPU,ay=H-1-tf[1]*COMP_PPU,k=tf[2]*COMP_PPU,fs=tf[3],ca=tf[4],sa=tf[5],jx=tf[6],jy=tf[7];
            float r0=Math.max(art.w,art.h)*k*Math.max(1,fs)*1.5f;int aw=art.w,ah=art.h;int x0=Math.max(0,(int)(ax-r0)),x1=Math.min(W-1,(int)(ax+r0)),y0=Math.max(0,(int)(ay-r0)),y1=Math.min(H-1,(int)(ay+r0));
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                float dx=x+.5f-ax,dy=y+.5f-ay;
                float lx=dx*ca+dy*sa,ly=-dx*sa+dy*ca;   // undo the rotation, then the scale (k across, k*fs along the bone)
                float ix=lx/k+jx,iy=ly/(k*fs)+jy;
                int px=(int)Math.floor(ix),py=(int)Math.floor(iy);if(px<0||py<0||px>=aw||py>=ah)continue;
                int c=src[py*aw+(mir?aw-1-px:px)];if((c>>>24)==0)continue;dst[y*W+x]=c;}
        }
        return out;
    }
    private static float clampAngle(float a,float lim){while(a>Math.PI)a-=2*Math.PI;while(a<-Math.PI)a+=2*Math.PI;return Math.max(-lim,Math.min(lim,a));}

    // ------------------------------------------------------------------ disk
    private static boolean loadArt(String key,Rig r){
        java.io.File d=Sprites.textureDir();if(d==null)return false;java.io.File f=new java.io.File(d,"puppet2_"+safe(key)+".bin");if(!f.isFile())return false;
        try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(new java.io.FileInputStream(f)),1<<15))){
            if(in.readInt()!=ARTS)return false;
            for(int a=0;a<ARTS;a++){Art art=new Art();art.w=in.readInt();art.h=in.readInt();art.empty=in.readBoolean();art.up=in.readBoolean();art.ppu=in.readFloat();art.jx=in.readFloat();art.jy=in.readFloat();art.len=in.readFloat();
                if(!art.empty)for(int v=0;v<VIEWS;v++){int[] px=new int[art.w*art.h];for(int i=0;i<px.length;i++)px[i]=in.readInt();art.px[v]=px;}r.art[a]=art;}
            return true;
        }catch(Throwable t){f.delete();return false;}
    }
    private static void saveArt(String key,Rig r){
        java.io.File d=Sprites.textureDir();if(d==null)return;java.io.File f=new java.io.File(d,"puppet2_"+safe(key)+".bin"),tmp=new java.io.File(d,"puppet2_"+safe(key)+".tmp");
        try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.util.zip.DeflaterOutputStream(new java.io.FileOutputStream(tmp)),1<<15))){
            out.writeInt(ARTS);
            for(Art art:r.art){out.writeInt(art.w);out.writeInt(art.h);out.writeBoolean(art.empty);out.writeBoolean(art.up);out.writeFloat(art.ppu);out.writeFloat(art.jx);out.writeFloat(art.jy);out.writeFloat(art.len);
                if(!art.empty)for(int v=0;v<VIEWS;v++){int[] px=art.px[v]==null?new int[art.w*art.h]:art.px[v];for(int c:px)out.writeInt(c);}}
        }catch(Throwable t){tmp.delete();return;}
        if(!tmp.renameTo(f))tmp.delete();
        if(key.startsWith("look_")){java.io.File[] all=d.listFiles();if(all!=null)for(java.io.File o:all)if((o.getName().startsWith("puppet2_look_")||o.getName().startsWith("puppet_"))&&!o.equals(f))o.delete();}
    }
    private static String safe(String k){StringBuilder b=new StringBuilder();for(char c:k.toCharArray())b.append(Character.isLetterOrDigit(c)?c:'_');return b+"_"+Integer.toHexString(k.hashCode());}

    // ------------------------------------------------------------------ vectors
    private static float[] sub(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] mul(float[] a,float s){return new float[]{a[0]*s,a[1]*s,a[2]*s};}
    private static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float len(float[] a){return (float)Math.sqrt(dot(a,a));}
    private static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}

    /** One sheet with every drawing (rows: parts, columns: the 8 angles), for checking and for the sprite editor. */
    static int[] sheet(Rig r){int C=MAX_ART,W=C*VIEWS,H=C*ARTS;int[] px=new int[W*H];
        for(int a=0;a<ARTS;a++){Art art=r.art[a];if(art==null||art.empty)continue;for(int v=0;v<VIEWS;v++){int[] s=art.px[v];if(s==null)continue;
            for(int y=0;y<art.h;y++)System.arraycopy(s,y*art.w,px,(a*C+y)*W+v*C,art.w);}}
        return px;}
}
