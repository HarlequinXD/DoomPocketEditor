package com.retro.doomeditor;

import org.json.*;
import java.util.*;

/** Serializable event-driven finite state machine. Independent of Unity and PlayMaker. */
public final class FSMGraph {
    public static final class Node {
        public String id,name,action="NONE",event="",argument="";
        public float x,y;
        public final LinkedHashMap<String,String> transitions=new LinkedHashMap<>();
        Node(String id,String name,float x,float y){this.id=id;this.name=name;this.x=x;this.y=y;}
    }
    public final ArrayList<Node> nodes=new ArrayList<>();
    public String start="",active="";
    public float elapsed;
    public boolean running;
    public final HashMap<String,Float> variables=new HashMap<>();
    public Node add(String name,float x,float y){Node n=new Node(UUID.randomUUID().toString(),name,x,y);nodes.add(n);if(start.isEmpty())start=n.id;return n;}
    public Node find(String id){for(Node n:nodes)if(n.id.equals(id))return n;return null;}
    public void reset(){active=start;elapsed=0;running=find(active)!=null;variables.clear();}
    public void event(String event){if(!running)return;Node n=find(active);if(n==null)return;String dest=n.transitions.get(event);if(dest!=null&&find(dest)!=null){active=dest;elapsed=0;}}
    /** Execute actions once on entry, or after WAIT duration. Call once per frame. */
    public void tick(float dt,Level level){if(!running)return;Node n=find(active);if(n==null){running=false;return;}
        float previous=elapsed;elapsed+=Math.max(0,Math.min(dt,.1f));
        if(previous==0){
            if("SET_LIGHT".equals(n.action)){
                try{String[] a=n.argument.split(",");int index=Integer.parseInt(a[0].trim());int brightness=Integer.parseInt(a[1].trim());
                    if(index>=0&&index<level.sectors.size())level.sectors.get(index).light=Math.max(0,Math.min(255,brightness));}catch(Exception ignored){}
            }
            if("SET_VARIABLE".equals(n.action)){
                try{String[] a=n.argument.split(",");variables.put(a[0].trim(),Float.parseFloat(a[1].trim()));}catch(Exception ignored){}
            }
        }
        if("WAIT".equals(n.action)){
            try{if(elapsed>=Float.parseFloat(n.argument.trim()))event("FINISHED");}catch(Exception ignored){}
        }
        if("CHECK_VARIABLE".equals(n.action)){
            try{String[] a=n.argument.split(",");if(variables.containsKey(a[0].trim())&&variables.get(a[0].trim())>=Float.parseFloat(a[1].trim()))event("TRUE");}catch(Exception ignored){}
        }
    }
    public JSONObject toJson()throws JSONException{
        JSONObject root=new JSONObject();root.put("start",start);JSONArray arr=new JSONArray();
        for(Node n:nodes){JSONObject o=new JSONObject();o.put("id",n.id);o.put("name",n.name);o.put("x",n.x);o.put("y",n.y);o.put("action",n.action);o.put("argument",n.argument);
            JSONObject tr=new JSONObject();for(Map.Entry<String,String> e:n.transitions.entrySet())tr.put(e.getKey(),e.getValue());o.put("transitions",tr);arr.put(o);}
        root.put("nodes",arr);return root;
    }
    public static FSMGraph fromJson(String text){FSMGraph g=new FSMGraph();try{
        JSONObject root=new JSONObject(text);g.start=root.optString("start","");JSONArray arr=root.optJSONArray("nodes");if(arr==null)return g;
        for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);Node n=new Node(o.getString("id"),o.optString("name","State"),(float)o.optDouble("x",80),(float)o.optDouble("y",80));
            n.action=o.optString("action","NONE");n.argument=o.optString("argument","");JSONObject tr=o.optJSONObject("transitions");if(tr!=null){Iterator<String> keys=tr.keys();while(keys.hasNext()){String k=keys.next();n.transitions.put(k,tr.optString(k));}}g.nodes.add(n);}
        if(g.find(g.start)==null&&!g.nodes.isEmpty())g.start=g.nodes.get(0).id;
    }catch(Exception ignored){}return g;}
}
