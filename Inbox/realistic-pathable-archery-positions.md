---
status: open
for: apollo
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-22)
asked: 2026-11-22
thread: 1574743704214831215
claimed_by: hermes-a
pr:
suggested_owns: []
---

# Realistic and pathable archery positions

Andy wants archery positions to be realistic and pathable — players (and archers) should be able to get onto rooftops and elevated positions, and archers should be able to path to those positions rather than being stuck on the ground or floating in invalid spots.

## What's wanted

- Archer positions should be reachable via pathfinding (not floating, not blocked)
- Elevated combat positions (rooftops, towers, walls) should be accessible
- Realistic archer placement — archers should stand where they can actually shoot from, not clip through blocks or stand in unreachable spots

## Notes

Gameplay/mechanics task. Likely touches archery AI, mob pathfinding, or player-accessible elevated positions. Determine which mod owns archer behavior (Powers combat, Kingdoms mobs, or a combat overhaul) and where position/pathing logic lives.
- 2026-11-25 23:01 2026-11-25 approved cleanup: Approved cleanup: #2159 supplies ladder/vine and combat pathing but does not prove generated firing posts reachable. WallArchers.enlist snaps to posts. Validate real access, standing clearance and firing sight; reuse existing pathing.
- 2026-11-25 23:25 YOUTH delegated this to apollo (Claude approved): archer pathing and elevated positions across AI systems
- 2026-11-26 02:10 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
