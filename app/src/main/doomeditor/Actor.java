package com.retro.doomeditor;

/** A live entity in the game world. */
final class Actor {
    final EntityDef def;
    float x,y,z,angle,pitch,vx,vy,vz,wishX,wishY,radius,height,speed,bob;
    float spawnX,spawnY;
    int health,sprite;
    boolean alive=true,removed,countedItem,solid,shootable,visible=true,invulnerable,noGravity,onGround,blocked,wishActive,isPlayer,touching,corpse;
    float painTimer,attackTimer,walkPhase,moveDist,deadTime,reactTimer;
    int sectorHint=-1;
    Actor target;
    FsmRuntime fsm;
    // Projectiles
    int projectileType=-1;Actor owner;int dmgMin,dmgMax;
    Actor(EntityDef d){def=d;}
    void stop(){vx=vy=0;wishX=wishY=0;wishActive=true;}
    /** Animation frame used by the renderer. */
    int frame(){
        if(!alive)return Sprites.F_DEAD;
        if(painTimer>0)return Sprites.F_PAIN;
        if(attackTimer>0)return Sprites.F_ATTACK;
        if(Math.hypot(vx,vy)>.25f)return ((int)(walkPhase*2.2f)&1)==0?Sprites.F_WALK1:Sprites.F_WALK2;
        return Sprites.F_IDLE;
    }
}
