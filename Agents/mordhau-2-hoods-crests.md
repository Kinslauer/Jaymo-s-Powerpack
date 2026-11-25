---
status: running
task: mordhau-2-hoods-crests
branch: claude/mordhau-2-hoods-crests
with: claude (PC worker, Opus 5.5)
pr: 
mods: kingdoms, powers
versions: 
started: 2026-11-24
owns:
  - kingdoms/tools/gen_heraldry_hoods.py (new: every new hood, hat, crest and neck piece of this track)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_<this track's new pieces>.json, their textures, icons, item models, recipes
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heraldry-hoods.json (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java (one separate block of this track's registrations, at the end of register())
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java (mayWear: the crown crest only)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/HeraldryLayer.java (hats under a helm, crown/orle without the torse, dyed crests, second colour)
  - src/main/java/com/jaymospowers/tinkering/smithing/HeraldryForging.java (the coif cuts and the armet wrapper only)
  - changes/kingdoms/claude-mordhau-2-hoods-crests.md, changes/powers/claude-mordhau-2-hoods-crests.md
---
# Mordhau harness 2: hoods, hats, crests and neck

**Task:** Andy's 21 picked rows from the Mordhau pick list (Inbox `mordhau-2-hoods-crests`): hoods and coifs, soft
caps, brimmed hats, Landsknecht bonnet, fur hat, turban, jester's hood, wreath, crests (with the new crown that
track 1's royal helms wear), antlers, mantling, bevor, armet wrapper, gorget. Our own models of the historical pieces.

## Plan
- New pieces built by a new generator, kingdoms/tools/gen_heraldry_hoods.py (it reuses gen_heraldry_armour's
  painters and helpers but doesn't edit that file, which track 1 is editing).
- Registered in one block of HeraldryArmour; recipes for cloth; coif cuts and the wrapper forged at Powers' anvil.
- Previews to `Assets/mordhau-2-hoods-crests/`; lore (new) paragraphs for the headsman's hood, turban, jester, crown.

## Notes
- Track 1 (claude/mordhau-1-helms) wasn't pushed when this started; its shared-file edits are the helm lists.

## Done
What a player will see, and what wasn't tested.
