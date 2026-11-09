package com.retro.doomeditor;

import static com.retro.doomeditor.SpriteModels.v;
import static com.retro.doomeditor.SpriteModels.add;
import static com.retro.doomeditor.SpriteModels.sub;
import static com.retro.doomeditor.SpriteModels.mul;
import static com.retro.doomeditor.SpriteModels.cross;
import static com.retro.doomeditor.SpriteModels.norm;
import static com.retro.doomeditor.SpriteModels.len;

/**
 * Every built-in character box-modelled by hand like a PS1 model (at most 500 triangles each), all with the same rules:
 * <ul>
 *   <li>Upper arm and forearm: boxes shaped into a bicep and forearm. Each end face is cut corner to corner both
 *       ways and the centre vertex pushed out into a low pyramid, so the ends look rounded where they meet. The
 *       ends overlap a little at the elbow, like the separate limb pieces of PS1-era characters, so bending never
 *       opens a gap.</li>
 *   <li>Fist: a box with the same pyramid end where it joins the forearm, flat knuckles.</li>
 *   <li>Thigh and shin: 5-sided cylinders; each cap is a fan of triangles to a centre vertex pushed out a little.</li>
 *   <li>Torso (one loop cut at the chest) and pelvis: shaped boxes. Neck: a box. Feet: a box loop-cut halfway,
 *       stretched into a foot, the toe end scaled in for a mild point.</li>
 *   <li>Head: one cube subdivided once. Front and sides flat, top rounded; at the bottom the back faces lift up and
 *       the front faces drop and narrow into a jaw. The face is painted on the flat front.</li>
 *   <li>Capes, robes, skirts and tabards are flat sheets of quads, never boxes.</li>
 *   <li>Tiny details (eyes that glow, gems, arrow fletching) are 8-triangle diamonds; ears, noses and tusks are
 *       boxes pulled into a pyramid point.</li>
 * </ul>
 * Detail that would cost polygons (ribs, belts, war paint, a sun on a shield) is painted into the texture instead,
 * the way PS1 artists did. Model space: x = forward, y = right, z = up, 1 unit = 1 grid square.
 */
final class BoxMesh {
    private BoxMesh(){}
    // atlas tiles (the same slots for every character)
    static final int SKIN=0,FACE=1,HAIR=2,TORSO=3,PELVIS=4,LEGS=5,BOOTS=6,HANDS=7,CLOAK=8,STEEL=9,TRIM=10,WOOD=11,UPPER=12,GLOW=13,X1=14,X2=15;
    // weapons
    static final int W_SWORD_SHIELD=0,W_BOW=1,W_DAGGERS=2,W_STAFF=3,W_MACE_SHIELD=4,W_RUSTY=5,W_CAST=6,W_CRUDE=7,W_AXE=8,W_NONE=9;
    // heads and faces
    static final int H_HAIR=0,H_HOOD=1,H_DEEP_HOOD=2,H_HAT=3,H_SKULL=4,H_GOBLIN=5,H_ORC=6;
    static final int F_HUMAN=0,F_SKULL=1,F_SHADOW=2,F_GOBLIN=3,F_ORC=4;

    /** What one character is made of. */
    static final class Spec {
        SpriteModels.Model m=new SpriteModels.Model();
        int weapon,head,face;
        boolean cape,robe,tunic,tabard,quiver,pauldrons,bones,beard,mask,sleeves,stubble=true,hairline=true,crouch,eyeGems;
        float scale=1,bulk=1,headS=1,shoulderW=1,chestW=1,legK=1;
        int iris=0xff34505e,hairRGB=0xff3a2414,beardRGB=0xff3a2414,maskRGB=0xff141418;
        // created heroes (CharLook): body type, hair, race features
        int tuskTile=X1;boolean female,solidEyes,heavyBrow,snout,tail,horns,tusks,dragonEyes;int hairStyle=1,ears;float nose,beardLen=1;String race="";
    }

    static boolean has(int id){
        return id==Sprites.CREATED||id==Sprites.HERO||id==Sprites.SKELETON||id==Sprites.GOBLIN||id==Sprites.ORC||id==Sprites.CULTIST||id==Sprites.VILLAGER||(id>=Sprites.HERO_RANGER&&id<=Sprites.HERO_CLERIC);
    }

    static int G;
    static float[] buf;static int nv;
    /** Tiles whose material glows (fire, magic): drawn full-bright. */
    static boolean[] glowTile=new boolean[16];
    /** Starts a frame for a model whose materials are {@code m} (atlas 4 x 4). */
    static void begin(SpriteModels.Model m){G=atlasFor(m);buf=new float[9*3*600];nv=0;quads=0;glowTile=new boolean[16];for(int i=0;i<Math.min(16,m.mats.size());i++)glowTile[i]=m.mats.get(i).glow;}
    /** Smallest square grid of 256-texel tiles that holds all of a model's materials (props: 2 x 2 is common, 1 MB instead of 4). */
    static int atlasFor(SpriteModels.Model m){int g=1;while(g*g<m.mats.size())g++;return g;}
    static MeshModels.Frame end(){MeshModels.Frame fr=new MeshModels.Frame();fr.v=java.util.Arrays.copyOf(buf,nv*9);fr.count=nv;return fr;}
    private static Spec S;

    /** A model for any look (the creation screen preview), without touching the shared Created Character model. */
    static synchronized MeshModels.Mesh buildLook(CharLook l){return make(specFor(l),outfitFor(Rpg.heroClass(l.cls)),"look_"+l.key(),false);}   // previews: not kept on disk
    static synchronized MeshModels.Mesh build(int id){
        Spec sp=id==Sprites.CREATED?specFor(CharLook.current):spec(id);if(sp==null)return null;
        return make(sp,id==Sprites.CREATED?outfitFor(Rpg.heroClass(CharLook.current.cls)):id,id==Sprites.CREATED?"look_"+CharLook.current.key():"char_"+id,true);
    }
    // ------------------------------------------------------------------ puppets
    /** What Puppet needs to draw a character's parts: the idle pose with part tags, every pose's joints, the painted (unlit) texture. */
    static synchronized Puppet.Source source(int id,boolean paint){
        Spec sp=id==Sprites.CREATED?specFor(CharLook.current):spec(id);if(sp==null)return null;
        return source(sp,id==Sprites.CREATED?outfitFor(Rpg.heroClass(CharLook.current.cls)):id,paint);}
    static Puppet.Source source(int id){return source(id,true);}
    static synchronized Puppet.Source sourceLook(CharLook l,boolean paint){return source(specFor(l),outfitFor(Rpg.heroClass(l.cls)),paint);}
    static Puppet.Source sourceLook(CharLook l){return sourceLook(l,true);}
    /** {@code paint}: false = poses and joints only (the parts' pictures are already on disk). */
    private static Puppet.Source source(Spec sp,int paintId,boolean paint){
        S=sp;G=4;for(int i=0;i<16;i++)glowTile[i]=i<sp.m.mats.size()&&sp.m.mats.get(i).glow;
        Puppet.Source src=new Puppet.Source();src.joints=new float[ANIM_FRAMES][][];
        for(int f=0;f<ANIM_FRAMES;f++){MeshModels.Frame fr=frame(f);src.joints[f]=joints;
            if(f==Sprites.F_IDLE){src.rest=fr.v;src.count=fr.count;src.parts=java.util.Arrays.copyOf(partBuf,fr.count);}}
        if(paint){MeshModels.Mesh out=new MeshModels.Mesh();out.atlas=G;PT=128;
            MeshModels.paint(out,sp.m,G,PT);paintFace(out);paintExtra(out,paintId);out.height=null;PT=MeshModels.TILE;   // painted, not lit: the puppet lights each view itself
            src.color=out.color;src.tile=128;}
        src.grid=G;src.mats=sp.m;
        S=null;buf=null;return src;
    }

    private static MeshModels.Mesh make(Spec sp,int paintId,String texKey,boolean keep){
        S=sp;G=4;for(int i=0;i<16;i++)glowTile[i]=i<sp.m.mats.size()&&sp.m.mats.get(i).glow;
        MeshModels.Mesh out=new MeshModels.Mesh();out.atlas=G;
        out.frames=new MeshModels.Frame[ANIM_FRAMES];
        for(int f=0;f<ANIM_FRAMES;f++)out.frames[f]=frame(f);
        if(shapeOnly){MeshModels.pack(out);S=null;buf=null;return out;}   // checks: geometry only
        PT=MeshModels.TILE;int Sz=G*MeshModels.TILE;int[] done=TexBake.load(texKey,Sz*Sz);
        if(done!=null)out.color=done;   // painted and lit on an earlier run
        else{MeshModels.paint(out,sp.m,G);paintFace(out);paintExtra(out,paintId);TexBake.bake(out,sp.m,true,FACE);if(keep)TexBake.save(texKey,out.color);}
        MeshModels.pack(out);
        S=null;buf=null;
        return out;
    }

    // ------------------------------------------------------------------ the characters
    /** The 16 materials in tile order: skin, face, hair, torso, pelvis, legs, boots, hands, cloak, steel, trim, wood, upper arm, glow, extra, extra. */
    private static Spec spec(int id){
        Spec s=new Spec();SpriteModels.Model m=s.m;
        switch(id){
            case Sprites.HERO:   // Fighter: plate, blue tabard, red cape, sword and round shield
                m.skin(0xffc89878,.55f);m.skin(0xffc89878,.55f);m.cloth(0xff4a2e18);m.metal(0xff8a9098);m.cloth(0xff2c4a8a);m.leather(0xff4a3624);
                m.leather(0xff2e2016);m.leather(0xff3a2a1c);m.cloth(0xff7a1e1e);m.metal(0xffb8c0c8);m.metal(0xffb08a40);m.wood(0xff6a4424);
                m.metal(0xff8a9098);m.glow(0xffffffff);m.leather(0xff3a2616);m.cloth(0xff2c4a8a);
                s.weapon=W_SWORD_SHIELD;s.head=H_HAIR;s.face=F_HUMAN;s.cape=true;break;
            case Sprites.HERO_RANGER:   // green hooded cloak, leathers, quiver, longbow
                m.skin(0xffc89878,.55f);m.skin(0xffc89878,.55f);m.cloth(0xff4a2e18);m.leather(0xff6a4a2a);m.leather(0xff4a3624);m.leather(0xff4a3a26);
                m.leather(0xff2e2016);m.leather(0xff3a2a1c);m.cloth(0xff2e4a26);m.metal(0xffb8c0c8);m.cloth(0xffd8d0b8);m.wood(0xff7a5230);
                m.leather(0xff6a4a2a);m.glow(0xffffffff);m.leather(0xff5a3a1e);m.cloth(0xffe0d8c8);
                s.weapon=W_BOW;s.head=H_HOOD;s.face=F_HUMAN;s.cape=true;s.quiver=true;s.iris=0xff3a5a2a;break;
            case Sprites.HERO_ROGUE:   // dark leathers, red sash, hood and mask, two daggers
                m.skin(0xffc89878,.55f);m.skin(0xffc89878,.55f);m.cloth(0xff1e1a16);m.leather(0xff3a3434);m.cloth(0xff6a1a22);m.leather(0xff2a2626);
                m.leather(0xff1a1616);m.leather(0xff1e1a1a);m.cloth(0xff26262c);m.metal(0xffc8ccd4);m.metal(0xffa0a0a8);m.leather(0xff2a1a12);
                m.leather(0xff3a3434);m.glow(0xffffffff);m.leather(0xff4a2e1a);m.cloth(0xff141418);
                s.weapon=W_DAGGERS;s.head=H_HOOD;s.face=F_HUMAN;s.mask=true;s.chestW=.94f;s.iris=0xff4a3a2a;break;
            case Sprites.HERO_WIZARD:   // long blue robe with wide sleeves, pointed hat, white beard, staff with a glowing orb
                m.skin(0xffd0a080,.55f);m.skin(0xffd0a080,.55f);m.cloth(0xffe0dcd4);m.cloth(0xff2a3a8a);m.cloth(0xffb09040);m.cloth(0xff2a3a8a);
                m.leather(0xff3a2a1c);m.skin(0xffd0a080,.55f);m.cloth(0xff2a3a8a);m.metal(0xffb8c0c8);m.cloth(0xffd0b040);m.wood(0xff5a3a20);
                m.cloth(0xff2a3a8a);m.glow(0xff60b0ff);m.cloth(0xffd0b040);m.cloth(0xff2a3a8a);
                s.weapon=W_STAFF;s.head=H_HAT;s.face=F_HUMAN;s.robe=true;s.sleeves=true;s.beard=true;s.beardRGB=0xffe0dcd4;s.hairRGB=0xffe0dcd4;s.stubble=false;s.hairline=false;s.iris=0xff3a5a8a;break;
            case Sprites.HERO_CLERIC:   // plate under a white and gold surcoat, pauldrons, mace and a shield with a holy sun
                m.skin(0xffc89878,.55f);m.skin(0xffc89878,.55f);m.cloth(0xff6a4a2a);m.cloth(0xffe8e4d8);m.cloth(0xffe8e4d8);m.metal(0xff8a9098);
                m.metal(0xff6a7078);m.metal(0xff8a9098);m.cloth(0xffe8e4d8);m.metal(0xffb8c0c8);m.metal(0xffd0a840);m.wood(0xff6a4424);
                m.metal(0xffa0a8b0);m.glow(0xffffffff);m.cloth(0xffe8e4d8);m.cloth(0xffe8e4d8);
                s.weapon=W_MACE_SHIELD;s.head=H_HAIR;s.face=F_HUMAN;s.tabard=true;s.pauldrons=true;s.hairRGB=0xff6a4a2a;break;
            case Sprites.SKELETON:   // bones, ribcage painted on a narrow box, skull with glowing eyes, rusty sword, loincloth
                {int bone=0xffd8cfb4;
                m.mat(bone,.25f,.25f);m.mat(bone,.25f,.25f);m.mat(bone,.25f,.25f);m.mat(bone,.25f,.25f);m.mat(bone,.25f,.25f);m.mat(bone,.25f,.25f);
                m.mat(bone,.25f,.25f);m.mat(bone,.25f,.25f);m.cloth(0xff4a3a2a);m.mat(0xff7a5a3c,.4f,.5f);m.mat(0xff7a5a3c,.4f,.5f);m.cloth(0xff4a3a2a);
                m.mat(bone,.25f,.25f);m.glow(0xff7ad8ff);m.cloth(0xff4a3a2a);m.mat(bone,.25f,.25f);}
                s.weapon=W_RUSTY;s.head=H_SKULL;s.face=F_SKULL;s.bones=true;s.bulk=.42f;s.chestW=.85f;s.eyeGems=true;break;
            case Sprites.GOBLIN:   // small, crouched and wiry: big ears, long nose, crude blade, leather jerkin
                m.skin(0xff6e8a3a,.35f);m.skin(0xff6e8a3a,.35f);m.cloth(0xff1e1a14);m.leather(0xff5a3e24);m.cloth(0xff5a4a30);m.skin(0xff6e8a3a,.35f);
                m.skin(0xff6e8a3a,.35f);m.skin(0xff6e8a3a,.35f);m.cloth(0xff5a4a30);m.mat(0xff8a6a48,.45f,.5f);m.plastic(0xffe8e0c0,.4f);m.leather(0xff3a2416);
                m.skin(0xff6e8a3a,.35f);m.glow(0xffffd030);m.cloth(0xff5a4a30);m.skin(0xff6e8a3a,.35f);
                s.weapon=W_CRUDE;s.head=H_GOBLIN;s.face=F_GOBLIN;s.scale=.74f;s.headS=1.22f;s.bulk=.9f;s.chestW=.9f;s.crouch=true;s.eyeGems=true;s.hairline=false;break;
            case Sprites.ORC:   // hulking, bare-chested: tusks, topknot, iron pauldrons, leather kilt, greataxe
                m.skin(0xff6a7a46,.3f);m.skin(0xff6a7a46,.3f);m.cloth(0xff141210);m.skin(0xff6a7a46,.3f);m.leather(0xff4a3020);m.leather(0xff3a2a1c);
                m.leather(0xff241a12);m.skin(0xff6a7a46,.3f);m.leather(0xff4a3020);m.metal(0xff9aa0a8);m.metal(0xff4a4c50);m.wood(0xff5a3a1e);
                m.skin(0xff6a7a46,.3f);m.glow(0xffff4a20);m.plastic(0xffeee6cc,.5f);m.leather(0xff4a3020);
                s.weapon=W_AXE;s.head=H_ORC;s.face=F_ORC;s.scale=1.08f;s.bulk=1.22f;s.chestW=1.15f;s.shoulderW=1.2f;s.headS=1.08f;s.pauldrons=true;s.tabard=true;s.eyeGems=true;s.hairline=false;break;
            case Sprites.CULTIST:   // long red robe with wide sleeves, deep hood with the face in shadow and glowing eyes, conjures fire
                m.skin(0xffb89a84,.5f);m.plastic(0xff0a0808,.05f);m.cloth(0xff5a1414);m.cloth(0xff5a1414);m.cloth(0xff2a1a10);m.cloth(0xff5a1414);
                m.leather(0xff2a1a12);m.skin(0xffb89a84,.5f);m.cloth(0xff5a1414);m.metal(0xffb8c0c8);m.cloth(0xffb08a30);m.wood(0xff5a3a20);
                m.cloth(0xff5a1414);m.glow(0xffff6a20);m.cloth(0xffb08a30);m.glow(0xffffb040);
                s.weapon=W_CAST;s.head=H_DEEP_HOOD;s.face=F_SHADOW;s.robe=true;s.sleeves=true;s.eyeGems=true;break;
            case Sprites.VILLAGER:   // tunic with a short skirt, apron, grey hair and beard; waves instead of attacking
                m.skin(0xffd0a080,.55f);m.skin(0xffd0a080,.55f);m.cloth(0xffb8b4ac);m.cloth(0xff7a5a34);m.cloth(0xff7a5a34);m.cloth(0xff4a4034);
                m.leather(0xff3a2a1c);m.skin(0xffd0a080,.55f);m.cloth(0xff7a5a34);m.metal(0xffb8c0c8);m.leather(0xff3a2616);m.wood(0xff6a4424);
                m.cloth(0xff7a5a34);m.glow(0xffffffff);m.cloth(0xffb8a888);m.cloth(0xff7a5a34);
                s.weapon=W_NONE;s.head=H_HAIR;s.face=F_HUMAN;s.tunic=true;s.beard=true;s.beardRGB=0xffb8b4ac;s.hairRGB=0xffb8b4ac;s.stubble=false;break;
            default:return null;
        }
        return s;
    }

    /** Which built-in hero outfit a class wears (custom classes pick by their main ability and gear). */
    static int outfitFor(Rpg.HeroClass hc){
        switch(hc.name){case "Fighter":return Sprites.HERO;case "Ranger":return Sprites.HERO_RANGER;case "Rogue":return Sprites.HERO_ROGUE;case "Wizard":return Sprites.HERO_WIZARD;case "Cleric":return Sprites.HERO_CLERIC;}
        if(hc.primary==Rpg.INT||hc.primary==Rpg.CHA&&hc.castAbility>=0)return Sprites.HERO_WIZARD;if(hc.primary==Rpg.WIS)return Sprites.HERO_CLERIC;
        if(hc.primary==Rpg.DEX){for(String g:hc.gear)if(g.contains("bow"))return Sprites.HERO_RANGER;return Sprites.HERO_ROGUE;}
        return Sprites.HERO;
    }
    private static void setMat(SpriteModels.Model m,int tile,int kind,int col){
        SpriteModels.Model t=new SpriteModels.Model();
        switch(kind){case 0:t.skin(col,.5f);break;case 1:t.cloth(col);break;case 2:t.glow(col);break;default:t.leather(col);}
        while(m.mats.size()<=tile)m.cloth(0xff808080);m.mats.set(tile,t.mats.get(0));
    }
    /** A hero from the character creation screen: the class outfit, then race body and head, then appearance. */
    static Spec specFor(CharLook l){
        Rpg.HeroClass hc=Rpg.heroClass(l.cls);int outfit=outfitFor(hc);
        Spec s=spec(outfit);String race=l.raceInfo().name;s.race=race;
        int skin=l.skinColour(),hair=l.hairColour();
        setMat(s.m,SKIN,0,skin);setMat(s.m,FACE,0,skin);if(outfit==Sprites.HERO_WIZARD)setMat(s.m,HANDS,0,skin);
        setMat(s.m,HAIR,1,hair);s.hairRGB=s.beardRGB=hair;s.iris=l.eyeColour();
        s.female=l.female;s.beard=l.beard;s.stubble=!l.female&&!l.beard;s.hairStyle=l.hair;s.hairline=l.hair!=0&&l.hair!=4;
        if(!l.headwear){if(s.head==H_HOOD||s.head==H_DEEP_HOOD||s.head==H_HAT)s.head=H_HAIR;s.mask=false;}
        if(l.female){s.shoulderW*=.9f;s.chestW*=.92f;s.scale*=.96f;}
        switch(race){
            case "Elf":s.scale*=1.05f;s.bulk*=.9f;s.shoulderW*=.94f;s.headS*=.97f;s.ears=2;break;
            case "Half-Elf":s.scale*=1.02f;s.bulk*=.95f;s.ears=1;break;
            case "Dwarf":s.legK=.8f;s.scale*=.8f;s.bulk*=1.28f;s.shoulderW*=1.16f;s.chestW*=1.14f;s.headS*=1.2f;s.beardLen=1.6f;break;
            case "Halfling":s.legK=.88f;s.scale*=.63f;s.headS*=1.22f;setMat(s.m,BOOTS,0,skin);break;
            case "Gnome":s.legK=.84f;s.scale*=.59f;s.headS*=1.38f;s.bulk*=.95f;s.ears=3;s.nose=.06f;break;
            case "Half-Orc":if(outfit==Sprites.HERO_RANGER)s.tuskTile=X2;else if(outfit!=Sprites.HERO_CLERIC){SpriteModels.Model t=new SpriteModels.Model();t.plastic(0xffeee6cc,.5f);s.m.mats.set(X1,t.mats.get(0));}   // ivory tusks
                s.scale*=1.04f;s.bulk*=1.16f;s.shoulderW*=1.1f;s.chestW*=1.08f;s.headS*=1.03f;s.tusks=true;s.heavyBrow=true;break;
            case "Tiefling":s.horns=true;s.tail=true;s.solidEyes=true;break;
            case "Dragonborn":s.scale*=1.07f;s.bulk*=1.14f;s.shoulderW*=1.1f;s.chestW*=1.06f;s.snout=true;s.tail=true;s.dragonEyes=true;s.eyeGems=true;
                s.hairStyle=0;s.hairline=false;s.beard=false;s.stubble=false;setMat(s.m,GLOW,2,l.eyeColour());break;
        }
        if(s.solidEyes)s.iris=0xffe0b030;
        return s;
    }

    // ------------------------------------------------------------------ animation
    /**
     * Animation poses. 0-5 are the classic frames (idle, walk A, walk B, attack, pain, dead) and the rest give smooth,
     * grounded motion: a breathing idle, 6-pose walk and run cycles (feet placed by IK: heel strike, roll, toe off,
     * swing; a flight phase when running), jump take-off, in-air, falling, landing, and a three-stage attack
     * (wind-up, strike, follow-through) shaped for each weapon. The renderer blends neighbouring poses.
     */
    static final int A_IDLE2=6,A_WALK=7,A_RUN=13,A_JUMP=19,A_AIR=20,A_FALL=21,A_LAND=22,A_WINDUP=23,A_STRIKE=24,A_FOLLOW=25,ANIM_FRAMES=26,CYCLE=6;
    /** Distance covered by one full walk / run cycle (two steps), in grid units. */
    static final float WALK_STRIDE=1.35f,RUN_STRIDE=2.4f,RUN_SPEED=3.6f;
    static final class Anim {
        float bob=-.015f,sway,lean=.02f,twist,headPitch,headYaw;
        float[] fL,fR;float pL,pR;            // foot sole targets on the ground (x forward, y right, z up) and toe pitch (+ = toes up)
        float swL=.06f,swR=.06f,elL=.25f,elR=.25f,spread;   // free arms: forward swing, elbow bend, sideways lift
        int atk=-1;boolean dead,pain,air;     // atk: 0 wind-up, 1 strike, 2 follow-through
        float handSwing,handLift,trail=.03f,tailSway,robeSway;
    }
    private static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    private static float cosT(float t){return (float)Math.cos(2*Math.PI*t);}
    /** One foot through a step: {x, height, toe pitch} at cycle phase t (0 = heel strike, front). */
    private static float[] footPath(float t,boolean run){
        t=t-(float)Math.floor(t);float st=run?.38f:.6f,half=run?.5f:.3f,lift=run?.26f:.085f;
        if(t<st){float s=t/st;float pitch=s<.15f?(run?.15f:.35f)*(1-s/.15f):s>.7f?-(run?.85f:.55f)*(s-.7f)/.3f:0;return new float[]{half-2*half*s,0,pitch};}
        float s=(t-st)/(1-st),e=smooth(s);float x=-half+2*half*e;if(run)x-=.22f*(float)Math.sin(Math.PI*s)*(1-s);
        float z=lift*(float)Math.sin(Math.PI*Math.pow(s,run?.7f:.85f));float pitch=-(run?.85f:.55f)*(1-s)*(1-s)+(s>.75f?(run?.15f:.35f)*(s-.75f)/.25f:0);
        return new float[]{x,z,pitch};
    }
    private static void cycle(Anim A,float t,boolean run,float hw){
        float[] l=footPath(t,run),r=footPath(t+.5f,run);
        A.fL=v(l[0],-hw,l[1]);A.pL=l[2];A.fR=v(r[0],hw,r[1]);A.pR=r[2];
        float c2=(float)Math.cos(4*Math.PI*(t-(run?.19f:0)));
        A.bob=run?-.04f-.035f*c2:-.02f-.014f*c2;
        A.sway=-(run?.01f:.018f)*cosT(t-(run?.19f:.3f));
        float amp=run?.7f:.32f;A.swR=amp*cosT(t);A.swL=-amp*cosT(t);
        A.elR=run?1.45f:.25f+.3f*Math.max(0,A.swR)/amp;A.elL=run?1.45f:.25f+.3f*Math.max(0,A.swL)/amp;
        A.twist=-(run?.16f:.08f)*cosT(t);A.headYaw=-A.twist*.8f;A.lean=run?.22f:.06f;A.headPitch=run?-.2f:-.03f;
        A.handSwing=run?.12f:.07f;A.handLift=run?.04f:0;A.trail=run?.26f:.12f;A.tailSway=.09f*(float)Math.sin(2*Math.PI*t);A.robeSway=(l[0]+r[0])*.25f;
    }
    /** The pose for animation frame f. */
    private static Anim anim(int f,Spec sp,SpriteModels.Body b){
        Anim A=new Anim();float hw=b.hipW;A.fL=v(.02f,-hw,0);A.fR=v(-.02f,hw,0);
        switch(f){
            case Sprites.F_DEAD:A.dead=true;A.fL=v(.05f,-hw-.05f,0);A.fR=v(-.1f,hw+.06f,0);A.bob=-.02f;A.swL=.25f;A.swR=.15f;return A;   // laid down afterwards
            case Sprites.F_PAIN:A.pain=true;A.lean=-.28f;A.headPitch=-.3f;A.bob=-.07f;A.fL=v(.12f,-hw,0);A.fR=v(-.12f,hw,0);A.pR=-.3f;A.swL=.6f;A.swR=.5f;A.elL=A.elR=.6f;A.spread=.35f;A.twist=.15f;break;
            case A_IDLE2:A.bob=-.008f;A.lean=-.012f;A.swL=A.swR=.09f;A.spread=.04f;A.headPitch=-.03f;break;
            case Sprites.F_WALK1:cycle(A,0,false,hw);break;
            case Sprites.F_WALK2:cycle(A,.5f,false,hw);break;
            case A_JUMP:A.air=true;A.bob=.03f;A.fL=v(.16f,-hw,.24f);A.pL=-.3f;A.fR=v(-.15f,hw,.07f);A.pR=-.85f;A.swL=1.3f;A.swR=-.45f;A.elL=.6f;A.elR=.3f;A.lean=-.04f;A.trail=.2f;A.headPitch=-.1f;A.handLift=.08f;break;
            case A_AIR:A.air=true;A.bob=.04f;A.fL=v(.09f,-hw*1.25f,.23f);A.pL=-.25f;A.fR=v(-.05f,hw*1.25f,.16f);A.pR=-.45f;A.swL=A.swR=.9f;A.spread=.7f;A.elL=A.elR=.4f;A.lean=.06f;A.trail=.22f;A.handLift=.1f;break;
            case A_FALL:A.air=true;A.bob=0;A.fL=v(.11f,-hw*1.15f,.04f);A.pL=.1f;A.fR=v(-.04f,hw*1.15f,.1f);A.pR=-.25f;A.swL=A.swR=1.55f;A.spread=.9f;A.elL=A.elR=.3f;A.lean=-.05f;A.trail=.28f;A.headPitch=.18f;A.handLift=.12f;break;
            case A_LAND:A.bob=-.28f;A.fL=v(.13f,-hw*1.4f,0);A.fR=v(-.07f,hw*1.4f,0);A.pR=-.15f;A.lean=.36f;A.headPitch=-.38f;A.swL=A.swR=.55f;A.spread=.35f;A.elL=A.elR=.5f;A.trail=.06f;A.handLift=-.06f;break;
            case Sprites.F_ATTACK:case A_STRIKE:attackPose(A,sp,1,hw);break;
            case A_WINDUP:attackPose(A,sp,0,hw);break;
            case A_FOLLOW:attackPose(A,sp,2,hw);break;
            default:
                if(f>=A_WALK&&f<A_WALK+CYCLE)cycle(A,(f-A_WALK)/(float)CYCLE,false,hw);
                else if(f>=A_RUN&&f<A_RUN+CYCLE)cycle(A,(f-A_RUN)/(float)CYCLE,true,hw);
        }
        if(sp.crouch&&!A.dead&&!A.pain){A.lean+=.25f;A.bob-=.08f;A.headPitch-=.2f;}
        return A;
    }
    /** Body and feet for an attack stage (0 wind-up, 1 strike, 2 follow-through); the hands come from hands(). */
    private static void attackPose(Anim A,Spec sp,int stage,float hw){
        A.atk=stage;A.trail=stage==1?.14f:.08f;
        switch(sp.weapon){
            case W_BOW:   // side-on archer's stance
                A.fL=v(.14f,-hw*1.3f,0);A.fR=v(-.12f,hw*1.2f,0);A.twist=stage==0?.35f:.25f;A.lean=.02f;A.bob=-.03f;A.headYaw=-A.twist;break;
            case W_STAFF:case W_CAST:   // step in and thrust the magic forward
                A.fL=v(stage==0?.08f:.26f,-hw*1.15f,0);A.fR=v(-.16f,hw,0);A.pR=stage==0?0:-.35f;A.lean=stage==0?-.08f:.16f;A.bob=stage==0?-.02f:-.06f;A.headPitch=stage==0?-.1f:0;break;
            case W_NONE:A.lean=-.02f;break;   // a friendly wave
            case W_DAGGERS:
                A.fL=v(stage==0?.1f:.32f,-hw*1.2f,0);A.fR=v(-.18f,hw*1.1f,0);A.pR=stage==0?0:-.45f;A.twist=stage==0?.3f:stage==1?-.35f:.25f;A.lean=stage==0?.1f:.22f;A.bob=stage==0?-.05f:-.08f;A.headYaw=-A.twist*.7f;break;
            case W_AXE:   // two-handed overhead chop
                A.fL=v(.2f,-hw*1.25f,0);A.fR=v(-.18f,hw*1.15f,0);A.pR=stage==0?0:-.3f;A.twist=stage==0?.15f:-.1f;A.lean=stage==0?-.1f:stage==1?.3f:.36f;A.bob=stage==0?-.02f:-.1f;A.headPitch=stage==0?-.15f:.05f;break;
            default:      // one-handed swing: weight back, then a lunging step through the cut
                A.fL=v(stage==0?.14f:.3f,-hw*1.15f,0);A.fR=v(stage==0?-.16f:-.22f,hw*1.1f,0);A.pR=stage==0?0:-.45f;A.pL=stage==1?.1f:0;
                A.twist=stage==0?.55f:stage==1?-.35f:-.7f;A.headYaw=-A.twist*.75f;A.lean=stage==0?-.05f:stage==1?.18f:.22f;A.bob=stage==0?-.03f:stage==1?-.07f:-.06f;
        }
    }
    private static float[] rotY(float[] p,float[] pv,float a){float dx=p[0]-pv[0],dz=p[2]-pv[2],c=(float)Math.cos(a),s=(float)Math.sin(a);return v(pv[0]+dx*c+dz*s,p[1],pv[2]-dx*s+dz*c);}
    /** Turns a point about the vertical axis through the body (positive = the right shoulder goes back). */
    private static float[] tz(float[] p,float a){float c=(float)Math.cos(a),s=(float)Math.sin(a);return v(p[0]*c-p[1]*s,p[0]*s+p[1]*c,p[2]);}
    private static MeshModels.Frame frame(int f){
        buf=new float[9*3*1000];nv=0;quads=0;curPart=Puppet.P_TORSO;float[][] J=new float[Puppet.JOINTS][];
        Spec sp=S;float k=sp.bulk;
        SpriteModels.Body b=new SpriteModels.Body();b.chestW*=sp.chestW;b.shoulderW*=sp.shoulderW;
        float drop=0;if(sp.legK!=1){drop=(b.thigh+b.shin)*(1-sp.legK);b.thigh*=sp.legK;b.shin*=sp.legK;b.hipZ-=drop;b.pelvisZ-=drop;b.waistZ-=drop;b.chestZ-=drop;b.shoulderZ-=drop;b.headZ-=drop;}   // short legs: the body sits lower
        Anim A=anim(f,sp,b);
        float bob=A.bob,lean=A.lean,tw=A.twist;
        final float[] piv=v(0,0,b.hipZ);final float[] off=v(0,A.sway,bob);
        // body point: twisted by `frac` of the upper-body twist, leaned about the hips, then moved with the hips
        java.util.function.BiFunction<float[],Float,float[]> B=(pt,frac)->add(rotY(tz(pt,tw*frac),piv,lean),off,1);
        float[] pelvis=add(piv,off,1);
        // ---- legs: 5-sided cylinders placed by two-bone IK so the feet land where the pose puts them
        for(int s=-1;s<=1;s+=2){float[] ft=s<0?A.fL:A.fR;float pitch=s<0?A.pL:A.pR;
            float[] hip=add(v(0,s*b.hipW,b.hipZ),off,1),target=v(ft[0],ft[1],ft[2]+.07f);
            float[] knee=SpriteModels.ik(hip,target,b.thigh,b.shin,v(1,s*.15f,.1f));
            float[] ankle=add(knee,norm(sub(target,knee)),b.shin);
            {float c=(float)Math.cos(-pitch),sn=(float)Math.sin(-pitch),dx=.04f,dz=-.07f;int o=s>0?0:4;   // the sole under the ankle, turned with the foot
                J[Puppet.J_HIP_R+o]=hip;J[Puppet.J_KN_R+o]=knee;J[Puppet.J_AN_R+o]=ankle;J[Puppet.J_SOLE_R+o]=v(ankle[0]+dx*c+dz*sn,ankle[1],ankle[2]-dx*sn+dz*c);}
            curPart=s>0?Puppet.P_THIGH_R:Puppet.P_THIGH_L;
            if(!sp.robe){
                if(sp.bones){cyl5(hip,knee,.026f,.02f,.012f,.016f,LEGS);curPart=s>0?Puppet.P_CALF_R:Puppet.P_CALF_L;cyl5(knee,ankle,.02f,.016f,.016f,.01f,LEGS);}
                else{float[] fw=v(1,0,0);
                    limb(hip,knee,fw,5,.072f*k,.072f*k,.3f,.075f*k,.077f*k,.05f*k,.052f*k,-1,.02f*k,LEGS);curPart=s>0?Puppet.P_CALF_R:Puppet.P_CALF_L;          // thigh: full at the top, slim above the knee, a kneecap
                    limb(add(knee,norm(sub(ankle,knee)),-.02f),ankle,fw,5,.054f*k,.057f*k,0,0,0,.036f*k,.038f*k,-1,-1,LEGS);}}   // shin with the calf swelling behind
            curPart=s>0?Puppet.P_FOOT_R:Puppet.P_FOOT_L;foot(ankle,sp.bones?.6f:Math.max(.85f,k),BOOTS,pitch);}
        curPart=Puppet.P_PELVIS;
        // ---- body (leans about the hips, twists through the chest)
        float[] waist=B.apply(v(0,0,b.waistZ),0f),chest=B.apply(v(0,0,b.chestZ),.5f),shTop=B.apply(v(0,0,b.shoulderZ+.035f),1f);
        float[] up=norm(sub(shTop,waist));
        if(sp.bones){
            seg(add(v(0,0,b.hipZ-.04f),off,1),add(v(0,0,b.hipZ+.05f),off,1),b.hipW*1.4f,.05f,b.hipW*1.1f,.04f,.01f,.01f,PELVIS,v(1,0,0));   // hip bone
            curPart=Puppet.P_TORSO;seg(add(v(0,0,b.hipZ+.03f),off,1),add(waist,up,.06f),.022f,.022f,.02f,.02f,0,0,TORSO,v(1,0,0));                             // spine
            torso(add(waist,up,.05f),chest,shTop,b,up,.8f,TORSO,tw);                                                                     // ribcage
        }else{
            pelvisBox(add(v(0,0,b.hipZ-.03f),off,1),waist,b,up,k);
            curPart=Puppet.P_TORSO;torso(waist,chest,shTop,b,up,k,TORSO,tw);
        }
        if(sp.tail){   // tiefling / dragonborn tail: one bent tube swaying with the stride
            curPart=Puppet.P_PELVIS;
            float sw=A.tailSway;float t=sp.snout?1.3f:.75f;
            float[] t0=B.apply(v(-.07f,0,b.hipZ+.02f),0f),t1=add(t0,v(-.24f,sw*.5f,-.24f)),t3=add(t1,v(-.36f,sw*1.6f,-.13f));
            if(t1[2]<.1f)t1[2]=.1f;if(t3[2]<.06f)t3[2]=.06f;
            chain(new float[][]{t0,t1,t3},new float[]{.03f*t,.02f*t,sp.snout?.004f:.012f},sp.snout?.02f:.035f,SKIN,v(0,0,1),3);}
        float[] neckB=add(shTop,up,-.005f),neckT=add(shTop,up,.07f*Math.min(1.1f,sp.headS));curPart=Puppet.P_HEAD;
        if(sp.bones)seg(neckB,neckT,.018f,.018f,.016f,.016f,0,0,TORSO,v(1,0,0));
        else limb(add(neckB,up,-.02f),neckT,v(1,0,0),5,.046f*k,.045f*k,0,0,0,.04f*k,.041f*k,-1,-1,SKIN);
        float[] hc=add(neckT,up,.085f*sp.headS*HEAD_K);
        HYAW=tw*.3f+A.headYaw;head(hc,lean*1.4f+A.headPitch,sp);HYAW=0;
        J[Puppet.J_NECK]=neckB;J[Puppet.J_WAIST]=waist;J[Puppet.J_CROTCH]=add(v(0,0,b.hipZ-.06f),off,1);
        {float hp2=lean*1.4f+A.headPitch;J[Puppet.J_CROWN]=place(hc,hp2,v(0,0,.11f*sp.headS*HEAD_K));}
        // ---- arms (two-bone IK to the hands, elbows bend back and out)
        float[] rS=B.apply(v(0,b.shoulderW,b.shoulderZ),1f),lS=B.apply(v(0,-b.shoulderW,b.shoulderZ),1f);
        float[][] h=hands(sp,A,b);float[] rh=h[0]==null?null:add(h[0],v(0,0,-drop)),lh=h[1]==null?null:add(h[1],v(0,0,-drop));
        if(rh==null)rh=limp(rS,A.swR,A.elR,A.spread,1,sp,b);else if(!A.dead)rh=add(rotY(add(rh,v(A.swR*A.handSwing,0,A.handLift+Math.abs(A.swR)*A.handLift)),piv,lean),off,1);
        if(lh==null)lh=limp(lS,A.swL,A.elL,A.spread,-1,sp,b);else if(!A.dead)lh=add(rotY(add(lh,v(A.swL*A.handSwing,0,A.handLift+Math.abs(A.swL)*A.handLift)),piv,lean),off,1);
        float[] rE=SpriteModels.ik(rS,rh,b.upper,b.fore,v(-1,.5f,-.4f)),lE=SpriteModels.ik(lS,lh,b.upper,b.fore,v(-1,-.5f,-.4f));
        armSide=1;arm(rS,rE,rh,sp);armSide=-1;arm(lS,lE,lh,sp);
        float[] wd=h[2];if(wd!=null&&!A.dead)wd=norm(rotY(add(wd,v(0,0,0)),v(0,0,0),lean));
        {float[] dR=norm(sub(rh,rE)),dL=norm(sub(lh,lE));
            J[Puppet.J_SH_R]=rS;J[Puppet.J_EL_R]=rE;J[Puppet.J_WR_R]=add(rh,dR,-.035f);J[Puppet.J_FIST_R]=add(rh,dR,.045f);
            J[Puppet.J_SH_L]=lS;J[Puppet.J_EL_L]=lE;J[Puppet.J_WR_L]=add(lh,dL,-.035f);J[Puppet.J_FIST_L]=add(lh,dL,.045f);
            J[Puppet.J_GRIP]=rh;J[Puppet.J_WTIP]=add(rh,wd!=null?wd:dR,.4f);J[Puppet.J_OFF]=lh;J[Puppet.J_OFFB]=add(lh,dL,.3f);}
        curPart=Puppet.P_WEAPON;weapon(sp,A,rh,lh,wd);curPart=Puppet.P_TORSO;
        if(sp.pauldrons)for(int s=-1;s<=1;s+=2){float[] sh=s>0?rS:lS;float[] side=norm(tz(v(0,s,0),tw));
            seg(add(add(sh,up,-.04f),side,.01f),add(add(sh,up,.04f),side,.02f),.085f*k,.075f*k,.07f*k,.065f*k,-1,.045f*k,TRIM,v(1,0,0));}
        // ---- clothing: flat sheets
        curPart=Puppet.P_PELVIS;
        if(sp.robe)skirt(waist,b.chestW*.95f,.1f,v(A.robeSway,A.sway,.03f+Math.max(0,bob+.02f)),.25f,.2f,CLOAK,true);
        if(sp.tunic)skirt(waist,b.chestW*.88f,.095f,v(A.robeSway*.5f,A.sway,.52f+bob),.18f,0,PELVIS,false);
        if(sp.tabard)tabard(waist,b,up,bob,k);
        curPart=Puppet.P_BACK;J[Puppet.J_BACK]=B.apply(v(-.17f,0,b.waistZ+.05f),.6f);   // things worn on the back: their own puppet part
        if(sp.quiver){float[] q0=B.apply(v(-.13f,.06f,b.waistZ+.02f),.3f),q1=B.apply(v(-.16f,-.06f,b.shoulderZ+.12f),1f);
            seg(q0,q1,.04f,.035f,.045f,.04f,0,0,X1,v(-1,0,0));
            octa(add(q1,v(-.005f,0,.045f)),.028f,X2);}
        if(sp.cape){float[] back=norm(v((float)-Math.cos(lean),0,(float)Math.sin(lean)));
            cape(add(B.apply(v(0,0,b.shoulderZ),1f),back,sp.quiver?.15f:.11f),b,A.trail);}
        MeshModels.Frame fr=new MeshModels.Frame();fr.v=java.util.Arrays.copyOf(buf,nv*9);fr.count=nv;lastPolys=polys();
        for(int i=0;i<J.length;i++)if(J[i]==null)J[i]=v(0,0,1);
        if(A.dead)layDown(fr,J);
        float sc=sp.scale*(drop>0?1.43f/(1.43f-drop):1);   // short-legged races keep their overall height
        if(sc!=1){for(int i=0;i<fr.count;i++){int o=i*9;fr.v[o]*=sc;fr.v[o+1]*=sc;fr.v[o+2]*=sc;}for(float[] j:J){j[0]*=sc;j[1]*=sc;j[2]*=sc;}}
        joints=J;
        return fr;
    }
    /** Hand targets {right, left, weapon direction} in the body's rest space (before lean); null = a free arm. */
    private static float[][] hands(Spec sp,Anim A,SpriteModels.Body b){
        if(A.dead)return new float[][]{v(.1f,.42f,b.shoulderZ-.2f),v(.05f,-.42f,b.shoulderZ-.25f),norm(v(.12f,.3f,1))};   // weapon along the body: flat on the floor once it tips over
        int st=A.atk;boolean pain=A.pain;
        switch(sp.weapon){
            case W_SWORD_SHIELD:case W_MACE_SHIELD:case W_RUSTY:case W_CRUDE:{
                boolean shield=sp.weapon==W_SWORD_SHIELD||sp.weapon==W_MACE_SHIELD;
                float[] rh,dir;float[] lh=null;
                if(pain){rh=v(.05f,.32f,.95f);dir=norm(v(.2f,.4f,.9f));lh=shield?v(.2f,-.24f,1.02f):null;}
                else if(st==0){rh=v(-.1f,.33f,1.32f);dir=norm(v(-.45f,.2f,.87f));lh=shield?v(.26f,-.16f,1.0f):null;}
                else if(st==1){rh=v(.44f,.06f,1.02f);dir=norm(v(.75f,-.55f,-.2f));lh=shield?v(.16f,-.26f,.95f):null;}
                else if(st==2){rh=v(.22f,-.26f,.78f);dir=norm(v(.25f,-.75f,-.6f));lh=shield?v(.1f,-.3f,.92f):null;}
                else{rh=sp.weapon==W_CRUDE?v(.2f,.2f,.84f):v(.2f,.2f,.88f);dir=norm(v(.45f,.05f,.9f));lh=shield?v(.22f,-.2f,.98f):null;}
                if(!shield&&lh==null&&sp.weapon==W_RUSTY&&st<0&&!pain)lh=null;
                return new float[][]{rh,lh,dir};}
            case W_BOW:
                if(st==0)return new float[][]{v(.1f,.06f,1.2f),v(.48f,-.05f,1.18f),null};       // drawn: string at the chin
                if(st==1)return new float[][]{v(-.06f,.16f,1.24f),v(.48f,-.05f,1.18f),null};    // released: hand flies back
                if(st==2)return new float[][]{v(0,.22f,1.02f),v(.42f,-.06f,1.08f),null};
                return new float[][]{v(.1f,.22f,.85f),v(.2f,-.22f,.9f),null};
            case W_DAGGERS:
                if(st==0)return new float[][]{v(.04f,.27f,1.06f),v(.16f,-.2f,.98f),null};
                if(st==1)return new float[][]{v(.52f,.06f,1.04f),v(.12f,-.22f,.92f),null};
                if(st==2)return new float[][]{v(.1f,.24f,.96f),v(.47f,-.07f,1.0f),null};
                return new float[][]{v(.22f,.2f,.85f),v(.22f,-.2f,.85f),null};
            case W_STAFF:
                if(st==0)return new float[][]{v(0,.24f,1.3f),v(0,-.26f,1.0f),norm(v(-.15f,0,1))};
                if(st==1)return new float[][]{v(.4f,.12f,1.12f),v(.42f,-.12f,1.15f),norm(v(.4f,0,1))};
                if(st==2)return new float[][]{v(.34f,.13f,1.05f),v(.34f,-.15f,1.0f),norm(v(.3f,0,1))};
                return new float[][]{v(.18f,.22f,.9f),null,norm(v(.05f,0,1))};
            case W_CAST:
                if(st==0)return new float[][]{v(-.03f,.19f,.95f),v(-.03f,-.19f,.95f),null};
                if(st==1)return new float[][]{v(.4f,.08f,1.1f),v(.4f,-.08f,1.1f),null};
                if(st==2)return new float[][]{v(.35f,.1f,1.02f),v(.35f,-.1f,1.02f),null};
                return new float[][]{v(.16f,.07f,.86f),v(.16f,-.07f,.86f),null};
            case W_AXE:{float[] r,d;
                if(st==0){r=v(-.03f,.1f,1.42f);d=norm(v(-.5f,-.05f,.86f));}
                else if(st==1){r=v(.44f,.04f,1.0f);d=norm(v(.86f,-.05f,-.5f));}
                else if(st==2){r=v(.36f,0,.62f);d=norm(v(.35f,0,-.94f));}
                else{r=v(.18f,.22f,.85f);float[] l=v(.22f,-.02f,1.05f);return new float[][]{r,l,norm(sub(l,r))};}
                return new float[][]{r,add(r,d,-.22f),d};}
            default:   // villager: waves instead of attacking
                if(st==0)return new float[][]{v(.1f,.4f,1.38f),null,null};
                if(st==1)return new float[][]{v(.14f,.52f,1.42f),null,null};
                if(st==2)return new float[][]{v(.12f,.34f,1.44f),null,null};
                return new float[][]{null,null,null};
        }
    }
    /** A free arm: swung forward by `swing`, lifted sideways by `spread`, the elbow bent by `elbow`. */
    private static float[] limp(float[] sh,float swing,float elbow,float spread,int side,Spec sp,SpriteModels.Body b){
        float sw=swing*(sp.weapon==W_NONE?.8f:1)+(sp.crouch?.3f:.04f);
        float reach=(b.upper+b.fore)*(1-.3f*Math.min(1.6f,elbow))-.02f;
        return add(sh,norm(v((float)Math.sin(sw),side*(.14f+spread),-(float)Math.cos(sw))),reach);
    }
    /** Death: tip the whole body over backwards onto the floor. */
    private static void layDown(MeshModels.Frame fr,float[][] J){
        float a=-1.45f,c=(float)Math.cos(a),s=(float)Math.sin(a);float minZ=9;
        for(int i=0;i<fr.count;i++){int o=i*9;float x=fr.v[o],z=fr.v[o+2];fr.v[o]=x*c+z*s;fr.v[o+2]=-x*s+z*c;float nx=fr.v[o+3],nz=fr.v[o+5];fr.v[o+3]=nx*c+nz*s;fr.v[o+5]=-nx*s+nz*c;minZ=Math.min(minZ,fr.v[o+2]);}
        for(int i=0;i<fr.count;i++)fr.v[i*9+2]-=minZ-.01f;
        for(int i=0;i<J.length;i++){float x=J[i][0],z=J[i][2];J[i]=v(x*c+z*s,J[i][1],-x*s+z*c-(minZ-.01f));}
    }

    // ------------------------------------------------------------------ vertex output
    static void put(float[] p,float[] n,int tile,float u,float vv){
        if((nv+1)*9>buf.length)buf=java.util.Arrays.copyOf(buf,buf.length*2);
        float in=4f/MeshModels.TILE;u=in+Math.max(0,Math.min(1,u))*(1-2*in);vv=in+Math.max(0,Math.min(1,vv))*(1-2*in);
        float[] nn=norm(n);int o=nv*9;buf[o]=p[0];buf[o+1]=p[1];buf[o+2]=p[2];buf[o+3]=nn[0];buf[o+4]=nn[1];buf[o+5]=nn[2];
        buf[o+6]=((tile%G)+u)/G;buf[o+7]=((tile/G)+vv)/G;buf[o+8]=glowTile[tile]?1:0;
        if(partBuf.length<=nv)partBuf=java.util.Arrays.copyOf(partBuf,Math.max(64,nv*2));partBuf[nv]=curPart;nv++;
    }
    static void tri(float[] a,float[] b,float[] c,float[] na,float[] nb,float[] nc,int tile,float[] uv){put(a,na,tile,uv[0],uv[1]);put(b,nb,tile,uv[2],uv[3]);put(c,nc,tile,uv[4],uv[5]);}
    static void quad(float[] a,float[] b,float[] c,float[] d,float[] na,float[] nb,float[] nc,float[] nd,int tile,float u0,float v0,float u1,float v1){
        tri(a,b,c,na,nb,nc,tile,new float[]{u0,v0,u1,v0,u1,v1});tri(a,c,d,na,nc,nd,tile,new float[]{u0,v0,u1,v1,u0,v1});quads++;}
    /** Two axes across a segment: s (sideways) and f (front), preferring the hint for f. */
    static float[][] across(float[] ax,float[] fwdHint){float[] f=sub(fwdHint,mul(ax,SpriteModels.dot(fwdHint,ax)));if(len(f)<1e-3f)f=sub(v(0,0,1),mul(ax,ax[2]));if(len(f)<1e-3f)f=v(1,0,0);f=norm(f);float[] s=norm(cross(ax,f));return new float[][]{s,f};}

    // ------------------------------------------------------------------ parts
    /**
     * Box from a to b with a w x d cross-section at each end (tapered), and pyramid ends: each end face is cut into 4
     * triangles meeting at a centre vertex pushed out by domeA / domeB (0 = flat end, two triangles).
     */
    static void seg(float[] a,float[] b,float wa,float da,float wb,float db,float domeA,float domeB,int tile,float[] fwd){
        float[] ax=norm(sub(b,a));float[][] sf=across(ax,fwd);float[] s=sf[0],f=sf[1];
        float[][] A=new float[4][],B=new float[4][],N=new float[4][];
        int[][] cc={{1,1},{-1,1},{-1,-1},{1,-1}};
        for(int i=0;i<4;i++){float cs=cc[i][0],cf=cc[i][1];A[i]=add(add(a,s,cs*wa),f,cf*da);B[i]=add(add(b,s,cs*wb),f,cf*db);N[i]=add(mul(s,cs),f,cf);}
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(A[i],A[j],B[j],B[i],N[i],N[j],N[j],N[i],tile,i/4f,0,(i+1)/4f,1);}
        if(domeA>=0)cap(A,a,mul(ax,-1),domeA,tile,true);if(domeB>=0)cap(B,b,ax,domeB,tile,false);   // negative: open (hidden inside the next part)
    }
    static void cap(float[][] ring,float[] c,float[] out,float dome,int tile,boolean flip){
        int n=ring.length;
        if(dome<=0&&n==4){float[] nn=out;quads++;if(flip){tri(ring[0],ring[2],ring[1],nn,nn,nn,tile,new float[]{0,0,1,1,1,0});tri(ring[0],ring[3],ring[2],nn,nn,nn,tile,new float[]{0,0,0,1,1,1});}
            else{tri(ring[0],ring[1],ring[2],nn,nn,nn,tile,new float[]{0,0,1,0,1,1});tri(ring[0],ring[2],ring[3],nn,nn,nn,tile,new float[]{0,0,1,1,0,1});}return;}
        float[] tip=add(c,out,dome);
        for(int i=0;i<n;i++){int j=(i+1)%n;float a0=(float)(i*2*Math.PI/n),a1=(float)(j*2*Math.PI/n);
            float[] uv={.5f+.45f*(float)Math.cos(a0),.5f+.45f*(float)Math.sin(a0),.5f+.45f*(float)Math.cos(a1),.5f+.45f*(float)Math.sin(a1),.5f,.5f};
            float[] n1=add(norm(sub(ring[i],c)),out,1.2f),n2=add(norm(sub(ring[j],c)),out,1.2f);
            if(flip)tri(ring[j],ring[i],tip,n2,n1,out,tile,new float[]{uv[2],uv[3],uv[0],uv[1],.5f,.5f});else tri(ring[i],ring[j],tip,n1,n2,out,tile,uv);}
    }
    /** 5-sided cylinder a→b with radii ra/rb; caps are fans to a centre vertex pushed out by domeA / domeB. */
    static void cyl5(float[] a,float[] b,float ra,float rb,float domeA,float domeB,int tile){
        float[] ax=norm(sub(b,a));float[][] sf=across(ax,v(1,0,0));float[] s=sf[0],f=sf[1];
        float[][] A=new float[5][],B=new float[5][],N=new float[5][];
        for(int i=0;i<5;i++){double t=i*2*Math.PI/5+Math.PI/2;float[] d=add(mul(f,(float)Math.sin(t)),s,(float)Math.cos(t));N[i]=d;A[i]=add(a,d,ra);B[i]=add(b,d,rb);}
        for(int i=0;i<5;i++){int j=(i+1)%5;quad(A[i],A[j],B[j],B[i],N[i],N[j],N[j],N[i],tile,i/5f,0,(i+1)/5f,1);}
        cap(A,a,mul(ax,-1),domeA,tile,true);cap(B,b,ax,domeB,tile,false);
    }
    /** n-sided cylinder (or cone) a→b, caps fanned to a centre vertex pushed out by domeA / domeB (negative = no cap: an open tube). */
    static void cylN(float[] a,float[] b,float ra,float rb,int n,float domeA,float domeB,int tile){
        float[] ax=norm(sub(b,a));float[][] sf=across(ax,v(1,0,0));float[] s=sf[0],f=sf[1];
        float[][] A=new float[n][],B=new float[n][],N=new float[n][];
        for(int i=0;i<n;i++){double t=i*2*Math.PI/n+Math.PI/n;float[] d=add(mul(f,(float)Math.sin(t)),s,(float)Math.cos(t));N[i]=d;A[i]=add(a,d,ra);B[i]=add(b,d,rb);}
        for(int i=0;i<n;i++){int j=(i+1)%n;quad(A[i],A[j],B[j],B[i],N[i],N[j],N[j],N[i],tile,i/(float)n,0,(i+1)/(float)n,1);}
        if(domeA>=0)cap(A,a,mul(ax,-1),Math.max(1e-4f,domeA),tile,true);if(domeB>=0)cap(B,b,ax,Math.max(1e-4f,domeB),tile,false);
    }
    static void box(float[] a,float[] b,float w,float d,int tile){seg(a,b,w,d,w,d,0,0,tile,v(1,0,0));}
    /**
     * A bent tube through several points (tails, horns): 4 sides per segment, one shared ring at each joint, so
     * only the two ends need caps; the last end is pulled to a pyramid point.
     */
    static void chain(float[][] P,float[] w,float tip,int tile,float[] fwd){chain(P,w,tip,tile,fwd,4);}
    static void chain(float[][] P,float[] w,float tip,int tile,float[] fwd,int sides){
        int n=P.length,m=sides;float[][][] R=new float[n][m][];float[][] N=new float[m][];
        for(int i=0;i<n;i++){float[] ax=norm(sub(P[Math.min(n-1,i+1)],P[Math.max(0,i-1)]));float[][] sf=across(ax,fwd);
            for(int k=0;k<m;k++){double a=(k+.5)*2*Math.PI/m;float[] d=add(mul(sf[0],(float)Math.cos(a)),sf[1],(float)Math.sin(a));R[i][k]=add(P[i],d,w[i]*1.41f);if(i==0)N[k]=d;}}
        for(int i=0;i+1<n;i++)for(int k=0;k<m;k++){int j=(k+1)%m;quad(R[i][k],R[i][j],R[i+1][j],R[i+1][k],N[k],N[j],N[j],N[k],tile,k/(float)m,i/(float)(n-1),(k+1)/(float)m,(i+1)/(float)(n-1));}
        if(m==4)cap(R[0],P[0],norm(sub(P[0],P[1])),0,tile,true);   // 3-sided chains (horns, tails) start inside the body: no cap
        cap(R[n-1],P[n-1],norm(sub(P[n-1],P[n-2])),Math.max(1e-4f,tip),tile,false);
    }
    /** 8-triangle diamond for tiny details (eyes, gems, fletching, magic). */
    static void octa(float[] c,float r,int tile){
        float[][] P={add(c,v(r,0,0)),add(c,v(0,r,0)),add(c,v(-r,0,0)),add(c,v(0,-r,0))};float[] t=add(c,v(0,0,r)),bt=add(c,v(0,0,-r));
        for(int i=0;i<4;i++){int j=(i+1)%4;float[] n1=sub(P[i],c),n2=sub(P[j],c);
            tri(P[i],P[j],t,n1,n2,v(0,0,1),tile,new float[]{0,1,1,1,.5f,0});tri(P[j],P[i],bt,n2,n1,v(0,0,-1),tile,new float[]{1,0,0,0,.5f,1});}
    }

    // ------------------------------------------------------------------ the artist's kit: shaped loops
    /** Quads made so far in this frame: the PS1 drew quads as one polygon, so polygons = triangles - quads. */
    static int quads;
    /** Tile size of the texture being painted (MeshModels.TILE for models, smaller for puppet parts). */
    static int PT=MeshModels.TILE;
    // ---- puppets (Puppet): which body part each vertex belongs to, and where the joints are, for the frame being built
    static int curPart;static int[] partBuf=new int[0];
    /** Joints of the last frame built (Puppet.J_*), in model space after death and race scaling. */
    static float[][] joints;
    /** Heads are drawn a little larger than life, as in most PS1 games, so faces read at a distance. */
    static final float HEAD_K=1.1f;
    /** Build shapes without painting textures (polygon checks). */
    static boolean shapeOnly;
    /** Polygons in the last frame built (for checks: at most 500 per character). */
    static int lastPolys;
    static int polys(){return nv/3-quads;}
    /** A quad with its own texture coordinates per corner. */
    static void quadUV(float[] a,float[] b,float[] c,float[] d,float[] na,float[] nb,float[] nc,float[] nd,int tile,float[] ta,float[] tb,float[] tc,float[] td){
        tri(a,b,c,na,nb,nc,tile,new float[]{ta[0],ta[1],tb[0],tb[1],tc[0],tc[1]});tri(a,c,d,na,nc,nd,tile,new float[]{ta[0],ta[1],tc[0],tc[1],td[0],td[1]});quads++;}
    /**
     * A loop of n points around {@code c}: angle 0 faces {@code f}, positive turns toward {@code s}. {@code w} = half
     * width (along s), {@code d} = depth to the front, {@code db} = depth to the back; {@code e} below 1 squares the
     * loop off like a chamfered box. {@code a0}: angle of the first point. out[0] = points, out[1] = normals.
     */
    static float[][][] loop(float[] c,float[] s,float[] f,int n,float a0,float w,float d,float db,float e){
        float[][] P=new float[n][],N=new float[n][];
        for(int i=0;i<n;i++){double a=a0-i*2*Math.PI/n;float cs=(float)Math.cos(a),sn=(float)Math.sin(a);
            float cx=Math.signum(cs)*(float)Math.pow(Math.abs(cs),e),sy=Math.signum(sn)*(float)Math.pow(Math.abs(sn),e);float dd=cs>=0?d:db;
            P[i]=add(add(c,f,cx*dd),s,sy*w);N[i]=norm(add(mul(f,cx/Math.max(.01f,dd)),s,sy/Math.max(.01f,w)));}
        return new float[][][]{P,N};
    }
    /** Joins two loops with quads; texture u goes around (i/n), v from va to vb. */
    static void band(float[][][] A,float[][][] B,int tile,float va,float vb){
        int n=A[0].length;
        for(int i=0;i<n;i++){int j=(i+1)%n;quad(A[0][i],A[0][j],B[0][j],B[0][i],A[1][i],A[1][j],B[1][j],B[1][i],tile,i/(float)n,va,(i+1)/(float)n,vb);}
    }
    /** Closes a loop with a fan to a centre point pushed out by {@code dome} (flip = facing the other way). */
    static void fan(float[][][] L,float[] c,float[] out,float dome,int tile,boolean flip){cap(L[0],c,out,Math.max(1e-4f,dome),tile,flip);}
    /**
     * A limb the way an artist blocks one in: an n-sided tube a → b with one loop in the middle (at {@code tm}) for
     * the muscle (bicep, forearm, thigh, calf), each loop its own width and depth; the hidden end is left open (it sits
     * inside the next part), the visible end gets a low domed cap ({@code capB} &lt; 0 = open).
     */
    static void limb(float[] a,float[] b,float[] fwd,int n,float wa,float da,float tm,float wm,float dm,float wb,float db,float capA,float capB,int tile){
        float[] ax=norm(sub(b,a));float[][] sf=across(ax,fwd);float[] s=sf[0],f=sf[1];float a0=(float)(Math.PI/n);
        float[][][] A=loop(a,s,f,n,a0,wa,da,da,1),B=loop(b,s,f,n,a0,wb,db,db,1);
        if(tm>0){float[][][] M=loop(SpriteModels.lerp(a,b,tm),s,f,n,a0,wm,dm,dm,1);band(A,M,tile,0,tm);band(M,B,tile,tm,1);}else band(A,B,tile,0,1);   // tm <= 0: no middle loop
        if(capA>=0)fan(A,a,mul(ax,-1),capA,tile,true);if(capB>=0)fan(B,b,ax,capB,tile,false);
    }
    /** Arm: bicep box and forearm box with pyramid ends that overlap at the elbow, and a fist (or a wide sleeve and a hand). */
    /** Which arm arm() is building (1 right, -1 left), for the puppet's part tags. */
    private static int armSide=1;
    private static void arm(float[] sh,float[] el,float[] hand,Spec sp){
        float[] fw=v(1,0,0);float k=sp.bulk;boolean R=armSide>0;
        float[] dir=norm(sub(hand,el));float[] wrist=add(hand,dir,-.035f);
        curPart=R?Puppet.P_UPPER_R:Puppet.P_UPPER_L;
        if(sp.bones){
            seg(sh,el,.02f,.02f,.016f,.016f,.012f,.014f,UPPER,fw);
            curPart=R?Puppet.P_FORE_R:Puppet.P_FORE_L;seg(el,wrist,.017f,.017f,.014f,.014f,.014f,.008f,HANDS,fw);
            curPart=R?Puppet.P_HAND_R:Puppet.P_HAND_L;seg(wrist,add(wrist,dir,.07f),.024f,.03f,.02f,.026f,.01f,0,HANDS,fw);
            return;
        }
        // upper arm: the deltoid cap at the shoulder, a bicep loop, narrowing into a rounded elbow
        float[] ud=norm(sub(el,sh));
        limb(add(sh,ud,-.02f),el,fw,5,.056f*k,.053f*k,0,0,0,.039f*k,.04f*k,-1,.02f*k,UPPER);
        curPart=R?Puppet.P_FORE_R:Puppet.P_FORE_L;
        if(sp.sleeves){   // wide sleeve flaring open past the elbow, the bare hand coming out of it
            limb(add(el,dir,-.03f),add(wrist,dir,-.005f),fw,5,.046f,.048f,0,0,0,.08f,.08f,-1,-1,CLOAK);
            curPart=R?Puppet.P_HAND_R:Puppet.P_HAND_L;hand(wrist,dir,Math.max(.85f,k*.85f));
            return;
        }
        // forearm: the muscle swells just below the elbow, then tapers to a slim wrist
        limb(add(el,dir,-.015f),wrist,fw,5,.044f*k,.043f*k,0,0,0,.029f*k,.03f*k,-1,-1,HANDS);
        curPart=R?Puppet.P_HAND_R:Puppet.P_HAND_L;hand(wrist,dir,Math.max(.9f,k));
    }
    /**
     * Hand: a palm block from the wrist to the knuckles, the curled fingers as a second block bent down, and a thumb
     * wrapped over the front, all with flat ends (a held weapon runs through the fist along {@code dir}).
     */
    private static void hand(float[] wrist,float[] dir,float k){
        float[][] sf=across(dir,v(0,0,1));float[] f=sf[1];
        float[] tip=add(add(wrist,dir,.08f*k),f,-.008f*k);   // the fist, knuckles turned a little down; fingers and thumb painted
        seg(wrist,tip,.03f*k,.019f*k,.032f*k,.024f*k,-1,0,HANDS,f);
    }
    /** Foot: box loop-cut halfway, stretched forward from the heel; the toe end narrowed for a mild point. */
    /** pitch: + lifts the toes (heel strike), - lifts the heel (toe-off, pointed toes in the air). */
    private static void foot(float[] ankle,float k,int tile,float pitch){
        int from=nv;foot(ankle,k,tile);
        if(pitch!=0){float c=(float)Math.cos(-pitch),sn=(float)Math.sin(-pitch);
            for(int i=from;i<nv;i++){int o=i*9;float dx=buf[o]-ankle[0],dz=buf[o+2]-ankle[2];buf[o]=ankle[0]+dx*c+dz*sn;buf[o+2]=ankle[2]-dx*sn+dz*c;
                float nx=buf[o+3],nz=buf[o+5];buf[o+3]=nx*c+nz*sn;buf[o+5]=-nx*sn+nz*c;}}
    }
    private static void foot(float[] ankle,float k,int tile){
        float kk=Math.min(1,k);float sole=ankle[2]-.07f;
        // boot shaft up the shin (bare feet - halflings - have none)
        if(!S.bones&&!"Halfling".equals(S.race))seg(v(ankle[0],ankle[1],sole+.04f),v(ankle[0]-.006f,ankle[1],ankle[2]+.12f*kk),.042f*k,.044f*k,.046f*k,.046f*k,-1,-1,tile,v(1,0,0));
        // foot: heel, ball and toe loops, the sole flat on the ground, the instep sloping up into the shaft
        float[] heel=v(ankle[0]-.05f*k,ankle[1],sole),ball=v(ankle[0]+.1f*k,ankle[1],sole),toe=v(ankle[0]+.16f*k,ankle[1],sole);
        float[][] R0=ring(heel,.038f*k,.072f*kk),R1=ring(ball,.043f*k,.046f*kk),R2=ring(toe,.032f*k,.03f*kk);
        float[][] N={norm(v(0,1,.2f)),norm(v(.3f,0,1)),norm(v(0,-1,.2f)),v(0,0,-1)};
        float[][][] L={R0,R1,R2};float[] vv={0,.7f,1};
        for(int r=0;r<2;r++)for(int i=0;i<4;i++){int j=(i+1)%4;quad(L[r][i],L[r][j],L[r+1][j],L[r+1][i],N[i],N[j],N[j],N[i],tile,i/4f,vv[r],(i+1)/4f,vv[r+1]);}
        cap(R0,heel,v(-1,0,0),0,tile,true);cap(R2,toe,v(1,0,0),0,tile,false);
    }
    private static float[][] ring(float[] c,float halfW,float h){return new float[][]{v(c[0],c[1]+halfW,c[2]),v(c[0],c[1]+halfW,c[2]+h),v(c[0],c[1]-halfW,c[2]+h),v(c[0],c[1]-halfW,c[2])};}
    /** Hips: three 8-sided loops (crotch, hips, waist), chamfered like a box, closed underneath. */
    private static void pelvisBox(float[] bottom,float[] top,SpriteModels.Body b,float[] up,float k){
        float hip=S.female?1.14f:1;float[][] sf=across(up,v(1,0,0));float[] s=sf[0],f=sf[1];float a0=(float)(Math.PI/4);
        float[] crotch=add(bottom,up,-.035f),hipC=SpriteModels.lerp(bottom,top,.35f);
        float hw=b.hipW*1.75f*Math.max(1,k*.9f)*hip;
        float[][][] L1=loop(hipC,s,f,8,a0,hw*1.02f,.088f*k,.1f*k,.75f),L2=loop(top,s,f,8,a0,b.chestW*.84f*(S.female?.86f:1),.088f*k,.088f*k,.75f);
        band(L1,L2,PELVIS,.35f,1);fan(L1,crotch,mul(up,-1),.001f,PELVIS,true);
    }
    /** Side and front axes turned about `up` (positive = the right side goes back, like tz). */
    private static float[][] turn(float[] s,float[] f,float[] up,float a){float c=(float)Math.cos(a),sn=(float)Math.sin(a);return new float[][]{add(mul(s,c),f,-sn),add(mul(f,c),s,sn)};}
    /** Torso: waist → chest → shoulders, a box with one loop cut at the chest, shaped wider across the chest. */
    /** twist: the chest ring turns half as far as the shoulders (the loop cut takes the twist, like a real spine). */
    private static void torso(float[] waist,float[] chest,float[] top,SpriteModels.Body b,float[] up,float k,int tile,float twist){
        // four 8-sided loops: waist, chest (with the pecs or bust pushed forward), the shoulder line (as wide as the
        // deltoids) and a narrow neck base above it, so the trapezius slopes up to the neck; the spine and breastbone
        // are vertices down the middle of the front and back
        float[][] sf=across(up,v(1,0,0));float[] s=sf[0],f=sf[1];float dk=Math.max(.8f,k);float a0=(float)(Math.PI/4);
        float[][] s1=turn(s,f,up,twist*.5f),s2=turn(s,f,up,twist);
        float waistW=S.female?.7f:.8f,bust=S.female&&!S.bones?.13f:.112f;
        float[] shl=add(top,up,-.045f),nk=add(top,up,.03f);
        float[][][] L0=loop(waist,s,f,8,a0,b.chestW*waistW,.088f*dk,.085f*dk,.8f),
            L1=loop(chest,s1[0],s1[1],8,a0,b.chestW*1.05f,bust*dk,.1f*dk,.72f),
            L2=loop(shl,s2[0],s2[1],8,a0,b.shoulderW*1.0f,.085f*dk,.088f*dk,.7f);
        if(!S.bones){L1[0][1]=add(L1[0][1],s1[1],-.012f*dk);   // the breastbone dips between the pecs
            if(S.female){L1[0][0]=add(L1[0][0],s1[1],.008f);L1[0][2]=add(L1[0][2],s1[1],.008f);}}
        band(L0,L1,tile,0,.5f);band(L1,L2,tile,.5f,1);
        fan(L2,nk,up,.001f,tile,false);   // the shoulders slope up to the neck (the hips close the waist)
    }

    // ------------------------------------------------------------------ heads
    private static float[][][][] HP;   // the last head's lattice, for attaching ears, noses, tusks and eyes
    /** Head turn about the vertical (set while a head is built). */
    private static float HYAW;
    private static float[] place(float[] c,float pitch,float[] p){if(HYAW!=0){float yc=(float)Math.cos(HYAW),ys=(float)Math.sin(HYAW);p=v(p[0]*yc-p[1]*ys,p[0]*ys+p[1]*yc,p[2]);}float cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);return v(c[0]+p[0]*cp+p[2]*sp,c[1]+p[1],c[2]-p[0]*sp+p[2]*cp);}
    /** A point on the head lattice by fractional index (0..2 each way), so parts follow the head's shape. */
    private static float[] hp(float i,float j,float k){
        int i0=Math.min(1,(int)i),j0=Math.min(1,(int)j),k0=Math.min(1,(int)k);float fi=i-i0,fj=j-j0,fk=k-k0;float[] r=v(0,0,0);
        for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int c=0;c<2;c++){float w=(a==0?1-fi:fi)*(b==0?1-fj:fj)*(c==0?1-fk:fk);r=add(r,HP[i0+a][j0+b][k0+c],w);}
        return r;
    }
    /**
     * Head: a cube subdivided once (3 x 3 x 3 lattice, 24 quads). Front and sides stay flat, the top is rounded,
     * the back of the bottom lifts toward the neck and the front of the bottom drops and narrows into a jaw.
     */
    private static void head(float[] c,float pitch,Spec sp){
        float hs=sp.headS*HEAD_K;float hx=.098f*hs,hy=.083f*hs,hz=.105f*hs;
        float jawNarrow=.68f,jawDrop=-1.16f,backLift=-.38f;
        if(sp.female){hx*=.95f;hy*=.96f;jawNarrow=.6f;jawDrop=-1.1f;}
        if(sp.snout){hz*=.84f;hy*=.92f;}   // dragonborn: a lower, flatter skull behind the muzzle
        int top=HAIR,back=HAIR,sideUp=HAIR,sideLow=SKIN,under=SKIN;
        if((sp.head==H_HAIR||sp.horns)&&(sp.hairStyle==0||sp.hairStyle==4)){top=back=sideUp=SKIN;}
        switch(sp.head){
            case H_SKULL:jawNarrow=.55f;jawDrop=-1.05f;hy*=.92f;top=back=sideUp=sideLow=under=HAIR;break;
            case H_GOBLIN:jawNarrow=.6f;jawDrop=-1.1f;hx*=1.05f;hz*=.9f;top=back=HAIR;sideUp=sideLow=under=SKIN;break;
            case H_ORC:jawNarrow=.92f;jawDrop=-1.28f;hy*=1.08f;top=back=sideUp=sideLow=under=SKIN;break;
            case H_HOOD:case H_DEEP_HOOD:case H_HAT:top=back=sideUp=HAIR;break;
        }
        float[][][][] P=new float[3][3][3][];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int k=0;k<3;k++){float x=i-1,y=j-1,z=k-1;
            if(k==2){boolean cx=Math.abs(x)==1,cy=Math.abs(y)==1;   // rounded top
                if(cx&&cy){z=.55f;x*=.86f;y*=.86f;}else if(cx||cy){z=.86f;if(cx)x*=.93f;if(cy)y*=.93f;}else z=1.02f;
                if(sp.head==H_ORC&&i==2)z*=.85f;}                  // low sloped brow
            if(k==0){   // jaw: back of the bottom lifts, front drops and narrows
                if(i==0){z=backLift;y*=.9f;}else if(i==1){z=(backLift+jawDrop)*.5f-.11f;y*=.92f;}else{z=jawDrop;y*=jawNarrow;x*=.98f;}}
            if(k==1&&i==0)y*=.97f;
            if(sp.snout){if(i==2){x+=k==2?.3f:k==1?.95f:.9f;if(k==0)z*=.75f;y*=k==2?.85f:.7f;}else if(i==1&&k==0)z*=.9f;}   // dragonborn: the face pushed out into a snout
            P[i][j][k]=place(c,pitch,v(x*hx,y*hy,z*hz));}
        HP=P;
        sculptHead(c,pitch,sp,hx,hy,hz,top,back,sideUp,sideLow);
        float[] fwd=norm(sub(hp(2,1,1),hp(0,1,1))),upH=norm(sub(hp(1,1,2),hp(1,1,0)));
        // ---- extra pieces, each a box pulled to a point or a diamond
        if(sp.head==H_GOBLIN){
            for(int s=-1;s<=1;s+=2){float[] e=hp(1.1f,s<0?0:2,1.25f);float[] tip=add(add(add(e,v(0,s,0),.17f*hs),fwd,-.06f),upH,.08f);
                seg(e,tip,.012f,.035f*hs,.003f,.006f,0,0,SKIN,fwd);}                                                          // big pointed ears
            float[] n=hp(2,1,.95f);seg(add(n,fwd,-.01f),add(add(n,fwd,.09f*hs),upH,-.03f),.018f,.016f,.006f,.006f,0,.008f,SKIN,upH);   // long nose
        }
        if(sp.head==H_ORC){
            for(int s=-1;s<=1;s+=2){float[] t0=hp(2,s<0?.55f:1.45f,.2f);seg(add(t0,fwd,-.01f),add(add(t0,upH,.06f),fwd,.015f),.009f,.009f,.002f,.002f,0,.004f,X1,fwd);}   // tusks
            float[] kt=hp(.4f,1,1.95f);seg(kt,add(add(kt,fwd,-.09f),upH,.1f),.025f,.025f,.012f,.012f,.01f,.008f,HAIR,fwd);                   // topknot
        }
        boolean hooded=sp.head==H_HOOD||sp.head==H_DEEP_HOOD;
        if(sp.ears>0&&!hooded){   // pointed ears: a flat leaf from the side of the head, swept back and up (elves long, half-elves and gnomes short)
            float len=sp.ears==2?.095f:sp.ears==1?.05f:.06f,upk=sp.ears==2?.75f:sp.ears==1?.6f:.35f;
            for(int s=-1;s<=1;s+=2){float[] e=add(hp(.98f,s<0?0:2,1.0f),v(0,s,0),-.006f*hs);
                float[] d=norm(add(add(v(0,s*.55f,0),fwd,-.55f),upH,upk));float[] tip=add(e,d,len*hs);
                seg(e,tip,.007f*hs,.026f*hs,.002f,.004f*hs,-1,0,SKIN,fwd);}}
        if(sp.ears==0&&!hooded&&!sp.snout&&(sp.head==H_HAIR||sp.head==H_HAT)){   // round ears: one quad each side, its back edge turned out from the head
            for(int s=-1;s<=1;s+=2){float[] e=hp(.98f,s<0?0:2,1.0f);float[] out=v(0,s,0),n=add(out,fwd,-.3f);
                float[] a0=add(add(e,upH,-.024f*hs),fwd,.008f*hs),a1=add(add(e,upH,.022f*hs),fwd,.01f*hs),a2=add(add(add(e,upH,.026f*hs),fwd,-.016f*hs),out,.014f*hs),a3=add(add(add(e,upH,-.018f*hs),fwd,-.012f*hs),out,.01f*hs);
                quad(a0,a1,a2,a3,n,n,n,n,SKIN,.2f,.2f,.8f,.8f);}}
        if(sp.nose>0){float[] n=add(hp(2,1,.9f),fwd,sp.nose*hs*.35f);octa(n,.024f*hs,SKIN);}   // gnome: a big round nose
        if(sp.tusks)for(int s=-1;s<=1;s+=2){float[] t0=add(hp(2,s<0?.62f:1.38f,.3f),fwd,.006f*hs);seg(t0,add(add(t0,upH,.045f*hs),fwd,.012f*hs),.007f*hs,.007f*hs,.002f,.002f,-1,.004f,sp.tuskTile,fwd);}   // half-orc tusks over the lip
        if(sp.horns)for(int s=-1;s<=1;s+=2){float[] h0=hp(1.55f,s<0?.45f:1.55f,1.85f),h1=add(add(add(h0,upH,.055f*hs),fwd,-.015f),v(0,s,0),.01f),h2=add(add(add(h1,upH,.03f*hs),fwd,-.07f*hs),v(0,s,0),.014f);
            chain(new float[][]{h0,h1,h2},new float[]{.015f*hs,.01f*hs,.002f},.005f,BOOTS,fwd,3);}   // tiefling horns: curling back
        if(sp.snout&&!hooded)for(int s=-1;s<=1;s+=2){float[] h0=hp(.7f,s<0?.5f:1.5f,1.9f);chain(new float[][]{h0,add(add(h0,fwd,-.12f*hs),upH,.03f)},new float[]{.016f,.003f},.005f,SKIN,fwd,3);}   // swept-back spikes
        if(sp.head==H_HAIR||sp.head==H_HAT||sp.horns){
            if(sp.hairStyle==2)hood(c,pitch,hx*.9f,hy*.88f,hz*.92f,false,-HAIR-1);                                                  // long hair: a shell over the back and sides
            if(sp.hairStyle==3){float[] t0=hp(.05f,1,1.55f);seg(t0,add(add(t0,fwd,-.1f),upH,-.22f),.03f,.03f,.012f,.014f,.01f,.01f,HAIR,upH);}   // ponytail
            if(sp.hairStyle==4){float[] a0=hp(1.9f,1,2),a1=hp(.15f,1,1.85f);seg(a0,a1,.014f,.035f,.014f,.03f,.01f,.01f,HAIR,upH);}             // mohawk ridge
        }
        if(sp.beard){float len=.15f*sp.beardLen*(sp.weapon==W_STAFF?1.4f:1);float[] ch=hp(1.9f,1,.25f);float[] bot=add(add(ch,upH,-len),fwd,.012f);   // beard: a tapering 6-sided lock from the jaw to a point
            seg(add(add(ch,upH,.04f),fwd,-.03f),bot,.078f*hs,.045f*hs,.022f*hs,.018f*hs,-1,0,HAIR,fwd);}
        if(sp.dragonEyes){for(int s=-1;s<=1;s+=2){float[] e=EYES[s<0?0:1];octa(add(add(e,v(0,s,0),.004f*hs),fwd,-.012f*hs),.009f*hs,GLOW);}}   // glowing reptile eyes in the sockets   // reptile eyes on the sides of the head
        else if(sp.eyeGems){float g=sp.head==H_SKULL?.012f:sp.head==H_DEEP_HOOD?.011f:.012f;
            for(int s=-1;s<=1;s+=2){float[] e=EYES[s<0?0:1];octa(add(e,fwd,sp.head==H_SKULL?-.012f:sp.head==H_DEEP_HOOD?-.004f:-.002f),g,GLOW);}}
        if((sp.head==H_HOOD||sp.head==H_DEEP_HOOD)&&!sp.horns)hood(c,pitch,hx,hy,hz,sp.head==H_DEEP_HOOD,CLOAK);   // horned heroes wear the hood down
        if(sp.head==H_HAT)hat(hp(1,1,2),upH,fwd,hs);
    }

    /** Angles of the 8 points around every loop of the head: front centre (the nose line), the eyes / cheekbones, the sides, behind the ears, the back. */
    private static final float[] HEAD_A={0,40,90,140,180,-140,-90,-40};
    /** Height of each loop of the head (fraction of the half-height): under the chin, mouth, nose, eyes, brow, crown. */
    static final float[] HEAD_Z={-1.05f,-.24f,.2f,.92f};
    /** Where those loops fall on the face texture (0 = top of the forehead, 1 = under the chin). */
    static float faceV(int ring){int t=HEAD_Z.length-1;return (HEAD_Z[t]-HEAD_Z[ring])/(HEAD_Z[t]-HEAD_Z[0]);}
    /**
     * The head, sculpted like a PS1 artist's: six 8-point loops (chin, mouth, nose, eyes, brow, crown) closed by fans
     * at the crown and under the jaw — 40 quads and 16 triangles. The front centre line carries the profile: the
     * nose tip sticks out, the bridge rises between eye sockets that sit back, the cheekbones push out to the sides,
     * the mouth sits back under the nose, then the chin comes forward again. The jaw narrows toward the chin, the
     * skull is widest behind the ears. The face texture wraps the front half (left ear to right ear).
     */
    /** Eye positions of the last head built (left, right). */
    private static final float[][] EYES=new float[2][];
    private static void sculptHead(float[] c,float pitch,Spec sp,float hx,float hy,float hz,int top,int back,int sideUp,int sideLow){
        // per loop: half width, front depth, back depth (chin, nose, eyes, crown)
        float[][] R={{.46f,.72f,.32f},{.88f,.95f,.92f},{.95f,.92f,1.02f},{.74f,.64f,.82f}};
        int NR=R.length;for(int i=0;i<NR;i++)R[i]=R[i].clone();
        float nose=.2f,bridge=.05f,socket=.07f,cheek=.05f,chin=.07f,brow=0;
        if(sp.female){for(float[] r:R)r[0]*=.97f;R[0][0]*=.86f;nose=.14f;chin=.04f;}
        if(sp.heavyBrow)brow=.08f;
        switch(sp.head){
            case H_SKULL:R[0][0]*=.8f;R[1][0]*=.88f;nose=-.08f;bridge=0;socket=.14f;cheek=.08f;chin=.02f;break;
            case H_GOBLIN:R[0][0]*=.82f;R[2][0]*=1.08f;R[3][0]*=1.08f;nose=.1f;socket=.09f;chin=.08f;break;
            case H_ORC:R[0][0]=.74f;R[0][1]=.86f;R[1][0]=.95f;R[3][1]*=.88f;nose=.08f;brow=.12f;socket=.1f;chin=.02f;break;
        }
        if(sp.snout){R[0][1]+=.75f;R[1][1]+=1.0f;R[2][1]+=.32f;R[0][0]*=.66f;R[1][0]*=.68f;R[3][0]*=.8f;R[3][1]*=.7f;nose=.06f;bridge=.0f;socket=.06f;chin=-.06f;brow=Math.max(brow,.1f);}   // dragonborn: a long, narrow muzzle and a low crown
        int n=8;float[][][] P=new float[NR][n][],N=new float[NR][n][];float[][][] loc=new float[NR][n][];
        for(int r=0;r<NR;r++)for(int i=0;i<n;i++){
            double a=Math.toRadians(HEAD_A[i]);float cs=(float)Math.cos(a),sn=(float)Math.sin(a);
            float cx=Math.signum(cs)*(float)Math.pow(Math.abs(cs),.8f),sy=Math.signum(sn)*(float)Math.pow(Math.abs(sn),.8f);
            float x=cx*(cs>=0?R[r][1]:R[r][2]),y=sy*R[r][0],z=HEAD_Z[r];
            if(i==0){if(r==1)x+=nose;if(r==2)x+=bridge;if(r==0)x+=chin;if(r==3)x+=brow;}
            if(i==1||i==7){if(r==2)x-=Math.max(0,socket-brow*.5f);if(r==1){x+=cheek*.4f;y+=Math.signum(sn)*cheek;}if(r==3)x+=brow;}
            loc[r][i]=v(x*hx,y*hy,z*hz);
            float nx=x/(hx*hx),ny=y/(hy*hy),nz=z*.55f/(hz*hz);if(i==0&&r==1){nz-=.3f/(hz*hz);}
            P[r][i]=place(c,pitch,loc[r][i]);N[r][i]=sub(place(v(0,0,0),pitch,v(nx,ny,nz)),place(v(0,0,0),pitch,v(0,0,0)));}
        // where the eyes sit (for glowing eyes): in the sockets on the eye loop, toward the bridge
        for(int e=0;e<2;e++){int i=e==0?7:1;float[] l=SpriteModels.lerp(SpriteModels.lerp(loc[2][i],loc[2][0],.42f),loc[3][i],.04f);EYES[e]=place(c,pitch,add(l,v(.012f*hx/.098f,0,0)));}
        // texture coordinates: the front half (-90..90 degrees) on the face tile, by angle across and height down;
        // behind the ears: hair above the ear line, skin (the nape) below
        for(int r=0;r+1<NR;r++)for(int i=0;i<n;i++){int j=(i+1)%n;
            float aI=HEAD_A[i],aJ=HEAD_A[j];boolean front=Math.abs(aI)<=90&&Math.abs(aJ)<=90&&!(Math.abs(aI)==90&&Math.abs(aJ)==90);
            float[] A0=P[r][i],A1=P[r][j],B1=P[r+1][j],B0=P[r+1][i];
            if(front){float uI=.5f+aI/180f,uJ=.5f+aJ/180f;
                quadUV(A0,A1,B1,B0,N[r][i],N[r][j],N[r+1][j],N[r+1][i],FACE,new float[]{uI,faceV(r)},new float[]{uJ,faceV(r)},new float[]{uJ,faceV(r+1)},new float[]{uI,faceV(r+1)});}
            else{float ua=((aI<0?aI+360:aI)-90)/180f,ub=((aJ<0?aJ+360:aJ)-90)/180f;if(ub<ua)ub+=1;
                boolean behind=Math.abs(aI)>=140&&Math.abs(aJ)>=140;int t=r==NR-2?(behind?back:sideUp):(r==NR-3&&behind&&back==HAIR?HAIR:sideLow);float k=NR-1;   // hair covers the back of the head down to the napefloat k=NR-1;
                quadUV(A0,A1,B1,B0,N[r][i],N[r][j],N[r+1][j],N[r+1][i],t,new float[]{ua,1-r/k},new float[]{ub,1-r/k},new float[]{ub,1-(r+1)/k},new float[]{ua,1-(r+1)/k});}
        }
        float[] crown=place(c,pitch,v(-.06f*hx,0,1.06f*hz)),under=place(c,pitch,v(-.1f*hx,0,-.8f*hz));
        float[] upN=sub(place(v(0,0,0),pitch,v(0,0,1)),place(v(0,0,0),pitch,v(0,0,0))),dnN=mul(upN,-1);
        for(int i=0;i<n;i++){int j=(i+1)%n;
            tri(P[NR-1][j],P[NR-1][i],crown,N[NR-1][j],N[NR-1][i],upN,top,new float[]{.5f+.45f*(float)Math.sin(Math.toRadians(HEAD_A[j])),.5f-.45f*(float)Math.cos(Math.toRadians(HEAD_A[j])),.5f+.45f*(float)Math.sin(Math.toRadians(HEAD_A[i])),.5f-.45f*(float)Math.cos(Math.toRadians(HEAD_A[i])),.5f,.5f});
            boolean fr=Math.abs(HEAD_A[i])<=90&&Math.abs(HEAD_A[j])<=90;
            tri(P[0][i],P[0][j],under,N[0][i],N[0][j],dnN,fr?FACE:sideLow,fr?new float[]{.5f+HEAD_A[i]/180f,.995f,.5f+HEAD_A[j]/180f,.995f,.5f,.995f}:new float[]{.2f,.9f,.8f,.9f,.5f,.99f});}
    }
    static void faceQuad(float[] a,float[] b,float[] c,float[] d,int tile,float u0,float v0,float u1,float v1){
        float[] n=norm(cross(sub(b,a),sub(d,a)));quad(a,b,c,d,n,n,n,n,tile,u0,v0,u1,v1);
    }
    /**
     * Hood (or long hair): five loops of the head's 8 points blown up around it — a drape over the shoulders, the
     * cheeks, the temples, the crown — open at the front (the face looks out of it), closed by a fan at the top. A
     * deep hood has its tip pulled back into a peak. Long hair ({@code tile} &lt; 0) leaves the whole front half open
     * and the top to the head's own hair.
     */
    private static void hood(float[] c,float pitch,float hx,float hy,float hz,boolean peak,int tile){
        boolean hair=tile<0;if(hair)tile=-tile-1;
        float[][] R=hair?new float[][]{{-1.5f,.95f,.2f,.9f},{.1f,1.06f,.8f,1.1f},{1.0f,.78f,.7f,.85f}}
                        :new float[][]{{-1.42f,1.38f,.78f,1.05f},{-.5f,1.15f,1.1f,1.16f},{.42f,1.1f,1.08f,1.15f},{1.04f,.76f,.74f,.88f}};
        int n=8;float[][][] P=new float[R.length][n][],N=new float[R.length][n][];
        for(int r=0;r<R.length;r++)for(int i=0;i<n;i++){double a=Math.toRadians(HEAD_A[i]);float cs=(float)Math.cos(a),sn=(float)Math.sin(a);
            float cx=Math.signum(cs)*(float)Math.pow(Math.abs(cs),.8f),sy=Math.signum(sn)*(float)Math.pow(Math.abs(sn),.8f);
            float x=cx*(cs>=0?R[r][2]:R[r][3]),y=sy*R[r][1],z=R[r][0];
            if(!hair&&i==0)x+=.06f;   // the front edge stands a little proud of the face
            float[] l=v(x*hx-.004f,y*hy,z*hz+.008f);P[r][i]=place(c,pitch,l);
            N[r][i]=sub(place(v(0,0,0),pitch,v(x/hx,y/hy,z*.5f/hz)),place(v(0,0,0),pitch,v(0,0,0)));}
        float open=hair?90:40;
        for(int r=0;r+1<R.length;r++)for(int i=0;i<n;i++){int j=(i+1)%n;
            if(Math.abs(HEAD_A[i])<=open&&Math.abs(HEAD_A[j])<=open&&!(hair&&r==R.length-2))continue;   // the opening
            quad(P[r][i],P[r][j],P[r+1][j],P[r+1][i],N[r][i],N[r][j],N[r+1][j],N[r+1][i],tile,i/(float)n,1-r/(R.length-1f),(i+1)/(float)n,1-(r+1)/(R.length-1f));}
        if(!hair){float[] tip=place(c,pitch,peak?v(-.95f*hx,0,1.32f*hz):v(-.1f*hx,0,1.16f*hz));float[] tn=sub(place(v(0,0,0),pitch,peak?v(-.5f,0,1):v(0,0,1)),place(v(0,0,0),pitch,v(0,0,0)));
            int L=R.length-1;for(int i=0;i<n;i++){int j=(i+1)%n;tri(P[L][j],P[L][i],tip,N[L][j],N[L][i],tn,tile,new float[]{(i+1)/(float)n,0,i/(float)n,0,(i+.5f)/n,0});}}
    }
    /** Wizard hat: a flat 8-sided brim and a 6-sided cone with one loop cut, bent back toward the tip. */
    private static void hat(float[] crown,float[] up,float[] fwd,float hs){
        float[] side=norm(cross(up,fwd));float[] base=add(crown,up,-.03f);
        float[][] Br=new float[8][];for(int i=0;i<8;i++){double a=i*Math.PI/4;Br[i]=add(add(base,fwd,(float)Math.cos(a)*.19f*hs),side,(float)Math.sin(a)*.19f*hs);}
        cap(Br,base,up,.0001f,CLOAK,false);   // one-sided brim: drawn two-sided
        float[][] R0=new float[6][],R1=new float[6][],N=new float[6][];float[] mid=add(add(base,up,.15f),fwd,-.03f),tip=add(add(base,up,.32f),fwd,-.11f);
        for(int i=0;i<6;i++){double a=i*Math.PI/3;float[] d=add(mul(fwd,(float)Math.cos(a)),side,(float)Math.sin(a));N[i]=d;R0[i]=add(base,d,.105f*hs);R1[i]=add(mid,d,.06f*hs);}
        for(int i=0;i<6;i++){int j=(i+1)%6;quad(R0[i],R0[j],R1[j],R1[i],N[i],N[j],N[j],N[i],CLOAK,i/6f,.5f,(i+1)/6f,1);}
        cap(R1,mid,norm(sub(tip,mid)),len(sub(tip,mid)),CLOAK,false);
    }

    // ------------------------------------------------------------------ gear
    private static void weapon(Spec sp,Anim A,float[] rh,float[] lh,float[] wd){
        int st=A.atk;
        switch(sp.weapon){
            case W_SWORD_SHIELD:sword(rh,wd,.6f,true);curPart=Puppet.P_OFFHAND;shield(add(lh,v(.05f,-.03f,.02f)),norm(v(1,-.25f,0)),TRIM,WOOD);break;
            case W_MACE_SHIELD:mace(rh,wd);curPart=Puppet.P_OFFHAND;shield(add(lh,v(.05f,-.03f,.02f)),norm(v(1,-.25f,0)),TRIM,X1);break;
            case W_RUSTY:sword(rh,wd,.55f,false);break;
            case W_CRUDE:sword(rh,wd,.42f,false);break;
            case W_DAGGERS:{if(A.dead){sword(rh,wd,.22f,true);sword(lh,norm(v(.1f,-.3f,1)),.22f,true);break;}
                float[] dr=st==1?norm(v(.95f,-.1f,.1f)):norm(v(.55f,.15f,.8f)),dl=st==2?norm(v(.95f,.1f,.05f)):norm(v(.6f,-.1f,-.75f));
                sword(rh,dr,.22f,true);curPart=Puppet.P_OFFHAND;sword(lh,dl,.22f,true);break;}
            case W_BOW:{curPart=Puppet.P_OFFHAND;if(A.dead)bow(rh,v(0,0,1),v(-1,0,0));
                else if(st>=0)bow(lh,v(0,0,1),v(1,0,0));else bow(lh,norm(v(.25f,0,1)),norm(v(1,0,-.25f)));
                int at=nv;{float[] a0=add(lh,v(1,0,0),.08f),a1=rh;float w=.006f;   // the drawn arrow: two crossed flat strips
                    quad(add(a0,v(0,0,w)),add(a1,v(0,0,w)),add(a1,v(0,0,-w)),add(a0,v(0,0,-w)),v(0,1,0),v(0,1,0),v(0,1,0),v(0,1,0),WOOD,0,0,1,1);
                    quad(add(a0,v(0,w,0)),add(a1,v(0,w,0)),add(a1,v(0,-w,0)),add(a0,v(0,-w,0)),v(0,0,1),v(0,0,1),v(0,0,1),v(0,0,1),WOOD,0,0,1,1);}
                if(st!=0)collapse(at,rh);break;}
            case W_STAFF:{float[] a=add(rh,wd,-.85f),t=add(rh,wd,.5f);seg(a,t,.017f,.017f,.014f,.014f,.008f,0,WOOD,v(1,0,0));
                seg(t,add(t,wd,.05f),.03f,.03f,.024f,.024f,0,0,WOOD,v(1,0,0));   // claw that holds the orb
                octa(add(t,wd,.1f),st==1?.085f:st>=0?.065f:.05f,GLOW);break;}
            case W_AXE:axe(rh,wd);break;
            case W_CAST:{float[] fc=add(SpriteModels.lerp(rh,lh,.5f),v(.09f,0,.02f));int at=nv;float sz=st==1?1:.75f;octa(fc,.085f*sz,GLOW);octa(add(fc,v(.03f,0,0)),.05f*sz,X2);
                if(st<1)collapse(at,fc);break;}
            default:break;
        }
    }
    /**
     * Parts that only show in some frames (a drawn arrow, conjured fire) are still built in every frame, shrunk to a
     * point when hidden, so all frames keep the same vertices and blend smoothly into each other.
     */
    static void collapse(int from,float[] p){for(int i=from;i<nv;i++){int o=i*9;buf[o]=p[0];buf[o+1]=p[1];buf[o+2]=p[2];}}
    private static void sword(float[] hand,float[] dir,float blade,boolean guard){
        float[] base=add(hand,dir,-.07f),gTop=add(hand,dir,.06f);
        seg(base,gTop,.014f,.014f,.014f,.014f,-1,-1,WOOD,v(0,0,1));                                                  // grip
        float[] side=norm(cross(dir,v(0,0,1)));if(len(cross(dir,v(0,0,1)))<1e-3f)side=v(0,1,0);
        if(guard){float gw=blade<.3f?.05f:.1f;seg(add(gTop,side,-gw),add(gTop,side,gw),.016f,.016f,.016f,.016f,0,0,TRIM,dir);   // cross-guard
            if(blade>=.3f)octa(add(base,dir,-.018f),.022f,TRIM);}             // pommel (not on daggers)
        float bw=blade<.3f?.02f:.028f;
        seg(add(gTop,dir,.012f),add(gTop,dir,blade),bw,.006f,bw*.85f,.006f,-1,blade<.3f?.04f:.07f,STEEL,side);          // blade, pyramid point
    }
    /** Mace: a wooden haft and a flanged steel head (a box with pyramid ends both ways). */
    private static void mace(float[] hand,float[] dir){
        float[] a=add(hand,dir,-.1f),t=add(hand,dir,.36f);
        seg(a,t,.015f,.015f,.015f,.015f,0,-1,WOOD,v(0,0,1));
        {float[] hd=add(t,dir,.06f);octa(hd,.055f,STEEL);}   // flanged head: a steel diamond
        float[] side=norm(cross(dir,v(0,0,1)));if(len(cross(dir,v(0,0,1)))<1e-3f)side=v(0,1,0);
    }
    /** Greataxe: long haft, two crescent bits made of flattened boxes, a spike on top. */
    private static void axe(float[] hand,float[] dir){
        float[] a=add(hand,dir,-.42f),t=add(hand,dir,.62f);
        seg(a,t,.018f,.018f,.016f,.016f,0,-1,WOOD,v(0,0,1));
        float[] side=norm(cross(dir,v(0,0,1)));if(len(cross(dir,v(0,0,1)))<1e-3f)side=v(0,1,0);
        float[] hd=add(t,dir,-.12f);
        for(int s=-1;s<=1;s+=2)seg(add(hd,side,s*.015f),add(hd,side,s*.2f),.008f,.06f,.006f,.13f,0,0,STEEL,dir);
        seg(t,add(t,dir,.1f),.016f,.016f,.004f,.004f,-1,.02f,TRIM,side);
    }
    /** Round shield: an 8-sided disc, the front fan's centre pushed out into a boss. */
    private static void shield(float[] c,float[] facing,int rim,int face){
        float[][] sf=across(facing,v(0,0,1));float[] s=sf[0],u=sf[1];float R=.21f,T=.018f;
        float[] front=add(c,facing,T),back=add(c,facing,-T);float[][] F=new float[8][],B=new float[8][],N=new float[8][];
        for(int i=0;i<8;i++){double a=i*Math.PI/4+Math.PI/8;float[] d=add(mul(s,(float)Math.cos(a)),u,(float)Math.sin(a));N[i]=d;F[i]=add(front,d,R);B[i]=add(back,d,R);}
        for(int i=0;i<8;i++){int j=(i+1)%8;quad(F[i],F[j],B[j],B[i],N[i],N[j],N[j],N[i],rim,i/8f,0,(i+1)/8f,1);}
        cap(F,front,facing,.05f,face,false);   // no back face: models are drawn two-sided
    }
    /** Longbow: four tapering boxes bent back at the tips, a grip, and the string as a thin flat strip. */
    private static void bow(float[] hand,float[] up,float[] fwd){
        float[] top=add(add(hand,up,.48f),fwd,-.1f),bot=add(add(hand,up,-.48f),fwd,-.1f),mt=add(hand,up,.24f),mb=add(hand,up,-.24f);
        chain(new float[][]{bot,mb,hand,mt,top},new float[]{.006f,.012f,.016f,.012f,.006f},.006f,WOOD,fwd,3);   // one bent stave, grip painted on
        float[] side=norm(cross(up,fwd));float w=.0025f;
        quad(add(top,side,-w),add(top,side,w),add(bot,side,w),add(bot,side,-w),fwd,fwd,fwd,fwd,TRIM,0,0,1,1);   // string: one flat quad
    }

    // ------------------------------------------------------------------ cloth (flat sheets)
    /** Cape: one flat sheet (3 x 2 quads), hanging from the shoulders and trailing back. */
    private static void cape(float[] top,SpriteModels.Body b,float trail){
        int cols=2,rows=3;float w0=b.shoulderW*.95f,w1=b.shoulderW*1.35f,h=b.shoulderZ-.32f;
        float[][][] P=new float[rows+1][cols+1][];
        for(int r=0;r<=rows;r++)for(int cI=0;cI<=cols;cI++){float t=r/(float)rows,w=w0+(w1-w0)*t,y=(cI/(float)cols*2-1)*w;
            float back=-t*t*trail*2-t*.06f-Math.abs(y)*.06f*t;P[r][cI]=v(top[0]+back,top[1]+y,top[2]-t*h);}
        for(int r=0;r<rows;r++)for(int cI=0;cI<cols;cI++){float[] a=P[r][cI],bb=P[r][cI+1],cc=P[r+1][cI+1],d=P[r+1][cI];float[] n=norm(cross(sub(d,a),sub(bb,a)));
            quad(a,bb,cc,d,n,n,n,n,CLOAK,cI/(float)cols,r/(float)rows,(cI+1)/(float)cols,(r+1)/(float)rows);}
    }
    /**
     * Skirt / robe: an open 8-sided tube of flat quads from the waist down to a hem (no caps), with one loop cut
     * at the knee for a long robe. The hem sways with the walk.
     */
    private static void skirt(float[] waist,float w,float d,float[] hem,float hemR,float kneeR,int tile,boolean knee){
        int n=8;int rows=knee?2:1;float[][][] R=new float[rows+1][n][];float[][] N=new float[n][];
        float[] kc=SpriteModels.lerp(waist,hem,.55f);
        for(int i=0;i<n;i++){double a=i*2*Math.PI/n+Math.PI/8;float cx=(float)Math.cos(a),cy=(float)Math.sin(a);N[i]=v(cx,cy,0);
            R[0][i]=add(waist,v(cx*d*1.15f,cy*w*1.02f,0));
            if(knee){R[1][i]=add(kc,v(cx*kneeR*.9f,cy*kneeR,0));R[2][i]=add(hem,v(cx*hemR*.92f,cy*hemR,0));}
            else R[1][i]=add(hem,v(cx*hemR*.85f,cy*hemR,0));}
        for(int r=0;r<rows;r++)for(int i=0;i<n;i++){int j=(i+1)%n;quad(R[r][i],R[r][j],R[r+1][j],R[r+1][i],N[i],N[j],N[j],N[i],tile,i/(float)n,r/(float)rows,(i+1)/(float)n,(r+1)/(float)rows);}
        if(tile==PELVIS&&S.tunic){   // villager's apron: a single flat quad in front
            float[] a0=add(waist,v(d*1.2f,-.08f,-.02f)),a1=add(waist,v(d*1.2f,.08f,-.02f)),a2=add(hem,v(hemR*.95f,.09f,-.08f)),a3=add(hem,v(hemR*.95f,-.09f,-.08f));
            float[] nn=v(1,0,0);quad(a0,a1,a2,a3,nn,nn,nn,nn,X1,0,0,1,1);}
    }
    /** Tabard / kilt: a flat panel front and back, hanging from the waist past the hips. */
    private static void tabard(float[] waist,SpriteModels.Body b,float[] up,float bob,float k){
        float hw=S.weapon==W_AXE?.13f*k:.1f;float drop=S.weapon==W_AXE?.27f:.33f;
        for(int s=-1;s<=1;s+=2){float x=s*(.098f*k+.006f);float[] fwd=v(s,0,0);
            float[] a=v(waist[0]+x,-hw,waist[2]+.04f),bq=v(waist[0]+x,hw,waist[2]+.04f),c=v(waist[0]+x*1.15f+s*.02f,hw*1.1f,waist[2]-drop),d=v(waist[0]+x*1.15f+s*.02f,-hw*1.1f,waist[2]-drop);
            if(s<0){float[] t=a;a=bq;bq=t;t=c;c=d;d=t;}
            quad(a,bq,c,d,fwd,fwd,fwd,fwd,X2,0,0,1,1);}
    }

    // ------------------------------------------------------------------ painting
    private static int rgb(int r,int g,int b){return 0xff000000|(Math.max(0,Math.min(255,r))<<16)|(Math.max(0,Math.min(255,g))<<8)|Math.max(0,Math.min(255,b));}
    private static float hash(int x,int y){int h=x*374761393+y*668265263;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0xffff)/65535f;}
    /** The face, painted on the front of the head (FacePaint), and hair strands on the hair tile. */
    private static void paintFace(MeshModels.Mesh m){
        int Sz=G*PT,T=PT;
        FacePaint.paint(m.color,Sz,(FACE%G)*T,(FACE/G)*T,T,S);
        boolean hairTile=!S.bones&&S.head!=H_SKULL;
        if(hairTile){int ox=(HAIR%G)*T,oy=(HAIR/G)*T;   // hair: strands, clumps and a sheen instead of woven cloth
            int hr=(S.hairRGB>>16)&255,hg=(S.hairRGB>>8)&255,hb=S.hairRGB&255;
            if(S.head==H_GOBLIN||S.head==H_ORC){int c=m.color[oy*Sz+ox+T/2];hr=(c>>16)&255;hg=(c>>8)&255;hb=c&255;}
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){float k=FacePaint.hairShade(x,y,T,13);m.color[(oy+y)*Sz+ox+x]=rgb((int)(hr*k),(int)(hg*k),(int)(hb*k));}}
    }
    /** Detail painted instead of modelled: ribs, a holy sun, belts, dragon scales. */
    private static void paintExtra(MeshModels.Mesh m,int id){
        int Sz=G*PT,T=PT;int[] px=m.color;
        if(S.snout)for(int t:new int[]{SKIN,HANDS}){if(t==HANDS&&S.weapon!=W_STAFF)continue;int ox=(t%G)*T,oy=(t/G)*T;   // dragon scales over the skin
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*Sz+ox+x,c=px[o];float k=FacePaint.scales(x,y,T,5.5f);px[o]=rgb((int)(((c>>16)&255)*k),(int)(((c>>8)&255)*k),(int)((c&255)*k));}}
        if(id==Sprites.SKELETON){int ox=(TORSO%G)*T,oy=(TORSO/G)*T;
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*Sz+ox+x;int c=px[o];float u=(x%(T/4))/(float)(T/4),v=y/(float)T;
                boolean rib=((int)(v*9))%2==0,spine=Math.abs(u-.5f)<.08f&&x/(T/4)%2==0;
                if(!rib&&!spine){px[o]=rgb(20,16,12);}else if(Math.abs((v*9)%1-.5f)>.38f&&!spine){px[o]=rgb(((c>>16)&255)*6/10,((c>>8)&255)*6/10,(c&255)*6/10);}}}
        if(id==Sprites.HERO_CLERIC){int ox=(X1%G)*T,oy=(X1/G)*T;   // shield face: white field with a gold sun
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*Sz+ox+x;float dx=x-T/2f,dy=y-T/2f,d=(float)Math.sqrt(dx*dx+dy*dy);double a=Math.atan2(dy,dx);
                boolean ray=d<T*.4f&&Math.abs(Math.sin(a*6))>.82f,sun=d<T*.16f;if(sun||ray)px[o]=rgb(214,172,64);}}
        // a belt across the waist of every tunic/torso (top band of the pelvis tile)
        if(id!=Sprites.SKELETON&&id!=Sprites.CULTIST&&id!=Sprites.HERO_WIZARD){int ox=(PELVIS%G)*T,oy=(PELVIS/G)*T;
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){float v=y/(float)T;if(v>.82f){int o=(oy+y)*Sz+ox+x;boolean buckle=((x%(T/4))>T/8-3*T/64&&(x%(T/4))<T/8+3*T/64)&&x/(T/4)==0;px[o]=buckle?rgb(184,154,80):rgb(58,38,22);}}}
    }
}
