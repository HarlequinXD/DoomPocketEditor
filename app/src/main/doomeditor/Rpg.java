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
        String id,name;int kind,sprite,value;
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
    /** The active project's items (the built-in set until a project is opened). */
    static ArrayList<Item> ITEMS=new ArrayList<>();
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
    static final ArrayList<Item> DEFAULT_ITEMS=new ArrayList<>(ITEMS);
    static final String[] FP_NAMES={"Fists","Sword","Mace","Bow","Staff","Dagger","Axe"};
    static Item item(String id){if(id==null)return null;for(Item i:ITEMS)if(i.id.equals(id))return i;return null;}
    static String itemNames(int kind){StringBuilder b=new StringBuilder();for(Item i:ITEMS)if(kind<0||i.kind==kind){if(b.length()>0)b.append('|');b.append(i.name);}return b.toString();}
    static Item itemByName(String name){for(Item i:ITEMS)if(i.name.equalsIgnoreCase(name)||i.id.equalsIgnoreCase(name))return i;return null;}
    static final String ITEMS_JOINED=itemNames(-1);
    static final String KEYS="Bronze|Silver|Gold";

    // ------------------------------------------------------------------ spells
    static final int S_BOLT=0,S_MISSILE=1,S_AREA=2,S_HEAL=3,S_WARD=4,S_SAVE_TARGET=5,S_CONE=6;
    static final class Spell {
        String id,name;int level,kind;Dice dice;int damageType;float range=20,radius;String desc;
        Spell(String id,String name,int level,int kind,String dice,int type,String desc){this.id=id;this.name=name;this.level=level;this.kind=kind;this.dice=Dice.parse(dice);damageType=type;this.desc=desc;}
        Spell radius(float r){radius=r;return this;}
        Spell range(float r){range=r;return this;}
    }
    static final String[] SPELL_KINDS={"Bolt (spell attack)","Missiles (never miss)","Area blast (DEX save)","Heal self","Ward (+AC)","Flame on target (DEX save)","Cone (DEX save)"};
    static ArrayList<Spell> SPELLS=new ArrayList<>();
    static{
        SPELLS.add(new Spell("fire_bolt","Fire Bolt",0,S_BOLT,"1d10",FIRE,"Hurl a mote of fire: ranged spell attack."));
        SPELLS.add(new Spell("sacred_flame","Sacred Flame",0,S_SAVE_TARGET,"1d8",RADIANT,"Radiant flame on the target you face; DEX save negates.").range(14));
        SPELLS.add(new Spell("magic_missile","Magic Missile",1,S_MISSILE,"1d4+1",FORCE,"Three glowing darts that never miss."));
        SPELLS.add(new Spell("cure_wounds","Cure Wounds",1,S_HEAL,"1d8",RADIANT,"Heal yourself (adds your spellcasting modifier)."));
        SPELLS.add(new Spell("shield","Shield",1,S_WARD,"5",FORCE,"+5 AC for a short while."));
        SPELLS.add(new Spell("burning_hands","Burning Hands",1,S_CONE,"3d6",FIRE,"A sheet of flame in front of you; DEX save for half.").range(3));
        SPELLS.add(new Spell("fireball","Fireball",3,S_AREA,"8d6",FIRE,"An explosion of flame where the bolt lands; DEX save for half.").radius(2.5f));
    }
    static final ArrayList<Spell> DEFAULT_SPELLS=new ArrayList<>(SPELLS);
    static Spell spell(String id){if(id==null)return null;for(Spell s:SPELLS)if(s.id.equals(id)||s.name.equalsIgnoreCase(id))return s;return null;}
    static String spellNames(){StringBuilder b=new StringBuilder();for(Spell s:SPELLS){if(b.length()>0)b.append('|');b.append(s.name);}return b.toString();}
    static final String SPELLS_JOINED=spellNames();

    // ------------------------------------------------------------------ classes
    static final class HeroClass {
        String name;int hitDie,primary,castAbility;int[] scores;String[] gear,spells;String desc;boolean heavyArmour;
        HeroClass(String name,int hitDie,int primary,int cast,int[] scores,String[] gear,String[] spells,boolean heavy,String desc){
            this.name=name;this.hitDie=hitDie;this.primary=primary;castAbility=cast;this.scores=scores;this.gear=gear;this.spells=spells;heavyArmour=heavy;this.desc=desc;}
    }
    /** Ability scores use the standard array (15, 14, 13, 12, 10, 8) arranged for each class. */
    static final HeroClass[] BUILTIN_CLASSES={
        new HeroClass("Fighter",10,STR,-1,new int[]{15,13,14,8,12,10},new String[]{"longsword","shield","chainmail","healing_potion"},new String[0],true,"Tough front-line warrior: heavy armour, shield and steel."),
        new HeroClass("Ranger",10,DEX,WIS,new int[]{12,15,13,8,14,10},new String[]{"longbow","shortsword","leather","healing_potion"},new String[]{"cure_wounds"},false,"Deadly archer who can mend their own wounds."),
        new HeroClass("Rogue",8,DEX,-1,new int[]{10,15,13,12,8,14},new String[]{"shortsword","dagger","leather","healing_potion","healing_potion"},new String[0],false,"Quick and sneaky: sneak attacks deal extra damage to unaware foes."),
        new HeroClass("Wizard",6,INT,INT,new int[]{8,14,13,15,12,10},new String[]{"quarterstaff","mana_potion","healing_potion"},new String[]{"fire_bolt","magic_missile","shield","burning_hands","fireball"},false,"Fragile master of arcane fire and force."),
        new HeroClass("Cleric",8,WIS,WIS,new int[]{13,10,14,8,15,12},new String[]{"mace","shield","scalemail","healing_potion"},new String[]{"sacred_flame","cure_wounds","shield"},true,"Armoured healer who smites with radiant light."),
    };
    // ------------------------------------------------------------------ races (the nine Player's Handbook races, 5e rules)
    static final class Race {
        final String name,desc,traits;final int[] bonus;final float speed;final boolean darkvision;final int[] skins;
        Race(String name,int[] bonus,float speed,boolean dv,int[] skins,String traits,String desc){this.name=name;this.bonus=bonus;this.speed=speed;darkvision=dv;this.skins=skins;this.traits=traits;this.desc=desc;}
        /** Scores after racial bonuses. Half-elves add +1 to the two best abilities other than Charisma. */
        int[] apply(int[] base){int[] s=base.clone();for(int i=0;i<6;i++)s[i]+=bonus[i];
            if(name.equals("Half-Elf")){int a=-1,b=-1;for(int i=0;i<5;i++){if(a<0||base[i]>base[a]){b=a;a=i;}else if(b<0||base[i]>base[b])b=i;}s[a]++;s[b]++;}
            for(int i=0;i<6;i++)s[i]=Math.min(20,s[i]);return s;}
        String bonusText(){StringBuilder b=new StringBuilder();String[] n={"STR","DEX","CON","INT","WIS","CHA"};
            for(int i=0;i<6;i++)if(bonus[i]>0){if(b.length()>0)b.append(", ");b.append(n[i]).append(" +").append(bonus[i]);}
            if(name.equals("Half-Elf"))b.append(", +1 to two others");return b.toString();}
    }
    private static final int[] HUMAN_SKIN={0xfff0c8a8,0xffd8a880,0xffc08a64,0xff9a6a48,0xff74482c,0xff4a2e1c};
    static final Race[] RACES={
        new Race("Human",new int[]{1,1,1,1,1,1},1f,false,HUMAN_SKIN,"+1 to every ability","Versatile and ambitious, found in every land."),
        new Race("Elf",new int[]{0,2,0,1,0,0},1f,true,new int[]{0xfff4dcc4,0xffe0bc98,0xffc8a07a,0xff8a6a8a,0xff5a4a6a},"Darkvision · keen senses","Graceful, long-lived and attuned to magic."),
        new Race("Dwarf",new int[]{0,0,2,0,1,0},.85f,true,new int[]{0xffe8bc98,0xffd0a07a,0xffb08060,0xff8a5a3c,0xff5a3a24},"Darkvision · poison resistance · +1 HP per level · slower","Stout, stubborn folk of stone and steel."),
        new Race("Halfling",new int[]{0,2,0,0,0,1},.85f,false,HUMAN_SKIN,"Lucky (rerolls natural 1s on attacks) · slower","Small, cheerful and surprisingly brave."),
        new Race("Gnome",new int[]{0,0,1,2,0,0},.85f,true,new int[]{0xfff0d0b0,0xffd8b090,0xffb88a68,0xff8a6448},"Darkvision · slower","Tiny tinkerers bursting with curiosity."),
        new Race("Half-Elf",new int[]{0,0,0,0,0,2},1f,true,HUMAN_SKIN,"Darkvision","Between two worlds, at home in neither."),
        new Race("Half-Orc",new int[]{2,0,1,0,0,0},1f,true,new int[]{0xff8a9a68,0xff6a7a50,0xff9a9a88,0xff7a8a7a,0xffa0886a},"Darkvision · relentless (once per map, drop to 1 HP instead of 0) · savage crits","Fierce and strong, with orcish blood."),
        new Race("Tiefling",new int[]{0,0,0,1,0,2},1f,true,new int[]{0xffb83a32,0xff8a2a3a,0xff6a3a7a,0xffc87a6a,0xff4a3a5a,0xffd8a890},"Darkvision · fire resistance · horns and tail","Marked by an infernal bloodline."),
        new Race("Dragonborn",new int[]{2,0,0,0,0,1},1f,false,new int[]{0xffb08a3a,0xff9a5a2a,0xffb03a2a,0xff3a5aa0,0xff3a8a4a,0xffd8d8d0,0xff2a2a30,0xffd0b040,0xffa0a8b0,0xffb86a40},"Fire resistance · draconic scales","Proud dragon-folk with scales of many colours."),
    };
    static Race race(String name){if(name!=null)for(Race r:RACES)if(r.name.equalsIgnoreCase(name))return r;return RACES[0];}
    static ArrayList<HeroClass> CLASSES=new ArrayList<>(java.util.Arrays.asList(BUILTIN_CLASSES));
    static final String CLASSES_JOINED;
    static{String[] n=new String[BUILTIN_CLASSES.length];for(int i=0;i<n.length;i++)n[i]=BUILTIN_CLASSES[i].name;CLASSES_JOINED=Sprites.join(n);}
    static HeroClass heroClass(String name){for(HeroClass c:CLASSES)if(c.name.equalsIgnoreCase(name))return c;return CLASSES.isEmpty()?BUILTIN_CLASSES[0]:CLASSES.get(0);}
    /** Level at which each of a class's spells becomes available (spell level 3 needs character level 5). */
    static int levelForSpell(Spell s){return s.level<=1?1:s.level==2?3:s.level==3?5:7;}

    // ------------------------------------------------------------------ loot tables and shops
    /** One possible drop: an item, the chance it drops and how many (dice). */
    static final class LootEntry {String item="";int chance=100;String count="1";}
    static final class LootTable {String id,name;String gold="0";final ArrayList<LootEntry> entries=new ArrayList<>();
        LootTable add(String item,int chance,String count){LootEntry e=new LootEntry();e.item=item;e.chance=chance;e.count=count;entries.add(e);return this;}}
    /** A shop line: an item, its price (0 = the item's value) and stock (-1 = unlimited). */
    static final class ShopEntry {String item="";int price;int stock=-1;}
    static final class Shop {String id,name;int sellRate=50;String greeting="Take a look at my wares.";final ArrayList<ShopEntry> stock=new ArrayList<>();
        Shop add(String item,int price){ShopEntry e=new ShopEntry();e.item=item;e.price=price;stock.add(e);return this;}}
    static ArrayList<LootTable> LOOT=defaultLoot();
    static ArrayList<Shop> SHOPS=defaultShops();
    static LootTable loot(String id){if(id==null)return null;for(LootTable t:LOOT)if(t.id.equals(id)||t.name.equalsIgnoreCase(id))return t;return null;}
    static Shop shop(String id){if(id==null)return null;for(Shop t:SHOPS)if(t.id.equals(id)||t.name.equalsIgnoreCase(id))return t;return null;}
    static int price(ShopEntry e){Item it=item(e.item);return e.price>0?e.price:it==null?0:Math.max(1,it.value);}
    static ArrayList<LootTable> defaultLoot(){
        ArrayList<LootTable> l=new ArrayList<>();LootTable t;
        t=new LootTable();t.id="chest";t.name="Treasure chest";t.gold="3d6";t.add("healing_potion",60,"1").add("gem",25,"1").add("scroll_cure",15,"1");l.add(t);
        t=new LootTable();t.id="skeleton";t.name="Skeleton remains";t.gold="1d6";t.add("small_healing",30,"1").add("dagger",10,"1");l.add(t);
        t=new LootTable();t.id="hoard";t.name="Boss hoard";t.gold="10d10";t.add("greater_healing",100,"1").add("scroll_fireball",50,"1").add("idol",50,"1").add("plate",10,"1");l.add(t);
        return l;
    }
    static ArrayList<Shop> defaultShops(){
        ArrayList<Shop> l=new ArrayList<>();Shop s;
        s=new Shop();s.id="general";s.name="General store";s.add("small_healing",0).add("healing_potion",0).add("mana_potion",0).add("scroll_cure",0).add("dagger",0);l.add(s);
        s=new Shop();s.id="blacksmith";s.name="Blacksmith";s.greeting="Steel and leather, finest in the land.";s.sellRate=40;
        s.add("shortsword",0).add("longsword",0).add("mace",0).add("battleaxe",0).add("leather",0).add("chainshirt",0).add("chainmail",0).add("shield",0);l.add(s);
        return l;
    }
    /** Points the catalogue at a project's own content. */
    static void use(Project p){
        if(p==null)return;p.ensureContent();Dialogue.project=p;
        ITEMS=p.items;SPELLS=p.spells;CLASSES=p.classes;LOOT=p.loot;SHOPS=p.shops;
        if(item("fists")==null)ITEMS.add(0,copy(DEFAULT_ITEMS.get(0)));   // the engine's unarmed attack must exist
    }
    static String uniqueItemId(String base){String b=slug(base,"item"),id=b;int n=2;while(item(id)!=null)id=b+"_"+(n++);return id;}
    static String uniqueSpellId(String base){String b=slug(base,"spell"),id=b;int n=2;while(spell(id)!=null)id=b+"_"+(n++);return id;}
    static String uniqueLootId(String base){String b=slug(base,"loot"),id=b;int n=2;while(loot(id)!=null)id=b+"_"+(n++);return id;}
    static String uniqueShopId(String base){String b=slug(base,"shop"),id=b;int n=2;while(shop(id)!=null)id=b+"_"+(n++);return id;}
    private static String slug(String s,String def){String b=s.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");return b.isEmpty()?def:b;}

    // ------------------------------------------------------------------ json
    static org.json.JSONObject toJson(Item i)throws Exception{
        org.json.JSONObject o=new org.json.JSONObject();o.put("id",i.id);o.put("name",i.name);o.put("kind",i.kind);o.put("sprite",i.sprite);o.put("value",i.value);
        o.put("damage",i.damage.toString());o.put("damageType",i.damageType);o.put("fp",i.fp);o.put("finesse",i.finesse);o.put("ranged",i.ranged);o.put("twoHanded",i.twoHanded);
        o.put("reach",(double)i.reach);o.put("cooldown",(double)i.cooldown);o.put("baseAC",i.baseAC);o.put("maxDex",i.maxDex);o.put("heavy",i.heavy);o.put("strReq",i.strReq);
        o.put("heal",i.heal==null?"":i.heal.toString());o.put("spellPoints",i.spellPoints);o.put("spell",i.spell==null?"":i.spell);o.put("key",i.key);o.put("desc",i.desc==null?"":i.desc);
        return o;
    }
    static Item itemFrom(org.json.JSONObject o){
        Item i=new Item(o.optString("id","item"),o.optString("name","Item"),Math.max(0,Math.min(ITEM_KINDS.length-1,o.optInt("kind",I_MISC))),o.optInt("sprite",-1),Math.max(0,o.optInt("value",0)));
        i.damage=Dice.parse(o.optString("damage","1d4"));i.damageType=Math.max(0,Math.min(DAMAGE_TYPES.length-1,o.optInt("damageType",BLUDGEONING)));i.fp=Math.max(0,Math.min(FP_NAMES.length-1,o.optInt("fp",FP_FIST)));
        i.finesse=o.optBoolean("finesse",false);i.ranged=o.optBoolean("ranged",false);i.twoHanded=o.optBoolean("twoHanded",false);i.reach=(float)o.optDouble("reach",1.5);i.cooldown=(float)o.optDouble("cooldown",.7);
        i.baseAC=o.optInt("baseAC",0);i.maxDex=o.optInt("maxDex",99);i.heavy=o.optBoolean("heavy",false);i.strReq=o.optInt("strReq",0);
        String h=o.optString("heal","");i.heal=h.isEmpty()?null:Dice.parse(h);i.spellPoints=o.optInt("spellPoints",0);String sp=o.optString("spell","");i.spell=sp.isEmpty()?null:sp;i.key=o.optInt("key",-1);i.desc=o.optString("desc","");
        return i;
    }
    static Item copy(Item i){try{return itemFrom(toJson(i));}catch(Exception e){return i;}}
    static org.json.JSONObject toJson(Spell s)throws Exception{
        org.json.JSONObject o=new org.json.JSONObject();o.put("id",s.id);o.put("name",s.name);o.put("level",s.level);o.put("kind",s.kind);o.put("dice",s.dice.toString());
        o.put("damageType",s.damageType);o.put("range",(double)s.range);o.put("radius",(double)s.radius);o.put("desc",s.desc==null?"":s.desc);return o;
    }
    static Spell spellFrom(org.json.JSONObject o){
        Spell s=new Spell(o.optString("id","spell"),o.optString("name","Spell"),Math.max(0,Math.min(9,o.optInt("level",1))),Math.max(0,Math.min(SPELL_KINDS.length-1,o.optInt("kind",S_BOLT))),
                o.optString("dice","1d6"),Math.max(0,Math.min(DAMAGE_TYPES.length-1,o.optInt("damageType",FIRE))),o.optString("desc",""));
        s.range=(float)o.optDouble("range",20);s.radius=(float)o.optDouble("radius",0);return s;
    }
    static Spell copy(Spell s){try{return spellFrom(toJson(s));}catch(Exception e){return s;}}
    static org.json.JSONObject toJson(HeroClass c)throws Exception{
        org.json.JSONObject o=new org.json.JSONObject();o.put("name",c.name);o.put("hitDie",c.hitDie);o.put("primary",c.primary);o.put("cast",c.castAbility);o.put("heavy",c.heavyArmour);o.put("desc",c.desc==null?"":c.desc);
        org.json.JSONArray a=new org.json.JSONArray();for(int v:c.scores)a.put(v);o.put("scores",a);
        org.json.JSONArray g=new org.json.JSONArray();for(String v:c.gear)g.put(v);o.put("gear",g);
        org.json.JSONArray sp=new org.json.JSONArray();for(String v:c.spells)sp.put(v);o.put("spells",sp);return o;
    }
    static HeroClass classFrom(org.json.JSONObject o){
        int[] sc={10,10,10,10,10,10};org.json.JSONArray a=o.optJSONArray("scores");if(a!=null)for(int i=0;i<6&&i<a.length();i++)sc[i]=Math.max(1,Math.min(30,a.optInt(i,10)));
        return new HeroClass(o.optString("name","Adventurer"),Math.max(4,Math.min(12,o.optInt("hitDie",8))),Math.max(0,Math.min(5,o.optInt("primary",STR))),Math.max(-1,Math.min(5,o.optInt("cast",-1))),sc,
                strings(o.optJSONArray("gear")),strings(o.optJSONArray("spells")),o.optBoolean("heavy",false),o.optString("desc",""));
    }
    static HeroClass copy(HeroClass c){try{return classFrom(toJson(c));}catch(Exception e){return c;}}
    private static String[] strings(org.json.JSONArray a){if(a==null)return new String[0];String[] r=new String[a.length()];for(int i=0;i<r.length;i++)r[i]=a.optString(i,"");return r;}
    static org.json.JSONObject toJson(LootTable t)throws Exception{
        org.json.JSONObject o=new org.json.JSONObject();o.put("id",t.id);o.put("name",t.name);o.put("gold",t.gold);
        org.json.JSONArray a=new org.json.JSONArray();for(LootEntry e:t.entries){org.json.JSONObject j=new org.json.JSONObject();j.put("item",e.item);j.put("chance",e.chance);j.put("count",e.count);a.put(j);}o.put("entries",a);return o;
    }
    static LootTable lootFrom(org.json.JSONObject o){
        LootTable t=new LootTable();t.id=o.optString("id","loot");t.name=o.optString("name","Loot");t.gold=o.optString("gold","0");
        org.json.JSONArray a=o.optJSONArray("entries");if(a!=null)for(int i=0;i<a.length();i++){org.json.JSONObject j=a.optJSONObject(i);if(j!=null)t.add(j.optString("item",""),Math.max(0,Math.min(100,j.optInt("chance",100))),j.optString("count","1"));}
        return t;
    }
    static LootTable copy(LootTable t){try{return lootFrom(toJson(t));}catch(Exception e){return t;}}
    static org.json.JSONObject toJson(Shop s)throws Exception{
        org.json.JSONObject o=new org.json.JSONObject();o.put("id",s.id);o.put("name",s.name);o.put("sellRate",s.sellRate);o.put("greeting",s.greeting);
        org.json.JSONArray a=new org.json.JSONArray();for(ShopEntry e:s.stock){org.json.JSONObject j=new org.json.JSONObject();j.put("item",e.item);j.put("price",e.price);j.put("stock",e.stock);a.put(j);}o.put("stock",a);return o;
    }
    static Shop shopFrom(org.json.JSONObject o){
        Shop s=new Shop();s.id=o.optString("id","shop");s.name=o.optString("name","Shop");s.sellRate=Math.max(0,Math.min(100,o.optInt("sellRate",50)));s.greeting=o.optString("greeting","");
        org.json.JSONArray a=o.optJSONArray("stock");if(a!=null)for(int i=0;i<a.length();i++){org.json.JSONObject j=a.optJSONObject(i);if(j==null)continue;ShopEntry e=new ShopEntry();e.item=j.optString("item","");e.price=Math.max(0,j.optInt("price",0));e.stock=j.optInt("stock",-1);s.stock.add(e);}
        return s;
    }
    static Shop copy(Shop s){try{return shopFrom(toJson(s));}catch(Exception e){return s;}}
}
