---
status: writing
for: prometheus
from: coordinator
asked: 2026-11-09
thread: Claude Code cloud session (Andy: "lets do the minotaur because its an actual blockbench model")
claimed_by: hermes-modeller
pr: 1188
suggested_owns:
  - kingdoms/art/minotaur.bbmodel (+ minotaur-CREDITS.md, minotaur-LICENSE.txt beside it)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/minotaur.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/minotaur.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/minotaur.png
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/Minotaur.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/BeastsClient.java (the minotaur's line only)
  - changes/kingdoms/<branch-name>.md
writer: hermes-writer
---
# Minotaur: move it to GeckoLib (modelling bot)

**Andy's words (2026-11-09):** asked "Can you use geckolib to animate the stegonaut and other bb models?", then
"Actually lets do the minotaur because its an actual blockbench model, and send it as a task for prometheus".

**What's wanted:** the minotaur already in Kingdoms (`jaymoskingdoms:minotaur`, `beasts/Minotaur.java`) drawn and
animated by GeckoLib 5.5.7 instead of our own Blockbench loader (`BbMobRenderer`), looking the same or better in game,
and using more of the animations its Blockbench model has. Its stats, AI, moves, spawning, egg and loot don't change.
This one includes the small Java switch (it's an existing mob, so there's no separate mob task).

**Where to start:**
- `docs/AI-HANDBOOK.md`, "Adding a GeckoLib mob to Kingdoms": the pattern; the barrow beetle
  (`inhabitants/BarrowBeetle.java`) and draugr are the examples to copy. Package `com.geckolib`, not `software.bernie`.
- The source: `tools/bbmodels/minotaur.bbmodel` ("Minotaur" by eanncrod, Blockbench Workshop, CC BY 4.0; credit in
  `kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bb/CREDITS.txt`). Bedrock format, box UV, one 256x256
  texture, 29 cubes, and 8 animations: `minotaur.idle`, `minotaur.idle_calm`, `minotaur.walking.hostile`,
  `minotaur.running`, `minotaur.slam`, `minotaur.mino_slam`, `minotaur.swing_1`, `minotaur.swing_2`.
- How it's drawn now: `BeastsClient.java` line 28 (`BbMobRenderer.Look("minotaur", 0.85F, 1.2F, "idle", "running", ...,
  "head")`: scale 0.85, shadow 1.2, walks with its run, head turned to look). Its moves are synced action names from
  `ActingMonster`: `swing_1`, `slam`, `running` (the charge), in `Minotaur.ACTIONS`.

## Steps
1. Copy the source to `kingdoms/art/minotaur.bbmodel` with its licence and credit beside it (`minotaur-LICENSE.txt`,
   `minotaur-CREDITS.md`). Facing north (-z), feet at y 0, unique bone names; rename the animations to short keys
   (`idle`, `idle_calm`, `walk`, `run`, `slam`, `mino_slam`, `swing_1`, `swing_2`).
2. `py -3 -I tools/bbmodel_geckolib.py kingdoms/art/minotaur.bbmodel kingdoms/src/main/resources/assets/jaymoskingdoms minotaur`.
3. `Minotaur implements GeoEntity` (keep it extending `ActingMonster`). A `move` controller: `run` while charging,
   `walk` (the model's `walking.hostile`, which the old loader never used: the credits say "it came without a walk")
   when moving, `idle` with a target, `idle_calm` without. An `action` controller playing `swing_1` / `swing_2`
   (alternate them) on a hammer blow and `slam` (or `mino_slam`, whichever reads better with the 4.5-block throw) on the
   slam, triggered from the server where the action starts, or read from the synced action, whichever the code makes
   simpler. The head still turns toward what it's looking at.
4. In `BeastsClient`, replace the minotaur's `look(...)` with an `InhabitantRenderer` (scale 0.85, shadow 1.2, head
   bone `head`), and delete `assets/jaymoskingdoms/bbmodels/minotaur.json` and `textures/entity/bb/minotaur.png` once
   nothing reads them (move the minotaur's lines from `bb/CREDITS.txt` next to the new files).
5. A change note in `changes/kingdoms/`, credit to eanncrod (CC BY 4.0). Build `kingdoms/` and boot `runServer` once.

**Check your work visually before the PR:** render it with
`python tools/mesh_to_cubes/bbrender.py kingdoms/art/minotaur.bbmodel <out.png>` and pose the animations with
`tools/bbmodel_preview.py` (handbook step 1); look at them with `vision_analyze`: front, side and back must read as the
minotaur with its hammer, no missing faces or stray cubes, and each animation should move the bones it should. Put the
renders in the PR body.

**PR:** say which animation plays for what, what changed from eanncrod's original, and that it hasn't been seen in
game yet (facing, size and the hammer's place need a look in game).

## Notes
- Only the minotaur. If it goes well Andy may move the other `BbMobRenderer` beasts (Piglin King, redstone golem,
  skulk golem, ...) and later the hand-made dragon models (Stegonaut first) the same way: those are separate tasks.
- 2026-11-09 09:52 Text pass: reviewed change note (changes/kingdoms/claude-minotaur-geckolib.md) — already clear, player-focused, has 'Not tested in game' line. No lang parts, dialogue, hints, or other player text added/changed for minotaur in this PR. Build and PR checks pass.
