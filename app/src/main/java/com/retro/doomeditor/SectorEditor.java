package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import org.json.JSONObject;

/** Validated, undoable sector operations. Coordinates are world units. */
public final class SectorEditor {
    public Level level;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private static final float EPS=.0001f;
    public String error="";
    public SectorEditor(Level level){this.level=level;}
    private String snapshot(){try{return level.toJson().toString();}catch(Exception e){return null;}}
    private void push(){String s=snapshot();if(s!=null){undo.push(s);if(undo.size()>60)undo.removeLast();redo.clear();}}
    public boolean undo(){if(undo.isEmpty())return false;String s=snapshot();try{Level next=Level.fromJson(new JSONObject(undo.pop()));if(s!=null)redo.push(s);level=next;return true;}catch(Exception e){return false;}}
    public boolean redo(){if(redo.isEmpty())return false;String s=snapshot();try{Level next=Level.fromJson(new JSONObject(redo.pop()));if(s!=null)undo.push(s);level=next;return true;}catch(Exception e){return false;}}
    public static float snap(float n,float step){return Math.round(n/step)*step;}
    public float[] snapPoint(float x,float y,float grid){
        float sx=snap(x,grid),sy=snap(y,grid),best=grid*.55f;
        for(Level.Vertex v:level.vertices){float d=(float)Math.hypot(v.x-x,v.y-y);if(d<best){best=d;sx=v.x;sy=v.y;}}
        return new float[]{sx,sy};
    }
    private static float cross(float ax,float ay,float bx,float by,float cx,float cy){return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);}
    private static boolean onSegment(float ax,float ay,float bx,float by,float px,float py){return Math.abs(cross(ax,ay,bx,by,px,py))<EPS&&px>=Math.min(ax,bx)-EPS&&px<=Math.max(ax,bx)+EPS&&py>=Math.min(ay,by)-EPS&&py<=Math.max(ay,by)+EPS;}
    private static boolean intersects(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        float a=cross(ax,ay,bx,by,cx,cy),b=cross(ax,ay,bx,by,dx,dy),c=cross(cx,cy,dx,dy,ax,ay),d=cross(cx,cy,dx,dy,bx,by);
        if((a>EPS&&b<-EPS||a<-EPS&&b>EPS)&&(c>EPS&&d<-EPS||c<-EPS&&d>EPS))return true;
        return onSegment(ax,ay,bx,by,cx,cy)||onSegment(ax,ay,bx,by,dx,dy)||onSegment(cx,cy,dx,dy,ax,ay)||onSegment(cx,cy,dx,dy,bx,by);
    }
    private static boolean properCross(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        float a=cross(ax,ay,bx,by,cx,cy),b=cross(ax,ay,bx,by,dx,dy),c=cross(cx,cy,dx,dy,ax,ay),d=cross(cx,cy,dx,dy,bx,by);
        return (a>EPS&&b<-EPS||a<-EPS&&b>EPS)&&(c>EPS&&d<-EPS||c<-EPS&&d>EPS);
    }
    private static boolean inside(ArrayList<float[]> poly,float x,float y){boolean result=false;for(int i=0,j=poly.size()-1;i<poly.size();j=i++){
        float[] a=poly.get(i),b=poly.get(j);if(onSegment(a[0],a[1],b[0],b[1],x,y))return true;
        if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])result=!result;
    }return result;}
    private ArrayList<float[]> coords(Level.Sector s){ArrayList<float[]> a=new ArrayList<>();for(int id:s.loop){if(id<0||id>=level.vertices.size())return new ArrayList<>();Level.Vertex v=level.vertices.get(id);a.add(new float[]{v.x,v.y});}return a;}
    private boolean validate(ArrayList<float[]> poly,int skipSector){
        error="";int n=poly.size();if(n<3){error="Need at least three corners";return false;}
        float area=0;
        for(int i=0;i<n;i++){float[] a=poly.get(i),b=poly.get((i+1)%n);if(!Float.isFinite(a[0])||!Float.isFinite(a[1])){error="Invalid coordinates";return false;}
            if(Math.hypot(a[0]-b[0],a[1]-b[1])<.08){error="Zero-length edge";return false;}area+=a[0]*b[1]-a[1]*b[0];}
        if(Math.abs(area)<.02){error="Sector has no area";return false;}
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
            float[] a=poly.get(i),b=poly.get((i+1)%n),c=poly.get(j),d=poly.get((j+1)%n);
            if(j==i+1||(i==0&&j==n-1))continue;
            if(intersects(a[0],a[1],b[0],b[1],c[0],c[1],d[0],d[1])){error="Sector edges intersect";return false;}
        }
        for(int k=0;k<level.sectors.size();k++){
            if(k==skipSector)continue;ArrayList<float[]> other=coords(level.sectors.get(k));if(other.size()<3)continue;
            for(int i=0;i<n;i++)for(int j=0;j<other.size();j++){
                float[] a=poly.get(i),b=poly.get((i+1)%n),c=other.get(j),d=other.get((j+1)%other.size());
                if(properCross(a[0],a[1],b[0],b[1],c[0],c[1],d[0],d[1])){error="Crosses an existing wall";return false;}
            }
            // Allow shared boundaries, but disallow rooms overlapping in their interiors.
            float[] a=poly.get(0),b=poly.get(1);float mx=(a[0]+b[0])*.5f,my=(a[1]+b[1])*.5f;
            float nx=-(b[1]-a[1]),ny=b[0]-a[0],len=(float)Math.hypot(nx,ny);if(len>EPS){nx*=.001f/len;ny*=.001f/len;float sign=area>0?1:-1;
                if(inside(other,mx+nx*sign,my+ny*sign)){error="Overlaps another sector";return false;}}
            float[] c=other.get(0),d=other.get(1);float cx=(c[0]+d[0])*.5f,cy=(c[1]+d[1])*.5f;
            float ox=-(d[1]-c[1]),oy=d[0]-c[0],ol=(float)Math.hypot(ox,oy);if(ol>EPS){ox*=.001f/ol;oy*=.001f/ol;
                float oa=0;for(int i=0;i<other.size();i++){float[] p=other.get(i),q=other.get((i+1)%other.size());oa+=p[0]*q[1]-p[1]*q[0];}
                if(inside(poly,cx+ox*(oa>0?1:-1),cy+oy*(oa>0?1:-1))){error="Contains an existing sector";return false;}}
        }
        return true;
    }
    public boolean addSector(ArrayList<float[]> points){if(!validate(points,-1))return false;push();Level.Sector s=new Level.Sector();for(float[] p:points)s.loop.add(level.addVertex(p[0],p[1]));level.sectors.add(s);return true;}
    public boolean deleteSector(int index){if(index<0||index>=level.sectors.size())return false;push();level.sectors.remove(index);return true;}
    public boolean changeHeight(int index,float floorDelta,float ceilingDelta){if(index<0||index>=level.sectors.size())return false;Level.Sector s=level.sectors.get(index);if(s.ceiling+ceilingDelta-s.floor-floorDelta<.5f){error="Not enough clearance";return false;}push();s.floor+=floorDelta;s.ceiling+=ceilingDelta;return true;}
    public boolean moveVertex(int id,float x,float y){if(id<0||id>=level.vertices.size())return false;Level.Vertex v=level.vertices.get(id);float oldX=v.x,oldY=v.y;v.x=x;v.y=y;
        for(int i=0;i<level.sectors.size();i++)if(level.sectors.get(i).loop.contains(id)&&!validate(coords(level.sectors.get(i)),i)){v.x=oldX;v.y=oldY;return false;}
        v.x=oldX;v.y=oldY;push();v.x=x;v.y=y;return true;}
    public boolean splitWall(int sector,int edge,float x,float y){if(sector<0||sector>=level.sectors.size())return false;Level.Sector s=level.sectors.get(sector);if(edge<0||edge>=s.loop.size())return false;
        Level.Vertex a=level.vertices.get(s.loop.get(edge)),b=level.vertices.get(s.loop.get((edge+1)%s.loop.size()));if(!onSegment(a.x,a.y,b.x,b.y,x,y)||Math.hypot(x-a.x,y-a.y)<.08||Math.hypot(x-b.x,y-b.y)<.08){error="Point must lie on wall";return false;}
        push();int id=level.addVertex(x,y);s.loop.add(edge+1,id);
        for(Level.Sector other:level.sectors){if(other==s)continue;for(int j=0;j<other.loop.size();j++)if(other.loop.get(j)==s.loop.get((edge+2)%s.loop.size())&&other.loop.get((j+1)%other.loop.size())==s.loop.get(edge)){other.loop.add(j+1,id);break;}}
        return true;
    }
    public boolean duplicateSector(int index,float dx,float dy){if(index<0||index>=level.sectors.size())return false;Level.Sector s=level.sectors.get(index);ArrayList<float[]> poly=coords(s);for(float[] p:poly){p[0]+=dx;p[1]+=dy;}if(!validate(poly,-1))return false;
        push();Level.Sector copy=new Level.Sector();copy.floor=s.floor;copy.ceiling=s.ceiling;copy.floorMaterial=s.floorMaterial;copy.ceilingMaterial=s.ceilingMaterial;copy.wallMaterial=s.wallMaterial;
        for(float[] p:poly)copy.loop.add(level.addVertex(p[0],p[1]));level.sectors.add(copy);return true;}
    public String validateMap(){for(int i=0;i<level.sectors.size();i++)if(!validate(coords(level.sectors.get(i)),i))return "Sector "+(i+1)+": "+error;return "Geometry valid";}
}
