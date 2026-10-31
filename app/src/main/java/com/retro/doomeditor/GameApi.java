package com.example.fsmstudio;
import java.util.*;
/** Host integration: override methods to connect actions to an actual game world. */
public class GameApi {
 public final HashMap<String,Double> numbers=new HashMap<>();public final HashMap<String,Boolean> flags=new HashMap<>();public final HashMap<String,String> strings=new HashMap<>();
 public void onAction(String action,String argument){} // Extend for engine-specific actions
 public void onEvent(String event){}
 public void message(String message){}
 public double number(String key){Double v=numbers.get(key);return v==null?0:v;}
 public boolean flag(String key){return Boolean.TRUE.equals(flags.get(key));}
}