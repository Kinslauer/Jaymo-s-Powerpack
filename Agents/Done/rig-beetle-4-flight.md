---
status: done
task: rig-beetle-4-flight
branch: claude/rig-beetle-4-flight
with:
pr: 1765
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
Added five new flight actives to the Rig menu (Sneak + J), gated by Mind level 5 (FLIGHT_LEVEL):

1. **Pull up** (`fl-pullup`): Time-to-impact warning on visor when diving, Mind shout, one-knee landing catches falls
2. **Hyper thrust** (`fl-hyper`): Double-tap Sprint while flying for 2x speed burst, heavy charge cost, cooldown
3. **Bank into turns** (`fl-bank`): Smooth body roll into turns visible to all players, eased by speed
4. **Meteor dive** (`fl-meteor`): Rocket up then crash down at look target, GravityMeteorFx-style impact, no block damage
5. **Other flyers look smooth** (`fl-smooth`): Client toggle to smooth other Rig pilots' flight poses

PR #1765 created. Compiles and passes PR checks. Not tested in multiplayer for smooth-others behavior, not tested meteor dive impact effects at scale, not tested pull-up timing with various fall heights.
- 2026-11-19 01:59 PR #1765 is merged into master; moved to Agents/Done.
