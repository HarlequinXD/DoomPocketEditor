package com.retro.doomeditor;

import android.view.View;

/**
 * Routes the controller, keyboard and mouse once a frame (PC build):
 *
 *  Playing   left stick / WASD move, right stick / mouse look, RT / left button attack, LT / right button cast,
 *            A / Space jump, X / E use, Y / R potion, RB / Q next spell, LB / Shift run, View / Tab / I the bag,
 *            Menu / Esc pause. The touch buttons hide while a controller or keyboard is in use.
 *  Menus     the global cursor: left stick or mouse moves it, A / left button clicks (hold to drag), the d-pad
 *            jumps between buttons, B / Esc goes back, right stick / wheel scrolls.
 *  Map       as menus, plus: hovering the map moves the map cursor, A / click applies, the d-pad / arrows step the
 *            map cursor, right stick / right- or middle-drag pans, bumpers / wheel zoom.
 *  3D view   hold RT / the right mouse button to look: the cursor hides, the left stick / WASD move, the right stick /
 *            mouse look, LB RB / Q E (Space C) go down and up in fly mode, A / left click picks the surface under the
 *            crosshair. Let go and it's the cursor again: click a surface to select it (double-click applies the
 *            texture), the d-pad / arrows raise and lower it and change texture, X / Enter applies, Y / F fly.
 */
final class Controls {
    /** What the platform's app tells the router. */
    interface Shell {
        /** The screen under the overlay that receives the cursor's clicks. */
        View screen();
        /** The editor while its game has the input (playing, not in a menu), else null. */
        EditorView playing();
        /** Esc / B. */
        void back();
        /** Hide the mouse and take relative motion (mouse look), or give it back. */
        void captureMouse(boolean on);
    }
    /** The touch buttons show (false once a controller, keyboard or mouse is used). */
    static volatile boolean touchControls=true;
    /** The game's hints name controller buttons (else keyboard and mouse). */
    static volatile boolean padHint;

    final Devices dev;final Overlay overlay;final Shell shell;
    private int prevButtons,prevArrows,prevShort,prevKeys3d,prevGameKeys;private boolean prevL,prevR,prevM,prevEsc,prevEnter,prevBack,prevF11,wasLook,captured;
    private float lastMX=Float.NaN,lastMY=Float.NaN,lastPX=Float.NaN,lastPY=Float.NaN,bsRepeat;
    Runnable toggleFullscreen;
    private final boolean[] held=new boolean[Input.COUNT];

    Controls(Devices d,Overlay o,Shell s){dev=d;overlay=o;shell=s;}

    void frame(float dt){
        int btn=dev.buttons,pressedB=btn&~prevButtons;prevButtons=btn;
        boolean[] k=dev.keys;
        boolean esc=k[Devices.K_ESC],escP=esc&&!prevEsc;prevEsc=esc;
        boolean ent=k[Devices.K_ENTER],entP=ent&&!prevEnter;prevEnter=ent;
        boolean bs=k[Devices.K_BACKSPACE],bsP=bs&&!prevBack;prevBack=bs;
        if(bs){bsRepeat+=dt;if(bsRepeat>.45f){bsRepeat-=.05f;bsP=true;}}else bsRepeat=0;
        boolean f11=k[Devices.K_F11];if(f11&&!prevF11&&toggleFullscreen!=null)toggleFullscreen.run();prevF11=f11;
        int src=dev.last;
        touchControls=src==Devices.SRC_TOUCH;if(src==Devices.SRC_PAD)padHint=true;else if(src!=Devices.SRC_TOUCH)padHint=false;
        Pointer ptr=overlay.pointer;
        EditorView game=overlay.busy()?null:shell.playing();
        if(game!=null){game(game,dt,pressedB,escP);ptr.visible=false;ptr.cancel();return;}
        View screen=overlay.busy()?overlay:shell.screen();
        Pointer.Aware aw=screen instanceof Pointer.Aware?(Pointer.Aware)screen:null;
        float w=overlay.getWidth(),h=overlay.getHeight();
        if(Float.isNaN(ptr.x))ptr.place(w*.5f,h*.5f);
        ptr.visible=src==Devices.SRC_MOUSE||src==Devices.SRC_PAD||src==Devices.SRC_KEYS;
        // keyboard text into dialogs
        String typed=dev.takeTyped();if(overlay.busy()){overlay.typed(typed);if(bsP)overlay.backspace();if(entP){overlay.enter();entP=false;}}
        // back
        if(escP||(pressedB&Devices.B)!=0){ptr.cancel();if(!overlay.back())shell.back();}
        boolean cam3d=aw!=null&&!overlay.busy()&&aw.pointerCamera();EditorView v3=cam3d?(EditorView)screen:null;
        // the 3D view's look mode: right trigger or right mouse button held. The cursor hides; the sticks or WASD and
        // the mouse move and look. Released, it's the cursor again.
        boolean lookPad=cam3d&&dev.padConnected&&dev.rt>.5f,lookMouse=cam3d&&dev.mouseR;
        boolean look=lookPad||lookMouse;
        if(lookMouse&&!captured){shell.captureMouse(true);captured=true;dev.takeMouseDelta();}
        if(!lookMouse&&captured){shell.captureMouse(false);captured=false;lastMX=lastMY=Float.NaN;}
        if(v3!=null){if(look!=wasLook){v3.setLooking(look);if(!look)v3.padCamera(0,0,0,0,0,dt);}wasLook=look;}else wasLook=false;
        if(look){ptr.visible=false;ptr.cancel();
            float mx=0,my=0,lx=0,ly=0,ud=0;
            if(lookPad){float[] ls=Devices.stick(dev.lx,dev.ly,.16f,1.9f),rs=Devices.stick(dev.rx,dev.ry,.16f,1.6f);mx=ls[0];my=-ls[1];lx=rs[0];ly=rs[1];
                ud=((btn&Devices.RB)!=0?1:0)-((btn&Devices.LB)!=0?1:0);
                if((pressedB&Devices.A)!=0)v3.pickCentre();if((pressedB&Devices.X)!=0)v3.pointerShortcut('\n');}
            mx+=(k[Devices.K_D]?1:0)-(k[Devices.K_A]?1:0);my+=(k[Devices.K_W]?1:0)-(k[Devices.K_S]?1:0);
            ud+=(k[Devices.K_E]||k[Devices.K_SPACE]?1:0)-(k[Devices.K_Q]||k[Devices.K_C]?1:0);
            float ml=(float)Math.hypot(mx,my);if(ml>1){mx/=ml;my/=ml;}if(k[Devices.K_SHIFT]){mx*=1.8f;my*=1.8f;}
            v3.padCamera(mx,my,lx,ly,Math.max(-1,Math.min(1,ud)),dt);
            if(lookMouse){float[] md=dev.takeMouseDelta();if(md[0]!=0||md[1]!=0)v3.mouseLook(md[0],md[1]);
                if(dev.mouseL&&!prevL)v3.pickCentre();}
            prevL=dev.mouseL;prevR=dev.mouseR;prevM=dev.mouseM;dev.takeWheel();prevArrows=0;
            return;}
        boolean ctrl=k[Devices.K_CTRL];int sc=(ctrl&&k[Devices.K_Z]?1:0)|(ctrl&&k[Devices.K_Y]?2:0)|(ctrl&&k[Devices.K_S]?4:0);
        if(aw!=null&&!overlay.busy()){int p=sc&~prevShort;if((p&1)!=0)aw.pointerShortcut('z');if((p&2)!=0)aw.pointerShortcut('y');if((p&4)!=0)aw.pointerShortcut('s');}prevShort=sc;
        // the 3D view's keys and buttons in cursor mode
        if(v3!=null){
            int ks=(k[Devices.K_F]?1:0)|(k[Devices.K_P]?2:0)|(k[Devices.K_H]?4:0)|(k[Devices.K_LBR]?8:0)|(k[Devices.K_RBR]?16:0)|(k[Devices.K_PGUP]?32:0)|(k[Devices.K_PGDN]?64:0)|(k[Devices.K_ENTER]?128:0)|(k[Devices.K_L]?256:0)|(k[Devices.K_K]?512:0);
            int kp=ctrl?0:ks&~prevKeys3d;prevKeys3d=ks;
            char[] map={'f','p','h','[',']','+','-','\n','l','k'};for(int i=0;i<map.length;i++)if((kp&(1<<i))!=0)v3.pointerShortcut(map[i]);
            if(dev.padConnected){if((pressedB&Devices.X)!=0)v3.pointerShortcut('\n');if((pressedB&Devices.Y)!=0)v3.pointerShortcut('f');
                if((pressedB&Devices.LB)!=0)v3.pointerShortcut('[');if((pressedB&Devices.RB)!=0)v3.pointerShortcut(']');}
            entP=false;}   // Enter applies here instead of clicking
        // ---------------------------------------------------------------- mouse
        float mx=dev.mouseX,my=dev.mouseY;
        if(src==Devices.SRC_MOUSE&&!Float.isNaN(mx)){
            if(mx!=lastMX||my!=lastMY){float ddx=Float.isNaN(lastMX)?0:mx-lastMX,ddy=Float.isNaN(lastMY)?0:my-lastMY;
                if((dev.mouseR||dev.mouseM)&&aw!=null&&!cam3d&&!ptr.down)aw.pointerPan(-ddx,-ddy);
                ptr.place(mx,my);
                if(ptr.down)ptr.drag();}
            if(dev.mouseL&&!prevL)ptr.press(screen);if(!dev.mouseL&&prevL)ptr.release();
            float wh=dev.takeWheel();if(wh!=0)wheel(screen,aw,wh,ptr);
        }
        lastMX=mx;lastMY=my;prevL=dev.mouseL;prevR=dev.mouseR;prevM=dev.mouseM;
        // ---------------------------------------------------------------- controller
        if(dev.padConnected&&src==Devices.SRC_PAD){
            float[] ls=Devices.stick(dev.lx,dev.ly,.16f,1.9f),rs=Devices.stick(dev.rx,dev.ry,.16f,1.6f);
            ptr.stick(ls[0],ls[1],dt,w,h);if(ptr.down)ptr.drag();
            if(rs[0]!=0||rs[1]!=0){
                if(aw!=null&&!cam3d&&(aw instanceof EditorView||aw instanceof Overlay))aw.pointerPan(rs[0]*dt*900*ptr.dp/2.6f,rs[1]*dt*900*ptr.dp/2.6f);
                else{padScroll+=-rs[1]*dt*6;if(Math.abs(padScroll)>=1){float st=(int)padScroll;padScroll-=st;wheel(screen,aw,st,ptr);}}}
            else padScroll=0;
            if(aw!=null&&!cam3d){if((pressedB&Devices.RB)!=0)aw.pointerZoom(1,ptr.x,ptr.y);if((pressedB&Devices.LB)!=0)aw.pointerZoom(-1,ptr.x,ptr.y);}
            if((pressedB&Devices.A)!=0)ptr.press(screen);if((btn&Devices.A)==0&&ptr.down)ptr.release();
            if((pressedB&Devices.START)!=0&&!overlay.back())shell.back();
        }
        // ---------------------------------------------------------------- d-pad / arrow keys
        int ddx=0,ddy=0;
        int arrows=(k[Devices.K_LEFT]?Devices.LEFT:0)|(k[Devices.K_RIGHT]?Devices.RIGHT:0)|(k[Devices.K_UP]?Devices.UP:0)|(k[Devices.K_DOWN]?Devices.DOWN:0)|(k[Devices.K_TAB]&&!overlay.busy()?Devices.RIGHT:0);
        int dirs=(btn|arrows)&(Devices.LEFT|Devices.RIGHT|Devices.UP|Devices.DOWN);
        int newDirs=(pressedB|(arrows&~prevArrows))&dirs;prevArrows=arrows;
        // held directions repeat (map cursor steps, list walking)
        if(dirs!=0&&dirs==heldDirs){repeatT+=dt;if(repeatT>.32f){repeatT-=.07f;newDirs|=dirs;}}else{repeatT=0;}heldDirs=dirs;
        if((newDirs&Devices.LEFT)!=0)ddx=-1;else if((newDirs&Devices.RIGHT)!=0)ddx=1;else if((newDirs&Devices.UP)!=0)ddy=-1;else if((newDirs&Devices.DOWN)!=0)ddy=1;
        if(ddx!=0||ddy!=0){if(aw==null||overlay.busy()||!aw.pointerDpad(ddx,ddy))ptr.dpadJump(ddx,ddy,aw!=null?aw.pointerUi():null);}
        if(src==Devices.SRC_KEYS&&entP&&!overlay.busy()){ptr.press(screen);ptr.release();}
        ptr.update(dt,aw);
        boolean moved=ptr.x!=lastPX||ptr.y!=lastPY;lastPX=ptr.x;lastPY=ptr.y;
        if(aw!=null&&ptr.visible&&!overlay.busy()&&moved&&!ptr.down)aw.pointerHover(ptr.x,ptr.y);
    }
    private float padScroll,repeatT;private int heldDirs;
    /** The wheel: zoom where the screen zooms, else scroll the list under the cursor. */
    private void wheel(View screen,Pointer.Aware aw,float steps,Pointer ptr){
        if(aw!=null&&aw.pointerZoom(steps,ptr.x,ptr.y))return;
        if(aw==null||aw.wheelDrags())ptr.wheelDrag(screen,steps);
    }

    /** In play: the pad and keyboard/mouse drive the hero alongside the touch controls. */
    private void game(EditorView ev,float dt,int pressedB,boolean escP){
        boolean[] k=dev.keys;
        boolean kb=dev.last==Devices.SRC_KEYS||dev.last==Devices.SRC_MOUSE;
        // mouse look: capture the mouse while playing with it
        if(kb&&!captured){shell.captureMouse(true);captured=true;dev.takeMouseDelta();}
        if(!kb&&captured){shell.captureMouse(false);captured=false;}
        java.util.Arrays.fill(held,false);
        float mvx=0,mvy=0,lookX=0,lookY=0;int b=dev.buttons;
        if(dev.padConnected){
            float[] ls=Devices.stick(dev.lx,dev.ly,.18f,1.4f),rs=Devices.stick(dev.rx,dev.ry,.14f,2.1f);
            mvx+=ls[0];mvy+=ls[1];
            lookX+=rs[0]*dt*480;lookY+=rs[1]*dt*330;
            held[Input.FIRE]|=dev.rt>.35f;held[Input.SPELL]|=dev.lt>.35f;held[Input.JUMP]|=(b&Devices.A)!=0;held[Input.USE]|=(b&Devices.X)!=0;
            held[Input.POTION]|=(b&Devices.Y)!=0;held[Input.WEAPON]|=(b&Devices.RB)!=0;held[Input.ALT]|=(b&(Devices.LB|Devices.LS))!=0;held[Input.MENU]|=(b&Devices.BACK)!=0;
            if((pressedB&Devices.START)!=0){release(ev);ev.devicePause();return;}
        }
        mvx+=(k[Devices.K_D]?1:0)-(k[Devices.K_A]?1:0);mvy+=(k[Devices.K_S]?1:0)-(k[Devices.K_W]?1:0);
        if(captured){float[] md=dev.takeMouseDelta();lookX+=md[0]*.55f;lookY+=md[1]*.55f;held[Input.FIRE]|=dev.mouseL;held[Input.SPELL]|=dev.mouseR;}
        held[Input.JUMP]|=k[Devices.K_SPACE];held[Input.USE]|=k[Devices.K_E]||k[Devices.K_F];held[Input.POTION]|=k[Devices.K_R];held[Input.WEAPON]|=k[Devices.K_Q];
        held[Input.ALT]|=k[Devices.K_SHIFT];held[Input.MENU]|=k[Devices.K_TAB]||k[Devices.K_I];
        dev.takeWheel();
        if(escP){release(ev);ev.devicePause();return;}
        float ml=(float)Math.hypot(mvx,mvy);if(ml>1){mvx/=ml;mvy/=ml;}
        boolean any=dev.last!=Devices.SRC_TOUCH;
        if(any)ev.devicePlay(mvx,mvy,lookX,lookY,held);
    }
    private void release(EditorView ev){java.util.Arrays.fill(held,false);ev.devicePlay(0,0,0,0,held);if(captured){shell.captureMouse(false);captured=false;}}
}
