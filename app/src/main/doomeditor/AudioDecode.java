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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Android: opens picked audio files (WAV directly; MP3, OGG, M4A, FLAC... through the phone's decoders). */
final class AudioDecode {
    private AudioDecode(){}
    static AudioIO.Result load(Context c,Uri uri)throws Exception{
        AudioIO.Result r=new AudioIO.Result();r.name=displayName(c,uri);
        byte[] head=new byte[12];int got=0;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException("Can't open file");int n;while(got<12&&(n=in.read(head,got,12-got))>0)got+=n;}
        float[][] d=AudioIO.isWav(head,got)?AudioIO.parseWav(readAll(c,uri,64<<20)):decodeCodec(c,uri);
        return AudioIO.finish(r.name,d[0],(int)d[1][0]);
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
}
