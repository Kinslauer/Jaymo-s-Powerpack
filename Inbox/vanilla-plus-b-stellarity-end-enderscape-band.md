---
status: open
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B1dgVWnWmWqNjNa691TsU49
claimed_by: 
pr: 
---
# Vanilla+ patch B: Stellarity as the End, Enderscape as a band under the Nether (Powers + pack)

**Andy (2026-11-19):** "Can we ship stellarity as the end and enderscape underneath?" He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-b-stellarity-end-enderscape-band`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- Stellarity 6.0.1 (Modrinth `WBiwFsvZ`) shipped UNCHANGED as the End (its licence forbids modified versions in packs without kohara's permission; unchanged is fine).
- New band dimension `jaymospowers:the_beneath` with a `minecraft:the_end` biome source (Fabric `TheEndBiomes` fills it with Enderscape's biomes) and its own copy of vanilla End noise; added under `minecraft:the_nether` in `compat/OneDimensionDefaults`; Stacked helpers learn it; Enderscape's sky/fog attributes copied onto its dimension type. New worlds only.
- Move our relic/writ/quest hooks from Enderscape's End structures to Stellarity's. Check Enderscape's End-city/gateway/dragon mixins against Stellarity's End in a plain server first.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes, and `checkPackMods`.
- Lore: approved ("The Beneath", Lore.md 2026-11-19).
