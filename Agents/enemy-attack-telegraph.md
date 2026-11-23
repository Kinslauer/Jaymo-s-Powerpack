---
status: working
task: enemy-attack-telegraph
branch: claude/youthful-villani-f039c2
with: local_29ede7b7-7e11-4e20-8506-df7674edc787
pr: 
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
