# Notus and Boreas animated with GeckoLib
- **Notus and Boreas** (nyxaris' Fire Dragon, CC BY 4.0) are now drawn and animated by GeckoLib, not posed in our
  own code. Andy asked for it as a test beside the minotaur's move to GeckoLib, to compare the two.
- The model came with only its wingbeat. These keyframed animations of ours are added to it in
  `kingdoms/art/fire_dragon.bbmodel` (made by `kingdoms/tools/gen_fire_dragon_geo.py`, and you can open it in
  Blockbench): standing (its folded wings breathing, its tail swaying), walking, sitting, crashed, landing (wings
  flared, then folding), gliding, a **bite** (the neck lunges, the jaw snaps) and a **tail sweep** (it winds up, then
  swings its body and tail round). The bite and the sweep are new: before, its model didn't move for them. The
  standing, sitting and crashed poses are the ones the old code worked out.
- The wingbeat still keeps time with its stroke (faster climbing, slower breathing). In code on top of the
  animations, as before: its neck and head turn where it looks, its body tips with a climb or a dive, and its jaw
  opens to breathe.
- Hides, glowing markings, sizes, seats and the hatchling's size are unchanged (`FireDragonRenderer`,
  `dragons/FireDragonAnimations`). `FireDragonModel` and the old baked model are removed.
- Built only. Not played, and no server run. Check in game that the animations face the right way and match the old
  ones, and that the bite, sweep and landing play.
