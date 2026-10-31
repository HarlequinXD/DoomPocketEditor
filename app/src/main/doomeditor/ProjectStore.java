package com.retro.doomeditor;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;

/** One JSON file per project in app storage, plus the "current project" pointer. */
final class ProjectStore {
    private ProjectStore(){}
    static final String[] TEMPLATES={"Starter level","Arena","Empty project"};
    static final String[] TEMPLATE_DESC={"Two rooms, a monster and pickups. All 20 entity types.","Big open arena with platforms, pillars, monsters and weapons.","A blank map with just the player type. Build from scratch."};

    static final class Meta {String id,name,template;long created,modified;int sectors,entities,sprites;}

    static File dir(Context c){File d=new File(c.getFilesDir(),"projects");if(!d.exists())d.mkdirs();return d;}
    private static File file(Context c,String id){return new File(dir(c),id+".json");}
    static String currentId(Context c){return c.getSharedPreferences("app",0).getString("current",null);}
    static void setCurrent(Context c,String id){c.getSharedPreferences("app",0).edit().putString("current",id).apply();}

    static String read(File f)throws Exception{
        StringBuilder b=new StringBuilder();char[] buf=new char[8192];
        try(InputStreamReader r=new InputStreamReader(new FileInputStream(f),"UTF-8")){int n;while((n=r.read(buf))>0)b.append(buf,0,n);}
        return b.toString();
    }
    static synchronized void write(Context c,String id,String json)throws Exception{
        File tmp=new File(dir(c),id+".tmp");
        try(OutputStreamWriter w=new OutputStreamWriter(new FileOutputStream(tmp),"UTF-8")){w.write(json);}
        File f=file(c,id);if(f.exists()&&!f.delete()){}
        if(!tmp.renameTo(f))throw new java.io.IOException("rename failed");
    }
    static ArrayList<Meta> list(Context c){
        migrate(c);
        ArrayList<Meta> out=new ArrayList<>();File[] files=dir(c).listFiles();if(files==null)return out;
        for(File f:files){if(!f.getName().endsWith(".json"))continue;
            try{JSONObject o=new JSONObject(read(f));JSONObject m=o.optJSONObject("meta");Meta meta=new Meta();
                meta.id=f.getName().substring(0,f.getName().length()-5);meta.name=m!=null?m.optString("name","Untitled"):"Untitled";meta.template=m!=null?m.optString("template",""):"";
                meta.created=m!=null?(long)m.optDouble("created",f.lastModified()):f.lastModified();meta.modified=f.lastModified();
                JSONObject lv=o.optJSONObject("level");if(lv!=null){meta.sectors=lv.optJSONArray("sectors")==null?0:lv.optJSONArray("sectors").length();meta.entities=lv.optJSONArray("things")==null?0:lv.optJSONArray("things").length();}
                meta.sprites=o.optJSONArray("sprites")==null?0:o.optJSONArray("sprites").length();
                out.add(meta);
            }catch(Exception ignored){}
        }
        Collections.sort(out,(a,b)->Long.compare(b.modified,a.modified));
        return out;
    }
    static Project load(Context c,String id)throws Exception{
        Project p=Project.fromJson(new JSONObject(read(file(c,id))));p.id=id;return p;
    }
    static String newId(){return "p"+Long.toString(System.currentTimeMillis(),36)+Integer.toString((int)(Math.random()*1296),36);}
    static Project create(Context c,String name,int template){
        Project p;
        switch(template){
            case 1:p=Project.fresh();p.level=Templates.arena();break;
            case 2:p=new Project();p.level=Templates.empty();p.defs.add(DefaultContent.player());break;
            default:p=Project.fresh();
        }
        p.id=newId();p.name=name;p.template=TEMPLATES[Math.max(0,Math.min(2,template))];p.created=System.currentTimeMillis();
        p.saveNow(c);setCurrent(c,p.id);return p;
    }
    static void delete(Context c,String id){file(c,id).delete();if(id.equals(currentId(c)))c.getSharedPreferences("app",0).edit().remove("current").apply();}
    static void rename(Context c,String id,String name)throws Exception{Project p=load(c,id);p.name=name;p.saveNow(c);}
    static String duplicate(Context c,String id)throws Exception{Project p=load(c,id);p.id=newId();p.name=p.name+" copy";p.created=System.currentTimeMillis();p.saveNow(c);return p.id;}

    /** Moves the single-project data of earlier versions into a project file once. */
    static void migrate(Context c){
        android.content.SharedPreferences app=c.getSharedPreferences("app",0);
        if(app.getString("migrated",null)!=null)return;
        app.edit().putString("migrated","1").apply();
        android.content.SharedPreferences old=c.getSharedPreferences("project",0);
        if(old.getString("project",null)==null&&old.getString("level",null)==null)return;
        Project p=Project.loadLegacy(c);p.id=newId();p.name="My Project";p.template="Imported";p.created=System.currentTimeMillis();
        p.saveNow(c);setCurrent(c,p.id);
    }
}
