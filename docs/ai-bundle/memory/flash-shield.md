---
name: flash-shield
description: Flash speed on Chrono V (Overclock) + Shield Throw Strength combat art, PR #153 (pack 0.10.28 / Kingdoms 0.7.3), from heropack ideas
metadata:
  type: project
  modified: 2026-10-14T08:38:30.467Z
---
2026-10-14: Andy asked about the Hell's Kitchen heropack (Fisk's Superheroes 1.7.10, ARR) and Symbiotes Unleashed (Forge 1.20.1, ARR): neither usable, ideas only.
Andy picked: Flash speed added to Chrono's V, and a Captain America shield throw as a Strength tree art.
PR #153 MERGED 2026-10-14 08:59Z as pack 0.10.28 / Kingdoms 0.7.3 (branch claude/project-thread-2d825y): `ChronoSpeed` (sprint x2.2, step +0.65, V again phases through up to 4 blocks), `ChronoSpeedClient` wall run (client-side), `ChronoWaterRunMixin` (canStandOnFluid on water while sprinting). Kingdoms `ShieldThrow` (perk SHIELD_THROW, appended last in Skills.Perk; Y with any ShieldItem, item display flight). Andy 08:45Z: only relic shields (Kingdoms Relics passive, Powers grand_shield or arsenal_relic component) always come home; plain shields drop where they stop if not caught.
Not playtested. Jars in /mnt/project-files/flash-shield/. Related: [[combat-arts]].
