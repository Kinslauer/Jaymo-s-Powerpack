---
name: townsfolk-oddities
description: Villagers react to meteors, stars, symbiotes, shades, powers in use, strange moons (PR #124 MERGED 2026-10-13 21:13Z, pack 0.9.24 / Kingdoms 0.6.11)
metadata:
  type: project
  modified: 2026-10-13T17:49:32.330Z
---
2026-10-13 Andy: "villagers should react to meteors, symbiotes, alien forms and anything abnormal".
PR #124 (branch claude/project-thread-uih4lc) merged unplaytested. Andy: "the more lines the better, never the same line twice": 12 lines per family, dealt as a shuffled deck (`Oddities.line`, LINES=12). Powers `core/Oddities` puts entity tags
`jaymospowers_odd:meteor|star|symbiote|shade|power/<id>` (players' tags lapse unless held). Kingdoms `Oddities` reads
them: stare/cry at sky rocks, flee impact (`Frights.scare`), remark on powers, gossip 3 days (Approaches GOSSIP).
`Aliens.KINDS` extends the alien rule to suit + shade (guards hunt until the kingdom sees that change).
**How to apply:** new strange things (e.g. disasters) just need Powers/Kingdoms to tag `jaymospowers_odd:<kind>` and
lang `talk.jaymoskingdoms.odd.<kind>.0-11` (12 lines each). See [[xenoform-plan]], [[villager-speech]].
