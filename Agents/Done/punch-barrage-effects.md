---
status: done
task: punch-barrage-effects
branch: claude/punch-barrage-effects, claude/punch-barrage-effects-kingdoms
with: 
pr: 1665, 1667
mods: powers, kingdoms
versions: 
started: 2026-11-16
owns:
  - src/main/java/com/jaymospowers/power/chrono/ChronoPunch.java
  - src/main/java/com/jaymospowers/power/gravity/GravityGutPunch.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRush.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Fx.java
  - kingdoms/src/main/java/com/jaymoskingdoms/spirits/SpiritPunch.java
  - kingdoms/src/main/java/com/jaymoskingdoms/spirits/SpiritBarrage.java
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
What a player will see: heavy punch impacts land with a shockwave ring, a dust wave along the ground and a short screen shake — on spirits' Spirit Punch, Gravity's Inversion (GravityGutPunch), the Z-Fighter rushes' finishers and Chrono Punch's last hit; Chrono Punch's flurry paints blurred fists in front of you and throws sparks where its punches land; Kingdoms' Spirit Barrage clashes throw sparks where the two flurries meet.
What wasn't tested: not played in game; compile-checked and sh ./gradlew clean build passes on both branches.
- 2026-11-17 08:34 PR #1665 is merged into master; moved to Agents/Done.
