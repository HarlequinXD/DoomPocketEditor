package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.ArrayList;

/** Shared dark editor theme, density-aware sizing and drawing primitives. */
final class Ui {
    // Palette
    static final int BG=0xff15181c, CANVAS=0xff1a1e23, SURFACE=0xff20252b, SURFACE_2=0xff282e36, SURFACE_3=0xff323a44,
            BORDER=0xff343b45, DIVIDER=0xff2a3038, TEXT=0xffe8ecf1, TEXT_2=0xffa6b0bd, TEXT_3=0xff6e7987,
            ACCENT=0xff3d8bfd, ACCENT_DARK=0xff2f6fd0, ACCENT_SOFT=0x333d8bfd, ORANGE=0xfff0a030, GREEN=0xff3ecf8e,
            RED=0xffef5b5b, AMBER=0xfff2b84b;
    static final int NORMAL=0, ACTIVE=1, PRIMARY=2, DANGER=3, GHOST=4, DISABLED=5, SUCCESS=6;

    final float dp;
    /** Where the app-wide cursor is (NaN when it is hidden), for hover highlights. */
    static float hoverX=Float.NaN,hoverY=Float.NaN;
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL),
            medium=Typeface.create("sans-serif-medium",Typeface.NORMAL),
            bold=Typeface.create("sans-serif",Typeface.BOLD);
    private final RectF tmp=new RectF();

    /** Touch target registered while drawing, so drawing and hit-testing always agree. */
    static final class Hit { final RectF r=new RectF(); int id; }
    final ArrayList<Hit> hits=new ArrayList<>();
    /** Hit objects are reused from frame to frame instead of allocating hundreds per frame. */
    private final ArrayList<Hit> hitPool=new ArrayList<>();
    private Hit nextHit(){int n=hits.size();Hit h;if(n<hitPool.size())h=hitPool.get(n);else{h=new Hit();hitPool.add(h);}hits.add(h);return h;}

    Ui(Context ctx){dp=ctx.getResources().getDisplayMetrics().density;}
    float dp(float v){return v*dp;}
    static int brighten(int c){int a=c>>>24;int r=Math.min(255,((c>>16)&255)*5/4+20),g=Math.min(255,((c>>8)&255)*5/4+20),b=Math.min(255,(c&255)*5/4+20);return (a<<24)|(r<<16)|(g<<8)|b;}

    void clearHits(){hits.clear();}
    void hit(RectF r,int id){Hit h=nextHit();h.r.set(r);h.id=id;}
    void hit(float l,float t,float r,float b,int id){Hit h=nextHit();h.r.set(l,t,r,b);h.id=id;}
    /** Last registered target under the point wins (drawn on top). */
    int hitAt(float x,float y){for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).r.contains(x,y))return hits.get(i).id;return -1;}

    void rect(Canvas c,float l,float t,float r,float b,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRect(l,t,r,b,p);}
    void round(Canvas c,float l,float t,float r,float b,float radius,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);tmp.set(l,t,r,b);c.drawRoundRect(tmp,radius,radius,p);}
    void round(Canvas c,RectF r,float radius,int color){round(c,r.left,r.top,r.right,r.bottom,radius,color);}
    void outline(Canvas c,float l,float t,float r,float b,float radius,int color,float width){
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);
        float h=width*.5f;tmp.set(l+h,t+h,r-h,b-h);c.drawRoundRect(tmp,radius,radius,p);p.setStyle(Paint.Style.FILL);
    }
    void outline(Canvas c,RectF r,float radius,int color,float width){outline(c,r.left,r.top,r.right,r.bottom,radius,color,width);}
    void hline(Canvas c,float l,float r,float y,int color){rect(c,l,y,r,y+Math.max(1,dp(.75f)),color);}
    void vline(Canvas c,float x,float t,float b,int color){rect(c,x,t,x+Math.max(1,dp(.75f)),b,color);}

    /** weight: 0 regular, 1 medium, 2 bold. align: -1 left, 0 centre, 1 right. y is the text baseline. */
    void text(Canvas c,String s,float x,float y,float sizeDp,int color,int weight,int align){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(dp(sizeDp));
        p.setTypeface(weight==2?bold:weight==1?medium:regular);
        p.setTextAlign(align<0?Paint.Align.LEFT:align==0?Paint.Align.CENTER:Paint.Align.RIGHT);
        c.drawText(s,x,y,p);p.setTextAlign(Paint.Align.LEFT);
    }
    void text(Canvas c,String s,float x,float y,float sizeDp,int color){text(c,s,x,y,sizeDp,color,0,-1);}
    /** Draws text vertically centred on cy. */
    void textMid(Canvas c,String s,float x,float cy,float sizeDp,int color,int weight,int align){
        p.setTextSize(dp(sizeDp));p.setTypeface(weight==2?bold:weight==1?medium:regular);
        float base=cy-(p.ascent()+p.descent())*.5f;text(c,s,x,base,sizeDp,color,weight,align);
    }
    float measure(String s,float sizeDp,int weight){p.setTextSize(dp(sizeDp));p.setTypeface(weight==2?bold:weight==1?medium:regular);return p.measureText(s);}
    String ellipsize(String s,float sizeDp,int weight,float max){
        if(measure(s,sizeDp,weight)<=max)return s;
        for(int n=s.length()-1;n>0;n--){String t=s.substring(0,n).trim()+"…";if(measure(t,sizeDp,weight)<=max)return t;}
        return "…";
    }
    // ---- app emblem: the golden d20 on a purple tile, matching the launcher icon
    private final java.util.HashMap<Integer,android.graphics.Bitmap> logoCache=new java.util.HashMap<>();
    /** Draws the app emblem in an s-pixel tile at (x,y). Rendered once per size, then blitted. */
    void logo(Canvas c,float x,float y,float s){
        int px=Math.max(8,Math.round(s));android.graphics.Bitmap b=logoCache.get(px);
        if(b==null){b=android.graphics.Bitmap.createBitmap(px,px,android.graphics.Bitmap.Config.ARGB_8888);renderLogo(new Canvas(b),px);logoCache.put(px,b);}
        c.drawBitmap(b,Math.round(x),Math.round(y),null);
    }
    private static final float LOGO_VIEW=13.5f,LOGO_SPAN=81f;
    private static float lgx(float u,float s){return (54+(u-54)*.74f-LOGO_VIEW)/LOGO_SPAN*s;}
    private static void renderLogo(Canvas c,float s){
        Paint q=new Paint(Paint.ANTI_ALIAS_FLAG);float k=s/LOGO_SPAN,mid=(54-LOGO_VIEW)*k;
        q.setShader(new android.graphics.RadialGradient(mid,s*.4f,s*.85f,0xff5a2a8a,0xff160828,android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0,0,s,s),s*.22f,s*.22f,q);
        q.setShader(new android.graphics.RadialGradient(mid,mid,36*k,0xd9ffd8ff,0x00b050ff,android.graphics.Shader.TileMode.CLAMP));c.drawCircle(mid,mid,36*k,q);q.setShader(null);
        float[][] f={{54,20,84,37,54,40},{54,20,24,37,54,40},{24,37,54,40,36,70},{84,37,54,40,72,70},{54,40,36,70,72,70},{24,37,36,70,24,71},{84,37,72,70,84,71},{36,70,72,70,54,88},{24,71,36,70,54,88},{84,71,72,70,54,88}};
        int[] col={0xfff6d478,0xffffe8a0,0xffe8b84a,0xffc88a28,0xffffe08a,0xffb07820,0xff8a5a14,0xffa86e1a,0xff8a5a14,0xff6e4610};
        android.graphics.Path pa=new android.graphics.Path();
        // soft drop shadow under the die
        q.setStyle(Paint.Style.FILL);q.setColor(0x66000000);pa.reset();pa.moveTo(lgx(54,s),lgx(21.5f,s));pa.lineTo(lgx(85,s),lgx(38.5f,s));pa.lineTo(lgx(85,s),lgx(72.5f,s));pa.lineTo(lgx(54,s),lgx(90,s));pa.lineTo(lgx(23,s),lgx(72.5f,s));pa.lineTo(lgx(23,s),lgx(38.5f,s));pa.close();c.drawPath(pa,q);
        for(int i=0;i<f.length;i++){pa.reset();pa.moveTo(lgx(f[i][0],s),lgx(f[i][1],s));pa.lineTo(lgx(f[i][2],s),lgx(f[i][3],s));pa.lineTo(lgx(f[i][4],s),lgx(f[i][5],s));pa.close();
            q.setStyle(Paint.Style.FILL);q.setColor(col[i]);c.drawPath(pa,q);
            q.setStyle(Paint.Style.STROKE);q.setStrokeWidth(Math.max(1,.8f*.74f*k));q.setStrokeJoin(Paint.Join.ROUND);q.setColor(0xff5a3408);c.drawPath(pa,q);}
        q.setStyle(Paint.Style.FILL);q.setColor(0xff4a2606);q.setTextAlign(Paint.Align.CENTER);q.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD));
        q.setTextSize(14*.74f*k);c.drawText("20",lgx(54,s),lgx(63,s),q);
        // quill: translate(72 31) rotate(28) scale(.5), drawn only when the tile is big enough to read it
        if(s>=28){double a=Math.toRadians(28);float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
            float[][] feather={{0,6},{-9,-14},{-6,-36},{3,-50},{8,-34},{9,-14},{0,6}};
            pa.reset();float[] p0=qt(feather[0],ca,sa,s);pa.moveTo(p0[0],p0[1]);
            for(int i=1;i<7;i+=3){float[] a1=qt(feather[i],ca,sa,s),a2=qt(feather[i+1],ca,sa,s),a3=qt(feather[i+2],ca,sa,s);pa.cubicTo(a1[0],a1[1],a2[0],a2[1],a3[0],a3[1]);}
            pa.close();q.setStyle(Paint.Style.FILL);q.setColor(0xfff4f0e8);c.drawPath(pa,q);q.setStyle(Paint.Style.STROKE);q.setStrokeWidth(Math.max(.8f,.35f*k));q.setColor(0xff8a8070);c.drawPath(pa,q);
            float[][] nib={{-1.6f,4},{1.6f,4},{.6f,13},{0,15},{-.6f,13}};pa.reset();float[] n0=qt(nib[0],ca,sa,s);pa.moveTo(n0[0],n0[1]);for(int i=1;i<nib.length;i++){float[] n=qt(nib[i],ca,sa,s);pa.lineTo(n[0],n[1]);}pa.close();
            q.setStyle(Paint.Style.FILL);q.setColor(0xffe8b84a);c.drawPath(pa,q);}
    }
    private static float[] qt(float[] p,float ca,float sa,float s){float x=(p[0]*ca-p[1]*sa)*.5f+72,y=(p[0]*sa+p[1]*ca)*.5f+31;return new float[]{lgx(x,s),lgx(y,s)};}
    void icon(Canvas c,int id,float cx,float cy,float sizeDp,int color){Icons.draw(c,p,id,cx,cy,dp(sizeDp),color);}

    /** Small all-caps section label. */
    void caption(Canvas c,String s,float x,float y,int color){text(c,s.toUpperCase(),x,y,10,color,1,-1);}

    /** Standard button: optional icon (-1 for none) and optional label. */
    void button(Canvas c,RectF r,int icon,String label,int style){
        float rad=dp(6);
        int bg,fg;
        switch(style){
            case ACTIVE: bg=ACCENT_SOFT;fg=0xff8fbcff;break;
            case PRIMARY: bg=ACCENT;fg=0xffffffff;break;
            case DANGER: bg=SURFACE_2;fg=0xffff8a8a;break;
            case GHOST: bg=0x00000000;fg=TEXT_2;break;
            case DISABLED: bg=0x80282e36;fg=0xff58626f;break;
            case SUCCESS: bg=0xff2e9e6c;fg=0xffffffff;break;
            default: bg=SURFACE_2;fg=TEXT;
        }
        if(bg!=0)round(c,r,rad,bg);
        if(style==ACTIVE)outline(c,r,rad,0x993d8bfd,dp(1));
        else if(style==NORMAL||style==DANGER||style==DISABLED)outline(c,r,rad,0x14ffffff,dp(1));
        float iconSize=Math.min(18,r.height()/dp-14);
        if(label==null||label.isEmpty()){if(icon>=0)icon(c,icon,r.centerX(),r.centerY(),iconSize,fg);return;}
        float textSize=r.height()<dp(30)?11.5f:12.5f;
        if(icon<0){textMid(c,ellipsize(label,textSize,1,r.width()-dp(12)),r.centerX(),r.centerY(),textSize,fg,1,0);return;}
        float pad=dp(r.width()<dp(110)?8:10),gap=dp(6),iw=dp(iconSize);
        float avail=r.width()-pad*2-iw-gap;
        String t=ellipsize(label,textSize,1,avail);
        if(avail<dp(18)){icon(c,icon,r.centerX(),r.centerY(),iconSize,fg);return;}
        icon(c,icon,r.left+pad+iw*.5f,r.centerY(),iconSize,style==NORMAL?TEXT_2:fg);
        textMid(c,t,r.left+pad+iw+gap,r.centerY(),textSize,fg,1,-1);
    }
    /** Centred icon-over-label button (used for big touch controls). */
    void stackButton(Canvas c,RectF r,int icon,String label,int style){
        int bg=style==PRIMARY?ACCENT:style==ACTIVE?ACCENT_SOFT:SURFACE_2,fg=style==PRIMARY?0xffffffff:TEXT;
        round(c,r,dp(8),bg);outline(c,r,dp(8),0x18ffffff,dp(1));
        icon(c,icon,r.centerX(),r.centerY()-dp(7),20,fg);
        text(c,label,r.centerX(),r.centerY()+dp(16),10.5f,fg,1,0);
    }
    /** Rounded pill with a soft shadow, used for floating hints and HUD chips. */
    void pill(Canvas c,float l,float t,float r,float b,int color){
        round(c,l,t+dp(2),r,b+dp(2),(b-t)*.5f,0x55000000);
        round(c,l,t,r,b,(b-t)*.5f,color);outline(c,l,t,r,b,(b-t)*.5f,0x1fffffff,dp(1));
    }
    /** Panel with a 1dp border. */
    void panel(Canvas c,float l,float t,float r,float b,float radius){round(c,l,t,r,b,radius,SURFACE);outline(c,l,t,r,b,radius,BORDER,dp(1));}
}
