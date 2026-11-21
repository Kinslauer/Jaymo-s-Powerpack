# mutant zombie reskin

Reskin the mutant zombie to match the walking-zombie look: green zombie head, cyan chestplate and blue pants.

- art/mutant_zombie.bbmodel - editable Blockbench model (GeckoLib animated-entity format), 6 bones and 6 cubes, about 1.9 blocks tall.
- src/main/resources/assets/jaymospowers/geckolib/models/entity/mutant_zombie.geo.json - the GeckoLib model.
- src/main/resources/assets/jaymospowers/geckolib/animations/entity/mutant_zombie.animation.json - idle and walk animations.
- src/main/resources/assets/jaymospowers/textures/entity/mutant_zombie.png - 64x64 reskinned texture.

The model, texture and animations are installed in jaymospowers but are not yet bound to an entity or renderer, so nothing changes in-game yet.

Not tested in game: no entity or renderer uses the model yet; not boot-tested.
