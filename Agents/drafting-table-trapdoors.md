---
status: done
task: drafting-table-trapdoors
branch: claude/drafting-table-trapdoors-3a26b9
with: 
pr: 2038
mods: powers, kingdoms
versions: 
started: 2026-11-23
owns:
  - src/main/java/com/jaymospowers/tinkering/smithing/DraftingTable*.java
  - src/main/java/com/jaymospowers/tinkering/smithing/Patterns.java
  - src/client/java/com/jaymospowers/client/tinkering/smithing/DraftingTableScreen.java
  - src/main/java/com/jaymospowers/buildblocks/TrapdoorUse.java
  - src/main/java/com/jaymospowers/buildblocks/mixin/TrapdoorTypeAccessor.java
---
# Drafting Table screen, deliberate trapdoors, villager particles

**Task:** Andy: "why didnt the drafting table work? it should have a GUI like the other tables. what are those particles on the villager? and trapdoors need a more deliberate activation method, maybe make it double right click or shift right click and hold like doors".

## Done
- Drafting Table opens a menu (blueprint + paper -> copy; forged piece + paper -> pattern).
- Trapdoors: second use in a row (double-click or hold use). Sneak + use was not picked: vanilla skips a block's use when you sneak with anything in hand, and Locks use sneak for keys.
- Particles: the Z is Subtle Effects' snoring; the purple and white sparkles are not traced to our code (the Stoneborn glint doesn't fit: too rare, no white ones); likely another pack mod.
