package android.os;
import java.util.*;
/** Runs posted work on the desktop's main loop (everything that touches the UI runs on that one thread). */
public class Handler {
    static final class Task{final Runnable r;final long at;Task(Runnable r,long at){this.r=r;this.at=at;}}
    private static final List<Task> queue=new ArrayList<>();
    public Handler(){} public Handler(Looper l){}
    public boolean post(Runnable r){return postDelayed(r,0);}
    public boolean postDelayed(Runnable r,long ms){synchronized(queue){queue.add(new Task(r,SystemClock.uptimeMillis()+ms));}return true;}
    /** Drops pending runs of r. */
    public boolean remove(Runnable r){synchronized(queue){return queue.removeIf(t->t.r==r);}}
    public void removeCallbacks(Runnable r){remove(r);}
    public void removeCallbacksAndMessages(Object token){if(token==null)synchronized(queue){queue.clear();}}
    /** Called once a frame by the desktop main loop. */
    public static void runPending(){
        long now=SystemClock.uptimeMillis();List<Task> due=new ArrayList<>();
        synchronized(queue){for(Iterator<Task> it=queue.iterator();it.hasNext();){Task t=it.next();if(t.at<=now){due.add(t);it.remove();}}}
        for(Task t:due)t.r.run();
    }
}
