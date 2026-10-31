package com.retro.doomeditor;
import java.util.ArrayList;
/** Non-mutating map integrity checker used before saving. */
public final class MapValidator {
    private MapValidator(){}
    public static ArrayList<String> check(Level level){
        ArrayList<String> errors=new ArrayList<>();
        for(int i=0;i<level.sectors.size();i++){
            Level.Sector s=level.sectors.get(i);
            if(s.loop.size()<3){errors.add("Sector "+(i+1)+": fewer than three vertices");continue;}
            if(s.ceiling-s.floor<.5f)errors.add("Sector "+(i+1)+": insufficient height");
            float area=0;
            for(int j=0;j<s.loop.size();j++){
                int ai=s.loop.get(j),bi=s.loop.get((j+1)%s.loop.size());
                if(ai<0||bi<0||ai>=level.vertices.size()||bi>=level.vertices.size()){
                    errors.add("Sector "+(i+1)+": invalid vertex index");continue;
                }
                Level.Vertex a=level.vertices.get(ai),b=level.vertices.get(bi);
                if(Math.hypot(a.x-b.x,a.y-b.y)<.001)errors.add("Sector "+(i+1)+": zero-length linedef");
                area+=a.x*b.y-b.x*a.y;
            }
            if(Math.abs(area)<.001)errors.add("Sector "+(i+1)+": zero area");
        }
        for(Level.Thing t:level.things)
            if(level.sectorAt(t.x,t.y)<0)errors.add("Entity outside sectors at "+t.x+","+t.y);
        return errors;
    }
}
