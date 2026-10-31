package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/** Non-destructive edge subdivision for accurate two-sided sector boundaries. */
final class PortalTopology {
    private static final float EPS = .0001f;
    private PortalTopology() {}
    private static boolean on(float ax,float ay,float bx,float by,float x,float y) {
        float dx=bx-ax,dy=by-ay;
        float len2=dx*dx+dy*dy;
        if(len2<EPS*EPS)return false;
        float t=((x-ax)*dx+(y-ay)*dy)/len2;
        return t>=-EPS&&t<=1+EPS&&Math.abs((x-ax)*dy-(y-ay)*dx)<=EPS*(float)Math.sqrt(len2);
    }
    static ArrayList<Level.LineDef> build(Level level) {
        ArrayList<Level.LineDef> result=new ArrayList<>();
        for(int si=0;si<level.sectors.size();si++) {
            Level.Sector s=level.sectors.get(si);
            for(int j=0;j<s.loop.size();j++) {
                int ai=s.loop.get(j),bi=s.loop.get((j+1)%s.loop.size());
                if(ai<0||bi<0||ai>=level.vertices.size()||bi>=level.vertices.size()||ai==bi)continue;
                Level.Vertex a=level.vertices.get(ai),b=level.vertices.get(bi);
                ArrayList<Integer> cuts=new ArrayList<>();cuts.add(ai);cuts.add(bi);
                for(int k=0;k<level.vertices.size();k++) {
                    if(k==ai||k==bi)continue;
                    Level.Vertex v=level.vertices.get(k);
                    if(on(a.x,a.y,b.x,b.y,v.x,v.y))cuts.add(k);
                }
                Collections.sort(cuts,new Comparator<Integer>() {
                    @Override public int compare(Integer x,Integer y) {
                        Level.Vertex vx=level.vertices.get(x),vy=level.vertices.get(y);
                        float dx=b.x-a.x,dy=b.y-a.y;
                        return Float.compare((vx.x-a.x)*dx+(vx.y-a.y)*dy,(vy.x-a.x)*dx+(vy.y-a.y)*dy);
                    }
                });
                for(int k=0;k+1<cuts.size();k++) {
                    int u=cuts.get(k),v=cuts.get(k+1);
                    Level.Vertex p=level.vertices.get(u),q=level.vertices.get(v);
                    if(Math.hypot(p.x-q.x,p.y-q.y)<EPS)continue;
                    Level.LineDef match=null;
                    for(Level.LineDef old:result) {
                        Level.Vertex oa=level.vertices.get(old.from),ob=level.vertices.get(old.to);
                        boolean same=(Math.hypot(oa.x-p.x,oa.y-p.y)<EPS&&Math.hypot(ob.x-q.x,ob.y-q.y)<EPS);
                        boolean reverse=(Math.hypot(oa.x-q.x,oa.y-q.y)<EPS&&Math.hypot(ob.x-p.x,ob.y-p.y)<EPS);
                        if(same||reverse){match=old;break;}
                    }
                    if(match==null)result.add(new Level.LineDef(u,v,si,-1));
                    else if(match.front!=si&&match.back<0)match.back=si;
                }
            }
        }
        return result;
    }
    static boolean canCross(Level level,float x,float y,float nx,float ny,float step,float height) {
        int from=level.sectorAt(x,y),to=level.sectorAt(nx,ny);
        if(to<0)return false;
        Level.Sector dest=level.sectors.get(to);
        if(dest.ceiling-dest.floor<height)return false;
        if(from==to)return true;
        if(from<0)return false;
        Level.Sector src=level.sectors.get(from);
        if(dest.floor-src.floor>step+EPS)return false;
        if(Math.min(src.ceiling,dest.ceiling)-Math.max(src.floor,dest.floor)<height)return false;
        for(Level.LineDef line:build(level)) {
            if(!((line.front==from&&line.back==to)||(line.front==to&&line.back==from)))continue;
            Level.Vertex a=level.vertices.get(line.from),b=level.vertices.get(line.to);
            float ux=nx-x,uy=ny-y,vx=b.x-a.x,vy=b.y-a.y;
            float den=ux*vy-uy*vx;
            if(Math.abs(den)<EPS)continue;
            float t=((a.x-x)*vy-(a.y-y)*vx)/den;
            float u=((a.x-x)*uy-(a.y-y)*ux)/den;
            if(t>=-EPS&&t<=1+EPS&&u>=-EPS&&u<=1+EPS)return true;
        }
        return false;
    }
}
