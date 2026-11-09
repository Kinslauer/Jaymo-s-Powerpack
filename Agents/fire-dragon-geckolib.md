---
status: running
task: 
branch: ccr-03d9471e-baz6ym
with: Prometheus's minotaur-geckolib (Andy compares the two)
pr: 
mods: kingdoms
versions: 
started: 2026-11-09
owns:
  - kingdoms/art/fire_dragon.bbmodel
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/**/fire_dragon.*
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/FireDragon* (new GeckoLib renderer)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/DragonsClient.java (Notus/Boreas lines)
  - kingdoms/src/main/java/com/jaymoskingdoms/dragons/NotusDragon.java, BoreasDragon.java
---
# Fire Dragon (Notus, Boreas) on GeckoLib

**Task:** Andy: "Ill have you work on the fire dragon and we will compare when both are done" (beside Prometheus's minotaur). Move nyxaris' Fire Dragon (Notus and Boreas) from our BbModel loader + posing in code to GeckoLib, with keyframed animations.

## Plan
- bbmodel source in kingdoms/art, animations authored, GeckoLib files, GeoEntity, GeckoLib renderer keeping hides, markings, saddle, riders.

## Notes
- 

## Done
