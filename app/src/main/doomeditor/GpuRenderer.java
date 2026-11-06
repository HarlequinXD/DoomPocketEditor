package com.retro.doomeditor;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * OpenGL ES 2.0 back end for {@link Renderer}. Draws the same scene the software renderer does
 * (sky, floors, ceilings, walls, sprites, blood, particles, first-person weapon) on the GPU into an
 * off-screen framebuffer, with Doom-style light diminishing, per-pixel coloured lights, fog and tint
 * done in the fragment shader. The finished frame is read back into a bitmap so the rest of the app
 * keeps drawing it with an ordinary Canvas. Textures use nearest filtering, so the pixel-art look stays.
 */
final class GpuRenderer {
    // ------------------------------------------------------------------ shared EGL context (one per app)
    private static EGLDisplay dpy=EGL14.EGL_NO_DISPLAY;private static EGLContext ctx=EGL14.EGL_NO_CONTEXT;private static EGLSurface surf=EGL14.EGL_NO_SURFACE;
    private static boolean failed,depth24;
    private static int progScene,progColor,progSky,progScreen,progId,progSplat;
    private static final int[] wallTex=new int[Textures.COUNT],reliefTex=new int[Textures.COUNT],matTex=new int[Textures.COUNT];private static int skyTex,flatRelief,flatMat;
    private static final float[][] avg=new float[Textures.COUNT][];private static float[] skyAvg={.5f,.6f,.75f};
    /** Physically based shading, and sky light + bounce lighting between sectors (settings). */
    static boolean pbr=true,gi=true;
    /** PS1 look: vertex wobble, warped (affine) textures, 15-bit dithered colour. 3D models for built-in entities. */
    static boolean ps1=true,models3d=true;
    /** Dynamic shadows from coloured lights (setting). */
    static boolean shadows=true;
    private static int shadowTex;private static final int SB=256;   // angle bins per light
    /** Normal + parallax mapping on textured surfaces (setting). */
    static boolean relief=true;
    /** False when the GPU path can't be used on this device (the software renderer takes over). */
    static synchronized boolean available(){
        if(failed)return false;
        if(ctx!=EGL14.EGL_NO_CONTEXT)return makeCurrent();
        try{
            dpy=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] ver=new int[2];
            if(dpy==EGL14.EGL_NO_DISPLAY||!EGL14.eglInitialize(dpy,ver,0,ver,1))throw new RuntimeException("egl init");
            int[] att={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
            EGLConfig[] cfg=new EGLConfig[1];int[] n=new int[1];
            if(!EGL14.eglChooseConfig(dpy,att,0,cfg,0,1,n,0)||n[0]<1)throw new RuntimeException("egl config");
            ctx=EGL14.eglCreateContext(dpy,cfg[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            if(ctx==EGL14.EGL_NO_CONTEXT)throw new RuntimeException("egl context");
            surf=EGL14.eglCreatePbufferSurface(dpy,cfg[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(surf==EGL14.EGL_NO_SURFACE||!makeCurrent())throw new RuntimeException("egl surface");
            String ext=GLES20.glGetString(GLES20.GL_EXTENSIONS);depth24=ext!=null&&ext.contains("GL_OES_depth24");
            progScene=program(VS_SCENE,FS_SCENE);progColor=program(VS_COLOR,FS_COLOR);progSky=program(VS_SCREEN,FS_SKY);progScreen=program(VS_SCREEN,FS_SCREEN);progId=program(VS_SCENE,FS_ID);progSplat=program(VS_SCENE,FS_SPLAT);
            Textures.init();
            for(int t=0;t<Textures.COUNT;t++){wallTex[t]=texture(Textures.base[t],Textures.SIZE,Textures.SIZE,true,true);reliefTex[t]=texture(Textures.relief(t),Textures.SIZE,Textures.SIZE,true,false);}
            flatRelief=texture(new int[]{0x808080ff|0x80000000},1,1,true,false);
            for(int t=0;t<Textures.COUNT;t++){matTex[t]=texture(Textures.material(t),Textures.SIZE,Textures.SIZE,true,true);avg[t]=Textures.average(t);}
            flatMat=texture(new int[]{0xff4000ff},1,1,true,true);
            {int[] t=new int[1];GLES20.glGenTextures(1,t,0);shadowTex=t[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,shadowTex);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,SB,16,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);}
            {int[] sk=Textures.sky;float sr=0,sg=0,sb=0;int cnt=0;for(int y=0;y<Textures.SKY_H*6/10;y++)for(int x=0;x<Textures.SKY_W;x+=2){int c=sk[y*Textures.SKY_W+x];sr+=(c>>16)&255;sg+=(c>>8)&255;sb+=c&255;cnt++;}
                if(cnt>0)skyAvg=new float[]{sr/cnt/255f,sg/cnt/255f,sb/cnt/255f};}
            skyTex=texture(Textures.sky,Textures.SKY_W,Textures.SKY_H,true,true);
            return true;
        }catch(Throwable e){failed=true;android.util.Log.w("GpuRenderer","GPU renderer unavailable, using software",e);return false;}
    }
    private static boolean makeCurrent(){return EGL14.eglMakeCurrent(dpy,surf,surf,ctx);}
    private static int program(String vs,String fs){
        int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);
        GLES20.glBindAttribLocation(p,0,"aPos");GLES20.glBindAttribLocation(p,1,"aUv");GLES20.glBindAttribLocation(p,2,"aLight");GLES20.glBindAttribLocation(p,3,"aId");GLES20.glBindAttribLocation(p,4,"aTan");GLES20.glBindAttribLocation(p,5,"aKind");GLES20.glBindAttribLocation(p,6,"aGi");GLES20.glBindAttribLocation(p,7,"aEdge");
        GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new RuntimeException("link: "+GLES20.glGetProgramInfoLog(p));return p;
    }
    private static int shader(int type,String src){
        int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new RuntimeException("shader: "+GLES20.glGetShaderInfoLog(s));return s;
    }
    /** Uploads ARGB pixels as an RGBA texture (nearest filtering; repeat or clamp). */
    private static int texture(int[] argb,int w,int h,boolean repeat){return texture(argb,w,h,repeat,false);}
    private static int texture(int[] argb,int w,int h,boolean repeat,boolean opaque){
        int[] t=new int[1];GLES20.glGenTextures(1,t,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,t[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        boolean pot=(w&(w-1))==0&&(h&(h-1))==0;int wrap=repeat&&pot?GLES20.GL_REPEAT:GLES20.GL_CLAMP_TO_EDGE;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,wrap);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,wrap);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,rgba(argb,w*h,opaque));
        return t[0];
    }
    /** Reused upload buffer: blood splats and sprites create textures often, and fresh native buffers each time pile up. */
    private static ByteBuffer scratch;
    private static ByteBuffer rgba(int[] argb,int n,boolean opaque){
        if(scratch==null||scratch.capacity()<n*4)scratch=ByteBuffer.allocateDirect(Math.max(n*4,64*64*4)).order(ByteOrder.nativeOrder());
        ByteBuffer b=scratch;b.clear();
        for(int i=0;i<n;i++){int c=argb[i];b.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put(opaque?(byte)255:(byte)(c>>>24));}
        b.position(0);return b;
    }

    // ------------------------------------------------------------------ shaders
    // scene vertex: world position, texture coords, (sector light, bright flag), pick id bytes
    private static final String VS_SCENE=
        "uniform vec3 uCam;uniform vec2 uCS;uniform vec3 uProj;uniform vec2 uSnap;\n"+
        "attribute vec3 aPos;attribute vec2 aUv;attribute vec2 aLight;attribute vec4 aId;attribute vec3 aTan;attribute float aKind;attribute vec3 aGi;attribute vec3 aEdge;\n"+
        "varying vec3 vUvA;varying vec3 vEdge;varying vec2 vUv;varying vec2 vLight;varying vec3 vWorld;varying float vZ;varying vec4 vId;varying vec3 vTan;varying float vKind;varying vec3 vGi;\n"+
        "void main(){vec2 d=aPos.xy-uCam.xy;float x=-d.x*uCS.y+d.y*uCS.x;float z=d.x*uCS.x+d.y*uCS.y;float y=aPos.z-uCam.z;\n"+
        " vUv=aUv;vLight=aLight;vWorld=aPos;vZ=z;vId=aId;vTan=aTan;vKind=aKind;vGi=aGi;vEdge=aEdge;\n"+
        " vec4 cp=vec4(x,uProj.y*z-y*uProj.x,(z*uProj.z-2.0*0.06*120.0/(120.0-0.06)),z);\n"+
        // PS1: vertices snap to a coarse screen grid (the wobble), and texture coordinates are kept for affine mapping
        " if(uSnap.x>0.0&&cp.w>0.01){cp.xy=floor(cp.xy/cp.w*uSnap+0.5)/uSnap*cp.w;}\n"+
        " vUvA=vec3(aUv*cp.w,cp.w);gl_Position=cp;}\n";
    private static final String FS_HEAD=
        "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";
    // vKind: 0 floor/ceiling, 1 wall, 2 sprite/decal (no relief). Tangent frame: walls T = along the wall,
    // B = down (texture v grows downward), N = facing the camera; floors T = +x, B = +y, N = up or down.
    // vKind: 0 floor/ceiling, 1 wall, 2 sprite/decal, 3 3D model (vTan = its normal). Shading is PS1-style
    // (not physically based): Lambert diffuse plus a Blinn-Phong highlight from the smoothness map, then 15-bit colour
    // with ordered dithering. Normal maps, parallax, shadows, sky light and fog all still apply.
    private static final String FS_SCENE=FS_HEAD+
        "uniform sampler2D uTex;uniform sampler2D uNorm;uniform sampler2D uMat;uniform float uRelief;uniform float uPom;uniform vec3 uCamPos;\n"+
        "uniform float uFade;uniform int uCount;uniform vec4 uL[8];uniform vec3 uLC[8];uniform int uShadowN;\n"+
        "uniform vec4 uFog;uniform vec3 uFogC;uniform vec4 uTint;uniform vec4 uHi;uniform float uAlphaCut;uniform float uAffine;uniform float uDither;\n"+
        "uniform sampler2D uShadow;uniform float uShadowOn;\n"+
        "varying vec3 vUvA;varying vec2 vUv;varying vec2 vLight;varying vec3 vWorld;varying float vZ;varying vec4 vId;varying vec3 vTan;varying float vKind;varying vec3 vGi;varying vec3 vEdge;\n"+
        "float pomShadow(vec2 uv,vec3 Lt,float amt,float h0){if(Lt.z<=0.02)return 0.35;vec2 dir=Lt.xy/max(Lt.z,0.2)*amt*(1.0-h0);float occ=0.0;\n"+
        " for(int j=1;j<=4;j++){float s=float(j)*0.25;float hh=texture2D(uNorm,uv+dir*s).a;occ=max(occ,(hh-(h0+(1.0-h0)*s))*(1.0-s*0.5));}\n"+
        " return 1.0-clamp(occ*5.0,0.0,0.75);}\n"+
        "float shadowAt(float row,vec3 lp,float actors){vec2 d=vWorld.xy-lp.xy;float dist=length(d);if(dist<0.05)return 1.0;\n"+
        " float u=atan(d.y,d.x)/6.2831853+0.5;float v=(row+0.5)/16.0;float s=0.0;\n"+
        " for(int k=-1;k<=1;k++){vec4 t=texture2D(uShadow,vec2(u+float(k)/256.0,v));float wd=(t.r*65280.0+t.g*255.0)/65535.0*32.0;\n"+
        "  float lit=dist<wd+0.08+dist*0.05?1.0:0.0;\n"+
        "  float ad=t.b*24.0;if(actors>0.5&&lit>0.0&&t.b<0.995&&dist>ad+0.15){float hz=lp.z+(vWorld.z-lp.z)*ad/dist;if(hz<t.a*16.0)lit=0.0;}\n"+
        "  s+=lit;}\n"+
        " return s/3.0;}\n"+
        "float bayer2(vec2 a){a=floor(a);return fract(dot(a,vec2(0.5,a.y*0.75)));}\n"+
        "float bayer4(vec2 a){return bayer2(0.5*a)*0.25+bayer2(a);}\n"+
        "void main(){vec2 uv=mix(vUv,vUvA.xy/vUvA.z,uAffine);vec3 V=uCamPos-vWorld;vec3 Vw=normalize(V);vec3 Nw;float shadeK=1.0;\n"+
        " bool model=vKind>2.5;bool surf=vKind<1.5||model;\n"+
        " vec3 T=vec3(1.0,0.0,0.0);vec3 B=vec3(0.0,1.0,0.0);vec3 N=vec3(0.0,0.0,1.0);\n"+
        " if(model){N=normalize(vTan);if(dot(N,V)<0.0)N=-N;T=normalize(cross(abs(N.z)<0.9?vec3(0.0,0.0,1.0):vec3(1.0,0.0,0.0),N));B=cross(N,T);}\n"+
        " else if(surf){if(vKind>0.5){T=normalize(vec3(vTan.xy,0.0));N=vec3(T.y,-T.x,0.0);if(dot(N,V)<0.0)N=-N;B=vec3(0.0,0.0,-1.0);}\n"+
        "  else{N=vec3(0.0,0.0,V.z>0.0?1.0:-1.0);}}\n"+
        " else{N=normalize(vec3(V.xy,0.0001));}\n"+
        " Nw=N;float amt=0.0;float h0=1.0;\n"+
        " if(surf&&uRelief>0.0){vec3 Vt=normalize(vec3(dot(V,T),dot(V,B),dot(V,N)));\n"+
        "  float lod=clamp(1.0-vZ/12.0,0.0,1.0);amt=uRelief*0.07*lod*uPom;\n"+
        "  if(amt>0.002){float steps=floor(mix(10.0,3.0,Vt.z)*(0.4+0.6*lod)+1.0);vec2 delta=Vt.xy/max(Vt.z,0.22)*amt/steps;float ls=1.0/steps;\n"+
        "   float layer=0.0;vec2 cur=uv;float depth=1.0-texture2D(uNorm,cur).a;vec2 pUv=cur;float pD=depth;float pL=0.0;\n"+
        "   for(int i=0;i<10;i++){if(float(i)>=steps||layer>=depth)break;pUv=cur;pD=depth;pL=layer;cur-=delta;layer+=ls;depth=1.0-texture2D(uNorm,cur).a;}\n"+
        "   float a1=depth-layer;float b1=pD-pL;uv=mix(cur,pUv,clamp(a1/(a1-b1+1e-5),0.0,1.0));\n"+
        "   if(vKind>0.5&&vKind<1.5){if(vEdge.z>0.5&&vEdge.z<1.5&&uv.y<vEdge.x)discard;if(vEdge.z>1.5&&uv.y>vEdge.y)discard;}}\n"+
        "  h0=texture2D(uNorm,uv).a;\n"+
        "  vec3 nm=texture2D(uNorm,uv).rgb*2.0-1.0;Nw=normalize(T*nm.x+B*nm.y+N*nm.z);\n"+
        "  shadeK=(0.8+0.32*dot(nm,vec3(-0.36,-0.46,0.81)))/1.06;}\n"+
        " vec4 t=texture2D(uTex,uv);if(t.a<uAlphaCut)discard;vec3 alb=t.rgb;\n"+
        " vec3 m=surf?texture2D(uMat,uv).rgb:vec3(0.1,0.0,1.0);float smo=m.r;float metal=m.g;float ao=m.b;\n"+
        " float L=vLight.x;float li;if(vLight.y>0.5)li=31.0;else{li=floor(L+3.0-vZ*uFade);li=clamp(li,max(1.0,floor(L/4.0)),31.0);}\n"+
        " float k=pow(li/31.0,1.25)*1.06;\n"+
        " float hemi=0.55+0.45*Nw.z;vec3 amb=(vec3(k*shadeK)+vGi*hemi)*ao;\n"+
        " if(model)amb*=0.75+0.25*max(0.0,dot(Nw,normalize(vec3(-0.4,-0.3,0.85))))+0.1;\n"+
        " vec3 c=alb*amb;vec3 specC=mix(vec3(1.0),alb*1.4,metal);float shin=6.0+smo*58.0;float sAmt=0.12+smo*0.9;int budget=2;\n"+
        " for(int i=0;i<8;i++){if(i>=uCount)break;vec3 dd=uL[i].xyz-vWorld;float d2=dot(dd,dd);if(d2<uL[i].w){float f=1.0-d2/uL[i].w;\n"+
        "  float sh=uShadowOn>0.5&&i<uShadowN?shadowAt(float(i),uL[i].xyz,(surf&&!model)?1.0:0.0):1.0;if(sh<=0.0)continue;\n"+
        "  vec3 Ld=dd*inversesqrt(max(d2,1e-4));\n"+
        "  if(surf&&amt>0.002&&budget>0&&f*f*sh>0.08){budget--;sh*=pomShadow(uv,vec3(dot(Ld,T),dot(Ld,B),dot(Ld,N)),amt,h0);}\n"+
        "  vec3 rad=uLC[i]*(f*f*1.35*sh);\n"+
        "  if(!surf){c+=alb*rad;continue;}\n"+
        "  float NdL=max(dot(Nw,Ld),0.0);vec3 H=normalize(Ld+Vw);float sp=pow(max(dot(Nw,H),0.0),shin)*sAmt;\n"+
        "  c+=(alb*(1.0-metal*0.6)*(0.15+0.85*NdL)+specC*sp*NdL)*rad;}}\n"+
        " c=min(c,vec3(1.7));\n"+
        " if(uFog.z>0.0&&vZ>uFog.x){float f=min(1.0,(vZ-uFog.x)*uFog.y)*uFog.z;c=mix(c,uFogC,f);}\n"+
        " c*=uTint.rgb;\n"+
        " if(uHi.a>=0.0&&all(lessThan(abs(vId-uHi),vec4(0.002))))c=c*0.625+vec3(30.0,96.0,60.0)/255.0;\n"+
        // PS1 colour: 15-bit (31 levels per channel) with a 4x4 ordered dither
        " c=clamp(c,0.0,1.0);if(uDither>0.5)c=floor(c*31.0+bayer4(gl_FragCoord.xy))/31.0;\n"+
        " gl_FragColor=vec4(c,t.a);}\n";
    /**
     * Blood splats drawn by a shader instead of a texture each: the shape (a wobbly blob, a few droplets around it,
     * a darker rim and a wet highlight) is worked out from the splat's seed, in 8 x 8 steps per radius so it stays
     * pixel-art. No textures are created or freed as blood appears, and all splats are one draw call.
     */
    private static final String FS_SPLAT=FS_HEAD+
        "uniform float uFade;uniform vec4 uFog;uniform vec3 uFogC;uniform vec4 uTint;\n"+
        "varying vec2 vUv;varying vec2 vLight;varying vec3 vWorld;varying float vZ;varying vec4 vId;varying vec3 vTan;varying float vKind;varying vec3 vGi;varying vec3 vEdge;\n"+
        "float hs(float n){return fract(sin(n)*43758.5453);}\n"+
        "void main(){vec2 p=floor((vUv*2.0-1.0)*1.9*8.0)/8.0+0.0625;float sd=vTan.x*977.0;\n"+
        " float an=atan(p.y,p.x);float d=length(p);\n"+
        " float rr=0.72+0.2*sin(an*3.0+hs(sd)*6.283)+0.12*sin(an*5.0+hs(sd+1.0)*6.283)+0.07*sin(an*9.0+hs(sd+2.0)*6.283);\n"+
        " float inside=d<rr?1.0:0.0;float core=d/rr;\n"+
        " for(int i=0;i<5;i++){float fi=float(i);float a=hs(sd+10.0+fi)*6.283;vec2 c=vec2(cos(a),sin(a))*(1.05+hs(sd+20.0+fi)*0.7);float r=0.07+0.13*hs(sd+30.0+fi);\n"+
        "  if(length(p-c)<r){inside=1.0;core=0.6;}}\n"+
        // wall splats run: a drip straight down from the blob
        " if(vTan.y>0.5){float dx=abs(p.x-(hs(sd+40.0)-0.5)*0.8);float len=0.9+hs(sd+41.0)*0.9;if(dx<0.09&&p.y<0.0&&p.y>-(rr+len)){inside=1.0;core=0.7;}}\n"+
        " if(inside<0.5)discard;\n"+
        // multiply: the stain darkens and reddens whatever is under it, so it is lit, relief-mapped and shadowed
        // exactly like that surface for free (rim darker, a lighter wet spot)
        " vec3 tint=clamp(vId.rgb*1.6,0.0,1.0);vec3 m=tint*(1.05-0.35*core);if(length(p-vec2(-0.28,-0.26))<0.16)m=mix(m,vec3(1.0),0.35);\n"+
        " if(uFog.z>0.0&&vZ>uFog.x){float f=min(1.0,(vZ-uFog.x)*uFog.y)*uFog.z;m=mix(m,vec3(1.0),f);}\n"+
        " gl_FragColor=vec4(m,1.0);}\n";
    private static final String FS_ID=FS_HEAD+
        "uniform sampler2D uTex;varying vec2 vUv;varying vec2 vLight;varying vec3 vWorld;varying float vZ;varying vec4 vId;\n"+
        "void main(){if(texture2D(uTex,vUv).a<0.5)discard;gl_FragColor=vId;}\n";
    // coloured points (drops, particles): world position, rgba in aUv+aLight
    private static final String VS_COLOR=
        "uniform vec3 uCam;uniform vec2 uCS;uniform vec3 uProj;\n"+
        "attribute vec3 aPos;attribute vec4 aId;varying vec4 vC;varying float vZ;\n"+
        "void main(){vec2 d=aPos.xy-uCam.xy;float x=-d.x*uCS.y+d.y*uCS.x;float z=d.x*uCS.x+d.y*uCS.y;float y=aPos.z-uCam.z;vC=aId;vZ=z;\n"+
        " gl_Position=vec4(x,uProj.y*z-y*uProj.x,(z*uProj.z-2.0*0.06*120.0/(120.0-0.06)),z);}\n";
    private static final String FS_COLOR=FS_HEAD+
        "uniform vec4 uFog;uniform vec3 uFogC;uniform vec4 uTint;varying vec4 vC;varying float vZ;\n"+
        "void main(){vec3 c=vC.rgb;if(uFog.z>0.0&&vZ>uFog.x){float f=min(1.0,(vZ-uFog.x)*uFog.y)*uFog.z;c=mix(c,uFogC,f);}gl_FragColor=vec4(c*uTint.rgb,vC.a);}\n";
    // screen quads: aPos.xy = pixel position (top-left origin), aUv = texture coords
    private static final String VS_SCREEN=
        "uniform vec2 uSize;attribute vec3 aPos;attribute vec2 aUv;varying vec2 vUv;varying vec2 vPix;\n"+
        "void main(){vUv=aUv;vPix=aPos.xy;gl_Position=vec4(aPos.x/uSize.x*2.0-1.0,aPos.y/uSize.y*2.0-1.0,0.0,1.0);}\n";
    private static final String FS_SKY=FS_HEAD+
        "uniform sampler2D uTex;uniform vec4 uSky;uniform vec4 uFog;uniform vec3 uFogC;uniform vec4 uTint;uniform float uDither;varying vec2 vUv;varying vec2 vPix;\n"+
        // uSky: angle, focal, horizon, W
        "void main(){float a=uSky.x+atan((vPix.x-uSky.w*0.5)/uSky.y);float u=a/6.2831853*2.0;\n"+
        " float v=(128.0*0.78+(vPix.y-uSky.z)*(128.0/(uFog.w*1.15)))/128.0;v=clamp(v,0.5/128.0,1.0-0.5/128.0);\n"+
        " vec3 c=texture2D(uTex,vec2(u,v)).rgb;if(uFog.z>0.0)c=mix(c,uFogC,uFog.z*0.6);c*=uTint.rgb;\n"+
        " if(uDither>0.5){vec2 a=floor(gl_FragCoord.xy);float b=fract(dot(floor(a*0.5),vec2(0.5,floor(a.y*0.5)*0.75)))*0.25+fract(dot(a,vec2(0.5,a.y*0.75)));c=floor(clamp(c,0.0,1.0)*31.0+b)/31.0;}\n"+
        " gl_FragColor=vec4(c,1.0);}\n";
    private static final String FS_SCREEN=FS_HEAD+
        "uniform sampler2D uTex;uniform vec4 uMul;uniform float uUseTex;varying vec2 vUv;varying vec2 vPix;\n"+
        "void main(){vec4 t=uUseTex>0.5?texture2D(uTex,vUv):vec4(1.0);if(t.a<0.02)discard;gl_FragColor=vec4(min(t.rgb*uMul.rgb,vec3(1.0)),t.a*uMul.a);}\n";

    // ------------------------------------------------------------------ per-renderer state
    private final Renderer r;
    private int W,H,fbo,depthRb,idFbo,idColor,idDepth;
    private Bitmap bmp;private ByteBuffer read;
    private final IdentityHashMap<Sprites.Sprite,int[]> spriteTex=new IdentityHashMap<>();   // {texture, last used frame}
    private final IdentityHashMap<World.Splat,int[]> splatTex=new IdentityHashMap<>();
    private int frame;
    // last frame's batches, kept for picking
    private FloatBuffer vb=FloatBuffer.allocate(0);private final ArrayList<int[]> batches=new ArrayList<>();   // {texture, first vertex, count, kind(0 opaque,1 sprite,2 splat)}
    private float cx,cy,eye,cs,sn,focal,horizon,angle;

    GpuRenderer(Renderer r){this.r=r;}
    int width(){return W;}int height(){return H;}

    void setup(int w,int h){
        if(w==W&&h==H&&fbo!=0)return;
        // smaller sizes (dynamic resolution) reuse the same framebuffer: only the viewport and read-back shrink
        if(fbo!=0&&w<=capW&&h<=capH){W=w;H=h;bmp=bitmapFor(W,H);return;}
        release();W=w;H=h;capW=w;capH=h;
        int[] t=new int[3];
        GLES20.glGenFramebuffers(2,t,0);fbo=t[0];idFbo=t[1];
        GLES20.glGenRenderbuffers(2,t,0);depthRb=t[0];idDepth=t[1];
        int dfmt=depth24?0x81A6/*GL_DEPTH_COMPONENT24_OES*/:GLES20.GL_DEPTH_COMPONENT16;
        // colour goes into RGBA8 textures (renderbuffer RGBA8 needs an extension)
        int[] ct=new int[2];GLES20.glGenTextures(2,ct,0);colorTex=ct[0];idColor=ct[1];
        for(int c:ct){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,c);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,W,H,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);}
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,depthRb);GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,dfmt,W,H);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,idDepth);GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,dfmt,W,H);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,colorTex,0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,GLES20.GL_DEPTH_ATTACHMENT,GLES20.GL_RENDERBUFFER,depthRb);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,idFbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,idColor,0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,GLES20.GL_DEPTH_ATTACHMENT,GLES20.GL_RENDERBUFFER,idDepth);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);
        if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new RuntimeException("framebuffer incomplete");
        bitmaps.clear();bmp=bitmapFor(W,H);read=ByteBuffer.allocateDirect(W*H*4).order(ByteOrder.nativeOrder());
    }
    private int capW,capH;private final HashMap<Long,Bitmap> bitmaps=new HashMap<>();
    private Bitmap bitmapFor(int w,int h){long k=((long)w<<32)|h;Bitmap b=bitmaps.get(k);if(b==null){b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bitmaps.put(k,b);}return b;}
    /** Frees this view's GPU memory (framebuffers and cached sprite/blood textures). */
    void dispose(){
        try{if(!available())return;release();
            for(int[] e:spriteTex.values())GLES20.glDeleteTextures(1,e,0);spriteTex.clear();
            for(int[] e:splatTex.values())GLES20.glDeleteTextures(1,e,0);splatTex.clear();
            for(Bitmap b:bitmaps.values())b.recycle();bitmaps.clear();bmp=null;W=H=0;
            if(vbos!=null){GLES20.glDeleteBuffers(3,vbos,0);vbos=null;}
        }catch(Throwable ignored){}
    }
    private int colorTex;
    private void release(){
        if(fbo!=0){capW=capH=0;GLES20.glDeleteFramebuffers(2,new int[]{fbo,idFbo},0);GLES20.glDeleteRenderbuffers(2,new int[]{depthRb,idDepth},0);GLES20.glDeleteTextures(2,new int[]{colorTex,idColor},0);fbo=0;}
    }

    // ------------------------------------------------------------------ geometry building
    static final int ST=24;   // floats per vertex: pos3 uv2 light2 id4 tangent3 kind1 pad1 gi3 pad1 edge3 pad1
    private float giR,giG,giB;   // sky/bounce light for the next vtx() call (sprites, decals)
    private float[] verts=new float[ST*4096];private int nv;
    private void ensure(int more){if((nv+more)*ST>verts.length)verts=java.util.Arrays.copyOf(verts,Math.max(verts.length*2,(nv+more)*ST));}
    private void vtx(float x,float y,float z,float u,float v,float L,float bright,int id){
        int o=nv*ST;verts[o+11]=verts[o+12]=verts[o+13]=0;verts[o+14]=2;verts[o+15]=0;verts[o+16]=giR;verts[o+17]=giG;verts[o+18]=giB;verts[o+19]=0;verts[o+20]=verts[o+21]=verts[o+22]=verts[o+23]=0;verts[o]=x;verts[o+1]=y;verts[o+2]=z;verts[o+3]=u;verts[o+4]=v;verts[o+5]=L;verts[o+6]=bright;
        if(id<0){verts[o+7]=verts[o+8]=verts[o+9]=verts[o+10]=0;}
        else{verts[o+7]=((id>>>24)&255)/255f;verts[o+8]=((id>>16)&255)/255f;verts[o+9]=((id>>8)&255)/255f;verts[o+10]=(id&255)/255f;}
        nv++;
    }
    private static float[] idVec(int id){return id<0?new float[]{0,0,0,0}:new float[]{((id>>>24)&255)/255f,((id>>16)&255)/255f,((id>>8)&255)/255f,(id&255)/255f};}

    void renderScene(Level level,float x,float y,float eyeZ,float ang,float pitch,List<Renderer.SpriteDraw> sprites){
        frame++;
        cx=x;cy=y;eye=eyeZ;angle=ang;cs=(float)Math.cos(ang);sn=(float)Math.sin(ang);
        focal=W*.5f;horizon=H*.5f+(float)Math.tan(Math.max(-.6f,Math.min(.6f,pitch)))*focal;
        nv=0;batches.clear();
        // ---- surfaces, grouped by texture
        int nm=Textures.COUNT;
        int vs=level.vertices.size();
        computeGi(level);pickLights();
        if(shadows&&nl>0)buildShadows(level,sprites);
        // pass 1: floors, ceilings, walls into per-material buckets (stored as start indexes after sorting)
        java.util.Arrays.fill(mn,0);Object lists=null;
        for(int si=0;si<level.sectors.size()&&si<r.tris.length;si++){
            Level.Sector s=level.sectors.get(si);int[] t=r.tris[si];int L=Renderer.lightLevel(s.light);
            for(int k=0;k+2<t.length;k+=3){
                if(t[k]>=vs||t[k+1]>=vs||t[k+2]>=vs)continue;
                float[] sub=tile(level,si,k/3,t[k],t[k+1],t[k+2]);
                if(s.floor<eyeZ)plane(sub,t[k],t[k+1],t[k+2],s.floor,s.floorMaterial,L,Renderer.id(Renderer.ID_FLOOR,si));
                if(s.ceiling>eyeZ)plane(sub,t[k],t[k+1],t[k+2],s.ceiling,s.ceilingMaterial,L,Renderer.id(Renderer.ID_CEIL,si));
            }
        }
        for(int li=0;li<r.lines.size();li++){
            Level.LineDef l=r.lines.get(li);
            if(l.from>=vs||l.to>=vs||l.front<0||l.front>=level.sectors.size())continue;
            Level.Sector f=level.sectors.get(l.front);Level.Vertex a=level.vertices.get(l.from),b=level.vertices.get(l.to);
            float len=(float)Math.hypot(b.x-a.x,b.y-a.y);int L=Renderer.lightLevel(f.light)+(Math.abs(b.x-a.x)>Math.abs(b.y-a.y)?-1:1);
            if(l.back<0||l.back>=level.sectors.size())wall(lists,a,b,len,f.floor,f.ceiling,r.materialFor(level,l,0,f.wallMaterial),L,Renderer.id(Renderer.ID_WALL,li<<1),0,l.from,l.to);
            else{Level.Sector k=level.sectors.get(l.back);
                boolean lift=f.special==Level.SP_LIFT||k.special==Level.SP_LIFT,door=f.special==Level.SP_DOOR||k.special==Level.SP_DOOR;
                if(f.floor!=k.floor){float lo=Math.min(f.floor,k.floor),hi=Math.max(f.floor,k.floor);wall(lists,a,b,len,lo,hi,r.materialFor(level,l,0,f.wallMaterial),L,Renderer.id(Renderer.ID_WALL,li<<1),lift?hi:0,l.from,l.to,1);}
                boolean bothSky=f.ceilingMaterial==Textures.SKY&&k.ceilingMaterial==Textures.SKY;
                if(f.ceiling!=k.ceiling&&!bothSky){float lo=Math.min(f.ceiling,k.ceiling),hi=Math.max(f.ceiling,k.ceiling);wall(lists,a,b,len,lo,hi,r.materialFor(level,l,1,f.wallMaterial),L,Renderer.id(Renderer.ID_WALL,(li<<1)|1),door?lo:0,l.from,l.to,2);}
            }
        }
        // sky ceilings first: they only fill depth, so whatever is behind them must not be drawn yet
        int[] order=new int[nm];order[0]=Textures.SKY;for(int m=0,k=1;m<nm;m++)if(m!=Textures.SKY)order[k++]=m;
        for(int m:order){int cnt=mn[m];if(cnt==0)continue;int first=nv;ensure(cnt);
            System.arraycopy(mb[m],0,verts,nv*ST,cnt*ST);nv+=cnt;
            batches.add(new int[]{m==Textures.SKY?-1:wallTex[Math.max(0,Math.min(nm-1,m))],first,nv-first,m==Textures.SKY?3:0,m});}
        // ---- splats (decals lying on surfaces)
        World fx=r.fx;
        if(fx!=null&&!fx.splats.isEmpty()){int first=nv;for(World.Splat s:fx.splats)splat(level,s);if(nv>first)batches.add(new int[]{0,first,nv-first,4});}
        // ---- sprites (camera-facing quads)
        if(sprites!=null)for(Renderer.SpriteDraw d:sprites)sprite(level,d);
        // ---- draw
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glViewport(0,0,W,H);
        GLES20.glDisable(GLES20.GL_CULL_FACE);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDepthMask(true);
        boolean sky=false;for(Level.Sector s:level.sectors)if(s.ceilingMaterial==Textures.SKY){sky=true;break;}
        GLES20.glColorMask(true,true,true,true);GLES20.glClearColor(12/255f,12/255f,16/255f,1);GLES20.glClearDepthf(1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glColorMask(true,true,true,false);   // alpha stays 1 from the clear, so the read-back bitmap is opaque
        if(sky)drawSky();
        vb=upload();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glUseProgram(progScene);sceneUniforms(progScene);int uRel=GLES20.glGetUniformLocation(progScene,"uRelief"),uPom=GLES20.glGetUniformLocation(progScene,"uPom");
        bindScene(progScene,vb);
        for(int[] b:batches){
            if(b[3]==4){GLES20.glUseProgram(progSplat);camUniforms(progSplat);atmosUniforms(progSplat);
                GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_DST_COLOR,GLES20.GL_ZERO);   // multiply
                GLES20.glDepthMask(false);GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glPolygonOffset(-1,-4);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,b[1],b[2]);
                GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glUseProgram(progScene);continue;}
            if(b[3]==3){GLES20.glColorMask(false,false,false,false);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,wallTex[0]);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,b[1],b[2]);GLES20.glColorMask(true,true,true,false);continue;}
            if(b[3]==2){GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);
                GLES20.glDepthMask(false);GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glPolygonOffset(-1,-4);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScene,"uAlphaCut"),.5f);}
            else if(b[3]==1){GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);
                GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScene,"uAlphaCut"),.5f);}
            else{GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScene,"uAlphaCut"),-1f);}
            if(b[3]==5){   // 3D model: its own colour, relief (normal map) and material atlases; normal-mapped, no parallax
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[4]);GLES20.glUniform1f(uRel,relief?.8f:0f);GLES20.glUniform1f(uPom,0f);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[5]);
            }else{
            GLES20.glUniform1f(uPom,1f);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[3]==0&&b.length>4?reliefTex[b[4]]:flatRelief);
            GLES20.glUniform1f(uRel,b[3]==0&&b.length>4&&relief?Textures.RELIEF[b[4]]:0f);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[3]==0&&b.length>4?matTex[b[4]]:flatMat);}
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[0]);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,b[1],b[2]);
        }
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glDepthMask(true);
        // ---- drops and particles: coloured quads
        drawColored(level);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        trimCaches();
    }
    // per-texture vertex buffers, reused every frame (building the scene makes no garbage)
    private final float[][] mb=new float[Textures.COUNT][];private final int[] mn=new int[Textures.COUNT];
    private int slot(int m){float[] b=mb[m];int need=(mn[m]+1)*ST;if(b==null||b.length<need)mb[m]=b=java.util.Arrays.copyOf(b==null?new float[0]:b,Math.max(need*2,ST*96));return (mn[m]++)*ST;}
    private void put(int m,float x,float y,float z,float u,float v,float L,float B,int id,float tx,float ty,float kind,float gr,float gg,float gb,float e0,float e1,float e2){
        int o=slot(m);float[] b=mb[m];b[o]=x;b[o+1]=y;b[o+2]=z;b[o+3]=u;b[o+4]=v;b[o+5]=L;b[o+6]=B;
        if(id<0){b[o+7]=b[o+8]=b[o+9]=b[o+10]=0;}else{b[o+7]=((id>>>24)&255)/255f;b[o+8]=((id>>16)&255)/255f;b[o+9]=((id>>8)&255)/255f;b[o+10]=(id&255)/255f;}
        b[o+11]=tx;b[o+12]=ty;b[o+13]=0;b[o+14]=kind;b[o+15]=0;b[o+16]=gr;b[o+17]=gg;b[o+18]=gb;b[o+19]=0;b[o+20]=e0;b[o+21]=e1;b[o+22]=e2;b[o+23]=0;
    }
    // ------------------------------------------------------------------ meta tiles
    /**
     * Like PS1 games, big surfaces are cut into 2 x 2 unit tiles before drawing: floors and ceilings along the world
     * grid, walls every 2 units along and up. Small triangles keep the wobble and warped-texture look without the
     * huge swimming textures one large polygon would get. The floor cut is worked out once per map shape.
     */
    static final float META=2f;
    private int[][] tessFor;private float[][][] tess;
    /** Sub-triangles of sector si's triangle n: per vertex x, y and the 3 barycentric weights of the original corners. */
    private float[] tile(Level level,int si,int n,int a,int b,int c){
        if(tessFor!=r.tris){tessFor=r.tris;tess=new float[r.tris.length][][];}
        if(tess[si]==null)tess[si]=new float[r.tris[si].length/3][];
        float[] got=tess[si][n];if(got!=null)return got;
        Level.Vertex A=level.vertices.get(a),B=level.vertices.get(b),C=level.vertices.get(c);
        float minX=Math.min(A.x,Math.min(B.x,C.x)),maxX=Math.max(A.x,Math.max(B.x,C.x)),minY=Math.min(A.y,Math.min(B.y,C.y)),maxY=Math.max(A.y,Math.max(B.y,C.y));
        float[] out=new float[64];int on=0;
        float[] poly0={A.x,A.y,1,0,0, B.x,B.y,0,1,0, C.x,C.y,0,0,1};
        for(float gx=(float)Math.floor(minX/META)*META;gx<maxX;gx+=META)for(float gy=(float)Math.floor(minY/META)*META;gy<maxY;gy+=META){
            float[] p=poly0;int n5=3;
            float[][] planes={{1,0,-gx},{-1,0,gx+META},{0,1,-gy},{0,-1,gy+META}};
            for(float[] pl:planes){if(n5<3)break;float[] q=new float[(n5+4)*5];int qn=0;
                for(int i2=0;i2<n5;i2++){int j2=(i2+1)%n5;float di=pl[0]*p[i2*5]+pl[1]*p[i2*5+1]+pl[2],dj=pl[0]*p[j2*5]+pl[1]*p[j2*5+1]+pl[2];
                    if(di>=0){System.arraycopy(p,i2*5,q,qn*5,5);qn++;}
                    if((di>=0)!=(dj>=0)){float tt=di/(di-dj);for(int e=0;e<5;e++)q[qn*5+e]=p[i2*5+e]+(p[j2*5+e]-p[i2*5+e])*tt;qn++;}}
                p=q;n5=qn;}
            if(n5<3)continue;
            for(int k=1;k+1<n5;k++){if(on+15>out.length)out=java.util.Arrays.copyOf(out,out.length*2);
                System.arraycopy(p,0,out,on,5);on+=5;System.arraycopy(p,k*5,out,on,5);on+=5;System.arraycopy(p,(k+1)*5,out,on,5);on+=5;}
        }
        got=java.util.Arrays.copyOf(out,on);tess[si][n]=got;return got;
    }
    private void plane(float[] sub,int a,int b,int c,float h,int mat,int L,int pid){
        int m=clampMat(mat);float br=Textures.BRIGHT[m]?1:0;
        for(int o=0;o+4<sub.length;o+=5){float x=sub[o],y=sub[o+1],w0=sub[o+2],w1=sub[o+3],w2=sub[o+4];
            put(m,x,y,h,x*.5f,y*.5f,L,br,pid,1,0,0,gv(a,0)*w0+gv(b,0)*w1+gv(c,0)*w2,gv(a,1)*w0+gv(b,1)*w1+gv(c,1)*w2,gv(a,2)*w0+gv(b,2)*w1+gv(c,2)*w2,0,0,0);}
    }
    private void wall(Object lists,Level.Vertex a,Level.Vertex b,float len,float lo,float hi,int mat,int L,int pid,float peg,int ia,int ib){wall(lists,a,b,len,lo,hi,mat,L,pid,peg,ia,ib,0);}
    /** {@code edge}: 1 = top edge is open (a step), 2 = bottom edge is open (a lintel); used for relief silhouettes. */
    private void wall(Object lists,Level.Vertex a,Level.Vertex b,float len,float lo,float hi,int mat,int L,int pid,float peg,int ia,int ib,int edge){
        if(hi-lo<.001f)return;int m=clampMat(mat);float B=Textures.BRIGHT[m]?1:0;
        float u1=len*.5f,vt=-(hi-peg)*.5f,vb2=-(lo-peg)*.5f,tx=len<1e-6f?1:(b.x-a.x)/len,ty=len<1e-6f?0:(b.y-a.y)/len;
        float ar=gv(ia,0),ag=gv(ia,1),ab=gv(ia,2),br2=gv(ib,0),bg=gv(ib,1),bb=gv(ib,2);
        // meta tiles: columns every 2 units along the wall, rows on 2-unit heights
        int cols=Math.max(1,(int)Math.ceil(len/META-1e-3f));
        float[] zs=new float[2+(int)Math.max(0,(hi-lo)/META)+1];int nz=0;zs[nz++]=lo;for(float z=(float)Math.floor(lo/META+1)*META;z<hi-1e-3f;z+=META)zs[nz++]=z;zs[nz++]=hi;
        for(int ci=0;ci<cols;ci++){float t0=ci/(float)cols,t1=(ci+1)/(float)cols;
            float x0=a.x+(b.x-a.x)*t0,y0=a.y+(b.y-a.y)*t0,x1=a.x+(b.x-a.x)*t1,y1=a.y+(b.y-a.y)*t1,u0=u1*t0,uu1=u1*t1;
            float r0=ar+(br2-ar)*t0,g0=ag+(bg-ag)*t0,b0=ab+(bb-ab)*t0,r1=ar+(br2-ar)*t1,g1=ag+(bg-ag)*t1,b1=ab+(bb-ab)*t1;
            for(int zi=0;zi+1<nz;zi++){float zl=zs[zi],zh=zs[zi+1],vl=-(zl-peg)*.5f,vh=-(zh-peg)*.5f;
                put(m,x0,y0,zh,u0,vh,L,B,pid,tx,ty,1,r0,g0,b0,vt,vb2,edge);put(m,x1,y1,zh,uu1,vh,L,B,pid,tx,ty,1,r1,g1,b1,vt,vb2,edge);put(m,x1,y1,zl,uu1,vl,L,B,pid,tx,ty,1,r1,g1,b1,vt,vb2,edge);
                put(m,x0,y0,zh,u0,vh,L,B,pid,tx,ty,1,r0,g0,b0,vt,vb2,edge);put(m,x1,y1,zl,uu1,vl,L,B,pid,tx,ty,1,r1,g1,b1,vt,vb2,edge);put(m,x0,y0,zl,u0,vl,L,B,pid,tx,ty,1,r0,g0,b0,vt,vb2,edge);}}
    }

    // ------------------------------------------------------------------ 3D models (PS1-style vertex-animated meshes)
    private static final int[][] modelTex=new int[Sprites.COUNT][];
    private static int[] modelTextures(int id,MeshModels.Mesh m){
        if(modelTex[id]==null){int S=m.atlas*MeshModels.TILE;modelTex[id]=new int[]{texture(m.color,S,S,false,true),texture(m.relief,S,S,false,false),texture(m.mat,S,S,false,true)};}
        return modelTex[id];
    }
    private boolean model(Level level,Renderer.SpriteDraw d){
        MeshModels.Mesh mm=MeshModels.get(d.model);if(mm==null)return false;
        MeshModels.Frame A=mm.frames[Math.max(0,Math.min(Sprites.FRAMES-1,d.fa))],B=mm.frames[Math.max(0,Math.min(Sprites.FRAMES-1,d.fb))];
        if(A==null)return false;boolean blend=B!=null&&B.count==A.count&&d.lerp>.001f;float t=blend?d.lerp:0;
        float dx=d.x-cx,dy=d.y-cy;if(dx*cs+dy*sn<-2f)return true;   // behind the camera
        int[] tex=modelTextures(d.model,mm);
        int si=level.sectorAt(d.x,d.y);int L=si<0?24:Renderer.lightLevel(level.sectors.get(si).light);setGi(si);
        float ca=(float)Math.cos(d.angle),sa=(float)Math.sin(d.angle);
        int n=A.count,first=nv;ensure(n);float[] a=A.v,b=blend?B.v:A.v;boolean bright=d.bright;
        for(int i=0;i<n;i++){int k=i*9;
            float mx=a[k]+(b[k]-a[k])*t,my=a[k+1]+(b[k+1]-a[k+1])*t,mz=a[k+2]+(b[k+2]-a[k+2])*t;
            float nx=a[k+3]+(b[k+3]-a[k+3])*t,ny=a[k+4]+(b[k+4]-a[k+4])*t,nz=a[k+5]+(b[k+5]-a[k+5])*t;
            int o=nv*ST;
            vtx(d.x+ca*mx-sa*my,d.y+sa*mx+ca*my,d.z+mz,a[k+6],a[k+7],L,(bright||a[k+8]>.5f)?1:0,d.pickId);
            verts[o+11]=ca*nx-sa*ny;verts[o+12]=sa*nx+ca*ny;verts[o+13]=nz;verts[o+14]=3;}
        batches.add(new int[]{tex[0],first,n,5,tex[1],tex[2]});
        return true;
    }
    private void sprite(Level level,Renderer.SpriteDraw d){
        if(d.model>=0&&models3d&&model(level,d))return;
        Sprites.Sprite s=d.spr;if(s==null)return;
        float dx=d.x-cx,dy=d.y-cy,cz=dx*cs+dy*sn;if(cz<.12f)return;
        float ww=s.w/s.ppu,hh=s.h/s.ppu,rx=-sn*ww*.5f,ry=cs*ww*.5f;
        int si=level.sectorAt(d.x,d.y);int L=si<0?24:Renderer.lightLevel(level.sectors.get(si).light);float B=(d.bright||s.bright)?1:0;
        int tex=spriteTexture(s);int first=nv;ensure(6);setGi(si);
        // left edge = screen-left: world point minus right vector
        float x0=d.x-rx,y0=d.y-ry,x1=d.x+rx,y1=d.y+ry,z0=d.z,z1=d.z+hh;
        vtx(x0,y0,z1,0,0,L,B,d.pickId);vtx(x1,y1,z1,1,0,L,B,d.pickId);vtx(x1,y1,z0,1,1,L,B,d.pickId);
        vtx(x0,y0,z1,0,0,L,B,d.pickId);vtx(x1,y1,z0,1,1,L,B,d.pickId);vtx(x0,y0,z0,0,1,L,B,d.pickId);
        batches.add(new int[]{tex,first,6,1});
    }
    private int spriteTexture(Sprites.Sprite s){
        int[] e=spriteTex.get(s);
        if(e==null){e=new int[]{texture(s.px,s.w,s.h,false),frame};spriteTex.put(s,e);}
        e[1]=frame;return e[0];
    }
    private static final int MS=32;private static final float SPAN=1.9f;
    private void splat(Level level,World.Splat s){
        float nx=s.nx,ny=s.ny,nz=s.nz;
        if((cx-s.x)*nx+(cy-s.y)*ny+(eye-s.z)*nz<=0)return;
        float dx=s.x-cx,dy=s.y-cy,ccz=dx*cs+dy*sn,R=s.r*SPAN;if(ccz<-R||ccz>40)return;
        float ux,uy,uz,vx2,vy2,vz2;
        if(nz>.5f){ux=1;uy=0;uz=0;vx2=0;vy2=1;vz2=0;}else{ux=-ny;uy=nx;uz=0;vx2=0;vy2=0;vz2=1;}
        int si=level.sectorAt(s.x+nx*.02f,s.y+ny*.02f);int L=si<0?24:Renderer.lightLevel(level.sectors.get(si).light);
        ensure(6);setGi(si);float seed=(s.seed%1000)/1000f+.001f,wallF=nz<.5f?1:0;
        float cr=((s.color>>16)&255)/255f,cg=((s.color>>8)&255)/255f,cb=(s.color&255)/255f;
        for(int q=0;q<6;q++){int k=q<3?q:(q==3?0:q==4?2:3);float a=(k==0||k==3)?-R:R,b=k<2?-R:R;
            int o=nv*ST;vtx(s.x+ux*a+vx2*b,s.y+uy*a+vy2*b,s.z+uz*a+vz2*b,(a/R+1)*.5f,(b/R+1)*.5f,L,0,-1);
            verts[o+7]=cr;verts[o+8]=cg;verts[o+9]=cb;verts[o+10]=1;verts[o+11]=seed;verts[o+12]=wallF;}
    }
    // ------------------------------------------------------------------ sky light and bounce light
    private float[] giV=new float[0],giS=new float[0];
    private float gv(int v,int ch){return v<0||v*3+ch>=giV.length?0:giV[v*3+ch];}
    private void setGi(int si){if(si<0||si*3+2>=giS.length){giR=giG=giB=0;return;}giR=giS[si*3];giG=giS[si*3+1];giB=giS[si*3+2];}
    /**
     * Light from the sky and from glowing floors, bounced between sectors. Sectors open to the sky are lit by the
     * sky's average colour; every sector passes light on through its openings (sized by how big the opening is
     * compared with the sector), tinted by the colour of its floor and walls. Doors that close cut the light off.
     * The result is averaged onto map vertices so it fades smoothly through doorways.
     */
    private void computeGi(Level level){
        int ns=level.sectors.size(),nv=level.vertices.size();
        if(giS.length<ns*3)giS=new float[ns*3];if(giV.length<nv*3)giV=new float[nv*3];
        java.util.Arrays.fill(giS,0,ns*3,0);java.util.Arrays.fill(giV,0,nv*3,0);
        if(!gi||ns==0)return;
        float[] E=new float[ns*3],rho=new float[ns*3],surfA=new float[ns],I=new float[ns*3],Bo=new float[ns*3];
        for(int si=0;si<ns;si++){Level.Sector s=level.sectors.get(si);
            float area=0;int[] t=si<r.tris.length?r.tris[si]:new int[0];
            for(int k=0;k+2<t.length;k+=3){if(t[k]>=nv||t[k+1]>=nv||t[k+2]>=nv)continue;Level.Vertex a=level.vertices.get(t[k]),b=level.vertices.get(t[k+1]),c=level.vertices.get(t[k+2]);area+=Math.abs((b.x-a.x)*(c.y-a.y)-(c.x-a.x)*(b.y-a.y))*.5f;}
            float per=0;for(int k=0;k<s.loop.size();k++){int ia=s.loop.get(k),ib=s.loop.get((k+1)%s.loop.size());if(ia>=nv||ib>=nv)continue;Level.Vertex a=level.vertices.get(ia),b=level.vertices.get(ib);per+=(float)Math.hypot(b.x-a.x,b.y-a.y);}
            surfA[si]=Math.max(1,area*2+per*Math.max(.5f,s.ceiling-s.floor));
            float[] fa=avg[clampMat(s.floorMaterial)],wa=avg[clampMat(s.wallMaterial)];
            for(int ch=0;ch<3;ch++)rho[si*3+ch]=Math.min(.85f,(fa[ch]*.5f+wa[ch]*.5f)*1.1f);
            if(s.ceilingMaterial==Textures.SKY)for(int ch=0;ch<3;ch++)E[si*3+ch]+=skyAvg[ch]*.55f;
            if(Textures.BRIGHT[clampMat(s.floorMaterial)])for(int ch=0;ch<3;ch++)E[si*3+ch]+=fa[ch]*.45f;
            if(s.ceilingMaterial!=Textures.SKY&&Textures.BRIGHT[clampMat(s.ceilingMaterial)]){float[] ca=avg[clampMat(s.ceilingMaterial)];for(int ch=0;ch<3;ch++)E[si*3+ch]+=ca[ch]*.3f;}
        }
        // openings between sectors
        ArrayList<float[]> links=new ArrayList<>();   // {from, to, transfer}
        for(Level.LineDef l:r.lines){if(l.back<0||l.front<0||l.back>=ns||l.front>=ns||l.from>=nv||l.to>=nv)continue;
            Level.Sector f=level.sectors.get(l.front),k=level.sectors.get(l.back);
            float open=Math.min(f.ceiling,k.ceiling)-Math.max(f.floor,k.floor);if(open<=.02f)continue;
            Level.Vertex a=level.vertices.get(l.from),b=level.vertices.get(l.to);float ar=(float)Math.hypot(b.x-a.x,b.y-a.y)*open;
            links.add(new float[]{l.front,l.back,Math.min(.6f,ar/surfA[l.front]*2.2f)});links.add(new float[]{l.back,l.front,Math.min(.6f,ar/surfA[l.back]*2.2f)});}
        for(int it=0;it<10;it++){
            for(int i=0;i<ns*3;i++)I[i]=E[i];
            for(float[] lk:links){int to=(int)lk[0],from=(int)lk[1];float tr=lk[2];for(int ch=0;ch<3;ch++)I[to*3+ch]+=Bo[from*3+ch]*tr;}
            for(int si=0;si<ns;si++){boolean sky=level.sectors.get(si).ceilingMaterial==Textures.SKY;
                for(int ch=0;ch<3;ch++)Bo[si*3+ch]=I[si*3+ch]*rho[si*3+ch]+(sky?skyAvg[ch]*.4f:0);}
        }
        for(int i=0;i<ns*3;i++)giS[i]=Math.min(1.2f,I[i]);
        int[] cnt=new int[nv];
        for(int si=0;si<ns;si++)for(int v:level.sectors.get(si).loop){if(v<0||v>=nv)continue;cnt[v]++;for(int ch=0;ch<3;ch++)giV[v*3+ch]+=giS[si*3+ch];}
        for(int v=0;v<nv;v++)if(cnt[v]>1)for(int ch=0;ch<3;ch++)giV[v*3+ch]/=cnt[v];
    }
    // ------------------------------------------------------------------ dynamic shadows (polar shadow maps)
    private final float[] wallD=new float[SB],actD=new float[SB],actTop=new float[SB];
    private final ByteBuffer shadowBuf=ByteBuffer.allocateDirect(SB*16*4).order(ByteOrder.nativeOrder());
    /**
     * The maps are 2.5D (walls are vertical), so a light's shadows fit in one row: for every direction around the
     * light, how far until a wall blocks it. Walls, closed doors and steps the light can't see past are drawn into
     * that row on the CPU (only lines within the light's reach), plus actors as round blockers with a height, so
     * monsters cast shadows that end at their head. The shader then needs one lookup per light: very cheap.
     */
    private void buildShadows(Level level,List<Renderer.SpriteDraw> sprites){
        int n=Math.min(SHADOW_LIGHTS,nl),nvx=level.vertices.size(),ns=level.sectors.size();
        shadowBuf.position(0);
        for(int li=0;li<16;li++){
            java.util.Arrays.fill(wallD,32f);java.util.Arrays.fill(actD,24f);java.util.Arrays.fill(actTop,0f);
            if(li<n){
                int L=order[li];float lx=r.lx[L],ly=r.ly[L],lz=r.lz[L],rad=(float)Math.sqrt(r.lr2[L]);
                for(Level.LineDef l:r.lines){
                    if(l.from>=nvx||l.to>=nvx||l.front<0||l.front>=ns)continue;
                    boolean block;
                    if(l.back<0||l.back>=ns)block=true;
                    else{Level.Sector f=level.sectors.get(l.front),k=level.sectors.get(l.back);float lo=Math.max(f.floor,k.floor),hi=Math.min(f.ceiling,k.ceiling);
                        block=hi-lo<.02f||lz<lo+.02f||lz>hi-.02f;}
                    if(!block)continue;
                    Level.Vertex a=level.vertices.get(l.from),b=level.vertices.get(l.to);
                    if(segDist(lx,ly,a.x,a.y,b.x,b.y)>rad)continue;
                    rasterSeg(lx,ly,a.x,a.y,b.x,b.y);
                }
                if(sprites!=null)for(Renderer.SpriteDraw sd:sprites){Sprites.Sprite sp=sd.spr;if(sp==null||sd.bright||sp.bright)continue;
                    float dx=sd.x-lx,dy=sd.y-ly,d=(float)Math.hypot(dx,dy);if(d<.4f||d>rad)continue;
                    float ro=Math.min(.45f,sp.w/sp.ppu*.28f),top=sd.z+sp.h/sp.ppu*.92f,half=(float)Math.asin(Math.min(1,ro/d)),c=(float)Math.atan2(dy,dx);
                    int b0=(int)Math.floor((c-half)/(2*Math.PI)*SB+SB*.5f),b1=(int)Math.ceil((c+half)/(2*Math.PI)*SB+SB*.5f);
                    for(int bb=b0;bb<=b1;bb++){int bi=Math.floorMod(bb,SB);float dd=Math.max(0,d-ro);if(dd<actD[bi]){actD[bi]=dd;actTop[bi]=top;}}
                }
            }
            for(int i=0;i<SB;i++){int w16=Math.round(Math.max(0,Math.min(1,wallD[i]/32f))*65535);
                shadowBuf.put((byte)(w16>>8)).put((byte)w16).put((byte)Math.round(Math.max(0,Math.min(1,actD[i]/24f))*255)).put((byte)Math.round(Math.max(0,Math.min(1,actTop[i]/16f))*255));}
        }
        shadowBuf.position(0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,shadowTex);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,SB,16,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,shadowBuf);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }
    private static float segDist(float px,float py,float ax,float ay,float bx,float by){
        float vx=bx-ax,vy=by-ay,l2=vx*vx+vy*vy;float t=l2<1e-9f?0:Math.max(0,Math.min(1,((px-ax)*vx+(py-ay)*vy)/l2));
        return (float)Math.hypot(px-(ax+vx*t),py-(ay+vy*t));
    }
    /** Writes a blocking segment into the current row: for each angle bin it covers, the distance along that ray. */
    private void rasterSeg(float lx,float ly,float ax,float ay,float bx,float by){
        float a0=(float)Math.atan2(ay-ly,ax-lx),a1=(float)Math.atan2(by-ly,bx-lx);float span=a1-a0;
        while(span>Math.PI)span-=2*Math.PI;while(span<-Math.PI)span+=2*Math.PI;
        float lo=span>=0?a0:a0+span,hi=lo+Math.abs(span);
        int b0=(int)Math.floor(lo/(2*Math.PI)*SB+SB*.5f)-1,b1=(int)Math.ceil(hi/(2*Math.PI)*SB+SB*.5f)+1;
        float ex=bx-ax,ey=by-ay;
        for(int bb=b0;bb<=b1;bb++){int bi=Math.floorMod(bb,SB);double th=((bb+.5)-SB*.5)/SB*2*Math.PI;float dx=(float)Math.cos(th),dy=(float)Math.sin(th);
            float den=dx*ey-dy*ex;if(Math.abs(den)<1e-7f)continue;
            float t=((ax-lx)*ey-(ay-ly)*ex)/den,u=((ax-lx)*dy-(ay-ly)*dx)/den;
            if(u<-.02f||u>1.02f||t<=0)continue;if(t<wallD[bi])wallD[bi]=t;}
    }
    private static int clampMat(int m){return Math.max(0,Math.min(Textures.COUNT-1,m));}
    private void trimCaches(){
        if((frame&63)!=0)return;
        for(Iterator<Map.Entry<Sprites.Sprite,int[]>> it=spriteTex.entrySet().iterator();it.hasNext();){int[] e=it.next().getValue();if(frame-e[1]>240){GLES20.glDeleteTextures(1,e,0);it.remove();}}
        for(Iterator<Map.Entry<World.Splat,int[]>> it=splatTex.entrySet().iterator();it.hasNext();){int[] e=it.next().getValue();if(frame-e[1]>120){GLES20.glDeleteTextures(1,e,0);it.remove();}}
    }
    private FloatBuffer upload(){
        FloatBuffer b=vb.capacity()>=nv*ST?vb:ByteBuffer.allocateDirect(Math.max(nv*ST,4096)*4*2).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.clear();b.put(verts,0,nv*ST);b.position(0);return b;
    }
    /**
     * Vertex data goes into GPU buffers (one each for the scene, coloured specks and screen quads), re-filled every
     * frame. Client-side arrays made the driver copy everything per draw call into memory it only recycles at a
     * buffer swap, which an off-screen renderer never does, so it piled up in big fights.
     */
    private int[] vbos;private int lastUpload=-1;
    private static final int VBO_SCENE=0,VBO_COLOR=1,VBO_QUAD=2;
    private void bindScene(int prog,FloatBuffer b){bindVbo(b==vb?VBO_SCENE:b==qb?VBO_QUAD:VBO_COLOR,b,b==vb?nv*ST:b==qb?6*ST:-1);}
    private void bindVbo(int which,FloatBuffer data,int floats){
        if(vbos==null){vbos=new int[3];GLES20.glGenBuffers(3,vbos,0);}
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vbos[which]);
        if(data!=null&&floats!=0){if(floats<0)floats=data.limit();
            if(!(which==VBO_SCENE&&lastUpload==frame)){data.position(0);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,Math.max(4,floats*4),data,GLES20.GL_STREAM_DRAW);if(which==VBO_SCENE)lastUpload=frame;}}
        int stride=ST*4;
        GLES20.glVertexAttribPointer(0,3,GLES20.GL_FLOAT,false,stride,0);GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1,2,GLES20.GL_FLOAT,false,stride,3*4);GLES20.glEnableVertexAttribArray(1);
        GLES20.glVertexAttribPointer(2,2,GLES20.GL_FLOAT,false,stride,5*4);GLES20.glEnableVertexAttribArray(2);
        GLES20.glVertexAttribPointer(3,4,GLES20.GL_FLOAT,false,stride,7*4);GLES20.glEnableVertexAttribArray(3);
        GLES20.glVertexAttribPointer(4,3,GLES20.GL_FLOAT,false,stride,11*4);GLES20.glEnableVertexAttribArray(4);
        GLES20.glVertexAttribPointer(5,1,GLES20.GL_FLOAT,false,stride,14*4);GLES20.glEnableVertexAttribArray(5);
        GLES20.glVertexAttribPointer(6,3,GLES20.GL_FLOAT,false,stride,16*4);GLES20.glEnableVertexAttribArray(6);
        GLES20.glVertexAttribPointer(7,3,GLES20.GL_FLOAT,false,stride,20*4);GLES20.glEnableVertexAttribArray(7);
    }
    private void camUniforms(int p){
        GLES20.glUniform3f(GLES20.glGetUniformLocation(p,"uCam"),cx,cy,eye);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(p,"uCS"),cs,sn);
        // clip y = (2*horizon/H-1)*z - y*2*focal/H  (image top at row 0 after read-back)
        float n=.06f,f=120f;
        GLES20.glUniform3f(GLES20.glGetUniformLocation(p,"uProj"),2*focal/H,2*horizon/H-1,(f+n)/(f-n));
        int us=GLES20.glGetUniformLocation(p,"uSnap");if(us>=0)GLES20.glUniform2f(us,ps1?W*.5f:0,ps1?H*.5f:0);
    }
    private void atmosUniforms(int p){
        float fa=r.fogAmt;GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"uFog"),r.fogStart,1f/Math.max(.01f,r.fogEnd-r.fogStart),fa,H);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(p,"uFogC"),r.fogR/255f,r.fogG/255f,r.fogB/255f);
        float ta=r.tintAmt;GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"uTint"),1-ta+ta*r.tintR/255f,1-ta+ta*r.tintG/255f,1-ta+ta*r.tintB/255f,1);
    }
    private final float[] lp=new float[16*4],lc=new float[16*3];
    /** Lights shaded per pixel (the ones nearest the camera for their size), and how many of them cast shadows. */
    static final int MAX_SHADED=8,SHADOW_LIGHTS=4;
    private final int[] order=new int[16];private int nl;
    private void pickLights(){
        int n=Math.min(16,r.lightCount);float[] score=new float[n];
        for(int i=0;i<n;i++){float dx=r.lx[i]-cx,dy=r.ly[i]-cy;score[i]=(dx*dx+dy*dy)/Math.max(.5f,r.lr2[i]);order[i]=i;}
        for(int i=1;i<n;i++){int k=order[i];float sk=score[k];int j=i-1;while(j>=0&&score[order[j]]>sk){order[j+1]=order[j];j--;}order[j+1]=k;}
        nl=Math.min(MAX_SHADED,n);
    }
    private void sceneUniforms(int p){
        camUniforms(p);atmosUniforms(p);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uAffine"),ps1?1f:0f);GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uDither"),ps1?1:0);GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uPom"),1f);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uTex"),0);GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uNorm"),1);GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uMat"),2);GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uShadow"),3);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uShadowOn"),shadows&&nl>0?1:0);GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uPbr"),pbr?1:0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(p,"uCamPos"),cx,cy,eye);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uFade"),r.fadeK);GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uAlphaCut"),-1f);
        int n=nl;
        for(int j=0;j<n;j++){int i=order[j];lp[j*4]=r.lx[i];lp[j*4+1]=r.ly[i];lp[j*4+2]=r.lz[i];lp[j*4+3]=r.lr2[i];lc[j*3]=r.lcr[i];lc[j*3+1]=r.lcg[i];lc[j*3+2]=r.lcb[i];}
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uShadowN"),Math.min(SHADOW_LIGHTS,n));
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uCount"),n);
        if(n>0){GLES20.glUniform4fv(GLES20.glGetUniformLocation(p,"uL"),n,lp,0);GLES20.glUniform3fv(GLES20.glGetUniformLocation(p,"uLC"),n,lc,0);}
        float[] hi=r.highlight>=0?idVec(r.highlight):new float[]{-1,-1,-1,-1};
        GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"uHi"),hi[0],hi[1],hi[2],r.highlight>=0?hi[3]:-1);
    }
    private void drawSky(){
        GLES20.glUseProgram(progSky);atmosUniforms(progSky);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(progSky,"uSize"),W,H);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(progSky,"uSky"),angle,focal,horizon,W);GLES20.glUniform1f(GLES20.glGetUniformLocation(progSky,"uDither"),ps1?1:0);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progSky,"uTex"),0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,skyTex);
        quad(0,0,W,H,0,0,1,1);
    }
    private final FloatBuffer qb=ByteBuffer.allocateDirect(6*ST*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Screen-space quad in internal pixels (top-left origin). */
    private void quad(float x0,float y0,float x1,float y1,float u0,float v0,float u1,float v1){
        float[][] c={{x0,y0,u0,v0},{x1,y0,u1,v0},{x1,y1,u1,v1},{x0,y0,u0,v0},{x1,y1,u1,v1},{x0,y1,u0,v1}};float[] q=new float[6*ST];
        for(int i=0;i<6;i++){q[i*ST]=c[i][0];q[i*ST+1]=c[i][1];q[i*ST+3]=c[i][2];q[i*ST+4]=c[i][3];}
        qb.clear();qb.put(q);qb.position(0);bindScene(0,qb);for(int a=2;a<8;a++)GLES20.glDisableVertexAttribArray(a);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6);
    }
    private float[] cverts=new float[0];private FloatBuffer cbuf;
    private void drawColored(Level level){
        World fx=r.fx;Atmosphere.Particles ps=r.particles;
        int need=(fx==null?0:fx.drops.size())+(ps==null?0:ps.n);if(need==0)return;
        if(cverts.length<need*6*ST)cverts=new float[need*6*ST];int n=0;
        if(fx!=null)for(World.Drop d:fx.drops){float dx=d.x-cx,dy=d.y-cy,cz=dx*cs+dy*sn;if(cz<.12f)continue;
            int si=level.sectorAt(d.x,d.y);int L=si<0?24:Renderer.lightLevel(level.sectors.get(si).light);int li=Math.max(Math.max(1,L/4),Math.min(31,(int)(L+3-cz*r.fadeK)));
            int c=Textures.mul(d.color,Textures.lightMul(li));n=cquad(n,d.x,d.y,d.z,d.size*.5f,c,1f);}
        if(ps!=null)for(int i=0;i<ps.n;i++){float fade=Math.min(1,ps.life[i]/Math.max(.01f,ps.max[i])*2.2f);n=cquad(n,ps.x[i],ps.y[i],ps.z[i],Math.max(.012f,ps.size[i]*.5f),ps.col[i],fade*.8f);}
        if(n==0)return;
        if(cbuf==null||cbuf.capacity()<n*ST)cbuf=ByteBuffer.allocateDirect(Math.max(n*ST,6*ST*256)*4*2).order(ByteOrder.nativeOrder()).asFloatBuffer();
        FloatBuffer b=cbuf;b.clear();b.put(cverts,0,n*ST);b.flip();
        GLES20.glUseProgram(progColor);camUniforms(progColor);atmosUniforms(progColor);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);GLES20.glDepthMask(false);
        bindScene(progColor,b);GLES20.glDisableVertexAttribArray(1);GLES20.glDisableVertexAttribArray(2);GLES20.glDisableVertexAttribArray(4);GLES20.glDisableVertexAttribArray(5);GLES20.glDisableVertexAttribArray(6);GLES20.glDisableVertexAttribArray(7);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,n);
        GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_BLEND);
    }
    private int cquad(int n,float x,float y,float z,float h,int col,float a){
        float rx=-sn*h,ry=cs*h,R=((col>>16)&255)/255f,G=((col>>8)&255)/255f,B=(col&255)/255f;
        float[][] p={{x-rx,y-ry,z+h},{x+rx,y+ry,z+h},{x+rx,y+ry,z-h},{x-rx,y-ry,z+h},{x+rx,y+ry,z-h},{x-rx,y-ry,z-h}};
        for(float[] q:p){int o=n*ST;cverts[o]=q[0];cverts[o+1]=q[1];cverts[o+2]=q[2];for(int k=3;k<7;k++)cverts[o+k]=0;cverts[o+7]=R;cverts[o+8]=G;cverts[o+9]=B;cverts[o+10]=a;for(int k=11;k<ST;k++)cverts[o+k]=0;n++;}
        return n;
    }

    // ------------------------------------------------------------------ overlays (after the scene, before present)
    void weapon(Sprites.Sprite s,int x0,int y0,int w,int h,int mulv){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glViewport(0,0,W,H);GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);
        GLES20.glUseProgram(progScreen);GLES20.glUniform2f(GLES20.glGetUniformLocation(progScreen,"uSize"),W,H);
        float m=mulv/256f;GLES20.glUniform4f(GLES20.glGetUniformLocation(progScreen,"uMul"),m,m,m,1);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScreen,"uUseTex"),1);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progScreen,"uTex"),0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,spriteTexture(s));
        quad(x0,y0,x0+w,y0+h,0,0,1,1);GLES20.glDisable(GLES20.GL_BLEND);
    }
    void tint(int color,float a){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glViewport(0,0,W,H);GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);
        GLES20.glUseProgram(progScreen);GLES20.glUniform2f(GLES20.glGetUniformLocation(progScreen,"uSize"),W,H);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(progScreen,"uMul"),((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,a);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScreen,"uUseTex"),0);
        quad(0,0,W,H,0,0,1,1);GLES20.glDisable(GLES20.GL_BLEND);
    }
    /** Reads the finished frame back into a bitmap (row 0 = top). */
    Bitmap finish(){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);read.position(0);
        GLES20.glReadPixels(0,0,W,H,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,read);read.position(0);
        bmp.copyPixelsFromBuffer(read);
        EGL14.eglSwapBuffers(dpy,surf);   // frame boundary for the driver (frees its per-frame memory)
        return bmp;
    }
    /** Pick id at an internal pixel, by drawing last frame's surfaces and sprites with their ids. */
    private final ByteBuffer pickBuf=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
    int pick(int px,int py){
        if(nv==0)return -1;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,idFbo);GLES20.glViewport(0,0,W,H);
        GLES20.glColorMask(true,true,true,true);GLES20.glClearColor(1,1,1,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthFunc(GLES20.GL_LEQUAL);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glColorMask(true,true,true,true);
        GLES20.glUseProgram(progId);camUniforms(progId);GLES20.glUniform1i(GLES20.glGetUniformLocation(progId,"uTex"),0);
        bindScene(progId,vb);
        for(int[] b:batches){if(b[3]==2||b[3]==4)continue;GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[3]==3?wallTex[0]:b[0]);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,b[1],b[2]);}
        ByteBuffer one=pickBuf;one.clear();
        GLES20.glReadPixels(px,py,1,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,one);
        int a=one.get(0)&255,b=one.get(1)&255,c=one.get(2)&255,d=one.get(3)&255;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glColorMask(true,true,true,false);
        if(a==255&&b==255&&c==255&&d==255)return -1;
        int id=(a<<24)|(b<<16)|(c<<8)|d;return id==0?-1:id;
    }
}
