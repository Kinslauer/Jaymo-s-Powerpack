---
status: done
task: mordhau-2-hoods-crests
branch: claude/mordhau-2-hoods-crests
with: claude (PC worker, Opus 5.5)
pr: 2178
mods: kingdoms, powers
versions: 
started: 2026-11-24
owns:
  - kingdoms/tools/gen_heraldry_hoods.py (new: every new hood, hat, crest and neck piece of this track)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_<this track's new pieces>.json, their textures, icons, item models, recipes
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heraldry-hoods.json (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java (one separate block of this track's registrations, at the end of register())
  - kingdoms/src/client/java/com/jaymoskingdoms/client/HeraldryLayer.java (hats under a helm, crown/orle without the torse, dyed crests, second colour)
  - src/main/java/com/jaymospowers/tinkering/smithing/HeraldryForging.java (the coif cuts and the armet wrapper only)
  - kingdoms/src/main/java/com/jaymoskingdoms/Harness.java (slotOf: any crown goes in the crest place)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterTournaments.java (the champion's wreath)
  - kingdoms/src/main/resources/data/jaymospowers/tags/item/{cloth,mail,plate}_armor.json (new entries)
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
- Track 1 (claude/mordhau-1-helms) wasn't pushed when this started; its shared-file edits are the helm lists. One-line conflict in HeraldryForging's PLATE arrays (second to merge keeps both).
- Crown id for track 1's royal helms: `crest_crown`; a ring round the skull at y 31-32.6, 5.1 each side, 6.1 before (clears the veiled helm), not lifted by crestRise.
- Andy (2026-11-25): "you should be able to wear any crown": every regalia crown goes in the crest place over a helm; anyone may wear one.

## Done
- PR #2178 (draft until Andy approves the previews in Assets/mordhau-2-hoods-crests/). 25 new pieces: 15 hoods and hats, the arming cap, 4 coif cuts, orle, helm crown, antlers, mantling, the armet wrapper; any regalia crown goes over a helm in the crest place; hats hidden under a helm; the sallet hides a bevor/wrapper; the tournament champion gets the victor's wreath. Lore: five (new) paragraphs after "The spectacle helm".
- Not played: compiles in CI and the pack boots; drawing, recipes (incl. second colour), forging and the wreath prize untested in game. Skipped: the spiked lowered coif (row allowed it).
- 2026-11-25 13:24 PR #2178 is merged into master; moved to Agents/Done.
