package com.example.doomeditor;
import org.json.*;import java.util.*;
/** Sector polygons in an unbounded 2D coordinate plane. Coordinates are grid units. */
public class World {
 public static class Sector {public final ArrayList<float[]> points=new ArrayList<>();public float floor=0,ceiling=3;public int light=180;public String name="Sector";}
 public final ArrayList<Sector> sectors=new ArrayList<>();public final ArrayList<float[]> draft=new ArrayList<>();
 public float px=3,py=3,angle=0;public int selected=-1;
 public void addPoint(float x,float y){draft.add(new float[]{x,y});}
 public boolean close(){if(draft.size()<3)return false;Sector s=new Sector();s.name="Sector "+(sectors.size()+1);s.points.addAll(draft);sectors.add(s);selected=sectors.size()-1;draft.clear();return true;}
 public void undoPoint(){if(!draft.isEmpty())draft.remove(draft.size()-1);}
 public JSONObject json()throws JSONException{JSONObject root=new JSONObject();root.put("px",px);root.put("py",py);root.put("angle",angle);JSONArray all=new JSONArray();for(Sector s:sectors){JSONObject o=new JSONObject();o.put("name",s.name);o.put("floor",s.floor);o.put("ceiling",s.ceiling);o.put("light",s.light);JSONArray pts=new JSONArray();for(float[] v:s.points){JSONArray a=new JSONArray();a.put(v[0]);a.put(v[1]);pts.put(a);}o.put("points",pts);all.put(o);}root.put("sectors",all);return root;}
 public static World parse(String raw)throws JSONException{JSONObject root=new JSONObject(raw);World w=new World();w.px=(float)root.optDouble("px",3);w.py=(float)root.optDouble("py",3);w.angle=(float)root.optDouble("angle");JSONArray all=root.optJSONArray("sectors");if(all!=null)for(int i=0;i<all.length();i++){JSONObject o=all.getJSONObject(i);Sector s=new Sector();s.name=o.optString("name","Sector");s.floor=(float)o.optDouble("floor",0);s.ceiling=(float)o.optDouble("ceiling",3);s.light=o.optInt("light",180);JSONArray pts=o.getJSONArray("points");for(int j=0;j<pts.length();j++){JSONArray a=pts.getJSONArray(j);s.points.add(new float[]{(float)a.getDouble(0),(float)a.getDouble(1)});}w.sectors.add(s);}return w;}
 public static World demo(){World w=new World();float[][] pts={{0,0},{12,0},{12,9},{0,9}};Sector s=new Sector();s.name="Starting Room";for(float[] v:pts)s.points.add(v);w.sectors.add(s);w.px=5;w.py=5;return w;}
 public boolean inside(float x,float y){for(Sector s:sectors){boolean inside=false;for(int i=0,j=s.points.size()-1;i<s.points.size();j=i++){float[] a=s.points.get(i),b=s.points.get(j);if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])inside=!inside;}if(inside)return true;}return false;}
}
