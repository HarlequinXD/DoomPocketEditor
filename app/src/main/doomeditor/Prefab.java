package com.retro.doomeditor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * A reusable chunk of map: sectors (with their heights, materials, light and specials) plus the
 * entities standing in them. Coordinates are relative to the chunk's top-left corner, so a prefab
 * can be stamped anywhere with the cursor, turned in 90° steps, in any map of the project.
 */
final class Prefab {
    static final class Sec {float[] pts;float floor,ceiling=2.5f;int floorMat=1,ceilMat=2,wallMat=3,light=192,special,tag;}
    static final class Th {float x,y,angle;String def;}
    String id,name="Prefab";
    final ArrayList<Sec> secs=new ArrayList<>();
    final ArrayList<Th> things=new ArrayList<>();
    float w,h;

    // ---------------------------------------------------------------- capture
    /** Everything fully inside the rectangle; null when no sector is. */
    static Prefab capture(Level lv,float x0,float y0,float x1,float y1){
        float l=Math.min(x0,x1)-.01f,t=Math.min(y0,y1)-.01f,r=Math.max(x0,x1)+.01f,b=Math.max(y0,y1)+.01f;
        ArrayList<Level.Sector> picked=new ArrayList<>();float mx=1e9f,my=1e9f,Mx=-1e9f,My=-1e9f;
        for(Level.Sector s:lv.sectors){boolean in=s.loop.size()>=3;
            for(int k:s.loop){Level.Vertex v=lv.vertices.get(k);if(v.x<l||v.x>r||v.y<t||v.y>b){in=false;break;}}
            if(!in)continue;picked.add(s);
            for(int k:s.loop){Level.Vertex v=lv.vertices.get(k);mx=Math.min(mx,v.x);my=Math.min(my,v.y);Mx=Math.max(Mx,v.x);My=Math.max(My,v.y);}}
        if(picked.isEmpty())return null;
        float ox=(float)Math.floor(mx),oy=(float)Math.floor(my);
        Prefab p=new Prefab();p.w=(float)Math.ceil(Mx)-ox;p.h=(float)Math.ceil(My)-oy;
        for(Level.Sector s:picked){Sec q=new Sec();q.pts=new float[s.loop.size()*2];
            for(int i=0;i<s.loop.size();i++){Level.Vertex v=lv.vertices.get(s.loop.get(i));q.pts[i*2]=v.x-ox;q.pts[i*2+1]=v.y-oy;}
            q.floor=s.floor;q.ceiling=s.ceiling;q.floorMat=s.floorMaterial;q.ceilMat=s.ceilingMaterial;q.wallMat=s.wallMaterial;q.light=s.light;q.special=s.special;q.tag=s.tag;p.secs.add(q);}
        for(Level.Thing th:lv.things){boolean in=false;for(Level.Sector s:picked)if(lv.contains(s,th.x,th.y)){in=true;break;}
            if(!in)continue;Th q=new Th();q.x=th.x-ox;q.y=th.y-oy;q.angle=th.angle;q.def=th.def;p.things.add(q);}
        return p;
    }

    // ---------------------------------------------------------------- placement
    /** Size after turning {@code rot} quarter turns. */
    float w(int rot){return (rot&1)==0?w:h;}
    float h(int rot){return (rot&1)==0?h:w;}
    /** Turns a local point and moves it to (ox,oy). */
    void place(float x,float y,int rot,float ox,float oy,float[] out){
        float px=x,py=y,cw=w,ch=h;
        for(int k=0;k<(rot&3);k++){float nx=ch-py,ny=px;px=nx;py=ny;float t=cw;cw=ch;ch=t;}
        out[0]=ox+px;out[1]=oy+py;
    }
    ArrayList<ArrayList<float[]>> polys(int rot,float ox,float oy){
        ArrayList<ArrayList<float[]>> out=new ArrayList<>();
        for(Sec s:secs){ArrayList<float[]> poly=new ArrayList<>();
            for(int i=0;i+1<s.pts.length;i+=2){float[] pt=new float[2];place(s.pts[i],s.pts[i+1],rot,ox,oy,pt);poly.add(pt);}
            out.add(poly);}
        return out;
    }

    // ---------------------------------------------------------------- built-in prefabs
    static Prefab rect(String name,float[][] rects,int[] specials,float[] floors){
        Prefab p=new Prefab();p.name=name;p.id="builtin:"+name;
        for(int i=0;i<rects.length;i++){float[] r=rects[i];Sec s=new Sec();s.pts=new float[]{r[0],r[1],r[2],r[1],r[2],r[3],r[0],r[3]};
            if(specials!=null)s.special=specials[i];if(floors!=null){s.floor=floors[i];s.ceiling=Math.max(2.5f,floors[i]+2.5f);}
            p.secs.add(s);p.w=Math.max(p.w,r[2]);p.h=Math.max(p.h,r[3]);}
        return p;
    }
    static ArrayList<Prefab> builtins(){
        ArrayList<Prefab> b=new ArrayList<>();
        b.add(rect("Small room 4×4",new float[][]{{0,0,4,4}},null,null));
        b.add(rect("Corridor 2×8",new float[][]{{0,0,2,8}},null,null));
        b.add(rect("Two rooms with a door",new float[][]{{0,0,4,4},{4,1,5,3},{5,0,9,4}},new int[]{0,Level.SP_DOOR,0},null));
        b.add(rect("Crossroads",new float[][]{{2,0,4,2},{0,2,2,4},{2,2,4,4},{4,2,6,4},{2,4,4,6}},null,null));
        Prefab st=rect("Stairs up to a landing",new float[][]{{0,0,4,4},{4,0,5,4},{5,0,6,4},{6,0,7,4},{7,0,10,4}},null,new float[]{0,.25f,.5f,.75f,1f});
        for(Sec s:st.secs)s.ceiling=3.75f;b.add(st);
        b.add(rect("Room with an exit",new float[][]{{0,0,4,4},{4,1,6,3}},new int[]{0,Level.SP_EXIT},null));
        b.add(rect("Room with a lift",new float[][]{{0,0,5,5},{5,1,7,3}},new int[]{0,Level.SP_LIFT},null));
        return b;
    }

    // ---------------------------------------------------------------- JSON
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("w",(double)w);o.put("h",(double)h);
        JSONArray sa=new JSONArray();
        for(Sec s:secs){JSONObject j=new JSONObject();JSONArray pa=new JSONArray();for(float f:s.pts)pa.put((double)f);j.put("pts",pa);
            j.put("floor",(double)s.floor);j.put("ceiling",(double)s.ceiling);j.put("fm",s.floorMat);j.put("cm",s.ceilMat);j.put("wm",s.wallMat);j.put("light",s.light);j.put("special",s.special);j.put("tag",s.tag);sa.put(j);}
        o.put("sectors",sa);
        JSONArray ta=new JSONArray();for(Th t:things){JSONObject j=new JSONObject();j.put("x",(double)t.x);j.put("y",(double)t.y);j.put("angle",(double)t.angle);j.put("def",t.def);ta.put(j);}
        o.put("things",ta);return o;
    }
    static Prefab fromJson(JSONObject o){
        Prefab p=new Prefab();p.id=o.optString("id","prefab");p.name=o.optString("name","Prefab");p.w=(float)o.optDouble("w",1);p.h=(float)o.optDouble("h",1);
        JSONArray sa=o.optJSONArray("sectors");
        if(sa!=null)for(int i=0;i<sa.length();i++){JSONObject j=sa.optJSONObject(i);if(j==null)continue;JSONArray pa=j.optJSONArray("pts");if(pa==null||pa.length()<6)continue;
            Sec s=new Sec();s.pts=new float[pa.length()];for(int k=0;k<pa.length();k++)s.pts[k]=(float)pa.optDouble(k,0);
            s.floor=(float)j.optDouble("floor",0);s.ceiling=(float)j.optDouble("ceiling",2.5);s.floorMat=j.optInt("fm",1);s.ceilMat=j.optInt("cm",2);s.wallMat=j.optInt("wm",3);
            s.light=j.optInt("light",192);s.special=j.optInt("special",0);s.tag=j.optInt("tag",0);p.secs.add(s);}
        JSONArray ta=o.optJSONArray("things");
        if(ta!=null)for(int i=0;i<ta.length();i++){JSONObject j=ta.optJSONObject(i);if(j==null)continue;Th t=new Th();t.x=(float)j.optDouble("x",0);t.y=(float)j.optDouble("y",0);t.angle=(float)j.optDouble("angle",0);t.def=j.optString("def","");p.things.add(t);}
        return p;
    }
}
