---
name: lineage
description: Lineage replaced the Blood Rite (PR #479 merged, Kingdoms 0.7.189 / pack 0.15.154): sneak-use your teen+ MCA child to live on as them; Andy's rules for blood, powers, items
metadata:
  type: project
  modified: 2026-10-24T16:26:12.431Z
---
2026-10-24 Andy asked for a lineage that replaces blood rites, then refined it (16:19Z):
- A player's child is born to a random pick from {player's heritage + every earned heritage they've woken} ("isnt directly influenced"). `VillagerHeritage.passed`.
- Shift-right-click your own child, teen or older → prompt "progress your bloodline's story?" → swap. No heritage picking.
- No keeping powers: the power is handed down as the child's affinity (Powers `Affinity.INHERITED`); they still have to awaken it (3-5 days).
- Player chooses: hand items down (keep them as the child) or leave them on the elder (worn + MCA pack).
- The old body lives on as an autonomous elder villager (old look/name/heritage): MCA follow for adventures, and `Elders` journeys (2-5 days home, 1-3 days at a far MCA village or realm seat 250-1500 blocks off, walked for real: in a road record while unseen, put back in the world near players (80 blocks), like Caravans; MCA letters to the heir). Andy asked 19:57Z.
- Andy: a Nightborn mother's child is Nightborn 70% of the time (`VillagerHeritage.NIGHTBORN_MOTHER_CHANCE`).
- Class cleared and asked again; skills respecced (XP kept). Old blood_rite items crumble.
- PR #479 MERGED 2026-10-24 22:10Z as pack 0.15.154 / Kingdoms 0.7.189, not playtested. Riskiest part: Elders taking a walker out of the world and back. Related: [[attributes-heritage]], [[old-age]], [[mca-in-pack]].
