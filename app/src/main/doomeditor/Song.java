package com.retro.doomeditor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * A piece of music: a step sequencer with a few tracks, each with an instrument and a list of notes.
 * 16 steps make one bar (four steps per beat). {@link #compile} turns it into fixed arrays for the mixer.
 */
final class Song {
    static final int STEPS_PER_BAR=16,STEPS_PER_BEAT=4,MIN_NOTE=24,MAX_NOTE=96,MAX_TRACKS=6;
    static final String INSTRUMENTS="Square|Pulse|Saw|Triangle|Sine|Noise|Custom sound|Drum kit";
    static final int I_SQUARE=0,I_PULSE=1,I_SAW=2,I_TRI=3,I_SINE=4,I_NOISE=5,I_SAMPLE=6,I_DRUMS=7;
    static final String[] DRUMS={"Kick","Snare","Closed hat","Open hat","Clap","Low tom","High tom","Crash"};
    static final String[] NOTE_NAMES={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
    static String noteName(int midi){return NOTE_NAMES[((midi%12)+12)%12]+(midi/12-1);}

    static final SoundAsset.P[] SONG_PARAMS={
            new SoundAsset.P("bpm","Tempo","bpm",40,300,120,false),
            new SoundAsset.P("bars","Length","bars",1,16,2,false),
            new SoundAsset.P("volume","Song volume","%",0,1,.8f,false)};
    static final SoundAsset.P[] TRACK_PARAMS={
            new SoundAsset.P("inst","Instrument",INSTRUMENTS,0),
            new SoundAsset.P("vol","Track volume","%",0,1,.6f,false),
            new SoundAsset.P("attack","Attack","s",0,1,.005f,false),
            new SoundAsset.P("decay","Decay","s",0,2,.2f,false),
            new SoundAsset.P("sustain","Sustain level","%",0,1,.55f,false),
            new SoundAsset.P("release","Release","s",0,2,.08f,false)};
    static final int T_INST=0,T_VOL=1,T_ATTACK=2,T_DECAY=3,T_SUSTAIN=4,T_RELEASE=5;

    static final class Note {int step,pitch,len;Note(int s,int p,int l){step=s;pitch=p;len=l;}}
    static final class Track {
        String name="Track";String sound="";boolean mute;
        final float[] v=new float[TRACK_PARAMS.length];
        final ArrayList<Note> notes=new ArrayList<>();
        Track(String name,int inst){this.name=name;for(int i=0;i<v.length;i++)v[i]=TRACK_PARAMS[i].def;v[T_INST]=inst;
            if(inst==I_DRUMS){v[T_VOL]=.8f;v[T_RELEASE]=.05f;}}
        int inst(){return Math.round(v[T_INST]);}
        boolean drums(){return inst()==I_DRUMS;}
        Note noteAt(int step,int pitch){for(Note n:notes)if(n.pitch==pitch&&step>=n.step&&step<n.step+n.len)return n;return null;}
        Note add(int step,int pitch,int len){Note n=new Note(step,pitch,len);notes.add(n);return n;}
    }

    String id,name="Song";
    final float[] v=new float[SONG_PARAMS.length];
    final ArrayList<Track> tracks=new ArrayList<>();

    Song(){for(int i=0;i<v.length;i++)v[i]=SONG_PARAMS[i].def;}
    int bpm(){return Math.round(v[0]);}
    int bars(){return Math.round(v[1]);}
    int steps(){return bars()*STEPS_PER_BAR;}
    float lengthSeconds(){return steps()*60f/(bpm()*STEPS_PER_BEAT);}
    int noteCount(){int n=0;for(Track t:tracks)n+=t.notes.size();return n;}

    /** Empty song with the standard four tracks. */
    static Song blank(String name){
        Song s=new Song();s.name=name;
        s.tracks.add(new Track("Lead",I_SQUARE));
        Track bass=new Track("Bass",I_SAW);bass.v[T_DECAY]=.15f;bass.v[T_SUSTAIN]=.45f;s.tracks.add(bass);
        Track pad=new Track("Pad",I_TRI);pad.v[T_ATTACK]=.12f;pad.v[T_SUSTAIN]=.8f;pad.v[T_RELEASE]=.4f;pad.v[T_VOL]=.4f;s.tracks.add(pad);
        s.tracks.add(new Track("Drums",I_DRUMS));
        return s;
    }
    /** A short original riff so a new project has something to hear straight away. */
    static Song starter(String name){
        Song s=blank(name);s.v[0]=138;s.v[1]=2;
        Track lead=s.tracks.get(0),bass=s.tracks.get(1),pad=s.tracks.get(2),dr=s.tracks.get(3);
        int[] riff={0,40,3, 3,40,1, 4,43,2, 6,40,2, 8,45,3, 11,43,1, 12,42,2, 14,38,2};
        for(int bar=0;bar<2;bar++)for(int i=0;i<riff.length;i+=3){int p=riff[i+1];if(bar==1&&i==riff.length-3)p=35;bass.add(bar*16+riff[i],p,riff[i+2]);}
        int[] mel={16,59,4, 20,57,4, 24,55,6, 30,54,2};
        for(int i=0;i<mel.length;i+=3)lead.add(mel[i],mel[i+1],mel[i+2]);
        pad.add(0,52,16);pad.add(0,55,16);pad.add(16,50,16);pad.add(16,54,16);
        for(int bar=0;bar<2;bar++){int o=bar*16;
            for(int k:new int[]{0,3,8,10})dr.add(o+k,0,1);
            dr.add(o+4,1,1);dr.add(o+12,1,1);
            for(int k=0;k<16;k+=2)dr.add(o+k,2,1);}
        dr.add(30,4,1);dr.add(0,7,1);
        return s;
    }

    // ------------------------------------------------------------------ playback data
    static final class Inst {int type;float vol,attack,decay,sustain,release;short[] sample;}
    static final class Compiled {
        double samplesPerStep;int length;Inst[] inst;int[][] events;float volume;
    }
    static Inst instFor(Track t,Project p){
        Inst in=new Inst();in.type=t.inst();in.vol=t.v[T_VOL];in.attack=t.v[T_ATTACK];in.decay=t.v[T_DECAY];in.sustain=t.v[T_SUSTAIN];in.release=t.v[T_RELEASE];
        if(in.type==I_SAMPLE){SoundAsset a=p==null?null:p.sound(t.sound);if(a!=null)in.sample=a.render();else in.type=I_SQUARE;}
        return in;
    }
    Compiled compile(Project p){
        Compiled c=new Compiled();int n=steps();c.length=n;c.volume=v[2];
        c.samplesPerStep=SoundFx.RATE*60.0/(bpm()*STEPS_PER_BEAT);
        c.inst=new Inst[tracks.size()];for(int i=0;i<tracks.size();i++)c.inst[i]=instFor(tracks.get(i),p);
        int[] count=new int[n];
        for(int t=0;t<tracks.size();t++){Track tr=tracks.get(t);if(tr.mute)continue;for(Note no:tr.notes)if(no.step>=0&&no.step<n)count[no.step]++;}
        c.events=new int[n][];for(int i=0;i<n;i++)c.events[i]=new int[count[i]*3];
        int[] fill=new int[n];
        for(int t=0;t<tracks.size();t++){Track tr=tracks.get(t);if(tr.mute)continue;
            for(Note no:tr.notes){if(no.step<0||no.step>=n)continue;int[] e=c.events[no.step];int k=fill[no.step];e[k]=t;e[k+1]=no.pitch;e[k+2]=Math.max(1,no.len);fill[no.step]+=3;}}
        return c;
    }

    // ------------------------------------------------------------------ json
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);
        for(int i=0;i<v.length;i++)o.put(SONG_PARAMS[i].key,(double)v[i]);
        JSONArray ts=new JSONArray();
        for(Track t:tracks){JSONObject j=new JSONObject();j.put("name",t.name);j.put("sound",t.sound);j.put("mute",t.mute);
            for(int i=0;i<t.v.length;i++)j.put(TRACK_PARAMS[i].key,(double)t.v[i]);
            JSONArray ns=new JSONArray();for(Note n:t.notes){ns.put(n.step);ns.put(n.pitch);ns.put(n.len);}j.put("notes",ns);ts.put(j);}
        o.put("tracks",ts);return o;
    }
    static Song fromJson(JSONObject o){
        Song s=new Song();s.id=o.optString("id","song");s.name=o.optString("name","Song");
        for(int i=0;i<s.v.length;i++){SoundAsset.P p=SONG_PARAMS[i];s.v[i]=p.clamp((float)o.optDouble(p.key,p.def));}
        JSONArray ts=o.optJSONArray("tracks");
        if(ts!=null)for(int i=0;i<ts.length()&&s.tracks.size()<MAX_TRACKS;i++){JSONObject j=ts.optJSONObject(i);if(j==null)continue;
            Track t=new Track(j.optString("name","Track"),0);t.sound=j.optString("sound","");t.mute=j.optBoolean("mute",false);
            for(int k=0;k<t.v.length;k++){SoundAsset.P p=TRACK_PARAMS[k];t.v[k]=p.clamp((float)j.optDouble(p.key,p.def));}
            JSONArray ns=j.optJSONArray("notes");if(ns!=null)for(int k=0;k+2<ns.length();k+=3)t.add(ns.optInt(k),ns.optInt(k+1),Math.max(1,ns.optInt(k+2,1)));
            s.tracks.add(t);}
        if(s.tracks.isEmpty())s.tracks.addAll(blank("x").tracks);
        return s;
    }
    Song copy(){try{return fromJson(toJson());}catch(Exception e){return blank(name);}}
}
