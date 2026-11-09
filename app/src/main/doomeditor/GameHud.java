package com.retro.doomeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Draws the in-game controls and HUD (status bar, quest tracker, messages) from a {@link HudLayout}.
 * Used by Play mode and by the UI Editor's live preview, so what you arrange is exactly what you get.
 * After each draw, {@link #rects} holds every element's on-screen bounds.
 */
final class GameHud {
    private final Ui ui;private final Paint p;
    HudLayout layout;
    UiTheme theme=new UiTheme();
    final RectF[] rects=new RectF[HudLayout.N];
    final boolean[] drawn=new boolean[HudLayout.N];
    private final float[] pt=new float[2];
    /** Touch ids for the controls in Play mode (index = HudLayout element), or null to register no touch targets. */
    int[] hitIds;

    GameHud(Ui ui,HudLayout layout){this.ui=ui;p=ui.p;this.layout=layout;for(int i=0;i<rects.length;i++)rects[i]=new RectF();}
    private float dp(float v){return ui.dp(v);}
    static int alpha(int col,float a){int al=Math.round((col>>>24)*Math.max(0,Math.min(1,a)));return (al<<24)|(col&0xffffff);}

    /**
     * Round buttons. {@code down[i]} lights a pressed control; {@code editing} shows every control
     * (Cast even for heroes without spells, hidden ones faintly) for the UI Editor.
     */
    /** UI Editor: preview the buttons as a spellcaster (true) or not (false); null = the hero's own spells decide. */
    Boolean previewCaster;
    void drawControls(Canvas c,float w,float h,Hero hero,boolean[] down,boolean editing){
        boolean caster=previewCaster!=null?previewCaster:hero!=null&&!hero.spells.isEmpty();
        java.util.Arrays.fill(drawn,HudLayout.ATTACK,HudLayout.JOYSTICK+1,false);
        int pots=hero==null?0:hero.potionCount();
        for(int id=HudLayout.ATTACK;id<=HudLayout.MENU;id++){
            HudLayout.Elem el=layout.e[id];
            if(id==HudLayout.CAST&&!caster)continue;   // only spellcasters have a Cast button (the other buttons move to make room for it)
            if(!el.visible&&!editing)continue;
            layout.center(id,w,h,ui.dp,caster,pt);float x=pt[0],y=pt[1],rad=layout.radius(id,ui.dp);
            float a=el.visible?el.alpha:.25f;
            boolean dn=down!=null&&down[id];
            switch(id){
                case HudLayout.ATTACK:button(c,x,y,rad,0xffc8402e,Icons.SWORD,"ATTACK",dn,a);break;
                case HudLayout.CAST:{boolean can=hero!=null&&caster&&hero.sp>=Rpg.spellCost(hero.currentSpell().level);button(c,x,y,rad,can||editing?0xdd6a3ab8:0x88403a50,Icons.SPARKLE,"CAST",dn,a);break;}
                case HudLayout.JUMP:button(c,x,y,rad,0xcc2a3038,Icons.ARROW_UP,"JUMP",dn,a);break;
                case HudLayout.USE:button(c,x,y,rad,0xcc2a3038,Icons.DOOR,"USE",dn,a);break;
                case HudLayout.POTION:button(c,x,y,rad,pots>0||editing?0xcc8a2030:0x882a3038,Icons.POTION,editing&&pots==0?"x2":pots>0?"x"+pots:"0",dn,a);break;
                case HudLayout.BAG:theme.button(c,p,x,y,rad,0xcc2a3038,false,ui.dp,a);ui.icon(c,Icons.BACKPACK,x,y,20*el.scale,alpha(0xffffffff,a));break;
                default:theme.button(c,p,x,y,rad,0x99101318,false,ui.dp,a);ui.icon(c,Icons.CANCEL,x,y,18*el.scale,alpha(0xddffffff,a));
            }
            float pad=dp(4);rects[id].set(x-rad-pad,y-rad-pad,x+rad+pad,y+rad+pad);drawn[id]=true;
            if(hitIds!=null&&el.visible)ui.hit(rects[id],hitIds[id]);
        }
    }
    private void button(Canvas c,float x,float y,float rad,int col,int icon,String label,boolean down,float a){
        boolean big=rad>dp(30);
        theme.button(c,p,x,y,rad,col,down,ui.dp,a);
        float k=rad/(big?dp(38):dp(25));
        ui.icon(c,icon,x,y-(big?dp(5):dp(3))*k,(big?24:16)*k,alpha(0xffffffff,a));ui.text(c,label,x,y+rad*.62f,(big?10:8)*Math.max(.8f,Math.min(1.4f,k)),alpha(0xffffffff,a),2,0);
    }

    /** Joystick base and knob; {@code alpha} fades it after release. In fixed mode the base always shows faintly. */
    void drawJoystick(Canvas c,float ox,float oy,float kx,float ky,float alpha){
        float a=alpha*layout.e[HudLayout.JOYSTICK].alpha;if(a<=0)return;
        float radius=layout.radius(HudLayout.JOYSTICK,ui.dp);p.setStyle(Paint.Style.FILL);
        p.setColor(((int)(a*110)<<24)|(theme.panel&0xffffff));c.drawCircle(ox,oy,radius,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(((int)(a*140)<<24)|(theme.accent&0xffffff));c.drawCircle(ox,oy,radius,p);p.setStyle(Paint.Style.FILL);
        p.setColor(((int)(a*235)<<24)|(theme.lightPanel()?0xf0e8d8:theme.text&0xffffff));c.drawCircle(kx,ky,radius*.4f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.2f));p.setColor(((int)(a*200)<<24)|(theme.accent&0xffffff));c.drawCircle(kx,ky,radius*.4f,p);p.setStyle(Paint.Style.FILL);
    }
    /** True when the floating joystick lives on the right half (left-handed layouts). */
    boolean joyOnRight(float w,float h){layout.center(HudLayout.JOYSTICK,w,h,ui.dp,false,pt);return pt[0]>w*.5f;}
    float[] joyCenter(float w,float h){float[] o=new float[2];layout.center(HudLayout.JOYSTICK,w,h,ui.dp,false,o);return o;}

    /** Status bar, XP strip, quest tracker and messages. */
    void drawStatus(Canvas c,float w,float h,World world,boolean editing){
        Hero hero=world==null?null:world.hero;if(hero==null)return;
        HudLayout.Elem st=layout.e[HudLayout.STATUS];
        float barH=dp(HudLayout.BAR_H),top=h-barH;
        rects[HudLayout.STATUS].set(0,top-dp(3),w,h);drawn[HudLayout.STATUS]=true;
        if(st.visible||editing){
            float a=st.visible?st.alpha:.25f;
            theme.panel(c,p,0,top,w,h,ui.dp,a,true);
            int T1=theme.text,T2=theme.textDim(),T3=theme.textFaint();boolean light=theme.lightPanel();int goldC=light?0xff8a5a10:0xffffd040,spellC=light?0xff5a2a9a:0xffc8b0ff;
            float x=dp(12),cy=top+barH*.5f;
            float bs=dp(36);ui.round(c,x,cy-bs*.5f,x+bs,cy+bs*.5f,dp(8),alpha(UiTheme.shade(theme.accent,.32f),a));ui.outline(c,x,cy-bs*.5f,x+bs,cy+bs*.5f,dp(8),alpha(theme.accent,a*.8f),dp(1.2f));ui.textMid(c,String.valueOf(hero.level),x+bs*.5f,cy-dp(3),15,alpha(0xffffffff,a),2,0);ui.textMid(c,"LV",x+bs*.5f,cy+dp(11),7.5f,alpha(theme.accent,a),2,0);
            x+=bs+dp(10);
            float bw=Math.min(dp(170),w*.2f);
            int hp=world.player.health;
            ui.text(c,hero.cls.name.toUpperCase(),x,top+dp(13),9,alpha(T3,a),2,-1);
            theme.bar(c,p,ui,x,top+dp(17),x+bw,top+dp(31),hp/(float)Math.max(1,hero.maxHp),hp*4<=hero.maxHp?0xffe0402a:0xffb8282a,Math.max(0,hp)+" / "+hero.maxHp+" HP",ui.dp,a);
            if(hero.maxSp>0)theme.bar(c,p,ui,x,top+dp(34),x+bw,top+dp(44),hero.sp/(float)Math.max(1,hero.maxSp),0xff3a5ad0,hero.sp+" / "+hero.maxSp+" SP",ui.dp,a);
            else ui.text(c,"AC "+hero.ac()+(hero.wardTime>0?" (shielded)":""),x,top+dp(43),11,alpha(T2,a),2,-1);
            float xf=hero.xpFraction();ui.rect(c,0,top-dp(3),w,top,alpha(0xff0e1013,a));ui.rect(c,0,top-dp(3),w*xf,top,alpha(0xffd8a830,a));
            x+=bw+dp(18);
            Rpg.Item wpn=hero.weaponOrFists();
            ui.text(c,"WEAPON",x,top+dp(13),9,alpha(T3,a),2,-1);ui.text(c,wpn.name,x,top+dp(29),13,alpha(T1,a),2,-1);
            ui.text(c,wpn.damage+Rpg.signed(hero.damageBonus(wpn))+" "+Rpg.DAMAGE_TYPES[wpn.damageType].toLowerCase()+"  ·  "+Rpg.signed(hero.attackBonus(wpn))+" to hit",x,top+dp(43),10,alpha(T2,a),1,-1);
            if(!hero.spells.isEmpty()&&w>dp(720)){float sx=x+Math.max(dp(170),ui.measure(wpn.name,13,2)+dp(20));Rpg.Spell sp=hero.currentSpell();
                ui.text(c,"SPELL",sx,top+dp(13),9,alpha(T3,a),2,-1);ui.text(c,sp.name,sx,top+dp(29),13,alpha(spellC,a),2,-1);ui.text(c,(sp.level==0?"cantrip":Rpg.spellCost(sp.level)+" SP")+"  ·  "+sp.dice,sx,top+dp(43),10,alpha(T2,a),1,-1);}
            float rx=w-dp(14);int[] kc={0xffc87a3a,0xffd0d4dc,0xffe8b830};
            for(int k=2;k>=0;k--){boolean has=hero.hasKey(k);float kx=rx-dp(14);ui.icon(c,Icons.TAG,kx,cy,15,alpha(has?kc[k]:(light?0x30000000:0x26ffffff),a));rx-=dp(20);}
            rx-=dp(8);String g=String.valueOf(hero.gold);ui.text(c,g,rx,cy+dp(5),15,alpha(goldC,a),2,1);float gw=ui.measure(g,15,2);ui.icon(c,Icons.COIN,rx-gw-dp(12),cy,16,alpha(goldC,a));
        }
        // quest tracker
        World.Quest q=null;for(World.Quest qq:world.quests.values())if(qq.state==World.Q_ACTIVE)q=qq;
        HudLayout.Elem qe=layout.e[HudLayout.QUEST];
        String qt=q!=null?q.title:editing?"Example quest":null,qx2=q!=null?q.text:"Shown while a quest is active";
        if(qt!=null&&(qe.visible||editing)){float a=qe.visible?qe.alpha:.25f,s=qe.scale;
            layout.center(HudLayout.QUEST,w,h,ui.dp,false,pt);float qw=layout.boxW(HudLayout.QUEST,w,ui.dp),qh=layout.boxH(HudLayout.QUEST,ui.dp),qx=pt[0]-qw*.5f,qy=pt[1]-qh*.5f;
            theme.panel(c,p,qx,qy,qx+qw,qy+qh,ui.dp,a*.92f,false);
            ui.icon(c,Icons.SCROLL,qx+dp(16)*s,qy+dp(15)*s,13*s,alpha(theme.lightPanel()?UiTheme.shade(theme.accent,.7f):theme.accent,a));ui.text(c,ui.ellipsize(qt,11.5f*s,2,qw-dp(36)*s),qx+dp(28)*s,qy+dp(19)*s,11.5f*s,alpha(theme.text,a),2,-1);
            ui.text(c,ui.ellipsize(qx2,10*s,1,qw-dp(20)*s),qx+dp(10)*s,qy+dp(35)*s,10*s,alpha(theme.textDim(),a),1,-1);
            rects[HudLayout.QUEST].set(qx,qy,qx+qw,qy+qh);drawn[HudLayout.QUEST]=true;}
        // messages
        HudLayout.Elem me=layout.e[HudLayout.MESSAGE];
        String msg=world.messageTime>0?world.message:editing?"Messages appear here":null;
        if(msg!=null&&(me.visible||editing)){float a=(world.messageTime>0?Math.min(1,world.messageTime*2):1)*(me.visible?me.alpha:.25f),s=me.scale;
            layout.center(HudLayout.MESSAGE,w,h,ui.dp,false,pt);float bw=layout.boxW(HudLayout.MESSAGE,w,ui.dp),bh=layout.boxH(HudLayout.MESSAGE,ui.dp),l=pt[0]-bw*.5f,t=pt[1]-bh*.5f,base=t+dp(17)*s;
            ui.text(c,msg,l+dp(1),base+dp(1),13*s,((int)(a*200)<<24),1,-1);ui.text(c,msg,l,base,13*s,((int)(a*255)<<24)|0xffe2e2,1,-1);
            rects[HudLayout.MESSAGE].set(l,t,l+bw,t+bh);drawn[HudLayout.MESSAGE]=true;}
        drawMinimap(c,w,h,world,editing);
        if(world.centerTime>0&&!world.center.isEmpty()){float a=Math.min(1,world.centerTime*2);ui.text(c,world.center,w*.5f+dp(2),h*.36f+dp(2),26,((int)(a*200)<<24),2,0);ui.text(c,world.center,w*.5f,h*.36f,26,((int)(a*255)<<24)|0xffe8b0,2,0);}
    }
    private final android.graphics.Path mp=new android.graphics.Path(),clip=new android.graphics.Path();
    /** Round minimap that turns with the player: sectors near the hero, doors, exits, monsters and NPCs. */
    private void drawMinimap(Canvas c,float w,float h,World world,boolean editing){
        HudLayout.Elem el=layout.e[HudLayout.MINIMAP];if(!el.visible&&!editing)return;
        Level lv=world.level;Actor pl=world.player;if(lv==null||pl==null)return;
        float a=el.visible?el.alpha:.25f;layout.center(HudLayout.MINIMAP,w,h,ui.dp,false,pt);
        float size=layout.boxW(HudLayout.MINIMAP,w,ui.dp),rad=size*.5f,cx=pt[0],cy=pt[1];
        float range=9f,sc=rad/range;float ang=world.camAngle();float cs=(float)Math.cos(ang),sn=(float)Math.sin(ang);
        theme.panel(c,p,cx-rad-dp(3),cy-rad-dp(3),cx+rad+dp(3),cy+rad+dp(3),ui.dp,a*.9f,false);
        int save=c.save();clip.reset();clip.addCircle(cx,cy,rad,android.graphics.Path.Direction.CW);c.clipPath(clip);
        p.setStyle(Paint.Style.FILL);p.setColor(alpha(0xd0101216,a));c.drawCircle(cx,cy,rad,p);
        int here=lv.sectorAt(pl.x,pl.y);
        for(int si=0;si<lv.sectors.size();si++){Level.Sector s=lv.sectors.get(si);if(s.loop.size()<3)continue;
            mp.reset();boolean near=false;
            for(int k=0;k<s.loop.size();k++){Level.Vertex v=lv.vertices.get(s.loop.get(k));float dx=v.x-pl.x,dy=v.y-pl.y;
                if(Math.abs(dx)<range*1.6f&&Math.abs(dy)<range*1.6f)near=true;
                float vx=-dx*sn+dy*cs,vz=dx*cs+dy*sn;float sx=cx+vx*sc,sy=cy-vz*sc;if(k==0)mp.moveTo(sx,sy);else mp.lineTo(sx,sy);}
            if(!near)continue;mp.close();
            int fill=si==here?0xff3a4250:s.special==Level.SP_EXIT?0xff2f5a3a:s.special==Level.SP_DOOR?0xff5a4a2c:s.special==Level.SP_DAMAGE?0xff5a2a2a:0xff262b33;
            p.setStyle(Paint.Style.FILL);p.setColor(alpha(fill,a));c.drawPath(mp,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.2f));p.setColor(alpha(theme.lightPanel()?0xff8a8f99:0xff6c7480,a));c.drawPath(mp,p);}
        p.setStyle(Paint.Style.FILL);
        for(Actor ac:world.actors){if(ac==pl||ac.removed||!ac.alive||ac.def==null||ac.projectileType>=0||!ac.visible)continue;
            int col=ac.def.kind==EntityDef.MONSTER?0xffe05050:ac.def.kind==EntityDef.NPC?0xff60d080:ac.def.kind==EntityDef.PICKUP?0xffe8c050:0;if(col==0)continue;
            float dx=ac.x-pl.x,dy=ac.y-pl.y;float vx=-dx*sn+dy*cs,vz=dx*cs+dy*sn;
            p.setColor(alpha(col,a));c.drawCircle(cx+vx*sc,cy-vz*sc,dp(ac.def.kind==EntityDef.PICKUP?1.8f:2.6f),p);}
        c.restoreToCount(save);
        // player arrow, always facing up
        mp.reset();mp.moveTo(cx,cy-dp(7));mp.lineTo(cx+dp(5),cy+dp(5));mp.lineTo(cx,cy+dp(2.5f));mp.lineTo(cx-dp(5),cy+dp(5));mp.close();
        p.setColor(alpha(theme.accent|0xff000000,a));c.drawPath(mp,p);
        ui.text(c,"N",cx-cs*(rad-dp(8)),cy+sn*(rad-dp(8))+dp(4),10,alpha(theme.textDim(),a),2,0);
        rects[HudLayout.MINIMAP].set(cx-rad,cy-rad,cx+rad,cy+rad);drawn[HudLayout.MINIMAP]=true;
    }
    private void bar(Canvas c,float l,float t,float r,float b,float frac,int col,String text,float a){
        ui.round(c,l,t,r,b,(b-t)*.5f,alpha(0xff0e1013,a));float f=Math.max(0,Math.min(1,frac));
        if(f>0)ui.round(c,l,t,l+Math.max(b-t,(r-l)*f),b,(b-t)*.5f,alpha(col,a));
        ui.outline(c,l,t,r,b,(b-t)*.5f,alpha(0x30ffffff,a),dp(1));
        if(text!=null)ui.textMid(c,text,(l+r)*.5f,(t+b)*.5f,10,alpha(0xffffffff,a),2,0);
    }
}
