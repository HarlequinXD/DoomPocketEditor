package com.retro.doomeditor;

import java.util.ArrayDeque;
import java.util.HashMap;

/** Runs one FSMGraph for one entity: action execution, events, transitions and local variables. */
final class FsmRuntime {
    final FSMGraph graph;
    FSMGraph.Node state;
    float stateTime;
    boolean[] done=new boolean[0];
    /** Which actions have run at least once in this visit to the state (each gets its "entering" call exactly once). */
    private boolean[] started=new boolean[0];
    /** A creature that died with no DIED transition: its FSM stops (it doesn't fight on as a corpse). */
    boolean halted;
    float[][] scratch=new float[0][];
    private boolean entering,finishedSent;
    private final ArrayDeque<String> queue=new ArrayDeque<>();
    private String pending;
    final HashMap<String,Float> vars=new HashMap<>();
    String lastEvent="";
    int transitions;
    private final Actions.Ctx ctx=new Actions.Ctx();

    FsmRuntime(FSMGraph g){graph=g;}

    void start(){FSMGraph.Node s=graph.find(graph.start);if(s!=null)enter(s);}
    /** Saved games: resume in a state (its actions run again from the start) with the saved local variables. */
    void restore(String stateId,java.util.Map<String,Float> v){FSMGraph.Node n=graph.find(stateId);if(n!=null)enter(n);halted=false;vars.clear();if(v!=null)vars.putAll(v);}
    String stateId(){return state==null?"":state.id;}
    private void enter(FSMGraph.Node n){
        state=n;stateTime=0;entering=true;finishedSent=false;pending=null;transitions++;
        int k=n.actions.size();done=new boolean[k];started=new boolean[k];scratch=new float[k][8];
    }
    /** Queues an event; it is processed at the start of the next tick or immediately inside an action. */
    void send(String event){if(event==null||event.isEmpty())return;if(pending==null&&inTick)pending=event;else if(queue.size()<16)queue.add(event);}
    private boolean inTick;
    /** DIED arrived and nothing handles it: a creature's FSM stops here (true if it did). The hero's keeps running. */
    private boolean died(Actor self){if(self==null||self.isPlayer||self.alive)return false;halted=true;return true;}
    private boolean fire(String ev){
        FSMGraph.Node n=target(ev);
        if(n==null)return false;
        lastEvent=ev;enter(n);return true;
    }
    /** The state an event would lead to from here, or null if this state ignores it. */
    private FSMGraph.Node target(String ev){
        if(state==null||ev==null)return null;
        String dest=state.transitions.get(ev);
        if(dest==null)dest=graph.globals.get(ev);
        return graph.find(dest);
    }
    float getVar(World w,String name){
        if(name.startsWith("g.")){Float v=w.globals.get(name.substring(2));return v==null?0:v;}
        Float v=vars.get(name);return v==null?0:v;
    }
    void setVar(World w,String name,float v){if(name.startsWith("g."))w.globals.put(name.substring(2),v);else vars.put(name,v);}

    void tick(World w,Actor self,float dt){
        if(state==null||halted)return;
        // External events (DIED, DAMAGED, TOUCHED, USED, broadcasts...). Only one transition per frame.
        // DIED always wins: if it arrived in the same frame as DAMAGED (a flurry of hits) it must not be dropped.
        if(queue.contains("DIED")){queue.clear();if(!fire("DIED")&&died(self))return;}
        else while(!queue.isEmpty()){if(fire(queue.poll())){queue.clear();break;}}
        FSMGraph.Node s=state;
        ctx.w=w;ctx.self=self;ctx.fsm=this;ctx.dt=dt;
        inTick=true;pending=null;
        boolean all=true;
        for(int i=0;i<s.actions.size()&&i<done.length;i++){
            if(done[i])continue;
            FSMGraph.Action a=s.actions.get(i);
            if(!a.enabled){done[i]=true;continue;}
            Actions.Spec spec=Actions.get(a.type);
            if(spec==null){done[i]=true;continue;}
            ctx.a=a;ctx.m=scratch[i];ctx.enter=!started[i];started[i]=true;
            int r;
            try{r=spec.impl.run(ctx);}catch(RuntimeException ex){r=Actions.DONE;w.message("Action error in "+spec.name+": "+ex.getClass().getSimpleName(),3);}
            if(r==Actions.DONE)done[i]=true;else all=false;
            // An event this state handles ends the state's actions for this frame; one it ignores is dropped, so
            // the actions after it still run (a Joystick Moved idle event with no transition must not stall them).
            if(pending!=null){if(pending.equals("DIED")||target(pending)!=null)break;pending=null;}
            if(!queue.isEmpty()&&queue.contains("DIED"))break;   // killed during this action
        }
        inTick=false;
        entering=false;
        if(state!=s)return;
        stateTime+=dt;
        if(queue.contains("DIED")||"DIED".equals(pending)){queue.clear();pending=null;if(!fire("DIED"))died(self);return;}
        if(pending!=null){String ev=pending;pending=null;if(fire(ev))return;}
        if(all&&!finishedSent){finishedSent=true;fire("FINISHED");}
    }
}
