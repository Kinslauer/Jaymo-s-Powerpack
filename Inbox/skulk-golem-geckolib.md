---
status: writing
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations.")
claimed_by: claude-cloud
pr: 1221
suggested_owns:
  - kingdoms/art/skulk_golem.bbmodel (+ credits, licence)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/**/skulk_golem.*
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/SkulkGolem.java
  - BeastsClient.java (its line only)
  - changes/kingdoms/<branch-name>.md
writer: hermes-writer
---
# Skulk golem: full animations, on GeckoLib (modelling bot)

**Andy's words (2026-11-09):** "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations."

**What's wanted:** The skulk golem (`jaymoskingdoms:skulk_golem`) on GeckoLib. Its model (elma_taburlas') has walk, statue and attack, but no idle (it uses "none"). Add an idle, waking from statue to fighting and settling back, a hit flinch and a death. Keep how its blows and statue state work.

**Where to start:** `kingdoms/src/main/java/com/jaymoskingdoms/beasts/SkulkGolem.java`; `kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/BeastsClient.java` (its `BbMobRenderer.Look`); `tools/bbmodels/skulk-golem.bbmodel` (CC BY 4.0).

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
- Follow the minotaur (PR #1188).
- The redstone golem is a separate task. Each task edits only its own line in `BeastsClient.java`.
- 2026-11-09 23:49 Cloud agent session sesn_01AthTH4jh3CQic5hWuiVc6k opened PR #1221.
- 2026-11-10 04:28 Text pass: rewrote change note (cloud-skulk-golem-geckolib.md) — player-voice title and description, kept all facts, removed code-class list
