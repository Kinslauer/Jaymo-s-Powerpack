# Rats in the kitchen, with snap traps, cage traps and cats
- **Rats**: food left out and unguarded draws rats. Every 20 s, for each player, one food store near them may get a rat
  in a dark corner (4-10 blocks off, light 7 or less). This happens only if nobody's within 10 blocks of the store, no
  cat is within 16 and there are fewer than 3 rats about. The more food it holds and the more food dropped on the floor
  nearby, the likelier. Food stores are any storage block with food, grain, seeds or dough in it (chests, barrels,
  Kitchen cabinets and baskets; not shulker boxes), food left on a cutting board, and carcasses on meat hooks.
- A rat creeps up to the food, sniffs, sits up and nibbles. Then it either eats one piece and comes back a minute
  later, or runs off with it in its mouth. A thief that gets 16 blocks away with nobody within 12 is gone for good, with
  the food; kill it first and it drops what it took. Rats gnaw hung carcasses instead: that carcass's meat then spoils
  in a few days like an unbled one's, however well it's bled.
- Rats run from players (unless you sneak up on them) and from cats. They're weak (2 hearts) and never fight back.
  They come in brown, black and blue-grey and squeak now and then.
- **Snap Trap** (iron ingot, iron trapdoor, 3 wooden slabs): bait it with food (right-click), take the bait back with
  sneak + empty hand. A rat that runs onto it is killed and the bar stays down until you right-click it again. Stepping
  on a set one without sneaking hurts (1 heart). A shut trap gives a full comparator signal.
- **Cage Trap** (5 iron bars, 3 wooden slabs): bait it. A rat that goes in for the bait is shut in alive, and you can
  see it in there sniffing at the bars. Break the cage to carry it off as a Caged Rat, set it down far away and
  right-click it: the rat bolts and doesn't come back.
- **Cats** (and ocelots), tame or stray, hunt rats, and no rat turns up within 16 blocks of a cat.
- Baited traps draw rats from 16 blocks, before any other food. When a trap goes off, the other rats nearby scatter.
- The rat's model, animations (walk, run, sniff, sit up and eat, twitching nose and whiskers), coats and sounds, and
  the snap trap's model and texture are from the Rats mod (LGPL-3.0, by Alexthe666 and its maintainers;
  `LICENSE_rats`), redone on vanilla 26.3 models. The code is our own.
- Classes: `rats/Rats`, `Rat`, `FoodStores`, `RatSpawning`, `TrapBlock`, `SnapTrapBlock`, `SnapTrapEntity`,
  `CageTrapBlock`, `CageTrapEntity`, `BaitTrapEntity`, `CagedRatItem`, `client/rats/RatModel`, `RatRenderer`,
  `SnapTrapRenderer`, `CageTrapRenderer`, `RatsClient`. `CorpseEntity` marks a gnawed carcass's cuts as spoiling, and
  `MobAccessor` gained the target selector.
- Built; not played. Not tested in game: spawning, the raids, the traps, the cage's rat, cats hunting, the mouth item's
  placement, and the snap trap's and spawn egg's item icons.
