package android.graphics;
import java.awt.image.BufferedImage;
import java.io.InputStream;
public class BitmapFactory {
    public static class Options { public boolean inPremultiplied=true,inScaled=true;public Bitmap.Config inPreferredConfig; }
    public static Bitmap decodeByteArray(byte[] b,int off,int len){try{java.awt.image.BufferedImage i=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b,off,len));if(i==null)return null;java.awt.image.BufferedImage a=new java.awt.image.BufferedImage(i.getWidth(),i.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);a.getGraphics().drawImage(i,0,0,null);return new Bitmap(a);}catch(Exception e){return null;}}
    public static Bitmap decodeStream(InputStream in,Rect pad,Options o){
        try{BufferedImage src=javax.imageio.ImageIO.read(in);if(src==null)return null;
            BufferedImage i=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);
            int[] px=src.getRGB(0,0,src.getWidth(),src.getHeight(),null,0,src.getWidth());i.setRGB(0,0,src.getWidth(),src.getHeight(),px,0,src.getWidth());
            return new Bitmap(i);}catch(java.io.IOException e){return null;}
    }
}
