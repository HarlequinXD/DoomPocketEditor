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

    static final class Sprite {final int w,h;final int[] px;final boolean bright;Sprite(int w,int h,boolean bright){this.w=w;this.h=h;px=new int[w*h];this.bright=bright;}}
    private static Sprite[][] sprites;
    private static Sprite[][] effects;
    private static Sprite[] weapons,flashes;

    static synchronized void init(){
        if(sprites!=null)return;
        sprites=new Sprite[COUNT][FRAMES];
        for(int s=0;s<COUNT;s++)for(int f=0;f<FRAMES;f++)sprites[s][f]=make(s,f);
        effects=new Sprite[7][3];
        for(int e=0;e<7;e++)for(int f=0;f<3;f++)effects[e][f]=effect(e,f);
        weapons=new Sprite[5];flashes=new Sprite[5];
        for(int i=0;i<5;i++){weapons[i]=weapon(i);flashes[i]=flash(i);}
    }
    static Sprite get(int id,int frame){init();return sprites[Math.max(0,Math.min(COUNT-1,id))][Math.max(0,Math.min(FRAMES-1,frame))];}
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

    // ------------------------------------------------------------------ characters
    private static Sprite humanoid(int body,int skin,int legs,int eye,int frame,int style){
        int W=34,H=54;Sprite s=new Sprite(W,H,false);
        if(frame==F_DEAD){
            oval(s,17,50,15,3.5f,0xff6a0a0a);
            rect(s,5,44,28,49,legs);rect(s,9,42,24,47,body);oval(s,27,45,4,3.5f,skin);rect(s,3,45,8,48,legs);
            px(s,27,44,0xff300000);finish(s,7);return s;
        }
        int lo=frame==F_WALK1?-2:frame==F_WALK2?2:0;
        // legs
        rect(s,11+lo,34,15+lo,51,legs);rect(s,19-lo,34,23-lo,51,legs);
        rect(s,10+lo,50,15+lo,52,0xff2a2420);rect(s,19-lo,50,24-lo,52,0xff2a2420);
        // torso
        rect(s,10,18,24,35,body);rect(s,10,32,24,33,shade(body,.6f));
        if(style==3){rect(s,8,20,26,33,body);}
        // arms
        if(frame==F_ATTACK){
            rect(s,7,20,10,26,body);rect(s,24,20,27,26,body);rect(s,10,23,24,26,body);
            if(style==1||style==4){rect(s,13,22,21,24,0xff3a3a3a);}
            if(style==2){oval(s,17,20,4,4,0xffffb030);oval(s,17,20,2,2,0xffffff90);}
        }else{
            int sw=frame==F_WALK1?1:frame==F_WALK2?-1:0;
            rect(s,6,19+sw,9,32+sw,body);rect(s,25,19-sw,28,32-sw,body);
            rect(s,6,31+sw,9,33+sw,skin);rect(s,25,31-sw,28,33-sw,skin);
            if(style==1||style==4)rect(s,24,26,31,28,0xff3a3a3a);
        }
        // head
        oval(s,17,11,style==3?7:5.5f,style==3?7:6.5f,skin);
        if(style==3){rect(s,12,13,22,16,0xff3a0a0a);for(int x=12;x<=22;x+=2)px(s,x,13,0xfff0f0e0);}
        px(s,15,10,eye);px(s,19,10,eye);px(s,14,10,eye);px(s,20,10,eye);
        if(style==2){line(s,10,18,6,13,0xffe8e0c0);line(s,24,18,28,13,0xffe8e0c0);px(s,13,5,0xffe8e0c0);px(s,21,5,0xffe8e0c0);}
        if(style==0){rect(s,11,4,23,8,shade(body,.8f));rect(s,13,8,21,9,0xff2a3a2a);}
        if(style==4){rect(s,12,4,22,6,0xff302820);}
        finish(s,style*13+frame);
        if(frame==F_PAIN)for(int i=0;i<s.px.length;i++)if((s.px[i]>>>24)!=0){int c=s.px[i];s.px[i]=Textures.rgb(((c>>16)&255)*.6f+100,((c>>8)&255)*.5f+20,(c&255)*.5f+20);}
        return s;
    }
    private static Sprite make(int id,int f){
        switch(id){
            case PLAYER:return humanoid(0xff3f7f3a,0xffc8a07a,0xff2f5f2b,0xff202020,f,0);
            case TROOPER:return humanoid(0xff5d6b3a,0xffa8b090,0xff4a5230,0xffe03020,f,1);
            case SERGEANT:return humanoid(0xff4a4a4a,0xffa8b090,0xff3a3a3a,0xffe03020,f,4);
            case IMP:return humanoid(0xff7a4a2a,0xff8a5a34,0xff6a3e22,0xffff4010,f,2);
            case DEMON:return humanoid(0xffd47a8a,0xffd88a98,0xffb86070,0xff401010,f,3);
        }
        Sprite s;
        switch(id){
            case MEDKIT:s=new Sprite(22,16,false);rect(s,1,3,20,15,0xffe8e8e8);rect(s,7,1,14,3,0xff9a9a9a);rect(s,9,5,12,13,0xffd02020);rect(s,6,8,15,10,0xffd02020);break;
            case STIMPACK:s=new Sprite(12,14,false);rect(s,1,2,10,13,0xffe0e0e0);rect(s,5,4,6,11,0xffd02020);rect(s,3,7,8,8,0xffd02020);break;
            case CLIP:s=new Sprite(8,14,false);rect(s,1,3,6,13,0xff505458);rect(s,2,0,5,3,0xffc8a040);break;
            case SHELLS:s=new Sprite(16,10,false);for(int i=0;i<4;i++){rect(s,1+i*4,2,3+i*4,9,0xffc02818);rect(s,1+i*4,7,3+i*4,9,0xffd0b050);}break;
            case ROCKETBOX:s=new Sprite(20,14,false);rect(s,1,4,18,13,0xff4a5a30);for(int i=0;i<4;i++){rect(s,3+i*4,0,4+i*4,4,0xff909090);px(s,3+i*4,0,0xffc03020);}break;
            case ARMOR:s=new Sprite(22,20,false);rect(s,3,2,18,18,0xff30a040);rect(s,0,2,4,8,0xff30a040);rect(s,17,2,21,8,0xff30a040);rect(s,8,0,13,3,0xff000000);s.px[0]=0;rect(s,9,6,12,14,0xff208030);break;
            case SHOTGUN_PICKUP:s=new Sprite(34,10,false);rect(s,1,3,24,5,0xff606468);rect(s,18,5,32,8,0xff7a4a22);rect(s,10,5,16,7,0xff5a3a1a);break;
            case CHAINGUN_PICKUP:s=new Sprite(34,14,false);for(int i=0;i<3;i++)rect(s,1,2+i*3,22,3+i*3,0xff68707a);rect(s,18,3,32,11,0xff50545a);rect(s,24,10,28,13,0xff3a3a3a);break;
            case LAUNCHER_PICKUP:s=new Sprite(36,12,false);rect(s,1,2,30,9,0xff4a6a3a);oval(s,3,5.5f,3,4,0xff2a2a2a);rect(s,20,9,24,11,0xff3a3a3a);break;
            case KEY_BLUE:case KEY_YELLOW:case KEY_RED:{int c=id==KEY_BLUE?0xff3060e0:id==KEY_YELLOW?0xffe0c020:0xffd02020;s=new Sprite(10,14,true);rect(s,1,1,8,12,c);rect(s,3,3,6,5,0xffffffff);rect(s,2,9,7,10,shade(c,.6f));break;}
            case BARREL:s=new Sprite(20,30,false);rect(s,2,4,17,29,0xff4a6040);rect(s,1,8,18,9,0xff3a4a30);rect(s,1,22,18,23,0xff3a4a30);oval(s,10,4,8,3,0xff60e040);oval(s,10,4,5,1.6f,0xffa0ff70);break;
            case TORCH:{s=new Sprite(10,42,true);rect(s,4,12,5,41,0xff5a4a3a);rect(s,2,39,7,41,0xff3a3a3a);int fl=(f&1)==0?0:1;
                oval(s,4.5f,8-fl,3.5f,6+fl,0xffff8020);oval(s,4.5f,9,2,3.5f,0xffffe060);break;}
            case COLUMN:s=new Sprite(18,52,false);rect(s,3,4,14,47,0xff9aa0a0);rect(s,1,0,16,4,0xff808888);rect(s,1,47,16,51,0xff808888);for(int x=5;x<14;x+=3)rect(s,x,6,x,45,0xffb8c0c0);break;
            case SWITCH:case SWITCH_ON:s=new Sprite(18,30,false);rect(s,1,1,16,29,0xff5a5e66);rect(s,4,6,13,22,0xff30343a);
                if(id==SWITCH){rect(s,7,8,10,14,0xffc03020);rect(s,8,14,9,20,0xff909090);}else{rect(s,7,15,10,21,0xff30c040);rect(s,8,9,9,15,0xff909090);}break;
            case LAMP:s=new Sprite(12,44,true);rect(s,5,12,6,43,0xff606060);rect(s,2,41,9,43,0xff404040);oval(s,5.5f,6,5,6,0xffeef0ff);oval(s,5.5f,6,3,4,0xffffffff);break;
            default:s=new Sprite(8,8,false);rect(s,0,0,7,7,0xffff00ff);
        }
        finish(s,id);
        return s;
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
