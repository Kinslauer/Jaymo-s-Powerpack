---
name: farsight-stalkers
description: Farsighted Mobs + Stalker Creepers remade in Kingdoms (Farsight, Stalkers), PR #243 merged as pack 0.11.53 / Kingdoms 0.7.56
metadata:
  type: project
  modified: 2026-10-15T13:25:03.796Z
---
2026-10-15 Andy posted Farsighted Mobs (LGPL-3, newest Fabric 1.21.4) and Stalker Creepers (AtomicStryker, Forge/NeoForge only, no licence) and said "i need these". Neither has a 26.3 Fabric build, so both were remade in Kingdoms:
- `Farsight`: monsters see at least 24 blocks, +8 on Hard, +4 at top local difficulty (transient FOLLOW_RANGE modifier on load); Raiders (illagers, witches) and monsters that already see farther keep their own.
- `Stalkers`: creepers targeting a player freeze while watched (view cosine > 0.6, line of sight); within 3.5 blocks or once hissing they come on anyway. Priority-1 goal via the public `Mob.getGoalSelector()` (no accessor needed).
PR #243 MERGED 2026-10-15 13:2xZ as pack 0.11.53 / Kingdoms 0.7.56 (master had taken 0.11.47-0.11.52 and 0.7.55). Not playtested. Next free: 0.11.54 / 0.7.57.
