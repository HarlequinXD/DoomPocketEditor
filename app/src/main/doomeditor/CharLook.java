package com.retro.doomeditor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A hero made on the character creation screen: name, race, class, body type, appearance and ability scores.
 * The 3D model is built from it (BoxMesh) as layers: race body and head, then appearance, then the class's outfit.
 */
final class CharLook {
    String name="Hero",race="Human",cls="Fighter";
    boolean female,beard,headwear=true;
    int skin=1,hair=1,hairColour=1,eyes=0;
    /** Rolled ability scores (before racial bonuses), or null to use the class's standard array. */
    int[] rolled;

    static final String[] HAIR_STYLES={"Bald","Short","Long","Ponytail","Mohawk"};
    static final int[] HAIR_COLOURS={0xff1e1a16,0xff4a2e18,0xff8a5a2a,0xffc89a50,0xffe0d0a0,0xff9a3a1a,0xffb8b4ac,0xfff0ece4,0xff3a3a6a,0xff2a5a3a};
    static final int[] EYE_COLOURS={0xff34505e,0xff3a5a2a,0xff5a3a1e,0xff6a6a70,0xff3a5a8a,0xffd0a020,0xffa02020,0xff7a3aa0};

    /** The hero being played (or being created): BoxMesh builds the "Created Character" model from it. */
    static CharLook current=new CharLook();

    /** Makes {@code l} the hero being shown; rebuilds the Created Character model only if the look changed. */
    static synchronized void setCurrent(CharLook l){
        if(l==null)return;boolean changed=!l.key().equals(current.key());current=l.copy();
        if(changed){MeshModels.invalidate(Sprites.CREATED);Sprites.invalidate(Sprites.CREATED);Puppet.invalidate(Sprites.CREATED);}
    }
    Rpg.Race raceInfo(){return Rpg.race(race);}
    int skinColour(){int[] s=raceInfo().skins;return s[Math.max(0,Math.min(s.length-1,skin))];}
    int hairColour(){return HAIR_COLOURS[Math.max(0,Math.min(HAIR_COLOURS.length-1,hairColour))];}
    int eyeColour(){return EYE_COLOURS[Math.max(0,Math.min(EYE_COLOURS.length-1,eyes))];}
    /** Ability scores before racial bonuses. */
    int[] baseScores(){return rolled!=null?rolled.clone():Rpg.heroClass(cls).scores.clone();}
    int[] finalScores(){return raceInfo().apply(baseScores());}
    /** Identifies the model: two looks with the same key build the same mesh. */
    String key(){return race+"|"+cls+"|"+female+"|"+beard+"|"+headwear+"|"+skin+"|"+hair+"|"+hairColour+"|"+eyes;}
    CharLook copy(){try{return fromJson(toJson());}catch(Exception e){return new CharLook();}}

    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("name",name);o.put("race",race);o.put("class",cls);o.put("female",female);o.put("beard",beard);o.put("headwear",headwear);
        o.put("skin",skin);o.put("hair",hair);o.put("hairColour",hairColour);o.put("eyes",eyes);
        if(rolled!=null){JSONArray a=new JSONArray();for(int v:rolled)a.put(v);o.put("rolled",a);}
        return o;
    }
    static CharLook fromJson(JSONObject o){
        CharLook l=new CharLook();if(o==null)return l;
        l.name=o.optString("name","Hero");l.race=Rpg.race(o.optString("race","Human")).name;l.cls=o.optString("class","Fighter");
        l.female=o.optBoolean("female",false);l.beard=o.optBoolean("beard",false);l.headwear=o.optBoolean("headwear",true);
        l.skin=o.optInt("skin",1);l.hair=Math.max(0,Math.min(HAIR_STYLES.length-1,o.optInt("hair",1)));l.hairColour=o.optInt("hairColour",1);l.eyes=o.optInt("eyes",0);
        JSONArray a=o.optJSONArray("rolled");if(a!=null&&a.length()==6){l.rolled=new int[6];for(int i=0;i<6;i++)l.rolled[i]=Math.max(3,Math.min(18,a.optInt(i,10)));}
        return l;
    }
    /** The last hero created on this phone, offered again next time. */
    static CharLook last(android.content.Context c){
        try{String s=c.getSharedPreferences("app",0).getString("lastHero",null);if(s!=null)return fromJson(new JSONObject(s));}catch(Exception ignored){}
        return new CharLook();
    }
    static void remember(android.content.Context c,CharLook l){try{c.getSharedPreferences("app",0).edit().putString("lastHero",l.toJson().toString()).apply();}catch(Exception ignored){}}
}
