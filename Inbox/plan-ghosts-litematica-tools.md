---
status: open
for: any
from: coordinator
asked: 2026-11-19
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BEaWHrE1YRTzZsQP6SD8uJi
claimed_by: 
pr: 
suggested_owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/PlanGhosts.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/MasonScreen.java
  - kingdoms/src/main/java/com/jaymoskingdoms/PlanSites.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MasonTable.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MasonNet.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang (the building-plan lines only)
  - changes/kingdoms/<branch>.md
---
# Building plans: wrong-block colours, layer view, full material list

**Andy's words:** asked whether Litematica (https://modrinth.com/mod/litematica) is how our schematics work. Told
him: mostly yes (the mason's table, building plans, laid-plan ghosts), but Litematica also colours wrong/missing/extra
blocks, shows one layer at a time and has a full material list against your inventory. Andy: "yes build this and all
your suggestions on PC claude".

**What's wanted:** our own versions (Litematica is LGPL-3.0: ideas only, no copied code or art). All three, none cut:

1. **Wrong / missing / extra blocks coloured** on a laid plan (`PlanSites` + `PlanGhosts`). Today a ghost is pale blue,
   disappears once the right block is in, and simply isn't drawn where a wrong block stands. Wanted: missing = the
   pale blue ghost as now; wrong block in the spot = a red tint/outline over that block; extra = a block standing where
   the design has air, inside the plan's box, shown in a third colour (e.g. orange). Right blocks show nothing.
   The client already gets every block's state in `PlanSites.Ghost` (`rel`, `states`); check whether air spots are
   sent (needed for "extra") and add them if not, cheaply.
2. **Layer view**: while holding the laid building plan, show one layer at a time (only that Y level's ghosts and
   colours, the rest hidden). A key or scroll while holding the plan steps the layer up/down, one setting shows all
   layers; the current layer shows on the action bar ("Layer 4 of 12"). Pick keys that don't clash with the pack's
   defaults (R/G/V/B/C/Z/H/', sneak+scroll is the stance wheel), keybind names under ~30 chars. Stays per player,
   client side.
3. **Full material list against your inventory**: every block the plan still needs (not just the table's top 8),
   with how many you carry, how many are still short, sorted by short. Show it at the mason's table screen for the
   plan in the table and for a held laid plan (e.g. use the plan in the air, or a button on the screen). Scrolls when
   long (screens never cut text: UiStyle/Ui). Counts the same substitutes the build does (dirt for grass/paths,
   either half of a door/bed).

**Where to start:** `docs/codemap/kingdoms.md` lines for MasonTable, PlanSites, PlanGhosts, MasonScreen, MasonNet,
SchematicFiles, Designs. `MasonTable` around line 580 builds today's top-8 needs list (`LIST_MATERIALS`).
`PlanGhosts` constants `RANGE = 48, MAX_SHOWN = 6000` (keep the frame cost about the same).

## Notes
- Lore: this only extends the existing mason's table and building plan, so no new lore placement is expected; check
  Lore.md anyway and add a `(new)` paragraph only if something new is named.
- One PR, one change note `changes/kingdoms/<branch>.md`. No `version=`, MODLOG or codemap edits.
- Check every 26.3 render/keybind API against the sources. Build `kingdoms/` to prove it compiles.
- Andy tests by merging, so don't ask him to test an unmerged build.
