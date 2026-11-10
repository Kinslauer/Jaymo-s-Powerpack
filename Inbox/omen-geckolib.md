---
status: in-review
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations.")
claimed_by: claude-cloud
pr: 1219
suggested_owns:
  - art/the_omen_red.bbmodel, art/the_omen_blue.bbmodel (+ credits, licence)
  - src/main/resources/assets/jaymospowers/geckolib/** (the Omen's files)
  - src/main/java/com/jaymospowers/power/chrono/future/FutureOmen.java
  - src/client/java/com/jaymospowers/client/power/chrono/OmenRenderer.java
  - changes/powers/<branch-name>.md
---
# The Omen: animations, on GeckoLib (modelling bot)

**Andy's words (2026-11-09):** "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations."

**What's wanted:** The Omen, the manned mech of Powers' Chrono future (red and blue), animated by GeckoLib. Its Blockbench models have only one pose each (`pose_1`). Add idle, walk, run, turning, its attacks and whatever else it does, a hit flinch and a death, all keyed to what `FutureOmen` does.

**Where to start:** `src/main/java/com/jaymospowers/power/chrono/future/FutureOmen.java` and `FutureOmens.java` (its moves); `src/client/java/com/jaymospowers/client/power/chrono/OmenRenderer.java` (how it's drawn now); `tools/bbmodels/the-omen-red.bbmodel` and `the-omen-blue.bbmodel` (Blockbench Workshop, CC BY 4.0, credited in Powers' `textures/entity/bb/CREDITS.txt`).

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
- This is Powers, not Kingdoms. The pack already ships GeckoLib 5.5.7. If Powers doesn't compile against it yet, add it the way `kingdoms/build.gradle` does, and as a `depends` in `src/main/resources/fabric.mod.json`.
- Red and blue share one class (two entity types). Keep one set of animations for both if the rigs match.
- 2026-11-09 16:08 hermes-modeller's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-09 23:50 Cloud agent session sesn_011PDa8uSjG37eNbrMwRSmdP opened PR #1219.
