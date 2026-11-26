# Cave band skins (REA spec)

The barbaric Warrior-born of the Primordial Caves (`ZFighterCaveBand`; Lore.md, "The old fighters under the ferns").
Andy (2026-11-24): "they should vary, not all shirtless but all in primitive hide"; "they can use the caveman gear as
well as hide pieces".

- **Base:** the vanilla 64x64 wide player skin (the rogue fighters' `PlayerModel`), painted pixel by pixel by
  `tools/gen_cave_brute_skins.py`; no other mod's or player's skin is read. Their tails come from `WarriorLooks`.
- **Shared:** sun-dark skin in four tones, wild dark hair standing out on the hat layer and hanging down the back,
  a heavy brow, dark eyes; hide, fur, cord and bone only. No cloth, metal or dye but ochre.
- **The four looks** (picked by the brute's id, so a band mixes them):
  0. Shirtless and bearded: a fur strap shoulder to hip, a long ragged hide loincloth, wrapped feet, a scar.
  1. A stitched hide tunic with fur at the neck and hem, bare arms striped with ochre, hide leggings, fur shoes.
  2. Bearded, a grey fur mantle over the shoulders and upper arms, bare belly, a bone at the throat, a dark hide
     kilt, bound shins.
  3. An open dark hide vest over a bare chest, leather-bound forearms, a fur loincloth and fur-wrapped shins.
- **On top:** `ZFighterCaveBand.dress` still rolls Armory caveman pieces or leather per slot, so the band varies more.
