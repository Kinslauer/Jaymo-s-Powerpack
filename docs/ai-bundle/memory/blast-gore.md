---
name: blast-gore
description: Explosions gib the killed and dismember survivors by proximity (core/BlastGore); PR #978 MERGED 2026-11-05 02:1xZ, Powers 0.20.67 / Kitchen 0.5.67 / Kingdoms 0.7.556 (hint 100)
metadata:
  type: project
  modified: 2026-11-05T02:08:29.903Z
---
2026-11-05 Andy: "explosions (dependent on proximity) have a higher chance to gib mobs and players on death and dismember survivors."
PR #978 (branch claude/project-thread-q5x3jh). Built, not played. Jars /mnt/project-files/blast-gore/.
- Closeness = 1 - dist/(2*radius) * sqrt(radius/4). Gib 0 below 0.25 up to 95%; survivor limb 0 below 0.3 up to 50%, second at 40% of that.
- Powers tags dying body jaymoskitchen.gib / no_gib; Kitchen Gibs.blownApart obeys (old 12-damage rule only without tags).
- Downed checks the gib before halving; Rubber-folk gibbed = all limbs off, head kept, split (swallow mend). Gibbed player respawns with only pre-blast missing parts (Dismember.restore), my call, not Andy's.
- Andy: "noone but stoneborn are exempt": only Stoneborn (tag jaymoskingdoms_heritage_stoneborn) never gib; royals can.
Related: [[leg-cleave-halve]].
