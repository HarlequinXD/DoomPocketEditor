package com.retro.doomeditor;

import java.util.ArrayList;

/**
 * Starter entity types with ready-made FSMs for a fantasy RPG: the hero, monsters with tabletop-style stats
 * (armour class, hit dice, attack bonus, damage dice, XP), a quest-giving villager, a merchant, loot and dungeon decor.
 */
final class DefaultContent {
    private DefaultContent(){}

    static ArrayList<EntityDef> all(){
        ArrayList<EntityDef> l=new ArrayList<>();
        l.add(player());
        // creatures                                       AC  hit dice  hit  damage    type             XP
        l.add(melee("skeleton","Skeleton",Sprites.SKELETON,2.3f).stats(13,"2d8+4",4,"1d6+2",Rpg.PIERCING,50).scores(10,14,15,6,8,5));
        l.add(ranged("skeleton_archer","Skeleton Archer",Sprites.SKELETON,2.0f,"Arrow").stats(13,"2d8+4",4,"1d6+2",Rpg.PIERCING,50).scores(10,14,15,6,8,5));
        l.add(melee("goblin","Goblin",Sprites.GOBLIN,3.2f).stats(15,"2d6",4,"1d6+2",Rpg.SLASHING,50).scores(8,14,10,10,8,8));
        l.add(melee("orc","Orc",Sprites.ORC,2.6f).stats(13,"2d8+6",5,"1d12+3",Rpg.SLASHING,100).scores(16,12,16,7,11,10));
        l.add(caster("cultist","Cultist",Sprites.CULTIST,2.0f,"Fire Bolt").stats(12,"4d8",4,"1d10",Rpg.FIRE,100).scores(11,12,10,13,11,10));
        // people
        l.add(questGiver());
        l.add(merchant());
        // loot
        l.add(item("healing_potion","Potion of Healing",Sprites.MEDKIT,"Potion of Healing"));
        l.add(item("healing_draught","Healing Draught",Sprites.STIMPACK,"Healing Draught"));
        l.add(item("arcana_potion","Potion of Arcana",Sprites.SHELLS,"Potion of Arcana"));
        l.add(gold());
        l.add(item("longsword","Longsword",Sprites.SWORD,"Longsword"));
        l.add(item("longbow","Longbow",Sprites.BOW,"Longbow"));
        l.add(item("dagger","Dagger",Sprites.DAGGER,"Dagger"));
        l.add(item("quarterstaff","Quarterstaff",Sprites.STAFF,"Quarterstaff"));
        l.add(item("chain_mail","Chain Mail",Sprites.ARMOUR,"Chain mail"));
        l.add(item("shield","Shield",Sprites.SHIELD_PICKUP,"Shield"));
        l.add(item("scroll_fireball","Scroll of Fireball",Sprites.SCROLL,"Scroll of Fireball"));
        l.add(item("golden_idol","Golden Idol",Sprites.TREASURE,"Golden idol"));
        l.add(key("bronze_key","Bronze Key",Sprites.KEY_BRONZE,"Bronze"));
        l.add(key("silver_key","Silver Key",Sprites.KEY_SILVER,"Silver"));
        l.add(key("gold_key","Gold Key",Sprites.KEY_GOLD,"Gold"));
        l.add(chest());
        // dungeon
        l.add(barrel());
        l.add(decoration("torch","Torch",Sprites.TORCH,.25f,1.3f));
        l.add(decoration("pillar","Stone Pillar",Sprites.PILLAR,.35f,1.6f));
        l.add(decoration("brazier","Brazier",Sprites.BRAZIER,.3f,1.2f));
        l.add(exitLever());
        return l;
    }

    // ------------------------------------------------------------------ hero
    static EntityDef player(){return player(false);}
    /** The hero; {@code third} builds the third-person controller (orbit camera, camera-relative movement). */
    static EntityDef player(boolean third){
        EntityDef d=new EntityDef("player",third?"Hero (Third Person)":"Hero",EntityDef.PLAYER,Sprites.HERO);
        d.health=12;d.radius=.4f;d.height=1.4f;d.speed=6f;d.solid=true;d.shootable=true;d.heroClass="Fighter";d.level=1;d.startGold=15;
        FSMGraph g=d.fsm;
        FSMGraph.Node spawn=g.add("Spawn",0,0),play=g.add("Exploring",260,0),atk=g.add("Attacking",580,-140),cast=g.add("Casting",580,10),use=g.add("Using",580,160),
            drink=g.add("Drinking",580,300),bag=g.add("Open Bag",260,330),jump=g.add("Jumping",0,180),dead=g.add("Dead",0,420),wait=g.add("Wait For Attack",0,580),restart=g.add("Restart",280,580);
        if(third)spawn.add("third_person_camera").set("style","Orbit").set("distance","3.8").set("height","0.8").set("shoulder","0.35");
        spawn.add("show_message").set("text","Tap ATTACK to fight, CAST for spells, BAG for your gear").set("seconds","4");
        spawn.on("FINISHED",play);
        controls(play,third);
        play.add("button_held").set("button","Fire").set("event","ATTACK");
        play.add("button_pressed").set("button","Spell").set("event","CAST");
        play.add("button_pressed").set("button","Use").set("event","USE");
        play.add("button_pressed").set("button","Potion").set("event","DRINK");
        play.add("button_pressed").set("button","Inventory").set("event","BAG");
        play.add("button_pressed").set("button","Jump").set("event","JUMP");
        play.on("ATTACK",atk).on("CAST",cast).on("USE",use).on("DRINK",drink).on("BAG",bag).on("JUMP",jump);
        controls(atk,third);atk.add("attack");atk.on("READY",play);
        controls(cast,third);cast.add("cast_spell").set("spell","Selected");cast.on("READY",play);
        use.add("use_now");use.on("FINISHED",play);
        drink.add("drink_potion");drink.on("FINISHED",play);
        bag.add("open_character");bag.on("FINISHED",play);
        jump.add("jump");jump.on("FINISHED",play);
        dead.add("center_print").set("text","You have fallen").set("seconds","999");
        dead.add("wait").set("seconds","1.2");
        dead.on("FINISHED",wait);
        wait.add("button_pressed").set("button","Fire").set("event","RESTART");
        wait.on("RESTART",restart);
        restart.add("restart_level");
        g.globals.put("DIED",dead.id);
        g.start=spawn.id;
        return d;
    }
    private static void controls(FSMGraph.Node n,boolean third){
        if(third){n.add("move_camera_relative");n.add("orbit_camera_look");}else{n.add("move_joystick");n.add("look_touch");}}

    // ------------------------------------------------------------------ monsters
    private static EntityDef creature(String id,String name,int sprite,float speed){
        EntityDef d=new EntityDef(id,name,EntityDef.MONSTER,sprite);
        d.health=10;d.speed=speed;d.radius=.4f;d.height=1.4f;d.solid=true;d.shootable=true;d.countKill=true;
        return d;
    }
    /** Shared idle → alert → chase skeleton, plus pain and a death that may drop gold. */
    private static FSMGraph.Node[] brain(EntityDef d,float speed,float engage,boolean rangedFirst){
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),alert=g.add("Alert",260,0),chase=g.add("Chase",520,0),attack=g.add("Attack",520,240),pain=g.add("Hurt",260,240),death=g.add("Death",0,240),loot=g.add("Drop Loot",0,420);
        idle.add("look_for_player").set("range","18");
        idle.on("SEE",alert);
        alert.add("play_sound").set("sound","Monster Sight");
        alert.on("FINISHED",chase);
        chase.add("chase_target").set("speed",speed).set("noTargetEvent","LOST");
        if(rangedFirst)chase.add("random_wait").set("min","1.0").set("max","2.4").set("event","ATTACK");
        else chase.add("distance_check").set("distance",String.valueOf(engage)).set("closerEvent","ATTACK");
        chase.on("ATTACK",attack).on("LOST",idle);
        pain.add("play_sound").set("sound","Monster Pain");
        pain.add("wait").set("seconds","0.25");
        pain.on("FINISHED",chase);
        death.add("play_sound").set("sound","Monster Death");
        death.add("roll_dice").set("dice","1d6").set("target","4").set("successEvent","LOOT");
        death.on("LOOT",loot);
        loot.add("drop_item").set("entity","gold");
        g.globals.put("DAMAGED",pain.id);g.globals.put("DIED",death.id);
        g.start=idle.id;
        return new FSMGraph.Node[]{idle,chase,attack};
    }
    static EntityDef melee(String id,String name,int sprite,float speed){
        EntityDef d=creature(id,name,sprite,speed);FSMGraph.Node[] n=brain(d,speed,1.4f,false);FSMGraph.Node attack=n[2];
        attack.add("face_target");
        attack.add("creature_melee").set("reach","1.3").set("outOfReachEvent","AWAY");
        attack.add("wait").set("seconds","1.1");
        attack.on("FINISHED",n[1]).on("AWAY",n[1]);
        return d;
    }
    static EntityDef ranged(String id,String name,int sprite,float speed,String projectile){
        EntityDef d=creature(id,name,sprite,speed);FSMGraph.Node[] n=brain(d,speed,0,true);FSMGraph.Node attack=n[2];
        attack.add("face_target");
        attack.add("line_of_sight").set("to","Target").set("hiddenEvent","HIDDEN");
        attack.add("creature_ranged").set("projectile",projectile);
        attack.add("wait").set("seconds","0.9");
        attack.on("FINISHED",n[1]).on("HIDDEN",n[1]);
        return d;
    }
    static EntityDef caster(String id,String name,int sprite,float speed,String spell){
        EntityDef d=creature(id,name,sprite,speed);FSMGraph.Node[] n=brain(d,speed,0,true);FSMGraph.Node attack=n[2];
        attack.add("face_target");
        attack.add("line_of_sight").set("to","Target").set("hiddenEvent","HIDDEN");
        attack.add("creature_cast").set("spell",spell);
        attack.add("wait").set("seconds","1.2");
        attack.on("FINISHED",n[1]).on("HIDDEN",n[1]);
        return d;
    }

    // ------------------------------------------------------------------ people
    /** A villager with a fetch quest: bring back the golden idol. */
    static EntityDef questGiver(){
        EntityDef d=new EntityDef("elder","Village Elder",EntityDef.NPC,Sprites.VILLAGER);
        d.health=10;d.radius=.4f;d.height=1.4f;d.speed=1f;d.solid=true;d.shootable=false;
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),check=g.add("Check Quest",260,0),offer=g.add("Offer Quest",520,-120),accept=g.add("Accepted",780,-120),
            ask=g.add("Ask For Idol",520,60),reward=g.add("Reward",780,60),notYet=g.add("Not Yet",780,200),thanks=g.add("Thanks Again",520,240);
        idle.on("USED",check);
        check.add("face_target").set("to","Player").set("everyFrame","false");
        check.add("quest_state").set("id","idol").set("notStartedEvent","OFFER").set("activeEvent","ASK").set("doneEvent","DONE");
        check.on("OFFER",offer).on("ASK",ask).on("DONE",thanks);
        offer.add("say").set("text","Thank the gods, an adventurer! Cultists stole our golden idol and fled into the old crypt. Will you bring it back?")
            .set("answer1","I'll find your idol.").set("event1","YES").set("answer2","Not now.").set("event2","NO");
        offer.on("YES",accept).on("NO",idle);
        accept.add("start_quest").set("id","idol").set("title","The Stolen Idol").set("objective","Recover the golden idol from the crypt and return it to the elder.");
        accept.add("say").set("text","Bless you. Beware the dead that walk down there.");
        accept.on("FINISHED",idle);
        ask.add("has_item").set("item","Golden idol").set("yesEvent","HAVE").set("noEvent","NOTYET");
        ask.on("HAVE",reward).on("NOTYET",notYet);
        notYet.add("say").set("text","Have you found the idol? It lies somewhere in the crypt.");
        notYet.on("FINISHED",idle);
        reward.add("take_item").set("item","Golden idol");
        reward.add("complete_quest").set("id","idol").set("xp","300").set("gold","50");
        reward.add("say").set("text","Our idol! You have the village's eternal thanks, hero. Take this for your trouble.");
        reward.on("FINISHED",idle);
        thanks.add("say").set("text","Safe travels, friend. The village remembers your deed.");
        thanks.on("FINISHED",idle);
        g.start=idle.id;
        return d;
    }
    /** A merchant who sells potions and gear for gold. */
    static EntityDef merchant(){
        EntityDef d=new EntityDef("merchant","Merchant",EntityDef.NPC,Sprites.VILLAGER);
        d.health=10;d.radius=.4f;d.height=1.4f;d.speed=1f;d.solid=true;d.shootable=false;
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),shop=g.add("Shop",280,0);
        idle.on("USED",shop);
        shop.add("open_shop").set("shop","general").set("closedEvent","CLOSED");shop.on("CLOSED",idle);
        g.start=idle.id;
        return d;
    }

    // ------------------------------------------------------------------ loot
    private static EntityDef pickupBase(String id,String name,int sprite){
        EntityDef d=new EntityDef(id,name,EntityDef.PICKUP,sprite);
        d.solid=false;d.shootable=false;d.radius=.35f;d.height=.6f;d.countItem=true;d.health=1;
        return d;
    }
    /** Pick-up that puts an item in the hero's pack. */
    static EntityDef item(String id,String name,int sprite,String itemName){
        EntityDef d=pickupBase(id,name,sprite);
        FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Idle",0,0),take=g.add("Pick Up",280,0);
        idle.add("float_bob").set("height","0.04").set("speed","0.8");
        idle.on("TOUCHED",take);
        take.add("give_item").set("item",itemName).set("count","1");
        take.add("play_sound").set("sound","Item");
        take.add("screen_flash").set("color","Gold").set("strength","0.3");
        take.add("show_message").set("text","You found: "+name).set("seconds","2.5");
        take.add("destroy_self");
        g.start=idle.id;
        return d;
    }
    static EntityDef key(String id,String name,int sprite,String key){
        EntityDef d=pickupBase(id,name,sprite);
        FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Idle",0,0),take=g.add("Pick Up",280,0);
        idle.add("float_bob").set("height","0.06").set("speed","1");idle.on("TOUCHED",take);
        take.add("give_key").set("key",key);take.add("play_sound").set("sound","Item");
        take.add("show_message").set("text","You picked up the "+name.toLowerCase()+".").set("seconds","2.5");take.add("destroy_self");
        g.start=idle.id;return d;
    }
    static EntityDef gold(){
        EntityDef d=pickupBase("gold","Gold Coins",Sprites.GOLD);d.countItem=false;d.height=.3f;
        FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Idle",0,0),take=g.add("Pick Up",280,0);
        idle.on("TOUCHED",take);take.add("give_gold").set("amount","2d10");take.add("destroy_self");
        g.start=idle.id;return d;
    }
    /** Treasure chest: open it (Use) for gold and a potion. */
    static EntityDef chest(){
        EntityDef d=new EntityDef("chest","Treasure Chest",EntityDef.TRIGGER,Sprites.CHEST);
        d.radius=.45f;d.height=.7f;d.solid=true;d.shootable=false;d.countItem=true;
        FSMGraph g=d.fsm;FSMGraph.Node closed=g.add("Closed",0,0),open=g.add("Open",280,0),empty=g.add("Empty",560,0);
        closed.on("USED",open);
        open.add("set_sprite").set("sprite","Chest (open)");open.add("play_sound").set("sound","Door");
        open.add("give_gold").set("amount","4d10");open.add("give_item").set("item","Potion of Healing");
        open.add("show_message").set("text","The chest holds gold and a potion of healing.").set("seconds","3");
        open.on("FINISHED",empty);
        g.start=closed.id;return d;
    }

    // ------------------------------------------------------------------ dungeon
    static EntityDef barrel(){
        EntityDef d=new EntityDef("barrel","Barrel",EntityDef.DECORATION,Sprites.BARREL);
        d.health=8;d.radius=.4f;d.height=1f;d.solid=true;d.shootable=true;d.ac=10;
        FSMGraph g=d.fsm;
        FSMGraph.Node idle=g.add("Idle",0,0),smash=g.add("Smashed",280,0),loot=g.add("Loot",560,0);
        smash.add("spawn_effect").set("effect","Puff");smash.add("play_sound").set("sound","Punch");
        smash.add("roll_dice").set("dice","1d6").set("target","5").set("successEvent","LOOT");
        smash.add("destroy_self");
        smash.on("LOOT",loot);loot.add("drop_item").set("entity","healing_draught");loot.add("destroy_self");
        g.globals.put("DIED",smash.id);g.start=idle.id;
        return d;
    }
    static EntityDef decoration(String id,String name,int sprite,float radius,float height){
        EntityDef d=new EntityDef(id,name,EntityDef.DECORATION,sprite);d.radius=radius;d.height=height;d.solid=true;d.shootable=false;
        d.fsm.add("Idle",0,0);return d;
    }
    static EntityDef exitLever(){
        EntityDef d=new EntityDef("exit_lever","Exit Lever",EntityDef.TRIGGER,Sprites.LEVER);
        d.radius=.35f;d.height=1.2f;d.solid=true;
        FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Idle",0,0),press=g.add("Pulled",280,0),exit=g.add("Exit",560,0);
        idle.on("USED",press);
        press.add("play_sound").set("sound","Switch");press.add("set_sprite").set("sprite","Lever (pulled)");press.add("wait").set("seconds","0.6");
        press.on("FINISHED",exit);exit.add("exit_level");
        g.start=idle.id;return d;
    }

    /** Templates offered when creating a new entity type. */
    static final String[] TEMPLATES={"Monster (melee)","Monster (archer)","Monster (spellcaster)","NPC (talks)","Merchant","Item pick-up","Treasure chest","Decoration","Lever / trigger","NPC (conversation tree)","Map exit (go to another map)","Arrival point (where map exits arrive)","Empty"};
    static EntityDef fromTemplate(int t,String id,String name){
        EntityDef d;
        switch(t){
            case 0:d=melee(id,name,Sprites.SKELETON,2.3f).stats(13,"2d8+4",4,"1d6+2",Rpg.SLASHING,50);break;
            case 1:d=ranged(id,name,Sprites.SKELETON,2f,"Arrow").stats(13,"2d8+4",4,"1d6+2",Rpg.PIERCING,50);break;
            case 2:d=caster(id,name,Sprites.CULTIST,2f,"Fire Bolt").stats(12,"4d8",4,"1d10",Rpg.FIRE,100);break;
            case 3:{d=new EntityDef(id,name,EntityDef.NPC,Sprites.VILLAGER);d.solid=true;d.radius=.4f;d.height=1.4f;FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Idle",0,0),talk=g.add("Talk",280,0);
                idle.on("USED",talk);talk.add("say").set("text","Hello there, traveller!");talk.on("FINISHED",idle);g.start=idle.id;break;}
            case 4:{d=merchant();d.id=id;d.name=name;break;}
            case 5:d=item(id,name,Sprites.MEDKIT,"Potion of Healing");break;
            case 6:{d=chest();d.id=id;d.name=name;break;}
            case 7:d=decoration(id,name,Sprites.PILLAR,.35f,1.5f);break;
            case 8:{d=exitLever();d.id=id;d.name=name;FSMGraph.Node p=d.fsm.byName("Pulled");p.actions.clear();p.add("play_sound").set("sound","Switch");p.add("set_sprite").set("sprite","Lever (pulled)");p.add("show_message").set("text",name+" pulled");p.transitions.clear();break;}
            case 9:{d=new EntityDef(id,name,EntityDef.NPC,Sprites.VILLAGER);d.solid=true;d.radius=.4f;d.height=1.4f;FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Idle",0,0),talk=g.add("Talking",280,0);
                idle.on("USED",talk);talk.add("talk").set("endEvent","DONE");talk.on("DONE",idle);g.start=idle.id;break;}
            case 10:{d=new EntityDef(id,name,EntityDef.TRIGGER,Sprites.BRAZIER);d.solid=false;d.shootable=false;d.radius=.6f;d.height=1f;
                FSMGraph g=d.fsm;FSMGraph.Node idle=g.add("Waiting",0,0),go=g.add("Travel",280,0);
                idle.on("TOUCHED",go);idle.on("USED",go);go.add("play_sound").set("sound","Teleport");go.add("go_to_map");g.start=idle.id;break;}
            case 11:{d=new EntityDef(id,name,EntityDef.TRIGGER,Sprites.PILLAR);d.solid=false;d.shootable=false;d.radius=.3f;d.height=.5f;
                FSMGraph.Node n=d.fsm.add("Hidden",0,0);n.add("set_visible").set("value","false");d.fsm.start=n.id;break;}
            default:d=new EntityDef(id,name,EntityDef.DECORATION,Sprites.PILLAR);d.fsm.add("Idle",0,0);
        }
        return d;
    }
}
