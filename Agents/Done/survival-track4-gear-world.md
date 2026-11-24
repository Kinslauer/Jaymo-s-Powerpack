---
status: done
task: Survival picks track 4 (minus From the Shadows): gauntlets, riding camera, wind sway, goblin extras, cannons
branch: claude/confident-davinci-cf16f0 (+ one branch per PR)
with: wounds-lso (track 1), butchery-hook (track 3), climate-lso (track 2, Kitchen climate: not touched here)
pr: 1994, 1996, 2003
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
- PR #1994 cannons (merged): Old Cannons was already in the pack (custom licence allows modpacks; 1.0.5.1 exists, not bumped: CurseForge ids unknown); stone shot (timber only) and fire shot added; ship cannons already existed.
- PR #1996 gauntlets (merged): Gallant base art for Fang Gauntlets, wraps, cestus, bracers, Glass Gauntlet; offhand punch; boots armour; Crushing. Parry Gauntlet/Climbing Claws (Kingdoms, GPL vs NC art) left for Andy.
- PR #2003 camera + wind: boats/helm free look; plants via Interactive Foliage weather (its direction fixed west); flames drift. Lanterns not done.
- Goblin extras: editor agent building claude/survival-goblin-extras.
- Not played: any of it.
- 2026-11-22 18:12 PR #1994 is merged into master; moved to Agents/Done.
