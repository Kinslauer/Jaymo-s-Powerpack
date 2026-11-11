---
status: in-review
task: ape-giant-trees-flying-screenshake-dust
branch: claude/ape-giant-trees-flying-screenshake-dust
with:
pr: 1256
mods: [powers, kingdoms]
versions:
started: 2026-11-10
owns:
  - src/main/java/com/jaymospowers/core/GiantTrample.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterLineage.java
  - kingdoms/src/main/java/com/jaymoskingdoms/remnants/AetherGiant.java
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
- Giants exist under Kingdoms (com.jaymoskingdoms.remnants.AetherGiant)
- Use existing particle types: ParticleTypes.SMOKE_NORMAL / ParticleTypes.DUST
- Keep changes modular: each feature can be separate PR but share entity touch/step events

## Done
**What the player will see:**
1. **Trees flying on sprint hit**: When a Great Ape or giant sprints into a tree, the tree is torn up and thrown outward, sending logs/wood items flying in the sprint direction.
2. **Massive scaling screenshake**: Screen shake from Great Ape stomps and Aether Giant footfalls/slams/stamps scales with entity size — larger entities produce more intense, longer-range shakes.
3. **Scaled dust particles**: Dust clouds at feet on steps/landings scale with entity size. Giants kick up larger, longer-lasting dust clouds.

**What wasn't tested in game:**
- Sprint hit tree flying with Kingdoms' FallingTree entities
- Sprint hit tree flying without Kingdoms (fallback log breaking)
- Scaling screenshake intensity at different Great Ape sizes
- Scaling dust particles on Aether Giant footfalls
- Multiplayer screenshake synchronization