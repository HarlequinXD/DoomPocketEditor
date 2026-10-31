from pathlib import Path
j=Path('app/src/main/java/com/retro/doomeditor')
p=j/'EditorView.java';s=p.read_text()
s=s.replace('cursorX=Math.min(Level.W,cursorX+1)','cursorX=Math.min(1000000,cursorX+1)').replace('cursorY=Math.min(Level.H,cursorY+1)','cursorY=Math.min(1000000,cursorY+1)').replace('Math.max(0,cursorY-1)','Math.max(-1000000,cursorY-1)').replace('Math.max(0,cursorX-1)','Math.max(-1000000,cursorX-1)')
s=s.replace('cell=Math.min(availW/Level.W,availH/Level.H)*zoom;','cell=Math.min(availW/Level.W,availH/Level.H)*zoom;')
s=s.replace('panel(c,mapLeft,mapTop,mapLeft+Level.W*cell,mapTop+Level.H*cell,0xff1b2732);','rect(c,0,toolbarH,w*.78f,h,0xff1b2732);')
s=s.replace('''        for(int i=0;i<=Level.W;i++)c.drawLine(mapLeft+i*cell,mapTop,mapLeft+i*cell,mapTop+Level.H*cell,p);
        for(int i=0;i<=Level.H;i++)c.drawLine(mapLeft,mapTop+i*cell,mapLeft+Level.W*cell,mapTop+i*cell,p);''','''        // Draw only the visible portion of an unbounded world grid, including negative coordinates.
        int minGX=Math.max(-1000000,(int)Math.floor((0-mapLeft)/cell)-1);
        int maxGX=Math.min(1000000,(int)Math.ceil((w*.78f-mapLeft)/cell)+1);
        int minGY=Math.max(-1000000,(int)Math.floor((toolbarH-mapTop)/cell)-1);
        int maxGY=Math.min(1000000,(int)Math.ceil((h-mapTop)/cell)+1);
        for(int i=minGX;i<=maxGX;i++)c.drawLine(mapLeft+i*cell,toolbarH,mapLeft+i*cell,h,p);
        for(int i=minGY;i<=maxGY;i++)c.drawLine(0,mapTop+i*cell,w*.78f,mapTop+i*cell,p);''')
s=s.replace('private boolean inMap(float x,float y){return x>=mapLeft&&y>=mapTop&&x<mapLeft+Level.W*cell&&y<mapTop+Level.H*cell&&x<w*.78f;}','private boolean inMap(float x,float y){return x>=0&&y>=toolbarH&&x<w*.78f&&y<h;}')
s=s.replace('''        label(c,"UNTITLED MAP",w*.46f,titleH*.72f,''','''        label(c,"FSM",w*.46f,titleH*.72f,''')
s=s.replace('''        if(action==MotionEvent.ACTION_DOWN){downX=lastX=x;''','''        if(action==MotionEvent.ACTION_DOWN){downX=lastX=x;''')
s=s.replace('''            if(y<toolbarH){float bw=(w-175)/4f;''','''            if(y<toolbarH){if(y<toolbarH*.40f&&x>w*.43f&&x<w*.59f){getContext().startActivity(new android.content.Intent(getContext(),FSMEditorActivity.class));return true;}
                float bw=(w-175)/4f;''')
s=s.replace('''    private void updateGame(){long now=System.nanoTime();''','''    private FSMGraph runtimeGraph;private long lastFSMTime;
    private void updateGame(){long now=System.nanoTime();''')
s=s.replace('''lastTime=now;
        if(moving)''','''lastTime=now;
        if(runtimeGraph!=null)runtimeGraph.tick(dt,level);
        if(moving)''')
s=s.replace('''if(next==PLAY){camX=level.playerX;''','''if(next==PLAY){runtimeGraph=FSMGraph.fromJson(getContext().getSharedPreferences("project",0).getString("fsm_graph",""));runtimeGraph.reset();camX=level.playerX;''')
s=s.replace('''private void fire(){if(ammo<=0)''','''private void fire(){if(runtimeGraph!=null&&mode==PLAY)runtimeGraph.event("FIRE");if(ammo<=0)''')
p.write_text(s)
p=j/'SectorEditor.java';s=p.read_text();print('bounds occurrences',[(line.strip()) for line in s.splitlines() if 'Level.W' in line or 'Level.H' in line or '<0' in line][:20]);p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml');s=p.read_text().replace('<activity android:name=".MainActivity"','<activity android:name=".FSMEditorActivity" android:screenOrientation="landscape" android:exported="false"/><activity android:name=".MainActivity"');p.write_text(s)
