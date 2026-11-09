package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/**
 * Content editor: the game's RPG data. Items, Spells, Classes, Loot tables and Shops each have a list on
 * the left and an inspector on the right. Everything here is what the game, the inventory, the hero's
 * starting gear and the FSM actions (Give Item, Cast Spell, Give Loot, Open Shop...) use.
 */
public class ContentEditorView extends View implements Pointer.Aware {
    @Override public Ui pointerUi(){return ui;}

    interface Host extends UiHost {void exit();void saved();}
    static final String[] TABS={"Items","Spells","Classes","Loot","Shops"};
    private static final int[] TAB_ICONS={Icons.SWORD,Icons.SPARKLE,Icons.PERSON,Icons.PACKAGE,Icons.COIN};
    private static final int H_BACK=1,H_SAVE=2,H_NEW=3,H_RESET=4,H_DUP=5,H_DEL=6,H_TEST=7,H_UNDO=8,H_REDO=9,H_TAB=10,H_ROW=100,H_PROP=1000;

    private final Project project;private final Host host;private final Ui ui;private final Paint p;
    private int tab;private int sel;
    private float w,h,barH,leftScroll,rightScroll,leftContent,rightContent;
    private final RectF r=new RectF(),leftRect=new RectF(),rightRect=new RectF();
    private String hint;private long hintUntil;
    private final java.util.Random rng=new java.util.Random();
    private final java.util.HashMap<Integer,Bitmap> thumbs=new java.util.HashMap<>();
    // touch
    private float downX,downY,lastY;private boolean moved;private int downHit=-1,zone;

    /** One inspector row: a label, its value and what tapping it does (null = read only). */
    private static final class Prop {String label,value;Runnable tap;boolean toggle,on,header,danger;}
    private final ArrayList<Prop> props=new ArrayList<>();

    ContentEditorView(Context c,Project project,int startTab,Host host){
        super(c);this.project=project;this.host=host;ui=new Ui(c);p=ui.p;tab=Math.max(0,Math.min(TABS.length-1,startTab));
        project.ensureContent();Rpg.use(project);
        undo=new Undo(()->project.section(Project.SEC_CONTENT),t->project.restoreSection(Project.SEC_CONTENT,t));undo.reset();
    }
    final Undo undo;
    /** After a dialog: records an undo step if something changed. */
    void afterEdit(){undo.commit();invalidate();}
    private void undoRedo(boolean back){
        if(back?undo.undo():undo.redo()){sel=Math.max(0,Math.min(Math.max(0,count()-1),sel));project.save(getContext());hint(back?"Undo":"Redo");}else hint(back?"Nothing to undo":"Nothing to redo");}
    private float dp(float v){return ui.dp(v);}
    private void hint(String s){hint=s;hintUntil=System.currentTimeMillis()+2600;invalidate();}
    private void changed(){undo.commit();project.save(getContext());invalidate();}
    private Bitmap thumb(int sprite){Bitmap b=thumbs.get(sprite);if(b==null){b=Sprites.preview(sprite);thumbs.put(sprite,b);}return b;}

    // ------------------------------------------------------------------ current list
    private int count(){switch(tab){case 0:return project.items.size();case 1:return project.spells.size();case 2:return project.classes.size();case 3:return project.loot.size();default:return project.shops.size();}}
    private String nameAt(int i){switch(tab){case 0:return project.items.get(i).name;case 1:return project.spells.get(i).name;case 2:return project.classes.get(i).name;case 3:return project.loot.get(i).name;default:return project.shops.get(i).name;}}
    private String subAt(int i){
        switch(tab){
            case 0:{Rpg.Item it=project.items.get(i);return Rpg.ITEM_KINDS[it.kind]+" · "+it.stats();}
            case 1:{Rpg.Spell s=project.spells.get(i);return (s.level==0?"Cantrip":"Level "+s.level)+" · "+s.dice+" "+Rpg.DAMAGE_TYPES[s.damageType].toLowerCase();}
            case 2:{Rpg.HeroClass c=project.classes.get(i);return "d"+c.hitDie+" · "+Rpg.ABILITIES[c.primary]+(c.castAbility>=0?" · casts with "+Rpg.ABILITIES[c.castAbility]:"");}
            case 3:{Rpg.LootTable t=project.loot.get(i);return t.entries.size()+" items · "+t.gold+" gold";}
            default:{Rpg.Shop s=project.shops.get(i);return s.stock.size()+" items · buys at "+s.sellRate+"%";}
        }
    }

    // ================================================================== drawing
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();barH=dp(48);
        if(sel>=count())sel=Math.max(0,count()-1);
        ui.clearHits();c.drawColor(Ui.BG);
        float lw=Math.max(dp(190),Math.min(dp(280),w*.32f));
        leftRect.set(dp(10),barH+dp(10),dp(10)+lw,h-dp(10));rightRect.set(leftRect.right+dp(14),barH+dp(10),w-dp(12),h-dp(10));
        drawList(c);drawInspector(c);drawTopBar(c);
        if(hint!=null&&System.currentTimeMillis()<hintUntil){float tw=Math.min(w-dp(20),ui.measure(hint,12,1)+dp(32)),cx=w*.5f,b=h-dp(16);
            ui.pill(c,cx-tw*.5f,b-dp(32),cx+tw*.5f,b,0xf02a313a);ui.textMid(c,ui.ellipsize(hint,12,1,tw-dp(20)),cx,b-dp(16),12,Ui.TEXT,1,0);postInvalidateDelayed(300);}
    }
    private void drawTopBar(Canvas c){
        ui.rect(c,0,0,w,barH,Ui.SURFACE);ui.hline(c,0,w,barH-dp(1),Ui.BORDER);
        float cy=barH*.5f,bh=dp(34),top=cy-bh*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        boolean wide=w>dp(860);
        float ib=dp(28);ui.round(c,dp(50),cy-ib*.5f,dp(50)+ib,cy+ib*.5f,dp(7),0xffc0703a);ui.icon(c,Icons.BACKPACK,dp(50)+ib*.5f,cy,16,0xffffffff);
        float tl=dp(86);if(wide){ui.textMid(c,"Content",tl,cy,15,Ui.TEXT,1,-1);tl+=dp(78);}
        float saveW=wide?dp(84):bh;r.set(w-dp(10)-saveW,top,w-dp(10),top+bh);ui.button(c,r,Icons.SAVE,wide?"Save":null,Ui.PRIMARY);ui.hit(r,H_SAVE);
        float ux=Undo.buttons(ui,c,r,undo,w-dp(16)-saveW,top,bh,H_UNDO,H_REDO);
        float avail=ux-tl-dp(4),tw=Math.min(dp(124),avail/TABS.length);
        for(int i=0;i<TABS.length;i++){r.set(tl+i*tw,top,tl+(i+1)*tw-dp(5),top+bh);ui.button(c,r,TAB_ICONS[i],TABS[i],i==tab?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_TAB+i);}
    }
    private void hitIn(RectF rr,int id,RectF area){if(rr.bottom<area.top||rr.top>area.bottom)return;ui.hit(rr.left,Math.max(rr.top,area.top),rr.right,Math.min(rr.bottom,area.bottom),id);}
    private void drawList(Canvas c){
        RectF A=leftRect;ui.panel(c,A.left-dp(2),A.top-dp(2),A.right+dp(2),A.bottom+dp(2),dp(10));
        c.save();c.clipRect(A);
        float y=A.top+dp(8)-leftScroll,l=A.left+dp(8),rr=A.right-dp(8);
        String[] news={"New item","New spell","New class","New loot table","New shop"};
        r.set(l,y,rr,y+dp(36));ui.button(c,r,Icons.PLUS,news[tab],Ui.PRIMARY);hitIn(r,H_NEW,A);y+=dp(44);
        ui.caption(c,TABS[tab]+" ("+count()+")",l,y+dp(6),Ui.TEXT_3);y+=dp(14);
        for(int i=0;i<count();i++){r.set(l,y,rr,y+dp(46));boolean on=i==sel;
            ui.round(c,r,dp(8),on?0xff26324a:Ui.SURFACE_2);if(on)ui.outline(c,r,dp(8),Ui.ACCENT,dp(1.5f));
            float tx=l+dp(10);
            if(tab==0){Rpg.Item it=project.items.get(i);if(it.sprite>=0){Bitmap b=thumb(it.sprite);float s=dp(30),tw=Math.min(s*1.3f,s*b.getWidth()/(float)Math.max(1,b.getHeight()));c.drawBitmap(b,null,new RectF(l+dp(6),r.centerY()-s*.5f,l+dp(6)+tw,r.centerY()+s*.5f),null);}tx=l+dp(46);}
            else{int ic=tab==1?Icons.SPARKLE:tab==2?Icons.PERSON:tab==3?Icons.PACKAGE:Icons.COIN;ui.icon(c,ic,l+dp(20),r.centerY(),15,on?0xff8fbcff:Ui.TEXT_2);tx=l+dp(38);}
            ui.text(c,ui.ellipsize(nameAt(i),12.5f,1,rr-tx-dp(6)),tx,r.centerY()-dp(2),12.5f,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize(subAt(i),10,0,rr-tx-dp(6)),tx,r.centerY()+dp(13),10,Ui.TEXT_3,0,-1);
            hitIn(r,H_ROW+i,A);y+=dp(52);}
        y+=dp(6);r.set(l,y,rr,y+dp(32));ui.button(c,r,Icons.RESET,"Reset "+TABS[tab].toLowerCase()+" to built-in",Ui.NORMAL);hitIn(r,H_RESET,A);y+=dp(40);
        leftContent=y+leftScroll-A.top;c.restore();
        float max=Math.max(0,leftContent-A.height());if(leftScroll>max){leftScroll=max;invalidate();}
    }
    private void drawInspector(Canvas c){
        RectF A=rightRect;
        if(count()==0){ui.text(c,"Nothing here yet – tap “+ New”.",A.centerX(),A.centerY(),13,Ui.TEXT_3,1,0);return;}
        buildProps();
        c.save();c.clipRect(A);
        float y=A.top-rightScroll,l=A.left,rr=A.right;
        ui.text(c,ui.ellipsize(nameAt(sel),17,1,rr-l-dp(180)),l,y+dp(20),17,Ui.TEXT,1,-1);
        ui.text(c,ui.ellipsize(subAt(sel),11,0,rr-l-dp(180)),l,y+dp(38),11,Ui.TEXT_3,0,-1);
        float bx=rr;
        r.set(bx-dp(40),y+dp(4),bx,y+dp(38));ui.button(c,r,Icons.TRASH,null,Ui.DANGER);hitIn(r,H_DEL,A);bx-=dp(46);
        r.set(bx-dp(40),y+dp(4),bx,y+dp(38));ui.button(c,r,Icons.COPY,null,Ui.NORMAL);hitIn(r,H_DUP,A);bx-=dp(46);
        if(tab==3){r.set(bx-dp(96),y+dp(4),bx,y+dp(38));ui.button(c,r,Icons.DICE,"Test roll",Ui.ACTIVE);hitIn(r,H_TEST,A);}
        y+=dp(52);
        int cols=rr-l>dp(520)?2:1;float gap=dp(8),cw=(rr-l-gap*(cols-1))/cols;int col=0;
        for(int i=0;i<props.size();i++){Prop pr=props.get(i);
            if(pr.header){if(col!=0){y+=dp(52);col=0;}ui.caption(c,pr.label,l,y+dp(12),Ui.TEXT_3);y+=dp(20);continue;}
            float x=l+col*(cw+gap);r.set(x,y,x+cw,y+dp(46));ui.round(c,r,dp(7),Ui.SURFACE_2);
            ui.text(c,ui.ellipsize(pr.label,10.5f,1,cw-dp(20)),x+dp(10),y+dp(16),10.5f,Ui.TEXT_3,1,-1);
            if(pr.toggle){float tx=x+cw-dp(44),ty=y+dp(15);ui.round(c,tx,ty,tx+dp(32),ty+dp(16),dp(8),pr.on?Ui.ACCENT:Ui.SURFACE_3);p.setColor(0xffffffff);c.drawCircle(pr.on?tx+dp(24):tx+dp(8),ty+dp(8),dp(6),p);
                ui.text(c,pr.on?"Yes":"No",x+dp(10),y+dp(36),12.5f,Ui.TEXT,1,-1);}
            else{ui.text(c,ui.ellipsize(pr.value,12.5f,1,cw-dp(40)),x+dp(10),y+dp(36),12.5f,pr.danger?0xffff8a8a:pr.tap==null?Ui.TEXT_2:Ui.TEXT,1,-1);
                if(pr.tap!=null)ui.icon(c,Icons.CHEVRON_DOWN,x+cw-dp(16),y+dp(23),13,Ui.TEXT_3);}
            if(pr.tap!=null)hitIn(r,H_PROP+i,A);
            col++;if(col>=cols){col=0;y+=dp(52);}
        }
        if(col!=0)y+=dp(52);
        rightContent=y+rightScroll-A.top+dp(10);c.restore();
        float max=Math.max(0,rightContent-A.height());if(rightScroll>max){rightScroll=max;invalidate();}
    }

    // ------------------------------------------------------------------ inspector rows
    private Prop prop(String label,String value,Runnable tap){Prop pr=new Prop();pr.label=label;pr.value=value;pr.tap=tap;props.add(pr);return pr;}
    private void header(String label){Prop pr=new Prop();pr.label=label;pr.header=true;props.add(pr);}
    private void toggle(String label,boolean on,Runnable flip){Prop pr=prop(label,"",flip);pr.toggle=true;pr.on=on;}
    private void text(String title,String cur,java.util.function.Consumer<String> set){if(host!=null)host.prompt(title,null,cur,false,t->{set.accept(t.trim());changed();});}
    private void number(String title,int cur,int min,int max,java.util.function.IntConsumer set){
        if(host!=null)host.prompt(title,min+" to "+max,String.valueOf(cur),true,t->{try{set.accept(Math.max(min,Math.min(max,Math.round(Float.parseFloat(t.trim())))));changed();}catch(NumberFormatException ignored){}});}
    private void decimal(String title,float cur,float min,float max,java.util.function.Consumer<Float> set){
        if(host!=null)host.prompt(title,min+" to "+max,trim(cur),true,t->{try{set.accept(Math.max(min,Math.min(max,Float.parseFloat(t.trim()))));changed();}catch(NumberFormatException ignored){}});}
    private void dice(String title,String cur,boolean allowEmpty,java.util.function.Consumer<String> set){
        if(host!=null)host.prompt(title,"Dice like 1d8, 2d6+3, 1d4-1 or a number"+(allowEmpty?" (empty = none)":""),cur,false,t->{t=t.trim();
            if(t.isEmpty()&&allowEmpty){set.accept("");changed();return;}
            if(!t.matches("(?i)\\s*\\d*\\s*d?\\s*\\d+\\s*([+-]\\s*\\d+)?\\s*")){hint("“"+t+"” isn't a dice expression");return;}
            set.accept(Rpg.Dice.parse(t).toString());changed();});}
    private void choose(String title,String[] items,int cur,java.util.function.IntConsumer set){if(host!=null)host.choose(title,items,cur,k->{set.accept(k);changed();});}
    private static String trim(float v){String s=String.format(java.util.Locale.US,"%.2f",v);while(s.contains(".")&&(s.endsWith("0")||s.endsWith(".")))s=s.substring(0,s.length()-1);return s;}
    private String[] itemNames(){String[] a=new String[project.items.size()];for(int i=0;i<a.length;i++)a[i]=project.items.get(i).name+"  ·  "+Rpg.ITEM_KINDS[project.items.get(i).kind];return a;}
    private String[] spellNames(){String[] a=new String[project.spells.size()];for(int i=0;i<a.length;i++)a[i]=project.spells.get(i).name;return a;}
    private String itemLabel(String id){Rpg.Item it=Rpg.item(id);return it==null?id+" (missing)":it.name;}
    private String spellLabel(String id){Rpg.Spell s=Rpg.spell(id);return s==null?id+" (missing)":s.name;}
    private String[] spriteNames(){String[] a=new String[Sprites.COUNT+1];a[0]="No picture";for(int i=0;i<Sprites.COUNT;i++)a[i+1]=Sprites.NAMES[i];return a;}

    private void buildProps(){
        props.clear();
        switch(tab){case 0:itemProps(project.items.get(sel));break;case 1:spellProps(project.spells.get(sel));break;case 2:classProps(project.classes.get(sel));break;case 3:lootProps(project.loot.get(sel));break;default:shopProps(project.shops.get(sel));}
    }
    private void itemProps(Rpg.Item it){
        header("Item");
        prop("Name",it.name,()->text("Item name",it.name,t->{if(!t.isEmpty())it.name=t;}));
        prop("Type",Rpg.ITEM_KINDS[it.kind],()->choose("Item type",Rpg.ITEM_KINDS,it.kind,k->it.kind=k));
        prop("Picture",it.sprite<0?"None":Sprites.NAMES[Math.min(Sprites.COUNT-1,it.sprite)],()->choose("Picture",spriteNames(),it.sprite+1,k->it.sprite=k-1));
        prop("Value (gold)",String.valueOf(it.value),()->number("Value in gold",it.value,0,100000,v->it.value=v));
        prop("Description",it.desc==null||it.desc.isEmpty()?"None":it.desc,()->text("Description",it.desc==null?"":it.desc,t->it.desc=t));
        prop("ID (used by saves and actions)",it.id,null);
        switch(it.kind){
            case Rpg.I_WEAPON:header("Weapon");
                prop("Damage",it.damage.toString(),()->dice("Damage dice",it.damage.toString(),false,t->it.damage=Rpg.Dice.parse(t)));
                prop("Damage type",Rpg.DAMAGE_TYPES[it.damageType],()->choose("Damage type",Rpg.DAMAGE_TYPES,it.damageType,k->it.damageType=k));
                prop("First-person model",Rpg.FP_NAMES[it.fp],()->choose("Weapon model in the hero's hand",Rpg.FP_NAMES,it.fp,k->it.fp=k));
                prop("Time between attacks",trim(it.cooldown)+" s",()->decimal("Seconds between attacks",it.cooldown,.2f,3f,v->it.cooldown=v));
                toggle("Finesse (uses DEX if better)",it.finesse,()->{it.finesse=!it.finesse;changed();});
                toggle("Two-handed (no shield)",it.twoHanded,()->{it.twoHanded=!it.twoHanded;changed();});
                toggle("Ranged",it.ranged,()->{it.ranged=!it.ranged;if(it.ranged&&it.reach<6)it.reach=24;if(!it.ranged)it.reach=1.5f;changed();});
                prop(it.ranged?"Range (map units)":"Reach (map units)",trim(it.reach),()->decimal(it.ranged?"Range":"Reach",it.reach,.5f,60,v->it.reach=v));
                break;
            case Rpg.I_ARMOR:header("Armour");
                prop("Armour class",String.valueOf(it.baseAC),()->number("Base armour class",it.baseAC,0,30,v->it.baseAC=v));
                prop("DEX bonus",it.maxDex>=99?"Full":it.maxDex<=0?"None":"Up to +"+it.maxDex,()->choose("DEX bonus to AC",new String[]{"None (heavy)","Up to +1","Up to +2","Up to +3","Full"},it.maxDex>=99?4:Math.max(0,Math.min(3,it.maxDex)),k->it.maxDex=k==4?99:k));
                toggle("Heavy armour (needs heavy armour training)",it.heavy,()->{it.heavy=!it.heavy;changed();});
                break;
            case Rpg.I_SHIELD:header("Shield");
                prop("AC bonus","+"+it.baseAC,()->number("AC bonus",it.baseAC,0,10,v->it.baseAC=v));break;
            case Rpg.I_POTION:header("Potion");
                prop("Heals",it.heal==null?"Nothing":it.heal.toString(),()->dice("Hit points healed",it.heal==null?"":it.heal.toString(),true,t->it.heal=t.isEmpty()?null:Rpg.Dice.parse(t)));
                prop("Spell points restored",String.valueOf(it.spellPoints),()->number("Spell points restored",it.spellPoints,0,200,v->it.spellPoints=v));break;
            case Rpg.I_SCROLL:header("Scroll");
                prop("Casts",it.spell==null?"Nothing":spellLabel(it.spell),()->{int cur=-1;for(int i=0;i<project.spells.size();i++)if(project.spells.get(i).id.equals(it.spell))cur=i;
                    choose("Spell on the scroll",spellNames(),cur,k->it.spell=project.spells.get(k).id);});break;
            case Rpg.I_KEY:header("Key");
                prop("Opens doors needing",it.key<0?"Nothing":Level.KEY_NAMES[it.key+1],()->choose("Opens doors that need",new String[]{"Bronze key","Silver key","Gold key"},Math.max(0,it.key),k->it.key=k));break;
            default:
        }
    }
    private void spellProps(Rpg.Spell s){
        header("Spell");
        prop("Name",s.name,()->text("Spell name",s.name,t->{if(!t.isEmpty())s.name=t;}));
        String[] lv=new String[10];lv[0]="Cantrip (free)";for(int i=1;i<10;i++)lv[i]="Level "+i+" ("+Rpg.spellCost(i)+" SP)";
        prop("Level",s.level==0?"Cantrip (free)":"Level "+s.level+" · "+Rpg.spellCost(s.level)+" SP · from hero level "+Rpg.levelForSpell(s),()->choose("Spell level",lv,s.level,k->s.level=k));
        prop("Effect",Rpg.SPELL_KINDS[s.kind],()->choose("What the spell does",Rpg.SPELL_KINDS,s.kind,k->{s.kind=k;if(k==Rpg.S_AREA&&s.radius<=0)s.radius=2.5f;}));
        prop(s.kind==Rpg.S_WARD?"AC bonus":s.kind==Rpg.S_HEAL?"Healing":"Damage",s.dice.toString(),()->dice("Dice",s.dice.toString(),false,t->s.dice=Rpg.Dice.parse(t)));
        if(s.kind!=Rpg.S_HEAL&&s.kind!=Rpg.S_WARD)prop("Damage type",Rpg.DAMAGE_TYPES[s.damageType],()->choose("Damage type",Rpg.DAMAGE_TYPES,s.damageType,k->s.damageType=k));
        if(s.kind!=Rpg.S_HEAL&&s.kind!=Rpg.S_WARD)prop("Range (map units)",trim(s.range),()->decimal("Range",s.range,1,60,v->s.range=v));
        if(s.kind==Rpg.S_AREA)prop("Blast radius",trim(s.radius),()->decimal("Blast radius",s.radius,.5f,10,v->s.radius=v));
        prop("Description",s.desc==null||s.desc.isEmpty()?"None":s.desc,()->text("Description",s.desc==null?"":s.desc,t->s.desc=t));
        prop("ID",s.id,null);
    }
    private void classProps(Rpg.HeroClass hc){
        header("Class");
        prop("Name",hc.name,()->text("Class name",hc.name,t->{if(t.isEmpty())return;EntityDef pd=project.playerDef();if(pd.heroClass.equalsIgnoreCase(hc.name))pd.heroClass=t;hc.name=t;}));
        prop("Description",hc.desc==null||hc.desc.isEmpty()?"None":hc.desc,()->text("Description",hc.desc==null?"":hc.desc,t->hc.desc=t));
        prop("Hit die","d"+hc.hitDie+" (HP per level)",()->choose("Hit die",new String[]{"d6 (fragile)","d8","d10","d12 (tough)"},Math.max(0,(hc.hitDie-6)/2),k->hc.hitDie=6+k*2));
        prop("Main ability",Rpg.ABILITY_NAMES[hc.primary],()->choose("Main ability (proficient checks)",Rpg.ABILITY_NAMES,hc.primary,k->hc.primary=k));
        String[] cast=new String[7];cast[0]="Doesn't cast spells";for(int i=0;i<6;i++)cast[i+1]=Rpg.ABILITY_NAMES[i];
        prop("Spellcasting",hc.castAbility<0?"None":Rpg.ABILITY_NAMES[hc.castAbility],()->choose("Spellcasting ability",cast,hc.castAbility+1,k->hc.castAbility=k-1));
        toggle("Heavy armour training",hc.heavyArmour,()->{hc.heavyArmour=!hc.heavyArmour;changed();});
        boolean isHero=project.playerDef().heroClass.equalsIgnoreCase(hc.name);
        prop("Hero's class",isHero?"Yes – the hero is a "+hc.name:"No (tap to make the hero a "+hc.name+")",isHero?null:()->{project.playerDef().heroClass=hc.name;changed();hint("The hero is now a "+hc.name);});
        header("Ability scores");
        for(int i=0;i<6;i++){final int k=i;prop(Rpg.ABILITY_NAMES[i],hc.scores[i]+"  ("+Rpg.signed(Rpg.mod(hc.scores[i]))+")",()->number(Rpg.ABILITY_NAMES[k],hc.scores[k],1,30,v->hc.scores[k]=v));}
        header("Starting gear");
        for(int i=0;i<hc.gear.length;i++){final int k=i;Prop pr=prop("Item "+(i+1),itemLabel(hc.gear[i]),()->choose(itemLabel(hc.gear[k]),new String[]{"Replace with another item","Remove"},-1,a->{
            if(a==1)hc.gear=remove(hc.gear,k);else choose("Starting item",itemNames(),-1,j->{hc.gear[k]=project.items.get(j).id;});}));pr.danger=Rpg.item(hc.gear[i])==null;}
        prop("+ Add starting item","Equipped if it fits an empty slot",()->choose("Add starting item",itemNames(),-1,j->hc.gear=append(hc.gear,project.items.get(j).id)));
        header("Spells known (unlock by level)");
        for(int i=0;i<hc.spells.length;i++){final int k=i;Rpg.Spell sp=Rpg.spell(hc.spells[i]);Prop pr=prop("Spell "+(i+1),spellLabel(hc.spells[i])+(sp!=null?" · hero level "+Rpg.levelForSpell(sp):""),()->choose(spellLabel(hc.spells[k]),new String[]{"Replace with another spell","Remove"},-1,a->{
            if(a==1)hc.spells=remove(hc.spells,k);else choose("Spell",spellNames(),-1,j->hc.spells[k]=project.spells.get(j).id);}));pr.danger=sp==null;}
        prop("+ Add spell",hc.castAbility<0?"Set a spellcasting ability first":"Learned when the hero reaches its level",()->choose("Add spell",spellNames(),-1,j->hc.spells=append(hc.spells,project.spells.get(j).id)));
    }
    private void lootProps(Rpg.LootTable t){
        header("Loot table");
        prop("Name",t.name,()->text("Loot table name",t.name,v->{if(!v.isEmpty())t.name=v;}));
        prop("Gold",t.gold,()->dice("Gold dropped",t.gold,false,v->t.gold=v));
        prop("ID",t.id,null);
        header("Items (each rolls its own chance)");
        for(int i=0;i<t.entries.size();i++){final Rpg.LootEntry e=t.entries.get(i);Prop pr=prop(e.chance+"% chance",itemLabel(e.item)+"  ×"+e.count,()->choose(itemLabel(e.item),new String[]{"Change item","Chance…","How many (dice)…","Remove"},-1,a->{
            if(a==0)choose("Item",itemNames(),-1,j->e.item=project.items.get(j).id);
            else if(a==1)number("Chance it drops (%)",e.chance,0,100,v->e.chance=v);
            else if(a==2)dice("How many",e.count,false,v->e.count=v);
            else t.entries.remove(e);}));pr.danger=Rpg.item(e.item)==null;}
        prop("+ Add item","Then set its chance",()->choose("Add item",itemNames(),-1,j->t.add(project.items.get(j).id,50,"1")));
    }
    private void shopProps(Rpg.Shop s){
        header("Shop");
        prop("Name",s.name,()->text("Shop name",s.name,v->{if(!v.isEmpty())s.name=v;}));
        prop("Greeting",s.greeting==null||s.greeting.isEmpty()?"None":s.greeting,()->text("What the shopkeeper says",s.greeting==null?"":s.greeting,v->s.greeting=v));
        prop("Buys the hero's items at",s.sellRate+"% of their value",()->number("Buy-back rate (%)",s.sellRate,0,100,v->s.sellRate=v));
        prop("ID",s.id,null);
        header("For sale");
        for(int i=0;i<s.stock.size();i++){final Rpg.ShopEntry e=s.stock.get(i);Prop pr=prop(itemLabel(e.item),Rpg.price(e)+" gold"+(e.price==0?" (its value)":"")+" · "+(e.stock<0?"unlimited":e.stock+" in stock"),()->choose(itemLabel(e.item),new String[]{"Change item","Price… (0 = item's value)","Stock… (-1 = unlimited)","Remove"},-1,a->{
            if(a==0)choose("Item",itemNames(),-1,j->e.item=project.items.get(j).id);
            else if(a==1)number("Price in gold",e.price,0,100000,v->e.price=v);
            else if(a==2)number("Stock",e.stock,-1,999,v->e.stock=v);
            else s.stock.remove(e);}));pr.danger=Rpg.item(e.item)==null;}
        prop("+ Add item","Sold at its value unless you set a price",()->choose("Add item for sale",itemNames(),-1,j->s.add(project.items.get(j).id,0)));
    }
    private static String[] remove(String[] a,int k){String[] o=new String[a.length-1];for(int i=0,j=0;i<a.length;i++)if(i!=k)o[j++]=a[i];return o;}
    private static String[] append(String[] a,String v){String[] o=java.util.Arrays.copyOf(a,a.length+1);o[a.length]=v;return o;}

    // ================================================================== actions
    private void newEntry(){
        if(host==null)return;
        switch(tab){
            case 0:host.choose("New item",Rpg.ITEM_KINDS,0,k->{String nm="New "+Rpg.ITEM_KINDS[k].toLowerCase();Rpg.Item it=new Rpg.Item(Rpg.uniqueItemId(nm),nm,k,-1,10);
                switch(k){case Rpg.I_WEAPON:it.weapon("1d6",Rpg.SLASHING,Rpg.FP_SWORD,.7f);it.sprite=Sprites.SHOTGUN_PICKUP;break;case Rpg.I_ARMOR:it.armour(12,99,false);it.sprite=Sprites.ARMOR;break;
                    case Rpg.I_SHIELD:it.armour(2,99,false);it.sprite=Sprites.SHIELD_PICKUP;break;case Rpg.I_POTION:it.heal=Rpg.Dice.parse("2d4+2");it.sprite=Sprites.MEDKIT;break;
                    case Rpg.I_SCROLL:it.spell=project.spells.isEmpty()?null:project.spells.get(0).id;it.sprite=Sprites.SCROLL;break;case Rpg.I_KEY:it.key=0;it.value=0;it.sprite=Sprites.KEY_BLUE;break;default:it.sprite=Sprites.ROCKETBOX;}
                project.items.add(it);sel=project.items.size()-1;rightScroll=0;leftScroll=1e9f;changed();hint("Place it in a map with an Item pick-up entity, or use Give Item");});break;
            case 1:{Rpg.Spell s=new Rpg.Spell(Rpg.uniqueSpellId("New spell"),"New spell",1,Rpg.S_BOLT,"2d6",Rpg.FIRE,"");project.spells.add(s);sel=project.spells.size()-1;rightScroll=0;leftScroll=1e9f;changed();hint("Add it to a class to let the hero learn it, or put it on a scroll");break;}
            case 2:{Rpg.HeroClass c=new Rpg.HeroClass("New class",8,Rpg.STR,-1,new int[]{14,12,13,10,11,10},new String[]{"shortsword","leather"},new String[0],false,"");
                int n=2;String base=c.name;while(Rpg.heroClass(c.name)!=null&&Rpg.heroClass(c.name).name.equalsIgnoreCase(c.name)&&n<99)c.name=base+" "+(n++);project.classes.add(c);sel=project.classes.size()-1;rightScroll=0;leftScroll=1e9f;changed();break;}
            case 3:{Rpg.LootTable t=new Rpg.LootTable();t.name="New loot";t.id=Rpg.uniqueLootId(t.name);t.gold="2d6";project.loot.add(t);sel=project.loot.size()-1;rightScroll=0;leftScroll=1e9f;changed();hint("Use it with the Give Loot action (chests, slain monsters)");break;}
            default:{Rpg.Shop s=new Rpg.Shop();s.name="New shop";s.id=Rpg.uniqueShopId(s.name);project.shops.add(s);sel=project.shops.size()-1;rightScroll=0;leftScroll=1e9f;changed();hint("Open it with the Open Shop action on a merchant");}
        }
    }
    private void duplicate(){
        switch(tab){
            case 0:{Rpg.Item c=Rpg.copy(project.items.get(sel));c.name+=" copy";c.id=Rpg.uniqueItemId(c.name);project.items.add(sel+1,c);break;}
            case 1:{Rpg.Spell c=Rpg.copy(project.spells.get(sel));c.name+=" copy";c.id=Rpg.uniqueSpellId(c.name);project.spells.add(sel+1,c);break;}
            case 2:{Rpg.HeroClass c=Rpg.copy(project.classes.get(sel));c.name+=" copy";project.classes.add(sel+1,c);break;}
            case 3:{Rpg.LootTable c=Rpg.copy(project.loot.get(sel));c.name+=" copy";c.id=Rpg.uniqueLootId(c.name);project.loot.add(sel+1,c);break;}
            default:{Rpg.Shop c=Rpg.copy(project.shops.get(sel));c.name+=" copy";c.id=Rpg.uniqueShopId(c.name);project.shops.add(sel+1,c);}
        }
        sel++;changed();hint("Duplicated");
    }
    private void delete(){
        if(host==null||count()==0)return;
        String nm=nameAt(sel);
        if(tab==0&&project.items.get(sel).id.equals("fists")){hint("Bare hands is the hero's unarmed attack and can't be deleted");return;}
        if(tab==2&&project.classes.size()<=1){hint("A game needs at least one class");return;}
        host.confirm("Delete “"+nm+"”?","Actions, scrolls, classes and shops that use it will show it as missing.","Delete",()->{
            switch(tab){case 0:project.items.remove(sel);break;case 1:project.spells.remove(sel);break;case 2:{Rpg.HeroClass hc=project.classes.remove(sel);EntityDef pd=project.playerDef();if(pd.heroClass.equalsIgnoreCase(hc.name))pd.heroClass=project.classes.get(0).name;break;}
                case 3:project.loot.remove(sel);break;default:project.shops.remove(sel);}
            sel=Math.max(0,sel-1);changed();});
    }
    private void resetTab(){
        if(host==null)return;
        host.confirm("Reset "+TABS[tab].toLowerCase()+"?","Replaces all "+TABS[tab].toLowerCase()+" in this project with the built-in set. Your own ones are removed.","Reset",()->{
            switch(tab){
                case 0:project.items.clear();for(Rpg.Item i:Rpg.DEFAULT_ITEMS)project.items.add(Rpg.copy(i));break;
                case 1:project.spells.clear();for(Rpg.Spell s:Rpg.DEFAULT_SPELLS)project.spells.add(Rpg.copy(s));break;
                case 2:project.classes.clear();for(Rpg.HeroClass c:Rpg.BUILTIN_CLASSES)project.classes.add(Rpg.copy(c));break;
                case 3:project.loot.clear();project.loot.addAll(Rpg.defaultLoot());break;
                default:project.shops.clear();project.shops.addAll(Rpg.defaultShops());
            }
            sel=0;changed();hint(TABS[tab]+" reset to built-in");});
    }
    private void testRoll(){
        Rpg.LootTable t=project.loot.get(sel);StringBuilder b=new StringBuilder();
        for(Rpg.LootEntry e:t.entries){if(rng.nextInt(100)>=e.chance)continue;Rpg.Item it=Rpg.item(e.item);if(it==null)continue;int n=Math.max(1,Rpg.Dice.parse(e.count).roll(rng));if(b.length()>0)b.append(", ");b.append(n>1?n+"× "+it.name:it.name);}
        int g=Math.max(0,Rpg.Dice.parse(t.gold).roll(rng));if(g>0){if(b.length()>0)b.append(", ");b.append(g).append(" gold");}
        hint("Rolled: "+(b.length()==0?"nothing":b));
    }

    // ================================================================== touch
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){downX=x;downY=y;lastY=y;moved=false;downHit=ui.hitAt(x,y);zone=leftRect.contains(x,y)?1:rightRect.contains(x,y)?2:0;return true;}
        if(a==MotionEvent.ACTION_MOVE){if(!moved&&Math.abs(x-downX)+Math.abs(y-downY)>dp(8))moved=true;
            if(moved){if(zone==1)leftScroll=Math.max(0,leftScroll-(y-lastY));else if(zone==2)rightScroll=Math.max(0,rightScroll-(y-lastY));invalidate();}
            lastY=y;return true;}
        if(a==MotionEvent.ACTION_UP){if(!moved){int hid=ui.hitAt(x,y);if(hid==downHit&&hid>=0)tap(hid);}undo.commit();invalidate();return true;}
        return true;
    }
    private void tap(int hid){
        if(hid==H_BACK){if(host!=null)host.exit();return;}
        if(hid==H_SAVE){if(host!=null)host.saved();return;}
        if(hid==H_UNDO||hid==H_REDO){undoRedo(hid==H_UNDO);return;}
        if(hid>=H_TAB&&hid<H_TAB+TABS.length){tab=hid-H_TAB;sel=0;leftScroll=rightScroll=0;return;}
        if(hid>=H_ROW&&hid<H_ROW+900){sel=hid-H_ROW;rightScroll=0;return;}
        if(hid>=H_PROP&&hid<H_PROP+props.size()){Prop pr=props.get(hid-H_PROP);if(pr.tap!=null)pr.tap.run();if(pr.toggle)invalidate();return;}
        switch(hid){case H_NEW:newEntry();break;case H_DUP:if(count()>0)duplicate();break;case H_DEL:delete();break;case H_RESET:resetTab();break;case H_TEST:testRoll();break;}
    }
}
