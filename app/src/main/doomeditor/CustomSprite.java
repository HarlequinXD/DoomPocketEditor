package com.retro.doomeditor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** A user-drawn sprite: a canvas size plus named animation clips of frames. */
final class CustomSprite {
    static final String[] STANDARD_CLIPS={"Idle","Walk","Attack","Pain","Death"};
    String id,name;
    int w,h;
    final ArrayList<Clip> clips=new ArrayList<>();
    int version;
    static final class Clip {
        String name;float fps=8;boolean loop=true;
        final ArrayList<int[]> frames=new ArrayList<>();
        Clip(String name){this.name=name;}
    }
    CustomSprite(String id,String name,int w,int h){this.id=id;this.name=name;this.w=w;this.h=h;}

    static CustomSprite blank(String id,String name,int w,int h){
        CustomSprite s=new CustomSprite(id,name,w,h);
        for(String c:STANDARD_CLIPS){Clip clip=new Clip(c);clip.frames.add(new int[w*h]);if(c.equals("Death"))clip.loop=false;if(c.equals("Walk"))clip.fps=6;s.clips.add(clip);}
        return s;
    }
    /** Copies a built-in sprite's frames into standard clips as an editable starting point. */
    static CustomSprite fromBuiltin(String id,String name,int builtin){
        Sprites.Sprite base=Sprites.get(builtin,Sprites.F_IDLE);
        CustomSprite s=new CustomSprite(id,name,base.w,base.h);
        int[][] map={{Sprites.F_IDLE},{Sprites.F_WALK1,Sprites.F_IDLE,Sprites.F_WALK2,Sprites.F_IDLE},{Sprites.F_ATTACK},{Sprites.F_PAIN},{Sprites.F_PAIN,Sprites.F_DEAD}};
        for(int i=0;i<STANDARD_CLIPS.length;i++){Clip c=new Clip(STANDARD_CLIPS[i]);if(i==4)c.loop=false;if(i==1)c.fps=6;
            for(int f:map[i]){Sprites.Sprite sp=Sprites.get(builtin,f);int[] px=new int[s.w*s.h];
                for(int y=0;y<Math.min(s.h,sp.h);y++)for(int x=0;x<Math.min(s.w,sp.w);x++)px[y*s.w+x]=sp.px[y*sp.w+x];c.frames.add(px);}
            s.clips.add(c);}
        return s;
    }
    Clip clip(String name){for(Clip c:clips)if(c.name.equalsIgnoreCase(name))return c;return null;}
    void touch(){version++;cache.clear();}

    // ------------------------------------------------------------------ engine lookup
    private final java.util.HashMap<int[],Sprites.Sprite> cache=new java.util.HashMap<>();
    private Sprites.Sprite wrap(int[] px){Sprites.Sprite s=cache.get(px);if(s==null){s=new Sprites.Sprite(w,h,false);System.arraycopy(px,0,s.px,0,Math.min(px.length,s.px.length));cache.put(px,s);}return s;}
    /** Frame for an engine animation state; clips fall back to Idle, then to the first clip. */
    Sprites.Sprite frameFor(int engineFrame,float time,float deadTime){
        String want=engineFrame==Sprites.F_DEAD?"Death":engineFrame==Sprites.F_PAIN?"Pain":engineFrame==Sprites.F_ATTACK?"Attack":engineFrame==Sprites.F_WALK1||engineFrame==Sprites.F_WALK2?"Walk":"Idle";
        Clip c=clip(want);if(c==null||c.frames.isEmpty())c=clip("Idle");if((c==null||c.frames.isEmpty())&&!clips.isEmpty())c=clips.get(0);
        if(c==null||c.frames.isEmpty())return null;
        int n=c.frames.size(),i;
        if(engineFrame==Sprites.F_DEAD||!c.loop)i=Math.min(n-1,(int)(Math.max(0,engineFrame==Sprites.F_DEAD?deadTime:time)*c.fps));
        else i=(int)(time*c.fps)%n;
        return wrap(c.frames.get(Math.max(0,i)));
    }
    android.graphics.Bitmap thumbnail(){
        Clip c=clip("Idle");if(c==null||c.frames.isEmpty())c=clips.isEmpty()?null:clips.get(0);
        android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
        if(c!=null&&!c.frames.isEmpty())b.setPixels(c.frames.get(0),0,w,0,0,w,h);return b;
    }

    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("w",w);o.put("h",h);
        JSONArray cs=new JSONArray();
        for(Clip c:clips){JSONObject co=new JSONObject();co.put("name",c.name);co.put("fps",c.fps);co.put("loop",c.loop);JSONArray fr=new JSONArray();for(int[] f:c.frames)fr.put(Codec.encode(f));co.put("frames",fr);cs.put(co);}
        o.put("clips",cs);return o;
    }
    static CustomSprite fromJson(JSONObject o)throws Exception{
        CustomSprite s=new CustomSprite(o.getString("id"),o.optString("name","Sprite"),Math.max(1,Math.min(128,o.optInt("w",32))),Math.max(1,Math.min(128,o.optInt("h",32))));
        JSONArray cs=o.optJSONArray("clips");
        if(cs!=null)for(int i=0;i<cs.length();i++){JSONObject co=cs.getJSONObject(i);Clip c=new Clip(co.optString("name","Clip"));c.fps=(float)co.optDouble("fps",8);c.loop=!"false".equals(co.optString("loop","true"));
            JSONArray fr=co.optJSONArray("frames");if(fr!=null)for(int k=0;k<fr.length();k++)c.frames.add(Codec.decode(String.valueOf(fr.get(k)),s.w*s.h));
            if(c.frames.isEmpty())c.frames.add(new int[s.w*s.h]);s.clips.add(c);}
        if(s.clips.isEmpty()){Clip c=new Clip("Idle");c.frames.add(new int[s.w*s.h]);s.clips.add(c);}
        return s;
    }
}
