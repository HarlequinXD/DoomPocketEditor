package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Map;

/**
 * PlayMaker-style state machine canvas: state boxes with title bars and event rows,
 * curved transition arrows, a START marker, an inspector panel and live run mode.
 */
public class FSMGraphView extends View {
    /** Dialog work the hosting activity performs. */
    interface Host {
        void rename(FSMGraph.Node node);
        void chooseAction(FSMGraph.Node node);
        void editArgument(FSMGraph.Node node);
        void chooseEvent(FSMGraph.Node from,FSMGraph.Node to);
        void save();
        void exit();
    }
    static final String[] ACTIONS={"NONE","WAIT","SET_LIGHT","SET_VARIABLE","CHECK_VARIABLE"};
    static String actionTitle(String a){
        switch(a){case "WAIT":return "Wait";case "SET_LIGHT":return "Set Light";case "SET_VARIABLE":return "Set Variable";case "CHECK_VARIABLE":return "Check Variable";default:return "No action";}
    }
    static String argumentHint(String a){
        switch(a){case "WAIT":return "Seconds, e.g. 1.5";case "SET_LIGHT":return "Sector, brightness · e.g. 0, 255";case "SET_VARIABLE":return "Name, value · e.g. alarm, 1";case "CHECK_VARIABLE":return "Name, minimum · e.g. alarm, 1";default:return "No argument";}
    }
    private static int actionIcon(String a){
        switch(a){case "WAIT":return Icons.CLOCK;case "SET_LIGHT":return Icons.SUN;case "SET_VARIABLE":return Icons.TAG;case "CHECK_VARIABLE":return Icons.VALIDATE;default:return Icons.DOT_CIRCLE;}
    }
    private static String actionSummary(FSMGraph.Node n){
        String a=n.argument==null?"":n.argument.trim();
        switch(n.action){
            case "WAIT":return "Wait "+(a.isEmpty()?"?":a)+"s";
            case "SET_LIGHT":{String[] s=a.split(",");return s.length>=2?"Light "+s[0].trim()+" → "+s[1].trim():"Set Light";}
            case "SET_VARIABLE":{String[] s=a.split(",");return s.length>=2?s[0].trim()+" = "+s[1].trim():"Set Variable";}
            case "CHECK_VARIABLE":{String[] s=a.split(",");return s.length>=2?s[0].trim()+" ≥ "+s[1].trim()+" → TRUE":"Check Variable";}
            default:return "";
        }
    }

    private static final int H_BACK=1,H_ADD=2,H_LINK=3,H_START=4,H_DELETE=5,H_FIT=6,H_RUN=7,H_SAVE=8,
            H_NAME=20,H_ACTION=21,H_ARG=22,H_ADD_TR=23,H_CANCEL_LINK=30,H_DEL_TR=100,H_SEND=200;
    // Node metrics in world units (dp at 100% zoom).
    private static final float NODE_W=176,TITLE_H=28,ACTION_H=22,ROW_H=24,PAD=5;

    final FSMGraph graph;
    private final Host host;
    private final Ui ui;
    private final Paint p;
    private final Path path=new Path();
    private final RectF r=new RectF();
    private final Level sandbox=new Level();
    FSMGraph.Node selected,linkFrom;
    private float w,h,toolbarH,camX=-40,camY=-60,zoom=1f;
    private boolean fitted,running;
    private long lastTick;
    private final ArrayList<String> rowEvents=new ArrayList<>();
    private ArrayList<String> sendEvents=new ArrayList<>();
    // touch state
    private FSMGraph.Node dragNode;
    private float downX,downY,lastX,lastY,pinchDist;
    private boolean moved,panning;
    private int downHit=-1;

    FSMGraphView(Context c,FSMGraph graph,Host host){super(c);this.graph=graph;this.host=host;ui=new Ui(c);p=ui.p;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}

    // ---------------------------------------------------------------- geometry
    private float dp(float v){return ui.dp(v);}
    private float inspectorW(){return Math.max(dp(224),Math.min(dp(300),w*.27f));}
    private float canvasRight(){return w-inspectorW();}
    private float scale(){return zoom*ui.dp;}
    private float sx(float x){return (x-camX)*scale();}
    private float sy(float y){return toolbarH+(y-camY)*scale();}
    private float nodeH(FSMGraph.Node n){return TITLE_H+(n.action.equals("NONE")?0:ACTION_H)+n.transitions.size()*ROW_H+(n.transitions.isEmpty()?6:PAD+2);}
    private float rowTop(FSMGraph.Node n,int i){return n.y+TITLE_H+(n.action.equals("NONE")?0:ACTION_H)+PAD*.5f+i*ROW_H;}
    private FSMGraph.Node nodeAt(float x,float y){
        for(int i=graph.nodes.size()-1;i>=0;i--){FSMGraph.Node n=graph.nodes.get(i);
            if(x>=sx(n.x)&&x<=sx(n.x+NODE_W)&&y>=sy(n.y)&&y<=sy(n.y+nodeH(n)))return n;}
        return null;
    }
    void fit(){
        if(graph.nodes.isEmpty()||w<=0)return;
        float minX=1e9f,minY=1e9f,maxX=-1e9f,maxY=-1e9f;
        for(FSMGraph.Node n:graph.nodes){minX=Math.min(minX,n.x);minY=Math.min(minY,n.y-24);maxX=Math.max(maxX,n.x+NODE_W);maxY=Math.max(maxY,n.y+nodeH(n));}
        float cw=canvasRight()/ui.dp,ch=(h-toolbarH)/ui.dp,margin=48;
        zoom=Math.max(.5f,Math.min(1.4f,Math.min(cw/(maxX-minX+margin*2),ch/(maxY-minY+margin*2+40))));
        camX=(minX+maxX)*.5f-cw/zoom*.5f;camY=(minY+maxY)*.5f-ch/zoom*.5f+12/zoom;
        invalidate();
    }
    void setRunning(boolean on){
        running=on;
        if(on){graph.reset();lastTick=System.nanoTime();}else{graph.running=false;graph.active="";}
        invalidate();
    }
    boolean isRunning(){return running;}

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();toolbarH=dp(48);
        if(!fitted&&w>0){fitted=true;fit();}
        if(running){long now=System.nanoTime();graph.tick(Math.min(.1f,(now-lastTick)*1e-9f),sandbox);lastTick=now;postInvalidateDelayed(16);}
        ui.clearHits();
        c.drawColor(0xff1b1e22);
        int save=c.save();c.clipRect(0,toolbarH,canvasRight(),h);
        drawGrid(c);
        for(FSMGraph.Node n:graph.nodes)drawTransitions(c,n);
        for(FSMGraph.Node n:graph.nodes)drawNode(c,n);
        if(graph.nodes.isEmpty()){float cx=canvasRight()*.5f,cy=(toolbarH+h)*.5f;
            ui.icon(c,Icons.GRAPH,cx,cy-dp(24),36,Ui.TEXT_3);ui.text(c,"No states yet",cx,cy+dp(20),15,Ui.TEXT_2,1,0);ui.text(c,"Tap Add State to begin",cx,cy+dp(40),12,Ui.TEXT_3,0,0);}
        drawCanvasOverlay(c);
        c.restoreToCount(save);
        drawInspector(c);
        drawTopBar(c);
    }
    private void drawGrid(Canvas c){
        float s=scale(),right=canvasRight();
        float step=20*s;if(step<dp(8))step*=5;
        float major=100*s;
        float ox=-camX*s,oy=toolbarH-camY*s;
        float lw=Math.max(1,dp(.75f));
        for(float x=ox%step-step;x<right;x+=step){if(x<0)continue;ui.rect(c,x,toolbarH,x+lw,h,0xff212429);}
        for(float y=toolbarH+((oy-toolbarH)%step+step)%step-step;y<h;y+=step){if(y<toolbarH)continue;ui.rect(c,0,y,right,y+lw,0xff212429);}
        for(float x=((ox%major)+major)%major;x<right;x+=major)ui.rect(c,x,toolbarH,x+lw,h,0xff272b31);
        for(float y=toolbarH+((oy-toolbarH)%major+major)%major;y<h;y+=major)ui.rect(c,0,y,right,y+lw,0xff272b31);
    }
    private void drawTransitions(Canvas c,FSMGraph.Node n){
        int i=0;float s=scale();
        for(Map.Entry<String,String> e:n.transitions.entrySet()){
            FSMGraph.Node d=graph.find(e.getValue());
            float ry=sy(rowTop(n,i)+ROW_H*.5f-1);i++;
            if(d==null)continue;
            boolean hot=n==selected||d==selected;
            boolean live=running&&graph.active.equals(n.id);
            int col=hot?Ui.ORANGE:live?0xff6fdcaa:0xff7d8896;
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(dp(1.5f),dp(2f)*zoom));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(col);
            float nl=sx(n.x),nr=sx(n.x+NODE_W),tl=sx(d.x),tr=sx(d.x+NODE_W),ty=sy(d.y+TITLE_H*.5f);
            float ax,bx;int dir;
            if(d==n){ // self loop
                float top=sy(n.y);path.reset();path.moveTo(nr,ry);path.cubicTo(nr+40*s,ry,nr+40*s,top-36*s,nr-24*s,top-14*s);
                c.drawPath(path,p);arrow(c,nr-24*s,top-14*s,-.6f,.8f,col);connector(c,nr,ry,col);continue;
            }
            if(tl>=nr-20*s){ax=nr;bx=tl;dir=1;}
            else if(tr<=nl+20*s){ax=nl;bx=tr;dir=-1;}
            else{ax=nr;bx=tr;dir=-2;} // overlapping columns: route around the right side
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
        float len=dp(9)*Math.max(.75f,zoom),wd=dp(5)*Math.max(.75f,zoom);float l=(float)Math.hypot(dx,dy);dx/=l;dy/=l;
        path.reset();path.moveTo(tipX,tipY);path.lineTo(tipX-dx*len-dy*wd,tipY-dy*len+dx*wd);path.lineTo(tipX-dx*len+dy*wd,tipY-dy*len-dx*wd);path.close();
        Paint.Style st=p.getStyle();p.setStyle(Paint.Style.FILL);p.setColor(col);c.drawPath(path,p);p.setStyle(st);
    }
    private void drawNode(Canvas c,FSMGraph.Node n){
        float z=zoom,l=sx(n.x),t=sy(n.y),rr=sx(n.x+NODE_W),b=sy(n.y+nodeH(n)),rad=dp(6)*z,th=TITLE_H*scale();
        boolean sel=n==selected,active=running&&graph.active.equals(n.id),isStart=n.id.equals(graph.start),linkSrc=n==linkFrom;
        if(rr<0||l>canvasRight()||b<toolbarH-dp(30)||t>h)return;
        // START marker
        if(isStart){float pw=dp(46)*z,ph=dp(17)*z,pl=l,pt=t-ph-dp(7)*z;
            ui.round(c,pl,pt,pl+pw,pt+ph,ph*.5f,Ui.GREEN);ui.textMid(c,"START",pl+pw*.5f,pt+ph*.5f,9.5f*z,0xff0f2a1e,2,0);
            p.setColor(Ui.GREEN);p.setStrokeWidth(dp(1.5f)*z);c.drawLine(pl+dp(12)*z,pt+ph,pl+dp(12)*z,t,p);}
        // shadow + body
        ui.round(c,l,t+dp(3)*z,rr,b+dp(3)*z,rad,0x66000000);
        ui.round(c,l,t,rr,b,rad,0xff2a2f37);
        // title bar
        int titleCol=active?0xff2e9e6c:sel?0xff59492e:0xff3b4350;
        int save=c.save();c.clipRect(l,t,rr,t+th);ui.round(c,l,t,rr,t+th+rad,rad,titleCol);c.restoreToCount(save);
        ui.textMid(c,ui.ellipsize(n.name,12.5f*z,1,rr-l-dp(20)*z),l+dp(10)*z,t+th*.5f,12.5f*z,0xffffffff,1,-1);
        if(active&&"WAIT".equals(n.action)){float secs=1;try{secs=Float.parseFloat(n.argument.trim());}catch(Exception ignored){}
            float f=Math.max(0,Math.min(1,graph.elapsed/Math.max(.01f,secs)));ui.rect(c,l,t+th-dp(2.5f)*z,l+(rr-l)*f,t+th,0xffa8f0cc);}
        float y=t+th;
        if(!n.action.equals("NONE")){
            float ah=ACTION_H*scale();
            ui.icon(c,actionIcon(n.action),l+dp(16)*z,y+ah*.5f,13*z,Ui.TEXT_3);
            ui.textMid(c,ui.ellipsize(actionSummary(n),11*z,0,rr-l-dp(36)*z),l+dp(28)*z,y+ah*.5f,11*z,Ui.TEXT_2,0,-1);
            ui.hline(c,l,rr,y+ah-dp(.75f),0xff232830);y+=ah;
        }
        int i=0;
        for(Map.Entry<String,String> e:n.transitions.entrySet()){
            float rt=sy(rowTop(n,i)),rb=rt+(ROW_H-3)*scale();
            ui.round(c,l+dp(5)*z,rt,rr-dp(5)*z,rb,dp(4)*z,graph.find(e.getValue())==null?0xff4a2c2c:0xff353c46);
            ui.textMid(c,ui.ellipsize(e.getKey(),11.5f*z,1,rr-l-dp(30)*z),l+dp(12)*z,(rt+rb)*.5f,11.5f*z,Ui.TEXT,1,-1);
            i++;
        }
        // outline
        if(sel||active||linkSrc)ui.outline(c,l,t,rr,b,rad,active?0xff6fdcaa:Ui.ORANGE,dp(2));
        else ui.outline(c,l,t,rr,b,rad,0x22ffffff,dp(1));
    }
    private void drawCanvasOverlay(Canvas c){
        float right=canvasRight();
        if(linkFrom!=null){
            String t="Tap the target state for a transition from "+linkFrom.name;
            float tw=ui.measure(t,12.5f,1)+dp(120),cx=right*.5f,top=toolbarH+dp(12);
            ui.pill(c,cx-tw*.5f,top,cx+tw*.5f,top+dp(36),0xf2262c34);
            ui.icon(c,Icons.LINK,cx-tw*.5f+dp(20),top+dp(18),16,Ui.ORANGE);
            ui.textMid(c,t,cx-tw*.5f+dp(36),top+dp(18),12.5f,Ui.TEXT,1,-1);
            r.set(cx+tw*.5f-dp(76),top+dp(5),cx+tw*.5f-dp(6),top+dp(31));ui.button(c,r,-1,"Cancel",Ui.NORMAL);ui.hit(r,H_CANCEL_LINK);
        }
        if(running){
            FSMGraph.Node a=graph.find(graph.active);
            sendEvents=graph.events();
            float bh=dp(40),bt=h-bh-dp(12),x=dp(12);
            float chipsW=0;for(String e:sendEvents)chipsW+=ui.measure(e,11.5f,1)+dp(52);
            String label=a==null?"Stopped":a.name;
            float lw=ui.measure(label,12.5f,1)+dp(70);
            float total=Math.min(right-dp(24),lw+chipsW+dp(8));
            ui.pill(c,x,bt,x+total,bt+bh,0xf2262c34);
            p.setColor(a==null?Ui.TEXT_3:Ui.GREEN);c.drawCircle(x+dp(18),bt+bh*.5f,dp(4.5f),p);
            ui.text(c,"ACTIVE",x+dp(30),bt+dp(16),9,Ui.TEXT_3,1,-1);
            ui.text(c,label,x+dp(30),bt+dp(31),12.5f,Ui.TEXT,1,-1);
            float cx=x+lw;
            for(int i=0;i<sendEvents.size();i++){String e=sendEvents.get(i);float ew=ui.measure(e,11.5f,1)+dp(46);
                if(cx+ew>x+total)break;
                r.set(cx,bt+dp(6),cx+ew,bt+bh-dp(6));ui.button(c,r,Icons.ZAP,e,Ui.NORMAL);ui.hit(r,H_SEND+i);cx+=ew+dp(6);}
        }else{
            ui.textMid(c,Math.round(zoom*100)+"%",right-dp(12),h-dp(14),11,Ui.TEXT_3,1,1);
        }
    }
    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,toolbarH,Ui.SURFACE);ui.hline(c,0,w,toolbarH-dp(1),Ui.BORDER);
        float cy=toolbarH*.5f,bh=dp(32),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        ui.textMid(c,"FSM Graph",dp(50),cy,15,Ui.TEXT,1,-1);
        float tx=dp(50)+ui.measure("FSM Graph",15,1)+dp(10);
        String count=graph.nodes.size()+(graph.nodes.size()==1?" state":" states");
        float cw=ui.measure(count,11,1)+dp(16);
        ui.round(c,tx,cy-dp(10),tx+cw,cy+dp(10),dp(10),Ui.SURFACE_2);ui.textMid(c,count,tx+cw*.5f,cy,11,Ui.TEXT_2,1,0);
        float x=w-dp(10);
        boolean wide=w>dp(840);
        // Save, run, fit
        float sw=wide?dp(82):bh;r.set(x-sw,top,x,top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);x-=sw+dp(8);
        float rw=wide?dp(78):bh;r.set(x-rw,top,x,top+bh);ui.button(c,r,running?Icons.STOP:Icons.PLAY,wide?(running?"Stop":"Run"):null,running?Ui.SUCCESS:Ui.NORMAL);ui.hit(r,H_RUN);x-=rw+dp(8);
        r.set(x-bh,top,x,top+bh);ui.button(c,r,Icons.FIT,null,Ui.NORMAL);ui.hit(r,H_FIT);x-=bh+dp(14);
        ui.vline(c,x+dp(6),top+dp(4),top+bh-dp(4),Ui.BORDER);
        // Editing group
        boolean has=selected!=null;
        boolean labels=w>dp(820);
        Object[][] items={{H_DELETE,Icons.TRASH,"Delete",has?Ui.DANGER:Ui.DISABLED},{H_START,Icons.FLAG,"Set Start",has?Ui.NORMAL:Ui.DISABLED},
                {H_LINK,Icons.LINK,"Transition",has?(linkFrom!=null?Ui.ACTIVE:Ui.NORMAL):Ui.DISABLED},{H_ADD,Icons.PLUS_SQUARE,"Add State",Ui.NORMAL}};
        for(Object[] it:items){String label=(String)it[2];float bw=labels?ui.measure(label,12.5f,1)+dp(46):bh;x-=dp(8);
            r.set(x-bw,top,x,top+bh);ui.button(c,r,(Integer)it[1],labels?label:null,(Integer)it[3]);ui.hit(r,(Integer)it[0]);x-=bw;}
    }
    private void field(Canvas c,float l,float t,float rr,String value,String placeholder,int icon,int hit){
        r.set(l,t,rr,t+dp(36));ui.round(c,r,dp(6),Ui.BG);ui.outline(c,r,dp(6),Ui.BORDER,dp(1));
        boolean empty=value==null||value.trim().isEmpty();
        ui.textMid(c,ui.ellipsize(empty?placeholder:value,12.5f,empty?0:1,rr-l-dp(44)),l+dp(10),t+dp(18),12.5f,empty?Ui.TEXT_3:Ui.TEXT,empty?0:1,-1);
        ui.icon(c,icon,rr-dp(16),t+dp(18),14,Ui.TEXT_3);ui.hit(r,hit);
    }
    private void drawInspector(Canvas c){
        float x0=canvasRight(),pad=dp(12),l=x0+pad,rr=w-pad;
        ui.rect(c,x0,toolbarH,w,h,Ui.SURFACE);ui.vline(c,x0,toolbarH,h,Ui.BORDER);
        float y=toolbarH+dp(16);
        if(selected!=null){
            FSMGraph.Node n=selected;
            ui.caption(c,"State",l,y+dp(8),Ui.TEXT_3);
            if(n.id.equals(graph.start)){float bw=dp(46);ui.round(c,rr-bw,y-dp(2),rr,y+dp(13),dp(8),0x333ecf8e);ui.textMid(c,"START",rr-bw*.5f,y+dp(5.5f),9,Ui.GREEN,2,0);}
            y+=dp(16);field(c,l,y,rr,n.name,"State name",Icons.PEN,H_NAME);y+=dp(48);
            ui.caption(c,"Action",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
            r.set(l,y,rr,y+dp(36));ui.round(c,r,dp(6),Ui.SURFACE_2);ui.outline(c,r,dp(6),0x14ffffff,dp(1));
            ui.icon(c,actionIcon(n.action),l+dp(18),y+dp(18),15,0xff8fbcff);ui.textMid(c,actionTitle(n.action),l+dp(34),y+dp(18),12.5f,Ui.TEXT,1,-1);
            ui.icon(c,Icons.CHEVRON_DOWN,rr-dp(16),y+dp(18),14,Ui.TEXT_2);ui.hit(r,H_ACTION);y+=dp(42);
            if(!n.action.equals("NONE")){field(c,l,y,rr,n.argument,argumentHint(n.action),Icons.PEN,H_ARG);y+=dp(48);}else y+=dp(6);
            ui.caption(c,"Transitions",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
            rowEvents.clear();
            int i=0;
            for(Map.Entry<String,String> e:n.transitions.entrySet()){
                if(y+dp(34)>h-dp(52))break;
                FSMGraph.Node d=graph.find(e.getValue());rowEvents.add(e.getKey());
                ui.round(c,l,y,rr,y+dp(32),dp(6),Ui.SURFACE_2);
                ui.icon(c,Icons.ZAP,l+dp(14),y+dp(16),13,Ui.AMBER);
                float ew=Math.min(ui.measure(e.getKey(),12,1),(rr-l)*.42f);
                ui.textMid(c,ui.ellipsize(e.getKey(),12,1,(rr-l)*.42f),l+dp(26),y+dp(16),12,Ui.TEXT,1,-1);
                ui.icon(c,Icons.CHEVRON_RIGHT,l+dp(34)+ew,y+dp(16),12,Ui.TEXT_3);
                ui.textMid(c,ui.ellipsize(d==null?"(missing)":d.name,12,0,rr-l-ew-dp(80)),l+dp(44)+ew,y+dp(16),12,d==null?Ui.RED:Ui.TEXT_2,0,-1);
                r.set(rr-dp(30),y+dp(2),rr-dp(2),y+dp(30));ui.icon(c,Icons.CANCEL,r.centerX(),r.centerY(),14,Ui.TEXT_3);ui.hit(r,H_DEL_TR+i);
                y+=dp(36);i++;
            }
            if(n.transitions.isEmpty()){ui.text(c,"No transitions yet.",l,y+dp(12),12,Ui.TEXT_3);y+=dp(22);}
            r.set(l,y+dp(2),rr,y+dp(34));ui.button(c,r,Icons.LINK,linkFrom==n?"Tap a target state…":"Add Transition",linkFrom==n?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_ADD_TR);
        }else{
            ui.caption(c,"State machine",l,y+dp(8),Ui.TEXT_3);y+=dp(20);
            FSMGraph.Node start=graph.find(graph.start);
            float cw=(rr-l)/2f;
            ui.round(c,l,y,rr,y+dp(56),dp(8),Ui.SURFACE_2);
            ui.text(c,String.valueOf(graph.nodes.size()),l+dp(12),y+dp(26),17,Ui.TEXT,1,-1);ui.text(c,"States",l+dp(12),y+dp(44),10.5f,Ui.TEXT_3);
            ui.text(c,String.valueOf(graph.transitionCount()),l+cw+dp(12),y+dp(26),17,Ui.TEXT,1,-1);ui.text(c,"Transitions",l+cw+dp(12),y+dp(44),10.5f,Ui.TEXT_3);
            y+=dp(66);
            ui.caption(c,"Start state",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
            ui.round(c,l,y,rr,y+dp(34),dp(6),Ui.SURFACE_2);ui.icon(c,Icons.FLAG,l+dp(16),y+dp(17),14,Ui.GREEN);
            ui.textMid(c,start==null?"None":start.name,l+dp(32),y+dp(17),12.5f,Ui.TEXT,1,-1);y+=dp(46);
            ui.caption(c,"Events",l,y+dp(8),Ui.TEXT_3);y+=dp(18);
            float ex=l;
            for(String e:graph.events()){float ew=ui.measure(e,11,1)+dp(30);if(ex+ew>rr){ex=l;y+=dp(30);}if(y>h-dp(90))break;
                ui.round(c,ex,y,ex+ew,y+dp(24),dp(12),Ui.SURFACE_2);ui.icon(c,Icons.ZAP,ex+dp(12),y+dp(12),11,Ui.AMBER);ui.textMid(c,e,ex+dp(22),y+dp(12),11,Ui.TEXT_2,1,-1);ex+=ew+dp(6);}
            y+=dp(40);
            ui.text(c,"Tap a state to edit it. Drag states",l,y,11.5f,Ui.TEXT_3);
            ui.text(c,"to move them, drag empty space to",l,y+dp(16),11.5f,Ui.TEXT_3);
            ui.text(c,"pan and pinch to zoom.",l,y+dp(32),11.5f,Ui.TEXT_3);
        }
    }

    // ---------------------------------------------------------------- touch
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){
            downX=lastX=x;downY=lastY=y;moved=false;panning=false;dragNode=null;pinchDist=0;
            downHit=ui.hitAt(x,y);
            if(downHit>=0||y<toolbarH||x>=canvasRight()){return true;}
            FSMGraph.Node n=nodeAt(x,y);
            if(linkFrom!=null){if(n!=null){FSMGraph.Node from=linkFrom;linkFrom=null;host.chooseEvent(from,n);}invalidate();return true;}
            if(n!=null){selected=n;dragNode=n;graph.nodes.remove(n);graph.nodes.add(n);}else panning=true;
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2){
            pinchDist=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;dragNode=null;panning=false;moved=true;return true;}
        if(a==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>=2&&pinchDist>0){
                float d=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1)),mx=(e.getX(0)+e.getX(1))*.5f,my=(e.getY(0)+e.getY(1))*.5f;
                float wx=camX+mx/scale(),wy=camY+(my-toolbarH)/scale();
                zoom=Math.max(.4f,Math.min(2.2f,zoom*d/pinchDist));pinchDist=d;
                camX=wx-mx/scale();camY=wy-(my-toolbarH)/scale();
                camX-=(mx-lastX)/scale();camY-=(my-lastY)/scale();lastX=mx;lastY=my;invalidate();return true;
            }
            if(Math.abs(x-downX)+Math.abs(y-downY)>dp(6))moved=true;
            if(moved){
                if(dragNode!=null){dragNode.x+=(x-lastX)/scale();dragNode.y+=(y-lastY)/scale();}
                else if(panning){camX-=(x-lastX)/scale();camY-=(y-lastY)/scale();}
                invalidate();
            }
            lastX=x;lastY=y;return true;
        }
        if(a==MotionEvent.ACTION_UP){
            if(downHit>=0&&!moved&&ui.hitAt(x,y)==downHit)action(downHit);
            else if(dragNode!=null&&moved){dragNode.x=Math.round(dragNode.x/8f)*8f;dragNode.y=Math.round(dragNode.y/8f)*8f;}
            else if(panning&&!moved){selected=null;}
            dragNode=null;panning=false;downHit=-1;invalidate();return true;
        }
        if(a==MotionEvent.ACTION_CANCEL){dragNode=null;panning=false;downHit=-1;return true;}
        return true;
    }
    private void action(int id){
        if(id>=H_SEND&&id<H_SEND+100){int i=id-H_SEND;if(i<sendEvents.size())graph.event(sendEvents.get(i));return;}
        if(id>=H_DEL_TR&&id<H_DEL_TR+100){int i=id-H_DEL_TR;if(selected!=null&&i<rowEvents.size())selected.transitions.remove(rowEvents.get(i));return;}
        switch(id){
            case H_BACK:host.exit();break;
            case H_SAVE:host.save();break;
            case H_RUN:setRunning(!running);break;
            case H_FIT:fit();break;
            case H_CANCEL_LINK:linkFrom=null;break;
            case H_ADD:{
                float cx=camX+canvasRight()*.5f/scale()-NODE_W*.5f,cy=camY+(h-toolbarH)*.4f/scale();
                for(int k=0;k<20&&nodeAtWorld(cx,cy);k++){cx+=24;cy+=24;}
                selected=graph.add("State "+(graph.nodes.size()+1),Math.round(cx/8f)*8f,Math.round(cy/8f)*8f);break;}
            case H_LINK:case H_ADD_TR:if(selected!=null)linkFrom=linkFrom==selected?null:selected;break;
            case H_START:if(selected!=null)graph.start=selected.id;break;
            case H_DELETE:if(selected!=null){FSMGraph.Node node=selected;graph.nodes.remove(node);
                for(FSMGraph.Node n:graph.nodes)n.transitions.values().removeAll(java.util.Collections.singleton(node.id));
                if(graph.start.equals(node.id))graph.start=graph.nodes.isEmpty()?"":graph.nodes.get(0).id;
                if(linkFrom==node)linkFrom=null;selected=null;}break;
            case H_NAME:if(selected!=null)host.rename(selected);break;
            case H_ACTION:if(selected!=null)host.chooseAction(selected);break;
            case H_ARG:if(selected!=null)host.editArgument(selected);break;
        }
    }
    private boolean nodeAtWorld(float x,float y){for(FSMGraph.Node n:graph.nodes)if(Math.abs(n.x-x)<40&&Math.abs(n.y-y)<40)return true;return false;}

    // ---------------------------------------------------------------- preview support (desktop renderer only)
    static FSMGraphView preview(Context ctx){
        FSMGraph g=new FSMGraph();
        FSMGraph.Node idle=g.add("Idle",0,40),alarm=g.add("Alarm",280,0),wait=g.add("Hold",560,40),dim=g.add("Lights Down",280,200);
        alarm.action="SET_LIGHT";alarm.argument="0, 255";wait.action="WAIT";wait.argument="2";dim.action="SET_LIGHT";dim.argument="0, 120";
        idle.transitions.put("FIRE",alarm.id);alarm.transitions.put("FINISHED",wait.id);wait.transitions.put("FINISHED",dim.id);wait.transitions.put("FIRE",alarm.id);dim.transitions.put("FINISHED",idle.id);
        FSMGraphView v=new FSMGraphView(ctx,g,null);v.selected=alarm;v.running=true;g.reset();g.active=wait.id;g.elapsed=.8f;v.lastTick=System.nanoTime();
        return v;
    }
    public void previewSelectNone(){selected=null;running=false;graph.running=false;graph.active="";}
}
