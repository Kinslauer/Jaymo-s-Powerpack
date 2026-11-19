# Rig: the back rig (wings, wing slash, spear legs, pincers, grapple, horn lightning)
- **Wings**: two vanes fold flat down the Core's back and spread wide in full flight, beating a little as you fly with a quiet buzz, and fold away again in hover or on the ground. (`RigBackClient`)
- New Rig menu entries, each needing the whole set and costing charge:
  - **Wing Slash** (Mind level 3, 60 charge): the dash with the vanes out as blades. It cuts everything you pass, each once, instead of ramming the first thing.
  - **Spear Legs** (level 2, 50): four jointed legs stab out of the back in a ring and back in. Everything within 3.5 blocks is run through and lifted.
  - **Pincers** (level 3, 40): two arms over the shoulders seize the creature in front of you (up to 60 health) and hold it before you for up to 5 seconds. Pick it again to throw it along your look.
  - **Grapple Claw** (level 2, 25): a claw on a line, up to 24 blocks. At a wall it pulls you there; at a creature it pulls it to you.
  - **Horn Lightning** (level 4, 90): lightning leaps from the helm to the nearest foe and on to up to four, half again as strong in the rain, and overcharges the Core.
- Metal art: `tools/gen_rig.py` `back()` writes `textures/entity/rig/back.png` and its glow. (`RigBack`)
- Built; not played.
