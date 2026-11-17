# Plan: hands-on cooking picks (2026-11-17)

Source: the vault spec `Agents/hands-on-cooking-realtime-SPEC.md` (hermes-b's research on Hands-On Delight; ideas only).
Its header says MC 1.21.4 / Java 21: wrong, the Kitchen is 26.3 Fabric on JDK 25. Pick page:
https://claude.ai/artifact/GCdmEEZx1zdiumU4T69KLz (db doc `picks/andy`). Andy ticked 18 of 19 rows.

Not picked: **Each player cooks their own food** (`hc-q3-shared`). Andy's note on it: "players should be able to help
each other finish stirring or flipping or overall cook together." So a station holds **one shared dish**: anyone with
the right tool can step in and stir, flip, turn or fan it, and the dish is judged once, for the pot, with what everyone
did. The spec's per-player sessions (R11) become one session per station that several players can join.

## Patches (Kitchen only, a commit per patch)
1. **At the block**: `hc-base` (no screen, one shared session per station that others join), `hc-walkaway` (judged as
   left once the last cook is 8 blocks off or logged out), `hc-same` (same judging), `hc-classic` (server config falls back
   to the screens), `hc-q2-tools` (reuse Kitchen's knife, spoon, tongs; add fan, spit and peel only if missing).
2. **Board and pot**: `hc-chop` with `hc-q6-chop` (left click chops), `hc-stir` with `hc-q7-stir` (circle the pointer round
   the pot's centre in the world), `hc-prep` (pour, knead, grind, sprinkle and roll by hand).
3. **Fire**: `hc-roast` (turn the spit), `hc-grill` (tongs on the campfire, peel in the oven), `hc-sear` (marker over the
   furnace), `hc-smoke` (fan the smoker).
4. **No screens left**: `hc-sequence` (step recipes done at the block, each step with its tool), `hc-recipepick` (the
   station reads the ingredients put on it; sneak and scroll picks between dishes that match).
5. **New work at the board and new stations**: `hc-sandwich` (stack bread and fillings on the board), `hc-newstations`
   (keg brewing, kettle tea, sushi mat, all by hand).

## As built

### Patch 1: at the block
- `cooking/hands/` (server): `HandsOn` (the game rule `jaymoskitchen:hands_on_cooking`, default on; the one registry of
  `Table`s keyed by dimension and block; the use-block and attack-block callbacks; the tick that drops cooks who logged
  out or went past `LEAVE_RANGE_SQ` = 8 blocks and judges the dish as left; what lies on an idle station for `IDLE_TICKS`
  (90 s) with nobody near goes back on the floor; on server stop dishes are judged as left and items dropped).
  `Table` is the worktop: what was put on it (sneak + right-click with the item; sneak + empty hand takes the last back),
  the dishes that makes (`Recipes`, fullest recipe first, sneak + scroll chooses: `HandsPick` packet and a client mixin on
  `MouseHandler.onScroll`), and the one shared `Run`. `Run` is the shared dish: any cook with the right tool in hand
  joins (`cooks` map), it is judged once. `Pick` = a dish (by its `Dish.Method`) or dough/joint/... with the `Tool`
  that starts it. A finished dish goes to the last cook still online (as `Kitchen.award` always did), else it is left on
  the station.
- Judging is the screens' own static functions (`Minigames.judge*`, `Quality.judgeGrill/judgeOven`, `Sear.judge`,
  `Smoke.judge`, `Prep.score`, `Rolling.score`) with the same constants; each run only moves the simulation to the server.
- Interim: the screen still opens to pick a dish; picking one whose method is done by hand starts it at the block and
  closes the screen (`CookUpdate.STATUS_HANDS`, `PrepUpdate.STATUS_HANDS`). The Cooking for Blockheads table likewise.
- Classic mode: the game rule off, and everything is as before (`HandsMode` tells clients).
- Tools: the Kitchen had a knife (`#jaymoskitchen:knives`) but NO spoon or tongs (the plan assumed them); added
  `Wooden Spoon`, `Kitchen Tongs`, `Smoker's Fan`, `Baker's Peel` (`CookTools`, art by `kitchen/tools/gen_cooking_tools.py`,
  shaped recipes). The spit already exists as a block (`SpitBlock`).
- Client (`client/hands/`): `HandsClient` (what stations round here hold, from `HandsSync`), `HandsHud` (a plate under the
  crosshair: placed items, the dish and its tool; the running readout; the verdict), `Readouts` (one drawer per kind),
  `HandsInputs` (per-tick inputs a click can't carry).

### Patch 2: board and pot
- `ChopRun` (`hc-chop`, `hc-q6-chop`): the knife's left-click swing is the chop; the screen's slide, speed step and marks run on the
  server (ints: cuts so far and each result; floats: slide, speed), judged by `Minigames.judgeChop`. Walking away: BURNT.
- `StirRun` (`hc-stir`, `hc-q7-stir`): with the spoon in hand the client sends the angle where the crosshair meets the pot's
  plane round `Geometry.potCenter` (`IN_ANGLE`); the server sums every cook's turn per tick (each capped at 1.2 rad), smooths it
  like the screen and keeps stirred/stuck/spilled and the boil-ups; judged by `Minigames.judgeStir`. Walking away: CHARRED.
- `DoughRun` (`hc-prep`, dough): hold use to pour water then oil (the client sends `IN_HOLD` with a keepalive every 5 ticks),
  let go inside the band, then knead with right-click and left-click in turn; `Prep.score`. The mixing bowl is borrowed.
- `JointRun` (`hc-prep`, joint): hold use and circle the grinder, hold use and point along the paper to sprinkle (`IN_ACROSS`),
  hold use and move the view up and down at a steady pace to roll (`IN_ROLL`, 6 px of the screen game per degree); `Rolling.score`.
- `Geometry`, the readouts for the four kinds, `HandsInputs`, and `Prep.start` goes through `HandsOn.fromPrep`.

### Patch 3: fire
- `RoastRun` (`hc-roast`): needs `SpitBlock.SPIT` on the fire (`Runs.ready` says so otherwise). Empty-hand right-click on the spit
  turns it a quarter (the screen's four sides, heat gaps, side spill, `TURN_TICKS`); sneak + right-click takes it off; judged by
  `Minigames.judgeRoast`. Walking away: CHARRED.
- `GrillRun` (`hc-grill`): one run for the grill and the oven. Grill (campfire, hearth or hob pan): tongs flip (`FLIP_TICKS` gap, new
  heat each flip, soul fire hotter), sneak + tongs serves; oven (`Kind.OVEN`): the peel pulls it out, heat surges by itself;
  judged by `Quality.judgeGrill/judgeOven`; burnt to `Grill.ASH` ends it. Walking away serves as it is, as closing the screen did.
- `SearRun` (`hc-sear`): five rounds of the sweeping marker; a right-click with the tongs presses; `Sear.judge`. Walking away: CHARRED.
- `SmokeRun` (`hc-smoke`): hold use with the fan (client `IN_HOLD`, any cook with a fan keeps the one fire going; two fans blow no harder than one, so the judging stays the screen's); heat, gusts, cure, scorch
  as the screen; `Smoke.judge`. Walking away: BURNT.
- Readouts for all four (side meters with the golden and burnt marks, the sear bar, the smoker's heat band).

### Patch 4: no screens left
- `SequenceRun` (`hc-sequence`): step recipes at the pot, pan or oven. Ingredient steps are a right-click with an empty hand (the
  ingredients went in when it started, as before); actions with their tool: spoon stirs, sneak + spoon simmers (my reading, the
  plan names no tool for simmering), tongs flip, peel bakes. Anything else is a mistake; three burn it; the dish's par and limit
  are the screen's, stretched the same way; `Quality.judge`. Clicks within 3 ticks count once. Walking away: burnt.
- `hc-recipepick`: with the game rule on, sneak + empty hand at a station or worktop no longer opens a screen (`JaymosKitchen`'s
  use callback): it says what lies on it and what it would make (`HandsOn.hintAt`); a worktop's picks are made from what was put on
  it (sneak + scroll chooses). The screens open only with the rule off, or for the Cooking for Blockheads table's own buttons, which
  start the dish at the block without a screen.

### Patch 5: new work at the board and new stations
- `hc-sandwich` (`SandwichRun`): on a cutting board, counter or slab, sneak + right-click puts layers on in order (any bread, any
  food); bread first, at least one filling, bread last is a sandwich pick, started with the knife. It is done in the moment of the
  cut: nothing to time (the old screens had no sandwich game to match). The new `Sandwich` item is as filling as the layers
  (their food values summed, capped), lists them in its lore; three different fillings or more is "made well".
- `hc-newstations` (`HandsBlocks`, `PourRuns`, `Recipes`): three new blocks, no block entity (the station's `Table` keeps the
  state like any worktop), art by `kitchen/tools/gen_cooking_stations.py`, shaped recipes: **Brewing Keg** (three of a brew's
  base and three water bottles; pour the water, pour the mash; perfect gives the cask's three drinks, good two), **Tea Kettle**
  (needs a lit fire under it; two herbs of the new `tea_herb` tag and three water bottles; pour, steep; three Cups of Tea or two),
  **Sushi Mat** (the rolls the board's chopping makes, `SALMON_ROLL`, `COD_ROLL`, `KELP_ROLL`, `RICE_ROLL_MEDLEY`; press, season, roll).
  They share dough's pouring (`DoughRun`, same bands and `Prep.score`), so they feel the same; sushi comes out at GOOD or PERFECT.
- New step `TEA_HERB` (before the actions in `Step`).

### After the row check (independent audit of every row against the code)
Fixed: left clicks at a running station never reached the server (the client answered FAIL, and Fabric only sends the
click on for SUCCESS), so every chop was a miss and dough and sushi rolls could not be kneaded; one press is now one
chop or knead (`HandsOn.clientPress`). The keg and the kettle went on to a knead they don't have and always came out
ruined; they now finish on the second pour. Kneading needs an empty hand. Pouring needs an empty hand and the mix in
the crosshair (a bystander eating nearby no longer pours). The kettle needs a real fire under it (lit campfire or stove,
or a burning furnace, smoker or blast furnace). The keg's and kettle's water bottles now hold the drinks instead of
coming back as extra glass bottles; the ones left empty are handed back.

Not as the rows say, for Andy to rule on:
- **`hc-sequence`**: every ingredient step is the same empty-hand click, so a wrong ingredient can't be picked (on the
  screen it was a mistake). Easier than the screen.
- **`hc-sear`**: the sweeping marker is on the HUD plate under the crosshair, not drawn in the world over the furnace.
- **`hc-sandwich`**: no timing game; stacked in order and cut once.
