---
name: chrono-downed-rewind
description: Chrono R (Rewind) works while downed and gets you up unbattered; no fall damage while Overclock runs; PR #411 MERGED (Powers 0.15.83), 2026-10-22
metadata:
  type: project
---
2026-10-22 Andy: "chrono should be able to use R when downed, this will revert him as it should" + "chrono should not take fall damage when using overclock". PR #411 MERGED 03:5xZ as Powers 0.15.83 (compiled on Andy's PC, not playtested).
- `Power.usableDowned(Player, PowerKey)` (default false) is the general hook for keys that stay live while downed; checked by client DownedKeysMixin and server PowerEvents. Chrono: R only, unlocked, not passed out (my call; Andy can ask to allow passed-out).
- Chrono history stops recording while downed (`ChronoState.downed`), so Rewind goes to the 3 s before the fall; `Downed.undo` = getUp without Battered (#412); health = then-health, min 30%.
- Overclock: ChronoPower ALLOW_DAMAGE cancels IS_FALL while overclocked.
Related: [[chrono-overclock-toggle]], [[chrono-g-reset]], [[downed-guards]].

2026-11-05: #1013 MERGED (Powers 0.20.92): cheat-death checkpoints no longer need 3 hearts (Andy's R did nothing after a long low-health fight); downed, R goes to cheat death when out cold, Rewind on cooldown, or an execution is coming (`ChronoCheatDeath.downedOpen`). Still no prompt anywhere.
