---
status: done
task: blood-overlay-zfighting
branch: claude/blood-overlay-zfighting
pr: 1124
mods: Powers
versions: 0.21.55
started: 2026-11-07
owns:
  - src/client/java/com/jaymospowers/client/blood/
  - src/client/java/com/jaymospowers/client/wet/
---

# Blood overlay z-fighting and pop-in

**Task:** Andy wants every flat thing drawn on top of something else (blood decals/puddles/wall marks, blood on held items, blood-coated bodies) checked for z-fighting flicker and pop-in at spawn or distance, with depth-offset and fade tuning as static final constants.

## Plan
- Raise the decal lift above block tops (BloodPuddle.plane/printQuad) and wall faces (BloodWallMark) so they no longer sit in the fighting plane of the surface they're drawn on.
- Push BloodItemOverlay's blood faces off the item's own faces (OVER constant).
- Cull puddles by their on-screen bounds, not just their centre point, so they don't pop out at the screen edge.
- Confirm existing fade-in (splat-in grow) and fade-out; review BloodCoatLayer (painted skin over skin geometry).
- sh ./gradlew build + tools/pr_checks.py; open PR; not playtested in game (shaders/Fresh Animations poses unverifiable here).

## Notes
- Candidates found: plane lift (zFight+0.08)*max(0.05, alpha-0.3)*0.05 -> shrinks to ~0 as it fades, i.e. on the block; printQuad ~0.004-0.006; WallMark LIFT 0.003; ItemOverlay OVER 0.001.
- Open PR versions taken: hollow-turning-follow-ups 0.21.52, fishing-real-tug 0.21.53, parcool-traceur-gear 0.21.54 -> take 0.21.55 right before opening the PR.

## Done
Blood on the ground, walls and weapons no longer z-fights with the surface it sits on; puddles near the screen edge stay in view until their whole quad is out instead of popping out. Decal lift (0.06 + per-decal jitter), wall-mark lift (0.015) and item overlay offset (0.02) are static final constants; puddle culling uses the quad's bounds, not just its centre. Version 0.21.55, MODLOG entry appended.

Not tested in game: no client run here; Complementary shader interaction and Fresh Animations/EMF pose compliance for BloodCoatLayer were not verified. `sh ./gradlew build` passes and tools/pr_checks.py passes.

PR #1124 ready for review; not merged.
---
- 2026-11-08 12:44 PR #1124 is merged into master; moved to Agents/Done.
