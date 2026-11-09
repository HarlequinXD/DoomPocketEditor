package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/**
 * The top layer of every screen on the PC: the app's own dialogs (lists to pick from, confirmations, text entry with an
 * on-screen keyboard for the controller), toasts, and the global cursor drawn over everything. Dialogs are drawn in the
 * editor's dark theme and work with a mouse, a controller (cursor + A, B to cancel) or a keyboard (typing, Enter, Esc).
 */
final class Overlay extends View implements Pointer.Aware {
    interface Text{void got(String s);}

    final Ui ui;final Pointer pointer;
    private static final int NONE=0,CHOOSE=1,CONFIRM=2,PROMPT=3;
    private int kind;private String title,message,okLabel;private String[] items;private int selected;
    private java.util.function.IntConsumer onPick;private Runnable onOk,after;private Text onText;private final StringBuilder text=new StringBuilder();private boolean shift,numeric;
    private String toast;private long toastUntil;private float scroll;
    private final RectF r=new RectF();private float downY;private int downHit=-1;private boolean scrolling;
    private static final int H_ITEM=1000,H_CANCEL=1,H_OK=2,H_KEY=2000,H_PANEL=3;
    private static final String[] ROWS={"1234567890","qwertyuiop","asdfghjkl'","zxcvbnm,.-"},NUM_ROWS={"789","456","123","-0."};
    private static final int MAX_TEXT=400;

    Overlay(Context c){super(c);ui=new Ui(c);pointer=new Pointer(ui.dp);}

    // ---------------------------------------------------------------- API
    boolean busy(){return kind!=NONE;}
    void choose(String title,String[] items,int selected,java.util.function.IntConsumer cb,Runnable after){
        this.title=title;this.items=items;this.selected=selected;onPick=cb;this.after=after;kind=CHOOSE;
        float rowH=ui.dp*44;scroll=selected>3?(selected-3)*rowH:0;focus();}
    void confirm(String title,String message,String ok,Runnable cb,Runnable after){this.title=title;this.message=message;okLabel=ok;onOk=cb;this.after=after;kind=CONFIRM;focus();}
    void prompt(String title,String hint,String value,boolean numeric,Text cb,Runnable after){
        this.title=title;message=hint;text.setLength(0);text.append(value==null?"":value);this.numeric=numeric;onText=cb;this.after=after;kind=PROMPT;shift=false;focus();}
    void toast(String msg){toast=msg;toastUntil=System.currentTimeMillis()+2600;}
    void dismiss(){kind=NONE;onPick=null;onOk=null;onText=null;Runnable a=after;after=null;if(a!=null)a.run();}
    /** Esc / B: closes the dialog; false when there was none. */
    boolean back(){if(kind==NONE)return false;dismiss();return true;}
    private void focus(){post(()->{if(Float.isNaN(pointer.x)||!pointer.visible)return;RectF t=firstTarget();if(t!=null)pointer.jumpTo(t.centerX(),t.centerY());});}
    private RectF firstTarget(){
        for(Ui.Hit h:ui.hits)if(kind==CHOOSE&&h.id==H_ITEM+Math.max(0,selected))return h.r;
        for(Ui.Hit h:ui.hits)if(kind==CONFIRM&&h.id==H_CANCEL)return h.r;   // a confirmation starts on Cancel, so a double press can't do it by accident
        for(Ui.Hit h:ui.hits)if(h.id==H_OK||h.id>=H_ITEM&&h.id<H_ITEM+999)return h.r;return null;}
    /** Keyboard: typed characters, Backspace, Enter, paste. */
    void typed(String s){if(kind!=PROMPT||s.isEmpty())return;for(char ch:s.toCharArray())if(ch>=32&&text.length()<MAX_TEXT&&(!numeric||"0123456789-.".indexOf(ch)>=0))text.append(ch);}
    void backspace(){if(kind==PROMPT&&text.length()>0)text.setLength(text.length()-1);}
    void enter(){if(kind==PROMPT)finishPrompt();else if(kind==CONFIRM){Runnable ok=onOk,a=after;onOk=null;after=null;dismiss();if(ok!=null)ok.run();if(a!=null)a.run();}}
    String text(){return text.toString();}

    @Override public Ui pointerUi(){return ui;}

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();ui.clearHits();
        if(kind!=NONE)drawDialog(c,w,h);
        if(toast!=null){long left=toastUntil-System.currentTimeMillis();
            if(left<=0)toast=null;else{float a=Math.min(1,left/400f);float tw=ui.measure(toast,13,1)+ui.dp(40);float l=(w-tw)/2,t=h-ui.dp(80);
                ui.pill(c,l,t,l+tw,t+ui.dp(36),(((int)(240*a))<<24)|0x262c34);ui.textMid(c,toast,w/2,t+ui.dp(18),13,(((int)(255*a))<<24)|0xe8ecf1,1,0);}}
        pointer.draw(c);
    }
    private ArrayList<String> wrap(String s,float size,float max){
        ArrayList<String> out=new ArrayList<>();if(s==null||s.isEmpty())return out;
        for(String para:s.split("\n")){StringBuilder line=new StringBuilder();
            for(String word:para.split(" ")){String t=line.length()==0?word:line+" "+word;
                if(ui.measure(t,size,0)>max&&line.length()>0){out.add(line.toString());line.setLength(0);line.append(word);}else{line.setLength(0);line.append(t);}}
            out.add(line.toString());}
        return out;
    }
    private void drawDialog(Canvas c,float w,float h){
        float dp=ui.dp;ui.rect(c,0,0,w,h,0x9a05070a);
        float pw=Math.min(w-dp*40,kind==PROMPT?dp*600:dp*460);
        ArrayList<String> msg=kind==CHOOSE?new ArrayList<>():wrap(message,13.5f,pw-dp*44);
        float msgH=msg.size()*dp*20;
        int rows=kind==CHOOSE?Math.max(2,Math.min(items.length,(int)((h-dp*(64+56+40))/(dp*44)))):0;   // as many rows as the screen allows; the rest scroll
        boolean keys=kind==PROMPT;float kh=0,gap=dp*5;
        float ph;
        if(kind==CHOOSE)ph=dp*(64+56)+rows*dp*44;
        else if(kind==CONFIRM)ph=dp*(64+70)+msgH;
        else{kh=Math.min(dp*38,(h-dp*(64+60+60+40)-msgH)/5-gap);ph=dp*(64+60+60)+msgH+5*(kh+gap);}
        float l=(w-pw)/2,t=Math.max(dp*12,(h-ph)/2),rr=l+pw,b=t+ph;
        ui.round(c,l,t+dp*4,rr,b+dp*8,dp*14,0x70000000);
        ui.panel(c,l,t,rr,b,dp*12);ui.hit(l,t,rr,b,H_PANEL);
        ui.rect(c,l+dp*12,t,rr-dp*12,t+dp*2,0xffe8b84a);   // a thin gold rule along the top, like the title screen
        ui.text(c,ui.ellipsize(title,17,2,pw-dp*40),l+dp*20,t+dp*38,17,Ui.TEXT,2,-1);
        float y=t+dp*58;
        if(kind==CHOOSE){
            float viewH=rows*dp*44,content=items.length*dp*44;scroll=Math.max(0,Math.min(Math.max(0,content-viewH),scroll));
            c.save();c.clipRect(l,y,rr,y+viewH);
            for(int i=0;i<items.length;i++){float it=y+i*dp*44-scroll;if(it+dp*44<y||it>y+viewH)continue;
                r.set(l+dp*16,it+dp*3,rr-dp*16,it+dp*41);ui.button(c,r,i==selected?Icons.CHECK:-1,items[i],i==selected?Ui.ACTIVE:Ui.NORMAL);
                if(it>=y-dp*20&&it+dp*41<=y+viewH+dp*20)ui.hit(r,H_ITEM+i);}
            c.restore();
            if(content>viewH){float bh=viewH*viewH/content,by=y+(viewH-bh)*scroll/Math.max(1,content-viewH);ui.round(c,rr-dp*9,by,rr-dp*5,by+bh,dp*2,0x80a6b0bd);}
            r.set(rr-dp*130,b-dp*48,rr-dp*16,b-dp*12);ui.button(c,r,-1,"Cancel",Ui.GHOST);ui.hit(r,H_CANCEL);
        }else if(kind==CONFIRM){
            for(String ln:msg){ui.text(c,ln,l+dp*22,y+dp*14,13.5f,Ui.TEXT_2);y+=dp*20;}
            r.set(rr-dp*150,b-dp*52,rr-dp*16,b-dp*14);ui.button(c,r,-1,okLabel,Ui.PRIMARY);ui.hit(r,H_OK);
            r.set(rr-dp*280,b-dp*52,rr-dp*162,b-dp*14);ui.button(c,r,-1,"Cancel",Ui.GHOST);ui.hit(r,H_CANCEL);
        }else{
            for(String ln:msg){ui.text(c,ln,l+dp*22,y+dp*12,13.5f,Ui.TEXT_2);y+=dp*20;}
            y+=dp*4;
            // the field with a blinking caret (long text shows its end)
            r.set(l+dp*16,y,rr-dp*16,y+dp*42);ui.round(c,r,dp*6,Ui.CANVAS);ui.outline(c,r,dp*6,0xb03d8bfd,dp*1.2f);
            String s=text.toString();float max=r.width()-dp*34;
            if(ui.measure(s,15,1)>max){int k=0;while(k<s.length()&&ui.measure("…"+s.substring(k),15,1)>max)k++;s="…"+s.substring(k);}
            boolean blink=(System.currentTimeMillis()/500)%2==0;
            ui.textMid(c,s+(blink?"|":" "),r.left+dp*12,r.centerY(),15,Ui.TEXT,1,-1);
            // the on-screen keyboard (for the controller; a real keyboard just types)
            float ky=y+dp*54;
            String[] rows2=numeric?NUM_ROWS:ROWS;int cols=numeric?3:10;float kw=(pw-dp*32-gap*(cols-1))/cols;
            for(int row=0;row<rows2.length;row++){String keys2=rows2[row];
                for(int i=0;i<keys2.length();i++){char ch=keys2.charAt(i);if(shift)ch=Character.toUpperCase(ch);
                    r.set(l+dp*16+i*(kw+gap),ky+row*(kh+gap),l+dp*16+i*(kw+gap)+kw,ky+row*(kh+gap)+kh);ui.button(c,r,-1,String.valueOf(ch),Ui.NORMAL);ui.hit(r,H_KEY+ch);}}
            float by=ky+4*(kh+gap),bw=(pw-dp*32-gap*4)/5;
            String[] labels={numeric?"":shift?"ABC":"abc","Space","⌫","Cancel","OK"};int[] ids={H_KEY+1,H_KEY+' ',H_KEY+8,H_CANCEL,H_OK};
            for(int i=0;i<5;i++){if(labels[i].isEmpty())continue;float kx=l+dp*16+i*(bw+gap);r.set(kx,by,kx+bw,by+kh);
                ui.button(c,r,-1,labels[i],i==4?Ui.PRIMARY:i==3?Ui.GHOST:shift&&i==0?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,ids[i]);}
            ui.text(c,"Type with the keyboard, or pick keys with the cursor · Enter to finish · Esc to cancel",l+dp*18,b-dp*14,10.5f,Ui.TEXT_3);
        }
    }

    // ---------------------------------------------------------------- touch (the cursor's clicks)
    @Override public boolean onTouchEvent(MotionEvent e){
        if(kind==NONE)return false;
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){downY=y;downHit=ui.hitAt(x,y);scrolling=false;return true;}
        if(a==MotionEvent.ACTION_MOVE){if(kind==CHOOSE&&(scrolling||Math.abs(y-downY)>ui.dp*8)){scrolling=true;scroll-=y-downY;downY=y;}return true;}
        if(a==MotionEvent.ACTION_UP){if(!scrolling){int id=ui.hitAt(x,y);if(id==downHit)tap(id);}return true;}
        return true;
    }
    @Override public void pointerPan(float dx,float dy){if(kind==CHOOSE)scroll+=dy;}
    @Override public boolean pointerZoom(float steps,float x,float y){if(kind==CHOOSE)scroll-=steps*ui.dp*44;return true;}
    private void tap(int id){
        if(id<0){dismiss();return;}   // outside the panel
        if(id==H_PANEL)return;
        if(id==H_CANCEL){dismiss();return;}
        if(kind==CHOOSE&&id>=H_ITEM&&id<H_ITEM+items.length){java.util.function.IntConsumer cb=onPick;onPick=null;Runnable a=after;after=null;dismiss();if(cb!=null)cb.accept(id-H_ITEM);if(a!=null)a.run();return;}
        if(kind==CONFIRM&&id==H_OK){enter();return;}
        if(kind==PROMPT){
            if(id==H_OK){finishPrompt();return;}
            if(id==H_KEY+1){shift=!shift;return;}
            if(id==H_KEY+8){backspace();return;}
            if(id>=H_KEY&&id<H_KEY+256&&text.length()<MAX_TEXT){text.append((char)(id-H_KEY));if(shift)shift=false;}
        }
    }
    private void finishPrompt(){Text cb=onText;onText=null;String s=text.toString();Runnable a=after;after=null;dismiss();if(cb!=null)cb.got(s);if(a!=null)a.run();}
}
