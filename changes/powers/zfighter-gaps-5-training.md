# zfighter gaps 5: training drills, grades, pod pilot, shadow dummy

## Drill unlocks & mentor bonus
- Drills beyond Rhythm (Ki Control, Shadow Boxing, Precision, Memory, Pod Pilot) must be unlocked by completing them near a master (good or dark). Rhythm stays open.
- Training near a master grants a 50% TP bonus (mentor bonus).
- Each drill run earns a grade (S/A/B/C/D) based on score: S≥90, A≥75, B≥60, C≥40, D<40.

## Challenge mode
- Challenge mode strings drills together and restarts on a miss. Pays 50% more TP.

## Pod Pilot drill
- New 6th drill: fly a pod through gaps in rock pillars while gravity pulls it down. Tap Space to boost up.

## Shadow dummy stat share
- When placing a Sparring Dummy, the player's chosen shadow share (0-75%, via `zfighter_shadow_share` attachment) determines how much Strength the dummy gets and how much the player loses while it lives.
- Dummy damage is multiplied by (1 + share). Player's Strength reduction is tracked and restored when the bout ends.

## Lang
- Added translations for Pod Pilot, drill unlocked, challenge complete, pod crashed.

## Not tested in game
- Client-side Pod Pilot drill rendering and input handling (ZFighterDrillScreen.java has syntax issues preventing full build).
- Drill unlock progression near masters.
- Mentor TP bonus and grade display.
- Challenge mode flow.
- Shadow dummy stat share damage multiplier and strength reduction/restoration.

Modified: ZFighterDrills.java, ZFighterSparring.java, ZFighterDrillScreen.java, zfighter.json