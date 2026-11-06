---
name: nether-faction
description: Nether faction (own remake of Just-In NETHER, ARR): stage 1 Hornkin merged #373; stage 2 war forts merged #387 2026-10-21 (Kingdoms 0.7.142 / pack 0.15.55); stage 3 raids+bounties+trophies+truce MERGED #390 2026-10-21 (Kingdoms 0.7.147 / pack 0.15.61); all 3 stages done
metadata:
  type: project
---
2026-10-20 Andy: "I want a new nether faction along the lines of" Just-In NETHER (ARR, NeoForge 1.21.1/Forge 1.20.1: bullkin vs piglins over gold, two bosses).
Plan /mnt/project-files/nether-faction/Nether-Plan.md: stage 1 Hornkin mobs + gold feud; stage 2 Nether forts + Hornkin Warlord / Piglin Warboss hosts; stage 3 portal raids + bounties.
Stage 1 = PR #373 MERGED (branch claude/project-thread-9ewa3l), 2026-10-21, unplaytested: kingdoms/nether/ (Hornkin warrior/hurler/brute/zombified, MagmaBomb, Nether registry), tools/gen_nether.py. Piglins provoked via brain ANGRY_AT memory. Emberborn left alone. Jar in /mnt/project-files/nether-faction/.
Stage 2 = PR #387 MERGED (2026-10-21, same branch): NetherForts (fort records as attachment on the Nether level, tagged members, hosts, routs; /kingdom netherfort found|host|info), FortBuildings, Hornkin WARLORD kind, piglin Warboss = boosted PiglinBrute. Dev-tested on runServer (needs temp empty c:ingots/silver + steel tags, pause-when-empty-seconds=-1).
Stage 3 = PR #390 MERGED (2026-10-21): PortalRaids (raiders keep portal/raid state on Hornkin, no zombifying, `/kingdom netherfort raid`), NetherTrophies (hornkin_horn paid by rulers in Bounties.claim, war_horn from fallen warlords), king quest CHIEF with target "netherfort:<id>" template king.nether.hornkin|piglin, Emberborn hand-over trade in Hornkin.mobInteract. Fallen forts still block founding within 240.
Name "Hornkin" is a working name (Andy may prefer Bullkin). Kingdoms factions are overworld-only (Factions.level()), so stage 2 forts need own machinery.
Thread: "Nether faction".
Related: [[orc-camps]], [[emberborn-fire-cold]].
