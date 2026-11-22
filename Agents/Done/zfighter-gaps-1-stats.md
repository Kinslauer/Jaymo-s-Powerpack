---
status: done
task: zfighter-gaps-1-stats
branch: claude/zfighter-gaps-1-stats
with: 
pr: 1958
mods: powers
versions: 
started: 2026-11-20
owns:
  - power/zfighter/ZFighterTraining.java
  - client/power/zfighter/ZFighterTrainingScreen.java
  - power/zfighter/ZFighterRush.java
  - power/zfighter/ZFighterGrab.java
  - power/zfighter/ZFighterCombo.java
  - power/zfighter/ZFighterBattle.java
  - power/zfighter/ZFighterNet.java
---

# Z Fighter gaps 1: stats

**Task:** Build the Z Fighter gap-checklist row 1 (stats): sixth stat for rush blows (Strike), critical blows, armour-piercing/wound-deepening blows, jump+sprint training, stat web on the training screen, weight+gravity readout. Ideas only (DMZ GPL-3.0, ours ARR/MIT, no copied code/art/Dragon Ball names).

## Plan
- Read ZFighterTraining.java, ZFighterGrowth.java, ZFighterTrainingScreen.java, ZFighterRush.java, ZFighterGrab.java, ZFighterCombo.java
- Check "Already in" on the checklist before building anything
- Implement stats + crits + piercing + wound-deepening + jump/sprint training + stat web + weight/gravity readout
- Add changes/powers/zfighter-gaps-1-stats.md
- sh ./gradlew build + tools/pr_checks.py, commit, push, open PR, set in-review

## Notes
- Do NOT touch ZFighterPower.java, ZFighterKiAura*.java, ZFighterAuraFlame.java (owned by open aura PR #1911).
- One PR, branch claude/zfighter-gaps-1-stats, change note changes/powers/zfighter-gaps-1-stats.md.
- No new original mobs; no slash commands; Abilities.md rows for every new input.
- Every Minecraft API checked against 26.3 sources.

## Done
What a player will see: a Strike stat on the training screen powering rushes, grab slams and hold-combo finishers (Strength stays for plain blows), plus Crit Chance and Crit Damage late TP buys that let landed blows crit for much heavier damage. Compile-checked and built, not played in game.
- 2026-11-20 PR #1958 opened: Strike stat + critical blows; sh ./gradlew build BUILD SUCCESSFUL; tools/pr_checks.py passes; not tested in game.
- 2026-11-22 08:57 PR #1958 is merged into master; moved to Agents/Done.
