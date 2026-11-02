package com.retro.doomeditor;

/** Procedurally drawn pixel-art sprites for entities, effects and first-person weapons. */
final class Sprites {
    private Sprites(){}
    static final int PLAYER=0,TROOPER=1,IMP=2,DEMON=3,MEDKIT=4,STIMPACK=5,CLIP=6,SHELLS=7,ROCKETBOX=8,ARMOR=9,SHOTGUN_PICKUP=10,
            CHAINGUN_PICKUP=11,LAUNCHER_PICKUP=12,KEY_BLUE=13,KEY_YELLOW=14,KEY_RED=15,BARREL=16,TORCH=17,COLUMN=18,SWITCH=19,SWITCH_ON=20,LAMP=21,SERGEANT=22;
    static final String[] NAMES={"Player","Trooper","Imp","Demon","Medkit","Stimpack","Ammo Clip","Shells","Rocket Box","Armor","Shotgun","Chaingun","Rocket Launcher",
            "Blue Key","Yellow Key","Red Key","Barrel","Torch","Column","Switch","Switch On","Lamp","Sergeant"};
    static final String NAMES_JOINED=join(NAMES);
    static String join(String[] a){StringBuilder b=new StringBuilder();for(int i=0;i<a.length;i++){if(i>0)b.append('|');b.append(a[i]);}return b.toString();}
    static final int COUNT=NAMES.length;
    // Effects (not user selectable)
    static final int FX_PUFF=0,FX_BLOOD=1,FX_EXPLOSION=2,FX_TELEPORT=3,FX_FIREBALL=4,FX_ROCKET=5,FX_PLASMA=6;
    static final int F_IDLE=0,F_WALK1=1,F_WALK2=2,F_ATTACK=3,F_PAIN=4,F_DEAD=5,FRAMES=6;
    /** Pixels per world unit for sprites. */
    static final float PPU=32f;

    /** Pixel data plus its density: built-in model sprites use 64 pixels per unit, hand-drawn ones 32. */
    static final class Sprite {final int w,h;final int[] px;final boolean bright;float ppu=PPU;Sprite(int w,int h,boolean bright){this.w=w;this.h=h;px=new int[w*h];this.bright=bright;}}
    /** Model sprites are rendered at twice the hand-drawn density for crisper close-ups. */
    static final float MODEL_PPU=64f;
    /** Number of view angles stored for every built-in sprite slot (characters really use all 8). */
    static final int VIEWS=8;
    private static Sprite[][][] cache;
    private static Sprite[][] effects;
    private static Sprite[] weapons,flashes;
    private static final Object lock=new Object();

    static void init(){
        synchronized(lock){
            if(cache!=null)return;
            cache=new Sprite[COUNT][FRAMES][];
            effects=new Sprite[7][3];
            for(int e=0;e<7;e++)for(int f=0;f<3;f++)effects[e][f]=effect(e,f);
            weapons=new Sprite[5];flashes=new Sprite[5];
            for(int i=0;i<5;i++){weapons[i]=weapon(i);flashes[i]=flash(i);}
        }
        // Pre-build the 8-angle characters in the background so the first frames of play don't stall.
        Thread t=new Thread(()->{
            try{for(int f:new int[]{F_IDLE,F_WALK1,F_WALK2,F_ATTACK,F_PAIN,F_DEAD})for(int id:CHARACTERS)for(int v=0;v<VIEWS;v++)get(id,f,v);
                for(int id=0;id<COUNT;id++)get(id,F_IDLE,0);}catch(Throwable ignored){}
        },"SpriteWarmup");
        t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);t.start();
    }
    /** Characters have 8 rotations (front, front-right, right, back-right, back, back-left, left, front-left). */
    static final int[] CHARACTERS={PLAYER,TROOPER,SERGEANT,IMP,DEMON};
    static boolean hasAngles(int id){for(int c:CHARACTERS)if(c==id)return true;return false;}
    static Sprite get(int id,int frame){return get(id,frame,0);}
    /** Sprite {@code id} in animation {@code frame} seen from {@code view} (0 = front … 4 = back, see CustomSprite.viewIndex). */
    static Sprite get(int id,int frame,int view){
        if(cache==null)init();
        id=Math.max(0,Math.min(COUNT-1,id));frame=Math.max(0,Math.min(FRAMES-1,frame));
        boolean angled=hasAngles(id);view=angled?((view%VIEWS)+VIEWS)%VIEWS:0;
        // static items share one image for every frame (torches flicker between two)
        int f=angled?frame:id==TORCH?(frame&1):0;
        synchronized(lock){Sprite[] a=cache[id][f];if(a!=null&&a[view]!=null)return a[view];}
        Sprite s=build(id,f,view);
        synchronized(lock){Sprite[] a=cache[id][f];if(a==null)a=cache[id][f]=new Sprite[VIEWS];if(a[view]==null)a[view]=s;return a[view];}
    }
    private static Sprite build(int id,int frame,int view){
        SpriteModels.Model m;float elev;boolean bright=id==KEY_BLUE||id==KEY_YELLOW||id==KEY_RED||id==TORCH||id==LAMP;
        switch(id){
            case PLAYER:m=SpriteModels.soldier(0,frame);elev=.15f;break;
            case TROOPER:m=SpriteModels.soldier(1,frame);elev=.15f;break;
            case SERGEANT:m=SpriteModels.soldier(2,frame);elev=.15f;break;
            case IMP:m=SpriteModels.imp(frame);elev=.15f;break;
            case DEMON:m=SpriteModels.demon(frame);elev=.15f;break;
            default:{m=SpriteModels.item(id,frame);
                boolean tall=id==TORCH||id==COLUMN||id==LAMP||id==SWITCH||id==SWITCH_ON||id==BARREL||id==KEY_BLUE||id==KEY_YELLOW||id==KEY_RED;
                elev=tall?.18f:.42f;   // pickups lie on the floor, so look down on them a little more
                // turn items a little so they read in 3D (front-three-quarter)
                return SpriteModels.render(m,tall?.0f:.55f,elev,MODEL_PPU,bright,id*7+frame);}
        }
        return SpriteModels.render(m,(float)(view*Math.PI/4),elev,MODEL_PPU,bright,id*7+frame);
    }
    /** Rear view (kept for older callers). */
    static Sprite getBack(int id,int frame){return get(id,frame,4);}
    static boolean hasBack(int id){return hasAngles(id);}
    static Sprite effect(int id,int frame,boolean dummy){init();return effects[Math.max(0,Math.min(6,id))][Math.max(0,Math.min(2,frame))];}
    static Sprite weaponSprite(int w){init();return weapons[Math.max(0,Math.min(4,w))];}
    static Sprite flashSprite(int w){init();return flashes[Math.max(0,Math.min(4,w))];}
    static android.graphics.Bitmap preview(int id){
        Sprite s=get(id,F_IDLE);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(s.w,s.h,android.graphics.Bitmap.Config.ARGB_8888);b.setPixels(s.px,0,s.w,0,0,s.w,s.h);return b;
    }

    // ------------------------------------------------------------------ drawing helpers
    private static void rect(Sprite s,int x0,int y0,int x1,int y1,int c){for(int y=Math.max(0,y0);y<=Math.min(s.h-1,y1);y++)for(int x=Math.max(0,x0);x<=Math.min(s.w-1,x1);x++)s.px[y*s.w+x]=c;}
    private static void oval(Sprite s,float cx,float cy,float rx,float ry,int c){
        for(int y=(int)(cy-ry-1);y<=cy+ry+1;y++)for(int x=(int)(cx-rx-1);x<=cx+rx+1;x++){
            if(x<0||y<0||x>=s.w||y>=s.h)continue;float dx=(x+.5f-cx)/rx,dy=(y+.5f-cy)/ry;if(dx*dx+dy*dy<=1)s.px[y*s.w+x]=c;}
    }
    private static void px(Sprite s,int x,int y,int c){if(x>=0&&y>=0&&x<s.w&&y<s.h)s.px[y*s.w+x]=c;}
    private static void line(Sprite s,int x0,int y0,int x1,int y1,int c){int n=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));for(int i=0;i<=n;i++){float t=n==0?0:i/(float)n;px(s,Math.round(x0+(x1-x0)*t),Math.round(y0+(y1-y0)*t),c);}}
    private static int shade(int c,float f){return Textures.rgb(((c>>16)&255)*f,((c>>8)&255)*f,(c&255)*f);}
    /** Adds top highlights, bottom shadows, grain and a dark outline so shapes read as 3D. */
    private static void finish(Sprite s,int seed){
        int[] src=s.px.clone();
        for(int y=0;y<s.h;y++)for(int x=0;x<s.w;x++){
            int c=src[y*s.w+x];if((c>>>24)==0)continue;
            boolean top=y==0||(src[(y-1)*s.w+x]>>>24)==0,bottom=y==s.h-1||(src[(y+1)*s.w+x]>>>24)==0,left=x==0||(src[y*s.w+x-1]>>>24)==0;
            float f=.92f+Textures.hash(x,y,seed)*.16f;if(top||left)f*=1.22f;if(bottom)f*=.72f;
            s.px[y*s.w+x]=shade(c,f);
        }
        src=s.px.clone();
        for(int y=0;y<s.h;y++)for(int x=0;x<s.w;x++){
            if((src[y*s.w+x]>>>24)!=0)continue;
            boolean edge=(x>0&&(src[y*s.w+x-1]>>>24)!=0)||(x<s.w-1&&(src[y*s.w+x+1]>>>24)!=0)||(y>0&&(src[(y-1)*s.w+x]>>>24)!=0)||(y<s.h-1&&(src[(y+1)*s.w+x]>>>24)!=0);
            if(edge)s.px[y*s.w+x]=0xff141010;
        }
    }

    private static Sprite effect(int e,int f){
        Sprite s;
        switch(e){
            case FX_PUFF:s=new Sprite(10,10,false);oval(s,5,5,2+f,2+f,0xff9a9a9a);oval(s,4,4,1+f*.5f,1+f*.5f,0xffd0d0d0);return s;
            case FX_BLOOD:s=new Sprite(10,10,false);oval(s,5,5,2+f*.7f,2+f*.7f,0xffa00808);px(s,4,4,0xffe02020);return s;
            case FX_EXPLOSION:{s=new Sprite(32,32,true);float r=6+f*5;oval(s,16,16,r,r,0xffd04010);oval(s,16,16,r*.7f,r*.7f,0xffff9020);oval(s,16,16,r*.4f,r*.4f,0xffffe080);return s;}
            case FX_TELEPORT:{s=new Sprite(22,34,true);for(int i=0;i<40;i++)px(s,(int)(Textures.hash(i,f,3)*22),(int)(Textures.hash(i,f,4)*34),0xff80ff80);oval(s,11,17,4+f*2,8+f*3,0x00000000);return s;}
            case FX_FIREBALL:s=new Sprite(14,14,true);oval(s,7,7,6,6,0xffe04010);oval(s,7,7,4,4,0xffff9020);oval(s,7,7,2,2,0xffffff90);return s;
            case FX_ROCKET:s=new Sprite(12,12,true);oval(s,6,6,3,3,0xff808080);oval(s,6,6,2,2,0xffffc040);return s;
            default:s=new Sprite(10,10,true);oval(s,5,5,4,4,0xff4060ff);oval(s,5,5,2,2,0xffc0d0ff);return s;
        }
    }
    // ------------------------------------------------------------------ first-person weapons
    private static Sprite weapon(int w){
        Sprite s;int skin=0xffc8a07a;
        switch(w){
            case 0:s=new Sprite(44,36,false);oval(s,22,18,12,11,skin);for(int i=0;i<4;i++)rect(s,13+i*5,8,16+i*5,13,shade(skin,1.1f));rect(s,14,24,30,35,skin);break;
            case 1:s=new Sprite(40,52,false);rect(s,16,4,23,30,0xff4a4e54);rect(s,18,0,21,4,0xff3a3d42);rect(s,17,8,22,10,0xff6a6e74);rect(s,12,28,28,51,skin);rect(s,14,26,26,30,0xff3a3d42);break;
            case 2:s=new Sprite(56,56,false);rect(s,24,0,27,34,0xff55595e);rect(s,29,0,32,34,0xff55595e);rect(s,22,20,34,30,0xff7a4a22);rect(s,20,30,36,55,0xff6a3a18);rect(s,14,36,22,50,skin);rect(s,34,38,42,52,skin);break;
            case 3:s=new Sprite(64,56,false);for(int i=0;i<4;i++)rect(s,22+i*5,0,25+i*5,30,0xff5a6068);rect(s,18,24,46,40,0xff4a4e54);rect(s,24,40,40,55,0xff3a3d42);rect(s,12,40,20,54,skin);rect(s,44,40,52,54,skin);break;
            default:s=new Sprite(64,56,false);rect(s,22,0,41,40,0xff4a6a3a);oval(s,31.5f,3,9,4,0xff1a1a1a);rect(s,18,26,46,44,0xff3a5a2e);rect(s,14,40,22,55,skin);rect(s,42,40,50,55,skin);break;
        }
        finish(s,200+w);return s;
    }
    private static Sprite flash(int w){
        Sprite s=new Sprite(30,24,true);if(w==0)return s;
        oval(s,15,14,13,10,0xffff9020);oval(s,15,14,8,6,0xffffd060);oval(s,15,14,4,3,0xffffffe0);return s;
    }
}
