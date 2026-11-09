package com.retro.doomeditor;

/**
 * Bakes lighting into a model's colour texture, the way PS1 artists painted light and shadow into their textures:
 * <ul>
 *   <li>A warm key light from the upper front left with soft cast shadows (several rays per point), a cool sky fill
 *       from above and a dim bounce from the ground.</li>
 *   <li>Ambient occlusion: every point looks around itself for nearby geometry, so armpits, the insides of legs,
 *       under the chin, under a belt or a pauldron and the joints all darken.</li>
 *   <li>The painted detail is lit too: its height (cloth folds and weave, seams, pores, plate edges, creases) is
 *       embossed by the key light, so it reads as relief rather than flat colour.</li>
 *   <li>Material response: skin gets warm subsurface red where it falls into shadow, metal a baked highlight,
 *       shadows lean cool and lit areas warm.</li>
 *   <li>Finally every tile is reduced to 15-bit colour (32 levels per channel, the PS1's texture format) with a
 *       4 x 4 ordered dither, so the gradients break up into the familiar PS1 dither pattern.</li>
 * </ul>
 * Lighting is computed in 3D on the first animation frame (the idle pose): every triangle is drawn into texture
 * space, and a texel shared by several parts (both arms use the same tile) gets the average of all of them,
 * weighted by how much of the model's surface each part covers there. Hand-built objects show one whole tile per
 * face, so their lighting keeps only the light/dark variation (the tile's average brightness is kept) plus a bevel
 * painted around the edge of each face.
 *
 * Model space: x = forward, y = right, z = up.
 */
final class TexBake {
    private TexBake(){}

    /** Key light: from the front, upper left. */
    private static final float[] KEY=norm(new float[]{.55f,-.5f,.68f});
    /** Roughly where the camera sees a model from, for the baked metal highlight. */
    private static final float[] VIEW=norm(new float[]{1f,-.15f,.35f});
    static boolean enabled=true,PROFILE=false;

    /**
     * Lights {@code m.color} (painted, unpacked mesh: 9 floats per vertex) in place and dithers it to 15 bits.
     * {@code perPart}: true for characters, whose parts wrap their tile around them (lighting kept as is); false for
     * objects, whose faces each show the whole tile.
     */
    static void bake(MeshModels.Mesh m,SpriteModels.Model mats,boolean perPart){bake(m,mats,perPart,-1);}
    /** {@code faceTile}: the tile with the painted face (-1 if none); it keeps more of its own brightness so faces stay readable. */
    static void bake(MeshModels.Mesh m,SpriteModels.Model mats,boolean perPart,int faceTile){
        if(!enabled||m.color==null){m.height=null;return;}long T0=System.nanoTime();
        MeshModels.Frame fr=m.frames[0];if(fr==null||fr.v.length<fr.count*9){m.height=null;return;}
        int S=m.atlas*MeshModels.TILE,T=MeshModels.TILE,G=m.atlas;
        float[] v=fr.v;int n=fr.count;
        // ---- occluders (glowing parts such as flames and eyes cast no shadow)
        float mnx=1e9f,mny=1e9f,mnz=1e9f,mxx=-1e9f,mxy=-1e9f,mxz=-1e9f;
        for(int i=0;i<n;i++){int o=i*9;mnx=Math.min(mnx,v[o]);mny=Math.min(mny,v[o+1]);mnz=Math.min(mnz,v[o+2]);mxx=Math.max(mxx,v[o]);mxy=Math.max(mxy,v[o+1]);mxz=Math.max(mxz,v[o+2]);}
        float size=Math.max(.05f,Math.max(mxx-mnx,Math.max(mxy-mny,mxz-mnz)));
        int nt=n/3;float[] tri=new float[nt*12];int nOcc=0;   // p0, e1, e2, centre... stored as p0 e1 e2 + bounding sphere
        float[] sph=new float[nt*4];
        for(int t=0;t<nt;t++){int o=t*27;if(v[o+8]>.5f)continue;
            float ax=v[o],ay=v[o+1],az=v[o+2],bx=v[o+9],by=v[o+10],bz=v[o+11],cx=v[o+18],cy=v[o+19],cz=v[o+20];
            int k=nOcc*12;tri[k]=ax;tri[k+1]=ay;tri[k+2]=az;tri[k+3]=bx-ax;tri[k+4]=by-ay;tri[k+5]=bz-az;tri[k+6]=cx-ax;tri[k+7]=cy-ay;tri[k+8]=cz-az;
            float sx=(ax+bx+cx)/3,sy=(ay+by+cy)/3,sz=(az+bz+cz)/3;float r=0;
            for(int q=0;q<3;q++){float dx=v[o+q*9]-sx,dy=v[o+q*9+1]-sy,dz=v[o+q*9+2]-sz;r=Math.max(r,dx*dx+dy*dy+dz*dz);}
            sph[nOcc*4]=sx;sph[nOcc*4+1]=sy;sph[nOcc*4+2]=sz;sph[nOcc*4+3]=(float)Math.sqrt(r)+1e-4f;nOcc++;}
        Occ occ=new Occ(tri,sph,nOcc);
        // ---- ambient occlusion and key-light shadow per vertex (shared corners computed once)
        float aoDist=size*.2f,eps=size*.006f;float[][] dirs=hemisphere(24);float[][] keyRays=cone(KEY,5,.09f);
        float[] ao=new float[n],sh=new float[n];java.util.HashMap<Long,float[]> seen=new java.util.HashMap<>();
        for(int i=0;i<n;i++){int o=i*9;float[] N=norm(new float[]{v[o+3],v[o+4],v[o+5]});
            long key=(((long)Math.round(v[o]*500)&0xfffff)<<44)^(((long)Math.round(v[o+1]*500)&0xfffff)<<24)^(((long)Math.round(v[o+2]*500)&0xfffff)<<4)^((long)(Math.round(N[0]*3)+Math.round(N[1]*3)*7+Math.round(N[2]*3)*49)*0x9E3779B97F4A7C15L);
            float[] r=seen.get(key);
            if(r==null){float px=v[o]+N[0]*eps,py=v[o+1]+N[1]*eps,pz=v[o+2]+N[2]*eps;
                float[][] tb=basis(N);float occl=0;
                for(float[] d:dirs){float dx=tb[0][0]*d[0]+tb[1][0]*d[1]+N[0]*d[2],dy=tb[0][1]*d[0]+tb[1][1]*d[1]+N[1]*d[2],dz=tb[0][2]*d[0]+tb[1][2]*d[1]+N[2]*d[2];
                    float hit=occ.hit(px,py,pz,dx,dy,dz,aoDist);if(hit<aoDist)occl+=1-hit/aoDist*.6f;}
                float a=1-occl/dirs.length;
                float lit=0;for(float[] d:keyRays)if(N[0]*d[0]+N[1]*d[1]+N[2]*d[2]>-.2f&&occ.hit(px,py,pz,d[0],d[1],d[2],size*2)>=size*2)lit++;
                r=new float[]{Math.max(0,Math.min(1,a)),lit/keyRays.length};seen.put(key,r);}
            ao[i]=r[0];sh[i]=r[1];}
        if(PROFILE)System.out.println("rays "+seen.size()+" verts, "+nOcc+" tris "+(System.nanoTime()-T0)/1000000+"ms");
        // ---- draw every triangle into a light map (a quarter of the texture's resolution: light and shade are
        //      smooth, the fine detail comes from the painted height below), accumulating the light at each texel
        final int LS=4,R=S/LS,TL=T/LS;
        float[] accL=new float[R*R],accSpec=new float[R*R],accW=new float[R*R];
        for(int t=0;t<nt;t++){int o=t*27;if(v[o+8]>.5f)continue;
            float u0=v[o+6]*R,v0=v[o+7]*R,u1=v[o+15]*R,v1=v[o+16]*R,u2=v[o+24]*R,v2=v[o+25]*R;
            float area=(u1-u0)*(v2-v0)-(u2-u0)*(v1-v0);if(Math.abs(area)<1e-5f)continue;
            float e1x=v[o+9]-v[o],e1y=v[o+10]-v[o+1],e1z=v[o+11]-v[o+2],e2x=v[o+18]-v[o],e2y=v[o+19]-v[o+1],e2z=v[o+20]-v[o+2];
            float cx=e1y*e2z-e1z*e2y,cy=e1z*e2x-e1x*e2z,cz=e1x*e2y-e1y*e2x;float area3=(float)Math.sqrt(cx*cx+cy*cy+cz*cz);
            float density=area3/Math.abs(area);   // surface per texel: big parts outweigh small ones sharing the tile
            int i0=t*3;
            int x0=Math.max(0,(int)Math.floor(Math.min(u0,Math.min(u1,u2)))),x1=Math.min(R-1,(int)Math.ceil(Math.max(u0,Math.max(u1,u2))));
            int y0=Math.max(0,(int)Math.floor(Math.min(v0,Math.min(v1,v2)))),y1=Math.min(R-1,(int)Math.ceil(Math.max(v0,Math.max(v1,v2))));
            float tol=1.2f/Math.max(1,Math.max(x1-x0,y1-y0));
            for(int py=y0;py<=y1;py++)for(int px=x0;px<=x1;px++){float qx=px+.5f,qy=py+.5f;
                float b0=((u1-qx)*(v2-qy)-(u2-qx)*(v1-qy))/area,b1=((u2-qx)*(v0-qy)-(u0-qx)*(v2-qy))/area,b2=1-b0-b1;
                if(b0<-tol||b1<-tol||b2<-tol)continue;
                b0=Math.max(0,b0);b1=Math.max(0,b1);b2=Math.max(0,b2);float bs=b0+b1+b2;b0/=bs;b1/=bs;b2/=bs;
                float nx=v[o+3]*b0+v[o+12]*b1+v[o+21]*b2,ny=v[o+4]*b0+v[o+13]*b1+v[o+22]*b2,nz=v[o+5]*b0+v[o+14]*b1+v[o+23]*b2;
                float nl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(nl<1e-6f)continue;nx/=nl;ny/=nl;nz/=nl;
                float a=ao[i0]*b0+ao[i0+1]*b1+ao[i0+2]*b2,s=sh[i0]*b0+sh[i0+1]*b1+sh[i0+2]*b2;
                float nd=nx*KEY[0]+ny*KEY[1]+nz*KEY[2];float diff=Math.max(0,(nd+.2f)/1.2f);diff*=diff*(3-2*diff)*.5f+.5f;
                float sky=.5f+.5f*nz,ground=Math.max(0,-nz);
                float amb=(.3f+.24f*sky+.05f*ground)*a,key=.64f*diff*s*(.55f+.45f*a);
                float L=.48f+.78f*(amb+key)/1.18f;
                float hx=KEY[0]+VIEW[0],hy=KEY[1]+VIEW[1],hz=KEY[2]+VIEW[2];float hl=(float)Math.sqrt(hx*hx+hy*hy+hz*hz);
                float sd=Math.max(0,(nx*hx+ny*hy+nz*hz)/hl);sd*=sd;sd*=sd;float spec=sd*sd*sd*s*a;   // ^24
                float w=density*(.15f+a);int k=py*R+px;accL[k]+=L*w;accSpec[k]+=spec*w;accW[k]+=w;}
        }
        if(PROFILE)System.out.println("raster "+(System.nanoTime()-T0)/1000000+"ms");
        // ---- light-map texels no triangle touches (seams, borders): take the neighbours' light, else the tile's average
        float[] Lm=new float[R*R],Sp=new float[R*R];boolean[] has=new boolean[R*R];
        for(int k=0;k<R*R;k++)if(accW[k]>0){Lm[k]=accL[k]/accW[k];Sp[k]=accSpec[k]/accW[k];has[k]=true;}
        for(int pass=0;pass<3;pass++){boolean[] nh=has.clone();
            for(int y=0;y<R;y++)for(int x=0;x<R;x++){int k=y*R+x;if(has[k])continue;float sl=0,ss=0;int c=0;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=R||yy>=R||xx/TL!=x/TL||yy/TL!=y/TL)continue;int q=yy*R+xx;if(has[q]){sl+=Lm[q];ss+=Sp[q];c++;}}
                if(c>0){Lm[k]=sl/c;Sp[k]=ss/c;nh[k]=true;}}
            has=nh;}
        // characters: keep the model's overall brightness (the game lights it too) and bake only the light and shade
        float gs=0;int gc=0;for(int k=0;k<R*R;k++){int tile=(k/R/TL)*G+(k%R)/TL;if(has[k]&&!(tile<mats.mats.size()&&mats.mats.get(tile).glow)){gs+=Lm[k];gc++;}}
        final float gain=gc>0?Math.max(.8f,Math.min(1.5f,1.04f/(gs/gc))):1;
        final float[] H=m.height;final boolean[] hasF=has;
        java.util.stream.IntStream.range(0,G*G).parallel().forEach(tile->{int ox=(tile%G)*T,oy=(tile/G)*T,lx=(tile%G)*TL,lyy=(tile/G)*TL;
            SpriteModels.Mat mt=tile<mats.mats.size()?mats.mats.get(tile):null;if(mt!=null&&mt.glow)return;
            int kind=mt==null?SpriteModels.K_PLAIN:mt.kind;boolean metal=mt!=null&&(mt.metal>.3f||kind==SpriteModels.K_METAL);
            float sum=0;int cnt=0;for(int y=0;y<TL;y++)for(int x=0;x<TL;x++){int k=(lyy+y)*R+lx+x;if(hasF[k]){sum+=Lm[k];cnt++;}}
            float mean=cnt>0?sum/cnt:1;
            // the tile's light map with the gaps filled by the mean
            float[] tl=new float[TL*TL],ts=new float[TL*TL];
            for(int y=0;y<TL;y++)for(int x=0;x<TL;x++){int k=(lyy+y)*R+lx+x;tl[y*TL+x]=hasF[k]?Lm[k]:mean;ts[y*TL+x]=hasF[k]?Sp[k]:0;}
            float relief=kind==SpriteModels.K_METAL?.55f:kind==SpriteModels.K_SKIN?.5f:kind==SpriteModels.K_WOOD?.6f:1.1f;
            for(int y=0;y<T;y++)for(int x=0;x<T;x++){int k=(oy+y)*S+ox+x;
                float fx=Math.max(0,Math.min(TL-1.001f,(x+.5f)/LS-.5f)),fy=Math.max(0,Math.min(TL-1.001f,(y+.5f)/LS-.5f));int ix=(int)fx,iy=(int)fy;fx-=ix;fy-=iy;
                int q=iy*TL+ix;
                float Lr=(tl[q]*(1-fx)+tl[q+1]*fx)*(1-fy)+(tl[q+TL]*(1-fx)+tl[q+TL+1]*fx)*fy;
                float spec=(ts[q]*(1-fx)+ts[q+1]*fx)*(1-fy)+(ts[q+TL]*(1-fx)+ts[q+TL+1]*fx)*fy;
                float L=Lr*(perPart?gain:1);
                if(tile==faceTile)L=1.08f+(Lr-mean)*.6f;   // the face keeps its brightness, painted form does the rest
                if(!perPart){L=1+(L-mean)*.9f;   // objects: keep the light and dark, not the overall level
                    float ex=Math.min(x,T-1-x)/(float)T,ey=Math.min(y,T-1-y)/(float)T,e=Math.min(ex,ey);   // a bevel around each face
                    if(e<.03f)L*=(x<T/2&&y<T/2&&ex<ey)||(y<T/2&&ey<=ex)?1.16f:.72f;else if(e<.09f)L*=.93f+e;}
                if(H!=null){int xa=Math.max(0,x-1),xb=Math.min(T-1,x+1),ya=Math.max(0,y-1),yb=Math.min(T-1,y+1);
                    float slope=H[(oy+ya)*S+ox+xa]-H[(oy+yb)*S+ox+xb];   // lit from the upper left of the tile
                    float cav=H[k]-(H[(oy+ya)*S+ox+x]+H[(oy+yb)*S+ox+x]+H[(oy+y)*S+ox+xa]+H[(oy+y)*S+ox+xb])*.25f;
                    L*=1+slope*relief*.55f+cav*relief*.6f;}
                int c=m.color[k];float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f;
                float dark=Math.max(0,1-L);
                if(kind==SpriteModels.K_SKIN||kind==SpriteModels.K_HIDE){r*=L*(1+dark*.28f);g*=L*(1+dark*.04f);b*=L*(1-dark*.06f);}   // light scattering through skin
                else{r*=L*(1-dark*.12f);g*=L*(1-dark*.04f);b*=L*(1+dark*.14f);}   // cool shadows
                if(L>1){float w=(L-1)*.18f;r*=1+w;b*=1-w*.6f;}   // warm key
                float sp=metal?spec*.85f:kind==SpriteModels.K_SKIN?spec*.08f:kind==SpriteModels.K_LEATHER?spec*.14f:kind==SpriteModels.K_WOOD?spec*.05f:spec*.03f;
                r+=sp;g+=sp*.97f;b+=sp*.9f;
                m.color[k]=0xff000000|(c255(r)<<16)|(c255(g)<<8)|c255(b);}
        });
        if(PROFILE)System.out.println("apply "+(System.nanoTime()-T0)/1000000+"ms");
        dither(m.color,S,mats,G,T);
        m.height=null;if(PROFILE)System.out.println("done "+(System.nanoTime()-T0)/1000000+"ms");
    }

    // ------------------------------------------------------------------ finished textures kept on disk
    /** A model's finished (painted, lit, dithered) texture saved by an earlier run, or null. */
    static int[] load(String key,int n){
        java.io.File d=Sprites.textureDir();if(d==null)return null;java.io.File f=new java.io.File(d,safe(key)+".tex");if(!f.isFile())return null;
        try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(new java.io.FileInputStream(f)),1<<16))){
            if(in.readInt()!=n)return null;int[] px=new int[n];for(int i=0;i<n;i++)px[i]=in.readInt();return px;
        }catch(Throwable t){f.delete();return null;}
    }
    static void save(String key,int[] px){
        java.io.File d=Sprites.textureDir();if(d==null||px==null)return;java.io.File f=new java.io.File(d,safe(key)+".tex"),tmp=new java.io.File(d,safe(key)+".tmp");
        try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.util.zip.DeflaterOutputStream(new java.io.FileOutputStream(tmp),new java.util.zip.Deflater(1)),1<<16))){
            out.writeInt(px.length);for(int c:px)out.writeInt(c);
        }catch(Throwable t){tmp.delete();return;}
        if(!tmp.renameTo(f))tmp.delete();
        if(key.startsWith("look_")){java.io.File[] all=d.listFiles();if(all!=null)for(java.io.File o:all)if(o.getName().startsWith("look_")&&!o.equals(f))o.delete();}   // only the current hero's look is kept
    }
    private static String safe(String k){StringBuilder b=new StringBuilder();for(char c:k.toCharArray())b.append(Character.isLetterOrDigit(c)?c:'_');return b.toString()+"_"+Integer.toHexString(k.hashCode());}

    private static final int[] BAYER={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
    /** 15-bit colour (32 levels per channel) with a 4 x 4 ordered dither, on every tile that doesn't glow. */
    static void dither(int[] px,int S,SpriteModels.Model mats,int G,int T){
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){int tile=(y/T)*G+x/T;if(tile<mats.mats.size()&&mats.mats.get(tile).glow)continue;
            int k=y*S+x,c=px[k];float d=(BAYER[(y&3)*4+(x&3)]+.5f)/16f-.5f;
            px[k]=0xff000000|(q5(((c>>16)&255),d)<<16)|(q5(((c>>8)&255),d)<<8)|q5(c&255,d);}
    }
    private static int q5(int c,float d){int q=Math.round(c/255f*31+d);q=Math.max(0,Math.min(31,q));return (q*255+15)/31;}

    // ------------------------------------------------------------------ rays
    private static final class Occ {
        final float[] t,s;final int n;Occ(float[] t,float[] s,int n){this.t=t;this.s=s;this.n=n;}
        /** Distance to the nearest triangle along the ray (unit dir), or {@code max}. */
        float hit(float ox,float oy,float oz,float dx,float dy,float dz,float max){
            float best=max;
            for(int i=0;i<n;i++){int j=i*4;float cx=s[j]-ox,cy=s[j+1]-oy,cz=s[j+2]-oz,r=s[j+3];
                float tc=cx*dx+cy*dy+cz*dz;if(tc<-r||tc-r>best)continue;float d2=cx*cx+cy*cy+cz*cz-tc*tc;if(d2>r*r)continue;
                int k=i*12;float e1x=t[k+3],e1y=t[k+4],e1z=t[k+5],e2x=t[k+6],e2y=t[k+7],e2z=t[k+8];
                float px=dy*e2z-dz*e2y,py=dz*e2x-dx*e2z,pz=dx*e2y-dy*e2x;float det=e1x*px+e1y*py+e1z*pz;if(Math.abs(det)<1e-9f)continue;float inv=1/det;
                float sx=ox-t[k],sy=oy-t[k+1],sz=oz-t[k+2];float u=(sx*px+sy*py+sz*pz)*inv;if(u<0||u>1)continue;
                float qx=sy*e1z-sz*e1y,qy=sz*e1x-sx*e1z,qz=sx*e1y-sy*e1x;float w=(dx*qx+dy*qy+dz*qz)*inv;if(w<0||u+w>1)continue;
                float tt=(e2x*qx+e2y*qy+e2z*qz)*inv;if(tt>1e-4f&&tt<best)best=tt;}
            return best;
        }
    }
    /** Evenly spread directions over a hemisphere around +z, denser towards the top (cosine weighted). */
    private static float[][] hemisphere(int n){float[][] d=new float[n][];double ga=Math.PI*(3-Math.sqrt(5));
        for(int i=0;i<n;i++){float r=(float)Math.sqrt((i+.5)/n);double a=i*ga;d[i]=new float[]{(float)(r*Math.cos(a)),(float)(r*Math.sin(a)),(float)Math.sqrt(Math.max(0,1-r*r))};}return d;}
    /** {@code n} directions spread a little around {@code dir}: soft shadow edges. */
    private static float[][] cone(float[] dir,int n,float spread){float[][] tb=basis(dir);float[][] d=new float[n][];d[0]=dir.clone();
        for(int i=1;i<n;i++){double a=i*2*Math.PI/(n-1);float x=(float)Math.cos(a)*spread,y=(float)Math.sin(a)*spread;
            d[i]=norm(new float[]{dir[0]+tb[0][0]*x+tb[1][0]*y,dir[1]+tb[0][1]*x+tb[1][1]*y,dir[2]+tb[0][2]*x+tb[1][2]*y});}return d;}
    private static float[][] basis(float[] n){float[] a=Math.abs(n[2])<.9f?new float[]{0,0,1}:new float[]{1,0,0};
        float[] t=norm(new float[]{a[1]*n[2]-a[2]*n[1],a[2]*n[0]-a[0]*n[2],a[0]*n[1]-a[1]*n[0]});float[] b={n[1]*t[2]-n[2]*t[1],n[2]*t[0]-n[0]*t[2],n[0]*t[1]-n[1]*t[0]};return new float[][]{t,b};}
    private static float[] norm(float[] a){float l=(float)Math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]);if(l<1e-9f)return new float[]{0,0,1};return new float[]{a[0]/l,a[1]/l,a[2]/l};}
    private static int c255(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
}
