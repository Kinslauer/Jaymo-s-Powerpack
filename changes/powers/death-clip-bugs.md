# Zombies leave the dead for shade by day; nothing regrows on the dead; player heads wear the MCA look
- **Zombies by day**: a zombie (anything that burns in the sun) no longer eats severed parts or tears at the downed
  out in the sun while the sun burns; it goes for shade instead (`LimbEaters.sunlit`). "In the sun" is the same ray test
  the burning goes by (`SunExposure`: leaves don't shade), and Kitchen's scavengers are handed it too
  (`KitchenCompat`, `Scavengers.setSunTest`). Andy saw a crowd of burning zombies stay round his torn-up body at dawn.
- **The dead don't mend**: new `Mortality.dead` (left for dead, in the body or out of it as a ghost, or waiting in
  Limbo), which the game still counts as alive. A Verdant's mending (`ZFighterLineage.mend`) and the symbiote's
  (`WebSymbioteMend`) stop on the dead; Kingdoms' heritages ask it too.
- **Player heads**: a player's head (worn, like a captain's trophy; set down as a block; held, framed or on a trophy
  stand) is drawn as the villager MCA draws that player as, not in the account's skin (`McaPlayerHeads`, mixins on
  `CustomHeadLayer`, `SkullBlockRenderer`, `PlayerHeadSpecialRenderer`). A head with no player behind it (a bounty
  head off a villager) is still the plain head.
- Built by CI only; not played: the head's place and facing on a wearer, a block and an item, in the inventory
  too, and zombies' shade-seeking round a body, are unseen in game.
