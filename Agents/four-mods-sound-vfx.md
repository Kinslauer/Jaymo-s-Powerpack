---
status: done
task: four-mods-sound-vfx
branch: claude/project-thread-5dj9k7 (sound), claude/project-thread-5dj9k7-vfx-test (VFX test)
with:
pr: 1303, 1305
mods: powers
versions:
started: 2026-11-11
owns:
  - src/client/java/com/jaymospowers/client/sound/Varied.java
  - src/client/java/com/jaymospowers/client/mixin/VariedSoundEngineMixin.java
  - src/client/java/com/jaymospowers/client/power/web/WebSenseGlance.java
---

# Four mods: sound patch and the ImmersiveVFX test

**Task:** first patch from the four-mods pick-list (https://claude.ai/artifact/VgfeFkbTT3gXw5xPxfVY1z). #1303 ships ImmersiveFX in place of AmbientSounds, adds varied one-shot pitches and the web sense glance. #1305 is a build.gradle-only trial of ImmersiveVFX in place of Iris and Complementary (revert to scrap). Both add to `removedIdsFromProfile`, so the second merged needs that line resolved. Still to build from the list: Divine-born and Aether villages, Eden Ring on the Verdant World, weather and looks.
