# Doom Sector Editor — Android Java project

Open this folder in Android Studio (AGP 8.6.1, JDK 17, compileSdk 35). Main activity is DoomEditorActivity.

## Included
- 2D unlimited-coordinate sector canvas with grid, on-screen D-pad cursor, APPLY point, Close Sector, undo point, pan and save.
- CPU-rendered 3D ray-cast wall preview of sector polygon edges, collision within sectors, touch movement.
- FSM Graph screen inside the editor: draggable states, cubic Bézier transitions, pinch zoom, event labels, start state, undo/redo, JSON autosave.
- Right-hand state inspector, actions, transition editing, searchable 90-item action registry.
- FSM runtime with a functional subset of built-in actions and an extensible GameApi for other actions.

## Important limitations
This is a fresh starter rebuild, **not** a complete Doom-compatible engine. The renderer is a basic wall preview; it does not yet render variable floor/ceiling heights, portals, textures, sprites, or sector-to-sector passage. The 90 actions are registered/editable but most are hooks, not implemented gameplay. FSM data and map data save separately; FSM gameplay is demonstrated in the FSM tab, not yet bound to map entities. No APK or build verification is included.

## Controls
2D: D-pad moves snapping cursor; APPLY adds point; Close Sector completes polygon; drag grid pans.
3D: turn, forward/back, strafe.
FSM: drag nodes, Connect then tap destination, inspector edits state and actions.
