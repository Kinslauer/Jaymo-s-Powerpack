---
name: sailing
description: Sailing: our own block-built ships (Kingdoms com.jaymoskingdoms.sailing), stages 1-2 merged, stage 3 pirates/navies PR #209; licence verdicts on other ship mods
metadata:
  type: project
---
Pack mods: Shippy Ships + Old Cannons (Caesius_Leo, modpack OK with credit). Can't use: Valkyrien Skies/Eureka/Pirates (1.20.1), Physics Mod Pro (paid), Sails and Airships (ARR, not on CF), Create Aeronautics (NeoForge 1.21.1). Plan: /mnt/project-files/sailing/Sailing-Plan.md.
- Stage 1 (helm, bellied sails, buoyancy, deck walking, anchor) MERGED PR #155 (0.10.42 / 0.7.7).
- Stage 2 (winds, oars, ShipCannonBlock/ShipShot broadsides, block-breaking shot per Andy "no mercy", kits, Gale Stream/Smoke Jet push sails) MERGED PR #174 (0.10.67 / 0.7.18). Ship's Plans, ship lights, creative kits MERGED PR #187 (0.10.97 / 0.7.28).
- Stage 3 (pirates + navies) PR #209 MERGED 2026-10-15 (pack 0.10.129 / Kingdoms 0.7.42, built, not playtested). Design: Crew (saved on Ship) + crew Folk riding at Ship.SEAT; Ship.Captaincy implemented by Corsairs (AI returns a player Input); Fleet designs sloop/brig/frigate/cog; PirateCoves = camp kind "pirate", cove leader is the ship's captain (his death clears it); Navies = harbours (1000+ emeralds), warship, merchantmen, Quest.ESCORT=14; TreasureWrecks = Pirate's Chart maps + rare wrecks. Roles 70-75. Ops: /kingdom fleet ..., /kingdom camp found pirate.
- Dev tips: dev fill limit 32768; temporary empty data/c/tags/item/ingots/silver.json for dev runs (never commit); copy Powers + Kitchen jars into kingdoms/run/mods to test with powers.
Related: [[muskets-cannons]], [[kingdoms-caravans]].
