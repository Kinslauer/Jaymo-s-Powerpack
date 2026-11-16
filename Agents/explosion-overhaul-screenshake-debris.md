---
status: done
task: explosion-overhaul-screenshake-debris
branch: claude/explosion-overhaul-screenshake-debris
with: 
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/1616
mods: 
versions: 
started: 2026-11-15
owns:
  -
---
# explosion-overhaul-screenshake-debris

**Task:** Research how Explosion Overhaul does screenshake and debris effects, for Powers/Kingdoms combat. Andy wants the visual effects only — NOT the realistic explosions. Research/investigation only — no code changes yet.

## Plan
- Check CurseForge page for Explosion Overhaul
- Determine screenshake implementation (camera shake, intensity, duration)
- Determine debris implementation (particles, block fragments, entity debris)
- Document what can be adapted for Powers/Kingdoms combat animations

## Notes

### Explosion Overhaul (CurseForge, by Vinlanx)
- **CurseForge:** curseforge.com/minecraft/mc-mods/explosion-overhaul-a-new-level-of-destruction (Project ID: 1296203)
- **Author:** Vinlanx | **License:** Custom License | **Environment:** Server
- **Game versions:** 1.21.1, 1.20.1, 1.19.2 | Latest: 0.2.3.0 NeoForge alpha (Jul 21, 2026)
- **Requires:** Cloth Config API, GeckoLib
- **Note:** Andy linked this specific mod; wants screenshake/debris visual effects ONLY, not the realistic explosions

### Screenshake / Concussion system
- "a complex simulation of shock that dynamically alters the perception of the world depending on the explosion's power, distance, environmental conditions, and the player's state"
- **Not a simple effect or fixed timer** — works in stages, has phases, variable intensity, adapts to circumstances
- Effects: hearing disrupted (ringing in ears, muffled sounds), camera loses stability (swaying, blurring of vision), heartbeat reacts to stress/adrenaline
- Different in: open spaces, confined caves, behind obstacles, at the epicenter
- "tangible tremors" — physical shock feeling
- Intensity scales with distance, power, environment (stronger in caves/enclosed spaces)
- Screen shake arrives together with the sound (v0.1.6.2 update)
- Player push effects: physical knockback in enclosed spaces, synchronized with camera shake

### Debris / visual effects
- **Dynamic Flash**: Screen flash effect scaling with explosion power, distance, and visibility
- **Atmospheric Effects**: Ground dust clouds and mist effects
- **Electrical Line Sparks**: Electrical discharge from explosion centers, adjustable density
- **Debris**: Explosion craters with scattered debris and displaced earth around crater edges
- **Cave-ins**: Underground explosions cause ceiling to tremble, throwing down dust, rocks, dripstones
- **Glass breaking and dripstone falling** from explosions
- **Plasma sparks and fire clouds**

### Important distinction
- There is a **different** mod also called "Explosion Overhaul" on Modrinth (by perxenic-studios, Apache-2.0, MC 26.2/26.1/1.21.1, Fabric+NeoForge+Quilt, server-side). Its features: physics-based debris (blocks as falling entities with randomized speed/arc/rotation), smoke trails, sparks. After its rewrite, screenshake was removed. This is NOT the mod Andy linked.

### Design takeaways for Powers/Kingdoms combat screenshake/debris
1. Screenshake should be dynamic, not fixed-timer — scale by power, distance, environment
2. Camera effects: sway, blur, intensity variation — not just a simple shake
3. Debris as physical entities (falling blocks) with randomized motion
4. Dust clouds, mist, sparks as particle effects
5. Synchronize shake with sound for impact
6. Environmental variation (caves vs open space)

## Done
Compiled research report on Explosion Overhaul (Vinlanx, CurseForge Project 1296203): screenshake/concussion system (dynamic, power/distance/environment-scaled, with audio sync and visual blur), debris effects (falling block entities, dust clouds, sparks, plasma, fire clouds), and cave-in/glass-breaking mechanics. Andy wants screenshake/debris visual effects only, not the realistic explosions. A different same-named mod exists on Modrinth (perxenic-studios) but is not the one linked. No code changes made — research only. Not tested in game (research task).

- 2026-11-15 Built by Claude (project thread) as PR #1616, branch claude/project-thread-kh0o85: debris chunks, ground dust, cave-in grit, flash, ringing ears + muffle + daze, 1.4x shake underground. Build passed, not played.
