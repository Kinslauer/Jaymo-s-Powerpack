---
status: in-review
task: Dodge roll prone-only (sideways log roll), slower, slower with heavier armour (capped)
branch: claude/project-thread-k8mvto
with: 
pr: 1699
mods: powers
versions: 
started: 2026-11-17
owns:
  - src/client/java/com/jaymospowers/client/CrawlPose.java
  - src/main/java/com/jaymospowers/stamina/Dodge.java
  - src/client/java/com/jaymospowers/client/stamina/DodgeClient.java
  - src/client/java/com/jaymospowers/client/stamina/DodgeRollPose.java
  - src/client/java/com/jaymospowers/client/mixin/StanceInputMixin.java
---
# Slow the roll and weigh it by gear

**Task:** Andy: "Rolling should be slower and look like snake from metal gear, it should be even slower the more weight your gear has. To an upper limit."

## Plan
- No hop: sustained ground carry for the whole roll, 0.9 s bare.
- Low shoulder roll pose, dive before the turn.
- Armour points + toughness / 32 -> up to 1.6x longer and slower.

## Done
- PR #1699: grounded shoulder roll, 0.9 s bare, up to 1.6x slower by armour points + toughness (cap 32). Compiles; not played.
