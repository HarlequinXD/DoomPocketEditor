package com.retro.doomeditor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Polygon map: shared vertices, sectors as vertex loops, placed things and per-wall texture overrides. */
public class Level {
    static final int W = 24, H = 24;
    // Sector specials (Doom-style behaviours handled by the game framework).
    static final int SP_NONE=0, SP_DOOR=1, SP_LIFT=2, SP_DAMAGE=3, SP_EXIT=4, SP_SECRET=5, SP_FLICKER=6, SP_BLINK=7;
    static final String[] SPECIAL_NAMES={"None","Door","Lift","Damage floor","Exit","Secret","Flicker light","Blink light"};
    static final String[] KEY_NAMES={"No key","Blue key","Yellow key","Red key"};

    ArrayList<Vertex> vertices=new ArrayList<>();
    ArrayList<Sector> sectors=new ArrayList<>();
    ArrayList<Thing> things=new ArrayList<>();
    /** Wall texture overrides keyed by Renderer.wallKey(). */
    HashMap<String,Integer> wallOverrides=new HashMap<>();
    float playerX=4.5f, playerY=4.5f, facing=0;

    static class Vertex {float x,y;Vertex(float x,float y){this.x=x;this.y=y;}}
    static class Sector {
        ArrayList<Integer> loop=new ArrayList<>();float floor=0,ceiling=2.5f;
        int floorMaterial=1,ceilingMaterial=2,wallMaterial=3,light=192;
        int special=SP_NONE,tag=0;
    }
    static class LineDef {int from,to,front,back;LineDef(int a,int b,int f,int k){from=a;to=b;front=f;back=k;}}
    static class Thing {
        float x,y,angle; int type; String def;
        Thing(float x,float y,int type){this.x=x;this.y=y;this.type=type;this.def=type==1?"medkit":"trooper";}
        Thing(float x,float y,String def){this.x=x;this.y=y;this.def=def;}
    }

    int addVertex(float x,float y){for(int i=0;i<vertices.size();i++)if(Math.hypot(vertices.get(i).x-x,vertices.get(i).y-y)<.17)return i;vertices.add(new Vertex(x,y));return vertices.size()-1;}
    boolean contains(Sector sec,float x,float y){boolean inside=false;for(int i=0,j=sec.loop.size()-1;i<sec.loop.size();j=i++){
        Vertex a=vertices.get(sec.loop.get(i)),b=vertices.get(sec.loop.get(j));
        if((a.y>y)!=(b.y>y)&&x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)inside=!inside;
    }return inside;}
    int sectorAt(float x,float y){for(int i=sectors.size()-1;i>=0;i--)if(contains(sectors.get(i),x,y))return i;return -1;}

    Level(){
        // Two example rooms with a shared portal edge.
        int a=addVertex(3,3),b=addVertex(10,3),c=addVertex(10,10),d=addVertex(3,10);
        Sector first=new Sector();Collections.addAll(first.loop,a,b,c,d);sectors.add(first);
        int e=addVertex(17,3),f=addVertex(17,10);
        Sector second=new Sector();Collections.addAll(second.loop,b,e,f,c);second.floor=.5f;second.ceiling=3.5f;second.wallMaterial=4;sectors.add(second);
        things.add(new Thing(13.5f,6.5f,"trooper"));
        things.add(new Thing(8.5f,8.5f,"medkit"));
    }
    private Level(boolean empty){}
    static Level empty(){return new Level(true);}

    /** True when every sector references existing vertices and all coordinates are finite. */
    boolean isConsistent(){
        for(Vertex v:vertices)if(!Float.isFinite(v.x)||!Float.isFinite(v.y))return false;
        for(Sector s:sectors){if(s.loop.size()<3)return false;for(int id:s.loop)if(id<0||id>=vertices.size())return false;
            if(!Float.isFinite(s.floor)||!Float.isFinite(s.ceiling))return false;}
        for(Thing t:things)if(!Float.isFinite(t.x)||!Float.isFinite(t.y))return false;
        return Float.isFinite(playerX)&&Float.isFinite(playerY)&&Float.isFinite(facing);
    }
    Level copy(){try{return fromJson(toJson());}catch(Exception e){return this;}}

    JSONObject toJson() throws Exception {
        JSONObject out=new JSONObject(); out.put("version",4);
        out.put("playerX",playerX);out.put("playerY",playerY);out.put("facing",facing);
        JSONArray arr=new JSONArray();for(Thing t:things){JSONObject o=new JSONObject();o.put("x",t.x);o.put("y",t.y);o.put("angle",t.angle);o.put("def",t.def==null?"trooper":t.def);arr.put(o);}out.put("things",arr);
        JSONArray verts=new JSONArray();for(Vertex v:vertices){JSONArray xy=new JSONArray();xy.put(v.x);xy.put(v.y);verts.put(xy);}out.put("vertices",verts);
        JSONArray secs=new JSONArray();for(Sector sec:sectors){JSONObject obj=new JSONObject();JSONArray loop=new JSONArray();for(int id:sec.loop)loop.put(id);obj.put("loop",loop);
            obj.put("floor",sec.floor);obj.put("ceiling",sec.ceiling);obj.put("floorMaterial",sec.floorMaterial);obj.put("ceilingMaterial",sec.ceilingMaterial);obj.put("wallMaterial",sec.wallMaterial);obj.put("light",sec.light);
            obj.put("special",sec.special);obj.put("tag",sec.tag);secs.put(obj);}out.put("sectors",secs);
        JSONObject walls=new JSONObject();for(Map.Entry<String,Integer> e:wallOverrides.entrySet())walls.put(e.getKey(),(int)e.getValue());out.put("walls",walls);
        return out;
    }
    static Level fromJson(JSONObject json)throws Exception {
        Level l=new Level(true);
        l.playerX=(float)json.optDouble("playerX",4.5);l.playerY=(float)json.optDouble("playerY",4.5);l.facing=(float)json.optDouble("facing",0);
        JSONArray a=json.optJSONArray("things");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);
            Thing t=o.has("def")?new Thing((float)o.getDouble("x"),(float)o.getDouble("y"),o.optString("def","trooper")):new Thing((float)o.getDouble("x"),(float)o.getDouble("y"),o.optInt("type",0));
            t.angle=(float)o.optDouble("angle",0);l.things.add(t);}
        JSONArray verts=json.optJSONArray("vertices");
        if(verts!=null)for(int i=0;i<verts.length();i++){JSONArray xy=verts.getJSONArray(i);l.vertices.add(new Vertex((float)xy.getDouble(0),(float)xy.getDouble(1)));}
        JSONArray secs=json.optJSONArray("sectors");
        if(secs!=null)for(int i=0;i<secs.length();i++){JSONObject o=secs.getJSONObject(i);Sector sec=new Sector();JSONArray loop=o.getJSONArray("loop");for(int j=0;j<loop.length();j++)sec.loop.add(loop.getInt(j));
            sec.floor=(float)o.optDouble("floor",0);sec.ceiling=(float)o.optDouble("ceiling",2.5);sec.floorMaterial=o.optInt("floorMaterial",1);sec.ceilingMaterial=o.optInt("ceilingMaterial",2);sec.wallMaterial=o.optInt("wallMaterial",3);sec.light=o.optInt("light",192);
            sec.special=o.optInt("special",0);sec.tag=o.optInt("tag",0);l.sectors.add(sec);}
        JSONObject walls=json.optJSONObject("walls");
        if(walls!=null){Iterator<String> k=walls.keys();while(k.hasNext()){String key=k.next();l.wallOverrides.put(key,walls.optInt(key,0));}}
        return l;
    }
}
