package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.opengl.EGL14;
import android.os.Handler;
import android.view.View;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * RPG Pocket Editor on Windows (and Linux): the same project list, editors and game as the Android app, in a GLFW
 * window. Every screen is the app's own view; their 2D drawing goes through Java2D into a UI layer shown with OpenGL,
 * and the 3D renderer draws with the window's OpenGL context. Input: mouse and keyboard, and Xbox controllers (any
 * XInput / SDL-mapped pad), all routed by {@link Controls} to one app-wide cursor.
 *
 *   F11 toggles full screen.  A .rpgpocket file given on the command line is imported.
 *   --shots DIR  renders a tour of the app to DIR and exits (the CI smoke test).
 */
public final class DesktopMain implements Controls.Shell {
    private long window;private int fbW=1600,fbH=900;private float scaleX=1,scaleY=1;
    private Context ctx;private final Devices dev=new Devices();private Overlay layer;private Controls controls;
    /** The screens, bottom to top (the title or editor, then any editors opened from it). */
    private final ArrayList<Screen> stack=new ArrayList<>();
    private TitleView title;private EditorView editor;
    /** Display mode: 0 full screen (a borderless window over the whole monitor; the default), 1 exclusive full screen, 2 a window. */
    private int mode=2;private int winX,winY,winW,winH;private static final String[] MODES={"Full screen","Exclusive full screen","Window"};
    private double lastCX=Double.NaN,lastCY=Double.NaN;private boolean captured;
    private String importOnStart;

    private static final class Screen {
        final View view;final BooleanSupplier back;final Runnable pause,resume,exit;
        Screen(View v,BooleanSupplier b,Runnable p,Runnable r,Runnable e){view=v;back=b;pause=p;resume=r;exit=e;}
    }

    public static void main(String[] args){
        String shots=null,open=null;
        for(int i=0;i<args.length;i++){if(args[i].equals("--shots")&&i+1<args.length)shots=args[++i];else if(!args[i].startsWith("--"))open=args[i];}
        System.setProperty("java.awt.headless","true");
        DesktopMain m=new DesktopMain();m.importOnStart=open;m.run(shots);
    }

    // ---------------------------------------------------------------- window
    private void run(String shots){
        GLFWErrorCallback.createPrint(System.err).set();
        if(!glfwInit())throw new IllegalStateException("GLFW");
        // a compatibility context (the 3D renderer is written for OpenGL ES 2.0 and runs on desktop GL 2.1 and later)
        glfwWindowHint(GLFW_DEPTH_BITS,24);glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        long mon=glfwGetPrimaryMonitor();GLFWVidMode vm=mon==0?null:glfwGetVideoMode(mon);
        int ww=1600,wh=900;if(vm!=null&&shots==null){ww=Math.min(1600,(int)(vm.width()*.85f));wh=Math.min(ww*9/16,(int)(vm.height()*.85f));}
        window=glfwCreateWindow(ww,wh,"RPG Pocket Editor",0,0);
        if(window==0)throw new IllegalStateException("Couldn't open a window with OpenGL");
        if(vm!=null)glfwSetWindowPos(window,(vm.width()-ww)/2,(vm.height()-wh)/2);
        winX=vm!=null?(vm.width()-ww)/2:80;winY=vm!=null?(vm.height()-wh)/2:80;winW=ww;winH=wh;
        icon();
        glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(1);
        System.out.println("OpenGL: "+GL11.glGetString(GL11.GL_VERSION)+" · "+GL11.glGetString(GL11.GL_RENDERER));
        EGL14.available=true;
        updateSize();
        // the app's density: lay the phone UI out as on a ~400dp-tall landscape phone, by the monitor's size so it
        // stays the same in a window and full screen
        int refW=vm!=null&&shots==null?vm.width():fbW,refH=vm!=null&&shots==null?vm.height():fbH;
        float density=Math.max(1f,Math.round(Math.min(refH/420f,refW/880f)*4)/4f);
        boolean win=System.getenv("APPDATA")!=null;
        File home=shots!=null?new File(shots,"appdata"):new File(win?System.getenv("APPDATA"):System.getProperty("user.home"),win?"RPGPocketEditor":".rpgpocketeditor");
        ctx=new Context(home,density);DoomApp.home=home;
        Thread.setDefaultUncaughtExceptionHandler((t,e)->DoomApp.record(ctx,e));
        Renderer.loadPrefs(ctx);GameSettings.load(ctx);
        layer=new Overlay(ctx);controls=new Controls(dev,layer,this);controls.toggleFullscreen=this::toggleFullscreen;
        Platform.quit=this::askQuit;Platform.displayName=()->MODES[mode];
        Platform.display=()->layer.choose("Display",MODES,mode,k->{applyMode(k);ctx.getSharedPreferences("app",0).edit().putInt("display",mode).apply();},null);
        if(shots==null)applyMode(Math.max(0,Math.min(2,ctx.getSharedPreferences("app",0).getInt("display",0))));   // launches full screen
        dev.last=Devices.SRC_MOUSE;Controls.touchControls=false;
        callbacks();
        glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_HIDDEN);   // the app draws its own cursor
        uiInit();
        File crash=new File(home,"crash.txt");
        if(crash.exists()&&shots==null){layer.toast("The editor hit an error last time · details in "+crash.getPath());crash.renameTo(new File(home,"crash-last.txt"));}
        push(new LoadingView(ctx,()->{showTitle();if(importOnStart!=null){String f=importOnStart;importOnStart=null;importFile(new File(f));}}),()->true,null,null,null);
        glfwShowWindow(window);
        if(shots!=null){shots(shots);return;}
        long last=System.nanoTime();
        while(!glfwWindowShouldClose(window)){
            long now=System.nanoTime();float dt=Math.min(.05f,(now-last)/1e9f);last=now;
            try{frame(dt);}catch(Throwable t){DoomApp.record(ctx,t);layer.toast("Something went wrong: "+t.getClass().getSimpleName()+(t.getMessage()==null?"":" · "+t.getMessage()));View.dirty=true;}
        }
        saveAll();
        glfwDestroyWindow(window);glfwTerminate();System.exit(0);
    }
    private void saveAll(){
        try{if(editor!=null){editor.onPause();editor.save(false);}else if(Project.isOpen())Project.get(ctx).saveNow(ctx);}catch(Throwable t){DoomApp.record(ctx,t);}
        try{for(Screen s:stack)if(s.pause!=null)s.pause.run();}catch(Throwable t){DoomApp.record(ctx,t);}
    }
    private float drawnX=Float.NaN,drawnY=Float.NaN;private boolean drawnVisible;
    private void frame(float dt){
        glfwPollEvents();pollPad();updateSize();
        Handler.runPending();
        controls.frame(dt);
        Pointer ptr=layer.pointer;
        boolean redraw=View.dirty||layer.busy()||ptr.animating()||ptr.visible!=drawnVisible||ptr.visible&&(ptr.x!=drawnX||ptr.y!=drawnY);
        if(redraw){View.dirty=false;drawnX=ptr.x;drawnY=ptr.y;drawnVisible=ptr.visible;drawUi();}
        present();
        if(shotPath!=null){save(shotPath);shotPath=null;}
        glfwSwapBuffers(window);
    }
    private void updateSize(){
        try(MemoryStack s=MemoryStack.stackPush()){IntBuffer w=s.mallocInt(1),h=s.mallocInt(1),ww=s.mallocInt(1),wh=s.mallocInt(1);
            glfwGetFramebufferSize(window,w,h);glfwGetWindowSize(window,ww,wh);
            int nw=Math.max(1,w.get(0)),nh=Math.max(1,h.get(0));if(nw!=fbW||nh!=fbH)View.dirty=true;
            fbW=nw;fbH=nh;scaleX=fbW/(float)Math.max(1,ww.get(0));scaleY=fbH/(float)Math.max(1,wh.get(0));}
    }
    /** F11: full screen and back to the window. */
    private void toggleFullscreen(){applyMode(mode==2?0:2);ctx.getSharedPreferences("app",0).edit().putInt("display",mode).apply();}
    private void applyMode(int to){
        long mon=glfwGetPrimaryMonitor();GLFWVidMode vm=mon==0?null:glfwGetVideoMode(mon);if(vm==null||to==mode)return;
        if(mode==2)try(MemoryStack s=MemoryStack.stackPush()){IntBuffer a=s.mallocInt(1),b=s.mallocInt(1);glfwGetWindowPos(window,a,b);winX=a.get(0);winY=b.get(0);glfwGetWindowSize(window,a,b);winW=a.get(0);winH=b.get(0);}
        if(to==1){glfwSetWindowAttrib(window,GLFW_DECORATED,GLFW_TRUE);glfwSetWindowMonitor(window,mon,0,0,vm.width(),vm.height(),vm.refreshRate());}
        else if(to==0){glfwSetWindowMonitor(window,0,0,0,vm.width(),vm.height(),0);glfwSetWindowAttrib(window,GLFW_DECORATED,GLFW_FALSE);glfwSetWindowPos(window,0,0);glfwSetWindowSize(window,vm.width(),vm.height());}
        else{glfwSetWindowMonitor(window,0,winX,winY,winW,winH,0);glfwSetWindowAttrib(window,GLFW_DECORATED,GLFW_TRUE);}
        mode=to;glfwSwapInterval(1);updateSize();View.dirty=true;
    }
    private void icon(){
        try(java.io.InputStream in=DesktopMain.class.getResourceAsStream("/icon.png")){if(in==null)return;
            BufferedImage img=javax.imageio.ImageIO.read(in);int w=img.getWidth(),h=img.getHeight();ByteBuffer px=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=img.getRGB(x,y);px.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>>24));}px.flip();
            GLFWImage.Buffer imgs=GLFWImage.malloc(1);imgs.get(0).set(w,h,px);glfwSetWindowIcon(window,imgs);imgs.free();
        }catch(Throwable ignored){}
    }
    private void askQuit(){layer.confirm("Quit RPG Pocket Editor?","Your project is saved.","Quit",()->glfwSetWindowShouldClose(window,true),null);}

    // ---------------------------------------------------------------- input
    private void callbacks(){
        glfwSetKeyCallback(window,(w,key,sc,action,mods)->{
            if(action==GLFW_PRESS&&key==GLFW_KEY_V&&(mods&GLFW_MOD_CONTROL)!=0&&layer.busy()){String clip=glfwGetClipboardString(window);if(clip!=null)layer.typed(clip.replace('\n',' '));View.dirty=true;return;}
            if(action==GLFW_REPEAT)return;
            int k=mapKey(key);if(k>=0)dev.key(k,action==GLFW_PRESS);View.dirty=true;});
        glfwSetCharCallback(window,(w,cp)->{if(layer.busy()&&cp>=32&&cp<0x10000)dev.type((char)cp);});
        glfwSetCursorPosCallback(window,(w,x,y)->{
            if(captured){if(!Double.isNaN(lastCX))dev.mouseDelta((float)(x-lastCX),(float)(y-lastCY));lastCX=x;lastCY=y;}
            else{dev.mouseMove((float)x*scaleX,(float)y*scaleY);lastCX=x;lastCY=y;}});
        glfwSetMouseButtonCallback(window,(w,b,action,mods)->{boolean d=action==GLFW_PRESS;
            if(b==GLFW_MOUSE_BUTTON_LEFT)dev.mouseL=d;else if(b==GLFW_MOUSE_BUTTON_RIGHT)dev.mouseR=d;else if(b==GLFW_MOUSE_BUTTON_MIDDLE)dev.mouseM=d;
            if(!captured&&!Double.isNaN(lastCX))dev.mouseMove((float)lastCX*scaleX,(float)lastCY*scaleY);else dev.last=Devices.SRC_MOUSE;});
        glfwSetScrollCallback(window,(w,x,y)->dev.scroll((float)y));
        glfwSetWindowCloseCallback(window,w->saveAll());
        glfwSetWindowRefreshCallback(window,w->View.dirty=true);
        glfwSetDropCallback(window,(w,count,names)->{for(int i=0;i<count;i++){String f=GLFWDropCallback.getName(names,i);if(f.toLowerCase().endsWith(".rpgpocket"))new Handler().post(()->importFile(new File(f)));}});
    }
    private static int mapKey(int key){
        switch(key){case GLFW_KEY_W:return Devices.K_W;case GLFW_KEY_A:return Devices.K_A;case GLFW_KEY_S:return Devices.K_S;case GLFW_KEY_D:return Devices.K_D;
            case GLFW_KEY_SPACE:return Devices.K_SPACE;case GLFW_KEY_LEFT_SHIFT:case GLFW_KEY_RIGHT_SHIFT:return Devices.K_SHIFT;case GLFW_KEY_ESCAPE:return Devices.K_ESC;
            case GLFW_KEY_ENTER:case GLFW_KEY_KP_ENTER:return Devices.K_ENTER;case GLFW_KEY_BACKSPACE:return Devices.K_BACKSPACE;case GLFW_KEY_TAB:return Devices.K_TAB;
            case GLFW_KEY_E:return Devices.K_E;case GLFW_KEY_F:return Devices.K_F;case GLFW_KEY_Q:return Devices.K_Q;case GLFW_KEY_R:return Devices.K_R;
            case GLFW_KEY_UP:return Devices.K_UP;case GLFW_KEY_DOWN:return Devices.K_DOWN;case GLFW_KEY_LEFT:return Devices.K_LEFT;case GLFW_KEY_RIGHT:return Devices.K_RIGHT;
            case GLFW_KEY_LEFT_CONTROL:case GLFW_KEY_RIGHT_CONTROL:return Devices.K_CTRL;case GLFW_KEY_Z:return Devices.K_Z;case GLFW_KEY_Y:return Devices.K_Y;
            case GLFW_KEY_F11:return Devices.K_F11;case GLFW_KEY_DELETE:return Devices.K_DELETE;case GLFW_KEY_P:return Devices.K_P;case GLFW_KEY_H:case GLFW_KEY_HOME:return Devices.K_H;
            case GLFW_KEY_LEFT_BRACKET:return Devices.K_LBR;case GLFW_KEY_RIGHT_BRACKET:return Devices.K_RBR;case GLFW_KEY_PAGE_UP:case GLFW_KEY_EQUAL:case GLFW_KEY_KP_ADD:return Devices.K_PGUP;
            case GLFW_KEY_PAGE_DOWN:case GLFW_KEY_MINUS:case GLFW_KEY_KP_SUBTRACT:return Devices.K_PGDN;case GLFW_KEY_C:return Devices.K_C;
            case GLFW_KEY_I:return Devices.K_I;case GLFW_KEY_X:return Devices.K_X;case GLFW_KEY_L:return Devices.K_L;case GLFW_KEY_K:return Devices.K_K;case GLFW_KEY_G:return Devices.K_G;default:return -1;}
    }
    private final GLFWGamepadState pad=GLFWGamepadState.create();
    /** The first connected gamepad (Xbox controllers via XInput, others via SDL's mapping database). */
    private void pollPad(){
        int found=-1;for(int j=GLFW_JOYSTICK_1;j<=GLFW_JOYSTICK_LAST;j++)if(glfwJoystickIsGamepad(j)){found=j;break;}
        dev.padConnected=found>=0;if(found<0){dev.buttons=0;dev.lx=dev.ly=dev.rx=dev.ry=dev.lt=dev.rt=0;return;}
        if(!glfwGetGamepadState(found,pad))return;
        dev.lx=pad.axes(GLFW_GAMEPAD_AXIS_LEFT_X);dev.ly=pad.axes(GLFW_GAMEPAD_AXIS_LEFT_Y);dev.rx=pad.axes(GLFW_GAMEPAD_AXIS_RIGHT_X);dev.ry=pad.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y);
        dev.lt=(pad.axes(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER)+1)*.5f;dev.rt=(pad.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)+1)*.5f;
        int[][] map={{GLFW_GAMEPAD_BUTTON_A,Devices.A},{GLFW_GAMEPAD_BUTTON_B,Devices.B},{GLFW_GAMEPAD_BUTTON_X,Devices.X},{GLFW_GAMEPAD_BUTTON_Y,Devices.Y},
            {GLFW_GAMEPAD_BUTTON_LEFT_BUMPER,Devices.LB},{GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER,Devices.RB},{GLFW_GAMEPAD_BUTTON_BACK,Devices.BACK},{GLFW_GAMEPAD_BUTTON_START,Devices.START},
            {GLFW_GAMEPAD_BUTTON_LEFT_THUMB,Devices.LS},{GLFW_GAMEPAD_BUTTON_RIGHT_THUMB,Devices.RS},{GLFW_GAMEPAD_BUTTON_DPAD_UP,Devices.UP},{GLFW_GAMEPAD_BUTTON_DPAD_DOWN,Devices.DOWN},
            {GLFW_GAMEPAD_BUTTON_DPAD_LEFT,Devices.LEFT},{GLFW_GAMEPAD_BUTTON_DPAD_RIGHT,Devices.RIGHT}};
        int b=0;for(int[] m:map)if(pad.buttons(m[0])==GLFW_PRESS)b|=m[1];
        if(b!=dev.buttons)View.dirty=true;dev.buttons=b;dev.padActivity();
    }

    // ---------------------------------------------------------------- the UI layer (Java2D, shown with OpenGL)
    private BufferedImage ui;private int uiTex,uiProg,uiVbo;private IntBuffer uiBuf;private boolean uploaded;
    private void uiInit(){
        String vs="#version 120\nattribute vec2 aPos;varying vec2 vUv;void main(){vUv=vec2(aPos.x*.5+.5,.5-aPos.y*.5);gl_Position=vec4(aPos,0.0,1.0);}\n";
        String fs="#version 120\nvarying vec2 vUv;uniform sampler2D uT;void main(){gl_FragColor=texture2D(uT,vUv);}\n";
        int v=shader(GL20.GL_VERTEX_SHADER,vs),f=shader(GL20.GL_FRAGMENT_SHADER,fs);uiProg=GL20.glCreateProgram();
        GL20.glAttachShader(uiProg,v);GL20.glAttachShader(uiProg,f);GL20.glBindAttribLocation(uiProg,0,"aPos");GL20.glLinkProgram(uiProg);
        if(GL20.glGetProgrami(uiProg,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException("UI shader: "+GL20.glGetProgramInfoLog(uiProg));
        uiVbo=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,uiVbo);
        FloatBuffer q=org.lwjgl.BufferUtils.createFloatBuffer(8);q.put(new float[]{-1,-1,1,-1,-1,1,1,1}).flip();GL15.glBufferData(GL15.GL_ARRAY_BUFFER,q,GL15.GL_STATIC_DRAW);
        uiTex=GL11.glGenTextures();
    }
    private static int shader(int type,String src){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,src);GL20.glCompileShader(s);
        if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException("UI shader: "+GL20.glGetShaderInfoLog(s));return s;}
    private void drawUi(){
        if(ui==null||ui.getWidth()!=fbW||ui.getHeight()!=fbH){ui=new BufferedImage(fbW,fbH,BufferedImage.TYPE_INT_ARGB_PRE);uiBuf=org.lwjgl.BufferUtils.createIntBuffer(fbW*fbH);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,uiTex);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,fbW,fbH,0,GL12.GL_BGRA,GL12.GL_UNSIGNED_INT_8_8_8_8_REV,(IntBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);}
        int[] px=((DataBufferInt)ui.getRaster().getDataBuffer()).getData();java.util.Arrays.fill(px,0xff15181c);
        Graphics2D g=ui.createGraphics();Canvas c=new Canvas(g);
        // the top screen covers the window; the ones under it are paused and not drawn
        View top=stack.isEmpty()?null:stack.get(stack.size()-1).view;
        if(top!=null){top.layout(fbW,fbH);int s=c.save();top.draw(c);c.restoreToCount(s);}
        layer.layout(fbW,fbH);int s=c.save();layer.draw(c);c.restoreToCount(s);g.dispose();
        uiBuf.clear();uiBuf.put(px).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,uiTex);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,4);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,fbW,fbH,GL12.GL_BGRA,GL12.GL_UNSIGNED_INT_8_8_8_8_REV,uiBuf);
        uploaded=true;
    }
    /** Shows the UI layer in the window (the 3D renderer draws into its own framebuffer during the UI pass). */
    private void present(){
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL11.glViewport(0,0,fbW,fbH);
        GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
        GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        if(!uploaded)return;
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL20.glUseProgram(uiProg);GL20.glUniform1i(GL20.glGetUniformLocation(uiProg,"uT"),0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,uiTex);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,uiVbo);GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,0,0L);GL20.glEnableVertexAttribArray(0);
        for(int i=1;i<8;i++)GL20.glDisableVertexAttribArray(i);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP,0,4);
        GL20.glDisableVertexAttribArray(0);GL20.glUseProgram(0);
    }

    // ---------------------------------------------------------------- screens (as the Android activities)
    private Screen top(){return stack.isEmpty()?null:stack.get(stack.size()-1);}
    private void push(View v,BooleanSupplier back,Runnable pause,Runnable resume,Runnable exit){
        Screen t=top();if(t!=null&&t.pause!=null)t.pause.run();
        layer.pointer.cancel();stack.add(new Screen(v,back,pause,resume,exit));View.dirty=true;
    }
    private void pop(){
        if(stack.isEmpty())return;Screen s=stack.remove(stack.size()-1);layer.pointer.cancel();
        if(s.pause!=null)s.pause.run();s.view.detach();
        Screen t=top();if(t!=null&&t.resume!=null)t.resume.run();View.dirty=true;
    }
    private void clearScreens(){while(!stack.isEmpty()){Screen s=stack.remove(stack.size()-1);if(s.pause!=null)s.pause.run();s.view.detach();}layer.pointer.cancel();View.dirty=true;}
    private Project project(){return Project.get(ctx);}

    void showTitle(){
        if(editor!=null){editor.onPause();editor.save(false);}
        clearScreens();editor=null;
        title=new TitleView(ctx);title.host=new Host();
        title.listener=new TitleView.Listener(){
            @Override public void openProject(String id){DesktopMain.this.openProject(id);}
            @Override public void playProject(String id){DesktopMain.this.playProject(id);}
            @Override public void exportProject(String id,boolean share){DesktopMain.this.exportProject(id,share);}
            @Override public void importProject(){pickImport();}
        };
        TitleView t=title;
        push(t,()->{askQuit();return true;},null,t::refresh,null);
    }
    private boolean open(String id){
        try{Project p=Project.isOpen()?Project.get(ctx):null;if(p==null||!id.equals(p.id))Project.open(ctx,id);return true;}
        catch(Exception e){layer.toast("Could not open that project");if(title!=null)title.refresh();return false;}
    }
    private void openProject(String id){if(open(id))startEditor();}
    private void playProject(String id){if(open(id)){startEditor();editor.startPlayerMode();}}
    private void startEditor(){
        clearScreens();title=null;
        EditorView e=new EditorView(ctx);editor=e;e.host=new Host();e.onExitToTitle=this::showTitle;
        push(e,()->{if(e.goBack())return true;e.save(false);showTitle();return true;},e::onPause,e::onResume,null);
    }
    private static String fileName(String name){String n=name.replaceAll("[^A-Za-z0-9 _-]","").trim();return (n.isEmpty()?"game":n)+".rpgpocket";}
    private void exportProject(String id,boolean share){
        String name="game";for(ProjectStore.Meta m:ProjectStore.list(ctx))if(m.id.equals(id))name=m.name;
        String path=saveDialog(share?"Save a game file to share":"Save the game file",fileName(name),new String[]{"*.rpgpocket"},"RPG Pocket Editor games");
        if(path==null)return;if(!path.toLowerCase().endsWith(".rpgpocket"))path+=".rpgpocket";
        try(java.io.Writer w=new java.io.OutputStreamWriter(new java.io.FileOutputStream(path),"UTF-8")){w.write(ProjectStore.exportPackage(ctx,id));layer.toast(share?"Saved · send "+new File(path).getName()+" to share it":"Game file saved");}
        catch(Exception e){layer.toast("Couldn't save the game file");}
    }
    private void pickImport(){String path=openDialog("Import a game file",new String[]{"*.rpgpocket"},"RPG Pocket Editor games");if(path!=null)importFile(new File(path));}
    private void importFile(File f){
        try{
            if(f.length()>80_000_000)throw new java.io.IOException("File too large");
            String id=ProjectStore.importPackage(ctx,new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            String name=id;for(ProjectStore.Meta m:ProjectStore.list(ctx))if(m.id.equals(id))name=m.name;
            if(title!=null)title.refresh();
            layer.choose("Imported “"+name+"”",new String[]{"Play it now","Open it in the editor","Later"},0,k->{if(k==0)playProject(id);else if(k==1)openProject(id);},null);
        }catch(Throwable t){layer.toast("Couldn't import that file: "+(t.getMessage()==null?t.getClass().getSimpleName():t.getMessage()));}
    }

    /** One host for every editor screen: dialogs through the overlay, other editors pushed on top. */
    private final class Host implements FSMGraphView.Host,SpriteEditorView.Host,ContentEditorView.Host,DialogueEditorView.Host,SoundEditorView.Host,UiEditorView.Host {
        Runnable after=()->View.dirty=true;String savedMsg="Project saved";View view;
        @Override public void prompt(String t,String hint,String initial,boolean numeric,java.util.function.Consumer<String> done){layer.prompt(t,hint,initial,numeric,done::accept,after);}
        @Override public void choose(String t,String[] items,int checked,java.util.function.IntConsumer done){layer.choose(t,items,checked,done,after);}
        @Override public void confirm(String t,String message,String ok,Runnable yes){layer.confirm(t,message,ok,yes,after);}
        @Override public void openFsm(String defId){
            if(view instanceof FSMGraphView){EntityDef d=project().def(defId);if(d!=null)((FSMGraphView)view).selectDef(d);return;}
            Project p=project();Host h=new Host();FSMGraphView v=new FSMGraphView(ctx,p,defId,h);h.view=v;h.after=v::afterEdit;
            push(v,()->{if(v.browserOpen){v.browserOpen=false;return true;}if(v.linkFrom!=null){v.linkFrom=null;return true;}if(v.selectedAction>=0){v.selectedAction=-1;return true;}return false;},
                ()->{v.stopRun();p.save(ctx);},v::invalidate,h::exit);
        }
        @Override public void openSprites(String defId){
            Project p=project();Host h=new Host();SpriteEditorView v=new SpriteEditorView(ctx,p,defId,h);h.view=v;h.after=v::invalidate;h.savedMsg="Sprite saved to project";
            push(v,()->{if(v.pickerOpen){v.pickerOpen=false;return true;}return false;},()->p.save(ctx),null,h::exit);
        }
        @Override public void openSound(int tab){
            Project p=project();Host h=new Host();SoundEditorView v=new SoundEditorView(ctx,p,tab,h);h.view=v;h.after=v::afterEdit;h.savedMsg="Sounds and music saved to project";
            push(v,v::back,()->{v.onPause();p.saveNow(ctx);},v::onResume,h::exit);v.onResume();
        }
        @Override public void openUi(){
            Project p=project();Host h=new Host();UiEditorView v=new UiEditorView(ctx,p,h);h.view=v;h.after=v::afterEdit;h.savedMsg="Layout saved to project";
            push(v,v::back,()->p.saveNow(ctx),v::invalidate,h::exit);
        }
        @Override public void openContent(int tab){
            Project p=project();Host h=new Host();ContentEditorView v=new ContentEditorView(ctx,p,tab,h);h.view=v;h.after=v::afterEdit;h.savedMsg="Content saved to project";
            push(v,()->false,()->p.saveNow(ctx),v::invalidate,h::exit);
        }
        @Override public void openDialogue(int tab){
            Project p=project();Host h=new Host();DialogueEditorView v=new DialogueEditorView(ctx,p,tab,h);h.view=v;h.after=v::afterEdit;h.savedMsg="Dialogue saved to project";
            push(v,()->false,()->p.saveNow(ctx),v::invalidate,h::exit);
        }
        @Override public void exit(){project().save(ctx);Screen t=top();if(t!=null&&t.view==view)pop();}
        @Override public void saved(){project().save(ctx);layer.toast(savedMsg);}
        // Sound editor files
        @Override public void importAudio(){
            String path=openDialog("Import a sound",AudioDecode.PATTERNS,"Sound files (WAV, AIFF, AU)");if(path==null)return;
            try{AudioIO.Result r=AudioDecode.load(new File(path));((SoundEditorView)view).imported(r);project().save(ctx);}
            catch(Throwable t){layer.toast("Couldn't load that file: "+(t.getMessage()==null?t.getClass().getSimpleName():t.getMessage()));}
        }
        @Override public void exportWav(short[] pcm,String name){
            if(pcm==null||pcm.length<2)return;
            String path=saveDialog("Save as WAV",name.replaceAll("[^A-Za-z0-9 _-]","").trim()+".wav",new String[]{"*.wav"},"WAV sound");if(path==null)return;
            if(!path.toLowerCase().endsWith(".wav"))path+=".wav";
            try(java.io.OutputStream o=new java.io.FileOutputStream(path)){AudioIO.writeWav(o,pcm);layer.toast("WAV saved");}catch(Exception e){layer.toast("Couldn't save the WAV file");}
        }
        // UI editor pictures
        @Override public void pickImage(java.util.function.Consumer<android.graphics.Bitmap> done){
            String path=openDialog("Choose a picture",new String[]{"*.png","*.jpg","*.jpeg","*.bmp","*.gif"},"Pictures");if(path==null)return;
            try{BufferedImage img=javax.imageio.ImageIO.read(new File(path));if(img==null)throw new java.io.IOException();
                int side=Math.max(img.getWidth(),img.getHeight()),k=1;while(side/k>2048)k*=2;
                BufferedImage argb=new BufferedImage(img.getWidth()/k,img.getHeight()/k,BufferedImage.TYPE_INT_ARGB);Graphics2D g=argb.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(img,0,0,argb.getWidth(),argb.getHeight(),null);g.dispose();
                done.accept(new android.graphics.Bitmap(argb));if(view instanceof UiEditorView)((UiEditorView)view).afterEdit();}
            catch(Exception e){layer.toast("That picture couldn't be read");}
        }
    }

    // ---------------------------------------------------------------- native file dialogs
    /** Exclusive full screen hides other windows, so file dialogs show over a borderless full screen for a moment. */
    private int dialogMode(){int was=mode;if(mode==1)applyMode(0);return was;}
    private String openDialog(String titleText,String[] patterns,String desc){
        int was=dialogMode();
        try(MemoryStack s=MemoryStack.stackPush()){PointerBuffer f=s.mallocPointer(patterns.length);for(String p:patterns)f.put(s.UTF8(p));f.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(titleText,null,f,desc,false);}
        catch(Throwable t){layer.toast("File dialogs aren't available here");return null;}
        finally{if(was!=mode)applyMode(was);dev.mouseL=false;View.dirty=true;}
    }
    private String saveDialog(String titleText,String name,String[] patterns,String desc){
        int was=dialogMode();
        try(MemoryStack s=MemoryStack.stackPush()){PointerBuffer f=s.mallocPointer(patterns.length);for(String p:patterns)f.put(s.UTF8(p));f.flip();
            String dir=System.getProperty("user.home");File docs=new File(dir,"Documents");
            return TinyFileDialogs.tinyfd_saveFileDialog(titleText,new File(docs.isDirectory()?docs:new File(dir),name).getPath(),f,desc);}
        catch(Throwable t){layer.toast("File dialogs aren't available here");return null;}
        finally{if(was!=mode)applyMode(was);dev.mouseL=false;View.dirty=true;}
    }

    // ---------------------------------------------------------------- Controls.Shell
    @Override public View screen(){Screen t=top();return t==null?layer:t.view;}
    @Override public EditorView playing(){Screen t=top();return editor!=null&&t!=null&&t.view==editor&&editor.deviceGame()?editor:null;}
    @Override public void back(){
        if(layer.back())return;
        Screen t=top();if(t==null){askQuit();return;}
        if(t.back!=null&&t.back.getAsBoolean())return;
        if(t.exit!=null)t.exit.run();
        View.dirty=true;
    }
    @Override public void captureMouse(boolean on){
        captured=on;lastCX=Double.NaN;
        glfwSetInputMode(window,GLFW_CURSOR,on?GLFW_CURSOR_DISABLED:GLFW_CURSOR_HIDDEN);
        if(on&&glfwRawMouseMotionSupported())glfwSetInputMode(window,GLFW_RAW_MOUSE_MOTION,GLFW_TRUE);
    }

    // ---------------------------------------------------------------- CI smoke test: a tour of the app as PNGs
    private void run(int frames){for(int i=0;i<frames;i++)frame(1/60f);}
    private void click(float x,float y){dev.mouseMove(x,y);run(2);dev.mouseL=true;run(2);dev.mouseL=false;run(4);}
    private void shots(String dir){
        new File(dir).mkdirs();
        try{
            for(int i=0;i<1200&&title==null;i++)frame(1/60f);   // the loading screen
            dev.mouseMove(fbW*.2f,fbH*.62f);run(20);shot(dir+"/01-title.png");
            layer.choose("Display",MODES,0,k->{},null);run(12);shot(dir+"/02-dialog-choose.png");layer.back();run(2);
            Project p=ProjectStore.create(ctx,"Smoke Test",0);Project.set(p);openProject(p.id);run(30);shot(dir+"/03-editor-home.png");
            editor.openEditorAt(0);dev.mouseMove(fbW*.4f,fbH*.45f);run(30);shot(dir+"/04-sectors.png");
            dev.key(Devices.K_RIGHT,true);run(2);dev.key(Devices.K_RIGHT,false);run(10);
            dev.scroll(1);run(10);shot(dir+"/05-sectors-zoomed.png");
            editor.openEditorAt(1);run(20);shot(dir+"/06-entities.png");
            editor.openEditorAt(2);for(int i=0;i<180;i++)frame(1/60f);shot(dir+"/07-3d-view.png");
            dev.mouseR=true;dev.key(Devices.K_W,true);run(40);shot(dir+"/08-3d-look.png");dev.key(Devices.K_W,false);dev.mouseR=false;run(10);
            click(fbW*.6f,fbH*.55f);run(10);shot(dir+"/09-3d-selected.png");
            editor.openEditorAt(3);for(int i=0;i<300;i++)frame(1/60f);shot(dir+"/10-play.png");
            editor.startPlayNow();run(60);
            dev.key(Devices.K_W,true);run(90);dev.key(Devices.K_W,false);shot(dir+"/11-playing.png");
            dev.key(Devices.K_TAB,true);run(3);dev.key(Devices.K_TAB,false);run(20);shot(dir+"/12-bag.png");
            back();run(10);dev.key(Devices.K_ESC,true);run(2);dev.key(Devices.K_ESC,false);run(20);shot(dir+"/13-paused.png");
            editor.exitPlay();run(10);
            String[] names={"14-fsm","15-sprites","16-sound","17-ui-editor","18-content","19-dialogue"};
            for(int i=0;i<names.length;i++){editor.openEditorAt(4+i);run(30);dev.mouseMove(fbW*.5f,fbH*.4f);run(10);shot(dir+"/"+names[i]+".png");back();run(10);}
            ((Host)editor.host).prompt("Rename the map","A short name for this map","Dungeon of Doom",false,s->{});run(15);shot(dir+"/20-dialog-prompt.png");layer.back();
            showTitle();run(20);shot(dir+"/21-title-again.png");
            System.out.println("shots saved to "+dir);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
        saveAll();glfwDestroyWindow(window);glfwTerminate();System.exit(0);
    }
    private String shotPath;
    private void shot(String path){View.dirty=true;shotPath=path;frame(1/60f);}
    private void save(String path){
        ByteBuffer b=org.lwjgl.BufferUtils.createByteBuffer(fbW*fbH*4);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);
        GL11.glReadPixels(0,0,fbW,fbH,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
        BufferedImage img=new BufferedImage(fbW,fbH,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<fbH;y++)for(int x=0;x<fbW;x++){int i=((fbH-1-y)*fbW+x)*4;img.setRGB(x,y,((b.get(i)&255)<<16)|((b.get(i+1)&255)<<8)|(b.get(i+2)&255));}
        try{javax.imageio.ImageIO.write(img,"png",new File(path));}catch(Exception e){throw new RuntimeException(e);}
    }
}
