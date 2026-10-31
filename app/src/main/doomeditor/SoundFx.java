package com.retro.doomeditor;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Tiny software mixer that synthesises Doom-like sound effects and plays them on one AudioTrack. */
final class SoundFx {
    static final int PISTOL=0,SHOTGUN=1,CHAINGUN=2,ROCKET=3,EXPLODE=4,PUNCH=5,DOOR=6,SWITCH=7,PICKUP=8,WEAPON_PICKUP=9,ITEM=10,
            PLAYER_PAIN=11,PLAYER_DEATH=12,MONSTER_SIGHT=13,MONSTER_PAIN=14,MONSTER_DEATH=15,FIREBALL=16,TELEPORT=17,SECRET=18,OOF=19,BEEP=20,COUNT=21;
    private static final int RATE=22050,VOICES=8,BUF=512;
    private final short[][] clips=new short[COUNT][];
    private final short[][] voice=new short[VOICES][];
    private final int[] pos=new int[VOICES];
    private final float[] vol=new float[VOICES];
    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;

    SoundFx(){for(int i=0;i<COUNT;i++)clips[i]=synth(i);}

    synchronized void play(int id,float volume){
        if(id<0||id>=COUNT||!running)return;
        int slot=0;for(int i=0;i<VOICES;i++){if(voice[i]==null){slot=i;break;}if(pos[i]>pos[slot])slot=i;}
        voice[slot]=clips[id];pos[slot]=0;vol[slot]=Math.max(0,Math.min(1,volume));
    }
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
        running=false;
        try{if(thread!=null)thread.join(200);}catch(InterruptedException ignored){}
        try{if(track!=null){track.stop();track.release();}}catch(Throwable ignored){}
        track=null;thread=null;
    }
    private void loop(){
        short[] out=new short[BUF];
        while(running){
            synchronized(this){
                for(int i=0;i<BUF;i++){
                    float mix=0;
                    for(int v=0;v<VOICES;v++){short[] c=voice[v];if(c==null)continue;mix+=c[pos[v]]*vol[v];if(++pos[v]>=c.length)voice[v]=null;}
                    mix=mix*.7f;out[i]=(short)Math.max(-32767,Math.min(32767,mix));
                }
            }
            try{track.write(out,0,BUF);}catch(Throwable t){running=false;}
        }
    }
    // ------------------------------------------------------------------ synthesis
    private static short[] synth(int id){
        java.util.Random r=new java.util.Random(id*31+7);
        float len;
        switch(id){case EXPLODE:case PLAYER_DEATH:case MONSTER_DEATH:len=.9f;break;case DOOR:len=.7f;break;case SHOTGUN:case ROCKET:case TELEPORT:case SECRET:len=.5f;break;default:len=.25f;}
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
                case TELEPORT:case SECRET:env=Math.min(1,(len-t)*5);phase+=(float)(2*Math.PI*(300+900*k)/RATE);v=(float)Math.sin(phase)*env*.35f;break;
                default:env=(float)Math.exp(-t*20);phase+=(float)(2*Math.PI*880/RATE);v=(float)Math.sin(phase)*env*.4f;
            }
            s[i]=(short)Math.max(-32767,Math.min(32767,v*20000));
        }
        return s;
    }
}
