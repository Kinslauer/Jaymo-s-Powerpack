# A far bed is not read while its land isn't loaded (Deck crash 16:45)
- **Fix (watchdog crash)**: the shy deer looked at whether a player's bed still stands (`ShyDeer.home`) wherever the bed
  was; with the player far away its chunk wasn't loaded, and loading it waited 60 s on the server thread behind the
  Deck's world generation (crash 2026-11-13 16:45, chunk [2, 8]). Now a bed in land not loaded isn't read (the deer
  just don't learn it this time); the same for a life aim's "has a bed in a realm" (`LifeAims`) and the old rules'
  bed check (`OldRules`).
- Built; tested on the Deck server.
