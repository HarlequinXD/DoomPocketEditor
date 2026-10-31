# FSM Studio — Java / Android Views

Standalone Android Studio source project (Phases 1–4 foundation). Open the folder in Android Studio, sync Gradle, and run on an Android device. Landscape layout is intentional.

## Included
- Touch graph: draggable states, Bézier edges, pinch zoom, pan, grid, connect mode, state selection and editing.
- Right inspector: state properties, action list, reorder/edit/enable/remove, transitions.
- 90 named actions in nine categories, searchable action picker.
- FSM runtime with start/stop, enter/exit, update, event transitions, and working math/flags/health/wait/event actions.
- Save/load JSON in app-private storage, undo/redo for editor changes, sample graph, event testing.
- GameApi extension hooks for game engine-specific actions.

## Important limitations
This is a standalone project, NOT a merged copy of your original Doom editor. The 90 actions are catalogued and can be attached to states, but engine-dependent actions (doors, raycasts, entities, sounds, animation, etc.) require implementing `GameApi.onAction` against your game world. This package is not a verified APK, and Android SDK/Gradle compilation was not available in the generation environment. It is not a claim of full PlayMaker feature parity.

## Action argument examples
- `Wait`: `2` (seconds)
- `Send Event`: `SEEN`
- `Set Float`: `speed,3.5`
- `Add Float`: `speed,1`
- `Set Game Flag`: `doorOpen,true`
- `Show Message`: `Hello`

## Integration
Use `GraphView`, `Model`, `ActionRegistry`, `FsmRuntime`, and `GameApi` in your own editor. The sample `MainActivity` is a standalone host. Replace `GameApi.onAction` with calls to your entity, sector, audio, and rendering APIs.
