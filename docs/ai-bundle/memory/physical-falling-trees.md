---
name: physical-falling-trees
description: Physical Falling Trees datapack remade in Kingdoms FallingTree (PR #164, Kingdoms 0.7.11 / pack 0.10.50)
metadata:
  type: project
---
2026-10-14 Andy asked to combine the Physical Falling Trees datapack with our felling ([[treechop-felling]]). The datapack is CC BY-NC-ND 4.0 and only goes to 26.2, so it was remade in FallingTree: falls to the heavier side (log 3, leaf 1; even trees fall away from the cutter), damage by tip speed (cutter included), bee nests come down with angry bees, and the tree settles as real blocks (logs on their side, leaves decaying). Lying logs aren't chopped (Trees.fallen). Woodcutters' trees still break into their bags. PR #164 MERGED 2026-10-14 16:1xZ (pack 0.10.50 / Kingdoms 0.7.11) on claude/project-thread-elwv6n, jar in /mnt/project-files/falling-trees/.
Tornado-thrown trees also settle as lying blocks (Andy). **How to apply:** fallen-tree behaviour lives in FallingTree.settle; lying logs skip chopping via Trees.fallen.

2026-11-01 PR #822 MERGED (Kingdoms 0.7.456 / pack 0.19.15): a tree that hits something before 45° lean (FallingTree.LYING) glances a quarter turn to a clear side (max GLANCES=2); boxed in, it leans then breaks apart. It must never settle as standing blocks (Andy: a felled tree "replanted itself upright").
