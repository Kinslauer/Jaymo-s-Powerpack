---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [src/client/java/com/jaymospowers/client/SneakZoom.java, docs/AI-HANDBOOK.md]
---

# Double-tapping Sneak no longer zooms

The zoom stays on its other keys; the Shift double tap stops reaching for it.

## What's wanted

Tiv, in the Tasks thread: "shift double tap should no longer zoom."

## Notes

- `SneakZoom` (client) currently opens OK Zoomer's zoom on a Sneak double tap within 6 ticks, and also on Shift + Left Alt.
- Take out the double tap only. Tiv didn't ask to remove Shift + Alt, so that path and Alt-alone rolling should stay as they are, unless he says otherwise.
- Side effect to tidy up: `Slide` and the freerunner's held-Sneak code treat a short tap as "the zoom's double tap" and leave it alone — that guard is dead once the double tap is gone.
- `docs/AI-HANDBOOK.md` still describes "tap then hold Shift" as the zoom gesture; fix the doc in the same PR.
- Ask Tiv if he wants anything to replace the double tap, or the gesture to simply go unbound.
