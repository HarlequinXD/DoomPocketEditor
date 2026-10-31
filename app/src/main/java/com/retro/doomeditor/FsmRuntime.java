package com.example.fsmstudio;
import java.util.*;
/** Executable FSM core. Update once per frame; events cause transitions. */
public class FsmRuntime {
 public final Model model;public final GameApi api;public Model.State active;public boolean running=false;private float elapsed=0;private final HashSet<Integer> once=new HashSet<>();
 public FsmRuntime(Model m,GameApi api){model=m;this.api=api;}
 public void start(){running=true;active=null;for(Model.State s:model.states)if(s.start){enter(s);break;}if(active==null&&!model.states.isEmpty())enter(model.states.get(0));}
 public void stop(){running=false;active=null;}
 private void enter(Model.State s){active=s;elapsed=0;once.clear();api.onEvent("ENTER:"+s.name);}
 public void send(String event){if(!running||active==null)return;for(Model.Edge e:model.edges)if(e.from.equals(active.id)&&e.event.equalsIgnoreCase(event)){Model.State target=model.get(e.to);if(target!=null){api.onEvent("EXIT:"+active.name);enter(target);}return;}}
 private String[] parts(String argument){return argument.split("\\s*,\\s*",3);}
 private double val(String[] a,int index,double def){try{return Double.parseDouble(a[index]);}catch(Exception e){return def;}}
 public void update(float dt){if(!running||active==null)return;elapsed+=Math.max(0,dt);Model.State s=active;for(int i=0;i<s.actions.size();i++){if(active!=s)return;Model.Action a=s.actions.get(i);if(!a.enabled)continue;String[] v=parts(a.argument);String key=v.length>0?v[0].trim():"value";double n=val(v,1,0);boolean first=once.add(i);switch(a.type){
 case "Wait":if(elapsed>=val(v,0,1)){send("FINISHED");return;}break;
 case "Finish State":if(first){send("FINISHED");return;}break;
 case "Send Event":case "Send Global Event":case "Broadcast Event":if(first){send(key);return;}break;
 case "Restart FSM":if(first){start();return;}break;
 case "Disable FSM":if(first){stop();return;}break;
 case "Set Float":case "Set Integer":if(first)api.numbers.put(key,n);break;
 case "Add Float":if(first)api.numbers.put(key,api.number(key)+n);break;
 case "Subtract Float":if(first)api.numbers.put(key,api.number(key)-n);break;
 case "Multiply Float":if(first)api.numbers.put(key,api.number(key)*n);break;
 case "Divide Float":if(first&&n!=0)api.numbers.put(key,api.number(key)/n);break;
 case "Clamp Float":if(first)api.numbers.put(key,Math.max(0,Math.min(n,api.number(key))));break;
 case "Random Float":if(first)api.numbers.put(key,Math.random()*n);break;
 case "Set Boolean":case "Set Game Flag":if(first)api.flags.put(key,v.length>1&&Boolean.parseBoolean(v[1]));break;
 case "Check Game Flag":if(api.flag(key))send("TRUE");break;
 case "Compare Integer":if(api.number(key)>=n)send("TRUE");break;
 case "Check Health":if(api.number("health")<=val(v,0,0))send("TRUE");break;
 case "Set Health":if(first)api.numbers.put("health",val(v,0,100));break;
 case "Heal":if(first)api.numbers.put("health",api.number("health")+val(v,0,10));break;
 case "Apply Damage":if(first)api.numbers.put("health",api.number("health")-val(v,0,10));break;
 case "Show Message":if(first)api.message(a.argument);break;
 default:if(first)api.onAction(a.type,a.argument);break;
 }}}
}