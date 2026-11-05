package com.retro.doomeditor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * Conversation trees and quest definitions from the Dialogue editor.
 * A conversation is a graph of nodes: each node has a speaker line, effects that happen when it is shown,
 * and choices (each with an optional condition) that lead to another node or end the talk.
 */
final class Dialogue {
    private Dialogue(){}

    // ------------------------------------------------------------------ conditions and effects
    static final String[] COND_KINDS={"Always","Hero has item","Quest is","Hero has gold","Variable is at least","Hero class is","Hero level is at least"};
    static final int C_NONE=0,C_ITEM=1,C_QUEST=2,C_GOLD=3,C_VAR=4,C_CLASS=5,C_LEVEL=6;
    static final String[] QUEST_STATES={"Not started","Active","Done","Failed"};
    static final String[] EFFECT_KINDS={"Give item","Take item","Give gold","Take gold","Start quest","Set quest stage","Complete quest (rewards)","Fail quest",
            "Set variable","Add to variable","Give XP","Give loot","Open shop (ends talk)","Send event to speaker","Fully rest the hero"};
    static final int E_GIVE_ITEM=0,E_TAKE_ITEM=1,E_GIVE_GOLD=2,E_TAKE_GOLD=3,E_START_QUEST=4,E_QUEST_STAGE=5,E_COMPLETE_QUEST=6,E_FAIL_QUEST=7,
            E_SET_VAR=8,E_ADD_VAR=9,E_XP=10,E_LOOT=11,E_SHOP=12,E_EVENT=13,E_REST=14;

    static final class Cond {int kind;String s="";int n=1;boolean not;}
    static final class Effect {int kind;String s="";int n=1;}
    static final class Choice {String text="…";String target="";final Cond cond=new Cond();}
    static final class Node {
        String id,speaker="",text="";float x,y;String next="";
        final ArrayList<Choice> choices=new ArrayList<>();
        final ArrayList<Effect> effects=new ArrayList<>();
    }
    static final class Def {
        String id,name,speaker="",start="";
        final ArrayList<Node> nodes=new ArrayList<>();
        Node node(String id){if(id==null)return null;for(Node n:nodes)if(n.id.equals(id))return n;return null;}
        String newNodeId(){int i=nodes.size()+1;while(node("n"+i)!=null)i++;return "n"+i;}
        Node add(String text,float x,float y){Node n=new Node();n.id=newNodeId();n.text=text;n.x=x;n.y=y;nodes.add(n);if(start.isEmpty())start=n.id;return n;}
    }
    /** A quest: title, stage objectives and rewards. */
    static final class QuestDef {
        String id,title="New quest";final ArrayList<String> stages=new ArrayList<>();int xp=100,gold=25;String rewardItem="";int rewardCount=1;
        String objective(int stage){return stages.isEmpty()?"":stages.get(Math.max(0,Math.min(stages.size()-1,stage)));}
    }

    /** A new conversation with a greeting and a goodbye. */
    static Def starter(String id,String name){
        Def d=new Def();d.id=id;d.name=name;
        Node a=d.add("Well met, traveller. What brings you here?",0,0);
        Node b=d.add("The old crypt lies east of the village. Be careful.",300,-80);
        Choice c1=new Choice();c1.text="Any news?";c1.target=b.id;a.choices.add(c1);
        Choice c2=new Choice();c2.text="Farewell.";a.choices.add(c2);
        return d;
    }

    // ------------------------------------------------------------------ labels
    static String condLabel(Cond c){
        if(c.kind==C_NONE)return "Always";
        String s;
        switch(c.kind){
            case C_ITEM:{Rpg.Item it=Rpg.item(c.s);s="has "+(c.n>1?c.n+"× ":"")+(it==null?c.s+"?":it.name);break;}
            case C_QUEST:s=questName(c.s)+" is "+QUEST_STATES[Math.max(0,Math.min(3,c.n))].toLowerCase();break;
            case C_GOLD:s="has "+c.n+" gold";break;
            case C_VAR:s=c.s+" ≥ "+c.n;break;
            case C_CLASS:s="is a "+c.s;break;
            default:s="level ≥ "+c.n;
        }
        return (c.not?"NOT ":"")+s;
    }
    static String effectLabel(Effect e){
        switch(e.kind){
            case E_GIVE_ITEM:case E_TAKE_ITEM:{Rpg.Item it=Rpg.item(e.s);return (e.kind==E_GIVE_ITEM?"Give ":"Take ")+(e.n>1?e.n+"× ":"")+(it==null?e.s+"?":it.name);}
            case E_GIVE_GOLD:return "Give "+e.n+" gold";
            case E_TAKE_GOLD:return "Take "+e.n+" gold";
            case E_START_QUEST:return "Start "+questName(e.s);
            case E_QUEST_STAGE:return questName(e.s)+" → stage "+e.n;
            case E_COMPLETE_QUEST:return "Complete "+questName(e.s);
            case E_FAIL_QUEST:return "Fail "+questName(e.s);
            case E_SET_VAR:return e.s+" = "+e.n;
            case E_ADD_VAR:return e.s+" + "+e.n;
            case E_XP:return "Give "+e.n+" XP";
            case E_LOOT:{Rpg.LootTable t=Rpg.loot(e.s);return "Loot: "+(t==null?e.s+"?":t.name);}
            case E_SHOP:{Rpg.Shop t=Rpg.shop(e.s);return "Open shop: "+(t==null?e.s+"?":t.name);}
            case E_EVENT:return "Send "+e.s;
            default:return "Fully rest";
        }
    }
    /** Quest names come from the open project. */
    static Project project;
    static String questName(String id){QuestDef q=project==null?null:project.questDef(id);return q==null?(id==null||id.isEmpty()?"(quest)":id):q.title;}

    // ------------------------------------------------------------------ json
    static JSONObject toJson(Def d)throws Exception{
        JSONObject o=new JSONObject();o.put("id",d.id);o.put("name",d.name);o.put("speaker",d.speaker);o.put("start",d.start);
        JSONArray ns=new JSONArray();
        for(Node n:d.nodes){JSONObject j=new JSONObject();j.put("id",n.id);j.put("speaker",n.speaker);j.put("text",n.text);j.put("x",(double)n.x);j.put("y",(double)n.y);j.put("next",n.next);
            JSONArray cs=new JSONArray();for(Choice c:n.choices){JSONObject k=new JSONObject();k.put("text",c.text);k.put("target",c.target);k.put("ck",c.cond.kind);k.put("cs",c.cond.s);k.put("cn",c.cond.n);k.put("cnot",c.cond.not);cs.put(k);}j.put("choices",cs);
            JSONArray es=new JSONArray();for(Effect e:n.effects){JSONObject k=new JSONObject();k.put("k",e.kind);k.put("s",e.s);k.put("n",e.n);es.put(k);}j.put("effects",es);
            ns.put(j);}
        o.put("nodes",ns);return o;
    }
    static Def fromJson(JSONObject o){
        Def d=new Def();d.id=o.optString("id","talk");d.name=o.optString("name","Conversation");d.speaker=o.optString("speaker","");d.start=o.optString("start","");
        JSONArray ns=o.optJSONArray("nodes");
        if(ns!=null)for(int i=0;i<ns.length();i++){JSONObject j=ns.optJSONObject(i);if(j==null)continue;Node n=new Node();
            n.id=j.optString("id","n"+(i+1));n.speaker=j.optString("speaker","");n.text=j.optString("text","");n.x=(float)j.optDouble("x",0);n.y=(float)j.optDouble("y",0);n.next=j.optString("next","");
            JSONArray cs=j.optJSONArray("choices");if(cs!=null)for(int k=0;k<cs.length();k++){JSONObject q=cs.optJSONObject(k);if(q==null)continue;Choice c=new Choice();c.text=q.optString("text","");c.target=q.optString("target","");
                c.cond.kind=Math.max(0,Math.min(COND_KINDS.length-1,q.optInt("ck",0)));c.cond.s=q.optString("cs","");c.cond.n=q.optInt("cn",1);c.cond.not=q.optBoolean("cnot",false);n.choices.add(c);}
            JSONArray es=j.optJSONArray("effects");if(es!=null)for(int k=0;k<es.length();k++){JSONObject q=es.optJSONObject(k);if(q==null)continue;Effect e=new Effect();e.kind=Math.max(0,Math.min(EFFECT_KINDS.length-1,q.optInt("k",0)));e.s=q.optString("s","");e.n=q.optInt("n",1);n.effects.add(e);}
            d.nodes.add(n);}
        if(d.node(d.start)==null&&!d.nodes.isEmpty())d.start=d.nodes.get(0).id;
        return d;
    }
    static Def copy(Def d){try{return fromJson(toJson(d));}catch(Exception e){return d;}}
    static JSONObject toJson(QuestDef q)throws Exception{
        JSONObject o=new JSONObject();o.put("id",q.id);o.put("title",q.title);JSONArray s=new JSONArray();for(String t:q.stages)s.put(t);o.put("stages",s);
        o.put("xp",q.xp);o.put("gold",q.gold);o.put("item",q.rewardItem);o.put("count",q.rewardCount);return o;
    }
    static QuestDef questFrom(JSONObject o){
        QuestDef q=new QuestDef();q.id=o.optString("id","quest");q.title=o.optString("title","Quest");JSONArray s=o.optJSONArray("stages");if(s!=null)for(int i=0;i<s.length();i++)q.stages.add(s.optString(i,""));
        q.xp=Math.max(0,o.optInt("xp",0));q.gold=Math.max(0,o.optInt("gold",0));q.rewardItem=o.optString("item","");q.rewardCount=Math.max(1,o.optInt("count",1));return q;
    }
}
