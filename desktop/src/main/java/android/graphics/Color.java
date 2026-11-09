package android.graphics;
public class Color{public static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}public static int rgb(int r,int g,int b){return argb(255,r,g,b);}
public static int red(int c){return (c>>16)&255;}public static int green(int c){return (c>>8)&255;}public static int blue(int c){return c&255;}public static int alpha(int c){return c>>>24;}
public static void colorToHSV(int c,float[] hsv){float[] h=java.awt.Color.RGBtoHSB(red(c),green(c),blue(c),null);hsv[0]=h[0]*360;hsv[1]=h[1];hsv[2]=h[2];}
public static int HSVToColor(float[] hsv){return 0xff000000|java.awt.Color.HSBtoRGB(hsv[0]/360f,hsv[1],hsv[2]);}
public static int HSVToColor(int a,float[] hsv){return (a<<24)|(java.awt.Color.HSBtoRGB(hsv[0]/360f,hsv[1],hsv[2])&0xffffff);}}
