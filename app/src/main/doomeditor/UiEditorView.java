package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * UI Editor, HUD & Controls page: the real game screen at full size with the real HUD on it. Tap a
 * control or HUD element to select it, drag it anywhere, and use the floating panel to change its size,
 * opacity and visibility, nudge it a step at a time, or apply a ready-made layout. Saved in the project.
 */
public class UiEditorView extends View {
    interface Host extends UiHost {void exit();void saved();}
    private static final int H_HEAD=1,H_COLLAPSE=2,H_PRESETS=3,H_GRID=4,H_RESET_ALL=5,H_SAVE=6,H_EXIT=7,H_SHOW=8,H_JOYMODE=9,H_RESET_ONE=10,
            H_NUDGE=20,H_SIZE=30,H_ALPHA=31,H_TAB=40,H_LIST=100;

    private final Project project;private final Host host;private final Ui ui;private final Paint p;
    private final GameHud hud;private final World world;private final Renderer renderer=new Renderer();
    private Bitmap backdrop;private int bw,bh;
    private float w,h;
    private int sel=-1;private boolean grid=true,collapsed;
    private float panelX=-1,panelY=-1,panelW,panelH;
    private final RectF panel=new RectF(),r=new RectF(),sizeTrack=new RectF(),alphaTrack=new RectF();
    private final float[] pt=new float[2];
    // touch
    private int mode;   // 0 none, 1 drag element, 2 drag panel, 3 slider size, 4 slider alpha, 5 panel tap
    private float grabX,grabY,downX,downY;private int downHit;private boolean moved;
    private String hint;private long hintUntil;
    private final Paint dash=new Paint(Paint.ANTI_ALIAS_FLAG);

    UiEditorView(Context c,Project project,Host host){
        super(c);this.project=project;this.host=host;ui=new Ui(c);p=ui.p;
        hud=new GameHud(ui,project.hud);
        world=new World(project,null);
        dash.setStyle(Paint.Style.STROKE);dash.setPathEffect(new DashPathEffect(new float[]{ui.dp(6),ui.dp(4)},0));
    }
    private float dp(float v){return ui.dp(v);}
    private HudLayout L(){return project.hud;}
    private void hint(String s){hint=s;hintUntil=System.currentTimeMillis()+2400;invalidate();}
    boolean back(){if(sel>=0){sel=-1;invalidate();return true;}return false;}

    // ================================================================== drawing
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();hud.layout=project.hud;
        ui.clearHits();
        drawBackdrop(c);
        if(grid)drawGrid(c);
        boolean caster=world.hero!=null&&!world.hero.spells.isEmpty();
        // joystick (fixed: its base; floating: the half of the screen it appears on)
        HudLayout.Elem je=L().e[HudLayout.JOYSTICK];
        if(L().joyFixed){float[] jc=hud.joyCenter(w,h);hud.drawJoystick(c,jc[0],jc[1],jc[0],jc[1],1f);
            float jr=L().radius(HudLayout.JOYSTICK,ui.dp);hud.rects[HudLayout.JOYSTICK].set(jc[0]-jr,jc[1]-jr,jc[0]+jr,jc[1]+jr);}
        else{boolean right=hud.joyOnRight(w,h);float l=right?w*.55f:0,rr=right?w:w*.45f,t=dp(70),b=h-L().barH(ui.dp)-dp(8);
            ui.round(c,l+dp(8),t,rr-dp(8),b,dp(14),0x183d8bfd);dash.setColor(0x603d8bfd);dash.setStrokeWidth(dp(1.5f));r.set(l+dp(8),t,rr-dp(8),b);c.drawRoundRect(r,dp(14),dp(14),dash);
            float[] jc=hud.joyCenter(w,h);hud.drawJoystick(c,jc[0],jc[1],jc[0],jc[1],.5f);
            ui.text(c,"Joystick appears where the thumb lands",(l+rr)*.5f,b-dp(10),10.5f,0xa0e8ecf1,1,0);
            float jr=L().radius(HudLayout.JOYSTICK,ui.dp);hud.rects[HudLayout.JOYSTICK].set(jc[0]-jr,jc[1]-jr,jc[0]+jr,jc[1]+jr);}
        hud.drawStatus(c,w,h,world,true);
        hud.drawControls(c,w,h,world.hero,null,true);
        if(!caster){RectF cr=hud.rects[HudLayout.CAST];ui.text(c,"spellcasters only",cr.centerX(),cr.bottom+dp(10),9,0xb0c8b0ff,1,0);}
        // labels on hidden elements
        for(int i=0;i<HudLayout.N;i++)if(!L().e[i].visible&&(hud.drawn[i]||i==HudLayout.JOYSTICK)){RectF q=hud.rects[i];ui.text(c,"hidden",q.centerX(),q.centerY()+dp(4),10,0xd0ffffff,2,0);}
        if(sel>=0)drawSelection(c);
        drawPanel(c);
        if(hint!=null&&System.currentTimeMillis()<hintUntil){float tw=ui.measure(hint,12,1)+dp(32),cx=w*.5f,t=dp(14);ui.pill(c,cx-tw*.5f,t,cx+tw*.5f,t+dp(32),0xf02a313a);ui.textMid(c,hint,cx,t+dp(16),12,Ui.TEXT,1,0);postInvalidateDelayed(300);}
    }
    private void drawBackdrop(Canvas c){
        int W=(int)w,H=(int)h;
        if(backdrop==null||bw!=W||bh!=H){
            if(backdrop!=null)backdrop.recycle();backdrop=Bitmap.createBitmap(Math.max(1,W),Math.max(1,H),Bitmap.Config.ARGB_8888);bw=W;bh=H;
            Canvas bc=new Canvas(backdrop);Level level=project.level;int si=level.sectorAt(level.playerX,level.playerY);
            if(si<0)bc.drawColor(0xff1a1e23);
            else{Level.Sector sec=level.sectors.get(si);renderer.highlight=-1;renderer.fx=null;
                renderer.render(bc,level,level.playerX,level.playerY,Math.min(sec.ceiling-.1f,sec.floor+World.EYE),level.facing,0,new RectF(0,0,W,H),new java.util.ArrayList<Renderer.SpriteDraw>());}
            bc.drawColor(0x40000000);
        }
        c.drawBitmap(backdrop,0,0,null);
    }
    private void drawGrid(Canvas c){
        float step=dp(16);
        for(float x=step;x<w;x+=step)ui.rect(c,x,0,x+1,h,((int)(x/step))%4==0?0x22ffffff:0x10ffffff);
        for(float y=step;y<h;y+=step)ui.rect(c,0,y,w,y+1,((int)(y/step))%4==0?0x22ffffff:0x10ffffff);
        ui.rect(c,w*.5f,0,w*.5f+1,h,0x303d8bfd);ui.rect(c,0,h*.5f,w,h*.5f+1,0x303d8bfd);
    }
    private void drawSelection(Canvas c){
        RectF q=hud.rects[sel];
        dash.setColor(Ui.ORANGE);dash.setStrokeWidth(dp(2));
        r.set(q.left-dp(3),q.top-dp(3),q.right+dp(3),q.bottom+dp(3));
        if(HudLayout.round(sel))c.drawCircle(r.centerX(),r.centerY(),r.width()*.5f,dash);else c.drawRoundRect(r,dp(8),dp(8),dash);
        String n=HudLayout.NAMES[sel];float tw=ui.measure(n,11,2)+dp(16),lx=Math.max(dp(4),Math.min(w-tw-dp(4),r.centerX()-tw*.5f)),ly=r.top-dp(26)<dp(4)?r.bottom+dp(4):r.top-dp(26);
        ui.round(c,lx,ly,lx+tw,ly+dp(22),dp(11),Ui.ORANGE);ui.textMid(c,n,lx+tw*.5f,ly+dp(11),11,0xff1a1206,2,0);
    }

    // ------------------------------------------------------------------ floating panel
    private void drawPanel(Canvas c){
        panelW=Math.min(dp(320),w-dp(24));
        panelH=collapsed?dp(40):sel>=0?dp(sel==HudLayout.JOYSTICK?320:sel==HudLayout.STATUS?198:286):dp(152);
        if(panelX<0){panelX=(w-panelW)*.5f;panelY=(h-panelH)*.42f;}
        panelX=Math.max(dp(4),Math.min(w-panelW-dp(4),panelX));panelY=Math.max(dp(4),Math.min(h-panelH-dp(4),panelY));
        float l=panelX,t=panelY,rr=l+panelW,b=t+panelH;panel.set(l,t,rr,b);
        ui.round(c,l,t+dp(4),rr,b+dp(4),dp(12),0x90000000);ui.round(c,l,t,rr,b,dp(12),0xf21b1f25);ui.outline(c,l,t,rr,b,dp(12),Ui.BORDER,dp(1));
        // header: drag handle + title + collapse
        r.set(l,t,rr-dp(40),t+dp(40));ui.hit(r,H_HEAD);
        ui.icon(c,Icons.MOVE,l+dp(18),t+dp(20),14,Ui.TEXT_3);
        ui.textMid(c,"UI Editor · HUD & Controls",l+dp(34),t+dp(20),12.5f,Ui.TEXT,1,-1);
        r.set(rr-dp(38),t+dp(4),rr-dp(4),t+dp(36));ui.button(c,r,collapsed?Icons.CHEVRON_DOWN:Icons.CHEVRON_UP,null,Ui.GHOST);ui.hit(r,H_COLLAPSE);
        if(collapsed)return;
        float x=l+dp(10),y=t+dp(42),bw2=(panelW-dp(20)-dp(18))/4f;
        int[] ic={Icons.LIST,Icons.GRID,Icons.RESET,Icons.SAVE};String[] lb={"Layouts","Grid","Reset","Save"};int[] id={H_PRESETS,H_GRID,H_RESET_ALL,H_SAVE};
        for(int i=0;i<4;i++){r.set(x+i*(bw2+dp(6)),y,x+i*(bw2+dp(6))+bw2,y+dp(34));ui.button(c,r,ic[i],lb[i],i==1&&grid?Ui.ACTIVE:i==3?Ui.PRIMARY:Ui.NORMAL);ui.hit(r,id[i]);}
        y+=dp(42);
        if(sel<0){
            ui.text(c,"Tap a control or HUD part to select it,",l+dp(12),y+dp(14),11.5f,Ui.TEXT_2);
            ui.text(c,"then drag it where you want it.",l+dp(12),y+dp(30),11.5f,Ui.TEXT_2);
            r.set(l+dp(10),y+dp(42),rr-dp(10),y+dp(72));ui.button(c,r,Icons.LIST,"Pick from a list…",Ui.NORMAL);ui.hit(r,H_LIST);
            return;}
        HudLayout.Elem el=L().e[sel];
        ui.text(c,HudLayout.NAMES[sel],l+dp(12),y+dp(14),14,Ui.ORANGE,2,-1);
        r.set(rr-dp(92),y-dp(2),rr-dp(10),y+dp(24));ui.button(c,r,Icons.RESET,"Reset",Ui.NORMAL);ui.hit(r,H_RESET_ONE);
        y+=dp(28);
        // visible toggle
        toggle(c,l+dp(12),rr-dp(12),y,"Show in game",el.visible,H_SHOW);y+=dp(34);
        if(sel==HudLayout.JOYSTICK){toggle(c,l+dp(12),rr-dp(12),y,L().joyFixed?"Fixed position":"Floating (follows thumb)",L().joyFixed,H_JOYMODE);y+=dp(34);}
        if(sel!=HudLayout.STATUS){y=slider(c,l+dp(12),rr-dp(12),y,"Size",Math.round(el.scale*100)+"%",(el.scale-.5f)/1.5f,sizeTrack,H_SIZE);}
        y=slider(c,l+dp(12),rr-dp(12),y,"Opacity",Math.round(el.alpha*100)+"%",(el.alpha-.15f)/.85f,alphaTrack,H_ALPHA);
        if(HudLayout.movable(sel)){
            float nb=dp(36),cx=(l+rr)*.5f;ui.text(c,"Nudge",l+dp(12),y+dp(24),11,Ui.TEXT_3,1,-1);
            int[] ni={Icons.ARROW_LEFT,Icons.ARROW_UP,Icons.ARROW_DOWN,Icons.CHEVRON_RIGHT};
            for(int i=0;i<4;i++){r.set(cx-nb*2-dp(9)+i*(nb+dp(6)),y+dp(4),cx-nb*2-dp(9)+i*(nb+dp(6))+nb,y+dp(4)+nb);ui.button(c,r,ni[i],null,Ui.NORMAL);ui.hit(r,H_NUDGE+i);}
        }
    }
    private void toggle(Canvas c,float l,float rr,float y,String label,boolean on,int id){
        r.set(l,y,rr,y+dp(30));ui.textMid(c,label,l,y+dp(15),12,Ui.TEXT,1,-1);
        float tx=rr-dp(36),ty=y+dp(7);ui.round(c,tx,ty,tx+dp(32),ty+dp(16),dp(8),on?Ui.ACCENT:Ui.SURFACE_3);p.setColor(0xffffffff);c.drawCircle(on?tx+dp(24):tx+dp(8),ty+dp(8),dp(6),p);
        ui.hit(r,id);
    }
    private float slider(Canvas c,float l,float rr,float y,String label,String val,float n,RectF track,int id){
        ui.textMid(c,label,l,y+dp(10),11,Ui.TEXT_3,1,-1);ui.textMid(c,val,rr,y+dp(10),11.5f,Ui.TEXT,1,1);
        float ty=y+dp(28);n=Math.max(0,Math.min(1,n));
        ui.round(c,l,ty-dp(2),rr,ty+dp(2),dp(2),Ui.SURFACE_3);ui.round(c,l,ty-dp(2),l+(rr-l)*n,ty+dp(2),dp(2),Ui.ACCENT);p.setColor(0xffffffff);c.drawCircle(l+(rr-l)*n,ty,dp(8),p);
        track.set(l,y,rr,y+dp(42));ui.hit(track,id);return y+dp(44);
    }

    // ================================================================== touch
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){
            downX=x;downY=y;moved=false;mode=0;downHit=-1;
            if(panel.contains(x,y)){
                downHit=ui.hitAt(x,y);
                if(downHit==H_HEAD){mode=2;grabX=x-panelX;grabY=y-panelY;}
                else if(downHit==H_SIZE){mode=3;setSlider(3,x);}
                else if(downHit==H_ALPHA){mode=4;setSlider(4,x);}
                else mode=5;
                invalidate();return true;}
            int hit=elementAt(x,y);
            if(hit>=0){sel=hit;if(HudLayout.movable(hit)){mode=1;L().center(hit,w,h,ui.dp,isCaster(),pt);grabX=x-pt[0];grabY=y-pt[1];}}
            else sel=-1;
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(!moved&&Math.abs(x-downX)+Math.abs(y-downY)>dp(6))moved=true;
            if(mode==1&&moved&&sel>=0){float cx=x-grabX,cy=y-grabY;if(grid){float s=dp(8);cx=Math.round(cx/s)*s;cy=Math.round(cy/s)*s;}
                boolean wasFloating=sel==HudLayout.JOYSTICK&&!L().joyFixed;
                L().place(sel,cx,cy,w,h,ui.dp);if(wasFloating)hint("Joystick switched to a fixed position");}
            else if(mode==2){panelX=x-grabX;panelY=y-grabY;}
            else if(mode==3||mode==4)setSlider(mode,x);
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_UP){
            if(mode==5&&!moved){int hid=ui.hitAt(x,y);if(hid==downHit)tap(hid);}
            if(mode==1||mode==3||mode==4)changed();
            mode=0;invalidate();return true;
        }
        if(a==MotionEvent.ACTION_CANCEL){mode=0;invalidate();}
        return true;
    }
    private boolean isCaster(){return world.hero!=null&&!world.hero.spells.isEmpty();}
    /** Topmost element under a point: buttons, joystick, then quest/messages, then the status bar. */
    private int elementAt(float x,float y){
        int[] order={HudLayout.MENU,HudLayout.BAG,HudLayout.ATTACK,HudLayout.CAST,HudLayout.JUMP,HudLayout.USE,HudLayout.POTION,HudLayout.JOYSTICK,HudLayout.QUEST,HudLayout.MESSAGE,HudLayout.STATUS};
        for(int id:order){RectF q=hud.rects[id];if((hud.drawn[id]||id==HudLayout.JOYSTICK)&&q.contains(x,y))return id;}
        return -1;
    }
    private void setSlider(int which,float x){
        if(sel<0)return;HudLayout.Elem el=L().e[sel];
        RectF t=which==3?sizeTrack:alphaTrack;float n=Math.max(0,Math.min(1,(x-t.left)/Math.max(1,t.width())));
        if(which==3)el.scale=Math.round((.5f+n*1.5f)*20)/20f;else el.alpha=Math.round((.15f+n*.85f)*20)/20f;
    }
    private void changed(){project.save(getContext());}
    private void tap(int hid){
        HudLayout.Elem el=sel>=0?L().e[sel]:null;
        switch(hid){
            case H_COLLAPSE:collapsed=!collapsed;return;
            case H_GRID:grid=!grid;hint(grid?"Grid on · controls snap to it":"Grid off · free placement");return;
            case H_SAVE:if(host!=null)host.saved();return;
            case H_EXIT:if(host!=null)host.exit();return;
            case H_PRESETS:if(host!=null)host.choose("Apply a layout",HudLayout.PRESETS,-1,k->{L().preset(k,w,h,ui.dp,isCaster());changed();hint(HudLayout.PRESETS[k]+" layout applied");invalidate();});return;
            case H_RESET_ALL:if(host!=null)host.confirm("Reset every control?","All controls and HUD parts go back to their default spot, size and opacity.","Reset",()->{L().resetAll();changed();invalidate();});return;
            case H_LIST:if(host!=null)host.choose("Select",HudLayout.NAMES,-1,k->{sel=k;invalidate();});return;
        }
        if(el==null)return;
        switch(hid){
            case H_SHOW:el.visible=!el.visible;if(sel==HudLayout.JOYSTICK&&!el.visible){el.visible=true;hint("The joystick is needed to move – make it fainter instead");}else if(sel==HudLayout.MENU&&!el.visible)hint("Players can still pause with the Back button");changed();return;
            case H_JOYMODE:if(L().joyFixed){L().joyFixed=false;}else{L().center(HudLayout.JOYSTICK,w,h,ui.dp,false,pt);L().place(HudLayout.JOYSTICK,pt[0],pt[1],w,h,ui.dp);}changed();return;
            case H_RESET_ONE:L().resetElem(sel);changed();return;
        }
        if(hid>=H_NUDGE&&hid<H_NUDGE+4&&HudLayout.movable(sel)){
            float step=grid?dp(8):dp(2);L().center(sel,w,h,ui.dp,isCaster(),pt);
            float dx=hid==H_NUDGE?-step:hid==H_NUDGE+3?step:0,dy=hid==H_NUDGE+1?-step:hid==H_NUDGE+2?step:0;
            L().place(sel,pt[0]+dx,pt[1]+dy,w,h,ui.dp);changed();
        }
    }
}
