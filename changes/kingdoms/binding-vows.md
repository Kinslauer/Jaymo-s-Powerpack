# oaths and pacts bind
- Andy: "the players are UNABLE to do things outside their parameters without specifically breaking the path or pact",
  for Paladin oaths and Warlock pacts, the pact terms after 5e (agreed 2026-11-17). A forbidden act is now refused, and
  the action bar names it. Done again within 10 s while sneaking (a choice in a menu: chosen again), the vow breaks on
  purpose and the act goes through: a Paladin's oath breaks (mended at a shrine or by a priest, as before); a
  Warlock's patron collects at once (favour, the slots, the curse) and lets that term be for 10 s.
- **Oaths:** Mercy, no killing blow on the helpless. Justice, no taking or breaking what isn't yours, picking pockets or
  locks, setting fires where folk live, or striking a settlement's people. Honour, no unprovoked blow, theft or
  pickpocketing. Crimes that can't be stopped before the act (trespass, poaching, dealing...) still break Justice when
  done, as before.
- **Pacts:** the Hearthkeeper, no blow on the downed or on anyone under a roof in a settlement who isn't fighting you,
  and no fire lit there. The Anvil-Father, no trident, no turtle shell worn (it's taken off). The Deep Mother, no pick
  on ore, no harm to dolphins and turtles. The Green Huntsman, no young beast. The Lantern Warden, no death or blood
  magic, no feeding on blood. The Crowned Sun, no theft, pickpocketing or lockpicking, no unprovoked blow on a guard or
  a ruler. The Ash Whisperer, no yielding to the guards (by kneeling or the white flag), no fine paid, no bounty turned
  in, and no good choice in a tale: mercy to a yielded foe, or a reply that goes against gain or vengeance. The Reaper
  sets none yet.
- **The Truth** (Andy: "thou shall not kill"): everyone sworn to it, and its Warlocks, can't land a killing blow on
  anything living but the undead. One who does it on purpose falls: the Truth lets them go, and their class becomes a
  Death Sorcerer (the old class's oath, pact, shapes and paths go; a second class and feats stay).
- `Vows` (new) holds the terms and the refusal; `Patrons.forsake`, `Classes.replace` for the Truth's fallen; `Theft` asks its crime checks before the act (`blockCrime`, `rummages`,
  `arson`); `Paladins` keeps the old oath-breaking only for what the refusal can't see. Hooks in `ForbiddenArts.stayed`,
  `BloodGrab`, `Lockpicking`, `Pickpocket`, `Busts`, `WhiteFlags`, `Approaches`, `Bounties` and `Tales`. Lang part `binding-vows.json`.
- Not built locally; not played.
