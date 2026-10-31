package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Map;

/**
 * PlayMaker-style FSM editor for every entity type in the project: entity list on the left,
 * state graph in the middle (action lists, event rows, global events, curved transitions),
 * inspector on the right (state, action parameters, transitions) and a searchable action browser.
 */
public class FSMGraphView extends View {
    interface Host extends UiHost {void exit();void saved();}
    static final String[] STANDARD_EVENTS={"FINISHED","DIED","DAMAGED","TOUCHED","USED","SEE","READY","NO_AMMO","FIRE","USE","ATTACK","LOST","TRUE","FALSE"};

    private static final int H_BACK=1,H_ADD=2,H_LINK=3,H_START=4,H_DELETE=5,H_FIT=6,H_RUN=7,H_SAVE=8,H_NEWDEF=9,
            H_NAME=20,H_ADD_ACTION=21,H_ADD_TR=23,H_ADD_GLOBAL=24,H_ACTION_BACK=25,H_ACT_UP=26,H_ACT_DOWN=27,H_ACT_TOGGLE=28,H_ACT_DEL=29,
            H_CANCEL_LINK=30,H_BROWSER_CLOSE=31,H_BROWSER_SEARCH=32,H_DUP_STATE=33,
            H_DEL_TR=100,H_ACTION=150,H_DEL_GLOBAL=200,H_PARAM=250,H_SEND=300,H_DEF=400,H_CAT=900,H_PICK=1000;
    private static final float NODE_W=190,TITLE_H=28,ACT_H=20,ROW_H=24,PAD=5,TAB_H=18;

    final Project project;
    EntityDef def;
    FSMGraph graph;
    private final Host host;
    private final Ui ui;
    private final Paint p,pixelPaint=new Paint();
    private final Path path=new Path();
    private final RectF r=new RectF();
    FSMGraph.Node selected,linkFrom;
    int selectedAction=-1;
    private float w,h,toolbarH,camX=-40,camY=-60,zoom=1f,inspScroll,defScroll,browserScroll;
    private boolean fitted;
    // action browser
    boolean browserOpen;private int browserCat=-1;private String browserQuery="";private final ArrayList<Actions.Spec> browserList=new ArrayList<>();
    // run simulation
    private World sim;private Actor simActor;private long lastTick;
    private final ArrayList<String> rowEvents=new ArrayList<>(),globalRows=new ArrayList<>();
    private ArrayList<String> sendEvents=new ArrayList<>();
    private final Bitmap[] thumbs=new Bitmap[Sprites.COUNT];
    // touch
    private FSMGraph.Node dragNode;private float downX,downY,lastX,lastY,pinchDist;private boolean moved,panning;private int downHit=-1,scrollZone;

    FSMGraphView(Context c,Project project,String defId,Host host){
        super(c);this.project=project;this.host=host;ui=new Ui(c);p=ui.p;pixelPaint.setFilterBitmap(false);
        EntityDef d=project.def(defId);if(d==null)d=project.playerDef();selectDef(d);
    }
    void selectDef(EntityDef d){
        stopRun();def=d;graph=d.fsm;selected=null;linkFrom=null;selectedAction=-1;inspScroll=0;browserOpen=false;fitted=false;
        if(graph.nodes.isEmpty())graph.add("Idle",0,0);
        invalidate();
    }

    // ---------------------------------------------------------------- geometry
    private float dp(float v){return ui.dp(v);}
    private float leftW(){return Math.max(dp(150),Math.min(dp(210),w*.19f));}
    private float inspectorW(){return Math.max(dp(236),Math.min(dp(320),w*.29f));}
    private float canvasL(){return leftW();}
    private float canvasR(){return w-inspectorW();}
    private float scale(){return zoom*ui.dp;}
    private float sx(float x){return canvasL()+(x-camX)*scale();}
    private float sy(float y){return toolbarH+(y-camY)*scale();}
    private float actionsH(FSMGraph.Node n){return n.actions.isEmpty()?0:n.actions.size()*ACT_H+6;}
    private float nodeH(FSMGraph.Node n){return TITLE_H+actionsH(n)+n.transitions.size()*ROW_H+(n.transitions.isEmpty()?6:PAD+2);}
    private float rowTop(FSMGraph.Node n,int i){return n.y+TITLE_H+actionsH(n)+PAD*.5f+i*ROW_H;}
    private int globalsTo(FSMGraph.Node n){int c=0;for(String id:graph.globals.values())if(id.equals(n.id))c++;return c;}
    private FSMGraph.Node nodeAt(float x,float y){
        for(int i=graph.nodes.size()-1;i>=0;i--){FSMGraph.Node n=graph.nodes.get(i);
            if(x>=sx(n.x)&&x<=sx(n.x+NODE_W)&&y>=sy(n.y-globalsTo(n)*TAB_H)&&y<=sy(n.y+nodeH(n)))return n;}
        return null;
    }
    void fit(){
        if(graph.nodes.isEmpty()||w<=0)return;
        float minX=1e9f,minY=1e9f,maxX=-1e9f,maxY=-1e9f;
        for(FSMGraph.Node n:graph.nodes){minX=Math.min(minX,n.x);minY=Math.min(minY,n.y-26-globalsTo(n)*TAB_H);maxX=Math.max(maxX,n.x+NODE_W);maxY=Math.max(maxY,n.y+nodeH(n));}
        float cw=(canvasR()-canvasL())/ui.dp,ch=(h-toolbarH)/ui.dp,margin=36;
        zoom=Math.max(.45f,Math.min(1.25f,Math.min(cw/(maxX-minX+margin*2),ch/(maxY-minY+margin*2+30))));
        camX=(minX+maxX)*.5f-cw/zoom*.5f;camY=(minY+maxY)*.5f-ch/zoom*.5f+10/zoom;
        invalidate();
    }

    // ---------------------------------------------------------------- run simulation
    boolean isRunning(){return sim!=null;}
    void startRun(){
        sim=new World(project,null);simActor=null;
        if(def.kind==EntityDef.PLAYER)simActor=sim.player;
        else{for(Actor a:sim.actors)if(a.def==def){simActor=a;break;}
            if(simActor==null){Actor pl=sim.player;simActor=sim.spawn(def.id,pl.x+(float)Math.cos(pl.angle)*2,pl.y+(float)Math.sin(pl.angle)*2,pl.angle+(float)Math.PI);
                if(simActor==null)simActor=sim.spawn(def.id,pl.x,pl.y,0);sim.tick(0.001f);}}
        if(simActor==null){sim=null;if(host!=null)host.choose("Can't run",new String[]{"Place this entity inside a sector first"},-1,i->{});return;}
        lastTick=System.nanoTime();invalidate();
    }
    void stopRun(){sim=null;simActor=null;invalidate();}
    private String activeStateId(){return simActor!=null&&simActor.fsm!=null&&simActor.fsm.state!=null?simActor.fsm.state.id:"";}

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();toolbarH=dp(48);
        if(!fitted&&w>0){fitted=true;fit();}
        if(sim!=null){long now=System.nanoTime();sim.tick(Math.min(.05f,(now-lastTick)*1e-9f));lastTick=now;if(simActor!=null&&simActor.removed&&simActor.fsm.state==null)stopRun();postInvalidateOnAnimation();}
        ui.clearHits();
        c.drawColor(0xff1b1e22);
        int save=c.save();c.clipRect(canvasL(),toolbarH,canvasR(),h);
        drawGrid(c);
        for(FSMGraph.Node n:graph.nodes)drawTransitions(c,n);
        for(FSMGraph.Node n:graph.nodes)drawNode(c,n);
        drawCanvasOverlay(c);
        c.restoreToCount(save);
        drawEntityList(c);
        drawInspector(c);
        if(browserOpen)drawBrowser(c);
        drawTopBar(c);
    }
    private void drawGrid(Canvas c){
        float s=scale(),L=canvasL(),R=canvasR(),step=20*s;if(step<dp(8))step*=5;float major=100*s,lw=Math.max(1,dp(.75f));
        float ox=L-camX*s,oy=toolbarH-camY*s;
        for(float x=L+((ox-L)%step+step)%step;x<R;x+=step)ui.rect(c,x,toolbarH,x+lw,h,0xff212429);
        for(float y=toolbarH+((oy-toolbarH)%step+step)%step;y<h;y+=step)ui.rect(c,L,y,R,y+lw,0xff212429);
        for(float x=L+((ox-L)%major+major)%major;x<R;x+=major)ui.rect(c,x,toolbarH,x+lw,h,0xff272b31);
        for(float y=toolbarH+((oy-toolbarH)%major+major)%major;y<h;y+=major)ui.rect(c,L,y,R,y+lw,0xff272b31);
    }
    private void drawTransitions(Canvas c,FSMGraph.Node n){
        int i=0;float s=scale();String active=activeStateId();
        for(Map.Entry<String,String> e:n.transitions.entrySet()){
            FSMGraph.Node d=graph.find(e.getValue());float ry=sy(rowTop(n,i)+ROW_H*.5f-1);i++;
            if(d==null)continue;
            boolean hot=n==selected||d==selected,live=active.equals(n.id);
            int col=hot?Ui.ORANGE:live?0xff6fdcaa:0xff7d8896;
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(dp(1.5f),dp(2f)*zoom));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(col);
            float nl=sx(n.x),nr=sx(n.x+NODE_W),tl=sx(d.x),tr=sx(d.x+NODE_W),ty=sy(d.y+TITLE_H*.5f);
            if(d==n){float top=sy(n.y);path.reset();path.moveTo(nr,ry);path.cubicTo(nr+40*s,ry,nr+40*s,top-30*s,nr-24*s,top-6*s);c.drawPath(path,p);arrow(c,nr-24*s,top-6*s,-.6f,.8f,col);connector(c,nr,ry,col);continue;}
            float ax,bx;int dir;
            if(tl>=nr-20*s){ax=nr;bx=tl;dir=1;}else if(tr<=nl+20*s){ax=nl;bx=tr;dir=-1;}else{ax=nr;bx=tr;dir=-2;}
            float off=Math.max(40*s,Math.abs(bx-ax)*.45f);
            path.reset();path.moveTo(ax,ry);
            if(dir==-2){float ox=Math.max(nr,tr)+50*s;path.cubicTo(ox,ry,ox,ty,bx+6*s,ty);c.drawPath(path,p);arrow(c,bx,ty,-1,0,col);}
            else{path.cubicTo(ax+off*dir,ry,bx-off*dir,ty,bx-dir*8*s,ty);c.drawPath(path,p);arrow(c,bx,ty,dir,0,col);}
            connector(c,ax,ry,col);
            p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
        }
    }
    private void connector(Canvas c,float x,float y,int col){p.setStyle(Paint.Style.FILL);p.setColor(0xff1b1e22);c.drawCircle(x,y,dp(4.5f)*Math.max(.7f,zoom),p);p.setColor(col);c.drawCircle(x,y,dp(3)*Math.max(.7f,zoom),p);p.setStyle(Paint.Style.STROKE);}
    private void arrow(Canvas c,float tipX,float tipY,float dx,float dy,int col){
        float len=dp(9)*Math.max(.75f,zoom),wd=dp(5)*Math.max(.75f,zoom),l=(float)Math.hypot(dx,dy);dx/=l;dy/=l;
        path.reset();path.moveTo(tipX,tipY);path.lineTo(tipX-dx*len-dy*wd,tipY-dy*len+dx*wd);path.lineTo(tipX-dx*len+dy*wd,tipY-dy*len-dx*wd);path.close();
        Paint.Style st=p.getStyle();p.setStyle(Paint.Style.FILL);p.setColor(col);c.drawPath(path,p);p.setStyle(st);
    }
    private void drawNode(Canvas c,FSMGraph.Node n){
        float z=zoom,l=sx(n.x),t=sy(n.y),rr=sx(n.x+NODE_W),b=sy(n.y+nodeH(n)),rad=dp(6)*z,th=TITLE_H*scale();
        boolean sel=n==selected,active=activeStateId().equals(n.id),isStart=n.id.equals(graph.start);
        if(rr<canvasL()||l>canvasR()||b<toolbarH-dp(60)||t>h+dp(40))return;
        // global event tabs (black, PlayMaker style) and START marker
        float tabY=t;
        for(Map.Entry<String,String> e:graph.globals.entrySet()){if(!e.getValue().equals(n.id))continue;
            float tt=tabY-TAB_H*scale();ui.round(c,l+dp(4)*z,tt,rr-dp(4)*z,tabY+dp(4)*z,dp(4)*z,0xff0d0f12);
            ui.icon(c,Icons.ZAP,l+dp(14)*z,tt+TAB_H*scale()*.5f,10*z,Ui.AMBER);
            ui.textMid(c,ui.ellipsize(e.getKey(),10.5f*z,1,rr-l-dp(40)*z),l+dp(24)*z,tt+TAB_H*scale()*.5f,10.5f*z,Ui.TEXT,1,-1);tabY=tt;}
        if(isStart){float pw=dp(46)*z,ph=dp(17)*z,pl=l,pt=tabY-ph-dp(5)*z;
            ui.round(c,pl,pt,pl+pw,pt+ph,ph*.5f,Ui.GREEN);ui.textMid(c,"START",pl+pw*.5f,pt+ph*.5f,9.5f*z,0xff0f2a1e,2,0);
            p.setColor(Ui.GREEN);p.setStrokeWidth(dp(1.5f)*z);c.drawLine(pl+dp(12)*z,pt+ph,pl+dp(12)*z,tabY,p);}
        ui.round(c,l,t+dp(3)*z,rr,b+dp(3)*z,rad,0x66000000);
        ui.round(c,l,t,rr,b,rad,0xff2a2f37);
        int titleCol=active?0xff2e9e6c:sel?0xff59492e:0xff3b4350;
        int save=c.save();c.clipRect(l,t,rr,t+th);ui.round(c,l,t,rr,t+th+rad,rad,titleCol);c.restoreToCount(save);
        ui.textMid(c,ui.ellipsize(n.name,12.5f*z,1,rr-l-dp(36)*z),l+dp(10)*z,t+th*.5f,12.5f*z,0xffffffff,1,-1);
        if(!n.actions.isEmpty()){String cnt=String.valueOf(n.actions.size());ui.textMid(c,cnt,rr-dp(10)*z,t+th*.5f,10*z,0xb0ffffff,1,1);}
        float y=t+th;
        if(!n.actions.isEmpty()){
            float ah=ACT_H*scale();y+=dp(3)*z;
            for(int i=0;i<n.actions.size();i++){FSMGraph.Action a=n.actions.get(i);Actions.Spec s=Actions.get(a.type);if(s==null)continue;
                boolean selA=sel&&i==selectedAction;
                if(selA)ui.round(c,l+dp(3)*z,y,rr-dp(3)*z,y+ah,dp(3)*z,0x33f0a030);
                ui.icon(c,s.icon,l+dp(14)*z,y+ah*.5f,11*z,a.enabled?Ui.TEXT_3:0xff4a525c);
                ui.textMid(c,ui.ellipsize(s.name,10.5f*z,1,rr-l-dp(30)*z),l+dp(24)*z,y+ah*.5f,10.5f*z,a.enabled?Ui.TEXT_2:0xff5a6470,1,-1);
                y+=ah;}
            y+=dp(3)*z;ui.hline(c,l,rr,y-dp(.75f),0xff232830);
        }
        int i=0;
        for(Map.Entry<String,String> e:n.transitions.entrySet()){
            float rt=sy(rowTop(n,i)),rb=rt+(ROW_H-3)*scale();
            ui.round(c,l+dp(5)*z,rt,rr-dp(5)*z,rb,dp(4)*z,graph.find(e.getValue())==null?0xff4a2c2c:0xff353c46);
            ui.textMid(c,ui.ellipsize(e.getKey(),11.5f*z,1,rr-l-dp(30)*z),l+dp(12)*z,(rt+rb)*.5f,11.5f*z,Ui.TEXT,1,-1);i++;}
        if(active&&simActor!=null){float f=Math.min(1,simActor.fsm.stateTime%1f);ui.rect(c,l,t+th-dp(2.5f)*z,l+(rr-l)*f,t+th,0x88a8f0cc);}
        if(sel||active||n==linkFrom)ui.outline(c,l,t,rr,b,rad,active?0xff6fdcaa:Ui.ORANGE,dp(2));
        else ui.outline(c,l,t,rr,b,rad,0x22ffffff,dp(1));
    }
    private void drawCanvasOverlay(Canvas c){
        float L=canvasL(),R=canvasR();
        if(linkFrom!=null){
            String t="Tap the target state for a transition from "+linkFrom.name;
            float tw=Math.min(R-L-dp(20),ui.measure(t,12f,1)+dp(120)),cx=(L+R)*.5f,top=toolbarH+dp(12);
            ui.pill(c,cx-tw*.5f,top,cx+tw*.5f,top+dp(36),0xf2262c34);ui.icon(c,Icons.LINK,cx-tw*.5f+dp(20),top+dp(18),16,Ui.ORANGE);
            ui.textMid(c,ui.ellipsize(t,12f,1,tw-dp(120)),cx-tw*.5f+dp(36),top+dp(18),12f,Ui.TEXT,1,-1);
            r.set(cx+tw*.5f-dp(76),top+dp(5),cx+tw*.5f-dp(6),top+dp(31));ui.button(c,r,-1,"Cancel",Ui.NORMAL);ui.hit(r,H_CANCEL_LINK);
        }
        if(sim!=null){
            FSMGraph.Node a=graph.find(activeStateId());sendEvents=graph.events();
            float bh=dp(40),bt=h-bh-dp(12),x=L+dp(12);
            String label=a==null?(simActor!=null&&simActor.removed?"Removed":"Stopped"):a.name;
            float lw=ui.measure(label,12.5f,1)+dp(70),cx=x+lw,maxR=R-dp(12);
            float chipsW=0;for(String e:sendEvents)chipsW+=ui.measure(e,11.5f,1)+dp(52);
            ui.pill(c,x,bt,Math.min(maxR,x+lw+chipsW+dp(8)),bt+bh,0xf2262c34);
            p.setColor(a==null?Ui.TEXT_3:Ui.GREEN);c.drawCircle(x+dp(18),bt+bh*.5f,dp(4.5f),p);
            ui.text(c,"ACTIVE",x+dp(30),bt+dp(16),9,Ui.TEXT_3,1,-1);ui.text(c,label,x+dp(30),bt+dp(31),12.5f,Ui.TEXT,1,-1);
            for(int i=0;i<sendEvents.size();i++){String e=sendEvents.get(i);float ew=ui.measure(e,11.5f,1)+dp(46);if(cx+ew>maxR)break;
                r.set(cx,bt+dp(6),cx+ew,bt+bh-dp(6));ui.button(c,r,Icons.ZAP,e,Ui.NORMAL);ui.hit(r,H_SEND+i);cx+=ew+dp(6);}
        }else ui.textMid(c,Math.round(zoom*100)+"%",R-dp(12),h-dp(14),11,Ui.TEXT_3,1,1);
    }
    private Bitmap thumb(int s){int i=Math.max(0,Math.min(Sprites.COUNT-1,s));if(thumbs[i]==null)thumbs[i]=Sprites.preview(i);return thumbs[i];}
    private void drawEntityList(Canvas c){
        float pw=leftW(),pad=dp(10);
        ui.rect(c,0,toolbarH,pw,h,Ui.SURFACE);ui.vline(c,pw-dp(1),toolbarH,h,Ui.BORDER);
        float ht=toolbarH+dp(10);ui.caption(c,"Entities",pad,ht+dp(17),Ui.TEXT_3);
        r.set(pw-pad-dp(30),ht+dp(2),pw-pad,ht+dp(30));ui.button(c,r,Icons.PLUS,null,Ui.PRIMARY);ui.hit(r,H_NEWDEF);
        float top=ht+dp(40),bottom=h,rowH=dp(44);
        float maxScroll=Math.max(0,project.defs.size()*rowH-(bottom-top)+dp(8));defScroll=Math.max(0,Math.min(maxScroll,defScroll));
        int save=c.save();c.clipRect(0,top,pw-dp(1),bottom);
        for(int i=0;i<project.defs.size();i++){EntityDef d=project.defs.get(i);float y=top+i*rowH-defScroll;if(y+rowH<top||y>bottom)continue;
            boolean sel=d==def;
            if(sel){ui.round(c,dp(6),y+dp(2),pw-dp(6),y+rowH-dp(2),dp(8),Ui.ACCENT_SOFT);ui.outline(c,dp(6),y+dp(2),pw-dp(6),y+rowH-dp(2),dp(8),0x993d8bfd,dp(1));}
            Bitmap b=thumb(d.sprite);float th=dp(26),tw=th*b.getWidth()/b.getHeight();if(tw>dp(28)){tw=dp(28);th=tw*b.getHeight()/b.getWidth();}
            r.set(dp(26)-tw*.5f,y+rowH*.5f-th*.5f,dp(26)+tw*.5f,y+rowH*.5f+th*.5f);c.drawBitmap(b,null,r,pixelPaint);
            ui.text(c,ui.ellipsize(d.name,12,1,pw-dp(56)),dp(46),y+dp(19),12,Ui.TEXT,1,-1);
            ui.text(c,d.fsm.nodes.size()+" states · "+EntityDef.KIND_NAMES[d.kind],dp(46),y+dp(34),10,Ui.TEXT_3);
            float vt=Math.max(top,y),vb=Math.min(bottom,y+rowH);if(vb>vt)ui.hit(0,vt,pw,vb,H_DEF+i);}
        c.restoreToCount(save);
    }
    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,toolbarH,Ui.SURFACE);ui.hline(c,0,w,toolbarH-dp(1),Ui.BORDER);
        float cy=toolbarH*.5f,bh=dp(32),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        ui.textMid(c,"FSM",dp(48),cy,15,Ui.TEXT_3,1,-1);
        float tx=dp(48)+ui.measure("FSM",15,1)+dp(8);
        String title=ui.ellipsize(def.name,15,1,dp(160));ui.textMid(c,title,tx,cy,15,Ui.TEXT,1,-1);tx+=ui.measure(title,15,1)+dp(10);
        String count=graph.nodes.size()+(graph.nodes.size()==1?" state":" states");float cw=ui.measure(count,11,1)+dp(16);
        if(w>dp(820)){ui.round(c,tx,cy-dp(10),tx+cw,cy+dp(10),dp(10),Ui.SURFACE_2);ui.textMid(c,count,tx+cw*.5f,cy,11,Ui.TEXT_2,1,0);}
        float x=w-dp(10);boolean wide=w>dp(900);
        float sw=wide?dp(82):bh;r.set(x-sw,top,x,top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);x-=sw+dp(8);
        float rw=wide?dp(78):bh;r.set(x-rw,top,x,top+bh);ui.button(c,r,sim!=null?Icons.STOP:Icons.PLAY,wide?(sim!=null?"Stop":"Run"):null,sim!=null?Ui.SUCCESS:Ui.NORMAL);ui.hit(r,H_RUN);x-=rw+dp(8);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.FIT,null,Ui.NORMAL);ui.hit(r,H_FIT);x-=bh+dp(14);
        ui.vline(c,x+dp(6),top+dp(4),top+bh-dp(4),Ui.BORDER);
        boolean has=selected!=null,labels=w>dp(1000);
        Object[][] items={{H_DELETE,Icons.TRASH,"Delete",has?Ui.DANGER:Ui.DISABLED},{H_START,Icons.FLAG,"Set Start",has?Ui.NORMAL:Ui.DISABLED},
                {H_LINK,Icons.LINK,"Transition",has?(linkFrom!=null?Ui.ACTIVE:Ui.NORMAL):Ui.DISABLED},{H_ADD,Icons.PLUS_SQUARE,"Add State",Ui.NORMAL}};
        for(Object[] it:items){String label=(String)it[2];float bw=labels?ui.measure(label,12.5f,1)+dp(46):bh;x-=dp(8);
            r.set(x-bw,top,x,top+bh);ui.button(c,r,(Integer)it[1],labels?label:null,(Integer)it[3]);ui.hit(r,(Integer)it[0]);x-=bw;}
    }

    // ---------------------------------------------------------------- inspector
    private float iy;private float iTop,iBottom;
    private void ihit(RectF rr,int id){float t=Math.max(iTop,rr.top),b=Math.min(iBottom,rr.bottom);if(b>t)ui.hit(rr.left,t,rr.right,b,id);}
    private void field(Canvas c,float l,float rr,String value,String placeholder,int icon,int hit){
        r.set(l,iy,rr,iy+dp(36));ui.round(c,r,dp(6),Ui.BG);ui.outline(c,r,dp(6),Ui.BORDER,dp(1));
        boolean empty=value==null||value.trim().isEmpty();
        ui.textMid(c,ui.ellipsize(empty?placeholder:value,12.5f,empty?0:1,rr-l-dp(44)),l+dp(10),iy+dp(18),12.5f,empty?Ui.TEXT_3:Ui.TEXT,empty?0:1,-1);
        ui.icon(c,icon,rr-dp(16),iy+dp(18),14,Ui.TEXT_3);ihit(r,hit);iy+=dp(44);
    }
    private void caption(Canvas c,float l,String s){ui.caption(c,s,l,iy+dp(10),Ui.TEXT_3);iy+=dp(18);}
    private ArrayList<String> wrap(String text,float size,int weight,float max){
        ArrayList<String> out=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(String word:text.split(" ")){String t=line.length()==0?word:line+" "+word;if(ui.measure(t,size,weight)>max&&line.length()>0){out.add(line.toString());line=new StringBuilder(word);}else{line=new StringBuilder(t);}}
        if(line.length()>0)out.add(line.toString());return out;
    }
    private void drawInspector(Canvas c){
        float x0=canvasR(),pad=dp(12),l=x0+pad,rr=w-pad;
        ui.rect(c,x0,toolbarH,w,h,Ui.SURFACE);ui.vline(c,x0,toolbarH,h,Ui.BORDER);
        iTop=toolbarH;iBottom=h;
        int save=c.save();c.clipRect(x0+dp(1),iTop,w,iBottom);
        iy=toolbarH+dp(14)-inspScroll;
        if(selected!=null&&selectedAction>=0&&selectedAction<selected.actions.size())drawActionEditor(c,l,rr);
        else if(selected!=null)drawStateInspector(c,l,rr);
        else drawMachineInspector(c,l,rr);
        float content=iy+inspScroll-toolbarH+dp(16);
        c.restoreToCount(save);
        float maxScroll=Math.max(0,content-(h-toolbarH));inspScroll=Math.max(0,Math.min(maxScroll,inspScroll));
        if(maxScroll>0){float vh=h-toolbarH,bar=vh*vh/(vh+maxScroll),by=toolbarH+(vh-bar)*inspScroll/maxScroll;ui.round(c,w-dp(5),by,w-dp(2),by+bar,dp(2),0x33ffffff);}
    }
    private void drawMachineInspector(Canvas c,float l,float rr){
        Bitmap b=thumb(def.sprite);ui.round(c,l,iy,l+dp(48),iy+dp(48),dp(8),Ui.BG);
        float th=dp(40),tw=th*b.getWidth()/b.getHeight();if(tw>dp(42)){tw=dp(42);th=tw*b.getHeight()/b.getWidth();}
        r.set(l+dp(24)-tw*.5f,iy+dp(24)-th*.5f,l+dp(24)+tw*.5f,iy+dp(24)+th*.5f);c.drawBitmap(b,null,r,pixelPaint);
        ui.text(c,ui.ellipsize(def.name,14,1,rr-l-dp(58)),l+dp(58),iy+dp(20),14,Ui.TEXT,1,-1);ui.text(c,EntityDef.KIND_NAMES[def.kind]+" FSM",l+dp(58),iy+dp(38),11,Ui.TEXT_3);
        iy+=dp(60);
        float cw=(rr-l)/2f;ui.round(c,l,iy,rr,iy+dp(56),dp(8),Ui.SURFACE_2);
        int acts=0;for(FSMGraph.Node n:graph.nodes)acts+=n.actions.size();
        ui.text(c,String.valueOf(graph.nodes.size()),l+dp(12),iy+dp(26),17,Ui.TEXT,1,-1);ui.text(c,"States",l+dp(12),iy+dp(44),10.5f,Ui.TEXT_3);
        ui.text(c,String.valueOf(acts),l+cw+dp(12),iy+dp(26),17,Ui.TEXT,1,-1);ui.text(c,"Actions",l+cw+dp(12),iy+dp(44),10.5f,Ui.TEXT_3);
        iy+=dp(66);
        caption(c,l,"Start state");FSMGraph.Node start=graph.find(graph.start);
        ui.round(c,l,iy,rr,iy+dp(34),dp(6),Ui.SURFACE_2);ui.icon(c,Icons.FLAG,l+dp(16),iy+dp(17),14,Ui.GREEN);ui.textMid(c,start==null?"None":start.name,l+dp(32),iy+dp(17),12.5f,Ui.TEXT,1,-1);iy+=dp(44);
        caption(c,l,"Global events");
        if(graph.globals.isEmpty()){ui.text(c,"None. Select a state to add one.",l,iy+dp(10),11.5f,Ui.TEXT_3);iy+=dp(22);}
        for(Map.Entry<String,String> e:graph.globals.entrySet()){FSMGraph.Node t=graph.find(e.getValue());ui.icon(c,Icons.ZAP,l+dp(8),iy+dp(10),12,Ui.AMBER);ui.text(c,e.getKey()+"  →  "+(t==null?"?":t.name),l+dp(20),iy+dp(14),11.5f,Ui.TEXT_2,1,-1);iy+=dp(22);}
        iy+=dp(8);caption(c,l,"How it works");
        String help="Each state runs its actions together, top to bottom, every frame. When every action has finished the state sends FINISHED. Events move to the state they are linked to; global events jump from anywhere. The engine sends DIED, DAMAGED, TOUCHED and USED to entities.";
        for(String line:wrap(help,11.5f,0,rr-l)){ui.text(c,line,l,iy+dp(12),11.5f,Ui.TEXT_3);iy+=dp(16);}
    }
    private void drawStateInspector(Canvas c,float l,float rr){
        FSMGraph.Node n=selected;
        ui.caption(c,"State",l,iy+dp(10),Ui.TEXT_3);
        if(n.id.equals(graph.start)){float bw=dp(46);ui.round(c,rr-bw,iy,rr,iy+dp(15),dp(8),0x333ecf8e);ui.textMid(c,"START",rr-bw*.5f,iy+dp(7.5f),9,Ui.GREEN,2,0);}
        iy+=dp(18);field(c,l,rr,n.name,"State name",Icons.PEN,H_NAME);
        float half=(rr-l-dp(6))/2f;
        r.set(l,iy,l+half,iy+dp(30));ui.button(c,r,Icons.FLAG,"Set start",n.id.equals(graph.start)?Ui.DISABLED:Ui.NORMAL);ihit(r,H_START);
        r.set(l+half+dp(6),iy,rr,iy+dp(30));ui.button(c,r,Icons.COPY,"Duplicate",Ui.NORMAL);ihit(r,H_DUP_STATE);iy+=dp(40);
        caption(c,l,"Actions · run together each frame");
        for(int i=0;i<n.actions.size();i++){FSMGraph.Action a=n.actions.get(i);Actions.Spec s=Actions.get(a.type);if(s==null)continue;
            r.set(l,iy,rr,iy+dp(42));ui.round(c,r,dp(7),Ui.SURFACE_2);ui.outline(c,r,dp(7),0x14ffffff,dp(1));
            ui.icon(c,s.icon,l+dp(17),iy+dp(21),15,a.enabled?0xff8fbcff:Ui.TEXT_3);
            ui.text(c,ui.ellipsize(s.name,12.5f,1,rr-l-dp(60)),l+dp(34),iy+dp(18),12.5f,a.enabled?Ui.TEXT:Ui.TEXT_3,1,-1);
            ui.text(c,ui.ellipsize(summary(a,s),10.5f,0,rr-l-dp(60)),l+dp(34),iy+dp(33),10.5f,Ui.TEXT_3);
            ui.icon(c,Icons.CHEVRON_RIGHT,rr-dp(14),iy+dp(21),13,Ui.TEXT_3);ihit(r,H_ACTION+i);iy+=dp(48);}
        if(n.actions.isEmpty()){ui.text(c,"No actions. This state just waits for events.",l,iy+dp(10),11.5f,Ui.TEXT_3);iy+=dp(22);}
        r.set(l,iy,rr,iy+dp(34));ui.button(c,r,Icons.PLUS,"Add Action",Ui.PRIMARY);ihit(r,H_ADD_ACTION);iy+=dp(46);
        caption(c,l,"Transitions");
        rowEvents.clear();int i=0;
        for(Map.Entry<String,String> e:n.transitions.entrySet()){FSMGraph.Node d=graph.find(e.getValue());rowEvents.add(e.getKey());
            transitionRow(c,l,rr,e.getKey(),d==null?"(missing)":d.name,H_DEL_TR+i);i++;}
        if(n.transitions.isEmpty()){ui.text(c,"No transitions yet.",l,iy+dp(10),11.5f,Ui.TEXT_3);iy+=dp(22);}
        r.set(l,iy,rr,iy+dp(32));ui.button(c,r,Icons.LINK,linkFrom==n?"Tap a target state…":"Add Transition",linkFrom==n?Ui.ACTIVE:Ui.NORMAL);ihit(r,H_ADD_TR);iy+=dp(44);
        caption(c,l,"Global events into this state");
        globalRows.clear();int g=0;
        for(Map.Entry<String,String> e:graph.globals.entrySet()){if(!e.getValue().equals(n.id))continue;globalRows.add(e.getKey());transitionRow(c,l,rr,e.getKey(),"any state",H_DEL_GLOBAL+g);g++;}
        r.set(l,iy,rr,iy+dp(32));ui.button(c,r,Icons.ZAP,"Add Global Event",Ui.NORMAL);ihit(r,H_ADD_GLOBAL);iy+=dp(40);
    }
    private void transitionRow(Canvas c,float l,float rr,String event,String target,int delId){
        ui.round(c,l,iy,rr,iy+dp(32),dp(6),Ui.SURFACE_2);ui.icon(c,Icons.ZAP,l+dp(14),iy+dp(16),13,Ui.AMBER);
        float ew=Math.min(ui.measure(event,12,1),(rr-l)*.42f);
        ui.textMid(c,ui.ellipsize(event,12,1,(rr-l)*.42f),l+dp(26),iy+dp(16),12,Ui.TEXT,1,-1);
        ui.icon(c,Icons.CHEVRON_RIGHT,l+dp(34)+ew,iy+dp(16),12,Ui.TEXT_3);
        ui.textMid(c,ui.ellipsize(target,12,0,Math.max(dp(20),rr-l-ew-dp(80))),l+dp(44)+ew,iy+dp(16),12,Ui.TEXT_2,0,-1);
        r.set(rr-dp(30),iy+dp(2),rr-dp(2),iy+dp(30));ui.icon(c,Icons.CANCEL,r.centerX(),r.centerY(),14,Ui.TEXT_3);ihit(r,delId);iy+=dp(36);
    }
    private String summary(FSMGraph.Action a,Actions.Spec s){
        StringBuilder b=new StringBuilder();
        for(Actions.Param pm:s.params){String v=a.params.get(pm.key);if(v==null||v.isEmpty())continue;if(pm.type==Actions.T_BOOL&&!v.equals("true"))continue;
            if(pm.type==Actions.T_FLOAT&&v.equals("0")&&b.length()>0)continue;
            if(b.length()>0)b.append(" · ");
            if(pm.type==Actions.T_BOOL)b.append(pm.label);else if(pm.type==Actions.T_EVENT)b.append("→ ").append(v);
            else if(pm.type==Actions.T_CHOICE||pm.type==Actions.T_ENTITY||pm.type==Actions.T_MATERIAL||pm.type==Actions.T_SECTOR||pm.type==Actions.T_TEXT)b.append(display(pm,v));
            else b.append(pm.label.toLowerCase()).append(' ').append(display(pm,v));
            if(b.length()>60)break;}
        return b.length()==0?s.category:b.toString();
    }
    private String display(Actions.Param pm,String v){
        switch(pm.type){
            case Actions.T_ENTITY:{EntityDef d=project.def(v);return v.isEmpty()?"any":d==null?v+"?":d.name;}
            case Actions.T_MATERIAL:try{return Textures.NAMES[Math.max(0,Math.min(Textures.COUNT-1,Integer.parseInt(v)))];}catch(NumberFormatException e){return v;}
            case Actions.T_SECTOR:try{return "Sector "+(Integer.parseInt(v)+1);}catch(NumberFormatException e){return v;}
            case Actions.T_EVENT:return v.isEmpty()?"—":v;
            default:return v;
        }
    }
    private void drawActionEditor(Canvas c,float l,float rr){
        FSMGraph.Action a=selected.actions.get(selectedAction);Actions.Spec s=Actions.get(a.type);
        r.set(l-dp(4),iy,rr,iy+dp(30));ui.icon(c,Icons.CHEVRON_LEFT,l+dp(8),iy+dp(15),14,Ui.TEXT_2);ui.textMid(c,ui.ellipsize(selected.name,12,1,rr-l-dp(30)),l+dp(22),iy+dp(15),12,Ui.TEXT_2,1,-1);ihit(r,H_ACTION_BACK);iy+=dp(38);
        ui.round(c,l,iy,l+dp(36),iy+dp(36),dp(8),Ui.ACCENT_SOFT);ui.icon(c,s.icon,l+dp(18),iy+dp(18),18,0xff8fbcff);
        ui.text(c,ui.ellipsize(s.name,15,1,rr-l-dp(46)),l+dp(46),iy+dp(16),15,Ui.TEXT,1,-1);ui.text(c,s.category+" action",l+dp(46),iy+dp(32),10.5f,Ui.TEXT_3);
        iy+=dp(46);
        for(String line:wrap(s.desc,11.5f,0,rr-l)){ui.text(c,line,l,iy+dp(12),11.5f,Ui.TEXT_2);iy+=dp(16);}
        iy+=dp(10);
        if(s.params.length>0)caption(c,l,"Parameters");
        for(int i=0;i<s.params.length;i++){Actions.Param pm=s.params[i];String v=a.params.get(pm.key);if(v==null)v="";
            r.set(l,iy,rr,iy+dp(46));ui.round(c,r,dp(6),Ui.SURFACE_2);
            ui.text(c,pm.label,l+dp(10),iy+dp(17),10.5f,Ui.TEXT_3,1,-1);
            if(pm.type==Actions.T_BOOL){boolean on=v.equals("true");float tx=rr-dp(44),ty=iy+dp(15);ui.round(c,tx,ty,tx+dp(32),ty+dp(16),dp(8),on?Ui.ACCENT:Ui.SURFACE_3);p.setColor(0xffffffff);c.drawCircle(on?tx+dp(24):tx+dp(8),ty+dp(8),dp(6),p);
                ui.text(c,on?"Yes":"No",l+dp(10),iy+dp(36),12.5f,Ui.TEXT,1,-1);}
            else{String shown=display(pm,v);boolean empty=v.isEmpty();ui.text(c,ui.ellipsize(empty?(pm.type==Actions.T_EVENT?"No event":"Not set"):shown,12.5f,1,rr-l-dp(44)),l+dp(10),iy+dp(36),12.5f,empty?Ui.TEXT_3:pm.type==Actions.T_EVENT?Ui.AMBER:Ui.TEXT,1,-1);
                ui.icon(c,pm.type==Actions.T_CHOICE||pm.type==Actions.T_EVENT||pm.type==Actions.T_ENTITY||pm.type==Actions.T_MATERIAL||pm.type==Actions.T_VAR?Icons.CHEVRON_DOWN:Icons.PEN,rr-dp(16),iy+dp(23),13,Ui.TEXT_3);}
            ihit(r,H_PARAM+i);iy+=dp(52);}
        iy+=dp(4);
        float half=(rr-l-dp(6))/2f;
        r.set(l,iy,l+half,iy+dp(32));ui.button(c,r,Icons.ARROW_UP,"Up",selectedAction>0?Ui.NORMAL:Ui.DISABLED);ihit(r,H_ACT_UP);
        r.set(l+half+dp(6),iy,rr,iy+dp(32));ui.button(c,r,Icons.ARROW_DOWN,"Down",selectedAction<selected.actions.size()-1?Ui.NORMAL:Ui.DISABLED);ihit(r,H_ACT_DOWN);iy+=dp(38);
        r.set(l,iy,l+half,iy+dp(32));ui.button(c,r,a.enabled?Icons.STOP:Icons.PLAY,a.enabled?"Disable":"Enable",Ui.NORMAL);ihit(r,H_ACT_TOGGLE);
        r.set(l+half+dp(6),iy,rr,iy+dp(32));ui.button(c,r,Icons.TRASH,"Remove",Ui.DANGER);ihit(r,H_ACT_DEL);iy+=dp(42);
    }

    // ---------------------------------------------------------------- action browser
    private void refreshBrowser(){
        browserList.clear();
        if(!browserQuery.isEmpty())browserList.addAll(Actions.search(browserQuery));
        else browserList.addAll(Actions.inCategory(browserCat<0?null:Actions.CATEGORIES[browserCat]));
    }
    private void drawBrowser(Canvas c){
        float L=canvasL()+dp(10),R=w-dp(10),T=toolbarH+dp(10),B=h-dp(10);
        ui.rect(c,canvasL(),toolbarH,w,h,0x99000000);
        ui.round(c,L,T+dp(3),R,B+dp(3),dp(12),0x66000000);ui.panel(c,L,T,R,B,dp(12));
        // header
        ui.text(c,"Add Action",L+dp(16),T+dp(28),15,Ui.TEXT,1,-1);
        ui.text(c,"to "+(selected==null?"":selected.name),L+dp(16)+ui.measure("Add Action",15,1)+dp(8),T+dp(28),12,Ui.TEXT_3);
        r.set(R-dp(44),T+dp(8),R-dp(8),T+dp(40));ui.button(c,r,Icons.CANCEL,null,Ui.GHOST);ui.hit(r,H_BROWSER_CLOSE);
        float sw=Math.min(dp(260),(R-L)*.4f);r.set(R-dp(52)-sw,T+dp(8),R-dp(52),T+dp(40));ui.round(c,r,dp(8),Ui.BG);ui.outline(c,r,dp(8),Ui.BORDER,dp(1));
        ui.icon(c,Icons.SEARCH,r.left+dp(16),r.centerY(),14,Ui.TEXT_3);ui.textMid(c,browserQuery.isEmpty()?"Search "+Actions.ALL.size()+" actions":browserQuery,r.left+dp(30),r.centerY(),12.5f,browserQuery.isEmpty()?Ui.TEXT_3:Ui.TEXT,browserQuery.isEmpty()?0:1,-1);ui.hit(r,H_BROWSER_SEARCH);
        ui.hline(c,L,R,T+dp(48),Ui.BORDER);
        // categories
        float cw=Math.max(dp(120),Math.min(dp(170),(R-L)*.26f)),ct=T+dp(56),rowH=dp(30);
        for(int i=-1;i<Actions.CATEGORIES.length;i++){float y=ct+(i+1)*rowH;if(y+rowH>B)break;
            boolean on=browserQuery.isEmpty()&&browserCat==i;r.set(L+dp(8),y,L+cw,y+rowH-dp(3));
            if(on)ui.round(c,r,dp(6),Ui.ACCENT_SOFT);
            String name=i<0?"All actions":Actions.CATEGORIES[i];int count=i<0?Actions.ALL.size():Actions.inCategory(name).size();
            ui.icon(c,i<0?Icons.LIST:Actions.CATEGORY_ICONS[i],r.left+dp(14),r.centerY(),13,on?0xff8fbcff:Ui.TEXT_3);
            ui.textMid(c,name,r.left+dp(28),r.centerY(),12,on?Ui.TEXT:Ui.TEXT_2,1,-1);ui.textMid(c,String.valueOf(count),r.right-dp(8),r.centerY(),10.5f,Ui.TEXT_3,1,1);
            ui.hit(r,H_CAT+i+1);}
        ui.vline(c,L+cw+dp(8),T+dp(48),B,Ui.BORDER);
        // list
        float lx=L+cw+dp(18),lr=R-dp(12),lt=T+dp(56),cardH=dp(52);
        int cols=lr-lx>dp(460)?2:1;float colW=(lr-lx-(cols-1)*dp(8))/cols;
        int rows=(browserList.size()+cols-1)/cols;float maxScroll=Math.max(0,rows*(cardH+dp(6))-(B-lt)+dp(6));browserScroll=Math.max(0,Math.min(maxScroll,browserScroll));
        int save=c.save();c.clipRect(lx-dp(2),lt,lr+dp(2),B-dp(2));
        for(int i=0;i<browserList.size();i++){Actions.Spec s=browserList.get(i);int col=i%cols,row=i/cols;
            float x=lx+col*(colW+dp(8)),y=lt+row*(cardH+dp(6))-browserScroll;if(y+cardH<lt||y>B)continue;
            r.set(x,y,x+colW,y+cardH);ui.round(c,r,dp(8),Ui.SURFACE_2);ui.outline(c,r,dp(8),0x14ffffff,dp(1));
            ui.round(c,x+dp(8),y+dp(10),x+dp(40),y+dp(42),dp(7),Ui.BG);ui.icon(c,s.icon,x+dp(24),y+dp(26),16,0xff8fbcff);
            ui.text(c,ui.ellipsize(s.name,12.5f,1,colW-dp(110)),x+dp(50),y+dp(22),12.5f,Ui.TEXT,1,-1);
            ui.textMid(c,s.category,x+colW-dp(10),y+dp(17),9.5f,Ui.TEXT_3,1,1);
            ui.text(c,ui.ellipsize(s.desc,10.5f,0,colW-dp(60)),x+dp(50),y+dp(39),10.5f,Ui.TEXT_3);
            float vt=Math.max(lt,y),vb=Math.min(B,y+cardH);if(vb>vt)ui.hit(x,vt,x+colW,vb,H_PICK+i);}
        if(browserList.isEmpty())ui.text(c,"No actions match \""+browserQuery+"\"",lx,lt+dp(24),12.5f,Ui.TEXT_3);
        c.restoreToCount(save);
    }

    // ---------------------------------------------------------------- touch
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){
            downX=lastX=x;downY=lastY=y;moved=false;panning=false;dragNode=null;pinchDist=0;scrollZone=0;
            downHit=ui.hitAt(x,y);
            if(browserOpen){scrollZone=4;return true;}
            if(x<canvasL()&&y>toolbarH){scrollZone=1;return true;}
            if(x>=canvasR()&&y>toolbarH){scrollZone=2;return true;}
            if(downHit>=0||y<toolbarH)return true;
            FSMGraph.Node n=nodeAt(x,y);
            if(linkFrom!=null){if(n!=null){FSMGraph.Node from=linkFrom;linkFrom=null;chooseEvent(from,n);}invalidate();return true;}
            if(n!=null){if(selected!=n)selectedAction=-1;selected=n;dragNode=n;graph.nodes.remove(n);graph.nodes.add(n);inspScroll=Math.min(inspScroll,0);}else panning=true;
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2&&(panning||dragNode!=null)){
            pinchDist=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;dragNode=null;panning=false;moved=true;return true;}
        if(a==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>=2&&pinchDist>0){
                float d=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1)),mx=(e.getX(0)+e.getX(1))*.5f,my=(e.getY(0)+e.getY(1))*.5f;
                float wx=camX+(mx-canvasL())/scale(),wy=camY+(my-toolbarH)/scale();
                zoom=Math.max(.35f,Math.min(2.2f,zoom*d/pinchDist));pinchDist=d;
                camX=wx-(mx-canvasL())/scale();camY=wy-(my-toolbarH)/scale();camX-=(mx-lastX)/scale();camY-=(my-lastY)/scale();lastX=mx;lastY=my;invalidate();return true;}
            if(Math.abs(x-downX)+Math.abs(y-downY)>dp(7))moved=true;
            if(moved){
                if(scrollZone==1)defScroll-=y-lastY;else if(scrollZone==2)inspScroll-=y-lastY;else if(scrollZone==4)browserScroll-=y-lastY;
                else if(dragNode!=null){dragNode.x+=(x-lastX)/scale();dragNode.y+=(y-lastY)/scale();}
                else if(panning){camX-=(x-lastX)/scale();camY-=(y-lastY)/scale();}
                invalidate();}
            lastX=x;lastY=y;return true;
        }
        if(a==MotionEvent.ACTION_UP){
            if(!moved&&downHit>=0&&ui.hitAt(x,y)==downHit)action(downHit);
            else if(!moved&&browserOpen&&downHit<0){float L=canvasL()+dp(10),T=toolbarH+dp(10);if(x<L||y<T||x>w-dp(10)||y>h-dp(10))browserOpen=false;}
            else if(dragNode!=null&&moved){dragNode.x=Math.round(dragNode.x/8f)*8f;dragNode.y=Math.round(dragNode.y/8f)*8f;}
            else if(panning&&!moved){selected=null;selectedAction=-1;}
            dragNode=null;panning=false;downHit=-1;scrollZone=0;invalidate();return true;
        }
        if(a==MotionEvent.ACTION_CANCEL){dragNode=null;panning=false;downHit=-1;scrollZone=0;return true;}
        return true;
    }
    private void action(int id){
        if(id>=H_PICK){int i=id-H_PICK;if(i<browserList.size()&&selected!=null){selected.actions.add(new FSMGraph.Action(browserList.get(i).id));selectedAction=selected.actions.size()-1;browserOpen=false;inspScroll=0;}return;}
        if(id>=H_CAT){browserCat=id-H_CAT-1;browserQuery="";browserScroll=0;refreshBrowser();return;}
        if(id>=H_DEF){int i=id-H_DEF;if(i<project.defs.size())selectDef(project.defs.get(i));return;}
        if(id>=H_SEND){int i=id-H_SEND;if(i<sendEvents.size()&&simActor!=null&&simActor.fsm!=null)simActor.fsm.send(sendEvents.get(i));return;}
        if(id>=H_PARAM){editParam(id-H_PARAM);return;}
        if(id>=H_DEL_GLOBAL){int i=id-H_DEL_GLOBAL;if(i<globalRows.size())graph.globals.remove(globalRows.get(i));return;}
        if(id>=H_ACTION){int i=id-H_ACTION;if(selected!=null&&i<selected.actions.size()){selectedAction=i;inspScroll=0;}return;}
        if(id>=H_DEL_TR){int i=id-H_DEL_TR;if(selected!=null&&i<rowEvents.size())selected.transitions.remove(rowEvents.get(i));return;}
        switch(id){
            case H_BACK:if(host!=null)host.exit();break;
            case H_SAVE:if(host!=null)host.saved();break;
            case H_RUN:if(sim!=null)stopRun();else startRun();break;
            case H_FIT:fit();break;
            case H_CANCEL_LINK:linkFrom=null;break;
            case H_NEWDEF:newEntity();break;
            case H_ADD:{float cx=camX+(canvasR()-canvasL())*.5f/scale()-NODE_W*.5f,cy=camY+(h-toolbarH)*.4f/scale();
                for(int k=0;k<20&&nodeAtWorld(cx,cy);k++){cx+=24;cy+=24;}
                selected=graph.add("State "+(graph.nodes.size()+1),Math.round(cx/8f)*8f,Math.round(cy/8f)*8f);selectedAction=-1;break;}
            case H_LINK:case H_ADD_TR:if(selected!=null)linkFrom=linkFrom==selected?null:selected;break;
            case H_START:if(selected!=null)graph.start=selected.id;break;
            case H_DUP_STATE:if(selected!=null){FSMGraph.Node n=graph.add(selected.name+" copy",selected.x+24,selected.y+24);for(FSMGraph.Action a:selected.actions)n.actions.add(a.copy());n.transitions.putAll(selected.transitions);selected=n;selectedAction=-1;}break;
            case H_DELETE:if(selected!=null){FSMGraph.Node node=selected;Runnable go=()->{graph.remove(node);if(linkFrom==node)linkFrom=null;selected=null;selectedAction=-1;invalidate();};
                if(host!=null&&!node.actions.isEmpty())host.confirm("Delete state "+node.name+"?","Its "+node.actions.size()+" action(s) and transitions will be removed.","Delete",go);else go.run();}break;
            case H_NAME:if(selected!=null&&host!=null){FSMGraph.Node n=selected;host.prompt("Rename state",null,n.name,false,t->{if(!t.trim().isEmpty())n.name=t.trim();});}break;
            case H_ADD_ACTION:if(selected!=null){browserOpen=true;browserScroll=0;refreshBrowser();}break;
            case H_BROWSER_CLOSE:browserOpen=false;break;
            case H_BROWSER_SEARCH:if(host!=null)host.prompt("Search actions","Try: light, door, damage, chase, button…",browserQuery,false,t->{browserQuery=t.trim();browserScroll=0;refreshBrowser();invalidate();});break;
            case H_ADD_GLOBAL:if(selected!=null){FSMGraph.Node n=selected;pickEvent("Global event to "+n.name,"",ev->{if(!ev.isEmpty())graph.globals.put(ev,n.id);});}break;
            case H_ACTION_BACK:selectedAction=-1;inspScroll=0;break;
            case H_ACT_UP:if(selected!=null&&selectedAction>0){FSMGraph.Action a=selected.actions.remove(selectedAction);selected.actions.add(selectedAction-1,a);selectedAction--;}break;
            case H_ACT_DOWN:if(selected!=null&&selectedAction<selected.actions.size()-1){FSMGraph.Action a=selected.actions.remove(selectedAction);selected.actions.add(selectedAction+1,a);selectedAction++;}break;
            case H_ACT_TOGGLE:if(selected!=null&&selectedAction>=0){FSMGraph.Action a=selected.actions.get(selectedAction);a.enabled=!a.enabled;}break;
            case H_ACT_DEL:if(selected!=null&&selectedAction>=0){selected.actions.remove(selectedAction);selectedAction=-1;}break;
        }
    }
    private boolean nodeAtWorld(float x,float y){for(FSMGraph.Node n:graph.nodes)if(Math.abs(n.x-x)<40&&Math.abs(n.y-y)<40)return true;return false;}
    private void newEntity(){
        if(host==null){EntityDef d=DefaultContent.fromTemplate(0,project.uniqueId("monster"),"New Monster");project.defs.add(d);selectDef(d);return;}
        host.choose("New entity from template",DefaultContent.TEMPLATES,0,t->host.prompt("Name",null,DefaultContent.TEMPLATES[t].replaceAll(" \\(.*\\)","").replace(" / trigger",""),false,name->{
            String n=name.trim().isEmpty()?"Entity":name.trim();EntityDef d=DefaultContent.fromTemplate(t,project.uniqueId(n),n);project.defs.add(d);selectDef(d);defScroll=1e9f;}));
    }
    /** Event picker: existing events, engine events, or a new name. */
    private void pickEvent(String title,String current,java.util.function.Consumer<String> done){
        ArrayList<String> items=new ArrayList<>();items.add("(none)");
        for(String e:graph.events())if(!items.contains(e))items.add(e);
        for(String e:STANDARD_EVENTS)if(!items.contains(e))items.add(e);
        items.add("New event…");
        String[] arr=items.toArray(new String[0]);int checked=items.indexOf(current);
        host.choose(title,arr,checked,i->{
            if(i==0)done.accept("");
            else if(i==arr.length-1)host.prompt("New event","Use CAPITALS, e.g. DOOR_OPEN",current,false,t->done.accept(t.trim().toUpperCase(java.util.Locale.US).replace(' ','_')));
            else done.accept(arr[i]);
        });
    }
    private void chooseEvent(FSMGraph.Node from,FSMGraph.Node to){
        if(host==null){from.transitions.put("FINISHED",to.id);return;}
        pickEvent("Transition "+from.name+" → "+to.name,"",ev->{if(ev.isEmpty())return;from.transitions.put(ev,to.id);selected=from;selectedAction=-1;invalidate();});
    }
    private void editParam(int i){
        if(selected==null||selectedAction<0||selectedAction>=selected.actions.size())return;
        FSMGraph.Action a=selected.actions.get(selectedAction);Actions.Spec s=Actions.get(a.type);if(s==null||i>=s.params.length)return;
        Actions.Param pm=s.params[i];String v0=a.params.get(pm.key);final String v=v0==null?"":v0;
        if(pm.type==Actions.T_BOOL){a.params.put(pm.key,v.equals("true")?"false":"true");return;}
        if(host==null)return;
        switch(pm.type){
            case Actions.T_CHOICE:host.choose(pm.label,pm.options,Actions.indexOf(Sprites.join(pm.options),v),k->a.params.put(pm.key,pm.options[k]));break;
            case Actions.T_EVENT:pickEvent(pm.label,v,ev->a.params.put(pm.key,ev));break;
            case Actions.T_ENTITY:{String[] names=new String[project.defs.size()+1];names[0]="(any / none)";int checked=0;
                for(int k=0;k<project.defs.size();k++){names[k+1]=project.defs.get(k).name;if(project.defs.get(k).id.equals(v))checked=k+1;}
                host.choose(pm.label,names,checked,k->a.params.put(pm.key,k==0?"":project.defs.get(k-1).id));break;}
            case Actions.T_MATERIAL:{int cur=0;try{cur=Integer.parseInt(v);}catch(NumberFormatException ignored){}host.choose(pm.label,Textures.NAMES,cur,k->a.params.put(pm.key,String.valueOf(k)));break;}
            case Actions.T_SECTOR:{int cur=0;try{cur=Integer.parseInt(v);}catch(NumberFormatException ignored){}
                host.prompt(pm.label,"Sector number as shown in the editor",String.valueOf(cur+1),true,t->{try{a.params.put(pm.key,String.valueOf(Math.max(0,Math.round(Float.parseFloat(t))-1)));}catch(NumberFormatException ignored){}});break;}
            case Actions.T_VAR:{ArrayList<String> vars=new ArrayList<>();
                for(FSMGraph.Node n:graph.nodes)for(FSMGraph.Action x:n.actions){Actions.Spec xs=Actions.get(x.type);if(xs==null)continue;for(Actions.Param q:xs.params)if(q.type==Actions.T_VAR){String vv=x.params.get(q.key);if(vv!=null&&!vv.isEmpty()&&!vars.contains(vv))vars.add(vv);}}
                vars.add("New variable…");String[] arr=vars.toArray(new String[0]);
                host.choose(pm.label+" (g. prefix = global)",arr,vars.indexOf(v),k->{if(k==arr.length-1)host.prompt("Variable name","Letters and numbers. Start with g. to share between entities.",v,false,t->{if(!t.trim().isEmpty())a.params.put(pm.key,t.trim());});else a.params.put(pm.key,arr[k]);});break;}
            default:{String hint=pm.type==Actions.T_FLOAT||pm.type==Actions.T_INT?"A number, or the name of a variable":null;
                host.prompt(pm.label,hint,v,false,t->a.params.put(pm.key,t.trim()));}
        }
    }

    // ---------------------------------------------------------------- preview support (desktop renderer only)
    static FSMGraphView preview(Context ctx,Project p,String def){FSMGraphView v=new FSMGraphView(ctx,p,def,null);return v;}
}
