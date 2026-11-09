package android.opengl;

/**
 * EGL on the desktop: there is one OpenGL context, the window's, current on the main thread for the whole run, so
 * the renderer's off-screen "pbuffer context" is that context and these calls only hand out tokens.
 */
public final class EGL14 {
    public static final int EGL_ALPHA_SIZE=0x3021,EGL_BLUE_SIZE=0x3022,EGL_GREEN_SIZE=0x3023,EGL_RED_SIZE=0x3024,EGL_HEIGHT=0x3056,EGL_WIDTH=0x3057,
            EGL_NONE=0x3038,EGL_RENDERABLE_TYPE=0x3040,EGL_SURFACE_TYPE=0x3033,EGL_PBUFFER_BIT=1,EGL_OPENGL_ES2_BIT=4,EGL_CONTEXT_CLIENT_VERSION=0x3098,EGL_DEFAULT_DISPLAY=0;
    public static final EGLDisplay EGL_NO_DISPLAY=new EGLDisplay();
    public static final EGLContext EGL_NO_CONTEXT=new EGLContext();
    public static final EGLSurface EGL_NO_SURFACE=new EGLSurface();
    private static final EGLDisplay DISPLAY=new EGLDisplay();
    /** Set by the desktop app once its window's context is up; without it the renderer falls back to software. */
    public static volatile boolean available;
    private EGL14(){}
    public static EGLDisplay eglGetDisplay(int id){return available?DISPLAY:EGL_NO_DISPLAY;}
    public static boolean eglInitialize(EGLDisplay d,int[] major,int mo,int[] minor,int mi){if(major!=null&&major.length>mo)major[mo]=1;if(minor!=null&&minor.length>mi)minor[mi]=4;return available&&d==DISPLAY;}
    public static boolean eglChooseConfig(EGLDisplay d,int[] attribs,int ao,EGLConfig[] configs,int co,int size,int[] num,int no){if(configs!=null&&configs.length>co)configs[co]=new EGLConfig();if(num!=null)num[no]=1;return available;}
    public static EGLContext eglCreateContext(EGLDisplay d,EGLConfig c,EGLContext share,int[] attribs,int o){return available?new EGLContext():EGL_NO_CONTEXT;}
    public static EGLSurface eglCreatePbufferSurface(EGLDisplay d,EGLConfig c,int[] attribs,int o){return available?new EGLSurface():EGL_NO_SURFACE;}
    public static boolean eglMakeCurrent(EGLDisplay d,EGLSurface draw,EGLSurface read,EGLContext c){return available;}
    public static boolean eglSwapBuffers(EGLDisplay d,EGLSurface s){return true;}
    public static boolean eglDestroyContext(EGLDisplay d,EGLContext c){return true;}
    public static boolean eglDestroySurface(EGLDisplay d,EGLSurface s){return true;}
    public static boolean eglTerminate(EGLDisplay d){return true;}
    public static int eglGetError(){return 0x3000;}
}
