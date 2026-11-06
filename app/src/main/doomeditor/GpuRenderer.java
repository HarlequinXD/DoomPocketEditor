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
    private static int progScene,progColor,progSky,progScreen,progId;
    private static final int[] wallTex=new int[Textures.COUNT];private static int skyTex;
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
            progScene=program(VS_SCENE,FS_SCENE);progColor=program(VS_COLOR,FS_COLOR);progSky=program(VS_SCREEN,FS_SKY);progScreen=program(VS_SCREEN,FS_SCREEN);progId=program(VS_SCENE,FS_ID);
            Textures.init();
            for(int t=0;t<Textures.COUNT;t++)wallTex[t]=texture(Textures.base[t],Textures.SIZE,Textures.SIZE,true,true);
            skyTex=texture(Textures.sky,Textures.SKY_W,Textures.SKY_H,true,true);
            return true;
        }catch(Throwable e){failed=true;android.util.Log.w("GpuRenderer","GPU renderer unavailable, using software",e);return false;}
    }
    private static boolean makeCurrent(){return EGL14.eglMakeCurrent(dpy,surf,surf,ctx);}
    private static int program(String vs,String fs){
        int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);
        GLES20.glBindAttribLocation(p,0,"aPos");GLES20.glBindAttribLocation(p,1,"aUv");GLES20.glBindAttribLocation(p,2,"aLight");GLES20.glBindAttribLocation(p,3,"aId");
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
    private static ByteBuffer rgba(int[] argb,int n,boolean opaque){
        ByteBuffer b=ByteBuffer.allocateDirect(n*4).order(ByteOrder.nativeOrder());
        for(int i=0;i<n;i++){int c=argb[i];b.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put(opaque?(byte)255:(byte)(c>>>24));}
        b.position(0);return b;
    }

    // ------------------------------------------------------------------ shaders
    // scene vertex: world position, texture coords, (sector light, bright flag), pick id bytes
    private static final String VS_SCENE=
        "uniform vec3 uCam;uniform vec2 uCS;uniform vec3 uProj;\n"+
        "attribute vec3 aPos;attribute vec2 aUv;attribute vec2 aLight;attribute vec4 aId;\n"+
        "varying vec2 vUv;varying vec2 vLight;varying vec3 vWorld;varying float vZ;varying vec4 vId;\n"+
        "void main(){vec2 d=aPos.xy-uCam.xy;float x=-d.x*uCS.y+d.y*uCS.x;float z=d.x*uCS.x+d.y*uCS.y;float y=aPos.z-uCam.z;\n"+
        " vUv=aUv;vLight=aLight;vWorld=aPos;vZ=z;vId=aId;\n"+
        " gl_Position=vec4(x,uProj.y*z-y*uProj.x,(z*uProj.z-2.0*0.06*120.0/(120.0-0.06)),z);}\n";
    private static final String FS_HEAD=
        "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";
    private static final String FS_SCENE=FS_HEAD+
        "uniform sampler2D uTex;uniform float uFade;uniform int uCount;uniform vec4 uL[16];uniform vec3 uLC[16];\n"+
        "uniform vec4 uFog;uniform vec3 uFogC;uniform vec4 uTint;uniform vec4 uHi;uniform float uAlphaCut;\n"+
        "varying vec2 vUv;varying vec2 vLight;varying vec3 vWorld;varying float vZ;varying vec4 vId;\n"+
        "void main(){vec4 t=texture2D(uTex,vUv);if(t.a<uAlphaCut)discard;\n"+
        " float L=vLight.x;float li;if(vLight.y>0.5)li=31.0;else{li=floor(L+3.0-vZ*uFade);li=clamp(li,max(1.0,floor(L/4.0)),31.0);}\n"+
        " float k=pow(li/31.0,1.25)*1.06;vec3 lc=vec3(0.0);\n"+
        " for(int i=0;i<16;i++){if(i>=uCount)break;vec3 dd=vWorld-uL[i].xyz;float d2=dot(dd,dd);if(d2<uL[i].w){float f=1.0-d2/uL[i].w;lc+=uLC[i]*(f*f);}}\n"+
        " vec3 c=t.rgb*min(vec3(1.7),vec3(k)+lc*1.35);\n"+
        " if(uFog.z>0.0&&vZ>uFog.x){float f=min(1.0,(vZ-uFog.x)*uFog.y)*uFog.z;c=mix(c,uFogC,f);}\n"+
        " c*=uTint.rgb;\n"+
        " if(uHi.a>=0.0&&all(lessThan(abs(vId-uHi),vec4(0.002))))c=c*0.625+vec3(30.0,96.0,60.0)/255.0;\n"+
        " gl_FragColor=vec4(min(c,vec3(1.0)),t.a);}\n";
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
        "uniform sampler2D uTex;uniform vec4 uSky;uniform vec4 uFog;uniform vec3 uFogC;uniform vec4 uTint;varying vec2 vUv;varying vec2 vPix;\n"+
        // uSky: angle, focal, horizon, W
        "void main(){float a=uSky.x+atan((vPix.x-uSky.w*0.5)/uSky.y);float u=a/6.2831853*2.0;\n"+
        " float v=(128.0*0.78+(vPix.y-uSky.z)*(128.0/(uFog.w*1.15)))/128.0;v=clamp(v,0.5/128.0,1.0-0.5/128.0);\n"+
        " vec3 c=texture2D(uTex,vec2(u,v)).rgb;if(uFog.z>0.0)c=mix(c,uFogC,uFog.z*0.6);gl_FragColor=vec4(c*uTint.rgb,1.0);}\n";
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
        release();W=w;H=h;
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
        bmp=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);read=ByteBuffer.allocateDirect(W*H*4).order(ByteOrder.nativeOrder());
    }
    private int colorTex;
    private void release(){
        if(fbo!=0){GLES20.glDeleteFramebuffers(2,new int[]{fbo,idFbo},0);GLES20.glDeleteRenderbuffers(2,new int[]{depthRb,idDepth},0);GLES20.glDeleteTextures(2,new int[]{colorTex,idColor},0);fbo=0;}
    }

    // ------------------------------------------------------------------ geometry building
    private float[] verts=new float[12*4096];private int nv;
    private void ensure(int more){if((nv+more)*12>verts.length)verts=java.util.Arrays.copyOf(verts,Math.max(verts.length*2,(nv+more)*12));}
    private void vtx(float x,float y,float z,float u,float v,float L,float bright,int id){
        int o=nv*12;verts[o]=x;verts[o+1]=y;verts[o+2]=z;verts[o+3]=u;verts[o+4]=v;verts[o+5]=L;verts[o+6]=bright;
        if(id<0){verts[o+7]=verts[o+8]=verts[o+9]=verts[o+10]=0;}
        else{verts[o+7]=((id>>>24)&255)/255f;verts[o+8]=((id>>16)&255)/255f;verts[o+9]=((id>>8)&255)/255f;verts[o+10]=(id&255)/255f;}
        verts[o+11]=0;nv++;
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
        // pass 1: floors, ceilings, walls into per-material buckets (stored as start indexes after sorting)
        ArrayList<float[]>[] lists=new ArrayList[nm];
        for(int si=0;si<level.sectors.size()&&si<r.tris.length;si++){
            Level.Sector s=level.sectors.get(si);int[] t=r.tris[si];int L=Renderer.lightLevel(s.light);
            for(int k=0;k+2<t.length;k+=3){
                if(t[k]>=vs||t[k+1]>=vs||t[k+2]>=vs)continue;
                if(s.floor<eyeZ)plane(lists,level,t[k],t[k+1],t[k+2],s.floor,s.floorMaterial,L,Renderer.id(Renderer.ID_FLOOR,si));
                if(s.ceiling>eyeZ)plane(lists,level,t[k],t[k+1],t[k+2],s.ceiling,s.ceilingMaterial,L,Renderer.id(Renderer.ID_CEIL,si));
            }
        }
        for(int li=0;li<r.lines.size();li++){
            Level.LineDef l=r.lines.get(li);
            if(l.from>=vs||l.to>=vs||l.front<0||l.front>=level.sectors.size())continue;
            Level.Sector f=level.sectors.get(l.front);Level.Vertex a=level.vertices.get(l.from),b=level.vertices.get(l.to);
            float len=(float)Math.hypot(b.x-a.x,b.y-a.y);int L=Renderer.lightLevel(f.light)+(Math.abs(b.x-a.x)>Math.abs(b.y-a.y)?-1:1);
            if(l.back<0||l.back>=level.sectors.size())wall(lists,a,b,len,f.floor,f.ceiling,r.materialFor(level,l,0,f.wallMaterial),L,Renderer.id(Renderer.ID_WALL,li<<1),0);
            else{Level.Sector k=level.sectors.get(l.back);
                boolean lift=f.special==Level.SP_LIFT||k.special==Level.SP_LIFT,door=f.special==Level.SP_DOOR||k.special==Level.SP_DOOR;
                if(f.floor!=k.floor){float lo=Math.min(f.floor,k.floor),hi=Math.max(f.floor,k.floor);wall(lists,a,b,len,lo,hi,r.materialFor(level,l,0,f.wallMaterial),L,Renderer.id(Renderer.ID_WALL,li<<1),lift?hi:0);}
                boolean bothSky=f.ceilingMaterial==Textures.SKY&&k.ceilingMaterial==Textures.SKY;
                if(f.ceiling!=k.ceiling&&!bothSky){float lo=Math.min(f.ceiling,k.ceiling),hi=Math.max(f.ceiling,k.ceiling);wall(lists,a,b,len,lo,hi,r.materialFor(level,l,1,f.wallMaterial),L,Renderer.id(Renderer.ID_WALL,(li<<1)|1),door?lo:0);}
            }
        }
        // sky ceilings first: they only fill depth, so whatever is behind them must not be drawn yet
        int[] order=new int[nm];order[0]=Textures.SKY;for(int m=0,k=1;m<nm;m++)if(m!=Textures.SKY)order[k++]=m;
        for(int m:order){ArrayList<float[]> lst=lists[m];if(lst==null)continue;int first=nv;ensure(lst.size());
            for(float[] q:lst){System.arraycopy(q,0,verts,nv*12,12);nv++;}
            batches.add(new int[]{m==Textures.SKY?-1:wallTex[Math.max(0,Math.min(nm-1,m))],first,nv-first,m==Textures.SKY?3:0});}
        // ---- splats (decals lying on surfaces)
        World fx=r.fx;
        if(fx!=null&&!fx.splats.isEmpty())for(World.Splat s:fx.splats)splat(level,s);
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
        GLES20.glUseProgram(progScene);sceneUniforms(progScene);
        bindScene(progScene,vb);
        for(int[] b:batches){
            if(b[3]==3){GLES20.glColorMask(false,false,false,false);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,wallTex[0]);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,b[1],b[2]);GLES20.glColorMask(true,true,true,false);continue;}
            if(b[3]==2){GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);
                GLES20.glDepthMask(false);GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glPolygonOffset(-1,-4);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScene,"uAlphaCut"),.5f);}
            else if(b[3]==1){GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);
                GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScene,"uAlphaCut"),.5f);}
            else{GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glUniform1f(GLES20.glGetUniformLocation(progScene,"uAlphaCut"),-1f);}
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[0]);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,b[1],b[2]);
        }
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glDepthMask(true);
        // ---- drops and particles: coloured quads
        drawColored(level);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        trimCaches();
    }
    private void plane(ArrayList<float[]>[] lists,Level level,int a,int b,int c,float h,int mat,int L,int pid){
        ArrayList<float[]> l=list(lists,mat);boolean br=mat>=0&&mat<Textures.COUNT&&Textures.BRIGHT[mat];float[] id=idVec(pid);
        for(int v:new int[]{a,b,c}){Level.Vertex w=level.vertices.get(v);l.add(new float[]{w.x,w.y,h,w.x*.5f,w.y*.5f,L,br?1:0,id[0],id[1],id[2],id[3],0});}
    }
    private void wall(ArrayList<float[]>[] lists,Level.Vertex a,Level.Vertex b,float len,float lo,float hi,int mat,int L,int pid,float peg){
        if(hi-lo<.001f)return;ArrayList<float[]> l=list(lists,mat);boolean br=mat>=0&&mat<Textures.COUNT&&Textures.BRIGHT[mat];float[] id=idVec(pid);float B=br?1:0;
        float u1=len*.5f,vt=-(hi-peg)*.5f,vb2=-(lo-peg)*.5f;
        float[] p0={a.x,a.y,hi,0,vt,L,B,id[0],id[1],id[2],id[3],0},p1={b.x,b.y,hi,u1,vt,L,B,id[0],id[1],id[2],id[3],0},p2={b.x,b.y,lo,u1,vb2,L,B,id[0],id[1],id[2],id[3],0},p3={a.x,a.y,lo,0,vb2,L,B,id[0],id[1],id[2],id[3],0};
        l.add(p0);l.add(p1);l.add(p2);l.add(p0);l.add(p2);l.add(p3);
    }
    @SuppressWarnings("unchecked")
    private static ArrayList<float[]> list(ArrayList<float[]>[] lists,int mat){int m=Math.max(0,Math.min(lists.length-1,mat));if(lists[m]==null)lists[m]=new ArrayList<>();return lists[m];}

    private void sprite(Level level,Renderer.SpriteDraw d){
        Sprites.Sprite s=d.spr;if(s==null)return;
        float dx=d.x-cx,dy=d.y-cy,cz=dx*cs+dy*sn;if(cz<.12f)return;
        float ww=s.w/s.ppu,hh=s.h/s.ppu,rx=-sn*ww*.5f,ry=cs*ww*.5f;
        int si=level.sectorAt(d.x,d.y);int L=si<0?24:Renderer.lightLevel(level.sectors.get(si).light);float B=(d.bright||s.bright)?1:0;
        int tex=spriteTexture(s);int first=nv;ensure(6);
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
        byte[] mask=s.mask;if(mask==null){mask=SplatDesigns.mask(s.type,s.ang,nz<.5f,s.seed,MS,SPAN);s.mask=mask;}
        int[] e=splatTex.get(s);
        if(e==null){int[] px=new int[MS*MS];int c1=s.color|0xff000000,c2=Textures.mul(s.color,190),c3=Textures.mul(s.color,330),c4=Textures.mul(s.color,150);
            for(int i=0;i<px.length;i++){int sh=mask[i];px[i]=sh==0?0:sh==1?c1:sh==2?c2:sh==3?c3:c4;}
            e=new int[]{texture(px,MS,MS,false),frame};splatTex.put(s,e);}
        e[1]=frame;
        int si=level.sectorAt(s.x+nx*.02f,s.y+ny*.02f);int L=si<0?24:Renderer.lightLevel(level.sectors.get(si).light);
        int first=nv;ensure(6);float[][] c=new float[4][];
        for(int k=0;k<4;k++){float a=(k==0||k==3)?-R:R,b=k<2?-R:R;
            c[k]=new float[]{s.x+ux*a+vx2*b,s.y+uy*a+vy2*b,s.z+uz*a+vz2*b,(a/R+1)*.5f,(b/R+1)*.5f};}
        int[] order={0,1,2,0,2,3};
        for(int k:order)vtx(c[k][0],c[k][1],c[k][2],c[k][3],c[k][4],L,0,-1);
        batches.add(new int[]{e[0],first,6,2});
    }
    private void trimCaches(){
        if((frame&63)!=0)return;
        for(Iterator<Map.Entry<Sprites.Sprite,int[]>> it=spriteTex.entrySet().iterator();it.hasNext();){int[] e=it.next().getValue();if(frame-e[1]>240){GLES20.glDeleteTextures(1,e,0);it.remove();}}
        for(Iterator<Map.Entry<World.Splat,int[]>> it=splatTex.entrySet().iterator();it.hasNext();){int[] e=it.next().getValue();if(frame-e[1]>120){GLES20.glDeleteTextures(1,e,0);it.remove();}}
    }
    private FloatBuffer upload(){
        FloatBuffer b=vb.capacity()>=nv*12?vb:ByteBuffer.allocateDirect(Math.max(nv*12,4096)*4*2).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.clear();b.put(verts,0,nv*12);b.position(0);return b;
    }
    private void bindScene(int prog,FloatBuffer b){
        int stride=12*4;
        b.position(0);GLES20.glVertexAttribPointer(0,3,GLES20.GL_FLOAT,false,stride,b);GLES20.glEnableVertexAttribArray(0);
        b.position(3);GLES20.glVertexAttribPointer(1,2,GLES20.GL_FLOAT,false,stride,b);GLES20.glEnableVertexAttribArray(1);
        b.position(5);GLES20.glVertexAttribPointer(2,2,GLES20.GL_FLOAT,false,stride,b);GLES20.glEnableVertexAttribArray(2);
        b.position(7);GLES20.glVertexAttribPointer(3,4,GLES20.GL_FLOAT,false,stride,b);GLES20.glEnableVertexAttribArray(3);
        b.position(0);
    }
    private void camUniforms(int p){
        GLES20.glUniform3f(GLES20.glGetUniformLocation(p,"uCam"),cx,cy,eye);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(p,"uCS"),cs,sn);
        // clip y = (2*horizon/H-1)*z - y*2*focal/H  (image top at row 0 after read-back)
        float n=.06f,f=120f;
        GLES20.glUniform3f(GLES20.glGetUniformLocation(p,"uProj"),2*focal/H,2*horizon/H-1,(f+n)/(f-n));
    }
    private void atmosUniforms(int p){
        float fa=r.fogAmt;GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"uFog"),r.fogStart,1f/Math.max(.01f,r.fogEnd-r.fogStart),fa,H);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(p,"uFogC"),r.fogR/255f,r.fogG/255f,r.fogB/255f);
        float ta=r.tintAmt;GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"uTint"),1-ta+ta*r.tintR/255f,1-ta+ta*r.tintG/255f,1-ta+ta*r.tintB/255f,1);
    }
    private final float[] lp=new float[16*4],lc=new float[16*3];
    private void sceneUniforms(int p){
        camUniforms(p);atmosUniforms(p);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uTex"),0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uFade"),r.fadeK);GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uAlphaCut"),-1f);
        int n=Math.min(16,r.lightCount);
        for(int i=0;i<n;i++){lp[i*4]=r.lx[i];lp[i*4+1]=r.ly[i];lp[i*4+2]=r.lz[i];lp[i*4+3]=r.lr2[i];lc[i*3]=r.lcr[i];lc[i*3+1]=r.lcg[i];lc[i*3+2]=r.lcb[i];}
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uCount"),n);
        if(n>0){GLES20.glUniform4fv(GLES20.glGetUniformLocation(p,"uL"),n,lp,0);GLES20.glUniform3fv(GLES20.glGetUniformLocation(p,"uLC"),n,lc,0);}
        float[] hi=r.highlight>=0?idVec(r.highlight):new float[]{-1,-1,-1,-1};
        GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"uHi"),hi[0],hi[1],hi[2],r.highlight>=0?hi[3]:-1);
    }
    private void drawSky(){
        GLES20.glUseProgram(progSky);atmosUniforms(progSky);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(progSky,"uSize"),W,H);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(progSky,"uSky"),angle,focal,horizon,W);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progSky,"uTex"),0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,skyTex);
        quad(0,0,W,H,0,0,1,1);
    }
    private final FloatBuffer qb=ByteBuffer.allocateDirect(6*12*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Screen-space quad in internal pixels (top-left origin). */
    private void quad(float x0,float y0,float x1,float y1,float u0,float v0,float u1,float v1){
        float[] q={x0,y0,0,u0,v0,0,0,0,0,0,0,0, x1,y0,0,u1,v0,0,0,0,0,0,0,0, x1,y1,0,u1,v1,0,0,0,0,0,0,0, x0,y0,0,u0,v0,0,0,0,0,0,0,0, x1,y1,0,u1,v1,0,0,0,0,0,0,0, x0,y1,0,u0,v1,0,0,0,0,0,0,0};
        qb.clear();qb.put(q);qb.position(0);bindScene(0,qb);GLES20.glDisableVertexAttribArray(2);GLES20.glDisableVertexAttribArray(3);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6);
    }
    private float[] cverts=new float[0];
    private void drawColored(Level level){
        World fx=r.fx;Atmosphere.Particles ps=r.particles;
        int need=(fx==null?0:fx.drops.size())+(ps==null?0:ps.n);if(need==0)return;
        if(cverts.length<need*6*12)cverts=new float[need*6*12];int n=0;
        if(fx!=null)for(World.Drop d:fx.drops){float dx=d.x-cx,dy=d.y-cy,cz=dx*cs+dy*sn;if(cz<.12f)continue;
            int si=level.sectorAt(d.x,d.y);int L=si<0?24:Renderer.lightLevel(level.sectors.get(si).light);int li=Math.max(Math.max(1,L/4),Math.min(31,(int)(L+3-cz*r.fadeK)));
            int c=Textures.mul(d.color,Textures.lightMul(li));n=cquad(n,d.x,d.y,d.z,d.size*.5f,c,1f);}
        if(ps!=null)for(int i=0;i<ps.n;i++){float fade=Math.min(1,ps.life[i]/Math.max(.01f,ps.max[i])*2.2f);n=cquad(n,ps.x[i],ps.y[i],ps.z[i],Math.max(.012f,ps.size[i]*.5f),ps.col[i],fade*.8f);}
        if(n==0)return;
        FloatBuffer b=ByteBuffer.allocateDirect(n*12*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(cverts,0,n*12);b.position(0);
        GLES20.glUseProgram(progColor);camUniforms(progColor);atmosUniforms(progColor);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA,GLES20.GL_ZERO,GLES20.GL_ONE);GLES20.glDepthMask(false);
        bindScene(progColor,b);GLES20.glDisableVertexAttribArray(1);GLES20.glDisableVertexAttribArray(2);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,n);
        GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_BLEND);
    }
    private int cquad(int n,float x,float y,float z,float h,int col,float a){
        float rx=-sn*h,ry=cs*h,R=((col>>16)&255)/255f,G=((col>>8)&255)/255f,B=(col&255)/255f;
        float[][] p={{x-rx,y-ry,z+h},{x+rx,y+ry,z+h},{x+rx,y+ry,z-h},{x-rx,y-ry,z+h},{x+rx,y+ry,z-h},{x-rx,y-ry,z-h}};
        for(float[] q:p){int o=n*12;cverts[o]=q[0];cverts[o+1]=q[1];cverts[o+2]=q[2];for(int k=3;k<7;k++)cverts[o+k]=0;cverts[o+7]=R;cverts[o+8]=G;cverts[o+9]=B;cverts[o+10]=a;cverts[o+11]=0;n++;}
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
        bmp.copyPixelsFromBuffer(read);return bmp;
    }
    /** Pick id at an internal pixel, by drawing last frame's surfaces and sprites with their ids. */
    int pick(int px,int py){
        if(nv==0)return -1;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,idFbo);GLES20.glViewport(0,0,W,H);
        GLES20.glColorMask(true,true,true,true);GLES20.glClearColor(1,1,1,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthFunc(GLES20.GL_LEQUAL);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glColorMask(true,true,true,true);
        GLES20.glUseProgram(progId);camUniforms(progId);GLES20.glUniform1i(GLES20.glGetUniformLocation(progId,"uTex"),0);
        bindScene(progId,vb);
        for(int[] b:batches){if(b[3]==2)continue;GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[3]==3?wallTex[0]:b[0]);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,b[1],b[2]);}
        ByteBuffer one=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(px,py,1,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,one);
        int a=one.get(0)&255,b=one.get(1)&255,c=one.get(2)&255,d=one.get(3)&255;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glColorMask(true,true,true,false);
        if(a==255&&b==255&&c==255&&d==255)return -1;
        int id=(a<<24)|(b<<16)|(c<<8)|d;return id==0?-1:id;
    }
}
