# Doom Pocket Editor – launch crash fix

Upload everything in this ZIP to the repository root, preserving paths, then run GitHub Actions.

Changes:
- Saved maps from older builds are checked before use. A map with broken vertex references
  (the most likely launch crash) is set aside as `level_unreadable` and a fresh map is loaded.
- New `DoomApp` crash recorder. If the app ever crashes, the next launch shows the full error
  with buttons to copy it, reset the saved map, or continue.
- Drawing/touch errors in the editor are shown on screen instead of closing the app.
- Removed the duplicated sector parsing in `Level.fromJson`.
- `patch.py` removed: it has already been applied, and running it again breaks the build.

All Java files must be in `app/src/main/java/com/retro/doomeditor/` with `package com.retro.doomeditor;`.
Delete any older copies of these classes that live in a different folder or package.
