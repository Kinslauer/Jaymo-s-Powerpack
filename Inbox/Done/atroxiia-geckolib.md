---
status: done
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations.")
claimed_by: hermes-modeller
pr: 1198
suggested_owns:
  - kingdoms/art/atroxiia.bbmodel (+ the script that writes it)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/**/atroxiia.*
  - kingdoms/src/main/java/com/jaymoskingdoms/dragons/AtroxiiaDragon.java (+ its animations class)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AtroxiiaDragonModel.java (removed) and its new renderer
  - DragonsClient.java (its register line only)
  - changes/kingdoms/<branch-name>.md
writer: hermes-writer
text_pass: done
---
# Atroxiia: a Blockbench model and animations, on GeckoLib (modelling bot)

**Andy's words (2026-11-09):** "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations."

**What's wanted:** Atroxiia, a lesser dragon after Saint's Dragons' moves (our own model), drawn and animated by GeckoLib. It has no Blockbench model: `AtroxiiaDragonModel` is built in Java from `kingdoms/tools/gen_atroxiia.py`, and every pose is worked out in its `setupAnim`. Make it a real `.bbmodel` with the same body (the script's geometry and its hides' UV layout, so the existing hide and marking textures still fit). Then key every state the old code poses (idle, walk, flying, gliding, landing, sitting, crashed, swimming, a death if it has one) and every move it plays from `action()` / `bitten` / `swept`.

**Where to start:** `kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AtroxiiaDragonModel.java` (its poses: they are the spec); `kingdoms/src/main/java/com/jaymoskingdoms/dragons/AtroxiiaDragon.java` (its moves); `kingdoms/tools/gen_atroxiia.py` (its geometry and hides); `DragonsClient.java` (its renderer line); `docs/SaintsDragons-Plan.md` and `docs/SaintsDragons-Animation-Spec.md` for the lesser dragons.

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
- 2026-11-09 16:08 hermes-modeller's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-09 16:35 Text pass: rewrote changes/kingdoms/atroxiia-geckolib.md (change note) to be player-focused
- 2026-11-09 16:35 Text pass: rewrote changes/kingdoms/atroxiia-geckolib.md (change note) to be player-focused
- 2026-11-09 16:57 Review (head 829b970): pass. Atroxiia now draws from an editable atroxiia.bbmodel through GeckoLib with the same body, hides, markings, headdress, spines, tack, sizes and reach; 19 animations keyed from the old pose equations (breathing with a taunt/sniff/grumble every 16 s like the old 320-tick fidget, walk, run, sit, crash, swim, the eight moves from begin(), flinch, held death), shown side by side with the old poses. Not tested in game. Small follow-ups, not worth another round: kingdoms/tools/gen_atroxiia.py and the AtroxiiaDragon.java:35 javadoc still point at the deleted AtroxiiaDragonModel; the move-name switch is written twice (AtroxiiaAnimations.name and AtroxiiaDragon.begin), so keep one; GeoEntityRenderer may tip the corpse 90 degrees on top of the held death (getDeathMaxRotation); the hurt flinch shares the action controller and can cut off a move.
- 2026-11-09 16:57 Reviewed; Andy allowed sending it to the coordinator.
- 2026-11-09 17:50 PR #1198 is merged into master; moved to Inbox/Done.
