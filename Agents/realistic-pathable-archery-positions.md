---
status: in-review
task: realistic-pathable-archery-positions
branch: claude/realistic-pathable-archery
with: apollo
pr: 2220
mods: kingdoms
versions:
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Castle.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Citadel.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Seats.java
  - kingdoms/src/main/java/com/jaymoskingdoms/WallArchers.java
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
- Moved archer posts from battlements/parapets to accessible wall walks and roof floors in Castle (keep, castle, tower, watchtower) and Citadel structures
- Added ladder/trapdoor access validation so archers can path to their posts
- Filtered village seat rooftop posts to only those accessible from the building interior or with climbable blocks nearby
- Validated posts have standing clearance (2 air blocks, solid floor) before placing archers
- PR #2220 opened for review, compiles and passes PR checks
