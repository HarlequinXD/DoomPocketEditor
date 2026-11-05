package com.retro.doomeditor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;

/** How the game's main menu and splash look: title, background, buttons, music and studio logo. Edited in the UI Editor. */
final class MenuStyle {
    static final String[] LAYOUTS={"Left column","Centred","Bottom left","Right column"};
    static final String[] BACKGROUNDS={"Start map (slow pan)","Start map (still)","Picture","Colour gradient","Theme backdrop"};
    static final int BG_PAN=0,BG_STILL=1,BG_PICTURE=2,BG_GRADIENT=3,BG_THEME=4;
    static final String[] TITLE_STYLES={"Drop shadow","Outline","Glow","Plain"};
    static final String[] BUTTON_STYLES={"Theme panels","Theme buttons","Text only"};
    static final String[] GRADIENTS={"Midnight","Crimson dusk","Deep forest","Ocean","Ember","Royal violet","Bone and ash"};
    static final int[][] GRADIENT_COLS={{0xff0b0f1e,0xff2a1840},{0xff1a0508,0xff6a1a1a},{0xff07140b,0xff24432a},{0xff051424,0xff1a4a66},{0xff120604,0xff7a3a10},{0xff0e0618,0xff4a2a70},{0xff1a1814,0xff6a645a}};
    static final String[] TITLE_COLOR_NAMES={"Theme accent","Gold","White","Parchment","Crimson","Emerald","Frost blue","Violet"};
    static final int[] TITLE_COLORS={0,0xffe8c070,0xffffffff,0xffe8d8b0,0xffe04848,0xff58d08a,0xff9ad0ff,0xffc08aff};

    String title="",subtitle="";
    int titleSize;         // 0 = fit automatically, otherwise dp
    int titleColor;        // index into TITLE_COLORS
    int titleStyle;boolean upper;
    int layout,background,gradient;
    String picture="";     // base64 JPEG
    float dim=.6f,pan=1f;
    String lNew="New Game",lContinue="Continue",lLoad="Load game",lOptions="Options",lQuit="";
    boolean showLoad=true,showOptions=true,credit=true;
    int buttonStyle;float buttonScale=1f;
    String music="";       // "" = start map's music, "none", or a song id
    boolean splash=true;String studio="";String studioPic="";float splashSeconds=2.8f;

    private Bitmap pic,studioBmp;private String picKey,studioKey;
    Bitmap picture(){if(picture.isEmpty())return null;if(!picture.equals(picKey)){picKey=picture;pic=decode(picture);}return pic;}
    Bitmap studioPicture(){if(studioPic.isEmpty())return null;if(!studioPic.equals(studioKey)){studioKey=studioPic;studioBmp=decode(studioPic);}return studioBmp;}
    private static Bitmap decode(String b64){try{byte[] b=android.util.Base64.decode(b64,android.util.Base64.NO_WRAP);return BitmapFactory.decodeByteArray(b,0,b.length);}catch(Exception e){return null;}}
    /** Scales a picked image down so project files stay small, and encodes it as JPEG (PNG keeps transparency for logos). */
    static String encode(Bitmap src,int maxSide,boolean png){
        if(src==null)return "";
        float s=Math.min(1f,maxSide/(float)Math.max(src.getWidth(),src.getHeight()));
        Bitmap b=s<1?Bitmap.createScaledBitmap(src,Math.max(1,Math.round(src.getWidth()*s)),Math.max(1,Math.round(src.getHeight()*s)),true):src;
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        b.compress(png?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,82,out);
        return android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);
    }
    String titleFor(Project p){String t=title.isEmpty()?(p.name==null||p.name.isEmpty()?"Untitled Adventure":p.name):title;return upper?t.toUpperCase():t;}
    String quitLabel(boolean playerMode){return !lQuit.isEmpty()?lQuit:playerMode?"Quit":"Exit to editor";}
    int titleColor(UiTheme th){int c=TITLE_COLORS[Math.max(0,Math.min(TITLE_COLORS.length-1,titleColor))];if(c!=0)return c;return UiTheme.luma(th.accent)<.35f?0xffe8c070:th.accent;}

    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();
        o.put("title",title);o.put("subtitle",subtitle);o.put("titleSize",titleSize);o.put("titleColor",titleColor);o.put("titleStyle",titleStyle);o.put("upper",upper);
        o.put("layout",layout);o.put("background",background);o.put("gradient",gradient);o.put("picture",picture);o.put("dim",(double)dim);o.put("pan",(double)pan);
        o.put("lNew",lNew);o.put("lContinue",lContinue);o.put("lLoad",lLoad);o.put("lOptions",lOptions);o.put("lQuit",lQuit);
        o.put("showLoad",showLoad);o.put("showOptions",showOptions);o.put("credit",credit);o.put("buttonStyle",buttonStyle);o.put("buttonScale",(double)buttonScale);
        o.put("music",music);o.put("splash",splash);o.put("studio",studio);o.put("studioPic",studioPic);o.put("splashSeconds",(double)splashSeconds);
        return o;
    }
    void read(JSONObject o){
        if(o==null)return;
        title=o.optString("title","");subtitle=o.optString("subtitle","");titleSize=o.optInt("titleSize",0);titleColor=clamp(o.optInt("titleColor",0),TITLE_COLORS.length);
        titleStyle=clamp(o.optInt("titleStyle",0),TITLE_STYLES.length);upper=o.optBoolean("upper",false);
        layout=clamp(o.optInt("layout",0),LAYOUTS.length);background=clamp(o.optInt("background",0),BACKGROUNDS.length);gradient=clamp(o.optInt("gradient",0),GRADIENTS.length);
        picture=o.optString("picture","");dim=(float)Math.max(0,Math.min(1,o.optDouble("dim",.6)));pan=(float)Math.max(0,Math.min(3,o.optDouble("pan",1)));
        lNew=o.optString("lNew","New Game");lContinue=o.optString("lContinue","Continue");lLoad=o.optString("lLoad","Load game");lOptions=o.optString("lOptions","Options");lQuit=o.optString("lQuit","");
        showLoad=o.optBoolean("showLoad",true);showOptions=o.optBoolean("showOptions",true);credit=o.optBoolean("credit",true);
        buttonStyle=clamp(o.optInt("buttonStyle",0),BUTTON_STYLES.length);buttonScale=(float)Math.max(.6,Math.min(1.6,o.optDouble("buttonScale",1)));
        music=o.optString("music","");splash=o.optBoolean("splash",true);studio=o.optString("studio","");studioPic=o.optString("studioPic","");
        splashSeconds=(float)Math.max(1,Math.min(8,o.optDouble("splashSeconds",2.8)));
    }
    static MenuStyle fromJson(JSONObject o){MenuStyle m=new MenuStyle();m.read(o);return m;}
    private static int clamp(int v,int n){return Math.max(0,Math.min(n-1,v));}
}
