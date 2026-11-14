---
status: done
task: Katana only from Mohknee's iron sand
branch: claude/project-thread-3uhctt
with: Andy
pr: 1507
mods: powers, kingdoms
versions: 
started: 2026-11-14
owns:
  - src/main/java/com/jaymospowers/tinkering/smithing/FoldedSteel.java
---
# Katana from folded steel

**Task:** Andy: "the katana should only be craftable with iron sand, giving it a reason to go to mohknee."

## Plan
- Folding a bloom (Kingdoms `BlossomSteel`) gives a Folded Steel Ingot (Powers).
- The katana blade is hammered only from hot folded steel (`WeaponParts.Scheme.FOLDED`, `FoldedSteel` check).

## Notes
- Nihon Blacksmithing: All Rights Reserved (Fabric 26.2, Forge 1.20.1). Idea only.
- Lore.md: "(new) The long blade", for Andy to approve.

## Done
Folding a bloom gives a Folded Steel Ingot; the katana blade is hammered only from it. Powers compiled; not played, no server boot check.
- 2026-11-14 07:49 PR #1503 is merged into master; moved to Agents/Done.
PR #1503 merged 2026-11-14. Follow-up PR #1507: a blossom trader captain sells a chart of Mohknee's western shore (Kingdoms `BlossomTraders.chart`); compiled, not played.
