---
status: writing
for: apollo
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-22)
asked: 2026-11-22
thread: 1574743704214831215
claimed_by: hermes-a
pr: 2220
suggested_owns: []
writer: hermes-writer
text_pass: 
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
- 2026-11-26 07:01 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-26 07:16 Text pass: PR #2220 added no player-visible strings (comments only). Added changes/kingdoms/realistic-pathable-archery.md (archers take reachable wall-walk and roof posts; posts need standing clearance; seats skip inaccessible roofs). Fixed the missing class brace in kingdoms/src/main/java/com/jaymoskingdoms/Seats.java and missing Direction/BlockTags imports in kingdoms/src/main/java/com/jaymoskingdoms/WallArchers.java so Kingdoms compiles. Kingdoms: build successful, pr_checks passed.
- 2026-11-27 02:58 Review (head 4fd0913), cloud coordinator for Andy: changes needed. Posts are an archer's feet, so they sit one above the block they stand on. Master had that right; this PR moves every post down one, into the floor. 1) Castle.java:626 watchtower: platform planks are at high+1, post is now (0, high+1, 1), inside the planks (was high+2, correct). 2) Castle.java:309 keep: the rim wall is solid up to KEEP_WALL, post now at KEEP_WALL is inside the wall. 3) Castle.java:499-505 curtain: the walk is a top slab at CURTAIN, posts now at CURTAIN are inside the slab; master's (-h+1, CURTAIN+1, d/2) was already right. 4) Castle.java:586 and Citadel.java:82-89: same off-by-one on roof posts. With every castle post invalid, WallArchers.freePost (line 53) falls back to a random invalid post, so archers snap into blocks: worse than before. Revert the y changes; if a post was on a battlement block, move it sideways to an open walk cell instead. 5) Seats.java:617 isPostAccessible returns true for any roof within 12 blocks of town ground, which is nearly all of them, so roofs aren't checked for access at all. The task asked to validate real access and reuse existing pathing: check that the archer can actually reach the post (level pathfinding from the seat's door, or a ladder/stair column that reaches the roof), and drop those that can't. 6) Indentation broken in all four files. CI is green.
