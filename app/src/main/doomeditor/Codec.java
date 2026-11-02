package com.retro.doomeditor;

/** Compact Base64 encoding of ARGB pixel arrays for project files (no Android API level concerns). */
final class Codec {
    private Codec(){}
    private static final char[] A="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] R=new int[128];
    static{java.util.Arrays.fill(R,-1);for(int i=0;i<A.length;i++)R[A[i]]=i;}
    /**
     * Sprite pixels are mostly transparent runs and a handful of colours, so they are stored as a palette
     * plus run lengths ("!" prefix), typically 10-30x smaller than raw. Raw base64 is still read for old files.
     */
    static String encode(int[] px){
        java.util.HashMap<Integer,Integer> pal=new java.util.HashMap<>();java.util.ArrayList<Integer> colors=new java.util.ArrayList<>();
        for(int c:px){if(!pal.containsKey(c)){if(colors.size()>=255)return encodeRaw(px);pal.put(c,colors.size());colors.add(c);}}
        java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream(64+px.length/4);
        b.write(colors.size());
        for(int c:colors){b.write(c>>>24);b.write(c>>16);b.write(c>>8);b.write(c);}
        for(int i=0;i<px.length;){int c=px[i],run=1;while(i+run<px.length&&px[i+run]==c)run++;
            b.write(pal.get(c));int r=run;while(r>=128){b.write((r&127)|128);r>>>=7;}b.write(r);i+=run;}
        return "!"+base64(b.toByteArray());
    }
    private static String base64(byte[] b){
        StringBuilder s=new StringBuilder((b.length+2)/3*4);
        for(int i=0;i<b.length;i+=3){
            int n=(b[i]&255)<<16|(i+1<b.length?(b[i+1]&255)<<8:0)|(i+2<b.length?b[i+2]&255:0);
            s.append(A[n>>18&63]).append(A[n>>12&63]).append(i+1<b.length?A[n>>6&63]:'=').append(i+2<b.length?A[n&63]:'=');
        }
        return s.toString();
    }
    private static byte[] unbase64(String s,int from){
        java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream(s.length()*3/4);int buf=0,bits=0;
        for(int i=from;i<s.length();i++){char ch=s.charAt(i);if(ch>=128||R[ch]<0)continue;buf=buf<<6|R[ch];bits+=6;if(bits>=8){bits-=8;o.write((buf>>bits)&255);}}
        return o.toByteArray();
    }
    static String encodeRaw(int[] px){
        byte[] b=new byte[px.length*4];
        for(int i=0;i<px.length;i++){int c=px[i];b[i*4]=(byte)(c>>>24);b[i*4+1]=(byte)(c>>16);b[i*4+2]=(byte)(c>>8);b[i*4+3]=(byte)c;}
        StringBuilder s=new StringBuilder((b.length+2)/3*4);
        for(int i=0;i<b.length;i+=3){
            int n=(b[i]&255)<<16|(i+1<b.length?(b[i+1]&255)<<8:0)|(i+2<b.length?b[i+2]&255:0);
            s.append(A[n>>18&63]).append(A[n>>12&63]).append(i+1<b.length?A[n>>6&63]:'=').append(i+2<b.length?A[n&63]:'=');
        }
        return s.toString();
    }
    static int[] decode(String s,int count){
        if(s.startsWith("!")){
            int[] px=new int[count];byte[] b=unbase64(s,1);int p=0;
            try{int n=b[p++]&255;int[] pal=new int[n];
                for(int i=0;i<n;i++){pal[i]=(b[p]&255)<<24|(b[p+1]&255)<<16|(b[p+2]&255)<<8|(b[p+3]&255);p+=4;}
                int o=0;while(p<b.length&&o<count){int idx=b[p++]&255;int run=0,shift=0,v;do{v=b[p++]&255;run|=(v&127)<<shift;shift+=7;}while((v&128)!=0&&p<b.length);
                    int c=idx<n?pal[idx]:0;for(int k=0;k<run&&o<count;k++)px[o++]=c;}
            }catch(RuntimeException ignored){}
            return px;
        }
        int[] px=new int[count];byte[] b=new byte[count*4];int bi=0,buf=0,bits=0;
        for(int i=0;i<s.length()&&bi<b.length;i++){char ch=s.charAt(i);if(ch>=128||R[ch]<0)continue;buf=buf<<6|R[ch];bits+=6;if(bits>=8){bits-=8;b[bi++]=(byte)(buf>>bits);}}
        for(int i=0;i<count;i++)px[i]=(b[i*4]&255)<<24|(b[i*4+1]&255)<<16|(b[i*4+2]&255)<<8|(b[i*4+3]&255);
        return px;
    }
}
