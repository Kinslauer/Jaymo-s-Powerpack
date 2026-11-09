---
status: open
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations.")
claimed_by: 
pr: 
suggested_owns:
  - tools/blockbench/android_mech/*.bbmodel or art/android_mech_*.bbmodel
  - src/main/resources/assets/jaymospowers/geckolib/** (the mech's files)
  - src/main/java/com/jaymospowers/power/chrono/future/FutureMech.java
  - src/client/java/com/jaymospowers/client/power/chrono/AndroidMechModel.java, FutureMechRenderer.java
  - changes/powers/<branch-name>.md
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
