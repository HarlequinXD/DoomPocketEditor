package com.retro.doomeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Random;

/**
 * World atmosphere: coloured entity lights (torches flicker, magic pulses), per-map fog, colour tint and
 * weather, and world-space particles (smoke, embers, sparkles...). The renderer applies lights, fog and
 * tint in one pass over the finished frame using its depth buffer.
 */
final class Atmosphere {
    private Atmosphere(){}

    // ------------------------------------------------------------------ entity lights
    static final String[] GLOWS={"Automatic (torches and braziers glow)","No light","Torch (warm, flickers)","Candle (small, flickers)","Brazier (large fire)","Magic blue (pulses)","Holy gold","Toxic green (pulses)","Blood red","Moonlight (pale blue)","Arcane violet (pulses)"};
    static final int G_AUTO=0,G_NONE=1;
    //                     colour      radius intensity flicker(0 steady,1 flicker,2 pulse)
    private static final float[][] GLOW_DATA={{0,0,0,0},{0,0,0,0},
            {0xffff9a40,6f,1f,1},{0xffffb860,3f,.8f,1},{0xffff8030,8.5f,1.15f,1},{0xff5aa0ff,6f,1f,2},{0xffffd870,7f,.9f,0},
            {0xff60ff70,5.5f,.9f,2},{0xffff3030,5f,1f,0},{0xffa8c8ff,9f,.7f,0},{0xffb070ff,6f,1f,2}};
    /** Light for an entity type, or null. Automatic: torches and braziers. */
    static float[] glowFor(EntityDef d,int sprite){return glowFor(d,sprite,-1);}
    /** As above; {@code override} is a preset set by the Set Light action (-1 = none). */
    static float[] glowFor(EntityDef d,int sprite,int override){
        int g=override>=0?override:d==null?G_AUTO:d.glow;
        if(g==G_AUTO){if(sprite==Sprites.TORCH)g=2;else if(sprite==Sprites.BRAZIER)g=4;else return null;}
        if(g<=G_NONE||g>=GLOW_DATA.length)return null;
        float[] gd=GLOW_DATA[g];
        if(d!=null&&d.glowRadius>0)return new float[]{gd[0],d.glowRadius,gd[2],gd[3]};
        return gd;
    }
    /** Adds a light to the renderer: colour, radius, intensity, flicker style, seeded per light so torches don't flicker in sync. */
    static void addLight(Renderer r,float x,float y,float z,float[] g,float time,int seed){
        float k=g[2];
        if(g[3]==1)k*=.82f+.1f*(float)Math.sin(time*13.1f+seed)+.08f*(float)Math.sin(time*29.7f+seed*1.7f);
        else if(g[3]==2)k*=.7f+.3f*(float)Math.sin(time*2.6f+seed);
        int c=(int)g[0];r.addLight(x,y,z,g[1],((c>>16)&255)/255f*k,((c>>8)&255)/255f*k,(c&255)/255f*k);
    }

    // ------------------------------------------------------------------ map fog, tint and weather
    static final String[] FOGS={"No fog","Light mist","Thick fog","Dark gloom","Green miasma","Crimson haze","Night shadows"};
    private static final int[] FOG_COL={0,0xffb8c0c8,0xffa8b0b8,0xff0a0b0e,0xff3a5a30,0xff5a1818,0xff0a1020};
    private static final float[][] FOG_RANGE={{0,0,0},{6,26,.55f},{2,13,.85f},{3,15,.9f},{3,18,.7f},{3,18,.7f},{4,17,.85f}};
    static final String[] TINTS={"No tint","Warm firelight","Cold","Night blue","Toxic green","Blood red","Old sepia"};
    private static final int[] TINT_COL={0,0xffffd8a0,0xffb8d0ff,0xff8098d8,0xffb8ffa0,0xffff9890,0xffd8c098};
    private static final float[] TINT_AMT={0,.35f,.35f,.55f,.4f,.4f,.5f};
    static final String[] WEATHERS={"Clear","Rain","Heavy rain","Snow","Falling ash","Rising embers","Falling leaves","Dust motes"};
    static void applyMap(Renderer r,Project.GameMap m){apply(r,m==null?0:m.fog,m==null?0:m.tint);}
    static void apply(Renderer r,int fog,int tint){
        int f=Math.max(0,Math.min(FOGS.length-1,fog));int t=Math.max(0,Math.min(TINTS.length-1,tint));
        r.setFog(f==0?0:FOG_COL[f],FOG_RANGE[f][0],FOG_RANGE[f][1],FOG_RANGE[f][2]);
        r.setTint(TINT_COL[t],TINT_AMT[t]);
    }

    /** Editor views and menu backdrops: the map's fog and tint, and lights from its placed entities. */
    static void prepareStatic(Renderer r,Project p,Project.GameMap m,Level level,float time){
        applyMap(r,m);r.particles=null;r.clearLights();
        for(int i=0;i<level.things.size();i++){Level.Thing t=level.things.get(i);EntityDef d=p.def(t.def);if(d==null||d.kind==EntityDef.PLAYER)continue;
            float[] g=glowFor(d,d.sprite);if(g==null)continue;int s=level.sectorAt(t.x,t.y);float z=s<0?0:level.sectors.get(s).floor;
            addLight(r,t.x,t.y,z+d.height*.8f,g,time,i*7);}
    }
    /** Weather drawn in screen space over the 3D view; moves with the camera turn so it feels anchored. */
    static final class Weather {
        private float[] x=new float[0],y,s,v;private int type=-1;private final Random rng=new Random(7);private float lastAngle;
        void draw(Canvas c,Paint p,int type,float w,float h,float dt,float camAngle,float dp){
            if(type<=0){this.type=type;return;}
            int n=type==1?160:type==2?320:type==3?180:type==6?40:type==7?70:120;
            if(type!=this.type||x.length!=n){this.type=type;x=new float[n];y=new float[n];s=new float[n];v=new float[n];for(int i=0;i<n;i++){x[i]=rng.nextFloat()*w;y[i]=rng.nextFloat()*h;s[i]=.5f+rng.nextFloat();v[i]=rng.nextFloat()*6.28f;}}
            float turn=(float)(World.angleDiff(camAngle,lastAngle));lastAngle=camAngle;float shift=-turn*w*.6f;
            p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<n;i++){
                float sp=s[i];
                switch(type){
                    case 1:case 2:{float vy=(type==2?1500:1000)*dp/3*sp,vx=-120*dp/3;y[i]+=vy*dt;x[i]+=vx*dt+shift;
                        p.setColor(type==2?0x70b8c8e0:0x58b8c8e0);p.setStrokeWidth(dp*.9f);c.drawLine(x[i],y[i],x[i]-vx*.018f,y[i]-vy*.018f,p);break;}
                    case 3:{v[i]+=dt*1.5f;y[i]+=55*dp/3*sp*dt*3;x[i]+=(float)Math.sin(v[i])*20*dp/3*dt+shift;p.setColor(0xd8ffffff);c.drawCircle(x[i],y[i],dp*(1.1f+sp*1.2f),p);break;}
                    case 4:{v[i]+=dt;y[i]+=35*dp*sp*dt;x[i]+=(float)Math.sin(v[i])*12*dp*dt+shift;p.setColor(0x90808080);c.drawCircle(x[i],y[i],dp*(.8f+sp),p);break;}
                    case 5:{v[i]+=dt*2;y[i]-=45*dp*sp*dt;x[i]+=(float)Math.sin(v[i])*18*dp*dt+shift;p.setColor(sp>1?0xd0ffb040:0xc0ff7020);c.drawCircle(x[i],y[i],dp*(.7f+sp*.6f),p);break;}
                    case 6:{v[i]+=dt*1.2f;y[i]+=40*dp*sp*dt;x[i]+=(float)Math.sin(v[i]*1.3f)*40*dp*dt+shift;p.setColor(sp>1?0xd0b86a28:0xd09a8a30);
                        c.save();c.rotate(v[i]*60,x[i],y[i]);c.drawOval(x[i]-dp*3,y[i]-dp*1.4f,x[i]+dp*3,y[i]+dp*1.4f,p);c.restore();break;}
                    default:{v[i]+=dt*.4f;x[i]+=(float)Math.cos(v[i])*6*dp*dt+shift;y[i]+=(float)Math.sin(v[i]*1.3f)*5*dp*dt;p.setColor(0x50fff0c0);c.drawCircle(x[i],y[i],dp*(.8f+sp*.5f),p);}
                }
                if(y[i]>h+10){y[i]=-10;x[i]=rng.nextFloat()*w;}else if(y[i]<-10){y[i]=h+10;x[i]=rng.nextFloat()*w;}
                if(x[i]<-20)x[i]+=w+40;else if(x[i]>w+20)x[i]-=w+40;
            }
            p.setStrokeCap(Paint.Cap.BUTT);
        }
    }

    // ------------------------------------------------------------------ particles
    static final String[] PARTICLES={"None","Smoke","Fire embers","Magic sparkles","Dust","Dripping water","Poison bubbles","Holy light"};
    //                   colour      rise  spread life size gravity
    private static final float[][] PDATA={{0,0,0,0,0,0},
            {0xff6a6a70,.6f,.25f,2.6f,.11f,0},{0xffff9030,1.1f,.2f,1.1f,.035f,0},{0xff9ad8ff,.35f,.45f,1.3f,.035f,0},{0xffc8b898,.08f,.6f,3f,.03f,0},
            {0xff8ab8ff,0,.15f,1.4f,.03f,-6},{0xff70e060,.45f,.3f,1.6f,.05f,0},{0xffffe8a0,.7f,.35f,1.5f,.04f,0}};
    static final class Particles {
        static final int MAX=600;
        final float[] x=new float[MAX],y=new float[MAX],z=new float[MAX],vx=new float[MAX],vy=new float[MAX],vz=new float[MAX],life=new float[MAX],max=new float[MAX],size=new float[MAX],grav=new float[MAX];
        final int[] col=new int[MAX];int n;private final Random rng=new Random(3);
        void emit(int type,float px,float py,float pz,int count){
            if(type<=0||type>=PDATA.length)return;float[] d=PDATA[type];
            for(int k=0;k<count;k++){if(n>=MAX)return;int i=n++;float a=rng.nextFloat()*6.28f,r=rng.nextFloat()*d[2];
                x[i]=px+(float)Math.cos(a)*r;y[i]=py+(float)Math.sin(a)*r;z[i]=pz+rng.nextFloat()*.1f;
                vx[i]=(rng.nextFloat()-.5f)*d[2];vy[i]=(rng.nextFloat()-.5f)*d[2];vz[i]=d[1]*(.6f+rng.nextFloat()*.8f);
                max[i]=life[i]=d[3]*(.6f+rng.nextFloat()*.8f);size[i]=d[4]*(.7f+rng.nextFloat()*.6f);col[i]=(int)d[0];grav[i]=d[5];}
        }
        void update(float dt){
            for(int i=0;i<n;i++){life[i]-=dt;if(life[i]<=0){n--;copy(n,i);i--;continue;}
                x[i]+=vx[i]*dt;y[i]+=vy[i]*dt;vz[i]+=grav[i]*dt;z[i]+=vz[i]*dt;}
        }
        private void copy(int from,int to){x[to]=x[from];y[to]=y[from];z[to]=z[from];vx[to]=vx[from];vy[to]=vy[from];vz[to]=vz[from];life[to]=life[from];max[to]=max[from];size[to]=size[from];col[to]=col[from];grav[to]=grav[from];}
        void clear(){n=0;}
    }
    /** Emitter rate (particles per second) for an entity type's particle setting. */
    static float rate(int type){return type<=0?0:type==1?6:type==2?14:type==3?10:type==4?3:type==5?2.5f:type==6?4:8;}
    /** Height on the entity where particles start (fire at the top of a torch, drips from a ceiling...). */
    static float emitHeight(int type,Actor a){return type==2||type==1?a.height*.9f:type==5?a.height:a.height*.5f;}
}
