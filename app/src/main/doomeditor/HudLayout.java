package com.retro.doomeditor;

import org.json.JSONObject;

/**
 * Where every on-screen control and HUD element sits in Play mode, edited in the UI Editor.
 * Positions are stored relative to the nearest screen edge or centre (in dp), so a layout made on one
 * phone still fits another. Elements that were never moved use the built-in default spot.
 */
final class HudLayout {
    static final int ATTACK=0,CAST=1,JUMP=2,USE=3,POTION=4,BAG=5,MENU=6,JOYSTICK=7,QUEST=8,MESSAGE=9,STATUS=10,MINIMAP=11,N=12;
    static final String[] NAMES={"Attack","Cast spell","Jump","Use","Potion","Bag","Pause menu","Joystick","Quest tracker","Messages","Status bar","Minimap"};
    static final String[] KEYS={"attack","cast","jump","use","potion","bag","menu","joystick","quest","message","status","minimap"};
    /** Base radius (dp) of round controls. */
    static final float[] RADIUS={38,28,24,25,24,22,18,56,0,0,0,0};
    static final float BAR_H=52;

    static final class Elem {boolean custom;float ax,ay,ox,oy;float scale=1,alpha=1;boolean visible=true;}
    final Elem[] e=new Elem[N];
    /** Joystick: false = appears wherever the thumb lands on its half of the screen, true = fixed spot. */
    boolean joyFixed;

    HudLayout(){for(int i=0;i<N;i++)e[i]=new Elem();}
    static boolean round(int id){return id<=JOYSTICK;}
    static boolean movable(int id){return id!=STATUS;}
    float barH(float dp){return e[STATUS].visible?BAR_H*dp:0;}
    float radius(int id,float dp){return RADIUS[id]*dp*e[id].scale;}
    /** Size of box elements (quest tracker, messages) in px. */
    float boxW(int id,float w,float dp){float s=e[id].scale;if(id==MINIMAP)return dp*104*s;return id==QUEST?Math.min(dp*260,w*.32f)*s:dp*260*s;}
    float boxH(int id,float dp){float s=e[id].scale;if(id==MINIMAP)return dp*104*s;return id==QUEST?dp*44*s:dp*24*s;}

    /** Centre of an element on a w x h screen. */
    void center(int id,float w,float h,float dp,boolean caster,float[] out){
        Elem el=e[id];
        if(el.custom){out[0]=el.ax*w+el.ox*dp;out[1]=el.ay*h+el.oy*dp;clamp(id,w,h,dp,out);return;}
        float bar=BAR_H*dp;   // defaults leave room for the status bar even when it is hidden
        switch(id){
            // right-hand cluster measured from the screen edge and the top of the status bar (dp):
            // Attack in the corner, Use and Potion stacked to its left, Jump above, Bag at the top right of the group
            case ATTACK:out[0]=w-73*dp;out[1]=h-bar-68*dp;break;
            case CAST:out[0]=w-148*dp;out[1]=h-bar-42*dp;break;
            case USE:out[0]=w-(caster?205:140)*dp;out[1]=h-bar-(caster?34:47)*dp;break;
            case POTION:out[0]=w-(caster?150:145)*dp;out[1]=h-bar-(caster?108:101)*dp;break;
            case JUMP:out[0]=w-105*dp;out[1]=h-bar-140*dp;break;
            case BAG:out[0]=w-33*dp;out[1]=h-bar-148*dp;if(e[MINIMAP].visible)out[1]=Math.max(out[1],16*dp+boxH(MINIMAP,dp)+36*dp);break;
            case MENU:out[0]=14*dp+18*dp;out[1]=14*dp+18*dp;break;
            case JOYSTICK:out[0]=120*dp;out[1]=h-bar-110*dp;break;
            case QUEST:{float qw=boxW(QUEST,w,dp),right=e[MINIMAP].visible?w-16*dp-boxW(MINIMAP,w,dp)-14*dp:w-70*dp;out[0]=right-qw*.5f;out[1]=14*dp+boxH(QUEST,dp)*.5f;break;}
            case MINIMAP:{float m=boxW(MINIMAP,w,dp);out[0]=w-16*dp-m*.5f;out[1]=16*dp+m*.5f;break;}
            case MESSAGE:out[0]=62*dp+boxW(MESSAGE,w,dp)*.5f;out[1]=13*dp+boxH(MESSAGE,dp)*.5f;break;
            default:out[0]=w*.5f;out[1]=h-bar*.5f;
        }
    }
    private void clamp(int id,float w,float h,float dp,float[] p){
        float hx,hy;if(round(id)){hx=hy=Math.min(radius(id,dp),Math.min(w,h)*.45f);}else{hx=boxW(id,w,dp)*.5f;hy=boxH(id,dp)*.5f;}
        p[0]=Math.max(hx,Math.min(w-hx,p[0]));p[1]=Math.max(hy,Math.min(h-hy,p[1]));
    }
    /** Moves an element so its centre is at (cx,cy), anchored to the nearest edge or the middle. */
    void place(int id,float cx,float cy,float w,float h,float dp){
        Elem el=e[id];el.custom=true;
        el.ax=cx<w/3f?0:cx>w*2/3f?1:.5f;el.ay=cy<h/3f?0:cy>h*2/3f?1:.5f;
        el.ox=(cx-el.ax*w)/dp;el.oy=(cy-el.ay*h)/dp;
        if(id==JOYSTICK)joyFixed=true;
    }
    void resetElem(int id){Elem el=e[id];el.custom=false;el.scale=1;el.alpha=1;el.visible=true;if(id==JOYSTICK)joyFixed=false;}
    void resetAll(){for(int i=0;i<N;i++)resetElem(i);}

    static final String[] PRESETS={"Default","Left-handed","Compact","Big buttons","Minimal HUD"};
    /** Applies a ready-made layout for a w x h screen. */
    void preset(int which,float w,float h,float dp,boolean caster){
        resetAll();float[] pt=new float[2];
        switch(which){
            case 1:for(int i=0;i<N;i++){if(!movable(i))continue;center(i,w,h,dp,caster,pt);place(i,w-pt[0],pt[1],w,h,dp);}
                joyFixed=false;e[JOYSTICK].custom=true;break;   // joystick floats on the right half, buttons on the left
            case 2:for(int i=0;i<=MENU;i++)e[i].scale=.8f;for(int i:new int[]{CAST,JUMP,USE,POTION}){center(i,w,h,dp,caster,pt);center(ATTACK,w,h,dp,caster,tmp);
                    place(i,tmp[0]+(pt[0]-tmp[0])*.82f,tmp[1]+(pt[1]-tmp[1])*.82f,w,h,dp);}
                e[QUEST].scale=.85f;e[MESSAGE].scale=.9f;break;
            case 3:for(int i=0;i<=POTION;i++)e[i].scale=1.25f;for(int i:new int[]{CAST,JUMP,USE,POTION}){center(i,w,h,dp,caster,pt);center(ATTACK,w,h,dp,caster,tmp);
                    place(i,tmp[0]+(pt[0]-tmp[0])*1.28f,tmp[1]+(pt[1]-tmp[1])*1.28f,w,h,dp);}break;
            case 4:e[QUEST].visible=false;e[MINIMAP].visible=false;for(int i=0;i<=MENU;i++)e[i].alpha=.65f;e[STATUS].alpha=.8f;break;
            default:
        }
    }
    private final float[] tmp=new float[2];

    // ------------------------------------------------------------------ json
    JSONObject toJson()throws Exception{
        JSONObject o=new JSONObject();o.put("joyFixed",joyFixed);
        for(int i=0;i<N;i++){Elem el=e[i];JSONObject j=new JSONObject();j.put("custom",el.custom);j.put("ax",(double)el.ax);j.put("ay",(double)el.ay);j.put("ox",(double)el.ox);j.put("oy",(double)el.oy);
            j.put("scale",(double)el.scale);j.put("alpha",(double)el.alpha);j.put("visible",el.visible);o.put(KEYS[i],j);}
        return o;
    }
    static HudLayout fromJson(JSONObject o){
        HudLayout l=new HudLayout();if(o==null)return l;
        l.joyFixed=o.optBoolean("joyFixed",false);
        for(int i=0;i<N;i++){JSONObject j=o.optJSONObject(KEYS[i]);if(j==null)continue;Elem el=l.e[i];
            el.custom=j.optBoolean("custom",false);el.ax=(float)j.optDouble("ax",0);el.ay=(float)j.optDouble("ay",0);el.ox=(float)j.optDouble("ox",0);el.oy=(float)j.optDouble("oy",0);
            el.scale=(float)Math.max(.5,Math.min(2,j.optDouble("scale",1)));el.alpha=(float)Math.max(.15,Math.min(1,j.optDouble("alpha",1)));el.visible=j.optBoolean("visible",true);}
        return l;
    }
}
