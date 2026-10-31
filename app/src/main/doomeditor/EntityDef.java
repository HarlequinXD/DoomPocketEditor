package com.retro.doomeditor;

import org.json.JSONObject;

/** An entity type in the project: appearance, physical properties and its FSM. */
final class EntityDef {
    static final int PLAYER=0,MONSTER=1,PICKUP=2,DECORATION=3,TRIGGER=4;
    static final String[] KIND_NAMES={"Player","Monster","Pickup","Decoration","Trigger"};
    String id,name;
    int kind=DECORATION,sprite=Sprites.COLUMN,health=100;
    float radius=.4f,height=1.4f,speed=3f;
    boolean solid=true,shootable=false,countKill=false,countItem=false;
    FSMGraph fsm=new FSMGraph();

    EntityDef(String id,String name,int kind,int sprite){this.id=id;this.name=name;this.kind=kind;this.sprite=sprite;}
    EntityDef copy(String newId,String newName){
        EntityDef d=new EntityDef(newId,newName,kind,sprite);d.health=health;d.radius=radius;d.height=height;d.speed=speed;d.solid=solid;d.shootable=shootable;d.countKill=countKill;d.countItem=countItem;d.fsm=fsm.copy();return d;
    }
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("kind",kind);o.put("sprite",sprite);o.put("health",health);
        o.put("radius",radius);o.put("height",height);o.put("speed",speed);o.put("solid",solid);o.put("shootable",shootable);o.put("countKill",countKill);o.put("countItem",countItem);
        o.put("fsm",fsm.toJson());return o;
    }
    static EntityDef fromJson(JSONObject o){
        EntityDef d=new EntityDef(o.optString("id","e"+System.nanoTime()),o.optString("name","Entity"),o.optInt("kind",DECORATION),o.optInt("sprite",Sprites.COLUMN));
        d.health=o.optInt("health",100);d.radius=(float)o.optDouble("radius",.4);d.height=(float)o.optDouble("height",1.4);d.speed=(float)o.optDouble("speed",3);
        d.solid=bool(o,"solid",true);d.shootable=bool(o,"shootable",false);d.countKill=bool(o,"countKill",false);d.countItem=bool(o,"countItem",false);
        JSONObject f=o.optJSONObject("fsm");if(f!=null)d.fsm=FSMGraph.fromJson(f);
        return d;
    }
    private static boolean bool(JSONObject o,String k,boolean def){String v=o.optString(k,String.valueOf(def));return v.equals("true")||v.equals("1");}
}
