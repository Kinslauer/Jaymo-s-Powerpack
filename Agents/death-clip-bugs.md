---
status: done
task: death-clip-bugs
branch: claude/sad-hodgkin-7d6dce
with: Claude (side session of local_2bfd1f34)
pr: 2010
mods: powers, kingdoms, kitchen
versions:
started: 2026-11-22
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Scavengers.java
  - src/main/java/com/jaymospowers/core/LimbEaters.java
  - kingdoms/src/main/java/com/jaymoskingdoms/RubberFolk.java, BioBuilt.java, Heritage.java (one line), PowersCompat.java (dead)
  - src/main/java/com/jaymospowers/core/wounds/Mortality.java (dead), power/zfighter/ZFighterLineage.java, power/web/WebSymbioteMend.java, power/web/KitchenCompat.java
  - src/client/java/com/jaymospowers/client/McaPlayerHeads.java, McaHeadOwner.java, mixin/McaHead*Mixin.java, mixin/LivingRendererScaleInvoker.java
---
# Death clip bugs (2026-11-22)

**Task:** Three bugs from Andy's death clip: zombies crowd his corpse in daylight instead of seeking cover; mob heads draw the
default player skin instead of the MCA look; a Rubber-folk in Limbo kept regrowing limbs.

## Plan
- Find each cause in code, fix, one PR with change notes per mod.

## Notes
- Clip (2026-11-22 16-45-20, 30 s): "You are in Limbo", and "A lost limb stretches back into place" shows twice. Burning
  zombies round his torn-up remains at dawn. Captain "Sir Aldous the Hollow" (skeleton) wears a player head.

## Done
PR #2010. Causes: Kitchen's GnawGoal ran at the zombie shelter search's priority (1), so it couldn't be cut off;
vanilla PLAYER_HEAD skulls draw the account skin, never MCA; `isAlive()` is true in Limbo and while left for dead.
Built by CI only; nothing seen in game (head placement/facing especially).
