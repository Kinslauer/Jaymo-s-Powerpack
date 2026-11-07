---
status: in-review
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Compare ParCool moves with ours"
claimed_by: parcool-ledges-a2d223
pr: 1116
suggested_owns:
  - src/main/java/com/jaymospowers/core/Ledge.java
  - src/client/java/com/jaymospowers/client/LedgeClient.java
  - src/client/java/com/jaymospowers/client/LedgePose.java
  - src/main/java/com/jaymospowers/freerun/Freerun.java
  - src/client/java/com/jaymospowers/client/freerun/FreerunClient.java
  - src/client/java/com/jaymospowers/client/freerun/BarHangPose.java
  - src/main/java/com/jaymospowers/freerun/WallSlide.java (new)
  - src/main/resources/assets/jaymospowers/lang/parts/freerun-ledge.json (new)
---
# ParCool moves, track A: ledges and walls

**Andy's words (2026-11-07):** "Go to their tutorial on github and make a checklist of everything we dont have yet",
then he ticked these on the checklist page (https://claude.ai/artifact/SoKZS5ZL3DvCmLKNvCoVm5, db doc picks/andy)
and said "Split it and put it in the vault inbox".

**What's wanted:** four moves, all our own code after ParCool's ideas (ParCool is LGPL and Forge-only, so read its wiki
https://github.com/alRex-U/ParCool/wiki for the idea, never copy its code or art):
- **Ledge shimmy:** while hanging on a ledge (Ledge grab), A/D moves you sideways along the edge, slowly, stopping
  where the edge ends or a wall blocks. Hanging pose shuffles hand over hand.
- **Castaway:** while hanging on a ledge, Jump + Back kicks you off backwards, out and up (like the vertical wall
  run's eject, `Freerun.EJECT_OUT/EJECT_UP`). Today Back only lets go.
- **Wall slide:** falling beside or facing a wall, hold right-click with an empty hand (match the ledge grab's
  empty-hand click, `LedgeClient.clickGrab`) to press against it and slow the fall; long enough on the wall cuts or
  removes fall damage. Ice, glass and leaves are too slippery (`FreerunWalls.slippery`). Costs Feathers.
- **Moving along chains and bars:** in Bar Swing (`Freerun` BAR, `BarHangPose`), W/S (or A/D across) moves you hand
  over hand along a horizontal chain, fence or iron bar run; the existing swing and Jump release stay.

**Where to start:** `docs/codemap/powers.md` entries for Ledge, LedgeClient, LedgePose, Freerun, FreerunClient,
FreerunWalls, BarHangPose; memory notes on ledge grabs (#973, #1051). Tuning numbers as `static final` at the top.

## Notes
- Track B (parcool-running-and-falling) must not edit Freerun.java or FreerunClient.java; this track owns them.
- Bump the Powers version (take the next free one right before merging).
- 2026-11-07 14:43 Coordinator: agent note says in-review with PR 1116, so set the task to in-review for the reviewer.
