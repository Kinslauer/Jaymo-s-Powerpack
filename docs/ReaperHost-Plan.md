# The Reaper's host, the living raiders, the tomb and the sea: plan

Andy's picks (2026-11-14) from Immersive Pillagers (GPL-3.0, ideas only), Mebahel's Draugr (ARR, ideas only) and Moog's
Ocean Structures (LGPL-3.0, shipped unchanged). 32 of 35 rows picked; not picked: draugr buildings in the world, draugr
raids on the surface, charts to Moog's wrecks. The reaped themselves are already in (`kingdoms/.../reaped/`).

One PR per patch, in this order. Each fits `Lore.md` first and adds its placement there as a `(new)` paragraph.

| # | Patch | Rows | Where |
|---|-------|------|-------|
| 1 | Moog's Ocean Structures in the pack; the drowned courts | mos_ship, mos_deep | `build.gradle`, `Lore.md` |
| 2 | People aboard: barbarian long ships, traders, fishers, passengers, a drifter on each raft; Tideborn hints | mos_crews, mos_raft, mos_deep (talk) | new `SeaSites` beside `IllagerSites`; `Crewmen`, `Drifters`, `ShipTalk` |
| 3 | Host II: riders on skeleton horses and on spiders that take them over walls (Andy: "only the undead illagers should ride spiders"), the reaped evoker raises the fallen, folk flee and gossip, a keeper calls the host | rh_riders, ip_spider, rh_evoker, rh_fear, rh_keeper | `reaped/`, `ZombieHordes`, `Reaper`, `WorkTalk`, `IllagerTalk` |
| 4 | The host at war: a standing Reaper side seated in a fallen illager camp, a mangonel with a dead crew, a black long ship | rh_war, rh_siege, rh_sea | `Camps`, `WarParties`, `SiegeTrain`, `Fleet` |
| 5 | Living raiders: horse, camel, boat, balloon (no spiders: those are the reaped's) | ip_horse, ip_camel, ip_boat, ip_air | `ZombieHordes` SQUAD kind |
| 6 | Wanted: the mark, posters, the raider's horn, rusty keys and caches, lost labs and the crude totem | ip_wanted, ip_poster, ip_horn, ip_keys, ip_labs | `IllagerSites`, `IllagerTalk`, `HordeLure`, Alchemy |
| 7 | Draugr ranks: archer, wight, scourge, the one that dodges, reading the fight; the skull that runs | dr_archer, dr_wight, dr_scourge, dr_dodge, dr_smart, dr_head | `Draugr`, Stormborn Tomb, chapels and barrows |
| 8 | The lord of the Stormborn Tomb (Andy: "use blockbench and fable, go all out for this"): own model, intro film, the seal in pieces, the hall's waves, rune traps | dr_overlord, dr_claw, dr_waves, dr_traps | Stormborn Tomb, `UltimateFilm`, Alchemy |
| 9 | Moog's boats made ships: a helm set on each long ship, hauling ship and small ship where the mod floats her, so she can be taken, sailed and drawn with Ship's Plans into a kit. Nothing of Moog's is copied into our jar, so no notice is needed. NPC crews do not sail them yet (that needs their hulls as `Fleet` designs) | mos_sail | `SeaSites.helm`, `IllagerSites` |

## Rules that shape these
- Draugr never leave the Stormborn Tomb. Everything in 7 and 8 stays inside it; the running skull is a skeleton of the
  chapels and barrows, not a draugr.
- No grid recipes for iron parts; the rune traps and the crude totem come through Alchemy.
- Lore only as hints in talk and books.
- Moog's templates may be copied (LGPL) with its notice kept; nothing is copied from the other two mods.
