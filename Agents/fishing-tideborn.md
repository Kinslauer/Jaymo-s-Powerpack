---
status: in-review
task: realistic fishing 2 + Tideborn react to fishing
branch: claude/fishing-realism-tideborn
with: Claude Code session (side session of the coordinator)
pr: 1132
mods: Powers, Kitchen, Kingdoms
versions: Powers 0.21.61, Kingdoms 0.7.650, Kitchen 0.5.91
started: 2026-11-07
owns:
  - src/main/java/com/jaymospowers/fishing/
  - src/main/java/com/jaymospowers/compat/KingdomsCompat.java
  - kitchen/src/main/java/com/jaymoskitchen/delight/Chopping.java
  - kingdoms/src/main/java/com/jaymoskingdoms/TideFishing.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Theft.java (one Crime entry)
---
# Fishing: place, stock, tuna and shark meat, weight; Tideborn

**Task:** Andy: "The goal is more realistic fishing, also tideborn will see fishing as an attack, and doing in their havens is seen as a crime."

## Plan
- Fish by place and time (biome, dawn/dusk, rain, high pools), Kitchen bait works again
- Overfishing per spot, refills over days
- Raw tuna, tuna steaks, shark meat (own items), Kitchen cuts by weight
- Fish weight on every catch; heavier fights harder, more fillets
- Tideborn who see you fish fight you (Retaliation); fishing in a Tideborn haven is poaching (Theft crime)

## Notes
- Andy picked all four fishing options (2026-11-07).

## Done
PR #1132. Players: fish depend on water, time and weather; spots fish out; every catch weighed; whole tuna, tuna steaks, shark meat; Tideborn fight anglers; poaching in Tideborn havens. Follow-up tracks A-D in docs/Plan-fishing.md (Andy: one PR each, two at a time). Compiles; not played.
