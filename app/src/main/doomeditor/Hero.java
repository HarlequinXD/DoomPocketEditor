package com.retro.doomeditor;

import java.util.ArrayList;

/** The player character: class, ability scores, level, hit points, spell points, equipment and inventory. */
final class Hero {
    Rpg.HeroClass cls;
    /** Race (null for heroes not made on the creation screen: plain human-ish defaults), name and look. */
    Rpg.Race race;String name="";CharLook look;
    boolean relentlessUsed;
    int level=1,xp,gold,maxHp,sp,maxSp;
    final int[] scores=new int[6];
    Rpg.Item weapon,armour,shield;
    /** Inventory stacks (equipped items stay listed). */
    static final class Stack {final Rpg.Item item;int count;Stack(Rpg.Item i,int c){item=i;count=c;}}
    final ArrayList<Stack> inventory=new ArrayList<>();
    final ArrayList<Rpg.Spell> spells=new ArrayList<>();
    int selectedSpell;
    float wardTime;int wardAC;
    int kills;

    /** A hero made on the character creation screen: its race, class and scores replace the player entity's. */
    static Hero create(EntityDef d,CharLook look){
        if(look==null)return create(d);
        String keep=d==null?null:d.heroClass;boolean keepCustom=d!=null&&d.customAbilities;
        if(d!=null){d.heroClass=look.cls;d.customAbilities=false;}
        Hero h;try{h=create(d);}finally{if(d!=null){d.heroClass=keep;d.customAbilities=keepCustom;}}
        h.cls=Rpg.heroClass(look.cls);h.race=look.raceInfo();h.name=look.name;h.look=look.copy();
        int[] sc=look.finalScores();int oldCon=Rpg.mod(h.scores[Rpg.CON]);System.arraycopy(sc,0,h.scores,0,6);
        h.maxHp+=(Rpg.mod(h.scores[Rpg.CON])-oldCon)*h.level;if(h.race.name.equals("Dwarf"))h.maxHp+=h.level;h.maxHp=Math.max(1,h.maxHp);
        return h;
    }
    boolean resists(int damageType){if(race==null)return false;switch(race.name){case "Dwarf":return damageType==Rpg.POISON;case "Tiefling":case "Dragonborn":return damageType==Rpg.FIRE;default:return false;}}
    boolean lucky(){return race!=null&&race.name.equals("Halfling");}
    boolean savage(){return race!=null&&race.name.equals("Half-Orc");}
    boolean darkvision(){return race!=null&&race.darkvision;}
    static Hero create(EntityDef d){
        Hero h=new Hero();h.cls=Rpg.heroClass(d==null?"Fighter":d.heroClass);
        System.arraycopy(h.cls.scores,0,h.scores,0,6);
        if(d!=null&&d.abilities!=null&&d.customAbilities)System.arraycopy(d.abilities,0,h.scores,0,6);
        for(String g:h.cls.gear){Rpg.Item it=Rpg.item(g);if(it!=null)h.add(it,1);}
        // equip best starting gear
        for(Stack s:h.inventory){Rpg.Item it=s.item;if(it.kind==Rpg.I_WEAPON&&h.weapon==null)h.weapon=it;else if(it.kind==Rpg.I_ARMOR&&h.armour==null)h.armour=it;else if(it.kind==Rpg.I_SHIELD&&h.shield==null&&(h.weapon==null||!h.weapon.twoHanded))h.shield=it;}
        h.gold=d==null?15:Math.max(0,d.startGold);
        h.maxHp=h.cls.hitDie+Rpg.mod(h.scores[Rpg.CON]);
        h.refreshSpells();
        h.maxSp=h.casterLevel()>0?Rpg.SPELL_POINTS[0]:0;
        int start=d==null?1:Math.max(1,Math.min(Rpg.MAX_LEVEL,d.level));
        while(h.level<start){h.xp=Rpg.XP_TABLE[h.level];h.levelUp();}
        h.sp=h.maxSp;
        return h;
    }
    boolean isCaster(){return cls.castAbility>=0;}
    int casterLevel(){return isCaster()?level:0;}
    int mod(int ability){return Rpg.mod(scores[ability]);}
    int prof(){return Rpg.proficiency(level);}
    Rpg.Item weaponOrFists(){return weapon!=null?weapon:Rpg.item("fists");}
    /** Ability used for attacks with this weapon: DEX for ranged, the better of STR/DEX for finesse, otherwise STR. */
    int attackAbility(Rpg.Item w){if(w.ranged)return Rpg.DEX;if(w.finesse&&mod(Rpg.DEX)>mod(Rpg.STR))return Rpg.DEX;return Rpg.STR;}
    int attackBonus(Rpg.Item w){return mod(attackAbility(w))+prof();}
    int damageBonus(Rpg.Item w){return mod(attackAbility(w));}
    int ac(){
        int dex=mod(Rpg.DEX),ac;
        if(armour==null)ac=10+dex;else ac=armour.baseAC+Math.min(dex,armour.maxDex);
        if(shield!=null)ac+=shield.baseAC;
        if(wardTime>0)ac+=wardAC;
        return ac;
    }
    int castMod(){return isCaster()?mod(cls.castAbility):0;}
    int spellAttack(){return castMod()+prof();}
    int saveDC(){return 8+prof()+castMod();}
    /** Number of extra d6 for a rogue's sneak attack. */
    int sneakDice(){return cls.name.equals("Rogue")?(level+1)/2:0;}
    void refreshSpells(){
        spells.clear();
        for(String id:cls.spells){Rpg.Spell s=Rpg.spell(id);if(s!=null&&level>=Rpg.levelForSpell(s))spells.add(s);}
        if(selectedSpell>=spells.size())selectedSpell=0;
    }
    Rpg.Spell currentSpell(){return spells.isEmpty()?null:spells.get(Math.max(0,Math.min(spells.size()-1,selectedSpell)));}

    // ------------------------------------------------------------------ experience
    /** Adds XP; returns how many levels were gained. */
    int gainXp(int amount){
        xp+=Math.max(0,amount);int gained=0;
        while(level<Rpg.MAX_LEVEL&&xp>=Rpg.XP_TABLE[level]){levelUp();gained++;}
        return gained;
    }
    /** Average hit die + CON per level, more spell points, new spells. */
    void levelUp(){
        level++;
        int gain=Math.max(1,cls.hitDie/2+1+mod(Rpg.CON))+(race!=null&&race.name.equals("Dwarf")?1:0);maxHp+=gain;
        if(isCaster())maxSp=Rpg.SPELL_POINTS[Math.min(Rpg.SPELL_POINTS.length-1,level-1)];
        // ability score improvement at 4, 8, 12, 16, 19: +2 to the primary ability (max 20)
        if(level==4||level==8||level==12||level==16||level==19)scores[cls.primary]=Math.min(20,scores[cls.primary]+2);
        refreshSpells();
    }
    float xpFraction(){if(level>=Rpg.MAX_LEVEL)return 1;int a=Rpg.XP_TABLE[level-1],b=Rpg.XP_TABLE[level];return Math.max(0,Math.min(1,(xp-a)/(float)(b-a)));}

    // ------------------------------------------------------------------ inventory
    void add(Rpg.Item it,int n){
        if(it==null||n<=0)return;
        if(it.kind==Rpg.I_POTION||it.kind==Rpg.I_SCROLL||it.kind==Rpg.I_MISC||it.kind==Rpg.I_KEY)for(Stack s:inventory)if(s.item==it){s.count+=n;return;}
        for(int i=0;i<n;i++)inventory.add(new Stack(it,1));
    }
    int count(Rpg.Item it){int c=0;for(Stack s:inventory)if(s.item==it)c+=s.count;return c;}
    boolean remove(Rpg.Item it,int n){
        if(count(it)<n)return false;
        for(int i=inventory.size()-1;i>=0&&n>0;i--){Stack s=inventory.get(i);if(s.item!=it)continue;int t=Math.min(n,s.count);s.count-=t;n-=t;
            if(s.count<=0){inventory.remove(i);if(count(it)==0)unequip(it);}}
        return true;
    }
    boolean hasKey(int k){for(Stack s:inventory)if(s.item.kind==Rpg.I_KEY&&s.item.key==k)return true;return false;}
    boolean isEquipped(Rpg.Item it){return it!=null&&(it==weapon||it==armour||it==shield);}
    /** Equips a weapon, armour or shield. Returns a message explaining why not, or null. */
    String equip(Rpg.Item it){
        switch(it.kind){
            case Rpg.I_WEAPON:weapon=it;if(it.twoHanded)shield=null;return null;
            case Rpg.I_ARMOR:if(it.heavy&&!cls.heavyArmour)return cls.name+"s can't wear heavy armour";armour=it;return null;
            case Rpg.I_SHIELD:if(weapon!=null&&weapon.twoHanded)return "Your weapon needs both hands";if(cls.name.equals("Wizard")||cls.name.equals("Rogue"))return cls.name+"s aren't trained with shields";shield=it;return null;
            default:return "That can't be equipped";
        }
    }
    void unequip(Rpg.Item it){if(weapon==it)weapon=null;if(armour==it)armour=null;if(shield==it)shield=null;}
    int potionCount(){int c=0;for(Stack s:inventory)if(s.item.kind==Rpg.I_POTION&&s.item.heal!=null)c+=s.count;return c;}
    /** Best healing potion to quaff (smallest that is enough, else the largest). */
    Rpg.Item bestPotion(int missing){Rpg.Item best=null;for(Stack s:inventory){Rpg.Item i=s.item;if(i.kind!=Rpg.I_POTION||i.heal==null)continue;
        if(best==null||(best.heal.average()<missing&&i.heal.average()>best.heal.average())||(i.heal.average()>=missing&&i.heal.average()<best.heal.average()))best=i;}return best;}
}
