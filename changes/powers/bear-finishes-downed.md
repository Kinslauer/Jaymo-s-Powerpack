# predators eat the downed, and finish them once full
- Andy (2026-11-20, clip of a brown bear tearing at him for most of a minute while he lay downed): "why didnt the bear
  execute here? it had more than enough time", then: "predators should start eating you when you go down. and only
  finish you if they are full".
- **Eating starts as you go down:** a hungry meat-eating animal (bear, wolf, big cat; not monsters, carrion birds, pets,
  the young or the leashed: `LimbEaters.eatsAlive`) lets the fight go the moment its target is a downed player (checked
  every 5 ticks, anger and grudge cleared), and can't be set on a downed player at all (`PredatorEatMixin` on
  `Mob.setTarget`). Its eating (`TearGoal`: a limb per 5 bites, eaten where it falls) takes over instead of its claws.
- **Finished only when full:** `MobExecutions` won't let a hungry eater execute anyone. Fullness is now kept per animal
  (`LimbEaters.full`/`ate`, shared by limb eating and tearing): 2 parts, or with no limbs left 5 mouthfuls of the body
  for each, and it's full for 4 minutes. Once full, going for a downed body or tearing at it counts as fighting them
  for 20 s (`LimbEaters.toreAt`, read by `MobExecutions.fighting`): after the usual 5 s grace it stands over them and
  finishes them. An animal already full when it downs you finishes you after the grace.
- A meat-eating animal's execution is the mauling (`Move.FEED`: two tearing bites, then the last), however big it is,
  with its own growl at the wind-up, not a ravager's crush and roar.
- Big executioners (a bear, a ravager) count as there once their bulk reaches the body, so they can't stop short of
  the 1.6-block reach and give up after 10 s.
- A standing player within 6 blocks still keeps the animals off a downed friend.
- Built; not played.
