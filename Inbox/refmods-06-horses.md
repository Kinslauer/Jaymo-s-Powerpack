---
status: in-review
for: any
from: coordinator
asked: 2026-11-24
thread: Build Reference Mods pick list (cloud); queued by Hermes Training
claimed_by: 
pr: 2120
suggested_owns:
  - build.gradle (Icy's Better Horses dependency)
  - kingdoms equestrian package (whole)
---
# Reference Mods Patch 6: Horses: Icy's Better Horses goes in, ours off

**Andy's decision (2026-11-24):** "icy has far better models and implementations, if we cant use it;s models then put icy's in instead"
Chosen: put Icy's Better Horses in whole and switch our overlapping horse systems off (spec below, rewritten for this).

**Do not run in parallel** with any patch touching stables, caravans or quests (this one owns build.gradle and the whole
kingdoms equestrian package). Full plan: Picklists/ReferenceMods-Plan.md, "## Patch 6". Change note per mod.

## Patch 6: Horses: Icy's Better Horses goes in, ours off
Owns: build.gradle pack mods; kingdoms equestrian (Breed, Gait, Stride, Bond, Bonding, Tack, TackMenu, Tackroom, Herd, Herding, HorseKit, HorseRevival, Horsemanship, Mounted, Riders, Discipline, Equine, Boarding, BreedGifts) and its users (Stables, Quests, Cavalry, TradeRoutes, Couriers, Emigrants, Races, Gatherers, MountClimb, PackResourcePacks).
Andy decided 2026-11-24 17:18Z: "icy has far better models and implementations, if we cant use its models then put icy's in instead." Its licence (Bare Minimum License v1.0) lets code be reused but its models and textures only as part of Icy's itself, so the mod goes in whole.
- Add Icy's Better Horses 2.1.8 (Modrinth icys-better-horses, Fabric 26.3) with Modonomicon (MIT/CC-BY-4.0, new) and GeckoLib + Fabric API (already in the pack). Keep its copyright notice and licence text in the pack licences; ship the official jar unchanged (anti-reposting clause), downloaded by id like other pack mods.
- Switch ours off where they overlap: our horse breeds and their coats, five gaits and stamina bar, bond/trust/discipline, tack screens, horse revival, the Mo' Creatures horse renderer for horse/skeleton/zombie horses, and anything Icy's does better (whistle, command wheel, free look, two riders). Keep what Icy's doesn't have: the war elephant and mammoth, the bat horse and nightmare (rewire as Icy's breeds or keep as their own mounts; say which in the PR), stables and stablehands, caravans, rickshaws, cavalry, horse quests (break-in, stud, race), hogtied captives carried on horseback.
- Check overlaps with Horseman, Free Rein (FreeRein/FreeReinClient), Kaffee's Dual Ride, Ultimate Horseshoes, Horse Expert, Craftable Horse Armor: drop or turn off whichever duplicates Icy's, list each in the PR.
- Existing worlds: Icy's converts existing horses once. Our saved breed data on old horses must not crash or duplicate.
- The ticked rows R1-R11 come from Icy's itself now; check each one works in the pack and note any that doesn't.

## Notes
- 2026-11-24 22:40 project-thread-ojz7uj's claim had no sign of work for 30 min: handed back as open.
