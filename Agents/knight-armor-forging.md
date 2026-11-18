---
status: done
task: weapon-hooks-and-breaks
branch: claude/knight-armor-forging
with: 
pr: 1718
mods: powers
versions: Knight Armor and Weapons 1.1.1
started: 2026-11-18
owns:
  - build.gradle (one packMods block after fantasy_armor)
  - src/main/java/com/jaymospowers/tinkering/smithing/KnightArmorForging.java
  - src/main/resources/data/knightarmorweapons/
  - changes/powers/knight-armor-forging.md
---
# Knight Armor forging datapack

**Task:** Andy (2026-11-18): "add the knight datapack for forging". Ship Knight Armor and Weapons unchanged, grid recipes off, Smithing Anvil forging instead.

## Plan
- packMods line, disable recipes (fabric:false), KnightArmorForging provider, change note, PR, CI.
