---
status: open
for: any
from: pc-claude-server-thread
asked: 2026-11-19
thread: PC Claude, Deck server thread
claimed_by: 
pr: 
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Havens.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Settlers.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Camps.java
  - kingdoms/src/main/java/com/jaymoskingdoms/GoblinVillages.java
---
# Underground Stoneborn holds and goblin villages (plan with Andy first)

**Andy's words (2026-11-19):** "only stone born and goblin villages spawn down there. spider beastfolk make their way into both settlements". His answers to the follow-up questions:
- Fix the layer order and drop the Lost Cities layer. Done in #1852.
- Don't turn ordinary villages into Stoneborn ones. Raise Stoneborn holds by code instead, with new building designs that need his input.
- Underground goblin villages may rise near any Stoneborn settlement down there.
- Spider beastfolk settle only into these underground settlements. Their names are to be drafted for him to approve.

Both entries are in Decisions.md (2026-11-19, 11:55 AM and 12:30 PM).

**What's wanted:** underground, a player finds Stoneborn holds cut into the rock and goblin villages near them. Now and then a spider-kin beastfolk turns up living in either one. There are no ordinary villages down there.

**First, settle with Andy where "down there" is.** The Lost Cities layer is gone from the stack, which is now high sky, Aether, Cloud Sea, Overworld, Nether. The likely home is the Overworld's deep caves (deepslate, Y -64 to 0) or under the mountains. Ask him.

**Where to start:**
- Lore.md: the Stoneborn (raised under the mountains), "The silk deeps", "The Beneath".
- Codemap entries:
  - Stoneborn havens: `Havens` (`draw`, `fits`), `HavenBuildings` (hillside houses), `HavenGrowth` (mine).
  - Goblins and camps: `GoblinVillages`, `GoblinBuildings`, `Camps.spawnCamps` (it only raises goblin villages near realms today).
  - Newcomers and looks: `Settlers` (`kindFor` only brings orcs and goblins), `HeritageVariants.ARACHNID` (only `Land.DARK`; `Land.DEEP` exists), `McaCompat.settler` and `enlist`.
- The ground finders (`ShutIn.openGround`, `GoblinVillages.spot`) use the heightmap, so underground they need a cave-floor finder.
- Format: a plan split into PR-sized tracks, as in `docs/Plan-0.8.md`, with a `(new)` Lore.md paragraph for each placement for Andy to approve.

**Rules:** don't build on Andy's PC, which runs only the game; CI or the Deck builds. Each PR carries a change note.

## Notes
- Background: on the 2026-11-19 world the first kingdom (Selhollow) was raised in the Lost Cities layer's plains village at Y -370. Fixed by #1850 (only surface Epic towns are crowned) and #1852 (stack order; Lost Cities left out). A new world started at 1:06 PM on Powers 0.25.68 / Kingdoms 0.10.93.
