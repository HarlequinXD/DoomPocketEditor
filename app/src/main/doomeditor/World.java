package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Doom-style game framework: actors with FSMs, grounded physics with steps and gravity, hitscan and
 * projectile weapons, doors, lifts, sector specials, pickups and HUD feedback. Entity behaviour comes
 * entirely from each entity type's FSM; the World supplies the engine services the actions call.
 */
final class World {
    // Physics constants (1 unit = one editor grid square).
    static final float STEP=.6f,GRAVITY=24f,EYE=1.15f,USE_RANGE=1.4f;
    static final int FIRE_OK=0,FIRE_COOLING=1,FIRE_NO_AMMO=2;
    static final int AM_BULLETS=0,AM_SHELLS=1,AM_ROCKETS=2;
    static final int[] AMMO_MAX={200,50,50};
    // Weapon table: ammo type (-1 none), ammo per shot, cooldown, damage min/max, pellets, spread, sound.
    static final String[] WEAPON_NAMES={"Fist","Pistol","Shotgun","Chaingun","Rocket Launcher"};
    static final int[] W_AMMO={-1,AM_BULLETS,AM_SHELLS,AM_BULLETS,AM_ROCKETS};
    static final float[] W_COOLDOWN={.45f,.38f,.95f,.12f,.8f};
    static final int[] W_DMIN={2,5,5,5,20},W_DMAX={20,15,15,15,160},W_PELLETS={1,1,7,1,1};
    static final float[] W_SPREAD={0,.02f,.1f,.05f,0};
    static final int[] W_SOUND={SoundFx.PUNCH,SoundFx.PISTOL,SoundFx.SHOTGUN,SoundFx.CHAINGUN,SoundFx.ROCKET};

    final Project project;
    final Level level;
    final ArrayList<Level.LineDef> lines;
    final ArrayList<Actor> actors=new ArrayList<>();
    private final ArrayList<Actor> spawned=new ArrayList<>();
    final ArrayList<Effect> effects=new ArrayList<>();
    final ArrayList<Mover> movers=new ArrayList<>();
    final HashMap<String,Float> globals=new HashMap<>();
    final Input input=new Input();
    final Random rng=new Random(1993);
    final SoundFx sfx;
    Actor player;
    float time;
    // Player inventory & HUD
    boolean[] weapons={true,true,false,false,false};
    final int[] ammo={50,0,0};
    boolean[] keys=new boolean[3];
    int weapon=1,pendingWeapon=-1,armor;
    float weaponCooldown,weaponFlash,weaponRaise,bobPhase,viewZ,damageFlash,pickupFlash,shakeTime,shakeStrength;
    int flashColor=0xffff0000;
    String message="",center="";
    float messageTime,centerTime;
    boolean levelComplete,restartRequested;
    int kills,totalKills,items,totalItems,secrets,totalSecrets;
    float completeTime;
    private final float[] doorOpen;   // open ceiling height per door sector
    private final boolean[] secretFound;
    private float damageTick;

    static final class Effect {int type;float x,y,z,t,life;Effect(int type,float x,float y,float z,float life){this.type=type;this.x=x;this.y=y;this.z=z;this.life=life;}}
    /** Moves a floor or ceiling to a target, optionally waits and returns (doors, lifts). */
    static final class Mover {int sector;boolean ceiling;float target,speed,wait,returnTo;boolean returning;float delay;}

    World(Project p,SoundFx sfx){this(p,sfx,false);}
    /** Editor camera body: walks the live level (no copy, no entities, doors left as drawn). */
    static World walker(Project p){return new World(p,null,true);}
    private long geoSig;
    final boolean live;
    /** Rebuilds collision lines if the live level's shape changed. */
    void syncGeometry(){long sig=Renderer.signature(level);if(sig!=geoSig){geoSig=sig;lines.clear();lines.addAll(PortalTopology.build(level));}}
    /** Physics only, for the editor camera. */
    void walkerTick(float dt){dt=Math.min(dt,.05f);time+=dt;syncGeometry();physics(player,dt);
        float target=player.z+EYE;if(player.noGravity)viewZ=target;else if(target>viewZ)viewZ+=Math.min(target-viewZ,Math.max(dt*4f,(target-viewZ)*dt*12f));else viewZ=target;}
    private World(Project p,SoundFx sfx,boolean live){
        project=p;this.sfx=sfx;this.live=live;
        level=live?p.level:p.level.copy();
        lines=PortalTopology.build(level);geoSig=Renderer.signature(level);
        doorOpen=new float[level.sectors.size()];secretFound=new boolean[level.sectors.size()];
        for(int i=0;i<level.sectors.size();i++){Level.Sector s=level.sectors.get(i);
            if(s.special==Level.SP_DOOR&&!live){doorOpen[i]=s.ceiling;s.ceiling=s.floor;}   // doors start closed
            if(s.special==Level.SP_SECRET)totalSecrets++;}
        EntityDef pd=p.playerDef();
        player=makeActor(pd,level.playerX,level.playerY,level.facing);player.isPlayer=true;
        player.z=floorAt(player.x,player.y);viewZ=player.z+EYE;
        actors.add(player);
        if(live){player.fsm=null;return;}
        for(Level.Thing t:level.things){EntityDef d=p.def(t.def);if(d==null||d.kind==EntityDef.PLAYER)continue;
            Actor a=makeActor(d,t.x,t.y,t.angle);a.z=floorAt(a.x,a.y);actors.add(a);
            if(d.countKill)totalKills++;if(d.countItem)totalItems++;}
        for(Actor a:actors)if(a.fsm!=null)a.fsm.start();
    }
    private Actor makeActor(EntityDef d,float x,float y,float angle){
        Actor a=new Actor(d);a.x=a.spawnX=x;a.y=a.spawnY=y;a.angle=angle;a.radius=d.radius;a.height=d.height;a.health=d.health;a.speed=d.speed;
        a.solid=d.solid;a.shootable=d.shootable;a.sprite=d.sprite;a.fsm=new FsmRuntime(d.fsm);
        return a;
    }

    // ------------------------------------------------------------------ frame
    void tick(float dt){
        dt=Math.min(dt,.05f);
        time+=dt;
        if(levelComplete){completeTime+=dt;input.endFrame();return;}
        for(int i=0;i<actors.size();i++){Actor a=actors.get(i);if(a.removed)continue;
            a.wishActive=false;
            if(a.fsm!=null)a.fsm.tick(this,a,dt);
            physics(a,dt);
            if(a.painTimer>0)a.painTimer-=dt;if(a.attackTimer>0)a.attackTimer-=dt;
            if(!a.alive)a.deadTime+=dt;
            if(a.projectileType>=0)projectileStep(a,dt);
        }
        if(!spawned.isEmpty()){for(Actor a:spawned){actors.add(a);if(a.fsm!=null)a.fsm.start();}spawned.clear();}
        touches();
        sectorSpecials(dt);
        moversStep(dt);
        for(int i=effects.size()-1;i>=0;i--){Effect e=effects.get(i);e.t+=dt;if(e.type==Sprites.FX_PUFF)e.z+=dt*.5f;if(e.t>=e.life)effects.remove(i);}
        for(int i=actors.size()-1;i>=0;i--)if(actors.get(i).removed)actors.remove(i);
        // weapon & view
        if(weaponCooldown>0)weaponCooldown-=dt;if(weaponFlash>0)weaponFlash-=dt;if(weaponRaise>0)weaponRaise-=dt*3;
        if(pendingWeapon>=0&&weaponCooldown<=0){weapon=pendingWeapon;pendingWeapon=-1;weaponRaise=1;}
        float speed=(float)Math.hypot(player.vx,player.vy);
        if(player.onGround)bobPhase+=dt*speed*1.6f;
        float target=player.z+EYE*(player.alive?1:.25f);
        if(target>viewZ)viewZ+=Math.min(target-viewZ,Math.max(dt*4f,(target-viewZ)*dt*12f));else viewZ=target;
        if(damageFlash>0)damageFlash-=dt*1.5f;if(pickupFlash>0)pickupFlash-=dt*2f;if(shakeTime>0)shakeTime-=dt;
        if(messageTime>0)messageTime-=dt;if(centerTime>0)centerTime-=dt;
        input.endFrame();
    }
    float bob(){float s=Math.min(1,(float)Math.hypot(player.vx,player.vy)/6f);return (float)Math.sin(bobPhase*Math.PI)*.06f*s;}
    float camX(){return player.x+(shakeTime>0?(rng.nextFloat()-.5f)*shakeStrength*.3f:0);}
    float camY(){return player.y+(shakeTime>0?(rng.nextFloat()-.5f)*shakeStrength*.3f:0);}
    float camZ(){return viewZ+bob();}

    // ------------------------------------------------------------------ physics
    int sectorIndex(Actor a){
        if(a.sectorHint>=0&&a.sectorHint<level.sectors.size()&&level.contains(level.sectors.get(a.sectorHint),a.x,a.y))return a.sectorHint;
        a.sectorHint=level.sectorAt(a.x,a.y);return a.sectorHint;
    }
    Level.Sector sector(int i){return i>=0&&i<level.sectors.size()?level.sectors.get(i):null;}
    float floorAt(float x,float y){int s=level.sectorAt(x,y);return s<0?0:level.sectors.get(s).floor;}
    float ceilAt(float x,float y){int s=level.sectorAt(x,y);return s<0?0:level.sectors.get(s).ceiling;}

    private void physics(Actor a,float dt){
        if(a.projectileType>=0||a.removed)return;
        // Movement intent -> velocity (with friction when no intent).
        if(a.alive){
            float accel=a.onGround?12f:2.5f;
            if(a.wishActive){a.vx+=(a.wishX-a.vx)*Math.min(1,accel*dt);a.vy+=(a.wishY-a.vy)*Math.min(1,accel*dt);}
            else if(a.onGround){float f=Math.max(0,1-10f*dt);a.vx*=f;a.vy*=f;}
        }else{float f=Math.max(0,1-8f*dt);a.vx*=f;a.vy*=f;}
        float mx=a.vx*dt,my=a.vy*dt;
        a.blocked=false;
        if(mx!=0||my!=0){
            int steps=Math.max(1,(int)Math.ceil(Math.hypot(mx,my)/(a.radius*.5f)));
            float sx=mx/steps,sy=my/steps;
            for(int i=0;i<steps;i++){
                if(tryMove(a,a.x+sx,a.y+sy))continue;
                a.blocked=true;
                if(tryMove(a,a.x+sx,a.y)){a.vy*=.2f;continue;}
                if(tryMove(a,a.x,a.y+sy)){a.vx*=.2f;continue;}
                a.vx=a.vy=0;break;
            }
            float d=(float)Math.hypot(mx,my);a.moveDist+=d;a.walkPhase+=d;
        }
        // Vertical: steps up instantly, gravity when above the floor.
        int si=sectorIndex(a);
        float floor=si<0?a.z:highestFloor(a,a.x,a.y),ceil=si<0?a.z+a.height+1:level.sectors.get(si).ceiling;
        if(a.noGravity){a.z+=a.vz*dt;a.vz*=Math.max(0,1-4*dt);if(a.z<floor)a.z=floor;a.onGround=a.z<=floor+.01f;}
        else if(a.z>floor+.001f||a.vz>0){
            a.vz-=GRAVITY*dt;a.z+=a.vz*dt;
            if(a.z<=floor){if(a.isPlayer&&a.vz<-8f)sound(SoundFx.OOF,a);a.z=floor;a.vz=0;a.onGround=true;}else a.onGround=false;
        }else{a.z=floor;a.vz=0;a.onGround=true;}
        if(a.z+a.height>ceil){a.z=Math.max(floor,ceil-a.height);if(a.vz>0)a.vz=0;}
    }
    /** Highest floor under the actor's footprint so it stands on ledges like Doom. */
    private float highestFloor(Actor a,float x,float y){
        float best=-1e9f;
        for(int k=0;k<5;k++){float px=x+(k==1?a.radius*.7f:k==2?-a.radius*.7f:0),py=y+(k==3?a.radius*.7f:k==4?-a.radius*.7f:0);
            int s=level.sectorAt(px,py);if(s>=0)best=Math.max(best,level.sectors.get(s).floor);}
        return best<-1e8f?a.z:best;
    }
    /** Tries to place the actor at (x,y); fails on walls, steps too high, low ceilings and solid actors. */
    boolean tryMove(Actor a,float x,float y){
        if(level.sectorAt(x,y)<0)return false;
        for(int i=0,n=lines.size();i<n;i++){
            Level.LineDef l=lines.get(i);Level.Vertex p=level.vertices.get(l.from),q=level.vertices.get(l.to);
            if(segDist(x,y,p.x,p.y,q.x,q.y)>=a.radius)continue;
            if(blocks(a,l))return false;
        }
        if(a.solid&&a.alive)for(Actor o:actors){
            if(o==a||!o.solid||!o.alive||o.removed||o==a.owner||a==o.owner)continue;
            float r=a.radius+o.radius,dx=x-o.x,dy=y-o.y;
            if(dx*dx+dy*dy<r*r&&(a.z<o.z+o.height&&o.z<a.z+a.height)){
                // allow moving away from an overlapping actor
                float ox=a.x-o.x,oy=a.y-o.y;if(dx*dx+dy*dy>ox*ox+oy*oy)continue;return false;}
        }
        a.x=x;a.y=y;return true;
    }
    private boolean blocks(Actor a,Level.LineDef l){
        if(l.back<0)return true;
        Level.Sector f=level.sectors.get(l.front),b=level.sectors.get(l.back);
        float lo=Math.max(f.floor,b.floor),hi=Math.min(f.ceiling,b.ceiling);
        if(hi-lo<a.height)return true;
        if(lo-a.z>STEP&&!a.noGravity)return true;
        if(hi<a.z+a.height)return true;
        if(!a.isPlayer&&a.alive&&a.projectileType<0&&a.z-Math.min(f.floor,b.floor)>STEP*1.5f)return true; // monsters avoid big drops
        return false;
    }
    static float segDist(float px,float py,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,l2=dx*dx+dy*dy;float t=l2<1e-6f?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/l2));
        float cx=ax+t*dx-px,cy=ay+t*dy-py;return (float)Math.sqrt(cx*cx+cy*cy);
    }
    /** Ray/segment intersection: returns t along the ray (0..1) or -1. */
    static float segHit(float x0,float y0,float x1,float y1,float ax,float ay,float bx,float by){
        float rx=x1-x0,ry=y1-y0,sx=bx-ax,sy=by-ay,den=rx*sy-ry*sx;if(Math.abs(den)<1e-7f)return -1;
        float t=((ax-x0)*sy-(ay-y0)*sx)/den,u=((ax-x0)*ry-(ay-y0)*rx)/den;return t>=0&&t<=1&&u>=0&&u<=1?t:-1;
    }

    // ------------------------------------------------------------------ AI helpers
    float dist(Actor a,Actor b){return (float)Math.hypot(a.x-b.x,a.y-b.y);}
    float angleTo(Actor a,Actor b){return (float)Math.atan2(b.y-a.y,b.x-a.x);}
    void walk(Actor a,float angle,float speed){a.wishX=(float)Math.cos(angle)*speed;a.wishY=(float)Math.sin(angle)*speed;a.wishActive=true;}
    /** Line of sight from a's eyes to b's centre, blocked by walls and closed openings. */
    boolean lineOfSight(Actor a,Actor b){
        float z0=a.z+a.height*.8f,z1=b.z+b.height*.6f;
        for(Level.LineDef l:lines){Level.Vertex p=level.vertices.get(l.from),q=level.vertices.get(l.to);
            float t=segHit(a.x,a.y,b.x,b.y,p.x,p.y,q.x,q.y);if(t<0)continue;
            if(l.back<0)return false;
            Level.Sector f=level.sectors.get(l.front),k=level.sectors.get(l.back);float z=z0+(z1-z0)*t;
            if(z<Math.max(f.floor,k.floor)||z>Math.min(f.ceiling,k.ceiling))return false;}
        return true;
    }
    boolean canSeePlayer(Actor a,float range,float fov){
        if(player==null||!player.alive)return false;
        float d=dist(a,player);if(d>range)return false;
        if(d>1.5f&&fov<360){float da=angleDiff(angleTo(a,player),a.angle);if(Math.abs(da)>Math.toRadians(fov)*.5f)return false;}
        return lineOfSight(a,player);
    }
    static float angleDiff(float a,float b){float d=a-b;while(d>Math.PI)d-=2*Math.PI;while(d<-Math.PI)d+=2*Math.PI;return d;}
    /** Doom-like chase: head for the target in 8 directions, picking a new direction when blocked. */
    void chase(Actor a,float speed,float dt){
        Actor t=a.target;if(t==null)return;
        a.reactTimer-=dt;
        float want=angleTo(a,t);
        if(a.blocked||a.reactTimer<=0){
            a.reactTimer=.35f+rng.nextFloat()*.4f;
            float best=want;
            if(a.blocked){
                float[] tries={want+(float)Math.PI/4,want-(float)Math.PI/4,want+(float)Math.PI/2,want-(float)Math.PI/2,want+(float)Math.PI*.75f,want-(float)Math.PI*.75f,want+(float)Math.PI};
                if(rng.nextBoolean())for(int i=0;i+1<tries.length;i+=2){float tmp=tries[i];tries[i]=tries[i+1];tries[i+1]=tmp;}
                for(float tr:tries){float ox=a.x,oy=a.y;boolean ok=tryMove(a,a.x+(float)Math.cos(tr)*.5f,a.y+(float)Math.sin(tr)*.5f);a.x=ox;a.y=oy;if(ok){best=tr;break;}}
            }
            a.angle=(float)(Math.round(best/(Math.PI/4))*(Math.PI/4));
        }
        if(dist(a,t)<a.radius+t.radius+.15f){a.stop();a.angle=want;return;}
        walk(a,a.angle,speed);
    }
    void teleport(Actor a,float x,float y){if(level.sectorAt(x,y)<0)return;float ox=a.x,oy=a.y;a.x=x;a.y=y;if(!tryMove(a,x,y)){a.x=ox;a.y=oy;return;}a.z=floorAt(x,y);effect(Sprites.FX_TELEPORT,x,y,a.z+.8f);sound(SoundFx.TELEPORT,a);}

    // ------------------------------------------------------------------ combat
    void damage(Actor t,int amount,Actor src){
        if(t==null||!t.alive||t.invulnerable||amount<=0||t.removed)return;
        if(t.isPlayer){
            if(armor>0){int saved=Math.min(armor,amount/3);armor-=saved;amount-=saved;}
            damageFlash=Math.min(1,damageFlash+.25f+amount*.02f);flashColor=0xffff0000;
        }
        t.health-=amount;
        if(src!=null&&src!=t&&!t.isPlayer&&src.alive)t.target=src;
        if(t.health<=0){
            t.health=0;t.alive=false;t.shootable=false;t.solid=false;t.vx*=.3f;t.vy*=.3f;
            if(t.isPlayer){sound(SoundFx.PLAYER_DEATH,t);}
            else{if(t.def.countKill)kills++;}
            if(t.fsm!=null)t.fsm.send("DIED");
        }else{
            t.painTimer=Math.max(t.painTimer,.2f);
            if(t.isPlayer)sound(SoundFx.PLAYER_PAIN,t);
            if(t.fsm!=null)t.fsm.send("DAMAGED");
        }
    }
    void radiusDamage(Actor src,int dmg,float radius){
        for(int i=0;i<actors.size();i++){Actor a=actors.get(i);if(!a.shootable||!a.alive||a==src)continue;
            float d=(float)Math.hypot(a.x-src.x,a.y-src.y)-a.radius;if(d>=radius)continue;
            if(!lineOfSight(src,a))continue;
            damage(a,Math.round(dmg*(1-Math.max(0,d)/radius)),src.owner!=null?src.owner:src);}
        if(player!=null&&Math.hypot(player.x-src.x,player.y-src.y)<radius*2)shake(.5f,.4f);
    }
    /** Instant bullets with Doom-style vertical auto-aim. */
    void hitscan(Actor src,float angle,int dmin,int dmax,float spread,int pellets,float range){
        for(int p=0;p<pellets;p++){
            float a=angle+(rng.nextFloat()*2-1)*spread;
            float ex=src.x+(float)Math.cos(a)*range,ey=src.y+(float)Math.sin(a)*range;
            float wallT=1;
            float z0=src.z+src.height*.75f;
            for(Level.LineDef l:lines){Level.Vertex pv=level.vertices.get(l.from),q=level.vertices.get(l.to);
                float t=segHit(src.x,src.y,ex,ey,pv.x,pv.y,q.x,q.y);if(t<0||t>=wallT)continue;
                boolean solid=l.back<0;
                if(!solid){Level.Sector f=level.sectors.get(l.front),k=level.sectors.get(l.back);float hi=Math.min(f.ceiling,k.ceiling),lo=Math.max(f.floor,k.floor);solid=hi-lo<.2f||z0<lo||z0>hi;}
                if(solid)wallT=t;}
            Actor hit=null;float best=wallT*range;
            for(Actor o:actors){if(o==src||!o.shootable||!o.alive||o.removed)continue;
                float dx=o.x-src.x,dy=o.y-src.y,along=dx*(float)Math.cos(a)+dy*(float)Math.sin(a);if(along<=0||along>=best)continue;
                float perp=Math.abs(-dx*(float)Math.sin(a)+dy*(float)Math.cos(a));if(perp<o.radius){best=along;hit=o;}}
            float hx=src.x+(float)Math.cos(a)*(best-.05f),hy=src.y+(float)Math.sin(a)*(best-.05f);
            if(hit!=null){damage(hit,dmin+rng.nextInt(Math.max(1,dmax-dmin+1)),src);effect(Sprites.FX_BLOOD,hx,hy,hit.z+hit.height*.6f);}
            else if(wallT<1)effect(Sprites.FX_PUFF,hx,hy,Math.min(z0,ceilAt(hx,hy)-.1f));
        }
    }
    boolean melee(Actor src,float range,int dmg){
        Actor t=src.target;if(t==null||!t.alive)return false;
        if(dist(src,t)>range+t.radius||!lineOfSight(src,t))return false;
        damage(t,dmg,src);sound(SoundFx.PUNCH,src);return true;
    }
    void projectile(Actor src,int type,int dmin,int dmax,float speed){
        float ang=src.target!=null?angleTo(src,src.target):src.angle;
        Actor p=new Actor(null);p.projectileType=type;p.owner=src;p.dmgMin=dmin;p.dmgMax=dmax;p.radius=.15f;p.height=.3f;p.alive=true;p.noGravity=true;
        p.x=src.x+(float)Math.cos(ang)*(src.radius+.2f);p.y=src.y+(float)Math.sin(ang)*(src.radius+.2f);p.z=src.z+src.height*.6f;
        float dz=0;if(src.target!=null){float d=Math.max(.5f,dist(src,src.target));dz=((src.target.z+src.target.height*.5f)-p.z)/d;}
        p.vx=(float)Math.cos(ang)*speed;p.vy=(float)Math.sin(ang)*speed;p.vz=dz*speed;p.angle=ang;
        spawned.add(p);sound(type==1?SoundFx.ROCKET:SoundFx.FIREBALL,src);
    }
    private void projectileStep(Actor p,float dt){
        if(!p.alive)return;
        float nx=p.x+p.vx*dt,ny=p.y+p.vy*dt,nz=p.z+p.vz*dt;
        boolean hitWall=false;
        for(Level.LineDef l:lines){Level.Vertex a=level.vertices.get(l.from),b=level.vertices.get(l.to);
            if(segHit(p.x,p.y,nx,ny,a.x,a.y,b.x,b.y)<0)continue;
            if(l.back<0){hitWall=true;break;}
            Level.Sector f=level.sectors.get(l.front),k=level.sectors.get(l.back);
            if(nz<Math.max(f.floor,k.floor)||nz>Math.min(f.ceiling,k.ceiling)){hitWall=true;break;}}
        int si=level.sectorAt(nx,ny);
        if(si<0||nz<level.sectors.get(si).floor||nz>level.sectors.get(si).ceiling)hitWall=true;
        Actor victim=null;
        if(!hitWall)for(Actor o:actors){if(o==p.owner||o==p||!o.shootable||!o.alive||o.removed)continue;
            if(Math.hypot(o.x-nx,o.y-ny)<o.radius+p.radius&&nz>=o.z&&nz<=o.z+o.height){victim=o;break;}}
        if(hitWall||victim!=null){
            p.alive=false;p.removed=true;
            if(victim!=null)damage(victim,p.dmgMin+rng.nextInt(Math.max(1,p.dmgMax-p.dmgMin+1)),p.owner);
            if(p.projectileType==1){effect(Sprites.FX_EXPLOSION,p.x,p.y,p.z);sound(SoundFx.EXPLODE,p);p.x=nx-p.vx*dt*.5f;p.y=ny-p.vy*dt*.5f;radiusDamage(p,128,3.2f);}
            else effect(p.projectileType==2?Sprites.FX_PLASMA:Sprites.FX_EXPLOSION,p.x,p.y,p.z);
            return;
        }
        p.x=nx;p.y=ny;p.z=nz;
    }

    // ------------------------------------------------------------------ player weapons
    boolean weaponReady(){return weaponCooldown<=0&&pendingWeapon<0;}
    int fireWeapon(){
        if(!weaponReady())return FIRE_COOLING;
        int at=W_AMMO[weapon];
        if(at>=0&&ammo[at]<=0){sound(SoundFx.OOF,player);return FIRE_NO_AMMO;}
        if(at>=0)ammo[at]--;
        weaponCooldown=W_COOLDOWN[weapon];
        sound(W_SOUND[weapon],player);
        if(weapon==0){Actor t=nearestInFront(1.3f);if(t!=null)damage(t,W_DMIN[0]+rng.nextInt(W_DMAX[0]-W_DMIN[0]+1),player);return FIRE_OK;}
        weaponFlash=.09f;
        if(weapon==4){player.target=null;projectile(player,1,20,160,14f);return FIRE_OK;}
        hitscan(player,player.angle,W_DMIN[weapon],W_DMAX[weapon],W_SPREAD[weapon],W_PELLETS[weapon],40);
        if(weapon==2)shake(.15f,.12f);
        return FIRE_OK;
    }
    private Actor nearestInFront(float range){
        Actor best=null;float bd=range;
        for(Actor o:actors){if(o==player||!o.shootable||!o.alive)continue;float d=dist(player,o)-o.radius;
            if(d<bd&&Math.abs(angleDiff(angleTo(player,o),player.angle))<.6f){bd=d;best=o;}}
        return best;
    }
    boolean selectWeapon(int w){if(w<0||w>4||!weapons[w])return false;if(w!=weapon)pendingWeapon=w;return true;}
    void nextWeapon(){for(int i=1;i<=5;i++){int w=(weapon+i)%5;if(weapons[w]&&(W_AMMO[w]<0||ammo[W_AMMO[w]]>0)&&w!=weapon){pendingWeapon=w;return;}}}
    boolean giveWeapon(int w,int am){
        boolean had=weapons[w];weapons[w]=true;boolean got=W_AMMO[w]>=0&&giveAmmo(W_AMMO[w],am);
        if(!had){pendingWeapon=w;return true;}return got;
    }
    boolean hasWeapon(int w){return w>=0&&w<5&&weapons[w];}
    boolean giveAmmo(int t,int am){if(t<0||t>2||ammo[t]>=AMMO_MAX[t])return false;ammo[t]=Math.min(AMMO_MAX[t],ammo[t]+am);return true;}
    boolean giveKey(int k){if(k<0||k>2)return false;keys[k]=true;return true;}
    boolean hasKey(int k){return k>=0&&k<3&&keys[k];}
    boolean giveHealth(Actor a,int amount,int max){if(a==null||!a.alive||a.health>=max)return false;a.health=Math.min(max,a.health+amount);return true;}
    boolean giveArmor(int amount,int max){if(armor>=max)return false;armor=Math.min(max,armor+amount);return true;}

    // ------------------------------------------------------------------ use, touch, specials
    /** Use whatever is directly in front: a door/lift linedef or an entity. */
    void use(Actor a){
        float ex=a.x+(float)Math.cos(a.angle)*USE_RANGE,ey=a.y+(float)Math.sin(a.angle)*USE_RANGE;
        Actor bestA=null;float bestT=1;
        for(Actor o:actors){if(o==a||o.removed||o.projectileType>=0)continue;
            float dx=o.x-a.x,dy=o.y-a.y,along=dx*(float)Math.cos(a.angle)+dy*(float)Math.sin(a.angle);
            if(along<=0||along>USE_RANGE+o.radius)continue;float perp=Math.abs(-dx*(float)Math.sin(a.angle)+dy*(float)Math.cos(a.angle));
            if(perp<o.radius+.2f&&along/USE_RANGE<bestT){bestT=along/USE_RANGE;bestA=o;}}
        Level.LineDef bestL=null;float lt=1;
        for(Level.LineDef l:lines){Level.Vertex p=level.vertices.get(l.from),q=level.vertices.get(l.to);float t=segHit(a.x,a.y,ex,ey,p.x,p.y,q.x,q.y);if(t>=0&&t<lt){lt=t;bestL=l;}}
        if(bestA!=null&&bestT<lt){if(bestA.fsm!=null)bestA.fsm.send("USED");return;}
        if(bestL!=null&&bestL.back>=0){
            int s=special(bestL.front)!=Level.SP_NONE?bestL.front:bestL.back;
            if(s==sectorIndex(a)&&special(bestL.back)!=Level.SP_NONE)s=bestL.back;
            Level.Sector sec=level.sectors.get(s);
            if(sec.special==Level.SP_DOOR){
                if(sec.tag>0&&!keys[sec.tag-1]){message("You need a "+Level.KEY_NAMES[sec.tag].toLowerCase()+" to open this door",2.5f);sound(SoundFx.OOF,a);return;}
                openDoor(s,true);return;}
            if(sec.special==Level.SP_LIFT){lift(s);return;}
        }
        if(a.isPlayer)sound(SoundFx.OOF,a);
    }
    private int special(int s){return s>=0&&s<level.sectors.size()?level.sectors.get(s).special:0;}
    void openDoor(int s,boolean autoClose){
        Level.Sector sec=sector(s);if(sec==null)return;
        float open=s<doorOpen.length&&doorOpen[s]>sec.floor?doorOpen[s]:lowestNeighbourCeiling(s)-.1f;
        if(sec.ceiling>=open-.01f&&!autoClose)return;
        Mover m=moverFor(s,true);m.target=open;m.speed=2.5f;m.wait=autoClose?3.5f:-1;m.returnTo=sec.floor;m.returning=false;m.delay=0;
        sound(SoundFx.DOOR,null,sectorCentre(s));
    }
    void closeDoor(int s){Level.Sector sec=sector(s);if(sec==null)return;Mover m=moverFor(s,true);m.target=sec.floor;m.speed=2.5f;m.wait=-1;m.returning=false;sound(SoundFx.DOOR,null,sectorCentre(s));}
    private void lift(int s){
        Level.Sector sec=sector(s);if(sec==null)return;Mover m=moverFor(s,false);if(m.speed>0&&(m.returning||m.delay>0))return;
        m.target=lowestNeighbourFloor(s);m.returnTo=sec.floor;m.speed=2f;m.wait=3f;m.returning=false;sound(SoundFx.DOOR,null,sectorCentre(s));
    }
    void moveFloor(int s,float amount,float speed){Level.Sector sec=sector(s);if(sec==null)return;Mover m=moverFor(s,false);m.target=sec.floor+amount;m.speed=Math.max(.1f,speed);m.wait=-1;m.returning=false;}
    void moveCeiling(int s,float amount,float speed){Level.Sector sec=sector(s);if(sec==null)return;Mover m=moverFor(s,true);m.target=sec.ceiling+amount;m.speed=Math.max(.1f,speed);m.wait=-1;m.returning=false;}
    boolean moving(int s){for(Mover m:movers)if(m.sector==s)return true;return false;}
    private Mover moverFor(int s,boolean ceiling){for(Mover m:movers)if(m.sector==s&&m.ceiling==ceiling)return m;Mover m=new Mover();m.sector=s;m.ceiling=ceiling;movers.add(m);return m;}
    private void moversStep(float dt){
        for(int i=movers.size()-1;i>=0;i--){Mover m=movers.get(i);Level.Sector s=level.sectors.get(m.sector);
            if(m.delay>0){m.delay-=dt;if(m.delay<=0){m.target=m.returnTo;m.returning=true;sound(SoundFx.DOOR,null,sectorCentre(m.sector));}continue;}
            float cur=m.ceiling?s.ceiling:s.floor,step=m.speed*dt;
            float next=Math.abs(m.target-cur)<=step?m.target:cur+Math.signum(m.target-cur)*step;
            if(m.ceiling&&next<cur&&blockedByActor(m.sector,next)){m.target=Math.max(m.target,cur);m.delay=0;m.returning=false;m.target=doorOpen.length>m.sector&&doorOpen[m.sector]>0?doorOpen[m.sector]:cur+1;continue;}
            if(m.ceiling)s.ceiling=next;else s.floor=next;
            if(next==m.target){if(m.wait>0&&!m.returning){m.delay=m.wait;}else movers.remove(i);}
        }
    }
    private boolean blockedByActor(int sector,float ceil){
        for(Actor a:actors){if(!a.alive||a.removed||a.projectileType>=0)continue;if(level.sectorAt(a.x,a.y)==sector&&a.z+a.height>ceil)return true;}return false;
    }
    private float lowestNeighbourCeiling(int s){float best=1e9f;for(Level.LineDef l:lines){int o=l.front==s?l.back:l.back==s?l.front:-2;if(o>=0)best=Math.min(best,level.sectors.get(o).ceiling);}return best>1e8f?level.sectors.get(s).floor+2:best;}
    private float lowestNeighbourFloor(int s){float best=level.sectors.get(s).floor;for(Level.LineDef l:lines){int o=l.front==s?l.back:l.back==s?l.front:-2;if(o>=0)best=Math.min(best,level.sectors.get(o).floor);}return best;}
    private float[] sectorCentre(int s){Level.Sector sec=level.sectors.get(s);float x=0,y=0;for(int id:sec.loop){x+=level.vertices.get(id).x;y+=level.vertices.get(id).y;}return new float[]{x/sec.loop.size(),y/sec.loop.size()};}
    private void touches(){
        if(player==null||!player.alive)return;
        for(Actor o:actors){if(o==player||o.removed||o.projectileType>=0||!o.alive||o.solid){if(o!=player)o.touching=false;continue;}
            boolean t=Math.hypot(o.x-player.x,o.y-player.y)<o.radius+player.radius&&player.z<o.z+Math.max(.5f,o.height)&&o.z<player.z+player.height;
            if(t&&!o.touching&&o.fsm!=null){o.fsm.send("TOUCHED");if(o.def!=null&&o.def.countItem)o.countedItem=true;}
            o.touching=t;}
    }
    private void sectorSpecials(float dt){
        if(player==null)return;
        int ps=sectorIndex(player);
        damageTick-=dt;
        for(int i=0;i<level.sectors.size();i++){Level.Sector s=level.sectors.get(i);
            if(s.special==Level.SP_FLICKER&&rng.nextFloat()<dt*8)s.light=rng.nextBoolean()?s.light:(rng.nextBoolean()?96:224);
            if(s.special==Level.SP_BLINK)s.light=((int)(time*2)&1)==0?224:80;}
        if(ps<0||!player.alive)return;
        Level.Sector s=level.sectors.get(ps);
        boolean onFloor=player.z<=s.floor+.05f;
        if(s.special==Level.SP_DAMAGE&&onFloor&&damageTick<=0){damageTick=1;damage(player,10,null);}
        if(s.special==Level.SP_EXIT&&onFloor)exitLevel();
        if(s.special==Level.SP_SECRET&&!secretFound[ps]){secretFound[ps]=true;secrets++;centerPrint("A secret is revealed!",2.5f);sound(SoundFx.SECRET,player);}
    }

    // ------------------------------------------------------------------ entities & feedback
    Actor spawn(String defId,float x,float y,float angle){
        EntityDef d=project.def(defId);if(d==null||d.kind==EntityDef.PLAYER)return null;
        if(level.sectorAt(x,y)<0)return null;
        Actor a=makeActor(d,x,y,angle);a.z=floorAt(x,y);
        if(d.solid){float ox=a.x,oy=a.y;a.solid=false;boolean ok=true;for(Actor o:actors)if(o.solid&&o.alive&&Math.hypot(o.x-x,o.y-y)<o.radius+a.radius)ok=false;a.solid=true;if(!ok)return null;a.x=ox;a.y=oy;}
        spawned.add(a);return a;
    }
    void remove(Actor a){if(a==player)return;a.removed=true;a.alive=false;if(a.def!=null&&a.def.countItem&&a.countedItem)items++;}
    int count(String defId){int c=0;for(Actor a:actors)if(a.alive&&!a.removed&&a.def!=null&&(defId==null||defId.isEmpty()||a.def.id.equals(defId)))c++;return c;}
    void broadcast(String event,String defId){if(event==null||event.isEmpty())return;for(Actor a:actors)if(a.fsm!=null&&!a.removed&&(defId==null||defId.isEmpty()||(a.def!=null&&a.def.id.equals(defId))))a.fsm.send(event);}
    void effect(int type,float x,float y,float z){effects.add(new Effect(type,x,y,z,type==Sprites.FX_EXPLOSION?.45f:type==Sprites.FX_TELEPORT?.6f:.3f));}
    void setLight(int s,int l){Level.Sector sec=sector(s);if(sec!=null)sec.light=Math.max(0,Math.min(255,l));}
    void message(String m,float secs){message=m;messageTime=Math.max(.5f,secs);}
    void centerPrint(String m,float secs){center=m;centerTime=Math.max(.5f,secs);}
    void flash(int color,float strength){flashColor=color==0?0xffff0000:color==1?0xffffd040:color==2?0xffffffff:0xff40ff40;if(color==0)damageFlash=Math.min(1,strength);else pickupFlash=Math.min(1,strength);}
    void shake(float strength,float secs){shakeStrength=Math.max(shakeStrength*(shakeTime>0?1:0),strength);shakeTime=Math.max(shakeTime,secs);}
    void exitLevel(){if(!levelComplete){levelComplete=true;completeTime=0;sound(SoundFx.SWITCH,player);}}
    void requestRestart(){restartRequested=true;}
    void sound(int id,Actor at){sound(id,at,null);}
    void sound(int id,Actor at,float[] pos){
        if(sfx==null||id<0)return;
        float vol=1;
        if(player!=null&&at!=player){float x=at!=null?at.x:pos!=null?pos[0]:player.x,y=at!=null?at.y:pos!=null?pos[1]:player.y;
            float d=(float)Math.hypot(x-player.x,y-player.y);vol=Math.max(0,1-d/28f);}
        if(vol>.02f)sfx.play(id,vol);
    }
}
