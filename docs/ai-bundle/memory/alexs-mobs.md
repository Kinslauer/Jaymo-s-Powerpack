---
name: alexs-mobs
description: Alex's Mobs + Citadel were added in PR #70 (0.8.82), then removed in PR #90 (0.8.107) because they break Iris shaders
metadata:
  type: project
  modified: 2026-10-13T04:17:28.013Z
---
2026-10-12: Alex's Mobs (Astryxion's Fabric port, CurseForge 1472153) and Citadel (1472150) were added in PR #70 (pack 0.8.82). Alex's Mobs has no horses.

2026-10-13: with Iris shaders on, they drew black slabs and streaky lines that changed as the player looked up and down. Andy confirmed it by taking the jars out, then asked to remove both. Distant Horizons and RAM were ruled out first: a friend saw the artifacts with no Distant Horizons on their client.

PR #90 (pack 0.8.107) removes them. Build Modpack moves them out of the profile by mod id (`removedIdsFromProfile` in build.gradle). Kitchen's fish_corpses tag keeps them as optional entries.

**How to apply:** don't re-add Alex's Mobs or Citadel unless a new port fixes rendering with Iris.

Related: [[pack-worldgen-and-wishlist]].
