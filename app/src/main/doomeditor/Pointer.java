package com.retro.doomeditor;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

/**
 * The app-wide cursor: one on-screen pointer for the project list, every editor and the game's menus, driven by a
 * mouse or an Xbox controller's left stick (A clicks, holding A drags), so the whole app works without a touch screen.
 *
 * It presses buttons by sending the screen under it ordinary touch events, so every screen works with it as-is.
 * With a controller it behaves like a console cursor: a response curve on the stick, speed that builds while held,
 * friction over anything clickable so it is easy to stop on a button, and the d-pad jumping straight to the next
 * button in that direction. What it is over is framed by animated corner brackets.
 *
 * The look matches the app's emblem: a gilded arrow (the d20's gold over a dark bronze edge) with an arcane violet
 * glow that brightens over a target, a squash when pressed and a ring on each click.
 */
final class Pointer {
    /** Screens that know about the cursor (all optional). */
    interface Aware {
        /** Its hit targets (for brackets, friction and d-pad jumps). */
        Ui pointerUi();
        /** The cursor moved over it (the map editor moves its map cursor with this). */
        default void pointerHover(float x,float y){}
        /** Right stick / mouse drag with the right or middle button: pan (dx,dy in pixels). */
        default void pointerPan(float dx,float dy){}
        /** Wheel / bumpers: zoom in (+) or out (-) about (x,y). Returns false to scroll the list under it instead. */
        default boolean pointerZoom(float steps,float x,float y){return false;}
        /** The wheel may scroll lists here by dragging them (false where a drag would paint or move things). */
        default boolean wheelDrags(){return true;}
        /** The d-pad: true if the screen used it (the map editor steps its map cursor), else the cursor jumps. */
        default boolean pointerDpad(int dx,int dy){return false;}
        /** The sticks drive a camera instead of the cursor (the 3D editor view). */
        default boolean pointerCamera(){return false;}
        /** Ctrl + a letter (z undo, y redo, s save), or a 3D view key. */
        default void pointerShortcut(char key){}
    }
    /** True while the cursor is delivering a click, so a screen can tell it from a finger. */
    static boolean dispatching;

    float x=Float.NaN,y=Float.NaN;boolean visible;boolean down;
    private float vx,vy,hold,pressK,hoverK,glowT;private long downTime;private View pressed;
    private final RectF bracket=new RectF();private boolean hasBracket;private RectF target;
    private float jumpX,jumpY,jumpT=1;
    private final float[] rings={0,0,1,0,0,1,0,0,1,0,0,1};   // x, y, age for up to four click rings
    final float dp;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path arrow=new Path(),inner=new Path();

    Pointer(float dp){this.dp=dp;}

    static float smooth(float a,float b,float x){float t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    static float approach(float v,float to,float rate,float dt){return v+(to-v)*Math.min(1,rate*dt);}
    static float mix(float a,float b,float k){return a+(b-a)*k;}

    void place(float nx,float ny){x=nx;y=ny;jumpT=1;}
    /** Glide to (nx,ny) (a d-pad jump). */
    void jumpTo(float nx,float ny){jumpX=nx;jumpY=ny;jumpT=0;}

    /**
     * Moves with the stick (sx,sy in -1..1, already curved) for dt seconds on a w x h screen. Friction applies over a
     * target; speed builds the longer the stick is held over.
     */
    void stick(float sx,float sy,float dt,float w,float h){
        float l=(float)Math.hypot(sx,sy);
        if(l<1e-3f){hold=0;vx*=.5f;vy*=.5f;return;}
        hold+=dt;jumpT=1;
        float speed=(float)Math.hypot(w,h)*.62f*(1+.7f*smooth(.35f,1.1f,hold));
        if(target!=null)speed*=hold<.5f?.42f:.7f;   // sticky over buttons
        float tx=sx*speed,ty=sy*speed;vx+=(tx-vx)*Math.min(1,dt*18);vy+=(ty-vy)*Math.min(1,dt*18);
        x=Math.max(0,Math.min(w-1,x+vx*dt));y=Math.max(0,Math.min(h-1,y+vy*dt));
    }

    private static boolean small(RectF r,float dp){return r.width()<dp*700&&r.height()<dp*300;}
    /** Per frame: glides, hover target and its brackets, animation timers. */
    void update(float dt,Aware screen){
        glowT+=dt;
        if(jumpT<1){jumpT=Math.min(1,jumpT+dt/.12f);float k=smooth(0,1,jumpT);x+=(jumpX-x)*k;y+=(jumpY-y)*k;}
        target=null;
        if(visible&&screen!=null&&screen.pointerUi()!=null){Ui ui=screen.pointerUi();
            for(int i=ui.hits.size()-1;i>=0;i--){RectF r=ui.hits.get(i).r;if(r.contains(x,y)){if(small(r,dp))target=r;break;}}}
        hoverK=approach(hoverK,target!=null?1:0,14,dt);pressK=approach(pressK,down?1:0,30,dt);
        if(target!=null){float k=hasBracket?Math.min(1,dt*22):1;float g=dp*3;
            bracket.set(mix(bracket.left,target.left-g,k),mix(bracket.top,target.top-g,k),mix(bracket.right,target.right+g,k),mix(bracket.bottom,target.bottom+g,k));hasBracket=true;}
        else if(hoverK<.02f)hasBracket=false;
        for(int i=0;i<4;i++)if(rings[i*3+2]<1)rings[i*3+2]+=dt/.35f;
        Ui.hoverX=visible?x:Float.NaN;Ui.hoverY=visible?y:Float.NaN;
    }
    boolean overTarget(){return target!=null;}
    boolean animating(){if(jumpT<1||Math.abs(hoverK-(target!=null?1:0))>.01f||Math.abs(pressK-(down?1:0))>.01f)return true;for(int i=0;i<4;i++)if(rings[i*3+2]<1)return true;return hasBracket;}

    // ---------------------------------------------------------------- clicks (as touch events)
    void press(View v){
        if(v==null||Float.isNaN(x))return;down=true;downTime=SystemClock.uptimeMillis();pressed=v;send(v,MotionEvent.ACTION_DOWN);
        for(int i=0;i<4;i++)if(rings[i*3+2]>=1){rings[i*3]=x;rings[i*3+1]=y;rings[i*3+2]=0;break;}
    }
    void drag(){if(down&&pressed!=null)send(pressed,MotionEvent.ACTION_MOVE);}
    void release(){if(!down)return;down=false;if(pressed!=null)send(pressed,MotionEvent.ACTION_UP);pressed=null;}
    /** Lifts a held click without clicking (the screen changed under it). */
    void cancel(){if(down&&pressed!=null)send(pressed,MotionEvent.ACTION_CANCEL);down=false;pressed=null;}
    boolean pressing(View v){return down&&pressed==v;}
    private void send(View v,int action){send(v,action,x,y,downTime);}
    /** One synthetic touch event at (sx,sy). */
    static void send(View v,int action,float sx,float sy,long downTime){
        MotionEvent e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,sx,sy,0);e.setSource(InputDevice.SOURCE_MOUSE);
        dispatching=true;try{v.dispatchTouchEvent(e);}finally{dispatching=false;e.recycle();}
    }
    /** The mouse wheel over a list: a quick drag of the list under the cursor (down-move-up, too far to count as a tap). */
    void wheelDrag(View v,float steps){
        if(v==null||Float.isNaN(x)||down)return;long t=SystemClock.uptimeMillis();float dy=steps*dp*56;
        send(v,MotionEvent.ACTION_DOWN,x,y,t);
        for(int i=1;i<=4;i++)send(v,MotionEvent.ACTION_MOVE,x,y+dy*i/4f,t);
        send(v,MotionEvent.ACTION_UP,x,y+dy,t);
    }

    /** The d-pad: the nearest target whose centre lies in that direction (favouring ones straight ahead). */
    boolean dpadJump(int dx,int dy,Ui ui){
        if(ui==null)return false;float best=Float.MAX_VALUE;RectF pick=null;
        for(Ui.Hit h:ui.hits){RectF r=h.r;if(!small(r,dp)||r.contains(x,y))continue;
            float ex=r.centerX()-x,ey=r.centerY()-y;float along=ex*dx+ey*dy;if(along<dp*4)continue;
            float perp=Math.abs(ex*dy-ey*dx);if(perp>along*2.2f)continue;
            float score=along+perp*2.5f;if(score<best){best=score;pick=r;}}
        if(pick==null)return false;jumpTo(pick.centerX(),pick.centerY());return true;
    }

    // ---------------------------------------------------------------- drawing
    void draw(Canvas c){
        if(!visible||Float.isNaN(x))return;
        // the target's corner brackets: gold, with a violet glow
        if(hasBracket&&hoverK>.02f){
            float pulse=.75f+.25f*(float)Math.sin(glowT*6);int a=(int)(225*hoverK*pulse);
            float L=Math.min(dp*11,Math.min(bracket.width(),bracket.height())*.35f),o=dp*2*(1-hoverK);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*2.2f);p.setStrokeCap(Paint.Cap.SQUARE);
            p.setShadowLayer(dp*6,0,0,(a<<24)|0x9b6dff);p.setColor((a<<24)|0xf6d478);
            float l=bracket.left-o,t=bracket.top-o,r=bracket.right+o,b=bracket.bottom+o;
            c.drawLine(l,t,l+L,t,p);c.drawLine(l,t,l,t+L,p);c.drawLine(r,t,r-L,t,p);c.drawLine(r,t,r,t+L,p);
            c.drawLine(l,b,l+L,b,p);c.drawLine(l,b,l,b-L,p);c.drawLine(r,b,r-L,b,p);c.drawLine(r,b,r,b-L,p);
            p.setShadowLayer(0,0,0,0);p.setStrokeCap(Paint.Cap.BUTT);}
        // click rings
        p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<4;i++){float t=rings[i*3+2];if(t>=1)continue;p.setStrokeWidth(dp*(3-2*t));p.setColor(((int)(210*(1-t))<<24)|0xe8b84a);c.drawCircle(rings[i*3],rings[i*3+1],dp*(5+22*t),p);}
        // the gilded arrow
        float s=dp*(1+.1f*hoverK-.12f*pressK);
        arrow.reset();
        arrow.moveTo(x,y);arrow.lineTo(x,y+20.5f*s);arrow.lineTo(x+5f*s,y+15.8f*s);arrow.lineTo(x+8.6f*s,y+23.6f*s);
        arrow.lineTo(x+12.2f*s,y+22f*s);arrow.lineTo(x+8.7f*s,y+14.4f*s);arrow.lineTo(x+15.2f*s,y+14.4f*s);arrow.close();
        float glow=.5f+.5f*hoverK+.1f*(float)Math.sin(glowT*3.2f);
        p.setStyle(Paint.Style.FILL);p.setShadowLayer(dp*(4+5*hoverK),0,dp,(((int)(190*glow))<<24)|0x7a4ad8);p.setColor(0xff3a1e06);c.drawPath(arrow,p);p.setShadowLayer(0,0,0,0);
        p.setShader(new LinearGradient(x,y,x+9*s,y+22*s,new int[]{0xfffff4c8,0xfff6d478,0xffe0a840,0xff8a5a14},new float[]{0,.3f,.65f,1},Shader.TileMode.CLAMP));
        c.save();c.translate(x,y);c.scale(.8f,.8f);c.translate(-x+1.5f*s,-y+3.6f*s);c.drawPath(arrow,p);c.restore();p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,dp*1.1f));p.setStrokeJoin(Paint.Join.MITER);p.setColor(0xff4a2606);c.drawPath(arrow,p);
        // a bright facet down the leading edge, like the die's top face
        inner.reset();inner.moveTo(x+1.4f*s,y+3.6f*s);inner.lineTo(x+1.4f*s,y+15f*s);
        p.setStrokeWidth(Math.max(1,dp*.8f));p.setColor((((int)(160+90*hoverK))<<24)|0xfff8e0);c.drawPath(inner,p);
        p.setStyle(Paint.Style.FILL);
    }
}
