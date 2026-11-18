# mail is good at three things: arrows, Slowness, quiet
- **Arrows**: an arrow whose head has no way of its own with armour does 0.75 of its damage against mail (`Blows.ARROW_VS_MAIL`); bodkins, swallowtails and war darts keep their own shares.
- **Nimble**: only plate slows movement now, scaled by the plate share of the four armour slots (`LightArmour.plateShare`): in a full suit the climbing grip (and a Climber's climb speed) is 30% less, a freerun vault 20% slower and the swimming stroke 20% weaker. Padding and mail cost nothing; a Climber's unlimited grip stays unlimited.
- **Slowness**: each mail piece worn shortens a newly added Slowness by 10% (a full set, 40%). A full mail set sprinting while slowed needed no change: vanilla 26.3 never stops a sprint for Slowness.
- **Quiet**: the distance mobs notice you at is scaled by armour, per slot share: padding x0.9, mail x1.0, plate x1.15 (bare slots x1.0); it multiplies with Hiding, Rigs and Darktide.
- New `core/LightArmour` holds the numbers; mixins `LightArmourSlowMixin`, `LightArmourQuietMixin`, `LightArmourSwimMixin`. Built; not played.
