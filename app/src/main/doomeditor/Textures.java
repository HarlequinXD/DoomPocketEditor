package com.retro.doomeditor;

/** Procedurally generated 64x64 textures plus pre-shaded light tables used by the renderer. */
final class Textures {
    private Textures(){}
    static final int SIZE=64,SHIFT=6,MASK=63,LIGHTS=32;
    static final String[] NAMES={
        "Castle: Grey Stone","Dungeon: Red Brick","Castle: Flagstone Floor","Ruins: Sandstone Brick","Arcane: Rune Panel","Tavern: Oak Planks","Temple: Green Marble","Swamp: Poison Bog","Infernal: Lava","Cave: Rough Rock","Castle: Iron-Banded Door","Castle: Lantern Ceiling","Sky",
        "Castle: Large Ashlar","Castle: Battlements","Castle: Arrow Slit","Castle: Portcullis","Castle: Royal Banner","Castle: Throne Carpet","Castle: Basketweave Floor","Castle: Painted Plaster",
        "Dungeon: Damp Brick","Dungeon: Mossy Blocks","Dungeon: Prison Bars","Dungeon: Iron Grate","Dungeon: Chained Wall","Dungeon: Slime Stone","Dungeon: Cracked Floor","Dungeon: Dark Cobbles","Dungeon: Torch Sconce","Dungeon: Rusted Iron",
        "Crypt: Bone Wall","Crypt: Skull Niches","Crypt: Tomb Slabs","Crypt: Carved Sarcophagus","Crypt: Cobwebbed Stone","Crypt: Ossuary Floor","Crypt: Black Granite","Crypt: Grave Dirt",
        "Temple: White Marble","Temple: Gold Mosaic","Temple: Stained Glass","Temple: Carved Frieze","Temple: Checker Marble","Temple: Holy Sigil","Temple: Fluted Column","Temple: Azure Tiles",
        "Elven: Living Wood","Elven: Leaf Lattice","Elven: Moonstone","Elven: Silver Filigree","Elven: Vine Wall","Elven: Pale Birch","Elven: Petal Floor",
        "Dwarven: Hewn Granite","Dwarven: Bronze Plate","Dwarven: Rune Bands","Dwarven: Forge Brick","Dwarven: Mine Supports","Dwarven: Hex Floor","Dwarven: Ore Vein",
        "Cave: Limestone","Cave: Crystal Cluster","Cave: Glowing Fungus","Cave: Wet Stone","Cave: Gravel","Cave: Basalt Columns","Cave: Dark Water",
        "Tavern: Timber Frame","Tavern: Thatch","Tavern: Wattle and Daub","Tavern: Rough Planks","Tavern: Woven Rug","Village: Wood Shingles","Village: Fieldstone","Tavern: Hearth Stone",
        "Arcane: Glowing Runes","Arcane: Arcane Swirl","Arcane: Star Chart","Arcane: Bookshelves","Arcane: Summoning Circle","Arcane: Violet Tiles","Arcane: Mana Crystal",
        "Infernal: Hellrock","Infernal: Demon Brick","Infernal: Brimstone","Infernal: Ash Floor","Infernal: Obsidian","Infernal: Blood Pool",
        "Nature: Grass","Nature: Forest Floor","Nature: Dirt Path","Nature: Water","Nature: Snow","Nature: Ice","Nature: Tree Bark",
        "Ruins: Weathered Stone","Ruins: Hieroglyphs","Ruins: Overgrown Brick","Ruins: Desert Sand"};
    static final int COUNT=NAMES.length,SKY=12;
    /** Textures that ignore distance fading (glowing liquids, lights, magic). */
    static final boolean[] BRIGHT=new boolean[COUNT];
    static{for(int i:new int[]{7,8,11,12,41,44,49,62,63,76,77,80,82,88})BRIGHT[i]=true;}
    /** How strongly each texture's relief (normal + parallax) shows: liquids, ice and glass stay nearly flat. */
    static final float[] RELIEF=new float[COUNT];
    static{java.util.Arrays.fill(RELIEF,1f);RELIEF[SKY]=0;
        for(int i=0;i<COUNT;i++){String n=NAMES[i].toLowerCase(java.util.Locale.US);
            if(n.contains("water")||n.contains("lava")||n.contains("bog")||n.contains("blood pool")||n.contains("ice")||n.contains("stained glass")||n.contains("slime"))RELIEF[i]=.2f;
            else if(n.contains("carpet")||n.contains("rug")||n.contains("plaster")||n.contains("snow")||n.contains("sand")||n.contains("banner"))RELIEF[i]=.5f;}}
    /**
     * Relief map for a texture: RGB = tangent-space normal, A = height (bright = raised). Height comes from the
     * texture's own brightness, softened and stretched to its full range, so mortar and cracks sink and bricks,
     * planks and stones stand out; the normal is the slope of that height.
     */
    /** Average colour of each texture (0..1 RGB), used for bounce light colour. */
    static float[] average(int t){int[] p=base[t];float r=0,g=0,b=0;for(int c:p){r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}float n=p.length*255f;return new float[]{r/n,g/n,b/n};}
    /** Physically based material values by texture: {smoothness 0..1, metalness 0..1}. */
    static float[] pbr(int t){
        String n=NAMES[t].toLowerCase(java.util.Locale.US);float sm=.25f,me=0;
        if(n.contains("wood")||n.contains("plank")||n.contains("oak")||n.contains("birch")||n.contains("timber")||n.contains("bark")||n.contains("bookshel")||n.contains("shingle")||n.contains("supports"))sm=.35f;
        if(n.contains("grass")||n.contains("dirt")||n.contains("sand")||n.contains("gravel")||n.contains("thatch")||n.contains("ash floor")||n.contains("forest")||n.contains("rug")||n.contains("carpet")||n.contains("banner")||n.contains("wattle")||n.contains("snow"))sm=.08f;
        if(n.contains("marble")||n.contains("tiles")||n.contains("mosaic")||n.contains("moonstone"))sm=.75f;
        if(n.contains("obsidian")||n.contains("black granite")||n.contains("crystal")||n.contains("stained glass"))sm=.85f;
        if(n.contains("wet")||n.contains("damp")||n.contains("slime")||n.contains("bog"))sm=.6f;
        if(n.contains("water")||n.contains("blood pool")||n.contains("ice"))sm=.93f;
        if(n.contains("lava"))sm=.5f;
        if(n.contains("iron")||n.contains("bars")||n.contains("grate")||n.contains("portcullis")){me=.85f;sm=.55f;}
        if(n.contains("rusted")){me=.45f;sm=.2f;}
        if(n.contains("bronze")||n.contains("filigree")||n.contains("gold")){me=.9f;sm=.65f;}
        if(n.contains("banded door")||n.contains("chained")||n.contains("ore vein")||n.contains("rune bands")){me=.35f;sm=.4f;}
        return new float[]{sm,me};
    }
    /**
     * Material map for a texture: R = smoothness, G = metalness, B = ambient occlusion. Smoothness follows the
     * material type and the height map: raised, worn faces are smoother than mortar and cracks. Metal parts are
     * found by colour (grey or golden texels in a metal texture). Cavities that sit below their surroundings get
     * ambient occlusion.
     */
    static int[] material(int t){
        int[] rel=relief(t),src=base[t];int S=SIZE,n=S*S;float[] pb=pbr(t);float[] h=new float[n],wide=new float[n];
        for(int i=0;i<n;i++)h[i]=((rel[i]>>>24)&255)/255f;
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){float sum=0;for(int dy=-3;dy<=3;dy++)for(int dx=-3;dx<=3;dx++)sum+=h[((y+dy)&(S-1))*S+((x+dx)&(S-1))];wide[y*S+x]=sum/49f;}
        int[] out=new int[n];
        for(int i=0;i<n;i++){int c=src[i];float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f,mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
            float sm=pb[0]*(.65f+.5f*h[i]);float me=pb[1];
            if(me>0){boolean metalish=(mx-mn<.12f&&mx>.25f)||(r>g&&g>b&&r-b>.2f&&mx>.35f);me=metalish?me:me*.25f;}
            float ao=1-Math.max(0,Math.min(.6f,(wide[i]-h[i])*2.2f));
            out[i]=0xff000000|(Math.round(Math.min(1,sm)*255)<<16)|(Math.round(me*255)<<8)|Math.round(ao*255);}
        return out;
    }
    /** Textures with glowing parts (lava cracks, runes, crystals, fungus, flames, stained glass...). */
    static boolean glows(int t){
        if(BRIGHT[t]&&t!=SKY)return true;String n=NAMES[t].toLowerCase(java.util.Locale.US);
        return n.contains("torch")||n.contains("rune panel")||n.contains("forge")||n.contains("brimstone")||n.contains("hellrock")||n.contains("lantern");
    }
    /**
     * Emissive map: the light a material gives off by itself (RGB, black = none). Only glowing materials have one,
     * and only their bright, saturated texels glow (the lava's cracks, not its crust; a sconce's flame, not the wall).
     */
    static int[] emissive(int t){
        int[] src=base[t],out=new int[src.length];if(!glows(t)){java.util.Arrays.fill(out,0xff000000);return out;}
        String n=NAMES[t].toLowerCase(java.util.Locale.US);
        float lo=n.contains("lava")||n.contains("lantern")?.45f:n.contains("water")||n.contains("bog")||n.contains("blood")?.62f:.55f;
        float k=n.contains("lava")?1f:n.contains("stained glass")?.85f:.9f;
        for(int i=0;i<src.length;i++){int c=src[i];float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f,mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
            float sat=mx<1e-3f?0:(mx-mn)/mx,e=sat(((mx-lo)/(1-lo)))*(.35f+.65f*sat)*k;e=e*e*(3-2*e);
            out[i]=0xff000000|(Math.round(r*e*255)<<16)|(Math.round(g*e*255)<<8)|Math.round(b*e*255);}
        return out;
    }
    static int[] relief(int t){
        int[] src=base[t];int S=SIZE,n=S*S;float[] h=new float[n],b=new float[n];
        for(int i=0;i<n;i++){int c=src[i];h[i]=(((c>>16)&255)*.3f+((c>>8)&255)*.59f+(c&255)*.11f)/255f;}
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){float sum=0;   // soften (wrapping, so tiles stay seamless)
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)sum+=h[((y+dy)&(S-1))*S+((x+dx)&(S-1))]*(dx==0&&dy==0?4:dx==0||dy==0?2:1);
            b[y*S+x]=sum/16f;}
        float lo=1,hi=0;for(float v:b){lo=Math.min(lo,v);hi=Math.max(hi,v);}
        float range=hi-lo;for(int i=0;i<n;i++)b[i]=range<.02f?.5f:(float)Math.pow((b[i]-lo)/range,.8f);
        float k=5f*RELIEF[t];int[] out=new int[n];
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            float dx=b[y*S+((x+1)&(S-1))]-b[y*S+((x-1)&(S-1))],dy=b[((y+1)&(S-1))*S+x]-b[((y-1)&(S-1))*S+x];
            float nx=-dx*k,ny=-dy*k,nz=1,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=l;ny/=l;nz/=l;
            int H=Math.round((RELIEF[t]<=0?.5f:b[y*S+x])*255);
            out[y*S+x]=(H<<24)|(Math.round((nx*.5f+.5f)*255)<<16)|(Math.round((ny*.5f+.5f)*255)<<8)|Math.round((nz*.5f+.5f)*255);}
        return out;
    }
    static final int[][] base=new int[COUNT][];
    /** Pre-shaded light tables, built the first time a texture is drawn. */
    private static final int[][][] shaded=new int[COUNT][][];
    private static boolean ready;
    static final int SKY_W=512,SKY_H=128;
    static int[] sky;

    static synchronized void init(){
        if(ready)return;
        for(int t=0;t<COUNT;t++)base[t]=generate(t);
        sky=generateSky();
        ready=true;
    }
    /** Light tables for one texture: [light 0..31][texel]. */
    static int[][] table(int t){
        int[][] tb=shaded[t];if(tb!=null)return tb;
        synchronized(Textures.class){
            if(shaded[t]==null){int[][] out=new int[LIGHTS][];int[] src=base[t];
                for(int l=0;l<LIGHTS;l++){int m=lightMul(l);int[] dst=new int[src.length];for(int i=0;i<src.length;i++)dst[i]=mul(src[i],m);out[l]=dst;}
                shaded[t]=out;}
            return shaded[t];
        }
    }
    /** Light level 0..31 to a multiplier out of 256 (slightly non-linear, like Doom's colormaps). */
    static int lightMul(int l){float f=l/31f;return Math.round((float)Math.pow(f,1.25)*256f*1.06f);}
    static int mul(int c,int m){
        int r=Math.min(255,((c>>16)&255)*m>>8),g=Math.min(255,((c>>8)&255)*m>>8),b=Math.min(255,(c&255)*m>>8);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    static int rgb(float r,float g,float b){return 0xff000000|(clamp(r)<<16)|(clamp(g)<<8)|clamp(b);}
    static int clamp(float v){return v<0?0:v>255?255:(int)v;}
    static float hash(int x,int y,int s){int h=x*374761393+y*668265263+s*1442695041;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0xffff)/65535f;}
    /** Smooth tileable value noise. */
    static float noise(float x,float y,int period,int seed){
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);float fx=x-x0,fy=y-y0;
        fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        int a=Math.floorMod(x0,period),b=Math.floorMod(x0+1,period),c=Math.floorMod(y0,period),d=Math.floorMod(y0+1,period);
        float v00=hash(a,c,seed),v10=hash(b,c,seed),v01=hash(a,d,seed),v11=hash(b,d,seed);
        return (v00*(1-fx)+v10*fx)*(1-fy)+(v01*(1-fx)+v11*fx)*fy;
    }
    static float fbm(int x,int y,int seed){float v=0,amp=.5f;int f=4;for(int o=0;o<4;o++){v+=noise(x*f/64f,y*f/64f,f,seed+o)*amp;amp*=.5f;f*=2;}return v;}

    private static int[] generate(int t){
        int[] px=new int[SIZE*SIZE];
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            float n=fbm(x,y,t*7+1),g=hash(x,y,t+99)*.12f-.06f;
            px[y*SIZE+x]=tex(t,x,y,n,g,hash(x,y,t*7+3));
        }
        return px;
    }
    private static final float TW=(float)(2*Math.PI/64);
    private static float sn(float v){return (float)Math.sin(v);}
    private static float cs(float v){return (float)Math.cos(v);}
    /** Shade helper: base colour scaled by s. */
    private static int c(int rgb,float s){return rgb(((rgb>>16)&255)*s,((rgb>>8)&255)*s,(rgb&255)*s);}
    private static int mix(int a,int b,float t){t=Math.max(0,Math.min(1,t));return rgb(((a>>16)&255)*(1-t)+((b>>16)&255)*t,((a>>8)&255)*(1-t)+((b>>8)&255)*t,(a&255)*(1-t)+(b&255)*t);}
    private static float sat(float v){return v<0?0:v>1?1:v;}
    /** Block grid cell: {u,v,id,mortar}. Staggered rows are offset by half a block. Tiles when bw and 2*bh divide 64. */
    private static int[] cell(int x,int y,int bw,int bh,int m,boolean stagger){
        int row=y/bh,off=stagger?(row&1)*(bw/2):0,xx=(x+off)&63,bx=xx%bw,by=y%bh;
        return new int[]{bx,by,row*64+xx/bw,(bx<m||by<m)?1:0};}
    /** Bevelled masonry block: lit top-left edge, shaded bottom-right edge, per-block tint. */
    private static int block(int x,int y,int bw,int bh,int m,boolean stagger,int stone,int mortar,int seed,float n,float g,float var){
        int[] b=cell(x,y,bw,bh,m,stagger);if(b[3]==1)return c(mortar,.85f+g+n*.25f);
        float s=.6f+(hash(b[2],seed,17)-.5f)*var+n*.34f+g;
        if(b[0]==m||b[1]==m)s+=.17f;else if(b[0]==bw-1||b[1]==bh-1)s-=.2f;
        return c(stone,s);}
    /** Tileable Worley noise with cs-pixel cells: {nearest, second nearest, nearest cell id}. */
    private static float[] worley(int x,int y,int cs,int seed){
        int cn=64/cs,cx=x/cs,cy=y/cs;float d1=1e9f,d2=1e9f;int id=0;
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){int gx=cx+i,gy=cy+j,wx=Math.floorMod(gx,cn),wy=Math.floorMod(gy,cn);
            float px=(gx+.15f+hash(wx,wy,seed)*.7f)*cs,py=(gy+.15f+hash(wx,wy,seed+1)*.7f)*cs,d=(float)Math.hypot(px-x-.5f,py-y-.5f);
            if(d<d1){d2=d1;d1=d;id=wy*cn+wx;}else if(d<d2)d2=d;}
        return new float[]{d1,d2,id};}
    /** Irregular stones from Worley cells: mortar where two cells meet, rounded shading inside. */
    private static int stones(int x,int y,int cs,int seed,int stone,int mortar,float n,float g,float var,float gap){
        float[] w=worley(x,y,cs,seed);float e=w[1]-w[0];if(e<gap)return c(mortar,.8f+g+n*.2f);
        float s=.58f+(hash((int)w[2],seed,3)-.5f)*var+n*.3f+g-w[0]/cs*.25f+(e<gap+1.2f?-.12f:0);return c(stone,s);}
    /** Planks: vertical when vert, else horizontal; width pw. */
    private static int planks(int x,int y,int pw,boolean vert,int wood,int seed,float n,float g){
        int a=vert?x:y,b=vert?y:x,p=a/pw;float grain=sn((b+hash(p,0,seed)*64)*.32f+n*5+a*.4f)*.07f;
        float s=.62f+grain+n*.25f+g+(hash(p,1,seed)-.5f)*.18f;int u=a%pw;
        if(u==0)s=.26f;else if(u==1)s+=.1f;else if(u==pw-1)s-=.12f;
        int joint=(int)(hash(p,2,seed)*63);if(b==joint&&u!=0)s=.3f;
        if((b==(joint+20)%64||b==(joint+44)%64)&&(u==2||u==pw-3))s=.95f;
        return c(wood,s);}
    private static float veins(int x,int y,int seed,float n,float freq){return Math.abs(sn((x*.6f+y)*TW*freq+fbm(x,y,seed)*7+n*3));}
    private static int glowMix(int base,int glow,float t){return mix(base,glow,t);}

    private static int tex(int t,int x,int y,float n,float g,float h){
        switch(t){
            // ---------- core slots (kept in their old roles so existing levels convert sensibly) ----------
            case 0:return block(x,y,32,16,1,true,0x8c8e92,0x3a3a3c,0,n,g,.3f);
            case 1:return block(x,y,16,8,1,true,0x8e4432,0x4a4440,1,n,g,.3f);
            case 2:return stones(x,y,16,2,0x857c6c,0x2c2a26,n,g,.3f,1.6f);
            case 3:{int col=block(x,y,32,16,1,true,0xd6ae74,0x8a6a40,3,n,g,.2f);return c(col,1+sn(y*TW*8+n*4)*.05f);}
            case 4:{ // carved rune panel
                int u=x%32,v=y%32,id=(x/32)+(y/32)*2;if(u<2||v<2)return c(0x24242a,1+g);
                float s=.55f+n*.3f+g;if(u==2||v==2)s+=.15f;if(u==31||v==31)s-=.15f;int col=c(0x4c4c5c,s);
                boolean r=false;
                if(u>8&&u<24&&v>6&&v<26){switch(id&3){case 0:r=u==16||(v-6==u-8&&u<16)||(v-6==24-u&&u>16);break;
                    case 1:r=(u==11||u==21)&&v<24||v==16&&u>10&&u<22;break;case 2:r=Math.abs(u-16)==Math.abs(v-16)||u==16&&v>16;break;default:r=v==9&&u>10&&u<22||u==16||v==23&&u>12&&u<20;}}
                return r?mix(col,0xff7fd8ff,.75f):col;}
            case 5:return planks(x,y,16,true,0x9a683c,5,n,g);
            case 6:{float v=veins(x,y,60,n,2);float s=.48f+v*.5f+g;if(x%32==0||y%32==0)s*=.55f;return rgb(40*s,112*s,82*s);}
            case 7:{ // poison bog
                float v=fbm(x+7,y+3,77);int col=mix(0xff1e3a14,0xff5aa02a,v);float[] w=worley(x,y,16,70);
                if(w[0]<3)col=mix(col,0xffb0ff70,(3-w[0])/3*.8f);if(w[0]>2.6f&&w[0]<3.4f)col=0xffd0ffa0;return col;}
            case 8:{ // lava with dark crust plates
                float[] w=worley(x,y,16,80);float e=w[1]-w[0];float v=fbm(x,y,88);
                if(e<3.5f){float k=1-e/3.5f;return mix(0xffe05010,0xffffe060,k*.8f+v*.3f);}
                return c(0x3a1a12,.6f+v*.6f+g+(e<5?.3f:0));}
            case 9:{float v=fbm(x,y,44);int col=c(0x74665a,.45f+v*.75f+g);float cr=Math.abs(sn(x*TW*2+v*10)+sn(y*TW*3+n*8));if(cr<.07f)col=c(0x2a221c,1);return col;}
            case 10:{ // iron-banded oak door
                int col=planks(x,y,16,true,0x6e4626,10,n,g);int v=y%32;
                if(v>=4&&v<9){col=c(0x3e3e44,.75f+n*.3f+g+(v==4?.2f:v==8?-.15f:0));if(v==6&&x%8==4)col=c(0xc8c8d0,1);}
                return col;}
            case 11:{ // timber ceiling with a hanging lantern glow in each bay
                int u=x%32,v=y%32;boolean beam=u<5||v<5;
                if(beam){float s=.55f+n*.3f+g;if(u==0||v==0)s-=.2f;return c(0x5a3a20,s);}
                float d=(float)Math.hypot(u-18.5f,v-18.5f);int col=c(0xc8b490,.7f+n*.2f+g);
                if(d<4)return mix(0xffffd070,0xffffffe0,1-d/4);if(d<11)col=mix(col,0xffffc060,(11-d)/7*.6f);return col;}
            case 12:{float t2=y/64f;float r=40+t2*170,gg=40+t2*90,b=90+t2*20;
                if(y>40+(int)(Math.sin(x*.2f)*5+Math.sin(x*.07f)*6)){r=40;gg=30;b=40;}return rgb(r,gg,b);}
            // ---------- castle ----------
            case 13:return block(x,y,32,32,1,true,0x9c978c,0x3e3a34,13,n,g,.25f);
            case 14:{if(y<16&&((x/16)&1)==1)return c(0x18181e,1+g);int col=block(x,y,16,16,1,true,0x8a8a86,0x34342f,14,n,g,.3f);if(y==16)col=c(col,.6f);return col;}
            case 15:{int col=block(x,y,16,8,1,true,0x8c8880,0x3a3833,15,n,g,.28f);
                boolean slit=(x>=30&&x<34&&y>=10&&y<54)||(y>=28&&y<32&&x>=22&&x<42);boolean rim=(x>=28&&x<36&&y>=8&&y<56)||(y>=26&&y<34&&x>=20&&x<44);
                if(slit)return c(0x0c0c10,1);if(rim)return c(0xa8a49a,.85f+n*.2f);return col;}
            case 16:{ // portcullis
                int u=x%16,v=y%16;boolean vb=u<5,hb=v>=6&&v<9;
                if(vb){float cyl=cs((u-2f)/2.5f*1.2f);return c(0x5a5a62,.4f+cyl*.7f+n*.2f+g);}
                if(hb)return c(0x4a4a52,.55f+(v==6?.3f:0)+n*.2f+g);return c(0x0c0c0e,1+g);}
            case 17:{ // royal banner over stone
                int col=block(x,y,32,16,1,true,0x8a867e,0x3a3833,17,n,g,.25f);int bx=x-32;
                boolean in=Math.abs(bx)<16&&y>2&&(y<50||Math.abs(bx)<(62-y)*1.4f);
                if(!in)return Math.abs(bx)<18&&y>2&&y<58?c(col,.6f):col;
                int ban=c(0x9a1c24,.7f+n*.25f+sn(x*.9f)*.06f+g);
                if(Math.abs(bx)>12||y<6)ban=c(0xd8a830,.85f+g);
                float d=Math.abs(bx)+Math.abs(y-26);if(d<9&&d>6)ban=c(0xd8a830,.95f);if(d<3)ban=c(0xf0d060,1);
                if(y<=3)ban=c(0x3a2a18,1);return ban;}
            case 18:{ // throne-room carpet
                int col=c(0x8a1820,.62f+n*.25f+h*.12f);if(x<6||x>57)col=c(0xd0a030,.8f+g);if(x==6||x==57)col=c(0x3a1010,1);
                int u=(x-32),v=y%16-8;float d=Math.abs(u)/1.6f+Math.abs(v);if(d<6&&d>4)col=c(0xd8a830,.9f);if(d<2)col=c(0x1a3a6a,.9f);return col;}
            case 19:{ // basketweave brick floor
                int cx=x/16,cy=y/16,u=x%16,v=y%16;boolean horiz=((cx+cy)&1)==0;int a=horiz?v:u;int brick=a/8,id=cx*7+cy*13+brick;
                if(a%8==0||(horiz?u:v)==0)return c(0x4a3e34,1+g);float s=.6f+(hash(id,1,19)-.5f)*.25f+n*.3f+g;if(a%8==1)s+=.12f;return c(0x9a5a40,s);}
            case 20:{ // painted plaster with stencil band and wainscot
                int col=c(0xd8ccb0,.78f+n*.2f+g);
                if(y>=8&&y<16){float p=Math.abs(sn(x*TW*8))*(1-Math.abs(y-12)/4f);col=p>.4f?c(0x8a2a20,.85f):c(0xc8b890,.8f);}if(y==7||y==16)col=c(0x6a4a2a,.9f);
                if(y>=44){col=planks(x,y,16,true,0x5a3820,20,n,g);if(y==44)col=c(0x2a1a0e,1);}return col;}
            // ---------- dungeon ----------
            case 21:{int col=block(x,y,16,8,1,true,0x6a645a,0x2e2c28,21,n,g,.3f);float wet=fbm(x,0,210)*(1-y/80f);if(wet>.42f)col=c(col,.65f);if(h>.996f)col=c(0xc0c8d0,.9f);return col;}
            case 22:{int col=block(x,y,16,16,1,true,0x6a6a62,0x2a2a24,22,n,g,.3f);float m=fbm(x,y,220);if(m>.5f)col=mix(col,0xff3e6a2a,(m-.5f)*3.5f);return col;}
            case 23:{ // prison bars over a dark cell
                int u=x%8;if(u<3){float cyl=cs((u-1f)*.9f);return c(0x6a6a70,.35f+cyl*.7f+g+(fbm(x,y,230)>.6f?-.2f:0));}
                if(y<4||y>59)return c(0x3a3a40,.8f+n*.2f);return c(0x14120f,1+n*.6f+g);}
            case 24:{boolean bar=x%8<3||y%8<3;if(!bar)return c(0x0a0a0a,1+g);int col=c(0x6a6a6e,.55f+n*.3f+g+((x%8==0||y%8==0)?.2f:0));float r=fbm(x,y,240);if(r>.5f)col=mix(col,0xff7a3a1a,(r-.5f)*3);return col;}
            case 25:{ // stone wall with a hanging chain
                int col=block(x,y,16,16,1,true,0x6e6a64,0x2c2a26,25,n,g,.3f);int v=y%12,u=x-32;
                boolean link=(v<8)?(Math.abs(u)<4&&(Math.abs(u)>=2||v==0||v==7)):(Math.abs(u)<1.5f);if(Math.abs(u)<6&&!link&&Math.abs(u)<5)col=c(col,.8f);
                return link?c(0x8a8a90,.6f+(u<0?.35f:0)+g):col;}
            case 26:{ // slime oozing down stone
                int col=block(x,y,16,16,1,true,0x5e605a,0x262622,26,n,g,.3f);int dc=x/3;float len=6+hash(dc,0,260)*hash(dc,1,261)*52;boolean on=hash(dc,2,262)>.35f;
                if(on&&y<len){float k=1-y/len;col=mix(col,0xff4ab030,.55f+k*.35f);if(x%3==0)col=c(col,.8f);if(y>len-2)col=0xffa8ff80;}
                else if(on&&y<len+3&&x%3==1&&y>=len+1)col=0xff90f070;return col;}
            case 27:{ // cracked floor slabs
                int col=block(x,y,32,32,1,false,0x77736a,0x2a2824,27,n,g,.25f);float[] w=worley(x,y,32,271);
                if(w[1]-w[0]<1.1f&&hash((int)w[2],0,272)>.3f)col=c(0x241f1a,1);return col;}
            case 28:{float[] w=worley(x,y,8,28);float e=w[1]-w[0];if(e<1.3f)return c(0x161412,1+g);return c(0x4e4a44,.6f+(hash((int)w[2],28,1)-.5f)*.35f+n*.3f+g+(.5f-w[0]/8)*.5f);}
            case 29:{ // wall sconce with torch flame
                int col=block(x,y,16,8,1,true,0x6e665c,0x2e2a26,29,n,g,.3f);float dx=x-32,dy=y-26;
                float glow=Math.max(0,1-(float)Math.hypot(dx,dy*1.2f)/26);col=mix(col,0xffffa040,glow*glow*.7f);
                if(Math.abs(dx)<3&&y>30&&y<44)col=c(0x5a3a1e,.8f+g);if(y>=42&&y<46&&Math.abs(dx)<7)col=c(0x3a3a40,.9f);
                float fl=(dx*dx)/9f+(dy+2)*(dy+2)/36f;if(fl<1&&dy<4)col=mix(0xffff6010,0xffffffa0,1-fl);return col;}
            case 30:{int col=c(0x707880,.55f+n*.3f+g);float rust=fbm(x,y,550);if(rust>.45f)col=mix(col,0xff8a4420,(rust-.45f)*3);if(x%32==0||y%32==0)col=c(col,.55f);
                if((x%32==3||x%32==28)&&(y%32==3||y%32==28))col=c(0xc0c4c8,1);return col;}
            // ---------- crypt ----------
            case 31:{float u=(x+((y/16)&1)*8)%16-8,v=y%16-8;float d=u*u/42+v*v/14;
                if(d<1){float s=.72f+(1-d)*.35f+g;if(Math.abs(u)>5&&Math.abs(v)<1)s-=.2f;return c(0xe0d4b4,s);}return c(0x2a2018,1+g+n*.3f);}
            case 32:{int u=x%16,v=y%16;int col=c(0x3a2a24,.6f+n*.3f+g);if(u<1||v<1)col=c(0x1a1410,1);
                float d=((u-8)*(u-8))/30f+((v-7)*(v-7))/26f;if(d<1)col=c(0xd6caa8,.72f+(1-d)*.32f);
                if(d<1&&v>5&&v<9&&(u==5||u==6||u==10||u==11))col=c(0x1a1008,1);if(d<1&&v==8&&u==8)col=c(0x2a1a10,1);if(v>11&&v<14&&u>5&&u<11)col=u%2==0?c(0x2a1a10,1):c(0xc8bc98,.8f);return col;}
            case 33:{int[] b=cell(x,y,32,16,1,true);int col=block(x,y,32,16,1,true,0x6a6a6e,0x26262a,33,n,g,.2f);
                if(b[3]==0){int u=b[0]-16,v=b[1]-8;boolean cross=(Math.abs(u)<1.5f&&Math.abs(v)<6)||(Math.abs(v+2)<1&&Math.abs(u)<4);if(cross)col=c(0x2e2e32,1);}return col;}
            case 34:{ // carved sarcophagus stone
                int col=c(0x9a948a,.6f+n*.3f+g);if(y<6||y>57)col=c(0x7a746a,.7f+g+(y==5||y==58?-.25f:0));
                if(y>=10&&y<54&&(x%32==4||x%32==28))col=c(0x4a463e,1);if(y==10||y==53)col=c(0x4a463e,1);
                if(y>=16&&y<24){int z=(x+(y-16))%8;col=z<2?c(0x4e4a42,1):c(col,1.05f);}
                if(y>=30&&y<48&&Math.abs(x%32-16)<6){float d=Math.abs(x%32-16)/6f+Math.abs(y-39)/9f;col=d<1?c(0xb0aa9e,.75f+(1-d)*.25f):col;}return col;}
            case 35:{int col=block(x,y,16,16,1,true,0x707070,0x2e2e2e,35,n,g,.25f);
                float ang=(float)Math.atan2(y,x),d=(float)Math.hypot(x,y);boolean web=d<56&&(Math.abs(sn(ang*12))<.05f*d/10+.02f||Math.abs(sn(d*.55f))<.06f&&d>4);
                if(web)col=mix(col,0xffe8e8e8,.75f*(1-d/60));return col;}
            case 36:{int col=c(0x5a4a38,.5f+n*.45f+g);float[] w=worley(x,y,16,360);
                if(w[0]<5&&hash((int)w[2],1,36)>.5f){col=c(0xd8ccaa,.7f+(5-w[0])/10+g);if(w[0]<1.2f)col=c(0x2a2018,1);}
                else if(h>.96f)col=c(0xc8bc98,.8f);return col;}
            case 37:{int col=block(x,y,32,32,1,false,0x2a2a30,0x101014,37,n,g,.15f);if(h>.95f)col=c(0xa8a8b8,.6f+h*.3f);if(((x+y)&31)<2&&(x&31)>3)col=c(col,1.35f);return col;}
            case 38:{int col=c(0x4a3624,.45f+n*.5f+g);if(h>.97f)col=c(0x8a8478,.75f);if(fbm(x,y,380)>.62f)col=mix(col,0xff2a3a1a,.5f);return col;}
            // ---------- temple ----------
            case 39:{float v=veins(x,y,390,n,2);int col=c(0xeae6dc,.85f+n*.15f);if(v<.05f)col=c(0xd8b050,1);else if(v<.18f)col=mix(col,0xffb8b0a0,.4f);
                if(x%32==0||y%32==0)col=c(0xc8a860,.85f);return col;}
            case 40:{boolean grout=x%4==0||y%4==0;float s=hash(x/4,y/4,43);int col=grout?c(0x3a2a10,1):mix(0xffb08820,0xfff0d070,s);
                float d=Math.abs((x%32)-16)+Math.abs((y%32)-16);if(!grout&&d>10&&d<14)col=c(0x1a5a8a,.9f+s*.2f);return col;}
            case 41:{ // stained glass
                float[] w=worley(x,y,16,410);if(w[1]-w[0]<1.4f||x%32==0||y%32==0)return c(0x1a1a1e,1);
                int[] pal={0xffc02030,0xff2050c0,0xff20a050,0xffe0b020,0xff8030b0,0xff30a0c0};int col=pal[(int)(hash((int)w[2],0,41)*5.99f)];
                return c(col,.8f+n*.35f+(1-w[0]/12)*.3f);}
            case 42:{ // carved frieze band with a meander key
                int col=c(0xd0bc96,.68f+n*.25f+g);if(y==14||y==49)col=c(0x6a5a40,1);if(y==15||y==50)col=c(0xf0e0c0,.95f);
                if(y>=18&&y<46){int u=x%16,v=(y-18)%28;boolean key=(v<3)||(u<3&&v<20)||(v>=17&&v<20&&u<12)||(u>=9&&u<12&&v>=8&&v<20)||(v>=8&&v<11&&u>=5&&u<12);
                    col=key?c(0xe8d8b4,.95f+g):c(0x8a7658,.8f+n*.2f);}return col;}
            case 43:{boolean dark=(((x/16)+(y/16))&1)==1;float v=veins(x,y,430,n,3);int col=dark?c(0x26242a,.8f+n*.3f):c(0xe8e4dc,.85f+n*.15f);
                if(v<.08f)col=dark?c(0x6a6a72,1):c(0xa8a49c,1);if(x%16==0||y%16==0)col=c(0x8a7a50,.9f);return col;}
            case 44:{ // holy sigil
                int col=block(x,y,32,16,1,true,0xe0dcd0,0x9a9488,44,n,g,.15f);float dx=x-31.5f,dy=y-31.5f,d=(float)Math.hypot(dx,dy),a=(float)Math.atan2(dy,dx);
                if(d<26)col=mix(col,0xffffe8a0,(26-d)/26*.35f);
                if((d>18&&d<20.5f)||(d<17&&Math.abs(sn(a*6))<.12f&&d>6)||d<5)col=mix(0xffe8b030,0xfffff0b0,1-d/26);return col;}
            case 45:{ // fluted column
                if(y<5||y>58){float s=.7f+n*.2f+g+(y==4||y==59?-.25f:y==0||y==63?.15f:0);return c(0xe0d6c0,s);}
                int u=x%8;float cyl=cs((u-3.5f)/4f*1.6f);return c(0xe4dac4,.5f+cyl*.45f+n*.15f+g);}
            case 46:{boolean grout=x%8==0||y%8==0;if(grout)return c(0xd8d0c0,.9f);int u=x%8,v=y%8;float d=Math.min(Math.abs(u-4)+Math.abs(v-4)*2,Math.abs(u-4)*2+Math.abs(v-4));
                int col=c(0x1e5aa0,.75f+n*.3f+g+(u==1||v==1?.15f:0));if(d<3)col=c(0xe8f0ff,.95f);return col;}
            // ---------- elven ----------
            case 47:{float ring=sn((float)Math.hypot(x-32,y*2.2f-70)*.5f+n*6);int col=c(0x6a4a2a,.6f+ring*.15f+n*.3f+g);if(fbm(x,y,440)>.58f)col=mix(col,0xff4a8a3a,.6f);return col;}
            case 48:{ // leaf lattice
                int a=(x+y)&15,b=(x-y+64)&15;boolean lat=a<3||b<3;if(lat)return c(0xd8c8a0,.7f+n*.25f+g+(a==0||b==0?.15f:0));
                float l=fbm(x,y,480);return mix(0xff1e4a1e,0xff6ab040,l+h*.2f);}
            case 49:{int col=block(x,y,32,16,1,true,0xc8d8f0,0x5a6a8a,49,n,g,.15f);float gl=fbm(x,y,490);col=mix(col,0xffe8f4ff,gl*.5f);if(h>.985f)col=0xffffffff;return col;}
            case 50:{float f=Math.abs(sn(x*TW*2+sn(y*TW*2)*2.2f));float f2=Math.abs(sn(y*TW*2+sn(x*TW*2+1)*2.2f));int col=c(0x1a2440,.7f+n*.3f+g);
                if(f<.2f||f2<.2f)col=c(0xd0d8e8,.75f+(Math.min(f,f2)<.1f?.25f:0));return col;}
            case 51:{int col=block(x,y,16,16,1,true,0x8a8a80,0x34342e,51,n,g,.25f);
                for(int k=0;k<3;k++){float vx=k*21+8+sn(y*TW*2+k*2)*6;float d=Math.abs(x-vx);if(d<1.6f)return c(0x3a6a2a,.75f+g);
                    if(d<5&&((y+k*7)%10<4)&&hash(x,y/3,k)>.25f&&(x>vx)==((y/10+k)%2==0))return mix(0xff2e6a24,0xff70b048,h);}
                return col;}
            case 52:{ // birch trunks
                int u=x%16;if(u>=13)return c(0x1a2a14,.8f+n*.4f);float cyl=cs((u-6f)/7f*1.4f);int col=c(0xe8e4d8,.55f+cyl*.4f+g);
                if(((y+hash(x/16,0,52)*64)%9<1.5f)&&hash(x/3,y,52)>.35f)col=c(0x2a2622,1);return col;}
            case 53:{int col=mix(0xff2e5a24,0xff5a9a3a,n+h*.2f);float[] w=worley(x,y,8,530);if(w[0]<2.2f&&hash((int)w[2],0,53)>.45f)col=mix(0xffe090b8,0xfffff0f4,1-w[0]/2.2f);return col;}
            // ---------- dwarven ----------
            case 54:{int col=block(x,y,32,16,1,true,0x8a7c7a,0x2c2828,54,n,g,.25f);if(h>.9f)col=c(col,h>.96f?1.3f:.75f);if(((x+y*2)%6)==0&&fbm(x,y,541)>.5f)col=c(col,.85f);return col;}
            case 55:{int u=x%32,v=y%32;int col=c(0xb07a3a,.55f+n*.35f+g+(sn((x+y)*.3f)*.04f));if(u<2||v<2)col=c(0x4a3014,1);else if(u==2||v==2)col=c(col,1.25f);
                if((u==6||u==26)&&(v==6||v==26))col=c(0xf0c070,1);float pat=fbm(x,y,551);if(pat>.6f)col=mix(col,0xff3a8a6a,(pat-.6f)*3);return col;}
            case 56:{int col=block(x,y,32,16,1,true,0x7a7470,0x2a2826,56,n,g,.2f);
                if(y>=22&&y<42){col=c(0xb08040,.6f+n*.3f+g);if(y==22||y==41)col=c(0x5a3a18,1);if(y==23)col=c(col,1.3f);
                    int u=x%12,v=y-27;boolean r=v>=0&&v<10&&(u==3||(u-3==v/2&&u<9)||(v==0&&u>2&&u<9)||(u==8&&v>4));if(r)col=c(0x3a2410,1);}return col;}
            case 57:{int[] b=cell(x,y,16,8,1,true);if(b[3]==1){float f=fbm(x,y,570);return mix(0xff3a1008,0xffffa030,f*1.3f-.2f);}return block(x,y,16,8,1,true,0x3a3230,0,57,n,g,.3f);}
            case 58:{int col=c(0x5a5048,.45f+fbm(x,y,580)*.7f+g);
                if(x<10){col=planks(x,y,10,true,0x7a5430,58,n,g);if(x==9)col=c(0x2a1a0e,1);}
                if(y<8){col=planks(x,y,8,false,0x7a5430,59,n,g);if(y==7)col=c(0x2a1a0e,1);}
                if(x<10&&y<8&&(x==4&&y==4))col=c(0xa0a0a8,1);return col;}
            case 59:{float q=(x+((y/16)&1)*8)%16-8,r=y%16-8;boolean edge=Math.abs(q)>6.5f||Math.abs(q)+Math.abs(r)*.6f>9||Math.abs(r)>7;
                return edge?c(0x2a2420,1+g):c(0x8a7a68,.62f+n*.35f+g+(r<-4?.1f:0));}
            case 60:{float v=fbm(x,y,600);int col=c(0x5a5450,.45f+v*.6f+g);float vein=Math.abs(sn(x*TW*2+v*9+y*TW));
                if(vein<.1f)col=mix(0xffd8a030,0xfffff0a0,h);if(h>.993f)col=0xff60e0ff;return col;}
            // ---------- cave ----------
            case 61:{float st=sn(y*TW*5+n*5)*.5f+.5f;int col=c(0xc8b89a,.55f+st*.25f+n*.25f+g);if(Math.abs(sn(y*TW*5+n*5))<.05f)col=c(0x6a5a44,1);return col;}
            case 62:{float a=(x*1.2f+y)%20,b2=(x*1.2f-y+128)%20;float facet=Math.min(Math.min(a,20-a),Math.min(b2,20-b2));float m=fbm(x,y,620);
                if(m<.45f)return c(0x2a2630,.6f+n*.4f+g);int col=mix(0xff3a1a80,0xffd090ff,facet/10+n*.2f);if(facet<.8f)col=0xfff0e0ff;return col;}
            case 63:{int col=c(0x2a2a26,.5f+n*.5f+g);float[] w=worley(x,y,16,630);
                if(w[0]<4.5f&&hash((int)w[2],0,63)>.3f){float k=1-w[0]/4.5f;col=mix(col,hash((int)w[2],1,63)>.5f?0xff40ffd0:0xff90ff60,.4f+k*.6f);}
                else if(w[0]<8)col=mix(col,0xff206050,(8-w[0])/8*.35f);return col;}
            case 64:{float v=fbm(x,y,640);int col=c(0x3e4448,.5f+v*.6f+g);float sh=sn((x+y*2)*.25f+n*6);if(sh>.85f)col=c(col,1.5f);if(fbm(x,y,641)>.6f)col=c(col,.75f);return col;}
            case 65:return stones(x,y,4,65,0x8a8478,0x2a2622,n,g,.5f,.7f);
            case 66:{float q=(x+((y/16)&1)*8)%16-8,r=y%16-8;boolean edge=Math.abs(q)>6.6f||Math.abs(q)+Math.abs(r)*.58f>9.2f||Math.abs(r)>7.2f;
                if(edge)return c(0x0e0e10,1);int id=(x+((y/16)&1)*8)/16+y/16*8;return c(0x3a3a40,.6f+(hash(id,0,66)-.5f)*.3f+n*.3f+g+(r<-3?.15f:0));}
            case 67:{float wv=sn(x*TW*3+n*6)*sn(y*TW*2+n*5);int col=mix(0xff060c18,0xff1e3a5a,.35f+wv*.35f+n*.3f);if(wv>.8f)col=mix(col,0xff8ab0d0,.5f);return col;}
            // ---------- tavern & village ----------
            case 68:{boolean beam=x<6||(y>28&&y<34)||Math.abs((x-6)-(y*58/64f))<3;
                return beam?c(0x4a3020,.65f+n*.3f+g+(x==5?-.2f:0)):c(0xe0d4b8,.78f+n*.2f+g);}
            case 69:{float str=sn(x*1.7f+n*10+y*.2f);int col=c(0xb89848,.5f+str*.15f+n*.35f+g);if(y%8==0)col=c(col,.62f);if(y%8==1)col=c(col,1.15f);return col;}
            case 70:{float daub=fbm(x,y,700);if(daub>.48f)return c(0xc8b48c,.7f+n*.25f+g);
                boolean over=((x/8+y/4)&1)==0;int u=over?y%4:x%8;float s=.5f+sn(u/(over?4f:8f)*3.14f)*.3f;return c(0x6a4a2a,s+g);}
            case 71:{int pl=y/10;float s=.55f+sn((x+hash(pl,3,38)*64)*.3f)*.07f+n*.25f+g;if(y%10==0)s=.2f;if(x==(int)(hash(pl,4,38)*63))s=.25f;return c(0x8a6a44,s);}
            case 72:{int v=y%32;int col=c(0x8a2a1a,.6f+h*.15f+n*.2f);if(v<3||v>28)col=c(0x2a1a14,.8f+h*.2f);else if(v<6||v>25)col=c(0xd09030,.8f+h*.15f);
                float d=Math.abs((x%16)-8)+Math.abs(v-16);if(d<7&&d>4)col=c(0x1a3a5a,.9f+h*.1f);if(d<2)col=c(0xe0c070,.9f);return col;}
            case 73:{int row=y/8,off=(row&1)*4,u=(x+off)%8,v=y%8;float s=.55f+n*.3f+g+(hash((x+off)/8,row,73)-.5f)*.25f-v*.03f;if(u==0)s=.2f;if(v==7)s=.18f;return c(0x7a5a3a,s);}
            case 74:{float[] w=worley(x,y,16,740);float e=w[1]-w[0];if(e<2f)return c(0xa09a8a,.75f+g+n*.2f);int[] pal={0x8a8278,0x7a6a5a,0x9a8a70,0x6a6a6a};
                return c(pal[(int)(hash((int)w[2],0,74)*3.99f)],.6f+n*.3f+g-w[0]/16*.3f+(e<3.2f?-.15f:0));}
            case 75:{int col=block(x,y,16,8,1,true,0x8a5a40,0x3a302a,75,n,g,.3f);float soot=sat((y-10)/40f)*(1-Math.abs(x-32)/40f)+fbm(x,y,750)*.3f;return mix(col,0xff181410,soot*.8f);}
            // ---------- arcane ----------
            case 76:{int col=c(0x2a2a34,.6f+n*.3f+g);int u=x%16,v=y%16,id=(x/16+y/16*4);
                boolean rune=(u>3&&u<12&&v>3&&v<12)&&((id%3==0&&(u==7||v==7))||(id%3==1&&(u==v||u+v==15))||(id%3==2&&(u==4||u==11||v==4)));
                if(rune)col=0xff60e0ff;else if(u>2&&u<13&&v>2&&v<13&&hash(u,v,id)>.97f)col=0xff3080a0;return col;}
            case 77:{float dx=(x%32)-16,dy=(y%32)-16,ang=(float)Math.atan2(dy,dx),d=(float)Math.hypot(dx,dy);
                float sw=sn(ang*3+d*.5f);return mix(0xff1a0a30,0xffb060ff,Math.max(0,sw)*Math.max(0,1-d/18)+n*.15f);}
            case 78:{int col=mix(0xff080c28,0xff1a2a5a,n);float d=(float)Math.hypot(x-32,y-32);if(Math.abs(d-26)<.7f)col=c(0xd8b050,.9f);
                if(h>.985f)col=0xffffffff;else if(h>.97f)col=0xff8090c0;
                int[][] st={{10,12},{22,18},{30,9},{44,20},{50,34},{38,44},{20,46}};for(int i=0;i<st.length;i++){int[] a=st[i],b=st[(i+1)%st.length];
                    float px=b[0]-a[0],py=b[1]-a[1],l=px*px+py*py,k=sat(((x-a[0])*px+(y-a[1])*py)/l),ex=a[0]+px*k-x,ey=a[1]+py*k-y;
                    if(ex*ex+ey*ey<.4f)col=mix(col,0xff90a8e0,.6f);if(Math.abs(x-a[0])+Math.abs(y-a[1])<2)col=0xfffff0c0;}return col;}
            case 79:{ // bookshelves
                int v=y%32;if(v<4){return c(0x5a3a1e,.6f+n*.3f+g+(v==0?-.25f:v==1?.15f:0));}if(x%64<3)return c(0x4a2e18,.7f+g);
                int bk=(int)((x+hash(y/32,0,79)*64)/4),bw=(int)(hash(bk,y/32,1)*2)+3;int u=(x+(int)(hash(y/32,0,79)*64))%4;
                float hgt=20+hash(bk,y/32,2)*8;if(v<32-hgt)return c(0x1a120c,1+g);int[] pal={0xff7a1a1a,0xff1a3a6a,0xff2a5a2a,0xff6a4a1a,0xff4a2a5a,0xff8a6a3a};
                int col=c(pal[(int)(hash(bk,y/32,3)*5.99f)],.6f+n*.2f+(u==0?-.3f:u==1?.15f:0));if(v==(int)(32-hgt)+4||v==29)col=c(0xd8b050,.8f);return col;}
            case 80:{int col=c(0x2a2628,.5f+n*.4f+g);float dx=x-31.5f,dy=y-31.5f,d=(float)Math.hypot(dx,dy);float line=99;
                for(int k=0;k<5;k++){double a1=-Math.PI/2+k*4*Math.PI/5,a2=-Math.PI/2+(k+1)*4*Math.PI/5;float ax=(float)Math.cos(a1)*24,ay=(float)Math.sin(a1)*24,bx=(float)Math.cos(a2)*24,by=(float)Math.sin(a2)*24;
                    float px=bx-ax,py=by-ay,kk=sat(((dx-ax)*px+(dy-ay)*py)/(px*px+py*py)),ex=ax+px*kk-dx,ey=ay+py*kk-dy;line=Math.min(line,(float)Math.hypot(ex,ey));}
                float ring=Math.min(Math.abs(d-25),Math.abs(d-29));float m=Math.min(line,ring);
                if(m<1.2f)return mix(0xffff3040,0xffffc0a0,1-m/1.2f);if(m<4)col=mix(col,0xff801020,(4-m)/4*.6f);return col;}
            case 81:{boolean grout=x%16==0||y%16==0;if(grout)return c(0xc8a040,.9f+g);int u=x%16,v=y%16;return c(0x5a2a8a,.65f+n*.25f+g+(u==1||v==1?.2f:u==15||v==15?-.15f:0)+sn((u+v)*.4f)*.05f);}
            case 82:{float a=(x+y)%16,b2=(x-y+64)%16;float facet=Math.min(Math.min(a,16-a),Math.min(b2,16-b2));int col=mix(0xff2050a0,0xff90f0ff,facet/8+n*.2f);if(facet<.8f)col=0xffe0ffff;return col;}
            // ---------- infernal ----------
            case 83:{float v=fbm(x,y,460);int col=c(0x5a2a1e,.5f+v*.6f+g);float crack=Math.abs(sn(x*TW*2+v*10)+sn(y*TW*3+n*8));if(crack<.08f)col=0xffff6010;else if(crack<.2f)col=mix(col,0xffa02008,.5f);return col;}
            case 84:{int[] b=cell(x,y,16,8,1,true);if(b[3]==1)return fbm(x,y,470)>.5f?0xffd04010:c(0x1a0a08,1);return block(x,y,16,8,1,true,0x6a1a14,0,84,n,g,.3f);}
            case 85:{float[] w=worley(x,y,8,850);int col=mix(0xff3a2c0c,0xffb8a030,1-w[0]/6+n*.3f-.15f);if(w[1]-w[0]<1.2f)col=c(0x1e1606,1);if(h>.99f)col=0xffff8020;return col;}
            case 86:{float v=fbm(x,y,490);int col=c(0x4a4440,.5f+v*.5f+g);if(h>.992f)col=0xffff7020;return col;}
            case 87:{float[] w=worley(x,y,16,870);float e=w[1]-w[0];int col=c(0x141018,.8f+n*.4f);if(e<1)col=c(0x3a2a4a,1);float sh=sn((x-y)*.3f+w[2]);if(sh>.9f)col=mix(col,0xff9070c0,.5f);return col;}
            case 88:{float v=fbm(x,y,480);return v>.6f?c(0xd02020,1):c(0x8a0a0a,.7f+v*.5f);}
            // ---------- nature ----------
            case 89:{float blade=hash(x,y/3,56);int col=mix(0xff2e5a1e,0xff6aa83a,n*.7f+blade*.4f);if(blade>.9f)col=c(col,1.25f);else if(blade<.1f)col=c(col,.7f);return col;}
            case 90:{float[] w=worley(x,y,8,900);int col=c(0x4a3420,.5f+n*.4f+g);
                if(w[0]<3.4f){int k=(int)(hash((int)w[2],0,90)*3.99f);int[] pal={0xffa05a1e,0xff7a3a14,0xffc08a2a,0xff4a6a2a};col=c(pal[k],.7f+(3.4f-w[0])/8+g);}return col;}
            case 91:{int col=c(0x7a5a38,.55f+n*.45f+g);if(h>.96f)col=c(0x9a9088,.8f);return col;}
            case 92:{float wv=sn(x*TW*2+n*6)*sn(y*TW*3+n*5);int col=mix(0xff12366a,0xff3a82c0,.4f+wv*.4f+n*.2f);if(wv>.85f)col=mix(col,0xffd0e8ff,.45f);return col;}
            case 93:{float v=fbm(x,y,930);int col=mix(0xffb8c8dc,0xfffafcff,v+h*.15f);if(h>.99f)col=0xffffffff;return col;}
            case 94:{float cr=Math.abs(sn(x*TW*2+fbm(x,y,600)*7)*sn(y*TW*2+n*6));int col=mix(0xff8ab8d8,0xffe0f4ff,n);if(cr<.05f)col=0xfff8ffff;return col;}
            case 95:{float fur=Math.abs(sn(x*TW*6+fbm(x,y,950)*8));int col=c(0x5a3e28,.45f+n*.35f+g+fur*.25f);if(fur<.15f)col=c(0x221810,1);return col;}
            // ---------- ruins ----------
            case 96:{int col=block(x,y,32,16,2,true,0xa49c8a,0x5a5448,96,n,g,.3f);float er=fbm(x,y,960);if(er>.6f)col=c(col,.7f);if(er>.68f)col=c(0x4a443a,1);return col;}
            case 97:{int col=c(0xd0a868,.65f+n*.25f+g);int u=x%16,v=y%16,id=hash(x/16,y/16,62)>.5f?1:0;
                boolean glyph=(u>3&&u<12&&v>2&&v<13)&&((id==0&&(u==5||v==12||(u>7&&v==6)))||(id==1&&(Math.abs(u-8)+Math.abs(v-7)==4||u==8)));
                if(glyph)col=c(0x5a3a20,1);if(y%16==0)col=c(0x8a6a40,1);return col;}
            case 98:{int col=block(x,y,16,8,1,true,0x8a5a44,0x4a4440,98,n,g,.3f);float m=fbm(x,y,980);if(m>.5f)col=mix(col,0xff3a6a2a,(m-.5f)*3);
                float vx=20+sn(y*TW*2)*8;if(Math.abs(x-vx)<1.3f)col=c(0x2e5a20,.9f);return col;}
            default:{float rip=sn((x*.4f+y)*TW*6+n*4)*.08f;return c(0xd8c088,.75f+rip+n*.15f+g*.5f);}
        }
    }
    private static int[] generateSky(){
        int[] px=new int[SKY_W*SKY_H];
        for(int y=0;y<SKY_H;y++)for(int x=0;x<SKY_W;x++){
            float t=y/(float)SKY_H;
            float r=30+t*180,g=24+t*80,b=60+t*10;
            float cloud=noise(x/40f,y/14f,SKY_W/40,3)*.6f+noise(x/12f,y/6f,SKY_W/12,4)*.4f;
            if(cloud>.55f){float k=(cloud-.55f)*1.6f;r+=k*90;g+=k*60;b+=k*50;}
            if(t<.35f&&hash(x,y,9)>.996f){r=g=b=230;}
            float m=SKY_H*.72f+(float)(Math.sin(x*2*Math.PI/SKY_W*3)*8+Math.sin(x*2*Math.PI/SKY_W*7+1)*6+Math.sin(x*2*Math.PI/SKY_W*17)*3);
            if(y>m){float d=(y-m)/(SKY_H-m);r=34-d*10;g=24-d*8;b=34-d*8;}
            px[y*SKY_W+x]=rgb(r,g,b);
        }
        return px;
    }
    static android.graphics.Bitmap preview(int t){
        init();android.graphics.Bitmap bmp=android.graphics.Bitmap.createBitmap(SIZE,SIZE,android.graphics.Bitmap.Config.ARGB_8888);
        bmp.setPixels(base[Math.max(0,Math.min(COUNT-1,t))],0,SIZE,0,0,SIZE,SIZE);return bmp;
    }
}
