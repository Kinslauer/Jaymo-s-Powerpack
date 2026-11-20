# villagers scrub blood out of their homes
- **Villagers clean up blood indoors**: a blood stain on a settlement's floors under a roof (Powers' pools, smears and
  footprints) is seen by the nearest grown villager within 14 blocks who can see it and has nothing pressing to do.
  They sometimes grumble about it in a bubble, walk over and scrub it with swipes of the arm, a splash and a brush
  sound, until it's gone, then the next stain in the same room, and now and then say they're done. A big pool takes
  them about half a minute.
- Guards stay at their posts. A villager busy with something that can't wait (a fight, a talk, tending, the dead, a
  prisoner, loot: `TalkBusy.occupied`), afraid, held by the guards, downed or shooing an animal out leaves it. Blood
  with a body still in it (a corpse, the downed) is left until the body's gone. They give up after a minute and leave
  that spot for two. Outdoors the rain does it. Nothing without Powers.
- Loading hint 187 now says how to scrub blood off floors and walls.
- Code: `BloodChores`, `PowersCompat.bloodStains`/`scrubStains`; lines in `lang/parts/blood_chores.json`.
- Built; not played.
