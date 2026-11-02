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
    /** Number of view angles drawn for this sprite: 1 (always faces the camera), 4 or 8 (Doom-style rotations). */
    int angles=1;
    static final int[] ANGLE_OPTIONS={1,4,8};
    static final String[] VIEW_NAMES_8={"Front","Front-Right","Right","Back-Right","Back","Back-Left","Left","Front-Left"};
    static final String[] VIEW_SHORT_8={"F","FR","R","BR","B","BL","L","FL"};
    static String viewName(int view,int angles){return angles<=1?"All views":VIEW_NAMES_8[(view*(8/angles))%8];}
    static String viewShort(int view,int angles){return angles<=1?"":VIEW_SHORT_8[(view*(8/angles))%8];}
    /** View index for an actor: {@code delta} is the angle from the actor's facing to the camera (0 = camera in front). */
    static int viewIndex(float delta,int angles){
        if(angles<=1)return 0;double step=Math.PI*2/angles;int k=(int)Math.round(delta/step);return ((k%angles)+angles)%angles;}
    /** The view that is the horizontal mirror of {@code view} (Right ↔ Left). */
    static int mirrorView(int view,int angles){return angles<=1?0:(angles-view)%angles;}

    static final class Clip {
        String name;float fps=8;boolean loop=true;
        /** Frames of the front view (view 0); other views live in {@link #views} with the same frame count. */
        final ArrayList<int[]> frames=new ArrayList<>();
        final ArrayList<ArrayList<int[]>> views=new ArrayList<>();
        Clip(String name){this.name=name;views.add(frames);}
        /** Frames of one view, creating blank frames so every view has as many frames as the front view. */
        ArrayList<int[]> view(int v){
            while(views.size()<=v)views.add(new ArrayList<int[]>());
            ArrayList<int[]> l=views.get(v);int len=frames.isEmpty()?0:frames.get(0).length;
            while(l.size()<frames.size())l.add(new int[len]);
            while(l.size()>frames.size()&&l!=frames)l.remove(l.size()-1);
            return l;
        }
        /** Inserts a frame at {@code i} in every view: {@code px} in view {@code v}, copies of the neighbour (dup) or blanks elsewhere. */
        void insertFrame(int i,int v,int[] px,boolean dup,int viewCount){
            for(int k=0;k<Math.max(viewCount,views.size());k++){ArrayList<int[]> l=view(k);
                int[] add=k==v?px:dup&&i>0&&i-1<l.size()?l.get(i-1).clone():new int[px.length];l.add(Math.min(i,l.size()),add);}
        }
        void removeFrame(int i){for(ArrayList<int[]> l:views)if(i<l.size())l.remove(i);}
        void trimViews(int n){while(views.size()>Math.max(1,n))views.remove(views.size()-1);}
    }
    /** Changes the number of view angles, keeping views that still exist (Front, Back, Left, Right map across 4 ↔ 8). */
    void setAngles(int n){
        if(n==angles)return;
        for(Clip c:clips){
            ArrayList<ArrayList<int[]>> old=new ArrayList<>();for(int v=0;v<angles;v++)old.add(c.view(v));
            ArrayList<ArrayList<int[]>> nv=new ArrayList<>();
            for(int v=0;v<n;v++){int dir8=(v*(8/n))%8;ArrayList<int[]> src=null;
                for(int o=0;o<angles;o++)if((o*(8/angles))%8==dir8){src=old.get(o);break;}
                nv.add(src);}
            ArrayList<int[]> front=c.frames;
            c.views.clear();c.views.add(front);
            for(int v=1;v<n;v++){ArrayList<int[]> l=new ArrayList<>();if(nv.get(v)!=null)for(int[] f:nv.get(v))l.add(f);c.views.add(l);c.view(v);}
        }
        angles=n;touch();
    }
    int[] pixels(Clip c,int view,int index){ArrayList<int[]> l=c.view(Math.max(0,Math.min(angles-1,view)));if(l.isEmpty())return null;return l.get(Math.max(0,Math.min(l.size()-1,index)));}
    CustomSprite(String id,String name,int w,int h){this.id=id;this.name=name;this.w=w;this.h=h;}

    static CustomSprite blank(String id,String name,int w,int h){
        CustomSprite s=new CustomSprite(id,name,w,h);
        for(String c:STANDARD_CLIPS){Clip clip=new Clip(c);clip.frames.add(new int[w*h]);if(c.equals("Death"))clip.loop=false;if(c.equals("Walk"))clip.fps=6;s.clips.add(clip);}
        return s;
    }
    /**
     * Copies a built-in sprite into standard clips as an editable starting point. Characters come with all
     * 8 view angles. Built-in art is rendered at 64 px per unit, so it is scaled down 2x to the editor's 32.
     */
    static CustomSprite fromBuiltin(String id,String name,int builtin){
        int[][] map={{Sprites.F_IDLE},{Sprites.F_WALK1,Sprites.F_IDLE,Sprites.F_WALK2,Sprites.F_IDLE},{Sprites.F_ATTACK},{Sprites.F_PAIN},{Sprites.F_PAIN,Sprites.F_DEAD}};
        int views=Sprites.hasAngles(builtin)?8:1;
        // canvas big enough for every frame/view, each placed bottom-centre
        int cw=1,ch=1;
        for(int[] fr:map)for(int f:fr)for(int v=0;v<views;v++){Sprites.Sprite sp=Sprites.get(builtin,f,v);int k=scale(sp);cw=Math.max(cw,(sp.w+k-1)/k);ch=Math.max(ch,(sp.h+k-1)/k);}
        cw=Math.min(128,cw+(cw&1));ch=Math.min(128,ch);
        CustomSprite s=new CustomSprite(id,name,cw,ch);
        for(int i=0;i<STANDARD_CLIPS.length;i++){Clip c=new Clip(STANDARD_CLIPS[i]);if(i==4)c.loop=false;if(i==1)c.fps=6;
            for(int f:map[i])c.frames.add(place(Sprites.get(builtin,f,0),cw,ch));
            s.clips.add(c);}
        if(views>1){s.setAngles(8);
            for(int i=0;i<STANDARD_CLIPS.length;i++){Clip c=s.clips.get(i);
                for(int v=1;v<8;v++){ArrayList<int[]> l=c.view(v);for(int k=0;k<map[i].length;k++)l.set(k,place(Sprites.get(builtin,map[i][k],v),cw,ch));}}}
        s.touch();
        return s;
    }
    private static int scale(Sprites.Sprite sp){return Math.max(1,Math.round(sp.ppu/Sprites.PPU));}
    /** Downscales (keeping the most common opaque colour of each block) and pastes bottom-centre. */
    private static int[] place(Sprites.Sprite sp,int cw,int ch){
        int k=scale(sp),sw=(sp.w+k-1)/k,sh=(sp.h+k-1)/k;int[] out=new int[cw*ch];
        int ox=(cw-sw)/2,oy=ch-sh;
        for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
            int best=0,bestN=0,opaque=0;
            for(int yy=0;yy<k;yy++)for(int xx=0;xx<k;xx++){int sx=x*k+xx,sy=y*k+yy;if(sx>=sp.w||sy>=sp.h)continue;int c=sp.px[sy*sp.w+sx];if((c>>>24)==0)continue;opaque++;
                int n=0;for(int y2=0;y2<k;y2++)for(int x2=0;x2<k;x2++){int a=x*k+x2,b=y*k+y2;if(a<sp.w&&b<sp.h&&sp.px[b*sp.w+a]==c)n++;}
                if(n>bestN){bestN=n;best=c;}}
            if(opaque*2>=k*k){int dx=ox+x,dy=oy+y;if(dx>=0&&dy>=0&&dx<cw&&dy<ch)out[dy*cw+dx]=best;}}
        return out;
    }
    Clip clip(String name){for(Clip c:clips)if(c.name.equalsIgnoreCase(name))return c;return null;}
    void touch(){version++;cache.clear();mirrorCache.clear();blank.clear();}

    // ------------------------------------------------------------------ engine lookup
    private final java.util.HashMap<int[],Sprites.Sprite> cache=new java.util.HashMap<>(),mirrorCache=new java.util.HashMap<>();
    private final java.util.HashMap<int[],Boolean> blank=new java.util.HashMap<>();
    boolean isBlank(int[] px){Boolean b=blank.get(px);if(b==null){b=true;for(int v:px)if((v>>>24)!=0){b=false;break;}blank.put(px,b);}return b;}
    private Sprites.Sprite wrapMirror(int[] px){Sprites.Sprite s=mirrorCache.get(px);if(s==null){s=new Sprites.Sprite(w,h,false);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x;if(i<px.length)s.px[y*w+(w-1-x)]=px[i];}mirrorCache.put(px,s);}return s;}
    /** Frame {@code i} of a clip seen from {@code view}: an empty view falls back to the mirrored opposite side, then to the front. */
    Sprites.Sprite resolve(Clip c,int i,int view){
        if(c==null||c.frames.isEmpty())return null;i=Math.max(0,Math.min(c.frames.size()-1,i));
        if(angles<=1||view<=0)return wrap(c.frames.get(i));
        view=Math.min(angles-1,view);int[] px=c.view(view).get(i);if(!isBlank(px))return wrap(px);
        int m=mirrorView(view,angles);if(m!=view){int[] mp=c.view(m).get(i);if(!isBlank(mp))return wrapMirror(mp);}
        return wrap(c.frames.get(i));
    }
    /** True when this view is empty and will show the mirrored opposite side in game. */
    boolean usesMirror(Clip c,int i,int view){if(angles<=1||view<=0)return false;int[] px=pixels(c,view,i);if(px==null||!isBlank(px))return false;int m=mirrorView(view,angles);return m!=view&&!isBlank(pixels(c,m,i));}
    private Sprites.Sprite wrap(int[] px){Sprites.Sprite s=cache.get(px);if(s==null){s=new Sprites.Sprite(w,h,false);System.arraycopy(px,0,s.px,0,Math.min(px.length,s.px.length));cache.put(px,s);}return s;}
    /** Frame for an engine animation state; clips fall back to Idle, then to the first clip. */
    Sprites.Sprite frameFor(int engineFrame,float time,float deadTime){return frameFor(engineFrame,time,deadTime,0);}
    Sprites.Sprite frameFor(int engineFrame,float time,float deadTime,int view){
        String want=engineFrame==Sprites.F_DEAD?"Death":engineFrame==Sprites.F_PAIN?"Pain":engineFrame==Sprites.F_ATTACK?"Attack":engineFrame==Sprites.F_WALK1||engineFrame==Sprites.F_WALK2?"Walk":"Idle";
        Clip c=clip(want);if(c==null||c.frames.isEmpty())c=clip("Idle");if((c==null||c.frames.isEmpty())&&!clips.isEmpty())c=clips.get(0);
        if(c==null||c.frames.isEmpty())return null;
        int n=c.frames.size(),i;
        if(engineFrame==Sprites.F_DEAD||!c.loop)i=Math.min(n-1,(int)(Math.max(0,engineFrame==Sprites.F_DEAD?deadTime:time)*c.fps));
        else i=(int)(time*c.fps)%n;
        return resolve(c,Math.max(0,i),view);
    }
    Sprites.Sprite frameAt(Clip c,int index){return frameAt(c,index,0);}
    Sprites.Sprite frameAt(Clip c,int index,int view){return resolve(c,index,view);}
    /** Seconds one pass of the clip takes. */
    static float length(Clip c){return c==null||c.frames.isEmpty()?0:c.frames.size()/Math.max(.1f,c.fps);}
    android.graphics.Bitmap thumbnail(){
        Clip c=clip("Idle");if(c==null||c.frames.isEmpty())c=clips.isEmpty()?null:clips.get(0);
        android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
        if(c!=null&&!c.frames.isEmpty())b.setPixels(c.frames.get(0),0,w,0,0,w,h);return b;
    }

    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("w",w);o.put("h",h);
        JSONArray cs=new JSONArray();
        if(angles>1)o.put("angles",angles);
        for(Clip c:clips){JSONObject co=new JSONObject();co.put("name",c.name);co.put("fps",c.fps);co.put("loop",c.loop);JSONArray fr=new JSONArray();for(int[] f:c.frames)fr.put(Codec.encode(f));co.put("frames",fr);
            if(angles>1){JSONArray vs=new JSONArray();for(int v=1;v<angles;v++){JSONArray va=new JSONArray();for(int[] f:c.view(v))va.put(isBlank(f)?"":Codec.encode(f));vs.put(va);}co.put("views",vs);}
            cs.put(co);}
        o.put("clips",cs);return o;
    }
    static CustomSprite fromJson(JSONObject o)throws Exception{
        CustomSprite s=new CustomSprite(o.getString("id"),o.optString("name","Sprite"),Math.max(1,Math.min(128,o.optInt("w",32))),Math.max(1,Math.min(128,o.optInt("h",32))));
        JSONArray cs=o.optJSONArray("clips");
        if(cs!=null)for(int i=0;i<cs.length();i++){JSONObject co=cs.getJSONObject(i);Clip c=new Clip(co.optString("name","Clip"));c.fps=(float)co.optDouble("fps",8);c.loop=!"false".equals(co.optString("loop","true"));
            JSONArray fr=co.optJSONArray("frames");if(fr!=null)for(int k=0;k<fr.length();k++)c.frames.add(Codec.decode(String.valueOf(fr.get(k)),s.w*s.h));
            if(c.frames.isEmpty())c.frames.add(new int[s.w*s.h]);
            JSONArray vs=co.optJSONArray("views");
            if(vs!=null)for(int v=0;v<vs.length();v++){JSONArray va=vs.getJSONArray(v);ArrayList<int[]> l=new ArrayList<>();
                for(int k=0;k<va.length();k++){String e=String.valueOf(va.get(k));l.add(e.isEmpty()?new int[s.w*s.h]:Codec.decode(e,s.w*s.h));}c.views.add(l);}
            s.clips.add(c);}
        int an=o.optInt("angles",1);s.angles=an==8?8:an==4?4:1;
        for(Clip c:s.clips){c.trimViews(s.angles);for(int v=0;v<s.angles;v++)c.view(v);}
        if(s.clips.isEmpty()){Clip c=new Clip("Idle");c.frames.add(new int[s.w*s.h]);s.clips.add(c);}
        return s;
    }
}
