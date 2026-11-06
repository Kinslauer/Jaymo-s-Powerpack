---
name: blood-magic-stains
description: Blood magic draws from ground stains + Kitchen hanging-carcass butchery and extras; PR #864 merged 2026-11-03 as Powers 0.19.64 / Kingdoms 0.7.482 / Kitchen 0.5.51
metadata:
  type: project
---
Andy (2026-11-03): "Blood magic should draw from stains on ground" -> Powers `BloodTrail.draw`, Kingdoms Hemomancy (Blood Sorcerer pool, blood witch heal) via `PowersCompat.drawStains`.
Same day, from Alex's Butchery (GPLv3 Forge, ideas only): Kitchen `butchery/` (MeatHookBlock, BloodGrateBlock level 0-8 x 250 mB, BloodBottleItem) + `corpse/Hanging` stages: cleaver bleeds (grate else Powers stains) -> cleaver head (Powers SeveredPart.dropTrophyHead, never despawns) -> knife skins (#jaymoskitchen:skins) -> 3 cleaver cuts (+table extras, bones, fat). Hung corpse = server ragdoll gripped at hook (both hind feet). Kingdoms: sorcerers draw grates; vampires/sorcerers drink bottles (Butchery.addOnDrink).
PR #864 merged 2026-11-03 (b299b1d3); built, Kitchen dev server booted; not playtested. Kitchen standalone runServer fails on master's disabled minecraft:leather recipe (pre-existing; move the file aside to boot).
Andy "yes all of this" (built same PR): unbled meat spoils (Kitchen Spoilage, 3 days, lore "Unbled"), prime skins + leather scraps from hook skinning, village butchers hang/bleed at nearby hooks, blood sausage, blood meal, supple/harness/hardened leather, scraps repair leather armour, hide rug, Powers wall trophy heads (set a carried head down looking at a wall). Deferred: skeleton displays (need per-animal models), hide traits (need armour items). Kingdom butcher/healer for every kingdom went to a separate thread via coordinator.
