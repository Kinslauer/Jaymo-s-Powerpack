---
agent: gemini
status: done
branch: gemini/legends-p7-zfighter-weapon-moves
worktree: E:/wt/legends-p7-zfighter-weapon-moves
pr: 2255
task: legends-p7-zfighter-weapon-moves
updated: 2026-11-26
---

# Legends Patch 7: Z Fighter and weapon moves

## Owns
- `src/main/java/com/jaymospowers/JaymosPowers.java` (attachment registration)
- `src/main/java/com/jaymospowers/power/zfighter/ApeMoves.java`
- `src/main/java/com/jaymospowers/power/zfighter/ApeSmash.java`
- `src/main/java/com/jaymospowers/power/zfighter/ApeEarthwork.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterLineage.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterPower.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterKi.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterWaveBeam.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterHands.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterKiSense.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterInstantStep.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterOverburn.java`
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterNet.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterApe.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterBeams.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterHandsPose.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterFiringPose.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterKiAura.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterInputClient.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterPowerClient.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/mixin/ZFighterKiSenseGlowMixin.java`
- `src/client/java/com/jaymospowers/client/power/zfighter/mixin/ZFighterKiSenseOutlineMixin.java`
- `src/main/java/com/jaymospowers/armory/Lash.java`
- `src/client/java/com/jaymospowers/client/armory/LashRenderer.java`
- `src/main/resources/assets/jaymospowers/lang/parts/zfighter.json`
- `src/main/resources/assets/jaymospowers/lang/parts/mortal.json`
- `kingdoms/src/main/java/com/jaymoskingdoms/forms/BladeForms.java`
- `kingdoms/src/main/java/com/jaymoskingdoms/forms/Schools.java`
- `kingdoms/src/main/resources/data/jaymoskingdoms/spell/form_cross_slash.json`
- `kingdoms/src/main/resources/data/jaymoskingdoms/tags/spell/weapon_forms.json`
- `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/cross_slash.json`
- `changes/powers/gemini-legends-p7-zfighter-weapon-moves.md`
- `changes/kingdoms/gemini-legends-p7-zfighter-weapon-moves.md`

## Done
- Implemented all six Patch 7 features (Great Ape moves & poses, staged beam firing + red target outlines, Instant Step, Overburn stacking power-up, Chain Blades pull & cyclone, Cross Slash blade form).
- Built `compileClientJava` for both Powers and Kingdoms (both succeeded).
- Ran `python tools/pr_checks.py` (passed, 33 changed files against `origin/master`).
- Opened PR #2255 to `master` (not merged; awaiting Claude review).

## Notes
- 2026-11-27 05:58 PR #2255 is merged into master; moved to Agents/Done.
