# survival picks: one base gauntlet for every fist weapon, offhand punches, Crushing
- **One gauntlet for all** (Andy: "reskin a copy, and use that base for all our gauntlet or fist weapons. except the
  hidden blade"): Gallant Gauntlets' gauntlet (by Joshieman, CC BY-NC 4.0, credited in `art/gallant_gauntlets/` and the
  jar) reskinned is now the icon and the worn shape of the Fang Gauntlets (with three bone spikes over the knuckles),
  the Hand Wraps (in our bandage linen), the leather, iron and steel cestus (studs over the knuckles) and bracers (a
  metal guard over the wrist), and the Glass Gauntlet's claws-out form (black glass, three glass claws; in the hand it
  is the gauntlet's 3D sleeve). Worn, each is the same sleeve round the fist (`FangGauntletsClient`); the old cuff,
  plate and wrap shapes are gone. Art from `tools/gauntlet_assets.py` (the older generators no longer write it).
- **Offhand gauntlets**: a gauntlet in the off hand is worn on that arm, whatever the main hand holds, and a right
  click at a monster, a player or a creature after you punches with it, on its own cooldown, with its own damage and
  enchantments (`boxing/OffhandPunch`). Not while the main hand's right click does something (a weapon's guard, food, a
  bow), so a boxer's block is unchanged; a villager or a horse still gets the right click as before.
- **Armour**: each gauntlet in either hand gives a pair of boots' worth: Fang Gauntlets and iron bracers 2, steel
  bracers 3, a cestus or leather bracers 1, Hand Wraps none.
- **Crushing** (gauntlets, I-III): every blow wears 3/5/7 durability off each piece of armour the one hit has on. At
  the enchanting table for redstone, iron and flint.
- Forged gauntlets count as weapons for grades and sockets, not armour (`Grades.gearSlot`).
- Kingdoms' Parry Gauntlet and Climbing Claws, the Fire Gauntlet (an Artifacts trinket), plate and fur-lined gauntlets
  (armour) and the hidden blade are unchanged.
- Built by CI only; not played: the worn sleeve's fit on wide and slim arms, the offhand punch, the armour and Crushing
  are unseen in game.
