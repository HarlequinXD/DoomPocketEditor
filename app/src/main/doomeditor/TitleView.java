package com.retro.doomeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/** Title screen and project manager: live 3D backdrop, project list and the New Project screen. */
public class TitleView extends View {
    interface Listener {void openProject(String id);}
    private static final int H_NEW=1,H_CONTINUE=2,H_NAME=3,H_CANCEL=4,H_CREATE=5,H_TEMPLATE=10,H_OPEN=100,H_MENU=600;

    private final Ui ui;
    private final Paint p;
    private final Path path=new Path();
    private final RectF r=new RectF();
    private final Renderer renderer=new Renderer();
    private final Level backdrop=Templates.arena();
    private final Level[] previews={new Level(),Templates.arena(),Templates.empty()};
    private final ArrayList<Renderer.SpriteDraw> sprites=new ArrayList<>();
    UiHost host;Listener listener;
    private ArrayList<ProjectStore.Meta> projects=new ArrayList<>();
    private boolean creating;private String newName="My Doom Game";private int template=0;
    private float w,h,scroll,downX,downY,lastY;private boolean dragged;private int downHit=-1;
    private long start=System.nanoTime();

    TitleView(Context c){super(c);ui=new Ui(c);p=ui.p;refresh();
        Project tmp=Project.fresh();
        for(Level.Thing t:backdrop.things){EntityDef d=tmp.def(t.def);if(d!=null)sprites.add(new Renderer.SpriteDraw().set(t.x,t.y,floor(backdrop,t.x,t.y),Sprites.get(d.sprite,Sprites.F_IDLE),-1,false));}
    }
    private static float floor(Level l,float x,float y){int s=l.sectorAt(x,y);return s<0?0:l.sectors.get(s).floor;}
    void refresh(){projects=ProjectStore.list(getContext());invalidate();}
    private float dp(float v){return ui.dp(v);}

    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();ui.clearHits();
        float t=(System.nanoTime()-start)*1e-9f;
        // Backdrop: slow orbit around the arena.
        float ang=t*.12f,cx=10+(float)Math.cos(ang)*6.5f,cy=10+(float)Math.sin(ang)*6.5f;
        r.set(0,0,w,h);renderer.render(c,backdrop,cx,cy,1.35f,ang+(float)Math.PI*.82f,.05f,r,sprites);
        p.setColor(0xffffffff);p.setShader(new LinearGradient(0,0,w,0,new int[]{0xf20d0f12,0xd90d0f12,0x800d0f12},new float[]{0,.45f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        drawBrand(c);
        drawProjects(c);
        if(creating)drawNewProject(c);
        postInvalidateOnAnimation();
    }
    private void drawBrand(Canvas c){
        float x=dp(44),y=h*.2f;
        float ls=dp(54);ui.round(c,x,y,x+ls,y+ls,dp(13),Ui.ACCENT);ui.icon(c,Icons.LOGO,x+ls*.5f,y+ls*.5f,32,0xffffffff);
        ui.text(c,"DOOM POCKET",x,y+ls+dp(40),30,Ui.TEXT,2,-1);
        ui.text(c,"E D I T O R",x+dp(2),y+ls+dp(62),13,0xff8fbcff,1,-1);
        ui.text(c,"Build, script and play Doom-style games on your phone.",x,y+ls+dp(92),12.5f,Ui.TEXT_2);
        float by=y+ls+dp(116),bw=Math.min(dp(260),w*.34f);
        r.set(x,by,x+bw,by+dp(44));ui.button(c,r,Icons.PLUS_SQUARE,"New Project",Ui.PRIMARY);ui.hit(r,H_NEW);
        String cur=ProjectStore.currentId(getContext());ProjectStore.Meta m=find(cur);
        if(m!=null){r.set(x,by+dp(54),x+bw,by+dp(98));ui.button(c,r,Icons.PLAY,"Continue · "+m.name,Ui.NORMAL);ui.hit(r,H_CONTINUE);}
        ui.text(c,"v0.4  ·  "+Actions.ALL.size()+" FSM actions  ·  software renderer",x,h-dp(20),10.5f,Ui.TEXT_3);
    }
    private ProjectStore.Meta find(String id){if(id==null)return null;for(ProjectStore.Meta m:projects)if(m.id.equals(id))return m;return null;}
    private void drawProjects(Canvas c){
        float L=Math.max(w*.5f,w-dp(470)),R=w-dp(24),T=dp(24),B=h-dp(24);
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(14),0x66000000);
        ui.round(c,L,T,R,B,dp(14),0xf020252b);ui.outline(c,L,T,R,B,dp(14),Ui.BORDER,dp(1));
        ui.text(c,"Your projects",L+dp(18),T+dp(32),16,Ui.TEXT,1,-1);
        ui.textMid(c,projects.size()+(projects.size()==1?" project":" projects"),R-dp(18),T+dp(27),11,Ui.TEXT_3,1,1);
        ui.hline(c,L,R,T+dp(48),Ui.BORDER);
        float top=T+dp(56),bottom=B-dp(8),rowH=dp(70);
        float max=Math.max(0,projects.size()*rowH-(bottom-top));scroll=Math.max(0,Math.min(max,scroll));
        int save=c.save();c.clipRect(L,top,R,bottom);
        if(projects.isEmpty()){ui.text(c,"No projects yet.",L+dp(18),top+dp(30),13,Ui.TEXT_2,1,-1);ui.text(c,"Tap New Project to make your first game.",L+dp(18),top+dp(50),12,Ui.TEXT_3);}
        String cur=ProjectStore.currentId(getContext());
        for(int i=0;i<projects.size();i++){ProjectStore.Meta m=projects.get(i);float y=top+i*rowH-scroll;if(y+rowH<top||y>bottom)continue;
            r.set(L+dp(10),y+dp(4),R-dp(10),y+rowH-dp(4));boolean on=m.id.equals(cur);
            ui.round(c,r,dp(10),on?0xff283241:Ui.SURFACE_2);ui.outline(c,r,dp(10),on?0x803d8bfd:0x14ffffff,dp(1));
            float ix=r.left+dp(10),iy=r.top+dp(9);ui.round(c,ix,iy,ix+dp(44),iy+dp(44),dp(9),Ui.BG);
            ui.icon(c,m.template.startsWith("Arena")?Icons.GRID:m.template.startsWith("Empty")?Icons.FRAME:Icons.SECTORS,ix+dp(22),iy+dp(22),20,0xff8fbcff);
            ui.text(c,ui.ellipsize(m.name,14,1,r.width()-dp(110)),ix+dp(56),r.top+dp(26),14,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize((m.template.isEmpty()?"Project":m.template)+" · "+m.sectors+" sectors · "+m.entities+" entities · "+m.sprites+" sprites",11,0,r.width()-dp(110)),ix+dp(56),r.top+dp(44),11,Ui.TEXT_3);
            ui.text(c,"Edited "+ago(m.modified),ix+dp(56),r.top+dp(58),10,Ui.TEXT_3);
            RectF menu=new RectF(r.right-dp(42),r.centerY()-dp(16),r.right-dp(10),r.centerY()+dp(16));
            ui.icon(c,Icons.MORE,menu.centerX(),menu.centerY(),18,Ui.TEXT_2);
            float vt=Math.max(top,r.top),vb=Math.min(bottom,r.bottom);
            if(vb>vt){ui.hit(r.left,vt,r.right,vb,H_OPEN+i);ui.hit(menu.left,Math.max(vt,menu.top),menu.right,Math.min(vb,menu.bottom),H_MENU+i);}
        }
        c.restoreToCount(save);
    }
    static String ago(long t){long s=(System.currentTimeMillis()-t)/1000;if(s<60)return "just now";if(s<3600)return (s/60)+" min ago";if(s<86400)return (s/3600)+" h ago";return (s/86400)+" d ago";}
    private void drawNewProject(Canvas c){
        ui.rect(c,0,0,w,h,0xb3000000);
        float pw=Math.min(w-dp(48),dp(720)),ph=Math.min(h-dp(32),dp(360)),L=(w-pw)*.5f,T=(h-ph)*.5f,R=L+pw,B=T+ph;
        ui.round(c,L,T+dp(4),R,B+dp(4),dp(14),0x80000000);ui.panel(c,L,T,R,B,dp(14));
        ui.text(c,"New project",L+dp(22),T+dp(36),17,Ui.TEXT,1,-1);
        ui.caption(c,"Game name",L+dp(22),T+dp(64),Ui.TEXT_3);
        r.set(L+dp(22),T+dp(72),R-dp(22),T+dp(110));ui.round(c,r,dp(8),Ui.BG);ui.outline(c,r,dp(8),Ui.BORDER,dp(1));
        ui.textMid(c,newName,r.left+dp(12),r.centerY(),14,Ui.TEXT,1,-1);ui.icon(c,Icons.PEN,r.right-dp(18),r.centerY(),15,Ui.TEXT_3);ui.hit(r,H_NAME);
        ui.caption(c,"Template",L+dp(22),T+dp(134),Ui.TEXT_3);
        float ct=T+dp(142),cb=B-dp(64),cw=(pw-dp(44)-dp(20))/3f;
        for(int i=0;i<3;i++){float x=L+dp(22)+i*(cw+dp(10));r.set(x,ct,x+cw,cb);boolean on=template==i;
            ui.round(c,r,dp(10),on?0xff26324a:Ui.SURFACE_2);ui.outline(c,r,dp(10),on?Ui.ACCENT:0x14ffffff,on?dp(2):dp(1));
            float mh=Math.max(dp(30),(cb-ct)-dp(70));drawMiniMap(c,previews[i],x+dp(10),ct+dp(10),x+cw-dp(10),ct+dp(10)+mh);
            ui.text(c,ProjectStore.TEMPLATES[i],x+dp(12),ct+mh+dp(30),13,Ui.TEXT,1,-1);
            ui.text(c,ui.ellipsize(ProjectStore.TEMPLATE_DESC[i],10.5f,0,cw-dp(24)),x+dp(12),ct+mh+dp(48),10.5f,Ui.TEXT_3);
            if(on){p.setColor(Ui.ACCENT);c.drawCircle(r.right-dp(14),r.top+dp(14),dp(9),p);ui.icon(c,Icons.CHECK,r.right-dp(14),r.top+dp(14),12,0xffffffff);}
            ui.hit(r,H_TEMPLATE+i);}
        r.set(R-dp(22)-dp(170),B-dp(52),R-dp(22),B-dp(14));ui.button(c,r,Icons.CHECK,"Create project",Ui.PRIMARY);ui.hit(r,H_CREATE);
        r.set(R-dp(22)-dp(280),B-dp(52),R-dp(22)-dp(180),B-dp(14));ui.button(c,r,-1,"Cancel",Ui.NORMAL);ui.hit(r,H_CANCEL);
    }
    /** Top-down sketch of a template's sectors. */
    private void drawMiniMap(Canvas c,Level l,float L,float T,float R,float B){
        ui.round(c,L,T,R,B,dp(6),0xff15181c);
        float minX=1e9f,minY=1e9f,maxX=-1e9f,maxY=-1e9f;for(Level.Vertex v:l.vertices){minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y);}
        if(l.vertices.isEmpty())return;
        float sc=Math.min((R-L-dp(16))/Math.max(1,maxX-minX),(B-T-dp(16))/Math.max(1,maxY-minY)),ox=(L+R)*.5f-(minX+maxX)*.5f*sc,oy=(T+B)*.5f-(minY+maxY)*.5f*sc;
        for(Level.Sector s:l.sectors){path.reset();for(int j=0;j<s.loop.size();j++){Level.Vertex v=l.vertices.get(s.loop.get(j));if(j==0)path.moveTo(ox+v.x*sc,oy+v.y*sc);else path.lineTo(ox+v.x*sc,oy+v.y*sc);}path.close();
            p.setStyle(Paint.Style.FILL);p.setColor(s.ceiling-s.floor<.1f?0xff4a5260:s.floor>0?0x663d8bfd:0x408fa3b8);c.drawPath(path,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0xccd6dce4);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
        for(Level.Thing t:l.things){p.setColor(t.def.equals("trooper")||t.def.equals("imp")||t.def.equals("demon")?Ui.RED:Ui.AMBER);c.drawCircle(ox+t.x*sc,oy+t.y*sc,dp(2),p);}
        p.setColor(Ui.GREEN);c.drawCircle(ox+l.playerX*sc,oy+l.playerY*sc,dp(2.5f),p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){downX=x;downY=lastY=y;dragged=false;downHit=ui.hitAt(x,y);return true;}
        if(a==MotionEvent.ACTION_MOVE){if(Math.abs(y-downY)>dp(8))dragged=true;if(dragged&&!creating){scroll-=y-lastY;invalidate();}lastY=y;return true;}
        if(a==MotionEvent.ACTION_UP&&!dragged&&downHit>=0&&ui.hitAt(x,y)==downHit)action(downHit);
        return true;
    }
    private void action(int id){
        if(creating){
            if(id==H_NAME&&host!=null)host.prompt("Game name",null,newName,false,t->{if(!t.trim().isEmpty())newName=t.trim();});
            else if(id>=H_TEMPLATE&&id<H_TEMPLATE+3)template=id-H_TEMPLATE;
            else if(id==H_CANCEL)creating=false;
            else if(id==H_CREATE){Project p=ProjectStore.create(getContext(),newName,template);Project.set(p);creating=false;if(listener!=null)listener.openProject(p.id);}
            invalidate();return;
        }
        if(id==H_NEW){creating=true;newName="My Doom Game";template=0;}
        else if(id==H_CONTINUE){String cur=ProjectStore.currentId(getContext());if(cur!=null&&listener!=null)listener.openProject(cur);}
        else if(id>=H_MENU){int i=id-H_MENU;if(i<projects.size())menu(projects.get(i));}
        else if(id>=H_OPEN){int i=id-H_OPEN;if(i<projects.size()&&listener!=null)listener.openProject(projects.get(i).id);}
        invalidate();
    }
    private void menu(ProjectStore.Meta m){
        if(host==null)return;
        host.choose(m.name,new String[]{"Open","Rename","Duplicate","Delete"},-1,i->{
            try{
                switch(i){
                    case 0:if(listener!=null)listener.openProject(m.id);break;
                    case 1:host.prompt("Rename project",null,m.name,false,t->{if(t.trim().isEmpty())return;try{ProjectStore.rename(getContext(),m.id,t.trim());if(Project.isOpen()&&m.id.equals(Project.get(getContext()).id))Project.get(getContext()).name=t.trim();}catch(Exception ignored){}refresh();});break;
                    case 2:ProjectStore.duplicate(getContext(),m.id);break;
                    case 3:host.confirm("Delete "+m.name+"?","This removes the level, entities, FSMs and sprites in this project. It can't be undone.","Delete",()->{
                        if(Project.isOpen()&&m.id.equals(Project.get(getContext()).id))Project.reset();ProjectStore.delete(getContext(),m.id);refresh();});break;
                }
            }catch(Exception ignored){}
            refresh();
        });
    }
}
