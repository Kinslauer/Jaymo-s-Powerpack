# The flanged mace is a blunt weapon: no more stabbing
- **Flanged mace** (every tier): it sat in the swords tag, so it fought like a sword. Now it's a mace. Its finisher is
  Skull Crusher (not Run Through), an untrained player slams with it (not Impale), its drop attack and stealth kill are
  the club's, and holding someone for questioning with it doesn't put a blade to them. It never gets left stuck in a
  body the way a run-through sword can. It breaks bones, crushes skulls, wears armour like a mace and counts as a blunt
  blow against padding, mail and plate (and as bludgeoning in Kingdoms). Its flanges still cut, so its blood spray and
  limb cuts are unchanged.
- **Same fix** for the other blunt weapons in the swords tag: the wooden and stone cudgels, the Anvil Father's hammer
  and the Ogre King's maul. Those have no edge, so they also stop spraying and cutting like a blade: their blood is a
  heavy blunt blow's splatter, and a killing blow tears a limb off at a blunt weapon's (lower) chance.
- How: they join the `jaymospowers:crushing` tag; `Fractures.blunt` reads that tag before the blade test,
  `Dismember.blade` passes over crushing weapons with no flanges, `BlastGore` doesn't take a blunt weapon for a blade,
  and `Finishers` treats a crushing weapon in the swords tag as no sword.
- Built by CI; not played.
