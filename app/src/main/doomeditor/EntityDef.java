package com.retro.doomeditor;

import org.json.JSONObject;

/** An entity type in the project: appearance, physical properties and its FSM. */
final class EntityDef {
    static final int PLAYER=0,MONSTER=1,PICKUP=2,DECORATION=3,TRIGGER=4,NPC=5;
    static final String[] KIND_NAMES={"Player","Monster","Pickup","Decoration","Trigger","NPC"};
    /** Creatures animate (walk, attack, pain, death frames). */
    boolean animated(){return kind==MONSTER||kind==PLAYER||kind==NPC;}
    String id,name;
    /** Optional user-drawn sprite id; overrides the built-in sprite when set. */
    String customSprite="";
    /** Built-in sprite actually shown: the default hero sprite follows the chosen class. */
    int lookSprite(){return kind==PLAYER&&sprite==Sprites.HERO&&(customSprite==null||customSprite.isEmpty())?Sprites.heroFor(heroClass):sprite;}
    int kind=DECORATION,sprite=Sprites.COLUMN,health=100;
    float radius=.4f,height=1.4f,speed=3f;
    boolean solid=true,shootable=false,countKill=false,countItem=false;
    // ---- RPG stats
    /** Player only: class, starting level and gold; abilities override the class array when customAbilities is set. */
    String heroClass="Fighter";int level=1,startGold=15;boolean customAbilities;
    /** Creatures: armour class, hit dice (empty = use health), to-hit bonus, damage dice and type, XP awarded when slain. */
    int ac=10,attackBonus=3,damageType=Rpg.SLASHING,xp=0;String hitDice="",damage="1d6";
    int[] abilities={10,10,10,10,10,10};
    FSMGraph fsm=new FSMGraph();
    /** Atmosphere: light preset (Atmosphere.GLOWS, 0 = automatic), radius override (0 = preset) and particle emitter (Atmosphere.PARTICLES). */
    int glow=Atmosphere.G_AUTO;float glowRadius;int particles;
    /** Sets creature stats in one go (used by the default content). */
    EntityDef stats(int ac,String hitDice,int attackBonus,String damage,int damageType,int xp){this.ac=ac;this.hitDice=hitDice;this.attackBonus=attackBonus;this.damage=damage;this.damageType=damageType;this.xp=xp;return this;}
    EntityDef scores(int... a){for(int i=0;i<6&&i<a.length;i++)abilities[i]=a[i];return this;}

    EntityDef(String id,String name,int kind,int sprite){this.id=id;this.name=name;this.kind=kind;this.sprite=sprite;}
    EntityDef copy(String newId,String newName){
        EntityDef d=new EntityDef(newId,newName,kind,sprite);d.health=health;d.radius=radius;d.height=height;d.speed=speed;d.solid=solid;d.shootable=shootable;d.countKill=countKill;d.countItem=countItem;d.heroClass=heroClass;d.level=level;d.startGold=startGold;d.customAbilities=customAbilities;d.ac=ac;d.hitDice=hitDice;d.attackBonus=attackBonus;d.damage=damage;d.damageType=damageType;d.xp=xp;d.abilities=abilities.clone();d.customSprite=customSprite;d.fsm=fsm.copy();d.glow=glow;d.glowRadius=glowRadius;d.particles=particles;return d;
    }
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("kind",kind);o.put("sprite",sprite);o.put("health",health);
        o.put("radius",radius);o.put("height",height);o.put("speed",speed);o.put("solid",solid);o.put("shootable",shootable);o.put("countKill",countKill);o.put("countItem",countItem);o.put("customSprite",customSprite==null?"":customSprite);
        JSONObject r=new JSONObject();r.put("class",heroClass);r.put("level",level);r.put("gold",startGold);r.put("customAbilities",customAbilities);
        r.put("ac",ac);r.put("hitDice",hitDice);r.put("attack",attackBonus);r.put("damage",damage);r.put("damageType",damageType);r.put("xp",xp);
        org.json.JSONArray ab=new org.json.JSONArray();for(int v:abilities)ab.put(v);r.put("abilities",ab);o.put("rpg",r);
        o.put("glow",glow);o.put("glowRadius",(double)glowRadius);o.put("particles",particles);
        o.put("fsm",fsm.toJson());return o;
    }
    static EntityDef fromJson(JSONObject o){
        EntityDef d=new EntityDef(o.optString("id","e"+System.nanoTime()),o.optString("name","Entity"),o.optInt("kind",DECORATION),o.optInt("sprite",Sprites.COLUMN));
        d.health=o.optInt("health",100);d.radius=(float)o.optDouble("radius",.4);d.height=(float)o.optDouble("height",1.4);d.speed=(float)o.optDouble("speed",3);
        d.solid=bool(o,"solid",true);d.shootable=bool(o,"shootable",false);d.countKill=bool(o,"countKill",false);d.countItem=bool(o,"countItem",false);d.customSprite=o.optString("customSprite","");
        JSONObject r=o.optJSONObject("rpg");
        if(r!=null){d.heroClass=r.optString("class","Fighter");d.level=r.optInt("level",1);d.startGold=r.optInt("gold",15);d.customAbilities=bool(r,"customAbilities",false);
            d.ac=r.optInt("ac",10);d.hitDice=r.optString("hitDice","");d.attackBonus=r.optInt("attack",3);d.damage=r.optString("damage","1d6");d.damageType=r.optInt("damageType",0);d.xp=r.optInt("xp",0);
            org.json.JSONArray ab=r.optJSONArray("abilities");if(ab!=null)for(int i=0;i<6&&i<ab.length();i++)d.abilities[i]=ab.optInt(i,10);}
        else if(d.kind==MONSTER){d.xp=Math.max(10,d.health);d.ac=11;d.attackBonus=3;}   // older projects: give monsters sensible defaults
        d.glow=Math.max(0,Math.min(Atmosphere.GLOWS.length-1,o.optInt("glow",Atmosphere.G_AUTO)));d.glowRadius=(float)o.optDouble("glowRadius",0);d.particles=Math.max(0,Math.min(Atmosphere.PARTICLES.length-1,o.optInt("particles",0)));
        JSONObject f=o.optJSONObject("fsm");if(f!=null)d.fsm=FSMGraph.fromJson(f);
        return d;
    }
    private static boolean bool(JSONObject o,String k,boolean def){String v=o.optString(k,String.valueOf(def));return v.equals("true")||v.equals("1");}
}
