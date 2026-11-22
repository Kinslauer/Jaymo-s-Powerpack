---
status: stalled
task: Survival picks track 4 (minus From the Shadows): gauntlets, riding camera, wind sway, goblin extras, cannons
branch: claude/confident-davinci-cf16f0 (+ one branch per PR)
with: wounds-lso (track 1), butchery-hook (track 3), climate-lso (track 2, Kitchen climate: not touched here)
pr: 
mods: powers, kingdoms
versions: 
started: 2026-11-22
owns:
  - src/client/java/com/jaymospowers/client/armory/FangGauntletsClient.java
  - src/main/java/com/jaymospowers/boxing/BoxingGear.java (armour, offhand punch)
  - src/main/java/com/jaymospowers/armory/ (Fang Gauntlets item only)
  - new: gauntlet offhand punch / Crushing enchant classes
  - kingdoms client: FreeReinClient area (riding camera), disasters/Wind* (wind sway)
  - kingdoms: Cannon, Cannonball, ship cannons; goblin extras (new classes)
---
# Survival track 4: gear and world

**Task:** Plan `docs/Survival-Picks-Plan.md` track 4 minus From the Shadows: Gallant Gauntlets art reskinned as the base
of every fist/gauntlet weapon (not the hidden blade), offhand punches, boots' armour, Crushing; riding camera not
dragged by the mount; plants, lanterns and fire sway with our wind; goblin trader, decoy, drunk goblins, engineer's
hammer and robot; Old Cannons in the pack, three kinds of shot, ship cannons.

## Plan
- PR gauntlets (Powers), PR camera + wind (Kingdoms client), PR goblins (Kingdoms), PR cannons (Kingdoms + pack).

## Notes
- Gallant Gauntlets 1.1.2 jar (Modrinth CDN): LICENSE_gauntlets is CC BY-NC 4.0; its fabric.mod.json says "ARR"
  (the Fabric example template's leftover, like its "Me!" author). Modrinth lists CC-BY-NC-4.0.

## Done
- 2026-11-22 14:17 No sign of work for 30 min: marked stalled (whoever ran it stopped).
