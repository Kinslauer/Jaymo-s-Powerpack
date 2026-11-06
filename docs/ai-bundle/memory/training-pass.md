---
name: training-pass
description: 2026-11-01 pack-wide training pass (PR #829): longer grind, shared milestone fanfare, Mastery ranks, training spots/items
metadata:
  type: project
  modified: 2026-11-01T01:57:03.217Z
---
2026-11-01 Andy: "emphasize training your abilities across the whole pack, a longer grind but every milestone should feel more rewarding"; then "new training items, like weighted clothing costumes for z fighters/gravity, meditating in the cold for wind, feel free to come up with more".
PR #829 (branch claude/project-thread-8xa8st, MERGED 2026-11-01 02:2xZ as pack 0.19.29 / Kingdoms 0.7.464, built, not playtested):
- Powers core/Milestones = the one fanfare (MINOR/MAJOR/GRAND); Kingdoms copy is `Fanfare` (Kingdoms already had an unrelated `Milestones` = road milestones, don't overwrite).
- Mastery.UNLOCK_AT {0,60,150,270,420}; past all 4 abilities RANK_POINTS -> 5 ranks (Training.RANK_AT, PERKS per power, attribute modifiers refreshed every 100 ticks).
- Training spots in core/Training.spot (Wind cold/peak, Darktide night water, Cryo ice, Web cobwebs, Smoke campfire, Chrono Hourglass, Resonance Tuning Fork/bell, Arsenal weapon, Gravity/ZF Weighted Gi). Weighted Gi also x1.5 Kingdoms Strength xp.
- Kingdoms Skills XP 60/30 (was 30/15); saved xp rescaled on join via `_pace` key in Progress.xp (Skills.PACE); bonus point every 5th level.
- Rig xp sources halved. Valor/Bio-built untouched (see [[slower-valor-bio]]). Related: [[mastery-pacing]].
**How to apply:** retune via the constants named above; to rescale Kingdoms skills again bump PACE with the curve.
