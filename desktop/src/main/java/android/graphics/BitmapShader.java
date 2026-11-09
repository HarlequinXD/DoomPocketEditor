package android.graphics;
import java.awt.geom.Rectangle2D;
/** A repeating bitmap fill (Java2D TexturePaint, with the local matrix's scale and offset). */
public class BitmapShader extends Shader {
    private final Bitmap b;private final Matrix m=new Matrix();
    public BitmapShader(Bitmap b,TileMode x,TileMode y){this.b=b;}
    public void setLocalMatrix(Matrix l){m.t.setTransform(l.t);}
    java.awt.Paint awt(){
        double sx=Math.hypot(m.t.getScaleX(),m.t.getShearY()),sy=Math.hypot(m.t.getShearX(),m.t.getScaleY());
        return new java.awt.TexturePaint(b.img,new Rectangle2D.Double(m.t.getTranslateX(),m.t.getTranslateY(),b.getWidth()*sx,b.getHeight()*sy));
    }
}
