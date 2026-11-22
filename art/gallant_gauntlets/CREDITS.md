# Gallant Gauntlets (the base gauntlet)

- **Source:** Gallant Gauntlets 1.1.2 by **Joshieman** (https://modrinth.com/mod/gauntlets, jar from Modrinth's CDN),
  licensed **CC BY-NC 4.0** (the licence file in its jar, `LICENSE_gauntlets`, copied to
  `docs/licenses/Gallant-Gauntlets-CC-BY-NC-4.0.txt`; https://creativecommons.org/licenses/by-nc/4.0/legalcode). Its
  `fabric.mod.json` says "ARR", a leftover of the Fabric example template it was made from (its author there is
  "Me!"); its Modrinth page and the licence it ships say CC BY-NC 4.0. Non-commercial: the pack is free, for friends.
- **Copied here, unchanged:** `iron_gauntlet.png` (the 16x16 icon), `iron_gauntlet_3d_1.png` and
  `iron_gauntlet_3d_2.png` (the 3D model's knuckle and side textures), `iron_gauntlet_3d.json` (the 3D model: a 4x6x4
  sleeve round the fist).
- **Changes (ours), all made by `tools/gauntlet_assets.py`:** recoloured into our materials (leather, iron, steel, dark
  iron, bandage linen, black glass) with the cuff in its own material; spikes, studs, straps and bandage turns drawn
  on; the sleeve's textures laid out for our worn gear (`client/armory/FangGauntletsClient`, which builds the sleeve's
  shape in code) with a knuckle band, a bracer's guard and three spikes beside it; the 3D model's textures repointed
  and three claws added for the Glass Gauntlet held in the hand. Used for the Fang Gauntlets, the Hand Wraps, the
  cestus and the bracers (leather, iron, steel) and the Glass Gauntlet's claws-out form.
- **Ideas only (our own code):** a gauntlet in the off hand punches with a right click, each gives a pair of boots'
  armour, and the Crushing enchantment wears the armour of whoever is hit (`boxing/OffhandPunch`).
