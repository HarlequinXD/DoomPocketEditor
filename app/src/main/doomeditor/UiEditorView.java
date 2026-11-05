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
            H_NUDGE=20,H_SIZE=30,H_ALPHA=31,H_TAB=40,H_LIST=100,H_BACK=101,H_TOPSAVE=102,H_GUNDO=103,H_GREDO=104;
    static final String[] TABS={"HUD & Controls","Theme","Main Menu","Screens"};
    private static final int[] TAB_ICONS={Icons.GAMEPAD,Icons.PALETTE,Icons.LIST,Icons.FLAG};
    private int tab;
    /** The game screen is previewed at its real size, scaled to fit under the top bar. */
    private float vw,vh,scale=1,offX,offY,barH;

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
    private float grabX,grabY,downX,downY,lastY;private int downHit;private boolean moved;
    private String hint;private long hintUntil;
    private final Paint dash=new Paint(Paint.ANTI_ALIAS_FLAG);

    UiEditorView(Context c,Project project,Host host){
        super(c);this.project=project;this.host=host;ui=new Ui(c);p=ui.p;
        hud=new GameHud(ui,project.hud);
        world=new World(project,null);
        dash.setStyle(Paint.Style.STROKE);dash.setPathEffect(new DashPathEffect(new float[]{ui.dp(6),ui.dp(4)},0));
        history=new Undo(()->project.section(Project.SEC_UI),t->project.restoreSection(Project.SEC_UI,t));history.reset();
    }
    final Undo history;
    void afterEdit(){history.commit();invalidate();}
    private void undoRedo(boolean back){
        if(back?history.undo():history.redo()){project.save(getContext());hint(back?"Undo":"Redo");}else hint(back?"Nothing to undo":"Nothing to redo");}
    private float dp(float v){return ui.dp(v);}
    private HudLayout L(){return project.hud;}
    private void hint(String s){hint=s;hintUntil=System.currentTimeMillis()+2400;invalidate();}
    boolean back(){if(sel>=0){sel=-1;invalidate();return true;}return false;}

    // ================================================================== drawing
    @Override protected void onDraw(Canvas c){
        vw=getWidth();vh=getHeight();barH=dp(48);
        w=vw;h=vh;   // HUD positions use the real screen size
        scale=Math.min(1,(vh-barH-dp(8))/vh);offX=(vw-w*scale)*.5f;offY=barH+dp(4);
        hud.layout=project.hud;hud.theme=project.theme;
        ui.clearHits();
        c.drawColor(Ui.BG);
        if(tab==1){drawThemeTab(c);drawTopBar(c);drawHint(c);return;}
        if(tab!=0){drawTopBar(c);drawComingSoon(c);return;}
        c.save();c.translate(offX,offY);c.scale(scale,scale);c.clipRect(0,0,w,h);
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
        c.restore();
        ui.clearHits();   // preview targets were in preview space; the bar and panel use screen space
        drawTopBar(c);
        drawPanel(c);
        if(hint!=null&&System.currentTimeMillis()<hintUntil){float tw=ui.measure(hint,12,1)+dp(32),cx=vw*.5f,t=barH+dp(12);ui.pill(c,cx-tw*.5f,t,cx+tw*.5f,t+dp(32),0xf02a313a);ui.textMid(c,hint,cx,t+dp(16),12,Ui.TEXT,1,0);postInvalidateDelayed(300);}
    }
    private void drawBackdrop(Canvas c){
        int W=(int)w,H=(int)h;
        if(backdrop==null||bw!=W||bh!=H){
            if(backdrop!=null)backdrop.recycle();backdrop=Bitmap.createBitmap(Math.max(1,W),Math.max(1,H),Bitmap.Config.ARGB_8888);bw=W;bh=H;
            Canvas bc=new Canvas(backdrop);Level level=project.level;int si=level.sectorAt(level.playerX,level.playerY);
            if(si<0)bc.drawColor(0xff1a1e23);
            else{Level.Sector sec=level.sectors.get(si);renderer.highlight=-1;renderer.fx=null;Atmosphere.prepareStatic(renderer,project,project.current(),level,0);
                renderer.render(bc,level,level.playerX,level.playerY,Math.min(sec.ceiling-.1f,sec.floor+World.EYE),level.facing,0,new RectF(0,0,W,H),new java.util.ArrayList<Renderer.SpriteDraw>());}
            bc.drawColor(0x40000000);
        }
        c.drawBitmap(backdrop,0,0,null);
    }
    // ================================================================== Theme page
    private static final int H_PRESET=200,H_RANDOM=220,H_MUTATE=221,H_UNDO=222,H_PROP=230,H_TEXTURE=250;
    private final RectF side=new RectF(),texTrack=new RectF();
    private float sideScroll,sideContent;
    private final UiTheme[] presetThemes=new UiTheme[UiTheme.PRESETS.length];
    private final java.util.ArrayDeque<UiTheme> undo=new java.util.ArrayDeque<>();
    private final java.util.Random rng=new java.util.Random();
    private static final String[] PROPS={"Material","Panel colour","Accent colour","Text colour","Border","Corners","Button shape","Button fill","Bar style"};
    private void drawHint(Canvas c){
        if(hint!=null&&System.currentTimeMillis()<hintUntil){float tw=ui.measure(hint,12,1)+dp(32),cx=vw*.5f,t=barH+dp(12);ui.pill(c,cx-tw*.5f,t,cx+tw*.5f,t+dp(32),0xf02a313a);ui.textMid(c,hint,cx,t+dp(16),12,Ui.TEXT,1,0);postInvalidateDelayed(300);}
    }
    private void drawThemeTab(Canvas c){
        float sideW=Math.min(dp(300),vw*.42f);side.set(vw-sideW,barH,vw,vh);
        float aw=vw-sideW-dp(12),ah=vh-barH-dp(8);
        scale=Math.min(aw/w,ah/h);offX=dp(6)+(aw-w*scale)*.5f;offY=barH+dp(4);
        UiTheme th=project.theme;
        c.save();c.translate(offX,offY);c.scale(scale,scale);c.clipRect(0,0,w,h);
        drawBackdrop(c);
        float[] jc=hud.joyCenter(w,h);hud.drawJoystick(c,jc[0],jc[1],jc[0]+dp(14),jc[1]-dp(10),.9f);
        hud.drawStatus(c,w,h,world,true);
        hud.drawControls(c,w,h,world.hero,null,true);
        // sample dialogue box and menu button so the whole style can be judged
        float dl=w*.18f,dr=w*.62f,dt=h*.3f,db=dt+dp(92);
        th.panel(c,p,dl,dt,dr,db,ui.dp,.97f,false);
        float pw=ui.measure("Old Merrick",13,2)+dp(28);ui.round(c,dl+dp(16),dt-dp(14),dl+dp(16)+pw,dt+dp(14),dp(14),UiTheme.shade(th.accent,.42f));ui.outline(c,dl+dp(16),dt-dp(14),dl+dp(16)+pw,dt+dp(14),dp(14),th.accent,dp(1.2f));
        ui.textMid(c,"Old Merrick",dl+dp(30),dt,13,0xffffffff,2,-1);
        ui.text(c,"Welcome, traveller. The crypt lies east.",dl+dp(22),dt+dp(38),14,th.text,1,-1);
        ui.text(c,"1.  Tell me more",dl+dp(22),dt+dp(66),13,th.textDim(),1,-1);
        float ml=w*.18f,mt=db+dp(20);th.panel(c,p,ml,mt,ml+dp(220),mt+dp(46),ui.dp,1,false);ui.icon(c,Icons.FLAG,ml+dp(26),mt+dp(23),18,th.text);ui.textMid(c,"New Game",ml+dp(50),mt+dp(23),16,th.text,2,-1);
        c.restore();
        ui.clearHits();
        // side panel
        ui.rect(c,side.left,side.top,side.right,side.bottom,Ui.SURFACE);ui.vline(c,side.left,side.top,side.bottom,Ui.BORDER);
        c.save();c.clipRect(side);
        float l=side.left+dp(12),rr=side.right-dp(12),y=side.top+dp(12)-sideScroll;
        ui.text(c,ui.ellipsize(th.name,15,1,rr-l),l,y+dp(14),15,Ui.TEXT,1,-1);ui.text(c,"Current theme",l,y+dp(30),10.5f,Ui.TEXT_3);y+=dp(42);
        float bw=(rr-l-dp(12))/3f;
        r.set(l,y,l+bw,y+dp(34));ui.button(c,r,Icons.SPARKLE,"Random",Ui.PRIMARY);sideHit(r,H_RANDOM);
        r.set(l+bw+dp(6),y,l+bw*2+dp(6),y+dp(34));ui.button(c,r,Icons.RESET,"Mutate",Ui.ACTIVE);sideHit(r,H_MUTATE);
        r.set(l+bw*2+dp(12),y,rr,y+dp(34));ui.button(c,r,Icons.UNDO,"Undo",history.canUndo()?Ui.NORMAL:Ui.DISABLED);sideHit(r,H_UNDO);y+=dp(44);
        ui.caption(c,"Built-in themes",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
        float tw=(rr-l-dp(8))/2f;
        for(int i=0;i<presetThemes.length;i++){if(presetThemes[i]==null)presetThemes[i]=UiTheme.preset(i);UiTheme pt2=presetThemes[i];
            float x=l+(i%2)*(tw+dp(8)),yy=y+(i/2)*dp(58);
            pt2.panel(c,p,x,yy,x+tw,yy+dp(50),ui.dp,1,false);
            pt2.button(c,p,x+tw-dp(20),yy+dp(25),dp(11),0xffc8402e,false,ui.dp,1);
            ui.text(c,ui.ellipsize(UiTheme.PRESETS[i],11.5f,2,tw-dp(44)),x+dp(10),yy+dp(22),11.5f,pt2.text,2,-1);
            ui.text(c,UiTheme.MATERIALS[pt2.material],x+dp(10),yy+dp(38),9.5f,pt2.textDim(),1,-1);
            if(th.name.equals(UiTheme.PRESETS[i])){ui.outline(c,x-dp(2),yy-dp(2),x+tw+dp(2),yy+dp(52),dp(8),Ui.ORANGE,dp(2));}
            r.set(x,yy,x+tw,yy+dp(50));sideHit(r,H_PRESET+i);}
        y+=((presetThemes.length+1)/2)*dp(58)+dp(6);
        ui.caption(c,"Customise",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
        for(int i=0;i<PROPS.length;i++){r.set(l,y,rr,y+dp(40));ui.round(c,r,dp(7),Ui.SURFACE_2);
            ui.text(c,PROPS[i],l+dp(10),y+dp(15),10.5f,Ui.TEXT_3,1,-1);
            String v;int sw=0;
            switch(i){case 0:v=UiTheme.MATERIALS[th.material];break;case 1:v=colorName(th.panel,UiTheme.PANEL_NAMES,UiTheme.PANEL_COLS);sw=th.panel;break;
                case 2:v=colorName(th.accent,UiTheme.ACCENT_NAMES,UiTheme.ACCENT_COLS);sw=th.accent;break;case 3:v=colorName(th.text,UiTheme.TEXT_NAMES,UiTheme.TEXT_COLS);sw=th.text;break;
                case 4:v=UiTheme.BORDERS[th.border];break;case 5:v=UiTheme.CORNERS[th.corners];break;case 6:v=UiTheme.BUTTON_SHAPES[th.buttonShape];break;case 7:v=UiTheme.BUTTON_FILLS[th.buttonFill];break;default:v=UiTheme.BAR_STYLES[th.barStyle];}
            float vx=l+dp(10);if(sw!=0){ui.round(c,vx,y+dp(22),vx+dp(14),y+dp(36),dp(3),sw);ui.outline(c,vx,y+dp(22),vx+dp(14),y+dp(36),dp(3),0x60ffffff,dp(1));vx+=dp(20);}
            ui.text(c,ui.ellipsize(v,12.5f,1,rr-vx-dp(30)),vx,y+dp(33),12.5f,Ui.TEXT,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,rr-dp(14),y+dp(20),13,Ui.TEXT_3);
            sideHit(r,H_PROP+i);y+=dp(46);}
        // texture strength slider
        r.set(l,y,rr,y+dp(46));ui.round(c,r,dp(7),Ui.SURFACE_2);
        ui.text(c,"Texture strength",l+dp(10),y+dp(16),10.5f,Ui.TEXT_3,1,-1);ui.text(c,Math.round(th.texture*100)+"%",rr-dp(10),y+dp(16),11.5f,Ui.TEXT,1,1);
        float tl=l+dp(12),tr=rr-dp(12),ty=y+dp(32);ui.round(c,tl,ty-dp(2),tr,ty+dp(2),dp(2),Ui.SURFACE_3);ui.round(c,tl,ty-dp(2),tl+(tr-tl)*th.texture,ty+dp(2),dp(2),Ui.ACCENT);p.setColor(0xffffffff);c.drawCircle(tl+(tr-tl)*th.texture,ty,dp(7),p);
        texTrack.set(tl,y,tr,y+dp(46));sideHit(r,H_TEXTURE);y+=dp(56);
        ui.text(c,"The theme styles the status bar, buttons, joystick,",l,y+dp(4),10.5f,Ui.TEXT_3);
        ui.text(c,"quest tracker, main menu, Options and dialogue.",l,y+dp(18),10.5f,Ui.TEXT_3);y+=dp(30);
        sideContent=y+sideScroll-side.top;c.restore();
        float max=Math.max(0,sideContent-side.height());if(sideScroll>max||sideScroll<0){sideScroll=Math.max(0,Math.min(max,sideScroll));invalidate();}
    }
    private void sideHit(RectF rr,int id){if(rr.bottom<side.top||rr.top>side.bottom)return;ui.hit(rr.left,Math.max(rr.top,side.top),rr.right,Math.min(rr.bottom,side.bottom),id);}
    private static String colorName(int c,String[] names,int[] cols){for(int i=0;i<cols.length;i++)if((cols[i]|0xff000000)==(c|0xff000000))return names[i];return String.format("#%06X",c&0xffffff);}
    private void pushUndo(){undo.push(project.theme.copy());while(undo.size()>20)undo.removeLast();}
    private void themeChanged(){project.theme.changed();changed();invalidate();}
    private void setTexture(float x){project.theme.texture=Math.round(Math.max(0,Math.min(1,(x-texTrack.left)/Math.max(1,texTrack.width())))*20)/20f;project.theme.changed();invalidate();}
    private void themeTap(int hid){
        UiTheme th=project.theme;
        if(hid>=H_PRESET&&hid<H_PRESET+UiTheme.PRESETS.length){pushUndo();project.theme=UiTheme.preset(hid-H_PRESET);themeChanged();hint(UiTheme.PRESETS[hid-H_PRESET]+" theme");return;}
        switch(hid){
            case H_RANDOM:pushUndo();project.theme=UiTheme.random(rng);themeChanged();hint("Generated "+UiTheme.MATERIALS[project.theme.material].toLowerCase()+" theme · Mutate to tweak, Undo to go back");return;
            case H_MUTATE:pushUndo();th.mutate(rng);themeChanged();return;
            case H_UNDO:undoRedo(true);return;
        }
        if(hid<H_PROP||hid>=H_PROP+PROPS.length||host==null)return;
        int i=hid-H_PROP;
        String[] items;int cur;
        switch(i){
            case 0:items=UiTheme.MATERIALS;cur=th.material;break;
            case 1:items=withRandom(UiTheme.PANEL_NAMES);cur=indexOf(UiTheme.PANEL_COLS,th.panel);break;
            case 2:items=withRandom(UiTheme.ACCENT_NAMES);cur=indexOf(UiTheme.ACCENT_COLS,th.accent);break;
            case 3:items=withRandom(UiTheme.TEXT_NAMES);cur=indexOf(UiTheme.TEXT_COLS,th.text);break;
            case 4:items=UiTheme.BORDERS;cur=th.border;break;
            case 5:items=UiTheme.CORNERS;cur=th.corners;break;
            case 6:items=UiTheme.BUTTON_SHAPES;cur=th.buttonShape;break;
            case 7:items=UiTheme.BUTTON_FILLS;cur=th.buttonFill;break;
            default:items=UiTheme.BAR_STYLES;cur=th.barStyle;
        }
        final String[] it=items;
        host.choose(PROPS[i],items,cur,k->{
            pushUndo();UiTheme t=project.theme;boolean rnd=k==it.length-1&&(i>=1&&i<=3);
            switch(i){
                case 0:t.material=k;break;
                case 1:t.panel=rnd?UiTheme.hsv(rng.nextFloat()*360,.1f+rng.nextFloat()*.5f,.1f+rng.nextFloat()*.3f):UiTheme.PANEL_COLS[k];if(UiTheme.luma(t.panel)>.5f!=UiTheme.luma(t.text)<.5f)t.fixText();break;
                case 2:t.accent=rnd?UiTheme.hsv(rng.nextFloat()*360,.5f+rng.nextFloat()*.4f,.8f+rng.nextFloat()*.2f):UiTheme.ACCENT_COLS[k];break;
                case 3:t.text=rnd?UiTheme.hsv(rng.nextFloat()*360,.1f,.95f):UiTheme.TEXT_COLS[k];break;
                case 4:t.border=k;break;case 5:t.corners=k;break;case 6:t.buttonShape=k;break;case 7:t.buttonFill=k;break;default:t.barStyle=k;
            }
            t.name="Custom";themeChanged();});
    }
    private static String[] withRandom(String[] names){String[] o=java.util.Arrays.copyOf(names,names.length+1);o[names.length]="Random colour";return o;}
    private static int indexOf(int[] cols,int c){for(int i=0;i<cols.length;i++)if((cols[i]|0xff000000)==(c|0xff000000))return i;return -1;}

    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,vw,barH,Ui.SURFACE);ui.hline(c,0,vw,barH-dp(1),Ui.BORDER);
        float cy=barH*.5f,bh=dp(34),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        boolean wide=vw>dp(820);
        float ib=dp(28);ui.round(c,dp(50),cy-ib*.5f,dp(50)+ib,cy+ib*.5f,dp(7),0xffd8a830);ui.icon(c,Icons.GAMEPAD,dp(50)+ib*.5f,cy,16,0xff1a1206);
        float tl=dp(86);if(wide){ui.textMid(c,"UI Editor",tl,cy,15,Ui.TEXT,1,-1);tl+=dp(90);}
        float saveW=wide?dp(84):bh;r.set(vw-dp(10)-saveW,top,vw-dp(10),top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_TOPSAVE);
        float ux=Undo.buttons(ui,c,r,history,vw-dp(16)-saveW,top,bh,H_GUNDO,H_GREDO);
        float avail=ux-tl-dp(4),tw=Math.min(dp(150),avail/TABS.length);
        for(int i=0;i<TABS.length;i++){r.set(tl+i*tw,top,tl+(i+1)*tw-dp(5),top+bh);ui.button(c,r,TAB_ICONS[i],TABS[i],i==tab?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_TAB+i);}
    }
    private void drawComingSoon(Canvas c){
        String[][] info={{},
            {},
            {"Main Menu","Title text and style, background (level camera, image or gradient), menu buttons and menu music.","Plus your own splash logo after \"Powered by RPG Pocket Editor\"."},
            {"Screens","Level title cards, the death screen and the victory screen.","Text, colours and timing for each one."}};
        String[] t=info[tab];float cx=vw*.5f,cy=barH+(vh-barH)*.45f;
        ui.icon(c,TAB_ICONS[tab],cx,cy-dp(56),40,Ui.TEXT_3);
        ui.text(c,t[0]+" · coming next",cx,cy-dp(10),18,Ui.TEXT,1,0);
        ui.text(c,ui.ellipsize(t[1],12.5f,0,vw-dp(60)),cx,cy+dp(16),12.5f,Ui.TEXT_2,0,0);
        ui.text(c,ui.ellipsize(t[2],12,0,vw-dp(60)),cx,cy+dp(36),12,Ui.TEXT_3,0,0);
        r.set(cx-dp(110),cy+dp(58),cx+dp(110),cy+dp(94));ui.button(c,r,Icons.GAMEPAD,"Back to HUD & Controls",Ui.NORMAL);ui.hit(r,H_TAB);
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
        panelW=Math.min(dp(320),vw-dp(24));
        panelH=collapsed?dp(40):sel>=0?dp(sel==HudLayout.JOYSTICK?320:sel==HudLayout.STATUS?198:286):dp(152);
        if(panelX<0){panelX=(vw-panelW)*.5f;panelY=barH+(vh-barH-panelH)*.42f;}
        panelX=Math.max(dp(4),Math.min(vw-panelW-dp(4),panelX));panelY=Math.max(barH+dp(4),Math.min(vh-panelH-dp(4),panelY));
        float l=panelX,t=panelY,rr=l+panelW,b=t+panelH;panel.set(l,t,rr,b);
        ui.round(c,l,t+dp(4),rr,b+dp(4),dp(12),0x90000000);ui.round(c,l,t,rr,b,dp(12),0xf21b1f25);ui.outline(c,l,t,rr,b,dp(12),Ui.BORDER,dp(1));
        // header: drag handle + title + collapse
        r.set(l,t,rr-dp(40),t+dp(40));ui.hit(r,H_HEAD);
        ui.icon(c,Icons.MOVE,l+dp(18),t+dp(20),14,Ui.TEXT_3);
        ui.textMid(c,"HUD & Controls",l+dp(34),t+dp(20),12.5f,Ui.TEXT,1,-1);
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
            downX=x;downY=y;lastY=y;moved=false;mode=0;downHit=-1;
            if(tab==1&&y>=barH&&x>=side.left){downHit=ui.hitAt(x,y);mode=downHit==H_TEXTURE?7:6;if(mode==7)setTexture(x);return true;}
            if(y<barH||tab!=0){downHit=ui.hitAt(x,y);mode=5;return true;}
            if(panel.contains(x,y)){
                downHit=ui.hitAt(x,y);
                if(downHit==H_HEAD){mode=2;grabX=x-panelX;grabY=y-panelY;}
                else if(downHit==H_SIZE){mode=3;setSlider(3,x);}
                else if(downHit==H_ALPHA){mode=4;setSlider(4,x);}
                else mode=5;
                invalidate();return true;}
            float px=(x-offX)/scale,py=(y-offY)/scale;
            int hit=elementAt(px,py);
            if(hit>=0){sel=hit;if(HudLayout.movable(hit)){mode=1;L().center(hit,w,h,ui.dp,isCaster(),pt);grabX=px-pt[0];grabY=py-pt[1];}}
            else sel=-1;
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(!moved&&Math.abs(x-downX)+Math.abs(y-downY)>dp(6))moved=true;
            if(mode==1&&moved&&sel>=0){float cx=(x-offX)/scale-grabX,cy=(y-offY)/scale-grabY;if(grid){float s=dp(8);cx=Math.round(cx/s)*s;cy=Math.round(cy/s)*s;}
                boolean wasFloating=sel==HudLayout.JOYSTICK&&!L().joyFixed;
                L().place(sel,cx,cy,w,h,ui.dp);if(wasFloating)hint("Joystick switched to a fixed position");}
            else if(mode==2){panelX=x-grabX;panelY=y-grabY;}
            else if(mode==3||mode==4)setSlider(mode,x);
            else if(mode==6&&moved){sideScroll-=y-lastY;}
            else if(mode==7)setTexture(x);
            lastY=y;invalidate();return true;
        }
        if(a==MotionEvent.ACTION_UP){
            if((mode==5||mode==6)&&!moved){int hid=ui.hitAt(x,y);if(hid==downHit){if(mode==6)themeTap(hid);else tap(hid);}}
            if(mode==7)changed();
            if(mode==1||mode==3||mode==4)changed();
            mode=0;history.commit();invalidate();return true;
        }
        if(a==MotionEvent.ACTION_CANCEL){mode=0;invalidate();}
        return true;
    }
    private boolean isCaster(){return world.hero!=null&&!world.hero.spells.isEmpty();}
    /** Topmost element under a point: buttons, joystick, then quest/messages, then the status bar. */
    private int elementAt(float x,float y){
        int[] order={HudLayout.MENU,HudLayout.BAG,HudLayout.ATTACK,HudLayout.CAST,HudLayout.JUMP,HudLayout.USE,HudLayout.POTION,HudLayout.JOYSTICK,HudLayout.QUEST,HudLayout.MESSAGE,HudLayout.MINIMAP,HudLayout.STATUS};
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
        if(hid>=H_TAB&&hid<H_TAB+TABS.length){tab=hid-H_TAB;sel=-1;return;}
        switch(hid){
            case H_BACK:if(host!=null)host.exit();return;
            case H_TOPSAVE:if(host!=null)host.saved();return;
            case H_GUNDO:case H_GREDO:undoRedo(hid==H_GUNDO);return;
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
