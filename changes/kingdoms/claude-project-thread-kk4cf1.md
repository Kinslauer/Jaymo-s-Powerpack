# story quests: 12 engine changes, 19 additions to the old tales, 17 new tales

All 48 rows Andy picked from the story-quest list (2026-11-17, "Build!"). Tales are still data files
(`data/jaymoskingdoms/tales/`), format in `docs/StoryQuests-Plan.md`, checked by `tools/tale_check.py`.

**What tales can now do (the 12 engine rows)**
- **Time doesn't wait**: a step can have its days (`within`, or a named `clock` shared by several steps) and a `late`
  way on. The tale goes on without you; a step left that way writes no journal, mark or pay. The journal and the asker
  say the time left in words (`{left:}`, `{in:}`). Used by The Asking Price, The Night Bell, The Fair Left Behind, The
  Bride Road and most of the new tales.
- **Day and night**: `when` on a step (a night escort only arrives after dark; a moon means that moon's night),
  `hours` on a cast member, `night`/`day`/`moon:` conditions, a `not_now` line. Off their hours a tale's people walk
  off out of sight and go in, and come back out when their hours come round: the same person, so hearts, what they
  remember and what they were told last.
- **Who you are matters**: conditions on heritage, patron, town reputation, hearts with the speaker, coin carried,
  dress (fine, plain, ragged, armoured, bloodied), being cursed or human.
- **Persuade, threaten or pay**: a `try` on a reply, odds said in words from the listener's temper, hearts, reputation
  and deeds; bribes priced by greed and paid in real coin; a failed try always has its own way on. Also a throw of dice.
- **Naming who did it**: a `name` step listing everyone met, weighed by the clues found; a wrong name has its own ending.
- **Know the beast**: `weak_to` on foe steps (fire, silver, the Warden's light, garlic, wolfsbane, and running water:
  a blood-drinker fought by a stream). The bestiary writes the note as soon as you learn it, on the page for that kind
  of beast, one page a note.
- **Companions speak up**: `voices` before and after a choice by Convictions belief, with `for`/`irks` moving the bond.
- **It comes back weeks later**: an ending's `later` list at 10 to 40 days: a letter (brought again another day if
  the runner can't find you), a scene, town talk, someone who settles in the town (a spared thief-taker can come back
  as one of its guards), a new grave with a board giving the name, or a household that packs up and goes;
  with a `still` condition asked on the day.
- **Letters you can't read**: a `tongue` on letters, clues and things; a priest or librarian reads it aloud for a coin
  and then knows what it says.
- **Lies come out**: a `lie` on a reply, remembered, checked later (one day for the whole party); it can change an
  ending or bring someone back, and the one lied to loses hearts when next you're near them.
- **Tales from the board and the inn**: `by: notice` (the work board shows a tale notice now and then) and more rumours.
- **Tales that grow into a story**: `arc`, and starts that need an earlier tale's marks and a number of days since it. A villager holding a later part
  offers it to the player it's meant for, and gives anyone else a tale of their own.

Smaller pieces the tales needed: a `vigil` step (a night kept somewhere, lines and sounds by the hour), a `tail` step
(follow someone unseen: too near and they see you, too far and they're lost), a `reap` step (a field is sown ripe and you
cut it), cast who walk to a new place when the day changes, cast who lie down while you watch, a black candle (and a
white one) stood on a tavern window sill, `await` remarks, letters addressed to someone else, and **Wolfsbane** (a new
item, now and then from cut fern; only the wolf tales use it so far). In The Keeping, the husks upstairs can be laid to
rest before the smith wakes (`KeepingCells`).

**Added to the 19 old tales**: The Empty Fold (a real wolf hunt: tracks by day, the pack at night, the old grey and
fire), The Lantern That Went Out (the barrow vigil, the ward's letter), Salt and Iron (the hearing at the well before
both priests), The Night Bell (the raid comes on its night; what you readied changes the defence), The Fair Left Behind
(the fair moves on by the day; catch it or follow the ruts), Hail Glass (the den, the fence's dice, the clerk's eyes),
The Cave-In (his air runs out in a day; the hunter's writ may be unreadable), The One Who Walks at Moonrise (tracks,
the bestiary, wolfsbane and silver, the night behind the cellar door), The Bride Road (scout the ambush or take the
long road; the lie can come out), The Asking Price (the ransom day; following the steward at night), The Stone That
Hums (still nights, two hearers, a sign weeks later), A Debt in Potatoes (the Tallow Room, the black candle, the
sister's letters), The Wrong Light (the drowned lie down in the burying ground as the bell is rung), The Fourth Measure
(galls gathered by night, the test for the grey, the sister's bargain), The Cold Hearth (camp nights and the village's
names), What Was Owed (how much to own up to, line by line; the widow's secret), The Brother Who Stayed (the harvest
letter and a new short tale, The Barley Cut), The Name She Left (the search party on the same hills), The Keeping.

**New tales**: The Crossroads Gibbet, Short Measure, The Ducking Stool, The Novice, Guests After Dark, The Deserter's
Coat, The Boundary Stone, The Wolf in the Wood, What the Well Was Promised, A Hare for the Pot, The Lists, The Night
Market, The Cordon, The Feast-Day Sermon, Letters to a Dead Man, The Heir in the Hayloft, and The Black Candle in three
parts (The Runner's Round, A Candle in the Window, Nobody Sent You).

**Not as the row reads** (named, not cut quietly): The Cave-In's "dig him out" is the blast step under a one-day
deadline, not digging by hand. The Fourth Measure's galls and the finished test are tale things; only the spirit is a
real Kitchen item, and that path is offered only where Kitchen is installed. The barley is wheat (the pack has no
barley) and the companion isn't standing in the field. The bestiary lookup in Moonrise is offered, not forced. The
Lists is fought on foot. The Night Market's stalls, The Cordon's fence and count of the sick, and The Boundary Stone's
fence, barn and cow are people and words, not blocks. Parts two and three of The Black Candle can be offered in a
different town from part one. People who go home off their hours walk to a spot out of sight, not to their own bed
or the tavern.

Classes: `Tales`, `TaleWays`, `TaleCast`, `Tale`, `TaleAfter` (graves, people moving away), `KeepingCells`, `QuestJobs`, `TownNotices`, `Bestiary`,
`Garlic` (wolfsbane), `StoryHoldScreen` (hints
193-204), `lang/parts/tales.json`, `tools/tale_check.py`, `docs/StoryQuests-Plan.md`.

Emigration (any household, not only a tale's): a family no longer splits when one of them may not leave (a ruler,
a town's leader, someone sworn or held, a player's family, one of a running tale's people): then they all stay.

Every row was checked against the pick list by independent readers (tales and engine separately) and their
findings fixed. Built (full build and the tale checker pass: 79 files, 0 problems). **Not played.** Lore placements are in `Lore.md` as
`(new)` paragraphs for Andy to approve.
