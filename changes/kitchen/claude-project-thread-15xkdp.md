# Corpse search hint only when there's something to search
- A body now tells clients what a tap on it will do (`CorpseEntity#tapDoes`, synced every 5 ticks and whenever the loot screen closes): open the loot screen, give up a bone (a picked-clean skeleton) or nothing. Jaymo's Powers' context hints read it through the object share (`Corpses.SHARE_TAP`), so the "search the body" line goes away once a body has nothing left, instead of players tapping on and on at it.
- A tap on a body with anything on it still always opens the loot screen; whether it still wears gear is read once per look, not on every check.
- Built; not played.
