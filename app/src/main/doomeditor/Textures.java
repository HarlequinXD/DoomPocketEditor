package com.retro.doomeditor;

/** Procedurally generated 64x64 textures plus pre-shaded light tables used by the renderer. */
final class Textures {
    private Textures(){}
    static final int SIZE=64,SHIFT=6,MASK=63,LIGHTS=32;
    static final String[] NAMES={"Grey Stone","Brown Brick","Metal Floor","Tan Brick","Tech Panel","Wood","Green Marble","Nukage","Lava","Rock","Door Metal","Ceiling Light","Sky"};
    static final int COUNT=NAMES.length,SKY=12;
    /** Textures that ignore distance fading (glowing liquids and lights). */
    static final boolean[] BRIGHT={false,false,false,false,false,false,false,true,true,false,false,true,true};
    static final int[][] base=new int[COUNT][];
    /** shaded[texture][light][texel]: colour already multiplied by the light level. */
    static int[][][] shaded;
    static final int SKY_W=512,SKY_H=128;
    static int[] sky;

    static synchronized void init(){
        if(shaded!=null)return;
        for(int t=0;t<COUNT;t++)base[t]=generate(t);
        sky=generateSky();
        shaded=new int[COUNT][LIGHTS][];
        for(int t=0;t<COUNT;t++)for(int l=0;l<LIGHTS;l++){
            int[] src=base[t],dst=new int[src.length];
            int m=lightMul(l);
            for(int i=0;i<src.length;i++)dst[i]=mul(src[i],m);
            shaded[t][l]=dst;
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
