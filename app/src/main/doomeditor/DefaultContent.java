package com.retro.doomeditor;

import java.util.ArrayList;

/** Starter entity types with ready-made FSMs that recreate classic Doom behaviour. */
final class DefaultContent {
    private DefaultContent(){}

    static ArrayList<EntityDef> all(){
        ArrayList<EntityDef> l=new ArrayList<>();
        l.add(player());
        l.add(monster("trooper","Trooper",Sprites.TROOPER,20,2.4f,false));
        l.add(monster("imp","Imp",Sprites.IMP,60,2.2f,true));
        l.add(demon());
        l.add(pickup("medkit","Medkit",Sprites.MEDKIT,"give_health","Picked up a medikit.",new String[]{"amount","25","max","100"}));
        l.add(pickup("stimpack","Stimpack",Sprites.STIMPACK,"give_health","Picked up a stimpack.",new String[]{"amount","10","max","100"}));
        l.add(pickup("clip","Ammo Clip",Sprites.CLIP,"give_ammo","Picked up a clip.",new String[]{"type","Bullets","amount","10"}));
        l.add(pickup("shells","Shotgun Shells",Sprites.SHELLS,"give_ammo","Picked up 4 shotgun shells.",new String[]{"type","Shells","amount","4"}));
        l.add(pickup("rocketbox","Box of Rockets",Sprites.ROCKETBOX,"give_ammo","Picked up a box of rockets.",new String[]{"type","Rockets","amount","5"}));
        l.add(pickup("armor","Green Armor",Sprites.ARMOR,"give_armor","Picked up the armor.",new String[]{"amount","100","max","100"}));
        l.add(pickup("shotgun","Shotgun",Sprites.SHOTGUN_PICKUP,"give_weapon","You got the shotgun!",new String[]{"weapon","Shotgun","ammo","8"}));
        l.add(pickup("chaingun","Chaingun",Sprites.CHAINGUN_PICKUP,"give_weapon","You got the chaingun!",new String[]{"weapon","Chaingun","ammo","20"}));
        l.add(pickup("launcher","Rocket Launcher",Sprites.LAUNCHER_PICKUP,"give_weapon","You got the rocket launcher!",new String[]{"weapon","Rocket Launcher","ammo","2"}));
        l.add(pickup("key_blue","Blue Keycard",Sprites.KEY_BLUE,"give_key","Picked up a blue keycard.",new String[]{"key","Blue"}));
        l.add(pickup("key_yellow","Yellow Keycard",Sprites.KEY_YELLOW,"give_key","Picked up a yellow keycard.",new String[]{"key","Yellow"}));
        l.add(pickup("key_red","Red Keycard",Sprites.KEY_RED,"give_key","Picked up a red keycard.",new String[]{"key","Red"}));
        l.add(barrel());
        l.add(decoration("torch","Torch",Sprites.TORCH,.25f,1.3f));
        l.add(decoration("column","Column",Sprites.COLUMN,.35f,1.6f));
        l.add(exitSwitch());
        return l;
    }

    static EntityDef player(){
        EntityDef d=new EntityDef("player","Player",EntityDef.PLAYER,Sprites.PLAYER);
        d.health=100;d.radius=.4f;d.height=1.4f;d.speed=6f;d.solid=true;d.shootable=true;
        FSMGraph g=d.fsm;
        FSMGraph.Node spawn=g.add("Spawn",0,0),play=g.add("Playing",260,0),shoot=g.add("Shooting",580,-60),use=g.add("Using",580,150),sw=g.add("Switch Weapon",580,290),jump=g.add("Jumping",260,330),dead=g.add("Dead",0,330),wait=g.add("Wait For Fire",0,500),restart=g.add("Restart",280,500);
        spawn.add("select_weapon").set("weapon","Pistol");
        spawn.on("FINISHED",play);
        controls(play);
        play.add("button_held").set("button","Fire").set("event","FIRE");
        play.add("button_pressed").set("button","Use").set("event","USE");
        play.add("button_pressed").set("button","Weapon").set("event","NEXT_WEAPON");
        play.add("button_pressed").set("button","Jump").set("event","JUMP");
        play.on("FIRE",shoot).on("USE",use).on("NEXT_WEAPON",sw).on("JUMP",jump);
        controls(shoot);
        shoot.add("fire_weapon");
        shoot.on("READY",play).on("NO_AMMO",sw);
        use.add("use_now");use.on("FINISHED",play);
        sw.add("next_weapon");sw.on("FINISHED",play);
        jump.add("jump");jump.on("FINISHED",play);
        dead.add("center_print").set("text","You died").set("seconds","999");
        dead.add("wait").set("seconds","1");
        dead.on("FINISHED",wait);
        wait.add("button_pressed").set("button","Fire").set("event","RESTART");
        wait.on("RESTART",restart);
        restart.add("restart_level");
        g.globals.put("DIED",dead.id);
        g.start=spawn.id;
        return d;
    }
    private static void controls(FSMGraph.Node n){n.add("move_joystick");n.add("look_touch");}

    static EntityDef monster(String id,String name,int sprite,int health,float speed,boolean projectile){
        EntityDef d=new EntityDef(id,name,EntityDef.MONSTER,sprite);
        d.health=health;d.speed=speed;d.radius=.45f;d.height=1.4f;d.solid=true;d.shootable=true;d.countKill=true;
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),alert=g.add("Alert",260,0),chase=g.add("Chase",520,0),attack=g.add("Attack",520,240),pain=g.add("Pain",260,240),death=g.add("Death",0,240);
        idle.add("look_for_player").set("range","22");
        idle.on("SEE",alert);
        alert.add("play_sound").set("sound","Monster Sight");
        alert.on("FINISHED",chase);
        chase.add("chase_target").set("speed",speed).set("noTargetEvent","LOST");
        chase.add("random_wait").set("min","0.8").set("max","2.4").set("event","ATTACK");
        chase.on("ATTACK",attack).on("LOST",idle);
        attack.add("face_target");
        attack.add("line_of_sight").set("to","Target").set("hiddenEvent","HIDDEN");
        if(projectile)attack.add("fire_projectile").set("type","Fireball");
        else attack.add("hitscan_attack").set("damageMin","3").set("damageMax","15").set("spread","6");
        attack.add("wait").set("seconds","0.7");
        attack.on("FINISHED",chase).on("HIDDEN",chase);
        pain.add("play_sound").set("sound","Monster Pain");
        pain.add("wait").set("seconds","0.3");
        pain.on("FINISHED",chase);
        death.add("play_sound").set("sound","Monster Death");
        if(!projectile)death.add("drop_item").set("entity","clip");
        g.globals.put("DAMAGED",pain.id);g.globals.put("DIED",death.id);
        g.start=idle.id;
        return d;
    }
    static EntityDef demon(){
        EntityDef d=new EntityDef("demon","Demon",EntityDef.MONSTER,Sprites.DEMON);
        d.health=150;d.speed=3.4f;d.radius=.55f;d.height=1.4f;d.solid=true;d.shootable=true;d.countKill=true;
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),chase=g.add("Chase",280,0),bite=g.add("Bite",560,0),pain=g.add("Pain",280,220),death=g.add("Death",0,220);
        idle.add("look_for_player").set("range","20");idle.on("SEE",chase);
        chase.add("chase_target").set("speed","3.4").set("noTargetEvent","LOST");
        chase.add("distance_check").set("distance","1.5").set("closerEvent","BITE");
        chase.on("BITE",bite).on("LOST",idle);
        bite.add("face_target");bite.add("melee_attack").set("damageMin","4").set("damageMax","40");bite.add("wait").set("seconds","0.6");
        bite.on("FINISHED",chase);
        pain.add("play_sound").set("sound","Monster Pain");pain.add("wait").set("seconds","0.35");pain.on("FINISHED",chase);
        death.add("play_sound").set("sound","Monster Death");
        g.globals.put("DAMAGED",pain.id);g.globals.put("DIED",death.id);g.start=idle.id;
        return d;
    }
    static EntityDef pickup(String id,String name,int sprite,String giveAction,String message,String[] params){
        EntityDef d=new EntityDef(id,name,EntityDef.PICKUP,sprite);
        d.solid=false;d.shootable=false;d.radius=.35f;d.height=.6f;d.countItem=true;d.health=1;
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),take=g.add("Pick Up",280,0);
        idle.add("float_bob").set("height","0.05").set("speed","1");
        idle.on("TOUCHED",take);
        FSMGraph.Action give=take.add(giveAction);for(int i=0;i+1<params.length;i+=2)give.set(params[i],params[i+1]);give.set("failEvent","FULL");
        take.add("play_sound").set("sound",giveAction.equals("give_weapon")?"Weapon Pickup":"Item");
        take.add("screen_flash").set("color","Gold").set("strength","0.35");
        take.add("show_message").set("text",message).set("seconds","3");
        take.add("destroy_self");
        take.on("FULL",idle);
        g.start=idle.id;
        return d;
    }
    static EntityDef barrel(){
        EntityDef d=new EntityDef("barrel","Explosive Barrel",EntityDef.DECORATION,Sprites.BARREL);
        d.health=20;d.radius=.4f;d.height=1f;d.solid=true;d.shootable=true;
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),boom=g.add("Explode",280,0);
        boom.add("spawn_effect").set("effect","Explosion");
        boom.add("play_sound").set("sound","Explosion");
        boom.add("radius_damage").set("damage","80").set("radius","3");
        boom.add("destroy_self");
        g.globals.put("DIED",boom.id);g.start=idle.id;
        idle.name="Idle";
        return d;
    }
    static EntityDef decoration(String id,String name,int sprite,float radius,float height){
        EntityDef d=new EntityDef(id,name,EntityDef.DECORATION,sprite);d.radius=radius;d.height=height;d.solid=true;d.shootable=false;
        d.fsm.add("Idle",0,0);return d;
    }
    static EntityDef exitSwitch(){
        EntityDef d=new EntityDef("exit_switch","Exit Switch",EntityDef.TRIGGER,Sprites.SWITCH);
        d.radius=.35f;d.height=1.2f;d.solid=true;
        FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Idle",0,0),press=g.add("Pressed",280,0),exit=g.add("Exit",560,0);
        idle.on("USED",press);
        press.add("play_sound").set("sound","Switch");press.add("set_sprite").set("sprite","Switch On");press.add("wait").set("seconds","0.6");
        press.on("FINISHED",exit);exit.add("exit_level");
        g.start=idle.id;return d;
    }
    /** Templates offered when creating a new entity type. */
    static final String[] TEMPLATES={"Monster (hitscan)","Monster (fireball)","Monster (melee)","Pickup","Decoration","Switch / trigger","Empty"};
    static EntityDef fromTemplate(int t,String id,String name){
        EntityDef d;
        switch(t){
            case 0:d=monster(id,name,Sprites.TROOPER,20,2.4f,false);break;
            case 1:d=monster(id,name,Sprites.IMP,60,2.2f,true);break;
            case 2:{d=demon();d.id=id;d.name=name;break;}
            case 3:d=pickup(id,name,Sprites.MEDKIT,"give_health","Picked up "+name+".",new String[]{"amount","10","max","100"});break;
            case 4:d=decoration(id,name,Sprites.COLUMN,.35f,1.5f);break;
            case 5:{d=exitSwitch();d.id=id;d.name=name;FSMGraph.Node p=d.fsm.byName("Pressed");p.actions.clear();p.add("play_sound").set("sound","Switch");p.add("show_message").set("text",name+" activated");p.add("wait").set("seconds","1");p.transitions.clear();p.on("FINISHED",d.fsm.byName("Idle"));d.fsm.remove(d.fsm.byName("Exit"));break;}
            default:d=new EntityDef(id,name,EntityDef.DECORATION,Sprites.COLUMN);d.fsm.add("Idle",0,0);
        }
        return d;
    }
}
