package com.retro.doomeditor;

/** Procedurally generated 64x64 textures plus pre-shaded light tables used by the renderer. */
final class Textures {
    private Textures(){}
    static final int SIZE=64,SHIFT=6,MASK=63,LIGHTS=32;
    static final String[] NAMES={"Classic: Grey Stone","Classic: Brown Brick","Classic: Metal Floor","Classic: Tan Brick","Classic: Tech Panel","Classic: Wood","Classic: Green Marble","Classic: Nukage","Classic: Lava","Classic: Rock","Classic: Door Metal","Classic: Ceiling Light","Sky",
        "Sci-fi: Hex Plating","Sci-fi: Floor Grate","Sci-fi: Circuit Board","Sci-fi: Server Rack","Sci-fi: Bulkhead","Sci-fi: Neon Panel","Sci-fi: Pipe Wall","Sci-fi: Hazard Floor","Sci-fi: Holo Glass","Sci-fi: Reactor Core",
        "Horror: Bloody Tiles","Horror: Flesh Wall","Horror: Rotten Planks","Horror: Cracked Plaster","Horror: Morgue Tiles","Horror: Bone Wall","Horror: Grimy Concrete","Horror: Old Wallpaper",
        "Medieval: Castle Wall","Medieval: Cobblestone","Medieval: Timber Frame","Medieval: Flagstones","Medieval: Studded Door","Medieval: Tapestry","Medieval: Thatch","Medieval: Rough Planks",
        "Fantasy: Elven Marble","Fantasy: Crystal","Fantasy: Mossy Stone","Fantasy: Glowing Runes","Fantasy: Gold Mosaic","Fantasy: Living Wood","Fantasy: Arcane Swirl",
        "Hell: Hellrock","Hell: Demon Brick","Hell: Blood Pool","Hell: Ash Floor","Hell: Skull Wall",
        "Industrial: Corrugated Metal","Industrial: Diamond Plate","Industrial: Concrete Blocks","Industrial: Warning Stripes","Industrial: Rusty Steel",
        "Nature: Grass","Nature: Dirt Path","Nature: Sand","Nature: Water","Nature: Ice",
        "Desert: Sandstone Blocks","Desert: Hieroglyphs","Desert: Gold Trim",
        "Modern: Office Carpet","Modern: Ceiling Tiles","Modern: Bathroom Tiles","Modern: Asphalt","Modern: Hardwood Floor"};
    static final int COUNT=NAMES.length,SKY=12;
    /** Textures that ignore distance fading (glowing liquids, lights, magic). */
    static final boolean[] BRIGHT=new boolean[COUNT];
    static{for(int i:new int[]{7,8,11,12,18,22,40,42,45,48})BRIGHT[i]=true;}
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
            float n=fbm(x,y,t*7+1),grain=hash(x,y,t+99)*.12f;
            float r,g,b;
            if(t>12){px[y*SIZE+x]=extra(t,x,y,n,grain);continue;}
            switch(t){
                case 0:{ // grey stone blocks
                    int row=y/16,off=(row&1)*16;boolean mortar=y%16==0||(x+off)%32==0;
                    float s=.55f+n*.5f+grain;if(mortar)s=.32f+grain;else if(y%16==1||(x+off)%32==1)s+=.12f;
                    r=118*s;g=124*s;b=132*s;break;}
                case 1:case 3:{ // bricks
                    int row=y/8,off=(row&1)*8;boolean mortar=y%8==0||(x+off)%16==0;
                    float s=.6f+n*.45f+grain;if(mortar)s=.38f;else if(y%8==7)s*=.8f;
                    if(t==1){r=140*s;g=82*s;b=58*s;}else{r=178*s;g=150*s;b=104*s;}
                    if(mortar){r=g=b=74+grain*40;}break;}
                case 2:{ // metal floor plates
                    boolean seam=x%32==0||y%32==0;boolean rivet=(x%32==4||x%32==28)&&(y%32==4||y%32==28);
                    float s=.62f+n*.35f+grain*.6f;if(((x/4+y/4)&1)==0)s+=.05f;if(seam)s=.3f;if(rivet)s=.95f;
                    r=106*s;g=110*s;b=116*s;break;}
                case 4:{ // tech panel
                    boolean frame=x%32<2||y%32<2;float s=.55f+n*.2f+grain*.5f;
                    r=72*s;g=70*s;b=110*s;if(frame){r=g=b=48;}
                    int cx=x%32,cy=y%32;
                    if(cy>6&&cy<10&&cx>5&&cx<27){boolean on=hash(cx/3,y/32+x/32*3,4)>.45f;r=on?60:20;g=on?220:60;b=on?120:40;}
                    if(cy>14&&cy<26&&cx>8&&cx<24){r=34;g=40;b=56;if(cy%3==0){r=50;g=90;b=150;}}
                    break;}
                case 5:{ // wood planks
                    int plank=x/16;float stripe=(float)Math.sin((y+hash(plank,0,5)*64)*.35f+n*6)*.12f;
                    float s=.65f+stripe+grain;if(x%16==0)s=.35f;if(y==(int)(hash(plank,1,5)*60)&&x%16!=0)s=.4f;
                    r=150*s;g=104*s;b=58*s;break;}
                case 6:{ // green marble
                    float v=(float)Math.abs(Math.sin((x+y*.6f)*.12f+n*9));float s=.5f+v*.5f;if(x%32==0||y%32==0)s*=.6f;
                    r=40*s+grain*30;g=110*s+grain*30;b=80*s+grain*30;break;}
                case 7:{ // nukage
                    float v=fbm(x+7,y+3,77);float s=.55f+v*.6f;r=60*s;g=200*s;b=50*s;if(v>.62f){r=150;g=255;b=120;}break;}
                case 8:{ // lava
                    float v=fbm(x,y,88);float s=v>.55f?1.1f:.6f+v*.5f;r=240*s;g=(v>.55f?190:80)*s;b=30*s;break;}
                case 9:{ // rock
                    float v=fbm(x,y,44);float s=.45f+v*.75f+grain;r=110*s;g=86*s;b=64*s;break;}
                case 10:{ // door metal with hazard stripes at the bottom
                    float s=.6f+n*.3f+grain*.5f;r=120*s;g=124*s;b=130*s;
                    if(x%16==0)s=.35f;if(x%16==0){r=g=b=60;}
                    if(y>52){boolean yellow=((x+y)/6&1)==0;r=yellow?220:30;g=yellow?180:30;b=yellow?30:30;}
                    if(y==52||y==12){r=g=b=70;}break;}
                case 11:{ // ceiling light panel
                    boolean lamp=x>12&&x<52&&y>12&&y<52;float s=.5f+n*.2f+grain*.4f;r=100*s;g=102*s;b=106*s;
                    if(lamp){float e=1-Math.max(Math.abs(x-32),Math.abs(y-32))/24f;r=230+e*25;g=232+e*23;b=210+e*30;}
                    if(x==12||x==52||y==12||y==52){r=g=b=60;}break;}
                default:{ // sky thumbnail
                    float t2=y/64f;r=40+t2*170;g=40+t2*90;b=90+t2*20;
                    if(y>40+(int)(Math.sin(x*.2f)*5+Math.sin(x*.07f)*6)){r=40;g=30;b=40;}break;}
            }
            px[y*SIZE+x]=rgb(r,g,b);
        }
        return px;
    }
    private static final float TW=(float)(2*Math.PI/64);
    private static float sn(float v){return (float)Math.sin(v);}
    /** Shade helper: base colour scaled by s. */
    private static int c(int rgb,float s){return rgb(((rgb>>16)&255)*s,((rgb>>8)&255)*s,(rgb&255)*s);}
    private static int mix(int a,int b,float t){t=Math.max(0,Math.min(1,t));return rgb(((a>>16)&255)*(1-t)+((b>>16)&255)*t,((a>>8)&255)*(1-t)+((b>>8)&255)*t,(a&255)*(1-t)+(b&255)*t);}
    /** Running-bond bricks: returns {u,v,brickId,mortar(0/1)}. */
    private static int[] brick(int x,int y,int bw,int bh,int mortar){int row=y/bh,off=(row&1)*(bw/2);int bx=(x+off)%bw,by=y%bh;int id=row*64+((x+off)/bw)%(64/bw);return new int[]{bx,by,id,(bx<mortar||by<mortar)?1:0};}
    private static int extra(int t,int x,int y,float n,float g){
        float h=hash(x,y,t*7+3);
        switch(t){
            case 13:{ // hex plating
                float q=(x+((y/16)&1)*8)%16-8,r=y%16-8;boolean edge=Math.abs(q)>6.5f||Math.abs(r)>6.5f||Math.abs(q)+Math.abs(r)*.6f>9;
                return edge?c(0x2a3138,1+g):c(0x6f7f8c,.7f+n*.4f+g);}
            case 14:{ // floor grate
                boolean bar=x%8<3||y%32<2;float s=bar?.75f+n*.3f+g:.12f+n*.08f;return c(0x8a9096,s);}
            case 15:{ // circuit board
                int base2=0x1f4a2c;float s=.7f+n*.4f;int col=c(base2,s);
                boolean trace=(y%12==4&&x%32<26)||(x%16==9&&y%24>4)||(y%20==14&&x%40>10&&x%40<38);
                if(trace)col=c(0xc8a040,.8f+g);if(x%16==9&&y%12==4)col=0xffe8d080;if((x/8+y/8)%7==0&&x%8>1&&x%8<6&&y%8>1&&y%8<6)col=c(0x202020,1);return col;}
            case 16:{ // server rack with blinking lights
                int u=x%32,v=y%8;if(u<2||v==0)return c(0x15181c,1);
                float s=.35f+n*.15f;int col=c(0x2c3440,s+.3f);
                if(v>=3&&v<=4&&u>22&&u<29&&(u%2==0))col=hash(x/2,y/8,5)>.5f?0xff40ff80:0xffff8030;
                if(v>=2&&v<=5&&u>3&&u<18)col=c(0x101418,1);return col;}
            case 17:{ // bulkhead
                float s=.55f+n*.3f+g;int col=c(0x7a8288,s);
                if(y<6||y>57)col=c(0x464c52,.9f+g);if((x==4||x==59)&&y%8==2)col=c(0xb0b8c0,1);
                if(y>26&&y<38)col=(((x+y)/5)&1)==0?c(0xd8b030,.9f):c(0x222222,1);return col;}
            case 18:{ // neon panel
                float d=Math.abs(y%32-16);int col=c(0x1a1030,.8f+n*.3f);if(d<2)col=mix(0xffff40d0,0xffffffff,(2-d)/2);else if(d<6)col=mix(col,0xffb020a0,(6-d)/4*.8f);
                if(x%32==0)col=c(0x0a0a14,1);return col;}
            case 19:{ // pipe wall
                int u=y%16;float cyl=(float)Math.cos((u-8)/8f*Math.PI*.5f);int col=u<15?c(0x6a7078,.35f+cyl*.75f+g*.5f):c(0x202428,1);
                if(x%32<3&&u<15)col=c(0x8a6a40,.4f+cyl*.7f);return col;}
            case 20:{ // hazard floor plates
                boolean stripe=x<8||x>55;int col=stripe?((((x+y)/6)&1)==0?c(0xe0b020,.85f+g):c(0x1c1c1c,1)):c(0x60666c,.6f+n*.35f+g);
                if(!stripe&&((x/4+y/4)&1)==0)col=c(col,1.06f);return col;}
            case 21:{ // holo glass
                float s=.6f+n*.3f;int col=mix(0xff1a3a5a,0xff4fa8d8,s*.6f+sn((x+y)*TW*2)*.15f);if(x%32==0||y%32==0)col=c(0x8aa0b0,1);
                if((x+y)%32<2&&x%32>4)col=mix(col,0xffffffff,.5f);return col;}
            case 22:{ // reactor core
                float d=(float)Math.hypot((x%32)-16,(y%32)-16);float glow=Math.max(0,1-d/14);int col=mix(c(0x203040,.8f+n*.2f),0xff40e0ff,glow*glow+.15f*sn(d*1.5f));
                if(d>14&&d<15.5f)col=c(0x9aa8b0,1);return col;}
            case 23:{ // bloody tiles
                boolean grout=x%16==0||y%16==0;int col=grout?c(0x4a4a44,1+g):c(0xd8d8cc,.82f+n*.2f+g);
                float blood=fbm(x+11,y+5,231);if(blood>.58f)col=mix(col,0xff600808,Math.min(1,(blood-.58f)*6));if(y>40&&fbm(x,0,99)>.6f&&blood>.45f)col=mix(col,0xff6a0a0a,.8f);return col;}
            case 24:{ // flesh wall
                float v=Math.abs(sn(x*TW*3+n*8)*sn(y*TW*2+n*6));int col=mix(0xff8a3a3a,0xffd88a80,n*.8f+g);
                if(v<.12f)col=c(0x501818,.9f);if(h>.985f)col=0xffe8c0a0;return col;}
            case 25:{ // rotten planks
                int pl=x/16;float s=.45f+sn((y+hash(pl,0,7)*64)*.4f+n*5)*.08f+g;if(x%16==0)s=.15f;
                int col=c(0x6a5038,s+n*.25f);float rot=fbm(x,y,250);if(rot>.6f)col=mix(col,0xff2a3020,(rot-.6f)*4);return col;}
            case 26:{ // cracked plaster
                int col=c(0xb8ae98,.75f+n*.25f+g);float cr=Math.abs(sn(x*TW*2+fbm(x,y,260)*9)+sn(y*TW*3+n*7));if(cr<.05f)col=c(0x3a3428,1);
                float st=fbm(x,y,261);if(st>.62f)col=mix(col,0xff5a4a30,(st-.62f)*3);return col;}
            case 27:{ // morgue tiles
                boolean grout=x%8==0||y%8==0;int col=grout?c(0x5a6660,1):c(0x8fb0a4,.8f+n*.15f+g);if(fbm(x,y,270)>.64f)col=mix(col,0xff4a5040,.5f);return col;}
            case 28:{ // bone wall
                float u=(x+((y/12)&1)*8)%16-8,v=y%12-6;float d=u*u/49+v*v/25;int col=d<1?c(0xe0d4b4,.75f+(1-d)*.35f+g):c(0x2a2018,1+g);
                if(d<1&&Math.abs(u)<3&&v>-2&&v<1&&(Math.abs(u)>1))col=c(0x201810,1);return col;}
            case 29:{ // grimy concrete
                int col=c(0x8a8a84,.55f+n*.4f+g);float drip=fbm(x,0,290)*fbm(x,y,291);if(drip>.32f)col=mix(col,0xff3a3a30,(drip-.32f)*4);if(y%32==0)col=c(col,.6f);return col;}
            case 30:{ // old wallpaper
                float pat=sn(x*TW*4)*sn(y*TW*4);int col=pat>.5f?c(0x8a7a50,.8f+g):c(0x5a4a3a,.8f+n*.2f+g);
                if(x%16==0)col=c(col,.85f);float peel=fbm(x,y,300);if(peel>.66f)col=c(0x9a9080,.7f+g);if(y>56)col=c(0x3a2a20,1+g);return col;}
            case 31:{ // castle wall
                int[] b=brick(x,y,21,13,1);if(b[3]==1)return c(0x3a3630,1+g);float sh=hash(b[2],0,31);return c(0x8a8478,.55f+sh*.25f+n*.3f+g);}
            case 32:{ // cobblestone: rounded stones in offset rows
                int row=y/11,off=(row&1)*8,bw=16,bh=11;int bx=(x+off)%bw,by=y%bh;
                float dx=Math.max(0,Math.max(3.5f-bx,bx-(bw-4.5f))),dy=Math.max(0,Math.max(3.5f-by,by-(bh-4.5f)));
                float d=(float)Math.hypot(dx,dy);if(d>2.6f||bx==0||by==0)return c(0x2a2622,1+g);
                float sh=hash(row,(x+off)/bw,32);return c(0x86806f,.5f+sh*.25f+n*.25f+g-(d>1.5f?.15f:0)+(by<4?.08f:0));}
            case 33:{ // timber frame
                boolean beam=x<6||(y>28&&y<34)||Math.abs((x-6)-(y*58/64f))<3;int col=beam?c(0x4a3020,.7f+n*.3f+g):c(0xe0d4b8,.78f+n*.2f+g);return col;}
            case 34:{ // flagstones
                int[] b=brick(x,y,32,21,1);if(b[3]==1)return c(0x2e2c28,1);float sh=hash(b[2],5,34);return c(0x7c7466,.55f+sh*.3f+n*.25f+g);}
            case 35:{ // studded wooden door
                int col=c(0x6a4426,.55f+sn(x*.9f+n*4)*.06f+n*.25f+g);if(x%16==0)col=c(0x3a2414,1);
                if(y%32<4||y%32>28)col=c(0x3c3c40,.8f+g);if((y%32==2||y%32==30)&&x%8==4)col=c(0xb8b8c0,1);return col;}
            case 36:{ // tapestry
                int col=c(0x8a1a20,.7f+n*.2f+g);float d=Math.abs((x%32)-16)+Math.abs((y%32)-16);if(d<10&&d>7)col=c(0xd8a830,.9f+g);if(d<3)col=c(0xd8a830,1);
                if(y%32<2)col=c(0xd8a830,.8f);return col;}
            case 37:{ // thatch
                float str=sn(x*1.7f+n*10+y*.2f);int col=c(0xb89848,.5f+str*.15f+n*.35f+g);if(y%10==0)col=c(col,.7f);return col;}
            case 38:{ // rough planks
                int pl=y/10;float s=.55f+sn((x+hash(pl,3,38)*64)*.3f)*.07f+n*.25f+g;if(y%10==0)s=.2f;if(x==(int)(hash(pl,4,38)*63))s=.25f;return c(0x8a6a44,s);}
            case 39:{ // elven marble
                float v=Math.abs(sn((x*.6f+y)*TW*2+fbm(x,y,390)*6));int col=c(0xeae6dc,.85f+n*.15f);if(v<.06f)col=c(0xd8b050,1);else if(v<.2f)col=mix(col,0xffb8b0a0,.4f);
                if(x%32==0||y%32==0)col=c(0xc8b070,.9f);return col;}
            case 40:{ // crystal
                float a=(x+y)%16,b2=(x-y+64)%16;float facet=Math.min(Math.min(a,16-a),Math.min(b2,16-b2));int col=mix(0xff2050a0,0xff90f0ff,facet/8+n*.2f);if(facet<.8f)col=0xffe0ffff;return col;}
            case 41:{ // mossy stone
                int[] b=brick(x,y,16,16,1);int col=b[3]==1?c(0x2a2a24,1):c(0x6a6a62,.6f+n*.3f+g);float m=fbm(x,y,410);if(m>.52f)col=mix(col,0xff3a6a2a,(m-.52f)*4);return col;}
            case 42:{ // glowing runes
                int col=c(0x2a2a34,.6f+n*.3f+g);int u=x%16,v=y%16,id=(x/16+y/16*4);
                boolean rune=(u>3&&u<12&&v>3&&v<12)&&((id%3==0&&(u==7||v==7))||(id%3==1&&(u==v||u+v==15))||(id%3==2&&(u==4||u==11||v==4)));
                if(rune)col=0xff60e0ff;else if(u>2&&u<13&&v>2&&v<13&&hash(u,v,id)>.97f)col=0xff3080a0;return col;}
            case 43:{ // gold mosaic
                boolean grout=x%4==0||y%4==0;float s=hash(x/4,y/4,43);int col=grout?c(0x3a2a10,1):mix(0xffb08820,0xfff0d070,s);
                if(!grout&&s<.12f)col=c(0x1a6080,.9f);return col;}
            case 44:{ // living wood
                float ring=sn((float)Math.hypot(x-32,y*2.2f-70)*.5f+n*6);int col=c(0x6a4a2a,.6f+ring*.15f+n*.3f+g);if(fbm(x,y,440)>.6f)col=mix(col,0xff4a8a3a,.6f);return col;}
            case 45:{ // arcane swirl
                float dx=(x%32)-16,dy=(y%32)-16,ang=(float)Math.atan2(dy,dx),d=(float)Math.hypot(dx,dy);
                float sw=sn(ang*3+d*.5f);int col=mix(0xff1a0a30,0xffb060ff,Math.max(0,sw)*Math.max(0,1-d/18)+n*.15f);return col;}
            case 46:{ // hellrock
                float v=fbm(x,y,460);int col=c(0x5a2a1e,.5f+v*.6f+g);float crack=Math.abs(sn(x*TW*2+v*10)+sn(y*TW*3+n*8));if(crack<.08f)col=0xffff6010;return col;}
            case 47:{ // demon brick
                int[] b=brick(x,y,16,8,1);int col=b[3]==1?c(0x1a0a08,1):c(0x6a1a14,.55f+hash(b[2],1,47)*.3f+n*.25f+g);
                if(b[3]==1&&fbm(x,y,470)>.55f)col=0xffd04010;return col;}
            case 48:{ // blood pool
                float v=fbm(x,y,480);return v>.6f?c(0xd02020,1):c(0x8a0a0a,.7f+v*.5f);}
            case 49:{ // ash floor
                float v=fbm(x,y,490);int col=c(0x4a4440,.5f+v*.5f+g);if(h>.993f)col=0xffff7020;return col;}
            case 50:{ // skull wall
                int u=x%16,v=y%16;int col=c(0x3a2a24,.6f+n*.3f+g);float d=((u-8)*(u-8))/36f+((v-7)*(v-7))/30f;
                if(d<1)col=c(0xd0c4a4,.75f+(1-d)*.3f);if(d<1&&v>5&&v<9&&(u==5||u==6||u==10||u==11))col=c(0x1a1008,1);if(d<1&&v>10&&v<13&&u%2==0)col=c(0x2a1a10,1);return col;}
            case 51:{ // corrugated metal
                float wave=sn(x*TW*8);return c(0x8a9094,.55f+wave*.2f+n*.2f+g);}
            case 52:{ // diamond plate
                int u=(x+((y/8)&1)*4)%8,v=y%8;boolean bump=Math.abs(u-4)+Math.abs(v-4)*2<4&&Math.abs(u-v)<2;int col=c(0x9aa0a6,.6f+n*.2f+g);if(bump)col=c(0xd0d8de,.95f);return col;}
            case 53:{ // concrete blocks
                int[] b=brick(x,y,32,16,1);if(b[3]==1)return c(0x5a5a56,1);return c(0x9c9a92,.6f+n*.3f+g+hash(b[2],2,53)*.08f);}
            case 54:return (((x+y)/8)&1)==0?c(0xe8b818,.85f+n*.15f):c(0x1c1c1c,1+g); // warning stripes
            case 55:{ // rusty steel
                int col=c(0x707880,.55f+n*.3f+g);float rust=fbm(x,y,550);if(rust>.48f)col=mix(col,0xff8a4420,(rust-.48f)*3);if(x%32==0||y%32==0)col=c(col,.6f);
                if((x%32==3||x%32==28)&&(y%32==3||y%32==28))col=c(0xc0c4c8,1);return col;}
            case 56:{ // grass
                float blade=hash(x,y/2,56);return mix(0xff2e5a1e,0xff6aa83a,n*.7f+blade*.4f);}
            case 57:{ // dirt path
                int col=c(0x7a5a38,.55f+n*.45f+g);if(h>.96f)col=c(0x9a9088,.8f);return col;}
            case 58:{ // sand
                float rip=sn((x*.4f+y)*TW*6+n*4)*.08f;return c(0xd8c088,.75f+rip+n*.15f+g*.5f);}
            case 59:{ // water
                float wv=sn(x*TW*2+n*6)*sn(y*TW*3+n*5);return mix(0xff12366a,0xff3a82c0,.4f+wv*.4f+n*.2f);}
            case 60:{ // ice
                float cr=Math.abs(sn(x*TW*2+fbm(x,y,600)*7)*sn(y*TW*2+n*6));int col=mix(0xff8ab8d8,0xffe0f4ff,n);if(cr<.05f)col=0xfff8ffff;return col;}
            case 61:{ // sandstone blocks
                int[] b=brick(x,y,32,16,1);if(b[3]==1)return c(0x8a6a40,1);float strata=sn(y*TW*6+n*3)*.05f;return c(0xd8b078,.65f+strata+n*.25f+g);}
            case 62:{ // hieroglyphs
                int col=c(0xd0a868,.65f+n*.25f+g);int u=x%16,v=y%16,id=hash(x/16,y/16,62)>.5f?1:0;
                boolean glyph=(u>3&&u<12&&v>2&&v<13)&&((id==0&&(u==5||v==12||(u>7&&v==6)))||(id==1&&(Math.abs(u-8)+Math.abs(v-7)==4||u==8)));
                if(glyph)col=c(0x5a3a20,1);if(y%16==0)col=c(0x8a6a40,1);return col;}
            case 63:{ // gold trim
                int col=c(0x2a5a7a,.6f+n*.25f+g);if(y<10||y>53){col=c(0xd8a830,.7f+sn(x*TW*8)*.2f+g);}if(y==10||y==53)col=c(0x6a4a10,1);return col;}
            case 64:{ // office carpet
                int u=x%8,v=y%8;boolean dot=(u==3||u==4)&&(v==3||v==4);int col=c(0x3a4a5e,.6f+n*.2f+h*.18f);if(dot)col=c(0x6a7a90,.8f+h*.1f);return col;}
            case 65:{ // ceiling tiles
                boolean frame=x%32<2||y%32<2;int col=frame?c(0x9a9a9a,.9f):c(0xe0ddd4,.82f+g*.5f);if(!frame&&hash(x,y,65)>.9f)col=c(col,.85f);return col;}
            case 66:{ // bathroom tiles
                boolean grout=x%8==0||y%8==0;return grout?c(0xc8c8c0,1):((((x/8)+(y/8))&1)==0?c(0xf0f0ec,.9f+g):c(0x2a6a8a,.85f+g));}
            case 67:{ // asphalt
                int col=c(0x3a3a3c,.6f+n*.4f+g);if(h>.97f)col=c(0x7a7a78,1);if(x>29&&x<34&&(y%32)<18)col=c(0xe8d040,.9f);return col;}
            default:{ // hardwood floor
                int pl=y/8;float s=.62f+sn((x+hash(pl,6,68)*64)*.25f+n*3)*.06f+g;if(y%8==0||(x+(int)(hash(pl,7,68)*32))%32==0)s=.3f;return c(0x9a6838,s+n*.15f);}
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
