package com.retro.doomeditor;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Software mixer on one AudioTrack: up to 12 sound effect voices (built-in Doom-like sounds, project
 * sounds and overrides of the built-ins) plus a music sequencer that plays {@link Song}s live.
 */
final class SoundFx {
    static final int PISTOL=0,SHOTGUN=1,CHAINGUN=2,ROCKET=3,EXPLODE=4,PUNCH=5,DOOR=6,SWITCH=7,PICKUP=8,WEAPON_PICKUP=9,ITEM=10,
            PLAYER_PAIN=11,PLAYER_DEATH=12,MONSTER_SIGHT=13,MONSTER_PAIN=14,MONSTER_DEATH=15,FIREBALL=16,TELEPORT=17,SECRET=18,OOF=19,BEEP=20,
            SWING=21,HIT=22,BOW=23,SPELL=24,HEAL=25,COIN=26,LEVEL_UP=27,POTION=28,COUNT=29;
    /** Built-in sound names, in id order. Also the values FSM sound parameters store for built-ins. */
    static final String[] NAMES=Actions.SOUNDS.split("\\|");
    static final int RATE=22050;
    private static final int VOICES=12,MVOICES=24,BUF=512,IDLE_BUFFERS=RATE/2/BUF;   // ~0.5 s of silence before pausing
    private static final short[][] BUILTIN=new short[COUNT][],DRUM=new short[Song.DRUMS.length][];
    private static final float[] SINE=new float[2048];
    static{for(int i=0;i<SINE.length;i++)SINE[i]=(float)Math.sin(i*2*Math.PI/SINE.length);}
    static synchronized short[] builtin(int id){if(id<0||id>=COUNT)return null;if(BUILTIN[id]==null)BUILTIN[id]=synth(id);return BUILTIN[id];}
    static synchronized short[] drum(int i){i=Math.max(0,Math.min(DRUM.length-1,i));if(DRUM[i]==null)DRUM[i]=drumSynth(i);return DRUM[i];}
    static int builtinIndex(String name){for(int i=0;i<COUNT;i++)if(NAMES[i].equalsIgnoreCase(name))return i;return -1;}

    private final short[][] override=new short[COUNT][];
    private final short[][] voice=new short[VOICES][];
    private final float[] pos=new float[VOICES],rate=new float[VOICES],vol=new float[VOICES];
    private final int[] vid=new int[VOICES];
    private int serial;
    float sfxVolume=1f,musicVolume=.8f;
    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;

    // music state (guarded by this)
    private static final class MVoice {boolean on;int track,type,stage,noiseHold,pitch;long gate,age;double phase,inc,spos,srate;float env,vol,atk,dec,sus,rel,noise;short[] smp;}
    private final MVoice[] mv=new MVoice[MVOICES];
    private Song.Compiled song;
    private boolean musicOn,musicLoop;
    private int step;private double stepLeft;private long age;
    private float fade=1,fadeTarget=1,fadeStep;
    private int rnd=0x2545F491;
    /** Song id the game last started, so a map change can keep the same music playing. */
    String musicTag="";
    volatile long musicTicks;
    volatile int musicStep=-1;

    SoundFx(){for(int i=0;i<MVOICES;i++)mv[i]=new MVoice();}

    // ------------------------------------------------------------------ sound effects
    /** Uses the project's replacements for built-in sounds and its volume settings. */
    synchronized void useProject(Project p){
        for(int i=0;i<COUNT;i++){SoundAsset a=p==null?null:p.sound(p.soundOverride(NAMES[i]));override[i]=a!=null?a.render():null;}
        if(p!=null){sfxVolume=p.sfxVolume*GameSettings.soundVolume;musicVolume=p.musicVolume*GameSettings.musicVolume;}   // project mix x player's Options
    }
    short[] clip(int id){if(id<0||id>=COUNT)return null;short[] o=override[id];return o!=null?o:builtin(id);}
    void play(int id,float volume){play(clip(id),volume,1f);}
    /** Plays a clip; returns a handle for {@link #playing}, or 0 if it could not play. */
    synchronized int play(short[] clip,float volume,float speed){
        if(clip==null||clip.length<2||!running)return 0;
        int slot=0;float best=-1;
        for(int i=0;i<VOICES;i++){if(voice[i]==null){slot=i;best=2;break;}float f=pos[i]/voice[i].length;if(f>best){best=f;slot=i;}}
        voice[slot]=clip;pos[slot]=0;rate[slot]=Math.max(.1f,Math.min(8,speed));vol[slot]=Math.max(0,Math.min(2,volume));vid[slot]=++serial;
        notifyAll();   // wake the mixer if it is idling
        return serial;
    }
    synchronized boolean playing(int handle){if(handle==0)return false;for(int i=0;i<VOICES;i++)if(voice[i]!=null&&vid[i]==handle)return true;return false;}
    synchronized boolean anySoundPlaying(){for(short[] v:voice)if(v!=null)return true;return false;}
    synchronized void stopSounds(){for(int i=0;i<VOICES;i++)voice[i]=null;}
    /** Position (0..1) of the newest voice playing this clip, or -1; drives the editor's playhead. */
    synchronized float progress(short[] clip){float p=-1;int best=-1;for(int i=0;i<VOICES;i++)if(voice[i]==clip&&vid[i]>best){best=vid[i];p=pos[i]/clip.length;}return p;}

    // ------------------------------------------------------------------ music
    synchronized void playMusic(Song.Compiled c,boolean loop,float fadeIn){
        if(c==null||c.length<=0){stopMusic(0);return;}
        killMusicVoices();song=c;musicLoop=loop;musicOn=true;step=0;stepLeft=0;musicTicks=0;musicStep=-1;
        fadeTarget=1;if(fadeIn>0){fade=0;fadeStep=1f/(fadeIn*RATE);}else fade=1;
        notifyAll();
    }
    /** Swaps in an edited version of the playing song without restarting it. */
    synchronized void updateMusic(Song.Compiled c){if(c==null)return;song=c;if(step>=c.length)step=0;}
    synchronized void stopMusic(float fadeOut){
        if(fadeOut<=0||!musicOn){musicOn=false;song=null;musicStep=-1;killMusicVoices();return;}
        fadeTarget=0;fadeStep=1f/(fadeOut*RATE);
    }
    synchronized void seekMusic(int s){if(song!=null){step=Math.max(0,Math.min(song.length-1,s));stepLeft=0;releaseAll();}}
    synchronized boolean musicPlaying(){return musicOn;}
    synchronized void setMusicVolume(float v){musicVolume=Math.max(0,Math.min(1,v));}
    /** Plays one note of an instrument (piano roll preview). */
    synchronized void note(Song.Inst in,int pitch,float seconds){
        if(in==null||!running)return;
        start(in,-1,pitch,(long)(seconds*RATE));notifyAll();
    }
    private void killMusicVoices(){for(MVoice v:mv)v.on=false;}
    private void releaseAll(){for(MVoice v:mv)if(v.on)v.gate=Math.min(v.gate,1);}
    private void trigger(Song.Compiled s,int st){
        int[] e=s.events[st];
        for(int k=0;k+2<e.length;k+=3){int t=e[k];if(t<0||t>=s.inst.length)continue;start(s.inst[t],t,e[k+1],(long)(e[k+2]*s.samplesPerStep*.92));}
    }
    private void start(Song.Inst in,int track,int pitch,long gate){
        MVoice v=null;
        if(track>=0)for(MVoice x:mv)if(x.on&&x.track==track&&x.pitch==pitch){v=x;break;}   // retrigger the same note
        if(v==null)for(MVoice x:mv)if(!x.on){v=x;break;}
        if(v==null){float low=9;for(MVoice x:mv)if(x.stage==3&&x.env<low){low=x.env;v=x;}}
        if(v==null){long oldest=Long.MAX_VALUE;for(MVoice x:mv)if(x.age<oldest){oldest=x.age;v=x;}}
        v.on=true;v.track=track;v.pitch=pitch;v.type=in.type;v.age=++age;v.vol=in.vol;v.phase=0;v.spos=0;v.noiseHold=0;
        v.srate=Math.pow(2,(pitch-60)/12.0);
        double f=440*Math.pow(2,(pitch-69)/12.0);v.inc=f/RATE;
        if(in.type==Song.I_DRUMS){v.smp=drum(pitch);v.srate=1;v.gate=v.smp.length;v.env=1;v.stage=2;v.sus=1;v.atk=1;v.dec=0;v.rel=1;return;}
        v.smp=in.type==Song.I_SAMPLE?in.sample:null;
        v.gate=Math.max(1,gate);v.env=0;v.stage=0;v.sus=in.sustain;
        v.atk=1f/Math.max(1,Math.max(.002f,in.attack)*RATE);
        v.dec=(1-in.sustain)/Math.max(1,in.decay*RATE);
        v.rel=1f/Math.max(1,Math.max(.005f,in.release)*RATE);
    }
    private boolean musicActive(){if(musicOn)return true;for(MVoice v:mv)if(v.on)return true;return false;}
    private void renderMusic(float[] mix){
        float master=musicVolume*32767f*.33f;
        for(int i=0;i<BUF;i++){
            Song.Compiled s=song;
            if(musicOn&&s!=null){
                if(stepLeft<=0){
                    if(step>=s.length){if(musicLoop)step=0;else{musicOn=false;musicStep=-1;}}
                    if(musicOn){trigger(s,step);musicStep=step;musicTicks++;step++;stepLeft+=s.samplesPerStep;}
                }
                stepLeft-=1;
            }
            if(fade!=fadeTarget){
                if(fade<fadeTarget)fade=Math.min(fadeTarget,fade+fadeStep);else fade=Math.max(fadeTarget,fade-fadeStep);
                if(fade<=0&&fadeTarget==0){musicOn=false;song=null;musicStep=-1;killMusicVoices();fade=1;fadeTarget=1;}
            }
            float out=0;
            for(int k=0;k<MVOICES;k++){MVoice v=mv[k];if(!v.on)continue;
                float w;
                if(v.smp!=null){int ip=(int)v.spos;if(ip>=v.smp.length-1){v.on=false;continue;}float fr=(float)(v.spos-ip);w=(v.smp[ip]+(v.smp[ip+1]-v.smp[ip])*fr)*(1f/32768f);v.spos+=v.srate;}
                else{
                    v.phase+=v.inc;if(v.phase>=1){v.phase-=1;if(v.type==Song.I_NOISE){rnd^=rnd<<13;rnd^=rnd>>>17;rnd^=rnd<<5;v.noise=(rnd&0xffff)/32768f-1;}}
                    float ph=(float)v.phase;
                    switch(v.type){
                        case Song.I_PULSE:w=ph<.25f?1:-1;break;
                        case Song.I_SAW:w=ph*2-1;break;
                        case Song.I_TRI:w=ph<.5f?ph*4-1:3-ph*4;break;
                        case Song.I_SINE:w=SINE[(int)(ph*2048)&2047];break;
                        case Song.I_NOISE:w=v.noise;break;
                        default:w=ph<.5f?1:-1;
                    }
                    w*=.7f;
                }
                if(v.type!=Song.I_DRUMS){
                    if(v.stage==0){v.env+=v.atk;if(v.env>=1){v.env=1;v.stage=1;}}
                    else if(v.stage==1){v.env-=v.dec;if(v.env<=v.sus){v.env=v.sus;v.stage=2;}}
                    if(v.stage<3&&--v.gate<=0)v.stage=3;
                    if(v.stage==3){v.env-=v.rel;if(v.env<=0){v.on=false;continue;}}
                }
                out+=w*v.env*v.vol;
            }
            float sv=s!=null?s.volume:1;
            mix[i]+=out*master*sv*fade;
        }
    }

    /** Renders a song once, without an audio device (WAV export). */
    static short[] offline(Song.Compiled c,int len){
        SoundFx f=new SoundFx();f.musicVolume=1;f.song=c;f.musicOn=true;f.musicLoop=false;
        float[] mix=new float[BUF];short[] out=new short[len];int end=0;
        synchronized(f){
            for(int o=0;o<len;o+=BUF){java.util.Arrays.fill(mix,0);f.renderMusic(mix);
                for(int i=0;i<BUF&&o+i<len;i++){float m=mix[i]*.7f;out[o+i]=(short)Math.max(-32767,Math.min(32767,m));if(Math.abs(out[o+i])>8)end=o+i+1;}
                if(!f.musicActive())break;}
        }
        return java.util.Arrays.copyOf(out,Math.max(2,end));
    }

    // ------------------------------------------------------------------ output
    void start(){
        if(running)return;
        try{
            int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            track=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,BUF*4),AudioTrack.MODE_STREAM);
            track.play();running=true;
            thread=new Thread(this::loop,"SoundFx");thread.setDaemon(true);thread.start();
        }catch(Throwable t){running=false;track=null;}
    }
    void stop(){
        running=false;synchronized(this){notifyAll();}
        try{if(thread!=null)thread.join(200);}catch(InterruptedException ignored){}
        try{if(track!=null){track.stop();track.release();}}catch(Throwable ignored){}
        track=null;thread=null;
    }
    /**
     * Mixes active voices and music into the stream. When nothing has played for about half a second the
     * track is paused and the thread sleeps until the next sound, so the audio hardware can power down.
     */
    private void loop(){
        short[] out=new short[BUF];float[] mix=new float[BUF];int silent=0;boolean paused=false;
        while(running){
            synchronized(this){
                boolean any=musicActive();if(!any)for(int v=0;v<VOICES;v++)if(voice[v]!=null){any=true;break;}
                if(!any){
                    if(++silent>=IDLE_BUFFERS){
                        if(!paused){try{track.pause();track.flush();}catch(Throwable ignored){}paused=true;}
                        try{wait(250);}catch(InterruptedException e){return;}
                        continue;
                    }
                }else silent=0;
                if(paused&&any){try{track.play();}catch(Throwable ignored){}paused=false;}
                java.util.Arrays.fill(mix,0);
                for(int v=0;v<VOICES;v++){short[] c=voice[v];if(c==null)continue;
                    float p=pos[v],r=rate[v],g=vol[v]*sfxVolume;int n=c.length;
                    for(int i=0;i<BUF;i++){int ip=(int)p;if(ip>=n-1){voice[v]=null;break;}float fr=p-ip;mix[i]+=(c[ip]+(c[ip+1]-c[ip])*fr)*g;p+=r;}
                    pos[v]=p;}
                if(musicActive())renderMusic(mix);
                for(int i=0;i<BUF;i++){float m=mix[i]*.7f;out[i]=(short)Math.max(-32767,Math.min(32767,m));}
            }
            try{track.write(out,0,BUF);}catch(Throwable t){running=false;}
        }
    }
    private static short[] drumSynth(int d){
        java.util.Random r=new java.util.Random(d*97+3);
        float len=d==7?1.3f:d==3||d==0||d==5?.4f:d==2?.07f:.28f;int n=(int)(RATE*len);short[] s=new short[n];
        float phase=0,lp=0,hp=0,prev=0;
        for(int i=0;i<n;i++){float t=i/(float)RATE,noise=r.nextFloat()*2-1,v;
            hp=.92f*(hp+noise-prev);prev=noise;   // simple high-pass for cymbals
            switch(d){
                case 0:{float f=45+110*(float)Math.exp(-t*30);phase+=f/RATE;v=(float)Math.sin(phase*2*Math.PI)*(float)Math.exp(-t*7)*1.1f;if(t<.004f)v+=noise*.4f;break;}
                case 1:{phase+=190f/RATE;lp+=(noise-lp)*.5f;v=(float)Math.sin(phase*2*Math.PI)*(float)Math.exp(-t*25)*.5f+lp*(float)Math.exp(-t*14)*.8f;break;}
                case 2:v=hp*(float)Math.exp(-t*60)*.6f;break;
                case 3:v=hp*(float)Math.exp(-t*7)*.5f;break;
                case 4:{float burst=(t%.012f<.006f&&t<.036f)?1:t>=.036f?(float)Math.exp(-(t-.036f)*16):0;lp+=(noise-lp)*.35f;v=(noise-lp)*burst*.9f;break;}
                case 5:case 6:{float base=d==5?75:130,f=base+60*(float)Math.exp(-t*12);phase+=f/RATE;v=(float)Math.sin(phase*2*Math.PI)*(float)Math.exp(-t*(d==5?7:9));break;}
                default:v=hp*(float)Math.exp(-t*2.4f)*.45f;
            }
            s[i]=(short)Math.max(-32767,Math.min(32767,v*26000));
        }
        return s;
    }
    // ------------------------------------------------------------------ synthesis
    static short[] synth(int id){
        java.util.Random r=new java.util.Random(id*31+7);
        float len;
        switch(id){case EXPLODE:case PLAYER_DEATH:case MONSTER_DEATH:case LEVEL_UP:len=.9f;break;case DOOR:case HEAL:len=.7f;break;case SHOTGUN:case ROCKET:case TELEPORT:case SECRET:case SPELL:case POTION:len=.5f;break;case SWING:case COIN:len=.3f;break;default:len=.25f;}
        int n=(int)(RATE*len);short[] s=new short[n];
        float phase=0,lp=0,lp2=0;
        for(int i=0;i<n;i++){
            float t=i/(float)RATE,k=t/len,env,v;
            float noise=r.nextFloat()*2-1;
            switch(id){
                case PISTOL:env=(float)Math.exp(-t*28);lp+= (noise-lp)*.5f;v=lp*env+(float)Math.sin(t*2*Math.PI*140)*env*.4f;break;
                case SHOTGUN:env=(float)Math.exp(-t*9);lp+=(noise-lp)*.25f;v=lp*env*1.4f;break;
                case CHAINGUN:env=(float)Math.exp(-t*35);lp+=(noise-lp)*.6f;v=lp*env;break;
                case ROCKET:env=(float)Math.exp(-t*5);lp+=(noise-lp)*.08f;v=lp*env*2+(float)Math.sin(t*2*Math.PI*90)*env*.3f;break;
                case EXPLODE:env=(float)Math.exp(-t*3.5f);lp+=(noise-lp)*.06f;lp2+=(lp-lp2)*.3f;v=lp2*env*3.2f;break;
                case PUNCH:env=(float)Math.exp(-t*40);lp+=(noise-lp)*.15f;v=lp*env*2;break;
                case DOOR:env=Math.min(1,t*12)*Math.min(1,(len-t)*6);phase+=(float)(2*Math.PI*(55+20*Math.sin(t*14))/RATE);lp+=(noise-lp)*.05f;v=((float)Math.sin(phase)*.5f+lp*1.5f)*env*.8f;break;
                case SWITCH:env=(float)Math.exp(-t*30);v=(float)Math.signum(Math.sin(t*2*Math.PI*900))*env*.35f+noise*env*.2f;break;
                case PICKUP:case ITEM:env=(float)Math.exp(-t*10);phase+=(float)(2*Math.PI*(k<.4f?660:990)/RATE);v=(float)Math.sin(phase)*env*.5f;break;
                case WEAPON_PICKUP:env=Math.min(1,(len-t)*8);phase+=(float)(2*Math.PI*(k<.33f?440:k<.66f?550:660)/RATE);v=(float)Math.signum(Math.sin(phase))*env*.25f;break;
                case PLAYER_PAIN:case OOF:env=(float)Math.exp(-t*(id==OOF?14:9));phase+=(float)(2*Math.PI*(id==OOF?110:180-120*k)/RATE);lp+=(noise-lp)*.2f;v=((float)Math.sin(phase)+lp*.6f)*env*.6f;break;
                case PLAYER_DEATH:case MONSTER_DEATH:env=(float)Math.exp(-t*3);phase+=(float)(2*Math.PI*(id==PLAYER_DEATH?200-150*k:150-100*k)/RATE);lp+=(noise-lp)*.15f;v=((float)Math.sin(phase)*.7f+lp*.7f)*env*.8f;break;
                case MONSTER_SIGHT:env=Math.min(1,t*20)*(float)Math.exp(-t*5);phase+=(float)(2*Math.PI*(90+60*Math.sin(t*30))/RATE);lp+=(noise-lp)*.3f;v=((float)Math.sin(phase)+lp*.5f)*env*.7f;break;
                case MONSTER_PAIN:env=(float)Math.exp(-t*12);phase+=(float)(2*Math.PI*(260-140*k)/RATE);v=(float)Math.sin(phase)*env*.6f+noise*env*.2f;break;
                case FIREBALL:env=(float)Math.exp(-t*8);lp+=(noise-lp)*.1f;v=lp*env*2;break;
                case SWING:env=(float)Math.sin(Math.PI*Math.min(1,k*1.2f));lp+=(noise-lp)*(.05f+.4f*k);v=lp*env*1.6f;break;   // whoosh
                case HIT:env=(float)Math.exp(-t*30);lp+=(noise-lp)*.12f;phase+=(float)(2*Math.PI*(95-40*k)/RATE);v=(lp*1.8f+(float)Math.sin(phase)*.6f)*env;break;   // meaty thud
                case BOW:env=(float)Math.exp(-t*14);phase+=(float)(2*Math.PI*(180+40*(float)Math.exp(-t*40))/RATE);v=((float)Math.sin(phase)*.7f+noise*.15f*(float)Math.exp(-t*60))*env;break;   // twang
                case SPELL:env=Math.min(1,t*10)*(float)Math.exp(-t*4);phase+=(float)(2*Math.PI*(440+660*k+30*Math.sin(t*90))/RATE);v=((float)Math.sin(phase)*.45f+(float)Math.sin(phase*1.5f)*.25f)*env+noise*.08f*env;break;
                case HEAL:env=Math.min(1,t*8)*Math.min(1,(len-t)*4);phase+=(float)(2*Math.PI*(k<.33f?523:k<.66f?659:784)/RATE);v=((float)Math.sin(phase)*.4f+(float)Math.sin(phase*2)*.12f)*env;break;   // rising chime
                case COIN:env=(float)Math.exp(-t*12);phase+=(float)(2*Math.PI*(k<.25f?1320:1760)/RATE);v=((float)Math.sin(phase)*.45f+(float)Math.sin(phase*2.7f)*.15f)*env;break;
                case LEVEL_UP:{env=Math.min(1,t*10)*Math.min(1,(len-t)*3);int step=Math.min(3,(int)(k*4));float[] notes={523,659,784,1047};phase+=(float)(2*Math.PI*notes[step]/RATE);v=((float)Math.signum(Math.sin(phase))*.18f+(float)Math.sin(phase)*.3f)*env;break;}
                case POTION:env=Math.min(1,t*15)*(float)Math.exp(-t*3);phase+=(float)(2*Math.PI*(220+180*(float)Math.sin(t*25))/RATE);v=(float)Math.sin(phase)*.35f*env*(.6f+.4f*(float)Math.sin(t*60));break;   // glug
                case TELEPORT:case SECRET:env=Math.min(1,(len-t)*5);phase+=(float)(2*Math.PI*(300+900*k)/RATE);v=(float)Math.sin(phase)*env*.35f;break;
                default:env=(float)Math.exp(-t*20);phase+=(float)(2*Math.PI*880/RATE);v=(float)Math.sin(phase)*env*.4f;
            }
            s[i]=(short)Math.max(-32767,Math.min(32767,v*20000));
        }
        return s;
    }
}
