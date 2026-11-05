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
    final ArrayList<SoundAsset> sounds=new ArrayList<>();
    final ArrayList<Song> songs=new ArrayList<>();
    /** Built-in sound name (e.g. "Pistol") to the project sound that replaces it everywhere in the game. */
    final java.util.HashMap<String,String> soundOverrides=new java.util.HashMap<>();
    String levelMusic="";
    float musicVolume=.8f,sfxVolume=1f;
    /** On-screen controls and HUD positions from the UI Editor. */
    HudLayout hud=new HudLayout();
    /**
     * A map (level) of the game. {@link #level} is always the map open in the editor; the others live in
     * {@link #maps}. Maps are referred to by a number that never changes, so exits survive renames and deletes.
     */
    static final class GameMap {int num;String name;Level level;String music="";GameMap(int num,String name,Level level){this.num=num;this.name=name;this.level=level;}}
    final ArrayList<GameMap> maps=new ArrayList<>();
    int currentMap=1,startMap=1;
    GameMap map(int num){for(GameMap m:maps)if(m.num==num)return m;return null;}
    /** The map open in the editor (creates the first map for older projects). */
    GameMap current(){ensureMaps();GameMap m=map(currentMap);if(m==null){m=maps.get(0);currentMap=m.num;}return m;}
    void ensureMaps(){if(maps.isEmpty()){maps.add(new GameMap(1,"Map 1",level));currentMap=startMap=1;}if(map(startMap)==null)startMap=maps.get(0).num;}
    /** Keeps the current map's entry pointing at the level being edited (undo/reset replace the Level object). */
    void syncCurrent(){current().level=level;}
    void switchMap(int num){syncCurrent();GameMap m=map(num);if(m!=null){currentMap=num;level=m.level;}}
    int nextMapNum(){int n=1;for(GameMap m:maps)n=Math.max(n,m.num+1);return n;}
    GameMap addMap(String name,Level l){ensureMaps();GameMap m=new GameMap(nextMapNum(),name,l);maps.add(m);return m;}
    String mapName(int num){GameMap m=map(num);return m==null?"Map "+num+"?":m.name;}
    /** Music for a map: its own song, or the project's level music. */
    String mapMusic(GameMap m){return m!=null&&!m.music.isEmpty()&&song(m.music)!=null?m.music:levelMusic;}
    /** The game's RPG content (Content editor): starts as a copy of the built-in catalogue. */
    ArrayList<Rpg.Item> items;ArrayList<Rpg.Spell> spells;ArrayList<Rpg.HeroClass> classes;ArrayList<Rpg.LootTable> loot;ArrayList<Rpg.Shop> shops;
    /** Conversations and quests from the Dialogue editor. */
    final ArrayList<Dialogue.Def> dialogues=new ArrayList<>();
    final ArrayList<Dialogue.QuestDef> quests=new ArrayList<>();
    Dialogue.Def dialogue(String id){if(id==null)return null;for(Dialogue.Def d:dialogues)if(d.id.equals(id))return d;return null;}
    Dialogue.QuestDef questDef(String id){if(id==null)return null;for(Dialogue.QuestDef q:quests)if(q.id.equals(id))return q;return null;}
    String uniqueTalkId(String base){String b=base.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");if(b.isEmpty())b="talk";String id=b;int n=2;while(dialogue(id)!=null)id=b+"_"+(n++);return id;}
    String uniqueQuestId(String base){String b=base.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");if(b.isEmpty())b="quest";String id=b;int n=2;while(questDef(id)!=null)id=b+"_"+(n++);return id;}
    void ensureContent(){
        if(items==null){items=new ArrayList<>();for(Rpg.Item i:Rpg.DEFAULT_ITEMS)items.add(Rpg.copy(i));}
        if(spells==null){spells=new ArrayList<>();for(Rpg.Spell s:Rpg.DEFAULT_SPELLS)spells.add(Rpg.copy(s));}
        if(classes==null){classes=new ArrayList<>();for(Rpg.HeroClass c:Rpg.BUILTIN_CLASSES)classes.add(Rpg.copy(c));}
        if(loot==null)loot=Rpg.defaultLoot();
        if(shops==null)shops=Rpg.defaultShops();
    }
    /** UI style for the HUD, menus and dialogue boxes, from the UI Editor's Theme page. */
    UiTheme theme=new UiTheme();
    String loadWarning;

    /** The open project; opens the last one used, or creates a starter project. */
    static Project get(Context ctx){
        if(instance!=null)return instance;
        ProjectStore.migrate(ctx);
        String id=ProjectStore.currentId(ctx);
        if(id!=null){try{instance=ProjectStore.load(ctx,id);Rpg.use(instance);return instance;}catch(Exception e){/* fall through */}}
        java.util.ArrayList<ProjectStore.Meta> all=ProjectStore.list(ctx);
        for(ProjectStore.Meta m:all){try{instance=ProjectStore.load(ctx,m.id);ProjectStore.setCurrent(ctx,m.id);Rpg.use(instance);return instance;}catch(Exception ignored){}}
        instance=ProjectStore.create(ctx,"My First Game",0);Rpg.use(instance);return instance;
    }
    static boolean isOpen(){return instance!=null;}
    static Project open(Context ctx,String id)throws Exception{Project p=ProjectStore.load(ctx,id);instance=p;Rpg.use(p);ProjectStore.setCurrent(ctx,id);return p;}
    static void reset(){instance=null;}
    static void set(Project p){instance=p;Rpg.use(p);}

    EntityDef def(String id){if(id==null)return null;for(EntityDef d:defs)if(d.id.equals(id))return d;return null;}
    CustomSprite sprite(String id){if(id==null||id.isEmpty())return null;for(CustomSprite s:sprites)if(s.id.equals(id))return s;return null;}
    SoundAsset sound(String id){if(id==null||id.isEmpty())return null;for(SoundAsset a:sounds)if(a.id.equals(id))return a;return null;}
    Song song(String id){if(id==null||id.isEmpty())return null;for(Song m:songs)if(m.id.equals(id))return m;return null;}
    String soundOverride(String builtinName){return soundOverrides.get(builtinName);}
    /** Name shown for an FSM sound value: a project sound id or a built-in sound name. */
    String soundLabel(String v){if(v==null||v.isEmpty())return "None";SoundAsset a=sound(v);if(a!=null)return a.name;int b=SoundFx.builtinIndex(v);return b>=0?SoundFx.NAMES[b]:v+"?";}
    String songLabel(String v){if(v==null||v.isEmpty())return "None";Song m=song(v);return m!=null?m.name:v+"?";}
    EntityDef playerDef(){for(EntityDef d:defs)if(d.kind==EntityDef.PLAYER)return d;EntityDef p=DefaultContent.player();defs.add(0,p);return p;}
    String uniqueId(String base){String b=base.toLowerCase().replaceAll("[^a-z0-9]+","_");if(b.isEmpty())b="entity";String id=b;int n=2;while(def(id)!=null||sprite(id)!=null||sound(id)!=null||song(id)!=null)id=b+"_"+(n++);return id;}
    int countPlaced(String defId){int c=0;for(Level.Thing t:level.things)if(defId.equals(t.def))c++;return c;}
    /** Sprite frame for an entity type, preferring its custom sprite. */
    Sprites.Sprite spriteFor(EntityDef d,int builtin,int frame,float time,float deadTime){return spriteFor(d,builtin,frame,time,deadTime,0f);}
    Sprites.Sprite spriteFor(EntityDef d,int builtin,int frame,float time,float deadTime,float viewDelta){
        if(d!=null&&builtin==d.sprite){CustomSprite cs=sprite(d.customSprite);if(cs!=null){Sprites.Sprite s=cs.frameFor(frame,time,deadTime,CustomSprite.viewIndex(viewDelta,cs.angles));if(s!=null)return s;}}
        return Sprites.get(builtin,builtinCycle(builtin,frame,time,deadTime),CustomSprite.viewIndex(viewDelta,Sprites.VIEWS));
    }

    /** Sprite for a live actor: the FSM-chosen animation clip if any, otherwise the automatic frame. */
    Sprites.Sprite actorSprite(Actor a,int autoFrame,float worldTime){return actorSprite(a,autoFrame,worldTime,0f);}
    /**
     * As above, seen from a camera at {@code viewDelta} radians from the actor's facing (0 = camera in front).
     * Custom sprites with 4 or 8 angles and the built-in characters (8 angles) pick the matching view.
     */
    Sprites.Sprite actorSprite(Actor a,int autoFrame,float worldTime,float viewDelta){
        CustomSprite cs=a.def!=null&&a.sprite==a.def.sprite?sprite(a.def.customSprite):null;
        int view=cs==null?CustomSprite.viewIndex(viewDelta,Sprites.VIEWS):CustomSprite.viewIndex(viewDelta,cs.angles);
        if(a.animClip!=null&&a.def!=null&&a.sprite==a.def.sprite){
            if(cs!=null){CustomSprite.Clip c=cs.clip(a.animClip);if(c!=null&&!c.frames.isEmpty())return cs.frameAt(c,animIndex(c,a),view);}
            return Sprites.get(a.sprite,builtinFrame(a.animClip,a.animTime),view);
        }
        if(cs!=null){Sprites.Sprite s=cs.frameFor(autoFrame,worldTime,a.deadTime,view);if(s!=null)return s;}
        return Sprites.get(a.sprite,builtinCycle(a.sprite,autoFrame,worldTime,a.deadTime),view);
    }
    static int animIndex(CustomSprite.Clip c,Actor a){int n=c.frames.size();int i=(int)(a.animTime*c.fps);boolean loop=a.animLoop<0?c.loop:a.animLoop==1;return loop?i%n:Math.min(n-1,i);}
    /**
     * Built-in sprites animate exactly like a custom sprite copied from them (CustomSprite.fromBuiltin):
     * Walk is a 4-step cycle stride, pass, other stride, pass at 6 fps, and Death shows the hit pose for
     * 1/8 s before the body. (Flipping straight between the two strides looked jerky.)
     */
    static final int[] WALK_CYCLE={Sprites.F_WALK1,Sprites.F_IDLE,Sprites.F_WALK2,Sprites.F_IDLE};
    static int builtinCycle(int id,int engineFrame,float time,float deadTime){
        if(!Sprites.hasAngles(id))return engineFrame;   // torches etc. use frame numbers for their own flicker
        if(engineFrame==Sprites.F_WALK1||engineFrame==Sprites.F_WALK2)return WALK_CYCLE[(int)(Math.max(0,time)*6)%4];
        if(engineFrame==Sprites.F_DEAD)return deadTime<.125f?Sprites.F_PAIN:Sprites.F_DEAD;
        return engineFrame;
    }
    /** Built-in sprites only have a few poses; map clip names onto them. */
    static int builtinFrame(String clip,float t){
        String n=clip.toLowerCase(java.util.Locale.US);
        if(n.contains("walk")||n.contains("run")||n.contains("move"))return WALK_CYCLE[(int)(Math.max(0,t)*6)%4];
        if(n.contains("attack")||n.contains("shoot")||n.contains("fire")||n.contains("melee"))return Sprites.F_ATTACK;
        if(n.contains("pain")||n.contains("hurt"))return Sprites.F_PAIN;
        if(n.contains("death")||n.contains("die")||n.contains("dead"))return Sprites.F_DEAD;
        return Sprites.F_IDLE;
    }
    /** Clip names an entity type can play: its custom sprite's clips plus the standard ones. */
    java.util.ArrayList<String> clipNames(EntityDef d){
        java.util.ArrayList<String> out=new java.util.ArrayList<>();CustomSprite cs=d==null?null:sprite(d.customSprite);
        if(cs!=null)for(CustomSprite.Clip c:cs.clips)if(!out.contains(c.name))out.add(c.name);
        for(String s:CustomSprite.STANDARD_CLIPS)if(!out.contains(s))out.add(s);
        return out;
    }
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("version",7);
        JSONObject m=new JSONObject();m.put("name",name);m.put("template",template);m.put("created",(double)created);o.put("meta",m);
        syncCurrent();
        o.put("level",level.toJson());
        // Every map; the open one's level is the "level" entry above (older versions only know that one).
        JSONArray ma=new JSONArray();
        for(GameMap gm:maps){JSONObject j=new JSONObject();j.put("num",gm.num);j.put("name",gm.name);j.put("music",gm.music);if(gm.num!=currentMap)j.put("level",gm.level.toJson());ma.put(j);}
        o.put("maps",ma);o.put("currentMap",currentMap);o.put("startMap",startMap);
        JSONArray a=new JSONArray();for(EntityDef d:defs)a.put(d.toJson());o.put("entities",a);
        JSONArray s=new JSONArray();for(CustomSprite cs:sprites)s.put(cs.toJson());o.put("sprites",s);
        // Sound & Music editor data. Keep these when changing the save format, or sounds and songs vanish on reload.
        JSONArray sn=new JSONArray();for(SoundAsset sa:sounds)sn.put(sa.toJson());o.put("sounds",sn);
        JSONArray mu=new JSONArray();for(Song m2:songs)mu.put(m2.toJson());o.put("songs",mu);
        JSONObject au=new JSONObject();JSONObject ov=new JSONObject();for(java.util.Map.Entry<String,String> e:soundOverrides.entrySet())ov.put(e.getKey(),e.getValue());
        au.put("overrides",ov);au.put("levelMusic",levelMusic);au.put("musicVolume",(double)musicVolume);au.put("sfxVolume",(double)sfxVolume);o.put("audio",au);
        // UI Editor data. Keep this when changing the save format.
        ensureContent();
        JSONObject rpg=new JSONObject();
        JSONArray ri=new JSONArray();for(Rpg.Item i:items)ri.put(Rpg.toJson(i));rpg.put("items",ri);
        JSONArray rs=new JSONArray();for(Rpg.Spell sp:spells)rs.put(Rpg.toJson(sp));rpg.put("spells",rs);
        JSONArray rc=new JSONArray();for(Rpg.HeroClass hc:classes)rc.put(Rpg.toJson(hc));rpg.put("classes",rc);
        JSONArray rl=new JSONArray();for(Rpg.LootTable lt:loot)rl.put(Rpg.toJson(lt));rpg.put("loot",rl);
        JSONArray rh=new JSONArray();for(Rpg.Shop sh:shops)rh.put(Rpg.toJson(sh));rpg.put("shops",rh);
        o.put("rpg",rpg);
        JSONArray dl=new JSONArray();for(Dialogue.Def d:dialogues)dl.put(Dialogue.toJson(d));o.put("dialogues",dl);
        JSONArray qd=new JSONArray();for(Dialogue.QuestDef q:quests)qd.put(Dialogue.toJson(q));o.put("quests",qd);
        JSONObject ui=new JSONObject();ui.put("hud",hud.toJson());ui.put("theme",theme.toJson());o.put("ui",ui);
        return o;
    }
    static Project fromJson(JSONObject o)throws Exception{
        Project p=new Project();
        JSONObject m=o.optJSONObject("meta");if(m!=null){p.name=m.optString("name","Untitled");p.template=m.optString("template","");p.created=(long)m.optDouble("created",0);}
        p.level=Level.fromJson(o.getJSONObject("level"));
        JSONArray a=o.optJSONArray("entities");if(a!=null)for(int i=0;i<a.length();i++)p.defs.add(EntityDef.fromJson(a.getJSONObject(i)));
        JSONArray s=o.optJSONArray("sprites");if(s!=null)for(int i=0;i<s.length();i++){try{p.sprites.add(CustomSprite.fromJson(s.getJSONObject(i)));}catch(Exception ignored){}}
        JSONArray sn=o.optJSONArray("sounds");if(sn!=null)for(int i=0;i<sn.length();i++){JSONObject j=sn.optJSONObject(i);if(j!=null)p.sounds.add(SoundAsset.fromJson(j));}
        JSONArray mu=o.optJSONArray("songs");if(mu!=null)for(int i=0;i<mu.length();i++){JSONObject j=mu.optJSONObject(i);if(j!=null)p.songs.add(Song.fromJson(j));}
        JSONObject au=o.optJSONObject("audio");
        if(au!=null){JSONObject ov=au.optJSONObject("overrides");if(ov!=null){java.util.Iterator<String> k=ov.keys();while(k.hasNext()){String key=k.next();p.soundOverrides.put(key,ov.optString(key));}}
            p.levelMusic=au.optString("levelMusic","");p.musicVolume=(float)Math.max(0,Math.min(1,au.optDouble("musicVolume",.8)));p.sfxVolume=(float)Math.max(0,Math.min(1,au.optDouble("sfxVolume",1)));}
        JSONArray ma=o.optJSONArray("maps");
        if(ma!=null){p.currentMap=o.optInt("currentMap",1);p.startMap=o.optInt("startMap",1);
            for(int i=0;i<ma.length();i++){JSONObject j=ma.optJSONObject(i);if(j==null)continue;int num=j.optInt("num",i+1);
                Level l;if(num==p.currentMap)l=p.level;else{JSONObject lj=j.optJSONObject("level");if(lj==null)continue;try{l=Level.fromJson(lj);if(!l.isConsistent())l=Templates.empty();}catch(Exception ex){l=Templates.empty();}}
                GameMap gm=new GameMap(num,j.optString("name","Map "+num),l);gm.music=j.optString("music","");p.maps.add(gm);}
            if(p.map(p.currentMap)==null){p.maps.add(0,new GameMap(p.nextMapNum(),"Map",p.level));p.currentMap=p.maps.get(0).num;}}
        p.ensureMaps();
        JSONObject rpg=o.optJSONObject("rpg");
        if(rpg!=null){
            JSONArray ri=rpg.optJSONArray("items");if(ri!=null){p.items=new ArrayList<>();for(int i=0;i<ri.length();i++){JSONObject j=ri.optJSONObject(i);if(j!=null)p.items.add(Rpg.itemFrom(j));}}
            JSONArray rs=rpg.optJSONArray("spells");if(rs!=null){p.spells=new ArrayList<>();for(int i=0;i<rs.length();i++){JSONObject j=rs.optJSONObject(i);if(j!=null)p.spells.add(Rpg.spellFrom(j));}}
            JSONArray rc=rpg.optJSONArray("classes");if(rc!=null&&rc.length()>0){p.classes=new ArrayList<>();for(int i=0;i<rc.length();i++){JSONObject j=rc.optJSONObject(i);if(j!=null)p.classes.add(Rpg.classFrom(j));}}
            JSONArray rl=rpg.optJSONArray("loot");if(rl!=null){p.loot=new ArrayList<>();for(int i=0;i<rl.length();i++){JSONObject j=rl.optJSONObject(i);if(j!=null)p.loot.add(Rpg.lootFrom(j));}}
            JSONArray rh=rpg.optJSONArray("shops");if(rh!=null){p.shops=new ArrayList<>();for(int i=0;i<rh.length();i++){JSONObject j=rh.optJSONObject(i);if(j!=null)p.shops.add(Rpg.shopFrom(j));}}}
        p.ensureContent();
        JSONArray dl=o.optJSONArray("dialogues");if(dl!=null)for(int i=0;i<dl.length();i++){JSONObject j=dl.optJSONObject(i);if(j!=null)p.dialogues.add(Dialogue.fromJson(j));}
        JSONArray qd=o.optJSONArray("quests");if(qd!=null)for(int i=0;i<qd.length();i++){JSONObject j=qd.optJSONObject(i);if(j!=null)p.quests.add(Dialogue.questFrom(j));}
        JSONObject ui=o.optJSONObject("ui");if(ui!=null){p.hud=HudLayout.fromJson(ui.optJSONObject("hud"));JSONObject th=ui.optJSONObject("theme");if(th!=null)p.theme=UiTheme.fromJson(th);}
        if(!p.level.isConsistent())throw new IllegalStateException("inconsistent map");
        p.fixUp();return p;
    }
    private void fixUp(){
        playerDef();ensureMaps();
        for(GameMap gm:maps){Level l=gm.level;
            if(defs.size()>1)for(Level.Thing t:l.things)if(def(t.def)==null)t.def=defs.get(1).id;
            l.things.removeIf(t->def(t.def)==null);}
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
