# Fix: crash on joining with no power while attuned or reading magic

- (Jung) The game crashed a few seconds after joining for a player with no power who is attuned or reads magic
  ("NullPointerException" in `Mana.blindToMana`, from the clash cinematic's tick). The check for the elemental powers
  that can't see mana looked a missing power up in a set that throws on a missing value. A player with no power now
  simply isn't an elemental power user.
- The same lookup in the story roads (the hermit's road) had the same fault and is fixed the same way.
