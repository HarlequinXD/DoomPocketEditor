package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Handler;
/**
 * A full-window view on the desktop: drawn into the UI layer, fed touch events by the cursor. As on Android, a view
 * is redrawn only after it asks (invalidate and friends): the window keeps showing the last picture otherwise.
 */
public class View {
    /** Something asked to be redrawn since the last UI frame. */
    public static volatile boolean dirty=true;
    private final Context ctx;private int w,h;private final Handler handler=new Handler();
    public View(Context c){ctx=c;}
    public Context getContext(){return ctx;}
    public Resources getResources(){return ctx.getResources();}
    public final int getWidth(){return w;} public final int getHeight(){return h;}
    /** Desktop: the window's size. */
    public void layout(int nw,int nh){if(nw!=w||nh!=h){int ow=w,oh=h;w=nw;h=nh;onSizeChanged(nw,nh,ow,oh);dirty=true;}}
    protected void onSizeChanged(int w,int h,int ow,int oh){}
    protected void onDraw(Canvas c){}
    /** Desktop: draws the view. */
    public void draw(Canvas c){onDraw(c);}
    public boolean onTouchEvent(MotionEvent e){return false;}
    public boolean dispatchTouchEvent(MotionEvent e){dirty=true;return onTouchEvent(e);}
    public void invalidate(){dirty=true;} public void postInvalidate(){dirty=true;} public void postInvalidateOnAnimation(){dirty=true;}
    public void postInvalidateDelayed(long ms){handler.postDelayed(()->dirty=true,ms);}
    public boolean post(Runnable r){return handler.post(r);} public boolean postDelayed(Runnable r,long ms){return handler.postDelayed(r,ms);}
    public boolean removeCallbacks(Runnable r){return handler.remove(r);}
    protected void onDetachedFromWindow(){}
    /** Desktop: the view left the screen for good. */
    public void detach(){onDetachedFromWindow();}
    public void setKeepScreenOn(boolean b){}
    public void setFocusable(boolean f){} public void setFocusableInTouchMode(boolean f){} public boolean requestFocus(){return true;}
}
