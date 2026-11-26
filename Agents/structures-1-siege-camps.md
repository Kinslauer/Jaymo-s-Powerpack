---
status: running
task: structures-1-siege-camps
branch: claude/structures-1-siege-camps
with: apollo
pr:
mods: kingdoms
versions:
started: 2026-11-25
owns:
  - kingdoms Camps.java, Sieges.java, SiegeTrain.java
  - kingdoms lang/parts/siege-camps.json
---
# Structures 1: siege camps and real sieges

**Task:** Build siege camps that spawn in groups of 3-4 around a kingdom/castle seat, with a muster period, supply-dependent sieges, and victor resource transfer. Currently a cleared camp's land and storage are wiped instead of going to the victor.

## Plan
- ✅ Scan existing Camps.java, Sieges.java, SiegeTrain.java to understand current camp/siege logic
- ✅ Modified camp placement to spawn in groups around kingdom seats
- ✅ Added muster period before siege starts
- ✅ Made siege draw supplies from camps (clearing a camp mid-siege weakens it)
- ✅ Change victory logic: winners take the site, losers routed/killed, their storage goes to victor
- ✅ Update lang/parts/siege-camps.json for any new keys
- ✅ Added transferLand method to KingdomEvents.java
- ✅ Added routDefender method to Sieges.java

## Notes
- Andy's rule: every generated structure needs a reason to be where it is, tied to the climate heatmap and economic/political climate
- Do not edit HollowCaptains.java, Captains.java, Rivals.java or Ladders.java while their separate PR owns them
- #2195 fixes NPC crowding, not this task

## Done
What a player will see: Groups of 3-4 camps now spawn in a ring around kingdom seats. When a war party arrives, a muster period begins where the siege draws supplies from nearby camps. If the siege is won, the defender faction is routed - its people scatter, its land transfers to the victor, and all its treasury/storage goes to the victor's spoils. Not tested in game.