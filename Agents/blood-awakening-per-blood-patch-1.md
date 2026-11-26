---
status: stalled
task: blood-awakening-per-blood-build
branch: t3/blood-awakening-per-blood-patch-1
with:
pr:
mods: [powers, kingdoms]
versions:
started: 2026-11-26
owns:
  - docs/Blood-Awakening-Per-Blood-Plan.md
  - kingdoms/src/main/java/com/jaymoskingdoms/HeritageKits.java
  - kingdoms/src/main/java/com/jaymoskingdoms/BlastMend.java
  - kingdoms/src/main/java/com/jaymoskingdoms/FrostPrison.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Darkvision.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/DarkvisionClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/SkillScreen.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/post_effect/darkvision_far.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/blood-awakening-upgrades.json
  - src/main/java/com/jaymospowers/power/heritage/HeritageMoves.java
  - src/main/java/com/jaymospowers/power/zfighter/FrostBeam.java
  - changes/kingdoms/blood-awakening-per-blood-patch-1.md
  - changes/powers/blood-awakening-per-blood-patch-1.md
---
# Blood Awakening per-blood upgrades (Patch 1)

**Task:** Split Andy's 90 ticked Blood Awakening per-blood upgrades (+ Emberborn retry, + Scalebound) into heritage-group patches in `docs/Blood-Awakening-Per-Blood-Plan.md` and build Patch 1 (Plain & Mountain Folk + Elemental/Sky Core: 14 bloods/looks, 28 rows).

## Plan
- Write `docs/Blood-Awakening-Per-Blood-Plan.md` covering all 48 bloods/looks across 5 patches (2 tracks per patch).
- Implement Patch 1 in Kingdoms (`HeritageKits`, `BlastMend`, `FrostPrison`, `Darkvision`, `DarkvisionClient`, `SkillScreen`) and Powers (`HeritageMoves`, `FrostBeam`), gated strictly behind `TreeLocks.awakened(player)` and stacking on top of PR #2117's shared rule.
- Add lang keys, change notes, run Gradle builds for both mods and `tools/pr_checks.py`, open PR with `(new)` lore paragraph, and note the PR in the vault task.

## Notes
- Fast-forwarded branch from `4c0ae8eafc` to `origin/master` (`b30ce055e9`) before starting.
- Obsidian's `ba-obsidian-aura` hooks `BlastMend.noted` (already called by `ServerExplosionMixin`) so it rewinds blast block damage like the court wizard's ward (PR #2222) without conflicting with PR #2222's mixin edit.

## Done
In progress.
- 2026-11-26 15:34 No sign of work for 30 min: marked stalled (whoever ran it stopped).
