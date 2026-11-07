---
name: immersion-mods-0152
description: Andy's 9 immersion mods (2026-10-14): Shoulder Surfing replaced Camera Utils + our shoulder view; spiders, portal spread, tool belt; own burning torches + dark nights; PR #167
metadata:
  type: project
  modified: 2026-10-14T16:33:16.039Z
---
2026-10-14 Andy asked which of 9 mods fit. PR #167 MERGED 2026-10-14 17:2xZ as pack 0.10.65 / Kingdoms 0.7.17:
- Shoulder Surfing Reloaded (MIT, CF 243190) is IN. It "breaks" camerautils, so Camera Utils is OUT (in removedIdsFromProfile). Our ShoulderCamera + Shoulder* mixins were deleted (SSR redirects the same Hud isFirstPerson call). client/CameraPreset now writes shouldersurfing-client.toml once (marker camera-preset-v2): offsets -0.9/0.1/3.4, decoupled_camera=false (powers aim along the body), replace_default_perspective=true.
- KeyConflicts v3: tool belt R->Caps Lock, SSR free look LAlt->RAlt (OK Zoomer is on LAlt), swap shoulder U->O (werewolf on U).
- Added: Stormie's Spiders (LGPL fork of Spiders 2.0), Nether Portal Spread (Serilum), Traveler Tool Belt (gigaherz Tool Belt is NeoForge-only).
- Remade in Kingdoms: Torches (Realistic Torches idea: player-placed torches burn 2 days, rain douses, Burnt-out Torch relit by flint/fire charge/torch) and client/DarkNights (Total Darkness idea: night sky light scaled by moon phase; lunar events = full).
- Turned down: Mo' Bends (fights FA/EMF/our poses), Immersive Portals (1.21.1), Better Third Person (1.21.8; SSR breaks with it).
- Follow-up same PR: Fancy World Animations (MIT, CF 1452982) and Ji AFK Cinematic (packModsNoCf, CF 1697412 lacks 26.3) added; Kingdoms wild/SpiderKinds = Spider Overhaul (ARR, 26.2) ideas on vanilla spiders by biome (birch pounce, desert spines, swamp bog, cavern gems+web, jungle poison/pollen, ice freeze), own skins via SpiderSkinMixin (tools/gen_spider_kinds.py). Andy then asked about Mo' Bends for spider limbs: own client/mixin/SpiderLegsMixin gives every spider leg a thigh+shin knee and a walking gait (pushed 2026-10-14, merged with PR #167).
Related: [[camera-utils-f7]] (superseded), [[fresh-animations-player]].
