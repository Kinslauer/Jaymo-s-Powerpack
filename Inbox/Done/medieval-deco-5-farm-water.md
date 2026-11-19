---
status: done
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: pc-claude-deco-5
pr: 1799
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

## Notes
- 2026-11-18 22:06 PC Claude took it (editor agent, branch claude/medieval-deco-5).
- 2026-11-18 23:49 pc-claude-deco-5's claim had no sign of work for 30 min: handed back as open.
- 2026-11-19 02:09 Review (head 82f4d1f): changes. 1) md_fish not finished: Andy's pick says fishers' huts and market stalls get fish barrels; none are placed. Have Kingdoms' hut and stall builders place one (through a compat check that Kitchen is loaded, block by id). Rest passes: water barrel, fish barrel keep, trough (sheep graze less when fed), watering can, fish trap with per-chunk stock that runs down and recovers, scarecrow; both mixin targets checked against 26.3 (Rabbit RaidGardenGoal.isValidTarget at Rabbit.java:654, EatBlockGoal.canUse). Flotsam: ripple spots and odd catches; the remains' token waits for patch 3. Follow-up after #1797 and this merge: patch 4's vine birds should check Scarecrows.guarded (one line in GrapeVineBlock.randomTick). Going back to the PC Claude agent that built it.
- 2026-11-19 02:18 Review (head 4dda4c1): pass. Water barrel (rain refills, clean drink), fish barrel that keeps fish longer and now stands in fisheries and about 3 in 10 market stalls, feeding trough (pen animals come, eat, breed; fed sheep graze less), watering can, baited fish trap drawing on a per-stretch fish stock that recovers, scarecrow with a pumpkin keeps rabbits off crops; drifting ripple spots on water give odd catches (purse, bottle, worn gear). Not tested in game. Stalls in new chunks roll differently than before (RNG order). Follow-ups: vine birds should check Scarecrows.guarded once #1797 is in; remains' token catch waits for patch 3.
- 2026-11-19 02:18 Reviewed; Andy allowed sending it to the coordinator.
- 2026-11-19 03:10 PR #1799 is merged into master; moved to Inbox/Done.
