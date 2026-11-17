# temperament idles come and go
- **Temperament looks in spells** (Andy: "none of the idle animations should play 24/7"): a timid or suspicious
  player's scared hunch and glancing, a hotheaded one's tense arms, a gloomy one's slump and a cheerful one's bounce
  no longer show all the time. Time is cut into 3 second slots, and each slot is on by chance (45% for a scared player,
  30% for the rest), so a look shows for a few seconds, sometimes longer, then rests; it still eases in and out
  (`BodyMotion.inSpell`, knobs `SPELL_SLOT_TICKS`, `SPELL_CHANCE_SCARED`, `SPELL_CHANCE`). The spells come from the
  player's id and the game time, so everyone in view sees the same.
- Built; not played.
