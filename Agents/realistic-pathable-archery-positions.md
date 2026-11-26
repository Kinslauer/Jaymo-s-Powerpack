---
status: running
task: realistic-pathable-archery-positions
branch: claude/realistic-pathable-archery
with: apollo
pr:
mods: kingdoms
versions:
started: 2026-11-26
owns:
  - kingdoms archer AI/pathfinding files (to be determined)
---

# Realistic and pathable archery positions

**Task:** Archer positions should be reachable via pathfinding (not floating, not blocked). Elevated combat positions (rooftops, towers, walls) should be accessible. Realistic archer placement — archers should stand where they can actually shoot from, not clip through blocks or stand in unreachable spots.

## Plan
- Scan codebase for archer AI, mob pathfinding, and position selection logic
- Determine which mod owns archer behavior (Powers combat, Kingdoms mobs, or a combat overhaul)
- Find where position/pathing logic lives (WallArchers.enlist mentioned in notes)
- Validate real access, standing clearance and firing sight; reuse existing pathing
- Make changes to ensure archers can path to elevated positions

## Notes
- Andy's approved cleanup: #2159 supplies ladder/vine and combat pathing but does not prove generated firing posts reachable. WallArchers.enlist snaps to posts. Validate real access, standing clearance and firing sight; reuse existing pathing.
- 2026-11-25 YOUTH delegated this to apollo (Claude approved): archer pathing and elevated positions across AI systems
- 2026-11-26 02:10 hermes-a's last run stopped before finishing; handed back as open so it can carry on.

## Done