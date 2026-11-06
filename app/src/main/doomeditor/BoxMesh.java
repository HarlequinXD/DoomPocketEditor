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
        float scale=1,bulk=1,headS=1,shoulderW=1,chestW=1;
        int iris=0xff34505e,hairRGB=0xff3a2414,beardRGB=0xff3a2414,maskRGB=0xff141418;
        // created heroes (CharLook): body type, hair, race features
        boolean female,solidEyes,heavyBrow,snout,tail,horns,tusks,dragonEyes;int hairStyle=1,ears;float nose,beardLen=1;String race="";
    }

    static boolean has(int id){
        return id==Sprites.CREATED||id==Sprites.HERO||id==Sprites.SKELETON||id==Sprites.GOBLIN||id==Sprites.ORC||id==Sprites.CULTIST||id==Sprites.VILLAGER||(id>=Sprites.HERO_RANGER&&id<=Sprites.HERO_CLERIC);
    }

    static int G;
    static float[] buf;static int nv;
    /** Tiles whose material glows (fire, magic): drawn full-bright. */
    static boolean[] glowTile=new boolean[16];
    /** Starts a frame for a model whose materials are {@code m} (atlas 4 x 4). */
    static void begin(SpriteModels.Model m){G=4;buf=new float[9*3*600];nv=0;glowTile=new boolean[16];for(int i=0;i<Math.min(16,m.mats.size());i++)glowTile[i]=m.mats.get(i).glow;}
    static MeshModels.Frame end(){MeshModels.Frame fr=new MeshModels.Frame();fr.v=java.util.Arrays.copyOf(buf,nv*9);fr.count=nv;return fr;}
    private static Spec S;

    /** A model for any look (the creation screen preview), without touching the shared Created Character model. */
    static synchronized MeshModels.Mesh buildLook(CharLook l){return make(specFor(l),outfitFor(Rpg.heroClass(l.cls)));}
    static synchronized MeshModels.Mesh build(int id){
        Spec sp=id==Sprites.CREATED?specFor(CharLook.current):spec(id);if(sp==null)return null;
        return make(sp,id==Sprites.CREATED?outfitFor(Rpg.heroClass(CharLook.current.cls)):id);
    }
    private static MeshModels.Mesh make(Spec sp,int paintId){
        S=sp;G=4;for(int i=0;i<16;i++)glowTile[i]=i<sp.m.mats.size()&&sp.m.mats.get(i).glow;
        MeshModels.Mesh out=new MeshModels.Mesh();out.atlas=G;
        for(int f=0;f<Sprites.FRAMES;f++)out.frames[f]=frame(f);
        MeshModels.paint(out,sp.m,G);
        paintFace(out);paintExtra(out,paintId);
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
            case "Dwarf":s.scale*=.8f;s.bulk*=1.28f;s.shoulderW*=1.16f;s.chestW*=1.14f;s.headS*=1.2f;s.beardLen=1.6f;break;
            case "Halfling":s.scale*=.63f;s.headS*=1.22f;setMat(s.m,BOOTS,0,skin);break;
            case "Gnome":s.scale*=.59f;s.headS*=1.38f;s.bulk*=.95f;s.ears=3;s.nose=.06f;break;
            case "Half-Orc":s.scale*=1.04f;s.bulk*=1.16f;s.shoulderW*=1.1f;s.chestW*=1.08f;s.headS*=1.03f;s.tusks=true;s.heavyBrow=true;break;
            case "Tiefling":s.horns=true;s.tail=true;s.solidEyes=true;break;
            case "Dragonborn":s.scale*=1.07f;s.bulk*=1.14f;s.shoulderW*=1.1f;s.chestW*=1.06f;s.snout=true;s.tail=true;s.dragonEyes=true;s.eyeGems=true;
                s.hairStyle=0;s.hairline=false;s.beard=false;s.stubble=false;setMat(s.m,GLOW,2,l.eyeColour());break;
        }
        if(s.solidEyes)s.iris=0xffe0b030;
        return s;
    }

    // ------------------------------------------------------------------ posing
    private static float[] rotY(float[] p,float[] pv,float a){float dx=p[0]-pv[0],dz=p[2]-pv[2],c=(float)Math.cos(a),s=(float)Math.sin(a);return v(pv[0]+dx*c+dz*s,p[1],pv[2]-dx*s+dz*c);}
    private static float[] legDir(float a){return v((float)Math.sin(a),0,-(float)Math.cos(a));}
    private static MeshModels.Frame frame(int f){
        buf=new float[9*3*560];nv=0;
        Spec sp=S;float k=sp.bulk;
        SpriteModels.Pose ps=SpriteModels.pose(f);SpriteModels.Body b=new SpriteModels.Body();
        b.chestW*=sp.chestW;b.shoulderW*=sp.shoulderW;
        boolean pain=f==Sprites.F_PAIN;
        if(sp.crouch&&!ps.dead&&!pain){ps.lean+=.25f;ps.lThigh+=.15f;ps.rThigh+=.15f;ps.lKnee+=.3f;ps.rKnee+=.3f;}
        if(sp.weapon==W_NONE){ps.attack=false;}
        float bob=ps.bob,lean=ps.lean;
        float[] pelvis=v(0,0,b.hipZ+bob);
        // ---- legs: 5-sided cylinders (hidden under a robe, but the feet still show), loop-cut feet
        for(int s=-1;s<=1;s+=2){float th=s<0?ps.lThigh:ps.rThigh,kn=s<0?ps.lKnee:ps.rKnee;
            float[] hip=v(0,s*b.hipW,b.hipZ+bob),knee=add(hip,legDir(th),b.thigh),ankle=add(knee,legDir(th-kn),b.shin);
            if(ankle[2]<.07f)ankle[2]=.07f;
            if(!sp.robe){
                if(sp.bones){cyl5(hip,knee,.026f,.02f,.012f,.016f,LEGS);cyl5(knee,ankle,.02f,.016f,.016f,.01f,LEGS);}
                else{cyl5(hip,knee,.072f*k,.052f*k,.018f*k,.016f*k,LEGS);cyl5(knee,ankle,.052f*k,.036f*k,.014f*k,.012f*k,LEGS);}}
            foot(ankle,sp.bones?.6f:Math.max(.85f,k),BOOTS);}
        // ---- body (leans about the pelvis)
        float[] waist=rotY(v(0,0,b.waistZ+bob),pelvis,lean),chest=rotY(v(0,0,b.chestZ+bob),pelvis,lean),shTop=rotY(v(0,0,b.shoulderZ+.035f+bob),pelvis,lean);
        float[] up=norm(sub(shTop,waist));
        if(sp.bones){
            seg(v(0,0,b.hipZ-.04f+bob),v(0,0,b.hipZ+.05f+bob),b.hipW*1.4f,.05f,b.hipW*1.1f,.04f,.01f,.01f,PELVIS,v(1,0,0));   // hip bone
            seg(v(0,0,b.hipZ+.03f+bob),add(waist,up,.06f),.022f,.022f,.02f,.02f,0,0,TORSO,v(1,0,0));                             // spine
            torso(add(waist,up,.05f),chest,shTop,b,up,.8f,TORSO);                                                                  // ribcage
        }else{
            pelvisBox(v(0,0,b.hipZ-.03f+bob),waist,b,up,k);
            torso(waist,chest,shTop,b,up,k,TORSO);
        }
        if(sp.tail){   // tiefling / dragonborn tail: three tapering boxes swaying with the walk, pyramid tip
            float sw=f==Sprites.F_WALK1?.08f:f==Sprites.F_WALK2?-.08f:ps.attack?.04f:0;float t=sp.snout?1.3f:.75f;
            float[] t0=rotY(v(-.07f,0,b.hipZ+.02f+bob),pelvis,lean),t1=add(t0,v(-.18f,sw*.5f,-.2f)),t2=add(t1,v(-.2f,sw,-.16f)),t3=add(t2,v(-.16f,sw*1.4f,.03f));
            if(t2[2]<.08f)t2[2]=.08f;if(t3[2]<.06f)t3[2]=.06f;
            chain(new float[][]{t0,add(t1,v(-.06f,0,-.04f)),t3},new float[]{.03f*t,.02f*t,sp.snout?.004f:.012f},sp.snout?.02f:.035f,SKIN,v(0,0,1));}
        float[] neckB=add(shTop,up,-.005f),neckT=add(shTop,up,.07f*Math.min(1.1f,sp.headS));
        if(sp.bones)seg(neckB,neckT,.018f,.018f,.016f,.016f,0,0,TORSO,v(1,0,0));
        else box(neckB,neckT,.04f*k,.042f*k,SKIN);
        float hp=ps.headPitch+lean*.4f;
        float[] hc=add(neckT,up,.085f*sp.headS);
        head(hc,lean+hp,sp);
        // ---- arms (two-bone IK to the hands, elbows bend back and out)
        float[] rS=rotY(v(0,b.shoulderW,b.shoulderZ+bob),pelvis,lean),lS=rotY(v(0,-b.shoulderW,b.shoulderZ+bob),pelvis,lean);
        float[][] h=hands(sp,ps,f,b,bob);float[] rh=h[0],lh=h[1];
        if(rh==null)rh=limp(rS,ps.rArmSwing,1,sp);else if(!ps.dead)rh=rotY(rh,pelvis,lean);
        if(lh==null)lh=limp(lS,ps.lArmSwing,-1,sp);else if(!ps.dead)lh=rotY(lh,pelvis,lean);
        float[] rE=SpriteModels.ik(rS,rh,b.upper,b.fore,v(-1,.5f,-.4f)),lE=SpriteModels.ik(lS,lh,b.upper,b.fore,v(-1,-.5f,-.4f));
        arm(rS,rE,rh,sp);arm(lS,lE,lh,sp);
        weapon(sp,ps,f,rh,lh,h[2]);
        if(sp.pauldrons)for(int s=-1;s<=1;s+=2){float[] sh=s>0?rS:lS;float[] side=v(0,s,0);
            seg(add(add(sh,up,-.04f),side,.01f),add(add(sh,up,.04f),side,.02f),.085f*k,.075f*k,.07f*k,.065f*k,0,.045f*k,TRIM,v(1,0,0));}
        // ---- clothing: flat sheets
        float sway=f==Sprites.F_WALK1?.04f:f==Sprites.F_WALK2?-.04f:0;
        if(sp.robe)skirt(waist,b.chestW*.95f,.1f,v(sway,0,.03f),.25f,.2f,CLOAK,true);
        if(sp.tunic)skirt(waist,b.chestW*.88f,.095f,v(sway*.5f,0,.52f+bob),.18f,0,PELVIS,false);
        if(sp.tabard)tabard(waist,b,up,bob,k);
        if(sp.quiver){float[] q0=rotY(v(-.13f,.06f,b.waistZ+.02f+bob),pelvis,lean),q1=rotY(v(-.16f,-.06f,b.shoulderZ+.12f+bob),pelvis,lean);
            seg(q0,q1,.04f,.035f,.045f,.04f,0,0,X1,v(-1,0,0));
            octa(add(q1,v(-.005f,0,.045f)),.028f,X2);}
        if(sp.cape){float trail=(f==Sprites.F_WALK1||f==Sprites.F_WALK2)?.12f:ps.attack?.06f:.03f;
            float[] back=norm(v((float)-Math.cos(lean),0,(float)Math.sin(lean)));
            cape(add(rotY(v(0,0,b.shoulderZ+bob),pelvis,lean),back,sp.quiver?.15f:.11f),b,trail);}
        MeshModels.Frame fr=new MeshModels.Frame();fr.v=java.util.Arrays.copyOf(buf,nv*9);fr.count=nv;
        if(ps.dead)layDown(fr);
        if(sp.scale!=1)for(int i=0;i<fr.count;i++){int o=i*9;fr.v[o]*=sp.scale;fr.v[o+1]*=sp.scale;fr.v[o+2]*=sp.scale;}
        return fr;
    }
    /** Hand targets {right, left, weapon direction} in the body's unleaned space; null = the arm hangs. */
    private static float[][] hands(Spec sp,SpriteModels.Pose ps,int f,SpriteModels.Body b,float bob){
        if(ps.dead)return new float[][]{v(.1f,.42f,b.shoulderZ-.2f),v(.05f,-.42f,b.shoulderZ-.25f),norm(v(.12f,.3f,1))};   // weapon along the body: flat on the floor once it tips over
        boolean at=ps.attack,pain=f==Sprites.F_PAIN;
        switch(sp.weapon){
            case W_SWORD_SHIELD:case W_MACE_SHIELD:
                if(pain)return new float[][]{v(.05f,.32f,.95f),v(.22f,-.2f,.98f+bob),norm(v(.2f,.4f,.9f))};
                return new float[][]{at?v(.4f,.08f,1.05f):v(.2f,.2f,.88f+bob),v(.22f,-.2f,.98f+bob),at?norm(v(.7f,-.5f,-.25f)):norm(v(.45f,.05f,.9f))};
            case W_BOW:return new float[][]{at?v(.12f,.04f,1.2f):v(.1f,.22f,.85f+bob),at?v(.48f,-.05f,1.18f):v(.2f,-.22f,.9f+bob),null};
            case W_DAGGERS:return new float[][]{at?v(.45f,.06f,1.0f):v(.22f,.2f,.85f+bob),at?v(.25f,-.2f,.95f):v(.22f,-.2f,.85f+bob),null};
            case W_STAFF:return new float[][]{at?v(.38f,.12f,1.12f):v(.18f,.22f,.9f+bob),at?v(.32f,-.12f,1.12f):null,at?norm(v(.35f,0,1)):norm(v(.05f,0,1))};
            case W_RUSTY:return new float[][]{at?v(.35f,.1f,1.13f):v(.18f,.2f,.88f+bob),v(.12f,-.24f,.8f+ps.lArmSwing*.1f),at?norm(v(.8f,-.4f,-.3f)):norm(v(.6f,.1f,.8f))};
            case W_CAST:return new float[][]{at?v(.36f,.08f,1.08f):v(.16f,.07f,.86f+bob),at?v(.36f,-.08f,1.08f):v(.16f,-.07f,.86f+bob),null};
            case W_CRUDE:return new float[][]{at?v(.42f,.05f,1.0f):v(.2f,.2f,.82f),null,at?norm(v(.8f,-.3f,-.4f)):norm(v(.55f,.1f,.82f))};
            case W_AXE:{if(at)return new float[][]{v(.38f,.1f,1.15f),v(.3f,-.05f,1.02f),norm(v(.8f,-.1f,-.4f))};
                float[] r=v(.18f,.22f,.85f+bob),l=v(.22f,-.02f,1.05f+bob);return new float[][]{r,l,norm(sub(l,r))};}
            default:return new float[][]{f==Sprites.F_ATTACK?v(.25f,.25f,1.3f):null,null,null};   // villager waves
        }
    }
    private static float[] limp(float[] sh,float swing,int side,Spec sp){
        float sw=swing*(sp.weapon==W_NONE?.5f:1)+(sp.crouch?.35f:.05f);
        return add(sh,norm(v((float)Math.sin(sw),side*.14f,-(float)Math.cos(sw))),.47f);
    }
    /** Death: tip the whole body over backwards onto the floor. */
    private static void layDown(MeshModels.Frame fr){
        float a=-1.45f,c=(float)Math.cos(a),s=(float)Math.sin(a);float minZ=9;
        for(int i=0;i<fr.count;i++){int o=i*9;float x=fr.v[o],z=fr.v[o+2];fr.v[o]=x*c+z*s;fr.v[o+2]=-x*s+z*c;float nx=fr.v[o+3],nz=fr.v[o+5];fr.v[o+3]=nx*c+nz*s;fr.v[o+5]=-nx*s+nz*c;minZ=Math.min(minZ,fr.v[o+2]);}
        for(int i=0;i<fr.count;i++)fr.v[i*9+2]-=minZ-.01f;
    }

    // ------------------------------------------------------------------ vertex output
    static void put(float[] p,float[] n,int tile,float u,float vv){
        if((nv+1)*9>buf.length)buf=java.util.Arrays.copyOf(buf,buf.length*2);
        float in=2f/MeshModels.TILE;u=in+Math.max(0,Math.min(1,u))*(1-2*in);vv=in+Math.max(0,Math.min(1,vv))*(1-2*in);
        float[] nn=norm(n);int o=nv*9;buf[o]=p[0];buf[o+1]=p[1];buf[o+2]=p[2];buf[o+3]=nn[0];buf[o+4]=nn[1];buf[o+5]=nn[2];
        buf[o+6]=((tile%G)+u)/G;buf[o+7]=((tile/G)+vv)/G;buf[o+8]=glowTile[tile]?1:0;nv++;
    }
    static void tri(float[] a,float[] b,float[] c,float[] na,float[] nb,float[] nc,int tile,float[] uv){put(a,na,tile,uv[0],uv[1]);put(b,nb,tile,uv[2],uv[3]);put(c,nc,tile,uv[4],uv[5]);}
    static void quad(float[] a,float[] b,float[] c,float[] d,float[] na,float[] nb,float[] nc,float[] nd,int tile,float u0,float v0,float u1,float v1){
        tri(a,b,c,na,nb,nc,tile,new float[]{u0,v0,u1,v0,u1,v1});tri(a,c,d,na,nc,nd,tile,new float[]{u0,v0,u1,v1,u0,v1});}
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
        cap(A,a,mul(ax,-1),domeA,tile,true);cap(B,b,ax,domeB,tile,false);
    }
    static void cap(float[][] ring,float[] c,float[] out,float dome,int tile,boolean flip){
        int n=ring.length;
        if(dome<=0&&n==4){float[] nn=out;if(flip){tri(ring[0],ring[2],ring[1],nn,nn,nn,tile,new float[]{0,0,1,1,1,0});tri(ring[0],ring[3],ring[2],nn,nn,nn,tile,new float[]{0,0,0,1,1,1});}
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
        cap(R[0],P[0],norm(sub(P[0],P[1])),0,tile,true);cap(R[n-1],P[n-1],norm(sub(P[n-1],P[n-2])),Math.max(1e-4f,tip),tile,false);
    }
    /** 8-triangle diamond for tiny details (eyes, gems, fletching, magic). */
    static void octa(float[] c,float r,int tile){
        float[][] P={add(c,v(r,0,0)),add(c,v(0,r,0)),add(c,v(-r,0,0)),add(c,v(0,-r,0))};float[] t=add(c,v(0,0,r)),bt=add(c,v(0,0,-r));
        for(int i=0;i<4;i++){int j=(i+1)%4;float[] n1=sub(P[i],c),n2=sub(P[j],c);
            tri(P[i],P[j],t,n1,n2,v(0,0,1),tile,new float[]{0,1,1,1,.5f,0});tri(P[j],P[i],bt,n2,n1,v(0,0,-1),tile,new float[]{1,0,0,0,.5f,1});}
    }
    /** Arm: bicep box and forearm box with pyramid ends that overlap at the elbow, and a fist (or a wide sleeve and a hand). */
    private static void arm(float[] sh,float[] el,float[] hand,Spec sp){
        float[] fw=v(1,0,0);float k=sp.bulk;
        float[] dir=norm(sub(hand,el));float[] wrist=add(hand,dir,-.035f);
        if(sp.bones){
            seg(sh,el,.02f,.02f,.016f,.016f,.012f,.014f,UPPER,fw);
            seg(el,wrist,.017f,.017f,.014f,.014f,.014f,.008f,HANDS,fw);
            seg(wrist,add(wrist,dir,.07f),.024f,.03f,.02f,.026f,.01f,0,HANDS,fw);
            return;
        }
        // bicep: thick in the middle of the upper arm, shaped box with rounded (pyramid) ends at the shoulder and elbow
        seg(add(sh,norm(sub(el,sh)),-.01f),el,.05f*k,.052f*k,.04f*k,.042f*k,.03f*k,.022f*k,UPPER,fw);
        if(sp.sleeves){   // wide sleeve flaring open past the elbow, the bare hand coming out of it
            seg(add(el,dir,-.02f),add(wrist,dir,-.01f),.045f,.047f,.075f,.075f,.02f,0,CLOAK,fw);
            seg(wrist,add(wrist,dir,.08f),.03f,.036f,.027f,.034f,.016f,0,HANDS,fw);
            return;
        }
        // forearm: wider below the elbow, narrowing to the wrist, rounded both ends
        seg(add(el,dir,-.012f),wrist,.042f*k,.044f*k,.031f*k,.033f*k,.02f*k,.016f*k,HANDS,fw);
        // fist: a box, pyramid end where it meets the forearm, flat knuckles
        seg(wrist,add(wrist,dir,.085f*Math.max(.9f,k)),.032f*k,.04f*k,.03f*k,.038f*k,.018f*k,0,HANDS,fw);
    }
    /** Foot: box loop-cut halfway, stretched forward from the heel; the toe end narrowed for a mild point. */
    private static void foot(float[] ankle,float k,int tile){
        float sole=Math.max(0,ankle[2]-.07f);float[] heel=v(ankle[0]-.045f*k,ankle[1],sole),mid=v(ankle[0]+.05f*k,ankle[1],sole),toe=v(ankle[0]+.15f*k,ankle[1],sole);
        float[][] R0=ring(heel,.042f*k,.075f*Math.min(1,k)),R1=ring(mid,.048f*k,.065f*Math.min(1,k)),R2=ring(toe,.032f*k,.035f*Math.min(1,k));
        float[][] N={v(0,1,0),v(0,0,1),v(0,-1,0),v(0,0,-1)};
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(R0[i],R0[j],R1[j],R1[i],N[i],N[j],N[j],N[i],tile,i/4f,0,(i+1)/4f,.5f);quad(R1[i],R1[j],R2[j],R2[i],N[i],N[j],N[j],N[i],tile,i/4f,.5f,(i+1)/4f,1);}
        cap(R0,heel,v(-1,0,0),0,tile,true);cap(R2,toe,v(1,0,0),0,tile,false);
    }
    private static float[][] ring(float[] c,float halfW,float h){return new float[][]{v(c[0],c[1]+halfW,c[2]),v(c[0],c[1]+halfW,c[2]+h),v(c[0],c[1]-halfW,c[2]+h),v(c[0],c[1]-halfW,c[2])};}
    private static void pelvisBox(float[] bottom,float[] top,SpriteModels.Body b,float[] up,float k){
        float hip=S.female?1.14f:1;seg(bottom,top,b.hipW*1.75f*Math.max(1,k*.9f)*hip,.085f*k,b.chestW*.82f*(S.female?.86f:1),.09f*k,.012f,0,PELVIS,v(1,0,0));
    }
    /** Torso: waist → chest → shoulders, a box with one loop cut at the chest, shaped wider across the chest. */
    private static void torso(float[] waist,float[] chest,float[] top,SpriteModels.Body b,float[] up,float k,int tile){
        float[][] sf=across(up,v(1,0,0));float[] s=sf[0],f=sf[1];float dk=Math.max(.8f,k);
        float[][] R0=new float[4][],R1=new float[4][],R2=new float[4][],N=new float[4][];int[][] cc={{1,1},{-1,1},{-1,-1},{1,-1}};
        for(int i=0;i<4;i++){float cs=cc[i][0],cf=cc[i][1];
            float waistW=S.female?.7f:.82f,bust=S.female&&!S.bones?.135f:.115f;
            R0[i]=add(add(waist,s,cs*b.chestW*waistW),f,cf*.09f*dk);R1[i]=add(add(chest,s,cs*b.chestW*1.05f),f,cf*(cf>0?bust:.095f)*dk);R2[i]=add(add(top,s,cs*b.shoulderW*1.1f),f,cf*.085f*dk);N[i]=add(mul(s,cs),f,cf);}
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(R0[i],R0[j],R1[j],R1[i],N[i],N[j],N[j],N[i],tile,i/4f,0,(i+1)/4f,.5f);quad(R1[i],R1[j],R2[j],R2[i],N[i],N[j],N[j],N[i],tile,i/4f,.5f,(i+1)/4f,1);}
        cap(R0,waist,mul(up,-1),0,tile,true);cap(R2,top,up,.025f,tile,false);
    }

    // ------------------------------------------------------------------ heads
    private static float[][][][] HP;   // the last head's lattice, for attaching ears, noses, tusks and eyes
    private static float[] place(float[] c,float pitch,float[] p){float cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);return v(c[0]+p[0]*cp+p[2]*sp,c[1]+p[1],c[2]-p[0]*sp+p[2]*cp);}
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
        float hs=sp.headS;float hx=.098f*hs,hy=.083f*hs,hz=.105f*hs;
        float jawNarrow=.68f,jawDrop=-1.16f,backLift=-.38f;
        if(sp.female){hx*=.95f;hy*=.96f;jawNarrow=.6f;jawDrop=-1.1f;}
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
        int frontTile=FACE;
        for(int a=0;a<2;a++)for(int d=0;d<2;d++){
            faceQuad(P[2][a][d+1],P[2][a+1][d+1],P[2][a+1][d],P[2][a][d],frontTile,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);                  // front: the face
            faceQuad(P[0][a+1][d+1],P[0][a][d+1],P[0][a][d],P[0][a+1][d],back,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);                      // back of the head
            faceQuad(P[a][2][d+1],P[a+1][2][d+1],P[a+1][2][d],P[a][2][d],d==1?sideUp:sideLow,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);       // right side
            faceQuad(P[a+1][0][d+1],P[a][0][d+1],P[a][0][d],P[a+1][0][d],d==1?sideUp:sideLow,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);       // left side
            faceQuad(P[a][d][2],P[a][d+1][2],P[a+1][d+1][2],P[a+1][d][2],top,a/2f,d/2f,(a+1)/2f,(d+1)/2f);                           // top
            faceQuad(P[a+1][d][0],P[a+1][d+1][0],P[a][d+1][0],P[a][d][0],under,a/2f,d/2f,(a+1)/2f,(d+1)/2f);                         // under the jaw
        }
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
        if(sp.ears>0&&!hooded){float len=sp.ears==2?.12f:sp.ears==1?.055f:.07f,upk=sp.ears==2?.08f:sp.ears==1?.035f:.02f;
            for(int s=-1;s<=1;s+=2){float[] e=hp(1.05f,s<0?0:2,1.2f);float[] tip=add(add(add(e,v(0,s,0),len*hs),fwd,-.05f),upH,upk);
                seg(e,tip,.01f,.028f*hs,.003f,.005f,0,0,SKIN,fwd);}}
        if(sp.nose>0){float[] n=hp(2,1,.95f);seg(add(n,fwd,-.01f),add(add(n,fwd,sp.nose*hs),upH,-.015f),.016f,.014f,.006f,.006f,0,.006f,SKIN,upH);}
        if(sp.tusks)for(int s=-1;s<=1;s+=2){float[] t0=hp(2,s<0?.6f:1.4f,.25f);seg(add(t0,fwd,-.01f),add(add(t0,upH,.04f),fwd,.01f),.007f,.007f,.002f,.002f,0,.003f,X1,fwd);}
        if(sp.horns)for(int s=-1;s<=1;s+=2){float[] h0=hp(1.55f,s<0?.45f:1.55f,1.85f),h1=add(add(add(h0,upH,.08f*hs),fwd,-.02f),v(0,s,0),.015f),h2=add(add(add(h1,upH,.04f*hs),fwd,-.1f*hs),v(0,s,0),.02f);
            chain(new float[][]{h0,h1,h2},new float[]{.02f,.014f,.003f},.006f,BOOTS,fwd,3);}
        if(sp.snout)for(int s=-1;s<=1;s+=2){float[] h0=hp(.7f,s<0?.5f:1.5f,1.9f);chain(new float[][]{h0,add(add(h0,fwd,-.12f*hs),upH,.03f)},new float[]{.016f,.003f},.005f,SKIN,fwd,3);}   // swept-back spikes
        if(sp.head==H_HAIR||sp.head==H_HAT||sp.horns){
            if(sp.hairStyle==2)hood(c,pitch,hx*.9f,hy*.88f,hz*.92f,false,-HAIR-1);                                                  // long hair: a shell over the back and sides
            if(sp.hairStyle==3){float[] t0=hp(.05f,1,1.55f);seg(t0,add(add(t0,fwd,-.1f),upH,-.22f),.03f,.03f,.012f,.014f,.01f,.01f,HAIR,upH);}   // ponytail
            if(sp.hairStyle==4){float[] a0=hp(1.9f,1,2),a1=hp(.15f,1,1.85f);seg(a0,a1,.014f,.035f,.014f,.03f,.01f,.01f,HAIR,upH);}             // mohawk ridge
        }
        if(sp.beard){float[] ch=hp(2,1,.2f);float[] bot=add(add(ch,upH,-.15f*sp.beardLen*(sp.weapon==W_STAFF?1.4f:1)),fwd,.0f);
            seg(add(add(ch,upH,.03f),fwd,-.035f),bot,.07f*hs,.04f,.02f,.012f,0,0,HAIR,fwd);}                                           // beard wedge
        if(sp.dragonEyes){for(int s=-1;s<=1;s+=2){float[] e=hp(1.55f,s<0?0:2,1.45f);octa(add(e,v(0,s,0),.004f),.014f,GLOW);}}
        else if(sp.eyeGems){float g=sp.head==H_SKULL?.012f:sp.head==H_DEEP_HOOD?.011f:.012f;
            for(int s=-1;s<=1;s+=2){float[] e=hp(2,1+s*.42f,sp.head==H_ORC?1.1f:1.18f);octa(add(e,fwd,sp.head==H_SKULL?-.004f:.004f),g,GLOW);}}
        if((sp.head==H_HOOD||sp.head==H_DEEP_HOOD)&&!sp.horns)hood(c,pitch,hx,hy,hz,sp.head==H_DEEP_HOOD,CLOAK);   // horned heroes wear the hood down
        if(sp.head==H_HAT)hat(hp(1,1,2),upH,fwd,hs);
    }
    static void faceQuad(float[] a,float[] b,float[] c,float[] d,int tile,float u0,float v0,float u1,float v1){
        float[] n=norm(cross(sub(b,a),sub(d,a)));quad(a,b,c,d,n,n,n,n,tile,u0,v0,u1,v1);
    }
    /** Hood: a second, larger subdivided cube around the head, open at the front, its bottom reaching the shoulders; a deep hood has a peak at the back. */
    private static void hood(float[] c,float pitch,float hx,float hy,float hz,boolean peak,int tile){
        boolean noTop=tile<0;if(noTop)tile=-tile-1;   // long hair: the head's own hair already covers the top
        float[][][][] P=new float[3][3][3][];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int k=0;k<3;k++){float x=i-1,y=j-1,z=k-1;
            if(k==2){boolean cx=Math.abs(x)==1,cy=Math.abs(y)==1;if(cx&&cy){z=.62f;x*=.88f;y*=.88f;}else if(cx||cy){z=.9f;}else z=1.04f;}
            if(k==0){z=-1.32f;y*=1.12f;}
            if(i==2){x=.92f;if(k==1)y*=1.02f;}   // open front sits just ahead of the face
            float[] p=v(x*hx*1.22f-.012f,y*hy*1.26f,z*hz*1.16f+.006f);
            if(peak&&i==0&&j==1&&k==2){p=add(p,v(-.08f,0,.06f));}
            P[i][j][k]=place(c,pitch,p);}
        for(int a=0;a<2;a++)for(int d=0;d<2;d++){
            faceQuad(P[0][a+1][d+1],P[0][a][d+1],P[0][a][d],P[0][a+1][d],tile,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);
            faceQuad(P[a][2][d+1],P[a+1][2][d+1],P[a+1][2][d],P[a][2][d],tile,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);
            faceQuad(P[a+1][0][d+1],P[a][0][d+1],P[a][0][d],P[a+1][0][d],tile,a/2f,1-(d+1)/2f,(a+1)/2f,1-d/2f);
            if(!noTop)faceQuad(P[a][d][2],P[a][d+1][2],P[a+1][d+1][2],P[a+1][d][2],tile,a/2f,d/2f,(a+1)/2f,(d+1)/2f);}
    }
    /** Wizard hat: a flat 8-sided brim and a 6-sided cone with one loop cut, bent back toward the tip. */
    private static void hat(float[] crown,float[] up,float[] fwd,float hs){
        float[] side=norm(cross(up,fwd));float[] base=add(crown,up,-.03f);
        float[][] Br=new float[8][];for(int i=0;i<8;i++){double a=i*Math.PI/4;Br[i]=add(add(base,fwd,(float)Math.cos(a)*.19f*hs),side,(float)Math.sin(a)*.19f*hs);}
        cap(Br,base,up,.0001f,CLOAK,false);cap(Br,base,mul(up,-1),.0001f,CLOAK,true);
        float[][] R0=new float[6][],R1=new float[6][],N=new float[6][];float[] mid=add(add(base,up,.15f),fwd,-.03f),tip=add(add(base,up,.32f),fwd,-.11f);
        for(int i=0;i<6;i++){double a=i*Math.PI/3;float[] d=add(mul(fwd,(float)Math.cos(a)),side,(float)Math.sin(a));N[i]=d;R0[i]=add(base,d,.105f*hs);R1[i]=add(mid,d,.06f*hs);}
        for(int i=0;i<6;i++){int j=(i+1)%6;quad(R0[i],R0[j],R1[j],R1[i],N[i],N[j],N[j],N[i],CLOAK,i/6f,.5f,(i+1)/6f,1);}
        cap(R1,mid,norm(sub(tip,mid)),len(sub(tip,mid)),CLOAK,false);
        // gold band around the cone, a ring of flat quads
        float[][] T0=new float[6][],T1=new float[6][];float[] b1=add(base,up,.03f);
        for(int i=0;i<6;i++){double a=i*Math.PI/3;float[] d=add(mul(fwd,(float)Math.cos(a)),side,(float)Math.sin(a));T0[i]=add(base,d,.108f*hs);T1[i]=add(b1,d,.1f*hs);}
        for(int i=0;i<6;i++){int j=(i+1)%6;quad(T0[i],T0[j],T1[j],T1[i],N[i],N[j],N[j],N[i],TRIM,i/6f,0,(i+1)/6f,1);}
    }

    // ------------------------------------------------------------------ gear
    private static void weapon(Spec sp,SpriteModels.Pose ps,int f,float[] rh,float[] lh,float[] wd){
        switch(sp.weapon){
            case W_SWORD_SHIELD:sword(rh,wd,.6f,true);shield(add(lh,v(.05f,-.03f,.02f)),norm(v(1,-.25f,0)),TRIM,WOOD);break;
            case W_MACE_SHIELD:mace(rh,wd);shield(add(lh,v(.05f,-.03f,.02f)),norm(v(1,-.25f,0)),TRIM,X1);break;
            case W_RUSTY:sword(rh,wd,.55f,false);break;
            case W_CRUDE:sword(rh,wd,.42f,false);break;
            case W_DAGGERS:if(ps.dead){sword(rh,wd,.22f,true);sword(lh,norm(v(.1f,-.3f,1)),.22f,true);break;}
                sword(rh,ps.attack?norm(v(.9f,-.2f,-.1f)):norm(v(.6f,.1f,.75f)),.22f,true);sword(lh,ps.attack?norm(v(.4f,-.2f,.9f)):norm(v(.6f,-.1f,-.75f)),.22f,true);break;
            case W_BOW:{if(ps.dead)bow(rh,v(0,0,1),v(-1,0,0));
                else if(ps.attack)bow(lh,v(0,0,1),v(1,0,0));else bow(lh,norm(v(.25f,0,1)),norm(v(1,0,-.25f)));
                int at=nv;{float[] a0=add(lh,v(1,0,0),.08f),a1=rh;float w=.006f;   // the drawn arrow: two crossed flat strips
                    quad(add(a0,v(0,0,w)),add(a1,v(0,0,w)),add(a1,v(0,0,-w)),add(a0,v(0,0,-w)),v(0,1,0),v(0,1,0),v(0,1,0),v(0,1,0),WOOD,0,0,1,1);
                    quad(add(a0,v(0,w,0)),add(a1,v(0,w,0)),add(a1,v(0,-w,0)),add(a0,v(0,-w,0)),v(0,0,1),v(0,0,1),v(0,0,1),v(0,0,1),WOOD,0,0,1,1);}
                if(!ps.attack)collapse(at,rh);break;}
            case W_STAFF:{float[] a=add(rh,wd,-.85f),t=add(rh,wd,.5f);seg(a,t,.017f,.017f,.014f,.014f,.008f,0,WOOD,v(1,0,0));
                seg(t,add(t,wd,.05f),.03f,.03f,.024f,.024f,0,0,WOOD,v(1,0,0));   // claw that holds the orb
                octa(add(t,wd,.1f),ps.attack?.075f:.05f,GLOW);break;}
            case W_AXE:axe(rh,wd);break;
            case W_CAST:{float[] fc=add(SpriteModels.lerp(rh,lh,.5f),v(.09f,0,.02f));int at=nv;octa(fc,.085f,GLOW);octa(add(fc,v(.03f,0,0)),.05f,X2);
                if(!ps.attack)collapse(at,fc);break;}
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
        seg(base,gTop,.014f,.014f,.014f,.014f,.008f,0,WOOD,v(0,0,1));                                                  // grip
        float[] side=norm(cross(dir,v(0,0,1)));if(len(cross(dir,v(0,0,1)))<1e-3f)side=v(0,1,0);
        if(guard){float gw=blade<.3f?.05f:.1f;seg(add(gTop,side,-gw),add(gTop,side,gw),.016f,.016f,.016f,.016f,0,0,TRIM,dir);   // cross-guard
            if(blade>=.3f)seg(add(base,dir,-.025f),base,.022f,.022f,.022f,.022f,.012f,0,TRIM,v(0,0,1));}             // pommel (not on daggers)
        float bw=blade<.3f?.02f:.028f;
        seg(add(gTop,dir,.012f),add(gTop,dir,blade),bw,.006f,bw*.85f,.006f,0,blade<.3f?.04f:.07f,STEEL,side);          // blade, pyramid point
    }
    /** Mace: a wooden haft and a flanged steel head (a box with pyramid ends both ways). */
    private static void mace(float[] hand,float[] dir){
        float[] a=add(hand,dir,-.1f),t=add(hand,dir,.36f);
        seg(a,t,.015f,.015f,.015f,.015f,.01f,0,WOOD,v(0,0,1));
        seg(t,add(t,dir,.12f),.045f,.045f,.045f,.045f,.03f,.04f,STEEL,v(0,0,1));
        float[] side=norm(cross(dir,v(0,0,1)));if(len(cross(dir,v(0,0,1)))<1e-3f)side=v(0,1,0);
    }
    /** Greataxe: long haft, two crescent bits made of flattened boxes, a spike on top. */
    private static void axe(float[] hand,float[] dir){
        float[] a=add(hand,dir,-.42f),t=add(hand,dir,.62f);
        seg(a,t,.018f,.018f,.016f,.016f,.012f,0,WOOD,v(0,0,1));
        float[] side=norm(cross(dir,v(0,0,1)));if(len(cross(dir,v(0,0,1)))<1e-3f)side=v(0,1,0);
        float[] hd=add(t,dir,-.12f);
        for(int s=-1;s<=1;s+=2)seg(add(hd,side,s*.015f),add(hd,side,s*.2f),.008f,.06f,.006f,.13f,0,0,STEEL,dir);
        seg(t,add(t,dir,.1f),.016f,.016f,.004f,.004f,0,.02f,TRIM,side);
    }
    /** Round shield: an 8-sided disc, the front fan's centre pushed out into a boss. */
    private static void shield(float[] c,float[] facing,int rim,int face){
        float[][] sf=across(facing,v(0,0,1));float[] s=sf[0],u=sf[1];float R=.21f,T=.018f;
        float[] front=add(c,facing,T),back=add(c,facing,-T);float[][] F=new float[8][],B=new float[8][],N=new float[8][];
        for(int i=0;i<8;i++){double a=i*Math.PI/4+Math.PI/8;float[] d=add(mul(s,(float)Math.cos(a)),u,(float)Math.sin(a));N[i]=d;F[i]=add(front,d,R);B[i]=add(back,d,R);}
        for(int i=0;i<8;i++){int j=(i+1)%8;quad(F[i],F[j],B[j],B[i],N[i],N[j],N[j],N[i],rim,i/8f,0,(i+1)/8f,1);}
        cap(F,front,facing,.05f,face,false);cap(B,back,mul(facing,-1),0,WOOD,true);
    }
    /** Longbow: four tapering boxes bent back at the tips, a grip, and the string as a thin flat strip. */
    private static void bow(float[] hand,float[] up,float[] fwd){
        float[] top=add(add(hand,up,.48f),fwd,-.1f),bot=add(add(hand,up,-.48f),fwd,-.1f),mt=add(hand,up,.24f),mb=add(hand,up,-.24f);
        chain(new float[][]{bot,mb,hand,mt,top},new float[]{.006f,.012f,.016f,.012f,.006f},.006f,WOOD,fwd);   // one bent stave, grip painted on
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
    /** The face, painted on the flat front of the head. */
    private static void paintFace(MeshModels.Mesh m){
        Spec sp=S;int Sz=G*MeshModels.TILE,T=MeshModels.TILE,ox=(FACE%G)*T,oy=(FACE/G)*T;int[] px=m.color;
        for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*Sz+ox+x;int c=px[o];int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            float u=x/(float)T,v=y/(float)T,cu=Math.abs(u-.5f);float n=hash(x,y);
            switch(sp.face){
                case F_SKULL:{   // dark sockets, nose hole, a row of teeth, cracks
                    for(int s=-1;s<=1;s+=2){float ex=.5f+s*.2f,dx=(u-ex)/.12f,dy=(v-.42f)/.1f;if(dx*dx+dy*dy<1){float e=dx*dx+dy*dy;r=g=b=(int)(18+e*30);}}
                    if(v>.55f&&v<.68f&&cu<(.68f-v)*.5f+.01f){r=22;g=18;b=14;}
                    if(v>.76f&&v<.88f&&cu<.17f){boolean gap=((int)(u*24))%2==0;if(gap||Math.abs(v-.82f)<.012f){r=(int)(r*.35f);g=(int)(g*.33f);b=(int)(b*.3f);}}
                    if(Math.abs(u-.3f-v*.15f)<.012f&&v<.3f){r=(int)(r*.55f);g=(int)(g*.5f);b=(int)(b*.45f);}
                    break;}
                case F_SHADOW:{float d=(float)Math.sqrt(cu*cu+(v-.5f)*(v-.5f));int k=(int)(6+d*30+n*6);r=k+2;g=k;b=k;break;}
                case F_GOBLIN:{
                    for(int s=-1;s<=1;s+=2){float ex=.5f+s*.2f;
                        if(Math.abs(v-(.33f+s*0)+(u-ex)*s*.4f)<.03f&&Math.abs(u-ex)<.13f){r=28;g=34;b=14;}                       // angry slanted brows
                        if(Math.abs(v-.43f)<.045f&&Math.abs(u-ex)<.08f){r=230;g=200;b=40;if(Math.abs(u-ex)<.018f){r=20;g=10;b=0;}}}
                    if(Math.abs(v-.76f-cu*cu*1.6f)<.035f&&cu<.3f){r=40;g=18;b=12;if(((int)(u*20))%3==0&&v<.77f+cu*cu*1.6f){r=232;g=224;b=192;}}   // wide toothy grin
                    if(n>.985f){r=(int)(r*.7f);g=(int)(g*.8f);b=(int)(b*.6f);}                                                      // warts
                    break;}
                case F_ORC:{
                    if(v>.26f&&v<.36f&&cu<.42f){r=(int)(r*.55f);g=(int)(g*.55f);b=(int)(b*.5f);}                                 // heavy brow
                    for(int s=-1;s<=1;s+=2){float ex=.5f+s*.19f;if(Math.abs(v-.43f)<.03f&&Math.abs(u-ex)<.06f){r=255;g=80;b=30;if(Math.abs(u-ex)<.015f){r=40;g=0;b=0;}}
                        for(int k=0;k<3;k++)if(Math.abs(u-(ex+s*(.04f+k*.05f)))<.014f&&v>.5f&&v<.66f){r=24;g=24;b=28;}}         // war paint stripes
                    if(u>.42f&&u<.58f&&v>.48f&&v<.64f){r=(int)(r*.75f);g=(int)(g*.75f);b=(int)(b*.7f);}                          // flat nose
                    if(Math.abs(v-.78f)<.025f&&cu<.22f){r=30;g=14;b=10;}
                    for(int s=-1;s<=1;s+=2)if(Math.abs(u-(.5f+s*.15f))<.025f&&v>.67f&&v<.79f){r=238;g=230;b=204;}                // tusks drawn on the lip
                    break;}
                default:{   // human
                    if(sp.snout){   // dragonborn snout tip: nostrils, a long mouth line with teeth, scales
                        if(((x/6+y/5)&1)==0&&(x%6==0||y%5==0)){r=r*7/10;g=g*7/10;b=b*7/10;}
                        for(int s2=-1;s2<=1;s2+=2)if(Math.abs(u-(.5f+s2*.16f))<.05f&&Math.abs(v-.3f)<.04f){r=20;g=14;b=10;}
                        if(Math.abs(v-.72f)<.02f&&cu<.42f){r=30;g=12;b=10;}
                        if(v>.74f&&v<.79f&&cu<.38f&&((int)(u*22))%2==0){r=232;g=226;b=200;}
                        break;}
                    int hr=(sp.hairRGB>>16)&255,hg=(sp.hairRGB>>8)&255,hb=sp.hairRGB&255;
                    if(sp.hairline&&v<.2f){float k=1-n*.15f;r=(int)(hr*k);g=(int)(hg*k);b=(int)(hb*k);}
                    float eyeY=.43f;int ir=(sp.iris>>16)&255,ig=(sp.iris>>8)&255,ib=sp.iris&255;
                    for(int s2=-1;s2<=1;s2+=2){float ex=.5f+s2*.19f;
                        if(Math.abs(v-(eyeY-.075f))<.022f&&Math.abs(u-ex)<.1f){r=(int)(hr*.9f);g=(int)(hg*.9f);b=(int)(hb*.9f);}           // brows
                        if(sp.heavyBrow&&Math.abs(v-(eyeY-.07f))<.04f&&Math.abs(u-ex)<.13f){r=(int)(r*.6f);g=(int)(g*.6f);b=(int)(b*.6f);}
                        if(Math.abs(v-eyeY)<.035f&&Math.abs(u-ex)<.075f){
                            if(sp.solidEyes){r=ir;g=ig;b=ib;if(Math.abs(u-ex)<.008f){r=40;g=10;b=10;}}   // tiefling: solid gold eyes, slit pupils
                            else{r=235;g=230;b=222;if(Math.abs(u-ex)<.035f){r=ir;g=ig;b=ib;if(Math.abs(u-ex)<.015f){r=12;g=12;b=12;}}}}
                        if(sp.female&&Math.abs(v-(eyeY-.042f))<.014f&&Math.abs(u-ex)<.085f&&(u-ex)*s2>-.06f){r=22;g=16;b=14;}}   // lashes
                    if(u>.45f&&u<.55f&&v>.45f&&v<.66f){r=(int)(r*.82f);g=(int)(g*.8f);b=(int)(b*.8f);}                          // nose shadow
                    if(v>.63f&&v<.67f&&u>.44f&&u<.56f){r=(int)(r*.7f);g=(int)(g*.68f);b=(int)(b*.68f);}
                    if(Math.abs(v-.77f)<(sp.female?.026f:.018f)&&cu<(sp.female?.1f:.11f)){r=sp.female?150:118;g=sp.female?60:52;b=sp.female?64:48;}           // mouth
                    if(sp.stubble&&v>.7f&&(u<.2f||u>.8f||v>.86f)){r=(int)(r*.55f);g=(int)(g*.5f);b=(int)(b*.45f);}               // stubble along the jaw
                    if(sp.beard){int br=(sp.beardRGB>>16)&255,bg=(sp.beardRGB>>8)&255,bb=sp.beardRGB&255;
                        boolean mouth=Math.abs(v-.77f)<.018f&&cu<.09f;
                        if(!mouth&&(v>.68f||(v>.6f&&cu>.18f)||(v>.62f&&v<.71f&&cu>.04f&&cu<.16f))){float k=.85f+n*.2f-(((x+y)&3)==0?.12f:0);r=(int)(br*k);g=(int)(bg*k);b=(int)(bb*k);}}
                    if(sp.mask&&v>.55f){int mr=(sp.maskRGB>>16)&255;r=mr+(int)(n*8);g=r;b=r+3;}
                }
            }
            px[o]=rgb(r,g,b);}
    }
    /** Detail painted instead of modelled: ribs, a holy sun, belts, dragon scales. */
    private static void paintExtra(MeshModels.Mesh m,int id){
        int Sz=G*MeshModels.TILE,T=MeshModels.TILE;int[] px=m.color;
        if(S.snout)for(int t:new int[]{SKIN,HANDS}){if(t==HANDS&&S.weapon!=W_STAFF)continue;int ox=(t%G)*T,oy=(t/G)*T;
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){int row=y/5,cx=(x+(row&1)*3)%6,cy=y%5;if(cy==0||cx==0){int o=(oy+y)*Sz+ox+x,c=px[o];px[o]=rgb(((c>>16)&255)*7/10,((c>>8)&255)*7/10,(c&255)*7/10);}}}
        if(id==Sprites.SKELETON){int ox=(TORSO%G)*T,oy=(TORSO/G)*T;
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*Sz+ox+x;int c=px[o];float u=(x%(T/4))/(float)(T/4),v=y/(float)T;
                boolean rib=((int)(v*9))%2==0,spine=Math.abs(u-.5f)<.08f&&x/(T/4)%2==0;
                if(!rib&&!spine){px[o]=rgb(20,16,12);}else if(Math.abs((v*9)%1-.5f)>.38f&&!spine){px[o]=rgb(((c>>16)&255)*6/10,((c>>8)&255)*6/10,(c&255)*6/10);}}}
        if(id==Sprites.HERO_CLERIC){int ox=(X1%G)*T,oy=(X1/G)*T;   // shield face: white field with a gold sun
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*Sz+ox+x;float dx=x-T/2f,dy=y-T/2f,d=(float)Math.sqrt(dx*dx+dy*dy);double a=Math.atan2(dy,dx);
                boolean ray=d<T*.4f&&Math.abs(Math.sin(a*6))>.82f,sun=d<T*.16f;if(sun||ray)px[o]=rgb(214,172,64);}}
        // a belt across the waist of every tunic/torso (top band of the pelvis tile)
        if(id!=Sprites.SKELETON&&id!=Sprites.CULTIST&&id!=Sprites.HERO_WIZARD){int ox=(PELVIS%G)*T,oy=(PELVIS/G)*T;
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){float v=y/(float)T;if(v>.82f){int o=(oy+y)*Sz+ox+x;boolean buckle=((x%(T/4))>T/8-3&&(x%(T/4))<T/8+3)&&x/(T/4)==0;px[o]=buckle?rgb(184,154,80):rgb(58,38,22);}}}
    }
}
