---
status: running
task: punch-barrage-effects
branch: claude/punch-barrage-effects
with: 
pr: 
mods: powers, kingdoms
versions: 
started: 2026-11-16
owns:
  - src/main/java/com/jaymospowers/power/chrono/ChronoPunch.java
  - kingdoms/src/main/java/com/jaymoskingdoms/spirits/SpiritBarrage.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/BarrageClient.java
---
# Punch and barrage effects

**Task:** Add pb-punch (heavy punch impact: shockwave ring, dust wave, short screen shake) and pb-barrage (barrage flurry: blurred fists, sparks where they land) using only typed effects from docs/MagicFx.md (Powers) and Kingdoms Fx. Hook into Powers ChronoPunch/GravityGutPunch/ZFighterRush last hits and Kingdoms SpiritPunch/SpiritBarrage clashes.

## Plan
- Read docs/MagicFx.md and the four suggested classes; check how existing powers already emit fx (ring/wave/stroke/mote/spark).
- Add pb-punch impact to Spirit Punch, GravityGutPunch, ZFighterRush, Chrono Punch last hit.
- Add pb-barrage flurry to Chrono Punch flurry and Kingdoms SpiritBarrage clashes with sparks between flurries.
- Build: sh ./gradlew clean build; python tools/pr_checks.py; changes/ notes per mod.
- Commit, push, gh pr create, vault.py --set status=in-review pr=<N>.

## Notes
- Do not copy code or art from the Cursed Fate add-on; build only from typed effects.
- One PR per mod (Powers vs Kingdom are separate projects) — check whether one PR can span both or split it.
- Not tested in game (builds go on Deck/CI, never Andy's PC).

## Done
What a player will see, and what wasn't tested.
