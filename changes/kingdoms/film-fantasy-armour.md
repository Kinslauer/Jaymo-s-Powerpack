# The Long Defence's cast in Fantasy Armor
- Andy: where Fantasy Armor is loaded, the Long Defence film dresses its cast in its sets: Thessaly in Lady Maria,
  Varro in Golden Horns with no helm, Grimsby in the Thief gear, Ambrose in the Wandering Wizard, the Grue in the Dark
  Lord plate, the first four orcs it raises in Dark Cover, and the Quiet One in the Redeemer plate, which comes up over
  him with the white (legs first, then the rest). Without the mod everyone wears what they did.
- Film actors are jointed, so `FilmGear` can't paint GeckoLib armour on them: `FantasyArmourLayer` draws each worn
  piece from Fantasy Armor's own model and texture (read at run time, nothing copied), riding the actor's head, body,
  upper arms, thighs and shins.
- Built; not played. Fantasy Armor itself isn't in the pack yet (its own patch).
