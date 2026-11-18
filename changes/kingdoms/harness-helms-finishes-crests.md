# Harness 1: new helms, finishes and crests

## What the player will see
- Six new helms: Great Helm (flat-topped pot helm with eye slits and breaths, fixed face), Barbute (deep helm with T/Y face opening, open face), Burgonet (open helm with peak, comb, hinged cheek plates, visored), Spangenhelm (riveted segments with nose bar and mail curtain, fixed face), Lobster-tail Pot (round skull with sliding nose bar and laminated neck tail, visored), Grotesque Visor (armet with sculpted beast/grinning face visor, visored)
- Five new plate finishes available at the smith's anvil: Russeted (red-brown), Heat-blued (dark blue), Painted (realm colours), Etched and Gilt (engraved gilt bands, for honoured knights), Rusted (weaker until scoured at a grindstone)
- Five new crest beasts: Griffin, Bear, Wolf, Raven, Hound (join the existing eight)
- New Plume crest: feathers in the realm's colour instead of a beast

## Cause
The Harness Pattern Book (Andy's 2026-11-18 ticks) specified these new helms, finishes, and crests. They were missing from `HeraldryArmour.java`, `HeraldryWear.java`, and the generator `kingdoms/tools/gen_heraldry_armour.py`.

## Changes
- Added 6 new helms to `HeraldryArmour.HELMS` and `register()` with appropriate Face, sight, and crestRise values
- Added 5 new finish constants (RUSSETED, BLUED, PAINTED, ETCHED, RUSTED) to `HeraldryArmour` and corresponding palettes to the generator's `FINISHES`
- Added 5 new crest beasts + plume to `HeraldryArmour.BEASTS`, `CRESTS`, and the generator with new build functions
- Updated `HeraldryWear.finish()` to distribute the 7 finishes (bright + 6 variants) across realms
- Ran `gen_heraldry_armour.py` to generate models, textures, icons, item models, recipes, and lang entries for all new pieces in all finishes
- Added lang entries for new finishes and item names

## Not tested in game
- Visual appearance of all new helms in each of the 7 finishes
- Visual appearance of new crest beasts and plume on different helms
- Realm knight generation with new helm/finish combinations
- Rusted finish weakening and grindstone scouring (grindstone interaction not yet implemented)
- Player crafting/dyeing of new pieces