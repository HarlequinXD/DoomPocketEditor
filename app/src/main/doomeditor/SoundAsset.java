package com.retro.doomeditor;

import org.json.JSONObject;
import java.util.Random;

/**
 * A project sound effect. It is either generated from synth settings (sfxr style) or built from an imported
 * sample, and then shaped by the shared edit settings (trim, pitch, volume, fades, echo, reverse).
 * {@link #render()} returns the finished 22 kHz mono clip and caches it until something changes.
 */
final class SoundAsset {
    static final int SYNTH=0,SAMPLE=1;
    static final int MAX_SECONDS=6;   // imported samples are cut to this so project files stay small

    /** One editable setting: a number slider or a list of choices. */
    static final class P {
        final String key,label,unit;final float min,max,def;final String[] options;final boolean log;
        P(String key,String label,String unit,float min,float max,float def,boolean log){this.key=key;this.label=label;this.unit=unit;this.min=min;this.max=max;this.def=def;this.log=log;options=null;}
        P(String key,String label,String options,int def){this.key=key;this.label=label;unit="";this.options=options.split("\\|");min=0;max=this.options.length-1;this.def=def;log=false;}
        boolean choice(){return options!=null;}
        float clamp(float v){if(choice())return Math.max(0,Math.min(max,Math.round(v)));if(unit.equals("st")||unit.equals("Hz")||unit.equals("bpm")||unit.equals("bars"))v=Math.round(v);return Math.max(min,Math.min(max,v));}
        /** Slider position 0..1 for a value. */
        float norm(float v){if(log)return (float)(Math.log(v/min)/Math.log(max/min));return (v-min)/(max-min);}
        float fromNorm(float t){t=Math.max(0,Math.min(1,t));return clamp(log?(float)(min*Math.pow(max/min,t)):min+(max-min)*t);}
        String format(float v){
            if(choice())return options[Math.max(0,Math.min(options.length-1,Math.round(v)))];
            switch(unit){
                case "Hz":return Math.round(v)+" Hz";
                case "bpm":return Math.round(v)+" BPM";
                case "bars":return Math.round(v)+(Math.round(v)==1?" bar":" bars");
                case "s":return String.format(java.util.Locale.US,"%.2f s",v);
                case "st":{int n=Math.round(v);return (n>0?"+":"")+n+" st";}
                case "%":return Math.round(v*100)+"%";
                case "x":return String.format(java.util.Locale.US,"%.2fx",v);
                case "/s":return v<=0?"Off":String.format(java.util.Locale.US,"%.1f /s",v);
                case "oct":return String.format(java.util.Locale.US,"%+.1f oct/s",v);
                default:return String.format(java.util.Locale.US,"%.2f",v);
            }
        }
    }
    static final String WAVES="Square|Saw|Sine|Triangle|Noise|Pulse";
    static final int S_WAVE=0,S_FREQ=1,S_SLIDE=2,S_VIB_DEPTH=3,S_VIB_SPEED=4,S_ARP=5,S_ARP_TIME=6,S_ATTACK=7,S_SUSTAIN=8,S_PUNCH=9,S_DECAY=10,
            S_DUTY=11,S_NOISE=12,S_LOWPASS=13,S_HIGHPASS=14,S_REPEAT=15,S_CRUSH=16;
    static final P[] SYNTH_PARAMS={
            new P("wave","Wave",WAVES,0),
            new P("freq","Pitch","Hz",40,2400,440,true),
            new P("slide","Pitch slide","oct",-8,8,0,false),
            new P("vibDepth","Vibrato depth","st",0,12,0,false),
            new P("vibSpeed","Vibrato speed","",0,30,6,false),
            new P("arp","Pitch jump","st",-24,24,0,false),
            new P("arpTime","Jump at","%",0,1,.5f,false),
            new P("attack","Attack","s",0,1,0,false),
            new P("sustain","Sustain","s",0,2,.15f,false),
            new P("punch","Punch","%",0,1,0,false),
            new P("decay","Decay","s",0,2,.3f,false),
            new P("duty","Square width","%",.05f,.5f,.5f,false),
            new P("noise","Noise mix","%",0,1,0,false),
            new P("lowpass","Low-pass","%",0,1,1,false),
            new P("highpass","High-pass","%",0,1,0,false),
            new P("repeat","Repeat","/s",0,20,0,false),
            new P("crush","Bit crush","%",0,1,0,false)};
    static final int E_VOLUME=0,E_PITCH=1,E_TRIM_START=2,E_TRIM_END=3,E_FADE_IN=4,E_FADE_OUT=5,E_ECHO_DELAY=6,E_ECHO_MIX=7,E_REVERSE=8,E_NORMALIZE=9;
    static final P[] EDIT_PARAMS={
            new P("volume","Volume","x",0,2,1,false),
            new P("pitch","Pitch shift","st",-24,24,0,false),
            new P("trimStart","Trim start","%",0,1,0,false),
            new P("trimEnd","Trim end","%",0,1,1,false),
            new P("fadeIn","Fade in","s",0,2,0,false),
            new P("fadeOut","Fade out","s",0,2,0,false),
            new P("echoDelay","Echo delay","s",.02f,.6f,.2f,false),
            new P("echoMix","Echo amount","%",0,.9f,0,false),
            new P("reverse","Reverse","Off|On",0),
            new P("normalize","Normalize","Off|On",0)};
    static final String[] PRESETS={"Random","Mutate","Pickup","Laser","Explosion","Power-up","Hit","Jump","Blip","Monster","Door"};

    String id,name="Sound";
    int source=SYNTH;
    final float[] synth=new float[SYNTH_PARAMS.length];
    final float[] edit=new float[EDIT_PARAMS.length];
    short[] data;            // imported sample at SoundFx.RATE
    String origin="";        // file name / built-in the sample came from
    private String dataB64;  // cached encoding of data
    private short[] cache;

    SoundAsset(){for(int i=0;i<synth.length;i++)synth[i]=SYNTH_PARAMS[i].def;resetEdits();}
    void resetEdits(){for(int i=0;i<edit.length;i++)edit[i]=EDIT_PARAMS[i].def;changed();}
    void changed(){cache=null;}
    void setData(short[] d){data=d;dataB64=null;changed();}
    float seconds(){short[] c=render();return c.length/(float)SoundFx.RATE;}

    /** The finished clip, ready to hand to the mixer. */
    short[] render(){
        if(cache!=null)return cache;
        short[] base=source==SAMPLE&&data!=null&&data.length>1?data:source==SAMPLE?new short[2]:synthesize();
        cache=applyEdits(base);return cache;
    }
    /** The unedited source (synth output or imported sample), used to draw the trim view. */
    short[] baseClip(){return source==SAMPLE?(data!=null&&data.length>1?data:new short[2]):synthesize();}

    // ------------------------------------------------------------------ edits
    private short[] applyEdits(short[] in){
        final int R=SoundFx.RATE;int n=in.length;
        int s0=(int)Math.floor(Math.min(edit[E_TRIM_START],edit[E_TRIM_END]-.001f)*n),s1=(int)Math.ceil(edit[E_TRIM_END]*n);
        s0=Math.max(0,Math.min(n-2,s0));s1=Math.max(s0+2,Math.min(n,s1));
        float rate=(float)Math.pow(2,edit[E_PITCH]/12f);
        int len=Math.max(2,Math.min(R*10,(int)((s1-s0)/rate)));
        int delay=(int)(edit[E_ECHO_DELAY]*R);float echo=edit[E_ECHO_MIX];
        int tail=echo>0?(int)(delay*Math.min(6,1+Math.log(.02)/Math.log(Math.max(.05,echo)))):0;
        int total=Math.min(R*10,len+tail);
        float[] f=new float[total];
        boolean rev=edit[E_REVERSE]>=.5f;
        for(int i=0;i<len;i++){
            float p=s0+i*rate;int ip=(int)p;if(ip>=s1-1)ip=s1-2;float fr=Math.min(1,p-ip);
            float v=in[ip]+(in[ip+1]-in[ip])*fr;
            f[rev?len-1-i:i]=v;
        }
        if(echo>0&&delay>0)for(int i=delay;i<total;i++)f[i]+=f[i-delay]*echo;
        int fi=(int)(edit[E_FADE_IN]*R),fo=(int)(edit[E_FADE_OUT]*R);
        if(fi>0)for(int i=0;i<Math.min(fi,total);i++)f[i]*=i/(float)fi;
        if(fo>0)for(int i=0;i<Math.min(fo,total);i++)f[total-1-i]*=i/(float)fo;
        float g=edit[E_VOLUME];
        if(edit[E_NORMALIZE]>=.5f){float peak=1;for(float v:f)peak=Math.max(peak,Math.abs(v));g*=30000f/peak;}
        short[] out=new short[total];
        for(int i=0;i<total;i++)out[i]=(short)Math.max(-32767,Math.min(32767,f[i]*g));
        return out;
    }

    // ------------------------------------------------------------------ synth
    private short[] synthesize(){
        final int R=SoundFx.RATE;float[] s=synth;
        int wave=Math.round(s[S_WAVE]);
        float attack=s[S_ATTACK],sustain=s[S_SUSTAIN],decay=s[S_DECAY];
        float total=Math.max(.02f,attack+sustain+decay);
        int n=(int)(total*R);short[] out=new short[n];
        Random rnd=new Random(12345);
        double phase=0,vibPhase=0;float lp=0,hp=0,noiseVal=rnd.nextFloat()*2-1,held=0;int holdLeft=0;
        float lpCoef=s[S_LOWPASS]*s[S_LOWPASS],hpCoef=s[S_HIGHPASS]*s[S_HIGHPASS]*.25f;
        float repeat=s[S_REPEAT],repeatLen=repeat>0?1f/repeat:1e9f,local=0;
        int hold=1+(int)(s[S_CRUSH]*24);float levels=(float)Math.pow(2,16-s[S_CRUSH]*12);
        for(int i=0;i<n;i++){
            float t=i/(float)R;
            local+=1f/R;if(local>=repeatLen)local-=repeatLen;
            double f=s[S_FREQ]*Math.pow(2,s[S_SLIDE]*local);
            if(s[S_ARP]!=0&&local>=s[S_ARP_TIME]*Math.min(total,repeatLen))f*=Math.pow(2,s[S_ARP]/12.0);
            if(s[S_VIB_DEPTH]>0){vibPhase+=s[S_VIB_SPEED]/R;f*=Math.pow(2,s[S_VIB_DEPTH]/12.0*Math.sin(vibPhase*2*Math.PI));}
            f=Math.max(20,Math.min(9000,f));
            phase+=f/R;if(phase>=1){phase-=Math.floor(phase);noiseVal=rnd.nextFloat()*2-1;}
            float ph=(float)phase,v;
            switch(wave){
                case 1:v=ph*2-1;break;
                case 2:v=(float)Math.sin(ph*2*Math.PI);break;
                case 3:v=ph<.5f?ph*4-1:3-ph*4;break;
                case 4:v=noiseVal;break;
                case 5:v=ph<s[S_DUTY]*.5f?1:-1;break;
                default:v=ph<s[S_DUTY]?1:-1;
            }
            if(s[S_NOISE]>0)v=v*(1-s[S_NOISE])+(rnd.nextFloat()*2-1)*s[S_NOISE];
            float env;
            if(t<attack)env=t/attack;
            else if(t<attack+sustain)env=1+s[S_PUNCH]*(1-(t-attack)/Math.max(.0001f,sustain));
            else env=Math.max(0,1-(t-attack-sustain)/Math.max(.0001f,decay));
            if(lpCoef<1){lp+=(v-lp)*Math.max(.002f,lpCoef);v=lp;}
            if(hpCoef>0){hp+=(v-hp)*hpCoef;v=v-hp;}
            v*=env*.55f;
            if(hold>1){if(holdLeft<=0){held=Math.round(v*levels)/levels;holdLeft=hold;}holdLeft--;v=held;}
            out[i]=(short)Math.max(-32767,Math.min(32767,v*32767));
        }
        return out;
    }
    /** Fills the synth settings with a style of sound, like sfxr's generator buttons. */
    void preset(int which,Random r){
        if(which==1){mutate(r);return;}
        for(int i=0;i<synth.length;i++)synth[i]=SYNTH_PARAMS[i].def;
        float[] s=synth;
        switch(which){
            case 0:for(int i=0;i<s.length;i++){P p=SYNTH_PARAMS[i];s[i]=p.fromNorm(r.nextFloat());}
                s[S_LOWPASS]=.4f+r.nextFloat()*.6f;s[S_HIGHPASS]*=.3f;s[S_ATTACK]*=.3f;s[S_SUSTAIN]=.05f+r.nextFloat()*.4f;s[S_DECAY]=.1f+r.nextFloat()*.6f;s[S_CRUSH]*=.5f;s[S_REPEAT]=r.nextFloat()<.7f?0:s[S_REPEAT];break;
            case 2:s[S_WAVE]=r.nextBoolean()?0:2;s[S_FREQ]=700+r.nextFloat()*900;s[S_SUSTAIN]=.05f+r.nextFloat()*.1f;s[S_DECAY]=.15f+r.nextFloat()*.25f;s[S_PUNCH]=.3f+r.nextFloat()*.4f;
                if(r.nextBoolean()){s[S_ARP]=r.nextBoolean()?5:7;s[S_ARP_TIME]=.2f+r.nextFloat()*.3f;}break;
            case 3:s[S_WAVE]=r.nextInt(3)==0?1:0;s[S_FREQ]=600+r.nextFloat()*1500;s[S_SLIDE]=-(3+r.nextFloat()*5);s[S_SUSTAIN]=.05f+r.nextFloat()*.15f;s[S_DECAY]=.05f+r.nextFloat()*.2f;s[S_DUTY]=.1f+r.nextFloat()*.4f;
                if(r.nextBoolean())s[S_HIGHPASS]=r.nextFloat()*.3f;break;
            case 4:s[S_WAVE]=4;s[S_FREQ]=60+r.nextFloat()*300;s[S_SLIDE]=-(r.nextFloat()*2);s[S_SUSTAIN]=.1f+r.nextFloat()*.3f;s[S_DECAY]=.4f+r.nextFloat()*.9f;s[S_PUNCH]=.3f+r.nextFloat()*.5f;
                s[S_LOWPASS]=.3f+r.nextFloat()*.5f;if(r.nextInt(3)==0)s[S_REPEAT]=4+r.nextFloat()*8;break;
            case 5:s[S_WAVE]=r.nextBoolean()?0:1;s[S_FREQ]=200+r.nextFloat()*400;s[S_SLIDE]=1+r.nextFloat()*3;s[S_SUSTAIN]=.2f+r.nextFloat()*.3f;s[S_DECAY]=.2f+r.nextFloat()*.4f;
                if(r.nextBoolean()){s[S_VIB_DEPTH]=1+r.nextFloat()*2;s[S_VIB_SPEED]=8+r.nextFloat()*10;}else s[S_REPEAT]=5+r.nextFloat()*10;break;
            case 6:s[S_WAVE]=r.nextBoolean()?4:1;s[S_FREQ]=150+r.nextFloat()*500;s[S_SLIDE]=-(2+r.nextFloat()*4);s[S_SUSTAIN]=.02f+r.nextFloat()*.05f;s[S_DECAY]=.08f+r.nextFloat()*.15f;s[S_PUNCH]=.4f;
                if(r.nextBoolean())s[S_HIGHPASS]=r.nextFloat()*.4f;break;
            case 7:s[S_WAVE]=0;s[S_DUTY]=.2f+r.nextFloat()*.3f;s[S_FREQ]=250+r.nextFloat()*350;s[S_SLIDE]=1.5f+r.nextFloat()*2.5f;s[S_SUSTAIN]=.05f+r.nextFloat()*.15f;s[S_DECAY]=.1f+r.nextFloat()*.2f;break;
            case 8:s[S_WAVE]=r.nextInt(2)==0?0:5;s[S_FREQ]=500+r.nextFloat()*1200;s[S_SUSTAIN]=.02f+r.nextFloat()*.06f;s[S_DECAY]=.02f+r.nextFloat()*.06f;s[S_HIGHPASS]=r.nextFloat()*.2f;break;
            case 9:s[S_WAVE]=r.nextBoolean()?1:0;s[S_FREQ]=70+r.nextFloat()*120;s[S_SLIDE]=-(r.nextFloat()*1.2f);s[S_VIB_DEPTH]=2+r.nextFloat()*4;s[S_VIB_SPEED]=10+r.nextFloat()*15;
                s[S_NOISE]=.2f+r.nextFloat()*.4f;s[S_ATTACK]=.03f;s[S_SUSTAIN]=.2f+r.nextFloat()*.3f;s[S_DECAY]=.3f+r.nextFloat()*.4f;s[S_LOWPASS]=.4f+r.nextFloat()*.3f;s[S_CRUSH]=r.nextFloat()*.3f;break;
            case 10:s[S_WAVE]=4;s[S_FREQ]=40+r.nextFloat()*60;s[S_NOISE]=.3f;s[S_ATTACK]=.08f;s[S_SUSTAIN]=.3f+r.nextFloat()*.3f;s[S_DECAY]=.2f;s[S_LOWPASS]=.15f+r.nextFloat()*.2f;
                s[S_VIB_DEPTH]=3;s[S_VIB_SPEED]=12+r.nextFloat()*6;break;
        }
        for(int i=0;i<s.length;i++)s[i]=SYNTH_PARAMS[i].clamp(s[i]);
        changed();
    }
    void mutate(Random r){
        for(int i=1;i<synth.length;i++){if(r.nextInt(3)!=0)continue;P p=SYNTH_PARAMS[i];synth[i]=p.fromNorm(p.norm(synth[i])+(r.nextFloat()-.5f)*.1f);}
        changed();
    }

    // ------------------------------------------------------------------ json
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("source",source==SAMPLE?"sample":"synth");
        JSONObject s=new JSONObject();for(int i=0;i<synth.length;i++)s.put(SYNTH_PARAMS[i].key,(double)synth[i]);o.put("synth",s);
        JSONObject e=new JSONObject();for(int i=0;i<edit.length;i++)e.put(EDIT_PARAMS[i].key,(double)edit[i]);o.put("edit",e);
        if(data!=null){if(dataB64==null)dataB64=encode(data);o.put("pcm",dataB64);o.put("origin",origin);}
        return o;
    }
    static SoundAsset fromJson(JSONObject o){
        SoundAsset a=new SoundAsset();a.id=o.optString("id","sound");a.name=o.optString("name","Sound");
        a.source=o.optString("source","synth").equals("sample")?SAMPLE:SYNTH;
        JSONObject s=o.optJSONObject("synth");if(s!=null)for(int i=0;i<a.synth.length;i++){P p=SYNTH_PARAMS[i];a.synth[i]=p.clamp((float)s.optDouble(p.key,p.def));}
        JSONObject e=o.optJSONObject("edit");if(e!=null)for(int i=0;i<a.edit.length;i++){P p=EDIT_PARAMS[i];a.edit[i]=p.clamp((float)e.optDouble(p.key,p.def));}
        String pcm=o.optString("pcm","");if(!pcm.isEmpty()){try{a.data=decode(pcm);a.dataB64=pcm;}catch(Exception ignored){}}
        a.origin=o.optString("origin","");
        return a;
    }
    SoundAsset copy(){try{return fromJson(toJson());}catch(Exception e){return new SoundAsset();}}
    static String encode(short[] d){byte[] b=new byte[d.length*2];for(int i=0;i<d.length;i++){b[i*2]=(byte)d[i];b[i*2+1]=(byte)(d[i]>>8);}return android.util.Base64.encodeToString(b,android.util.Base64.NO_WRAP);}
    static short[] decode(String s){byte[] b=android.util.Base64.decode(s,android.util.Base64.NO_WRAP);short[] d=new short[b.length/2];for(int i=0;i<d.length;i++)d[i]=(short)((b[i*2]&255)|(b[i*2+1]<<8));return d;}
}
