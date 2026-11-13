package android.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

/**
 * OpenGL ES 2.0 on desktop OpenGL (a compatibility context, 2.1 or later): the same calls, through LWJGL. Shaders
 * written for GLSL ES 1.00 are compiled as GLSL 1.20 (precision qualifiers removed), which accepts them unchanged
 * otherwise.
 */
public final class GLES20 {
    private GLES20(){}
    public static final int GL_ARRAY_BUFFER=0x8892,GL_BLEND=0x0BE2,GL_CLAMP_TO_EDGE=0x812F,GL_COLOR_ATTACHMENT0=0x8CE0,GL_COLOR_BUFFER_BIT=0x4000,
            GL_COMPILE_STATUS=0x8B81,GL_CULL_FACE=0x0B44,GL_DEPTH_ATTACHMENT=0x8D00,GL_DEPTH_BUFFER_BIT=0x0100,GL_DEPTH_COMPONENT16=0x81A5,
            GL_DEPTH_TEST=0x0B71,GL_DST_COLOR=0x0306,GL_EXTENSIONS=0x1F03,GL_FLOAT=0x1406,GL_FRAGMENT_SHADER=0x8B30,GL_FRAMEBUFFER=0x8D40,
            GL_FRAMEBUFFER_COMPLETE=0x8CD5,GL_LEQUAL=0x0203,GL_LESS=0x0201,GL_LINK_STATUS=0x8B82,GL_NEAREST=0x2600,GL_LINEAR=0x2601,GL_NEAREST_MIPMAP_NEAREST=0x2700,
            GL_LINEAR_MIPMAP_LINEAR=0x2703,GL_ONE=1,GL_ONE_MINUS_SRC_ALPHA=0x0303,GL_POLYGON_OFFSET_FILL=0x8037,GL_RENDERBUFFER=0x8D41,GL_REPEAT=0x2901,
            GL_RGBA=0x1908,GL_RGB=0x1907,GL_SRC_ALPHA=0x0302,GL_STREAM_DRAW=0x88E0,GL_STATIC_DRAW=0x88E4,GL_DYNAMIC_DRAW=0x88E8,GL_TEXTURE0=0x84C0,GL_TEXTURE1=0x84C1,
            GL_TEXTURE2=0x84C2,GL_TEXTURE3=0x84C3,GL_TEXTURE4=0x84C4,GL_TEXTURE5=0x84C5,GL_TEXTURE_2D=0x0DE1,GL_TEXTURE_MAG_FILTER=0x2800,GL_TEXTURE_MIN_FILTER=0x2801,GL_TEXTURE_WRAP_S=0x2802,
            GL_TEXTURE_WRAP_T=0x2803,GL_TRIANGLES=4,GL_TRIANGLE_STRIP=5,GL_UNSIGNED_BYTE=0x1401,GL_VERTEX_SHADER=0x8B31,GL_ZERO=0,GL_BACK=0x0405,GL_FRONT=0x0404,
            GL_ALWAYS=0x0207,GL_RENDERER=0x1F01,GL_VERSION=0x1F02,GL_VENDOR=0x1F00,GL_NO_ERROR=0;

    // ---------------------------------------------------------------- shaders
    /** GLSL ES 1.00 → desktop GLSL 1.20. */
    static String translate(String src){
        String s=src.replaceAll("(?m)^\\s*#version[^\\n]*\\n","");
        s=s.replaceAll("precision\\s+(lowp|mediump|highp)\\s+\\w+\\s*;","");
        s=s.replaceAll("\\b(lowp|mediump|highp)\\s+","");
        return "#version 120\n"+s;
    }
    public static int glCreateShader(int type){return GL20C.glCreateShader(type);}
    public static void glShaderSource(int s,String src){GL20C.glShaderSource(s,translate(src));}
    public static void glCompileShader(int s){GL20C.glCompileShader(s);}
    public static void glGetShaderiv(int s,int pname,int[] p,int o){p[o]=GL20C.glGetShaderi(s,pname);}
    public static String glGetShaderInfoLog(int s){return GL20C.glGetShaderInfoLog(s);}
    public static void glDeleteShader(int s){GL20C.glDeleteShader(s);}
    public static int glCreateProgram(){return GL20C.glCreateProgram();}
    public static void glAttachShader(int p,int s){GL20C.glAttachShader(p,s);}
    public static void glBindAttribLocation(int p,int i,String n){GL20C.glBindAttribLocation(p,i,n);}
    public static void glLinkProgram(int p){GL20C.glLinkProgram(p);}
    public static void glGetProgramiv(int p,int pname,int[] v,int o){v[o]=GL20C.glGetProgrami(p,pname);}
    public static String glGetProgramInfoLog(int p){return GL20C.glGetProgramInfoLog(p);}
    public static void glUseProgram(int p){GL20C.glUseProgram(p);}
    public static void glDeleteProgram(int p){GL20C.glDeleteProgram(p);}
    public static int glGetUniformLocation(int p,String n){return GL20C.glGetUniformLocation(p,n);}
    public static int glGetAttribLocation(int p,String n){return GL20C.glGetAttribLocation(p,n);}
    public static void glUniform1f(int l,float a){GL20C.glUniform1f(l,a);}
    public static void glUniform2f(int l,float a,float b){GL20C.glUniform2f(l,a,b);}
    public static void glUniform3f(int l,float a,float b,float c){GL20C.glUniform3f(l,a,b,c);}
    public static void glUniform4f(int l,float a,float b,float c,float d){GL20C.glUniform4f(l,a,b,c,d);}
    public static void glUniform1i(int l,int a){GL20C.glUniform1i(l,a);}
    private static float[] sub(float[] v,int o,int n){if(o==0&&v.length==n)return v;float[] r=new float[n];System.arraycopy(v,o,r,0,n);return r;}
    public static void glUniform1fv(int l,int count,float[] v,int o){if(count>0)GL20C.glUniform1fv(l,sub(v,o,count));}
    public static void glUniform2fv(int l,int count,float[] v,int o){if(count>0)GL20C.glUniform2fv(l,sub(v,o,count*2));}
    public static void glUniform3fv(int l,int count,float[] v,int o){if(count>0)GL20C.glUniform3fv(l,sub(v,o,count*3));}
    public static void glUniform4fv(int l,int count,float[] v,int o){if(count>0)GL20C.glUniform4fv(l,sub(v,o,count*4));}
    public static void glUniformMatrix4fv(int l,int count,boolean t,float[] v,int o){if(count>0)GL20C.glUniformMatrix4fv(l,t,sub(v,o,count*16));}

    // ---------------------------------------------------------------- buffers and vertex arrays
    public static void glGenBuffers(int n,int[] b,int o){for(int i=0;i<n;i++)b[o+i]=GL15C.glGenBuffers();}
    public static void glDeleteBuffers(int n,int[] b,int o){for(int i=0;i<n;i++)GL15C.glDeleteBuffers(b[o+i]);}
    public static void glBindBuffer(int t,int b){GL15C.glBindBuffer(t,b);}
    public static void glBufferData(int t,int size,Buffer data,int usage){
        if(data==null){GL15C.nglBufferData(t,size,0L,usage);return;}
        if(!data.isDirect()){   // a heap buffer (Android copies these too): through a direct copy of `size` bytes
            ByteBuffer tmp=MemoryUtil.memCalloc(Math.max(1,size));
            try{if(data instanceof FloatBuffer){FloatBuffer f=((FloatBuffer)data).duplicate();FloatBuffer d=tmp.asFloatBuffer();int n=Math.min(f.remaining(),size/4);for(int i=0;i<n;i++)d.put(i,f.get(f.position()+i));}
                else if(data instanceof ByteBuffer){ByteBuffer f=((ByteBuffer)data).duplicate();int n=Math.min(f.remaining(),size);for(int i=0;i<n;i++)tmp.put(i,f.get(f.position()+i));}
                GL15C.nglBufferData(t,size,MemoryUtil.memAddress(tmp),usage);}
            finally{MemoryUtil.memFree(tmp);}
            return;}
        GL15C.nglBufferData(t,size,MemoryUtil.memAddress(data),usage);   // memAddress: from the buffer's position, as on Android
    }
    public static void glBufferSubData(int t,int off,int size,Buffer data){GL15C.nglBufferSubData(t,off,size,MemoryUtil.memAddress(data));}
    public static void glVertexAttribPointer(int i,int size,int type,boolean norm,int stride,int offset){GL20C.glVertexAttribPointer(i,size,type,norm,stride,offset);}
    public static void glEnableVertexAttribArray(int i){GL20C.glEnableVertexAttribArray(i);}
    public static void glDisableVertexAttribArray(int i){GL20C.glDisableVertexAttribArray(i);}
    public static void glDrawArrays(int mode,int first,int count){GL11C.glDrawArrays(mode,first,count);}

    // ---------------------------------------------------------------- textures
    public static void glGenTextures(int n,int[] t,int o){for(int i=0;i<n;i++)t[o+i]=GL11C.glGenTextures();}
    public static void glDeleteTextures(int n,int[] t,int o){for(int i=0;i<n;i++)GL11C.glDeleteTextures(t[o+i]);}
    public static void glBindTexture(int t,int id){GL11C.glBindTexture(t,id);}
    public static void glActiveTexture(int t){GL13C.glActiveTexture(t);}
    public static void glTexParameteri(int t,int p,int v){GL11C.glTexParameteri(t,p,v);}
    public static void glTexParameterf(int t,int p,float v){GL11C.glTexParameterf(t,p,v);}
    private static ByteBuffer bytes(Buffer b){
        if(b==null)return null;if(b instanceof ByteBuffer)return (ByteBuffer)b;
        return MemoryUtil.memByteBuffer(MemoryUtil.memAddress(b),b.remaining()*(b instanceof IntBuffer||b instanceof FloatBuffer?4:2));
    }
    public static void glTexImage2D(int t,int level,int ifmt,int w,int h,int border,int fmt,int type,Buffer px){
        int internal=ifmt==GL_RGBA?0x8058/*GL_RGBA8*/:ifmt==GL_RGB?0x8051/*GL_RGB8*/:ifmt;
        GL11C.glTexImage2D(t,level,internal,w,h,border,fmt,type,bytes(px));
    }
    public static void glTexSubImage2D(int t,int level,int x,int y,int w,int h,int fmt,int type,Buffer px){GL11C.glTexSubImage2D(t,level,x,y,w,h,fmt,type,bytes(px));}
    public static void glGenerateMipmap(int t){GL30C.glGenerateMipmap(t);}
    public static void glPixelStorei(int p,int v){GL11C.glPixelStorei(p,v);}

    // ---------------------------------------------------------------- framebuffers
    /** The app's own default framebuffer (0 on the desktop: the window). */
    public static void glGenFramebuffers(int n,int[] f,int o){for(int i=0;i<n;i++)f[o+i]=GL30C.glGenFramebuffers();}
    public static void glDeleteFramebuffers(int n,int[] f,int o){for(int i=0;i<n;i++)GL30C.glDeleteFramebuffers(f[o+i]);}
    public static void glBindFramebuffer(int t,int f){GL30C.glBindFramebuffer(t,f);}
    public static void glFramebufferTexture2D(int t,int a,int tt,int tex,int level){GL30C.glFramebufferTexture2D(t,a,tt,tex,level);}
    public static void glFramebufferRenderbuffer(int t,int a,int rt,int rb){GL30C.glFramebufferRenderbuffer(t,a,rt,rb);}
    public static int glCheckFramebufferStatus(int t){return GL30C.glCheckFramebufferStatus(t);}
    public static void glGenRenderbuffers(int n,int[] r,int o){for(int i=0;i<n;i++)r[o+i]=GL30C.glGenRenderbuffers();}
    public static void glDeleteRenderbuffers(int n,int[] r,int o){for(int i=0;i<n;i++)GL30C.glDeleteRenderbuffers(r[o+i]);}
    public static void glBindRenderbuffer(int t,int r){GL30C.glBindRenderbuffer(t,r);}
    public static void glRenderbufferStorage(int t,int fmt,int w,int h){GL30C.glRenderbufferStorage(t,fmt,w,h);}
    public static void glReadPixels(int x,int y,int w,int h,int fmt,int type,Buffer px){GL11C.glReadPixels(x,y,w,h,fmt,type,bytes(px));}

    // ---------------------------------------------------------------- state
    public static void glViewport(int x,int y,int w,int h){GL11C.glViewport(x,y,w,h);}
    public static void glScissor(int x,int y,int w,int h){GL11C.glScissor(x,y,w,h);}
    public static void glClearColor(float r,float g,float b,float a){GL11C.glClearColor(r,g,b,a);}
    public static void glClearDepthf(float d){GL11C.glClearDepth(d);}
    public static void glClear(int m){GL11C.glClear(m);}
    public static void glEnable(int c){GL11C.glEnable(c);}
    public static void glDisable(int c){GL11C.glDisable(c);}
    public static void glBlendFunc(int s,int d){GL11C.glBlendFunc(s,d);}
    public static void glBlendFuncSeparate(int sc,int dc,int sa,int da){GL14C.glBlendFuncSeparate(sc,dc,sa,da);}
    public static void glDepthFunc(int f){GL11C.glDepthFunc(f);}
    public static void glDepthMask(boolean m){GL11C.glDepthMask(m);}
    public static void glColorMask(boolean r,boolean g,boolean b,boolean a){GL11C.glColorMask(r,g,b,a);}
    public static void glCullFace(int m){GL11C.glCullFace(m);}
    public static void glPolygonOffset(float f,float u){GL11C.glPolygonOffset(f,u);}
    public static void glLineWidth(float w){GL11C.glLineWidth(w);}
    public static void glFlush(){GL11C.glFlush();}
    public static void glFinish(){GL11C.glFinish();}
    public static int glGetError(){return GL11C.glGetError();}
    /** GL_EXTENSIONS lists GL_OES_depth24, which every desktop driver has as plain 24-bit depth. */
    public static String glGetString(int name){
        if(name==GL_EXTENSIONS){String e;try{e=GL11.glGetString(GL_EXTENSIONS);}catch(Throwable t){e=null;}return (e==null?"":e)+" GL_OES_depth24";}
        return GL11C.glGetString(name);
    }
}
