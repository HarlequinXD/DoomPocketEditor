package com.retro.doomeditor;

/**
 * 25 blood-splat designs, rasterised into small masks once per splat.
 * Mask values: 0 = empty, 1 = blood, 2 = dark (thick) blood, 3 = wet highlight, 4 = dried rim.
 * Coordinates are in units of the splat radius; +a is the direction the droplet was travelling.
 */
final class SplatDesigns {
    private SplatDesigns(){}
    static final int COUNT=25;
    static final String[] NAMES={
        "Round splat","Lobed splat","Four-lobe splat","Wobbly splat","Splat with flecks",
        "Splash","Long splash","Splash with trail","Comet","Skid",
        "Crown","Spiky crown","Star burst","Burst with droplets","Ragged burst",
        "Smear","Double smear","Drag mark",
        "Fine mist","Spray","Scattered drops",
        "Dried ring","Ringed splat",
        "Puddle","Wide puddle"};
    /** Splats on walls also run: drip trails are added below the splat (in unrotated "down" = -b). */
    static byte[] mask(int type,float angle,boolean wall,int seed,int ms,float span){
        byte[] m=new byte[ms*ms];float ca=(float)Math.cos(-angle),sa=(float)Math.sin(-angle),step=2*span/ms;
        for(int v=0;v<ms;v++)for(int u=0;u<ms;u++){
            float A=-span+(u+.5f)*step,B=-span+(v+.5f)*step;
            float a=A*ca-B*sa,b=A*sa+B*ca;                     // into the design's own frame
            int s=shade(type,a,b,seed);
            if(s==0&&wall)s=drips(A,B,seed);
            m[v*ms+u]=(byte)s;
        }
        return m;
    }
    private static float h(int seed,int k){int x=seed*374761393+k*668265263;x=(x^(x>>>13))*1274126177;return ((x^(x>>>16))&0xffff)/65536f;}
    private static float wob(float ang,int seed,float amp,int lobes){
        return 1+amp*(float)Math.sin(ang*lobes+h(seed,1)*6.28f)+amp*.55f*(float)Math.sin(ang*(lobes*2+1)+h(seed,2)*6.28f)+amp*.3f*(float)Math.sin(ang*(lobes*3+2)+h(seed,3)*6.28f);
    }
    private static boolean disc(float a,float b,float x,float y,float r){float dx=a-x,dy=b-y;return dx*dx+dy*dy<r*r;}
    /** Distance from (a,b) to segment (x0,y0)-(x1,y1). */
    private static float seg(float a,float b,float x0,float y0,float x1,float y1){float px=x1-x0,py=y1-y0,l=px*px+py*py;float t=l<1e-6f?0:Math.max(0,Math.min(1,((a-x0)*px+(b-y0)*py)/l));float ex=x0+px*t-a,ey=y0+py*t-b;return (float)Math.sqrt(ex*ex+ey*ey);}
    /** Irregular blob of radius r: 2 in the thick middle, 3 on a highlight, 1 elsewhere. */
    private static int blob(float a,float b,float r,int seed,float amp,int lobes){
        float d=(float)Math.hypot(a,b);if(d>r*(1+amp*1.9f))return 0;
        float rr=r*wob((float)Math.atan2(b,a),seed,amp,lobes);if(d>=rr)return 0;
        float hx=a+r*.35f,hy=b+r*.32f;if(hx*hx+hy*hy<r*r*.025f)return 3;
        return d<rr*.38f?2:1;
    }
    private static int satellites(float a,float b,int seed,int n,float d0,float d1,float s0,float s1){
        for(int k=0;k<n;k++){float ang=h(seed,10+k)*6.283f,d=d0+h(seed,30+k)*(d1-d0),r=s0+h(seed,50+k)*(s1-s0);
            if(disc(a,b,(float)Math.cos(ang)*d,(float)Math.sin(ang)*d,r))return 1;}
        return 0;
    }
    private static int shade(int type,float a,float b,int seed){
        int s;
        switch(type){
            // ---- round splats
            case 0:s=blob(a,b,.8f,seed,.08f,3);return s!=0?s:satellites(a,b,seed,3,1.0f,1.3f,.06f,.12f);
            case 1:s=blob(a,b,.75f,seed,.2f,5);return s!=0?s:satellites(a,b,seed,4,1.0f,1.45f,.05f,.13f);
            case 2:s=blob(a,b,.72f,seed,.26f,4);return s!=0?s:satellites(a,b,seed,2,1.05f,1.3f,.08f,.12f);
            case 3:s=blob(a,b,.7f,seed,.3f,7);return s!=0?s:satellites(a,b,seed,5,.95f,1.5f,.04f,.1f);
            case 4:s=blob(a,b,.6f,seed,.15f,3);return s!=0?s:satellites(a,b,seed,12,.75f,1.6f,.03f,.08f);
            // ---- directional splashes (droplet came in travelling +a)
            case 5:{s=blob(a*.8f+.15f,b,.62f,seed,.12f,3);if(s!=0)return s;
                for(int k=0;k<4;k++){float x=.75f+k*.22f,r=.12f-k*.022f;if(disc(a,b,x,(h(seed,k)-.5f)*.25f,r))return 1;}return 0;}
            case 6:{s=blob(a*.55f+.1f,b*1.05f,.5f,seed,.1f,3);if(s!=0)return s;
                if(seg(a,b,.6f,0,1.5f,(h(seed,5)-.5f)*.3f)<.07f)return 1;return disc(a,b,1.62f,(h(seed,5)-.5f)*.33f,.1f)?1:0;}
            case 7:{s=blob(a+.25f,b,.6f,seed,.14f,4);if(s!=0)return s;
                for(int k=0;k<7;k++){float x=.45f+k*.18f,y=(float)Math.sin(k*1.3f+h(seed,6)*6)*.12f;if(disc(a,b,x,y,.09f-k*.009f))return 1;}return 0;}
            case 8:{if(seg(a,b,-1.1f,0,.35f,0)<.06f+.18f*Math.max(0,(a+1.1f)/1.45f))return 1;s=blob(a-.45f,b,.38f,seed,.1f,3);return s;}
            case 9:{float w=.2f+.06f*(float)Math.sin(a*6+h(seed,7)*6);if(Math.abs(b)<w&&a>-1.2f&&a<.9f)return Math.abs(b)<w*.4f?2:1;
                return satellites(a,b,seed,4,.9f,1.4f,.04f,.09f);}
            // ---- crowns and bursts
            case 10:case 11:case 12:case 13:case 14:{
                float core=type==12?.38f:.5f;s=blob(a,b,core,seed,.1f,4);if(s!=0)return s;
                int spikes=type==10?8:type==11?13:type==12?16:type==13?9:11;float d=(float)Math.hypot(a,b),ang=(float)Math.atan2(b,a);
                for(int k=0;k<spikes;k++){float sa=k*6.283f/spikes+(h(seed,70+k)-.5f)*.4f,len=core+.35f+h(seed,90+k)*(type==14?1.0f:.75f);
                    float ex=(float)Math.cos(sa),ey=(float)Math.sin(sa);float w=(type==12?.05f:.08f)*(1-(d-core)/(len-core+.01f)*.6f);
                    if(seg(a,b,0,0,ex*len,ey*len)<w)return 1;
                    if((type==13||type==11)&&disc(a,b,ex*(len+.14f),ey*(len+.14f),.07f))return 1;}
                return type==13?satellites(a,b,seed,6,1.3f,1.7f,.03f,.06f):0;}
            // ---- smears
            case 15:{float w=.32f*(1+.25f*(float)Math.sin(a*4+h(seed,8)*6))*(1-Math.abs(a)/1.6f);if(Math.abs(a)<1.5f&&Math.abs(b-(float)Math.sin(a*1.5f)*.1f)<w)return Math.abs(b)<w*.3f?2:1;return 0;}
            case 16:{for(int k=-1;k<=1;k+=2){float off=k*.22f,w=.12f*(1-Math.abs(a)/1.7f);if(Math.abs(a)<1.6f&&Math.abs(b-off-(float)Math.sin(a*2+k)*.05f)<w)return 1;}return 0;}
            case 17:{float t=(a+1.5f)/3f;if(t<0||t>1)return 0;float w=.28f*(1-t*.75f)+.04f*(float)Math.sin(a*11+h(seed,9)*6);
                if(Math.abs(b)<w){if(Math.abs(b)<w*.35f&&((int)((a+2)*9)&1)==0)return 0;return t<.2f?2:1;}return 0;}
            // ---- mist and droplets
            case 18:case 19:case 20:{int n=type==18?30:type==19?22:14;float spread=type==19?1.6f:1.4f;
                for(int k=0;k<n;k++){float ang=h(seed,110+k)*6.283f,rr=(float)Math.sqrt(h(seed,140+k))*spread,r=(type==20?.07f:.04f)+h(seed,170+k)*(type==20?.08f:.05f);
                    float x=(float)Math.cos(ang)*rr+(type==19?rr*.4f:0),y=(float)Math.sin(ang)*rr*(type==19?.7f:1);if(disc(a,b,x,y,r))return 1;}
                return type==20?blob(a,b,.28f,seed,.1f,3):0;}
            // ---- dried rings
            case 21:{float d=(float)Math.hypot(a,b),rr=.85f*wob((float)Math.atan2(b,a),seed,.12f,3);if(d>=rr)return satellites(a,b,seed,3,1.05f,1.35f,.05f,.1f);return d>rr-.13f?4:1;}
            case 22:{float d=(float)Math.hypot(a,b),rr=.75f*wob((float)Math.atan2(b,a),seed,.2f,5);if(d>=rr)return satellites(a,b,seed,5,1.0f,1.5f,.04f,.1f);return d>rr-.12f?4:d<rr*.35f?2:1;}
            // ---- puddles
            case 23:{float d=(float)Math.hypot(a,b),ang=(float)Math.atan2(b,a);float rr=1.05f*(1+.14f*(float)Math.sin(ang*2+h(seed,11)*6)+.09f*(float)Math.sin(ang*3+h(seed,12)*6)+.04f*(float)Math.sin(ang*9));
                if(d>=rr)return 0;if(disc(a,b,-.3f,-.25f,.12f)||disc(a,b,.25f,.3f,.07f))return 3;return d<rr*.6f?2:1;}
            default:{float d=(float)Math.hypot(a*.75f,b*1.1f),ang=(float)Math.atan2(b,a);float rr=1.1f*(1+.18f*(float)Math.sin(ang*3+h(seed,13)*6)+.08f*(float)Math.sin(ang*7+h(seed,14)*6));
                if(d>=rr){return satellites(a,b,seed,4,1.4f,1.7f,.05f,.1f);}if(disc(a,b,.4f,-.2f,.1f))return 3;return d<rr*.55f?2:d>rr-.08f?4:1;}
        }
    }
    /** Drips running down a wall from under the splat. */
    private static int drips(float a,float b,int seed){
        int n=1+(int)(h(seed,200)*3);
        for(int k=0;k<n;k++){float x=(h(seed,210+k)-.5f)*.9f,len=.6f+h(seed,220+k)*1.1f,top=-.35f,bot=top-len;
            if(Math.abs(a-x)<.055f&&b<top&&b>bot)return 1;
            if(disc(a,b,x,bot,.085f))return 2;}
        return 0;
    }
}
