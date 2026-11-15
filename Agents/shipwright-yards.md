---
status: in-review
task: shipwright-yards
branch: claude/project-thread-c00opi
with: 
pr: 1603
mods: kingdoms
started: 2026-11-15
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Shipyards.java
  - kingdoms/src/main/java/com/jaymoskingdoms/sailing/Fleet.java (Slip, launchLaid)
  - kingdoms/src/main/java/com/jaymoskingdoms/Corsairs.java (man)
  - kingdoms/src/main/java/com/jaymoskingdoms/Navies.java (warship via yard, commission)
  - kingdoms/src/main/java/com/jaymoskingdoms/Whalers.java (whaling gated on a built whaler)
  - kingdoms/src/main/java/com/jaymoskingdoms/ShipTalk.java (shipwright talk, timber)
  - kingdoms/src/main/java/com/jaymoskingdoms/Workers.java (shipwright step hook)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/shipyards.json
  - changes/kingdoms/shipwright-yards.md
---

# Shipwrights build ships at the harbour

**Task:** Andy (2026-11-15): how many Shippy Ships ships (4 hulls x 11 woods; can't add to that mod), and a shipwright
MCA villager seen assembling ships at a kingdom's dock, with a whaler that unlocks whaling.

## Done
- Harbour shipwright builds warship and whaler block by block beside the pier (`Shipyards`, `Fleet.Slip`).
- A realm whales only after its yard launches a whaler; lost whalers are rebuilt. Talk about work + timber help.
- Lore paragraph "The yards" added to Lore.md as (new), awaiting Andy.
- Kingdoms `gradlew build` passes. Not played, not booted.
