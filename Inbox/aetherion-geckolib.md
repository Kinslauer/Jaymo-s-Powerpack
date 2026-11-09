---
status: claimed
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations.")
claimed_by: hermes-modeller
pr: 
suggested_owns:
  - kingdoms/art/aetherion.bbmodel (+ the script that writes it)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/**/aetherion.*
  - kingdoms/src/main/java/com/jaymoskingdoms/dragons/AetherionDragon.java (+ its animations class)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AetherionDragonModel.java (removed) and its new renderer
  - DragonsClient.java (its register line only)
  - changes/kingdoms/<branch-name>.md
---
# Aetherion: a Blockbench model and animations, on GeckoLib (modelling bot)

**Andy's words (2026-11-09):** "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations."

**What's wanted:** Aetherion, the star dragon on a CC0 GeckoLib dragon's body plan (rebuilt as our own model), drawn and animated by GeckoLib. It has no Blockbench model: `AetherionDragonModel` is built in Java from `kingdoms/tools/gen_aetherion.py`, and every pose is worked out in its `setupAnim`. Make it a real `.bbmodel` with the same body (the script's geometry and its hides' UV layout, so the existing hide and marking textures still fit). Then key every state the old code poses (idle, walk, flying, gliding, landing, sitting, crashed, a death if it has one) and every move it plays from `action()` / `bitten` / `swept`.

**Where to start:** `kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AetherionDragonModel.java` (its poses: they are the spec); `kingdoms/src/main/java/com/jaymoskingdoms/dragons/AetherionDragon.java` (its moves); `kingdoms/tools/gen_aetherion.py` (its geometry and hides); `DragonsClient.java` (its renderer line); `docs/SaintsDragons-Plan.md` and `docs/SaintsDragons-Animation-Spec.md` for the lesser dragons.

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
- Saint's Dragons' art is All Rights Reserved: never copy it. Only our own models.
- Hides, glowing markings, looks, sizes, seats and the hatchling's size must not change. Riders must still sit where they sit now.
- The five dragon tasks run as separate tracks. Each touches only its own dragon's files and its own line in `DragonsClient.java`. Don't edit `WindDragon.java` or `WindDragonRenderer.java`; if you need a shared change there, ask under this task's Notes.
- The Kintsugi wyverns (Eurus, Aeolus, Ignivorus) already have a full Blockbench model with 28 animations, so they aren't part of this.
- 2026-11-09 11:57 2026-11-09 11:58 The modeller's run on this was cut off when the gateway disconnected (job paused with the turn outcome unknown, nothing pushed); handed back as open so it can be taken again.
