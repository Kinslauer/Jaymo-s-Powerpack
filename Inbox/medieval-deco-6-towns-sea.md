---
status: open
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Medieval music/deco patch 6: Towns and the sea (Kingdoms) (rows: md_figurehead, md_signs, ld_journey, ld_firsts)

**Andy (2026-11-19):** cross-referenced Medieval Music, Medieval Buildings, Fallen Adventurers, Medieval Deco, MmmMmmMmmMmm, Satisfyu's Let's Do mods and Global Wind; picked 41 of 49 rows on https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv (db `picks/andy`), then chose "Send to the PC reviewer". He OK'd hosting the trimmed music zip as a GitHub release asset.

**Full plan:** `docs/MedievalMusicDeco-Plan.md` on branch `claude/project-thread-ttl8xk` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/medieval-deco-6`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the ARR mods (our own code, names, art); Global Wind's MIT code may be ported with its notice; the music is CC BY (credit Nakarada and Sarisan).

## Picked
- **md_figurehead**: a figurehead block in each wood (our own carvings); realm ships wear their realm's (navy,
  merchant, whaler), pirates a carving of their own choosing. Kingdoms' ship builders place them.
- **md_signs**: a hanging trade sign with a mark (anvil, loaf, mug, needle, scales...) set over each workplace by the
  town builders.
- **ld_journey**: a journey book (a private book like the Lore Codex): your firsts with the day each happened, sorted
  by theme. No reward and no toast.
- **ld_firsts**: a stone in each capital where firsts are cut (first to slay each great foe, found a realm, reach a far
  world); bards and villagers mention them in talk.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: the `(new)` paragraphs for the song stones and figureheads are in Lore.md, quoted to Andy 2026-11-19 and **awaiting his OK**. Don't merge before he approves; if a paragraph changes, quote the new full text to him in the thread.
