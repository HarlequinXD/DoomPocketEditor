package com.retro.doomeditor;

/**
 * Paints the faces of the built-in characters onto the face texture (256 x 256), the way a PS1 artist painted a
 * face from a photo reference: the face wraps the front half of the head from ear to ear (u = 0 at the left ear,
 * .5 down the nose, 1 at the right ear; v = 0 at the top of the forehead, 1 under the chin), matching the loops
 * of the sculpted head (BoxMesh.sculptHead): eyes on the eye loop, the nostrils under the nose tip, the mouth
 * between the nose and mouth loops.
 *
 * Every feature is drawn with soft (anti-aliased) edges and painted light and shade: eyes with a white that
 * darkens into the corners and under the lid, an iris with a dark rim, radial fibres and a lighter ring round the
 * pupil, a catchlight, a lash line, a lid crease and a lower lid; brows made of individual hairs; a nose with a lit
 * bridge and tip, shaded wings and nostrils; lips with a cupid's bow, a darker upper lip, a lit lower lip and a
 * shadow under it; laugh lines, a philtrum, stubble or a beard made of hairs.
 */
final class FacePaint {
    private FacePaint(){}

    /** Eye centre (either side of the nose) and height on the face texture. */
    static final float EYE_U=.168f,EYE_V=.372f,BROW_V=.292f,NOSE_V=.585f,MOUTH_V=.7f;

    private static float sst(float a,float b,float x){x=(x-a)/(b-a);x=x<0?0:x>1?1:x;return x*x*(3-2*x);}
    private static float hash(int x,int y,int s){int h=x*374761393+y*668265263+s*1442695041;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0xffff)/65535f;}
    private static float vn(float x,float y,int s){int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float xf=x-xi,yf=y-yi,u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf);
        float a=hash(xi,yi,s),b=hash(xi+1,yi,s),c=hash(xi,yi+1,s),d=hash(xi+1,yi+1,s);return a+(b-a)*u+(c-a)*v+(a-b-c+d)*u*v;}
    /** Soft inside-ness of an ellipse (1 inside, 0 outside, a texel-wide edge). */
    private static float ell(float u,float v,float cu,float cv,float ru,float rv,float px){float dx=(u-cu)/ru,dy=(v-cv)/rv;float d=(float)Math.sqrt(dx*dx+dy*dy);return 1-sst(1-px/Math.min(ru,rv),1+px/Math.min(ru,rv),d);}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}

    /**
     * Reptile scales: rounded plates (the nearest of a jittered grid of centres), each lit on its upper side and
     * dark in the cracks between them. {@code size} in 64ths of the texture. Returns a brightness factor.
     */
    static float scales(int x,int y,int T,float size){
        float X=x*64f/T/size,Y=y*64f/T/size;int cx=(int)Math.floor(X),cy=(int)Math.floor(Y);float best=9,second=9,bdy=0;
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){int gx=cx+i,gy=cy+j;float px=gx+.5f+(hash(gx,gy,71)-.5f)*.6f+((gy&1)*.5f),py=gy+.5f+(hash(gx,gy,72)-.5f)*.4f;
            float dx=X-px,dy=(Y-py)*1.25f;float d=(float)Math.sqrt(dx*dx+dy*dy);if(d<best){second=best;best=d;bdy=dy;}else if(d<second)second=d;}
        float edge=second-best;float crack=sst(.0f,.12f,edge);
        return (.72f+.28f*crack)*(1+.14f*(-bdy))*(.94f+.08f*hash(cx,cy,73));
    }

    /** Hair texture: strands running down (v), clumps, and a sheen band. */
    static float hairShade(int x,int y,int T,int seed){
        float X=x*64f/T,Y=y*64f/T;
        float strand=vn(x/1.3f,y/30f,seed)*.55f+vn(x/3f,y/60f,seed+1)*.45f;
        float clump=vn(X/4f,Y/22f,seed+2);
        float sheen=(float)Math.exp(-Math.pow((Y/64f-.32f-(clump-.5f)*.12f)/.07f,2));
        return .62f+strand*.42f+clump*.18f+sheen*.32f*(.5f+strand*.5f);
    }

    static void paint(int[] px,int S,int ox,int oy,int T,BoxMesh.Spec sp){
        float p=1f/T;   // one texel
        int hr=(sp.hairRGB>>16)&255,hg=(sp.hairRGB>>8)&255,hb=sp.hairRGB&255;
        int ir=(sp.iris>>16)&255,ig=(sp.iris>>8)&255,ib=sp.iris&255;
        int br0=(sp.beardRGB>>16)&255,bg0=(sp.beardRGB>>8)&255,bb0=sp.beardRGB&255;
        for(int y=0;y<T;y++)for(int x=0;x<T;x++){int o=(oy+y)*S+ox+x;int c=px[o];
            float r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            float u=(x+.5f)*p,v=(y+.5f)*p,cu=Math.abs(u-.5f);int side=u<.5f?-1:1;
            float n=hash(x,y,9);
            switch(sp.face){
                case BoxMesh.F_SKULL:{
                    float k=form(u,v,sp);
                    // sockets: deep, darkest at the back, a bony rim
                    float so=ell(cu,v,EYE_U,EYE_V,.085f,.07f,p),rim=ell(cu,v,EYE_U,EYE_V,.1f,.084f,p)-so;
                    float dd=(float)Math.hypot((cu-EYE_U)/.085f,(v-EYE_V)/.07f);
                    if(so>0){float dark=mix(1,.08f+dd*.25f,so);k*=dark;}
                    k*=1+rim*.12f;
                    // nasal cavity: an upside-down heart
                    float nv=(v-NOSE_V+.04f)/.06f;if(nv>-1&&nv<1){float w=.035f*(1-Math.abs(nv)*.6f)*(nv<0?1+nv*.4f:1);float ins=1-sst(w-p,w+p,cu+(nv<-.3f?Math.max(0,.012f-cu*.6f):0));k*=1-ins*.85f;}
                    // teeth: a row top and bottom, gaps between
                    if(v>.66f&&v<.82f&&cu<.15f){float tw=.024f;float tu=((u-.5f)%tw+tw)%tw/tw;boolean gap=tu<.12f||Math.abs(v-.74f)<.008f;
                        float tooth=1-sst(.13f,.15f,cu);if(gap)k*=mix(1,.25f,tooth);else{k*=mix(1,1.08f-Math.abs(tu-.55f)*.3f,tooth);}}
                    // cracks
                    float ck=Math.abs(u-.31f-v*.12f+vn(v*40,1,3)*.02f);if(ck<.005f&&v<.25f)k*=.5f;
                    r*=k;g*=k;b*=k;break;}
                case BoxMesh.F_SHADOW:{float d=(float)Math.sqrt(cu*cu+(v-.5f)*(v-.5f));float k=6+d*30+n*6;r=k+2;g=k;b=k;break;}
                default:{
                    float[] rgb={r,g,b};
                    if(sp.snout)dragon(rgb,u,v,cu,x,y,T,p,sp);
                    else if(sp.face==BoxMesh.F_GOBLIN)goblin(rgb,u,v,cu,side,x,y,T,p,sp);
                    else if(sp.face==BoxMesh.F_ORC)orc(rgb,u,v,cu,side,x,y,T,p,sp);
                    else human(rgb,u,v,cu,side,x,y,T,p,sp,hr,hg,hb,ir,ig,ib,br0,bg0,bb0);
                    r=rgb[0];g=rgb[1];b=rgb[2];
                }
            }
            px[o]=0xff000000|(cl(r)<<16)|(cl(g)<<8)|cl(b);}
    }
    private static int cl(float x){return x<0?0:x>255?255:Math.round(x);}

    /** Light and shade a flat face can't have: the cheeks turn away, sockets and brow shade, bridge, cheekbones and chin catch the light. */
    static float form(float u,float v,BoxMesh.Spec sp){
        float cu=Math.abs(u-.5f),k=1-.18f*sst(.28f,.5f,cu);
        float sock=ell(cu,v,EYE_U,EYE_V-.008f,.09f,.06f,.02f);k*=1-.13f*sock;                                   // eye sockets
        k*=1+.08f*ell(cu,v,.2f,.5f,.09f,.045f,.03f);                                                              // cheekbones
        k*=1+.05f*ell(cu,v,EYE_U,BROW_V-.02f,.11f,.02f,.02f);                                                     // brow ridge
        k*=1+.1f*ell(u,v,.5f,.47f,.018f,.1f,.012f);                                                               // nose bridge
        if(u>.5f)k*=1-.12f*ell(u,v,.535f,.5f,.03f,.09f,.02f);                                                     // the far side of the nose
        k*=1-.12f*ell(u,v,.5f,NOSE_V+.035f,.05f,.018f,.015f);                                                     // under the nose
        k*=1-.13f*ell(u,v,.5f,MOUTH_V+.06f,.06f,.016f,.015f);                                                     // under the lower lip
        k*=1+.06f*ell(u,v,.5f,.9f,.07f,.04f,.03f);                                                                // chin
        k*=1-.18f*sst(.93f,1f,v);                                                                                 // under the jaw
        if(sp.hairline)k*=1-.12f*sst(.13f,.16f,v)*(1-sst(.16f,.2f,v));                                             // the hair's shadow on the forehead
        return k;
    }

    private static void human(float[] c,float u,float v,float cu,int side,int x,int y,int T,float p,BoxMesh.Spec sp,
                              int hr,int hg,int hb,int ir,int ig,int ib,int br0,int bg0,int bb0){
        float r=c[0],g=c[1],b=c[2];
        // ---- painted form and a warm flush on cheeks, nose and lips' surround
        float k=form(u,v,sp);
        float flush=ell(cu,v,.21f,.53f,.09f,.06f,.04f)+ell(u,v,.5f,.53f,.03f,.03f,.02f)*.6f;
        r*=k*(1+flush*.07f);g*=k*(1-flush*.02f);b*=k*(1-flush*.04f);
        // ---- brows: individual hairs growing outward
        {float bu=(cu-.06f)/.2f;if(bu>-.05f&&bu<1.05f){float t=Math.max(0,Math.min(1,bu));
            float cy=BROW_V-.022f*(float)Math.sin(Math.PI*Math.min(1,t*1.25f))+(sp.heavyBrow?.006f:0);float th=(sp.female?.012f:.017f)*(1-t*.55f)+(sp.heavyBrow?.006f:0);
            float in=1-sst(th-p*1.5f,th+p*1.5f,Math.abs(v-cy));in*=sst(-.05f,.04f,bu)*(1-sst(.95f,1.05f,bu));
            float hairs=.55f+.45f*vn(x/1.1f+y*.6f*side,y/1.1f,21);
            if(in>0){float a=in*hairs*.95f;r=mix(r,hr*.75f,a);g=mix(g,hg*.75f,a);b=mix(b,hb*.75f,a);}}}
        // ---- eyes
        {float t=(cu-(EYE_U-.062f))/.124f;   // 0 inner corner, 1 outer corner
            if(t>-.15f&&t<1.2f){
                float tc=Math.max(0,Math.min(1,t));
                float up=EYE_V-.026f*(float)Math.sin(Math.PI*Math.pow(tc,.85f))+.004f,lo=EYE_V+.017f*(float)Math.sin(Math.PI*tc)+.004f;
                float inside=sst(up-p,up+p,v)*(1-sst(lo-p,lo+p,v))*sst(-.02f,.03f,t)*(1-sst(.97f,1.02f,t));
                // lid crease and a soft shadow on the upper lid
                float crease=up-.017f-.004f*(float)Math.sin(Math.PI*tc);float cr=1-sst(.0f,.006f,Math.abs(v-crease));
                if(t>0&&t<1){float lid=sst(crease,up,v)*(1-sst(up,up+.002f,v));r*=1-cr*.14f-lid*.08f;g*=1-cr*.16f-lid*.1f;b*=1-cr*.15f-lid*.08f;}
                if(inside>0){
                    // the white: darker into the corners and under the upper lid
                    float wh=.86f-.18f*Math.abs(t-.5f)*2-.2f*(1-sst(up,up+.012f,v));
                    float sr=232*wh,sg=222*wh,sb=214*wh;
                    float iu=EYE_U+.004f*(sp.female?1:0),iv=EYE_V+.004f,ri=.0215f;
                    float d=(float)Math.hypot(cu-iu,v-iv);
                    float iris=1-sst(ri-p,ri+p,d),pupil=1-sst(.0085f-p,.0085f+p,d);
                    float ang=(float)Math.atan2(v-iv,(cu-iu)*side);float fib=.82f+.18f*vn(ang*9+3,d*80,31);
                    float ring=sst(ri*.62f,ri,d);   // the rim darkens
                    float coll=1-sst(.009f,.014f,d); // lighter ring round the pupil
                    float ar=ir*fib*(1-ring*.45f)*(1+coll*.25f),ag=ig*fib*(1-ring*.45f)*(1+coll*.25f),ab=ib*fib*(1-ring*.45f)*(1+coll*.25f);
                    if(sp.solidEyes){ar=230*fib;ag=170*fib;ab=40*fib;iris=1;pupil=1-sst(.003f,.005f,Math.abs(cu-iu))*1;pupil*=1-sst(.01f,.02f,Math.abs(v-iv));}
                    float ur=mix(sr,ar,iris),ug=mix(sg,ag,iris),ub=mix(sb,ab,iris);
                    ur=mix(ur,10,pupil);ug=mix(ug,8,pupil);ub=mix(ub,8,pupil);
                    float sh=1-.35f*(1-sst(up,up+.01f,v));ur*=sh;ug*=sh;ub*=sh;   // the lid's shadow over the top of the eye
                    float ue=.5f+side*iu;float glint=1-sst(.002f,.0045f,(float)Math.hypot(u-(ue-.007f),v-(iv-.008f)));
                    ur=mix(ur,250,glint);ug=mix(ug,250,glint);ub=mix(ub,250,glint);
                    r=mix(r,ur,inside);g=mix(g,ug,inside);b=mix(b,ub,inside);
                }
                // lash line (thicker and winged for the women), lower lid
                float lw=(sp.female?.0065f:.0045f)*(.6f+.4f*(float)Math.sin(Math.PI*tc))+(sp.female&&t>.75f?.003f:0);
                float wingUp=sp.female&&t>.8f?(t-.8f)*.03f:0;
                float lash=(1-sst(lw*.5f,lw*.5f+p*1.2f,Math.abs(v-(up-lw*.4f-wingUp))))*sst(-.04f,.02f,t)*(1-sst(sp.female?1.12f:1f,sp.female?1.18f:1.06f,t));
                r=mix(r,28,lash*.92f);g=mix(g,20,lash*.92f);b=mix(b,18,lash*.92f);
                float low=(1-sst(.0f,.0035f,Math.abs(v-(lo+.002f))))*sst(0,.1f,t)*(1-sst(.9f,1f,t));
                r*=1-low*.18f;g*=1-low*.22f;b*=1-low*.2f;
                float bag=ell(cu,v,EYE_U+.005f,lo+.016f,.05f,.01f,.01f);r*=1-bag*.06f;g*=1-bag*.08f;b*=1-bag*.05f;
            }}
        // ---- nose: lit tip, shaded wings, nostrils
        {float tip=ell(u,v,.5f,NOSE_V-.012f,.016f,.012f,.01f);r*=1+tip*.1f;g*=1+tip*.08f;b*=1+tip*.07f;
            for(int s=-1;s<=1;s+=2){float wing=ell(u,v,.5f+s*.032f,NOSE_V,.017f,.016f,.008f);r*=1-wing*.1f;g*=1-wing*.13f;b*=1-wing*.12f;
                float nos=ell(u,v,.5f+s*.017f,NOSE_V+.012f,.0105f,.0055f,.003f);r=mix(r,r*.32f,nos);g=mix(g,g*.26f,nos);b=mix(b,b*.26f,nos);}
            if(sp.nose>0){float big=ell(u,v,.5f,NOSE_V-.01f,.035f,.03f,.02f);r*=1+big*.08f;g*=1+big*.03f;}}
        // ---- philtrum and laugh lines
        for(int s=-1;s<=1;s+=2){float ph=(1-sst(.0f,.004f,Math.abs(u-(.5f+s*.011f))))*sst(NOSE_V+.02f,NOSE_V+.03f,v)*(1-sst(MOUTH_V-.025f,MOUTH_V-.015f,v));r*=1-ph*.07f;g*=1-ph*.09f;b*=1-ph*.08f;
            float lt=(v-(NOSE_V-.01f))/.13f;if(lt>0&&lt<1){float lu=.5f+s*(.055f+lt*.04f+lt*lt*.01f);float fl=(1-sst(.0f,.007f,Math.abs(u-lu)))*(float)Math.sin(Math.PI*lt);
                r*=1-fl*(sp.female?.04f:.09f);g*=1-fl*(sp.female?.05f:.11f);b*=1-fl*(sp.female?.05f:.1f);}}
        // ---- mouth: cupid's bow, upper lip darker, lower lip full and lit, corners turned in
        {float mw=sp.female?.062f:.068f,t=(u-.5f)/mw;
            if(Math.abs(t)<1.15f){float at=Math.min(1,Math.abs(t));
                float line=MOUTH_V+.004f*at*at;   // the corners dip a little
                float topU=line-(sp.female?.018f:.012f)*(1-at*at)-.004f*(float)Math.exp(-Math.pow((Math.abs(t)-.28f)/.13f,2))+.004f*(float)Math.exp(-Math.pow(t/.12f,2));
                float botL=line+(sp.female?.026f:.02f)*(float)Math.sqrt(Math.max(0,1-at*at));
                float edge=1-sst(.9f,1.05f,Math.abs(t));
                float upper=sst(topU-p,topU+p,v)*(1-sst(line-p,line+p,v))*edge,lower=sst(line-p,line+p,v)*(1-sst(botL-p,botL+p,v))*edge;
                float lr=sp.female?172:146,lg=sp.female?82:86,lb=sp.female?86:80;
                if(upper>0){float d=.78f;r=mix(r,lr*d*k,upper);g=mix(g,lg*d*k,upper);b=mix(b,lb*d*k,upper);}
                if(lower>0){float hi=1+.22f*(float)Math.exp(-Math.pow((v-(line+(botL-line)*.4f))/.006f,2))*(1-at);r=mix(r,lr*k*hi,lower);g=mix(g,lg*k*hi,lower);b=mix(b,lb*k*hi,lower);}
                float ml=(1-sst(.0f,.0028f,Math.abs(v-line)))*(1-sst(.95f,1.15f,Math.abs(t)));r=mix(r,60,ml*.85f);g=mix(g,30,ml*.85f);b=mix(b,30,ml*.85f);
            }}
        // ---- stubble, beard and moustache made of hairs
        if(sp.stubble||sp.beard){
            float mous=ell(u,v,.5f,MOUTH_V-.022f,.085f,.016f,.01f);
            float mouthHole=ell(u,v,.5f,MOUTH_V+.006f,.06f,.016f,.004f);
            float sideburn=sst(.4f,.47f,v)*sst(.38f,.44f,cu);
            if(sp.beard){float area=Math.max(Math.max(sst(.62f,.7f,v)*sst(.06f,.14f,cu+(v-.62f)),sst(.68f,.75f,v)),Math.max(mous,sideburn));area*=1-mouthHole;
                float hairs=hairShade(x,y,T,77)*(.85f+.15f*vn(x/1.5f,y/4f,5));
                r=mix(r,br0*hairs,area);g=mix(g,bg0*hairs,area);b=mix(b,bb0*hairs,area);}
            else{float area=Math.max(Math.max(sst(.66f,.74f,v)*sst(.06f,.13f,cu),sst(.8f,.86f,v)),Math.max(mous*.8f,sideburn));area*=1-mouthHole;
                float dot=hash(x,y,41)>.55f?1:0;float a=area*dot*.55f;r=mix(r,br0*.6f+20,a);g=mix(g,bg0*.6f+16,a);b=mix(b,bb0*.6f+14,a);
                float sh=area*.1f;r*=1-sh;g*=1-sh*1.1f;b*=1-sh*.9f;}}
        // ---- hairline and sideburns
        if(sp.hairline){float hl=.135f+.02f*(float)Math.cos(cu*9)-.02f*sst(.3f,.45f,cu)+(vn(x/3f,0,6)-.5f)*.012f;
            float sideH=sst(.4f,.46f,cu)*(1-sst(.42f,.5f,v));
            float hair=Math.max(1-sst(hl-.008f,hl+.004f,v),sideH);
            if(hair>0){float hs=hairShade(x,y,T,13);r=mix(r,hr*hs,hair);g=mix(g,hg*hs,hair);b=mix(b,hb*hs,hair);}}
        // ---- cloth mask over the lower face
        if(sp.mask){float mk=sst(.5f,.52f,v);int mr=(sp.maskRGB>>16)&255,mg=(sp.maskRGB>>8)&255,mb=sp.maskRGB&255;
            float fold=.85f+.25f*vn(x/4f,y/12f,8)+(((x/2+y/2)&1)==0?.04f:-.04f);float edge=1-.3f*(1-sst(.52f,.56f,v));
            r=mix(r,mr*fold*edge*k,mk);g=mix(g,mg*fold*edge*k,mk);b=mix(b,(mb+3)*fold*edge*k,mk);}
        c[0]=r;c[1]=g;c[2]=b;
    }

    private static void goblin(float[] c,float u,float v,float cu,int side,int x,int y,int T,float p,BoxMesh.Spec sp){
        float r=c[0],g=c[1],b=c[2];float k=form(u,v,sp);r*=k;g*=k;b*=k;
        // heavy slanted brows, a scowl
        float bt=(cu-.05f)/.2f;if(bt>0&&bt<1){float cy=BROW_V+.03f-bt*.05f;float in=1-sst(.012f,.02f,Math.abs(v-cy));r=mix(r,r*.45f,in);g=mix(g,g*.5f,in);b=mix(b,b*.4f,in);}
        // yellow eyes with slit pupils, red rims
        float e=ell(cu,v,EYE_U,EYE_V+.006f,.05f,.026f,p);float rim=ell(cu,v,EYE_U,EYE_V+.006f,.058f,.033f,p)-e;
        r=mix(r,r*.75f+30,rim);g=mix(g,g*.6f,rim);b=mix(b,b*.6f,rim);
        if(e>0){float d=(float)Math.hypot((cu-EYE_U)/.05f,(v-EYE_V-.006f)/.026f);float er=235-d*50,eg=200-d*70,eb=40;float sl=1-sst(.003f,.006f,Math.abs(cu-EYE_U));
            er=mix(er,20,sl);eg=mix(eg,12,sl);eb=mix(eb,0,sl);r=mix(r,er,e);g=mix(g,eg,e);b=mix(b,eb,e);}
        // wide grin full of crooked teeth
        float t=(u-.5f)/.2f;if(Math.abs(t)<1){float line=MOUTH_V+.01f-.05f*(1-t*t);float m=1-sst(.012f,.02f,Math.abs(v-line-.004f));
            r=mix(r,40,m);g=mix(g,16,m);b=mix(b,12,m);
            if(v<line+.004f&&v>line-.012f){float tw=.03f,tu=((u-.5f)%tw+tw)%tw/tw;if(tu>.18f){float tt=(1-sst(.0f,.005f,Math.abs(v-line+.004f)))*0+1;float a=m*tt;r=mix(r,226,a);g=mix(g,214,a);b=mix(b,170,a);}}}
        // warts and blotches
        float w=hash(x/3,y/3,17);if(w>.992f){r*=.8f;g*=.9f;b*=.7f;}
        float blot=vn(x/14f,y/14f,4);r*=.92f+blot*.14f;g*=.94f+blot*.12f;
        c[0]=r;c[1]=g;c[2]=b;
    }

    private static void orc(float[] c,float u,float v,float cu,int side,int x,int y,int T,float p,BoxMesh.Spec sp){
        float r=c[0],g=c[1],b=c[2];float k=form(u,v,sp);
        // a heavy, overhanging brow throws the eyes into shadow
        float brow=ell(cu,v,EYE_U-.01f,EYE_V-.005f,.11f,.05f,.02f);k*=1-brow*.35f;
        k*=1+.12f*ell(cu,v,EYE_U,BROW_V-.01f,.13f,.022f,.02f);
        r*=k;g*=k;b*=k;
        // small angry red eyes (glowing gems sit on them in the game)
        float e=ell(cu,v,EYE_U,EYE_V,.03f,.014f,p);r=mix(r,250,e);g=mix(g,90,e);b=mix(b,30,e);
        float pu=ell(cu,v,EYE_U,EYE_V,.008f,.012f,p);r=mix(r,40,pu);g=mix(g,0,pu);b=mix(b,0,pu);
        // flat broad nose with flared nostrils
        float nose=ell(u,v,.5f,NOSE_V-.02f,.05f,.04f,.02f);r*=1+nose*.06f;g*=1+nose*.05f;
        for(int s=-1;s<=1;s+=2){float nos=ell(u,v,.5f+s*.028f,NOSE_V+.01f,.014f,.009f,.004f);r=mix(r,r*.3f,nos);g=mix(g,g*.3f,nos);b=mix(b,b*.3f,nos);}
        // war paint: three black stripes down each cheek
        for(int s=0;s<3;s++){float su=EYE_U+.025f+s*.03f;float st=(1-sst(.007f,.011f,Math.abs(cu-su)))*sst(.42f,.45f,v)*(1-sst(.56f+s*.02f,.6f+s*.02f,v));float worn=vn(x/2f,y/2f,s+40)>.3f?1:.3f;
            r=mix(r,26,st*worn*.9f);g=mix(g,24,st*worn*.9f);b=mix(b,28,st*worn*.9f);}
        // a thick lower lip pushed up by the tusks, a scar across the brow
        float lip=ell(u,v,.5f,MOUTH_V+.02f,.09f,.022f,.006f);r=mix(r,r*.75f+12,lip);g=mix(g,g*.7f,lip);b=mix(b,b*.7f,lip);
        float ml=(1-sst(.003f,.006f,Math.abs(v-(MOUTH_V+.002f))))*(1-sst(.08f,.1f,cu));r=mix(r,30,ml);g=mix(g,14,ml);b=mix(b,10,ml);
        float sc=(1-sst(.003f,.006f,Math.abs(v-(.25f+(u-.6f)*.8f))))*(u>.55f&&u<.75f?1:0);r=mix(r,r*.6f+40,sc);g=mix(g,g*.6f+20,sc);b=mix(b,b*.6f+20,sc);
        c[0]=r;c[1]=g;c[2]=b;
    }

    /** Dragonborn: scales over a long snout, a brow ridge over each eye, nostrils at the tip, a lipless jaw line with teeth, pale belly scales under the jaw. */
    private static void dragon(float[] c,float u,float v,float cu,int x,int y,int T,float p,BoxMesh.Spec sp){
        float r=c[0],g=c[1],b=c[2];float k=1-.15f*sst(.3f,.5f,cu);
        // overlapping scales, larger on the crown, small on the snout
        k*=scales(x,y,T,v<.45f?5.5f:4f);
        // pale belly scales under the jaw
        float belly=sst(.8f,.86f,v);if(belly>0){float bs=((y*64f/T)%3/3f)>.85f?.8f:1;r=mix(r,236*bs,belly*.55f);g=mix(g,206*bs,belly*.55f);b=mix(b,150*bs,belly*.55f);}
        // brow ridges and deep sockets
        k*=1+.14f*ell(cu,v,EYE_U+.01f,EYE_V-.05f,.07f,.02f,.01f);k*=1-.4f*ell(cu,v,EYE_U,EYE_V,.04f,.028f,.01f);
        r*=k;g*=k;b*=k;
        for(int s=-1;s<=1;s+=2){float nos=ell(u,v,.5f+s*.04f,NOSE_V-.02f,.016f,.009f,p);r=mix(r,22,nos);g=mix(g,14,nos);b=mix(b,10,nos);}
        float line=MOUTH_V+.04f+cu*cu*.4f;float ml=(1-sst(.004f,.008f,Math.abs(v-line)))*(1-sst(.36f,.4f,cu));r=mix(r,30,ml);g=mix(g,12,ml);b=mix(b,10,ml);
        if(cu<.34f&&v<line&&v>line-.018f){float tw=.028f,tu=((u-.5f)%tw+tw)%tw/tw;if(tu>.25f&&tu<.75f){float t=1-sst(line-.018f,line-.004f,v)*.0f;r=mix(r,236,t*(v>line-.004f-(.5f-Math.abs(tu-.5f))*.03f?1:0));g=mix(g,228,t*(v>line-.004f-(.5f-Math.abs(tu-.5f))*.03f?1:0));b=mix(b,200,t*(v>line-.004f-(.5f-Math.abs(tu-.5f))*.03f?1:0));}}
        c[0]=r;c[1]=g;c[2]=b;
    }
}
