---
status: working
task: thrown-ragdoll-perch
branch: claude/magical-diffie-b83781
with: 
pr: 
mods: powers, kitchen
versions: 
started: 2026-11-08
owns:
  - src/main/java/com/jaymospowers/core/Carry.java
  - src/main/java/com/jaymospowers/core/ThrowWeight.java
  - src/main/java/com/jaymospowers/core/Perch.java
  - src/main/java/com/jaymospowers/mixin/PerchRidingMixin.java
  - src/client/java/com/jaymospowers/client/CarryPose.java
  - src/client/java/com/jaymospowers/client/ThrownFlight.java
  - src/client/java/com/jaymospowers/client/PerchClient.java
  - src/client/java/com/jaymospowers/client/mixin/CarryUseMixin.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java (toss only)
---

# Thrown bodies ragdoll, throws have weight, small animals perch on you

**Task:** Andy: thrown entities ragdoll mid-air; every thrown entity has weight; small animals (parrot, cat, butterfly, cricket) ride on you with hands free.

## Plan
- Carry throw: weight scale from ThrowWeight (per-type kg, else hitbox bulk); thrown players knocked limp into Kitchen's ragdoll; mobs tumble/flail via ThrownFlight
- Perch: sneak+use on #jaymospowers:rides_on_shoulder rides the player (shoulder/head/draped); sneak+empty use lets down; 3 hearts of damage knocks off
- Kitchen corpse toss on the same weight curve
