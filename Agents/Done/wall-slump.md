---
status: done
task: Ragdolls knocked into walls slide down and sit against them (not all lying flat)
branch: claude/project-thread-7ttuy5
with: Claude (cloud thread "Slump ragdolls against walls")
pr: 1601
mods: kitchen, powers
versions: 
started: 2026-11-15
owns:
  - kitchen/src/main/java/com/jaymoskitchen/ragdoll/WallSlump.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseRagdoll.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseImpacts.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java (slumpWall, stillPropped, Propped save)
  - src/main/java/com/jaymospowers/core/BladeBlood.java (wallSmear)
  - src/main/java/com/jaymospowers/power/web/KitchenCompat.java (onSlump hook)
  - src/client/java/com/jaymospowers/client/wounds/WallSit.java
  - src/main/java/com/jaymospowers/core/wounds/DownedLie.java (wall, propSpot, prop, PROPPED)
  - src/main/java/com/jaymospowers/core/Carry.java (prop on let-go)
  - src/client/java/com/jaymospowers/client/mixin/ProneCameraMixin.java (WallSit eye)
  - src/client/java/com/jaymospowers/client/wounds/WallSitArms.java
  - src/client/java/com/jaymospowers/client/CrawlPose.java (lay pins the sit's limbs, bendArms)
---
# Wall slump

**Task:** Andy: "can enemies who ragdoll into walls pose similarly? as if they were knocked into the wall and slipped down onto the floor? basically so they dont all default to lying flat."

## Plan
- Kitchen WallSlump: humanoid corpse that strikes a wall (thrown, or killed beside it) slides down and sits, back to the wall, legs out, slumped to one side, head lolled; stays until disturbed.
- Powers: blood smear down the wall + stain where it sits.

## Notes
- Checked in tools/ragdoll_sim (new section).

## Done
PR #1601. Humanoid corpses flung into a wall (or killed beside it) slide down and sit, back to the wall, until disturbed; blood smear down the wall. Kitchen + Powers compile with Gradle; ragdoll_sim passes; not played.
PR #1601 now also: downed players/mobs sit against walls (WallSit, DownedPose WALL), corpse stays sat.
PR #1601 third commit (Andy: "the view needs to move to the exact height on the model. other players should be able to place them on the wall, and there should be a smooth animation for the 1.5s it takes to get onto the wall"): 1.5 s get-up (roll onto back, sit up, scoot round, shuffle back, lean), carriers set downed folk against a wall (look at it within 3 blocks and let go, or let go of a drag beside one; synced `downed_propped`), first-person view at the model's eyes. Powers compiles; pose sim checked; not played.
PR #1601 fourth commit (Andy: "I dont see the arms being used in the frames, that's fine if they dont have any or if they are broken, but they should use what they can"): `WallSitArms` plans each arm (top arm pushes off to roll, both prop the sit-up, hands step round during the turn, two pushes back to the wall; broken/missing arms hang, out cold or set down by a carrier both hang) and a two-bone reach puts planted hands on the floor; body schedule now leans back on the hands as far as the wall allows, two-step shuffle. Java arm code run against the Python pose sim (match to 1e-4); Powers compiles; not played. Preview: project-files art-overhaul/wall-slump/wall-sit-arms.png.
- 2026-11-15 14:20 PR #1601 is merged into master; moved to Agents/Done.
