package com.retro.doomeditor;

import org.json.*;
import java.util.*;

/**
 * PlayMaker-style finite state machine definition. Each state holds an ordered action list and
 * event transitions; global transitions jump to a state from anywhere. Runtime lives in FsmRuntime.
 */
public final class FSMGraph {
    public static final class Action {
        public String type;
        public boolean enabled=true;
        public final LinkedHashMap<String,String> params=new LinkedHashMap<>();
        public Action(String type){this.type=type;Actions.Spec s=Actions.get(type);if(s!=null)for(Actions.Param p:s.params)params.put(p.key,p.def);}
        Action copy(){Action a=new Action(type);a.enabled=enabled;a.params.clear();a.params.putAll(params);return a;}
        public Action set(String k,Object v){params.put(k,String.valueOf(v));return this;}
    }
    public static final class Node {
        public String id,name;
        public float x,y;
        public final ArrayList<Action> actions=new ArrayList<>();
        public final LinkedHashMap<String,String> transitions=new LinkedHashMap<>();
        Node(String id,String name,float x,float y){this.id=id;this.name=name;this.x=x;this.y=y;}
        public Action add(String type){Action a=new Action(type);actions.add(a);return a;}
        public Node on(String event,Node to){transitions.put(event,to.id);return this;}
    }
    public final ArrayList<Node> nodes=new ArrayList<>();
    /** Global transitions: event name -> target state id, fired from any state. */
    public final LinkedHashMap<String,String> globals=new LinkedHashMap<>();
    public String start="";

    public Node add(String name,float x,float y){Node n=new Node(newId(),name,x,y);nodes.add(n);if(start.isEmpty())start=n.id;return n;}
    static String newId(){return UUID.randomUUID().toString().substring(0,13);}
    public Node find(String id){if(id==null)return null;for(Node n:nodes)if(n.id.equals(id))return n;return null;}
    public Node byName(String name){for(Node n:nodes)if(n.name.equalsIgnoreCase(name))return n;return null;}
    /** Distinct event names used by transitions, globals and action parameters. */
    public ArrayList<String> events(){
        ArrayList<String> out=new ArrayList<>();
        for(String e:globals.keySet())if(!out.contains(e))out.add(e);
        for(Node n:nodes){for(String e:n.transitions.keySet())if(!out.contains(e))out.add(e);
            for(Action a:n.actions){Actions.Spec s=Actions.get(a.type);if(s==null)continue;
                for(Actions.Param p:s.params)if(p.type==Actions.T_EVENT){String v=a.params.get(p.key);if(v!=null&&!v.isEmpty()&&!out.contains(v))out.add(v);}}}
        return out;
    }
    public int transitionCount(){int c=globals.size();for(Node n:nodes)c+=n.transitions.size();return c;}
    public void remove(Node node){
        nodes.remove(node);
        for(Node n:nodes)n.transitions.values().removeAll(Collections.singleton(node.id));
        globals.values().removeAll(Collections.singleton(node.id));
        if(start.equals(node.id))start=nodes.isEmpty()?"":nodes.get(0).id;
    }
    public FSMGraph copy(){try{return fromJson(toJson());}catch(Exception e){return new FSMGraph();}}

    public JSONObject toJson()throws JSONException{
        JSONObject root=new JSONObject();root.put("start",start);root.put("version",2);JSONArray arr=new JSONArray();
        for(Node n:nodes){JSONObject o=new JSONObject();o.put("id",n.id);o.put("name",n.name);o.put("x",n.x);o.put("y",n.y);
            JSONArray acts=new JSONArray();
            for(Action a:n.actions){JSONObject ao=new JSONObject();ao.put("type",a.type);ao.put("enabled",a.enabled);JSONObject ps=new JSONObject();for(Map.Entry<String,String> e:a.params.entrySet())ps.put(e.getKey(),e.getValue());ao.put("params",ps);acts.put(ao);}
            o.put("actions",acts);
            JSONObject tr=new JSONObject();for(Map.Entry<String,String> e:n.transitions.entrySet())tr.put(e.getKey(),e.getValue());o.put("transitions",tr);arr.put(o);}
        root.put("nodes",arr);
        JSONObject gl=new JSONObject();for(Map.Entry<String,String> e:globals.entrySet())gl.put(e.getKey(),e.getValue());root.put("globals",gl);
        return root;
    }
    public static FSMGraph fromJson(String text){try{return fromJson(new JSONObject(text));}catch(Exception e){return new FSMGraph();}}
    public static FSMGraph fromJson(JSONObject root){FSMGraph g=new FSMGraph();try{
        g.start=root.optString("start","");JSONArray arr=root.optJSONArray("nodes");
        if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);Node n=new Node(o.optString("id",newId()),o.optString("name","State"),(float)o.optDouble("x",80),(float)o.optDouble("y",80));
            JSONArray acts=o.optJSONArray("actions");
            if(acts!=null)for(int k=0;k<acts.length();k++){JSONObject ao=acts.getJSONObject(k);String type=ao.optString("type","");if(Actions.get(type)==null)continue;
                Action a=new Action(type);a.enabled=!"false".equals(ao.optString("enabled","true"));JSONObject ps=ao.optJSONObject("params");
                if(ps!=null){Iterator<String> it=ps.keys();while(it.hasNext()){String key=it.next();if(a.params.containsKey(key))a.params.put(key,ps.optString(key,""));}}
                n.actions.add(a);}
            else migrateLegacy(n,o.optString("action","NONE"),o.optString("argument",""));
            JSONObject tr=o.optJSONObject("transitions");if(tr!=null){Iterator<String> keys=tr.keys();while(keys.hasNext()){String k=keys.next();n.transitions.put(k,tr.optString(k));}}
            g.nodes.add(n);}
        JSONObject gl=root.optJSONObject("globals");if(gl!=null){Iterator<String> keys=gl.keys();while(keys.hasNext()){String k=keys.next();g.globals.put(k,gl.optString(k));}}
        if(g.find(g.start)==null&&!g.nodes.isEmpty())g.start=g.nodes.get(0).id;
    }catch(Exception ignored){}return g;}

    /** Converts the single action/argument format of earlier versions. */
    private static void migrateLegacy(Node n,String action,String arg){
        String[] s=arg.split(",");
        try{
            switch(action){
                case "WAIT":n.add("wait").set("seconds",arg.trim().isEmpty()?"1":arg.trim());break;
                case "SET_LIGHT":n.add("set_sector_light").set("sector",s[0].trim()).set("light",s.length>1?s[1].trim():"255");break;
                case "SET_VARIABLE":n.add("set_float").set("var",s[0].trim()).set("value",s.length>1?s[1].trim():"0");break;
                case "CHECK_VARIABLE":n.add("float_compare").set("a",s[0].trim()).set("b",s.length>1?s[1].trim():"0").set("greaterEvent","TRUE").set("equalEvent","TRUE");break;
            }
        }catch(Exception ignored){}
    }
}
