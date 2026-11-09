package android.content;
import android.content.res.*;
import java.io.*;
import java.util.*;
/** The desktop app context: display density, files and preferences in the user's app-data folder. */
public class Context {
    private final Resources res=new Resources();private final AssetManager assets=new AssetManager();private final File home;
    private final Map<String,Prefs> prefs=new HashMap<>();
    public Context(File home,float density){this.home=home;home.mkdirs();res.getDisplayMetrics().density=density;}
    public Resources getResources(){return res;}
    public Context getApplicationContext(){return this;}
    public AssetManager getAssets(){return assets;}
    public String getPackageName(){return "com.retro.doomeditor";}
    public File home(){return home;}
    public File getFilesDir(){File f=new File(home,"files");f.mkdirs();return f;}
    public File getCacheDir(){File f=new File(home,"cache");f.mkdirs();return f;}
    public synchronized SharedPreferences getSharedPreferences(String name,int mode){return prefs.computeIfAbsent(name,n->new Prefs(new File(home,n+".properties")));}

    static final class Prefs implements SharedPreferences {
        final File f;final Properties p=new Properties();
        Prefs(File f){this.f=f;if(f.exists())try(InputStream in=new FileInputStream(f)){p.load(in);}catch(IOException ignored){}}
        public synchronized int getInt(String k,int d){try{return Integer.parseInt(p.getProperty(k));}catch(Exception e){return d;}}
        public synchronized long getLong(String k,long d){try{return Long.parseLong(p.getProperty(k));}catch(Exception e){return d;}}
        public synchronized float getFloat(String k,float d){try{return Float.parseFloat(p.getProperty(k));}catch(Exception e){return d;}}
        public synchronized boolean getBoolean(String k,boolean d){String v=p.getProperty(k);return v==null?d:Boolean.parseBoolean(v);}
        public synchronized String getString(String k,String d){return p.getProperty(k,d);}
        public synchronized boolean contains(String k){return p.containsKey(k);}
        public Editor edit(){return new Editor(){
            public Editor putInt(String k,int v){synchronized(Prefs.this){p.setProperty(k,""+v);}return this;}
            public Editor putLong(String k,long v){synchronized(Prefs.this){p.setProperty(k,""+v);}return this;}
            public Editor putFloat(String k,float v){synchronized(Prefs.this){p.setProperty(k,""+v);}return this;}
            public Editor putBoolean(String k,boolean v){synchronized(Prefs.this){p.setProperty(k,""+v);}return this;}
            public Editor putString(String k,String v){synchronized(Prefs.this){if(v==null)p.remove(k);else p.setProperty(k,v);}return this;}
            public Editor remove(String k){synchronized(Prefs.this){p.remove(k);}return this;}
            public Editor clear(){synchronized(Prefs.this){p.clear();}return this;}
            public void apply(){commit();}
            public boolean commit(){synchronized(Prefs.this){try(OutputStream o=new FileOutputStream(f)){p.store(o,"RPG Pocket Editor");return true;}catch(IOException e){return false;}}}};}
    }
}
