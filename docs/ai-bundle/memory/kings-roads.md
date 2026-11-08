---
name: kings-roads
description: King's Roads (Kingdoms 0.7.9 / pack 0.10.44, PR #159): own settlement roads; Via Romana and Settlement Roads can't ship on 26.3
metadata:
  type: project
---
2026-10-14 Andy asked whether Via Romana and Countered's Settlement Roads could work with our carriages ("edit the world travel").
Via Romana: 1.21.1 max, Responsive Source License bars hosting ports. Settlement Roads: MIT, 1.21.10 max. Neither ships.
Built our own `Roads` in Kingdoms (MERGED 2026-10-14 15:38Z as PR #162 (draft #159 closed: GitHub refused to undraft it), branch claude/project-thread-u7hw6q): A* routes on worldgen base heights
between realms/villages (2 nearest within 700 + overlord), paved per chunk as it loads, biome-styled, bridges, lanterns,
milestone signs; cabs/rickshaws/rulers (via Construction.leg) and caravans follow them. Ops: /kingdom roads [plan].
Andy chose "Both" for milestone travel (2026-10-14): Milestones.java adds coach rides (Wagon.COACH, fare to realm) and teleport
(dearer, fare lost, hunger, 30 s rest) from linked signs, plus a Road Charter item linking player-built roads (80% on road blocks).
Not seen in game yet. Related: [[kingdoms-caravans]], [[kingdoms-rickshaws]].
