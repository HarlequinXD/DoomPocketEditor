package com.retro.doomeditor;

/** Compact Base64 encoding of ARGB pixel arrays for project files (no Android API level concerns). */
final class Codec {
    private Codec(){}
    private static final char[] A="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] R=new int[128];
    static{java.util.Arrays.fill(R,-1);for(int i=0;i<A.length;i++)R[A[i]]=i;}
    static String encode(int[] px){
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
        int[] px=new int[count];byte[] b=new byte[count*4];int bi=0,buf=0,bits=0;
        for(int i=0;i<s.length()&&bi<b.length;i++){char ch=s.charAt(i);if(ch>=128||R[ch]<0)continue;buf=buf<<6|R[ch];bits+=6;if(bits>=8){bits-=8;b[bi++]=(byte)(buf>>bits);}}
        for(int i=0;i<count;i++)px[i]=(b[i*4]&255)<<24|(b[i*4+1]&255)<<16|(b[i*4+2]&255)<<8|(b[i*4+3]&255);
        return px;
    }
}
