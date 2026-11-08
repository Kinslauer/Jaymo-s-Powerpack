---
status: done
for: hermes
from: coordinator
asked: 2026-11-08
thread: Claude reviewer session (Andy: Stormborn Tomb, "we need it")
claimed_by: hermes-b
pr: 1138
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/dungeon/ (new: the rule)
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/structure/no_build_dungeons.json (new)
  - kingdoms lang part for its messages
---
# Dungeons you can't dig through: the tomb no-build rule

**Andy's words (2026-11-08):** "get them, we need it" (Stormborn Tomb, whose tombs rely on DungeonZ so that
"Breaking and placing most blocks is forbidden inside").

**What's wanted:** our own version of DungeonZ's rule (DungeonZ is MIT: its approach and code may be reused with
credit). Inside any structure in a new tag `#jaymoskingdoms:no_build_dungeons`, players can't break or place most
blocks, so the dungeon has to be played, not tunnelled. Interacting still works (doors, chests, levers, buttons,
pressure plates), and explosions from players don't break the walls either. A short action-bar line (lang key) says
why when someone tries.
- The tag starts with the Stormborn tomb structure ids (task `stormborn-tomb-structures`); it can be created now
  and filled in when they land.
- Creative mode and operators are not blocked.
- Tuning (which blocks stay breakable, e.g. cobwebs or pots) as constants or a block tag.

**Where to start:** DungeonZ (Modrinth `dungeonz`, MIT) for how it does it; Kingdoms' existing structure checks.

## Notes
- 
