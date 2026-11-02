package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.Random;

/**
 * Tabletop-style RPG rules for the game framework: dice, the six ability scores, classes, levels and
 * experience, armour class, d20 attack rolls and saving throws, plus the catalogues of items and spells.
 * Combat is real time, but every hit, miss, critical and save is decided by the dice.
 */
final class Rpg {
    private Rpg(){}

    // ------------------------------------------------------------------ abilities & levels
    static final String[] ABILITIES={"STR","DEX","CON","INT","WIS","CHA"};
    static final String[] ABILITY_NAMES={"Strength","Dexterity","Constitution","Intelligence","Wisdom","Charisma"};
    static final int STR=0,DEX=1,CON=2,INT=3,WIS=4,CHA=5;
    static int mod(int score){return Math.floorDiv(score-10,2);}
    static String signed(int v){return v>=0?"+"+v:String.valueOf(v);}
    static int proficiency(int level){return 2+(Math.max(1,level)-1)/4;}
    /** Experience needed to reach each level (index = level - 1). */
    static final int[] XP_TABLE={0,300,900,2700,6500,14000,23000,34000,48000,64000,85000,100000,120000,140000,165000,195000,225000,265000,305000,355000};
    static final int MAX_LEVEL=XP_TABLE.length;
    static int levelForXp(int xp){int l=1;while(l<MAX_LEVEL&&xp>=XP_TABLE[l])l++;return l;}
    static int xpForNext(int level){return level>=MAX_LEVEL?XP_TABLE[MAX_LEVEL-1]:XP_TABLE[level];}
    /** Spell points by caster level (a points-based alternative to spell slots). */
    static final int[] SPELL_POINTS={4,6,14,17,27,32,38,44,57,64,73,73,83,83,94,94,107,114,123,133};
    static int spellCost(int spellLevel){return spellLevel<=0?0:spellLevel==1?2:spellLevel==2?3:spellLevel==3?5:6+spellLevel;}

    // ------------------------------------------------------------------ dice
    /** Dice expression such as "2d6+3", "1d20", "d8" or a flat "5". */
    static final class Dice {
        final int n,sides,bonus;
        Dice(int n,int sides,int bonus){this.n=n;this.sides=sides;this.bonus=bonus;}
        static Dice parse(String s){
            if(s==null)return new Dice(0,0,0);s=s.trim().toLowerCase(java.util.Locale.US).replace(" ","");
            try{int d=s.indexOf('d');if(d<0)return new Dice(0,0,Integer.parseInt(s.isEmpty()?"0":s));
                int n=d==0?1:Integer.parseInt(s.substring(0,d));String rest=s.substring(d+1);int p=Math.max(rest.indexOf('+'),rest.indexOf('-'));
                int sides=Integer.parseInt(p<0?rest:rest.substring(0,p)),b=p<0?0:Integer.parseInt(rest.substring(p).replace("+",""));
                return new Dice(Math.max(0,Math.min(100,n)),Math.max(1,Math.min(1000,sides)),b);}
            catch(RuntimeException e){return new Dice(1,4,0);}
        }
        int roll(Random r){int t=bonus;for(int i=0;i<n;i++)t+=1+r.nextInt(sides);return t;}
        /** Critical hit: roll the dice twice, add the bonus once. */
        int rollCrit(Random r){int t=bonus;for(int i=0;i<n*2;i++)t+=1+r.nextInt(sides);return t;}
        float average(){return n*(sides+1)/2f+bonus;}
        int max(){return n*sides+bonus;}
        @Override public String toString(){if(n==0)return String.valueOf(bonus);return n+"d"+sides+(bonus>0?"+"+bonus:bonus<0?String.valueOf(bonus):"");}
    }
    static int d20(Random r){return 1+r.nextInt(20);}

    // ------------------------------------------------------------------ damage types
    static final String[] DAMAGE_TYPES={"Slashing","Piercing","Bludgeoning","Fire","Cold","Lightning","Necrotic","Radiant","Poison","Force"};
    static final int SLASHING=0,PIERCING=1,BLUDGEONING=2,FIRE=3,COLD=4,LIGHTNING=5,NECROTIC=6,RADIANT=7,POISON=8,FORCE=9;
    static final String DAMAGE_JOINED=Sprites.join(DAMAGE_TYPES);

    // ------------------------------------------------------------------ items
    static final int I_WEAPON=0,I_ARMOR=1,I_SHIELD=2,I_POTION=3,I_SCROLL=4,I_KEY=5,I_MISC=6;
    static final String[] ITEM_KINDS={"Weapon","Armour","Shield","Potion","Scroll","Key","Treasure"};
    /** First-person weapon models. */
    static final int FP_FIST=0,FP_SWORD=1,FP_MACE=2,FP_BOW=3,FP_STAFF=4,FP_DAGGER=5,FP_AXE=6;
    static final class Item {
        final String id,name;final int kind,sprite,value;
        // weapons
        Dice damage=new Dice(1,4,0);int damageType=BLUDGEONING,fp=FP_FIST;boolean finesse,ranged,twoHanded;float reach=1.5f,cooldown=.7f;
        // armour / shield
        int baseAC;int maxDex=99;boolean heavy;int strReq;
        // consumables
        Dice heal;int spellPoints;String spell;int key=-1;
        String desc="";
        Item(String id,String name,int kind,int sprite,int value){this.id=id;this.name=name;this.kind=kind;this.sprite=sprite;this.value=value;}
        Item weapon(String dice,int type,int fp,float cooldown){damage=Dice.parse(dice);damageType=type;this.fp=fp;this.cooldown=cooldown;return this;}
        Item finesse(){finesse=true;return this;}
        Item ranged(float range){ranged=true;reach=range;return this;}
        Item twoH(){twoHanded=true;return this;}
        Item armour(int ac,int maxDex,boolean heavy){baseAC=ac;this.maxDex=maxDex;this.heavy=heavy;return this;}
        Item desc(String d){desc=d;return this;}
        String stats(){
            switch(kind){
                case I_WEAPON:return damage+" "+DAMAGE_TYPES[damageType].toLowerCase()+(finesse?" · finesse":"")+(ranged?" · ranged":"")+(twoHanded?" · two-handed":"");
                case I_ARMOR:return "AC "+baseAC+(maxDex>=99?" + DEX":maxDex>0?" + DEX (max "+maxDex+")":"")+(heavy?" · heavy":"");
                case I_SHIELD:return "+"+baseAC+" AC";
                case I_POTION:return heal!=null?"Heals "+heal:"Restores "+spellPoints+" spell points";
                case I_SCROLL:{Spell sp=spell(spell);return sp==null?"Scroll":"Casts "+sp.name+" once";}
                case I_KEY:return "Opens matching doors";
                default:return value+" gold";
            }
        }
    }
    static final ArrayList<Item> ITEMS=new ArrayList<>();
    private static Item add(Item i){ITEMS.add(i);return i;}
    static{
        add(new Item("fists","Bare hands",I_WEAPON,-1,0).weapon("1d2",BLUDGEONING,FP_FIST,.5f).desc("Better than nothing."));
        add(new Item("dagger","Dagger",I_WEAPON,Sprites.CLIP,2).weapon("1d4",PIERCING,FP_DAGGER,.4f).finesse().desc("Quick and light. Uses DEX if better."));
        add(new Item("shortsword","Shortsword",I_WEAPON,Sprites.SHOTGUN_PICKUP,10).weapon("1d6",PIERCING,FP_SWORD,.55f).finesse());
        add(new Item("longsword","Longsword",I_WEAPON,Sprites.SHOTGUN_PICKUP,15).weapon("1d8",SLASHING,FP_SWORD,.7f).desc("A knight's blade."));
        add(new Item("mace","Mace",I_WEAPON,Sprites.SHOTGUN_PICKUP,5).weapon("1d6",BLUDGEONING,FP_MACE,.7f));
        add(new Item("battleaxe","Battleaxe",I_WEAPON,Sprites.SHOTGUN_PICKUP,10).weapon("1d10",SLASHING,FP_AXE,.95f).twoH());
        add(new Item("greatsword","Greatsword",I_WEAPON,Sprites.SHOTGUN_PICKUP,50).weapon("2d6",SLASHING,FP_SWORD,1.05f).twoH());
        add(new Item("quarterstaff","Quarterstaff",I_WEAPON,Sprites.LAUNCHER_PICKUP,1).weapon("1d6",BLUDGEONING,FP_STAFF,.75f).desc("Also a focus for spellcasting."));
        add(new Item("shortbow","Shortbow",I_WEAPON,Sprites.CHAINGUN_PICKUP,25).weapon("1d6",PIERCING,FP_BOW,.8f).ranged(24).twoH());
        add(new Item("longbow","Longbow",I_WEAPON,Sprites.CHAINGUN_PICKUP,50).weapon("1d8",PIERCING,FP_BOW,1f).ranged(36).twoH());
        add(new Item("leather","Leather armour",I_ARMOR,Sprites.ARMOR,10).armour(11,99,false));
        add(new Item("studded","Studded leather",I_ARMOR,Sprites.ARMOR,45).armour(12,99,false));
        add(new Item("chainshirt","Chain shirt",I_ARMOR,Sprites.ARMOR,50).armour(13,2,false));
        add(new Item("scalemail","Scale mail",I_ARMOR,Sprites.ARMOR,50).armour(14,2,false));
        add(new Item("chainmail","Chain mail",I_ARMOR,Sprites.ARMOR,75).armour(16,0,true));
        add(new Item("plate","Plate armour",I_ARMOR,Sprites.ARMOR,1500).armour(18,0,true));
        add(new Item("shield","Shield",I_SHIELD,Sprites.SHIELD_PICKUP,10).armour(2,99,false));
        Item p;
        p=add(new Item("healing_potion","Potion of Healing",I_POTION,Sprites.MEDKIT,50));p.heal=Dice.parse("2d4+2");
        p=add(new Item("greater_healing","Potion of Greater Healing",I_POTION,Sprites.MEDKIT,150));p.heal=Dice.parse("4d4+4");
        p=add(new Item("small_healing","Healing Draught",I_POTION,Sprites.STIMPACK,20));p.heal=Dice.parse("1d4+1");
        p=add(new Item("mana_potion","Potion of Arcana",I_POTION,Sprites.SHELLS,60));p.spellPoints=6;
        p=add(new Item("scroll_fireball","Scroll of Fireball",I_SCROLL,Sprites.SCROLL,200));p.spell="fireball";
        p=add(new Item("scroll_cure","Scroll of Cure Wounds",I_SCROLL,Sprites.SCROLL,50));p.spell="cure_wounds";
        p=add(new Item("scroll_missile","Scroll of Magic Missile",I_SCROLL,Sprites.SCROLL,50));p.spell="magic_missile";
        p=add(new Item("bronze_key","Bronze key",I_KEY,Sprites.KEY_BLUE,0));p.key=0;
        p=add(new Item("silver_key","Silver key",I_KEY,Sprites.KEY_YELLOW,0));p.key=1;
        p=add(new Item("gold_key","Gold key",I_KEY,Sprites.KEY_RED,0));p.key=2;
        add(new Item("gem","Gemstone",I_MISC,Sprites.ROCKETBOX,100).desc("Worth a fair sum to a merchant."));
        add(new Item("idol","Golden idol",I_MISC,Sprites.ROCKETBOX,250));
    }
    static Item item(String id){if(id==null)return null;for(Item i:ITEMS)if(i.id.equals(id))return i;return null;}
    static String itemNames(int kind){StringBuilder b=new StringBuilder();for(Item i:ITEMS)if(kind<0||i.kind==kind){if(b.length()>0)b.append('|');b.append(i.name);}return b.toString();}
    static Item itemByName(String name){for(Item i:ITEMS)if(i.name.equalsIgnoreCase(name)||i.id.equalsIgnoreCase(name))return i;return null;}
    static final String ITEMS_JOINED=itemNames(-1);
    static final String KEYS="Bronze|Silver|Gold";

    // ------------------------------------------------------------------ spells
    static final int S_BOLT=0,S_MISSILE=1,S_AREA=2,S_HEAL=3,S_WARD=4,S_SAVE_TARGET=5,S_CONE=6;
    static final class Spell {
        final String id,name;final int level,kind;final Dice dice;final int damageType;float range=20,radius;String desc;
        Spell(String id,String name,int level,int kind,String dice,int type,String desc){this.id=id;this.name=name;this.level=level;this.kind=kind;this.dice=Dice.parse(dice);damageType=type;this.desc=desc;}
        Spell radius(float r){radius=r;return this;}
        Spell range(float r){range=r;return this;}
    }
    static final ArrayList<Spell> SPELLS=new ArrayList<>();
    static{
        SPELLS.add(new Spell("fire_bolt","Fire Bolt",0,S_BOLT,"1d10",FIRE,"Hurl a mote of fire: ranged spell attack."));
        SPELLS.add(new Spell("sacred_flame","Sacred Flame",0,S_SAVE_TARGET,"1d8",RADIANT,"Radiant flame on the target you face; DEX save negates.").range(14));
        SPELLS.add(new Spell("magic_missile","Magic Missile",1,S_MISSILE,"1d4+1",FORCE,"Three glowing darts that never miss."));
        SPELLS.add(new Spell("cure_wounds","Cure Wounds",1,S_HEAL,"1d8",RADIANT,"Heal yourself (adds your spellcasting modifier)."));
        SPELLS.add(new Spell("shield","Shield",1,S_WARD,"5",FORCE,"+5 AC for a short while."));
        SPELLS.add(new Spell("burning_hands","Burning Hands",1,S_CONE,"3d6",FIRE,"A sheet of flame in front of you; DEX save for half.").range(3));
        SPELLS.add(new Spell("fireball","Fireball",3,S_AREA,"8d6",FIRE,"An explosion of flame where the bolt lands; DEX save for half.").radius(2.5f));
    }
    static Spell spell(String id){if(id==null)return null;for(Spell s:SPELLS)if(s.id.equals(id)||s.name.equalsIgnoreCase(id))return s;return null;}
    static String spellNames(){StringBuilder b=new StringBuilder();for(Spell s:SPELLS){if(b.length()>0)b.append('|');b.append(s.name);}return b.toString();}
    static final String SPELLS_JOINED=spellNames();

    // ------------------------------------------------------------------ classes
    static final class HeroClass {
        final String name;final int hitDie,primary,castAbility;final int[] scores;final String[] gear,spells;final String desc;final boolean heavyArmour;
        HeroClass(String name,int hitDie,int primary,int cast,int[] scores,String[] gear,String[] spells,boolean heavy,String desc){
            this.name=name;this.hitDie=hitDie;this.primary=primary;castAbility=cast;this.scores=scores;this.gear=gear;this.spells=spells;heavyArmour=heavy;this.desc=desc;}
    }
    /** Ability scores use the standard array (15, 14, 13, 12, 10, 8) arranged for each class. */
    static final HeroClass[] CLASSES={
        new HeroClass("Fighter",10,STR,-1,new int[]{15,13,14,8,12,10},new String[]{"longsword","shield","chainmail","healing_potion"},new String[0],true,"Tough front-line warrior: heavy armour, shield and steel."),
        new HeroClass("Ranger",10,DEX,WIS,new int[]{12,15,13,8,14,10},new String[]{"longbow","shortsword","leather","healing_potion"},new String[]{"cure_wounds"},false,"Deadly archer who can mend their own wounds."),
        new HeroClass("Rogue",8,DEX,-1,new int[]{10,15,13,12,8,14},new String[]{"shortsword","dagger","leather","healing_potion","healing_potion"},new String[0],false,"Quick and sneaky: sneak attacks deal extra damage to unaware foes."),
        new HeroClass("Wizard",6,INT,INT,new int[]{8,14,13,15,12,10},new String[]{"quarterstaff","mana_potion","healing_potion"},new String[]{"fire_bolt","magic_missile","shield","burning_hands","fireball"},false,"Fragile master of arcane fire and force."),
        new HeroClass("Cleric",8,WIS,WIS,new int[]{13,10,14,8,15,12},new String[]{"mace","shield","scalemail","healing_potion"},new String[]{"sacred_flame","cure_wounds","shield"},true,"Armoured healer who smites with radiant light."),
    };
    static final String CLASSES_JOINED;
    static{String[] n=new String[CLASSES.length];for(int i=0;i<n.length;i++)n[i]=CLASSES[i].name;CLASSES_JOINED=Sprites.join(n);}
    static HeroClass heroClass(String name){for(HeroClass c:CLASSES)if(c.name.equalsIgnoreCase(name))return c;return CLASSES[0];}
    /** Level at which each of a class's spells becomes available (spell level 3 needs character level 5). */
    static int levelForSpell(Spell s){return s.level<=1?1:s.level==2?3:s.level==3?5:7;}
}
