---
status: stalled
task: rig-beetle-3-back-rig
branch: claude/rig-beetle-3-back-rig
with: 
pr: 
mods: 
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/rig/RigBack.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigBackClient.java (new)
  - tools/gen_rig.py (back plates only) + regenerated RigSuitMesh
  - changes/powers/rig-beetle-3-back-rig.md
---
# Rig patch 3: the back rig

**Task:** Six new Rig back rig actives as Rig menu entries (Sneak+J radial menu): folding wings for flight, wing slash dash, spear legs impale, pincer grab/throw, grapple claw, chain lightning from helm. All Frostborn Rig tech, plates slide/lock/fold.

## Plan
- Create RigBack.java with six gun actives
- Create RigBackClient.java for client-side state
- Add action constants to RigNet
- Add move kinds to RigMoves
- Add State fields to RigActives
- Add picks to RigPicks
- Add menu entries to RigMenuScreen
- Add translations to rig.json
- Update tools/gen_rig.py for back plates and regenerate RigSuitMesh
- Create change note

## Notes
- Shared rules: Frostborn Rig tech only, no DC names, Rig menu entries, Mind level gates, Core charge costs
- Wings: two vane panels on Core's back plate, spread in full flight, folded in hover (eased, RigBeats)
- Wing slash: upgrades RigStrikes' dash with blade damage
- Pincer hold: uses CarryPose for held creature
- Grapple: uses WebHeft's rope rules (Rig active, not web)
- Horn lightning: overcharges Core like lightning strike (RigActives OVER_*)

## Done
Compiles but not tested in game.
- 2026-11-18 14:07 No sign of work for 30 min: marked stalled (whoever ran it stopped).
