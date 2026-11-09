package com.retro.doomeditor;

import java.io.File;

/** PC: opens audio files for the Sound editor. WAV is read directly; AIFF and AU through Java Sound. */
final class AudioDecode {
    private AudioDecode(){}
    static final String[] PATTERNS={"*.wav","*.aif","*.aiff","*.au"};
    static AudioIO.Result load(File f)throws Exception{
        String name=f.getName();int dot=name.lastIndexOf('.');if(dot>0)name=name.substring(0,dot);
        byte[] all=java.nio.file.Files.readAllBytes(f.toPath());
        if(all.length>64<<20)throw new java.io.IOException("That file is too large");
        if(AudioIO.isWav(all,all.length)){float[][] w=AudioIO.parseWav(all);return AudioIO.finish(name,w[0],(int)w[1][0]);}
        try(javax.sound.sampled.AudioInputStream in0=javax.sound.sampled.AudioSystem.getAudioInputStream(f)){
            javax.sound.sampled.AudioFormat src=in0.getFormat();
            javax.sound.sampled.AudioFormat pcm=new javax.sound.sampled.AudioFormat(src.getSampleRate(),16,src.getChannels(),true,false);
            try(javax.sound.sampled.AudioInputStream in=javax.sound.sampled.AudioSystem.getAudioInputStream(pcm,in0)){
                byte[] b=in.readAllBytes();int ch=Math.max(1,pcm.getChannels()),n=b.length/2/ch;float[] mono=new float[n];
                for(int i=0;i<n;i++){float s=0;for(int c=0;c<ch;c++){int q=(i*ch+c)*2;s+=(short)((b[q]&255)|(b[q+1]<<8))/32768f;}mono[i]=s/ch;}
                return AudioIO.finish(name,mono,Math.round(pcm.getSampleRate()));}
        }catch(javax.sound.sampled.UnsupportedAudioFileException e){throw new java.io.IOException("Use a WAV file (MP3 and OGG can be converted to WAV in any audio app)");}
    }
}
