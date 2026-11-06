---
name: treechop-felling
description: Kingdoms 0.5.25 (PR #48 merged 2026-10-12): TreeChop-style chopping of trees via ChoppedLog block; woodcutters chop too
metadata:
  type: project
  modified: 2026-10-12T07:52:57.727Z
---
2026-10-12 Andy asked to combine HT's TreeChop with our Trees system ([[kingdoms-living-economy]]). TreeChop (MIT) has no 26.3 build, so rebuilt: `ChoppedLog` block + block entity (narrows per chop, renderer draws original log scaled), `Trees.chop` via PlayerBlockBreakEvents.BEFORE, chops needed = round(1+10 ln(1+(n-1)/10)). Woodcutters chop every 12 ticks. PR #48 (merged) on branch claude/treechop-felling-fil1sp, jar in /mnt/project-files/treechop/. Player chopping seen in game; woodcutter chopping not yet seen.
**Why:** Andy wants trees to feel like TreeChop.
**How to apply:** take the next free Kingdoms version before merging; Andy merges on "merge the PR".
