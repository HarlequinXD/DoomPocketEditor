package com.retro.doomeditor;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Loads audio files (WAV directly; MP3, OGG, M4A, FLAC... through the phone's decoders) and writes WAV files. */
final class AudioIO {
    private AudioIO(){}
    static final int MAX_SAMPLES=SoundFx.RATE*SoundAsset.MAX_SECONDS;

    /** Decoded file: mono samples at SoundFx.RATE, and whether it was cut to the length limit. */
    static final class Result {short[] pcm;boolean cut;String name;}

    static Result load(Context c,Uri uri)throws Exception{
        Result r=new Result();r.name=displayName(c,uri);
        byte[] head=new byte[12];int got=0;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException("Can't open file");int n;while(got<12&&(n=in.read(head,got,12-got))>0)got+=n;}
        float[] mono;int rate;
        if(got==12&&head[0]=='R'&&head[1]=='I'&&head[2]=='F'&&head[3]=='F'&&head[8]=='W'&&head[9]=='A'&&head[10]=='V'&&head[11]=='E'){
            byte[] all=readAll(c,uri,64<<20);float[][] w=parseWav(all);mono=w[0];rate=(int)w[1][0];
        }else{float[][] d=decodeCodec(c,uri);mono=d[0];rate=(int)d[1][0];}
        if(mono.length==0)throw new java.io.IOException("No audio found in that file");
        float[] res=resample(mono,rate,SoundFx.RATE);
        int n=Math.min(res.length,MAX_SAMPLES);r.cut=res.length>MAX_SAMPLES;
        r.pcm=new short[n];for(int i=0;i<n;i++)r.pcm[i]=(short)Math.max(-32767,Math.min(32767,res[i]*32767f));
        return r;
    }
    static String displayName(Context c,Uri uri){
        try(Cursor cur=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cur!=null&&cur.moveToFirst()){String s=cur.getString(0);if(s!=null)return s;}
        }catch(Exception ignored){}
        String p=uri.getLastPathSegment();return p==null?"sound":p;
    }
    private static byte[] readAll(Context c,Uri uri,int max)throws Exception{
        try(InputStream in=c.getContentResolver().openInputStream(uri)){
            ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;
            while((n=in.read(b))>0){o.write(b,0,n);if(o.size()>max)break;}
            return o.toByteArray();
        }
    }
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
    /** Decodes compressed audio with MediaCodec. Returns {mono samples, {sampleRate}}. */
    private static float[][] decodeCodec(Context c,Uri uri)throws Exception{
        MediaExtractor ex=new MediaExtractor();MediaCodec codec=null;
        try{
            ex.setDataSource(c,uri,null);
            int track=-1;MediaFormat f=null;
            for(int i=0;i<ex.getTrackCount();i++){MediaFormat tf=ex.getTrackFormat(i);String m=tf.getString(MediaFormat.KEY_MIME);if(m!=null&&m.startsWith("audio/")){track=i;f=tf;break;}}
            if(track<0)throw new java.io.IOException("That file has no audio track");
            ex.selectTrack(track);
            int rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE),ch=f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?f.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
            boolean floats=false;
            codec=MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME));codec.configure(f,null,null,0);codec.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            float[] acc=new float[SoundFx.RATE*2];int n=0;
            boolean inDone=false,outDone=false;long deadline=System.currentTimeMillis()+20000;
            while(!outDone&&System.currentTimeMillis()<deadline){
                if(!inDone){int ii=codec.dequeueInputBuffer(5000);
                    if(ii>=0){ByteBuffer ib=codec.getInputBuffer(ii);int sz=ib==null?-1:ex.readSampleData(ib,0);
                        if(sz<0){codec.queueInputBuffer(ii,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true;}
                        else{codec.queueInputBuffer(ii,0,sz,ex.getSampleTime(),0);ex.advance();}}}
                int oi=codec.dequeueOutputBuffer(info,5000);
                if(oi==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat of=codec.getOutputFormat();
                    if(of.containsKey(MediaFormat.KEY_SAMPLE_RATE))rate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if(of.containsKey(MediaFormat.KEY_CHANNEL_COUNT))ch=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(of.containsKey("pcm-encoding"))floats=of.getInteger("pcm-encoding")==4;}
                else if(oi>=0){
                    ByteBuffer ob=codec.getOutputBuffer(oi);
                    if(ob!=null&&info.size>0){ob.position(info.offset);ob.limit(info.offset+info.size);ob.order(ByteOrder.LITTLE_ENDIAN);
                        int chs=Math.max(1,ch);int frames=info.size/((floats?4:2)*chs);
                        for(int fr=0;fr<frames;fr++){float sum=0;for(int k=0;k<chs;k++)sum+=floats?ob.getFloat():ob.getShort()/32768f;
                            if(n==acc.length)acc=java.util.Arrays.copyOf(acc,acc.length*2);acc[n++]=sum/chs;}}
                    codec.releaseOutputBuffer(oi,false);
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outDone=true;
                    if(n>(long)rate*(SoundAsset.MAX_SECONDS+1))outDone=true;
                }
            }
            return new float[][]{java.util.Arrays.copyOf(acc,n),{rate}};
        }finally{
            try{if(codec!=null){codec.stop();codec.release();}}catch(Exception ignored){}
            ex.release();
        }
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
