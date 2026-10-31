package com.retro.doomeditor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** The whole editable project: the level plus all entity types. Shared by every screen. */
final class Project {
    private static Project instance;
    private static final java.util.concurrent.ExecutorService writer=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ProjectSave");t.setDaemon(true);return t;});
    String id,name="Untitled",template="";
    long created;
    Level level;
    final ArrayList<EntityDef> defs=new ArrayList<>();
    final ArrayList<CustomSprite> sprites=new ArrayList<>();
    String loadWarning;

    /** The open project; opens the last one used, or creates a starter project. */
    static Project get(Context ctx){
        if(instance!=null)return instance;
        ProjectStore.migrate(ctx);
        String id=ProjectStore.currentId(ctx);
        if(id!=null){try{instance=ProjectStore.load(ctx,id);return instance;}catch(Exception e){/* fall through */}}
        java.util.ArrayList<ProjectStore.Meta> all=ProjectStore.list(ctx);
        for(ProjectStore.Meta m:all){try{instance=ProjectStore.load(ctx,m.id);ProjectStore.setCurrent(ctx,m.id);return instance;}catch(Exception ignored){}}
        instance=ProjectStore.create(ctx,"My First Game",0);return instance;
    }
    static boolean isOpen(){return instance!=null;}
    static Project open(Context ctx,String id)throws Exception{Project p=ProjectStore.load(ctx,id);instance=p;ProjectStore.setCurrent(ctx,id);return p;}
    static void reset(){instance=null;}
    static void set(Project p){instance=p;}

    EntityDef def(String id){if(id==null)return null;for(EntityDef d:defs)if(d.id.equals(id))return d;return null;}
    CustomSprite sprite(String id){if(id==null||id.isEmpty())return null;for(CustomSprite s:sprites)if(s.id.equals(id))return s;return null;}
    EntityDef playerDef(){for(EntityDef d:defs)if(d.kind==EntityDef.PLAYER)return d;EntityDef p=DefaultContent.player();defs.add(0,p);return p;}
    String uniqueId(String base){String b=base.toLowerCase().replaceAll("[^a-z0-9]+","_");if(b.isEmpty())b="entity";String id=b;int n=2;while(def(id)!=null||sprite(id)!=null)id=b+"_"+(n++);return id;}
    int countPlaced(String defId){int c=0;for(Level.Thing t:level.things)if(defId.equals(t.def))c++;return c;}
    /** Sprite frame for an entity type, preferring its custom sprite. */
    Sprites.Sprite spriteFor(EntityDef d,int builtin,int frame,float time,float deadTime){
        if(d!=null&&builtin==d.sprite){CustomSprite cs=sprite(d.customSprite);if(cs!=null){Sprites.Sprite s=cs.frameFor(frame,time,deadTime);if(s!=null)return s;}}
        return Sprites.get(builtin,frame);
    }

    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("version",6);
        JSONObject m=new JSONObject();m.put("name",name);m.put("template",template);m.put("created",(double)created);o.put("meta",m);
        o.put("level",level.toJson());
        JSONArray a=new JSONArray();for(EntityDef d:defs)a.put(d.toJson());o.put("entities",a);
        JSONArray s=new JSONArray();for(CustomSprite cs:sprites)s.put(cs.toJson());o.put("sprites",s);
        return o;
    }
    static Project fromJson(JSONObject o)throws Exception{
        Project p=new Project();
        JSONObject m=o.optJSONObject("meta");if(m!=null){p.name=m.optString("name","Untitled");p.template=m.optString("template","");p.created=(long)m.optDouble("created",0);}
        p.level=Level.fromJson(o.getJSONObject("level"));
        JSONArray a=o.optJSONArray("entities");if(a!=null)for(int i=0;i<a.length();i++)p.defs.add(EntityDef.fromJson(a.getJSONObject(i)));
        JSONArray s=o.optJSONArray("sprites");if(s!=null)for(int i=0;i<s.length();i++){try{p.sprites.add(CustomSprite.fromJson(s.getJSONObject(i)));}catch(Exception ignored){}}
        if(!p.level.isConsistent())throw new IllegalStateException("inconsistent map");
        p.fixUp();return p;
    }
    private void fixUp(){
        playerDef();
        if(defs.size()>1)for(Level.Thing t:level.things)if(def(t.def)==null)t.def=defs.get(1).id;
        level.things.removeIf(t->def(t.def)==null);
    }
    /** Saves in the background so editing never stutters. */
    void save(Context ctx){
        if(id==null)return;
        try{final String json=toJson().toString();final Context app=ctx.getApplicationContext()!=null?ctx.getApplicationContext():ctx;final String pid=id;
            writer.execute(()->{try{ProjectStore.write(app,pid,json);}catch(Exception ignored){}});}catch(Exception ignored){}
    }
    void saveNow(Context ctx){try{ProjectStore.write(ctx,id,toJson().toString());}catch(Exception ignored){}}
    static Project fresh(){Project p=new Project();p.level=new Level();p.defs.addAll(DefaultContent.all());return p;}

    static Project loadLegacy(Context ctx){
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
