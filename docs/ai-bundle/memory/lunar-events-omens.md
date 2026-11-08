---
name: lunar-events-omens
description: Lunar events (Blood/Super Blood/Harvest/Blue moons) + Bad Omen grudges in Kingdoms, PR #117 MERGED 2026-10-13 15:22Z (Kingdoms 0.6.6 / pack 0.9.15)
metadata:
  type: project
  modified: 2026-10-13T15:10:36.676Z
---
2026-10-13 Andy: random events inspired by Lunar + Enhanced Celestials (ideas only, ARR), and Bad Omen into a kingdom destroys rep; if they survive the raid they almost never forget.
Built in Kingdoms: `Moons` (roll at dusk, saved; colours via an EnvironmentAttributeSystem.Builder.addDefaultLayers mixin, moon tint/size via SkyRenderer.renderMoon mixin; op `/kingdom moon <id|clear>`), `Omens` (rep wiped -400, grudge saved on Overworld: gains x0.1, ceiling -50, 1%/day forgiven).
Draft PR #117 opened 2026-10-13, not playtested. 15:20Z Andy: blood moon zombies/skeletons rise out of the ground (Moons.rise: frozen noPhysics climb, freed on SERVER_STOPPING); only those two spawn. Master merged in at pack 0.9.13 / Kingdoms 0.6.5. Jar: /mnt/project-files/lunar-events/.
Related: [[kingdoms-war-tempers]], [[cloud-build]].
