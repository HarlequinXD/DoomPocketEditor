package com.retro.doomeditor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Character creation, shown when a new game starts: pick a race, a class, body type and appearance, ability scores
 * and a name, with a live, turnable 3D preview of the box-modelled hero on the left. Styled with the project's UI theme.
 */
final class CharacterCreator {
    interface Listener {void begin(CharLook look);void back();void prompt(String title,String value,java.util.function.Consumer<String> done);}
    private static final String[] TABS={"Race","Class","Look","Abilities","Name"};
    private static final String[] ABIL={"Strength","Dexterity","Constitution","Intelligence","Wisdom","Charisma"};
    private static final int H_TAB=10,H_RACE=20,H_CLASS=40,H_BODY=100,H_SKIN=110,H_HAIR=130,H_HCOL=140,H_EYE=160,H_BEARD=180,H_HEADWEAR=181,
            H_STD=190,H_ROLL=191,H_NAME=195,H_RNDNAME=196,H_BACK=200,H_BEGIN=201,H_RANDOM=202,H_PREVIEW=203;

    private final Ui ui;private final Paint p;private final Project project;private final Listener listener;
    CharLook look;private int tab;private float yaw=-.5f,spin=.35f,scroll,maxScroll,time;
    private MeshModels.Mesh mesh;private String meshKey;private volatile MeshModels.Mesh built;private volatile String builtKey;private volatile boolean building;private Bitmap bmp;private int[] bmpPx;
    private final RectF r=new RectF(),content=new RectF(),preview=new RectF();
    private float downX,downY,lastX,lastY;private boolean dragging;private int dragKind;   // 1 = turning the model, 2 = scrolling
    private final java.util.Random rng=new java.util.Random();

    CharacterCreator(Ui ui,Project project,CharLook start,Listener l){
        this.ui=ui;p=ui.p;this.project=project;listener=l;look=start==null?new CharLook():start.copy();
        if(Rpg.heroClass(look.cls)==null||!Rpg.heroClass(look.cls).name.equalsIgnoreCase(look.cls))look.cls=Rpg.heroClass(project.playerDef().heroClass).name;
    }
    private float dp(float v){return ui.dp(v);}
    private UiTheme th(){return project.theme;}

    // ================================================================== drawing
    void draw(Canvas c,float w,float h,float dt){
        time+=dt;ui.clearHits();
        if(!dragging||dragKind!=1)yaw+=spin*dt;
        UiTheme th=th();
        // backdrop: dark vignette, warm light behind the hero
        c.drawColor(0xff0c0d10);
        float pw=Math.min(w*.42f,dp(380));preview.set(0,0,pw,h);
        p.setShader(new android.graphics.RadialGradient(pw*.5f,h*.55f,Math.max(pw,h)*.6f,0x40e8c070,0x00000000,android.graphics.Shader.TileMode.CLAMP));c.drawRect(preview,p);p.setShader(null);
        drawPreview(c,pw,h);
        // right panel
        float L=pw+dp(8),R=w-dp(14),T=dp(14),B=h-dp(14);
        th.panel(c,p,L,T,R,B,ui.dp,.96f,false);
        ui.text(c,"Create your hero",L+dp(18),T+dp(32),19,th.accent,2,-1);
        r.set(R-dp(122),T+dp(10),R-dp(14),T+dp(42));th.rectButton(c,p,ui,r,Icons.SPARKLE,"Random",UiTheme.BTN_GHOST,ui.dp);ui.hit(r,H_RANDOM);
        float ty=T+dp(48),tw=(R-L-dp(28))/TABS.length;
        for(int i=0;i<TABS.length;i++){r.set(L+dp(14)+i*tw+dp(2),ty,L+dp(14)+(i+1)*tw-dp(2),ty+dp(32));th.rectButton(c,p,ui,r,-1,TABS[i],tab==i?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,ui.dp);ui.hit(r,H_TAB+i);}
        // bottom buttons
        float by=B-dp(50);
        r.set(L+dp(14),by,L+dp(124),by+dp(38));th.rectButton(c,p,ui,r,Icons.ARROW_LEFT,"Back",UiTheme.BTN_NORMAL,ui.dp);ui.hit(r,H_BACK);
        r.set(R-dp(200),by,R-dp(14),by+dp(38));th.rectButton(c,p,ui,r,Icons.PLAY,"Begin adventure",UiTheme.BTN_PRIMARY,ui.dp);ui.hit(r,H_BEGIN);
        // tab content (scrolls)
        content.set(L+dp(14),ty+dp(42),R-dp(14),by-dp(8));
        int save=c.save();c.clipRect(content);
        float y=content.top-scroll,end;
        switch(tab){case 0:end=drawRaces(c,y);break;case 1:end=drawClasses(c,y);break;case 2:end=drawLook(c,y);break;case 3:end=drawAbilities(c,y);break;default:end=drawName(c,y);}
        c.restoreToCount(save);
        maxScroll=Math.max(0,end+scroll-content.bottom);scroll=Math.max(0,Math.min(maxScroll,scroll));
        if(maxScroll>0){float vh=content.height(),bar=vh*vh/(vh+maxScroll),bt=content.top+(vh-bar)*scroll/maxScroll;ui.round(c,content.right+dp(4),bt,content.right+dp(7),bt+bar,dp(2),0x50ffffff);}
    }
    private void drawPreview(Canvas c,float pw,float h){
        UiTheme th=th();
        String key=look.key();
        if(mesh==null||!key.equals(meshKey)){   // a new look: build it in the background (256-texel textures take a moment) and keep showing the last one
            MeshModels.Mesh ready=built;if(ready!=null&&key.equals(builtKey)){mesh=ready;meshKey=key;}
            else if(!building){building=true;final CharLook lk=look.copy();final String k=key;
                Thread t=new Thread(()->{MeshModels.Mesh m=null;try{m=BoxMesh.buildLook(lk);}catch(Throwable ignored){}builtKey=k;built=m;building=false;},"hero-preview");t.setPriority(Thread.NORM_PRIORITY-1);t.start();}}
        // chunky PS1 pixels: render small, scale up 3x without smoothing
        float avail=h*.62f,heroH=1.45f*scaleOf();int K=3;float ppu=avail/K/Math.max(.8f,heroH);
        Sprites.Sprite s=mesh==null?null:MeshModels.raster(mesh,0,yaw,.12f,ppu,false);
        float base=h*.8f;
        p.setColor(0x60000000);c.drawOval(pw*.5f-dp(70),base-dp(10),pw*.5f+dp(70),base+dp(10),p);
        if(s!=null){
            if(bmp==null||bmp.getWidth()!=s.w||bmp.getHeight()!=s.h){bmp=Bitmap.createBitmap(s.w,s.h,Bitmap.Config.ARGB_8888);}
            bmp.setPixels(s.px,0,s.w,0,0,s.w,s.h);
            float sw=s.w*K,sh=s.h*K;r.set(pw*.5f-sw*.5f,base-sh,pw*.5f+sw*.5f,base);
            p.setFilterBitmap(false);c.drawBitmap(bmp,null,r,p);p.setFilterBitmap(true);}
        ui.hit(preview,H_PREVIEW);
        Rpg.Race rc=look.raceInfo();
        ui.text(c,ui.ellipsize(look.name.isEmpty()?"Unnamed hero":look.name,20,2,pw-dp(30)),pw*.5f,dp(40),20,th.accent,2,0);
        ui.text(c,(look.female?"Female ":"Male ")+rc.name+" "+Rpg.heroClass(look.cls).name,pw*.5f,dp(62),13,th.text,1,0);
        int[] sc=look.finalScores();Rpg.HeroClass hc=Rpg.heroClass(look.cls);
        int hp=hc.hitDie+Rpg.mod(sc[Rpg.CON])+(rc.name.equals("Dwarf")?1:0);
        ui.text(c,"HP "+Math.max(1,hp)+"   ·   Speed "+(rc.speed<1?"slow":"normal")+(rc.darkvision?"   ·   Darkvision":""),pw*.5f,h-dp(30),11.5f,th.textDim(),1,0);
        ui.text(c,"Drag to turn",pw*.5f,h-dp(12),10,0x80ffffff,1,0);
    }
    private float scaleOf(){switch(look.raceInfo().name){case "Dwarf":return .8f;case "Halfling":return .63f;case "Gnome":return .6f;case "Elf":case "Dragonborn":return 1.07f;default:return 1;}}

    private float drawRaces(Canvas c,float y){
        UiTheme th=th();int cols=3;float gw=(content.width()-dp(8)*(cols-1))/cols,gh=dp(44);
        for(int i=0;i<Rpg.RACES.length;i++){Rpg.Race rc=Rpg.RACES[i];int cx=i%cols,cy=i/cols;
            r.set(content.left+cx*(gw+dp(8)),y+cy*(gh+dp(8)),content.left+cx*(gw+dp(8))+gw,y+cy*(gh+dp(8))+gh);
            boolean sel=rc.name.equals(look.raceInfo().name);th.card(c,p,ui,r,sel,ui.dp);
            ui.textMid(c,rc.name,r.left+dp(12),r.centerY()-dp(6),13.5f,sel?th.accent:th.text,2,-1);
            ui.textMid(c,ui.ellipsize(rc.bonusText(),10,1,r.width()-dp(20)),r.left+dp(12),r.centerY()+dp(10),10,th.textDim(),1,-1);
            hitIn(r,H_RACE+i);}
        y+=3*(gh+dp(8))+dp(6);
        Rpg.Race rc=look.raceInfo();
        y=para(c,rc.desc,y,13,th.text);y=para(c,"Traits: "+rc.traits,y+dp(4),12,th.textDim());
        return y;
    }
    private float drawClasses(Canvas c,float y){
        UiTheme th=th();String[] ab={"STR","DEX","CON","INT","WIS","CHA"};
        for(int i=0;i<Rpg.CLASSES.size();i++){Rpg.HeroClass hc=Rpg.CLASSES.get(i);
            r.set(content.left,y,content.right,y+dp(54));boolean sel=hc.name.equalsIgnoreCase(look.cls);th.card(c,p,ui,r,sel,ui.dp);
            ui.text(c,hc.name,r.left+dp(14),r.top+dp(22),14,sel?th.accent:th.text,2,-1);
            String info="d"+hc.hitDie+" hit die · "+ab[hc.primary]+(hc.castAbility>=0?" · casts spells":"")+(hc.heavyArmour?" · heavy armour":"");
            ui.text(c,ui.ellipsize(info,10.5f,1,r.width()*.5f),r.right-dp(12),r.top+dp(22),10.5f,th.textDim(),1,1);
            ui.text(c,ui.ellipsize(hc.desc,11,1,r.width()-dp(28)),r.left+dp(14),r.top+dp(42),11,th.textDim(),1,-1);
            hitIn(r,H_CLASS+i);y+=dp(60);}
        return y;
    }
    private float drawLook(Canvas c,float y){
        UiTheme th=th();Rpg.Race rc=look.raceInfo();
        y=label(c,"Body",y);float bw=dp(96);
        for(int i=0;i<2;i++){r.set(content.left+i*(bw+dp(6)),y,content.left+i*(bw+dp(6))+bw,y+dp(32));th.rectButton(c,p,ui,r,-1,i==0?"Male":"Female",look.female==(i==1)?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,ui.dp);hitIn(r,H_BODY+i);}
        y+=dp(42);
        y=label(c,rc.name.equals("Dragonborn")?"Scales":"Skin",y);y=swatches(c,y,rc.skins,look.skin,H_SKIN);
        if(!rc.name.equals("Dragonborn")){
            y=label(c,"Hair",y);float hw=(content.width()-dp(6)*4)/5;
            for(int i=0;i<CharLook.HAIR_STYLES.length;i++){r.set(content.left+i*(hw+dp(6)),y,content.left+i*(hw+dp(6))+hw,y+dp(32));th.rectButton(c,p,ui,r,-1,CharLook.HAIR_STYLES[i],look.hair==i?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,ui.dp);hitIn(r,H_HAIR+i);}
            y+=dp(42);
            y=label(c,"Hair colour",y);y=swatches(c,y,CharLook.HAIR_COLOURS,look.hairColour,H_HCOL);}
        y=label(c,"Eyes",y);y=swatches(c,y,CharLook.EYE_COLOURS,look.eyes,H_EYE);
        if(!rc.name.equals("Dragonborn"))y=toggleRow(c,y,"Beard",look.beard,H_BEARD);
        y=toggleRow(c,y,"Class headwear (hood, hat)",look.headwear,H_HEADWEAR);
        return y;
    }
    private float drawAbilities(Canvas c,float y){
        UiTheme th=th();int[] base=look.baseScores(),fin=look.finalScores();
        float hw=(content.width()-dp(8))/2;
        r.set(content.left,y,content.left+hw,y+dp(34));th.rectButton(c,p,ui,r,Icons.LIST,"Standard array",look.rolled==null?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,ui.dp);hitIn(r,H_STD);
        r.set(content.left+hw+dp(8),y,content.right,y+dp(34));th.rectButton(c,p,ui,r,Icons.SPARKLE,look.rolled==null?"Roll 4d6":"Roll again",look.rolled!=null?UiTheme.BTN_ACTIVE:UiTheme.BTN_NORMAL,ui.dp);hitIn(r,H_ROLL);
        y+=dp(44);
        y=para(c,look.rolled==null?"The standard array (15, 14, 13, 12, 10, 8), arranged for your class.":"Rolled 4d6 six times, dropping the lowest die; the best rolls go to your class's main abilities.",y,11,th.textDim())+dp(4);
        for(int i=0;i<6;i++){r.set(content.left,y,content.right,y+dp(30));if(i%2==0)ui.round(c,r.left,r.top,r.right,r.bottom,dp(6),0x14ffffff);
            ui.textMid(c,ABIL[i],r.left+dp(12),r.centerY(),12.5f,th.text,1,-1);
            int bonus=fin[i]-base[i];int m=Rpg.mod(fin[i]);
            ui.textMid(c,String.valueOf(base[i]),r.right-dp(150),r.centerY(),12.5f,th.textDim(),1,1);
            ui.textMid(c,bonus>0?"+"+bonus:"",r.right-dp(104),r.centerY(),12.5f,th.accent,1,1);
            ui.textMid(c,String.valueOf(fin[i]),r.right-dp(56),r.centerY(),14,th.text,2,1);
            ui.textMid(c,"("+(m>=0?"+":"")+m+")",r.right-dp(10),r.centerY(),11.5f,th.textDim(),1,1);
            y+=dp(32);}
        return y;
    }
    private float drawName(Canvas c,float y){
        UiTheme th=th();
        r.set(content.left,y,content.right,y+dp(52));th.card(c,p,ui,r,false,ui.dp);
        ui.textMid(c,ui.ellipsize(look.name.isEmpty()?"Tap to name your hero":look.name,18,2,r.width()-dp(30)),r.left+dp(16),r.centerY(),18,look.name.isEmpty()?th.textDim():th.text,2,-1);hitIn(r,H_NAME);
        y+=dp(62);
        float hw=(content.width()-dp(8))/2;
        r.set(content.left,y,content.left+hw,y+dp(36));th.rectButton(c,p,ui,r,Icons.PEN,"Change name",UiTheme.BTN_NORMAL,ui.dp);hitIn(r,H_NAME);
        r.set(content.left+hw+dp(8),y,content.right,y+dp(36));th.rectButton(c,p,ui,r,Icons.SPARKLE,"Random "+look.raceInfo().name+" name",UiTheme.BTN_NORMAL,ui.dp);hitIn(r,H_RNDNAME);
        y+=dp(48);
        Rpg.Race rc=look.raceInfo();Rpg.HeroClass hc=Rpg.heroClass(look.cls);
        y=para(c,"You are "+(look.name.isEmpty()?"a nameless hero":look.name)+", a "+(look.female?"female ":"male ")+rc.name.toLowerCase(java.util.Locale.US)+" "+hc.name.toLowerCase(java.util.Locale.US)+". "+rc.desc+" "+hc.desc,y,12.5f,th.text);
        return y;
    }
    // ---- small pieces
    private void hitIn(RectF rr,int id){if(rr.bottom>content.top&&rr.top<content.bottom){RectF q=new RectF(rr);q.top=Math.max(q.top,content.top);q.bottom=Math.min(q.bottom,content.bottom);ui.hit(q,id);}}
    private float label(Canvas c,String s,float y){ui.text(c,s,content.left,y+dp(12),11,th().textDim(),1,-1);return y+dp(20);}
    private float swatches(Canvas c,float y,int[] cols,int sel,int hid){
        float s=dp(30),g=dp(8);int per=Math.max(1,(int)((content.width()+g)/(s+g)));
        for(int i=0;i<cols.length;i++){float x=content.left+(i%per)*(s+g),yy=y+(i/per)*(s+g);r.set(x,yy,x+s,yy+s);
            ui.round(c,x,yy,x+s,yy+s,dp(6),cols[i]);if(i==sel)ui.outline(c,x-dp(2),yy-dp(2),x+s+dp(2),yy+s+dp(2),dp(8),th().accent,dp(2));hitIn(r,hid+i);}
        return y+((cols.length+per-1)/per)*(s+g)+dp(4);
    }
    private float toggleRow(Canvas c,float y,String label,boolean on,int hid){
        r.set(content.left,y,content.right,y+dp(38));ui.textMid(c,label,content.left,r.centerY(),12.5f,th().text,1,-1);
        th().toggle(c,p,ui,content.right-dp(46),y+dp(11),on,ui.dp);hitIn(r,hid);return y+dp(42);
    }
    private float para(Canvas c,String text,float y,float size,int col){
        String[] words=text.split(" ");StringBuilder line=new StringBuilder();float lh=dp(size*1.45f);
        for(String wd:words){String t=line.length()==0?wd:line+" "+wd;if(ui.measure(t,size,1)>content.width()&&line.length()>0){ui.text(c,line.toString(),content.left,y+lh,size,col,1,-1);y+=lh;line=new StringBuilder(wd);}else line=new StringBuilder(t);}
        if(line.length()>0){ui.text(c,line.toString(),content.left,y+lh,size,col,1,-1);y+=lh;}
        return y+dp(4);
    }

    // ================================================================== touch
    void down(float x,float y){downX=lastX=x;downY=lastY=y;dragging=false;dragKind=preview.contains(x,y)?1:content.contains(x,y)?2:0;}
    void move(float x,float y){
        if(!dragging&&Math.hypot(x-downX,y-downY)>dp(8))dragging=true;
        if(dragging){if(dragKind==1){yaw+=(x-lastX)/dp(120);spin=0;}else if(dragKind==2)scroll=Math.max(0,Math.min(maxScroll,scroll-(y-lastY)));}
        lastX=x;lastY=y;
    }
    void up(float x,float y){
        if(dragging){if(dragKind==1)spin=.35f;dragging=false;return;}
        tap(ui.hitAt(x,y));
    }
    /** Hardware back: one step back out of the screen. */
    void back(){listener.back();}
    private void tap(int hid){
        if(hid<0)return;
        if(hid>=H_TAB&&hid<H_TAB+TABS.length){tab=hid-H_TAB;scroll=0;return;}
        if(hid>=H_RACE&&hid<H_RACE+Rpg.RACES.length){setRace(Rpg.RACES[hid-H_RACE].name);return;}
        if(hid>=H_CLASS&&hid<H_CLASS+Rpg.CLASSES.size()){look.cls=Rpg.CLASSES.get(hid-H_CLASS).name;if(look.rolled!=null)look.rolled=arrange(look.rolled,look.cls);return;}
        if(hid==H_BODY||hid==H_BODY+1){look.female=hid==H_BODY+1;if(look.female)look.beard=false;return;}
        if(hid>=H_SKIN&&hid<H_SKIN+20){look.skin=hid-H_SKIN;return;}
        if(hid>=H_HAIR&&hid<H_HAIR+CharLook.HAIR_STYLES.length){look.hair=hid-H_HAIR;return;}
        if(hid>=H_HCOL&&hid<H_HCOL+CharLook.HAIR_COLOURS.length){look.hairColour=hid-H_HCOL;return;}
        if(hid>=H_EYE&&hid<H_EYE+CharLook.EYE_COLOURS.length){look.eyes=hid-H_EYE;return;}
        switch(hid){
            case H_BEARD:look.beard=!look.beard;return;
            case H_HEADWEAR:look.headwear=!look.headwear;return;
            case H_STD:look.rolled=null;return;
            case H_ROLL:look.rolled=roll(look.cls);return;
            case H_NAME:listener.prompt("Hero's name",look.name,t->{String n=t.trim();if(n.length()>24)n=n.substring(0,24);look.name=n;});return;
            case H_RNDNAME:look.name=randomName();return;
            case H_RANDOM:randomise();return;
            case H_BACK:listener.back();return;
            case H_BEGIN:if(look.name.trim().isEmpty())look.name=randomName();listener.begin(look.copy());return;
        }
    }
    private void setRace(String race){
        String before=look.raceInfo().name;look.race=race;Rpg.Race rc=look.raceInfo();
        if(look.skin>=rc.skins.length)look.skin=rc.skins.length-1;
        if(!before.equals(race)){if(race.equals("Dwarf")&&!look.female)look.beard=true;if(race.equals("Dragonborn"))look.beard=false;
            if(race.equals("Tiefling")||race.equals("Dragonborn"))look.skin=rng.nextInt(rc.skins.length);}
    }
    /** 4d6 drop the lowest, six times; the best go to the class's highest standard-array slots. */
    private int[] roll(String cls){int[] v=new int[6];for(int i=0;i<6;i++){int[] d={1+rng.nextInt(6),1+rng.nextInt(6),1+rng.nextInt(6),1+rng.nextInt(6)};java.util.Arrays.sort(d);v[i]=d[1]+d[2]+d[3];}return arrange(v,cls);}
    private static int[] arrange(int[] values,String cls){
        int[] v=values.clone();java.util.Arrays.sort(v);int[] pri=Rpg.heroClass(cls).scores;Integer[] order={0,1,2,3,4,5};
        java.util.Arrays.sort(order,(a,b)->pri[b]-pri[a]);int[] out=new int[6];for(int k=0;k<6;k++)out[order[k]]=v[5-k];return out;
    }
    private void randomise(){
        setRace(Rpg.RACES[rng.nextInt(Rpg.RACES.length)].name);look.cls=Rpg.CLASSES.get(rng.nextInt(Rpg.CLASSES.size())).name;
        look.female=rng.nextBoolean();look.skin=rng.nextInt(look.raceInfo().skins.length);look.hair=rng.nextInt(CharLook.HAIR_STYLES.length);
        look.hairColour=rng.nextInt(CharLook.HAIR_COLOURS.length);look.eyes=rng.nextInt(CharLook.EYE_COLOURS.length);
        look.beard=!look.female&&(look.race.equals("Dwarf")||rng.nextInt(3)==0)&&!look.race.equals("Dragonborn");look.rolled=null;look.name=randomName();
    }
    private String randomName(){
        String[][] m={{"Aldric","Bram","Cedric","Doran","Edmund","Garrick"},{"Aelar","Thamior","Erevan","Galinndan","Varis","Riardon"},{"Thorin","Bruenor","Rurik","Harbek","Eberk","Vondal"},
            {"Milo","Perrin","Cade","Wellby","Roscoe","Corrin"},{"Fonkin","Orryn","Boddynock","Zook","Wrenn","Dimble"},{"Kaelen","Tharivol","Aramil","Ivellios","Soveliss","Lucan"},
            {"Grosh","Thokk","Dench","Holg","Krusk","Ront"},{"Akmenos","Damakos","Kallistes","Mordai","Skamos","Leucis"},{"Arjhan","Balasar","Kriv","Medrash","Torinn","Ghesh"}};
        String[][] f={{"Elsa","Mira","Rowan","Isolde","Brynn","Tessa"},{"Adrie","Naivara","Sariel","Shava","Lia","Keyleth"},{"Hilda","Dagna","Eldeth","Gunnloda","Vistra","Riswynn"},
            {"Lavinia","Seraphina","Bree","Verna","Kithri","Merla"},{"Bimpnottin","Carlin","Nissa","Lilli","Orla","Ellywick"},{"Sorrel","Ilyana","Mialee","Talia","Jelenneth","Wren"},
            {"Baggi","Engong","Ovak","Shautha","Volen","Yevelda"},{"Akta","Kallista","Nemeia","Orianna","Rieta","Lerissa"},{"Akra","Biri","Daar","Harann","Kava","Sora"}};
        int ri=0;for(int i=0;i<Rpg.RACES.length;i++)if(Rpg.RACES[i].name.equals(look.raceInfo().name))ri=i;
        String[] list=look.female?f[ri]:m[ri];return list[rng.nextInt(list.length)];
    }
}
