---
status: running
task: defence-disciplines
branch: claude/defence-disciplines
with: 
pr: 
mods: powers, kingdoms
versions: 
started: 2026-11-22
owns:
  - src/main/java/com/jaymospowers/combat/Guard.java
  - src/main/java/com/jaymospowers/combat/PassiveBlocks.java (new)
  - src/main/java/com/jaymospowers/stamina/Feathers.java
  - src/client/java/com/jaymospowers/client/stamina/FeathersHud.java
  - src/client/java/com/jaymospowers/client/combat/GuardClient.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Disciplines.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/MobParry.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MobShields.java
---
# The three disciplines of defence

**Task:** build Inbox/defence-disciplines.md (passive blocks by discipline: Arms, Ki, Arcana; active block; tighter parries that give blocks back; fist disarm and Snatch; clean-hit flinch; enemies' blocks by world tier). Andy (2026-11-22): "add the same capabilities to MCA when you build", so armed MCA villagers get blocks in their heritage's discipline too.

## Plan
- Stage 1 (Powers): passive blocks in Guard (a discipline hook Kingdoms fills; without Kingdoms everyone has Arms, 1 block), the locked shield icons in Feathers + FeathersHud, fists 10% unless ki/Valor, fist-parry disarm + Snatch hook, the tighter parry windows with a Keen Timing hook, refunds on PARRIED, the clean-hit flinch.
- Stage 2 (Kingdoms): Disciplines (levels, inclination by class, heritage leans, teachers, XP), Keen Timing / Snatch / Iron Guard skills, the K screen.
- Stage 3 (Kingdoms): enemies' blocks by tier and experience, by mob kind; armed MCA villagers by heritage lean; lock-on pips.

## Notes
- 

## Done
