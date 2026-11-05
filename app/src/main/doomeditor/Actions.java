package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Registry of FSM actions. Each action has a category, typed parameters and an implementation that runs
 * against the game World. Return DONE when the action has finished, RUNNING to be called again next frame.
 * A state sends FINISHED once all of its actions are DONE.
 */
final class Actions {
    private Actions(){}
    static final int T_FLOAT=0,T_INT=1,T_BOOL=2,T_TEXT=3,T_EVENT=4,T_VAR=5,T_CHOICE=6,T_ENTITY=7,T_MATERIAL=8,T_SECTOR=9,T_CLIP=10,T_SOUND=11,T_SONG=12,T_MAP=13;
    static final int DONE=0,RUNNING=1;
    static final String CAM_STYLES="Orbit|Shoulder|Chase";
    static final String[] CATEGORIES={"Flow","Animation","Variables","Input","Combat","Character","Inventory","Dialogue","Quests","Health","Movement","Camera","AI","World","Entities","Feedback","Audio"};
    static final int[] CATEGORY_ICONS={Icons.GRAPH,Icons.FILM,Icons.TAG,Icons.MOVE,Icons.SWORD,Icons.PERSON,Icons.PACKAGE,Icons.CHAT,Icons.SCROLL,Icons.HEART,Icons.ARROW_UP,Icons.CAMERA,Icons.SKULL,Icons.LAYERS,Icons.ENTITIES,Icons.INFO,Icons.SPEAKER};
    static final String WEAPONS="Fist|Pistol|Shotgun|Chaingun|Rocket Launcher";
    static final String AMMO="Bullets|Shells|Rockets";
    static final String BUTTONS="Fire|Use|Jump|Weapon|Alt|Spell|Potion|Inventory";
    static final String KEYS=Rpg.KEYS;
    static final String WHO="Player|Self|Target";
    static final String ABIL="STR|DEX|CON|INT|WIS|CHA";
    static final String SOUNDS="Pistol|Shotgun|Chaingun|Rocket|Explosion|Punch|Door|Switch|Pickup|Weapon Pickup|Item|Player Pain|Player Death|Monster Sight|Monster Pain|Monster Death|Fireball|Teleport|Secret|Oof|Beep|Sword Swing|Hit|Bow|Spell|Heal|Coin|Level Up|Potion";

    static final class Param {
        final String key,label;final int type;final String def;final String[] options;
        Param(String key,int type,String def,String[] options){this.key=key;this.type=type;this.def=def;this.options=options;label=labelOf(key);}
        static String labelOf(String key){StringBuilder b=new StringBuilder();for(int i=0;i<key.length();i++){char c=key.charAt(i);if(i==0)b.append(Character.toUpperCase(c));else if(Character.isUpperCase(c))b.append(' ').append(c);else b.append(c);}return b.toString();}
    }
    interface Impl{int run(Ctx c);}
    static final class Spec {
        final String id,name,category,desc;final int icon;final Param[] params;final Impl impl;
        Spec(String id,String name,String category,int icon,String desc,Param[] params,Impl impl){this.id=id;this.name=name;this.category=category;this.icon=icon;this.desc=desc;this.params=params;this.impl=impl;}
        Param param(String key){for(Param p:params)if(p.key.equals(key))return p;return null;}
    }
    static final LinkedHashMap<String,Spec> ALL=new LinkedHashMap<>();
    static Spec get(String id){return id==null?null:ALL.get(id);}
    /** Actions kept only so older projects still run; never offered in the browser. */
    static boolean hidden(Spec s){return s.category.equals("Legacy");}
    static int visibleCount(){int n=0;for(Spec s:ALL.values())if(!hidden(s))n++;return n;}
    static ArrayList<Spec> inCategory(String cat){ArrayList<Spec> out=new ArrayList<>();for(Spec s:ALL.values())if(!hidden(s)&&(cat==null||s.category.equals(cat)))out.add(s);return out;}
    static ArrayList<Spec> search(String q){ArrayList<Spec> out=new ArrayList<>();String l=q.toLowerCase();for(Spec s:ALL.values())if(!hidden(s)&&(s.name.toLowerCase().contains(l)||s.desc.toLowerCase().contains(l)||s.category.toLowerCase().contains(l)))out.add(s);return out;}

    /** Parses "key:type=default" where type is f,i,b,t,e,v,E,M,A,X,S (sound),U (song) or c(A|B|C). */
    private static Param parse(String spec){
        int colon=spec.indexOf(':'),eq=spec.indexOf('=',colon);
        String key=spec.substring(0,colon),type=spec.substring(colon+1,eq<0?spec.length():eq),def=eq<0?"":spec.substring(eq+1);
        if(type.startsWith("c(")){String[] opts=type.substring(2,type.length()-1).split("\\|");return new Param(key,T_CHOICE,def.isEmpty()?opts[0]:def,opts);}
        int t;switch(type){case "f":t=T_FLOAT;break;case "i":t=T_INT;break;case "b":t=T_BOOL;break;case "e":t=T_EVENT;break;case "v":t=T_VAR;break;case "E":t=T_ENTITY;break;case "M":t=T_MATERIAL;break;case "A":t=T_CLIP;break;case "X":t=T_SECTOR;break;case "S":t=T_SOUND;break;case "U":t=T_SONG;break;case "P":t=T_MAP;break;default:t=T_TEXT;}
        return new Param(key,t,def,null);
    }
    private static void def(String id,String name,String cat,int icon,String desc,Impl impl,String... params){
        Param[] ps=new Param[params.length];for(int i=0;i<params.length;i++)ps[i]=parse(params[i]);
        ALL.put(id,new Spec(id,name,cat,icon,desc,ps,impl));
    }
    static int indexOf(String options,String value){String[] o=options.split("\\|");for(int i=0;i<o.length;i++)if(o[i].equalsIgnoreCase(value))return i;return 0;}

    /** Execution context handed to an action implementation. */
    static final class Ctx {
        World w;Actor self;FsmRuntime fsm;FSMGraph.Action a;boolean enter;float dt;float[] m;
        String s(String k){String v=a.params.get(k);return v==null?"":v.trim();}
        float f(String k){String v=s(k);if(v.isEmpty())return 0;try{return Float.parseFloat(v);}catch(NumberFormatException e){return fsm.getVar(w,v);}}
        int i(String k){return Math.round(f(k));}
        boolean b(String k){String v=s(k);if(v.equalsIgnoreCase("true")||v.equals("1")||v.equalsIgnoreCase("yes"))return true;if(v.equalsIgnoreCase("false")||v.equals("0")||v.isEmpty())return false;return fsm.getVar(w,v)!=0;}
        int choice(String k,String options){return indexOf(options,s(k));}
        void send(String k){String e=s(k);if(!e.isEmpty())fsm.send(e);}
        boolean every(){return b("everyFrame");}
        int doneOrRun(){return every()?RUNNING:DONE;}
        float rnd(float lo,float hi){return lo+w.rng.nextFloat()*(hi-lo);}
        int rndInt(int lo,int hi){return hi<=lo?lo:lo+w.rng.nextInt(hi-lo+1);}
        Actor who(String k){String v=s(k);if(v.equalsIgnoreCase("Player"))return w.player;if(v.equalsIgnoreCase("Self"))return self;return self.target;}
        void setVar(String k,float v){String name=s(k);if(!name.isEmpty())fsm.setVar(w,name,v);}
        float getVar(String k){String name=s(k);return name.isEmpty()?0:fsm.getVar(w,name);}
        /** Per-action timer stored in scratch slot 0..7. */
        boolean timer(int slot,float seconds){if(enter)m[slot]=0;m[slot]+=dt;return m[slot]>=seconds;}
    }

    static {
        // ------------------------------------------------------------------ Flow
        def("wait","Wait","Flow",Icons.CLOCK,"Waits for a time, then sends an event (FINISHED by default).",c->{
            if(c.enter){c.m[1]=c.f("seconds")+c.rnd(0,Math.max(0,c.f("random")));c.m[0]=0;}
            c.m[0]+=c.dt;if(c.m[0]>=c.m[1]){c.send("event");return DONE;}return RUNNING;},"seconds:f=1","random:f=0","event:e=FINISHED");
        def("random_wait","Random Wait","Flow",Icons.CLOCK,"Waits a random time between min and max, then sends an event.",c->{
            if(c.enter){c.m[1]=c.rnd(c.f("min"),Math.max(c.f("min"),c.f("max")));c.m[0]=0;}
            c.m[0]+=c.dt;if(c.m[0]>=c.m[1]){c.send("event");return DONE;}return RUNNING;},"min:f=0.5","max:f=2","event:e=FINISHED");
        def("send_event","Send Event","Flow",Icons.ZAP,"Sends an event to this FSM, optionally after a delay.",c->{
            if(c.timer(0,c.f("delay"))){c.send("event");return DONE;}return RUNNING;},"event:e=","delay:f=0");
        def("random_event","Random Event","Flow",Icons.ZAP,"Sends one of up to three events, picked at random.",c->{
            ArrayList<String> ev=new ArrayList<>();for(String k:new String[]{"eventA","eventB","eventC"})if(!c.s(k).isEmpty())ev.add(c.s(k));
            if(!ev.isEmpty())c.fsm.send(ev.get(c.w.rng.nextInt(ev.size())));return DONE;},"eventA:e=","eventB:e=","eventC:e=");
        def("chance_event","Chance Event","Flow",Icons.ZAP,"Sends an event with a percentage chance, otherwise the else event.",c->{
            if(c.w.rng.nextFloat()*100<c.f("percent"))c.send("event");else c.send("elseEvent");return DONE;},"percent:f=50","event:e=","elseEvent:e=");
        def("repeat_event","Repeat Event","Flow",Icons.RESET,"Keeps sending an event at a fixed interval.",c->{
            if(c.enter)c.m[0]=0;c.m[0]+=c.dt;if(c.m[0]>=Math.max(.05f,c.f("interval"))){c.m[0]=0;c.send("event");}return RUNNING;},"interval:f=1","event:e=");
        def("next_frame_event","Next Frame Event","Flow",Icons.ZAP,"Sends an event on the next frame.",c->{
            if(c.enter){c.m[0]=0;return RUNNING;}c.send("event");return DONE;},"event:e=");
        def("finish_state","Finish","Flow",Icons.CHECK,"Sends FINISHED immediately.",c->{c.fsm.send("FINISHED");return DONE;});
        def("send_to_target","Send Event To Entity","Flow",Icons.LINK,"Sends an event to another entity's FSM.",c->{
            Actor t=c.who("to");if(t!=null&&t.fsm!=null&&!c.s("event").isEmpty())t.fsm.send(c.s("event"));return DONE;},"event:e=","to:c(Target|Player|Self)=Target");
        def("broadcast_event","Broadcast Event","Flow",Icons.LINK,"Sends an event to every entity, or every entity of one type.",c->{
            c.w.broadcast(c.s("event"),c.s("entity"));return DONE;},"event:e=","entity:E=");
        def("counter_event","Counter","Flow",Icons.PLUS,"Adds one to a variable each time the state is entered; sends an event when it reaches the count.",c->{
            float v=c.getVar("var")+1;c.setVar("var",v);if(v>=c.f("count"))c.send("event");return DONE;},"var:v=count","count:i=3","event:e=");
        def("debug_log","Debug Log","Flow",Icons.INFO,"Shows text in the message area while testing.",c->{c.w.message("[debug] "+c.s("text"),2);return DONE;},"text:t=Hello");

        // ------------------------------------------------------------------ Animation
        def("play_animation","Play Animation","Animation",Icons.FILM,"Plays one of the entity's sprite clips. Optionally sends an event when a play-once clip ends.",c->{
            if(c.enter){int l=c.choice("loop","Clip setting|Loop|Play once");c.w.playAnim(c.self,c.s("clip"),c.f("speed"),l==0?-1:l==1?1:0,c.b("restart"));}
            if(c.s("finishedEvent").isEmpty())return DONE;
            if(c.w.animFinished(c.self)){c.send("finishedEvent");return DONE;}return RUNNING;},
            "clip:A=Walk","loop:c(Clip setting|Loop|Play once)=Clip setting","speed:f=1","restart:b=true","finishedEvent:e=");
        def("wait_animation","Wait For Animation","Animation",Icons.CLOCK,"Waits until the current play-once clip ends, then sends an event.",c->{
            if(c.w.animFinished(c.self)){c.send("event");return DONE;}return RUNNING;},"event:e=FINISHED");
        def("animation_frame_event","Animation Frame Event","Animation",Icons.ZAP,"Sends an event when the current clip reaches a frame, e.g. the frame where a punch lands.",c->{
            int f=c.w.animFrame(c.self)+1;if(f!=c.self.lastAnimFrame){c.self.lastAnimFrame=f;if(f==c.i("frame"))c.send("event");}return RUNNING;},"frame:i=2","event:e=HIT");
        def("set_animation_speed","Set Animation Speed","Animation",Icons.CHEVRONS,"Speeds up or slows down the current clip (1 = normal).",c->{c.self.animSpeed=Math.max(0,c.f("speed"));return DONE;},"speed:f=1");
        def("auto_animation","Automatic Animation","Animation",Icons.RESET,"Hands animation back to the engine: idle, walk, attack, pain and death are chosen automatically.",c->{c.self.animClip=null;return DONE;});

        // ------------------------------------------------------------------ Variables
        def("set_float","Set Variable","Variables",Icons.TAG,"Sets a number variable. Prefix with g. for a global variable.",c->{c.setVar("var",c.f("value"));return c.doneOrRun();},"var:v=value","value:f=0","everyFrame:b=false");
        def("add_float","Add To Variable","Variables",Icons.PLUS,"Adds an amount to a variable, optionally per second.",c->{
            c.setVar("var",c.getVar("var")+c.f("amount")*(c.b("perSecond")?c.dt:1));return c.doneOrRun();},"var:v=value","amount:f=1","perSecond:b=false","everyFrame:b=false");
        def("multiply_float","Multiply Variable","Variables",Icons.TAG,"Multiplies a variable by a factor.",c->{c.setVar("var",c.getVar("var")*c.f("factor"));return DONE;},"var:v=value","factor:f=2");
        def("random_float","Random Number","Variables",Icons.SPARKLE,"Stores a random number between min and max.",c->{c.setVar("var",c.rnd(c.f("min"),c.f("max")));return DONE;},"var:v=value","min:f=0","max:f=1");
        def("clamp_float","Clamp Variable","Variables",Icons.TAG,"Keeps a variable between min and max.",c->{c.setVar("var",Math.max(c.f("min"),Math.min(c.f("max"),c.getVar("var"))));return c.doneOrRun();},"var:v=value","min:f=0","max:f=100","everyFrame:b=false");
        def("float_operator","Math Operation","Variables",Icons.TAG,"Combines two numbers and stores the result.",c->{
            float a=c.f("a"),b=c.f("b"),r;switch(c.choice("op","Add|Subtract|Multiply|Divide|Min|Max")){case 1:r=a-b;break;case 2:r=a*b;break;case 3:r=b==0?0:a/b;break;case 4:r=Math.min(a,b);break;case 5:r=Math.max(a,b);break;default:r=a+b;}
            c.setVar("result",r);return c.doneOrRun();},"a:f=0","op:c(Add|Subtract|Multiply|Divide|Min|Max)=Add","b:f=0","result:v=result","everyFrame:b=false");
        def("float_compare","Compare Numbers","Variables",Icons.VALIDATE,"Compares A with B and sends the matching event.",c->{
            float a=c.f("a"),b=c.f("b");if(Math.abs(a-b)<1e-4f)c.send("equalEvent");else if(a<b)c.send("lessEvent");else c.send("greaterEvent");return c.doneOrRun();},
            "a:f=0","b:f=0","equalEvent:e=","lessEvent:e=","greaterEvent:e=","everyFrame:b=false");
        def("set_bool","Set Flag","Variables",Icons.FLAG,"Sets a true/false variable.",c->{c.setVar("var",c.b("value")?1:0);return DONE;},"var:v=flag","value:b=true");
        def("toggle_bool","Toggle Flag","Variables",Icons.FLAG,"Flips a true/false variable.",c->{c.setVar("var",c.getVar("var")!=0?0:1);return DONE;},"var:v=flag");
        def("bool_test","Test Flag","Variables",Icons.VALIDATE,"Sends an event depending on a true/false variable.",c->{if(c.getVar("var")!=0)c.send("trueEvent");else c.send("falseEvent");return c.doneOrRun();},"var:v=flag","trueEvent:e=","falseEvent:e=","everyFrame:b=false");
        def("copy_float","Copy Variable","Variables",Icons.COPY,"Copies one variable into another.",c->{c.setVar("to",c.getVar("from"));return DONE;},"from:v=","to:v=");
        def("get_distance","Get Distance","Variables",Icons.MOVE,"Stores the distance to the player or target.",c->{Actor t=c.who("to");c.setVar("store",t==null?9999:c.w.dist(c.self,t));return c.doneOrRun();},"to:c(Player|Target)=Player","store:v=distance","everyFrame:b=false");
        def("get_health","Get Health","Variables",Icons.HEART,"Stores the health of self, target or player.",c->{Actor t=c.who("of");c.setVar("store",t==null?0:t.health);return c.doneOrRun();},"of:c(Self|Target|Player)=Self","store:v=health","everyFrame:b=false");
        def("get_time","Get Time","Variables",Icons.CLOCK,"Stores the seconds since the level started.",c->{c.setVar("store",c.w.time);return c.doneOrRun();},"store:v=time","everyFrame:b=false");
        def("get_position","Get Position","Variables",Icons.CROSSHAIR,"Stores an entity's X and Y position.",c->{Actor t=c.who("of");if(t!=null){c.setVar("x",t.x);c.setVar("y",t.y);}return c.doneOrRun();},"of:c(Self|Target|Player)=Self","x:v=x","y:v=y","everyFrame:b=false");

        // ------------------------------------------------------------------ Input
        def("move_joystick","Move With Joystick","Input",Icons.MOVE,"Walks and strafes using the on-screen joystick, relative to where the entity faces.",c->{
            float spd=c.f("speed")*(c.w.input.held(Input.ALT)?1.6f:1f);float fw=-c.w.input.moveY,st=c.b("strafe")?c.w.input.moveX:0;
            if(!c.b("strafe"))c.self.angle+=c.w.input.moveX*2.4f*c.dt;
            float cs=(float)Math.cos(c.self.angle),sn=(float)Math.sin(c.self.angle);
            c.self.wishX=(cs*fw-sn*st)*spd;c.self.wishY=(sn*fw+cs*st)*spd;c.self.wishActive=true;return RUNNING;},"speed:f=6","strafe:b=true");
        def("look_touch","Look With Touch","Input",Icons.CROSSHAIR,"Turns the view by swiping on the right half of the screen.",c->{
            c.self.angle+=c.w.input.lookX*.0065f*c.f("sensitivity");
            if(c.b("pitch"))c.self.pitch=Math.max(-.55f,Math.min(.55f,c.self.pitch-c.w.input.lookY*.0045f*c.f("sensitivity")));return RUNNING;},"sensitivity:f=1","pitch:b=true");
        def("button_pressed","On Button Pressed","Input",Icons.ZAP,"Sends an event the moment a button is pressed.",c->{if(c.w.input.pressed(c.choice("button",BUTTONS)))c.send("event");return RUNNING;},"button:c("+BUTTONS+")=Fire","event:e=");
        def("button_held","While Button Held","Input",Icons.ZAP,"Sends an event every frame a button is held down.",c->{if(c.w.input.held(c.choice("button",BUTTONS)))c.send("event");return RUNNING;},"button:c("+BUTTONS+")=Fire","event:e=");
        def("button_released","On Button Released","Input",Icons.ZAP,"Sends an event when a button is released.",c->{if(c.w.input.released(c.choice("button",BUTTONS)))c.send("event");return RUNNING;},"button:c("+BUTTONS+")=Fire","event:e=");
        def("get_joystick","Get Joystick","Input",Icons.MOVE,"Stores the joystick X and Y (-1 to 1) in variables.",c->{c.setVar("x",c.w.input.moveX);c.setVar("y",-c.w.input.moveY);return RUNNING;},"x:v=moveX","y:v=moveY");
        def("joystick_moved","Joystick Moved","Input",Icons.MOVE,"Sends an event while the joystick is moved, or when it is idle.",c->{
            boolean mv=Math.hypot(c.w.input.moveX,c.w.input.moveY)>c.f("threshold");c.send(mv?"movingEvent":"idleEvent");return RUNNING;},"threshold:f=0.2","movingEvent:e=","idleEvent:e=");
        def("use_on_button","Use On Button","Input",Icons.DOOR,"Opens doors, presses switches and uses entities when the button is pressed.",c->{if(c.w.input.pressed(c.choice("button",BUTTONS)))c.w.use(c.self);return RUNNING;},"button:c("+BUTTONS+")=Use");
        def("weapon_on_button","Switch Weapon On Button","Input",Icons.RESET,"Equips the next weapon in the pack when the button is pressed.",c->{if(c.w.input.pressed(c.choice("button",BUTTONS)))c.w.nextWeapon();return RUNNING;},"button:c("+BUTTONS+")=Weapon");
        def("set_move_speed","Set Move Speed","Input",Icons.ARROW_UP,"Sets the entity's movement speed used by AI movement.",c->{c.self.speed=c.f("speed");return DONE;},"speed:f=6");

        // ------------------------------------------------------------------ Combat (d20)
        def("attack","Attack With Weapon","Combat",Icons.SWORD,"The hero swings or fires the equipped weapon: d20 + modifier + proficiency against the target's AC. Sends the ready event once the weapon can be used again.",c->{
            if(c.enter){c.m[0]=0;}
            if(c.m[0]==0){if(!c.w.weaponReady())return RUNNING;c.w.attack();c.m[0]=1;return RUNNING;}
            if(c.w.weaponReady()){c.send("readyEvent");return DONE;}return RUNNING;},"readyEvent:e=READY");
        def("cast_spell","Cast Spell","Combat",Icons.SPARKLE,"The hero casts a spell they know (or the selected one), paying spell points. Sends the ready event when done, the fail event if there aren't enough points.",c->{
            if(c.enter){c.m[0]=0;}
            if(c.m[0]==0){if(!c.w.weaponReady())return RUNNING;String n=c.s("spell");Rpg.Spell sp=n.equalsIgnoreCase("Selected")||n.isEmpty()?(c.w.hero==null?null:c.w.hero.currentSpell()):Rpg.spell(n);
                if(sp==null){c.w.message("You don't know any spells",1.5f);c.send("failEvent");return DONE;}
                if(c.w.castSpell(sp,false)==World.FIRE_NO_POWER){c.send("failEvent");return DONE;}c.m[0]=1;return RUNNING;}
            if(c.w.weaponReady()){c.send("readyEvent");return DONE;}return RUNNING;},"spell:c(Selected|"+Rpg.SPELLS_JOINED+")=Selected","readyEvent:e=READY","failEvent:e=READY");
        def("next_spell","Select Next Spell","Combat",Icons.CHEVRON_RIGHT,"Cycles the hero's selected spell.",c->{Hero h=c.w.hero;if(h!=null&&!h.spells.isEmpty()){h.selectedSpell=(h.selectedSpell+1)%h.spells.size();c.w.message("Spell: "+h.currentSpell().name+" ("+Rpg.spellCost(h.currentSpell().level)+" SP)",1.5f);}return DONE;});
        def("next_weapon","Next Weapon","Combat",Icons.CHEVRON_RIGHT,"Equips the next weapon in the hero's pack.",c->{c.w.nextWeapon();return DONE;});
        def("weapon_ready_event","Weapon Ready","Combat",Icons.CHECK,"Sends an event once the hero can attack again.",c->{if(c.w.weaponReady()){c.send("event");return DONE;}return RUNNING;},"event:e=READY");
        def("creature_melee","Creature Melee Attack","Combat",Icons.SKULL,"Attacks the target with this creature's stats (attack bonus vs AC, damage dice). Sends the miss event if the target is out of reach.",c->{
            if(!c.w.meleeAttack(c.self,c.f("reach")))c.send("outOfReachEvent");return DONE;},"reach:f=1.3","outOfReachEvent:e=");
        def("creature_ranged","Creature Ranged Attack","Combat",Icons.CROSSHAIR,"Shoots an arrow, fire bolt or arcane bolt at the target using this creature's attack bonus and damage dice.",c->{
            int t=c.choice("projectile","Fire Bolt|Arrow|Arcane");c.w.rangedAttack(c.self,t==1?World.P_ARROW:t==2?World.P_MAGIC:World.P_FIRE);return DONE;},"projectile:c(Fire Bolt|Arrow|Arcane)=Arrow");
        def("creature_cast","Creature Casts Spell","Combat",Icons.SPARKLE,"This creature casts a spell at its target (spell attack = its attack bonus, save DC = 8 + attack bonus).",c->{c.w.monsterCast(c.self,Rpg.spell(c.s("spell")));return DONE;},"spell:c("+Rpg.SPELLS_JOINED+")=Fire Bolt");
        def("roll_dice","Roll Dice","Combat",Icons.DICE,"Rolls dice like 1d20, 2d6+3 or 4d8 and stores the total. Sends the success event if it meets the target.",c->{
            int r=Rpg.Dice.parse(c.s("dice")).roll(c.w.rng);c.setVar("store",r);if(r>=c.i("target"))c.send("successEvent");else c.send("failEvent");return DONE;},"dice:t=1d20","store:v=roll","target:i=10","successEvent:e=","failEvent:e=");
        def("ability_check","Ability Check","Combat",Icons.DICE,"d20 + ability modifier against a difficulty class (the hero adds proficiency for their class's main ability).",c->{
            Actor a=c.who("who");int ab=c.choice("ability",ABIL);if(a!=null&&c.w.save(a,ab,c.i("dc")))c.send("successEvent");else c.send("failEvent");return DONE;},"who:c("+WHO+")=Player","ability:c("+ABIL+")=STR","dc:i=12","successEvent:e=","failEvent:e=");
        def("saving_throw","Saving Throw","Combat",Icons.VALIDATE,"Makes an entity roll a saving throw; on a failure it takes the damage dice (half on a success if 'halfOnSave').",c->{
            Actor a=c.who("who");if(a==null)return DONE;boolean ok=c.w.save(a,c.choice("ability",ABIL),c.i("dc"));int dmg=Rpg.Dice.parse(c.s("damage")).roll(c.w.rng);
            if(!ok){if(dmg>0)c.w.damage(a,dmg,c.self);c.send("failEvent");}else{if(dmg>0&&c.b("halfOnSave"))c.w.damage(a,dmg/2,c.self);c.w.floater(a,"saved",0xffb0b8c0);c.send("successEvent");}return DONE;},
            "who:c("+WHO+")=Player","ability:c("+ABIL+")=DEX","dc:i=13","damage:t=2d6","halfOnSave:b=true","successEvent:e=","failEvent:e=");
        def("damage_dice","Deal Damage (Dice)","Combat",Icons.MINUS,"Deals rolled damage to the player, this entity or its target.",c->{Actor a=c.who("to");if(a!=null)c.w.damage(a,Math.max(1,Rpg.Dice.parse(c.s("dice")).roll(c.w.rng)),c.self);return DONE;},"to:c("+WHO+")=Target","dice:t=1d6");
        def("area_damage","Area Damage (Save)","Combat",Icons.SUN,"Damages everything around this entity; each makes a DEX save for half (like a trap or explosion).",c->{
            c.w.effect(Sprites.FX_EXPLOSION,c.self.x,c.self.y,c.self.z+.5f);c.w.areaDamage(c.self,c.self.x,c.self.y,c.self.z,c.f("radius"),Rpg.Dice.parse(c.s("dice")),Rpg.FIRE,c.i("dc"));return DONE;},"radius:f=2.5","dice:t=4d6","dc:i=13");

        // ------------------------------------------------------------------ Character
        def("give_xp","Give Experience","Character",Icons.SPARKLE,"Awards experience points to the hero (levelling up when they reach the next threshold).",c->{c.w.awardXp(c.i("amount"),c.w.player);return DONE;},"amount:i=50");
        def("get_hero_stat","Get Hero Stat","Character",Icons.TAG,"Stores one of the hero's numbers in a variable.",c->{Hero h=c.w.hero;if(h==null)return DONE;int v;
            switch(c.choice("stat","HP|Max HP|SP|Max SP|AC|Level|XP|Gold|STR|DEX|CON|INT|WIS|CHA")){case 0:v=c.w.player.health;break;case 1:v=h.maxHp;break;case 2:v=h.sp;break;case 3:v=h.maxSp;break;case 4:v=h.ac();break;case 5:v=h.level;break;case 6:v=h.xp;break;case 7:v=h.gold;break;
                default:v=h.scores[c.choice("stat","HP|Max HP|SP|Max SP|AC|Level|XP|Gold|STR|DEX|CON|INT|WIS|CHA")-8];}
            c.setVar("store",v);return c.doneOrRun();},"stat:c(HP|Max HP|SP|Max SP|AC|Level|XP|Gold|STR|DEX|CON|INT|WIS|CHA)=Level","store:v=stat","everyFrame:b=false");
        def("level_at_least","Level At Least","Character",Icons.VALIDATE,"Sends an event depending on whether the hero has reached a level.",c->{if(c.w.hero!=null&&c.w.hero.level>=c.i("level"))c.send("yesEvent");else c.send("noEvent");return DONE;},"level:i=3","yesEvent:e=","noEvent:e=");
        def("is_class","Hero Class Is","Character",Icons.PERSON,"Sends an event if the hero is a particular class.",c->{if(c.w.hero!=null&&c.w.hero.cls.name.equalsIgnoreCase(c.s("class")))c.send("yesEvent");else c.send("noEvent");return DONE;},"class:c("+Rpg.CLASSES_JOINED+")=Fighter","yesEvent:e=","noEvent:e=");
        def("long_rest","Rest","Character",Icons.MOON,"Fully restores the hero's hit points and spell points (e.g. at an inn or shrine).",c->{Hero h=c.w.hero;if(h!=null){c.w.heal(c.w.player,h.maxHp);h.sp=h.maxSp;c.w.sound(SoundFx.HEAL,c.w.player);c.w.message("You feel rested",2);}return DONE;});
        def("restore_sp","Restore Spell Points","Character",Icons.SPARKLE,"Gives the hero spell points (up to their maximum).",c->{Hero h=c.w.hero;if(h!=null)h.sp=Math.min(h.maxSp,h.sp+c.i("amount"));return DONE;},"amount:i=4");
        def("open_character","Open Character Sheet","Character",Icons.PERSON,"Opens the hero's inventory and character sheet (pauses the game).",c->{c.w.menuRequested=true;return DONE;});

        // ------------------------------------------------------------------ Inventory
        def("give_item","Give Item","Inventory",Icons.PACKAGE,"Puts an item in the hero's pack (weapons and armour are equipped if that slot is empty).",c->{Rpg.Item it=Rpg.itemByName(c.s("item"));if(it==null||!c.w.giveItem(it,Math.max(1,c.i("count"))))c.send("failEvent");return DONE;},"item:c("+Rpg.ITEMS_JOINED+")=Potion of Healing","count:i=1","failEvent:e=");
        def("take_item","Take Item","Inventory",Icons.MINUS,"Removes items from the hero's pack; sends the fail event if they don't have enough.",c->{Rpg.Item it=Rpg.itemByName(c.s("item"));if(c.w.hero==null||it==null||!c.w.hero.remove(it,Math.max(1,c.i("count"))))c.send("failEvent");else c.send("doneEvent");return DONE;},"item:c("+Rpg.ITEMS_JOINED+")=Gemstone","count:i=1","doneEvent:e=","failEvent:e=");
        def("has_item","Has Item","Inventory",Icons.VALIDATE,"Checks whether the hero carries at least a number of an item.",c->{Rpg.Item it=Rpg.itemByName(c.s("item"));if(c.w.hero!=null&&it!=null&&c.w.hero.count(it)>=Math.max(1,c.i("count")))c.send("yesEvent");else c.send("noEvent");return DONE;},"item:c("+Rpg.ITEMS_JOINED+")=Gemstone","count:i=1","yesEvent:e=","noEvent:e=");
        def("equip_item","Equip Item","Inventory",Icons.SHIELD,"Equips a weapon, armour or shield the hero carries.",c->{Rpg.Item it=Rpg.itemByName(c.s("item"));if(c.w.hero==null||it==null||c.w.hero.count(it)==0||c.w.hero.equip(it)!=null)c.send("failEvent");return DONE;},"item:c("+Rpg.itemNames(Rpg.I_WEAPON)+"|"+Rpg.itemNames(Rpg.I_ARMOR)+"|Shield)=Longsword","failEvent:e=");
        def("give_gold","Give Gold","Inventory",Icons.COIN,"Gives the hero gold pieces.",c->{c.w.giveGold(Rpg.Dice.parse(c.s("amount")).roll(c.w.rng));return DONE;},"amount:t=2d6");
        def("pay_gold","Pay Gold","Inventory",Icons.COIN,"Takes gold from the hero; sends the fail event (and takes nothing) if they can't afford it.",c->{Hero h=c.w.hero;int a=c.i("amount");if(h==null||h.gold<a){c.send("failEvent");return DONE;}h.gold-=a;c.w.sound(SoundFx.COIN,c.w.player);c.send("paidEvent");return DONE;},"amount:i=10","paidEvent:e=","failEvent:e=");
        def("sell_item","Sell Item To Hero","Inventory",Icons.COIN,"A shop sale: if the hero can pay the price they get the item.",c->{Hero h=c.w.hero;Rpg.Item it=Rpg.itemByName(c.s("item"));int price=c.i("price");
            if(h==null||it==null||h.gold<price){c.w.message("You can't afford that",2);c.send("failEvent");return DONE;}h.gold-=price;c.w.giveItem(it,1);c.w.sound(SoundFx.COIN,c.w.player);c.w.message("Bought "+it.name,2);c.send("boughtEvent");return DONE;},
            "item:c("+Rpg.ITEMS_JOINED+")=Potion of Healing","price:i=50","boughtEvent:e=","failEvent:e=");
        def("has_gold","Has Gold","Inventory",Icons.COIN,"Checks whether the hero has at least an amount of gold.",c->{if(c.w.hero!=null&&c.w.hero.gold>=c.i("amount"))c.send("yesEvent");else c.send("noEvent");return DONE;},"amount:i=10","yesEvent:e=","noEvent:e=");
        def("give_key","Give Key","Inventory",Icons.TAG,"Gives the hero a door key.",c->{if(!c.w.giveKey(c.choice("key",KEYS)))c.send("failEvent");return DONE;},"key:c("+KEYS+")=Bronze","failEvent:e=");
        def("has_key","Has Key","Inventory",Icons.VALIDATE,"Checks whether the hero has a key.",c->{if(c.w.hasKey(c.choice("key",KEYS)))c.send("yesEvent");else c.send("noEvent");return DONE;},"key:c("+KEYS+")=Bronze","yesEvent:e=","noEvent:e=");
        def("drink_potion","Drink Healing Potion","Inventory",Icons.HEART,"Drinks the hero's best healing potion.",c->{if(!c.w.quaff())c.send("noneEvent");return DONE;},"noneEvent:e=");
        def("use_now","Use","Inventory",Icons.DOOR,"Uses whatever is in front of the entity: talk to someone, open a door, pull a lever.",c->{c.w.use(c.self);return DONE;});

        // ------------------------------------------------------------------ Dialogue
        def("say","Say / Ask","Dialogue",Icons.CHAT,"Shows a conversation box (the game pauses). Up to three answers; each sends its event to this entity. With no answers it shows Continue and sends the done event.",c->{
            if(c.enter){java.util.ArrayList<String> ch=new java.util.ArrayList<>(),ev=new java.util.ArrayList<>();
                for(int k=1;k<=3;k++){String t=c.s("answer"+k);if(!t.isEmpty()){ch.add(t);ev.add(c.s("event"+k));}}
                if(ch.isEmpty()){ch.add("Continue");ev.add(c.s("doneEvent"));}
                String sp=c.s("speaker");if(sp.isEmpty()&&c.self.def!=null)sp=c.self.def.name;
                c.w.say(c.self,sp,c.s("text"),ch.toArray(new String[0]),ev.toArray(new String[0]));c.m[0]=1;return RUNNING;}
            return c.w.dialogue==null?DONE:RUNNING;},
            "speaker:t=","text:t=Well met, traveller.","answer1:t=","event1:e=","answer2:t=","event2:e=","answer3:t=","event3:e=","doneEvent:e=");
        def("dialogue_open","Is Talking","Dialogue",Icons.CHAT,"Sends an event while a conversation box is open.",c->{if(c.w.dialogue!=null)c.send("event");return c.doneOrRun();},"event:e=","everyFrame:b=true");

        // ------------------------------------------------------------------ Quests
        def("start_quest","Start Quest","Quests",Icons.SCROLL,"Adds a quest to the hero's journal.",c->{World.Quest q=c.w.quest(c.s("id"));if(q.state==World.Q_INACTIVE){q.state=World.Q_ACTIVE;q.title=c.s("title").isEmpty()?q.id:c.s("title");q.text=c.s("objective");c.w.centerPrint("New quest: "+q.title,3);c.w.sound(SoundFx.SECRET,c.w.player);}return DONE;},
            "id:t=quest1","title:t=The Lost Relic","objective:t=Find the relic in the crypt.");
        def("update_quest","Update Quest","Quests",Icons.SCROLL,"Changes a quest's current objective.",c->{World.Quest q=c.w.quest(c.s("id"));q.text=c.s("objective");if(q.state==World.Q_ACTIVE)c.w.message("Journal updated: "+q.text,3);return DONE;},"id:t=quest1","objective:t=Return the relic to the priest.");
        def("complete_quest","Complete Quest","Quests",Icons.CHECK,"Completes a quest and rewards the hero with XP and gold.",c->{World.Quest q=c.w.quest(c.s("id"));if(q.state!=World.Q_DONE){q.state=World.Q_DONE;c.w.centerPrint("Quest complete: "+q.title,3);c.w.sound(SoundFx.LEVEL_UP,c.w.player);
                if(c.i("gold")>0)c.w.giveGold(c.i("gold"));c.w.awardXp(c.i("xp"),c.w.player);}return DONE;},"id:t=quest1","xp:i=100","gold:i=25");
        def("fail_quest","Fail Quest","Quests",Icons.CANCEL,"Marks a quest as failed.",c->{World.Quest q=c.w.quest(c.s("id"));q.state=World.Q_FAILED;c.w.message("Quest failed: "+q.title,3);return DONE;},"id:t=quest1");
        def("quest_state","Quest State","Quests",Icons.SCROLL,"Sends an event for a quest's state: not started, active, done or failed.",c->{
            int st=c.w.questState(c.s("id"));c.send(st==World.Q_ACTIVE?"activeEvent":st==World.Q_DONE?"doneEvent":st==World.Q_FAILED?"failedEvent":"notStartedEvent");return DONE;},"id:t=quest1","notStartedEvent:e=","activeEvent:e=","doneEvent:e=","failedEvent:e=");

        // ------------------------------------------------------------------ Legacy (older projects; hidden from the browser)
        def("fire_weapon","Fire Weapon (old)","Legacy",Icons.SWORD,"Older projects: attacks with the hero's weapon.",c->{
            if(c.enter){c.m[0]=0;}
            if(c.m[0]==0){if(!c.w.weaponReady())return RUNNING;c.w.attack();c.m[0]=1;return RUNNING;}
            if(c.w.weaponReady()){c.send("readyEvent");return DONE;}return RUNNING;},"readyEvent:e=READY","noAmmoEvent:e=NO_AMMO");
        def("select_weapon","Select Weapon (old)","Legacy",Icons.SWORD,"Older projects: no effect.",c->DONE,"weapon:t=","failEvent:e=");
        def("give_weapon","Give Weapon (old)","Legacy",Icons.PACKAGE,"Older projects: gives a comparable fantasy weapon.",c->{String w=c.s("weapon");String id=w.equalsIgnoreCase("Shotgun")?"longsword":w.equalsIgnoreCase("Chaingun")?"longbow":w.equalsIgnoreCase("Rocket Launcher")?"greatsword":"dagger";
            c.w.giveItem(Rpg.item(id),1);c.w.message("You found a "+Rpg.item(id).name.toLowerCase()+"!",2);return DONE;},"weapon:t=Shotgun","ammo:i=0","failEvent:e=");
        def("has_weapon","Has Weapon (old)","Legacy",Icons.VALIDATE,"Older projects.",c->{c.send("yesEvent");return DONE;},"weapon:t=","yesEvent:e=","noEvent:e=");
        def("give_ammo","Give Ammo (old)","Legacy",Icons.COIN,"Older projects: gives a little gold instead.",c->{c.w.giveGold(Math.max(1,c.i("amount")/4));return DONE;},"type:t=","amount:i=10","failEvent:e=");
        def("get_ammo","Get Ammo (old)","Legacy",Icons.TAG,"Older projects.",c->DONE,"type:t=","store:v=ammo","everyFrame:b=false");
        def("check_ammo","Check Ammo (old)","Legacy",Icons.VALIDATE,"Older projects: always enough.",c->{c.send("enoughEvent");return DONE;},"type:t=","min:i=1","enoughEvent:e=","emptyEvent:e=","everyFrame:b=false");
        def("take_ammo","Take Ammo (old)","Legacy",Icons.MINUS,"Older projects.",c->DONE,"type:t=","amount:i=1");
        def("give_armor","Give Armor (old)","Legacy",Icons.SHIELD,"Older projects: gives chain mail.",c->{c.w.giveItem(Rpg.item("chainmail"),1);return DONE;},"amount:i=100","max:i=100","failEvent:e=");

        // ------------------------------------------------------------------ Health
        def("give_health","Give Health","Health",Icons.HEART,"Heals up to a maximum. Sends the fail event if already at the maximum.",c->{if(!c.w.giveHealth(c.w.player,c.i("amount"),c.i("max")))c.send("failEvent");return DONE;},"amount:i=25","max:i=100","failEvent:e=");
        def("damage_self","Damage Self","Health",Icons.MINUS,"Hurts this entity.",c->{c.w.damage(c.self,c.i("amount"),null);return DONE;},"amount:i=10");
        def("damage_target","Damage Entity","Health",Icons.MINUS,"Hurts the target or the player.",c->{Actor t=c.who("to");if(t!=null)c.w.damage(t,c.i("amount"),c.self);return DONE;},"amount:i=10","to:c(Target|Player)=Target");
        def("kill_self","Kill Self","Health",Icons.SKULL,"Kills this entity (sends DIED).",c->{c.w.damage(c.self,100000,null);return DONE;});
        def("set_health","Set Health","Health",Icons.HEART,"Sets this entity's health.",c->{c.self.health=Math.max(1,c.i("value"));return DONE;},"value:i=100");
        def("health_compare","Health Below","Health",Icons.HEART,"Sends an event when health drops below a value.",c->{if(c.self.health<c.i("below"))c.send("event");return c.doneOrRun();},"below:i=30","event:e=","everyFrame:b=true");
        def("set_invulnerable","Set Invulnerable","Health",Icons.VALIDATE,"Makes this entity ignore damage.",c->{c.self.invulnerable=c.b("value");return DONE;},"value:b=true");
        def("radius_damage","Explode Damage","Health",Icons.SUN,"Damages everything within a radius of this entity.",c->{c.w.radiusDamage(c.self,c.i("damage"),c.f("radius"));return DONE;},"damage:i=80","radius:f=3");

        // ------------------------------------------------------------------ Movement
        def("move_forward","Move Forward","Movement",Icons.ARROW_UP,"Moves in the facing direction.",c->{c.w.walk(c.self,c.self.angle,c.f("speed"));return c.doneOrRun();},"speed:f=2","everyFrame:b=true");
        def("move_towards","Move Towards","Movement",Icons.ARROW_UP,"Walks straight towards the target or player; sends an event on arrival.",c->{
            Actor t=c.who("to");if(t==null)return DONE;if(c.w.dist(c.self,t)<=c.f("arriveDistance")){c.self.stop();c.send("arriveEvent");return DONE;}
            c.self.angle=c.w.angleTo(c.self,t);c.w.walk(c.self,c.self.angle,c.f("speed"));return RUNNING;},"to:c(Target|Player)=Target","speed:f=3","arriveDistance:f=1","arriveEvent:e=");
        def("chase_target","Chase Target","Movement",Icons.SKULL,"Classic chase: moves towards the target and steers around walls.",c->{
            if(c.self.target==null||!c.self.target.alive){c.send("noTargetEvent");return RUNNING;}c.w.chase(c.self,c.f("speed"),c.dt);return RUNNING;},"speed:f=2.5","noTargetEvent:e=");
        def("wander","Wander","Movement",Icons.MOVE,"Wanders around, changing direction now and then.",c->{
            if(c.enter)c.m[0]=0;c.m[0]-=c.dt;if(c.m[0]<=0||c.self.blocked){c.m[0]=c.f("turnInterval")*(0.5f+c.w.rng.nextFloat());c.self.angle=(float)(c.w.rng.nextInt(8)*Math.PI/4);}
            c.w.walk(c.self,c.self.angle,c.f("speed"));return RUNNING;},"speed:f=1.5","turnInterval:f=1.5");
        def("turn","Turn","Movement",Icons.RESET,"Rotates continuously.",c->{c.self.angle+=(float)Math.toRadians(c.f("degreesPerSecond"))*c.dt;return RUNNING;},"degreesPerSecond:f=90");
        def("face_target","Face Entity","Movement",Icons.CROSSHAIR,"Turns to face the target or player.",c->{Actor t=c.who("to");if(t!=null)c.self.angle=c.w.angleTo(c.self,t);return c.doneOrRun();},"to:c(Target|Player)=Target","everyFrame:b=false");
        def("set_angle","Set Angle","Movement",Icons.RESET,"Faces a fixed direction in degrees (0 = east, 90 = south).",c->{c.self.angle=(float)Math.toRadians(c.f("degrees"));return DONE;},"degrees:f=0");
        def("jump","Jump","Movement",Icons.ARROW_UP,"Jumps if standing on the ground.",c->{if(c.self.onGround){c.self.vz=c.f("velocity");c.self.onGround=false;}return DONE;},"velocity:f=5.5");
        def("thrust","Thrust","Movement",Icons.EXTRUDE,"Pushes the entity forward and/or up.",c->{float cs=(float)Math.cos(c.self.angle),sn=(float)Math.sin(c.self.angle);c.self.vx+=cs*c.f("forward");c.self.vy+=sn*c.f("forward");c.self.vz+=c.f("up");c.self.onGround=false;return DONE;},"forward:f=4","up:f=0");
        def("stop_moving","Stop Moving","Movement",Icons.STOP,"Stops all movement.",c->{c.self.stop();return DONE;});
        def("teleport","Teleport","Movement",Icons.SPARKLE,"Moves instantly to a map position (if there is room).",c->{c.w.teleport(c.self,c.f("x"),c.f("y"));return DONE;},"x:f=0","y:f=0");
        def("return_to_spawn","Return To Spawn","Movement",Icons.FLAG,"Teleports back to where the entity started.",c->{c.w.teleport(c.self,c.self.spawnX,c.self.spawnY);return DONE;});
        def("set_flying","Set Flying","Movement",Icons.LAYERS,"Turns gravity off (flying) or on.",c->{c.self.noGravity=c.b("value");return DONE;},"value:b=true");
        def("float_bob","Float Bob","Movement",Icons.SUN_DIM,"Bobs the sprite gently up and down, like a pickup.",c->{c.self.bob=(float)Math.sin(c.w.time*c.f("speed")*Math.PI)*c.f("height");return RUNNING;},"height:f=0.12","speed:f=1");

        // ------------------------------------------------------------------ AI
        def("look_for_player","Look For Player","AI",Icons.CROSSHAIR,"Sends the see event when the player is visible within range and field of view, and targets them.",c->{
            if(c.w.canSeePlayer(c.self,c.f("range"),c.f("fov"))){c.self.target=c.w.player;c.send("seeEvent");return DONE;}return c.doneOrRun();},"range:f=20","fov:f=180","seeEvent:e=SEE","everyFrame:b=true");
        def("line_of_sight","Line Of Sight","AI",Icons.CROSSHAIR,"Checks whether the target or player is visible.",c->{Actor t=c.who("to");if(t!=null&&c.w.lineOfSight(c.self,t))c.send("visibleEvent");else c.send("hiddenEvent");return c.doneOrRun();},"to:c(Target|Player)=Player","visibleEvent:e=","hiddenEvent:e=","everyFrame:b=false");
        def("distance_check","Distance Check","AI",Icons.MOVE,"Sends an event depending on the distance to the target or player.",c->{
            Actor t=c.who("to");if(t==null)return c.doneOrRun();if(c.w.dist(c.self,t)<c.f("distance"))c.send("closerEvent");else c.send("fartherEvent");return c.doneOrRun();},"to:c(Target|Player)=Target","distance:f=2","closerEvent:e=","fartherEvent:e=","everyFrame:b=true");
        def("set_target_player","Target Player","AI",Icons.CROSSHAIR,"Makes the player this entity's target.",c->{c.self.target=c.w.player;return DONE;});
        def("clear_target","Clear Target","AI",Icons.CANCEL,"Forgets the current target.",c->{c.self.target=null;return DONE;});
        def("target_dead_event","Target Lost","AI",Icons.SKULL,"Sends an event when there is no living target.",c->{if(c.self.target==null||!c.self.target.alive)c.send("event");return c.doneOrRun();},"event:e=LOST","everyFrame:b=true");
        def("hitscan_attack","Hitscan Attack","AI",Icons.CROSSHAIR,"Fires instant bullets at the target (like a zombieman or shotgun guy).",c->{
            c.self.attackTimer=.3f;Actor t=c.self.target;float ang=t!=null?c.w.angleTo(c.self,t):c.self.angle;
            c.w.playSound(c.s("sound"),c.self,1,1,true);
            c.w.hitscan(c.self,ang,c.i("damageMin"),c.i("damageMax"),(float)Math.toRadians(c.f("spread")),Math.max(1,c.i("pellets")),32);return DONE;},
            "damageMin:i=3","damageMax:i=15","spread:f=5","pellets:i=1","sound:S=Pistol");
        def("melee_attack","Melee Attack","AI",Icons.SKULL,"Bites or claws the target if it is in range; otherwise sends the miss event.",c->{
            c.self.attackTimer=.3f;if(!c.w.melee(c.self,c.f("range"),c.rndInt(c.i("damageMin"),c.i("damageMax"))))c.send("missEvent");return DONE;},"damageMin:i=4","damageMax:i=20","range:f=1.4","missEvent:e=");
        def("fire_projectile","Fire Projectile","AI",Icons.SUN,"Launches a projectile at the target (fireball, rocket or plasma).",c->{
            c.self.attackTimer=.35f;c.w.projectile(c.self,c.choice("type","Fireball|Rocket|Plasma"),c.i("damageMin"),c.i("damageMax"),c.f("speed"));return DONE;},"type:c(Fireball|Rocket|Plasma)=Fireball","damageMin:i=3","damageMax:i=24","speed:f=10");

        // ------------------------------------------------------------------ World
        def("set_sector_light","Set Sector Light","World",Icons.SUN,"Sets a sector's brightness (0-255).",c->{c.w.setLight(c.i("sector"),c.i("light"));return DONE;},"sector:X=0","light:i=255");
        def("fade_sector_light","Fade Sector Light","World",Icons.SUN_DIM,"Fades a sector's brightness over time.",c->{
            Level.Sector s=c.w.sector(c.i("sector"));if(s==null)return DONE;if(c.enter){c.m[0]=s.light;c.m[1]=0;}c.m[1]+=c.dt;float t=Math.min(1,c.m[1]/Math.max(.01f,c.f("seconds")));
            s.light=Math.round(c.m[0]+(c.f("light")-c.m[0])*t);return t>=1?DONE:RUNNING;},"sector:X=0","light:i=64","seconds:f=1");
        def("flicker_light","Flicker Light","World",Icons.SUN,"Randomly flickers a sector's light between min and max.",c->{
            if(c.timer(0,1f/Math.max(.5f,c.f("speed")))){c.m[0]=0;c.w.setLight(c.i("sector"),c.rndInt(c.i("min"),c.i("max")));}return RUNNING;},"sector:X=0","min:i=64","max:i=255","speed:f=8");
        def("open_door","Open Door","World",Icons.DOOR,"Opens a door sector (raises its ceiling).",c->{c.w.openDoor(c.i("sector"),false);return DONE;},"sector:X=0");
        def("close_door","Close Door","World",Icons.DOOR,"Closes a door sector.",c->{c.w.closeDoor(c.i("sector"));return DONE;},"sector:X=0");
        def("raise_floor","Raise Floor","World",Icons.FLOOR_UP,"Raises a sector's floor; sends the done event when it stops.",c->{
            if(c.enter)c.w.moveFloor(c.i("sector"),c.f("amount"),c.f("speed"));if(!c.w.moving(c.i("sector"))){c.send("doneEvent");return DONE;}return RUNNING;},"sector:X=0","amount:f=1","speed:f=1","doneEvent:e=");
        def("lower_floor","Lower Floor","World",Icons.FLOOR_DOWN,"Lowers a sector's floor; sends the done event when it stops.",c->{
            if(c.enter)c.w.moveFloor(c.i("sector"),-c.f("amount"),c.f("speed"));if(!c.w.moving(c.i("sector"))){c.send("doneEvent");return DONE;}return RUNNING;},"sector:X=0","amount:f=1","speed:f=1","doneEvent:e=");
        def("move_ceiling","Move Ceiling","World",Icons.CEIL_UP,"Moves a sector's ceiling up (positive) or down (negative).",c->{
            if(c.enter)c.w.moveCeiling(c.i("sector"),c.f("amount"),c.f("speed"));if(!c.w.moving(c.i("sector"))){c.send("doneEvent");return DONE;}return RUNNING;},"sector:X=0","amount:f=1","speed:f=1","doneEvent:e=");
        def("set_sector_texture","Set Sector Texture","World",Icons.WALL_TEX,"Changes a sector's floor, ceiling or wall texture.",c->{
            Level.Sector s=c.w.sector(c.i("sector"));if(s!=null){int t=Math.max(0,Math.min(Textures.COUNT-1,c.i("texture")));switch(c.choice("surface","Floor|Ceiling|Wall")){case 1:s.ceilingMaterial=t;break;case 2:s.wallMaterial=t;break;default:s.floorMaterial=t;}}return DONE;},
            "sector:X=0","surface:c(Floor|Ceiling|Wall)=Floor","texture:M=0");
        def("go_to_map","Go To Map","World",Icons.FLAG,"Takes the hero to another map. Optionally arrive at the first placed entity of a type (an Arrival Point); otherwise at that map's player start. The hero keeps health, items, gold, keys and quests.",c->{
            int num;try{num=Integer.parseInt(c.s("map"));}catch(NumberFormatException e){num=-1;}c.w.goToMap(num,c.s("arriveAt"));return DONE;},"map:P=","arriveAt:E=");
        def("is_map","Current Map Is","World",Icons.VALIDATE,"Sends one event if the hero is on a particular map, another if not.",c->{
            int num;try{num=Integer.parseInt(c.s("map"));}catch(NumberFormatException e){num=-1;}c.send(c.w.mapNum==num?"yesEvent":"noEvent");return DONE;},"map:P=","yesEvent:e=","noEvent:e=");
        def("exit_level","Exit Level","World",Icons.FLAG,"Finishes the level and shows the results screen.",c->{c.w.exitLevel();return DONE;});
        def("restart_level","Restart Level","World",Icons.RESET,"Restarts the level from the beginning.",c->{c.w.requestRestart();return DONE;});
        def("in_sector_event","In Sector","World",Icons.SECTORS,"Sends an event while this entity is inside a sector.",c->{if(c.w.sectorIndex(c.self)==c.i("sector"))c.send("event");return c.doneOrRun();},"sector:X=0","event:e=","everyFrame:b=true");
        def("player_in_sector","Player In Sector","World",Icons.SECTORS,"Sends an event while the player is inside a sector.",c->{if(c.w.player!=null&&c.w.sectorIndex(c.w.player)==c.i("sector"))c.send("event");return c.doneOrRun();},"sector:X=0","event:e=","everyFrame:b=true");

        // ------------------------------------------------------------------ Entities
        def("spawn_entity","Spawn Entity","Entities",Icons.PLUS_SQUARE,"Creates a new entity in front of this one.",c->{
            float d=c.f("distance");Actor a=c.w.spawn(c.s("entity"),c.self.x+(float)Math.cos(c.self.angle)*d,c.self.y+(float)Math.sin(c.self.angle)*d,c.self.angle);
            if(a!=null&&c.b("storeAsTarget"))c.self.target=a;return DONE;},"entity:E=trooper","distance:f=1.5","storeAsTarget:b=false");
        def("drop_item","Drop Item","Entities",Icons.PACKAGE,"Drops an entity (usually a pickup) where this entity stands.",c->{c.w.spawn(c.s("entity"),c.self.x,c.self.y,0);return DONE;},"entity:E=clip");
        def("destroy_self","Destroy Self","Entities",Icons.TRASH,"Removes this entity from the level.",c->{c.w.remove(c.self);return DONE;});
        def("set_visible","Set Visible","Entities",Icons.SUN,"Shows or hides this entity's sprite.",c->{c.self.visible=c.b("value");return DONE;},"value:b=true");
        def("set_solid","Set Solid","Entities",Icons.STOP,"Makes this entity block movement, or not.",c->{c.self.solid=c.b("value");return DONE;},"value:b=true");
        def("set_shootable","Set Shootable","Entities",Icons.CROSSHAIR,"Lets bullets hit this entity, or not.",c->{c.self.shootable=c.b("value");return DONE;},"value:b=true");
        def("set_sprite","Set Sprite","Entities",Icons.ENTITIES,"Changes the sprite this entity shows.",c->{c.self.sprite=Actions.indexOf(Sprites.NAMES_JOINED,c.s("sprite"));return DONE;},"sprite:c("+Sprites.NAMES_JOINED+")=Trooper");
        def("count_entities","Count Entities","Entities",Icons.LIST,"Stores how many living entities of a type exist.",c->{c.setVar("store",c.w.count(c.s("entity")));return c.doneOrRun();},"entity:E=trooper","store:v=count","everyFrame:b=false");
        def("spawn_effect","Spawn Effect","Entities",Icons.SPARKLE,"Shows a visual effect at this entity.",c->{c.w.effect(c.choice("effect","Puff|Blood|Explosion|Teleport"),c.self.x,c.self.y,c.self.z+c.self.height*.5f);return DONE;},"effect:c(Puff|Blood|Explosion|Teleport)=Explosion");

        // ------------------------------------------------------------------ Camera (first / third person)
        def("third_person_camera","Third Person Camera","Camera",Icons.CAMERA,"Switches the player's view to a third-person camera. Orbit: swipe to circle the camera, the player runs where you push. Shoulder: over-the-shoulder aiming. Chase: the camera swings behind the player.",c->{
            c.w.thirdPersonCamera(c.choice("style",CAM_STYLES),c.f("distance"),c.f("height"),c.f("shoulder"),c.f("smoothing"));return DONE;},
            "style:c("+CAM_STYLES+")=Orbit","distance:f=3.8","height:f=0.8","shoulder:f=0.35","smoothing:f=8");
        def("first_person_camera","First Person Camera","Camera",Icons.CROSSHAIR,"Switches back to the first-person view with the weapon on screen.",c->{c.w.firstPersonCamera();return DONE;});
        def("toggle_camera_view","Toggle Camera View","Camera",Icons.CAMERA,"Flips between first and third person (bind it to a button event).",c->{
            if(c.w.thirdPerson())c.w.firstPersonCamera();else c.w.thirdPersonCamera(c.choice("style",CAM_STYLES),c.f("distance"),-99,-99,-1);return DONE;},
            "style:c("+CAM_STYLES+")=Orbit","distance:f=3.2");
        def("set_camera_style","Set Camera Style","Camera",Icons.CAMERA,"Changes the third-person style without leaving third person.",c->{
            if(c.w.thirdPerson())c.w.thirdPersonCamera(c.choice("style",CAM_STYLES),-1,-99,-99,-1);return DONE;},"style:c("+CAM_STYLES+")=Shoulder");
        def("set_camera_distance","Set Camera Distance","Camera",Icons.FIT,"Moves the third-person camera nearer or further (e.g. zoom in while aiming). Speed 0 snaps.",c->{
            c.w.setCameraDistance(c.f("distance"),c.f("speed"));return DONE;},"distance:f=2","speed:f=8");
        def("set_camera_offset","Set Camera Offset","Camera",Icons.MOVE,"Sets the camera height above the head and the over-the-shoulder offset (negative = left shoulder).",c->{
            c.w.camHeight=c.f("height");c.w.camShoulder=c.f("shoulder");return DONE;},"height:f=0.55","shoulder:f=0.35");
        def("orbit_camera_look","Orbit Camera Look","Camera",Icons.CAMERA,"Swipe to look. In Orbit style this circles the camera around the player; otherwise it turns the player like Look With Touch.",c->{
            float sens=c.f("sensitivity"),inv=c.b("invertY")?-1:1;World w=c.w;
            if(w.thirdPerson()&&w.camStyle==World.CS_ORBIT&&c.self==w.player){
                w.camYaw+=w.input.lookX*.0065f*sens;w.camOrbitPitch=Math.max(-.9f,Math.min(.6f,w.camOrbitPitch-w.input.lookY*.0045f*sens*inv));}
            else{c.self.angle+=w.input.lookX*.0065f*sens;c.self.pitch=Math.max(-.55f,Math.min(.55f,c.self.pitch-w.input.lookY*.0045f*sens*inv));}
            return RUNNING;},"sensitivity:f=1","invertY:b=false");
        def("move_camera_relative","Move Relative To Camera","Camera",Icons.PERSON,"Third-person movement: the joystick moves the player relative to the camera, and the player turns to face where they run (Orbit). In Shoulder/Chase it walks and strafes like the first-person controls.",c->{
            World w=c.w;float spd=c.f("speed")*(w.input.held(Input.ALT)?1.6f:1f);float fw=-w.input.moveY,st=w.input.moveX;
            boolean orbit=w.thirdPerson()&&w.camStyle==World.CS_ORBIT&&c.self==w.player;
            if(!orbit&&w.camStyle==World.CS_CHASE&&w.thirdPerson()){c.self.angle+=st*2.4f*c.dt;st=0;}
            float yaw=orbit?w.camYaw:c.self.angle;float cs=(float)Math.cos(yaw),sn=(float)Math.sin(yaw);
            float vx=(cs*fw-sn*st),vy=(sn*fw+cs*st),m=(float)Math.hypot(vx,vy);
            c.self.wishX=vx*spd;c.self.wishY=vy*spd;c.self.wishActive=true;
            if(orbit&&m>.15f)w.turnToward(c.self,(float)Math.atan2(vy,vx),c.f("turnSpeed"),c.dt);
            return RUNNING;},"speed:f=6","turnSpeed:f=12");
        def("face_movement","Face Movement Direction","Camera",Icons.ARROW_UP,"Smoothly turns this entity to face the direction it is moving.",c->{
            float sp=(float)Math.hypot(c.self.vx,c.self.vy);if(sp>.4f)c.w.turnToward(c.self,(float)Math.atan2(c.self.vy,c.self.vx),c.f("turnSpeed"),c.dt);return RUNNING;},"turnSpeed:f=10");
        def("face_camera","Face Camera Direction","Camera",Icons.CROSSHAIR,"Turns the player to face where the camera looks (e.g. before an attack). Turn speed 0 is instant.",c->{
            World w=c.w;float yaw=w.thirdPerson()?w.camYaw:c.self.angle;w.turnToward(c.self,yaw,c.f("turnSpeed"),c.dt);
            return c.f("turnSpeed")<=0||Math.abs(World.angleDiff(yaw,c.self.angle))<.05f?DONE:RUNNING;},"turnSpeed:f=0");
        def("is_third_person","Is Third Person?","Camera",Icons.CAMERA,"Sends one event if the camera is in third person, another if first person.",c->{
            c.send(c.w.thirdPerson()?"thirdEvent":"firstEvent");return DONE;},"thirdEvent:e=","firstEvent:e=");

        // ------------------------------------------------------------------ Feedback
        def("show_message","Show Message","Feedback",Icons.INFO,"Shows a message at the top of the screen, like item pickups.",c->{c.w.message(c.s("text"),c.f("seconds"));return DONE;},"text:t=Hello","seconds:f=3");
        def("center_print","Center Message","Feedback",Icons.INFO,"Shows large text in the middle of the screen.",c->{c.w.centerPrint(c.s("text"),c.f("seconds"));return DONE;},"text:t=","seconds:f=2");
        def("screen_flash","Screen Flash","Feedback",Icons.SUN,"Tints the screen briefly (red for pain, gold for pickups).",c->{c.w.flash(c.choice("color","Red|Gold|White|Green"),c.f("strength"));return DONE;},"color:c(Red|Gold|White|Green)=Gold","strength:f=0.4");
        def("spray_blood","Spray Blood","Feedback",Icons.POTION,"Sprays red droplets from this entity; they splat on nearby floors and walls.",c->{c.w.spray(c.self,null,Math.max(1,c.i("amount")),false,false);return DONE;},"amount:i=5");
        def("camera_shake","Camera Shake","Feedback",Icons.MOVE,"Shakes the player's view.",c->{c.w.shake(c.f("strength"),c.f("seconds"));return DONE;},"strength:f=0.3","seconds:f=0.5");

        // ------------------------------------------------------------------ Audio
        def("play_sound","Play Sound","Audio",Icons.SPEAKER,"Plays a built-in or project sound. Positional sounds get quieter with distance from the player. Pitch is in semitones.",c->{
            c.w.playSound(c.s("sound"),c.self,c.f("volume"),pitchRate(c),c.b("positional"));return DONE;},
            "sound:S=Pickup","volume:f=1","pitch:f=0","randomPitch:f=0","positional:b=true");
        def("play_sound_wait","Play Sound And Wait","Audio",Icons.SPEAKER,"Plays a sound, then sends an event when it has finished.",c->{
            if(c.enter){c.m[0]=c.w.playSound(c.s("sound"),c.self,c.f("volume"),pitchRate(c),c.b("positional"));c.m[1]=0;}
            c.m[1]+=c.dt;if(c.m[0]==0||!c.w.soundPlaying((int)c.m[0])||c.m[1]>12){c.send("finishedEvent");return DONE;}return RUNNING;},
            "sound:S=Pickup","volume:f=1","pitch:f=0","randomPitch:f=0","positional:b=true","finishedEvent:e=FINISHED");
        def("random_sound","Random Sound","Audio",Icons.SPARKLE,"Plays one of up to three sounds, picked at random. Good for footsteps and grunts.",c->{
            ArrayList<String> l=new ArrayList<>();for(String k:new String[]{"soundA","soundB","soundC"})if(!c.s(k).isEmpty())l.add(c.s(k));
            if(!l.isEmpty())c.w.playSound(l.get(c.w.rng.nextInt(l.size())),c.self,c.f("volume"),pitchRate(c),c.b("positional"));return DONE;},
            "soundA:S=Monster Sight","soundB:S=Monster Pain","soundC:S=","volume:f=1","pitch:f=0","randomPitch:f=1","positional:b=true");
        def("repeat_sound","Repeat Sound","Audio",Icons.RESET,"Keeps playing a sound at a fixed interval while the state is active (engines, alarms, humming).",c->{
            if(c.enter)c.m[0]=9999;c.m[0]+=c.dt;if(c.m[0]>=Math.max(.05f,c.f("interval"))){c.m[0]=0;c.w.playSound(c.s("sound"),c.self,c.f("volume"),pitchRate(c),c.b("positional"));}return RUNNING;},
            "sound:S=Beep","interval:f=1","volume:f=1","pitch:f=0","randomPitch:f=0","positional:b=true");
        def("stop_sounds","Stop All Sounds","Audio",Icons.STOP,"Silences every sound effect that is playing (music keeps going).",c->{c.w.stopSounds();return DONE;});
        def("set_sound_volume","Set Sound Volume","Audio",Icons.SPEAKER,"Sets the volume of all sound effects for this play session (0 to 1).",c->{c.w.setSoundVolume(c.f("volume"));return DONE;},"volume:f=1");
        def("play_music","Play Music","Audio",Icons.MUSIC,"Starts a song from the Sound & Music editor, replacing the current one.",c->{
            c.w.playMusic(c.s("song"),c.b("loop"),c.f("fadeIn"));return DONE;},"song:U=","loop:b=true","fadeIn:f=0");
        def("stop_music","Stop Music","Audio",Icons.STOP,"Stops the music, optionally fading it out over some seconds.",c->{c.w.stopMusic(c.f("fadeOut"));return DONE;},"fadeOut:f=1");
        def("set_music_volume","Set Music Volume","Audio",Icons.MUSIC,"Sets the music volume for this play session (0 to 1).",c->{c.w.setMusicVolume(c.f("volume"));return DONE;},"volume:f=0.8");
        def("music_beat","On Music Beat","Audio",Icons.MUSIC,"Sends an event in time with the music: every 4 steps is one beat, 16 is one bar.",c->{
            long t=c.w.musicTicks();if(c.enter)c.m[0]=t<=0?-1:(t-1)/Math.max(1,c.i("every"));
            if(t>0){float beat=(t-1)/Math.max(1,c.i("every"));if(beat!=c.m[0]){c.m[0]=beat;c.send("event");}}return RUNNING;},"every:i=4","event:e=BEAT");
        def("music_playing","Is Music Playing?","Audio",Icons.MUSIC,"Sends one event if music is playing, another if it is silent.",c->{c.send(c.w.musicPlaying()?"yesEvent":"noEvent");return c.doneOrRun();},"yesEvent:e=","noEvent:e=","everyFrame:b=false");
    }
    /** Playback speed from an action's pitch and random pitch (semitones). */
    private static float pitchRate(Ctx c){float st=c.f("pitch");float rp=c.f("randomPitch");if(rp>0)st+=c.rnd(-rp,rp);return (float)Math.pow(2,st/12f);}
}
