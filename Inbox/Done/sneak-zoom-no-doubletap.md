---
status: done
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-a
pr: 1186
suggested_owns: [src/client/java/com/jaymospowers/client/SneakZoom.java, docs/AI-HANDBOOK.md]
writer: hermes-writer
text_pass: done
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
- 2026-11-09 09:14 Text pass: reviewed change note (changes/powers/sneak-zoom-no-doubletap.md) — only player-visible text in PR; no lang parts, chat lines, tooltips, or hints added. Change note is concise and follows style.
- 2026-11-09 09:20 Review (head cbf4cd3): pass. Double-tapping Shift no longer zooms; Shift+Left Alt still zooms until Alt is let go, Alt alone still rolls, and the sprint slide is unchanged; the handbook line is updated. Not tested in game. Small leftovers for a later tidy-up, not worth another round: SneakZoom.java still keeps byAlt (now only written), held, sneakWasDown and TAP_HOLD_TICKS, which nothing reads any more; FreerunClient.java:491 newly adds a '(0.15.12)' version tag to a comment; KeyConflicts.java:28 still says double-tap Sneak zooms. Still open from the task: ask Tiv whether he wants anything to replace the double tap.
- 2026-11-09 09:21 Reviewed; Andy allowed sending it to the coordinator.
- 2026-11-09 09:33 PR #1186 is merged into master; moved to Inbox/Done.
