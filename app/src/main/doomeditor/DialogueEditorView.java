package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/**
 * Dialogue editor. Conversations: a node graph (lines of dialogue with choices) and an inspector for the
 * selected node's speaker, text, effects and choices. Quests: titles, stage objectives and rewards.
 * NPCs run a conversation with the Talk (Conversation) FSM action.
 */
public class DialogueEditorView extends View {
    interface Host extends UiHost {void exit();void saved();}
    static final String[] TABS={"Conversations","Quests"};
    private static final int H_BACK=1,H_SAVE=2,H_PICK=3,H_ZIN=4,H_ZOUT=5,H_FIT=6,H_NEWQ=7,H_DELQ=8,H_ADDNODE=9,H_TAB=10,H_UNDO=11,H_REDO=12,H_QROW=100,H_PROP=1000;
    private static final float NODE_W=210,HEAD_H=24,LINE_H=15,ROW_H=22;

    private final Project project;private final Host host;private final Ui ui;private final Paint p;private final Path path=new Path();
    private int tab;Dialogue.Def talk;Dialogue.Node node;private int questSel;
    private float w,h,barH,camX=-40,camY=-80,zoom=1,inspScroll,inspContent,listScroll;
    private boolean fitted;
    private final RectF r=new RectF(),canvasR=new RectF(),inspR=new RectF(),listR=new RectF();
    private String hint;private long hintUntil;
    // touch
    private float downX,downY,lastX,lastY;private boolean moved;private int downHit=-1,zone;private Dialogue.Node drag;

    private static final class Prop {String label,value;Runnable tap;boolean toggle,on,header,danger;}
    private final ArrayList<Prop> props=new ArrayList<>();

    DialogueEditorView(Context c,Project project,int startTab,Host host){
        super(c);this.project=project;this.host=host;ui=new Ui(c);p=ui.p;tab=startTab==1?1:0;
        project.ensureContent();Rpg.use(project);
        if(!project.dialogues.isEmpty())talk=project.dialogues.get(0);
        undo=new Undo(()->project.section(Project.SEC_DIALOGUE),t->project.restoreSection(Project.SEC_DIALOGUE,t));undo.reset();
    }
    final Undo undo;
    void afterEdit(){undo.commit();invalidate();}
    private void undoRedo(boolean back){
        String tid=talk==null?null:talk.id,nid=node==null?null:node.id;
        if(back?undo.undo():undo.redo()){
            talk=null;node=null;for(Dialogue.Def d:project.dialogues)if(d.id.equals(tid))talk=d;
            if(talk==null&&!project.dialogues.isEmpty())talk=project.dialogues.get(0);
            if(talk!=null&&nid!=null)for(Dialogue.Node n:talk.nodes)if(nid.equals(n.id))node=n;
            questSel=Math.max(0,Math.min(Math.max(0,project.quests.size()-1),questSel));
            project.save(getContext());hint(back?"Undo":"Redo");}
        else hint(back?"Nothing to undo":"Nothing to redo");}
    private float dp(float v){return ui.dp(v);}
    private void hint(String s){hint=s;hintUntil=System.currentTimeMillis()+2600;invalidate();}
    private void changed(){undo.commit();project.save(getContext());invalidate();}

    // ================================================================== geometry
    private float sc(){return zoom*ui.dp;}
    private float sx(float x){return canvasR.left+(x-camX)*sc();}
    private float sy(float y){return canvasR.top+(y-camY)*sc();}
    private int textLines(Dialogue.Node n){return Math.max(1,Math.min(3,wrap(n.text,NODE_W-16).size()));}
    private float nodeH(Dialogue.Node n){return HEAD_H+6+textLines(n)*LINE_H+(n.effects.isEmpty()?0:LINE_H)+4+Math.max(1,n.choices.size())*ROW_H+6;}
    private float rowY(Dialogue.Node n,int i){return n.y+HEAD_H+6+textLines(n)*LINE_H+(n.effects.isEmpty()?0:LINE_H)+4+i*ROW_H;}
    private ArrayList<String> wrap(String text,float maxW){
        ArrayList<String> out=new ArrayList<>();StringBuilder line=new StringBuilder();float size=10.5f;float mw=maxW*ui.dp;
        for(String word:(text==null?"":text).split(" ")){String t=line.length()==0?word:line+" "+word;if(ui.measure(t,size,0)>mw&&line.length()>0){out.add(line.toString());line=new StringBuilder(word);}else line=new StringBuilder(t);}
        if(line.length()>0)out.add(line.toString());if(out.isEmpty())out.add("");return out;
    }
    private Dialogue.Node nodeAt(float x,float y){if(talk==null)return null;for(int i=talk.nodes.size()-1;i>=0;i--){Dialogue.Node n=talk.nodes.get(i);if(x>=sx(n.x)&&x<=sx(n.x+NODE_W)&&y>=sy(n.y)&&y<=sy(n.y+nodeH(n)))return n;}return null;}
    private void fit(){
        if(talk==null||talk.nodes.isEmpty()||canvasR.width()<=0)return;
        float minX=1e9f,minY=1e9f,maxX=-1e9f,maxY=-1e9f;
        for(Dialogue.Node n:talk.nodes){minX=Math.min(minX,n.x);minY=Math.min(minY,n.y);maxX=Math.max(maxX,n.x+NODE_W);maxY=Math.max(maxY,n.y+nodeH(n));}
        float cw=canvasR.width()/ui.dp,ch=canvasR.height()/ui.dp,m=30;
        zoom=Math.max(.4f,Math.min(1.2f,Math.min(cw/(maxX-minX+m*2),ch/(maxY-minY+m*2))));
        camX=(minX+maxX)*.5f-cw/zoom*.5f;camY=(minY+maxY)*.5f-ch/zoom*.5f;invalidate();
    }

    // ================================================================== drawing
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();barH=dp(48);
        ui.clearHits();c.drawColor(Ui.BG);
        float iw=Math.max(dp(250),Math.min(dp(330),w*.36f));
        inspR.set(w-iw,barH,w,h);
        if(tab==0){
            canvasR.set(0,barH,w-iw,h);
            if(!fitted&&canvasR.width()>0){fitted=true;fit();}
            drawGraph(c);
        }else{listR.set(dp(10),barH+dp(10),w-iw-dp(12),h-dp(10));drawQuestList(c);}
        drawInspector(c);
        drawTopBar(c);
        if(hint!=null&&System.currentTimeMillis()<hintUntil){float tw=Math.min(w-dp(20),ui.measure(hint,12,1)+dp(32)),cx=w*.5f,b=h-dp(16);
            ui.pill(c,cx-tw*.5f,b-dp(32),cx+tw*.5f,b,0xf02a313a);ui.textMid(c,ui.ellipsize(hint,12,1,tw-dp(20)),cx,b-dp(16),12,Ui.TEXT,1,0);postInvalidateDelayed(300);}
    }
    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,barH,Ui.SURFACE);ui.hline(c,0,w,barH-dp(1),Ui.BORDER);
        float cy=barH*.5f,bh=dp(34),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        boolean wide=w>dp(820);
        float ib=dp(28);ui.round(c,dp(50),cy-ib*.5f,dp(50)+ib,cy+ib*.5f,dp(7),0xff5a8a4a);ui.icon(c,Icons.CHAT,dp(50)+ib*.5f,cy,16,0xffffffff);
        float tl=dp(86);if(wide){ui.textMid(c,"Dialogue",tl,cy,15,Ui.TEXT,1,-1);tl+=dp(84);}
        float saveW=wide?dp(84):bh;r.set(w-dp(10)-saveW,top,w-dp(10),top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);
        float ux=Undo.buttons(ui,c,r,undo,w-dp(16)-saveW,top,bh,H_UNDO,H_REDO);float tw=Math.min(dp(150),(ux-tl)/TABS.length);
        for(int i=0;i<TABS.length;i++){r.set(tl+i*tw,top,tl+(i+1)*tw-dp(6),top+bh);ui.button(c,r,i==0?Icons.CHAT:Icons.SCROLL,TABS[i],i==tab?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_TAB+i);}
    }
    private void drawGraph(Canvas c){
        RectF R=canvasR;c.save();c.clipRect(R);c.drawColor(0xff1b1e22);
        float step=20*sc();if(step<dp(8))step*=5;float ox=R.left-camX*sc(),oy=R.top-camY*sc();
        for(float x=R.left+((ox-R.left)%step+step)%step;x<R.right;x+=step)ui.rect(c,x,R.top,x+1,R.bottom,0xff212429);
        for(float y=R.top+((oy-R.top)%step+step)%step;y<R.bottom;y+=step)ui.rect(c,R.left,y,R.right,y+1,0xff212429);
        if(talk==null){ui.text(c,"No conversations yet",R.centerX(),R.centerY()-dp(10),15,Ui.TEXT,1,0);
            ui.text(c,"Tap the conversation chip above to create one",R.centerX(),R.centerY()+dp(12),12,Ui.TEXT_3,0,0);}
        else{
            for(Dialogue.Node n:talk.nodes)drawLinks(c,n);
            for(Dialogue.Node n:talk.nodes)drawNode(c,n);
        }
        c.restore();
        // overlay: conversation picker + zoom
        String nm=talk==null?"New conversation…":talk.name;float cw=Math.min(R.width()-dp(150),ui.measure(nm,12.5f,1)+dp(60));
        r.set(R.left+dp(10),R.top+dp(10),R.left+dp(10)+cw,R.top+dp(44));ui.round(c,r,dp(8),0xf0262c34);ui.outline(c,r,dp(8),0x605a8a4a,dp(1));
        ui.icon(c,Icons.CHAT,r.left+dp(16),r.centerY(),14,0xff8ad06a);ui.textMid(c,ui.ellipsize(nm,12.5f,1,cw-dp(56)),r.left+dp(30),r.centerY(),12.5f,Ui.TEXT,1,-1);ui.icon(c,Icons.CHEVRON_DOWN,r.right-dp(14),r.centerY(),13,Ui.TEXT_2);ui.hit(r,H_PICK);
        float bx=R.right-dp(10),bs=dp(34);
        int[] ic={Icons.FIT,Icons.MINUS,Icons.PLUS};int[] id={H_FIT,H_ZOUT,H_ZIN};
        for(int i=0;i<3;i++){r.set(bx-bs,R.top+dp(10),bx,R.top+dp(10)+bs);ui.button(c,r,ic[i],null,Ui.NORMAL);ui.hit(r,id[i]);bx-=bs+dp(6);}
        if(talk!=null){r.set(R.left+dp(10),R.bottom-dp(48),R.left+dp(150),R.bottom-dp(12));ui.button(c,r,Icons.PLUS,"Add line",Ui.PRIMARY);ui.hit(r,H_ADDNODE);}
    }
    private void drawNode(Canvas c,Dialogue.Node n){
        float s=sc(),l=sx(n.x),t=sy(n.y),rr=sx(n.x+NODE_W),b=sy(n.y+nodeH(n));
        if(rr<canvasR.left||l>canvasR.right||b<canvasR.top||t>canvasR.bottom)return;
        boolean sel=n==node,start=n.id.equals(talk.start);
        ui.round(c,l,t+3*s,rr,b+3*s,8*s,0x50000000);ui.round(c,l,t,rr,b,8*s,0xff262b33);
        ui.round(c,l,t,rr,t+HEAD_H*s,8*s,start?0xff3a6a3a:0xff31404f);ui.rect(c,l,t+HEAD_H*s-8*s,rr,t+HEAD_H*s,start?0xff3a6a3a:0xff31404f);
        ui.outline(c,l,t,rr,b,8*s,sel?Ui.ORANGE:0x30ffffff,sel?2*ui.dp:ui.dp);
        String sp=!n.speaker.isEmpty()?n.speaker:!talk.speaker.isEmpty()?talk.speaker:"NPC";
        float fs=Math.max(7,11*zoom);
        ui.textMid(c,ui.ellipsize((start?"▶ ":"")+sp,fs,2,(NODE_W-20)*s),l+8*s,t+HEAD_H*s*.5f,fs,0xffffffff,2,-1);
        float y=t+(HEAD_H+6)*s;ArrayList<String> lines=wrap(n.text,NODE_W-16);
        for(int i=0;i<Math.min(3,lines.size());i++){String ln=lines.get(i);if(i==2&&lines.size()>3)ln=ui.ellipsize(ln+"…",10.5f*zoom,0,(NODE_W-16)*s);ui.text(c,ln,l+8*s,y+11*s,Math.max(6,10.5f*zoom),Ui.TEXT_2,0,-1);y+=LINE_H*s;}
        if(!n.effects.isEmpty()){ui.text(c,ui.ellipsize("⚡ "+Dialogue.effectLabel(n.effects.get(0))+(n.effects.size()>1?" +"+(n.effects.size()-1):""),9.5f*zoom,1,(NODE_W-16)*s),l+8*s,y+11*s,Math.max(6,9.5f*zoom),0xffffd27a,1,-1);}
        for(int i=0;i<Math.max(1,n.choices.size());i++){float ry=sy(rowY(n,i));ui.rect(c,l+6*s,ry,rr-6*s,ry+(ROW_H-3)*s,0xff1d2229);
            String lbl;int col;
            if(n.choices.isEmpty()){lbl=n.next.isEmpty()?"(Farewell — ends)":"Continue";col=Ui.TEXT_3;}
            else{Dialogue.Choice ch=n.choices.get(i);lbl=(ch.cond.kind!=Dialogue.C_NONE?"? ":"")+ch.text;col=ch.target.isEmpty()?0xffd08a8a:Ui.TEXT;}
            ui.textMid(c,ui.ellipsize(lbl,Math.max(6,10.5f*zoom),1,(NODE_W-30)*s),l+12*s,ry+(ROW_H-3)*s*.5f,Math.max(6,10.5f*zoom),col,1,-1);
            p.setColor(0xff8ad06a);c.drawCircle(rr,ry+(ROW_H-3)*s*.5f,3.5f*s,p);}
    }
    private void drawLinks(Canvas c,Dialogue.Node n){
        float s=sc();
        int count=Math.max(1,n.choices.size());
        for(int i=0;i<count;i++){String target=n.choices.isEmpty()?n.next:n.choices.get(i).target;Dialogue.Node d=talk.node(target);if(d==null)continue;
            float ax=sx(n.x+NODE_W),ay=sy(rowY(n,i))+(ROW_H-3)*s*.5f,bx=sx(d.x),by=sy(d.y)+HEAD_H*s*.5f;
            boolean hot=n==node||d==node;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(dp(1.5f),2*sc()));p.setColor(hot?Ui.ORANGE:0xff7d8896);
            path.reset();path.moveTo(ax,ay);float dx=Math.max(40*s,Math.abs(bx-ax)*.5f);
            if(d==n){path.cubicTo(ax+50*s,ay,ax+50*s,sy(n.y)-30*s,bx+30*s,sy(n.y)-4*s);}else path.cubicTo(ax+dx,ay,bx-dx,by,bx,by);
            c.drawPath(path,p);p.setStyle(Paint.Style.FILL);c.drawCircle(bx,by,3.5f*s,p);}
    }

    // ------------------------------------------------------------------ quest list
    private void drawQuestList(Canvas c){
        RectF A=listR;ui.panel(c,A.left-dp(2),A.top-dp(2),A.right+dp(2),A.bottom+dp(2),dp(10));
        c.save();c.clipRect(A);
        float y=A.top+dp(8)-listScroll,l=A.left+dp(8),rr=A.right-dp(8);
        r.set(l,y,l+Math.min(dp(220),rr-l),y+dp(36));ui.button(c,r,Icons.PLUS,"New quest",Ui.PRIMARY);hitIn(r,H_NEWQ,A);y+=dp(46);
        if(project.quests.isEmpty()){ui.text(c,"No quests yet. A quest has a title, stages (objectives) and rewards.",l,y+dp(14),12,Ui.TEXT_3);y+=dp(26);
            ui.text(c,"Start them from conversations or with the Begin Quest action.",l,y+dp(14),12,Ui.TEXT_3);y+=dp(26);}
        for(int i=0;i<project.quests.size();i++){Dialogue.QuestDef q=project.quests.get(i);r.set(l,y,rr,y+dp(48));boolean on=i==questSel;
            ui.round(c,r,dp(8),on?0xff26324a:Ui.SURFACE_2);if(on)ui.outline(c,r,dp(8),Ui.ACCENT,dp(1.5f));
            ui.icon(c,Icons.SCROLL,l+dp(20),r.centerY(),16,0xffd8a830);
            ui.text(c,ui.ellipsize(q.title,13,1,rr-l-dp(50)),l+dp(40),r.centerY()-dp(3),13,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize(q.stages.size()+" stage"+(q.stages.size()==1?"":"s")+" · "+q.xp+" XP · "+q.gold+" gold"+(q.rewardItem.isEmpty()?"":" · item")+" · id "+q.id,10.5f,0,rr-l-dp(50)),l+dp(40),r.centerY()+dp(13),10.5f,Ui.TEXT_3,0,-1);
            hitIn(r,H_QROW+i,A);y+=dp(54);}
        c.restore();
    }
    private void hitIn(RectF rr,int id,RectF area){if(rr.bottom<area.top||rr.top>area.bottom)return;ui.hit(rr.left,Math.max(rr.top,area.top),rr.right,Math.min(rr.bottom,area.bottom),id);}

    // ------------------------------------------------------------------ inspector
    private void drawInspector(Canvas c){
        RectF A=inspR;ui.rect(c,A.left,A.top,A.right,A.bottom,Ui.SURFACE);ui.vline(c,A.left,A.top,A.bottom,Ui.BORDER);
        props.clear();
        String title,sub;
        if(tab==1){if(project.quests.isEmpty()){title="Quests";sub="Create one with + New quest";}else{questSel=Math.min(questSel,project.quests.size()-1);Dialogue.QuestDef q=project.quests.get(questSel);title=q.title;sub="Quest · id "+q.id;questProps(q);}}
        else if(talk==null){title="Conversations";sub="Create one from the chip on the left";}
        else if(node==null){title=talk.name;sub=talk.nodes.size()+" lines · tap a line to edit it";talkProps();}
        else{title=node.id.equals(talk.start)?"Opening line":"Line";sub=talk.name;nodeProps(node);}
        c.save();c.clipRect(A);
        float l=A.left+dp(12),rr=A.right-dp(12),y=A.top+dp(12)-inspScroll;
        ui.text(c,ui.ellipsize(title,16,1,rr-l),l,y+dp(16),16,Ui.TEXT,1,-1);ui.text(c,ui.ellipsize(sub,11,0,rr-l),l,y+dp(33),11,Ui.TEXT_3,0,-1);y+=dp(44);
        for(int i=0;i<props.size();i++){Prop pr=props.get(i);
            if(pr.header){ui.caption(c,pr.label,l,y+dp(12),Ui.TEXT_3);y+=dp(20);continue;}
            r.set(l,y,rr,y+dp(46));ui.round(c,r,dp(7),Ui.SURFACE_2);
            ui.text(c,ui.ellipsize(pr.label,10.5f,1,rr-l-dp(20)),l+dp(10),y+dp(16),10.5f,Ui.TEXT_3,1,-1);
            if(pr.toggle){float tx=rr-dp(44),ty=y+dp(15);ui.round(c,tx,ty,tx+dp(32),ty+dp(16),dp(8),pr.on?Ui.ACCENT:Ui.SURFACE_3);p.setColor(0xffffffff);c.drawCircle(pr.on?tx+dp(24):tx+dp(8),ty+dp(8),dp(6),p);
                ui.text(c,pr.on?"Yes":"No",l+dp(10),y+dp(36),12.5f,Ui.TEXT,1,-1);}
            else{ui.text(c,ui.ellipsize(pr.value,12.5f,1,rr-l-dp(36)),l+dp(10),y+dp(36),12.5f,pr.danger?0xffff8a8a:pr.tap==null?Ui.TEXT_2:Ui.TEXT,1,-1);if(pr.tap!=null)ui.icon(c,Icons.CHEVRON_DOWN,rr-dp(14),y+dp(23),13,Ui.TEXT_3);}
            if(pr.tap!=null)hitIn(r,H_PROP+i,A);
            y+=dp(52);}
        inspContent=y+inspScroll-A.top;c.restore();
        float max=Math.max(0,inspContent-A.height());if(inspScroll>max){inspScroll=max;invalidate();}
    }
    private Prop prop(String label,String value,Runnable tap){Prop pr=new Prop();pr.label=label;pr.value=value;pr.tap=tap;props.add(pr);return pr;}
    private void header(String label){Prop pr=new Prop();pr.label=label;pr.header=true;props.add(pr);}
    private void text(String title,String hintText,String cur,java.util.function.Consumer<String> set){if(host!=null)host.prompt(title,hintText,cur,false,t->{set.accept(t.trim());changed();});}
    private void number(String title,int cur,int min,int max,java.util.function.IntConsumer set){
        if(host!=null)host.prompt(title,min+" to "+max,String.valueOf(cur),true,t->{try{set.accept(Math.max(min,Math.min(max,Math.round(Float.parseFloat(t.trim())))));changed();}catch(NumberFormatException ignored){}});}
    private void choose(String title,String[] items,int cur,java.util.function.IntConsumer set){if(host!=null)host.choose(title,items,cur,k->{set.accept(k);changed();});}
    private String[] itemNames(){String[] a=new String[Rpg.ITEMS.size()];for(int i=0;i<a.length;i++)a[i]=Rpg.ITEMS.get(i).name;return a;}
    private String nodeLabel(Dialogue.Node n){String t=n.text.length()>40?n.text.substring(0,40)+"…":n.text;return (n.id.equals(talk.start)?"▶ ":"")+(t.isEmpty()?"(empty line)":t);}

    private void talkProps(){
        header("Conversation");
        prop("Name",talk.name,()->text("Conversation name",null,talk.name,v->{if(!v.isEmpty())talk.name=v;}));
        prop("Speaker",talk.speaker.isEmpty()?"The NPC's name":talk.speaker,()->text("Default speaker","Empty = the talking entity's name",talk.speaker,v->talk.speaker=v));
        Dialogue.Node st=talk.node(talk.start);prop("Opening line",st==null?"None":nodeLabel(st),()->pickNode("Opening line",false,id->talk.start=id));
        prop("ID",talk.id,null);
        header("Use it");
        prop("In an NPC's FSM","Add Talk (Conversation) and pick “"+talk.name+"”",null);
        header("Manage");
        prop("Duplicate","Make a copy of this conversation",()->{Dialogue.Def d=Dialogue.copy(talk);d.name=talk.name+" copy";d.id=project.uniqueTalkId(d.name);project.dialogues.add(d);talk=d;node=null;changed();hint("Duplicated");});
        Prop del=prop("Delete conversation","Removes all its lines",()->{if(host!=null)host.confirm("Delete “"+talk.name+"”?","Talk actions that use it will do nothing.","Delete",()->{project.dialogues.remove(talk);talk=project.dialogues.isEmpty()?null:project.dialogues.get(0);node=null;fitted=false;changed();});});del.danger=true;
    }
    private void nodeProps(Dialogue.Node n){
        header("Line");
        prop("Speaker",n.speaker.isEmpty()?"Default ("+(talk.speaker.isEmpty()?"the NPC":talk.speaker)+")":n.speaker,()->text("Speaker","Empty = the conversation's speaker",n.speaker,v->n.speaker=v));
        prop("Text",n.text.isEmpty()?"(tap to write)":n.text,()->text("What they say",null,n.text,v->n.text=v));
        prop("Opening line",n.id.equals(talk.start)?"Yes – the conversation starts here":"No (tap to start here)",n.id.equals(talk.start)?null:()->{talk.start=n.id;changed();});
        header("When this line is shown");
        for(int i=0;i<n.effects.size();i++){final Dialogue.Effect e=n.effects.get(i);prop("Effect "+(i+1),Dialogue.effectLabel(e),()->choose(Dialogue.effectLabel(e),new String[]{"Change…","Remove"},-1,a->{if(a==0)editEffect(e);else n.effects.remove(e);}));}
        prop("+ Add effect","Give items or gold, quests, variables, XP, loot, shop…",()->choose("Add effect",Dialogue.EFFECT_KINDS,-1,k->{Dialogue.Effect e=new Dialogue.Effect();e.kind=k;n.effects.add(e);editEffect(e);}));
        header("Player choices");
        for(int i=0;i<n.choices.size();i++){final int k=i;final Dialogue.Choice ch=n.choices.get(i);
            Dialogue.Node t=talk.node(ch.target);
            prop((i+1)+". "+(ch.cond.kind==Dialogue.C_NONE?"":"if "+Dialogue.condLabel(ch.cond)+" · "),"“"+ch.text+"” → "+(t==null?"ends":nodeLabel(t)),()->editChoice(n,ch,k));}
        prop("+ Add choice",n.choices.isEmpty()?"Without choices the player just taps Continue":"Up to 8 choices",n.choices.size()>=8?null:()->{Dialogue.Choice ch=new Dialogue.Choice();ch.text="…";n.choices.add(ch);
            text("Choice text",null,"",v->{if(!v.isEmpty())ch.text=v;pickNode("Choice leads to",true,id->{ch.target=id;changed();});});});
        if(n.choices.isEmpty()){Dialogue.Node nx=talk.node(n.next);prop("Continue goes to",nx==null?"Ends the conversation":nodeLabel(nx),()->pickNode("Continue goes to",true,id->n.next=id));}
        header("Manage");
        prop("Duplicate line","Copy with its choices and effects",()->{
            Dialogue.Node c=new Dialogue.Node();c.id=talk.newNodeId();c.speaker=n.speaker;c.text=n.text;c.x=n.x+30;c.y=n.y+nodeH(n)+20;c.next=n.next;
            for(Dialogue.Choice ch:n.choices){Dialogue.Choice d=new Dialogue.Choice();d.text=ch.text;d.target=ch.target;d.cond.kind=ch.cond.kind;d.cond.s=ch.cond.s;d.cond.n=ch.cond.n;d.cond.not=ch.cond.not;c.choices.add(d);}
            for(Dialogue.Effect e:n.effects){Dialogue.Effect d=new Dialogue.Effect();d.kind=e.kind;d.s=e.s;d.n=e.n;c.effects.add(d);}
            talk.nodes.add(c);node=c;changed();});
        Prop del=prop("Delete line","Choices pointing here will end the conversation",()->{if(host!=null)host.confirm("Delete this line?",null,"Delete",()->{
            talk.nodes.remove(n);for(Dialogue.Node o:talk.nodes){if(o.next.equals(n.id))o.next="";for(Dialogue.Choice ch:o.choices)if(ch.target.equals(n.id))ch.target="";}
            if(talk.start.equals(n.id))talk.start=talk.nodes.isEmpty()?"":talk.nodes.get(0).id;node=null;changed();});});del.danger=true;
    }
    /** Pick a line of the current conversation; {@code allowEnd} adds "End conversation" and "New line". */
    private void pickNode(String title,boolean allowEnd,java.util.function.Consumer<String> set){
        if(host==null)return;
        int extra=allowEnd?2:0;String[] items=new String[talk.nodes.size()+extra];
        for(int i=0;i<talk.nodes.size();i++)items[i]=nodeLabel(talk.nodes.get(i));
        if(allowEnd){items[items.length-2]="End the conversation";items[items.length-1]="+ New line";}
        host.choose(title,items,-1,k->{
            if(allowEnd&&k==items.length-2)set.accept("");
            else if(allowEnd&&k==items.length-1){Dialogue.Node base=node!=null?node:talk.nodes.get(0);Dialogue.Node nn=talk.add("",base.x+NODE_W+70,base.y+(talk.nodes.size()%3)*60-60);set.accept(nn.id);changed();
                text("What they say",null,"",v->nn.text=v);return;}
            else set.accept(talk.nodes.get(k).id);
            changed();});
    }
    private void editChoice(Dialogue.Node n,Dialogue.Choice ch,int index){
        choose("“"+ch.text+"”",new String[]{"Edit text","Leads to…","Condition… ("+Dialogue.condLabel(ch.cond)+")","Move up","Remove"},-1,a->{
            switch(a){
                case 0:text("Choice text",null,ch.text,v->{if(!v.isEmpty())ch.text=v;});break;
                case 1:pickNode("Choice leads to",true,id->ch.target=id);break;
                case 2:editCond(ch.cond);break;
                case 3:if(index>0){n.choices.remove(index);n.choices.add(index-1,ch);}break;
                default:n.choices.remove(ch);
            }});
    }
    private void editCond(Dialogue.Cond c){
        choose("Show this choice only if",Dialogue.COND_KINDS,c.kind,k->{
            c.kind=k;
            Runnable neg=()->choose("When the condition is",new String[]{"True","NOT true"},c.not?1:0,v->c.not=v==1);
            switch(k){
                case Dialogue.C_ITEM:choose("Item",itemNames(),-1,i->{c.s=Rpg.ITEMS.get(i).id;number("How many",Math.max(1,c.n),1,999,v->{c.n=v;neg.run();});});break;
                case Dialogue.C_QUEST:pickQuest(id->{c.s=id;choose("Quest state",Dialogue.QUEST_STATES,Math.max(0,Math.min(3,c.n)),v->{c.n=v;neg.run();});});break;
                case Dialogue.C_GOLD:number("Gold at least",c.n,0,100000,v->{c.n=v;neg.run();});break;
                case Dialogue.C_VAR:text("Variable name","Shared game variable (same as g.name in FSMs)",c.s.isEmpty()?"met_elder":c.s,v->{c.s=v;number("At least",c.n,-9999,9999,x->{c.n=x;neg.run();});});break;
                case Dialogue.C_CLASS:{String[] cl=new String[Rpg.CLASSES.size()];for(int i=0;i<cl.length;i++)cl[i]=Rpg.CLASSES.get(i).name;choose("Hero class",cl,-1,i->{c.s=cl[i];neg.run();});break;}
                case Dialogue.C_LEVEL:number("Hero level at least",Math.max(1,c.n),1,20,v->{c.n=v;neg.run();});break;
                default:c.not=false;
            }});
    }
    private void pickQuest(java.util.function.Consumer<String> set){
        if(project.quests.isEmpty()){hint("Create a quest in the Quests tab first");return;}
        String[] q=new String[project.quests.size()];for(int i=0;i<q.length;i++)q[i]=project.quests.get(i).title;
        choose("Quest",q,-1,i->set.accept(project.quests.get(i).id));
    }
    private void editEffect(Dialogue.Effect e){
        switch(e.kind){
            case Dialogue.E_GIVE_ITEM:case Dialogue.E_TAKE_ITEM:choose("Item",itemNames(),-1,i->{e.s=Rpg.ITEMS.get(i).id;number("How many",Math.max(1,e.n),1,999,v->e.n=v);});break;
            case Dialogue.E_GIVE_GOLD:case Dialogue.E_TAKE_GOLD:number("Gold",e.n,0,100000,v->e.n=v);break;
            case Dialogue.E_START_QUEST:case Dialogue.E_COMPLETE_QUEST:case Dialogue.E_FAIL_QUEST:pickQuest(id->e.s=id);break;
            case Dialogue.E_QUEST_STAGE:pickQuest(id->{e.s=id;Dialogue.QuestDef q=project.questDef(id);int n=q==null?1:Math.max(1,q.stages.size());
                String[] st=new String[n];for(int i=0;i<n;i++)st[i]=(i+1)+". "+(q==null||q.stages.isEmpty()?"Stage "+(i+1):q.stages.get(i));choose("Stage",st,Math.max(0,e.n-1),v->e.n=v+1);});break;
            case Dialogue.E_SET_VAR:case Dialogue.E_ADD_VAR:text("Variable name","Shared game variable (same as g.name in FSMs)",e.s.isEmpty()?"met_elder":e.s,v->{e.s=v;number(e.kind==Dialogue.E_SET_VAR?"Set to":"Add",e.n,-9999,9999,x->e.n=x);});break;
            case Dialogue.E_XP:number("XP",e.n<=1?50:e.n,0,100000,v->e.n=v);break;
            case Dialogue.E_LOOT:{String[] l=new String[Rpg.LOOT.size()];for(int i=0;i<l.length;i++)l[i]=Rpg.LOOT.get(i).name;choose("Loot table",l,-1,i->e.s=Rpg.LOOT.get(i).id);break;}
            case Dialogue.E_SHOP:{String[] l=new String[Rpg.SHOPS.size()];for(int i=0;i<l.length;i++)l[i]=Rpg.SHOPS.get(i).name;choose("Shop",l,-1,i->e.s=Rpg.SHOPS.get(i).id);break;}
            case Dialogue.E_EVENT:text("Event sent to the speaker's FSM",null,e.s.isEmpty()?"TALKED":e.s,v->e.s=v.toUpperCase(java.util.Locale.US));break;
            default:changed();
        }
    }
    private void questProps(Dialogue.QuestDef q){
        header("Quest");
        prop("Title",q.title,()->text("Quest title",null,q.title,v->{if(!v.isEmpty())q.title=v;}));
        prop("ID (used by actions and saves)",q.id,null);
        header("Stages (journal objectives)");
        for(int i=0;i<q.stages.size();i++){final int k=i;prop("Stage "+(i+1),q.stages.get(i),()->choose("Stage "+(k+1),new String[]{"Edit","Move up","Remove"},-1,a->{
            if(a==0)text("Objective",null,q.stages.get(k),v->q.stages.set(k,v));else if(a==1){if(k>0){String s=q.stages.remove(k);q.stages.add(k-1,s);}}else q.stages.remove(k);}));}
        prop("+ Add stage",q.stages.isEmpty()?"Stage 1 shows when the quest starts":"Move to it with Set Quest Stage",()->text("Objective",null,"",v->{if(!v.isEmpty())q.stages.add(v);}));
        header("Rewards when finished");
        prop("XP",String.valueOf(q.xp),()->number("XP reward",q.xp,0,100000,v->q.xp=v));
        prop("Gold",String.valueOf(q.gold),()->number("Gold reward",q.gold,0,100000,v->q.gold=v));
        Rpg.Item it=Rpg.item(q.rewardItem);
        prop("Item",it==null?"None":(q.rewardCount>1?q.rewardCount+"× ":"")+it.name,()->{String[] items=new String[Rpg.ITEMS.size()+1];items[0]="None";for(int i=0;i<Rpg.ITEMS.size();i++)items[i+1]=Rpg.ITEMS.get(i).name;
            choose("Reward item",items,-1,k->{if(k==0)q.rewardItem="";else{q.rewardItem=Rpg.ITEMS.get(k-1).id;number("How many",q.rewardCount,1,99,v->q.rewardCount=v);}});});
        header("Use it");
        prop("Start","Begin Quest action, or a conversation effect",null);
        prop("Finish","Finish Quest action gives these rewards",null);
        Prop del=prop("Delete quest","Actions that use it keep working without rewards",()->{if(host!=null)host.confirm("Delete “"+q.title+"”?",null,"Delete",()->{project.quests.remove(q);questSel=Math.max(0,questSel-1);changed();});});del.danger=true;
    }

    // ================================================================== touch
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){
            downX=lastX=x;downY=lastY=y;moved=false;drag=null;downHit=ui.hitAt(x,y);zone=0;
            if(inspR.contains(x,y))zone=2;
            else if(tab==1&&listR.contains(x,y))zone=3;
            else if(tab==0&&canvasR.contains(x,y)&&downHit<0){zone=1;Dialogue.Node n=nodeAt(x,y);if(n!=null){drag=n;if(node!=n)inspScroll=0;node=n;talk.nodes.remove(n);talk.nodes.add(n);}}
            invalidate();return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(!moved&&Math.abs(x-downX)+Math.abs(y-downY)>dp(7))moved=true;
            if(moved){
                if(zone==2)inspScroll=Math.max(0,inspScroll-(y-lastY));
                else if(zone==3)listScroll=Math.max(0,listScroll-(y-lastY));
                else if(zone==1){if(drag!=null){drag.x+=(x-lastX)/sc();drag.y+=(y-lastY)/sc();}else{camX-=(x-lastX)/sc();camY-=(y-lastY)/sc();}}
                invalidate();}
            lastX=x;lastY=y;return true;
        }
        if(a==MotionEvent.ACTION_UP){
            if(zone==1&&moved&&drag!=null)changed();
            if(!moved){int hid=ui.hitAt(x,y);
                if(hid>=0&&hid==downHit)tap(hid);
                else if(zone==1&&drag==null&&hid<0){node=null;inspScroll=0;}}
            drag=null;undo.commit();invalidate();return true;
        }
        return true;
    }
    private void tap(int hid){
        if(hid==H_BACK){if(host!=null)host.exit();return;}
        if(hid==H_SAVE){if(host!=null)host.saved();return;}
        if(hid==H_UNDO||hid==H_REDO){undoRedo(hid==H_UNDO);return;}
        if(hid>=H_TAB&&hid<H_TAB+TABS.length){tab=hid-H_TAB;inspScroll=0;node=null;return;}
        if(hid>=H_QROW&&hid<H_QROW+800){questSel=hid-H_QROW;inspScroll=0;return;}
        if(hid>=H_PROP&&hid<H_PROP+props.size()){Prop pr=props.get(hid-H_PROP);if(pr.tap!=null)pr.tap.run();return;}
        switch(hid){
            case H_ZIN:zoom=Math.min(2f,zoom*1.25f);break;
            case H_ZOUT:zoom=Math.max(.35f,zoom/1.25f);break;
            case H_FIT:fit();break;
            case H_PICK:pickTalk();break;
            case H_ADDNODE:if(talk!=null){float cx=camX+canvasR.width()/sc()*.5f-NODE_W*.5f,cy=camY+canvasR.height()/sc()*.4f;Dialogue.Node n=talk.add("",cx,cy);node=n;inspScroll=0;changed();
                text("What they say",null,"",v->n.text=v);}break;
            case H_NEWQ:{Dialogue.QuestDef q=new Dialogue.QuestDef();q.title="New quest";q.id=project.uniqueQuestId("quest");q.stages.add("Speak to the village elder.");project.quests.add(q);questSel=project.quests.size()-1;inspScroll=0;changed();
                text("Quest title",null,"",v->{if(!v.isEmpty()){q.title=v;}});break;}
        }
    }
    private void pickTalk(){
        if(host==null)return;
        String[] items=new String[project.dialogues.size()+1];for(int i=0;i<project.dialogues.size();i++)items[i]=project.dialogues.get(i).name+"  ·  "+project.dialogues.get(i).nodes.size()+" lines";
        items[items.length-1]="+ New conversation";
        host.choose("Conversations",items,talk==null?-1:project.dialogues.indexOf(talk),k->{
            if(k==project.dialogues.size()){host.prompt("Conversation name","e.g. Elder Maren, Blacksmith, Gate guard","",false,t->{String nm=t.trim().isEmpty()?"Conversation "+(project.dialogues.size()+1):t.trim();
                Dialogue.Def d=Dialogue.starter(project.uniqueTalkId(nm),nm);project.dialogues.add(d);talk=d;node=null;fitted=false;changed();
                hint("Give an NPC the Talk (Conversation) action to use it");});}
            else{talk=project.dialogues.get(k);node=null;fitted=false;inspScroll=0;}
            invalidate();});
    }
}
