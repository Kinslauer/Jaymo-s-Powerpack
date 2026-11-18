# Zombies no longer crash the server when they give up vanilla door-breaking
- Zombies spawned able to break doors (Hard) lose vanilla's door breaker when they load, not from inside the Pound goal's `canUse`. Removing a goal there changed the goal set while the goal selector was walking it, and a NullPointerException stopped the server (a zombie, 2026-11-18).
- `DoorPounding`: `loaded()` calls `setCanBreakDoors(false)` before `paths()`; `Pound.canUse()` no longer does.
- Not built or played; CI compiles.
