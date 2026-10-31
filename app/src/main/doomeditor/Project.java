package com.retro.doomeditor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** The whole editable project: the level plus all entity types. Shared by every screen. */
final class Project {
    private static Project instance;
    Level level;
    final ArrayList<EntityDef> defs=new ArrayList<>();
    String loadWarning;

    static Project get(Context ctx){if(instance==null)instance=load(ctx);return instance;}
    static void reset(){instance=null;}

    EntityDef def(String id){if(id==null)return null;for(EntityDef d:defs)if(d.id.equals(id))return d;return null;}
    EntityDef playerDef(){for(EntityDef d:defs)if(d.kind==EntityDef.PLAYER)return d;EntityDef p=DefaultContent.player();defs.add(0,p);return p;}
    String uniqueId(String base){String b=base.toLowerCase().replaceAll("[^a-z0-9]+","_");if(b.isEmpty())b="entity";String id=b;int n=2;while(def(id)!=null)id=b+"_"+(n++);return id;}
    int countPlaced(String defId){int c=0;for(Level.Thing t:level.things)if(defId.equals(t.def))c++;return c;}

    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("version",5);o.put("level",level.toJson());
        JSONArray a=new JSONArray();for(EntityDef d:defs)a.put(d.toJson());o.put("entities",a);return o;
    }
    void save(Context ctx){
        try{ctx.getSharedPreferences("project",0).edit().putString("project",toJson().toString()).apply();}catch(Exception ignored){}
    }
    static Project fresh(){Project p=new Project();p.level=new Level();p.defs.addAll(DefaultContent.all());return p;}

    private static Project load(Context ctx){
        android.content.SharedPreferences pref=ctx.getSharedPreferences("project",0);
        String saved=pref.getString("project",null);
        Project p=new Project();
        try{
            if(saved!=null){
                JSONObject o=new JSONObject(saved);
                p.level=Level.fromJson(o.getJSONObject("level"));
                JSONArray a=o.optJSONArray("entities");
                if(a!=null)for(int i=0;i<a.length();i++)p.defs.add(EntityDef.fromJson(a.getJSONObject(i)));
            }else{
                // Migrate earlier versions: separate "level", "fsm_graph" and "wallMaterials" keys.
                String lv=pref.getString("level",null);
                p.level=lv!=null?Level.fromJson(new JSONObject(lv)):new Level();
                p.defs.addAll(DefaultContent.all());
                String walls=pref.getString("wallMaterials",null);
                if(walls!=null){JSONObject w=new JSONObject(walls);java.util.Iterator<String> k=w.keys();while(k.hasNext()){String key=k.next();p.level.wallOverrides.put(key,w.optInt(key,0));}}
                String fsm=pref.getString("fsm_graph",null);
                if(fsm!=null){FSMGraph g=FSMGraph.fromJson(fsm);if(!g.nodes.isEmpty()){EntityDef d=new EntityDef("legacy_script","Old FSM Script",EntityDef.TRIGGER,Sprites.SWITCH);d.solid=false;d.fsm=g;p.defs.add(d);}}
            }
            if(!p.level.isConsistent())throw new IllegalStateException("inconsistent map");
        }catch(Throwable t){
            if(saved!=null)pref.edit().putString("project_unreadable",saved).apply();
            p=fresh();p.loadWarning="Saved project could not be loaded; started a new one";
        }
        if(p.defs.isEmpty())p.defs.addAll(DefaultContent.all());
        p.playerDef();
        // Things that reference missing types fall back to the first monster.
        for(Level.Thing t:p.level.things)if(p.def(t.def)==null)t.def=p.defs.size()>1?p.defs.get(1).id:p.defs.get(0).id;
        return p;
    }
}
