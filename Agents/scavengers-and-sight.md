---
status: in-review
task: scavengers eat limb by limb, distracted mobs, humanoid vision cones
branch: scavengers-and-sight
with: Claude (desktop, Opus)
pr: 2033
mods: kitchen, kingdoms
versions: 
started: 2026-11-23
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Scavengers.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java (bite counter, part taking)
  - kitchen/src/client/java/com/jaymoskitchen/client/corpse/Severed.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Senses.java
---
# Scavengers and sight

**Task:** Andy (2026-11-23, clip of vultures eating his body while he was in Limbo): vultures land and pick a body apart
limb by limb until disturbed; every scavenger and body eater goes a limb at a time; mobs busy with something (eating)
notice sneaking players later and fly off, maybe with a limb in their mouth. A pillager caravan saw him too well, even
outside their sight: MCA and humanoids see in a cone and hear what's at their sides.

## Plan
- Kitchen: Scavengers eat a part at a time (arms, legs, head, then the trunk); vultures land; disturbed feeders flee with the limb.
- Kingdoms: Senses: humanoids see nothing outside their cone (hearing only); busy mobs see less.

## Notes
- 

## Done
PR #2033 (Kitchen + Kingdoms change notes). Bodies are eaten a part at a time; fliers land; 2 eaters at once; driven
off by monsters/bigger scavengers, skittish ones by players (feeding sight 40%, 1 s reaction), maybe with a part in
the mouth. Humanoids see nothing outside the cone past 2 blocks (hearing only); busy mobs see/hear half as far.
Not built on the PC (CI builds); not played.
