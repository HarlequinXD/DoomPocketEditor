package com.retro.doomeditor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Saved games: the hero, quests, global variables, the map being played and the remembered state of
 * every visited map. One JSON file per slot, kept per project ("auto" plus manual slots 1-3).
 */
final class GameSave {
    private GameSave(){}
    static final String[] SLOTS={"auto","1","2","3"};
    static String slotName(String slot){return slot.equals("auto")?"Autosave":"Slot "+slot;}

    // ------------------------------------------------------------------ in-game state
    static final class ThingState {boolean gone,alive=true,visible=true;float x,y,angle;int health,sprite;String state="";HashMap<String,Float> vars;}
    static final class MapState {ThingState[] things;boolean[] secrets;int kills,items,secretCount;}
    /** What a running game remembers across maps. */
    static final class Session {final HashMap<Integer,MapState> maps=new HashMap<>();final HashMap<String,Integer> shopStock=new HashMap<>();float playTime;}

    /** Short description shown on a slot. */
    static final class Info {String slot,className,mapName;int level,gold;long time;float playTime;boolean exists;}

    // ------------------------------------------------------------------ files
    private static File dir(Context c,Project p){File d=new File(c.getFilesDir(),"saves/"+(p.id==null?"unsaved":p.id));if(!d.exists())d.mkdirs();return d;}
    private static File file(Context c,Project p,String slot){return new File(dir(c,p),"slot_"+slot+".json");}
    static boolean exists(Context c,Project p,String slot){return file(c,p,slot).exists();}
    static Info info(Context c,Project p,String slot){
        Info i=new Info();i.slot=slot;File f=file(c,p,slot);if(!f.exists())return i;
        try{JSONObject o=new JSONObject(ProjectStore.read(f));JSONObject m=o.optJSONObject("meta");if(m==null)return i;
            i.exists=true;i.className=m.optString("class","Hero");i.mapName=m.optString("map","");i.level=m.optInt("level",1);i.gold=m.optInt("gold",0);
            i.time=(long)m.optDouble("time",f.lastModified());i.playTime=(float)m.optDouble("playTime",0);}catch(Exception ignored){}
        return i;
    }
    /** The newest save of any slot, or null. */
    static Info latest(Context c,Project p){Info best=null;for(String s:SLOTS){Info i=info(c,p,s);if(i.exists&&(best==null||i.time>best.time))best=i;}return best;}
    static boolean any(Context c,Project p){return latest(c,p)!=null;}
    static void delete(Context c,Project p,String slot){file(c,p,slot).delete();}

    static boolean save(Context c,Project p,World w,String slot){
        if(w==null||w.hero==null)return false;
        try{JSONObject o=toJson(p,w);File f=file(c,p,slot),tmp=new File(f.getPath()+".tmp");
            try(java.io.OutputStreamWriter out=new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp),"UTF-8")){out.write(o.toString());}
            if(f.exists())f.delete();return tmp.renameTo(f);
        }catch(Exception e){return false;}
    }
    /** Builds a running World from a saved slot, or returns null if it can't be read. */
    static World load(Context c,Project p,SoundFx sfx,String slot){
        try{JSONObject o=new JSONObject(ProjectStore.read(file(c,p,slot)));return fromJson(p,sfx,o);}catch(Exception e){return null;}
    }

    // ------------------------------------------------------------------ json
    private static JSONObject toJson(Project p,World w)throws Exception{
        JSONObject o=new JSONObject();o.put("version",1);
        Hero h=w.hero;
        JSONObject m=new JSONObject();m.put("class",h.cls.name);m.put("level",h.level);m.put("gold",h.gold);m.put("map",p.mapName(w.mapNum));
        m.put("time",(double)System.currentTimeMillis());m.put("playTime",(double)(w.session==null?0:w.session.playTime));o.put("meta",m);
        o.put("map",w.mapNum);
        JSONObject pl=new JSONObject();pl.put("x",(double)w.player.x);pl.put("y",(double)w.player.y);pl.put("angle",(double)w.player.angle);pl.put("health",w.player.health);o.put("player",pl);
        o.put("hero",heroJson(h));
        JSONArray qs=new JSONArray();for(World.Quest q:w.quests.values()){JSONObject j=new JSONObject();j.put("id",q.id);j.put("title",q.title);j.put("text",q.text);j.put("state",q.state);j.put("stage",q.stage);qs.put(j);}o.put("quests",qs);
        JSONObject g=new JSONObject();for(Map.Entry<String,Float> e:w.globals.entrySet())g.put(e.getKey(),(double)e.getValue());o.put("globals",g);
        // all visited maps, with the current one captured now
        JSONObject maps=new JSONObject();
        if(w.session!=null)for(Map.Entry<Integer,MapState> e:w.session.maps.entrySet())if(e.getKey()!=w.mapNum)maps.put(String.valueOf(e.getKey()),mapJson(e.getValue()));
        maps.put(String.valueOf(w.mapNum),mapJson(w.captureMapState()));
        o.put("maps",maps);
        JSONObject st=new JSONObject();if(w.session!=null)for(Map.Entry<String,Integer> e:w.session.shopStock.entrySet())st.put(e.getKey(),(int)e.getValue());o.put("shopStock",st);
        return o;
    }
    private static World fromJson(Project p,SoundFx sfx,JSONObject o)throws Exception{
        int mapNum=o.optInt("map",p.startMap);if(p.map(mapNum)==null)mapNum=p.startMap;
        // a stand-in previous world carries the session into the constructor, which applies the saved map state
        Session ses=new Session();
        JSONObject maps=o.optJSONObject("maps");
        if(maps!=null){Iterator<String> k=maps.keys();while(k.hasNext()){String key=k.next();try{ses.maps.put(Integer.parseInt(key),readMap(maps.getJSONObject(key)));}catch(Exception ignored){}}}
        JSONObject st=o.optJSONObject("shopStock");if(st!=null){Iterator<String> k=st.keys();while(k.hasNext()){String key=k.next();ses.shopStock.put(key,st.optInt(key,0));}}
        JSONObject meta=o.optJSONObject("meta");if(meta!=null)ses.playTime=(float)meta.optDouble("playTime",0);
        World w=new World(p,sfx,mapNum,null,"",ses);
        Hero h=readHero(o.optJSONObject("hero"),p);if(h!=null){w.hero=h;w.weapon=h.weaponOrFists().fp;w.applyHeroLook();}
        JSONObject pl=o.optJSONObject("player");
        if(pl!=null){float x=(float)pl.optDouble("x",w.player.x),y=(float)pl.optDouble("y",w.player.y);
            if(w.level.sectorAt(x,y)>=0){w.player.x=x;w.player.y=y;w.player.z=w.floorAt(x,y);}
            w.player.angle=(float)pl.optDouble("angle",w.player.angle);w.player.health=Math.max(1,pl.optInt("health",w.player.health));}
        w.quests.clear();JSONArray qs=o.optJSONArray("quests");
        if(qs!=null)for(int i=0;i<qs.length();i++){JSONObject j=qs.getJSONObject(i);World.Quest q=new World.Quest(j.optString("id","q"+i));q.title=j.optString("title","");q.text=j.optString("text","");q.state=j.optInt("state",0);q.stage=j.optInt("stage",0);w.quests.put(q.id,q);}
        w.globals.clear();JSONObject g=o.optJSONObject("globals");
        if(g!=null){Iterator<String> k=g.keys();while(k.hasNext()){String key=k.next();w.globals.put(key,(float)g.optDouble(key,0));}}
        w.syncView();
        return w;
    }
    private static JSONObject heroJson(Hero h)throws Exception{
        JSONObject o=new JSONObject();o.put("class",h.cls.name);o.put("level",h.level);o.put("xp",h.xp);o.put("gold",h.gold);
        o.put("maxHp",h.maxHp);o.put("sp",h.sp);o.put("maxSp",h.maxSp);o.put("selectedSpell",h.selectedSpell);
        JSONArray sc=new JSONArray();for(int v:h.scores)sc.put(v);o.put("scores",sc);
        JSONArray inv=new JSONArray();for(Hero.Stack s:h.inventory){JSONObject j=new JSONObject();j.put("id",s.item.id);j.put("count",s.count);inv.put(j);}o.put("inventory",inv);
        if(h.look!=null)o.put("look",h.look.toJson());
        o.put("weapon",h.weapon==null?"":h.weapon.id);o.put("armour",h.armour==null?"":h.armour.id);o.put("shield",h.shield==null?"":h.shield.id);
        return o;
    }
    private static Hero readHero(JSONObject o,Project p){
        if(o==null)return null;
        JSONObject lk=o.optJSONObject("look");CharLook look=lk==null?null:CharLook.fromJson(lk);
        Hero h=look!=null?Hero.create(p.playerDef(),look):Hero.create(p.playerDef());
        h.cls=Rpg.heroClass(o.optString("class",h.cls.name));
        h.level=Math.max(1,o.optInt("level",1));h.xp=o.optInt("xp",0);h.gold=o.optInt("gold",0);h.maxHp=Math.max(1,o.optInt("maxHp",h.maxHp));
        h.sp=o.optInt("sp",h.sp);h.maxSp=o.optInt("maxSp",h.maxSp);
        JSONArray sc=o.optJSONArray("scores");if(sc!=null)for(int i=0;i<6&&i<sc.length();i++)h.scores[i]=sc.optInt(i,h.scores[i]);
        h.inventory.clear();h.weapon=h.armour=h.shield=null;
        JSONArray inv=o.optJSONArray("inventory");
        if(inv!=null)for(int i=0;i<inv.length();i++){JSONObject j=inv.optJSONObject(i);if(j==null)continue;Rpg.Item it=Rpg.item(j.optString("id"));if(it!=null)h.inventory.add(new Hero.Stack(it,Math.max(1,j.optInt("count",1))));}
        h.weapon=Rpg.item(o.optString("weapon",""));h.armour=Rpg.item(o.optString("armour",""));h.shield=Rpg.item(o.optString("shield",""));
        h.refreshSpells();h.selectedSpell=Math.max(0,o.optInt("selectedSpell",0));if(h.selectedSpell>=h.spells.size())h.selectedSpell=0;
        return h;
    }
    private static JSONObject mapJson(MapState m)throws Exception{
        JSONObject o=new JSONObject();o.put("kills",m.kills);o.put("items",m.items);o.put("secretCount",m.secretCount);
        JSONArray se=new JSONArray();if(m.secrets!=null)for(boolean b:m.secrets)se.put(b);o.put("secrets",se);
        JSONArray th=new JSONArray();
        if(m.things!=null)for(ThingState t:m.things){
            if(t==null){th.put(JSONObject.NULL);continue;}
            JSONObject j=new JSONObject();j.put("gone",t.gone);j.put("alive",t.alive);j.put("visible",t.visible);j.put("x",(double)t.x);j.put("y",(double)t.y);j.put("a",(double)t.angle);
            j.put("hp",t.health);j.put("spr",t.sprite);j.put("st",t.state==null?"":t.state);
            if(t.vars!=null&&!t.vars.isEmpty()){JSONObject v=new JSONObject();for(Map.Entry<String,Float> e:t.vars.entrySet())v.put(e.getKey(),(double)e.getValue());j.put("vars",v);}
            th.put(j);}
        o.put("things",th);return o;
    }
    private static MapState readMap(JSONObject o){
        MapState m=new MapState();m.kills=o.optInt("kills",0);m.items=o.optInt("items",0);m.secretCount=o.optInt("secretCount",0);
        JSONArray se=o.optJSONArray("secrets");if(se!=null){m.secrets=new boolean[se.length()];for(int i=0;i<se.length();i++)m.secrets[i]=se.optBoolean(i,false);}
        JSONArray th=o.optJSONArray("things");
        if(th!=null){m.things=new ThingState[th.length()];
            for(int i=0;i<th.length();i++){JSONObject j=th.optJSONObject(i);if(j==null)continue;ThingState t=new ThingState();
                t.gone=j.optBoolean("gone",false);t.alive=j.optBoolean("alive",true);t.visible=j.optBoolean("visible",true);t.x=(float)j.optDouble("x",0);t.y=(float)j.optDouble("y",0);t.angle=(float)j.optDouble("a",0);
                t.health=j.optInt("hp",1);t.sprite=j.optInt("spr",0);t.state=j.optString("st","");
                JSONObject v=j.optJSONObject("vars");if(v!=null){t.vars=new HashMap<>();Iterator<String> k=v.keys();while(k.hasNext()){String key=k.next();t.vars.put(key,(float)v.optDouble(key,0));}}
                m.things[i]=t;}}
        return m;
    }
    static String formatTime(float seconds){int s=(int)seconds,h=s/3600,mn=(s/60)%60;return h>0?String.format(java.util.Locale.US,"%d:%02d:%02d",h,mn,s%60):String.format(java.util.Locale.US,"%d:%02d",mn,s%60);}
}
