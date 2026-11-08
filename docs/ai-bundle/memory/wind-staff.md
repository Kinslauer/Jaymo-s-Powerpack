---
name: wind-staff
description: Quarterstaff gales for wind sorcerers only (Kingdoms Sorcerer, air element), WindStaff, PR #140 MERGED 2026-10-14 07:0xZ as pack 0.10.17
metadata:
  type: project
  modified: 2026-10-14T06:03:55.453Z
---
2026-10-14 Andy: "give quarterstaff synergies with wind". Draft PR #140 (pack 0.10.13): WindStaff, server-only. Own chain of landed staff swings maps to the 5 Better Combat staff swings: Gust, Wind Blade, Vortex, Piercing Gale, Downdraft (air slam bounces you up). Right click = Staff Vault (sneak = straight up), soft landing. Applies to any item id ending "quarterstaff". Not playtested; jar /mnt/project-files/wind-staff/. Related: [[combat-animations]], [[wind-flight-swing]].
2026-10-14 06:40Z Andy: "these should only apply to wind sorcerers": now gated on the Kingdoms Sorcerer class with the air element (read by name via Classes.of); the Wind power alone doesn't count. Re-versioned 0.10.17.
MERGED 2026-10-14 ~07:02Z as pack 0.10.17 (Z Fighter GUI #143 also claimed 0.10.17 and must take 0.10.18).

2026-10-14 08:22Z Andy: "wind should get increased proficiency with daggers the same way it does quarterstaves": WindDagger (PR #152, 0.10.27), same wind-sorcerer gate; 4 dagger gales (Gale Cut, Wind Stab, Crosswind, Cyclone Thrust) + right-click = Sora's old winds' Flurry, 20 s cooldown (Andy 08:48Z/08:53Z; Wind Step dropped). Ids containing dagger/knife/kunai.
MERGED 2026-10-14 08:55Z as pack 0.10.27.
