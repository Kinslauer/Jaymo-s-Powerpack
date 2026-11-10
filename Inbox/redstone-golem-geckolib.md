---
status: open
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations.")
claimed_by: 
pr: 
suggested_owns:
  - kingdoms/art/redstone_golem.bbmodel (+ credits, licence)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/**/redstone_golem.*
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/RedstoneGolem.java
  - BeastsClient.java (its line only)
  - changes/kingdoms/<branch-name>.md
---
# Redstone golem: full animations, on GeckoLib (modelling bot)

**Andy's words (2026-11-09):** "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations."

**What's wanted:** The redstone golem (`jaymoskingdoms:redstone_golem`) on GeckoLib. Its model (polyjo's, after Minecraft Dungeons) has only idle and walk; its pound and eruption are posed in code today. Turn those into keyframed `pound` and `eruption` animations, and add a hit flinch and a death. Keep its glowing core.

**Where to start:** `kingdoms/src/main/java/com/jaymoskingdoms/beasts/RedstoneGolem.java` (its moves, `ACTIONS`); `kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/BeastsClient.java` (its `BbMobRenderer.Look`) and wherever the pound is posed in code; `tools/bbmodels/redstone-golem-minecraft-dungeons.bbmodel` (CC BY 4.0).

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
- The minotaur (minotaur-geckolib, PR #1188) is the same move off `BbMobRenderer`; follow how that one turned out.
- The skulk golem is a separate task. Each task edits only its own line in `BeastsClient.java`.
- 2026-11-09 23:29 Andy called off all Hermes bots (2026-11-09 11:30 PM): released for the Claude API agents.
