# Red (unparryable) mob blows rarer and slower
- Andy: "armed enemies should [not] attack with unblockables EVERY attack anymore, it should be closer to 20%. and the windup should be longer so players can react".
- **Attack tells** (`AttackTells`): a mob red only for its attack damage (10+) now glints red on 1 blow in 5 (`RED_CHANCE`), rolled per blow; the rest are white and can be parried. A body 1.35+ wide (iron golems, ravagers, wardens) is still always red.
- A red blow winds up 20 ticks instead of 10 (`HEAVY_WINDUP`): the arms stay up, the step in comes 3 ticks before the blow, the crosshair cue closes over the full 20, and the red star holds the whole time (`Glint` carries its wind-up; `TellGlintParticle`).
- `AttackTells.glint(level, at, heavy, windup)` for Kingdoms' longer red swings.
- Unchanged: parry and block windows, lock-on bonuses, the Alt roll.
- Built; not played.
