# the tundra's muskox, herds that keep together, and predators that stop when fed
- **Muskox** (`wild/Muskox`): a giant shaggy goat cousin for the far cold, in herds of 3 to 6 on snowy plains (and the
  tundra and lichen woodland when Powers adds them; biome tag `jaymoskingdoms:has_muskox`). Brown or black, 24 health,
  slow. It leaves people alone, but a grown one that's struck butts back with a hard knockback for a few seconds; a
  calf just runs. Drops mutton and leather. Breed it by hand with glow lichen. Spawn egg.
- **It grazes glow lichen**: when hungry (about every two minutes) it walks to the game's glow lichen within 10 blocks
  and eats it off the rock, head down. Only vanilla `minecraft:glow_lichen`: sprouted or snipped lichen is left alone.
- **It sheds string after each feed**, more the better it lives: room about it, 2 to 8 other muskox within 16 blocks,
  open sky by day, and its home country (`jaymoskingdoms:muskox_home`). None of those: 0 or 1 string. All four: 6 to 8.
  More than 12 others packed within 8 blocks: nothing. So a herd on open tundra with lichen to eat is a string farm
  you keep by looking after it.
- **Herds keep together** (`wild/HerdPull`): a muskox that has strayed strolls back toward its herd, and a herd drifts
  toward lichen within 24 blocks. Wild cows, sheep, pigs, chickens and goats keep together the same way (entity tag
  `jaymoskingdoms:herds_together`). Never a leashed, named, tamed or ridden animal, and never through a fence: an
  animal that can't walk the whole way isn't pulled, so pens don't pile into a corner.
- **A herd recovers its losses**: when a muskox dies, the others within 32 blocks remember it for three days. One that
  remembers, with no calf near and fewer than 10 in the herd, is ready to breed for two minutes after its next feed.
  With Respawning Animals, muskox never despawn and are not refilled by it (tag
  `respawninganimals:persistent_animals`): a herd you wipe out is gone, a herd you leave some of comes back.
- **Muskox run from their hunters** (entity tag `jaymoskingdoms:predators`: polar bears, wolves, feral wolves, coyotes,
  grizzlies, and Naturalist's and Cube Animals' big cats and bears), each its own way.
- **Fed predators stop hunting** (`wild/FedPredators`): after a polar bear, wolf, fox, feral wolf or coyote (entity tag
  `jaymoskingdoms:sated_predators`, with other mods' hunters) kills an animal, it doesn't hunt another animal for two
  days. Players, villagers and anything that hurts it are still fair game, and wolves stalk players as before. It's
  saved on the animal.
- **The young go first**: a hungry predator that picks an animal takes one of the young in reach instead of an adult.
- **Polar bears hunt muskox.**
- The muskox's model and both coats are our own (`docs/Muskox-Model-Spec.md`, `tools/gen_muskox.py`); pose previews in
  `art-overhaul/muskox/`.
- Classes: `wild/Muskox`, `wild/HerdPull`, `wild/FedPredators`, `mixin/wild/PredatorTargetMixin`,
  `client/wild/MuskoxModel`, `client/wild/MuskoxRenderer`.
- Built (compiles); not played. Not seen in the game: the model and its poses, the grazing walk, the pen test, a
  polar bear's hunt, and whether Naturalist's, Cube Animals' and Bear Minimum's hunters pick prey the way the game's
  do (if they don't, being fed changes nothing for them).
