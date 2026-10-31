package com.example.fsmstudio;
import java.util.*;
public final class ActionRegistry {
 public static final String[][] GROUPS={
 {"Send Event","Send Global Event","Finish State","Wait","Random Event","Compare Event","Broadcast Event","Restart FSM","Enable FSM","Disable FSM"},
 {"Set Float","Add Float","Subtract Float","Multiply Float","Divide Float","Clamp Float","Random Float","Set Integer","Compare Integer","Set Boolean"},
 {"Spawn Entity","Destroy Entity","Find Entity","Enable Entity","Disable Entity","Set Position","Set Rotation","Set Scale","Get Position","Get Distance"},
 {"Move Towards","Move By","Rotate Towards","Rotate By","Set Velocity","Add Impulse","Stop Movement","Follow Target","Patrol Waypoints","Teleport"},
 {"Detect Player","Check Line of Sight","Check Distance","Face Target","Wander","Chase Target","Flee Target","Select Target","Raycast","Check Field of View"},
 {"Apply Damage","Heal","Set Health","Check Health","Fire Hitscan","Spawn Projectile","Apply Knockback","Set Invulnerable","Check Ammo","Reload Weapon"},
 {"Play Animation","Stop Animation","Set Animation Speed","Check Animation Finished","Set Sprite","Set Sprite Direction","Set Visibility","Flash Sprite","Set Frame","Flip Sprite"},
 {"Play Sound","Stop Sound","Play Music","Stop Music","Set Volume","Spawn Effect","Screen Shake","Fade Screen","Set Light Level","Set Tint"},
 {"Open Door","Close Door","Move Sector Floor","Move Sector Ceiling","Set Sector Light","Change Level","Set Checkpoint","Show Message","Set Game Flag","Check Game Flag"}};
 public static final String[] CATEGORIES={"FSM & Events","Variables & Math","Entities","Movement","AI & Sensing","Combat","Animation & Sprites","Audio & Visuals","World & Game"};
 public static ArrayList<String> all(){ArrayList<String> a=new ArrayList<>();for(String[] group:GROUPS)Collections.addAll(a,group);return a;}
 public static boolean exists(String type){return all().contains(type);}
}