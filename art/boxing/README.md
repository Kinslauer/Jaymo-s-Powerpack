# Boxing in Blockbench (Jung)

One Blockbench project per boxing style, each with every stance, block, punch and evade that style has:

| Project | Moves |
|---|---|
| `arrow_slit.bbmodel` | idle, block, gassed, jab, lead_hook, cross, lead_hook_full (after a slip), uppercut (after a bob), slip_left, slip_right, bob |
| `long_guard.bbmodel` | idle, block, gassed, jab, cross, pull, pull_counter (after a pull) |
| `bastion.bbmodel` | idle, block, gassed, jab, roll, roll_straight (after a roll), slip_left, slip_right |

**Opening one:** Blockbench with the GeckoLib Animation Utils plugin (File > Plugins). Open the `.bbmodel`, go to the
Animate tab, pick a move on the left and press play. Punches start with their full windup (the level 1 one, 0.75 s);
higher levels throw from partway into it.

**The rig** is the player model, cut where the game bends it: `right_arm` / `left_arm` with a `right_forearm` /
`left_forearm` at the elbow, `right_leg` / `left_leg` with a `right_shin` / `left_shin` at the knee, and `body` with
a `waist` below it. Turn a forearm, shin or waist about X to fold it; a forearm turned about Z folds across the body
(the Bastion's lead arm, a hook). The hand wrap rides the forearm, so you can see the gear clear the face.

**Where they come from:** `tools/blockbench/boxing_rig.py` writes them from the numbers the game draws boxing with
today (`client/boxing/BoxingPoses.java`), and `--check` checks the rig against those numbers.
`tools/blockbench/bbmodel_sheet.py <project> <png>` draws a contact sheet of a project's moves without Blockbench.

**Not in the game yet.** The game still poses boxers from `BoxingPoses`; these projects show exactly what it does.
The next step plays them in game through Player Animation Library, so an edit saved here is what players see. Until
then, send edited projects in and the angles get copied into the code.
