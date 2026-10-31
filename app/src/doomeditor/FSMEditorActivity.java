package com.retro.doomeditor;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Landscape touch graph editor: add, select, drag, link, edit actions, save and test. */
public class FSMEditorActivity extends Activity {
    private FSMGraph graph;private GraphView view;private FSMGraph.Node selected,linkFrom;
    private final Paint paint=new Paint(3);
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        graph=FSMGraph.fromJson(getSharedPreferences("project",0).getString("fsm_graph",""));
        if(graph.nodes.isEmpty()){FSMGraph.Node idle=graph.add("Idle",90,100),active=graph.add("Active",370,100);idle.transitions.put("FIRE",active.id);active.action="WAIT";active.argument="1.5";active.transitions.put("FINISHED",idle.id);}
        view=new GraphView(this);setContentView(view);
    }
    private void save(){try{getSharedPreferences("project",0).edit().putString("fsm_graph",graph.toJson().toString()).apply();Toast.makeText(this,"FSM saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Save failed",Toast.LENGTH_SHORT).show();}}
    @Override public void onBackPressed(){save();super.onBackPressed();}
    private void prompt(String title,String initial,java.util.function.Consumer<String> callback){EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel",null).setPositiveButton("OK",(d,w)->{callback.accept(input.getText().toString());view.invalidate();}).show();}
    private void menu(){if(selected==null)return;
        String[] options={"Rename state","Choose action","Edit action argument","Set as start","Create event transition","Delete state"};
        new AlertDialog.Builder(this).setTitle(selected.name).setItems(options,(d,which)->{
            FSMGraph.Node node=selected;
            switch(which){
                case 0:prompt("State name",node.name,t->node.name=t);break;
                case 1:String[] actions={"NONE","WAIT","SET_LIGHT","SET_VARIABLE","CHECK_VARIABLE"};new AlertDialog.Builder(this).setTitle("Action").setItems(actions,(dd,i)->{node.action=actions[i];view.invalidate();}).show();break;
                case 2:prompt("Argument (WAIT: seconds; SET_LIGHT: sectorIndex,brightness; SET_VARIABLE: name,value)",node.argument,t->node.argument=t);break;
                case 3:graph.start=node.id;view.invalidate();break;
                case 4:linkFrom=node;Toast.makeText(this,"Tap destination state",Toast.LENGTH_SHORT).show();break;
                case 5:graph.nodes.remove(node);for(FSMGraph.Node n:graph.nodes)n.transitions.values().removeAll(java.util.Collections.singleton(node.id));if(graph.start.equals(node.id))graph.start=graph.nodes.isEmpty()?"":graph.nodes.get(0).id;selected=null;view.invalidate();break;
            }
        }).show();
    }
    private class GraphView extends View {
        private float lastX,lastY,downX,downY;private boolean dragging;
        GraphView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void txt(Canvas c,String t,float x,float y,int size,int color){paint.setColor(color);paint.setTextSize(size);paint.setStyle(Paint.Style.FILL);c.drawText(t,x,y,paint);}
        void rect(Canvas c,float l,float t,float r,float b,int color){paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(l,t,r,b,10,10,paint);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();c.drawColor(0xff18232d);
            paint.setStrokeWidth(1);paint.setColor(0xff293945);for(int x=0;x<w;x+=32)c.drawLine(x,56,x,h,paint);for(int y=56;y<h;y+=32)c.drawLine(0,y,w,y,paint);
            rect(c,0,0,w,55,0xff283b49);txt(c,"FSM GRAPH",15,35,22,0xffd9f5ef);
            String[] buttons={"+ STATE","EDIT","LINK","SAVE","TEST","EXIT"};float bw=Math.min(105,(w-160)/6);for(int i=0;i<buttons.length;i++){float x=w-(buttons.length-i)*bw;rect(c,x+2,7,x+bw-3,49,0xff36535b);txt(c,buttons[i],x+8,33,Math.max(10,(int)(bw*.15f)),0xffffffff);}
            for(FSMGraph.Node n:graph.nodes)for(java.util.Map.Entry<String,String> e:n.transitions.entrySet()){
                FSMGraph.Node dest=graph.find(e.getValue());if(dest==null)continue;
                paint.setColor(0xff8ec5c1);paint.setStrokeWidth(3);float ax=n.x+150,ay=n.y+32,bx=dest.x,by=dest.y+32;c.drawLine(ax,ay,bx,by,paint);
                txt(c,e.getKey(),(ax+bx)*.5f,(ay+by)*.5f-5,13,0xffe9d795);
            }
            for(FSMGraph.Node n:graph.nodes){rect(c,n.x,n.y,n.x+150,n.y+68,n==selected?0xff376d70:0xff344653);
                if(n.id.equals(graph.start))rect(c,n.x,n.y,n.x+150,n.y+7,0xff76d6a6);
                txt(c,n.name,n.x+10,n.y+30,17,0xffffffff);txt(c,n.action,n.x+10,n.y+53,12,0xffb4d6df);
            }
            txt(c,linkFrom!=null?"LINK: tap destination state":"Tap state to select; drag to move; EDIT for actions",12,h-14,14,0xffb7cbd5);
        }
        FSMGraph.Node hit(float x,float y){for(int i=graph.nodes.size()-1;i>=0;i--){FSMGraph.Node n=graph.nodes.get(i);if(x>=n.x&&x<=n.x+150&&y>=n.y&&y<=n.y+68)return n;}return null;}
        @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();int a=e.getActionMasked();float w=getWidth();float bw=Math.min(105,(w-160)/6);
            if(a==MotionEvent.ACTION_DOWN){lastX=downX=x;lastY=downY=y;dragging=false;if(y<55){int b=(int)((x-(w-6*bw))/bw);if(b>=0&&b<6){switch(b){
                case 0:selected=graph.add("State "+(graph.nodes.size()+1),80+graph.nodes.size()*32,90+graph.nodes.size()*25);invalidate();break;
                case 1:menu();break;case 2:linkFrom=selected;invalidate();break;case 3:save();break;
                case 4:graph.reset();Toast.makeText(getContext(),"Runtime reset: "+(graph.find(graph.active)==null?"No start state":graph.find(graph.active).name),Toast.LENGTH_SHORT).show();break;
                case 5:save();finish();break;
            }}return true;}
            FSMGraph.Node target=hit(x,y);if(linkFrom!=null&&target!=null&&target!=linkFrom){FSMGraph.Node from=linkFrom;linkFrom=null;prompt("Transition event","FINISHED",t->{if(!t.trim().isEmpty())from.transitions.put(t.trim(),target.id);});}
            else selected=target;invalidate();return true;}
            if(a==MotionEvent.ACTION_MOVE){if(selected!=null&&linkFrom==null){float dx=x-lastX,dy=y-lastY;if(Math.abs(x-downX)+Math.abs(y-downY)>5)dragging=true;if(dragging){selected.x+=dx;selected.y+=dy;invalidate();}}lastX=x;lastY=y;return true;}
            return true;
        }
    }
}
