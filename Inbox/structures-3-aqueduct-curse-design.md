# Aqueduct Curse System - Design Draft for Andy's Approval

**Task:** Structures 3: aqueduct curse quests  
**Branch:** claude/structures-3-aqueduct-curse  
**Date:** 2026-11-26  
**Status:** Awaiting Andy's approval before implementation

---

## Overview

Per the task: "the village near the aqueduct dungeon makes sense as an old feature of their civilization that was taken over by the skeletons/evil magic. there should be quests that come from that dynamic, tasking the player to clear it, and lift the curse from the village."

**Key constraint from notes:** "Identify the Overworld aqueduct and recover full section 5/four quest specifications before implementation; do not invent the missing plan." — The "Structure placement and factions plan" (Claude artifact) section 5 is not accessible, so this draft reconstructs the design from available context and Andy's words.

---

## 1. Overworld Aqueduct Structure

### Problem
The Veradexian aqueduct exists only in the Aether (Powers mod: `Veradexian.aqueduct()`). Kingdoms needs an Overworld equivalent.

### Design: `AqueductStructure` (new Kingdoms structure)
- **Type:** Single structure type `jaymoskingdoms:aqueduct` (like OldRuins uses `old_ruins` with variants)
- **Placement:** Tagged in `ruins_with_heirs` → generates near settlements (within ~300 blocks / 19 chunks per `PlacementReasons.RUIN_NEAR`)
- **Biomes:** Temperate, plains, river valleys (where aqueducts make sense historically)
- **Appearance:** Stone/brick arcade with water channel, partially ruined, pillars every ~8 blocks, ~50-60 blocks long (like Veradexian's 9x49 but Overworld-scaled)
- **"Taken" state:** Skeletons/spawners in the pillars, blight/vines on the stone, dark magic particles — the "evil magic" that took it over

### Structure Registration
```java
// New file: kingdoms/src/main/java/com/jaymoskingdoms/structure/AqueductStructure.java
// Registers: TYPE, PIECE, structure JSON, structure_set JSON
// Tagged in: data/jaymoskingdoms/tags/worldgen/structure/ruins_with_heirs.json
```

### Lore Placement (for Lore.md)
> **(new)** The old peoples built aqueducts to carry sweet water from the highlands to their cities. Most fell to time, but a few were taken by the skeletons and the dark that followed them. Their channels run black now, and the pillars crawl with the risen dead. A village that draws from a taken aqueduct drinks the curse with its water.

---

## 2. SettlementCurse System

### Attachment: `SettlementCurse` (on Faction)
```java
// New file: kingdoms/src/main/java/com/jaymoskingdoms/SettlementCurse.java

record CurseState(
    String ruinId,           // "aqueduct" (extensible)
    BlockPos ruinPos,        // aqueduct center
    boolean active,          // curse is on
    int stage,               // 0=dormant, 1=signs, 2=full, 3=lifting, 4=lifted
    long liftedAt,           // game time when lifted (0 if not)
    List<String> completedQuests  // quest template IDs done
) {}
```

### Curse Detection
- On server tick (every ~600 ticks): for each aqueduct structure start, find settlements within 300 blocks (`Factions.around(server, level, aqueductPos, 300)`)
- Apply `SettlementCurse` attachment to those factions if not present
- Curse stages progress based on player presence/time:
  - **Stage 0 (dormant):** Just placed, no effects yet
  - **Stage 1 (signs):** Water turns dark, villagers cough, crops wither at edges — visible but subtle
  - **Stage 2 (full):** Villagers get "aqueduct_curse" effect (weakness, mining fatigue), water particles black, skeleton patrols at night
  - **Stage 3 (lifting):** After 4 quests done, curse fades over 3 days
  - **Stage 4 (lifted):** Permanent village transformation

### Signs (Plan Section 5 — reconstructed)
1. **Water:** Village wells/fountains run dark (black particles), cauldrons show black water
2. **Villagers:** Occasional coughing animation, "unwell" gossip lines, reduced trades
3. **Crops:** Edge farms grow slower, some plants wither (visual only)
4. **Mobs:** Skeleton patrols spawn near village at night (not raids, just patrols)
5. **Board:** Quest board shows 4 special "Aqueduct Curse" notices with gold ! mark

---

## 3. Four Board Quests

Quests appear on the village board (`TownProjects` / `BoardTerms` system) for cursed settlements only. Templates use prefix `aqueduct_curse.`.

### Quest 1: "Stem the Flow" (FETCH/CHAMPION hybrid)
- **Giver:** Village board (not a specific villager)
- **Objective:** Enter the aqueduct, find the **source block** where dark magic enters the water (a corrupted spawner/block at the aqueduct's head), break it
- **Target:** Aqueduct structure position + offset to head
- **Reward:** Emeralds, reputation, "Purified Water" bottle (new item)
- **Template:** `aqueduct_curse.stem_flow`

### Quest 2: "Clear the Pillars" (SLAY)
- **Giver:** Village board
- **Objective:** Slay the skeleton infestation in the aqueduct's pillars — **12 skeletons** (matching pillar count ~50/8 + ends)
- **Target:** `#minecraft:skeletons` at aqueduct location
- **Reward:** Emeralds, reputation, bow/arrows
- **Template:** `aqueduct_curse.clear_pillars`

### Quest 3: "Purify the Stones" (GATHER + block interaction)
- **Giver:** Village board
- **Objective:** Collect **8 Purified Water** (from Quest 1 reward or crafted: water bottle + glistering melon + blaze powder at shrine) and use on **8 pillar bases** in the aqueduct (right-click block)
- **Target:** Block interaction at pillar positions (tracked via attachment on player)
- **Reward:** Emeralds, reputation, lingering potion of healing
- **Template:** `aqueduct_curse.purify_stones`

### Quest 4: "Break the Anchor" (CHAMPION/BOSS)
- **Giver:** Village board
- **Objective:** Defeat the **Anchor Wraith** — a champion skeleton variant (large, glowing eyes, summons minions) that holds the curse at the aqueduct's deepest chamber (under the end pillar)
- **Target:** Custom entity `jaymoskingdoms:anchor_wraith` (new GeckoLib mob or retextured Wither Skeleton with abilities)
- **Reward:** High emeralds, high reputation, standing, unique relic "Aqueduct Key" (opens aqueduct chest)
- **Template:** `aqueduct_curse.break_anchor`

### Quest Flow
1. All 4 appear on board at Stage 1 (signs)
2. Can be done in any order, but Quest 4 unlocks after 2 others done (progression gate)
3. Each completion advances curse stage: 1→2→3→4 (lifting)
4. Board shows progress: "3 of 4 rites complete"

---

## 4. Lifted Village State (Stage 4)

When all 4 quests done → curse enters Stage 3 (lifting) for 3 in-game days → Stage 4 (lifted).

### Permanent Changes to Village
| Aspect | Cursed | Lifted |
|--------|--------|--------|
| **Water** | Black particles, "tainted" | Clear, blue particles, "blessed" — gives minor regeneration when drunk |
| **Villagers** | Coughing, reduced trades | Healthy, **+1 trade tier** on all professions, new "Aqueduct Keeper" villager type (cleric variant) |
| **Crops** | Slow/withered | **2x growth speed** within 50 blocks of village center |
| **Mobs** | Skeleton patrols | **Iron golem** patrols (1 per 10 villagers), no hostile spawns in village bounds |
| **Board** | Curse quests | New "Aqueduct Blessing" repeatable: deliver blessed water to other settlements |
| **Visual** | Dark vines on buildings | **Clean stone**, lanterns on wells, flower beds at edges |
| **Reputation** | Hard to gain | **+50 base reputation** with this faction for all players who helped |

### Lore Placement (for Lore.md)
> **(new)** When a village lifts an aqueduct's curse, the water runs sweet again and the old stones remember. The village grows an Aqueduct Keeper — a cleric who tends the channel and blesses the water. Their crops never fail, and iron guardians walk their streets. Other settlements send envoys for a draught of the blessed water.

---

## 5. Implementation Files (Planned)

| File | Purpose |
|------|---------|
| `kingdoms/src/main/java/com/jaymoskingdoms/structure/AqueductStructure.java` | Structure generation |
| `kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure/aqueduct.json` | Structure definition |
| `kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure_set/aqueducts.json` | Structure set (spacing 40/14) |
| `kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/structure/ruins_with_heirs.json` | Add aqueduct to tag |
| `kingdoms/src/main/java/com/jaymoskingdoms/SettlementCurse.java` | Curse attachment + logic |
| `kingdoms/src/main/java/com/jaymoskingdoms/QuestBoard.java` | Add 4 quest templates in `villager()`/`boardQuests()` |
| `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/aqueduct_curse.json` | Lang keys for quests, signs, lifted state |
| `kingdoms/src/main/resources/data/jaymoskingdoms/loot_tables/...` | Aqueduct chest, Anchor Wraith drops |
| `kingdoms/src/main/java/com/jaymoskingdoms/monsters/AnchorWraith.java` | Champion entity (if new) |

---

## 6. Questions for Andy

1. **Aqueduct appearance:** Should it visually resemble the Veradexian aqueduct (holystone/brick) or use Overworld materials (stone bricks, mossy cobble, prismarine for water channels)?
2. **Anchor Wraith:** New custom mob, or reuse existing (Wither Skeleton, Vex, custom GeckoLib)?
3. **Quest 3 (Purify Stones):** Block interaction tracking — use player attachment or scoreboard?
4. **Lifted state persistence:** Should the village keep its lifted bonuses if the aqueduct is later re-corrupted (e.g., by a raid)?
5. **Multiplayer:** If multiple players do quests, does the village lift for all, or per-player? (Assume per-faction, shared)
6. **Plan Section 5:** Do you have the original "Structure placement and factions plan" section 5 text? The signs list above is reconstructed.

---

## 7. Next Steps (After Approval)

1. Implement `AqueductStructure` (structure + placement)
2. Implement `SettlementCurse` attachment + detection tick
3. Add 4 quest templates to `QuestBoard` + lang
4. Add Anchor Wraith entity (if new)
5. Implement lifted village transformations
6. Test: generate aqueduct, verify curse applies, complete 4 quests, verify lift
7. `sh ./gradlew build` + `python3 tools/pr_checks.py`
8. PR with "Not tested in game" line

---

**Ready for Andy's review.** Please confirm the design or request changes before implementation begins.