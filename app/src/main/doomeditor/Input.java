package com.retro.doomeditor;

/** Player input for one frame: joystick axes, look deltas and button edges. */
final class Input {
    static final int FIRE=0,USE=1,JUMP=2,WEAPON=3,ALT=4,COUNT=5;
    float moveX,moveY,lookX,lookY;
    private final boolean[] down=new boolean[COUNT],was=new boolean[COUNT],tapped=new boolean[COUNT];
    void setButton(int b,boolean on){if(on&&!down[b])tapped[b]=true;down[b]=on;}
    boolean held(int b){return b>=0&&b<COUNT&&(down[b]||tapped[b]);}
    boolean pressed(int b){return b>=0&&b<COUNT&&tapped[b];}
    boolean released(int b){return b>=0&&b<COUNT&&was[b]&&!down[b];}
    /** Call after the world has processed a frame. */
    void endFrame(){for(int i=0;i<COUNT;i++){was[i]=down[i];tapped[i]=false;}lookX=lookY=0;}
    void clear(){for(int i=0;i<COUNT;i++){down[i]=was[i]=tapped[i]=false;}moveX=moveY=lookX=lookY=0;}
}
