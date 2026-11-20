---
status: running
task: zfighter-gaps-1-stats
branch: claude/zfighter-gaps-1-stats
with: 
pr: 
mods: powers
versions: 
started: 2026-11-19
owns:
  - power/zfighter/ZFighterTraining.java
  - power/zfighter/ZFighterGrowth.java
  - client/power/zfighter/ZFighterTrainingScreen.java
  - power/zfighter/ZFighterRush.java
  - power/zfighter/ZFighterGrab.java
  - power/zfighter/ZFighterCombo.java
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
What a player will see, and what wasn't tested.
