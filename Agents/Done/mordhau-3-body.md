---
status: done
task: mordhau-3-body
branch: claude/mordhau-3-body
with: claude (T3 thread, Sonnet 5.5)
pr: 2185
mods: kingdoms, powers
versions: 
started: 2026-11-25
owns:
  - kingdoms/tools/gen_heraldry_body.py (new: every new or re-cut piece of this track, previews)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_<this track's new pieces>.json, their textures (entity/bb, entity/under_armour, item), icons, item and item-model files, recipes
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heraldry-body.json (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java (one separate block of this track's registrations at the end of register())
  - kingdoms/src/main/java/com/jaymoskingdoms/Harness.java (only the helper for guards' under-layers)
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java (only the off-duty sweep: guards keep their under-layers)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/HeraldryLayer.java (undyed colours, covered finish tint, second colour, padding/mail skirts)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/UnderArmourLayer.java (a sheet for every padding and mail piece, second-colour pass)
  - src/main/java/com/jaymospowers/tinkering/smithing/HeraldryForging.java (new cuirasses, lamellar, covered/painted/etched finishes, new mail pieces)
  - kingdoms/src/main/resources/data/jaymospowers/tags/item/{cloth,mail,plate}_armor.json (new entries)
  - changes/kingdoms/claude-mordhau-3-body.md, changes/powers/claude-mordhau-3-body.md
  - docs/vault Assets: E:/Jaymo Vault Live/Assets/mordhau-3-body/ (previews, row table)
---
# Mordhau harness 3: body

**Task:** Inbox `mordhau-3-body`: Andy's 35 picked body rows (padding, mail, cuirasses, over-garments, shoulders, arms, hands), plus
his 2026-11-25 ruling that this track also builds the Covered flat top and the Royal flat top. Own models of the historical armour,
made the way the harness makes its pieces. Mordhau's meshes, textures and names are not used.

## Plan
- New generator kingdoms/tools/gen_heraldry_body.py importing gen_heraldry_armour as `g` (as track 2's gen_heraldry_hoods.py does), so the
  shared generator isn't rerun. Previews of every new or re-cut piece worn, front/side/back, to Assets/mordhau-3-body/.
- "Have" rows: check each is reachable by a player (crafting, anvil, shop) and add the missing route; list the evidence per row.
- Guards' under-layers (Andy's note on the slashed doublet): read as "off duty or in town a guard takes the heavy places off and keeps the
  harness's under-layers on" (padding, mail; hose once track 4 adds it). Done in HeraldryWear's sweep.
- Track 4 (mordhau-4-legs, same thread) stacks on this branch.

## Notes
- No other running agent owns any of these files (checked Agents/ 2026-11-25).
- Vault sync: the parent's `vault_sync.py` held the sync lock for 10+ minutes pushing; I edited notes by hand rather than start a second sync.
- Local builds: Andy plays on this PC, so no Gradle here. The generators (Python) run here; CI compiles.

## Done
- 2026-11-25 4:54 PM Generator, models, sheets, previews, Java (registrations, layers, forging, guards), lore and change notes are written; branch claude/mordhau-3-body is being checked and pushed.
- 2026-11-25 4:59 PM Draft PR #2185 (waits on Andy's preview approval). 35 rows: 25 new pieces + covered finish + 10 have rows checked (3 made reachable at the anvil, coat of plates given a recipe). Change notes: changes/kingdoms and changes/powers claude-mordhau-3-body.md. Lore: 8 (new) paragraphs after 'The victor's wreath'. Not played; Java compiled only by CI.
- 2026-11-25 5:40 PM The slashed doublet is puffed now (commit 2c11177dc9 on claude/mordhau-3-body, merged into claude/mordhau-4-legs as 5167428419): puffed sleeves slashed through, lined and gathered in the second colour, the left a size fuller, as the slashed hose's thigh puffs. Preview refreshed in Assets/mordhau-3-body/landsknecht_doublet.png. Not played; CI compiles.
- 2026-11-25 5:40 PM Vault sync repaired: every push since 2026-11-24 12:43 PM had failed because a 290 MB `Assets/otherworld-fabric-picklist/Otherworld-v8 HF2.zip` was committed (GitHub refuses files over 100 MB), so each sync run only added commits (319 piled up). The 319 local commits were kept (backup branch `backup-before-zip-strip` in the live folder holds the originals) with the zip dropped from them, and pushed as a plain fast-forward (c2bc11ebf..b73d1c0aa, no force). The zip is still on disk, kept out of git by `.git/info/exclude`. Anything else over 100 MB put in the vault will stall the sync the same way.
- 2026-11-26 00:33 PR #2185 is merged into master; moved to Agents/Done.
