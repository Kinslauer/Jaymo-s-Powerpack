---
status: claimed
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: pc-claude-deco-5
pr: 
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Medieval music/deco patch 5: Farm, field and water (Kitchen + Powers fishing) (rows: md_water, md_fish, ld_trough, ld_watering, ld_debris, ld_traps, dm_scare)

**Andy (2026-11-19):** cross-referenced Medieval Music, Medieval Buildings, Fallen Adventurers, Medieval Deco, MmmMmmMmmMmm, Satisfyu's Let's Do mods and Global Wind; picked 41 of 49 rows on https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv (db `picks/andy`), then chose "Send to the PC reviewer". He OK'd hosting the trimmed music zip as a GitHub release asset.

**Full plan:** `docs/MedievalMusicDeco-Plan.md` on branch `claude/project-thread-ttl8xk` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/medieval-deco-5`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the ARR mods (our own code, names, art); Global Wind's MIT code may be ported with its notice; the music is CC BY (credit Nakarada and Sarisan).

## Picked
- **md_water**: a water barrel: fill bottles and buckets from it, rain refills it slowly, clean for `Thirst`.
- **md_fish**: a fish barrel: salted fish keep longer in it (`Freshness`) and the top shows the catch; fishers' huts
  and market stalls get them.
- **ld_trough**: a feeding trough (hay, grain): animals in its pen stay near and breed when fed; when it empties they
  wander to graze (Kitchen `Herds`). Feedback both ways: a fed herd crops the ground less.
- **ld_watering**: a watering can: water a crop by hand on a dry day for a small growth step; never needed.
- **ld_debris**: drifting flotsam and rippling spots on rivers and the sea; a cast into them (Powers `FishCast`) pulls
  an odd catch: a purse, a bottle, old gear, a remains' token (patch 3).
- **ld_traps**: a wicker fish trap, baited, fills over a day; each stretch of water has a stock that traps draw down
  and that recovers over days (feedback, stewardship). Tideborn see a trap in their havens as fishing (`TideFishing`).
- **dm_scare**: the scarecrow prop (and the Practice Dummy) with a carved pumpkin keeps crop-eating animals and the
  vine birds off crops within 8 blocks.
- Check: JEI routes; a trap in an empty stretch comes up near empty.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none needed; if one turns out to be, quote it in full to Andy in the thread for approval first.
