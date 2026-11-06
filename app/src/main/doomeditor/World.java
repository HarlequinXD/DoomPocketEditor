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
    static final int FIRE_OK=0,FIRE_COOLING=1,FIRE_NO_TARGET=2,FIRE_NO_POWER=3;
    /** Projectile kinds: fire bolt, arrow, arcane missile. */
    static final int P_FIRE=0,P_ARROW=1,P_MAGIC=2;

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
    // The hero (player character) and HUD state
    Hero hero;
    /** First-person weapon model currently shown (Rpg.FP_*). */
    int weapon=Rpg.FP_FIST;
    /** True while a dialogue, the inventory or another menu has paused the game. */
    boolean paused;
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

    /** Floating combat text (damage numbers, MISS, CRITICAL, +XP...). */
    static final class Floater {float x,y,z,t;final String text;final int color;Floater(float x,float y,float z,String text,int color){this.x=x;this.y=y;this.z=z;this.text=text;this.color=color;}}
    final ArrayList<Floater> floaters=new ArrayList<>();
    void floater(Actor a,String text,int color){if(a==null||!GameSettings.combatText)return;floaters.add(new Floater(a.x+(rng.nextFloat()-.5f)*.25f,a.y+(rng.nextFloat()-.5f)*.25f,a.z+a.height+.1f,text,color));if(floaters.size()>40)floaters.remove(0);}
    /** NPC conversation shown over the game (pauses it). Choices send events back to the speaker's FSM. */
    static final class Dialogue {String speaker,text;String[] choices,events;Actor who;
        /** Conversation tree mode (Dialogue editor): the tree, the node shown and which choice each button is. */
        com.retro.doomeditor.Dialogue.Def tree;com.retro.doomeditor.Dialogue.Node node;int[] choiceMap;String endEvent="";}
    Dialogue dialogue;
    static final class Quest {final String id;String title,text;int state,stage;Quest(String id){this.id=id;}}
    static final int Q_INACTIVE=0,Q_ACTIVE=1,Q_DONE=2,Q_FAILED=3;
    final java.util.LinkedHashMap<String,Quest> quests=new java.util.LinkedHashMap<>();
    static final class Effect {int type;float x,y,z,t,life;Effect(int type,float x,float y,float z,float life){this.type=type;this.x=x;this.y=y;this.z=z;this.life=life;}}
    /** Moves a floor or ceiling to a target, optionally waits and returns (doors, lifts). */
    static final class Mover {int sector;boolean ceiling;float target,speed,wait,returnTo;boolean returning;float delay;}

    World(Project p,SoundFx sfx){this(p,sfx,false,p.currentMap,null,"",null);}
    /** Plays map {@code mapNum}. When {@code from} is given the hero, quests and global variables carry over from it. */
    World(Project p,SoundFx sfx,int mapNum,World from,String arrival){this(p,sfx,false,mapNum,from,arrival,null);}
    /** Loading a saved game: starts map {@code mapNum} with a remembered session. */
    World(Project p,SoundFx sfx,int mapNum,World from,String arrival,GameSave.Session ses){this(p,sfx,false,mapNum,from,arrival,ses);}
    /** Re-centres the camera after the player was moved (loading a save). */
    void syncView(){viewZ=player.z+EYE;}
    /** Map being played, and a map change requested by an exit or the Go To Map action (applied by the screen). */
    /** Smoke, embers and sparkles from entity emitters and the Emit Particles action. */
    final Atmosphere.Particles particles=new Atmosphere.Particles();
    /** Map atmosphere, changeable by FSM actions for this visit. */
    int fog,tint,weather;
    private void tickParticles(float dt){
        particles.update(dt);
        for(Actor a:actors){if(a.removed||a.def==null||(!a.visible&&!a.isPlayer))continue;
            int type=a.def.particles;boolean autoFire=type<=0&&a.def.glow==Atmosphere.G_AUTO&&PropMesh.fire(a.sprite)&&project.builtinLook(a.def,a.sprite)&&a.alive;
            if(type<=0&&!autoFire)continue;
            if(Math.hypot(a.x-player.x,a.y-player.y)>22)continue;
            if(autoFire){   // built-in fires burn on their own: flames plus a few embers
                boolean big=PropMesh.bigFire(a.sprite);float z=a.z+PropMesh.flameZ(a.sprite);float spread=big?(a.sprite==Sprites.CAMPFIRE?.16f:.12f):.025f;
                if(a.sprite==Sprites.CANDLE||a.sprite==Sprites.CANDELABRA)continue;
                a.emitAcc+=dt*(big?22:9);
                while(a.emitAcc>=1){a.emitAcc-=1;float ox=(float)(Math.random()-.5)*2*spread,oy=(float)(Math.random()-.5)*2*spread;
                    if(a.sprite==Sprites.FIREPLACE){float ca=(float)Math.cos(a.angle),sa=(float)Math.sin(a.angle);ox+=ca*-.08f;oy+=sa*-.08f;}
                    particles.emit(Math.random()<.15?Atmosphere.P_EMBERS:Atmosphere.P_FLAMES,a.x+ox,a.y+oy,z,1);}
                continue;}
            a.emitAcc+=dt*Atmosphere.rate(a.def.particles);while(a.emitAcc>=1){a.emitAcc-=1;particles.emit(a.def.particles,a.x,a.y,a.z+Atmosphere.emitHeight(a.def.particles,a),1);}}
    }
    /** Testing cheat from the debug panel. */
    boolean godMode;
    int mapNum;int pendingMap=-1;String pendingArrival="";
    /** Everything remembered across maps this game (visited map states, play time); shared when changing maps. */
    GameSave.Session session;
    private Actor[] thingActors=new Actor[0];
    /** Snapshot of this map's placed entities, secrets and counters. */
    GameSave.MapState captureMapState(){
        GameSave.MapState m=new GameSave.MapState();m.things=new GameSave.ThingState[thingActors.length];
        for(int i=0;i<thingActors.length;i++){Actor a=thingActors[i];if(a==null)continue;GameSave.ThingState t=new GameSave.ThingState();
            t.gone=a.removed;t.alive=a.alive;t.x=a.x;t.y=a.y;t.angle=a.angle;t.health=a.health;t.sprite=a.sprite;t.visible=a.visible;
            if(a.fsm!=null){t.state=a.fsm.stateId();t.vars=new java.util.HashMap<>(a.fsm.vars);}
            m.things[i]=t;}
        m.secrets=secretFound.clone();m.kills=kills;m.items=items;m.secretCount=secrets;
        return m;
    }
    void applyMapState(GameSave.MapState m){
        for(int i=0;i<thingActors.length&&m.things!=null&&i<m.things.length;i++){Actor a=thingActors[i];GameSave.ThingState t=m.things[i];if(a==null||t==null)continue;
            if(t.gone){a.removed=true;a.alive=false;continue;}
            a.x=t.x;a.y=t.y;a.angle=t.angle;a.z=floorAt(a.x,a.y);a.health=t.health;a.sprite=t.sprite;a.visible=t.visible;
            if(!t.alive){a.alive=false;a.health=0;a.solid=false;a.shootable=false;a.deadTime=60;if(a.fsm!=null)a.fsm.state=null;}   // stays a corpse; its death actions don't run again
            else if(a.fsm!=null&&t.state!=null&&!t.state.isEmpty())a.fsm.restore(t.state,t.vars);}
        if(m.secrets!=null)for(int i=0;i<Math.min(m.secrets.length,secretFound.length);i++)secretFound[i]=m.secrets[i];
        kills=m.kills;items=m.items;secrets=m.secretCount;
    }
    void goToMap(int num,String arrival){if(levelComplete||pendingMap>0||project.map(num)==null)return;pendingMap=num;pendingArrival=arrival==null?"":arrival;paused=true;
        session.maps.put(mapNum,captureMapState());}   // remember this map for when the hero comes back
    /** Editor camera body: walks the live level (no copy, no entities, doors left as drawn). */
    static World walker(Project p){return new World(p,null,true,p.currentMap,null,"",null);}
    private long geoSig;
    final boolean live;
    /** Rebuilds collision lines if the live level's shape changed. */
    void syncGeometry(){long sig=Renderer.signature(level);if(sig!=geoSig){geoSig=sig;lines.clear();lines.addAll(PortalTopology.build(level));}}
    /** Physics only, for the editor camera. */
    void walkerTick(float dt){dt=Math.min(dt,.05f);time+=dt;syncGeometry();physics(player,dt);
        float target=player.z+EYE;if(player.noGravity)viewZ=target;else if(target>viewZ)viewZ+=Math.min(target-viewZ,Math.max(dt*4f,(target-viewZ)*dt*12f));else viewZ=target;}
    private World(Project p,SoundFx sfx,boolean live,int mapNum,World from,String arrival,GameSave.Session ses){
        project=p;this.sfx=sfx;this.live=live;
        p.syncCurrent();
        Project.GameMap gm=p.map(mapNum);if(gm==null)gm=p.current();this.mapNum=gm.num;
        if(sfx!=null&&!live){sfx.stopSounds();sfx.useProject(p);String song=p.mapMusic(gm);
            // keep the music going across a map change when both maps use the same song
            if(from==null||!song.equals(sfx.musicTag)||!sfx.musicPlaying()){playMusic(song,true,0);sfx.musicTag=song;}}
        level=live?p.level:gm.level.copy();
        fog=gm.fog;tint=gm.tint;weather=gm.weather;
        lines=PortalTopology.build(level);geoSig=Renderer.signature(level);
        doorOpen=new float[level.sectors.size()];secretFound=new boolean[level.sectors.size()];
        for(int i=0;i<level.sectors.size();i++){Level.Sector s=level.sectors.get(i);
            if(s.special==Level.SP_DOOR&&!live){doorOpen[i]=s.ceiling;s.ceiling=s.floor;}   // doors start closed
            if(s.special==Level.SP_SECRET)totalSecrets++;}
        EntityDef pd=p.playerDef();
        float sx=level.playerX,sy=level.playerY,sa=level.facing;
        if(arrival!=null&&!arrival.isEmpty())for(Level.Thing t:level.things)if(arrival.equals(t.def)){sx=t.x;sy=t.y;sa=t.angle;break;}
        player=makeActor(pd,sx,sy,sa);player.isPlayer=true;
        if(from!=null&&from.hero!=null){hero=from.hero;player.sprite=from.player.sprite;player.health=Math.max(1,from.player.health);if(hero.race!=null)player.speed=pd.speed*hero.race.speed;hero.relentlessUsed=false;
            quests.putAll(from.quests);globals.putAll(from.globals);}
        else{hero=Hero.create(pd);player.sprite=pd.lookSprite();player.health=hero.maxHp;}
        weapon=hero.weaponOrFists().fp;
        player.z=floorAt(player.x,player.y);viewZ=player.z+EYE;
        actors.add(player);
        if(live){player.fsm=null;return;}
        thingActors=new Actor[level.things.size()];
        for(int ti=0;ti<level.things.size();ti++){Level.Thing t=level.things.get(ti);EntityDef d=p.def(t.def);if(d==null||d.kind==EntityDef.PLAYER)continue;
            Actor a=makeActor(d,t.x,t.y,t.angle);a.z=floorAt(a.x,a.y);a.thingIndex=ti;thingActors[ti]=a;actors.add(a);
            if(d.countKill)totalKills++;if(d.countItem)totalItems++;}
        for(Actor a:actors)if(a.fsm!=null)a.fsm.start();
        // remembered state of this map from earlier in the session (or a loaded save)
        session=from!=null?from.session:ses!=null?ses:new GameSave.Session();
        GameSave.MapState ms=session.maps.get(this.mapNum);if(ms!=null)applyMapState(ms);
    }
    private Actor makeActor(EntityDef d,float x,float y,float angle){
        Actor a=new Actor(d);a.x=a.spawnX=x;a.y=a.spawnY=y;a.angle=angle;a.radius=d.radius;a.height=d.height;a.health=d.health;a.speed=d.speed;
        a.solid=d.solid;a.shootable=d.shootable;a.sprite=d.sprite;a.fsm=new FsmRuntime(d.fsm);
        if(d.hitDice!=null&&!d.hitDice.trim().isEmpty()&&d.kind!=EntityDef.PLAYER)a.health=Math.max(1,Rpg.Dice.parse(d.hitDice).roll(rng));
        a.maxHealth=a.health;
        return a;
    }

    // ------------------------------------------------------------------ blood: droplets fly, then stick to floors and walls
    static final class Drop {float x,y,z,vx,vy,vz,size;int color;}
    /** A splat lying on a surface: centre, surface normal (floor = 0,0,1), radius and a seed for its shape. */
    static final class Splat {float x,y,z,nx,ny,nz,r,ang;int seed,color,type;byte[] mask;}
    final ArrayList<Drop> drops=new ArrayList<>();
    final ArrayList<Splat> splats=new ArrayList<>();
    static final int MAX_DROPS=280,MAX_SPLATS=450;
    boolean blood=true;
    private static final int[] BLOOD={0xff8a0a0c,0xff7a0608,0xff9c1012,0xff6a0406};
    /** Sprays blood from a creature that was hit, away from whoever hit it. */
    void bleed(Actor t,Actor src,int amount,boolean crit,boolean death){
        if(!blood||t==null||t.def==null)return;int k=t.def.kind;
        if(k!=EntityDef.PLAYER&&k!=EntityDef.MONSTER&&k!=EntityDef.NPC)return;
        spray(t,src,amount,crit,death);
    }
    /** Blood spray from any entity (used by hits and by the Spray Blood action). */
    void spray(Actor t,Actor src,int amount,boolean crit,boolean death){
        if(t==null)return;
        float ax,ay;
        if(src!=null&&src!=t&&(src.x!=t.x||src.y!=t.y)){ax=t.x-src.x;ay=t.y-src.y;}else{ax=(float)Math.cos(t.angle+Math.PI);ay=(float)Math.sin(t.angle+Math.PI);}
        float base=(float)Math.atan2(ay,ax),hz=t.z+t.height*.6f;
        int n=Math.min(40,6+amount*2+(crit?10:0)+(death?14:0));
        for(int i=0;i<n&&drops.size()<MAX_DROPS;i++){
            Drop d=new Drop();float a=base+(rng.nextFloat()-.5f)*(death?3.2f:1.7f),sp=.9f+rng.nextFloat()*(crit||death?3.4f:2.6f);
            d.x=t.x+(float)Math.cos(a)*t.radius*.4f;d.y=t.y+(float)Math.sin(a)*t.radius*.4f;d.z=hz+(rng.nextFloat()-.5f)*.25f;
            d.vx=(float)Math.cos(a)*sp;d.vy=(float)Math.sin(a)*sp;d.vz=.4f+rng.nextFloat()*2.4f;
            d.size=.02f+rng.nextFloat()*.035f;d.color=BLOOD[rng.nextInt(BLOOD.length)];drops.add(d);
        }
        // a little pool right under them
        float fl=floorAt(t.x,t.y);addSplat(t.x+(rng.nextFloat()-.5f)*.2f,t.y+(rng.nextFloat()-.5f)*.2f,fl+.002f,0,0,1,death?.3f:.08f+rng.nextFloat()*.06f,death?23+rng.nextInt(2):rng.nextInt(SplatDesigns.COUNT),rng.nextFloat()*6.2832f);
    }
    void addSplat(float x,float y,float z,float nx,float ny,float nz,float r){addSplat(x,y,z,nx,ny,nz,r,rng.nextInt(SplatDesigns.COUNT),rng.nextFloat()*6.2832f);}
    /** type is one of the SplatDesigns; angle turns it (floor: the direction the droplet was travelling). */
    void addSplat(float x,float y,float z,float nx,float ny,float nz,float r,int type,float angle){
        Splat s=new Splat();s.type=type;s.ang=angle;s.x=x;s.y=y;s.z=z;s.nx=nx;s.ny=ny;s.nz=nz;s.r=r;s.seed=rng.nextInt(1<<20);s.color=BLOOD[rng.nextInt(BLOOD.length)];
        splats.add(s);if(splats.size()>MAX_SPLATS)splats.remove(0);
    }
    private void tickBlood(float dt){
        for(int i=drops.size()-1;i>=0;i--){Drop d=drops.get(i);
            float nx=d.x+d.vx*dt,ny=d.y+d.vy*dt;d.vz-=9.8f*dt;float nz=d.z+d.vz*dt;
            // first wall the droplet's path crosses (solid, or a step / lintel at the droplet's height)
            Level.LineDef hit=null;float best=2;
            for(int li=0,cnt=lines.size();li<cnt;li++){Level.LineDef l=lines.get(li);Level.Vertex p=level.vertices.get(l.from),q=level.vertices.get(l.to);
                float tt=segHit(d.x,d.y,nx,ny,p.x,p.y,q.x,q.y);if(tt<0||tt>=best)continue;
                if(l.back>=0){Level.Sector f=level.sectors.get(l.front),b=level.sectors.get(l.back);float z=d.z+(nz-d.z)*tt;
                    if(z>=Math.max(f.floor,b.floor)&&z<=Math.min(f.ceiling,b.ceiling))continue;}
                best=tt;hit=l;}
            if(hit!=null){Level.Vertex p=level.vertices.get(hit.from),q=level.vertices.get(hit.to);
                float hx=d.x+(nx-d.x)*best,hy=d.y+(ny-d.y)*best,hz=d.z+(nz-d.z)*best;
                float wx=q.x-p.x,wy=q.y-p.y,wl=(float)Math.hypot(wx,wy);if(wl<1e-5f){drops.remove(i);continue;}
                float qx=-wy/wl,qy=wx/wl;if(qx*(d.x-hx)+qy*(d.y-hy)<0){qx=-qx;qy=-qy;}
                addSplat(hx+qx*.006f,hy+qy*.006f,hz,qx,qy,0,d.size*(1.8f+rng.nextFloat()*1.6f));drops.remove(i);continue;}
            float ce=ceilAt(nx,ny);if(nz>ce-.03f){nz=ce-.03f;d.vz=-Math.abs(d.vz)*.2f;}
            float fl=floorAt(nx,ny);
            if(nz<=fl){addSplat(nx,ny,fl+.002f,0,0,1,d.size*(2.0f+rng.nextFloat()*2.1f),rng.nextInt(SplatDesigns.COUNT),(float)Math.atan2(d.vy,d.vx));drops.remove(i);continue;}
            d.x=nx;d.y=ny;d.z=nz;
        }
    }
    // ------------------------------------------------------------------ frame
    void tick(float dt){
        dt=Math.min(dt,.05f);
        time+=dt;
        if(levelComplete){completeTime+=dt;input.endFrame();return;}
        for(int i=floaters.size()-1;i>=0;i--){Floater f=floaters.get(i);f.t+=dt;f.z+=dt*.45f;if(f.t>1.1f)floaters.remove(i);}
        if(paused||dialogue!=null){input.endFrame();return;}
        if(session!=null)session.playTime+=dt;
        tickParticles(dt);
        tickBlood(dt);
        if(hero!=null){if(hero.wardTime>0)hero.wardTime-=dt;weapon=hero.weaponOrFists().fp;}
        for(int i=0;i<actors.size();i++){Actor a=actors.get(i);if(a.removed)continue;
            a.wishActive=false;
            if(a.fsm!=null)a.fsm.tick(this,a,dt);
            physics(a,dt);
            if(a.painTimer>0)a.painTimer-=dt;if(a.attackTimer>0)a.attackTimer-=dt;
            if(!a.alive)a.deadTime+=dt;
            if(a.animClip!=null)a.animTime+=dt*a.animSpeed;
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
        float speed=(float)Math.hypot(player.vx,player.vy);
        if(player.onGround)bobPhase+=dt*speed*1.6f;
        float target=player.z+EYE*(player.alive?1:.25f);
        if(target>viewZ)viewZ+=Math.min(target-viewZ,Math.max(dt*4f,(target-viewZ)*dt*12f));else viewZ=target;
        updateCamera(dt);
        if(damageFlash>0)damageFlash-=dt*1.5f;if(pickupFlash>0)pickupFlash-=dt*2f;if(shakeTime>0)shakeTime-=dt;
        if(messageTime>0)messageTime-=dt;if(centerTime>0)centerTime-=dt;
        input.endFrame();
    }
    float bob(){if(camMode==CAM_THIRD)return 0;float s=Math.min(1,(float)Math.hypot(player.vx,player.vy)/6f);return (float)Math.sin(bobPhase*Math.PI)*.06f*s;}
    float camX(){return (camMode==CAM_THIRD?rigX:player.x)+(shakeTime>0?(rng.nextFloat()-.5f)*shakeStrength*.3f:0);}
    float camY(){return (camMode==CAM_THIRD?rigY:player.y)+(shakeTime>0?(rng.nextFloat()-.5f)*shakeStrength*.3f:0);}
    float camZ(){return camMode==CAM_THIRD?rigZ:viewZ+bob();}
    float camAngle(){return camMode==CAM_THIRD?rigAngle:player.angle;}
    float camPitch(){return camMode==CAM_THIRD?rigPitch:player.pitch;}
    boolean thirdPerson(){return camMode==CAM_THIRD;}

    // ------------------------------------------------------------------ camera rig (first / third person)
    static final int CAM_FIRST=0,CAM_THIRD=1;
    /** Orbit: the camera turns freely and the player turns to face where they run.
     *  Shoulder: over-the-shoulder, the player always faces the camera direction.
     *  Chase: the camera swings round behind the player's facing. */
    static final String[] CAM_STYLES={"Orbit","Shoulder","Chase"};
    static final int CS_ORBIT=0,CS_SHOULDER=1,CS_CHASE=2;
    int camMode=CAM_FIRST,camStyle=CS_ORBIT;
    float camDist=3.2f,camTargetDist=3.2f,camDistRate=0,camHeight=.55f,camShoulder=0f,camSmooth=8f;
    float camYaw,camOrbitPitch=-.18f;
    float rigX,rigY,rigZ,rigAngle,rigPitch,rigLen=-1,chaseYaw;
    private boolean rigReady;
    /** Switches to a third-person camera; values below zero keep the current setting. */
    void thirdPersonCamera(int style,float dist,float height,float shoulder,float smooth){
        boolean was=camMode==CAM_THIRD;camMode=CAM_THIRD;camStyle=Math.max(0,Math.min(2,style));
        if(dist>=0){camDist=camTargetDist=Math.max(.8f,dist);camDistRate=0;}if(height>-50)camHeight=height;if(shoulder>-50)camShoulder=shoulder;if(smooth>=0)camSmooth=smooth;
        if(!was){camYaw=chaseYaw=player.angle;camOrbitPitch=Math.max(-.6f,Math.min(.45f,player.pitch-.18f));rigLen=-1;}
        updateCamera(0);
    }
    void firstPersonCamera(){
        if(camMode==CAM_THIRD&&camStyle==CS_ORBIT){player.angle=camYaw;player.pitch=Math.max(-.55f,Math.min(.55f,camOrbitPitch+.18f));}
        camMode=CAM_FIRST;
    }
    void setCameraDistance(float d,float speed){camTargetDist=Math.max(.8f,Math.min(12f,d));camDistRate=Math.max(0,speed);if(speed<=0)camDist=camTargetDist;}
    /** Yaw the camera looks along (where the player aims in third person). */
    float aimYaw(){return camMode==CAM_THIRD&&camStyle==CS_ORBIT?camYaw:player.angle;}
    /** Rotates the player toward an angle at up to turnSpeed radians/sec (instant if turnSpeed<=0). */
    void turnToward(Actor a,float angle,float turnSpeed,float dt){
        float d=angleDiff(angle,a.angle);if(turnSpeed<=0){a.angle=angle;return;}
        float m=turnSpeed*dt;a.angle+=Math.max(-m,Math.min(m,d));
    }
    /** Places the camera behind the player, pulled in along the ray when walls or low openings are in the way. */
    void updateCamera(float dt){
        if(camMode!=CAM_THIRD||player==null){rigReady=false;return;}
        if(camDistRate>0&&camDist!=camTargetDist){float m=camDistRate*dt;camDist+=Math.max(-m,Math.min(m,camTargetDist-camDist));}
        float yaw,pitch;
        if(camStyle==CS_ORBIT){yaw=camYaw;pitch=camOrbitPitch;}
        else if(camStyle==CS_CHASE){float k=dt<=0||!rigReady?1:Math.min(1,dt*Math.max(.5f,camSmooth)*.5f);chaseYaw+=angleDiff(player.angle,chaseYaw)*k;yaw=chaseYaw;pitch=player.pitch-.15f;}
        else{yaw=player.angle;pitch=player.pitch;}
        camYaw=yaw;
        float cs=(float)Math.cos(yaw),sn=(float)Math.sin(yaw),cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);
        float px=player.x,py=player.y,pz=Math.min(ceilAt(px,py)-.12f,viewZ+camHeight*.35f);
        // shoulder offset (to the right of the view direction)
        float sh=camStyle==CS_ORBIT?camShoulder*.6f:camShoulder;
        float rx=sn*sh,ry=-cs*sh;
        float bx=px+rx,by=py+ry;
        float tShoulder=sh==0?1:clearT(px,py,pz,bx,by,pz);if(tShoulder<1){bx=px+rx*tShoulder*.8f;by=py+ry*tShoulder*.8f;}
        float dx=-cs*cp*camDist,dy=-sn*cp*camDist,dz=camHeight*.65f-sp*camDist;
        float t=clearT(bx,by,pz,bx+dx,by+dy,pz+dz);
        float full=camDist,len=Math.max(.25f,t*full-.22f);if(t>=1)len=full;
        if(rigLen<0||len<rigLen||dt<=0)rigLen=len;else rigLen+=(len-rigLen)*Math.min(1,dt*4f);
        float f=rigLen/full;
        float x=bx+dx*f,y=by+dy*f,z=pz+dz*f;
        int s=level.sectorAt(x,y);if(s<0){x=bx;y=by;s=level.sectorAt(x,y);}
        if(s>=0){Level.Sector sec=level.sectors.get(s);z=Math.max(sec.floor+.15f,Math.min(sec.ceiling-.15f,z));}
        if(!rigReady||dt<=0||camSmooth<=0){rigX=x;rigY=y;rigZ=z;}
        else{float k=Math.min(1,dt*camSmooth*2.2f);rigX+=(x-rigX)*k;rigY+=(y-rigY)*k;rigZ+=(z-rigZ)*k;
            if(level.sectorAt(rigX,rigY)<0||clearT(px,py,pz,rigX,rigY,rigZ)<1){rigX=x;rigY=y;rigZ=z;}}
        rigAngle=yaw;
        // look at a point ahead of the player so the crosshair lines up with the aim
        float ax=px+cs*8,ay=py+sn*8,az=pz+sp*8;
        rigAngle=(float)Math.atan2(ay-rigY,ax-rigX);
        rigPitch=Math.max(-1.1f,Math.min(1.1f,(float)Math.atan2(az-rigZ,Math.hypot(ax-rigX,ay-rigY))));
        rigReady=true;
    }
    /** Fraction of a 3D segment that is clear of walls, closed openings and floors/ceilings. */
    private float clearT(float x0,float y0,float z0,float x1,float y1,float z1){
        float best=1;
        for(Level.LineDef l:lines){Level.Vertex p=level.vertices.get(l.from),q=level.vertices.get(l.to);
            float t=segHit(x0,y0,x1,y1,p.x,p.y,q.x,q.y);if(t<0||t>=best)continue;
            if(l.back<0){best=t;continue;}
            Level.Sector a=level.sectors.get(l.front),b=level.sectors.get(l.back);float z=z0+(z1-z0)*t;
            float lo=Math.max(a.floor,b.floor)+.1f,hi=Math.min(a.ceiling,b.ceiling)-.1f;
            if(hi<=lo||z<lo||z>hi)best=t;}
        return best;
    }

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
    void walk(Actor a,float angle,float speed){if(!a.alive)return;a.wishX=(float)Math.cos(angle)*speed;a.wishY=(float)Math.sin(angle)*speed;a.wishActive=true;}
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
        Actor t=a.target;if(t==null||!a.alive)return;
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
    /** Plays as a hero made on the character creation screen: its race, class, scores and 3D look. */
    void applyCharacter(CharLook look){
        if(look==null||player==null)return;EntityDef pd=project.playerDef();
        hero=Hero.create(pd,look);applyHeroLook();player.health=hero.maxHp;weapon=hero.weaponOrFists().fp;
    }
    /** Shows the hero's created look (after creating or loading a game) and applies racial speed. */
    void applyHeroLook(){
        if(hero==null||hero.look==null||player==null)return;EntityDef pd=project.playerDef();
        if(project.builtinLook(pd,pd.sprite)){CharLook.setCurrent(hero.look);player.sprite=Sprites.CREATED;}
        if(hero.race!=null)player.speed=pd.speed*hero.race.speed;
    }
    void damage(Actor t,int amount,Actor src){damage(t,amount,src,false);}
    /** Applies damage, shows the number, awards XP to the hero for kills and sends DAMAGED / DIED to the FSM. */
    void damage(Actor t,int amount,Actor src,boolean crit){
        if(t==null||!t.alive||t.invulnerable||amount<=0||t.removed)return;
        if(t.isPlayer&&godMode)return;   // testing: Debug > God mode
        if(t.isPlayer)amount=Math.max(1,Math.round(amount*GameSettings.damageTaken()));   // Options > Difficulty
        if(t.isPlayer){damageFlash=Math.min(1,damageFlash+.25f+amount*.02f);flashColor=0xffff0000;}
        floater(t,crit?amount+"!":String.valueOf(amount),t.isPlayer?0xffff5a4a:crit?0xffffd040:0xffffffff);
        t.health-=amount;
        bleed(t,src,amount,crit,t.health<=0);
        if(src!=null&&src!=t&&!t.isPlayer&&src.alive)t.target=src;
        if(t.health<=0&&t.isPlayer&&hero!=null&&hero.savage()&&!hero.relentlessUsed){hero.relentlessUsed=true;t.health=1;floater(t,"Relentless!",0xffffa040);}
        if(t.health<=0){
            t.health=0;t.alive=false;t.shootable=false;t.solid=false;t.vx*=.3f;t.vy*=.3f;t.animClip=null;
            if(t.isPlayer){sound(SoundFx.PLAYER_DEATH,t);}
            else{if(t.def.countKill)kills++;Actor killer=src!=null&&src.owner!=null?src.owner:src;if(killer==player&&hero!=null)awardXp(t.def.xp,t);}
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
        if(src==null||!src.alive||src.removed)return;   // the dead don't shoot
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
            if(hit!=null){int roll=Rpg.d20(rng);boolean crit=roll==20;
                if(roll!=1&&(crit||roll+attackBonusOf(src)>=ac(hit))){damage(hit,(dmin+rng.nextInt(Math.max(1,dmax-dmin+1)))*(crit?2:1),src,crit);effect(Sprites.FX_BLOOD,hx,hy,hit.z+hit.height*.6f);}
                else floater(hit,"miss",0xffb0b8c0);}
            else if(wallT<1)effect(Sprites.FX_PUFF,hx,hy,Math.min(z0,ceilAt(hx,hy)-.1f));
        }
    }
    boolean melee(Actor src,float range,int dmg){
        if(src==null||!src.alive||src.removed)return false;
        Actor t=src.target;if(t==null||!t.alive)return false;
        if(dist(src,t)>range+t.radius||!lineOfSight(src,t))return false;
        int roll=Rpg.d20(rng);boolean crit=roll==20;sound(SoundFx.SWING,src);
        if(roll==1||(!crit&&roll+attackBonusOf(src)<ac(t))){floater(t,"miss",0xffb0b8c0);return true;}
        damage(t,dmg*(crit?2:1),src,crit);sound(SoundFx.HIT,t);return true;
    }
    void projectile(Actor src,int type,int dmin,int dmax,float speed){
        if(src==null||!src.alive||src.removed)return;
        float ang=src.target!=null?angleTo(src,src.target):src.angle;
        Actor p=new Actor(null);p.projectileType=type;p.owner=src;p.dmgMin=dmin;p.dmgMax=dmax;p.radius=.15f;p.height=.3f;p.alive=true;p.noGravity=true;
        p.x=src.x+(float)Math.cos(ang)*(src.radius+.2f);p.y=src.y+(float)Math.sin(ang)*(src.radius+.2f);p.z=src.z+src.height*.6f;
        float dz=0;if(src.target!=null){float d=Math.max(.5f,dist(src,src.target));dz=((src.target.z+src.target.height*.5f)-p.z)/d;}
        p.vx=(float)Math.cos(ang)*speed;p.vy=(float)Math.sin(ang)*speed;p.vz=dz*speed;p.angle=ang;
        p.pAttack=attackBonusOf(src);
        spawned.add(p);sound(type==P_ARROW?SoundFx.BOW:type==P_MAGIC?SoundFx.SPELL:SoundFx.FIREBALL,src);
    }
    /**
     * Launches a projectile that carries its own attack: an attack-roll bonus (or auto-hit), damage dice and type,
     * and optionally an area burst with a DEX saving throw. Aims at {@code at} (or straight ahead).
     */
    Actor shoot(Actor src,int type,Actor at,float angle,int attack,Rpg.Dice dice,int dmgType,boolean auto,float radius,int dc,float speed){
        if(src==null||!src.alive||src.removed)return null;
        float ang=at!=null?angleTo(src,at):angle;
        Actor p=new Actor(null);p.projectileType=type;p.owner=src;p.radius=.12f;p.height=.25f;p.alive=true;p.noGravity=true;
        p.pAttack=attack;p.pDice=dice;p.pType=dmgType;p.pAuto=auto;p.pRadius=radius;p.pDC=dc;
        p.x=src.x+(float)Math.cos(ang)*(src.radius+.15f);p.y=src.y+(float)Math.sin(ang)*(src.radius+.15f);p.z=src.z+src.height*(src.isPlayer?.72f:.6f);
        float dz=0;if(at!=null){float d=Math.max(.5f,dist(src,at));dz=((at.z+at.height*.55f)-p.z)/d;}else if(src.isPlayer)dz=(float)Math.tan(src.pitch);
        p.vx=(float)Math.cos(ang)*speed;p.vy=(float)Math.sin(ang)*speed;p.vz=dz*speed;p.angle=ang;
        spawned.add(p);return p;
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
            if(p.pRadius>0){   // area spell: everything in the blast makes a DEX save for half
                p.x=nx-p.vx*dt*.5f;p.y=ny-p.vy*dt*.5f;effect(Sprites.FX_EXPLOSION,p.x,p.y,p.z);sound(SoundFx.EXPLODE,p);
                areaDamage(p.owner,p.x,p.y,p.z,p.pRadius,p.pDice,p.pType,p.pDC);return;}
            if(victim!=null){
                if(p.pDice==null){   // legacy projectile (min..max damage)
                    int roll=Rpg.d20(rng);if(roll!=1&&(roll==20||roll+p.pAttack>=ac(victim)))damage(victim,p.dmgMin+rng.nextInt(Math.max(1,p.dmgMax-p.dmgMin+1)),p.owner,roll==20);else floater(victim,"miss",0xffb0b8c0);}
                else{int roll=p.pAuto?10:Rpg.d20(rng);if(roll==1&&!p.pAuto&&p.owner==player&&hero!=null&&hero.lucky())roll=Rpg.d20(rng);boolean crit=!p.pAuto&&roll==20;
                    if(p.pAuto||(roll!=1&&(crit||roll+p.pAttack>=ac(victim)))){int dmg=crit?p.pDice.rollCrit(rng):p.pDice.roll(rng);
                        if(p.owner==player&&hero!=null&&hero.sneakDice()>0&&victim.target!=player){Rpg.Dice sd=new Rpg.Dice(hero.sneakDice()*(crit?2:1),6,0);dmg+=sd.roll(rng);floater(victim,"sneak attack",0xffc8a0ff);}
                        if(victim==player&&hero!=null&&hero.resists(p.pType)){dmg/=2;floater(victim,"resisted",0xffa0c0ff);}
                        damage(victim,Math.max(1,dmg),p.owner,crit);if(p.projectileType==P_ARROW)sound(SoundFx.HIT,victim);}
                    else floater(victim,"miss",0xffb0b8c0);}
            }
            effect(p.projectileType==P_MAGIC?Sprites.FX_PLASMA:p.projectileType==P_ARROW?Sprites.FX_PUFF:Sprites.FX_EXPLOSION,p.x,p.y,p.z);
            return;
        }
        p.x=nx;p.y=ny;p.z=nz;
    }

    // ------------------------------------------------------------------ RPG combat
    int ac(Actor a){if(a==null)return 10;if(a.isPlayer&&hero!=null)return hero.ac();return a.def!=null?a.def.ac:10;}
    int attackBonusOf(Actor a){if(a==null)return 0;if(a.isPlayer&&hero!=null)return hero.attackBonus(hero.weaponOrFists());if(a.owner!=null)return attackBonusOf(a.owner);return a.def!=null?a.def.attackBonus:2;}
    int abilityMod(Actor a,int ability){if(a==null)return 0;if(a.isPlayer&&hero!=null)return hero.mod(ability);return a.def!=null?Rpg.mod(a.def.abilities[ability]):0;}
    /** Saving throw: d20 + ability modifier (+ proficiency for the hero's class ability) against a DC. */
    boolean save(Actor a,int ability,int dc){int r=Rpg.d20(rng);if(r==20)return true;if(r==1)return false;int bonus=abilityMod(a,ability)+(a.isPlayer&&hero!=null&&hero.cls.primary==ability?hero.prof():0);return r+bonus>=dc;}
    boolean weaponReady(){return weaponCooldown<=0;}
    /** The hero attacks with the equipped weapon: melee swing or a fired arrow. */
    int attack(){
        if(!weaponReady())return FIRE_COOLING;
        if(hero==null)return FIRE_NO_TARGET;
        if(camMode==CAM_THIRD&&camStyle==CS_ORBIT)player.angle=camYaw;   // attack where the camera looks
        Rpg.Item w=hero.weaponOrFists();
        weaponCooldown=w.cooldown;weaponFlash=.22f;player.attackTimer=.25f;
        if(w.ranged){
            Actor t=aimTarget(w.reach,.18f);sound(SoundFx.BOW,player);
            shoot(player,P_ARROW,t,player.angle,hero.attackBonus(w),new Rpg.Dice(w.damage.n,w.damage.sides,w.damage.bonus+hero.damageBonus(w)),w.damageType,false,0,0,22f);
            return FIRE_OK;
        }
        sound(SoundFx.SWING,player);
        Actor t=meleeTarget(w.reach);
        if(t==null)return FIRE_NO_TARGET;
        int roll=Rpg.d20(rng);if(roll==1&&hero.lucky()){roll=Rpg.d20(rng);floater(player,"lucky",0xffffe080);}boolean crit=roll==20;
        if(roll==1||(!crit&&roll+hero.attackBonus(w)<ac(t))){floater(t,"miss",0xffb0b8c0);if(t.fsm!=null&&t.target==null){t.target=player;t.fsm.send("DAMAGED");}return FIRE_OK;}
        int dmg=(crit?w.damage.rollCrit(rng):w.damage.roll(rng))+hero.damageBonus(w);
        if(crit&&hero.savage()&&w.damage.sides>0)dmg+=new Rpg.Dice(1,w.damage.sides,0).roll(rng);   // half-orc savage attacks
        if(hero.sneakDice()>0&&t.target!=player){dmg+=new Rpg.Dice(hero.sneakDice()*(crit?2:1),6,0).roll(rng);floater(t,"sneak attack",0xffc8a0ff);}
        effect(Sprites.FX_BLOOD,t.x-(float)Math.cos(player.angle)*t.radius*.6f,t.y-(float)Math.sin(player.angle)*t.radius*.6f,t.z+t.height*.6f);
        damage(t,Math.max(1,dmg),player,crit);sound(SoundFx.HIT,t);if(crit)shake(.12f,.12f);
        return FIRE_OK;
    }
    /** Closest living creature within reach in a cone in front of the player, with line of sight. */
    private Actor meleeTarget(float reach){
        Actor best=null;float bd=1e9f;
        for(Actor o:actors){if(o==player||!o.shootable||!o.alive||o.removed)continue;float d=dist(player,o)-o.radius;
            if(d>reach)continue;float da=Math.abs(angleDiff(angleTo(player,o),player.angle));if(da>.75f)continue;if(!lineOfSight(player,o))continue;
            float score=d+da*.8f;if(score<bd){bd=score;best=o;}}
        return best;
    }
    /** Auto-aim for ranged attacks and spells: the creature nearest the crosshair within a narrow cone. */
    Actor aimTarget(float range,float cone){
        Actor best=null;float bd=1e9f;
        for(Actor o:actors){if(o==player||!o.shootable||!o.alive||o.removed||o.projectileType>=0)continue;float d=dist(player,o);if(d>range)continue;
            float da=Math.abs(angleDiff(angleTo(player,o),player.angle));if(da>cone+Math.atan2(o.radius,Math.max(.5f,d)))continue;if(!lineOfSight(player,o))continue;
            if(da<bd){bd=da;best=o;}}
        return best;
    }
    /** The hero casts a spell (scrolls cast for free). Returns FIRE_* status. */
    int castSpell(Rpg.Spell sp,boolean free){
        if(hero==null||sp==null)return FIRE_NO_TARGET;
        if(!weaponReady())return FIRE_COOLING;
        int cost=free?0:Rpg.spellCost(sp.level);
        if(cost>hero.sp){message("Not enough spell points for "+sp.name,2);sound(SoundFx.OOF,player);return FIRE_NO_POWER;}
        if(camMode==CAM_THIRD&&camStyle==CS_ORBIT)player.angle=camYaw;
        hero.sp-=cost;weaponCooldown=sp.level==0?.8f:1.1f;weaponFlash=.3f;player.attackTimer=.3f;
        int atk=free?5+Math.max(hero.spellAttack(),0):hero.spellAttack(),dc=free?13:hero.saveDC(),mod=free?2:hero.castMod();
        sound(SoundFx.SPELL,player);
        // cantrips grow stronger at levels 5, 11 and 17
        int scale=sp.level==0?(hero.level>=17?4:hero.level>=11?3:hero.level>=5?2:1):1;
        Rpg.Dice dice=new Rpg.Dice(sp.dice.n*scale,sp.dice.sides,sp.dice.bonus);
        switch(sp.kind){
            case Rpg.S_BOLT:shoot(player,P_FIRE,aimTarget(sp.range,.2f),player.angle,atk,dice,sp.damageType,false,0,0,16f);break;
            case Rpg.S_MISSILE:{Actor t=aimTarget(sp.range,.35f);for(int k=0;k<3;k++){Actor m=shoot(player,P_MAGIC,t,player.angle+(k-1)*.12f,0,dice,sp.damageType,true,0,0,13f+k);if(m!=null&&t==null)m.angle+=0;}break;}
            case Rpg.S_AREA:shoot(player,P_FIRE,aimTarget(sp.range,.2f),player.angle,0,dice,sp.damageType,true,sp.radius,dc,14f);break;
            case Rpg.S_SAVE_TARGET:{Actor t=aimTarget(sp.range,.3f);if(t==null){message("No target in sight",1.5f);break;}
                effect(Sprites.FX_TELEPORT,t.x,t.y,t.z+.2f);if(save(t,Rpg.DEX,dc))floater(t,"saved",0xffb0b8c0);else damage(t,dice.roll(rng),player,false);if(t.target==null)t.target=player;break;}
            case Rpg.S_CONE:{float cs=(float)Math.cos(player.angle),sn=(float)Math.sin(player.angle);effect(Sprites.FX_EXPLOSION,player.x+cs*1.2f,player.y+sn*1.2f,player.z+.8f);
                for(int i=0;i<actors.size();i++){Actor o=actors.get(i);if(o==player||!o.shootable||!o.alive)continue;float d=dist(player,o)-o.radius;if(d>sp.range)continue;
                    if(Math.abs(angleDiff(angleTo(player,o),player.angle))>.6f||!lineOfSight(player,o))continue;int dmg=dice.roll(rng);boolean ok=save(o,Rpg.DEX,dc);damage(o,Math.max(1,ok?dmg/2:dmg),player,false);if(ok)floater(o,"half",0xffb0b8c0);}break;}
            case Rpg.S_HEAL:{int h=dice.roll(rng)+mod;heal(player,h);effect(Sprites.FX_TELEPORT,player.x+(float)Math.cos(player.angle)*.6f,player.y+(float)Math.sin(player.angle)*.6f,player.z+.6f);sound(SoundFx.HEAL,player);break;}
            case Rpg.S_WARD:hero.wardAC=Math.max(1,dice.bonus);hero.wardTime=8;flash(3,.3f);message(sp.name+": +"+hero.wardAC+" AC",2);break;
        }
        return FIRE_OK;
    }
    /** Fireball-style blast: DEX save for half damage. */
    void areaDamage(Actor src,float x,float y,float z,float radius,Rpg.Dice dice,int type,int dc){
        int dmg=dice==null?20:dice.roll(rng);
        for(int i=0;i<actors.size();i++){Actor a=actors.get(i);if(!a.shootable||!a.alive||a.projectileType>=0)continue;
            float d=(float)Math.hypot(a.x-x,a.y-y)-a.radius;if(d>=radius)continue;
            boolean ok=save(a,Rpg.DEX,dc);damage(a,Math.max(1,ok?dmg/2:dmg),src,false);if(ok)floater(a,"half",0xffb0b8c0);}
        if(player!=null&&Math.hypot(player.x-x,player.y-y)<radius*2)shake(.4f,.35f);
    }
    /** Heals an actor up to its maximum; shows a green number. */
    int heal(Actor a,int amount){if(a==null||!a.alive)return 0;int max=a.isPlayer&&hero!=null?hero.maxHp:Math.max(a.maxHealth,1);int before=a.health;a.health=Math.min(max,a.health+Math.max(0,amount));int got=a.health-before;if(got>0)floater(a,"+"+got,0xff60e070);return got;}
    /** Monster melee using its own stats: d20 + attack bonus against AC, damage dice on a hit. */
    boolean meleeAttack(Actor src,float reach){
        if(src==null||!src.alive||src.def==null)return false;Actor t=src.target;if(t==null||!t.alive)return false;
        if(dist(src,t)>reach+t.radius||!lineOfSight(src,t))return false;
        src.attackTimer=.35f;sound(SoundFx.SWING,src);
        int roll=Rpg.d20(rng);boolean crit=roll==20;
        if(roll==1||(!crit&&roll+src.def.attackBonus<ac(t))){floater(t,"miss",0xffb0b8c0);return true;}
        Rpg.Dice dd=Rpg.Dice.parse(src.def.damage);int md=crit?dd.rollCrit(rng):dd.roll(rng);
        if(t==player&&hero!=null&&hero.resists(src.def.damageType)){md/=2;floater(t,"resisted",0xffa0c0ff);}
        damage(t,Math.max(1,md),src,crit);sound(SoundFx.HIT,t);return true;
    }
    /** Monster ranged attack (arrow or bolt) using its own stats. */
    void rangedAttack(Actor src,int type){
        if(src==null||!src.alive||src.def==null)return;src.attackTimer=.35f;
        shoot(src,type,src.target,src.angle,src.def.attackBonus,Rpg.Dice.parse(src.def.damage),src.def.damageType,false,0,0,type==P_ARROW?18f:12f);
        sound(type==P_ARROW?SoundFx.BOW:SoundFx.SPELL,src);
    }
    /** Monster spellcasting (spell attack = its attack bonus, save DC = 8 + attack bonus). */
    void monsterCast(Actor src,Rpg.Spell sp){
        if(src==null||!src.alive||src.def==null||sp==null)return;src.attackTimer=.4f;Actor t=src.target;int atk=src.def.attackBonus,dc=8+atk;
        sound(SoundFx.SPELL,src);
        switch(sp.kind){
            case Rpg.S_HEAL:heal(src,sp.dice.roll(rng)+2);effect(Sprites.FX_TELEPORT,src.x,src.y,src.z+.5f);break;
            case Rpg.S_MISSILE:for(int k=0;k<3;k++)shoot(src,P_MAGIC,t,src.angle+(k-1)*.12f,0,sp.dice,sp.damageType,true,0,0,11f);break;
            case Rpg.S_AREA:shoot(src,P_FIRE,t,src.angle,0,sp.dice,sp.damageType,true,sp.radius,dc,11f);break;
            case Rpg.S_SAVE_TARGET:case Rpg.S_CONE:if(t!=null&&dist(src,t)<sp.range&&lineOfSight(src,t)){effect(Sprites.FX_TELEPORT,t.x,t.y,t.z+.2f);int dmg=sp.dice.roll(rng);damage(t,save(t,Rpg.DEX,dc)?dmg/2:dmg,src,false);}break;
            default:shoot(src,P_FIRE,t,src.angle,atk,sp.dice,sp.damageType,false,0,0,12f);
        }
    }
    /** Hero gains XP; levels up with a fanfare. */
    void awardXp(int amount,Actor from){
        if(hero==null||amount<=0)return;int hpBefore=hero.maxHp;
        if(from!=null)floater(from,"+"+amount+" XP",0xff9ad0ff);
        int gained=hero.gainXp(amount);
        if(gained>0){player.health+=hero.maxHp-hpBefore;hero.sp=hero.maxSp;
            centerPrint("Level "+hero.level+"!",2.5f);message("You are now a level "+hero.level+" "+hero.cls.name.toLowerCase()+" ("+hero.maxHp+" HP"+(hero.isCaster()?", "+hero.maxSp+" SP":"")+")",4);
            flash(1,.45f);sound(SoundFx.LEVEL_UP,player);levelUps+=gained;}
    }
    int levelUps;
    /** Inventory screen requested by an FSM action (the editor opens it). */
    boolean menuRequested;
    /** Equips the next weapon in the hero's pack. */
    void nextWeapon(){Hero h=hero;if(h==null)return;java.util.ArrayList<Rpg.Item> ws=new java.util.ArrayList<>();for(Hero.Stack st:h.inventory)if(st.item.kind==Rpg.I_WEAPON&&!ws.contains(st.item))ws.add(st.item);
        if(ws.size()>1){int k=(ws.indexOf(h.weapon)+1)%ws.size();String err=h.equip(ws.get(k));message(err!=null?err:"Wielding "+ws.get(k).name,1.5f);weaponRaise=1;}}
    /** Gives the hero an item (equips it if the slot is empty). */
    boolean giveItem(Rpg.Item it,int count){
        if(hero==null||it==null)return false;hero.add(it,count);
        if(it.kind==Rpg.I_WEAPON&&(hero.weapon==null))hero.equip(it);
        if(it.kind==Rpg.I_ARMOR&&hero.armour==null)hero.equip(it);
        if(it.kind==Rpg.I_SHIELD&&hero.shield==null)hero.equip(it);
        return true;
    }
    // ------------------------------------------------------------------ loot and shops (Content editor)
    /** Rolls a loot table into the hero's pack; returns what was found ("" if nothing). */
    String giveLoot(Rpg.LootTable t){
        if(t==null||hero==null)return "";
        StringBuilder b=new StringBuilder();
        int g=Math.max(0,Rpg.Dice.parse(t.gold).roll(rng));
        for(Rpg.LootEntry e:t.entries){if(rng.nextInt(100)>=e.chance)continue;Rpg.Item it=Rpg.item(e.item);if(it==null)continue;
            int n=Math.max(1,Rpg.Dice.parse(e.count).roll(rng));if(giveItem(it,n)){if(b.length()>0)b.append(", ");b.append(n>1?n+"× "+it.name:it.name);}}
        if(g>0){giveGold(g);if(b.length()>0)b.append(", ");b.append(g).append(" gold");}
        if(b.length()>0)message("Found: "+b,3);
        return b.toString();
    }
    /** Shop open over the game (drawn by the play screen), the entity that opened it and its close event. */
    Rpg.Shop shop;Actor shopOwner;String shopClosedEvent="";
    // themed message box from the Show Popup action; the game waits while it is open
    static final class Popup {String title,text;String[] buttons,events;Actor owner;}
    Popup popup;
    static final String[][] POPUP_BUTTONS={{"OK"},{"Yes","No"},{"Accept","Decline"},{"Continue"}};
    void showPopup(Actor owner,String title,String text,int kind,String event,String event2){
        Popup p=new Popup();p.owner=owner;p.title=title==null?"":title;p.text=text==null?"":text;p.buttons=POPUP_BUTTONS[Math.max(0,Math.min(POPUP_BUTTONS.length-1,kind))];
        p.events=new String[]{event==null?"":event,event2==null?"":event2};popup=p;paused=true;input.clear();
    }
    void choosePopup(int i){Popup p=popup;if(p==null)return;popup=null;paused=false;
        String ev=i>=0&&i<p.events.length?p.events[i]:"";if(p.owner!=null&&p.owner.fsm!=null&&!ev.isEmpty())p.owner.fsm.send(ev);}
    void openShop(Rpg.Shop s,Actor owner,String closedEvent){if(s==null||hero==null)return;shop=s;shopOwner=owner;shopClosedEvent=closedEvent==null?"":closedEvent;paused=true;}
    void closeShop(){if(shop==null)return;shop=null;paused=false;if(shopOwner!=null&&shopOwner.fsm!=null&&!shopClosedEvent.isEmpty())shopOwner.fsm.send(shopClosedEvent);shopOwner=null;}
    /** Units left of a shop line this game (-1 = unlimited). */
    int stockLeft(Rpg.Shop s,int line){Rpg.ShopEntry e=s.stock.get(line);if(e.stock<0)return -1;Integer v=session==null?null:session.shopStock.get(s.id+":"+line);return v==null?e.stock:v;}
    String buy(Rpg.Shop s,int line){
        if(hero==null||line<0||line>=s.stock.size())return "";
        Rpg.ShopEntry e=s.stock.get(line);Rpg.Item it=Rpg.item(e.item);if(it==null)return "That item no longer exists";
        int left=stockLeft(s,line);if(left==0)return "Sold out";
        int price=Rpg.price(e);if(hero.gold<price)return "Not enough gold ("+price+" needed)";
        if(!giveItem(it,1))return "You can't carry that";
        hero.gold-=price;sound(SoundFx.COIN,player);if(left>0&&session!=null)session.shopStock.put(s.id+":"+line,left-1);
        return "Bought "+it.name+" for "+price+" gold";
    }
    int sellPrice(Rpg.Shop s,Rpg.Item it){return it==null||it.value<=0?0:Math.max(1,it.value*s.sellRate/100);}
    String sell(Rpg.Shop s,Rpg.Item it){
        if(hero==null||it==null)return "";int price=sellPrice(s,it);if(price<=0)return "The merchant won't buy that";
        if(hero.isEquipped(it)&&hero.count(it)<=1)return "Unequip it first – it's in use";
        if(!hero.remove(it,1))return "";hero.gold+=price;sound(SoundFx.COIN,player);return "Sold "+it.name+" for "+price+" gold";
    }
    void giveGold(int g){if(hero==null||g==0)return;hero.gold=Math.max(0,hero.gold+g);if(g>0){floater(player,"+"+g+" gold",0xffffd040);sound(SoundFx.COIN,player);}}
    /** Uses an inventory item: drink a potion, read a scroll, equip gear. Returns a message. */
    String useItem(Rpg.Item it){
        if(hero==null||it==null||hero.count(it)==0)return "You don't have that";
        switch(it.kind){
            case Rpg.I_POTION:{
                if(it.heal!=null){if(player.health>=hero.maxHp)return "You're already at full health";hero.remove(it,1);int h=heal(player,it.heal.roll(rng));sound(SoundFx.POTION,player);flash(3,.25f);return "You drink the "+it.name.toLowerCase()+" (+"+h+" HP)";}
                if(hero.sp>=hero.maxSp)return hero.maxSp==0?"Nothing happens":"Your magic is already full";
                hero.remove(it,1);hero.sp=Math.min(hero.maxSp,hero.sp+it.spellPoints);sound(SoundFx.POTION,player);return "Arcane power flows back (+"+it.spellPoints+" SP)";}
            case Rpg.I_SCROLL:{Rpg.Spell sp=Rpg.spell(it.spell);if(sp==null)return "The scroll is blank";hero.remove(it,1);paused=false;castSpell(sp,true);return "You read the "+it.name.toLowerCase();}
            case Rpg.I_WEAPON:case Rpg.I_ARMOR:case Rpg.I_SHIELD:{if(hero.isEquipped(it)){hero.unequip(it);return "Unequipped "+it.name;}String err=hero.equip(it);return err!=null?err:"Equipped "+it.name;}
            default:return it.name+" — "+it.stats();
        }
    }
    /** Quick heal button: drinks the best healing potion. */
    boolean quaff(){if(hero==null)return false;Rpg.Item p=hero.bestPotion(hero.maxHp-player.health);if(p==null){message("No healing potions",1.5f);sound(SoundFx.OOF,player);return false;}String m=useItem(p);message(m,2);return true;}
    boolean giveKey(int k){if(k<0||k>2||hero==null)return false;Rpg.Item it=Rpg.item(k==0?"bronze_key":k==1?"silver_key":"gold_key");return giveItem(it,1);}
    boolean hasKey(int k){return hero!=null&&hero.hasKey(k);}
    // ---- dialogue & quests
    void say(Actor who,String speaker,String text,String[] choices,String[] events){Dialogue d=new Dialogue();d.who=who;d.speaker=speaker;d.text=text;d.choices=choices;d.events=events;dialogue=d;input.clear();}
    void chooseDialogue(int i){Dialogue d=dialogue;if(d!=null&&d.tree!=null){chooseTalk(d,i);return;}dialogue=null;if(d==null)return;if(d.events!=null&&i>=0&&i<d.events.length&&d.events[i]!=null&&!d.events[i].isEmpty()&&d.who!=null&&d.who.fsm!=null)d.who.fsm.send(d.events[i]);}
    // ------------------------------------------------------------------ conversation trees (Dialogue editor)
    void startTalk(Actor who,com.retro.doomeditor.Dialogue.Def def,String endEvent){
        if(def==null||def.nodes.isEmpty()){if(who!=null&&who.fsm!=null&&endEvent!=null&&!endEvent.isEmpty())who.fsm.send(endEvent);return;}
        Dialogue d=new Dialogue();d.who=who;d.tree=def;d.endEvent=endEvent==null?"":endEvent;
        showNode(d,def.node(def.start)!=null?def.node(def.start):def.nodes.get(0),0);
    }
    private void showNode(Dialogue d,com.retro.doomeditor.Dialogue.Node n,int depth){
        if(n==null||depth>20){endTalk(d);return;}
        d.node=n;
        for(com.retro.doomeditor.Dialogue.Effect e:n.effects){if(applyEffect(d,e))return;}   // an effect ended the talk (shop)
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();java.util.ArrayList<Integer> map=new java.util.ArrayList<>();
        for(int i=0;i<n.choices.size();i++){com.retro.doomeditor.Dialogue.Choice c=n.choices.get(i);if(check(c.cond)){labels.add(c.text.isEmpty()?"…":c.text);map.add(i);}}
        if(labels.isEmpty()){labels.add(n.next.isEmpty()?"Farewell":"Continue");map.add(-1);}
        d.choiceMap=new int[map.size()];for(int i=0;i<map.size();i++)d.choiceMap[i]=map.get(i);
        String sp=!n.speaker.isEmpty()?n.speaker:!d.tree.speaker.isEmpty()?d.tree.speaker:d.who!=null&&d.who.def!=null?d.who.def.name:"";
        d.speaker=sp;d.text=n.text;d.choices=labels.toArray(new String[0]);d.events=null;
        dialogue=d;input.clear();
    }
    private void chooseTalk(Dialogue d,int i){
        if(i<0||i>=d.choiceMap.length)return;
        int ci=d.choiceMap[i];String target=ci<0?d.node.next:d.node.choices.get(ci).target;
        com.retro.doomeditor.Dialogue.Node nx=target==null||target.isEmpty()?null:d.tree.node(target);
        if(nx==null)endTalk(d);else showNode(d,nx,0);
    }
    private void endTalk(Dialogue d){if(dialogue==d)dialogue=null;if(d.who!=null&&d.who.fsm!=null&&!d.endEvent.isEmpty())d.who.fsm.send(d.endEvent);}
    private static String varName(String s){return s.startsWith("g.")?s.substring(2):s;}
    boolean check(com.retro.doomeditor.Dialogue.Cond c){
        boolean r;
        switch(c.kind){
            case com.retro.doomeditor.Dialogue.C_ITEM:{Rpg.Item it=Rpg.item(c.s);r=hero!=null&&it!=null&&hero.count(it)>=Math.max(1,c.n);break;}
            case com.retro.doomeditor.Dialogue.C_QUEST:r=questState(c.s)==c.n;break;
            case com.retro.doomeditor.Dialogue.C_GOLD:r=hero!=null&&hero.gold>=c.n;break;
            case com.retro.doomeditor.Dialogue.C_VAR:{Float v=globals.get(varName(c.s));r=(v==null?0:v)>=c.n;break;}
            case com.retro.doomeditor.Dialogue.C_CLASS:r=hero!=null&&hero.cls.name.equalsIgnoreCase(c.s);break;
            case com.retro.doomeditor.Dialogue.C_LEVEL:r=hero!=null&&hero.level>=c.n;break;
            default:return true;
        }
        return c.not!=r;
    }
    /** Runs a node effect; returns true if it ended the conversation. */
    private boolean applyEffect(Dialogue d,com.retro.doomeditor.Dialogue.Effect e){
        switch(e.kind){
            case com.retro.doomeditor.Dialogue.E_GIVE_ITEM:{Rpg.Item it=Rpg.item(e.s);if(it!=null&&giveItem(it,Math.max(1,e.n)))message("Received "+(e.n>1?e.n+"× ":"")+it.name,2.5f);break;}
            case com.retro.doomeditor.Dialogue.E_TAKE_ITEM:{Rpg.Item it=Rpg.item(e.s);if(it!=null&&hero!=null)hero.remove(it,Math.max(1,e.n));break;}
            case com.retro.doomeditor.Dialogue.E_GIVE_GOLD:giveGold(e.n);break;
            case com.retro.doomeditor.Dialogue.E_TAKE_GOLD:if(hero!=null)hero.gold=Math.max(0,hero.gold-e.n);break;
            case com.retro.doomeditor.Dialogue.E_START_QUEST:questBegin(e.s);break;
            case com.retro.doomeditor.Dialogue.E_QUEST_STAGE:questStage(e.s,e.n);break;
            case com.retro.doomeditor.Dialogue.E_COMPLETE_QUEST:questFinish(e.s);break;
            case com.retro.doomeditor.Dialogue.E_FAIL_QUEST:{Quest q=quest(e.s);if(q.state!=Q_FAILED){q.state=Q_FAILED;message("Quest failed: "+q.title,3);}break;}
            case com.retro.doomeditor.Dialogue.E_SET_VAR:globals.put(varName(e.s),(float)e.n);break;
            case com.retro.doomeditor.Dialogue.E_ADD_VAR:{String k=varName(e.s);Float v=globals.get(k);globals.put(k,(v==null?0:v)+e.n);break;}
            case com.retro.doomeditor.Dialogue.E_XP:awardXp(e.n,player);break;
            case com.retro.doomeditor.Dialogue.E_LOOT:giveLoot(Rpg.loot(e.s));break;
            case com.retro.doomeditor.Dialogue.E_SHOP:{Rpg.Shop s=Rpg.shop(e.s);endTalk(d);if(s!=null)openShop(s,d.who,"");return true;}
            case com.retro.doomeditor.Dialogue.E_EVENT:if(d.who!=null&&d.who.fsm!=null&&!e.s.isEmpty())d.who.fsm.send(e.s);break;
            default:if(hero!=null){heal(player,hero.maxHp);hero.sp=hero.maxSp;message("You feel rested",2);}
        }
        return false;
    }
    // ------------------------------------------------------------------ quests defined in the Dialogue editor
    void questBegin(String id){
        Quest q=quest(id);if(q.state!=Q_INACTIVE)return;com.retro.doomeditor.Dialogue.QuestDef qd=project.questDef(id);
        q.state=Q_ACTIVE;q.stage=0;if(qd!=null){q.title=qd.title;q.text=qd.objective(0);}
        centerPrint("New quest: "+q.title,3);sound(SoundFx.SECRET,player);
    }
    /** Stage numbers are 1-based in the editor. */
    void questStage(String id,int stage){
        Quest q=quest(id);com.retro.doomeditor.Dialogue.QuestDef qd=project.questDef(id);
        if(q.state==Q_INACTIVE)questBegin(id);if(q.state!=Q_ACTIVE)return;
        q.stage=Math.max(0,stage-1);if(qd!=null){q.text=qd.objective(q.stage);message("Journal updated: "+q.text,3);}
    }
    void questFinish(String id){
        Quest q=quest(id);if(q.state==Q_DONE)return;com.retro.doomeditor.Dialogue.QuestDef qd=project.questDef(id);
        if(qd!=null&&q.title.equals(q.id))q.title=qd.title;
        q.state=Q_DONE;centerPrint("Quest complete: "+q.title,3);sound(SoundFx.LEVEL_UP,player);
        if(qd!=null){if(qd.gold>0)giveGold(qd.gold);Rpg.Item it=Rpg.item(qd.rewardItem);if(it!=null)giveItem(it,qd.rewardCount);awardXp(qd.xp,player);}
    }
    Quest quest(String id){Quest q=quests.get(id);if(q==null){q=new Quest(id);q.title=id;q.text="";quests.put(id,q);}return q;}
    int questState(String id){Quest q=quests.get(id);return q==null?Q_INACTIVE:q.state;}
    boolean giveHealth(Actor a,int amount,int max){if(a==null||!a.alive)return false;if(a.isPlayer&&hero!=null)max=hero.maxHp;if(a.health>=max)return false;int b=a.health;a.health=Math.min(max,a.health+amount);floater(a,"+"+(a.health-b),0xff60e070);return true;}

    // ------------------------------------------------------------------ use, touch, specials
    /** Use whatever is directly in front: a door/lift linedef or an entity. */
    void use(Actor a){
        if(a==player&&camMode==CAM_THIRD&&camStyle==CS_ORBIT)a.angle=camYaw;
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
                if(sec.tag>0&&!hasKey(sec.tag-1)){message("You need the "+Level.KEY_NAMES[sec.tag].toLowerCase()+" to open this door",2.5f);sound(SoundFx.OOF,a);return;}
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
        if(s.special==Level.SP_EXIT&&onFloor){if(s.tag>0&&project.map(s.tag)!=null)goToMap(s.tag,"");else exitLevel();}
        if(s.special==Level.SP_SECRET&&!secretFound[ps]){secretFound[ps]=true;secrets++;centerPrint("A secret is revealed!",2.5f);sound(SoundFx.SECRET,player);}
    }

    // ------------------------------------------------------------------ animation
    private CustomSprite.Clip clipOf(Actor a){if(a.animClip==null||a.def==null)return null;CustomSprite cs=project.sprite(a.def.customSprite);return cs==null?null:cs.clip(a.animClip);}
    void playAnim(Actor a,String clip,float speed,int loop,boolean restart){
        if(clip==null||clip.isEmpty()){a.animClip=null;return;}
        if(restart||!clip.equals(a.animClip)){a.animTime=0;a.lastAnimFrame=-1;}
        a.animClip=clip;a.animSpeed=Math.max(0,speed);a.animLoop=loop;
    }
    /** True once a play-once clip has shown its last frame (looping clips never finish). */
    boolean animFinished(Actor a){
        if(a.animClip==null)return true;
        CustomSprite.Clip c=clipOf(a);
        boolean loop=a.animLoop<0?(c==null||c.loop):a.animLoop==1;
        if(loop)return false;
        float len=c!=null?CustomSprite.length(c):.6f;
        return a.animTime>=len;
    }
    /** Current frame number (0-based) of the actor's animation. */
    int animFrame(Actor a){CustomSprite.Clip c=clipOf(a);if(c==null)return a.animClip==null?0:(int)(a.animTime*6);return Project.animIndex(c,a);}

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
    void shake(float strength,float secs){if(!GameSettings.cameraShake)return;shakeStrength=Math.max(shakeStrength*(shakeTime>0?1:0),strength);shakeTime=Math.max(shakeTime,secs);}
    void exitLevel(){if(!levelComplete){levelComplete=true;completeTime=0;sound(SoundFx.SWITCH,player);}}
    void requestRestart(){restartRequested=true;}
    void sound(int id,Actor at){sound(id,at,null);}
    void sound(int id,Actor at,float[] pos){
        if(sfx==null||id<0)return;
        float vol=distanceVolume(at,pos);
        if(vol>.02f)sfx.play(id,vol);
    }
    private float distanceVolume(Actor at,float[] pos){
        if(player==null||at==player)return 1;
        float x=at!=null?at.x:pos!=null?pos[0]:player.x,y=at!=null?at.y:pos!=null?pos[1]:player.y;
        float d=(float)Math.hypot(x-player.x,y-player.y);return Math.max(0,1-d/28f);
    }
    /** Plays an FSM sound value (project sound id or built-in name). Returns a handle for soundPlaying, or 0. */
    int playSound(String value,Actor at,float volume,float speed,boolean positional){
        if(sfx==null||value==null||value.isEmpty())return 0;
        SoundAsset a=project.sound(value);short[] clip=a!=null?a.render():sfx.clip(SoundFx.builtinIndex(value));
        float vol=volume*(positional?distanceVolume(at,null):1);
        if(clip==null||vol<=.02f)return 0;
        return sfx.play(clip,vol,speed);
    }
    boolean soundPlaying(int handle){return sfx!=null&&sfx.playing(handle);}
    void stopSounds(){if(sfx!=null)sfx.stopSounds();}
    void setSoundVolume(float v){if(sfx!=null)sfx.sfxVolume=Math.max(0,Math.min(1,v));}
    void playMusic(String songId,boolean loop,float fadeIn){
        if(sfx!=null)sfx.musicTag=songId==null?"":songId;
        if(sfx==null)return;Song m=project.song(songId);
        if(m==null){sfx.stopMusic(0);return;}
        sfx.playMusic(m.compile(project),loop,fadeIn);
    }
    void stopMusic(float fadeOut){if(sfx!=null)sfx.stopMusic(fadeOut);}
    void setMusicVolume(float v){if(sfx!=null)sfx.setMusicVolume(v);}
    boolean musicPlaying(){return sfx!=null&&sfx.musicPlaying();}
    long musicTicks(){return sfx==null?0:sfx.musicTicks;}
}
