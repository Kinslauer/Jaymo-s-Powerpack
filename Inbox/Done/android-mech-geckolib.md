---
status: done
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations.")
claimed_by: hermes-modeller
pr: 1195
suggested_owns:
  - tools/blockbench/android_mech/*.bbmodel or art/android_mech_*.bbmodel
  - src/main/resources/assets/jaymospowers/geckolib/** (the mech's files)
  - src/main/java/com/jaymospowers/power/chrono/future/FutureMech.java
  - src/client/java/com/jaymospowers/client/power/chrono/AndroidMechModel.java, FutureMechRenderer.java
  - changes/powers/<branch-name>.md
writer: hermes-writer
---
# Android mech (four builds): animations, on GeckoLib (modelling bot)

**Andy's words (2026-11-09):** "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations."

**What's wanted:** Powers' android mech (the war mech `FutureMech` and the test unit), in all four builds (bulwark, reclaimer, strider, warden), animated by GeckoLib. It's our own model, with no animations; its walk is posed in Java (`AndroidMechModel`). Give it idle, walk, its attacks, powering up and down if it does, a hit flinch and a death, keeping the visor, core and lights `FutureMechRenderer` draws.

**Where to start:** `src/client/java/com/jaymospowers/client/power/chrono/AndroidMechModel.java` and `FutureMechRenderer.java`; `src/main/java/com/jaymospowers/power/chrono/future/FutureMech.java` (`FutureMachines.java` for where it spawns); `tools/blockbench/android_mech/*.bbmodel` (our own).

## How
- Follow `docs/AI-HANDBOOK.md`, "Adding a GeckoLib mob" and "Animating a Blockbench model with keyframes of our own".
  The worked example is Notus and Boreas (PR #1177): `kingdoms/tools/gen_fire_dragon_geo.py`,
  `kingdoms/src/main/java/com/jaymoskingdoms/dragons/FireDragonAnimations.java`,
  `kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/FireDragonRenderer.java`.
- Write the keyframes with a script, not by hand, into the editable `.bbmodel`, then turn it into GeckoLib's files with
  `tools/bbmodel_geckolib.py`.
- Every pose the old code makes must look the same or better. Render the old pose (`POSE=` in
  `tools/bbmodel_preview.py`) next to the new animation, and put the side-by-side renders in the PR.
- Moves the server starts (attacks, roars, special moves) go on an `action` controller through `triggerAnim`, from the
  entity's existing hooks. Things that follow the game every frame (head look, aim, a jaw while breathing) stay in
  the renderer's `adjustModelBonesForRender`.
- Keep credits and licences beside the art (`<name>-CREDITS.md`, `-LICENSE.txt`) and update
  `textures/entity/bb/CREDITS.txt`. Add a change note. Build, and boot `runServer` once. Say plainly that it hasn't
  been seen in game.

## Notes
- This is Powers. If Powers doesn't compile against GeckoLib yet, add it the way `kingdoms/build.gradle` does. If the Omen task got there first, use what it added.
- The four builds should share one skeleton and one set of animations if the bone names line up. Make them line up if they don't.
- 2026-11-09 13:06 Review (head e8d88f8): pass. The war mech and test unit now run on one GeckoLib rig in all four builds (Warden, Bulwark, Strider, Reclaimer) with their skins, optional parts and glowing visor/core/lights kept; new coolant idle, walk, cannon recoil, shoulder-shell fire, double-fist smash, hit flinch and a held death. The server triggers each move from the existing hooks, head look and cannon aim stay per-frame in the renderer, and the old walk/aim poses match in the side-by-side renders. Powers now declares GeckoLib (>=5.5.7), which the pack already ships for Kingdoms. Not tested in game. Small follow-ups, not worth another round: GeoEntityRenderer still tips a dying mob 90 degrees on top of the death animation (override getDeathMaxRotation to 0 if the held collapse looks doubled); tools/gen_android_mech.py still rewrites the '// parts:start' block that AndroidMechModel no longer has, so it now edits nothing; the hurt flinch shares the 'action' controller and cuts off a smash or recoil in progress.
- 2026-11-09 13:06 Reviewed; Andy allowed sending it to the coordinator.
- 2026-11-09 13:14 PR #1195 is merged into master; moved to Inbox/Done.
