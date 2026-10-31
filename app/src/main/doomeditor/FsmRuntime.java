package com.retro.doomeditor;

import java.util.ArrayDeque;
import java.util.HashMap;

/** Runs one FSMGraph for one entity: action execution, events, transitions and local variables. */
final class FsmRuntime {
    final FSMGraph graph;
    FSMGraph.Node state;
    float stateTime;
    boolean[] done=new boolean[0];
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
    private void enter(FSMGraph.Node n){
        state=n;stateTime=0;entering=true;finishedSent=false;pending=null;transitions++;
        int k=n.actions.size();done=new boolean[k];scratch=new float[k][8];
    }
    /** Queues an event; it is processed at the start of the next tick or immediately inside an action. */
    void send(String event){if(event==null||event.isEmpty())return;if(pending==null&&inTick)pending=event;else if(queue.size()<16)queue.add(event);}
    private boolean inTick;
    private boolean fire(String ev){
        if(state==null)return false;
        String dest=state.transitions.get(ev);
        if(dest==null)dest=graph.globals.get(ev);
        FSMGraph.Node n=graph.find(dest);
        if(n==null)return false;
        lastEvent=ev;enter(n);return true;
    }
    float getVar(World w,String name){
        if(name.startsWith("g.")){Float v=w.globals.get(name.substring(2));return v==null?0:v;}
        Float v=vars.get(name);return v==null?0:v;
    }
    void setVar(World w,String name,float v){if(name.startsWith("g."))w.globals.put(name.substring(2),v);else vars.put(name,v);}

    void tick(World w,Actor self,float dt){
        if(state==null)return;
        // External events (DIED, DAMAGED, TOUCHED, USED, broadcasts...). Only one transition per frame.
        while(!queue.isEmpty()){if(fire(queue.poll())){queue.clear();break;}}
        FSMGraph.Node s=state;
        ctx.w=w;ctx.self=self;ctx.fsm=this;ctx.dt=dt;ctx.enter=entering;
        inTick=true;pending=null;
        boolean all=true;
        for(int i=0;i<s.actions.size()&&i<done.length;i++){
            if(done[i])continue;
            FSMGraph.Action a=s.actions.get(i);
            if(!a.enabled){done[i]=true;continue;}
            Actions.Spec spec=Actions.get(a.type);
            if(spec==null){done[i]=true;continue;}
            ctx.a=a;ctx.m=scratch[i];
            int r;
            try{r=spec.impl.run(ctx);}catch(RuntimeException ex){r=Actions.DONE;w.message("Action error in "+spec.name+": "+ex.getClass().getSimpleName(),3);}
            if(r==Actions.DONE)done[i]=true;else all=false;
            if(pending!=null)break;
        }
        inTick=false;
        entering=false;
        if(state!=s)return;
        stateTime+=dt;
        if(pending!=null){String ev=pending;pending=null;if(fire(ev))return;}
        if(all&&!finishedSent){finishedSent=true;fire("FINISHED");}
    }
}
