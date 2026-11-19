---
status: running
task: smoothmount-1-climb-on-off
branch: hermes/smoothmount-1-climb-on-off
with: 
pr: 
mods: Powers
versions: 
started: 2026-11-19
owns:
  - powers/src/client/java/com/jaymospowers/client/MountClimbPose.java
  - powers/src/main/resources/assets/jaymospowers/geckolib/animations/entity/player_mount_climb_on_left.animation.json
  - powers/src/main/resources/assets/jaymospowers/geckolib/animations/entity/player_mount_climb_on_right.animation.json
  - powers/src/main/resources/assets/jaymospowers/geckolib/animations/entity/player_mount_climb_on_back.animation.json
  - powers/src/main/resources/assets/jaymospowers/geckolib/animations/entity/player_mount_climb_off_forward.animation.json
  - powers/src/main/resources/assets/jaymospowers/geckolib/animations/entity/player_mount_climb_off_back.animation.json
  - powers/src/client/java/com/jaymospowers/client/EmfCompat.java
  - powers/src/main/java/com/jaymospowers/core/MountClimbHandler.java
---
# SmoothMount climbing, patch 1: Climb on, climb off, camera glide

**Task:** Implement SmoothMount patch 1 - climb on (left/right/behind), climb off (forward/back), camera glide, armour-weight scaling, server-side hold. New class `MountClimbPose`, GeckoLib animation clips, client + small server hold.

## Plan
- [ ] Study existing CrawlPose and KeyframeClip usage in Powers client
- [ ] Study EmfCompat.hold for pausing Fresh Animations
- [ ] Create MountClimbPose class with climb-on (3 variants), climb-off (2 variants), camera glide
- [ ] Create GeckoLib animation JSON files for each clip
- [ ] Implement armour-weight scaling (cloth ~1x, full plate ~2x)
- [ ] Implement server-side hold: ride starts only when climb ends, interruptible by hit
- [ ] Ensure other players see the climb animations
- [ ] Test build and create change note
- [ ] Open PR

## Notes
- Andy ticked all 14 rows on SmoothMount pick page; full plan at /mnt/project-files/smoothmount/Climbing-Plan.md (cloud)
- Source: SmoothMount (Ender_Fake, LGPL-3.0, Fabric 1.21.11 only, client only) - ideas only, write own code
- Clips are Blockbench (GeckoLib .animation.json) played on vanilla player model through Powers' client/KeyframeClip
- Pause Fresh Animations via client/EmfCompat#hold while clips play
- Horse models: climbs are on rider; horse-side poses go into BreedHorseModel/WarElephantModel
- If horse models clash, stop and set task blocked
- Order: patch 1 first (others reuse its pose class); two tracks at a time: 1+6, then 2+4, then 3+5
- Picks for patch 1: climb-on, climb-off, camera-glide, pig, armour-weight
- New class e.g. client/MountClimbPose; clips in geckolib/animations/entity/player_mount_*.animation.json

## Done