package com.retro.doomeditor;

import static com.retro.doomeditor.SpriteModels.v;
import static com.retro.doomeditor.SpriteModels.add;
import static com.retro.doomeditor.SpriteModels.norm;

/**
 * Built-in objects box-modelled by hand like PS1 props, using the same kit as the characters (BoxMesh): boxes,
 * tapered boxes with pyramid ends, n-sided cylinders with fanned caps, flat sheets for cloth and 8-triangle diamonds
 * for tiny details. Detail (planks, book spines, quilt patches, stone blocks, heraldry) is painted into the colour
 * texture instead of modelled. Fire is two nested 5-sided cones on a full-bright texture that flicker between
 * frames 0 and 1; the game adds flame particles on top.
 * Model space: x = forward, y = right, z = up, 1 unit = 1 grid square, standing on z = 0, centred on the origin.
 */
final class PropMesh {
    private PropMesh(){}

    static boolean has(int id){
        switch(id){
            case Sprites.TORCH:case Sprites.BRAZIER:case Sprites.BARREL:case Sprites.PILLAR:case Sprites.LEVER:case Sprites.LEVER_ON:case Sprites.CHEST:case Sprites.CHEST_OPEN:return true;
            default:return id>=Sprites.CAMPFIRE&&id<Sprites.COUNT;
        }
    }
    /** Objects with a fire: they flicker, light up automatically and give off flames. */
    static boolean fire(int id){return id==Sprites.TORCH||id==Sprites.BRAZIER||id==Sprites.CAMPFIRE||id==Sprites.FIREPLACE||id==Sprites.CANDELABRA||id==Sprites.CANDLE;}
    /** Height of the flames above the floor, and how big the fire is (0 = candle-sized). */
    static float flameZ(int id){switch(id){case Sprites.TORCH:return 1.12f;case Sprites.BRAZIER:return .95f;case Sprites.CAMPFIRE:return .12f;case Sprites.FIREPLACE:return .16f;case Sprites.CANDELABRA:return 1.3f;case Sprites.CANDLE:return .2f;default:return -1;}}
    static boolean bigFire(int id){return id==Sprites.CAMPFIRE||id==Sprites.FIREPLACE||id==Sprites.BRAZIER;}

    static MeshModels.Mesh build(int id){synchronized(BoxMesh.class){return make(id);}}
    private static MeshModels.Mesh make(int id){
        SpriteModels.Model m=new SpriteModels.Model();mats(id,m);
        MeshModels.Mesh out=new MeshModels.Mesh();out.atlas=BoxMesh.atlasFor(m);
        for(int f=0;f<Sprites.FRAMES;f++){BoxMesh.begin(m);shape(id,f);out.frames[f]=BoxMesh.end();}
        int Sz=out.atlas*MeshModels.TILE;int[] done=TexBake.load("prop_"+id,Sz*Sz);
        if(done!=null)out.color=done;else{MeshModels.paint(out,m,out.atlas);paint(id,out);TexBake.bake(out,m,false);TexBake.save("prop_"+id,out.color);}
        MeshModels.pack(out);
        return out;
    }

    // ------------------------------------------------------------------ materials (tile = order added)
    // shared slots for fire
    private static final int FLAME=14,FLAME_CORE=15;
    private static void fireMats(SpriteModels.Model m){while(m.mats.size()<14)m.cloth(0xff808080);m.glow(0xffff7a1a);m.glow(0xffffd860);}
    private static void mats(int id,SpriteModels.Model m){
        switch(id){
            case Sprites.TORCH:m.wood(0xff5a3c22);m.metal(0xff3c3c40);m.cloth(0xff6a5440);break;                       // 0 pole 1 iron 2 pitch-soaked rag
            case Sprites.BRAZIER:m.metal(0xff3a3836);m.glow(0xffb03010);m.metal(0xff5a5450);break;                     // 0 iron 1 coals 2 rim
            case Sprites.CAMPFIRE:m.mat(0xff7a7670,.1f,.45f);m.wood(0xff5a3a20);m.glow(0xffa02808);m.mat(0xff3a3430,.05f,.3f);break;   // 0 stone 1 logs 2 embers 3 ash
            case Sprites.FIREPLACE:m.mat(0xff8a8278,.1f,.45f);m.mat(0xff1a1412,.05f,.2f);m.wood(0xff6a4428);m.wood(0xff5a3a20);m.glow(0xffa02808);m.mat(0xff6a6258,.1f,.45f);break;   // 0 stone 1 soot 2 mantel 3 logs 4 embers 5 hearth
            case Sprites.BARREL:m.wood(0xff7a5232);m.metal(0xff3e3c3a);m.wood(0xff6a4628);break;
            case Sprites.PILLAR:m.mat(0xff8a8680,.15f,.45f);m.mat(0xff6a6660,.1f,.4f);break;
            case Sprites.LEVER:case Sprites.LEVER_ON:m.mat(0xff7a7670,.1f,.45f);m.wood(0xff6a4428);m.metal(0xff4a4c50);m.mat(0xff2a2826,.05f,.3f);break;
            case Sprites.CHEST:case Sprites.CHEST_OPEN:m.wood(0xff6a4024);m.metal(0xff3e3c3a);m.metal(0xffe0b040);m.mat(0xff1a1210,.05f,.2f);break;
            case Sprites.BED:m.wood(0xff6a4428);m.cloth(0xffd8ccb0);m.cloth(0xff8a2a2a);m.cloth(0xffe8e0d0);m.wood(0xff5a3a20);break;   // 0 frame 1 mattress 2 quilt 3 pillow 4 posts
            case Sprites.CABINET:case Sprites.WARDROBE:case Sprites.DRESSER:m.wood(0xff6a4428);m.wood(0xff7a5232);m.metal(0xffb08a40);m.wood(0xff5a3a20);break;   // 0 carcass 1 doors 2 handles 3 trim
            case Sprites.TABLE:case Sprites.CHAIR:case Sprites.STOOL:case Sprites.BENCH:m.wood(0xff7a5232);m.wood(0xff6a4428);break;   // 0 top 1 legs
            case Sprites.BOOKSHELF:m.wood(0xff6a4428);m.wood(0xff4a2e18);m.cloth(0xff7a2a2a);m.cloth(0xff2a4a6a);m.cloth(0xff3a5a2a);break;   // 0 carcass 1 back 2-4 books
            case Sprites.CRATE:m.wood(0xff9a7448);m.wood(0xff7a5a34);break;
            case Sprites.CANDELABRA:case Sprites.CANDLE:m.metal(0xff3c3a38);m.mat(0xffeee4cc,.2f,.15f);m.metal(0xffb08a40);break;   // 0 iron 1 wax 2 brass
            case Sprites.CAULDRON:m.metal(0xff2a2a2c);m.glow(0xff58c040);m.wood(0xff5a3a20);m.glow(0xffa02808);break;   // 0 iron 1 brew 2 logs 3 embers
            case Sprites.RUG:m.cloth(0xff8a2a22);break;
            case Sprites.BANNER:m.wood(0xff5a3a20);m.cloth(0xff2c4a8a);m.metal(0xffb08a40);m.mat(0xff6a6660,.1f,.4f);break;   // 0 pole 1 cloth 2 finials 3 stone base
            case Sprites.PLANT:m.mat(0xffa85a34,.1f,.35f);m.mat(0xff3a2a1c,.05f,.4f);m.cloth(0xff3a7a2a);m.cloth(0xff2a5a20);break;   // 0 clay 1 soil 2-3 leaves
            case Sprites.WEAPON_RACK:m.wood(0xff6a4428);m.metal(0xffb8c0c8);m.leather(0xff3a2416);m.metal(0xffb08a40);break;
            case Sprites.SACK:m.cloth(0xffb8a478);m.cloth(0xff6a5a3a);m.cloth(0xffd8c890);break;   // 0 hessian 1 rope 2 grain
            default:m.cloth(0xff808080);
        }
        if(fire(id))fireMats(m);
    }

    // ------------------------------------------------------------------ toolkit
    /** Axis-aligned box from (x0,y0,z0) to (x1,y1,z1): six faces, each showing the whole texture. */
    private static void cube(float x0,float y0,float z0,float x1,float y1,float z1,int side,int top){
        float[] a=v(x0,y0,z0),b=v(x1,y0,z0),c=v(x1,y1,z0),d=v(x0,y1,z0),e=v(x0,y0,z1),f=v(x1,y0,z1),g=v(x1,y1,z1),h=v(x0,y1,z1);
        face(f,g,c,b,v(1,0,0),side);face(h,e,a,d,v(-1,0,0),side);face(g,h,d,c,v(0,1,0),side);face(e,f,b,a,v(0,-1,0),side);
        face(e,h,g,f,v(0,0,1),top);face(b,c,d,a,v(0,0,-1),side);
    }
    /** Box from the front face only: a door, drawer front or panel, slightly proud of the carcass. */
    private static void face(float[] a,float[] b,float[] c,float[] d,float[] n,int tile){BoxMesh.quad(a,b,c,d,n,n,n,n,tile,0,0,1,1);}
    private static void sheet(float[] a,float[] b,float[] c,float[] d,int tile){float[] n=SpriteModels.cross(SpriteModels.sub(b,a),SpriteModels.sub(d,a));face(a,b,c,d,n,tile);}
    /** Vertical square post. */
    private static void post(float x,float y,float z0,float z1,float hw,int tile){BoxMesh.seg(v(x,y,z0),v(x,y,z1),hw,hw,hw,hw,0,0,tile,v(1,0,0));}
    /** Tapered beam from a to b with pyramid ends. */
    private static void beam(float[] a,float[] b,float w,float d,float dome,int tile){BoxMesh.seg(a,b,w,d,w,d,dome,dome,tile,v(0,0,1));}
    /** Two nested 5-sided cones, taller and leaning on odd frames (flicker). */
    private static void flame(float[] base,float h,float r,int f){
        boolean odd=(f&1)==1;float hh=h*(odd?1.18f:.94f);float[] tip=add(base,v(odd?.015f:-.01f,odd?-.01f:.012f,hh));
        BoxMesh.cylN(base,tip,r,r*.25f,5,r*.3f,r*.6f,FLAME);
        float[] tip2=add(base,v(odd?.008f:-.004f,0,hh*.62f));BoxMesh.cylN(add(base,v(.004f,0,0)),tip2,r*.62f,r*.12f,5,0,r*.4f,FLAME_CORE);
    }
    private static float c(double a){return (float)Math.cos(a);}
    private static float s(double a){return (float)Math.sin(a);}

    // ------------------------------------------------------------------ the props
    private static void shape(int id,int f){
        switch(id){
            case Sprites.TORCH:{   // standing torch: iron foot, wooden pole, iron cup, pitch-soaked rag head, flame
                for(int k=0;k<3;k++){double a=k*2.094+.5;beam(v(0,0,.18f),v(c(a)*.22f,s(a)*.22f,0),.018f,.018f,.008f,1);}
                BoxMesh.cylN(v(0,0,0),v(0,0,1.0f),.028f,.022f,5,.01f,0,0);
                BoxMesh.cylN(v(0,0,.97f),v(0,0,1.06f),.035f,.06f,6,.01f,-1,1);
                BoxMesh.cylN(v(0,0,1.0f),v(0,0,1.1f),.042f,.036f,6,0,.02f,2);
                flame(v(0,0,1.08f),.24f,.075f,f);break;}
            case Sprites.BRAZIER:{   // iron tripod, open bowl with glowing coals and a big fire
                for(int k=0;k<3;k++){double a=k*2.094;beam(v(c(a)*.28f,s(a)*.28f,0),v(c(a)*.14f,s(a)*.14f,.72f),.02f,.02f,.01f,0);}
                BoxMesh.cylN(v(0,0,.66f),v(0,0,.86f),.15f,.27f,8,.02f,-1,0);
                BoxMesh.cylN(v(0,0,.85f),v(0,0,.88f),.28f,.28f,8,-1,-1,2);
                BoxMesh.cylN(v(0,0,.8f),v(0,0,.86f),.24f,.25f,8,-1,.03f,1);
                for(int k=0;k<5;k++){double a=k*1.257;BoxMesh.octa(v(c(a)*.13f,s(a)*.13f,.88f),.045f,1);}
                flame(v(.02f,-.03f,.86f),.34f,.11f,f);flame(v(-.06f,.07f,.86f),.24f,.08f,f+1);break;}
            case Sprites.CAMPFIRE:{   // ring of stones, crossed logs, embers and flames
                for(int k=0;k<8;k++){double a=k*Math.PI/4;float x=c(a)*.42f,y=s(a)*.42f;
                    BoxMesh.seg(v(x,y,0),v(x,y,.09f+(k%3)*.015f),.07f,.06f,.055f,.05f,0,.03f,0,v(c(a),s(a),0));}
                BoxMesh.cylN(v(0,0,0),v(0,0,.015f),.36f,.34f,8,-1,0,3);
                for(int k=0;k<4;k++){double a=k*Math.PI/2+.4;beam(v(c(a)*.32f,s(a)*.32f,.03f),v(c(a)*-.04f,s(a)*-.04f,.16f),.035f,.035f,.02f,1);}
                for(int k=0;k<4;k++){double a=k*1.57+.8;BoxMesh.octa(v(c(a)*.12f,s(a)*.12f,.04f),.05f,2);}
                flame(v(0,0,.06f),.42f,.13f,f);flame(v(.08f,.06f,.05f),.28f,.08f,f+1);flame(v(-.07f,-.06f,.05f),.3f,.08f,f);break;}
            case Sprites.FIREPLACE:{   // stone hearth against a wall (the back is at -x), mantel, chimney breast, fire in the firebox
                float bx=-.42f;
                cube(-.3f,-.75f,0,.22f,.75f,.06f,5,5);                                 // hearth slab
                cube(bx,-.7f,0,-.3f,.7f,1.9f,0,0);                                     // back wall
                cube(-.3f,-.7f,.06f,.12f,-.42f,.95f,0,0);cube(-.3f,.42f,.06f,.12f,.7f,.95f,0,0);   // side jambs
                cube(-.3f,-.42f,.06f,-.24f,.42f,.8f,1,1);                              // sooty back of the firebox
                cube(-.3f,-.42f,.8f,.1f,.42f,.95f,0,0);                                // lintel
                cube(-.32f,-.8f,.95f,.2f,.8f,1.03f,2,2);                               // wooden mantel shelf
                cube(-.3f,-.6f,1.03f,.0f,.6f,1.9f,0,0);                                // chimney breast
                beam(v(-.05f,-.24f,.1f),v(-.12f,.24f,.1f),.035f,.035f,.02f,3);beam(v(-.14f,-.22f,.12f),v(.0f,.2f,.15f),.033f,.033f,.02f,3);
                BoxMesh.octa(v(-.08f,0,.08f),.06f,4);
                flame(v(-.08f,-.04f,.12f),.36f,.12f,f);flame(v(-.1f,.13f,.12f),.24f,.08f,f+1);flame(v(-.06f,-.18f,.12f),.22f,.07f,f);break;}
            case Sprites.BARREL:{   // 8 sides, loop cuts bulging the middle, iron hoops, lid
                float[] zs={0,.18f,.4f,.62f,.8f},rs={.25f,.29f,.31f,.29f,.25f};
                for(int k=0;k<4;k++)BoxMesh.cylN(v(0,0,zs[k]),v(0,0,zs[k+1]),rs[k],rs[k+1],8,k==0?0:-1,k==3?0:-1,0);
                for(float hz:new float[]{.08f,.7f})BoxMesh.cylN(v(0,0,hz),v(0,0,hz+.05f),.285f,.285f,8,-1,-1,1);
                BoxMesh.cylN(v(0,0,.31f),v(0,0,.36f),.322f,.322f,8,-1,-1,1);
                BoxMesh.cylN(v(0,0,.79f),v(0,0,.8f),.23f,.23f,8,-1,0,2);break;}
            case Sprites.PILLAR:{   // plinth, 8-sided shaft with a loop cut, capital
                cube(-.3f,-.3f,0,.3f,.3f,.14f,1,1);cube(-.25f,-.25f,.14f,.25f,.25f,.22f,0,0);
                BoxMesh.cylN(v(0,0,.22f),v(0,0,.9f),.2f,.19f,8,-1,-1,0);BoxMesh.cylN(v(0,0,.9f),v(0,0,1.42f),.19f,.18f,8,-1,-1,0);
                cube(-.25f,-.25f,1.42f,.25f,.25f,1.5f,0,0);cube(-.3f,-.3f,1.5f,.3f,.3f,1.62f,1,1);break;}
            case Sprites.LEVER:case Sprites.LEVER_ON:{   // stone block with a slot and a wooden handle (forward = pulled)
                cube(-.2f,-.18f,0,.2f,.18f,.42f,0,0);cube(-.12f,-.05f,.42f,.12f,.05f,.425f,3,3);
                float a=id==Sprites.LEVER_ON?.7f:-.7f;float[] piv=v(0,0,.42f),tip=v(s(a)*.42f,0,.42f+c(a)*.42f);
                beam(piv,tip,.02f,.02f,.01f,1);BoxMesh.cylN(tip,add(tip,v(s(a)*.08f,0,c(a)*.08f)),.035f,.03f,6,.01f,.02f,2);
                beam(v(0,-.07f,.42f),v(0,.07f,.42f),.022f,.022f,.01f,2);break;}
            case Sprites.CHEST:case Sprites.CHEST_OPEN:{   // wooden body, iron bands and corners, curved lid (6-sided half), gold lock
                cube(-.25f,-.38f,0,.25f,.38f,.32f,0,0);
                for(float y:new float[]{-.3f,.3f})cube(-.26f,y-.025f,0,.26f,y+.025f,.325f,1,1);
                boolean open=id==Sprites.CHEST_OPEN;float[] hinge=v(-.25f,0,.32f);
                float[][] P=new float[4][];for(int k=0;k<4;k++){double a=k*Math.PI/3;float lx=-.25f+(1-c(a))*.25f,lz=s(a)*.14f;
                    if(open){float dx=lx+.25f,dz=lz;float ang=1.9f;lx=-.25f+dx*c(ang)-dz*s(ang);lz=dx*s(ang)+dz*c(ang);}P[k]=v(lx,0,.32f+lz);}
                for(int k=0;k<3;k++){float[] a=P[k],b=P[k+1];sheet(v(a[0],-.38f,a[2]),v(a[0],.38f,a[2]),v(b[0],.38f,b[2]),v(b[0],-.38f,b[2]),0);}
                for(float y:new float[]{-.38f,.38f}){for(int k=0;k<2;k++)sheet(v(hinge[0],y,hinge[2]),v(P[k][0],y,P[k][2]),v(P[k+1][0],y,P[k+1][2]),v(P[k+2][0],y,P[k+2][2]),0);}
                if(open){cube(-.23f,-.36f,.2f,.23f,.36f,.3f,2,2);for(int k=0;k<3;k++)BoxMesh.octa(v(-.05f+k*.06f,-.15f+k*.13f,.33f),.05f,2);}
                else BoxMesh.seg(v(.25f,0,.25f),v(.29f,0,.25f),.035f,.045f,.03f,.04f,0,.01f,2,v(0,0,1));
                break;}
            case Sprites.BED:{   // single bed: frame, four posts, head and foot boards, straw mattress, pillow, quilt
                float L=.85f,W=.42f;
                cube(-L,-W,.12f,L,W,.24f,0,0);
                for(float x:new float[]{-L,L})for(float y:new float[]{-W,W})post(x,y,0,x<0?.82f:.5f,.04f,4);
                cube(-L-.02f,-W,.3f,-L+.02f,W,.78f,0,0);cube(L-.02f,-W,.24f,L+.02f,W,.46f,0,0);
                cube(-L+.03f,-W+.03f,.24f,L-.03f,W-.03f,.36f,1,1);
                BoxMesh.seg(v(-L+.12f,0,.36f),v(-L+.12f,0,.44f),.3f,.1f,.28f,.09f,0,.04f,3,v(1,0,0));
                cube(-L+.32f,-W+.01f,.36f,L-.02f,W-.01f,.39f,2,2);
                sheet(v(-L+.32f,-W-.005f,.39f),v(L-.02f,-W-.005f,.39f),v(L-.02f,-W-.005f,.22f),v(-L+.32f,-W-.005f,.22f),2);   // quilt hangs over the sides
                sheet(v(L-.02f,W+.005f,.39f),v(-L+.32f,W+.005f,.39f),v(-L+.32f,W+.005f,.22f),v(L-.02f,W+.005f,.22f),2);break;}
            case Sprites.CABINET:{   // low cupboard: carcass, two doors, brass handles, top with overhang
                cube(-.22f,-.45f,.06f,.2f,.45f,.82f,0,0);
                for(float y:new float[]{-.4f,.4f})for(float x:new float[]{-.2f,.18f})post(x,y,0,.06f,.03f,3);
                cube(-.25f,-.49f,.82f,.24f,.49f,.87f,3,3);
                face(v(.205f,-.43f,.78f),v(.205f,-.01f,.78f),v(.205f,-.01f,.1f),v(.205f,-.43f,.1f),v(1,0,0),1);
                face(v(.205f,.01f,.78f),v(.205f,.43f,.78f),v(.205f,.43f,.1f),v(.205f,.01f,.1f),v(1,0,0),1);
                BoxMesh.octa(v(.22f,-.06f,.5f),.02f,2);BoxMesh.octa(v(.22f,.06f,.5f),.02f,2);break;}
            case Sprites.WARDROBE:{   // tall cupboard with two doors, crown moulding and bun feet
                cube(-.28f,-.5f,.08f,.26f,.5f,1.72f,0,0);
                for(float y:new float[]{-.44f,.44f})for(float x:new float[]{-.22f,.2f})BoxMesh.cylN(v(x,y,0),v(x,y,.08f),.05f,.04f,6,0,0,3);
                cube(-.31f,-.54f,1.72f,.3f,.54f,1.8f,3,3);cube(-.29f,-.52f,1.8f,.28f,.52f,1.84f,3,3);
                face(v(.265f,-.47f,1.68f),v(.265f,-.01f,1.68f),v(.265f,-.01f,.14f),v(.265f,-.47f,.14f),v(1,0,0),1);
                face(v(.265f,.01f,1.68f),v(.265f,.47f,1.68f),v(.265f,.47f,.14f),v(.265f,.01f,.14f),v(1,0,0),1);
                beam(v(.28f,-.05f,.8f),v(.28f,-.05f,1.0f),.012f,.012f,.006f,2);beam(v(.28f,.05f,.8f),v(.28f,.05f,1.0f),.012f,.012f,.006f,2);break;}
            case Sprites.DRESSER:{   // chest of drawers: three drawer fronts with brass pulls
                cube(-.22f,-.42f,.06f,.2f,.42f,.9f,0,0);cube(-.25f,-.46f,.9f,.23f,.46f,.95f,3,3);
                for(float y:new float[]{-.38f,.38f})for(float x:new float[]{-.18f,.16f})post(x,y,0,.06f,.03f,3);
                for(int k=0;k<3;k++){float z0=.1f+k*.27f,z1=z0+.24f;face(v(.205f,-.4f,z1),v(.205f,.4f,z1),v(.205f,.4f,z0),v(.205f,-.4f,z0),v(1,0,0),1);
                    for(float y:new float[]{-.2f,.2f})BoxMesh.octa(v(.22f,y,(z0+z1)*.5f),.022f,2);}break;}
            case Sprites.TABLE:{   // plank top, four tapered legs, stretchers
                cube(-.42f,-.62f,.6f,.42f,.62f,.66f,0,0);
                for(float x:new float[]{-.34f,.34f})for(float y:new float[]{-.54f,.54f})BoxMesh.seg(v(x,y,0),v(x,y,.6f),.035f,.035f,.045f,.045f,0,0,1,v(1,0,0));
                cube(-.02f,-.54f,.14f,.02f,.54f,.18f,1,1);for(float y:new float[]{-.54f,.54f})cube(-.34f,y-.02f,.14f,.34f,y+.02f,.18f,1,1);
                cube(-.36f,-.56f,.54f,.36f,.56f,.6f,1,1);break;}
            case Sprites.CHAIR:{   // seat, four legs, back posts and two slats (back at -x)
                cube(-.2f,-.2f,.34f,.2f,.2f,.39f,0,0);
                for(float x:new float[]{-.17f,.17f})for(float y:new float[]{-.17f,.17f})post(x,y,0,x<0?.88f:.34f,.022f,1);
                cube(-.19f,-.17f,.6f,-.15f,.17f,.66f,1,1);cube(-.19f,-.17f,.76f,-.15f,.17f,.84f,1,1);
                cube(-.02f,-.17f,.1f,.02f,.17f,.13f,1,1);break;}
            case Sprites.STOOL:{   // round 8-sided seat on three splayed legs
                BoxMesh.cylN(v(0,0,.4f),v(0,0,.45f),.18f,.18f,8,0,.01f,0);
                for(int k=0;k<3;k++){double a=k*2.094;beam(v(c(a)*.2f,s(a)*.2f,0),v(c(a)*.1f,s(a)*.1f,.41f),.022f,.022f,.01f,1);}break;}
            case Sprites.BENCH:{   // long seat on two slab legs with a stretcher
                cube(-.16f,-.62f,.36f,.16f,.62f,.42f,0,0);
                for(float y:new float[]{-.48f,.48f})cube(-.13f,y-.03f,0,.13f,y+.03f,.36f,1,1);
                cube(-.025f,-.48f,.12f,.025f,.48f,.17f,1,1);break;}
            case Sprites.BOOKSHELF:{   // tall open shelf (back at -x), three rows of books
                cube(-.2f,-.5f,0,.16f,-.46f,1.7f,0,0);cube(-.2f,.46f,0,.16f,.5f,1.7f,0,0);
                cube(-.2f,-.5f,1.66f,.18f,.5f,1.72f,0,0);cube(-.2f,-.46f,0,.16f,.46f,.08f,0,0);cube(-.2f,-.46f,0,-.17f,.46f,1.66f,1,1);
                for(int r=0;r<4;r++){float z=.08f+r*.4f;if(r>0)cube(-.17f,-.46f,z-.03f,.16f,.46f,z,0,0);
                    if(r==3)break;
                    float y=-.45f;int k=r;while(y<.42f){float bw=.05f+((k*7)%3)*.02f,bh=.24f+((k*5)%4)*.03f;if(y+bw>.45f)break;
                        boolean lean=k%9==4;if(lean){beam(v(-.03f,y+.02f,z),v(-.03f,y+.1f,z+bh*.95f),bw*.5f,.11f,0,2+(k%3));y+=.12f;}
                        else{cube(-.15f+((k*3)%2)*.02f,y,z,.1f,y+bw,z+bh,2+(k%3),2+(k%3));y+=bw+.004f;}k++;}}break;}
            case Sprites.CRATE:cube(-.3f,-.3f,0,.3f,.3f,.6f,0,0);break;
            case Sprites.CANDELABRA:{   // floor stand: tripod foot, stem, three arms with candles
                for(int k=0;k<3;k++){double a=k*2.094+.5;beam(v(0,0,.14f),v(c(a)*.2f,s(a)*.2f,0),.016f,.016f,.008f,0);}
                BoxMesh.cylN(v(0,0,0),v(0,0,1.1f),.022f,.018f,5,.01f,0,0);BoxMesh.octa(v(0,0,.6f),.04f,2);
                for(int k=-1;k<=1;k++){float y=k*.17f;float[] cup=v(0,y,1.12f+(k==0?.06f:0));
                    if(k!=0)beam(v(0,0,1.0f),add(cup,v(0,0,-.03f)),.014f,.014f,.006f,0);
                    BoxMesh.cylN(add(cup,v(0,0,-.03f)),cup,.03f,.045f,6,.008f,-1,2);
                    BoxMesh.cylN(cup,add(cup,v(0,0,.12f)),.02f,.02f,6,0,.004f,1);flame(add(cup,v(0,0,.12f)),.06f,.018f,f+k);}break;}
            case Sprites.CANDLE:{   // a candle in a brass dish (for tables and shelves)
                BoxMesh.cylN(v(0,0,0),v(0,0,.02f),.07f,.07f,8,0,.004f,2);BoxMesh.cylN(v(0,0,.02f),v(0,0,.18f),.024f,.022f,6,0,.004f,1);
                beam(v(.07f,0,.015f),v(.11f,0,.04f),.008f,.008f,.006f,2);flame(v(0,0,.18f),.06f,.018f,f);break;}
            case Sprites.CAULDRON:{   // iron pot with a bulge, rim and three feet over a small fire; glowing green brew
                float[] zs={.12f,.22f,.42f,.56f},rs={.18f,.3f,.31f,.24f};
                for(int k=0;k<3;k++)BoxMesh.cylN(v(0,0,zs[k]),v(0,0,zs[k+1]),rs[k],rs[k+1],8,k==0?.03f:-1,-1,0);
                BoxMesh.cylN(v(0,0,.56f),v(0,0,.6f),.27f,.27f,8,-1,-1,0);BoxMesh.cylN(v(0,0,.5f),v(0,0,.53f),.23f,.23f,8,-1,.015f,1);
                for(int k=0;k<3;k++){double a=k*2.094;beam(v(c(a)*.22f,s(a)*.22f,.2f),v(c(a)*.27f,s(a)*.27f,0),.025f,.025f,.01f,0);}
                for(int k=0;k<3;k++){double a=k*2.094+1;beam(v(c(a)*.22f,s(a)*.22f,.03f),v(c(a)*-.05f,s(a)*-.05f,.05f),.03f,.03f,.015f,2);}
                BoxMesh.octa(v(0,0,.05f),.06f,3);break;}
            case Sprites.RUG:cube(-.7f,-1.0f,0,.7f,1.0f,.012f,0,0);break;
            case Sprites.BANNER:{   // stone foot, pole, crossbar with brass finials, cloth banner as one flat sheet with a swallowtail
                cube(-.16f,-.16f,0,.16f,.16f,.1f,3,3);BoxMesh.cylN(v(0,0,.1f),v(0,0,2.0f),.025f,.022f,5,0,0,0);BoxMesh.octa(v(0,0,2.04f),.04f,2);
                beam(v(.03f,-.34f,1.9f),v(.03f,.34f,1.9f),.016f,.016f,.008f,0);
                for(float y:new float[]{-.36f,.36f})BoxMesh.octa(v(.03f,y,1.9f),.028f,2);
                float sw=(f&1)==1?.02f:0;
                sheet(v(.05f,-.3f,1.88f),v(.05f,.3f,1.88f),v(.05f+sw,.3f,.75f),v(.05f+sw,-.3f,.75f),1);
                break;}
            case Sprites.PLANT:{   // clay pot with a lip, soil, crossed leaf sheets
                BoxMesh.cylN(v(0,0,0),v(0,0,.32f),.13f,.19f,8,0,-1,0);BoxMesh.cylN(v(0,0,.3f),v(0,0,.36f),.21f,.21f,8,-1,-1,0);
                BoxMesh.cylN(v(0,0,.28f),v(0,0,.31f),.18f,.18f,8,-1,.01f,1);
                for(int k=0;k<7;k++){double a=k*2.4;float r=.12f+(k%3)*.08f,h=.45f+(k%4)*.12f;float[] base=v(c(a)*.04f,s(a)*.04f,.3f),tip=v(c(a)*r*2.2f,s(a)*r*2.2f,.3f+h);
                    float[] side=v(-s(a)*.07f,c(a)*.07f,0);float[] mid=SpriteModels.lerp(base,tip,.5f);mid=add(mid,v(0,0,.08f));
                    sheet(base,add(mid,side),tip,add(mid,mul(side,-1)),2+(k&1));}break;}
            case Sprites.WEAPON_RACK:{   // A-frame rack with two swords and a spear leaning on it
                for(float y:new float[]{-.5f,.5f}){beam(v(-.18f,y,0),v(0,y,1.1f),.025f,.025f,.01f,0);beam(v(.18f,y,0),v(0,y,1.1f),.025f,.025f,.01f,0);}
                beam(v(0,-.52f,1.05f),v(0,.52f,1.05f),.022f,.022f,.01f,0);beam(v(.12f,-.5f,.35f),v(.12f,.5f,.35f),.02f,.02f,.01f,0);
                for(int k=0;k<3;k++){float y=-.3f+k*.3f;boolean spear=k==1;float[] bot=v(.2f,y,.02f),top=v(.04f,y+.02f,spear?1.6f:1.0f);
                    float[] dir=norm(SpriteModels.sub(top,bot));
                    if(spear){beam(bot,top,.014f,.014f,.006f,0);BoxMesh.seg(top,add(top,dir,.2f),.03f,.006f,.004f,.004f,0,.03f,1,v(1,0,0));}
                    else{beam(bot,add(bot,dir,.2f),.014f,.014f,.008f,2);beam(add(add(bot,dir,.2f),v(0,1,0),-.07f),add(add(bot,dir,.2f),v(0,1,0),.07f),.014f,.014f,0,3);
                        BoxMesh.seg(add(bot,dir,.21f),top,.026f,.006f,.022f,.006f,0,.05f,1,v(1,0,0));}}break;}
            case Sprites.SACK:{   // grain sack: 8 sides, bulging, tied neck, a spill of grain
                float[] zs={0,.1f,.32f,.48f,.56f},rs={.2f,.26f,.24f,.12f,.07f};
                for(int k=0;k<4;k++)BoxMesh.cylN(v(0,0,zs[k]),v(0,0,zs[k+1]),rs[k],rs[k+1],8,k==0?0:-1,-1,0);
                BoxMesh.cylN(v(0,0,.54f),v(0,0,.58f),.075f,.075f,6,-1,-1,1);BoxMesh.cylN(v(0,0,.58f),v(0,0,.66f),.06f,.11f,6,-1,.01f,0);
                BoxMesh.cylN(v(.26f,.04f,0),v(.26f,.04f,.03f),.12f,.06f,6,-1,.01f,2);break;}
        }
    }
    private static float[] mul(float[] a,float s){return SpriteModels.mul(a,s);}

    // ------------------------------------------------------------------ painted detail
    private static int rgb(float r,float g,float b){return 0xff000000|(cl(r)<<16)|(cl(g)<<8)|cl(b);}
    private static int cl(float x){return Math.max(0,Math.min(255,Math.round(x)));}
    private static int sc(int c,float f){return rgb(((c>>16)&255)*f,((c>>8)&255)*f,(c&255)*f);}
    private static float hash(int x,int y,int s){int h=x*374761393+y*668265263+s*1442695041;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0xffff)/65535f;}
    interface Painter{int px(int c,int x,int y,int T);}
    private static void tile(MeshModels.Mesh m,int tile,Painter p){
        int T=MeshModels.TILE,S=m.atlas*T,ox=(tile%m.atlas)*T,oy=(tile/m.atlas)*T;
        int k=T/64;   // the patterns are drawn on a 64 x 64 grid over the full-resolution texture
        for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*S+ox+x;m.color[o]=p.px(m.color[o],x/k,y/k,64);}
    }
    /** Horizontal planks with dark seams and nail heads. */
    private static final Painter PLANKS=(c,x,y,T)->{int band=y/(T/4);float f=.9f+hash(band,0,7)*.2f;if(y%(T/4)==0)return sc(c,.45f);if((x==3||x==T-4)&&y%(T/4)==T/8)return rgb(40,38,36);return sc(c,f);};
    /** Vertical planks. */
    private static final Painter VPLANKS=(c,x,y,T)->{int band=x/(T/4);float f=.9f+hash(band,1,9)*.2f;if(x%(T/4)==0)return sc(c,.5f);return sc(c,f);};
    /** Raised door panel with a dark frame. */
    private static final Painter PANEL=(c,x,y,T)->{int e=Math.min(Math.min(x,T-1-x),Math.min(y,T-1-y));if(e<2)return sc(c,.5f);if(e<7)return sc(c,1.08f);if(e==7||e==8)return sc(c,.7f);return c;};
    /** Fieldstone blocks with mortar lines. */
    private static final Painter BLOCKS=(c,x,y,T)->{int row=y/(T/4),off=(row&1)*(T/6);int col=(x+off)/(T/3);if(y%(T/4)<2||(x+off)%(T/3)<2)return rgb(58,54,50);return sc(c,.86f+hash(col,row,3)*.28f);};
    private static final Painter SOOT=(c,x,y,T)->sc(c,.4f+hash(x,y,5)*.2f);
    private static void paint(int id,MeshModels.Mesh m){
        switch(id){
            case Sprites.CRATE:tile(m,0,(c,x,y,T)->{int e=Math.min(Math.min(x,T-1-x),Math.min(y,T-1-y));
                if(e<6)return sc(c,e<1?.5f:.8f);if(Math.abs(x-y)<4)return sc(c,.8f);return VPLANKS.px(c,x,y,T);});break;
            case Sprites.BARREL:tile(m,0,VPLANKS);break;
            case Sprites.CHEST:case Sprites.CHEST_OPEN:tile(m,0,PLANKS);tile(m,2,(c,x,y,T)->sc(c,.85f+hash(x/4,y/4,2)*.35f));break;
            case Sprites.FIREPLACE:tile(m,0,BLOCKS);tile(m,1,SOOT);tile(m,5,BLOCKS);break;
            case Sprites.PILLAR:tile(m,1,BLOCKS);break;
            case Sprites.LEVER:case Sprites.LEVER_ON:tile(m,0,BLOCKS);break;
            case Sprites.CAMPFIRE:tile(m,3,(c,x,y,T)->hash(x,y,4)>.93f?rgb(170,50,14):sc(c,.7f+hash(x/3,y/3,6)*.5f));break;
            case Sprites.BED:tile(m,0,PLANKS);
                tile(m,2,(c,x,y,T)->{int qx=x/(T/4),qy=y/(T/4);if(x%(T/4)==0||y%(T/4)==0)return rgb(220,200,150);int k=(qx+qy*3)%3;return k==0?sc(c,1):k==1?rgb(46,72,120):rgb(196,160,70);});
                tile(m,1,(c,x,y,T)->hash(x,y/2,8)>.8f?sc(c,.82f):c);break;
            case Sprites.CABINET:case Sprites.WARDROBE:case Sprites.DRESSER:tile(m,0,VPLANKS);tile(m,1,PANEL);break;
            case Sprites.TABLE:case Sprites.BENCH:case Sprites.CHAIR:tile(m,0,PLANKS);break;
            case Sprites.BOOKSHELF:tile(m,0,PLANKS);for(int t=2;t<=4;t++){final int tt=t;tile(m,t,(c,x,y,T)->{
                float v=y/(float)T;if(Math.abs(v-.15f)<.03f||Math.abs(v-.85f)<.03f)return rgb(200,170,80);if(Math.abs(v-.5f)<.08f&&x>T/4&&x<T*3/4)return sc(c,1.35f);return sc(c,.9f+hash(x/8,tt,1)*.2f);});}break;
            case Sprites.RUG:tile(m,0,(c,x,y,T)->{int e=Math.min(Math.min(x,T-1-x),Math.min(y,T-1-y));float dx=Math.abs(x-T/2f+.5f),dy=Math.abs(y-T/2f+.5f);
                if(e<3)return rgb(60,30,24);if(e<6)return rgb(200,160,70);if(e<8)return rgb(60,30,24);
                if(dx+dy<T*.22f)return (dx+dy<T*.12f)?rgb(200,160,70):rgb(40,52,90);if(((x+y)/4)%4==0&&e>10)return sc(c,1.2f);return c;});break;
            case Sprites.BANNER:tile(m,1,(c,x,y,T)->{float u=x/(float)T,v=y/(float)T;if(u<.06f||u>.94f)return rgb(200,160,60);
                float dx=Math.abs(u-.5f),dy=v-.42f;boolean shield=dy>-.2f&&dy<.25f&&dx<.24f-Math.max(0,dy)*.6f;if(shield){return Math.abs(dx)<.03f||Math.abs(dy)<.03f?rgb(220,180,70):rgb(150,30,30);}return c;});
                tile(m,3,BLOCKS);break;
            case Sprites.SACK:tile(m,0,(c,x,y,T)->sc(c,((x+y)&1)==0?1.06f:.9f));break;
        }
    }
}
