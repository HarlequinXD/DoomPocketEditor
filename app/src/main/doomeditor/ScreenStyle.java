package com.retro.doomeditor;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import org.json.JSONObject;

/**
 * The game's story screens: the title card shown when a map starts, the death screen and the
 * victory (game complete) screen. Edited in UI Editor → Screens; drawn by Play and the preview.
 */
final class ScreenStyle {
    static final String[] CARD_STYLES={"Cinematic (fade from black)","Banner","Simple text","Off"};
    static final int CARD_CINEMATIC=0,CARD_BANNER=1,CARD_SIMPLE=2,CARD_OFF=3;
    static final String[] DEATH_TINTS={"Blood red","Black","Ash grey","Deep violet"};
    static final int[] DEATH_TINT_COLS={0xff5a0606,0xff000000,0xff2a2a2a,0xff1e0a30};

    // title card
    int cardStyle;float cardSeconds=3f;boolean chapter=true;
    // death
    boolean death=true;String deathTitle="You have fallen",deathText="Your tale is not over yet.";int deathTint;float deathDelay=1.2f;
    String lRetry="Try again",lLoadSave="Load last save";
    // victory
    String winTitle="Quest complete",winText="",lAgain="Play again";boolean winStats=true,winHero=true;

    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();
        o.put("cardStyle",cardStyle);o.put("cardSeconds",(double)cardSeconds);o.put("chapter",chapter);
        o.put("death",death);o.put("deathTitle",deathTitle);o.put("deathText",deathText);o.put("deathTint",deathTint);o.put("deathDelay",(double)deathDelay);o.put("lRetry",lRetry);o.put("lLoadSave",lLoadSave);
        o.put("winTitle",winTitle);o.put("winText",winText);o.put("lAgain",lAgain);o.put("winStats",winStats);o.put("winHero",winHero);
        return o;
    }
    void read(JSONObject o){
        if(o==null)return;
        cardStyle=Math.max(0,Math.min(CARD_STYLES.length-1,o.optInt("cardStyle",0)));cardSeconds=(float)Math.max(1,Math.min(8,o.optDouble("cardSeconds",3)));chapter=o.optBoolean("chapter",true);
        death=o.optBoolean("death",true);deathTitle=o.optString("deathTitle","You have fallen");deathText=o.optString("deathText","Your tale is not over yet.");
        deathTint=Math.max(0,Math.min(DEATH_TINTS.length-1,o.optInt("deathTint",0)));deathDelay=(float)Math.max(0,Math.min(5,o.optDouble("deathDelay",1.2)));
        lRetry=o.optString("lRetry","Try again");lLoadSave=o.optString("lLoadSave","Load last save");
        winTitle=o.optString("winTitle","Quest complete");winText=o.optString("winText","");lAgain=o.optString("lAgain","Play again");winStats=o.optBoolean("winStats",true);winHero=o.optBoolean("winHero",true);
    }

    // ================================================================== drawing
    /** Title card {@code t} seconds after a map starts; false once it has finished. */
    boolean drawCard(Canvas c,Paint p,Ui ui,UiTheme th,float w,float h,String name,String sub,int mapIndex,float t){
        if(cardStyle==CARD_OFF||t>=cardSeconds)return false;
        float D=ui.dp,a=Math.max(0,Math.min(1,Math.min(t/.5f,(cardSeconds-t)/.6f)));int al=(int)(a*255);
        String chap=chapter&&mapIndex>=0?"CHAPTER "+(mapIndex+1):null;
        int acc=UiTheme.luma(th.accent)<.35f?0xffe8c070:th.accent;
        if(cardStyle==CARD_CINEMATIC){
            float veil=Math.max(0,Math.min(1,1.2f-t/.9f));if(veil>0){p.setColor(((int)(veil*255))<<24);c.drawRect(0,0,w,h,p);}
            p.setShader(new LinearGradient(0,h*.3f,0,h*.7f,0x00000000,((int)(a*170))<<24,Shader.TileMode.MIRROR));c.drawRect(0,h*.3f,w,h*.7f,p);p.setShader(null);
            float cy=h*.5f;
            if(chap!=null)ui.text(c,chap,w*.5f,cy-D*30,12,(al<<24)|(acc&0xffffff),2,0);
            float size=Math.min(40,Math.max(22,w/D*.9f/Math.max(6,name.length())));
            ui.text(c,name,w*.5f+D*2,cy+D*8+D*2,size,(int)(a*200)<<24,2,0);ui.text(c,name,w*.5f,cy+D*8,size,(al<<24)|0xf2eee6,2,0);
            float lw=Math.min(w*.4f,D*240)*Math.min(1,t/.8f);ui.rect(c,w*.5f-lw*.5f,cy+D*20,w*.5f+lw*.5f,cy+D*22,UiTheme.alpha(acc,a*.7f));
            if(sub!=null&&!sub.isEmpty())ui.text(c,ui.ellipsize(sub,13,1,w*.8f),w*.5f,cy+D*44,13,(al<<24)|0xc8ccd4,1,0);
        }else if(cardStyle==CARD_BANNER){
            float slide=Math.min(1,t/.35f),bw=Math.min(w*.7f,D*420),bh=D*(sub==null||sub.isEmpty()?58:76),top=D*70;
            float l=w*.5f-bw*.5f,dy=(1-slide)*-D*30;
            th.panel(c,p,l,top+dy,l+bw,top+bh+dy,D,a*.96f,false);
            float y=top+dy+D*(chap!=null?22:34);
            if(chap!=null)ui.text(c,chap,w*.5f,y,10.5f,(al<<24)|(th.accentOnPanel()&0xffffff),2,0);
            ui.text(c,ui.ellipsize(name,20,2,bw-D*30),w*.5f,y+D*(chap!=null?22:4),20,(al<<24)|(th.text&0xffffff),2,0);
            if(sub!=null&&!sub.isEmpty())ui.text(c,ui.ellipsize(sub,12,1,bw-D*30),w*.5f,y+D*(chap!=null?42:24),12,(al<<24)|(th.textDim()&0xffffff),1,0);
        }else{
            ui.text(c,name,w*.5f+D*2,h*.36f+D*2,26,(int)(a*200)<<24,2,0);ui.text(c,name,w*.5f,h*.36f,26,(al<<24)|0xffe8b0,2,0);
            if(sub!=null&&!sub.isEmpty())ui.text(c,ui.ellipsize(sub,13,1,w*.8f),w*.5f,h*.36f+D*24,13,(al<<24)|0xd0d0d0,1,0);
        }
        return true;
    }
    /** Death screen {@code t} seconds after the hero fell; registers hit ids for the buttons that apply. */
    void drawDeath(Canvas c,Paint p,Ui ui,UiTheme th,float w,float h,float t,boolean canLoad,String backLabel,int hRetry,int hLoad,int hBack,RectF r){
        float D=ui.dp,a=Math.max(0,Math.min(1,(t-deathDelay)/.8f));if(a<=0)return;int al=(int)(a*255);
        int tint=DEATH_TINT_COLS[deathTint];
        p.setShader(new android.graphics.RadialGradient(w*.5f,h*.45f,Math.max(w,h)*.7f,((int)(a*120))<<24|(tint&0xffffff),((int)(a*235))<<24|(tint&0xffffff),Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        float cy=h*.38f;
        ui.text(c,deathTitle,w*.5f+D*2,cy+D*2,34,(int)(a*200)<<24,2,0);ui.text(c,deathTitle,w*.5f,cy,34,(al<<24)|0xe8d8d0,2,0);
        if(!deathText.isEmpty())ui.text(c,ui.ellipsize(deathText,13,1,w*.85f),w*.5f,cy+D*28,13,(al<<24)|0xc0b8b4,1,0);
        if(a<1)return;   // buttons once the fade is done
        java.util.ArrayList<Object[]> b=new java.util.ArrayList<>();
        b.add(new Object[]{Icons.RESET,lRetry,hRetry,UiTheme.BTN_PRIMARY});if(canLoad)b.add(new Object[]{Icons.FOLDER,lLoadSave,hLoad,UiTheme.BTN_NORMAL});b.add(new Object[]{Icons.ARROW_LEFT,backLabel,hBack,UiTheme.BTN_NORMAL});
        float bw=Math.min(D*190,(w-D*40)/b.size()-D*10),total=b.size()*(bw+D*10)-D*10,x=w*.5f-total*.5f,by=cy+D*62;
        for(Object[] o:b){r.set(x,by,x+bw,by+D*42);th.rectButton(c,p,ui,r,(Integer)o[0],(String)o[1],(Integer)o[3],D);ui.hit(r,(Integer)o[2]);x+=bw+D*10;}
    }
    /** Victory screen: title, optional text, hero line and stats rows, then Play again / back. */
    void drawVictory(Canvas c,Paint p,Ui ui,UiTheme th,float w,float h,String heroLine,String[] labels,String[] vals,String backLabel,int hAgain,int hBack,RectF r){
        float D=ui.dp;
        ui.rect(c,0,0,w,h,0x99000000);
        boolean txt=!winText.isEmpty(),hero=winHero&&heroLine!=null;int n=winStats?labels.length:0;
        float body=D*(22+(txt?22:0)+(hero?22:0)+n*28+70),pw=Math.min(w-D*40,D*460),pl=(w-pw)*.5f,top=Math.max(D*16,(h-body-D*46)*.42f);
        th.panel(c,p,pl,top,pl+pw,top+body+D*46,D,.97f,false);
        float cx=w*.5f,y=top+D*46;
        ui.text(c,ui.ellipsize(winTitle.toUpperCase(),26,2,pw-D*30),cx,y,26,th.accentOnPanel(),2,0);y+=D*4;
        if(txt){y+=D*22;ui.text(c,ui.ellipsize(winText,13,1,pw-D*30),cx,y,13,th.text,1,0);}
        if(hero){y+=D*22;ui.text(c,heroLine,cx,y,12,th.textDim(),1,0);}
        y+=D*14;
        for(int i=0;i<n;i++){y+=D*28;ui.text(c,labels[i],cx-D*20,y,16,th.textDim(),1,1);ui.text(c,vals[i],cx+D*20,y,16,th.text,2,-1);}
        float by=y+D*24;
        r.set(cx-D*170,by,cx-D*10,by+D*40);th.rectButton(c,p,ui,r,Icons.RESET,lAgain,UiTheme.BTN_PRIMARY,D);ui.hit(r,hAgain);
        r.set(cx+D*10,by,cx+D*170,by+D*40);th.rectButton(c,p,ui,r,Icons.ARROW_LEFT,backLabel,UiTheme.BTN_NORMAL,D);ui.hit(r,hBack);
    }
}
