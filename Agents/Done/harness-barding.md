---
status: done
task: harness-barding
branch: claude/harness-barding
with:
pr: 1744
mods: kingdoms
versions:
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/equestrian/Tackroom.java
  - kingdoms/src/main/java/com/jaymoskingdoms/equestrian/Bonding.java
  - kingdoms/src/main/java/com/jaymoskingdoms/equestrian/Tack.java
  - kingdoms/src/main/java/com/jaymoskingdoms/equestrian/TackMenu.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/equestrian/BreedHorseRenderer.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/equestrian/TackLayer.java
  - kingdoms/tools/gen_heraldry_armour.py
  - kingdoms/src/main/resources/assets/jaymoskingdoms/horse/builds.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heraldry-armour-items.json
---

# Harness 4: matching barding and caparisons

**Task:** Add matching barding (chanfron, crinet, peytral, crupper in the same make and finish as the rider's harness: bright, blackened, gilded) and caparison (cloth cover in the realm's colour and arms, over barding) for horses. The tack screen already takes barding; draw on BreedHorseModel.

## Plan
- Add FINISH component support to barding items (reuse HeraldryArmour.FINISH)
- Update CHAIN_BARDING and PLATE_BARDING to support finishes (bright, blackened, gilded)
- Update BreedHorseRenderer to select barding texture based on finish component
- Add caparison items (cloth covers dyed with realm colours, worn over barding)
- Update gen_heraldry_armour.py to generate barding textures for each finish
- Update TackMenu to add caparison slot
- Update TackLayer to render caparison over barding
- Add change note in changes/kingdoms/

## Notes
- The builds.json already has barding model pieces (armor_chanfron, armor_crinet_0/1/2, armor_peytral, armor_flank_left/right, armor_crupper, armor_spine)
- Barding currently uses vanilla horse armor system (equipment assets). Need to extend to support finishes.
- Caparison is a new cloth layer over barding, tinted with realm colours.

## Done
Player will see: matching barding sets in three finishes (bright/blackened/gilded) that match their heraldry armour, and caparisons in realm colours. Not tested in game.
- 2026-11-18 20:11 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-18 19:03 PR #1744 is merged into master; moved to Agents/Done.
