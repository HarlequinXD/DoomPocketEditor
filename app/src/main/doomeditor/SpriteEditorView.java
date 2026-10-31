package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Pixel art sprite editor. Drawing is cursor-driven: an 8-way D-pad moves a pixel cursor and the Draw button
 * applies the current tool (hold it while moving to paint strokes). Sprites are stored inside the project.
 */
public class SpriteEditorView extends View {
    interface Host extends UiHost {void exit();void saved();}
    // tools
    static final int PENCIL=0,ERASER=1,FILL=2,PICKER=3,LINE=4,RECT=5,RECT_FILL=6,ELLIPSE=7,DITHER=8,LIGHTEN=9,DARKEN=10,SELECT=11;
    static final String[] TOOL_NAMES={"Pencil","Eraser","Fill","Colour picker","Line","Rectangle","Filled rectangle","Ellipse","Dither","Lighten","Darken","Select & move"};
    static final int[] TOOL_ICONS={Icons.PEN,Icons.ERASER,Icons.BUCKET,Icons.DROPPER,Icons.LINE_TOOL,Icons.SQUARE,Icons.SQUARE_FILL,Icons.CIRCLE,Icons.CHECKER,Icons.SUN,Icons.MOON,Icons.SELECT_RECT};
    /** DawnBringer-style 24 colour palette. */
    static final int[] PALETTE={0xff000000,0xff222034,0xff45283c,0xff663931,0xff8f563b,0xffdf7126,0xffd9a066,0xffeec39a,
            0xfffbf236,0xff99e550,0xff6abe30,0xff37946e,0xff4b692f,0xff524b24,0xff323c39,0xff3f3f74,
            0xff306082,0xff5b6ee1,0xff639bff,0xff5fcde4,0xffcbdbfc,0xffffffff,0xff9badb7,0xffac3232};
    private static final int H_BACK=1,H_SPRITE=2,H_MENU=3,H_UNDO=4,H_REDO=5,H_APPLY=6,H_SAVE=7,H_COLOR=8,H_GRID=9,H_ONION=10,H_MIRROR=11,H_SIZE=12,H_PERFECT=13,
            H_ZOOMIN=14,H_ZOOMOUT=15,H_DRAW=16,H_CANCEL=17,H_ADDCLIP=18,H_ADDFRAME=19,H_DUPFRAME=20,H_DELFRAME=21,H_PLAY=22,H_FPS=23,H_LOOP=24,
            H_NEWBLANK=25,H_NEWBUILTIN=26,H_PICK_OK=27,H_PICK_CANCEL=28,H_PICK_HEX=29,H_PICK_SV=30,H_PICK_HUE=31,H_PICK_ALPHA=32,H_SWAPCOL=33,
            H_TOOL=100,H_PAL=200,H_CLIP=300,H_FRAME=500,H_RECENT=800;

    final Project project;
    CustomSprite sprite;
    private final Host host;
    private final Ui ui;
    private final Paint p,pixelPaint=new Paint(),fadePaint=new Paint();
    private final Path path=new Path();
    private final RectF r=new RectF(),canvasR=new RectF();
    private int clipIndex=0,frameIndex=0,tool=PENCIL,lastPaintTool=PENCIL,brush=1;
    int color=0xffac3232,color2=0xff000000;
    private boolean grid=true,onion=false,mirror=false,perfect=true,playing=false;
    private int cursorX,cursorY,zoomStep=0;
    // stroke & shape state
    private boolean drawing;private int stage=0,ax,ay;
    private final HashMap<Integer,Integer> strokeOrig=new HashMap<>();
    private final ArrayList<int[]> strokePts=new ArrayList<>();
    // selection
    private int selX0,selY0,selX1,selY1;private int[] floating;private int floatW,floatH,floatX,floatY,moveAx,moveAy;
    // undo
    private static final class Edit{final int[] target;final int[] before;Edit(int[] t,int[] b){target=t;before=b;}}
    private final ArrayDeque<Edit> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    // pointers
    private int dpadPointer=-1,dpadDir=-1,drawPointer=-1,uiPointer=-1;private long nextRepeat;
    private float w,h,toolbarH,downX,downY,lastX,lastY,clipScroll,frameScroll;private boolean dragged;private int downHit=-1,scrollZone;
    // colour picker
    boolean pickerOpen;private float pickH,pickS,pickV,pickA;private int pickTarget;private final ArrayList<Integer> recent=new ArrayList<>();
    private Bitmap frameBmp,onionBmp;private int bmpVersion=-1;
    private final HashMap<int[],Bitmap> thumbCache=new HashMap<>();private int thumbVersion=-1;private CustomSprite thumbSprite;
    private Bitmap thumb(int[] px){
        if(thumbSprite!=sprite||thumbVersion!=sprite.version){thumbCache.clear();thumbSprite=sprite;thumbVersion=sprite.version;}
        Bitmap b=thumbCache.get(px);if(b==null){b=Bitmap.createBitmap(sprite.w,sprite.h,Bitmap.Config.ARGB_8888);b.setPixels(px,0,sprite.w,0,0,sprite.w,sprite.h);thumbCache.put(px,b);}return b;
    }
    private final Bitmap[] spriteThumbs=new Bitmap[Sprites.COUNT];
    private long playStart=System.nanoTime();

    SpriteEditorView(Context c,Project project,String defId,Host host){
        super(c);this.project=project;this.host=host;ui=new Ui(c);p=ui.p;pixelPaint.setFilterBitmap(false);fadePaint.setFilterBitmap(false);fadePaint.setAlpha(70);
        EntityDef d=project.def(defId);
        CustomSprite s=d!=null?project.sprite(d.customSprite):null;
        if(s==null&&d==null&&!project.sprites.isEmpty())s=project.sprites.get(0);
        pendingDef=d;
        if(s!=null)open(s);
    }
    EntityDef pendingDef;
    void open(CustomSprite s){sprite=s;clipIndex=0;frameIndex=0;cursorX=s.w/2;cursorY=s.h/2;stage=0;floating=null;undo.clear();redo.clear();bmpVersion=-1;frameBmp=null;onionBmp=null;zoomStep=0;invalidate();}
    private CustomSprite.Clip clip(){if(sprite==null)return null;clipIndex=Math.max(0,Math.min(sprite.clips.size()-1,clipIndex));return sprite.clips.get(clipIndex);}
    private int[] frame(){CustomSprite.Clip c=clip();if(c==null)return null;if(c.frames.isEmpty())c.frames.add(new int[sprite.w*sprite.h]);frameIndex=Math.max(0,Math.min(c.frames.size()-1,frameIndex));return c.frames.get(frameIndex);}
    private float dp(float v){return ui.dp(v);}

    // ---------------------------------------------------------------- layout
    private float leftW(){return Math.max(dp(132),Math.min(dp(172),w*.16f));}
    private float rightW(){return Math.max(dp(150),Math.min(dp(196),w*.18f));}
    private float bottomH(){return Math.max(dp(98),Math.min(dp(132),h*.27f));}
    private float pixelSize(){
        if(sprite==null)return dp(8);
        float cw=canvasR.width()-dp(24),ch=canvasR.height()-dp(24);
        float fit=Math.max(1,Math.min(cw/sprite.w,ch/sprite.h));
        float base=(float)Math.floor(fit);if(base<1)base=fit;
        return Math.max(1,base*(float)Math.pow(1.5,zoomStep));
    }
    private float ox,oy;
    private void layoutCanvas(){
        float ps=pixelSize(),sw=sprite.w*ps,sh=sprite.h*ps;
        ox=sw<=canvasR.width()-dp(16)?canvasR.centerX()-sw*.5f:clamp(canvasR.centerX()-(cursorX+.5f)*ps,canvasR.right-dp(8)-sw,canvasR.left+dp(8));
        oy=sh<=canvasR.height()-dp(16)?canvasR.centerY()-sh*.5f:clamp(canvasR.centerY()-(cursorY+.5f)*ps,canvasR.bottom-dp(8)-sh,canvasR.top+dp(8));
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();toolbarH=dp(48);ui.clearHits();
        c.drawColor(Ui.BG);
        float L=leftW(),R=w-rightW(),B=h-bottomH();
        canvasR.set(L,toolbarH,R,B);
        drawCanvas(c);
        drawTools(c,0,toolbarH,L,B);
        drawClips(c,0,B,L,h);
        drawFrames(c,L,B,R,h);
        drawRight(c,R,toolbarH,w,h);
        drawTopBar(c);
        if(pickerOpen)drawPicker(c);
        if(playing)postInvalidateOnAnimation();
    }
    private void drawCanvas(Canvas c){
        int save=c.save();c.clipRect(canvasR);
        ui.rect(c,canvasR.left,canvasR.top,canvasR.right,canvasR.bottom,0xff15171b);
        if(sprite==null){drawEmpty(c);c.restoreToCount(save);return;}
        layoutCanvas();
        float ps=pixelSize(),sw=sprite.w*ps,sh=sprite.h*ps;
        // transparency checkerboard (2x2 sprite pixels per square)
        int step=Math.max(1,Math.round(dp(8)/ps));
        for(int y=0;y<sprite.h;y+=step)for(int x=0;x<sprite.w;x+=step){boolean dark=((x/step)+(y/step))%2==0;
            ui.rect(c,ox+x*ps,oy+y*ps,ox+Math.min(sprite.w,x+step)*ps,oy+Math.min(sprite.h,y+step)*ps,dark?0xff2a2e35:0xff353a42);}
        int[] f=frame();
        if(onion&&frameIndex>0){if(onionBmp==null)onionBmp=Bitmap.createBitmap(sprite.w,sprite.h,Bitmap.Config.ARGB_8888);onionBmp.setPixels(clip().frames.get(frameIndex-1),0,sprite.w,0,0,sprite.w,sprite.h);
            r.set(ox,oy,ox+sw,oy+sh);c.drawBitmap(onionBmp,null,r,fadePaint);}
        if(frameBmp==null||frameBmp.getWidth()!=sprite.w||frameBmp.getHeight()!=sprite.h)frameBmp=Bitmap.createBitmap(sprite.w,sprite.h,Bitmap.Config.ARGB_8888);
        frameBmp.setPixels(f,0,sprite.w,0,0,sprite.w,sprite.h);
        r.set(ox,oy,ox+sw,oy+sh);c.drawBitmap(frameBmp,null,r,pixelPaint);
        // floating selection
        if(floating!=null)for(int y=0;y<floatH;y++)for(int x=0;x<floatW;x++){int col=floating[y*floatW+x];if((col>>>24)==0)continue;int px=floatX+x,py=floatY+y;if(px<0||py<0||px>=sprite.w||py>=sprite.h)continue;
            ui.rect(c,ox+px*ps,oy+py*ps,ox+(px+1)*ps,oy+(py+1)*ps,col);}
        // shape preview
        if(stage==1&&(tool==LINE||tool==RECT||tool==RECT_FILL||tool==ELLIPSE)){
            ArrayList<int[]> pts=shapePoints(tool,ax,ay,cursorX,cursorY);int pc=(color&0xffffff)|0xb0000000;
            for(int[] q:pts)ui.rect(c,ox+q[0]*ps,oy+q[1]*ps,ox+(q[0]+1)*ps,oy+(q[1]+1)*ps,pc);}
        // grid
        if(grid&&ps>=dp(6)){int gc=0x1affffff;for(int x=0;x<=sprite.w;x++)ui.rect(c,ox+x*ps,oy,ox+x*ps+1,oy+sh,x%8==0?0x33ffffff:gc);for(int y=0;y<=sprite.h;y++)ui.rect(c,ox,oy+y*ps,ox+sw,oy+y*ps+1,y%8==0?0x33ffffff:gc);}
        ui.outline(c,ox-1,oy-1,ox+sw+1,oy+sh+1,0,0x55ffffff,1);
        if(mirror){p.setColor(0x993d8bfd);p.setStrokeWidth(dp(1.5f));c.drawLine(ox+sw*.5f,oy-dp(6),ox+sw*.5f,oy+sh+dp(6),p);}
        // selection marquee
        if(tool==SELECT&&(stage==1||stage==2)){int x0=Math.min(stage==1?ax:selX0,stage==1?cursorX:selX1),x1=Math.max(stage==1?ax:selX0,stage==1?cursorX:selX1),y0=Math.min(stage==1?ay:selY0,stage==1?cursorY:selY1),y1=Math.max(stage==1?ay:selY0,stage==1?cursorY:selY1);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(0xffffffff);p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4),dp(4)},(System.nanoTime()/30000000L)%8*dp(1)));
            c.drawRect(ox+x0*ps,oy+y0*ps,ox+(x1+1)*ps,oy+(y1+1)*ps,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);postInvalidateDelayed(60);}
        // cursor: brush footprint with a double outline so it reads on any colour
        int b=tool==PENCIL||tool==ERASER||tool==DITHER||tool==LIGHTEN||tool==DARKEN?brush:1,bx=cursorX-(b-1)/2,by=cursorY-(b-1)/2;
        float cl=ox+bx*ps,ct=oy+by*ps,cr=cl+b*ps,cb=ct+b*ps;
        if(tool==PENCIL&&ps>=dp(4))ui.rect(c,cl,ct,cr,cb,(color&0xffffff)|0x80000000);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setColor(0xff000000);c.drawRect(cl-dp(1),ct-dp(1),cr+dp(1),cb+dp(1),p);
        p.setStrokeWidth(dp(1.5f));p.setColor(0xffffffff);c.drawRect(cl-dp(1),ct-dp(1),cr+dp(1),cb+dp(1),p);p.setStyle(Paint.Style.FILL);
        if(mirror&&(tool<=FILL||tool==DITHER||tool==LIGHTEN||tool==DARKEN)){float ml=ox+(sprite.w-bx-b)*ps;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x99ffffff);c.drawRect(ml,ct,ml+b*ps,cb,p);p.setStyle(Paint.Style.FILL);}
        // info chip
        int under=f[cursorY*sprite.w+cursorX];
        String info=cursorX+", "+cursorY+"   ·   "+sprite.w+"×"+sprite.h+"   ·   zoom "+Math.max(1,Math.round(ps/ui.dp))+"×";
        float iw=ui.measure(info,11,1)+dp(40);ui.pill(c,canvasR.left+dp(10),canvasR.bottom-dp(34),canvasR.left+dp(10)+iw,canvasR.bottom-dp(10),0xe6262c34);
        drawSwatch(c,canvasR.left+dp(22),canvasR.bottom-dp(22),dp(6),under);
        ui.textMid(c,info,canvasR.left+dp(34),canvasR.bottom-dp(22),11,Ui.TEXT_2,1,-1);
        // live animation preview
        if(playing){CustomSprite.Clip cl2=clip();float t=(System.nanoTime()-playStart)*1e-9f;int n=cl2.frames.size(),i=cl2.loop?(int)(t*cl2.fps)%n:Math.min(n-1,(int)(t*cl2.fps));
            float pw=Math.min(dp(120),canvasR.width()*.3f),phh=pw*sprite.h/sprite.w;if(phh>canvasR.height()*.6f){phh=canvasR.height()*.6f;pw=phh*sprite.w/sprite.h;}
            float px=canvasR.right-pw-dp(14),py=canvasR.top+dp(12);
            ui.round(c,px-dp(6),py-dp(6),px+pw+dp(6),py+phh+dp(24),dp(8),0xee1b1e22);ui.outline(c,px-dp(6),py-dp(6),px+pw+dp(6),py+phh+dp(24),dp(8),Ui.BORDER,dp(1));
            r.set(px,py,px+pw,py+phh);c.drawBitmap(i==frameIndex?frameBmp:thumb(cl2.frames.get(i)),null,r,pixelPaint);
            ui.textMid(c,cl2.name+" · "+(i+1)+"/"+n,px+pw*.5f,py+phh+dp(10),10,Ui.TEXT_2,1,0);}
        c.restoreToCount(save);
    }
    private void drawEmpty(Canvas c){
        float cx=canvasR.centerX(),cy=canvasR.centerY();
        ui.icon(c,Icons.PIXEL,cx,cy-dp(56),40,Ui.TEXT_3);
        ui.text(c,pendingDef!=null?"No sprite for "+pendingDef.name+" yet":"No sprites in this project yet",cx,cy-dp(10),15,Ui.TEXT,1,0);
        ui.text(c,"Start from a blank canvas or copy a built-in sprite to edit.",cx,cy+dp(12),12,Ui.TEXT_3,0,0);
        r.set(cx-dp(200),cy+dp(32),cx-dp(8),cy+dp(72));ui.button(c,r,Icons.PLUS_SQUARE,"Blank sprite",Ui.PRIMARY);ui.hit(r,H_NEWBLANK);
        r.set(cx+dp(8),cy+dp(32),cx+dp(200),cy+dp(72));ui.button(c,r,Icons.COPY,pendingDef!=null?"Copy "+Sprites.NAMES[pendingDef.sprite]:"Copy built-in",Ui.NORMAL);ui.hit(r,H_NEWBUILTIN);
    }
    private void drawSwatch(Canvas c,float cx,float cy,float rad,int col){
        p.setColor(0xff3a3f46);c.drawCircle(cx,cy,rad+dp(1),p);
        if((col>>>24)<255){p.setColor(0xffffffff);c.drawCircle(cx,cy,rad,p);p.setColor(0xffbfbfbf);c.drawRect(cx-rad*.7f,cy-rad*.7f,cx,cy,p);c.drawRect(cx,cy,cx+rad*.7f,cy+rad*.7f,p);}
        p.setColor(col);c.drawCircle(cx,cy,rad,p);
    }
    private void swatchRect(Canvas c,float l,float t,float rr,float b,int col,float rad){
        int save=c.save();path.reset();r.set(l,t,rr,b);path.addRoundRect(r,rad,rad,Path.Direction.CW);c.clipPath(path);
        ui.rect(c,l,t,rr,b,0xffd8d8d8);float s=Math.max(dp(4),(b-t)/4);for(float y=t;y<b;y+=s)for(float x=l;x<rr;x+=s)if(((int)((x-l)/s)+(int)((y-t)/s))%2==0)ui.rect(c,x,y,Math.min(rr,x+s),Math.min(b,y+s),0xffa8a8a8);
        ui.rect(c,l,t,rr,b,col);c.restoreToCount(save);
    }
    private void drawTools(Canvas c,float L,float T,float R,float B){
        ui.rect(c,L,T,R,B,Ui.SURFACE);ui.vline(c,R-dp(1),T,B,Ui.BORDER);
        float pad=dp(8),l=L+pad,rr=R-pad,y=T+dp(8);
        // colour: primary + secondary swatches
        float sw=dp(34);swatchRect(c,l,y,l+sw,y+sw,color,dp(7));ui.outline(c,l,y,l+sw,y+sw,dp(7),0x66ffffff,dp(1.5f));ui.hit(l,y,l+sw,y+sw,H_COLOR);
        swatchRect(c,l+sw+dp(6),y+dp(10),l+sw+dp(30),y+sw,color2,dp(5));ui.hit(l+sw+dp(4),y,l+sw+dp(32),y+sw,H_SWAPCOL);
        ui.text(c,hex(color),l+sw+dp(36),y+dp(14),10.5f,Ui.TEXT,1,-1);ui.text(c,"tap to edit",l+sw+dp(36),y+dp(28),9.5f,Ui.TEXT_3);
        y+=sw+dp(8);
        // palette: 8 columns
        int cols=8;float cell=(rr-l)/cols;int rows=(PALETTE.length+cols-1)/cols;
        float avail=B-y-dp(8),toolRows=4,needTools=Math.min(dp(40),(avail-rows*cell-dp(10))/toolRows);
        if(needTools<dp(28)){rows=Math.min(rows,2);}
        for(int i=0;i<Math.min(PALETTE.length,rows*cols);i++){float x=l+(i%cols)*cell,yy=y+(i/cols)*cell;ui.round(c,x+dp(1),yy+dp(1),x+cell-dp(1),yy+cell-dp(1),dp(2),PALETTE[i]);
            if(PALETTE[i]==color)ui.outline(c,x,yy,x+cell,yy+cell,dp(3),0xffffffff,dp(1.5f));ui.hit(x,yy,x+cell,yy+cell,H_PAL+i);}
        y+=rows*cell+dp(10);
        // tools grid 3 x 4
        float tw=(rr-l)/3f,th=Math.max(dp(26),Math.min(dp(40),(B-y-dp(6))/4f));
        for(int i=0;i<12;i++){float x=l+(i%3)*tw,yy=y+(i/3)*th;r.set(x+dp(1.5f),yy+dp(1.5f),x+tw-dp(1.5f),yy+th-dp(1.5f));
            ui.button(c,r,TOOL_ICONS[i],null,tool==i?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_TOOL+i);}
    }
    static String hex(int c){return String.format(java.util.Locale.US,"#%06X",c&0xffffff)+((c>>>24)<255?" "+Math.round((c>>>24)/2.55f)+"%":"");}
    private Bitmap builtinThumb(int id){int i=Math.max(0,Math.min(Sprites.COUNT-1,id));if(spriteThumbs[i]==null)spriteThumbs[i]=Sprites.preview(i);return spriteThumbs[i];}
    private void drawClips(Canvas c,float L,float T,float R,float B){
        ui.rect(c,L,T,R,B,Ui.SURFACE);ui.hline(c,L,R,T,Ui.BORDER);ui.vline(c,R-dp(1),T,B,Ui.BORDER);
        ui.caption(c,"Clips",L+dp(10),T+dp(19),Ui.TEXT_3);
        r.set(R-dp(34),T+dp(5),R-dp(8),T+dp(29));ui.button(c,r,Icons.PLUS,null,sprite==null?Ui.DISABLED:Ui.NORMAL);ui.hit(r,H_ADDCLIP);
        if(sprite==null)return;
        float top=T+dp(32),rowH=dp(28);float max=Math.max(0,sprite.clips.size()*rowH-(B-top-dp(4)));clipScroll=clamp(clipScroll,0,max);
        int save=c.save();c.clipRect(L,top,R-dp(1),B);
        for(int i=0;i<sprite.clips.size();i++){CustomSprite.Clip cl=sprite.clips.get(i);float y=top+i*rowH-clipScroll;if(y+rowH<top||y>B)continue;
            boolean on=i==clipIndex;r.set(L+dp(6),y+dp(1),R-dp(6),y+rowH-dp(2));if(on){ui.round(c,r,dp(6),Ui.ACCENT_SOFT);ui.outline(c,r,dp(6),0x993d8bfd,dp(1));}
            ui.icon(c,Icons.FILM,r.left+dp(12),r.centerY(),12,on?0xff8fbcff:Ui.TEXT_3);
            ui.textMid(c,ui.ellipsize(cl.name,12,1,r.width()-dp(56)),r.left+dp(24),r.centerY(),12,on?Ui.TEXT:Ui.TEXT_2,1,-1);
            ui.textMid(c,String.valueOf(cl.frames.size()),r.right-dp(8),r.centerY(),10.5f,Ui.TEXT_3,1,1);
            float vt=Math.max(top,r.top),vb=Math.min(B,r.bottom);if(vb>vt)ui.hit(r.left,vt,r.right,vb,H_CLIP+i);}
        c.restoreToCount(save);
    }
    private void drawFrames(Canvas c,float L,float T,float R,float B){
        ui.rect(c,L,T,R,B,Ui.SURFACE);ui.hline(c,L,R,T,Ui.BORDER);
        if(sprite==null){ui.caption(c,"Frames",L+dp(12),T+dp(19),Ui.TEXT_3);return;}
        CustomSprite.Clip cl=clip();
        ui.caption(c,"Frames · "+cl.name,L+dp(12),T+dp(19),Ui.TEXT_3);
        float x=R-dp(8),bh=dp(26),bt=T+dp(5);
        Object[][] btn={{H_DELFRAME,Icons.TRASH,cl.frames.size()>1?Ui.DANGER:Ui.DISABLED},{H_DUPFRAME,Icons.COPY,Ui.NORMAL},{H_LOOP,Icons.RESET,cl.loop?Ui.ACTIVE:Ui.NORMAL}};
        for(Object[] b:btn){r.set(x-bh,bt,x,bt+bh);ui.button(c,r,(Integer)b[1],null,(Integer)b[2]);ui.hit(r,(Integer)b[0]);x-=bh+dp(6);}
        String fps=Math.round(cl.fps)+" fps";float fw=ui.measure(fps,11,1)+dp(18);r.set(x-fw,bt,x,bt+bh);ui.button(c,r,-1,fps,Ui.NORMAL);ui.hit(r,H_FPS);x-=fw+dp(6);
        float pw=dp(64);r.set(x-pw,bt,x,bt+bh);ui.button(c,r,playing?Icons.STOP:Icons.PLAY,playing?"Stop":"Play",playing?Ui.SUCCESS:Ui.NORMAL);ui.hit(r,H_PLAY);
        float top=T+dp(36),cellH=B-top-dp(10),cellW=Math.max(dp(40),cellH*Math.min(1.2f,(float)sprite.w/sprite.h)),gap=dp(8);
        float content=(cl.frames.size()+1)*(cellW+gap);float max=Math.max(0,content-(R-L-dp(20)));frameScroll=clamp(frameScroll,0,max);
        int save=c.save();c.clipRect(L,top-dp(2),R,B);
        float playIdx=-1;if(playing){float t=(System.nanoTime()-playStart)*1e-9f;int n=cl.frames.size();playIdx=cl.loop?(int)(t*cl.fps)%n:Math.min(n-1,(int)(t*cl.fps));}
        for(int i=0;i<=cl.frames.size();i++){float fx=L+dp(12)+i*(cellW+gap)-frameScroll;if(fx+cellW<L||fx>R)continue;
            r.set(fx,top,fx+cellW,top+cellH);
            if(i==cl.frames.size()){ui.round(c,r,dp(6),0x00000000);ui.outline(c,r,dp(6),0x33ffffff,dp(1.5f));ui.icon(c,Icons.PLUS,r.centerX(),r.centerY(),18,Ui.TEXT_2);ui.hit(r,H_ADDFRAME);continue;}
            swatchRect(c,r.left,r.top,r.right,r.bottom,0,dp(6));
            Bitmap tb=i==frameIndex&&frameBmp!=null?frameBmp:thumb(cl.frames.get(i));
            float sc=Math.min((cellW-dp(6))/sprite.w,(cellH-dp(14))/sprite.h),dw=sprite.w*sc,dh=sprite.h*sc;
            RectF d=new RectF(r.centerX()-dw*.5f,r.top+dp(3),r.centerX()+dw*.5f,r.top+dp(3)+dh);c.drawBitmap(tb,null,d,pixelPaint);
            boolean on=i==frameIndex;ui.outline(c,r,dp(6),on?Ui.ACCENT:i==playIdx?Ui.GREEN:0x26ffffff,on||i==playIdx?dp(2.5f):dp(1));
            ui.round(c,r.left+dp(3),r.bottom-dp(15),r.left+dp(19),r.bottom-dp(3),dp(3),0xcc15181c);ui.textMid(c,String.valueOf(i+1),r.left+dp(11),r.bottom-dp(9),9,Ui.TEXT,1,0);
            ui.hit(r,H_FRAME+i);}
        c.restoreToCount(save);
    }
    private float dpadR(){return Math.min(dp(60),(rightW()-dp(24))*.5f);}
    private float dpadCX(){return w-rightW()*.5f;}
    private float dpadCY(){return h-dp(22)-dp(56)-dp(14)-dpadR();}
    private void drawRight(Canvas c,float L,float T,float R,float B){
        ui.rect(c,L,T,R,B,Ui.SURFACE);ui.vline(c,L,T,B,Ui.BORDER);
        float pad=dp(10),l=L+pad,rr=R-pad,y=T+dp(10);
        // toggles: grid, onion, mirror, pixel-perfect
        float tw=(rr-l)/4f,th=dp(32);
        Object[][] tg={{H_GRID,Icons.GRID,grid},{H_ONION,Icons.LAYERS,onion},{H_MIRROR,Icons.MIRROR,mirror},{H_PERFECT,Icons.PIXEL,perfect}};
        for(int i=0;i<4;i++){r.set(l+i*tw+dp(1.5f),y,l+(i+1)*tw-dp(1.5f),y+th);ui.button(c,r,(Integer)tg[i][1],null,(Boolean)tg[i][2]?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,(Integer)tg[i][0]);}
        y+=th+dp(8);
        // brush size + zoom
        float third=(rr-l)/3f;
        r.set(l,y,l+third-dp(2),y+th);ui.button(c,r,-1,brush+"px",Ui.NORMAL);ui.hit(r,H_SIZE);
        r.set(l+third+dp(1),y,l+third*2-dp(1),y+th);ui.button(c,r,Icons.MINUS,null,Ui.NORMAL);ui.hit(r,H_ZOOMOUT);
        r.set(l+third*2+dp(2),y,rr,y+th);ui.button(c,r,Icons.PLUS,null,Ui.NORMAL);ui.hit(r,H_ZOOMIN);
        y+=th+dp(10);
        ui.text(c,TOOL_NAMES[tool],l,y+dp(10),12,Ui.TEXT,1,-1);
        ui.text(c,toolHint(),l,y+dp(26),10,Ui.TEXT_3);
        // 8-way D-pad
        float cx=dpadCX(),cy=dpadCY(),Rr=dpadR();
        p.setStyle(Paint.Style.FILL);p.setColor(0x55000000);c.drawCircle(cx,cy+dp(3),Rr,p);p.setColor(0xff252a31);c.drawCircle(cx,cy,Rr,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x24ffffff);c.drawCircle(cx,cy,Rr,p);c.drawCircle(cx,cy,Rr*.3f,p);p.setStyle(Paint.Style.FILL);
        if(dpadDir>=0){p.setColor(0x403d8bfd);r.set(cx-Rr,cy-Rr,cx+Rr,cy+Rr);c.drawArc(r,dpadDir*45-90-22.5f,45,true,p);p.setColor(0xff252a31);c.drawCircle(cx,cy,Rr*.3f,p);}
        for(int i=0;i<8;i++){double a=Math.toRadians(i*45-90);float ix=cx+(float)Math.cos(a)*Rr*.68f,iy=cy+(float)Math.sin(a)*Rr*.68f;
            if(i%2==0)ui.icon(c,new int[]{Icons.CHEVRON_UP,Icons.CHEVRON_RIGHT,Icons.CHEVRON_DOWN,Icons.CHEVRON_LEFT}[i/2],ix,iy,18,i==dpadDir?0xff8fbcff:Ui.TEXT);
            else{p.setColor(i==dpadDir?0xff8fbcff:Ui.TEXT_2);c.drawCircle(ix,iy,dp(2.5f),p);}}
        // Draw button (+ cancel while a shape or selection is in progress)
        float bt=h-dp(22)-dp(56),bw=rr-l;
        boolean busy=stage>0||floating!=null;
        float dl=busy?l+dp(62):l;
        if(busy){r.set(l,bt,l+dp(56),bt+dp(56));ui.round(c,r,dp(12),Ui.SURFACE_2);ui.outline(c,r,dp(12),0x22ffffff,dp(1));ui.icon(c,Icons.CANCEL,r.centerX(),r.centerY()-dp(6),20,Ui.TEXT);ui.text(c,"Cancel",r.centerX(),r.centerY()+dp(16),9.5f,Ui.TEXT_2,1,0);ui.hit(r,H_CANCEL);}
        r.set(dl,bt,rr,bt+dp(56));
        boolean down=drawPointer>=0;
        ui.round(c,r.left,r.top+dp(3),r.right,r.bottom+dp(3),dp(12),0x66000000);
        ui.round(c,r,dp(12),down?Ui.ACCENT_DARK:Ui.ACCENT);ui.outline(c,r,dp(12),0x33ffffff,dp(1));
        if(r.width()>dp(110)){ui.icon(c,TOOL_ICONS[tool],r.left+dp(26),r.centerY(),20,0xffffffff);ui.textMid(c,drawLabel(),r.left+dp(46),r.centerY(),14,0xffffffff,2,-1);}
        else{ui.icon(c,TOOL_ICONS[tool],r.centerX(),r.centerY()-dp(7),18,0xffffffff);ui.text(c,drawLabel(),r.centerX(),r.centerY()+dp(16),11,0xffffffff,2,0);}
        ui.hit(r.left,r.top-dp(4),r.right,r.bottom+dp(10),H_DRAW);
    }
    private String toolHint(){
        switch(tool){case PENCIL:return "Hold Draw and move to paint";case ERASER:return "Hold Draw and move to erase";case FILL:return "Fills the touching area";case PICKER:return "Copies the colour under the cursor";
            case LINE:case RECT:case RECT_FILL:case ELLIPSE:return stage==0?"Draw on the start point":"Draw again on the end point";case DITHER:return "Checkerboard shading";case LIGHTEN:case DARKEN:return "Shades existing pixels";
            default:return stage==0?"Draw on one corner":stage==1?"Draw on the opposite corner":floating==null?"Draw to lift, then move":"Draw to drop it here";}
    }
    private String drawLabel(){
        switch(tool){case PENCIL:return "Draw";case ERASER:return "Erase";case FILL:return "Fill";case PICKER:return "Pick";case DITHER:return "Dither";case LIGHTEN:return "Lighten";case DARKEN:return "Darken";
            case SELECT:return stage==0?"Start":stage==1?"Select":floating==null?"Lift":"Drop";
            default:return stage==0?"Start":"Finish";}
    }
    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,toolbarH,Ui.SURFACE);ui.hline(c,0,w,toolbarH-dp(1),Ui.BORDER);
        float cy=toolbarH*.5f,bh=dp(32),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        ui.textMid(c,"Sprites",dp(48),cy,15,Ui.TEXT,1,-1);
        float sx=dp(48)+ui.measure("Sprites",15,1)+dp(12),sw=Math.min(dp(210),w*.22f);
        r.set(sx,top,sx+sw,top+bh);ui.round(c,r,dp(8),Ui.BG);ui.outline(c,r,dp(8),Ui.BORDER,dp(1));
        if(sprite!=null){Bitmap tb=thumb(sprite.clips.get(0).frames.get(0));float th=dp(22),tw=th*sprite.w/sprite.h;r.set(sx+dp(8),cy-th*.5f,sx+dp(8)+tw,cy+th*.5f);c.drawBitmap(tb,null,r,pixelPaint);}
        ui.textMid(c,ui.ellipsize(sprite==null?"New sprite…":sprite.name,12.5f,1,sw-dp(60)),sx+dp(36),cy,12.5f,Ui.TEXT,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,sx+sw-dp(14),cy,13,Ui.TEXT_2);ui.hit(sx,top,sx+sw,top+bh,H_SPRITE);
        float x=w-dp(10);boolean wide=w>dp(820);
        float svw=wide?dp(82):bh;r.set(x-svw,top,x,top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);x-=svw+dp(8);
        // Apply-to dropdown
        String applied=appliedLabel();float aw=Math.min(dp(220),ui.measure(applied,12,1)+dp(64));
        r.set(x-aw,top,x,top+bh);ui.round(c,r,dp(8),sprite==null?0x80282e36:Ui.SURFACE_2);ui.outline(c,r,dp(8),0x14ffffff,dp(1));
        ui.icon(c,Icons.ENTITIES,r.left+dp(16),cy,14,0xff8fbcff);ui.textMid(c,ui.ellipsize(applied,12,1,aw-dp(56)),r.left+dp(30),cy,12,sprite==null?Ui.TEXT_3:Ui.TEXT,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,r.right-dp(14),cy,13,Ui.TEXT_2);ui.hit(r,H_APPLY);x-=aw+dp(12);
        ui.vline(c,x+dp(6),top+dp(4),top+bh-dp(4),Ui.BORDER);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.REDO,null,redo.isEmpty()?Ui.DISABLED:Ui.NORMAL);ui.hit(r,H_REDO);x-=bh+dp(6);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.UNDO,null,undo.isEmpty()?Ui.DISABLED:Ui.NORMAL);ui.hit(r,H_UNDO);x-=bh+dp(6);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.MORE,null,sprite==null?Ui.DISABLED:Ui.NORMAL);ui.hit(r,H_MENU);
    }
    private String appliedLabel(){
        if(sprite==null)return "Apply to entity";
        ArrayList<String> names=new ArrayList<>();for(EntityDef d:project.defs)if(sprite.id.equals(d.customSprite))names.add(d.name);
        if(names.isEmpty())return "Apply to entity";if(names.size()==1)return names.get(0);return names.get(0)+" +"+(names.size()-1);
    }

    // ---------------------------------------------------------------- colour picker
    private void openPicker(int target){pickTarget=target;int c=target==0?color:color2;float[] hsv=new float[3];Color.colorToHSV(c,hsv);pickH=hsv[0];pickS=hsv[1];pickV=hsv[2];pickA=(c>>>24)/255f;pickerOpen=true;}
    private int pickColor(){return (Math.round(pickA*255)<<24)|(Color.HSVToColor(new float[]{pickH,pickS,pickV})&0xffffff);}
    private final RectF svR=new RectF(),hueR=new RectF(),alphaR=new RectF();
    private void drawPicker(Canvas c){
        ui.rect(c,0,0,w,h,0x99000000);
        float pw=Math.min(w-dp(40),dp(520)),ph=Math.min(h-dp(30),dp(300)),L=(w-pw)*.5f,T=(h-ph)*.5f,R=L+pw,B=T+ph;
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(14),0x80000000);ui.panel(c,L,T,R,B,dp(14));
        ui.text(c,"Colour",L+dp(18),T+dp(30),15,Ui.TEXT,1,-1);
        float sq=ph-dp(70);svR.set(L+dp(18),T+dp(46),L+dp(18)+sq,T+dp(46)+sq);
        int hue=Color.HSVToColor(new float[]{pickH,1,1});
        p.setColor(0xffffffff);p.setShader(new LinearGradient(svR.left,0,svR.right,0,0xffffffff,hue,Shader.TileMode.CLAMP));c.drawRect(svR,p);
        p.setColor(0xffffffff);p.setShader(new LinearGradient(0,svR.top,0,svR.bottom,0x00000000,0xff000000,Shader.TileMode.CLAMP));c.drawRect(svR,p);p.setShader(null);
        float kx=svR.left+pickS*svR.width(),ky=svR.top+(1-pickV)*svR.height();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setColor(0xff000000);c.drawCircle(kx,ky,dp(7),p);p.setStrokeWidth(dp(1.5f));p.setColor(0xffffffff);c.drawCircle(kx,ky,dp(7),p);p.setStyle(Paint.Style.FILL);
        ui.hit(svR,H_PICK_SV);
        hueR.set(svR.right+dp(14),svR.top,svR.right+dp(40),svR.bottom);
        p.setColor(0xffffffff);p.setShader(new LinearGradient(0,hueR.top,0,hueR.bottom,new int[]{0xffff0000,0xffffff00,0xff00ff00,0xff00ffff,0xff0000ff,0xffff00ff,0xffff0000},null,Shader.TileMode.CLAMP));c.drawRect(hueR,p);p.setShader(null);
        float hy=hueR.top+pickH/360f*hueR.height();ui.outline(c,hueR.left-dp(3),hy-dp(4),hueR.right+dp(3),hy+dp(4),dp(3),0xffffffff,dp(2));ui.hit(hueR.left-dp(6),hueR.top,hueR.right+dp(6),hueR.bottom,H_PICK_HUE);
        alphaR.set(hueR.right+dp(12),svR.top,hueR.right+dp(38),svR.bottom);
        swatchRect(c,alphaR.left,alphaR.top,alphaR.right,alphaR.bottom,0,0);
        int opaque=pickColor()|0xff000000;p.setColor(0xffffffff);p.setShader(new LinearGradient(0,alphaR.top,0,alphaR.bottom,opaque,opaque&0xffffff,Shader.TileMode.CLAMP));c.drawRect(alphaR,p);p.setShader(null);
        float ay2=alphaR.top+(1-pickA)*alphaR.height();ui.outline(c,alphaR.left-dp(3),ay2-dp(4),alphaR.right+dp(3),ay2+dp(4),dp(3),0xffffffff,dp(2));ui.hit(alphaR.left-dp(6),alphaR.top,alphaR.right+dp(6),alphaR.bottom,H_PICK_ALPHA);
        // right column: preview, hex, recent
        float rx=alphaR.right+dp(20),rr=R-dp(18);
        int old=pickTarget==0?color:color2;
        swatchRect(c,rx,svR.top,rx+(rr-rx)*.5f,svR.top+dp(44),old,0);swatchRect(c,rx+(rr-rx)*.5f,svR.top,rr,svR.top+dp(44),pickColor(),0);ui.outline(c,rx,svR.top,rr,svR.top+dp(44),dp(6),Ui.BORDER,dp(1));
        ui.text(c,"Old",rx+dp(6),svR.top+dp(58),10,Ui.TEXT_3);ui.text(c,"New",rr-dp(6),svR.top+dp(58),10,Ui.TEXT_3,0,1);
        r.set(rx,svR.top+dp(68),rr,svR.top+dp(100));ui.round(c,r,dp(6),Ui.BG);ui.outline(c,r,dp(6),Ui.BORDER,dp(1));ui.textMid(c,hex(pickColor()),r.left+dp(10),r.centerY(),12.5f,Ui.TEXT,1,-1);ui.icon(c,Icons.PEN,r.right-dp(14),r.centerY(),13,Ui.TEXT_3);ui.hit(r,H_PICK_HEX);
        ui.caption(c,"Recent",rx,svR.top+dp(120),Ui.TEXT_3);
        float rc=(rr-rx)/6f;for(int i=0;i<Math.min(12,recent.size());i++){float x=rx+(i%6)*rc,y=svR.top+dp(128)+(i/6)*rc;swatchRect(c,x+dp(1.5f),y+dp(1.5f),x+rc-dp(1.5f),y+rc-dp(1.5f),recent.get(i),dp(3));ui.hit(x,y,x+rc,y+rc,H_RECENT+i);}
        float half=(rr-rx-dp(8))/2f;
        r.set(rx+half+dp(8),svR.bottom-dp(36),rr,svR.bottom);ui.button(c,r,Icons.CHECK,"OK",Ui.PRIMARY);ui.hit(r,H_PICK_OK);
        r.set(rx,svR.bottom-dp(36),rx+half,svR.bottom);ui.button(c,r,-1,"Cancel",Ui.NORMAL);ui.hit(r,H_PICK_CANCEL);
    }
    private void pickerDrag(int zone,float x,float y){
        if(zone==H_PICK_SV){pickS=clamp((x-svR.left)/svR.width(),0,1);pickV=1-clamp((y-svR.top)/svR.height(),0,1);}
        else if(zone==H_PICK_HUE)pickH=clamp((y-hueR.top)/hueR.height(),0,1)*359.9f;
        else if(zone==H_PICK_ALPHA)pickA=1-clamp((y-alphaR.top)/alphaR.height(),0,1);
        invalidate();
    }
    private void setColor(int c){color=c;recent.remove(Integer.valueOf(c));recent.add(0,c);while(recent.size()>12)recent.remove(recent.size()-1);}

    // ---------------------------------------------------------------- pixel operations
    private void beginEdit(){int[] f=frame();undo.push(new Edit(f,f.clone()));if(undo.size()>80)undo.removeLast();redo.clear();}
    private void endEdit(){sprite.touch();save();}
    private void save(){project.save(getContext());}
    private boolean in(int x,int y){return x>=0&&y>=0&&x<sprite.w&&y<sprite.h;}
    private void setPx(int[] f,int x,int y,int c){if(!in(x,y))return;int i=y*sprite.w+x;if(!strokeOrig.containsKey(i))strokeOrig.put(i,f[i]);f[i]=c;}
    private void plotBrush(int x,int y){
        int[] f=frame();int b=brush,x0=x-(b-1)/2,y0=y-(b-1)/2;
        for(int yy=y0;yy<y0+b;yy++)for(int xx=x0;xx<x0+b;xx++){plotOne(f,xx,yy);if(mirror)plotOne(f,sprite.w-1-xx,yy);}
    }
    private void plotOne(int[] f,int x,int y){
        if(!in(x,y))return;int i=y*sprite.w+x;
        switch(tool){
            case PENCIL:setPx(f,x,y,color);break;
            case ERASER:setPx(f,x,y,0);break;
            case DITHER:if(((x+y)&1)==0)setPx(f,x,y,color);break;
            case LIGHTEN:case DARKEN:{if(strokeOrig.containsKey(i))return;int c=f[i];if((c>>>24)==0)return;float k=tool==LIGHTEN?1.18f:.82f;int add=tool==LIGHTEN?10:0;
                setPx(f,x,y,(c&0xff000000)|(Math.min(255,(int)(((c>>16)&255)*k)+add)<<16)|(Math.min(255,(int)(((c>>8)&255)*k)+add)<<8)|Math.min(255,(int)((c&255)*k)+add));break;}
        }
    }
    /** Pixel-perfect: removes the corner pixel of an L-shaped step so single-pixel lines stay clean. */
    private void pixelPerfect(){
        if(!perfect||brush!=1||(tool!=PENCIL&&tool!=ERASER)||strokePts.size()<3)return;
        int[] a=strokePts.get(strokePts.size()-3),b=strokePts.get(strokePts.size()-2),c=strokePts.get(strokePts.size()-1);
        boolean corner=Math.abs(a[0]-c[0])==1&&Math.abs(a[1]-c[1])==1&&(Math.abs(b[0]-a[0])+Math.abs(b[1]-a[1])==1)&&(Math.abs(b[0]-c[0])+Math.abs(b[1]-c[1])==1);
        if(!corner)return;
        int[] f=frame();restore(f,b[0],b[1]);if(mirror)restore(f,sprite.w-1-b[0],b[1]);
        strokePts.remove(strokePts.size()-2);
    }
    private void restore(int[] f,int x,int y){if(!in(x,y))return;int i=y*sprite.w+x;Integer o=strokeOrig.get(i);if(o!=null){f[i]=o;strokeOrig.remove(i);}}
    private void flood(int[] f,int sx,int sy,int target,int repl){
        if(target==repl||!in(sx,sy)||f[sy*sprite.w+sx]!=target)return;
        ArrayDeque<int[]> q=new ArrayDeque<>();q.add(new int[]{sx,sy});
        while(!q.isEmpty()){int[] n=q.poll();int x=n[0],y=n[1];if(!in(x,y)||f[y*sprite.w+x]!=target)continue;f[y*sprite.w+x]=repl;
            q.add(new int[]{x+1,y});q.add(new int[]{x-1,y});q.add(new int[]{x,y+1});q.add(new int[]{x,y-1});}
    }
    static ArrayList<int[]> shapePoints(int tool,int x0,int y0,int x1,int y1){
        ArrayList<int[]> out=new ArrayList<>();
        if(tool==LINE){int dx=Math.abs(x1-x0),dy=-Math.abs(y1-y0),sx=x0<x1?1:-1,sy=y0<y1?1:-1,err=dx+dy,x=x0,y=y0;
            while(true){out.add(new int[]{x,y});if(x==x1&&y==y1)break;int e2=2*err;if(e2>=dy){err+=dy;x+=sx;}if(e2<=dx){err+=dx;y+=sy;}}return out;}
        int l=Math.min(x0,x1),r=Math.max(x0,x1),t=Math.min(y0,y1),b=Math.max(y0,y1);
        if(tool==RECT_FILL){for(int y=t;y<=b;y++)for(int x=l;x<=r;x++)out.add(new int[]{x,y});return out;}
        if(tool==RECT){for(int x=l;x<=r;x++){out.add(new int[]{x,t});if(b!=t)out.add(new int[]{x,b});}for(int y=t+1;y<b;y++){out.add(new int[]{l,y});if(r!=l)out.add(new int[]{r,y});}return out;}
        // ellipse inscribed in the box: test pixel centres against the ellipse edge band
        float cx=(l+r)*.5f,cy=(t+b)*.5f,rx=(r-l)*.5f+.5f,ry=(b-t)*.5f+.5f;
        for(int y=t;y<=b;y++)for(int x=l;x<=r;x++){float dx=(x-cx)/rx,dy=(y-cy)/ry,d=dx*dx+dy*dy;if(d>1.0001f)continue;
            boolean edge=false;for(int[] o:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){float ex=(x+o[0]-cx)/rx,ey=(y+o[1]-cy)/ry;if(ex*ex+ey*ey>1.0001f){edge=true;break;}}
            if(edge)out.add(new int[]{x,y});}
        return out;
    }
    /** Draw button pressed. */
    private void drawDown(){
        if(sprite==null)return;
        int[] f=frame();
        switch(tool){
            case PENCIL:case ERASER:case DITHER:case LIGHTEN:case DARKEN:
                beginEdit();strokeOrig.clear();strokePts.clear();drawing=true;strokePts.add(new int[]{cursorX,cursorY});plotBrush(cursorX,cursorY);break;
            case FILL:beginEdit();flood(f,cursorX,cursorY,f[cursorY*sprite.w+cursorX],color);if(mirror)flood(f,sprite.w-1-cursorX,cursorY,f[cursorY*sprite.w+sprite.w-1-cursorX],color);endEdit();break;
            case PICKER:{int c=f[cursorY*sprite.w+cursorX];if((c>>>24)!=0){setColor(c);tool=lastPaintTool;}break;}
            case LINE:case RECT:case RECT_FILL:case ELLIPSE:
                if(stage==0){ax=cursorX;ay=cursorY;stage=1;}
                else{beginEdit();strokeOrig.clear();for(int[] q:shapePoints(tool,ax,ay,cursorX,cursorY)){setPx(f,q[0],q[1],color);if(mirror)setPx(f,sprite.w-1-q[0],q[1],color);}stage=0;endEdit();}
                break;
            case SELECT:
                if(stage==0){ax=cursorX;ay=cursorY;stage=1;}
                else if(stage==1){selX0=Math.min(ax,cursorX);selX1=Math.max(ax,cursorX);selY0=Math.min(ay,cursorY);selY1=Math.max(ay,cursorY);stage=2;}
                else if(floating==null){beginEdit();floatW=selX1-selX0+1;floatH=selY1-selY0+1;floating=new int[floatW*floatH];
                    for(int y=0;y<floatH;y++)for(int x=0;x<floatW;x++){int i=(selY0+y)*sprite.w+selX0+x;floating[y*floatW+x]=f[i];f[i]=0;}
                    floatX=selX0;floatY=selY0;moveAx=cursorX;moveAy=cursorY;}
                else{for(int y=0;y<floatH;y++)for(int x=0;x<floatW;x++){int c=floating[y*floatW+x];int px=floatX+x,py=floatY+y;if((c>>>24)!=0&&in(px,py))f[py*sprite.w+px]=c;}
                    floating=null;stage=0;endEdit();}
                break;
        }
        invalidate();
    }
    private void drawUp(){if(drawing){drawing=false;strokePts.clear();endEdit();}invalidate();}
    private void cancelOp(){
        if(floating!=null){int[] f=frame();for(int y=0;y<floatH;y++)for(int x=0;x<floatW;x++){int px=selX0+x,py=selY0+y;if(in(px,py))f[py*sprite.w+px]=floating[y*floatW+x];}floating=null;if(!undo.isEmpty())undo.pop();}
        stage=0;invalidate();
    }
    private void moveCursor(int dir){
        if(sprite==null)return;
        int dx=dir==1||dir==2||dir==3?1:dir==5||dir==6||dir==7?-1:0,dy=dir==3||dir==4||dir==5?1:dir==7||dir==0||dir==1?-1:0;
        int nx=Math.max(0,Math.min(sprite.w-1,cursorX+dx)),ny=Math.max(0,Math.min(sprite.h-1,cursorY+dy));
        if(nx==cursorX&&ny==cursorY)return;
        cursorX=nx;cursorY=ny;
        if(floating!=null){floatX=selX0+(cursorX-moveAx);floatY=selY0+(cursorY-moveAy);}
        if(drawing){strokePts.add(new int[]{cursorX,cursorY});plotBrush(cursorX,cursorY);pixelPerfect();}
        invalidate();
    }
    private void selectTool(int t){if(t!=tool){cancelOp();}tool=t;if(t==PENCIL||t==ERASER||t==DITHER||t==LIGHTEN||t==DARKEN||t==FILL||t==LINE||t==RECT||t==RECT_FILL||t==ELLIPSE)if(t!=ERASER)lastPaintTool=t;invalidate();}
    private void undoOp(){if(undo.isEmpty())return;cancelOp();Edit e=undo.pop();redo.push(new Edit(e.target,e.target.clone()));System.arraycopy(e.before,0,e.target,0,e.target.length);sprite.touch();save();invalidate();}
    private void redoOp(){if(redo.isEmpty())return;Edit e=redo.pop();undo.push(new Edit(e.target,e.target.clone()));System.arraycopy(e.before,0,e.target,0,e.target.length);sprite.touch();save();invalidate();}

    // ---------------------------------------------------------------- menus
    private void spriteMenu(){
        if(host==null){newSprite(2);return;}
        ArrayList<String> items=new ArrayList<>();for(CustomSprite s:project.sprites)items.add(s.name+"  ("+s.w+"×"+s.h+")");
        items.add("New blank sprite…");items.add("New from built-in sprite…");
        int cur=sprite==null?-1:project.sprites.indexOf(sprite);
        host.choose("Sprites in this project",items.toArray(new String[0]),cur,i->{
            if(i<project.sprites.size())open(project.sprites.get(i));
            else if(i==project.sprites.size())newBlankDialog();else newBuiltinDialog();});
    }
    private static final String[] SIZES={"16 × 16 (item)","32 × 32","32 × 48 (character)","48 × 64 (large)","64 × 64"};
    private static final int[][] SIZE_WH={{16,16},{32,32},{32,48},{48,64},{64,64}};
    private void newBlankDialog(){if(host==null){newSprite(2);return;}host.choose("Canvas size",SIZES,2,this::newSprite);}
    private void newSprite(int size){
        String base=pendingDef!=null?pendingDef.name:"Sprite "+(project.sprites.size()+1);
        Runnable make=()->{};
        java.util.function.Consumer<String> done=name->{String n=name.trim().isEmpty()?base:name.trim();CustomSprite s=CustomSprite.blank(project.uniqueId(n),n,SIZE_WH[size][0],SIZE_WH[size][1]);project.sprites.add(s);autoAssign(s);open(s);save();};
        if(host!=null)host.prompt("Sprite name",null,base,false,done);else done.accept(base);
    }
    private void newBuiltinDialog(){
        if(host==null){fromBuiltin(Sprites.TROOPER);return;}
        host.choose("Copy a built-in sprite",Sprites.NAMES,pendingDef!=null?pendingDef.sprite:1,this::fromBuiltin);
    }
    private void fromBuiltin(int b){
        String n=(pendingDef!=null?pendingDef.name:Sprites.NAMES[b])+" sprite";
        CustomSprite s=CustomSprite.fromBuiltin(project.uniqueId(n),n,b);project.sprites.add(s);autoAssign(s);open(s);save();
    }
    /** When the editor was opened for an entity that has no sprite yet, the first new sprite is applied to it. */
    private void autoAssign(CustomSprite s){if(pendingDef!=null&&project.sprite(pendingDef.customSprite)==null){pendingDef.customSprite=s.id;}}
    private void applyMenu(){
        if(sprite==null||host==null)return;
        String[] items=new String[project.defs.size()];
        for(int i=0;i<items.length;i++){EntityDef d=project.defs.get(i);boolean on=sprite.id.equals(d.customSprite);items[i]=(on?"✓  ":"     ")+d.name+(on?"":project.sprite(d.customSprite)!=null?"  (uses "+project.sprite(d.customSprite).name+")":"");}
        host.choose("Use "+sprite.name+" for…",items,-1,i->{EntityDef d=project.defs.get(i);d.customSprite=sprite.id.equals(d.customSprite)?"":sprite.id;save();invalidate();});
    }
    private void imageMenu(){
        if(sprite==null||host==null)return;
        String[] items={"Flip horizontally","Flip vertically","Shift 1px left","Shift 1px right","Shift 1px up","Shift 1px down","Add dark outline","Clear frame","Copy frame to all frames in clip","Resize canvas…","Rename sprite","Duplicate sprite","Delete sprite"};
        host.choose(sprite.name,items,-1,i->{
            int[] f=frame();int W=sprite.w,H=sprite.h;
            switch(i){
                case 0:beginEdit();for(int y=0;y<H;y++)for(int x=0;x<W/2;x++){int a=y*W+x,b=y*W+W-1-x;int t=f[a];f[a]=f[b];f[b]=t;}endEdit();break;
                case 1:beginEdit();for(int y=0;y<H/2;y++)for(int x=0;x<W;x++){int a=y*W+x,b=(H-1-y)*W+x;int t=f[a];f[a]=f[b];f[b]=t;}endEdit();break;
                case 2:case 3:case 4:case 5:{beginEdit();int dx=i==2?-1:i==3?1:0,dy=i==4?-1:i==5?1:0;int[] src=f.clone();java.util.Arrays.fill(f,0);
                    for(int y=0;y<H;y++)for(int x=0;x<W;x++){int nx=x+dx,ny=y+dy;if(nx>=0&&ny>=0&&nx<W&&ny<H)f[ny*W+nx]=src[y*W+x];}endEdit();break;}
                case 6:{beginEdit();int[] src=f.clone();for(int y=0;y<H;y++)for(int x=0;x<W;x++){if((src[y*W+x]>>>24)!=0)continue;
                    boolean edge=(x>0&&(src[y*W+x-1]>>>24)!=0)||(x<W-1&&(src[y*W+x+1]>>>24)!=0)||(y>0&&(src[(y-1)*W+x]>>>24)!=0)||(y<H-1&&(src[(y+1)*W+x]>>>24)!=0);if(edge)f[y*W+x]=0xff141010;}endEdit();break;}
                case 7:beginEdit();java.util.Arrays.fill(f,0);endEdit();break;
                case 8:{CustomSprite.Clip c=clip();for(int k=0;k<c.frames.size();k++)if(k!=frameIndex)System.arraycopy(f,0,c.frames.get(k),0,f.length);sprite.touch();save();break;}
                case 9:host.choose("Resize canvas (keeps pixels, anchored bottom-centre)",SIZES,-1,k->resize(SIZE_WH[k][0],SIZE_WH[k][1]));break;
                case 10:host.prompt("Rename sprite",null,sprite.name,false,t->{if(!t.trim().isEmpty()){sprite.name=t.trim();save();}});break;
                case 11:{try{CustomSprite c=CustomSprite.fromJson(sprite.toJson());c.id=project.uniqueId(sprite.name+" copy");c.name=sprite.name+" copy";project.sprites.add(c);open(c);save();}catch(Exception ignored){}break;}
                case 12:host.confirm("Delete "+sprite.name+"?","Entities using it go back to their built-in sprite.","Delete",()->{
                    for(EntityDef d:project.defs)if(sprite.id.equals(d.customSprite))d.customSprite="";project.sprites.remove(sprite);sprite=null;
                    if(!project.sprites.isEmpty())open(project.sprites.get(0));save();invalidate();});break;
            }
            invalidate();
        });
    }
    private void resize(int nw,int nh){
        int ow=sprite.w,oh=sprite.h,dx=(nw-ow)/2,dy=nh-oh;
        for(CustomSprite.Clip c:sprite.clips)for(int k=0;k<c.frames.size();k++){int[] src=c.frames.get(k),dst=new int[nw*nh];
            for(int y=0;y<oh;y++)for(int x=0;x<ow;x++){int nx=x+dx,ny=y+dy;if(nx>=0&&ny>=0&&nx<nw&&ny<nh)dst[ny*nw+nx]=src[y*ow+x];}c.frames.set(k,dst);}
        sprite.w=nw;sprite.h=nh;sprite.touch();undo.clear();redo.clear();frameBmp=null;onionBmp=null;cursorX=Math.min(cursorX+dx,nw-1);cursorY=Math.max(0,Math.min(cursorY+dy,nh-1));save();invalidate();
    }
    private void clipMenu(int i){
        if(host==null)return;CustomSprite.Clip c=sprite.clips.get(i);
        host.choose(c.name,new String[]{"Rename","Duplicate clip","Delete clip"},-1,k->{
            if(k==0)host.prompt("Clip name","Idle, Walk, Attack, Pain and Death are used by the game automatically.",c.name,false,t->{if(!t.trim().isEmpty()){c.name=t.trim();save();}});
            else if(k==1){CustomSprite.Clip d=new CustomSprite.Clip(c.name+" copy");d.fps=c.fps;d.loop=c.loop;for(int[] f:c.frames)d.frames.add(f.clone());sprite.clips.add(i+1,d);sprite.touch();save();}
            else if(sprite.clips.size()>1){sprite.clips.remove(i);clipIndex=Math.max(0,i-1);frameIndex=0;sprite.touch();save();}
            invalidate();});
    }

    // ---------------------------------------------------------------- touch
    private int dpadHit(float x,float y){
        float dx=x-dpadCX(),dy=y-dpadCY(),d=(float)Math.hypot(dx,dy);
        if(d>dpadR()*1.12f||d<dpadR()*.18f)return -1;
        double a=Math.toDegrees(Math.atan2(dy,dx))+90;if(a<0)a+=360;return (int)Math.round(a/45)%8;
    }
    private void startRepeat(){
        postDelayed(new Runnable(){public void run(){
            if(dpadPointer<0||dpadDir<0)return;
            if(System.currentTimeMillis()>=nextRepeat){moveCursor(dpadDir);nextRepeat=System.currentTimeMillis()+(drawing?70:85);}
            postDelayed(this,25);}},25);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked(),idx=e.getActionIndex(),id=e.getPointerId(idx);float x=e.getX(idx),y=e.getY(idx);
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            int hid=ui.hitAt(x,y);
            if(pickerOpen){if(hid==H_PICK_SV||hid==H_PICK_HUE||hid==H_PICK_ALPHA){uiPointer=id;downHit=hid;pickerDrag(hid,x,y);}else{uiPointer=id;downHit=hid;}return true;}
            if(sprite!=null&&dpadPointer<0){int dir=dpadHit(x,y);if(dir>=0){dpadPointer=id;dpadDir=dir;moveCursor(dir);nextRepeat=System.currentTimeMillis()+260;startRepeat();invalidate();return true;}}
            if(hid==H_DRAW&&drawPointer<0&&sprite!=null){drawPointer=id;drawDown();return true;}
            if(uiPointer<0){uiPointer=id;downX=lastX=x;downY=lastY=y;dragged=false;downHit=hid;
                scrollZone=y>h-bottomH()?(x<leftW()?1:x<w-rightW()?2:0):0;}
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float px=e.getX(i),py=e.getY(i);
                if(pid==dpadPointer){int dir=dpadHit(px,py);if(dir!=dpadDir){dpadDir=dir;if(dir>=0){moveCursor(dir);nextRepeat=System.currentTimeMillis()+260;}invalidate();}}
                else if(pid==uiPointer){
                    if(pickerOpen){if(downHit==H_PICK_SV||downHit==H_PICK_HUE||downHit==H_PICK_ALPHA)pickerDrag(downHit,px,py);continue;}
                    if(Math.abs(px-downX)+Math.abs(py-downY)>dp(8))dragged=true;
                    if(dragged){if(scrollZone==1)clipScroll-=py-lastY;else if(scrollZone==2)frameScroll-=px-lastX;invalidate();}
                    lastX=px;lastY=py;}}
            return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_CANCEL){
            boolean all=a!=MotionEvent.ACTION_POINTER_UP;
            if(id==dpadPointer||all&&dpadPointer>=0){dpadPointer=-1;dpadDir=-1;invalidate();}
            if(id==drawPointer||all&&drawPointer>=0){drawPointer=-1;drawUp();}
            if(id==uiPointer||all&&uiPointer>=0){
                if(a!=MotionEvent.ACTION_CANCEL&&!dragged&&downHit>=0&&ui.hitAt(x,y)==downHit)action(downHit);
                uiPointer=-1;downHit=-1;scrollZone=0;invalidate();}
            return true;
        }
        return true;
    }
    private void action(int id){
        if(pickerOpen){
            if(id==H_PICK_OK){int c=pickColor();if(pickTarget==0)setColor(c);else color2=c;pickerOpen=false;}
            else if(id==H_PICK_CANCEL)pickerOpen=false;
            else if(id==H_PICK_HEX&&host!=null)host.prompt("Hex colour","RRGGBB or AARRGGBB",String.format("%06X",pickColor()&0xffffff),false,t->{try{String s=t.trim().replace("#","");long v=Long.parseLong(s,16);int c=s.length()<=6?(int)(0xff000000L|v):(int)v;
                float[] hsv=new float[3];Color.colorToHSV(c,hsv);pickH=hsv[0];pickS=hsv[1];pickV=hsv[2];pickA=(c>>>24)/255f;invalidate();}catch(Exception ignored){}});
            else if(id>=H_RECENT&&id<H_RECENT+12){int c=recent.get(id-H_RECENT);float[] hsv=new float[3];Color.colorToHSV(c,hsv);pickH=hsv[0];pickS=hsv[1];pickV=hsv[2];pickA=(c>>>24)/255f;}
            invalidate();return;
        }
        if(id>=H_RECENT)return;
        if(id>=H_FRAME){int i=id-H_FRAME;if(sprite!=null&&i<clip().frames.size()){cancelOp();frameIndex=i;}invalidate();return;}
        if(id>=H_CLIP){int i=id-H_CLIP;if(sprite!=null&&i<sprite.clips.size()){if(i==clipIndex)clipMenu(i);else{cancelOp();clipIndex=i;frameIndex=0;playStart=System.nanoTime();}}invalidate();return;}
        if(id>=H_PAL){int i=id-H_PAL;if(i<PALETTE.length){setColor(PALETTE[i]);if(tool==ERASER||tool==PICKER)tool=lastPaintTool;}invalidate();return;}
        if(id>=H_TOOL){selectTool(id-H_TOOL);return;}
        switch(id){
            case H_BACK:if(host!=null)host.exit();break;
            case H_SAVE:save();if(host!=null)host.saved();break;
            case H_SPRITE:spriteMenu();break;
            case H_MENU:imageMenu();break;
            case H_UNDO:undoOp();break;
            case H_REDO:redoOp();break;
            case H_APPLY:applyMenu();break;
            case H_COLOR:openPicker(0);break;
            case H_SWAPCOL:{int t=color;color=color2;color2=t;break;}
            case H_GRID:grid=!grid;break;
            case H_ONION:onion=!onion;break;
            case H_MIRROR:mirror=!mirror;break;
            case H_PERFECT:perfect=!perfect;break;
            case H_SIZE:brush=brush%3+1;break;
            case H_ZOOMIN:zoomStep=Math.min(4,zoomStep+1);break;
            case H_ZOOMOUT:zoomStep=Math.max(-2,zoomStep-1);break;
            case H_CANCEL:cancelOp();break;
            case H_NEWBLANK:newBlankDialog();break;
            case H_NEWBUILTIN:if(pendingDef!=null&&host!=null)fromBuiltin(pendingDef.sprite);else newBuiltinDialog();break;
            case H_ADDCLIP:if(sprite!=null){java.util.function.Consumer<String> add=t->{CustomSprite.Clip c=new CustomSprite.Clip(t.trim().isEmpty()?"Clip "+(sprite.clips.size()+1):t.trim());c.frames.add(new int[sprite.w*sprite.h]);sprite.clips.add(c);clipIndex=sprite.clips.size()-1;frameIndex=0;sprite.touch();save();invalidate();};
                if(host!=null)host.prompt("New clip","Idle, Walk, Attack, Pain and Death are played by the game automatically.","Clip "+(sprite.clips.size()+1),false,add);else add.accept("");}break;
            case H_ADDFRAME:if(sprite!=null){cancelOp();clip().frames.add(frameIndex+1,new int[sprite.w*sprite.h]);frameIndex++;sprite.touch();save();}break;
            case H_DUPFRAME:if(sprite!=null){cancelOp();clip().frames.add(frameIndex+1,frame().clone());frameIndex++;sprite.touch();save();}break;
            case H_DELFRAME:if(sprite!=null&&clip().frames.size()>1){cancelOp();clip().frames.remove(frameIndex);frameIndex=Math.max(0,frameIndex-1);sprite.touch();save();}break;
            case H_PLAY:playing=!playing;playStart=System.nanoTime();break;
            case H_LOOP:if(sprite!=null){clip().loop=!clip().loop;save();}break;
            case H_FPS:if(sprite!=null&&host!=null)host.choose("Frames per second",new String[]{"2","4","6","8","10","12","15","20","24"},-1,k->{clip().fps=new int[]{2,4,6,8,10,12,15,20,24}[k];save();invalidate();});break;
        }
        invalidate();
    }

    // ---------------------------------------------------------------- testing hooks
    void testMove(int dir){moveCursor(dir);}
    void testDraw(boolean down){if(down)drawDown();else drawUp();}
    void testTool(int t){selectTool(t);}
    void testPlay(boolean on){playing=on;}
}
