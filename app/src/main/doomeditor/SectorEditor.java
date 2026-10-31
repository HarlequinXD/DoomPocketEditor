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
        float sx=snap(x,1f),sy=snap(y,1f),best=.42f;
        for(Level.Vertex v:level.vertices){float d=(float)Math.hypot(v.x-x,v.y-y);if(d<best&&Math.abs(v.x-Math.round(v.x))<EPS&&Math.abs(v.y-Math.round(v.y))<EPS){best=d;sx=v.x;sy=v.y;}}
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
    public boolean addSector(ArrayList<float[]> points){if(!validate(points,-1))return false;push();Level.Sector s=new Level.Sector();for(float[] p:points)s.loop.add(level.addVertex(p[0],p[1]));level.sectors.add(s);normalizeEdges();return true;}
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
        push();Level.Sector copy=new Level.Sector();copy.floor=s.floor;copy.ceiling=s.ceiling;copy.floorMaterial=s.floorMaterial;copy.ceilingMaterial=s.ceilingMaterial;copy.wallMaterial=s.wallMaterial;copy.light=s.light;copy.special=s.special;copy.tag=s.tag;
        for(float[] p:poly)copy.loop.add(level.addVertex(p[0],p[1]));level.sectors.add(copy);return true;}
    /** Insert grid-aligned vertices on collinear edges so adjacent sectors share full segments. */
    private void normalizeEdges(){
        for(Level.Sector sec:level.sectors){
            ArrayList<Integer> loop=new ArrayList<>();int n=sec.loop.size();
            for(int i=0;i<n;i++){
                int ia=sec.loop.get(i),ib=sec.loop.get((i+1)%n);
                Level.Vertex a=level.vertices.get(ia),b=level.vertices.get(ib);
                loop.add(ia);
                ArrayList<Integer> mids=new ArrayList<>();
                for(int k=0;k<level.vertices.size();k++){
                    if(k==ia||k==ib)continue;
                    Level.Vertex v=level.vertices.get(k);
                    if(onSegment(a.x,a.y,b.x,b.y,v.x,v.y)&&
                       Math.hypot(v.x-a.x,v.y-a.y)>.001&&Math.hypot(v.x-b.x,v.y-b.y)>.001)mids.add(k);
                }
                java.util.Collections.sort(mids,(u,v)->{
                    Level.Vertex x=level.vertices.get(u),y=level.vertices.get(v);
                    float dx=b.x-a.x,dy=b.y-a.y;
                    return Float.compare((x.x-a.x)*dx+(x.y-a.y)*dy,(y.x-a.x)*dx+(y.y-a.y)*dy);
                });
                loop.addAll(mids);
            }
            sec.loop=loop;
        }
    }
    public boolean rectangle(int x1,int y1,int x2,int y2){
        if(x1==x2||y1==y2){error="Rectangle needs width and height";return false;}
        ArrayList<float[]> p=new ArrayList<>();
        p.add(new float[]{x1,y1});p.add(new float[]{x2,y1});p.add(new float[]{x2,y2});p.add(new float[]{x1,y2});
        return addSector(p);
    }
    public boolean setMaterial(int index,int surface,int material){
        if(index<0||index>=level.sectors.size())return false;
        push();Level.Sector s=level.sectors.get(index);
        if(surface==0)s.wallMaterial=material;else if(surface==1)s.floorMaterial=material;else s.ceilingMaterial=material;
        return true;
    }
    public boolean setHeights(int index,float floor,float ceiling){
        if(index<0||index>=level.sectors.size()||ceiling-floor<.5f){error="Invalid floor/ceiling clearance";return false;}
        push();Level.Sector s=level.sectors.get(index);s.floor=floor;s.ceiling=ceiling;return true;
    }
    /** Moves a set of vertices by (dx,dy) as one undoable step, validating every affected sector. */
    public boolean moveVertices(int[] ids,float dx,float dy){
        error="";if(ids.length==0||(dx==0&&dy==0))return false;
        float[] old=new float[ids.length*2];
        for(int i=0;i<ids.length;i++){if(ids[i]<0||ids[i]>=level.vertices.size())return false;Level.Vertex v=level.vertices.get(ids[i]);old[i*2]=v.x;old[i*2+1]=v.y;v.x+=dx;v.y+=dy;}
        boolean valid=true;
        for(int i=0;i<level.sectors.size()&&valid;i++){Level.Sector s=level.sectors.get(i);boolean touched=false;for(int id:ids)if(s.loop.contains(id)){touched=true;break;}
            if(touched&&!validate(coords(s),i))valid=false;}
        for(int i=0;i<ids.length;i++){Level.Vertex v=level.vertices.get(ids[i]);v.x=old[i*2];v.y=old[i*2+1];}
        if(!valid)return false;
        push();for(int id:ids){Level.Vertex v=level.vertices.get(id);v.x+=dx;v.y+=dy;}return true;
    }
    /** Splits an axis-aligned rectangular sector into 1-unit steps rising away from (fromX,fromY). */
    public boolean makeStairs(int index,float fromX,float fromY,float rise){
        error="";if(index<0||index>=level.sectors.size())return false;
        Level.Sector s=level.sectors.get(index);
        float minX=1e9f,minY=1e9f,maxX=-1e9f,maxY=-1e9f;
        for(int id:s.loop){Level.Vertex v=level.vertices.get(id);minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y);}
        float area=0;for(int i=0;i<s.loop.size();i++){Level.Vertex a=level.vertices.get(s.loop.get(i)),b=level.vertices.get(s.loop.get((i+1)%s.loop.size()));area+=a.x*b.y-b.x*a.y;}
        if(Math.abs(Math.abs(area*.5f)-(maxX-minX)*(maxY-minY))>.01f){error="Stairs need a rectangular sector";return false;}
        boolean alongX=maxX-minX>=maxY-minY;int n=(int)Math.floor(alongX?maxX-minX:maxY-minY);
        if(n<2){error="Sector is too small for stairs";return false;}
        boolean reverse=alongX?Math.abs(fromX-maxX)<Math.abs(fromX-minX):Math.abs(fromY-maxY)<Math.abs(fromY-minY);
        push();
        Level.Sector template=s;level.sectors.remove(index);
        for(int i=0;i<n;i++){
            float a=alongX?minX+i:minY+i,b=i==n-1?(alongX?maxX:maxY):a+1;
            ArrayList<float[]> poly=new ArrayList<>();
            if(alongX){poly.add(new float[]{a,minY});poly.add(new float[]{b,minY});poly.add(new float[]{b,maxY});poly.add(new float[]{a,maxY});}
            else{poly.add(new float[]{minX,a});poly.add(new float[]{maxX,a});poly.add(new float[]{maxX,b});poly.add(new float[]{minX,b});}
            Level.Sector step=new Level.Sector();for(float[] q:poly)step.loop.add(level.addVertex(q[0],q[1]));
            int k=reverse?n-1-i:i;
            step.floor=template.floor+rise*k;step.ceiling=Math.max(template.ceiling,step.floor+2f);
            step.floorMaterial=template.floorMaterial;step.ceilingMaterial=template.ceilingMaterial;step.wallMaterial=template.wallMaterial;step.light=template.light;
            level.sectors.add(step);
        }
        normalizeEdges();
        return true;
    }
    public boolean moveEdge(int sector,int edge,float dx,float dy){
        if(sector<0||sector>=level.sectors.size())return false;
        Level.Sector s=level.sectors.get(sector);if(edge<0||edge>=s.loop.size())return false;
        int a=s.loop.get(edge),b=s.loop.get((edge+1)%s.loop.size());
        Level.Vertex va=level.vertices.get(a),vb=level.vertices.get(b);
        float ax=va.x,ay=va.y,bx=vb.x,by=vb.y;
        va.x+=dx;va.y+=dy;vb.x+=dx;vb.y+=dy;
        boolean valid=true;
        for(int i=0;i<level.sectors.size();i++)if((level.sectors.get(i).loop.contains(a)||level.sectors.get(i).loop.contains(b))&&!validate(coords(level.sectors.get(i)),i)){valid=false;break;}
        va.x=ax;va.y=ay;vb.x=bx;vb.y=by;
        if(!valid)return false;
        push();va.x+=dx;va.y+=dy;vb.x+=dx;vb.y+=dy;return true;
    }
    public boolean extrude(int sector,int edge,int distance){
        if(sector<0||sector>=level.sectors.size()||distance==0)return false;
        Level.Sector s=level.sectors.get(sector);if(edge<0||edge>=s.loop.size())return false;
        Level.Vertex a=level.vertices.get(s.loop.get(edge)),b=level.vertices.get(s.loop.get((edge+1)%s.loop.size()));
        float dx=b.x-a.x,dy=b.y-a.y,len=(float)Math.hypot(dx,dy);if(len<.001)return false;
        float area=0;for(int i=0;i<s.loop.size();i++){Level.Vertex x=level.vertices.get(s.loop.get(i)),y=level.vertices.get(s.loop.get((i+1)%s.loop.size()));area+=x.x*y.y-x.y*y.x;}
        float sign=area>0?1:-1,nx=sign*dy/len*distance,ny=-sign*dx/len*distance;
        ArrayList<float[]> poly=new ArrayList<>();
        poly.add(new float[]{b.x,b.y});poly.add(new float[]{a.x,a.y});poly.add(new float[]{a.x+nx,a.y+ny});poly.add(new float[]{b.x+nx,b.y+ny});
        if(!addSector(poly))return false;
        Level.Sector created=level.sectors.get(level.sectors.size()-1);
        created.floor=s.floor;created.ceiling=s.ceiling;created.floorMaterial=s.floorMaterial;created.ceilingMaterial=s.ceilingMaterial;created.wallMaterial=s.wallMaterial;
        return true;
    }
    /** Remove orphaned vertices, preserving sector indices by remapping references. */
    public int cleanOrphans(){
        java.util.HashSet<Integer> used=new java.util.HashSet<>();for(Level.Sector s:level.sectors)used.addAll(s.loop);
        int removed=level.vertices.size()-used.size();if(removed<=0)return 0;
        push();ArrayList<Level.Vertex> vertices=new ArrayList<>();int[] remap=new int[level.vertices.size()];
        for(int i=0;i<level.vertices.size();i++)if(used.contains(i)){remap[i]=vertices.size();vertices.add(level.vertices.get(i));}
        for(Level.Sector s:level.sectors)for(int i=0;i<s.loop.size();i++)s.loop.set(i,remap[s.loop.get(i)]);
        level.vertices=vertices;return removed;
    }
    public String validateMap(){for(int i=0;i<level.sectors.size();i++)if(!validate(coords(level.sectors.get(i)),i))return "Sector "+(i+1)+": "+error;return "Geometry valid";}
}
