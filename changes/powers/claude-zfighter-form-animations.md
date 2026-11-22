# Z Fighter form animations

Adds GeckoLib animations for the four Z Fighter form models: idle, walk and
attack for the bulky Earthling full power, the towering Rubber-folk giant, the
slender floating Divine-born with halo, and the insectoid Bio-built with back
wings and curving tail. Each animation targets its form's bones (legs, torso,
head, arms, plus halo for Divine-born and wings/tail for Bio-built) with motion
matched to the silhouette: heavy and grounded for the Earthling, slow and
stretchy for the Rubber-folk giant, floating with a spinning halo for the
Divine-born, and wing flutter with tail whips for the Bio-built.

- assets/jaymospowers/geckolib/animations/entity/: zfighter_form_earthling,
  zfighter_form_rubberfolk, zfighter_form_divine and zfighter_form_bio
  animation files (idle, walk, attack in each)

Built; not played. The forms reuse the player model/aura and are not yet wired
to entities, so the animations are not tested in game (entity wiring is the
following integration task).
