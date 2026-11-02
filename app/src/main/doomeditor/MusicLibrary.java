package com.retro.doomeditor;

import java.util.ArrayList;
import java.util.Random;

/**
 * 50 built-in fantasy songs, composed procedurally from a style (tavern, dungeon, battle...), a musical mode,
 * a key and a tempo. Each one is generated the same way every time, so the library needs no stored data.
 * Adding a track to a project copies it into an ordinary {@link Song} that can then be edited note by note.
 */
final class MusicLibrary {
    private MusicLibrary(){}

    static final String[] CATEGORIES={"Tavern & Town","Adventure","Forest","Dungeon","Battle","Boss","Castle","Magic","Night & Sorrow"};
    // styles
    private static final int TAVERN=0,TOWN=1,ADVENTURE=2,FOREST=3,DUNGEON=4,BATTLE=5,BOSS=6,ROYAL=7,MAGIC=8,SAD=9,CAMP=10,FANFARE=11;
    private static final String[] STYLE_MOOD={"Lively","Cheerful","Heroic","Peaceful","Ominous","Intense","Menacing","Regal","Mysterious","Sorrowful","Calm","Triumphant"};
    // modes
    private static final int[][] MODES={
            {0,2,4,5,7,9,11},   // 0 major
            {0,2,3,5,7,8,10},   // 1 natural minor
            {0,2,3,5,7,9,10},   // 2 dorian
            {0,2,4,5,7,9,10},   // 3 mixolydian
            {0,1,3,5,7,8,10},   // 4 phrygian
            {0,2,3,5,7,8,11},   // 5 harmonic minor
            {0,2,4,6,7,9,11}};  // 6 lydian
    private static final String[] MODE_NAMES={"Major","Minor","Dorian","Mixolydian","Phrygian","Harmonic minor","Lydian"};
    private static final String[] KEYS={"C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B"};

    static final class Entry {
        final String name;final int category,style,mode,key,bpm,bars;final int seed;
        Entry(String name,int category,int style,int mode,int key,int bpm,int bars,int seed){this.name=name;this.category=category;this.style=style;this.mode=mode;this.key=key;this.bpm=bpm;this.bars=bars;this.seed=seed;}
        String mood(){return STYLE_MOOD[style];}
        String info(){return mood()+" · "+KEYS[key]+" "+MODE_NAMES[mode].toLowerCase()+" · "+bpm+" BPM · "+bars+" bars";}
        float seconds(){return bars*Song.STEPS_PER_BAR*60f/(bpm*Song.STEPS_PER_BEAT);}
    }
    static final ArrayList<Entry> ALL=new ArrayList<>();
    private static void add(String name,int cat,int style,int mode,int key,int bpm,int bars){ALL.add(new Entry(name,cat,style,mode,key,bpm,bars,ALL.size()*7919+131));}
    static{
        // Tavern & Town
        add("Copper Mug Tavern",0,TAVERN,3,2,144,8);
        add("Hearthside Jig",0,TAVERN,2,9,152,8);
        add("The Bard's Rest",0,TAVERN,0,7,132,8);
        add("Harvest Festival",0,TAVERN,0,5,140,8);
        add("Market Morning",0,TOWN,0,0,112,8);
        add("Millbrook Village",0,TOWN,0,7,104,8);
        add("Cobblestone Lane",0,TOWN,3,5,108,8);
        add("Lantern Square",0,TOWN,6,2,100,8);
        // Adventure
        add("Road to Everdawn",1,ADVENTURE,0,2,126,8);
        add("Over the Green Hills",1,ADVENTURE,3,7,132,8);
        add("Wanderer's Path",1,ADVENTURE,2,4,120,8);
        add("Banners in the Wind",1,ADVENTURE,0,10,128,8);
        add("The Open Realm",1,ADVENTURE,6,0,118,16);
        add("Sunlit Highlands",1,ADVENTURE,3,9,124,8);
        add("Caravan Trail",1,ADVENTURE,5,4,116,8);
        // Forest
        add("Whispering Woods",2,FOREST,2,2,96,8);
        add("Elderglade",2,FOREST,6,5,90,8);
        add("Mossy Hollow",2,FOREST,0,9,100,8);
        add("Fairy Ring",2,FOREST,6,7,104,8);
        add("Riverbend",2,FOREST,3,0,98,8);
        add("Moonlit Grove",2,FOREST,1,4,86,8);
        // Dungeon
        add("Crypt of Echoes",3,DUNGEON,4,4,72,8);
        add("Deep Delve",3,DUNGEON,1,9,80,8);
        add("Torchlit Corridors",3,DUNGEON,5,2,84,8);
        add("Goblin Warrens",3,DUNGEON,4,7,96,8);
        add("Sunken Catacombs",3,DUNGEON,1,0,70,8);
        add("Dripping Stone",3,DUNGEON,2,5,76,8);
        add("The Forgotten Vault",3,DUNGEON,5,11,78,16);
        // Battle
        add("Steel and Thunder",4,BATTLE,1,4,152,8);
        add("Clash at the Ford",4,BATTLE,5,9,148,8);
        add("Ambush!",4,BATTLE,4,2,160,8);
        add("Shield Wall",4,BATTLE,1,7,140,8);
        add("Charge of the Knights",4,BATTLE,2,0,156,8);
        add("Orcish Onslaught",4,BATTLE,4,5,150,8);
        add("Skirmish",4,BATTLE,5,11,144,8);
        // Boss
        add("Dragon's Wrath",5,BOSS,5,4,164,8);
        add("The Bone Sovereign",5,BOSS,4,9,156,8);
        add("Throne of Shadows",5,BOSS,5,2,150,16);
        add("Demon of the Pit",5,BOSS,4,0,170,8);
        // Castle
        add("Hall of Kings",6,ROYAL,0,2,104,8);
        add("The Royal Court",6,ROYAL,0,10,100,8);
        add("Coronation March",6,ROYAL,3,7,112,8);
        add("Victory Fanfare",6,FANFARE,0,0,120,4);
        // Magic
        add("Arcane Library",7,MAGIC,6,9,96,8);
        add("Wizard's Tower",7,MAGIC,5,4,104,8);
        add("Crystal Caverns",7,MAGIC,6,2,88,8);
        add("Runes of Old",7,MAGIC,2,7,92,8);
        // Night & Sorrow
        add("Fallen Hero",8,SAD,1,9,66,8);
        add("Requiem of the Lost",8,SAD,5,2,60,8);
        add("Starlit Camp",8,CAMP,0,5,80,8);
    }
    static int count(int category){int n=0;for(Entry e:ALL)if(category<0||e.category==category)n++;return n;}

    private static final Song[] CACHE=new Song[64];
    /** The generated song for a library entry (shared; copy it before editing). */
    static synchronized Song song(int index){
        if(index<0||index>=ALL.size())return null;
        if(CACHE[index]==null){CACHE[index]=generate(ALL.get(index));CACHE[index].id="lib_"+index;}
        return CACHE[index];
    }
    /** A project copy of a library song. */
    static Song copyFor(Project p,int index){
        Song s=song(index).copy();s.name=ALL.get(index).name;s.id=p.uniqueId("mus_"+s.name);return s;
    }

    // ================================================================== composer
    private static final class Style {
        int[][] progs;int bass,harmony,drums;int[][] rhythms;float rest;int lead,leadOct;float leadVol,drumVol;boolean busy;
    }
    // bass patterns
    private static final int B_PULSE=0,B_ROOT_FIFTH=1,B_DRONE=2,B_GALLOP=3,B_WALK=4,B_OOMPAH=5,B_HEARTBEAT=6;
    // harmony parts
    private static final int H_ARP8=0,H_ARP16=1,H_PAD=2,H_STABS=3,H_OFFBEAT=4,H_HARP_SLOW=5;
    // drum kits
    private static final int D_NONE=0,D_TAVERN=1,D_LIGHT=2,D_MARCH=3,D_BATTLE=4,D_BOSS=5,D_DUNGEON=6,D_ADVENTURE=7,D_FANFARE=8;

    private static Style style(int s,int mode){
        Style st=new Style();boolean minor=mode==1||mode==2||mode==4||mode==5;
        int[][] majorProgs={{0,4,5,3},{0,3,4,0},{0,5,3,4},{0,3,0,4},{0,2,3,4}};
        int[][] minorProgs={{0,5,6,0},{0,3,4,0},{0,6,5,6},{0,5,3,4},{0,3,6,4}};
        st.progs=minor?minorProgs:majorProgs;
        st.rest=.12f;st.lead=Song.I_PULSE;st.leadOct=0;st.leadVol=.42f;st.drumVol=.7f;
        int[][] even={{4,4,4,4},{4,2,2,4,4},{2,2,4,4,4},{6,2,4,4},{4,4,8},{2,2,2,2,8}};
        int[][] busy={{2,2,2,2,2,2,4},{2,1,1,2,2,4,4},{3,1,2,2,4,4},{2,2,4,2,2,4},{1,1,2,2,2,4,4}};
        int[][] slow={{8,8},{4,4,8},{12,4},{8,4,4},{16}};
        switch(s){
            case TAVERN:st.bass=B_OOMPAH;st.harmony=H_OFFBEAT;st.drums=D_TAVERN;st.rhythms=busy;st.lead=Song.I_SQUARE;st.rest=.06f;st.busy=true;break;
            case TOWN:st.bass=B_ROOT_FIFTH;st.harmony=H_ARP8;st.drums=D_LIGHT;st.rhythms=even;st.lead=Song.I_PULSE;st.drumVol=.45f;break;
            case ADVENTURE:st.bass=B_ROOT_FIFTH;st.harmony=H_STABS;st.drums=D_ADVENTURE;st.rhythms=even;st.lead=Song.I_SQUARE;break;
            case FOREST:st.bass=B_DRONE;st.harmony=H_ARP8;st.drums=D_LIGHT;st.rhythms=even;st.lead=Song.I_SINE;st.leadVol=.5f;st.drumVol=.3f;break;
            case DUNGEON:st.bass=B_HEARTBEAT;st.harmony=H_PAD;st.drums=D_DUNGEON;st.rhythms=slow;st.lead=Song.I_TRI;st.leadOct=-1;st.rest=.35f;st.leadVol=.45f;st.drumVol=.55f;
                st.progs=new int[][]{{0,0,1,0},{0,5,0,1},{0,6,5,0},{0,1,0,6}};break;
            case BATTLE:st.bass=B_GALLOP;st.harmony=H_STABS;st.drums=D_BATTLE;st.rhythms=busy;st.lead=Song.I_SQUARE;st.rest=.05f;st.busy=true;break;
            case BOSS:st.bass=B_GALLOP;st.harmony=H_PAD;st.drums=D_BOSS;st.rhythms=busy;st.lead=Song.I_SAW;st.leadVol=.32f;st.rest=.04f;st.busy=true;
                st.progs=new int[][]{{0,1,0,6},{0,5,1,4},{0,6,5,1},{0,0,5,4}};break;
            case ROYAL:st.bass=B_WALK;st.harmony=H_PAD;st.drums=D_MARCH;st.rhythms=even;st.lead=Song.I_PULSE;break;
            case MAGIC:st.bass=B_DRONE;st.harmony=H_ARP16;st.drums=D_NONE;st.rhythms=slow;st.lead=Song.I_SINE;st.leadOct=1;st.leadVol=.4f;st.rest=.2f;break;
            case SAD:st.bass=B_DRONE;st.harmony=H_PAD;st.drums=D_NONE;st.rhythms=slow;st.lead=Song.I_SINE;st.leadVol=.5f;st.rest=.1f;break;
            case CAMP:st.bass=B_PULSE;st.harmony=H_HARP_SLOW;st.drums=D_NONE;st.rhythms=slow;st.lead=Song.I_TRI;st.leadVol=.5f;break;
            default:st.bass=B_PULSE;st.harmony=H_STABS;st.drums=D_FANFARE;st.rhythms=even;st.lead=Song.I_SQUARE;st.rest=0;
                st.progs=new int[][]{{0,3,4,0}};
        }
        return st;
    }
    private static int pitch(int base,int[] mode,int deg){int o=Math.floorDiv(deg,7),d=Math.floorMod(deg,7);return base+mode[d]+12*o;}
    private static int clampNote(int p){while(p<Song.MIN_NOTE)p+=12;while(p>Song.MAX_NOTE)p-=12;return p;}

    /** A note in a motif: start step in the 2-bar phrase, length, scale degree relative to the bar's chord root. */
    private static final class MNote {int pos,len,rel;MNote(int p,int l,int r){pos=p;len=l;rel=r;}}

    private static ArrayList<MNote> motif(Random r,Style st){
        ArrayList<MNote> out=new ArrayList<>();int deg=new int[]{0,2,4,7}[r.nextInt(4)];
        for(int bar=0;bar<2;bar++){int[] rh=st.rhythms[r.nextInt(st.rhythms.length)];int pos=bar*16;
            for(int i=0;i<rh.length;i++){int len=rh[i];boolean strong=pos%8==0;
                if(!(i==0&&bar==0)&&r.nextFloat()<st.rest){pos+=len;continue;}
                if(strong){int[] ct={0,2,4,7,9};int best=ct[0];for(int c:ct)if(Math.abs(c-deg)<Math.abs(best-deg))best=c;deg=r.nextFloat()<.7f?best:deg;}
                else{int step=r.nextFloat()<.75f?(r.nextBoolean()?1:-1):(r.nextBoolean()?2:-2);if(st.busy&&r.nextFloat()<.2f)step*=2;deg+=step;}
                if(deg<-2)deg+=3;if(deg>10)deg-=3;
                out.add(new MNote(pos,len,deg));pos+=len;}
        }
        return out;
    }
    private static ArrayList<MNote> vary(Random r,ArrayList<MNote> m,float amount){
        ArrayList<MNote> out=new ArrayList<>();
        for(MNote n:m){int rel=n.rel;if(r.nextFloat()<amount)rel+=r.nextBoolean()?1:-1;out.add(new MNote(n.pos,n.len,rel));}
        return out;
    }

    static Song generate(Entry e){
        Random r=new Random(e.seed);
        Style st=style(e.style,e.mode);int[] mode=MODES[e.mode];
        Song s=new Song();s.name=e.name;s.v[0]=e.bpm;s.v[1]=e.bars;s.v[2]=.8f;
        int bars=e.bars;
        // chord per bar: two passes of a progression, the last bar always resolves home
        int[] prog=st.progs[r.nextInt(st.progs.length)],prog2=st.progs[r.nextInt(st.progs.length)];
        int[] chords=new int[bars];
        for(int b=0;b<bars;b++){int[] p=(b/4)%2==1&&bars>4?prog2:prog;chords[b]=p[b%4];}
        chords[bars-1]=0;if(bars>=8&&e.style!=DUNGEON)chords[bars-2]=e.mode==5||e.mode==0||e.mode==6?4:chords[bars-2];

        Song.Track lead=new Song.Track("Melody",st.lead),bass=new Song.Track("Bass",e.style==BATTLE||e.style==BOSS?Song.I_SAW:Song.I_TRI),
                harm=new Song.Track(st.harmony==H_PAD?"Strings":st.harmony==H_STABS||st.harmony==H_OFFBEAT?"Chords":"Harp",st.harmony==H_PAD?Song.I_SAW:st.harmony==H_STABS||st.harmony==H_OFFBEAT?Song.I_PULSE:Song.I_TRI),
                drum=new Song.Track("Drums",Song.I_DRUMS);
        lead.v[Song.T_VOL]=st.leadVol;lead.v[Song.T_ATTACK]=st.lead==Song.I_SINE||st.lead==Song.I_TRI?.03f:.005f;lead.v[Song.T_SUSTAIN]=.6f;lead.v[Song.T_RELEASE]=st.rhythms.length>0&&e.style==SAD||e.style==MAGIC?.4f:.12f;
        bass.v[Song.T_VOL]=e.style==BATTLE||e.style==BOSS?.32f:.55f;bass.v[Song.T_DECAY]=.2f;bass.v[Song.T_SUSTAIN]=.6f;bass.v[Song.T_RELEASE]=.06f;
        if(st.harmony==H_PAD){harm.v[Song.T_VOL]=.22f;harm.v[Song.T_ATTACK]=.25f;harm.v[Song.T_SUSTAIN]=.85f;harm.v[Song.T_RELEASE]=.6f;}
        else if(st.harmony==H_STABS||st.harmony==H_OFFBEAT){harm.v[Song.T_VOL]=.2f;harm.v[Song.T_DECAY]=.12f;harm.v[Song.T_SUSTAIN]=.25f;harm.v[Song.T_RELEASE]=.05f;}
        else{harm.v[Song.T_VOL]=.35f;harm.v[Song.T_ATTACK]=.002f;harm.v[Song.T_DECAY]=.5f;harm.v[Song.T_SUSTAIN]=0;harm.v[Song.T_RELEASE]=.4f;}
        drum.v[Song.T_VOL]=st.drumVol;

        int leadBase=clampNote(60+e.key+st.leadOct*12),harmBase=48+e.key,bassBase=36+e.key;if(e.key>=7){harmBase-=12;bassBase-=12;leadBase-=12;}
        if(e.key>=7&&bassBase<Song.MIN_NOTE)bassBase+=12;

        // melody: phrase structure A A' B A'' (and again for 16 bars), last note held on the home note
        ArrayList<MNote> A=motif(r,st),B=motif(r,st);
        for(int ph=0;ph<bars/2;ph++){
            ArrayList<MNote> m;int k=ph%4;m=k==0?A:k==1?vary(r,A,.3f):k==2?B:vary(r,A,.2f);
            if(e.style==FANFARE)m=ph==0?A:vary(r,A,.25f);
            for(MNote n:m){int bar=ph*2+n.pos/16;if(bar>=bars)continue;
                int step=ph*32+n.pos;
                if(bar==bars-1&&(bars>=8||n.pos%16>=8))continue;   // the last bar holds the home note
                lead.add(step,clampNote(pitch(leadBase,mode,chords[bar]+n.rel)),n.len);}
        }
        lead.add((bars-1)*16+(bars>=8?0:8),clampNote(pitch(leadBase,mode,7)),bars>=8?12:8);
        trimOverlaps(lead);

        for(int b=0;b<bars;b++){int o=b*16,c=chords[b];
            int root=pitch(bassBase,mode,c),fifth=pitch(bassBase,mode,c+4),third=pitch(bassBase,mode,c+2);
            switch(st.bass){
                case B_PULSE:for(int k=0;k<16;k+=4)bass.add(o+k,root,3);break;
                case B_ROOT_FIFTH:bass.add(o,root,4);bass.add(o+4,fifth,4);bass.add(o+8,root,4);bass.add(o+12,r.nextBoolean()?fifth:third,4);break;
                case B_DRONE:bass.add(o,root,16);break;
                case B_GALLOP:for(int k=0;k<16;k+=4){bass.add(o+k,root,2);bass.add(o+k+3,root,1);}if(b%2==1){bass.add(o+12,fifth,2);bass.add(o+15,fifth,1);bass.notes.removeIf(n->n.step==o+12&&n.pitch==root||n.step==o+15&&n.pitch==root);}break;
                case B_WALK:bass.add(o,root,4);bass.add(o+4,third,4);bass.add(o+8,fifth,4);bass.add(o+12,pitch(bassBase,mode,c+(r.nextBoolean()?5:1)),4);break;
                case B_OOMPAH:bass.add(o,root,2);bass.add(o+8,fifth,2);if(r.nextFloat()<.4f)bass.add(o+14,third,2);break;
                default:bass.add(o,root,2);bass.add(o+3,root,2);if(b%2==1)bass.add(o+8,pitch(bassBase,mode,c-1),6);break;   // heartbeat
            }
            int[] tri={pitch(harmBase,mode,c),pitch(harmBase,mode,c+2),pitch(harmBase,mode,c+4),pitch(harmBase,mode,c+7)};
            switch(st.harmony){
                case H_ARP8:{int[] seq={0,1,2,3,2,1,0,2};for(int k=0;k<8;k++)harm.add(o+k*2,tri[seq[k]]+12,3);break;}
                case H_ARP16:{int[] seq={0,1,2,3,2,3,1,2};for(int k=0;k<16;k++)harm.add(o+k,tri[seq[k%8]]+12+(k>=8?12:0),2);break;}
                case H_PAD:for(int k=0;k<3;k++)harm.add(o,tri[k]+12,16);break;
                case H_STABS:{int[] at=e.style==FANFARE?new int[]{0,4,8,12}:e.style==BATTLE?new int[]{0,6,12}:new int[]{0,8};for(int pos:at)for(int k=0;k<3;k++)harm.add(o+pos,tri[k]+12,2);break;}
                case H_OFFBEAT:for(int pos=4;pos<16;pos+=8)for(int k=0;k<3;k++)harm.add(o+pos,tri[k]+12,2);break;
                default:{harm.add(o,tri[0]+12,6);harm.add(o+4,tri[1]+12,6);harm.add(o+8,tri[2]+12,6);harm.add(o+12,tri[3]+12,4);}
            }
            drums(drum,st.drums,o,b,bars,r);
        }
        s.tracks.add(lead);s.tracks.add(bass);s.tracks.add(harm);if(st.drums!=D_NONE)s.tracks.add(drum);
        else{Song.Track d=new Song.Track("Drums",Song.I_DRUMS);d.v[Song.T_VOL]=.5f;s.tracks.add(d);}   // empty, ready to fill in
        for(Song.Track t:s.tracks)for(Song.Note n:t.notes){n.pitch=clampNote(n.pitch);if(n.step+n.len>bars*16)n.len=Math.max(1,bars*16-n.step);}
        return s;
    }
    private static void trimOverlaps(Song.Track t){
        t.notes.sort((a,b)->a.step-b.step);
        for(int i=0;i+1<t.notes.size();i++){Song.Note a=t.notes.get(i),b=t.notes.get(i+1);if(a.step+a.len>b.step)a.len=Math.max(1,b.step-a.step);}
        for(int i=t.notes.size()-1;i>0;i--)if(t.notes.get(i).step==t.notes.get(i-1).step)t.notes.remove(i-1);
    }
    // drum indices: 0 kick,1 snare,2 closed hat,3 open hat,4 clap,5 low tom,6 high tom,7 crash
    private static void drums(Song.Track d,int kit,int o,int bar,int bars,Random r){
        boolean fill=bar%4==3,first=bar%8==0;
        switch(kit){
            case D_TAVERN:d.add(o,0,1);d.add(o+8,0,1);d.add(o+4,4,1);d.add(o+12,4,1);for(int k=2;k<16;k+=4)d.add(o+k,2,1);if(fill){d.add(o+14,6,1);d.add(o+15,6,1);}break;
            case D_LIGHT:d.add(o,0,1);if(r.nextFloat()<.5f)d.add(o+10,0,1);for(int k=4;k<16;k+=8)d.add(o+k,2,1);if(fill)d.add(o+14,6,1);break;
            case D_ADVENTURE:d.add(o,0,1);d.add(o+8,0,1);if(r.nextBoolean())d.add(o+10,0,1);d.add(o+4,1,1);d.add(o+12,1,1);for(int k=0;k<16;k+=2)d.add(o+k,2,1);
                if(fill){d.add(o+13,5,1);d.add(o+14,6,1);d.add(o+15,6,1);}if(first)d.add(o,7,1);break;
            case D_MARCH:d.add(o,0,1);d.add(o+8,0,1);d.add(o+4,1,1);d.add(o+12,1,1);d.add(o+14,1,1);if(bar%2==1){d.add(o+13,1,1);d.add(o+15,1,1);}if(first)d.add(o,7,1);break;
            case D_BATTLE:for(int k:new int[]{0,3,8,11})d.add(o+k,0,1);d.add(o+4,1,1);d.add(o+12,1,1);for(int k=0;k<16;k+=2)d.add(o+k,2,1);
                if(fill){d.add(o+12,5,1);d.add(o+13,5,1);d.add(o+14,6,1);d.add(o+15,6,1);}if(bar%4==0)d.add(o,7,1);break;
            case D_BOSS:for(int k=0;k<16;k+=2)d.add(o+k,0,1);d.add(o+4,1,1);d.add(o+12,1,1);d.add(o+4,4,1);d.add(o+12,4,1);for(int k=1;k<16;k+=2)d.add(o+k,2,1);
                if(fill)for(int k=8;k<16;k++)d.add(o+k,k<12?5:6,1);if(bar%2==0)d.add(o,7,1);break;
            case D_DUNGEON:d.add(o,0,1);d.add(o+3,0,1);if(bar%2==1)d.add(o+10,5,1);if(bar%4==3)d.add(o+8,3,1);break;
            case D_FANFARE:d.add(o,0,1);d.add(o+8,0,1);d.add(o+4,1,1);d.add(o+12,1,1);if(bar==0)d.add(o,7,1);if(bar==bars-1){d.add(o,7,1);for(int k=10;k<16;k++)d.add(o+k,1,1);}break;
            default:
        }
    }
}
