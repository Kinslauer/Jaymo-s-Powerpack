---
status: running
task: ape-giant-trees-flying-screenshake-dust
branch: claude/ape-giant-trees-flying-screenshake-dust
with:
pr:
mods: [powers, kingdoms]
versions:
started: 2026-11-10
owns:
  - src/main/java/com/jaymospowers/entity/ape/
  - src/main/java/com/jaymoskingdoms/beast/
---

# Ape/Giant: Trees Flying, Screenshake, Dust on Steps/Landings

**Task:** When great apes or giants sprint into trees, the trees should be knocked flying/broken with wood/log items flying outward. Massive scaling screenshake should occur based on entity size. Dust particles should spawn at feet on steps and landings, scaled to entity size.

## Plan
- Scan existing ape entity code in `src/main/java/com/jaymospowers/entity/ape/`
- Scan giant entity code in `src/main/java/com/jaymoskingdoms/beast/`
- Find existing screenshake utilities in the mod
- Find existing tree/wood block breaking patterns
- Implement tree-flying on sprint hit
- Implement scaling screenshake
- Implement dust particles on steps/landings
- Build and test each feature

## Notes
- PR #1241 (apes-xeno-voices-parasites-blood) recently added voice/titan changes for ape forms
- Giants exist under Kingdoms (com.jaymoskingdoms.beast)
- Use existing particle types: ParticleTypes.SMOKE_NORMAL / ParticleTypes.DUST
- Keep changes modular: each feature can be separate PR but share entity touch/step events

## Done
What a player will see, and what wasn't tested.