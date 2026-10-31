package com.retro.doomeditor;

import java.util.ArrayList;

/** Starting maps for new projects. */
final class Templates {
    private Templates(){}
    static Level empty(){
        Level l=Level.empty();
        int a=l.addVertex(0,0),b=l.addVertex(8,0),c=l.addVertex(8,8),d=l.addVertex(0,8);
        Level.Sector s=new Level.Sector();java.util.Collections.addAll(s.loop,a,b,c,d);s.floorMaterial=2;s.ceilingMaterial=11;s.wallMaterial=0;l.sectors.add(s);
        l.playerX=4;l.playerY=4;l.facing=0;
        return l;
    }
    private static Level.Sector rect(Level l,int x0,int y0,int x1,int y1,float floor,float ceil,int fm,int cm,int wm,int light){
        Level.Sector s=new Level.Sector();
        int[][] pts={{x0,y0},{x1,y0},{x1,y1},{x0,y1}};
        for(int[] p:pts)s.loop.add(l.addVertex(p[0],p[1]));
        s.floor=floor;s.ceiling=ceil;s.floorMaterial=fm;s.ceilingMaterial=cm;s.wallMaterial=wm;s.light=light;l.sectors.add(s);return s;
    }
    /** 5x5 grid of 4-unit tiles: sky ring, solid pillars, raised centre platform, lit floor. */
    static Level arena(){
        Level l=Level.empty();
        for(int gy=0;gy<5;gy++)for(int gx=0;gx<5;gx++){
            int x0=gx*4,y0=gy*4;
            boolean edge=gx==0||gy==0||gx==4||gy==4,pillar=(gx==1||gx==3)&&(gy==1||gy==3),centre=gx==2&&gy==2;
            if(pillar)rect(l,x0,y0,x0+4,y0+4,3.5f,3.5f,6,6,6,192);
            else if(centre)rect(l,x0,y0,x0+4,y0+4,.5f,4,11,11,0,240);
            else if(edge)rect(l,x0,y0,x0+4,y0+4,0,6,9,Textures.SKY,9,(gx+gy)%2==0?216:184);
            else rect(l,x0,y0,x0+4,y0+4,0,4,2,11,3,200);
        }
        l.playerX=2f;l.playerY=2f;l.facing=(float)(Math.PI/4);
        String[][] things={{"imp","18","2"},{"imp","2","18"},{"trooper","10","2"},{"trooper","2","10"},{"trooper","18","10"},{"demon","16","16"},
            {"shotgun","10","10"},{"medkit","9","11"},{"armor","18","18"},{"shells","6","10"},{"clip","10","6"},{"barrel","14","2"},{"barrel","2","14"},{"stimpack","18","6"}};
        for(String[] t:things)l.things.add(new Level.Thing(Float.parseFloat(t[1]),Float.parseFloat(t[2]),t[0]));
        for(Level.Thing t:l.things)t.angle=(float)Math.atan2(l.playerY-t.y,l.playerX-t.x);
        return l;
    }
}
