package com.retro.doomeditor;

/** Procedurally drawn pixel-art sprites for entities, effects and first-person weapons. */
final class Sprites {
    private Sprites(){}
    // Built-in sprite ids. The numbers are stable (projects store them); the art is fantasy.
    static final int PLAYER=0,TROOPER=1,IMP=2,DEMON=3,MEDKIT=4,STIMPACK=5,CLIP=6,SHELLS=7,ROCKETBOX=8,ARMOR=9,SHOTGUN_PICKUP=10,
            CHAINGUN_PICKUP=11,LAUNCHER_PICKUP=12,KEY_BLUE=13,KEY_YELLOW=14,KEY_RED=15,BARREL=16,TORCH=17,COLUMN=18,SWITCH=19,SWITCH_ON=20,LAMP=21,SERGEANT=22,
            SHIELD_PICKUP=23,SCROLL=24,NPC=25,CHEST=26,CHEST_OPEN=27,GOLD=28,
            HERO_RANGER=29,HERO_ROGUE=30,HERO_WIZARD=31,HERO_CLERIC=32,
            // box-modelled decorations (PropMesh)
            CAMPFIRE=33,FIREPLACE=34,BED=35,CABINET=36,WARDROBE=37,TABLE=38,CHAIR=39,STOOL=40,BENCH=41,BOOKSHELF=42,CRATE=43,CANDELABRA=44,CAULDRON=45,RUG=46,
            BANNER=47,PLANT=48,WEAPON_RACK=49,SACK=50,CANDLE=51,DRESSER=52,
            // the hero made on the character creation screen (its look is CharLook.current)
            CREATED=53;
    // Fantasy names for the same ids
    static final int HERO=PLAYER,SKELETON=TROOPER,GOBLIN=IMP,ORC=DEMON,CULTIST=SERGEANT,VILLAGER=NPC,
            POTION_HEALING=MEDKIT,DRAUGHT=STIMPACK,DAGGER=CLIP,POTION_ARCANA=SHELLS,TREASURE=ROCKETBOX,ARMOUR=ARMOR,SWORD=SHOTGUN_PICKUP,BOW=CHAINGUN_PICKUP,STAFF=LAUNCHER_PICKUP,
            KEY_BRONZE=KEY_BLUE,KEY_SILVER=KEY_YELLOW,KEY_GOLD=KEY_RED,PILLAR=COLUMN,LEVER=SWITCH,LEVER_ON=SWITCH_ON,BRAZIER=LAMP;
    static final String[] NAMES={"Hero · Fighter","Skeleton","Goblin","Orc","Healing Potion","Healing Draught","Dagger","Arcana Potion","Treasure","Armour","Sword","Bow","Staff",
            "Bronze Key","Silver Key","Gold Key","Barrel","Torch","Pillar","Lever","Lever (pulled)","Brazier","Cultist","Shield","Scroll","Villager","Chest","Chest (open)","Gold","Hero · Ranger","Hero · Rogue","Hero · Wizard","Hero · Cleric",
            "Campfire","Fireplace","Bed","Cabinet","Wardrobe","Table","Chair","Stool","Bench","Bookshelf","Crate","Candelabra","Cauldron","Rug","Banner","Potted Plant","Weapon Rack","Grain Sack","Candle","Chest of Drawers","Created Character"};
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
    /**
     * Model sprites are rendered at 3x the editor density (96 px per unit) and, in pixel-art style, reduced 3x to
     * exactly 32 px per unit: the same pixel size as wall/floor textures and hand-drawn sprites, so they sit in the
     * world like everything else and a copy in the sprite editor is pixel-for-pixel identical.
     */
    private static final int PIXEL_K_CONST=3;
    static final float MODEL_PPU=PPU*PIXEL_K_CONST;
    /** Number of view angles stored for every built-in sprite slot (characters really use all 8). */
    static final int VIEWS=8;
    private static Sprite[][][] cache;
    private static Sprite[][] effects;
    private static Sprite[] weapons,flashes;
    private static final Object lock=new Object();

    /** Bump when the models or renderer change so stale disk-cached sprites are ignored. */
    static final int MODEL_VERSION=7;
    private static java.io.File diskRoot,diskDir;
    /** Pixel-art style (default): built-in sprites are rendered in HD, then reduced 3x keeping each block's main colour,
     *  exactly like copying a built-in sprite into the sprite editor. Off = the smooth HD render. */
    static volatile boolean pixel=true;
    static final int PIXEL_K=PIXEL_K_CONST;
    /** Rendered sprites are saved here (compressed) so each one is only ever rendered once per install. */
    static void setCacheDir(java.io.File dir){
        diskRoot=dir;
        try{String keep="sprites_v"+MODEL_VERSION;
            java.io.File[] old=dir.listFiles();if(old!=null)for(java.io.File o:old)if(o.isDirectory()&&o.getName().startsWith("sprites_v")&&!o.getName().startsWith(keep+"_")){java.io.File[] fs=o.listFiles();if(fs!=null)for(java.io.File f:fs)f.delete();o.delete();}
        }catch(Throwable ignored){}
        useDiskDir();
    }
    private static void useDiskDir(){
        if(diskRoot==null)return;
        try{java.io.File d=new java.io.File(diskRoot,"sprites_v"+MODEL_VERSION+(pixel?"_px":"_hd"));d.mkdirs();diskDir=d;}catch(Throwable ignored){}
    }
    /** Switches between pixel-art and HD built-in sprites; everything already drawn is dropped and redrawn on demand. */
    static void setPixelStyle(boolean on){
        if(pixel==on)return;
        pixel=on;useDiskDir();
        synchronized(requests){requests.clear();}
        synchronized(lock){if(cache!=null)cache=new Sprite[COUNT][FRAMES][];}
        synchronized(fp){for(Sprite[] a:fp)java.util.Arrays.fill(a,null);}
    }
    /** Block-reduces a sprite by k, keeping the most common opaque colour of each k x k block (hard edges, no blending). */
    static Sprite pixelate(Sprite sp,int k){
        int sw=(sp.w+k-1)/k,sh=(sp.h+k-1)/k;Sprite out=new Sprite(sw,sh,sp.bright);out.ppu=sp.ppu/k;
        // align blocks to the bottom edge so feet stay on the floor
        int oy=sh*k-sp.h,ox=(sw*k-sp.w)/2;int[] cols=new int[k*k],cnt=new int[k*k];
        for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
            int n=0,opaque=0;
            for(int yy=0;yy<k;yy++)for(int xx=0;xx<k;xx++){int sx=x*k+xx-ox,sy=y*k+yy-oy;if(sx<0||sy<0||sx>=sp.w||sy>=sp.h)continue;int c=sp.px[sy*sp.w+sx];if((c>>>24)<128)continue;c|=0xff000000;opaque++;
                int j=0;while(j<n&&cols[j]!=c)j++;if(j==n){cols[n]=c;cnt[n]=0;n++;}cnt[j]++;}
            if(opaque*2>=k*k){int best=0;for(int j=1;j<n;j++)if(cnt[j]>cnt[best])best=j;out.px[y*sw+x]=cols[best];}
        }
        return out;
    }
    private static final java.util.ArrayDeque<int[]> requests=new java.util.ArrayDeque<>();
    private static Thread worker;

    static void init(){
        synchronized(lock){
            if(cache!=null)return;
            cache=new Sprite[COUNT][FRAMES][];
            effects=new Sprite[7][3];
            for(int e=0;e<7;e++)for(int f=0;f<3;f++)effects[e][f]=effect(e,f);
            weapons=new Sprite[5];flashes=new Sprite[5];
            for(int i=0;i<5;i++){weapons[i]=weapon(i);flashes[i]=flash(i);}
        }
        if(Boolean.getBoolean("sprites.nowarm"))return;   // test harness timing
        // Background worker: serves on-screen requests first, then pre-builds everything else.
        worker=new Thread(Sprites::workLoop,"SpriteWorker");
        worker.setDaemon(true);worker.setPriority(Thread.MIN_PRIORITY+1);worker.start();
    }
    private static void workLoop(){
        java.util.ArrayList<int[]> warm=new java.util.ArrayList<>();
        for(int id:CHARACTERS)for(int v=0;v<VIEWS;v++)warm.add(new int[]{id,F_IDLE,v});
        for(int id=0;id<COUNT;id++)if(!hasAngles(id))warm.add(new int[]{id,F_IDLE,0});
        warm.add(new int[]{TORCH,1,0});
        for(int f:new int[]{F_WALK1,F_WALK2,F_ATTACK,F_PAIN,F_DEAD})for(int id:CHARACTERS)for(int v=0;v<VIEWS;v++)warm.add(new int[]{id,f,v});
        int wi=0;
        while(true){
            int[] job;
            synchronized(requests){job=requests.poll();}
            if(job==null){if(wi>=warm.size()){synchronized(requests){try{if(requests.isEmpty())requests.wait();}catch(InterruptedException e){return;}}continue;}job=warm.get(wi++);}
            try{getNow(job[0],job[1],job[2]);}catch(Throwable ignored){}
        }
    }
    private static void request(int id,int f,int view){
        synchronized(requests){for(int[] r:requests)if(r[0]==id&&r[1]==f&&r[2]==view)return;requests.addFirst(new int[]{id,f,view});requests.notifyAll();}
    }
    /** Characters have 8 rotations (front, front-right, right, back-right, back, back-left, left, front-left). */
    static final int[] CHARACTERS={PLAYER,TROOPER,SERGEANT,IMP,DEMON,NPC,HERO_RANGER,HERO_ROGUE,HERO_WIZARD,HERO_CLERIC,CREATED};
    /** Built-in hero look for a class (Fighter, Ranger, Rogue, Wizard, Cleric). */
    static int heroFor(String cls){if(cls==null)return HERO;switch(cls){case "Ranger":return HERO_RANGER;case "Rogue":return HERO_ROGUE;case "Wizard":return HERO_WIZARD;case "Cleric":return HERO_CLERIC;default:return HERO;}}
    static boolean isHero(int id){return id==HERO||(id>=HERO_RANGER&&id<=HERO_CLERIC)||id==CREATED;}
    /** Drops every rendered picture of a sprite (the created hero's look changed). */
    static void invalidate(int id){if(cache==null||id<0||id>=COUNT)return;synchronized(lock){for(int f=0;f<FRAMES;f++)cache[id][f]=null;}}
    static boolean hasAngles(int id){for(int c:CHARACTERS)if(c==id)return true;return false;}
    static Sprite get(int id,int frame){return get(id,frame,0);}
    private static int frameSlot(int id,int frame){return hasAngles(id)?frame:PropMesh.fire(id)?(frame&1):0;}
    /**
     * Sprite {@code id} in animation {@code frame} seen from {@code view} (0 = front … 4 = back, see CustomSprite.viewIndex).
     * Never stalls the game: if that angle isn't rendered yet, the closest ready one is shown while it renders in the background.
     */
    static Sprite get(int id,int frame,int view){
        if(cache==null)init();
        id=Math.max(0,Math.min(COUNT-1,id));frame=Math.max(0,Math.min(FRAMES-1,frame));
        view=hasAngles(id)?((view%VIEWS)+VIEWS)%VIEWS:0;int f=frameSlot(id,frame);
        Sprite s=cached(id,f,view);if(s!=null)return s;
        s=loadDisk(id,f,view);if(s!=null)return put(id,f,view,s);
        if(worker!=null){Sprite fb=fallback(id,f,view);if(fb!=null){request(id,f,view);return fb;}}
        return getNow(id,frame,view);
    }
    /** Like {@link #get} but always returns exactly the requested angle, rendering it now if needed. */
    static Sprite getNow(int id,int frame,int view){
        if(cache==null)init();
        id=Math.max(0,Math.min(COUNT-1,id));frame=Math.max(0,Math.min(FRAMES-1,frame));
        view=hasAngles(id)?((view%VIEWS)+VIEWS)%VIEWS:0;int f=frameSlot(id,frame);
        Sprite s=cached(id,f,view);if(s!=null)return s;
        s=loadDisk(id,f,view);if(s==null){s=build(id,f,view);saveDisk(id,f,view,s);}
        return put(id,f,view,s);
    }
    private static Sprite cached(int id,int f,int view){synchronized(lock){Sprite[] a=cache[id][f];return a==null?null:a[view];}}
    private static Sprite put(int id,int f,int view,Sprite s){synchronized(lock){Sprite[] a=cache[id][f];if(a==null)a=cache[id][f]=new Sprite[VIEWS];if(a[view]==null)a[view]=s;return a[view];}}
    /** Nearest ready angle of the same frame, else any ready frame of the same sprite. */
    private static Sprite fallback(int id,int f,int view){
        synchronized(lock){
            Sprite[] a=cache[id][f];
            if(a!=null)for(int d=1;d<=VIEWS/2;d++){Sprite s=a[(view+d)%VIEWS];if(s!=null)return s;s=a[(view-d+VIEWS)%VIEWS];if(s!=null)return s;}
            for(int g=0;g<FRAMES;g++){Sprite[] b=cache[id][g];if(b==null)continue;Sprite s=b[view];if(s!=null)return s;for(Sprite x:b)if(x!=null)return x;}
        }
        return null;
    }
    private static java.io.File diskFile(int id,int f,int view){return diskDir==null||id==CREATED?null:new java.io.File(diskDir,id+"_"+f+"_"+view+".spr");}
    private static Sprite loadDisk(int id,int f,int view){
        java.io.File file=diskFile(id,f,view);if(file==null||!file.exists())return null;
        try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(new java.io.FileInputStream(file))))){
            int w=in.readInt(),h=in.readInt();float ppu=in.readFloat();boolean bright=in.readBoolean();
            if(w<=0||h<=0||w>1024||h>1024)return null;
            Sprite s=new Sprite(w,h,bright);s.ppu=ppu;for(int i=0;i<s.px.length;i++)s.px[i]=in.readInt();return s;
        }catch(Throwable t){file.delete();return null;}
    }
    private static void saveDisk(int id,int f,int view,Sprite s){
        java.io.File file=diskFile(id,f,view);if(file==null)return;
        java.io.File tmp=new java.io.File(file.getPath()+".tmp");
        try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.util.zip.DeflaterOutputStream(new java.io.FileOutputStream(tmp))))){
            out.writeInt(s.w);out.writeInt(s.h);out.writeFloat(s.ppu);out.writeBoolean(s.bright);for(int c:s.px)out.writeInt(c);
        }catch(Throwable t){tmp.delete();return;}
        tmp.renameTo(file);
    }
    private static Sprite build(int id,int frame,int view){Sprite s=buildHd(id,frame,view);return pixel?pixelate(s,PIXEL_K):s;}
    private static Sprite buildHd(int id,int frame,int view){
        SpriteModels.Model m;float elev;boolean bright=PropMesh.fire(id);
        if(id==CREATED){MeshModels.Mesh mesh=MeshModels.get(id);if(mesh!=null)return MeshModels.raster(mesh,frame,(float)(view*Math.PI/4),.15f,MODEL_PPU,false);}
        if(PropMesh.has(id)){   // box-modelled objects: the sprite is a picture of the same low-poly model
            MeshModels.Mesh mesh=MeshModels.get(id);
            if(mesh!=null)return MeshModels.raster(mesh,frame,id==RUG?.6f:.55f,id==RUG?.7f:.3f,MODEL_PPU,bright);}
        switch(id){
            case HERO:m=SpriteModels.hero(frame);elev=.15f;break;
            case SKELETON:m=SpriteModels.skeleton(frame);elev=.15f;break;
            case CULTIST:m=SpriteModels.cultist(frame);elev=.15f;break;
            case GOBLIN:m=SpriteModels.goblin(frame);elev=.15f;break;
            case ORC:m=SpriteModels.orc(frame);elev=.15f;break;
            case VILLAGER:m=SpriteModels.villager(frame);elev=.15f;break;
            case HERO_RANGER:case HERO_ROGUE:case HERO_WIZARD:case HERO_CLERIC:m=SpriteModels.hero(frame,id-HERO_RANGER+1);elev=.15f;break;
            default:{m=SpriteModels.item(id,frame);
                boolean tall=id==TORCH||id==COLUMN||id==LAMP||id==SWITCH||id==SWITCH_ON||id==BARREL;
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
    private static final Sprite[][] fp=new Sprite[8][2];
    /** First-person weapon (Rpg.FP_*), at rest or mid-attack. Rendered from 3D models on first use. */
    static Sprite fpSprite(int w,boolean attacking){
        w=Math.max(0,Math.min(7,w));int k=attacking?1:0;
        synchronized(fp){if(fp[w][k]!=null)return fp[w][k];}
        Sprite s=SpriteModels.render(SpriteModels.firstPerson(w,attacking),(float)Math.PI,.06f,96f,false,500+w*2+k);if(pixel)s=pixelate(s,PIXEL_K);
        synchronized(fp){if(fp[w][k]==null)fp[w][k]=s;return fp[w][k];}
    }
    static Sprite weaponSprite(int w){init();return weapons[Math.max(0,Math.min(4,w))];}
    static Sprite flashSprite(int w){init();return flashes[Math.max(0,Math.min(4,w))];}
    static android.graphics.Bitmap preview(int id){
        Sprite s=getNow(id,F_IDLE,0);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(s.w,s.h,android.graphics.Bitmap.Config.ARGB_8888);b.setPixels(s.px,0,s.w,0,0,s.w,s.h);return b;
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
