---
status: in-review
task: frostborn-tech-villagers
branch: claude/project-thread-2kqme0
with: claude-cloud
pr: 1285
mods: kingdoms
versions: 
started: 2026-11-11
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/FrostTech.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/FrostTechLayer.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/frost_tech.json
  - tools/gen_frost_tech.py
---
# Frostborn wear their tech, and talk about it

**Task:** Frostborn villagers show scouters and made limbs more often (less so Overworld-raised ones), people talk about Frostborn tech, and the old Frostborn text is fixed.

## Plan
- FrostTech: what a villager wears, from their UUID. FrostTechLayer draws it.
- Blood lines by what they wear, neighbours' gossip, three TalkBook talks.
- Life-story answer "starwrights" for Frostborn players (scouter + spare iron arm).

## Notes
- Heritage desc and the "came down from the ice" line were already fixed in #1271. No game text mentions the draugr's Frostborn jailer.
- Look only: head slot, MCA armour routine and limbs untouched. No line names Hail (open question in Lore.md).
- Players get no cosmetic limb: a real prosthetic needs a lost limb.

## Done
About two in three grown Frostborn villagers wear a scouter, a made arm or a made leg and talk about it proudly; neighbours gossip about Frostborn work; a Frostborn-only life-story answer starts a player with a scouter. Compiles; not played, look not seen in game.
