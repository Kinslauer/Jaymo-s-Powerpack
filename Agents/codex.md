---
status: stalled
task: legends-p6-core-animations
branch: codex/legends-p6-core-animations
pr:
mods: powers
started: 2026-11-26
owns:
  - src/main/resources/assets/jaymospowers/player_animations/legends_core/
  - src/main/resources/assets/jaymospowers/player_animations/katana_forms.json
  - src/main/resources/assets/jaymospowers/lang/parts/katana-forms.json
  - src/main/java/com/jaymospowers/freerun/
  - src/main/java/com/jaymospowers/combat/KatanaForms.java
  - src/main/java/com/jaymospowers/core/jump/PowerJump.java
  - src/main/java/com/jaymospowers/mixin/ImpactShakeMixin.java
  - src/main/java/com/jaymospowers/JaymosPowers.java
  - src/client/java/com/jaymospowers/client/JaymosPowersClient.java
  - src/client/java/com/jaymospowers/client/freerun/
  - src/client/java/com/jaymospowers/client/compat/CoreMotionPal.java
  - src/client/java/com/jaymospowers/client/combat/KatanaFormsClient.java
  - src/client/java/com/jaymospowers/client/combat/ArmedPose.java
  - src/client/java/com/jaymospowers/client/moving/BodyWeight.java
  - src/client/java/com/jaymospowers/client/power/web/WebBodyPose.java
  - src/client/java/com/jaymospowers/client/power/web/WebParkourPose.java
  - src/client/java/com/jaymospowers/client/stamina/DodgeRollPose.java
  - changes/powers/legends-p6-core-animations.md
---
# Legends patch 6

Port Core's LGPL player and katana animation data. Connect the landings and shared acrobatics to Powers and the katana set to Weapon Forms. Build and send one PR to Claude for review.

## Notes
- 2026-11-26 21:03 No sign of work for 30 min: marked stalled (whoever ran it stopped).
