---
status: ready
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Compare ParCool moves with ours"
claimed_by: parcool-running-655696
pr: 1118
suggested_owns:
  - src/main/java/com/jaymospowers/freerun/FastRun.java
  - src/main/java/com/jaymospowers/freerun/AirFlip.java
  - src/client/java/com/jaymospowers/client/freerun/AirFlipClient.java
  - src/main/java/com/jaymospowers/freerun/LandingRoll.java
  - src/main/java/com/jaymospowers/stamina/Feathers.java
  - src/main/java/com/jaymospowers/water/Dives.java
  - src/client/java/com/jaymospowers/client/water/SwimStrokePose.java
  - src/main/java/com/jaymospowers/freerun/moves/ (new package: long jump, charge jump, safety tap, sky dive, fast swim, hiding)
  - src/main/java/com/jaymospowers/freerun/gear/ (new package: traceur gloves and boots)
  - src/main/resources/assets/jaymospowers/lang/parts/freerun-run.json (new)
---
# ParCool moves, track B: running, falling, swimming, hiding, gear

**Andy's words (2026-11-07):** "Go to their tutorial on github and make a checklist of everything we dont have yet",
then he ticked these on the checklist page (https://claude.ai/artifact/SoKZS5ZL3DvCmLKNvCoVm5, db doc picks/andy)
and said "Split it and put it in the vault inbox".

**What's wanted:** our own code after ParCool's ideas (LGPL, Forge-only: read https://github.com/alRex-U/ParCool/wiki
for the idea, never copy its code or art). Everyone gets them; costs come out of Feathers.
- **Long jump:** after a while in a fast run (`FastRun`), tap Sneak as you jump: a long, flat leap over big gaps.
  Mind that Sneak taps already mean zoom (double tap) and air flip (single tap in the air).
- **Charge jump for everyone:** stand still and hold Sneak to charge (shown on the Feathers row), Jump to leap up to
  twice as high; moving cancels. Web already has its own (`WebSwing#tickChargeJump`), leave that one alone.
- **Automatic trick jump:** jumping out of a fast run sometimes flips on its own (front or back flip), using the air
  flip's pose (`AirFlip`); the Sneak-tap air flip stays.
- **Safety tap:** a landing without the forward roll (crouched knees, absorbs the impact) for tight spaces: the dodge
  key at touchdown with no direction held, next to `LandingRoll`. Less fall damage cut than the roll.
- **Sky dive:** in a long fall, Jump spreads you out: falls a bit slower and steers faster; Jump again ends it. Must
  not clash with Leap of Faith's head-first dive or `Dives`.
- **Fast swim:** sprinting in water for a while breaks into a faster swim with its own stroke (`SwimStrokePose`).
- **Hide in leaves or hay:** sneaking into a leaves block or hay bale hides you inside: mobs lose track of you, your
  name tag is hidden; taking damage, breaking the block or using items ends it.
- **Traceur gloves and boots:** wearable gear (gloves in a bauble slot if that fits our baubles, boots as armour),
  dyeable; both add Feathers, the boots add a little speed, the gloves stop wall-slide damage. Our own models.

**Where to start:** `docs/codemap/powers.md` entries for FastRun, AirFlip, LandingRoll, Slide, Dodge, Feathers,
Dives, SwimStrokePose; the baubles package for the gloves.

## Notes
- Don't edit Freerun.java or FreerunClient.java (track A owns them): register the new moves with one init call in
  JaymosPowers / the client initializer instead.
- Gloves' wall-slide protection hooks into track A's WallSlide; if A isn't merged yet, leave a small hook and say so.
- Bump the Powers version (take the next free one right before merging).
- 2026-11-07 14:43 Coordinator: agent note says in-review with PR 1118 (moves), so set the task to in-review; the gear PR follows.
- 2026-11-07 11:59 Review (head 3ef019b): pass for the moves, merged as #1118 (Powers 0.21.51). Players get the long jump, charge jump, trick jumps out of a fast run, safety tap landing, sky dive, fast swim and hiding in leaves or hay. Compiles and all checks pass; not played. Not done: the traceur gloves and boots aren't built yet and need their own PR (gloves can use WallSlide's hook from #1116).
- 2026-11-07 11:59 Reviewed; Andy allowed sending it to the coordinator.
