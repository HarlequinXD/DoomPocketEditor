package com.retro.doomeditor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Audio files: WAV parsing and writing, resampling, and song rendering (the platform decoders are in AudioDecode). */
final class AudioIO {
    private AudioIO(){}
    static final int MAX_SAMPLES=SoundFx.RATE*SoundAsset.MAX_SECONDS;

    /** Decoded file: mono samples at SoundFx.RATE, and whether it was cut to the length limit. */
    static final class Result {short[] pcm;boolean cut;String name;}

    /** Wraps decoded audio: mixes to mono at SoundFx.RATE and cuts it to the length limit. */
    static Result finish(String name,float[] mono,int rate)throws java.io.IOException{
        if(mono.length==0)throw new java.io.IOException("No audio found in that file");
        Result r=new Result();r.name=name;
        float[] res=resample(mono,rate,SoundFx.RATE);
        int n=Math.min(res.length,MAX_SAMPLES);r.cut=res.length>MAX_SAMPLES;
        r.pcm=new short[n];for(int i=0;i<n;i++)r.pcm[i]=(short)Math.max(-32767,Math.min(32767,res[i]*32767f));
        return r;
    }
    static boolean isWav(byte[] h,int got){return got>=12&&h[0]=='R'&&h[1]=='I'&&h[2]=='F'&&h[3]=='F'&&h[8]=='W'&&h[9]=='A'&&h[10]=='V'&&h[11]=='E';}
    private static int u16(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8);}
    private static int s32(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8)|((b[i+2]&255)<<16)|(b[i+3]<<24);}

    /** Returns {mono samples -1..1, {sampleRate}}. Handles 8/16/24/32-bit PCM and 32-bit float. */
    static float[][] parseWav(byte[] b)throws Exception{
        int pos=12,fmt=1,ch=1,rate=22050,bits=16,dataOff=-1,dataLen=0;
        while(pos+8<=b.length){
            String id=new String(b,pos,4,"US-ASCII");int len=s32(b,pos+4);int body=pos+8;
            if(id.equals("fmt ")){fmt=u16(b,body);ch=Math.max(1,u16(b,body+2));rate=s32(b,body+4);bits=u16(b,body+14);if(fmt==0xFFFE&&len>=26)fmt=u16(b,body+24);}
            else if(id.equals("data")){dataOff=body;dataLen=len<0||body+len>b.length?b.length-body:len;break;}
            if(len<0)break;pos=body+len+(len&1);
        }
        if(dataOff<0)throw new java.io.IOException("WAV file has no audio data");
        if(fmt!=1&&fmt!=3)throw new java.io.IOException("Compressed WAV files aren't supported");
        int bps=Math.max(1,bits/8),frame=bps*ch,frames=dataLen/frame;
        frames=Math.min(frames,rate*(SoundAsset.MAX_SECONDS+1));
        float[] out=new float[frames];
        for(int f=0;f<frames;f++){float sum=0;
            for(int k=0;k<ch;k++){int i=dataOff+f*frame+k*bps;float v;
                if(fmt==3&&bits==32)v=Float.intBitsToFloat(s32(b,i));
                else if(bits==8)v=((b[i]&255)-128)/128f;
                else if(bits==16)v=(short)u16(b,i)/32768f;
                else if(bits==24)v=(((b[i]&255)|((b[i+1]&255)<<8)|(b[i+2]<<16)))/8388608f;
                else v=s32(b,i)/2147483648f;
                sum+=v;}
            out[f]=sum/ch;}
        return new float[][]{out,{rate}};
    }
    static float[] resample(float[] in,int from,int to){
        if(from<=0||from==to)return in;
        double ratio=from/(double)to;int n=(int)(in.length/ratio);float[] out=new float[n];
        // light averaging when shrinking a high sample rate stops harsh aliasing
        int span=Math.max(1,(int)Math.floor(ratio));
        for(int i=0;i<n;i++){double p=i*ratio;int ip=(int)p;
            if(span>1){float s=0;int c=0;for(int k=0;k<span&&ip+k<in.length;k++){s+=in[ip+k];c++;}out[i]=c==0?0:s/c;}
            else{float fr=(float)(p-ip);float a=in[Math.min(ip,in.length-1)],b=in[Math.min(ip+1,in.length-1)];out[i]=a+(b-a)*fr;}}
        return out;
    }
    /** 16-bit mono WAV at SoundFx.RATE. */
    static void writeWav(OutputStream o,short[] pcm)throws Exception{
        int data=pcm.length*2;ByteBuffer b=ByteBuffer.allocate(44+data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes("US-ASCII")).putInt(36+data).put("WAVE".getBytes("US-ASCII"));
        b.put("fmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(SoundFx.RATE).putInt(SoundFx.RATE*2).putShort((short)2).putShort((short)16);
        b.put("data".getBytes("US-ASCII")).putInt(data);for(short s:pcm)b.putShort(s);
        o.write(b.array());o.flush();
    }
    /** Renders a song (one pass, no loop) to samples for WAV export. */
    static short[] renderSong(Song s,Project p){
        Song.Compiled c=s.compile(p);int len=(int)(c.length*c.samplesPerStep)+SoundFx.RATE*2;
        return SoundFx.offline(c,len);
    }
}
