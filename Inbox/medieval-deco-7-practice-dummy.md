---
status: open
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Medieval music/deco patch 7: Practice Dummy (Powers) (rows: dm_kinds, dm_colours, plus mb_ship)

**Andy (2026-11-19):** cross-referenced Medieval Music, Medieval Buildings, Fallen Adventurers, Medieval Deco, MmmMmmMmmMmm, Satisfyu's Let's Do mods and Global Wind; picked 41 of 49 rows on https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv (db `picks/andy`), then chose "Send to the PC reviewer". He OK'd hosting the trimmed music zip as a GitHub release asset.

**Full plan:** `docs/MedievalMusicDeco-Plan.md` on branch `claude/project-thread-ttl8xk` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/medieval-deco-7`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the ARR mods (our own code, names, art); Global Wind's MIT code may be ported with its notice; the music is CC BY (credit Nakarada and Sarisan).

## Picked
- **dm_kinds**: `PracticeDummy` reads its head and hand slots: a skeleton or zombie skull makes it undead (Smite), a
  turtle shell an aquatic (Impaling), an ominous banner an illager. MmmMmmMmmMmm is CC0, so its approach may be reused.
- **dm_colours**: floating numbers coloured by damage type: crits red, fire orange, lightning yellow, magic and ki in
  their `MagicFx` colours, on top of the size colours.
- **mb_ship**: no build. Medieval Buildings only loads on 26.1.x; add it to packMods once a 26.3 build exists.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none needed; if one turns out to be, quote it in full to Andy in the thread for approval first.

## Notes
- 2026-11-18 22:22 PC Claude took it (editor agent, branch claude/medieval-deco-7).
- 2026-11-18 23:49 pc-claude-deco-7's claim had no sign of work for 30 min: handed back as open.
