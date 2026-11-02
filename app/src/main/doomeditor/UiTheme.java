package com.retro.doomeditor;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import org.json.JSONObject;
import java.util.Random;

/**
 * The game's UI style: one look shared by the status bar, buttons, bars, menus and dialogue boxes.
 * Everything is drawn in code (procedural material textures, borders, corners), so it stays sharp at
 * any size and any setting can be changed. Comes with built-in fantasy themes and a generator.
 */
final class UiTheme {
    static final String[] MATERIALS={"Dark","Wood","Stone","Iron","Leather","Parchment","Crystal","Bone"};
    static final int M_DARK=0,M_WOOD=1,M_STONE=2,M_IRON=3,M_LEATHER=4,M_PARCHMENT=5,M_CRYSTAL=6,M_BONE=7;
    static final String[] BORDERS={"None","Thin","Double","Rivets","Gold trim","Runes","Vines"};
    static final int B_NONE=0,B_THIN=1,B_DOUBLE=2,B_RIVETS=3,B_TRIM=4,B_RUNES=5,B_VINES=6;
    static final String[] CORNERS={"Square","Rounded","Bevelled"};
    static final String[] BUTTON_SHAPES={"Circle","Rounded square","Diamond","Hexagon"};
    static final String[] BUTTON_FILLS={"Coloured","Material"};
    static final String[] BAR_STYLES={"Rounded","Square","Gem"};
    static final String[] PRESETS={"Classic Dark","Parchment & Ink","Stone & Iron","Elven Wood","Arcane Crystal","Royal Gold","Necromancer Bone","Dwarven Forge"};

    String name="Classic Dark";
    int material=M_DARK,panel=0xff141619,accent=0xffe8c070,text=0xffe8ecf1;
    int border=B_THIN,corners=0,buttonShape=0,buttonFill=0,barStyle=0;
    float texture=.6f;

    // palettes for the colour pickers
    static final String[] PANEL_NAMES={"Charcoal","Midnight","Oak","Walnut","Slate","Granite","Iron","Leather","Parchment","Bone","Amethyst","Emerald","Crimson","Navy","Ash"};
    static final int[] PANEL_COLS={0xff141619,0xff141a2e,0xff5a3a22,0xff3a2516,0xff3c434c,0xff55565a,0xff2a2c30,0xff5a3420,0xffd8c49a,0xffd2c9b2,0xff2a1e4a,0xff1e4a34,0xff4a1418,0xff1a2a4a,0xff6a6a6a};
    static final String[] ACCENT_NAMES={"Gold","Brass","Silver","Copper","Ruby","Sapphire","Emerald","Amethyst","Bone white","Ember","Ice","Leaf","Ink"};
    static final int[] ACCENT_COLS={0xffe8c070,0xffc89a4a,0xffc8ccd4,0xffc87a4a,0xffe0485a,0xff5a8aff,0xff4ac88a,0xffb07aff,0xffeee6d0,0xffff8a3a,0xff9ad8ff,0xff8ad06a,0xff3a2a1a};
    static final String[] TEXT_NAMES={"Ivory","White","Ink (for light panels)","Gold","Pale blue","Pale green"};
    static final int[] TEXT_COLS={0xfff0e8d8,0xffffffff,0xff2a2018,0xffffe0a0,0xffd8e4ff,0xffd8f0d8};

    UiTheme copy(){UiTheme t=new UiTheme();try{t.read(toJson());}catch(Exception ignored){}return t;}
    static UiTheme preset(int i){
        UiTheme t=new UiTheme();t.name=PRESETS[i];
        switch(i){
            case 1:t.set(M_PARCHMENT,0xffd8c49a,0xff6a4a24,0xff2a2018,B_DOUBLE,1,0,0,1,.7f);break;
            case 2:t.set(M_STONE,0xff4a4c50,0xffb8bcc4,0xfff0f0f0,B_RIVETS,2,1,1,1,.8f);break;
            case 3:t.set(M_WOOD,0xff4a3220,0xff8ad06a,0xfff0e8d0,B_VINES,1,0,0,0,.8f);break;
            case 4:t.set(M_CRYSTAL,0xff2a1e4a,0xff9ad8ff,0xffe8f0ff,B_RUNES,2,2,1,2,.8f);break;
            case 5:t.set(M_LEATHER,0xff4a1418,0xffe8c070,0xfffff0d0,B_TRIM,1,0,0,2,.6f);break;
            case 6:t.set(M_BONE,0xffd2c9b2,0xff3a5a3a,0xff1e1a14,B_THIN,2,3,1,1,.7f);break;
            case 7:t.set(M_IRON,0xff2a2c30,0xffff8a3a,0xffffe8d0,B_RIVETS,0,1,1,1,.8f);break;
            default:t.set(M_DARK,0xff141619,0xffe8c070,0xffe8ecf1,B_THIN,0,0,0,0,.6f);
        }
        return t;
    }
    private void set(int m,int pn,int ac,int tx,int b,int co,int bs,int bf,int bar,float tex){material=m;panel=pn;accent=ac;text=tx;border=b;corners=co;buttonShape=bs;buttonFill=bf;barStyle=bar;texture=tex;cache=null;}
    void changed(){cache=null;}

    // ------------------------------------------------------------------ generator
    static UiTheme random(Random r){
        UiTheme t=new UiTheme();t.name="Generated";
        t.material=r.nextInt(MATERIALS.length);
        float hue=r.nextFloat()*360;
        switch(t.material){
            case M_WOOD:t.panel=hsv(20+r.nextFloat()*20,.5f+r.nextFloat()*.2f,.22f+r.nextFloat()*.15f);break;
            case M_STONE:t.panel=hsv(hue,.04f+r.nextFloat()*.08f,.25f+r.nextFloat()*.15f);break;
            case M_IRON:t.panel=hsv(hue,.05f,.14f+r.nextFloat()*.08f);break;
            case M_PARCHMENT:t.panel=hsv(36+r.nextFloat()*10,.3f+r.nextFloat()*.15f,.78f+r.nextFloat()*.1f);break;
            case M_BONE:t.panel=hsv(40+r.nextFloat()*10,.1f+r.nextFloat()*.08f,.76f+r.nextFloat()*.1f);break;
            case M_CRYSTAL:t.panel=hsv(hue,.45f+r.nextFloat()*.3f,.2f+r.nextFloat()*.12f);break;
            default:t.panel=hsv(hue,.2f+r.nextFloat()*.5f,.1f+r.nextFloat()*.15f);
        }
        t.accent=r.nextFloat()<.45f?ACCENT_COLS[r.nextInt(4)]:hsv((hue+120+r.nextFloat()*120)%360,.45f+r.nextFloat()*.4f,.75f+r.nextFloat()*.25f);
        t.fixText();
        t.border=1+r.nextInt(BORDERS.length-1);t.corners=r.nextInt(3);t.buttonShape=r.nextInt(4);t.buttonFill=r.nextInt(2);t.barStyle=r.nextInt(3);t.texture=.4f+r.nextFloat()*.5f;
        return t;
    }
    /** Changes one or two things, keeping the rest. */
    void mutate(Random r){
        int n=1+r.nextInt(2);
        for(int i=0;i<n;i++)switch(r.nextInt(7)){
            case 0:{float[] h=toHsv(panel);panel=hsv((h[0]+r.nextFloat()*60-30+360)%360,h[1],Math.max(.06f,Math.min(.92f,h[2]+(r.nextFloat()-.5f)*.12f)));fixText();break;}
            case 1:{float[] h=toHsv(accent);accent=hsv((h[0]+r.nextFloat()*80-40+360)%360,h[1],h[2]);break;}
            case 2:border=1+r.nextInt(BORDERS.length-1);break;
            case 3:corners=r.nextInt(3);break;
            case 4:buttonShape=r.nextInt(4);break;
            case 5:barStyle=r.nextInt(3);break;
            default:texture=Math.max(0,Math.min(1,texture+(r.nextFloat()-.5f)*.4f));
        }
        name="Custom";changed();
    }
    /** Dark text on light panels, light text on dark ones. */
    void fixText(){text=luma(panel)>.5f?0xff2a2018:0xfff0e8d8;}
    static float luma(int c){return (((c>>16)&255)*.299f+((c>>8)&255)*.587f+(c&255)*.114f)/255f;}
    boolean lightPanel(){return luma(panel)>.5f;}
    static int hsv(float h,float s,float v){return android.graphics.Color.HSVToColor(new float[]{h,Math.max(0,Math.min(1,s)),Math.max(0,Math.min(1,v))});}
    static float[] toHsv(int c){float[] h=new float[3];android.graphics.Color.colorToHSV(c,h);return h;}
    static int shade(int c,float k){int r=(int)Math.min(255,((c>>16)&255)*k),g=(int)Math.min(255,((c>>8)&255)*k),b=(int)Math.min(255,(c&255)*k);return (c&0xff000000)|(r<<16)|(g<<8)|b;}
    static int alpha(int c,float a){return GameHud.alpha(c,a);}
    int textDim(){return (text&0xffffff)|0xb0000000;}
    int textFaint(){return (text&0xffffff)|0x80000000;}

    // ------------------------------------------------------------------ material texture
    private Bitmap cache;private BitmapShader shader;private float shaderDp;private final Matrix m=new Matrix();
    private static final int TS=96;
    private Shader materialShader(float dp){
        if(cache==null){cache=makeTexture();shader=null;}
        if(shader==null||shaderDp!=dp){shader=new BitmapShader(cache,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);m.setScale(dp*.75f,dp*.75f);shader.setLocalMatrix(m);shaderDp=dp;}
        return shader;
    }
    private static float noise(int x,int y,int seed){int n=x*374761393+y*668265263+seed*1442695041;n=(n^(n>>13))*1274126177;return ((n^(n>>16))&0xffff)/65535f;}
    private static float vnoise(float x,float y,int period,int seed){
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);float fx=x-x0,fy=y-y0;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        int a=Math.floorMod(x0,period),b=Math.floorMod(x0+1,period),c=Math.floorMod(y0,period),d=Math.floorMod(y0+1,period);
        float v00=noise(a,c,seed),v10=noise(b,c,seed),v01=noise(a,d,seed),v11=noise(b,d,seed);
        return (v00+(v10-v00)*fx)+((v01+(v11-v01)*fx)-(v00+(v10-v00)*fx))*fy;
    }
    private Bitmap makeTexture(){
        int[] px=new int[TS*TS];float k=texture;
        for(int y=0;y<TS;y++)for(int x=0;x<TS;x++){
            float v;   // brightness multiplier around 1
            float n1=vnoise(x/12f,y/12f,TS/12,3),n2=vnoise(x/4f,y/4f,TS/4,7),g=noise(x,y,11);
            switch(material){
                case M_WOOD:{float warp=vnoise(x/24f,y/6f,TS/24,5)*6;float band=(float)Math.sin((y+warp*2.2f)*.55f)*.5f+.5f;v=.82f+band*.26f+(g-.5f)*.08f;if(noise(x/3,y,9)>.985f)v*=.8f;break;}
                case M_STONE:{int row=y/24,bx=(x+(row%2)*24)%TS,col=bx/48;boolean mortar=y%24<2||bx%48<2;float blk=noise(col,row,13);
                    v=mortar?.55f:.84f+blk*.22f+(n2-.5f)*.18f+(g-.5f)*.1f;break;}
                case M_IRON:{float streak=vnoise(x/30f,y*1.5f,TS/30,17);v=.86f+streak*.2f+(g-.5f)*.06f;break;}
                case M_LEATHER:{float cell=n2*n2;v=.88f+n1*.14f-cell*.12f+(g-.5f)*.06f;break;}
                case M_PARCHMENT:{v=.9f+n1*.14f+(n2-.5f)*.06f+(g-.5f)*.03f;if(noise(x,y,23)>.996f)v*=.85f;break;}
                case M_CRYSTAL:{float f=((x+y)/16+(x-y+TS)/16)%3;v=.8f+f*.12f+n1*.12f;break;}
                case M_BONE:{v=.92f+n1*.1f+(g-.5f)*.05f;float crack=Math.abs(vnoise(x/10f,y/10f,TS/10,29)-.5f);if(crack<.015f)v*=.78f;break;}
                default:v=.95f+(n1-.5f)*.08f+(g-.5f)*.03f;
            }
            v=1+(v-1)*(k*1.4f);
            px[y*TS+x]=shade(panel|0xff000000,Math.max(.3f,v));
        }
        Bitmap b=Bitmap.createBitmap(TS,TS,Bitmap.Config.ARGB_8888);b.setPixels(px,0,TS,0,0,TS,TS);return b;
    }

    // ------------------------------------------------------------------ painting
    private final Path path=new Path();private final RectF tmp=new RectF();
    /** Outline path of a panel with the theme's corners. */
    private Path shapePath(float l,float t,float r,float b,float dp,boolean forceSquare){
        path.reset();float c=forceSquare||corners==0?0:corners==1?Math.min(dp*10,(b-t)*.5f):Math.min(dp*9,(b-t)*.35f);
        if(c<=0)path.addRect(l,t,r,b,Path.Direction.CW);
        else if(corners==1){tmp.set(l,t,r,b);path.addRoundRect(tmp,c,c,Path.Direction.CW);}
        else{path.moveTo(l+c,t);path.lineTo(r-c,t);path.lineTo(r,t+c);path.lineTo(r,b-c);path.lineTo(r-c,b);path.lineTo(l+c,b);path.lineTo(l,b-c);path.lineTo(l,t+c);path.close();}
        return path;
    }
    /** A themed panel (status bar, menu button, dialogue box). {@code edgeOnly} draws only the top edge decoration (full-width bars). */
    void panel(Canvas c,Paint p,float l,float t,float r,float b,float dp,float a,boolean fullWidth){
        Path sp=shapePath(l,t,r,b,dp,fullWidth);
        p.setStyle(Paint.Style.FILL);p.setShader(materialShader(dp));p.setColor(0xffffffff);p.setAlpha(Math.round(255*a*(material==M_DARK?.94f:1)));
        c.drawPath(sp,p);p.setShader(null);p.setAlpha(255);
        // soft inner light and shade
        p.setColor(alpha(0x22ffffff,a));c.drawRect(l,t,r,t+Math.min(dp*2,(b-t)*.1f),p);
        if(material==M_PARCHMENT||material==M_BONE){p.setShader(new RadialGradient((l+r)*.5f,(t+b)*.5f,Math.max(r-l,b-t)*.7f,0x00000000,alpha(0x40402810,a),Shader.TileMode.CLAMP));c.drawPath(sp,p);p.setShader(null);}
        drawBorder(c,p,l,t,r,b,dp,a,fullWidth);
    }
    private void drawBorder(Canvas c,Paint p,float l,float t,float r,float b,float dp,float a,boolean fullWidth){
        if(border==B_NONE)return;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        float sw=border==B_TRIM?dp*2.5f:dp*1.2f,h=sw*.5f;
        if(fullWidth){   // full-width bar: decorate the top edge only
            p.setStrokeWidth(sw);p.setColor(alpha(accent,a*(border==B_THIN?.6f:1)));c.drawLine(l,t+h,r,t+h,p);
            if(border==B_DOUBLE||border==B_TRIM){p.setStrokeWidth(dp);p.setColor(alpha(border==B_TRIM?0xfffff0c0:accent,a*.6f));c.drawLine(l,t+sw+dp*1.5f,r,t+sw+dp*1.5f,p);}
            if(border==B_RIVETS){p.setStyle(Paint.Style.FILL);for(float x=l+dp*14;x<r;x+=dp*48)rivet(c,p,x,t+dp*6,dp,a);}
            if(border==B_RUNES)runes(c,p,l,r,t+dp*5,dp,a);
            if(border==B_VINES){vine(c,p,l+dp*4,t+dp*3,1,dp,a);vine(c,p,r-dp*4,t+dp*3,-1,dp,a);}
            p.setStyle(Paint.Style.FILL);return;
        }
        Path sp=shapePath(l+h,t+h,r-h,b-h,dp,false);
        p.setStrokeWidth(sw);p.setColor(alpha(accent,a*(border==B_THIN?.75f:1)));c.drawPath(sp,p);
        if(border==B_DOUBLE||border==B_TRIM){float in=sw+dp*2;if(b-t>in*3){p.setStrokeWidth(dp*.9f);p.setColor(alpha(border==B_TRIM?0xfffff0c0:accent,a*.55f));c.drawPath(shapePath(l+in,t+in,r-in,b-in,dp,false),p);}}
        p.setStyle(Paint.Style.FILL);
        if(border==B_RIVETS){float o=dp*6;rivet(c,p,l+o,t+o,dp,a);rivet(c,p,r-o,t+o,dp,a);rivet(c,p,l+o,b-o,dp,a);rivet(c,p,r-o,b-o,dp,a);}
        if(border==B_RUNES&&r-l>dp*80)runes(c,p,l+dp*16,r-dp*16,t+dp*4,dp,a);
        if(border==B_VINES&&b-t>dp*24){vine(c,p,l+dp*3,t+dp*3,1,dp,a);vine(c,p,r-dp*3,t+dp*3,-1,dp,a);}
    }
    private void rivet(Canvas c,Paint p,float x,float y,float dp,float a){p.setStyle(Paint.Style.FILL);p.setColor(alpha(shade(accent,.55f),a));c.drawCircle(x,y+dp*.6f,dp*2.4f,p);p.setColor(alpha(accent,a));c.drawCircle(x,y,dp*2.2f,p);p.setColor(alpha(0xccffffff,a));c.drawCircle(x-dp*.7f,y-dp*.7f,dp*.7f,p);}
    private void runes(Canvas c,Paint p,float l,float r,float y,float dp,float a){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp);p.setColor(alpha(accent,a*.7f));int i=0;
        for(float x=l;x<r-dp*6;x+=dp*22,i++){int k=i%4;float s=dp*3;
            switch(k){case 0:c.drawLine(x,y-s,x,y+s,p);c.drawLine(x,y-s,x+s,y,p);break;case 1:c.drawLine(x-s,y+s,x,y-s,p);c.drawLine(x,y-s,x+s,y+s,p);break;
                case 2:c.drawLine(x,y-s,x,y+s,p);c.drawLine(x-s,y-s*.3f,x+s,y+s*.3f,p);break;default:c.drawLine(x-s,y,x+s,y,p);c.drawLine(x,y-s,x,y+s,p);}}
        p.setStyle(Paint.Style.FILL);
    }
    private void vine(Canvas c,Paint p,float x,float y,int dir,float dp,float a){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*1.3f);p.setColor(alpha(accent,a*.85f));
        path.reset();path.moveTo(x,y);path.cubicTo(x+dir*dp*10,y-dp*2,x+dir*dp*16,y+dp*8,x+dir*dp*28,y+dp*2);c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);
        for(int i=1;i<=3;i++){float lx=x+dir*dp*(7*i),ly=y+dp*(i%2==0?4:-1);tmp.set(lx-dp*3,ly-dp*1.6f,lx+dp*3,ly+dp*1.6f);c.drawOval(tmp,p);}
    }
    /** Body of a round control: shape, fill (its own colour or the material) and a ring in the accent. */
    void button(Canvas c,Paint p,float x,float y,float rad,int col,boolean down,float dp,float a){
        Path sp=buttonPath(x,y,rad);
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(0x55000000,a));c.save();c.translate(0,dp*3);c.drawPath(sp,p);c.restore();
        if(buttonFill==1){p.setShader(materialShader(dp));p.setColor(0xffffffff);p.setAlpha(Math.round(255*a));c.drawPath(sp,p);p.setShader(null);p.setAlpha(255);
            p.setColor(alpha(col,a*(down?.8f:.45f)));c.drawPath(sp,p);}
        else{p.setColor(alpha(down?Ui.brighten(col):col,a));c.drawPath(sp,p);}
        p.setShader(new RadialGradient(x-rad*.3f,y-rad*.4f,rad*1.2f,alpha(0x40ffffff,a),0x00ffffff,Shader.TileMode.CLAMP));c.drawPath(sp,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(border==B_TRIM?dp*2.5f:border==B_NONE?dp:dp*1.6f);p.setColor(alpha(border==B_NONE?0x40ffffff:accent,a*(border==B_THIN?.7f:1)));c.drawPath(buttonPath(x,y,rad-dp*.8f),p);
        p.setStyle(Paint.Style.FILL);
    }
    private Path buttonPath(float x,float y,float rad){
        path.reset();
        switch(buttonShape){
            case 1:{float s=rad*.9f;tmp.set(x-s,y-s,x+s,y+s);path.addRoundRect(tmp,rad*.35f,rad*.35f,Path.Direction.CW);break;}
            case 2:path.moveTo(x,y-rad*1.1f);path.lineTo(x+rad*1.1f,y);path.lineTo(x,y+rad*1.1f);path.lineTo(x-rad*1.1f,y);path.close();break;
            case 3:for(int i=0;i<6;i++){double an=Math.PI/6+i*Math.PI/3;float px=x+(float)Math.cos(an)*rad,py=y+(float)Math.sin(an)*rad;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();break;
            default:path.addCircle(x,y,rad,Path.Direction.CW);
        }
        return path;
    }
    /** HP / SP bar in the theme's bar style. */
    void bar(Canvas c,Paint p,Ui ui,float l,float t,float r,float b,float frac,int col,String label,float dp,float a){
        float rad=barStyle==0?(b-t)*.5f:barStyle==2?dp*2:dp;
        float gem=barStyle==2?(b-t)*.9f:0,bl=l+gem*.6f;
        ui.round(c,bl,t,r,b,rad,alpha(0xff0e1013,a));float f=Math.max(0,Math.min(1,frac));
        if(f>0)ui.round(c,bl,t,bl+Math.max(rad*2,(r-bl)*f),b,rad,alpha(col,a));
        ui.round(c,bl,t,bl+Math.max(rad*2,(r-bl)*f),t+(b-t)*.35f,rad,alpha(0x30ffffff,a*(f>0?1:0)));
        ui.outline(c,bl,t,r,b,rad,alpha(border==B_NONE?0x30ffffff:accent,a*.7f),dp);
        if(gem>0){float cx=l+gem*.5f,cy=(t+b)*.5f,s=gem*.6f;path.reset();path.moveTo(cx,cy-s);path.lineTo(cx+s,cy);path.lineTo(cx,cy+s);path.lineTo(cx-s,cy);path.close();
            p.setStyle(Paint.Style.FILL);p.setColor(alpha(col,a));c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp);p.setColor(alpha(accent,a));c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
        if(label!=null)ui.textMid(c,label,(bl+r)*.5f,(t+b)*.5f,10,alpha(0xffffffff,a),2,0);
    }

    // ------------------------------------------------------------------ screens, cards and buttons
    static final int BTN_NORMAL=0,BTN_ACTIVE=1,BTN_PRIMARY=2,BTN_GHOST=3;
    /** Accent that reads on this theme's panels (dark accents are lifted on dark panels, light ones darkened on light panels). */
    int accentOnPanel(){return lightPanel()?(luma(accent)>.55f?shade(accent,.5f):accent):(luma(accent)<.35f?0xffe8c070:accent);}
    /** Full-screen background (character sheet, results): the material, dimmed so cards stand out. */
    void backdrop(Canvas c,Paint p,float w,float h,float dp){
        panel(c,p,0,0,w,h,dp,1,true);
        p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(lightPanel()?0x26000000:0x70000000);c.drawRect(0,0,w,h,p);
    }
    /** A row or tile inside a panel; {@code selected} adds an accent frame. */
    void card(Canvas c,Paint p,Ui ui,RectF r,boolean selected,float dp){
        int base=lightPanel()?shade(panel,.9f):shade(panel,1.45f)|0xff000000;
        float rad=corners==0?dp*3:corners==1?dp*9:dp*5;
        ui.round(c,r,rad,alpha(base,.92f));
        if(selected){ui.round(c,r,rad,alpha(accent,.16f));ui.outline(c,r,rad,accentOnPanel(),dp*1.6f);}
        else ui.outline(c,r,rad,alpha(lightPanel()?0xff000000:0xffffffff,.08f),dp);
    }
    /** A rectangular themed button: normal, active (selected tab), primary (main action) or ghost (icon only). */
    void rectButton(Canvas c,Paint p,Ui ui,RectF r,int icon,String label,int style,float dp){
        int fg=text;
        if(style==BTN_PRIMARY){int a=accentOnPanel();float rad=corners==0?dp*3:corners==1?dp*9:dp*5;
            ui.round(c,r.left,r.top+dp*2,r.right,r.bottom+dp*2,rad,0x50000000);ui.round(c,r,rad,a);ui.round(c,r.left,r.top,r.right,r.top+r.height()*.45f,rad,0x22ffffff);
            ui.outline(c,r,rad,shade(a,.6f),dp);fg=luma(a)>.55f?0xff1a1206:0xffffffff;}
        else if(style==BTN_GHOST){fg=text;}
        else{card(c,p,ui,r,style==BTN_ACTIVE,dp);if(style==BTN_ACTIVE)fg=accentOnPanel();}
        float size=r.height()<dp*30?11.5f:12.5f;
        if(label==null||label.isEmpty()){if(icon>=0)ui.icon(c,icon,r.centerX(),r.centerY(),Math.min(18,r.height()/dp-14),fg);return;}
        if(icon<0){ui.textMid(c,ui.ellipsize(label,size,1,r.width()-dp*12),r.centerX(),r.centerY(),size,fg,1,0);return;}
        float iw=dp*16,gap=dp*6,tw=Math.min(ui.measure(label,size,1),r.width()-iw-gap-dp*16),x0=r.centerX()-(iw+gap+tw)*.5f;
        ui.icon(c,icon,x0+iw*.5f,r.centerY(),15,fg);ui.textMid(c,ui.ellipsize(label,size,1,tw+1),x0+iw+gap,r.centerY(),size,fg,1,-1);
    }
    /** Toggle switch and slider in theme colours. */
    void toggle(Canvas c,Paint p,Ui ui,float x,float y,boolean on,float dp){
        int a=accentOnPanel();ui.round(c,x,y,x+dp*40,y+dp*20,dp*10,on?shade(a,.7f):alpha(0xff000000,.3f));
        p.setStyle(Paint.Style.FILL);p.setColor(on?a:textFaint());c.drawCircle(on?x+dp*30:x+dp*10,y+dp*10,dp*8,p);
    }

    // ------------------------------------------------------------------ json
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("name",name);o.put("material",material);o.put("panel",panel);o.put("accent",accent);o.put("text",text);
        o.put("border",border);o.put("corners",corners);o.put("buttonShape",buttonShape);o.put("buttonFill",buttonFill);o.put("barStyle",barStyle);o.put("texture",(double)texture);
        return o;
    }
    void read(JSONObject o){
        name=o.optString("name","Custom");material=clamp(o.optInt("material",0),MATERIALS.length);panel=o.optInt("panel",panel)|0xff000000;accent=o.optInt("accent",accent)|0xff000000;text=o.optInt("text",text)|0xff000000;
        border=clamp(o.optInt("border",1),BORDERS.length);corners=clamp(o.optInt("corners",0),CORNERS.length);buttonShape=clamp(o.optInt("buttonShape",0),BUTTON_SHAPES.length);
        buttonFill=clamp(o.optInt("buttonFill",0),BUTTON_FILLS.length);barStyle=clamp(o.optInt("barStyle",0),BAR_STYLES.length);texture=(float)Math.max(0,Math.min(1,o.optDouble("texture",.6)));cache=null;
    }
    private static int clamp(int v,int n){return Math.max(0,Math.min(n-1,v));}
    static UiTheme fromJson(JSONObject o){UiTheme t=new UiTheme();if(o!=null)t.read(o);return t;}
}
