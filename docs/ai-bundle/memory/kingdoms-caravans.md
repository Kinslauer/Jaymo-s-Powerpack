---
name: kingdoms-caravans
description: Kingdoms 0.5.14 (PR #44, pack 0.8.47): fief focus, caravans of horse-drawn carts, mounted guards/archers, king's carriage
metadata:
  type: project
  modified: 2026-10-12T06:43:45.386Z
---

Andy asked 2026-10-12 for horse-drawn carriages, mounted guards and archers for wealthy kingdoms, fiefs focused on mines or farms (fewer walls), and caravans of storage carts. Built as Kingdoms 0.5.14 on branch claude/kingdoms-caravans-xs4x72, PR #44 merged 2026-10-12 06:48Z. Classes: Focus, Wealth, Cavalry, Caravans, Carriages, Wagon (+ client WagonRenderer).

Not playtested yet: a player robbing a cart, a mining fief's caravan, mounted war parties.

Dev-server gotcha: 26.3 servers stop ticking 60 s after the last player leaves; set pause-when-empty-seconds=0 in kingdoms/run/server.properties and /forceload the test area (now noted in MODLOG's Testing section).

Related: [[kingdoms-war-tempers]], [[kingdoms-living-economy]].
