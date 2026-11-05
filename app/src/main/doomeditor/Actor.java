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
    /** Animation chosen by the FSM (null = automatic: idle/walk/attack/pain/death picked by the engine). */
    String animClip;float animTime,animSpeed=1;int animLoop=-1,lastAnimFrame=-1;
    FsmRuntime fsm;
    /** Index of the placed thing this actor came from (-1 for the player, projectiles and spawned actors). */
    int thingIndex=-1;
    float emitAcc;
    /** Light preset set by the Set Light action (-1 = the entity type's). */
    int lightOverride=-1;
    // Projectiles
    int projectileType=-1;Actor owner;int dmgMin,dmgMax;
    int maxHealth;
    /** Projectile attack data: to-hit bonus (or auto-hit), damage dice and type, area radius and save DC. */
    int pAttack,pType,pDC;Rpg.Dice pDice;boolean pAuto;float pRadius;
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
