---
status: stalled
task: legends-p7-zfighter-weapon-moves
branch: gemini/legends-p7-zfighter-weapon-moves
with: 
pr: 
mods: [powers, kingdoms]
versions: 
started: 2026-11-26
owns:
  - src/main/java/com/jaymospowers/JaymosPowers.java
  - src/main/java/com/jaymospowers/power/zfighter/ApeMoves.java
  - src/main/java/com/jaymospowers/power/zfighter/ApeSmash.java
  - src/main/java/com/jaymospowers/power/zfighter/ApeEarthwork.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterLineage.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPower.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterKi.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterWaveBeam.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterHands.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterKiSense.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterInstantStep.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterOverburn.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterNet.java
  - src/main/java/com/jaymospowers/armory/Lash.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterApe.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterBeams.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterHandsPose.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterFiringPose.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterKiAura.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterInputClient.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterPowerClient.java
  - src/client/java/com/jaymospowers/client/power/zfighter/mixin/ZFighterKiSenseGlowMixin.java
  - src/client/java/com/jaymospowers/client/power/zfighter/mixin/ZFighterKiSenseOutlineMixin.java
  - src/client/java/com/jaymospowers/client/armory/LashRenderer.java
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter.json
  - src/main/resources/assets/jaymospowers/lang/parts/mortal.json
  - kingdoms/src/main/java/com/jaymoskingdoms/forms/BladeForms.java
  - kingdoms/src/main/java/com/jaymoskingdoms/forms/Schools.java
  - kingdoms/src/main/resources/data/jaymoskingdoms/spell/form_cross_slash.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/spell/weapon_forms.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/cross_slash.json
  - changes/powers/gemini-legends-p7-zfighter-weapon-moves.md
  - changes/kingdoms/gemini-legends-p7-zfighter-weapon-moves.md
---
# Legends patch 7: Z Fighter and weapon moves

**Task:** Implement Legends Patch 7: Great Ape moves (swipes, roar, ground slam, leap smash, boulder throw with poses), staged beam firing with red target outline, Instant Step (two fingers to brow), stacking Overburn power-up on ZFighterKiAura, Chain Blades pull and cyclone on chain weapons, and Cross Slash Weapon Form for swords.

## Plan
- Great Ape moves (`ApeMoves` + `ZFighterApe` poses + hooks in `ApeSmash`, `ApeEarthwork`, `ZFighterPower`, `ZFighterLineage`)
- Staged beam firing (`ZFighterWaveBeam`, `ZFighterKi`, `ZFighterBeams`, `ZFighterHandsPose`, `ZFighterFiringPose`, red outline in `ZFighterKiSenseGlowMixin`/`ZFighterKiSenseOutlineMixin`)
- Instant Step (`ZFighterInstantStep`, `ZFighterKiSense` learned tracking, `ZFighterHands.BROW` pose in `ZFighterHandsPose`)
- Stacking power-up Overburn (`ZFighterOverburn`, `ZFighterInputClient`, `ZFighterKiAura`, `ZFighterPowerClient`)
- Chain Blades (`Lash` pull & cyclone, `LashRenderer` cyclone & pull rendering, tooltips)
- Cross Slash (`BladeForms`, `Schools`, `form_cross_slash.json`, `weapon_forms.json`, `cross_slash.json`)

## Notes
- Ideas only from Legends add-ons (All Rights Reserved): all code, poses, and names are original.
- Cross Slash is a Weapon Form for swords, which lives in Kingdoms (`forms/BladeForms`), so both Powers and Kingdoms change notes and builds are included.

## Done
In progress.
- 2026-11-26 20:37 No sign of work for 30 min: marked stalled (whoever ran it stopped).
