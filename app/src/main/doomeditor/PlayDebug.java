package com.retro.doomeditor;

import android.graphics.Canvas;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Map;

/**
 * Testing tools for Play mode (editor testing only, never in shared games): cheats, a live list of every
 * entity with its FSM state and variables, the shared game variables and quests, and overlay options.
 * The game is paused while the panel is open.
 */
final class PlayDebug {
    interface Host {UiHost ui();void restartMap();void hint(String s);}
    static final String[] TABS={"Cheats","Entities","Variables","Overlay"};
    private static final int H_CLOSE=1,H_TAB=10,H_CHEAT=100,H_ENT=300,H_ENT_ACT=700,H_VAR=900,H_ADDVAR=899,H_QUEST=1200,H_OPT=1500;
    private final Ui ui;private final Host host;private final Project project;
    boolean open;int tab;
    /** Overlay options, also used while the panel is closed. */
    static boolean showStates,showFps;
    private float scroll,contentH;private Actor expanded;
    private final RectF r=new RectF(),list=new RectF();

    PlayDebug(Ui ui,Project project,Host host){this.ui=ui;this.project=project;this.host=host;}
    private float dp(float v){return ui.dp(v);}
    void toggle(World w){open=!open;scroll=0;if(w!=null)w.paused=open;}
    void close(World w){if(!open)return;open=false;if(w!=null)w.paused=false;}

    private static final String[] CHEATS={"God mode","Full heal + spell points","+100 gold","Level up","Give an item…","Give all keys","Go to map…","Kill every monster here","Finish active quests","Restart this map"};
    void draw(Canvas c,float w,float h,World world){
        ui.rect(c,0,0,w,h,0xd80c0e12);
        float L=dp(14),R=w-dp(14),T=dp(12);
        ui.round(c,L,T,L+dp(84),T+dp(30),dp(15),0xff8a3030);ui.textMid(c,"DEBUG",L+dp(42),T+dp(15),11,0xffffffff,2,0);
        float tx=L+dp(96);
        for(int i=0;i<TABS.length;i++){r.set(tx,T-dp(2),tx+dp(104),T+dp(32));ui.button(c,r,-1,TABS[i],tab==i?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_TAB+i);tx+=dp(110);}
        r.set(R-dp(40),T-dp(2),R,T+dp(32));ui.button(c,r,Icons.CANCEL,null,Ui.NORMAL);ui.hit(r,H_CLOSE);
        list.set(L,T+dp(44),R,h-dp(10));
        c.save();c.clipRect(list);
        float y=list.top-scroll;
        Hero hero=world.hero;
        switch(tab){
            case 0:{
                int cols=w>dp(700)?3:2;float gap=dp(8),cw=(R-L-gap*(cols-1))/cols;
                for(int i=0;i<CHEATS.length;i++){float x=L+(i%cols)*(cw+gap),yy=y+(i/cols)*dp(50);r.set(x,yy,x+cw,yy+dp(44));
                    boolean on=i==0&&world.godMode;ui.button(c,r,-1,CHEATS[i]+(i==0?(on?": ON":": off"):""),on?Ui.SUCCESS:Ui.NORMAL);hitIn(r,H_CHEAT+i);}
                y+=((CHEATS.length+cols-1)/cols)*dp(50)+dp(10);
                if(hero!=null){ui.text(c,hero.cls.name+" · level "+hero.level+" · "+world.player.health+"/"+hero.maxHp+" HP · "+hero.sp+"/"+hero.maxSp+" SP · "+hero.gold+" gold · "+hero.xp+" XP",L,y+dp(14),12,Ui.TEXT_2);y+=dp(22);}
                ui.text(c,"Map: "+project.mapName(world.mapNum)+String.format(java.util.Locale.US,"  ·  hero at %.1f, %.1f  ·  %d entities",world.player.x,world.player.y,world.actors.size()),L,y+dp(14),12,Ui.TEXT_3);y+=dp(26);
                break;}
            case 1:{
                ArrayList<Actor> as=entities(world);
                if(as.isEmpty())ui.text(c,"No entities on this map.",L,y+dp(18),13,Ui.TEXT_3);
                for(int i=0;i<as.size();i++){Actor a=as.get(i);boolean ex=a==expanded;
                    String st=a.fsm==null||a.fsm.state==null?"—":a.fsm.state.name;float d=(float)Math.hypot(a.x-world.player.x,a.y-world.player.y);
                    float rh=ex?dp(52)+Math.max(1,a.fsm==null?0:a.fsm.vars.size())*dp(18)+dp(52):dp(48);
                    r.set(L,y,R,y+rh-dp(6));ui.round(c,r,dp(8),ex?0xff26324a:Ui.SURFACE_2);if(ex)ui.outline(c,r,dp(8),Ui.ACCENT,dp(1.5f));
                    int col=!a.alive?Ui.TEXT_3:a.def!=null&&a.def.kind==EntityDef.MONSTER?0xffff8a8a:Ui.TEXT;
                    ui.text(c,ui.ellipsize((a.def==null?"?":a.def.name)+(a.alive?"":" (dead)"),13,1,(R-L)*.4f),L+dp(12),y+dp(19),13,col,1,-1);
                    ui.text(c,String.format(java.util.Locale.US,"%d HP · %.1f away",a.health,d),L+dp(12),y+dp(35),10.5f,Ui.TEXT_3,0,-1);
                    ui.text(c,ui.ellipsize("State: "+st,12,1,(R-L)*.5f),R-dp(12),y+dp(19),12,0xff8ad06a,1,1);
                    if(a.fsm!=null)ui.text(c,ui.ellipsize("last event: "+(a.fsm.lastEvent.isEmpty()?"—":a.fsm.lastEvent)+(a.target!=null?" · targeting "+(a.target.isPlayer?"the hero":a.target.def==null?"?":a.target.def.name):""),10.5f,0,(R-L)*.5f),R-dp(12),y+dp(35),10.5f,Ui.TEXT_3,0,1);
                    if(!ex)hitIn(r,H_ENT+i);
                    else{float yy=y+dp(52);
                        if(a.fsm==null||a.fsm.vars.isEmpty()){ui.text(c,"No local variables",L+dp(24),yy+dp(12),11,Ui.TEXT_3);yy+=dp(18);}
                        else for(Map.Entry<String,Float> e:a.fsm.vars.entrySet()){ui.text(c,e.getKey()+" = "+fmt(e.getValue()),L+dp(24),yy+dp(12),11.5f,Ui.TEXT_2,1,-1);yy+=dp(18);}
                        String[] acts={"Teleport hero here","Kill","Send event…","Close"};float bw=(R-L-dp(24)-dp(18))/4f;
                        for(int k=0;k<4;k++){RectF b=new RectF(L+dp(12)+k*(bw+dp(6)),yy+dp(4),L+dp(12)+k*(bw+dp(6))+bw,yy+dp(38));ui.button(c,b,-1,acts[k],k==1?Ui.DANGER:Ui.NORMAL);hitIn(b,H_ENT_ACT+k);}}
                    y+=rh;}
                break;}
            case 2:{
                ui.caption(c,"Shared variables (g.name in FSMs, and conversation variables)",L,y+dp(12),Ui.TEXT_3);y+=dp(20);
                ArrayList<String> keys=new ArrayList<>(world.globals.keySet());java.util.Collections.sort(keys);
                if(keys.isEmpty()){ui.text(c,"None set yet.",L,y+dp(14),12,Ui.TEXT_3);y+=dp(24);}
                for(int i=0;i<keys.size();i++){r.set(L,y,R,y+dp(36));ui.round(c,r,dp(7),Ui.SURFACE_2);ui.textMid(c,"g."+keys.get(i),L+dp(12),r.centerY(),12.5f,Ui.TEXT,1,-1);
                    ui.textMid(c,fmt(world.globals.get(keys.get(i))),R-dp(14),r.centerY(),12.5f,0xff8ad06a,2,1);hitIn(r,H_VAR+i);y+=dp(42);}
                r.set(L,y,L+dp(190),y+dp(34));ui.button(c,r,Icons.PLUS,"Set a variable",Ui.NORMAL);hitIn(r,H_ADDVAR);y+=dp(46);
                ui.caption(c,"Quests (tap to change state)",L,y+dp(12),Ui.TEXT_3);y+=dp(20);
                ArrayList<World.Quest> qs=new ArrayList<>(world.quests.values());
                if(qs.isEmpty()){ui.text(c,"No quests started yet.",L,y+dp(14),12,Ui.TEXT_3);y+=dp(24);}
                for(int i=0;i<qs.size();i++){World.Quest q=qs.get(i);r.set(L,y,R,y+dp(40));ui.round(c,r,dp(7),Ui.SURFACE_2);
                    ui.text(c,ui.ellipsize(q.title+"  ("+q.id+")",12.5f,1,(R-L)*.6f),L+dp(12),y+dp(17),12.5f,Ui.TEXT,1,-1);ui.text(c,ui.ellipsize(q.text==null?"":q.text,10.5f,0,(R-L)*.6f),L+dp(12),y+dp(32),10.5f,Ui.TEXT_3,0,-1);
                    ui.textMid(c,Dialogue.QUEST_STATES[Math.max(0,Math.min(3,q.state))]+" · stage "+(q.stage+1),R-dp(14),r.centerY(),12,0xffd8a830,1,1);hitIn(r,H_QUEST+i);y+=dp(46);}
                break;}
            default:{
                String[] opts={"Show FSM states above entities","Show frames per second"};boolean[] on={showStates,showFps};
                for(int i=0;i<opts.length;i++){r.set(L,y,R,y+dp(44));ui.round(c,r,dp(7),Ui.SURFACE_2);ui.textMid(c,opts[i],L+dp(12),r.centerY(),13,Ui.TEXT,1,-1);
                    float tx2=R-dp(48),ty=r.centerY()-dp(8);ui.round(c,tx2,ty,tx2+dp(32),ty+dp(16),dp(8),on[i]?Ui.ACCENT:Ui.SURFACE_3);ui.p.setColor(0xffffffff);c.drawCircle(on[i]?tx2+dp(24):tx2+dp(8),ty+dp(8),dp(6),ui.p);
                    hitIn(r,H_OPT+i);y+=dp(50);}
                ui.text(c,"The DEBUG button only appears while testing from the editor, never in shared games.",L,y+dp(14),11.5f,Ui.TEXT_3);y+=dp(24);
            }
        }
        contentH=y+scroll-list.top;c.restore();
        float max=Math.max(0,contentH-list.height());if(scroll>max)scroll=max;
    }
    private void hitIn(RectF rr,int id){if(rr.bottom<list.top||rr.top>list.bottom)return;ui.hit(rr.left,Math.max(rr.top,list.top),rr.right,Math.min(rr.bottom,list.bottom),id);}
    private static String fmt(Float v){if(v==null)return "0";float f=v;return f==Math.round(f)?String.valueOf(Math.round(f)):String.format(java.util.Locale.US,"%.2f",f);}
    private ArrayList<Actor> entities(World w){
        ArrayList<Actor> out=new ArrayList<>();for(Actor a:w.actors)if(!a.isPlayer&&!a.removed&&a.projectileType<0&&a.def!=null)out.add(a);
        final float px=w.player.x,py=w.player.y;out.sort((a,b)->Float.compare((float)Math.hypot(a.x-px,a.y-py),(float)Math.hypot(b.x-px,b.y-py)));return out;
    }
    void scrollBy(float dy){scroll=Math.max(0,scroll-dy);}

    /** Returns true if the panel used the tap. */
    boolean tap(int hid,World world){
        if(!open)return false;
        UiHost u=host.ui();Hero hero=world.hero;
        if(hid==H_CLOSE){close(world);return true;}
        if(hid>=H_TAB&&hid<H_TAB+TABS.length){tab=hid-H_TAB;scroll=0;expanded=null;return true;}
        if(hid>=H_CHEAT&&hid<H_CHEAT+CHEATS.length){
            switch(hid-H_CHEAT){
                case 0:world.godMode=!world.godMode;host.hint(world.godMode?"God mode on: the hero takes no damage":"God mode off");break;
                case 1:if(hero!=null){world.player.health=hero.maxHp;hero.sp=hero.maxSp;}host.hint("Healed");break;
                case 2:if(hero!=null)hero.gold+=100;break;
                case 3:if(hero!=null&&hero.level<Rpg.MAX_LEVEL){hero.xp=Math.max(hero.xp,Rpg.XP_TABLE[hero.level]);hero.levelUp();world.player.health=hero.maxHp;host.hint("Now level "+hero.level);}break;
                case 4:if(u!=null){String[] n=new String[Rpg.ITEMS.size()];for(int i=0;i<n.length;i++)n[i]=Rpg.ITEMS.get(i).name+"  ·  "+Rpg.ITEM_KINDS[Rpg.ITEMS.get(i).kind];
                    u.choose("Give item",n,-1,k->{world.giveItem(Rpg.ITEMS.get(k),1);host.hint("Gave "+Rpg.ITEMS.get(k).name);});}break;
                case 5:for(Rpg.Item it:Rpg.ITEMS)if(it.kind==Rpg.I_KEY&&hero!=null&&!hero.hasKey(it.key))world.giveItem(it,1);host.hint("All keys given");break;
                case 6:if(u!=null){project.ensureMaps();String[] n=new String[project.maps.size()];for(int i=0;i<n.length;i++)n[i]=project.maps.get(i).name;
                    u.choose("Go to map",n,-1,k->{close(world);world.paused=false;world.goToMap(project.maps.get(k).num,"");});}break;
                case 7:{int n=0;for(Actor a:new ArrayList<>(world.actors))if(a.alive&&a.def!=null&&a.def.kind==EntityDef.MONSTER){world.damage(a,100000,world.player);n++;}host.hint(n+" monsters defeated");break;}
                case 8:{int n=0;for(World.Quest q:world.quests.values())if(q.state==World.Q_ACTIVE){world.questFinish(q.id);n++;}host.hint(n+" quests finished");break;}
                default:close(world);host.restartMap();
            }
            return true;}
        if(hid>=H_ENT&&hid<H_ENT+400){ArrayList<Actor> as=entities(world);int i=hid-H_ENT;if(i<as.size())expanded=as.get(i);return true;}
        if(hid>=H_ENT_ACT&&hid<H_ENT_ACT+4&&expanded!=null){Actor a=expanded;
            switch(hid-H_ENT_ACT){
                case 0:{float ang=(float)Math.atan2(a.y-world.player.y,a.x-world.player.x);world.teleport(world.player,a.x-(float)Math.cos(ang)*1.2f,a.y-(float)Math.sin(ang)*1.2f);world.player.angle=ang;close(world);break;}
                case 1:if(a.alive)world.damage(a,100000,world.player);break;
                case 2:if(u!=null&&a.fsm!=null)u.prompt("Send event to "+(a.def==null?"entity":a.def.name),"e.g. USED, DAMAGED, ATTACK","USED",false,t->{if(!t.trim().isEmpty())a.fsm.send(t.trim().toUpperCase(java.util.Locale.US));});break;
                default:expanded=null;
            }
            return true;}
        if(hid>=H_VAR&&hid<H_VAR+300&&u!=null){ArrayList<String> keys=new ArrayList<>(world.globals.keySet());java.util.Collections.sort(keys);int i=hid-H_VAR;if(i<keys.size()){String k=keys.get(i);
            u.prompt("g."+k,"New value",fmt(world.globals.get(k)),true,t->{try{world.globals.put(k,Float.parseFloat(t.trim()));}catch(NumberFormatException ignored){}});}return true;}
        if(hid==H_ADDVAR&&u!=null){u.prompt("Variable name","Without the g. prefix","",false,n->{String k=n.trim().startsWith("g.")?n.trim().substring(2):n.trim();if(k.isEmpty())return;
            u.prompt("g."+k,"Value","1",true,t->{try{world.globals.put(k,Float.parseFloat(t.trim()));}catch(NumberFormatException ignored){}});});return true;}
        if(hid>=H_QUEST&&hid<H_QUEST+200&&u!=null){ArrayList<World.Quest> qs=new ArrayList<>(world.quests.values());int i=hid-H_QUEST;if(i<qs.size()){World.Quest q=qs.get(i);
            u.choose(q.title,Dialogue.QUEST_STATES,q.state,k->q.state=k);}return true;}
        if(hid==H_OPT){showStates=!showStates;return true;}
        if(hid==H_OPT+1){showFps=!showFps;return true;}
        return true;
    }
}
