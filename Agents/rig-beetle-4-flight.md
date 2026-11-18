---
status: stalled
task: rig-beetle-4-flight
branch: claude/rig-beetle-4-flight
with:
pr:
mods: powers
versions:
started: 2026-11-18
owns:
  - src/client/java/com/jaymospowers/client/rig/RigFlightFeel.java
  - src/main/java/com/jaymospowers/rig/RigDive.java
  - changes/powers/rig-beetle-4-flight.md
---

# Rig patch 4: flight and falling

**Task:** Implement Rig flight features: Pull up warning, Hyper thrust, Banking turns, Meteor dive, and smooth other flyers.

## Plan
- Explore existing rig flight code (RigFlightFeel, RigPose, RigSuit, RigHud, RigClient, RigActives, RigMenuScreen/RigPicks)
- Implement Pull up (fl-pullup): time-to-impact warning on RigHelmView + Mind shout, RigLanding catches
- Implement Hyper thrust (fl-hyper): sprint while flying, double-tap sprint, heavy charge cost
- Implement Banking (fl-bank): smooth roll into turns, eased by speed, visible to everyone via RigPose
- Implement Meteor dive (fl-meteor): rocket up then crash down, reuse GravityMeteorFx look at Rig scale, no block damage
- Implement Other flyers smooth (fl-others): check multiplayer first, smooth pose/roll between updates if needed
- Add all as Rig menu entries with charge costs, gated by Mind level
- Add change note

## Notes
- From Andy's pick list (Zemo&Baki DC Universe jar, ideas only, no DC names)
- Frostborn Rig tech: plates slide, lock, fold; nothing grows or is alive
- New actives are Rig menu entries (RigMenuScreen / RigPicks pattern)
- Poses through RigBeats / RigPose (wind-up, snap with overshoot, settle)
- Effects through MagicFx only
- Tuning numbers as static final at top of each class
- Suggested pair: (2 guns + 4 flight) - this is patch 4

## Done
What a player will see, and what wasn't tested.
- 2026-11-18 20:42 No sign of work for 30 min: marked stalled (whoever ran it stopped).
