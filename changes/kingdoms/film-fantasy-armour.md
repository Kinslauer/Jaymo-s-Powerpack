# The Long Defence's cast in Fantasy Armor
- Andy: where Fantasy Armor is loaded, the Long Defence film dresses its cast in its sets: Thessaly in Lady Maria,
  Varro in Golden Horns with no helm, Grimsby in the Thief gear, Ambrose in the Wandering Wizard, the Grue in the Dark
  Lord plate, the first four orcs it raises in Dark Cover, and the Quiet One in the Redeemer plate. Without the mod
  everyone wears what they did.
- Andy: the Redeemer plate "should weave onto his body like the symbiote usually does". It goes on with the white and
  winds out over him in the symbiote suit's order (chest, then arms, head, legs, boots last), each piece growing out
  from its joint, twisting and swollen at first, then settling flat (`FantasyArmourLayer.WEAVE_*`).
- Film actors are jointed, so `FilmGear` can't paint GeckoLib armour on them: `FantasyArmourLayer` draws each worn
  piece from Fantasy Armor's own model and texture (read at run time, nothing copied), riding the actor's head, body,
  upper arms, thighs and shins.
- The tape holds each Fantasy Armor piece with what the actor had as its fallback, so one tape plays in games with or
  without the mod. The shipped tape predates this: re-record it once (`/kingdomfilm record long_defence`).
- Built; not played. Fantasy Armor itself isn't in the pack yet (its own patch).
