---
name: boss-posture-dawncraft
description: Gemini's DawnCraft boss handoff (2026-11-04): boss posture + Knowledge boss ladder (5 levels gating anvils/smithing/enchanting/higher arts/End) MERGED 2026-11-04 01:03Z as PR #889, Kingdoms 0.7.492
metadata:
  type: project
---
2026-11-04 Andy pasted a Gemini 1.21.1 handoff (DawnCraft boss ladder, workstation locks, 12 eyes, generic poise boss + model)., PR #889 (Kingdoms 0.7.492) MERGED 2026-11-04 01:03Z, unplayed.
- `BossPosture`: bosses kneel when posture breaks; next melee blow 2.25x riposte; Posture bar under boss bar.
- `Knowledge` (Andy: "tying combat directly into progression"; he picked levels 1-2 as Gemini had them, 3 enchanting alone, 4 higher spells/techniques, 5 End portal; told me to tie into world tiers). Bosses chosen by me (flagged): 1 Goblin King (anvils incl. iron Smithing Anvil, Stone Anvil open; bog iron), 2 Ogre King (smithing table + heavy-arms forgings), 3 Skeleton Lord (enchanting table), 4 Piglin King (learning scroll spells, Storm Arts, perks minLevel>=15), 5 Wither (End frames). Stored as WorldTiers.Progress KNOW_1..5 feat bits; tiers want Borderlands 1, Wildlands 2, Dread 4, Legend 5. One-time grandfather on join (attachment knowledge_checked), world floor gives none.
- Dropped Gemini's entity/model/renderer and 12 eyes.
- Gemini: key works via `?key=` query param (header gets proxy bearer, 401); Pro/Argon free-tier quota 0; handed to the Gemini thread. See [[claude-gemini-tandem]].
Related: [[apotheosis-world-tiers]], [[gemini-handoff-ki-forge]].
