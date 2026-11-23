# Weapons in set places on the body: swords at the hips, four flintlocks, guns on the back
- Andy: "weapons should always go to a specific place on the body unless the player toggles otherwise ... I want to look
  like edward kenway with all his weapons visible". Each kind of weapon on your hotbar now always shows in the same place,
  whatever slot it's in:
  - **Swords** (and maces, other weapons): left hip, then right hip, then crossed on the back.
  - **Flintlock pistols** (WeaponMod's and the Void Armory's): two on the chest strap, barrels angled down across the
    chest, then two more tucked in the sash at the front of the belt. Four pistols and two swords show together.
  - **Muskets, blunderbusses, Kingdoms' long guns, bows, crossbows, tridents, spears**: crossed on the back, muzzle up.
  - **Shield**: on the back, over the guns. **Axe**: small of the back, else a free hip. **Pickaxe, shovel, hoe**: small
    of the back.
  - The item in your hand leaves its place empty; drawing one pistol never moves the others. Up to 6 show (was 3), all
    of them with a tool belt worn. Over a chestplate, leggings or a costume they sit further out; back ones clear a backpack.
  - A hidden blade (Jaymo's Kingdoms) is worn on the wrist as before.
- **Toggle**: the link "Weapons: set places" on the Character page (H), right of the Record's title, switches to "Weapons:
  free": YDM's Weapon Master places them by hotbar slot as before (its Wardrobe tab settings). Saved per player, seen by
  everyone.
- Classes: `armory/BodyWeapons` (homes, synced list, toggle), `client/armory/BodyWeaponsLayer` (the drawing; every place is
  a row in `HOMES`), `client/mixin/weaponmaster/WeaponMasterShownMixin` (Weapon Master draws only in free mode).
- Not played: every position and angle is set from the model's numbers, not seen. No screenshot. Banners are not shown in
  set places (Weapon Master's banner on the back is free mode only).
