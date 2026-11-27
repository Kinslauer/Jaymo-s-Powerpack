---
status: done
task: legends-p3-big-symbiote
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2281
---
# Legends patch 3 (big symbiote): morning report for Andy

Overnight checks by Claude on the Hermes bots' work. One line per check.

## Checks
- 2026-11-27 1:40 AM: Not started. Apollo was given it at 12:06 AM but made no branch or PR (its last run, 12:27 AM, did nothing; Hermes keeps warning that a `hermes update` is unfinished). Task is open with no claim, so I asked YOUTH to put a bot back on it. No PR yet.
- 2026-11-27 1:54 AM: YOUTH moved it to Bacchus (hermes-b), since Apollo is busy on structures-5-faction-table. Only one builder runs at a time, so Bacchus starts once Apollo finishes. Still no branch or PR.
- 2026-11-27 3:12 AM: Still waiting, not stuck. Apollo is busy again: the 2:56 AM review of its faction-table PR (#2216) asked for changes, so the builder slot is taken and Bacchus hasn't run since 12:17 AM. No branch or PR yet. I didn't ask YOUTH for anything: a second builder can't start while one is running (dispatch_bots.py lets only one builder run at a time), and nothing has gone stale.
- 2026-11-27 4:48 AM: Bacchus opened [PR #2281](https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2281) (base master, CI green) and the text pass found nothing. My review sent it back (status changes) with 8 problems, each confirmed by a separate checker: it brings back 3 change notes already released with #2257 (would log them twice); the first new key is F1, which is vanilla's Hide GUI, and the key list wasn't in the PR; one javadoc was copied instead of moved and another left a stray `/**`; the "face the wall" check drops you mid-climb when you turn to aim a wall leap; in the takeover, mashing Shift scores the same as hitting the beat; flying stones use the wrong world after a dimension change; and all five fixes are in one commit. I didn't build it on the PC; CI built all three mods green. Not played.

## Lessons taught
- 30. When merging master, take its deletions of change notes; only your own note may show in `changes/`.
- 31. "Free across the pack" includes vanilla (F1 = Hide GUI); list the keys you found in the PR.
- 32. Moving a javadoc means deleting the old one; no lone `/**` left behind.
- 33. A check for starting something (climb only when facing the wall) must not end it every tick.
- 34. "Timing beats mashing" needs a cost for a missed press; do the maths for a masher in the same mode.
- 35. Things left running after a move ends tick and hit in their own level, and stop on a dimension change.
- 36. One commit per fix when the brief says so.

## Notes
- 2026-11-27 02:25 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-27 11:01 PR #2281 is merged into master; moved to Agents/Done.
