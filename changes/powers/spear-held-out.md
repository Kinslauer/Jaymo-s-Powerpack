# Vanilla spears held out again
- **The spear is held out ahead again**: a vanilla spear (wooden to netherite) is no longer stood upright at your side by the armed stance. It keeps the game's own spear pose, pointed ahead (with Better Combat's two-handed polearm hold over it), and Fresh Animations steps aside for that pose so it doesn't hang the spear from a walking arm like any held item. Other polearms (halberds, glaives, mod spears, lances) keep the armed stance as before.
- `ArmedPose.heldOut` (`#minecraft:spears`) leaves the stance off; `PoseTakeover.spearHeldOut` (the game's `ArmPose.SPEAR` on either arm) pauses Entity Model Features like our own poses do.
- Built; not played.
