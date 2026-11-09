package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap {
    public enum Config {ARGB_8888,RGB_565}
    public final BufferedImage img;
    public Bitmap(BufferedImage i){img=i;}
    public static Bitmap createBitmap(int[] px,int w,int h,Config c){BufferedImage i=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);i.setRGB(0,0,w,h,px,0,w);return new Bitmap(i);}
    public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
    public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean filter){
        BufferedImage i=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=i.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,filter?java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR:java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(b.img,0,0,w,h,null);g.dispose();return new Bitmap(i);}
    public int getWidth(){return img.getWidth();} public int getHeight(){return img.getHeight();}
    private int[] data(){return img.getType()==BufferedImage.TYPE_INT_ARGB?((java.awt.image.DataBufferInt)img.getRaster().getDataBuffer()).getData():null;}
    public void getPixels(int[] px,int off,int stride,int x,int y,int w,int h){
        int[] d=data();if(d==null){img.getRGB(x,y,w,h,px,off,stride);return;}int W=img.getWidth();
        for(int r=0;r<h;r++)System.arraycopy(d,(y+r)*W+x,px,off+r*stride,w);}
    /** RGBA bytes (as read back from OpenGL) into the bitmap, row 0 first. */
    public void copyPixelsFromBuffer(java.nio.Buffer buf){
        java.nio.ByteBuffer b=(java.nio.ByteBuffer)buf;int[] d=data();int n=img.getWidth()*img.getHeight(),p=b.position();
        if(d==null){d=new int[n];for(int i=0;i<n;i++){int q=p+i*4;d[i]=((b.get(q+3)&255)<<24)|((b.get(q)&255)<<16)|((b.get(q+1)&255)<<8)|(b.get(q+2)&255);}img.setRGB(0,0,img.getWidth(),img.getHeight(),d,0,img.getWidth());return;}
        for(int i=0;i<n;i++){int q=p+i*4;d[i]=((b.get(q+3)&255)<<24)|((b.get(q)&255)<<16)|((b.get(q+1)&255)<<8)|(b.get(q+2)&255);}
    }
    public boolean hasAlpha(){return img.getColorModel().hasAlpha();}
    public void recycle(){}
    public void setPixels(int[] px,int off,int stride,int x,int y,int w,int h){
        int[] d=data();if(d==null){img.setRGB(x,y,w,h,px,off,stride);return;}int W=img.getWidth();
        for(int r=0;r<h;r++)System.arraycopy(px,off+r*stride,d,(y+r)*W+x,w);}
    public enum CompressFormat{PNG,JPEG,WEBP}
    public boolean compress(CompressFormat f,int q,java.io.OutputStream o){try{javax.imageio.ImageIO.write(img,f==CompressFormat.PNG?"png":"jpg",o);return true;}catch(Exception e){return false;}}
    public int getPixel(int x,int y){return img.getRGB(x,y);}
    public void setPixel(int x,int y,int c){img.setRGB(x,y,c);}
}
