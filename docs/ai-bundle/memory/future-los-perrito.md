---
name: future-los-perrito
description: The future dimension (Chrono sneak+B to ruined Los Perrito City with feral zombie hordes), PR #197, pack 0.10.109
metadata:
  type: project
  modified: 2026-10-15T16:50:07.561Z
---
2026-10-14 Andy asked for a dimension made of Los Perrito City (Uospag, CurseForge 992693) with feral zombies, "going to the future in an apocalypse". Licence is CC BY 4.0 and its licensing.txt explicitly allows modpacks and modification; credit "Los Perrito City by Uospag" + CurseForge link, never imply endorsement.
Built in Powers `power/chrono/future/` (PR #197, 0.10.106): sneak+B (Chrono, non-starter) takes everyone within 4 blocks to `jaymospowers:future`; Time Rift 5 N of arrival goes home. Map is 764 MB / 1.07 GB unpacked, chunks really 1.19.2 (DataVersion 3120) despite the 26.3 tag: downloaded at runtime on first leap (or `/future fetch`), map arts moved to ids above the world's. "Feral zombies like corpses" = vanilla zombies + Kitchen scavenging (no feral zombie entity exists); FutureFerals makes faster hordes.
**How to apply:** dev runServer needs Stormie's Spiders dev:false and CameraPreset preLaunch removed locally (never commit). Not client-playtested.
2026-10-15 Andy's follow-up (same PR): Fast Forward only after your echo died for good (`future_echo_slain`, set in ChronoEchoes.collapse; echoes killable in the future too); echoes in the future carry TaCZ guns; 45% of hordes are Kitchen zombie players with random UUIDs (random default skins), 30% of those gunners (FutureGuns: traced shots, no ammo).
2026-10-15 Andy: "only want the modern guns in the future, but dont load them anywhere but that dimension. not even in jei or creative. also spawn up to 4 echos": TaCZ default pack loads again (TaczGuns.trimDefaultPack exports it minus gun_smith_table index, recipes, loot injector), hidden from creative tabs/JEI (hideModern), confiscated outside the future (FutureContraband). Gunners use 51 modern guns. Up to 4 future-born echoes (tag jaymospowers.future_echo; don't follow home, fade when owner leaves). Retook version 0.10.108 (master was 0.10.107).
2026-10-15 Andy reversed: guns you get out of the future stay yours (no confiscation; still hidden from creative/JEI); echoes CAN follow you home armed ("make sure you arent followed"); Time Rift removed, only a Chrono or death gets you out. Now 0.10.109.
2026-10-15 00:57Z: PR #197 MERGED as pack 0.10.111.
2026-10-15 PR #235 (pack 0.11.38): loading shows only "The future is forming..." (Andy: leave it vague); always night (own timeline jaymospowers:future_night); map signs/billboards at arrival removed; `/future start save [radius]` snapshots arrival square, restored on each leap into an empty future (FutureStart, file in world's future folder); no spiders in the future; 65% of corpse zombies armed; ferals 0.75x speed when watched, sprint 1.5x when you look away.
Same PR later: gunners drop melee goals (MobGoalsAccessor) and hold range; dropped gun <=1/4 mag; 20% ammo drop skewed low, rarely up to 64; future has only zombies + player zombies (no husks/zombie villagers/camels/spiders).
Same PR: plain future zombies get 16 own civilian skins (tools/gen_future_zombies.py, FutureZombieRendererMixin, by UUID).
Same PR: androids (FutureAndroid, 3 shells, 75% armed) + war mech (FutureMech, golem frame x1.5, cannons/shells/boss bar) spawned by FutureMachines.check; skins tools/gen_future_machines.py.
2026-10-15 13:3xZ: PR #235 MERGED as pack 0.11.55.
2026-10-15 Andy: zombie players rarer (CORPSE_SHARE 0.12), always armed (unarmable ones not spawned), far less accurate (FutureGuns ZOMBIE_STRAY_SCALE 3 + 4 deg, Zombie gunners only); rest of horde 40% husks (feral-tagged husks allowed by Future.unwanted) / 60% civilian zombies. PR #267 MERGED 2026-10-15 ~16:50Z as pack 0.11.80.
