# Doom Pocket Editor

A new **Android Studio Java project** for creating and playing retro FPS maps on Android, with no external rendering libraries.

## Included in this initial prototype
- Landscape fullscreen, touch-driven interface
- **2D MAP:** 24×24 editable cell grid, wall brush, erase brush, selectable player spawn
- **3D TEXTURE:** real-time pseudo-3D raycasting, surface selection (wall/floor/ceiling), five colour-based material presets, touch to paint and swipe to turn
- **THINGS:** place/remove red enemies and green medkits in an overhead view
- **PLAY:** FPS mode with left virtual joystick, right drag to look, FIRE button, collision, target shooting and ammo/kills HUD
- Save current level to on-device local preferences; reset to demo map

## Build the debug APK
1. Open this folder in Android Studio (JDK 17).
2. Allow Gradle sync (requires the Android SDK Platform 35 and internet for Gradle dependencies).
3. Run `gradle assembleDebug` (or select **Build > Build APK(s)**).
4. Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 7+ device.

Alternatively, upload the project to your own GitHub repository; the included GitHub Actions workflow builds an APK as a downloadable workflow artifact.

## Current limitations
This is an initial, editable **grid-cell raycaster**, not a true Doom arbitrary-polygon sector engine. Room layout is formed by cells, not connected linedefs/sectors. Textures are procedural flat/checker-shaded colour palettes rather than imported image files. Enemies are simple shooting targets and do not yet move, medkits are decorative, and the project has not been compiled in this environment (Android SDK/Gradle not installed). A production sector editor would require polygon editing, vertex/line splitting, proper sector height data, portals, texture asset import, serialization/export, touch camera navigation and more robust gameplay.

## v0.3 polygon geometry
- POLYGONS tab: snapped vertex creation, tap first vertex to close a polygon, sector selection, floor and ceiling height adjustments, sector deletion.
- Shared vertex indices and computed linedefs; opposite-direction shared edges become portals.
- POLY 3D tab: projected wall quads, ceiling/floor height differences at portals, near-plane clipping, swipe look.
- JSON v3 persistence for vertices and polygon sectors, with backward-compatible loading of grid levels.

**Limitations:** The new polygon mode is a separate experimental geometry layer. The original PLAY mode still uses the grid raycaster and does not yet traverse polygon sectors. The polygon 3D preview currently renders walls and portal steps; textured polygon floor/ceiling spans, z-buffering, wall splitting and polygon collision are not implemented.
