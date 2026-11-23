---
status: done
task: enemy-attack-telegraph
branch: claude/enemy-attack-telegraph
with: local_29ede7b7-7e11-4e20-8506-df7674edc787
pr: 2042 (merged 2026-11-23)
mods: powers, kingdoms
versions: 
started: 2026-11-23
owns:
  - src/main/java/com/jaymospowers/combat/AttackTells.java
  - src/main/java/com/jaymospowers/mixin/AttackTellGoalMixin.java
  - src/main/java/com/jaymospowers/mixin/AttackTellBrainMixin.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MobCombat.java (START_REACH_SHARE only)
---
# Enemies telegraph attacks more and close in before swinging

**Task:** Andy: "enemies should telegraph attacks more and get closer before swinging."

## Plan
- Vanilla/modded melee (AttackTells): longer wind-up, arms held up through it, the arm swing on the blow, a short lunge in, and the wind-up only starts from closer than vanilla reach.
- Better Combat humanoids (Kingdoms MobCombat): start their swing from closer.

## Result
Merged as PR #2042 on Andy's word ("merge"). CI built all three mods and the pack-start check passed. Standalone dev boots on the Deck failed for reasons unrelated to this change: Powers' dev run rejects Stormie's Spiders (loader/fabric-api not seen), and Kingdoms' alone lacks Powers' items. Not played in game.
