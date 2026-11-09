package android.graphics;
import java.awt.geom.AffineTransform;
/** A 2D transform (Java2D's AffineTransform). */
public class Matrix {
    final AffineTransform t=new AffineTransform();
    public void reset(){t.setToIdentity();}
    public void setScale(float x,float y){t.setToScale(x,y);}
    public void setTranslate(float x,float y){t.setToTranslation(x,y);}
    public void setRotate(float d){t.setToRotation(Math.toRadians(d));}
    public void preScale(float x,float y){t.scale(x,y);}
    public void preTranslate(float x,float y){t.translate(x,y);}
    public void postScale(float x,float y){AffineTransform s=AffineTransform.getScaleInstance(x,y);s.concatenate(t);t.setTransform(s);}
    public void postTranslate(float x,float y){AffineTransform s=AffineTransform.getTranslateInstance(x,y);s.concatenate(t);t.setTransform(s);}
    public void postRotate(float d){AffineTransform s=AffineTransform.getRotateInstance(Math.toRadians(d));s.concatenate(t);t.setTransform(s);}
}
