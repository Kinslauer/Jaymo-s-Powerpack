---
status: working
task: Sengoku Jidai picks for the blossom villages, on a far continent
branch: claude/project-thread-795vuu-p3
with: Andy
pr: 
mods: kingdoms
versions: 
started: 2026-11-12
owns:
  - docs/BlossomLands-Plan.md
---
# Blossom lands

**Task:** Andy picked 59 ideas from Sengoku Jidai (ARR, ideas only) for the blossom villages, all to live on a separate continent reached by long sailing and spoken of by villagers.

## Plan
- docs/BlossomLands-Plan.md: 16 patches; patch 1 is the far land (worldgen), patch 2 the talk and the voyage.

## Notes
- Patch 1 (Mohknee, the far land) was PR #1407, merged 2026-11-13: tools/gen_mohknee.py, seas/Mohknee, MohkneeStructureMixin, IslandStructure only_at.
- Andy's answers so far: two days and nights (16,000 blocks of sea); the land is Mohknee; Tideborn islands on the way; a captain or companion can pilot (patch 2).
- Still open: fliers/pods, old mainland blossom villages, house names, a blossom-born start.

## Done
Patch 1 built and checked in a Kingdoms dev server (map command, /locate, one island generated). Not walked in game.

Patch 1 merged 2026-11-13 (PR #1407). Patch 2 next, on branch claude/project-thread-795vuu-p2 from master.
Patch 2 first half (Helmsmen, MohkneeTalk) is PR #1410, 2026-11-13. Left for patch 2: blossom merchantmen, bought passage, rutter.
PR #1410 merged 2026-11-13. Second half of patch 2 (blossom merchantmen, bought passage) next on -p2b.
Patch 2 second half (BlossomTraders, passage) is PR #1411, 2026-11-13.
PR #1411 merged 2026-11-13. Patch 2 done. Patch 3 (building blocks) next on -p3.
