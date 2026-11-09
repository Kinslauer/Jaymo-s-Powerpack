---
status: done-unmerged
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
Pushed to ccr-03d9471e-baz6ym (no PR yet: Andy compares it with Prometheus's minotaur first). Notus and Boreas animated by GeckoLib: idle, walk, sit, crash, land, glide, fly (synced to its stroke), new bite and tail sweep; head look, climb pitch and breath jaw in code. Built (kingdoms `gradlew build`), pr_checks pass; not played, no server run. Render: kingdoms/art/fire_dragon-render.png.
