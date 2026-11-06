---
name: pack-worldgen-and-wishlist
description: Pack worldgen decisions (2026-10-10, Powers 0.8.4) and the mod wishlist verdicts awaiting Andy's go
metadata:
  type: project
  modified: 2026-10-10T23:39:33.158Z
---

2026-10-10: Andy dropped Towns and Towers + Cristel Lib (clashed with Epic Structures: Villages, where Kingdoms founds
capitals) and added ATi Structures + Grim Kingdoms **Vanilla Editions** (PR #3, merged, Powers 0.8.4). Both sit in
`packModsNoCf` until their CurseForge ids are filled in on the home PC.

**Why:** Andy wants grander-scale kingdoms and structures; generation that doesn't contribute gets removed.

**How to apply:** don't re-add T&T or the full (non-vanilla) ATi/Grim editions. Wishlist verdicts are in
/mnt/project-files/assessments/mod-wishlist.md: add outright Moog's Paths, Grindstone Sharper Tools, Combat Roll,
Traveler's Backpack; Epic Knights (faction armor) once it has a 26.3 build, via a Kingdoms compat class; recreate
illager camps, Better Mob Combat in Kingdoms. Andy decides before those move.

2026-10-11: pack additions in PR #5 (0.8.5, merged). Kingdoms 0.4.0 (illager camps, Epic Knights compat, Better
Combat mob combat) built and merged locally on top of kingdoms-0.3; saved as a git bundle + jar in
/mnt/project-files/kingdoms-0.4/. All merged to master 2026-10-11 (PR #5 pack, PR #4 fixes into kingdoms-0.3, then PR #2).
Cloud builds need JDK 25 (Temurin from api.adoptium.net into /opt/jdk25) and a Maven Central mirror init script
(~/.gradle/init.d) because Central returns 429 here. CurseForge ids: api.cfwidget.com/minecraft/mc-mods/<slug> works.

2026-10-11 (PR #17, merged, Powers 0.8.22 / Kingdoms 0.5.0): Andy wants kingdoms seated strictly inside Epic villages
(church or largest building = king's seat, another building = storehouse), fiefdoms and barbarian camps in Grim
Kingdoms structures near kingdoms, Moog's roads out of settlements, and visibly different gear per realm/rank/biome.
Epic Knights Slavic Armory (desert kingdoms) and Epic Knights have no 26.3 builds: compat only, not in the pack.

2026-10-12 (PR #37, merged, pack 0.8.56): Distant Horizons 3.3.4 and Iris Shaders 1.11.7 are back/in the pack as
packMods with `dev: false` (kept out of the dev game). Andy asked for them after a CurseForge modpack they liked
(Cinematic Horizons) turned out to be 1.21.1 only. Don't remove DH again without asking.
