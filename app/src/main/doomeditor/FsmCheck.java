package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Checks an entity's state machine for the mistakes that make a game quietly not work: transitions on events
 * nothing ever sends, actions sending events their state ignores, states that can never be reached or that the
 * FSM gets stuck in, FINISHED transitions that can't fire, buttons the game doesn't have, and references to items,
 * spells, entities, maps, conversations and quests that don't exist. Pure logic (no Android), used by the FSM
 * editor's Check button.
 */
final class FsmCheck {
    private FsmCheck(){}
    static final int ERROR=0,WARNING=1;
    static final class Issue {
        final int level;final EntityDef def;final FSMGraph.Node node;final int action;final String text;
        Issue(int level,EntityDef def,FSMGraph.Node node,int action,String text){this.level=level;this.def=def;this.node=node;this.action=action;this.text=text;}
        @Override public String toString(){return (level==ERROR?"Error":"Warning")+" · "+def.name+(node!=null?" · "+node.name:"")+": "+text;}
    }
    /** Events the engine sends by itself. */
    static final Set<String> ENGINE=new HashSet<>(java.util.Arrays.asList("FINISHED","DIED","DAMAGED","TOUCHED","USED"));
    /** Actions that keep running every frame for as long as their state is active (their state never FINISHES). */
    static final Set<String> ENDLESS=new HashSet<>(java.util.Arrays.asList("move_joystick","look_touch","button_pressed","button_held","button_released","get_joystick","joystick_moved",
            "use_on_button","weapon_on_button","repeat_event","animation_frame_event","chase_target","wander","turn","float_bob","flicker_light","orbit_camera_look","move_camera_relative",
            "face_movement","repeat_sound","music_beat"));
    /** Actions that send to another entity: their event belongs to the receiver's FSM, not this one. */
    static final Set<String> SENDS_ELSEWHERE=new HashSet<>(java.util.Arrays.asList("send_to_target","broadcast_event"));
    /** Actions with mutually exclusive outcomes: exactly one of these event parameters is sent each time they run. */
    static final HashMap<String,String[]> OUTCOMES=new HashMap<>();
    static{
        String[][] o={{"quest_state","notStartedEvent","activeEvent","doneEvent","failedEvent"},{"has_item","yesEvent","noEvent"},{"has_gold","yesEvent","noEvent"},{"has_key","yesEvent","noEvent"},
            {"level_at_least","yesEvent","noEvent"},{"is_class","yesEvent","noEvent"},{"float_compare","equalEvent","lessEvent","greaterEvent"},{"bool_test","trueEvent","falseEvent"},
            {"chance_event","event","elseEvent"},{"roll_dice","successEvent","failEvent"},{"ability_check","successEvent","failEvent"},{"saving_throw","successEvent","failEvent"},
            {"line_of_sight","visibleEvent","hiddenEvent"},{"distance_check","closerEvent","fartherEvent"},{"take_item","doneEvent","failEvent"},{"pay_gold","paidEvent","failEvent"},
            {"is_map","yesEvent","noEvent"},{"is_third_person","thirdEvent","firstEvent"},{"music_playing","yesEvent","noEvent"},{"give_loot","foundEvent","emptyEvent"}};
        for(String[] r:o)OUTCOMES.put(r[0],java.util.Arrays.copyOfRange(r,1,r.length));
    }

    /** Every issue in the project's entity FSMs, errors first. */
    static ArrayList<Issue> project(Project p){ArrayList<Issue> out=new ArrayList<>();for(EntityDef d:p.defs)out.addAll(check(p,d));sort(out);return out;}
    static void sort(ArrayList<Issue> l){l.sort((a,b)->a.level-b.level);}

    /** Issues in one entity's FSM. */
    static ArrayList<Issue> check(Project p,EntityDef d){
        ArrayList<Issue> out=new ArrayList<>();FSMGraph g=d.fsm;
        if(g.nodes.isEmpty())return out;
        if(g.find(g.start)==null){out.add(new Issue(ERROR,d,null,-1,"No start state: choose one with the flag button."));}
        Set<String> sendable=sendable(p,d);
        // transitions: dangling targets, events nothing sends, case slips
        for(FSMGraph.Node n:g.nodes)for(java.util.Map.Entry<String,String> e:n.transitions.entrySet())transition(out,d,n,e.getKey(),e.getValue(),sendable,g);
        for(java.util.Map.Entry<String,String> e:g.globals.entrySet())transition(out,d,null,e.getKey(),e.getValue(),sendable,g);
        // reachability from the start through transitions and globals
        Set<String> reach=new HashSet<>();ArrayList<String> stack=new ArrayList<>();
        if(g.find(g.start)!=null)stack.add(g.start);
        for(java.util.Map.Entry<String,String> e:g.globals.entrySet())if(sendable.contains(e.getKey()))stack.add(e.getValue());
        while(!stack.isEmpty()){String id=stack.remove(stack.size()-1);if(!reach.add(id))continue;FSMGraph.Node n=g.find(id);if(n==null)continue;
            for(java.util.Map.Entry<String,String> e:n.transitions.entrySet())if(sendable.contains(e.getKey()))stack.add(e.getValue());}
        for(FSMGraph.Node n:g.nodes)if(!reach.contains(n.id))out.add(new Issue(WARNING,d,n,-1,"This state can never be reached: no transition leads to it."));
        boolean diedHandled=g.globals.containsKey("DIED");
        // states
        for(FSMGraph.Node n:g.nodes){
            boolean endless=false;String endlessName="";boolean sendsFinished=false;
            for(int i=0;i<n.actions.size();i++){FSMGraph.Action a=n.actions.get(i);if(!a.enabled)continue;Actions.Spec s=Actions.get(a.type);if(s==null)continue;
                if(isEndless(a,s)){endless=true;endlessName=s.name;}
                for(Actions.Param pr:s.params){String v=val(a,pr.key);
                    if(pr.type==Actions.T_EVENT&&v.equals("FINISHED"))sendsFinished=true;
                    references(out,p,d,n,i,a,s,pr,v);
                    if(pr.type==Actions.T_CHOICE&&pr.key.equals("button")){int hud=v.equalsIgnoreCase("Weapon")?HudLayout.SWAP:v.equalsIgnoreCase("Alt")?HudLayout.SPRINT:-1;
                        if(hud>=0&&p.hud!=null&&!p.hud.e[hud].visible&&d.kind==EntityDef.PLAYER&&!a.type.equals("weapon_on_button"))
                            out.add(new Issue(WARNING,d,n,i,s.name+" waits for the "+v+" button, which is hidden: show "+HudLayout.NAMES[hud]+" on the UI Editor's HUD page."));}}
                // events this action sends to its own FSM that the state ignores
                if(!SENDS_ELSEWHERE.contains(a.type))for(Actions.Param pr:s.params){if(pr.type!=Actions.T_EVENT)continue;String v=val(a,pr.key);
                    if(v.isEmpty()||v.equals("FINISHED")||handles(g,n,v))continue;
                    if(a.type.equals("say")||a.type.equals("show_popup")||a.type.equals("open_shop")||a.type.equals("talk")){   // arrives later, maybe in another state
                        if(!handledAnywhere(g,v))out.add(new Issue(WARNING,d,n,i,s.name+" sends "+v+", but no state has a transition for it."));continue;}
                    out.add(new Issue(WARNING,d,n,i,s.name+" sends "+v+", but this state has no transition for it, so it is ignored."));}
                // outcomes that leave the FSM stuck
                String[] oc=OUTCOMES.get(a.type);
                if(oc!=null&&!n.transitions.containsKey("FINISHED")&&!isEndless(a,s)&&!deathState(g,n)){
                    boolean any=false;ArrayList<String> dead=new ArrayList<>();
                    for(String k:oc){String v=val(a,k);if(!v.isEmpty()&&handles(g,n,v))any=true;else dead.add(Actions.Param.labelOf(k).replace(" Event",""));}
                    if(any&&!dead.isEmpty())out.add(new Issue(WARNING,d,n,i,s.name+": if the result is "+join(dead," or ").toLowerCase()+", nothing leads out of this state and the FSM stops here. Handle it, or add a FINISHED transition as a fallback."));}
            }
            if(n.transitions.containsKey("FINISHED")&&endless&&!sendsFinished)
                out.add(new Issue(WARNING,d,n,-1,"The FINISHED transition never fires: "+endlessName+" keeps running, so the state never finishes."));
            if(n.transitions.containsKey("DIED"))diedHandled=true;
        }
        if((d.kind==EntityDef.MONSTER||d.kind==EntityDef.NPC)&&d.shootable&&!diedHandled)
            out.add(new Issue(WARNING,d,null,-1,"No DIED transition: when it dies its FSM just stops. Add a global DIED event to a death state (sound, loot, XP)."));
        if(d.countItem){boolean removes=false;for(FSMGraph.Node n:g.nodes)for(FSMGraph.Action a:n.actions)if(a.enabled&&a.type.equals("destroy_self"))removes=true;
            if(!removes)out.add(new Issue(WARNING,d,null,-1,"It counts towards the items found, but its FSM never uses Destroy Self, so it can never be collected and the level can't reach 100% items."));}
        if(d.kind==EntityDef.PLAYER){boolean moves=false;
            for(FSMGraph.Node n:g.nodes)if(reach.contains(n.id))for(FSMGraph.Action a:n.actions)if(a.enabled&&(a.type.equals("move_joystick")||a.type.equals("move_camera_relative")||a.type.equals("move_forward")))moves=true;
            if(!moves)out.add(new Issue(ERROR,d,null,-1,"The hero can't move: no reachable state has Move With Joystick or Move Relative To Camera."));}
        return out;
    }
    private static void transition(ArrayList<Issue> out,EntityDef d,FSMGraph.Node n,String ev,String target,Set<String> sendable,FSMGraph g){
        String where=n==null?"Global event "+ev:"Transition "+ev;
        if(g.find(target)==null){out.add(new Issue(ERROR,d,n,-1,where+" leads to a state that no longer exists."));return;}
        if(sendable.contains(ev))return;
        for(String s:sendable)if(s.equalsIgnoreCase(ev)){out.add(new Issue(ERROR,d,n,-1,where+": events are case-sensitive and this FSM sends "+s+", not "+ev+"."));return;}
        out.add(new Issue(WARNING,d,n,-1,where+" never happens: nothing sends "+ev+" to this entity."));
    }
    /** A state entered on DIED: it is meant to be the end. */
    private static boolean deathState(FSMGraph g,FSMGraph.Node n){if(n.id.equals(g.globals.get("DIED")))return true;for(FSMGraph.Node o:g.nodes)if(n.id.equals(o.transitions.get("DIED")))return true;return false;}
    private static boolean handles(FSMGraph g,FSMGraph.Node n,String ev){return n.transitions.containsKey(ev)||g.globals.containsKey(ev);}
    private static boolean handledAnywhere(FSMGraph g,String ev){if(g.globals.containsKey(ev))return true;for(FSMGraph.Node n:g.nodes)if(n.transitions.containsKey(ev))return true;return false;}
    private static String join(java.util.List<String> l,String sep){StringBuilder b=new StringBuilder();for(String x:l){if(b.length()>0)b.append(sep);b.append(x);}return b.toString();}
    static String val(FSMGraph.Action a,String k){String v=a.params.get(k);return v==null?"":v.trim();}
    static boolean isEndless(FSMGraph.Action a,Actions.Spec s){
        if(ENDLESS.contains(a.type))return true;
        if(s.param("everyFrame")!=null&&!a.type.equals("look_for_player")){String v=val(a,"everyFrame");return v.equalsIgnoreCase("true")||v.equals("1");}
        return false;
    }
    /** Every event that can reach this entity's FSM: the engine's, its own actions', other entities' sends and broadcasts, conversation effects. */
    static Set<String> sendable(Project p,EntityDef d){
        Set<String> s=new LinkedHashSet<>(ENGINE);
        for(FSMGraph.Node n:d.fsm.nodes)for(FSMGraph.Action a:n.actions){if(!a.enabled||SENDS_ELSEWHERE.contains(a.type))continue;Actions.Spec sp=Actions.get(a.type);if(sp==null)continue;
            for(Actions.Param pr:sp.params)if(pr.type==Actions.T_EVENT){String v=val(a,pr.key);if(!v.isEmpty())s.add(v);}}
        for(EntityDef o:p.defs)for(FSMGraph.Node n:o.fsm.nodes)for(FSMGraph.Action a:n.actions){if(!a.enabled)continue;String ev=val(a,"event");if(ev.isEmpty())continue;
            if(a.type.equals("broadcast_event")){String only=val(a,"entity");if(only.isEmpty()||only.equals(d.id))s.add(ev);}
            else if(a.type.equals("send_to_target")){String to=val(a,"to");if(to.equalsIgnoreCase("Target")||(to.equalsIgnoreCase("Player")&&d.kind==EntityDef.PLAYER)||(to.equalsIgnoreCase("Self")&&o==d))s.add(ev);}}
        for(Dialogue.Def dl:p.dialogues)for(Dialogue.Node n:dl.nodes)for(Dialogue.Effect e:n.effects)if(e.kind==Dialogue.E_EVENT&&e.s!=null&&!e.s.isEmpty())s.add(e.s.trim());
        return s;
    }
    /** Parameters that name things in the project: each must exist. */
    private static void references(ArrayList<Issue> out,Project p,EntityDef d,FSMGraph.Node n,int i,FSMGraph.Action a,Actions.Spec s,Actions.Param pr,String v){
        String what=null;
        switch(pr.type){
            case Actions.T_ENTITY:if(!v.isEmpty()&&p.def(v)==null)what="entity type \""+v+"\"";else if(v.isEmpty()&&(a.type.equals("spawn_entity")||a.type.equals("drop_item")))what="entity type (none chosen)";break;
            case Actions.T_ITEM:if(Rpg.itemByName(v)==null)what=v.isEmpty()?"item (none chosen)":"item \""+v+"\"";break;
            case Actions.T_SPELL:if(!v.equalsIgnoreCase("Selected")&&Rpg.spell(v)==null)what=v.isEmpty()?"spell (none chosen)":"spell \""+v+"\"";break;
            case Actions.T_LOOT:if(Rpg.loot(v)==null)what=v.isEmpty()?"loot table (none chosen)":"loot table \""+v+"\"";break;
            case Actions.T_SHOP:if(Rpg.shop(v)==null)what=v.isEmpty()?"shop (none chosen)":"shop \""+v+"\"";break;
            case Actions.T_TALK:if(p.dialogue(v)==null)what=v.isEmpty()?"conversation (none chosen)":"conversation \""+v+"\"";break;
            case Actions.T_QUEST:if((a.type.startsWith("quest_")&&!a.type.equals("quest_state"))&&p.questDef(v)==null)what=v.isEmpty()?"quest (none chosen)":"quest \""+v+"\" in the Quests tab";break;
            case Actions.T_MAP:{int num;try{num=Integer.parseInt(v);}catch(NumberFormatException e){num=-1;}if(p.map(num)==null)what=v.isEmpty()?"map (none chosen)":"map "+v;break;}
            case Actions.T_SONG:if(!v.isEmpty()&&p.song(v)==null)what="song \""+v+"\"";else if(v.isEmpty()&&a.type.equals("play_music"))what="song (none chosen)";break;
            case Actions.T_SOUND:if(!v.isEmpty()&&p.sound(v)==null&&SoundFx.builtinIndex(v)<0)what="sound \""+v+"\"";break;
            case Actions.T_CLASS:{boolean ok=false;for(Rpg.HeroClass c:Rpg.CLASSES)if(c.name.equalsIgnoreCase(v))ok=true;if(!ok)what="class \""+v+"\"";break;}
            default:break;
        }
        if(what!=null)out.add(new Issue(ERROR,d,n,i,s.name+": there is no "+what+"."));
    }
}
