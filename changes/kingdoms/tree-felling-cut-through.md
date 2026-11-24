# tree felling - cut through the trunk, carved stumps, blasts bring buildings down
Andy's Reference Mods picks, patch 1 (rows T1-T8; ideas from Timber Tales and TNT Logic Enhanced, all our own code).
- **Cut through the trunk (T1)**: an axe stroke bites into the trunk at the height it lands and works round it. Each
  log of the trunk at that height takes its share of the tree's chops to be cut clean through (a 2x2 trunk is cut all
  the way round). The tree comes down only once nothing joins its top to the ground; that's looked at only on the
  stroke that cuts a log through. A tree caught on a neighbour's wood tears free on the next stroke. Then it falls as
  before (Felled for a player's tree when it's in the pack, else ours). `Felling`, `Trees.chop`.
- **Carved stump (T2)**: the cut logs are left as a Tree Stump: the lower part of the log with its flat cut face and the
  bottom of the notch, drawn in its own bark and grain. Sit on it (use it), dig it out with a shovel for 2-4 sticks
  (fewer as it rots), or leave it: it darkens over three seasons and rots into Kitchen's rich soil (rooted dirt
  without Kitchen). Rot goes by the world clock, so it keeps rotting while nobody's near. `TreeStump`.
- **Cracks and creaks, no bar (T3)**: the gold chop meter under the crosshair is gone. Cracks spread up the trunk as it's
  cut, and past 40% through it creaks (past 70% groans), louder the nearer it is to breaking.
- **Choose which way it falls (T4)**: the side your first strokes come from is the notch. Finish it from the opposite
  side (the back-cut) and it falls toward the notch, unless its weight leans badly the other way (1.2 blocks or more).
  Cut it all from one side and it falls as it always has (its heavier side, else away from you). Felled's trees are
  pushed the same way.
- **The ground answers (T5)**: where a felled tree lands (ours and Felled's), dust and leaves burst up along it, grass
  and flowers under it are crushed and deep snow pressed flat, players within 14 blocks feel a thump (a short camera
  shake, stronger close by and for a bigger tree), and animals within 16 blocks bolt. As it goes over, the birds in
  its crown burst out with a flutter of leaves; with Naturalist, a few of its songbirds fly up out of a big crown by day
  (not where six or more are about already).
- **Woodcutters fell the same way (T6)**: a village woodcutter picks the side with the most open ground (clear of
  roofs), cuts the notch there, walks round for the back-cut, and shouts a warning ("Timber!") before the stroke that
  brings it down. He grubs out the stump before planting the sapling. A half-cut tree (half through or more, nobody
  at it for 10 s) is a danger: anyone standing where it would fall makes it creak and may bring it down on them, and a
  thunderstorm can too. Half-cut trees are found again after a restart.
- **Blasts bring buildings down (T7)**: TNT, creepers, Balkon's dynamite and cannon shells and balls (field guns and a
  siege camp's) undercut what they hit: whatever a building has above the hole that nothing holds any more comes
  down as falling blocks onto whoever's under it. A block stands on the one under it and reaches out sideways 4 blocks
  from a held one for stone and brick, 3 for timber and the rest; Powers' support beams reach 8, so posts and cross
  beams hold what they prop. The land itself (stone, earth, sand, ore, ice, living trees) never falls here, and only
  what the blast took the hold from comes down, so sky bridges and cloud towns that floated before still do. Up to 384
  blocks a blast, 32 blocks up. Falling blocks in a besieged seat count as breaches for its masons. Doors and beds
  break. `Collapse`, `CollapseBlastMixin`.
- **Chests fall with what's in them (T8)**: chests, barrels and anything else that keeps things ride down whole and
  land in the rubble with their contents; one that can't land spills its things where it came down. A double chest's
  halves come down as two single chests.
- Built; not played.
