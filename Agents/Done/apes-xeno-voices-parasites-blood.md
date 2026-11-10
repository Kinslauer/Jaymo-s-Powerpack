---
status: done
task: apes-xeno-voices-parasites-blood
branch: claude/apes-xeno-voices-parasites-blood
with: 
pr: 1241
mods: powers, kingdoms
versions: 
started: 2026-11-10
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterLineage.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPower.java
  - src/main/resources/assets/jaymospowers/lang/parts/wounds.json
  - src/main/java/com/jaymospowers/core/wounds/Afflictions.java
  - src/main/java/com/jaymospowers/core/wounds/Herbal.java
  - src/main/java/com/jaymospowers/core/wounds/herbs/Herbs.java
  - src/main/java/com/jaymospowers/power/web/KitchenCompat.java
  - kingdoms/build.gradle
  - kingdoms/src/main/java/com/jaymoskingdoms/client/VoiceChangerCompat.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Awareness.java
  - kingdoms/src/main/java/com/jaymoskingdoms/PowersCompat.java
  - kingdoms/src/main/java/com/jaymoskingdoms/VoiceMasks.java
  - kingdoms/src/main/java/com/jaymoskingdoms/VoiceRange.java
  - kingdoms/src/main/resources/fabric.mod.json
  - changes/powers/apes-xeno-voices-parasites-blood.md
  - changes/kingdoms/apes-xeno-voices-parasites-blood.md
---
# Great apes unstuck and hearing the ground, titan voices for xeno forms, garlic for parasites, still blood

**Task:** Andy's four clips of 2026-11-09: add another way to clear parasites (raw wild garlic), fix Great Apes getting stuck and not hearing ground players when transformed, give big forms (Great Ape + every xeno form) a titan voice in Simple Voice Chat audible from the ground, and make blood spatter lie still instead of re-animating every second.

## Plan
- Powers: wild garlic eats like sweet berries, Herbal.garlicEaten purges Kitchen's parasite; ZFighterLineage lets apes step up as they grow so they don't stick on rises.
- Kingdoms: Awareness no longer re-sends blood drops (lasting stain instead); VoiceRange/VoiceMasks/VoiceChangerCompat force titan voice and extra range for big forms.
- Change notes for powers and kingdoms; verified with `sh ./gradlew clean build` at root and in `kingdoms/`, plus `tools/pr_checks.py`.

## Notes
- Continued the cloud agent's branch `cloud/apes-xeno-voices-parasites-blood` (PR #1241) from `origin/master`; merged real master (#1243 unique-starts lang fix) because CI's Build Kingdoms failed on a duplicate `story.hint.125` lang key.
- Reverted the cloud agent's unrelated `tools/vault_sync.py` guard and two doc notes so the PR touches only the task.
- All four GitHub checks are SUCCESS.

## Done
What a player will see: wild garlic clears parasites a third at a time; Great Apes step over rises and hear players on the ground; xeno forms speak with the titan voice carried farther; blood spatters lie still and dry. Not tested in game (no dev client/server was booted; the voice plugin is compiled only, never loaded by Simple Voice Chat).
- 2026-11-10 19:33 PR #1241 is merged into master; moved to Agents/Done.
