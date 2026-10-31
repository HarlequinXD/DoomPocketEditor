package com.retro.doomeditor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;

public class Level {
    static final int W = 24, H = 24;
    static final int EMPTY = 0, WALL = 1;
    int[][] cells = new int[H][W];
    int[][] wallTex = new int[H][W];
    int[][] floorTex = new int[H][W];
    int[][] ceilingTex = new int[H][W];
    // Independent polygon map: vertices are shared by linedefs; sectors reference vertex indices.
    ArrayList<Vertex> vertices=new ArrayList<>();
    ArrayList<Sector> sectors=new ArrayList<>();
    static class Vertex {float x,y;Vertex(float x,float y){this.x=x;this.y=y;}}
    static class Sector {
        ArrayList<Integer> loop=new ArrayList<>();float floor=0,ceiling=2.5f;
        int floorMaterial=1,ceilingMaterial=2,wallMaterial=3,light=192;
    }
    static class LineDef {int from,to,front,back;LineDef(int a,int b,int f,int k){from=a;to=b;front=f;back=k;}}
    ArrayList<LineDef> linedefs(){
        ArrayList<LineDef> out=new ArrayList<>();
        for(int si=0;si<sectors.size();si++){Sector sec=sectors.get(si);for(int j=0;j<sec.loop.size();j++){
            int a=sec.loop.get(j),b=sec.loop.get((j+1)%sec.loop.size());boolean found=false;
            for(LineDef line:out)if(line.from==b&&line.to==a){line.back=si;found=true;break;}
            if(!found)out.add(new LineDef(a,b,si,-1));
        }}return out;
    }
    int addVertex(float x,float y){for(int i=0;i<vertices.size();i++)if(Math.hypot(vertices.get(i).x-x,vertices.get(i).y-y)<.17)return i;vertices.add(new Vertex(x,y));return vertices.size()-1;}
    boolean contains(Sector sec,float x,float y){boolean inside=false;for(int i=0,j=sec.loop.size()-1;i<sec.loop.size();j=i++){
        Vertex a=vertices.get(sec.loop.get(i)),b=vertices.get(sec.loop.get(j));
        if((a.y>y)!=(b.y>y)&&x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)inside=!inside;
    }return inside;}
    int sectorAt(float x,float y){for(int i=sectors.size()-1;i>=0;i--)if(contains(sectors.get(i),x,y))return i;return -1;}
    boolean polygonMove(float x,float y){int si=sectorAt(x,y);return si>=0&&sectors.get(si).floor<1.2f&&sectors.get(si).ceiling>1.6f;}
    float playerX=4.5f, playerY=4.5f, facing=0;
    ArrayList<Thing> things=new ArrayList<>();
    static class Thing {
        float x,y; int type;
        Thing(float x,float y,int type) { this.x=x; this.y=y; this.type=type; }
    }
    Level(){
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) {
            if (x==0||y==0||x==W-1||y==H-1) cells[y][x]=WALL;
            wallTex[y][x]=1; floorTex[y][x]=1; ceilingTex[y][x]=2;
        }
        for (int x=2;x<13;x++) {cells[2][x]=1;cells[11][x]=1;}
        for (int y=2;y<12;y++) {cells[y][2]=1;cells[y][12]=1;}
        cells[6][12]=0;cells[7][12]=0;
        for(int x=13;x<21;x++){cells[4][x]=1;cells[15][x]=1;}
        for(int y=4;y<16;y++){cells[y][20]=1;}
        cells[4][15]=0;cells[11][8]=0;
        // Two example rooms with a shared portal edge.
        int a=addVertex(3,3),b=addVertex(10,3),c=addVertex(10,10),d=addVertex(3,10);
        Sector first=new Sector();Collections.addAll(first.loop,a,b,c,d);sectors.add(first);
        int e=addVertex(17,3),f=addVertex(17,10);
        Sector second=new Sector();Collections.addAll(second.loop,b,e,f,c);second.floor=.5f;second.ceiling=3.5f;second.wallMaterial=4;sectors.add(second);
        things.add(new Thing(8.5f,6.5f,0));
        things.add(new Thing(17.5f,10.5f,1));
    }
    boolean wall(float x,float y){int ix=(int)Math.floor(x),iy=(int)Math.floor(y);return ix<0||iy<0||ix>=W||iy>=H||cells[iy][ix]==WALL;}
    boolean canMove(float x,float y){float r=.19f;return !wall(x-r,y-r)&&!wall(x+r,y-r)&&!wall(x-r,y+r)&&!wall(x+r,y+r);}
    JSONObject toJson() throws Exception {
        JSONObject out=new JSONObject(); out.put("version",1);
        out.put("playerX",playerX);out.put("playerY",playerY);out.put("facing",facing);
        out.put("cells",encode(cells));out.put("wallTex",encode(wallTex));out.put("floorTex",encode(floorTex));out.put("ceilingTex",encode(ceilingTex));
        JSONArray arr=new JSONArray();for(Thing t:things){JSONObject o=new JSONObject();o.put("x",t.x);o.put("y",t.y);o.put("type",t.type);arr.put(o);}out.put("things",arr);
        JSONArray verts=new JSONArray();for(Vertex v:vertices){JSONArray xy=new JSONArray();xy.put(v.x);xy.put(v.y);verts.put(xy);}out.put("vertices",verts);
        JSONArray secs=new JSONArray();for(Sector sec:sectors){JSONObject obj=new JSONObject();JSONArray loop=new JSONArray();for(int id:sec.loop)loop.put(id);obj.put("loop",loop);obj.put("floor",sec.floor);obj.put("ceiling",sec.ceiling);obj.put("floorMaterial",sec.floorMaterial);obj.put("ceilingMaterial",sec.ceilingMaterial);obj.put("wallMaterial",sec.wallMaterial);obj.put("light",sec.light);secs.put(obj);}out.put("sectors",secs);out.put("version",3);return out;
    }
    static JSONArray encode(int[][] a){JSONArray rows=new JSONArray();for(int[] row:a){JSONArray r=new JSONArray();for(int x:row)r.put(x);rows.put(r);}return rows;}
    static void decode(JSONObject json,String key,int[][] a)throws Exception {JSONArray rows=json.getJSONArray(key);for(int y=0;y<H;y++){JSONArray r=rows.getJSONArray(y);for(int x=0;x<W;x++)a[y][x]=r.getInt(x);}}
    static Level fromJson(JSONObject json)throws Exception {Level l=new Level();decode(json,"cells",l.cells);decode(json,"wallTex",l.wallTex);decode(json,"floorTex",l.floorTex);decode(json,"ceilingTex",l.ceilingTex);l.playerX=(float)json.getDouble("playerX");l.playerY=(float)json.getDouble("playerY");l.facing=(float)json.getDouble("facing");l.things.clear();JSONArray a=json.getJSONArray("things");for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);l.things.add(new Thing((float)o.getDouble("x"),(float)o.getDouble("y"),o.getInt("type")));}if(json.has("vertices")&&json.has("sectors")){l.vertices.clear();l.sectors.clear();JSONArray verts=json.getJSONArray("vertices");for(int i=0;i<verts.length();i++){JSONArray xy=verts.getJSONArray(i);l.vertices.add(new Vertex((float)xy.getDouble(0),(float)xy.getDouble(1)));}JSONArray secs=json.getJSONArray("sectors");for(int i=0;i<secs.length();i++){JSONObject o=secs.getJSONObject(i);Sector sec=new Sector();JSONArray loop=o.getJSONArray("loop");for(int j=0;j<loop.length();j++)sec.loop.add(loop.getInt(j));sec.floor=(float)o.optDouble("floor",0);sec.ceiling=(float)o.optDouble("ceiling",2.5);sec.floorMaterial=o.optInt("floorMaterial",1);sec.ceilingMaterial=o.optInt("ceilingMaterial",2);sec.wallMaterial=o.optInt("wallMaterial",3);sec.light=o.optInt("light",192);l.sectors.add(sec);}}if(json.has("vertices")&&json.has("sectors")){l.vertices.clear();l.sectors.clear();JSONArray verts=json.getJSONArray("vertices");for(int i=0;i<verts.length();i++){JSONArray xy=verts.getJSONArray(i);l.vertices.add(new Vertex((float)xy.getDouble(0),(float)xy.getDouble(1)));}JSONArray secs=json.getJSONArray("sectors");for(int i=0;i<secs.length();i++){JSONObject o=secs.getJSONObject(i);Sector sec=new Sector();JSONArray loop=o.getJSONArray("loop");for(int j=0;j<loop.length();j++)sec.loop.add(loop.getInt(j));sec.floor=(float)o.optDouble("floor",0);sec.ceiling=(float)o.optDouble("ceiling",2.5);sec.floorMaterial=o.optInt("floorMaterial",1);sec.ceilingMaterial=o.optInt("ceilingMaterial",2);sec.wallMaterial=o.optInt("wallMaterial",3);sec.light=o.optInt("light",192);l.sectors.add(sec);}}return l;}
}
