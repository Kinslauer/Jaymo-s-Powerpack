# court wizard creeper ward
- While a kingdom's court wizard is alive and at home, a magical ward covers the kingdom's plot: creeper
  explosions inside it fizzle before they blow (a fresh wizard prevents most of them), creepers within
  are slowed 30% and their blast radius halved, and about half of creepers at the ward's edge feel the
  disturbance and turn away. When the ward can't prevent a blast, the blocks it would break are rewound
  back over a few seconds with a visible time-reversal shimmer. The ward breaks when the wizard dies,
  leaves the plot, or a siege starts against the kingdom.
- `CreeperWard` (new), changes in `CourtWizards` (ward re-evaluated on wizard death and each step),
  `ServerExplosionMixin` (creeper explosions routed through the ward), `JaymosKingdoms` (registration).
- Built; not played.
