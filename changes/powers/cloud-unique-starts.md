# The life story's starts in Powers' worlds are each player's own

Tiv: "Every player should have their own start, it should search for another if they are already taken." Jaymo's Kingdoms now remembers every player's start. The starts Powers places itself take a check from Kingdoms of which places are nobody else's, and use it:
- **Pale Moon**: a crash site nobody else woke at (out to twice as far if the ring is full). **Rustfall**: a mine mouth nobody else escaped from (the same).
- **Verdant World**: the village nearest the Lookout that nobody else was raised in, looked past up to three taken ones; with none free, a spot of your own further out.
- **The Maw**: if the pad is someone else's start, a deck of your own (a pad and its pod) on a ring 70 blocks round the hole, past the wrecks and still in its pull. With all ten taken (39 blocks apart), the pad.
- **Raised by a demon master**: a demon hall nobody else was raised in; a taken one is passed over and the next raised.
- **Back from the afterlife**: a free spot on the Other World's platform when the arrival spot is someone else's start.
- **Lost in time**: the future's arrival square, else a street round it (16-96 blocks out, near the square's height). A newcomer who waited for the future to form is checked when they're sent.
- **Escaped the alchemist's lab, Fresh off the bench**: the room is built where nobody else started, out to twice as far if it must be.
- Kingdoms calls the new overloads: `ZFighterStarts.land(..., BiPredicate)`, `Future.strand(..., BiPredicate)`, `LabEscape.start(..., BiPredicate)` and `ZFighterAndroidStart.start(..., BiPredicate)`. Also `WildRoom.find`, `ZFighterDemonRealm.raiseHome` and `ZFighterAfterlife.strand` with a free check, and `ZFighterSpace.deck`. The old signatures stay. Tuning: `ZFighterStarts.FREE_TRIES`, `FREE_STEP`, `DEMON_FREE_STEP`, `MAW_DECK_RADIUS`, `MAW_DECKS`; `Future.ARRIVAL_FREE_*`; `ZFighterAfterlife.FREE_TRIES`, `FREE_REACH`.
- Built; not played. Not tried with two players on a server.
