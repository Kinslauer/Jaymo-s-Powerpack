---
status: in-review
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: claude-cloud
pr: 1228
suggested_owns: [src/client/java/com/jaymospowers/client/KeyConflicts.java, src/client/java/com/jaymospowers/client/mixin/, src/main/resources/assets/jaymospowers/lang/parts/]
---

# F5 stops at first and third person; the front-facing camera moves to F6

Vanilla's F5 cycles three views. Tiv wants two on F5 and the mirror view on its own key.

## What's wanted

Tiv, in the Tasks thread: "F5 should only toggle between first and 3rd person, the front facung camera should.be toggled on w/ f6."

## Notes

- F5 keeps its normal job, just stops at two: first person ↔ third person behind. It never lands on third-person-front.
- F6 becomes the front-facing camera toggle: press it and you look at your own face; press it again and you're back where you were, or back to third-behind. Pick whichever reads better and say which in the PR.
- The new key wants a proper entry in the controls menu, not a hard-coded key code, so it can be rebound and shows in the list.
- F6 may already be taken by something in the pack or a mod in it — `KeyConflicts` is the existing place where the pack moves another mod's binding off a key it wants. Check what's on F6 before claiming it, and don't silently break someone else's key.
- Cinematics and powers that force a camera (`UltimateCinematic`, `BeamCinematic`, `GiantCamera`) set the camera type directly; make sure the new toggle doesn't fight them mid-cutscene, and that the front camera survives a cinematic ending.
- No lang churn beyond the one new key name.
- 2026-11-09 23:56 Cloud agent session sesn_01MPUYWbeHeGYBs9PRyVgJTX opened PR #1228.
