# Doom Pocket Editor — Infinite Sector Canvas + FSM prototype

Based on the previously working Professional Edition v1.1 source.

## New
- Sector and entity grid renders continuously in all directions, including negative world coordinates.
- Grid cursor no longer stops at the original 24x24 map boundary. Practical coordinate safety limit is +/-1,000,000 grid units.
- Camera follows cursor; only visible grid lines are rendered.
- New **FSM** entry in the upper title/header area opens a separate touch graph editor.
- FSM graph nodes can be added, selected, dragged, renamed and deleted.
- EDIT opens action configuration, start-state selection and event linking.
- LINK: select source, tap LINK, then destination, then enter event name.
- Saved FSM JSON is persisted locally in SharedPreferences under `fsm_graph`.
- FSM runtime resets on Play Mode entry, receives `FIRE` events and ticks each game frame.
- Actions implemented: NONE, WAIT (seconds), SET_LIGHT (sectorIndex,brightness), SET_VARIABLE (name,value), CHECK_VARIABLE (name,threshold).
- Transition events implemented: FIRE, FINISHED, TRUE, and arbitrary custom events if sent programmatically.
- Default example graph: Idle --FIRE--> Active --FINISHED after 1.5s--> Idle.

## Limits
- This is an initial FSM editor and runtime, **not full PlayMaker feature parity**. No per-entity FSM attachment, action library browser, inspector panels, graph zoom/pan, breakpoints, templates, undo, global events or trigger integration yet.
- Existing legacy 24x24 cell-based grid remains for backward compatibility; only the polygon sector map is unbounded.
- The map is not literally infinite: coordinates and device memory are finite.
- FSM graph is saved separately from level JSON; exporting a standalone level JSON will not include it.
- Android APK compilation/device testing not completed by assistant. Run GitHub Actions.

## Install
Replace the entire Android Studio project content, keeping a backup of your working version. Run Actions > Build Android APK.
