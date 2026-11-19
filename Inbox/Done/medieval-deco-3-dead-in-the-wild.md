---
status: done
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: 
pr: 1802
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Medieval music/deco patch 3: The dead in the wild (Kingdoms worldgen + Kitchen bones) (rows: fa_remains, fa_gear, fa_surprise, fa_page, fa_chamber, fa_rites, fa_far, mb_traps, ld_rider)

**Andy (2026-11-19):** cross-referenced Medieval Music, Medieval Buildings, Fallen Adventurers, Medieval Deco, MmmMmmMmmMmm, Satisfyu's Let's Do mods and Global Wind; picked 41 of 49 rows on https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv (db `picks/andy`), then chose "Send to the PC reviewer". He OK'd hosting the trimmed music zip as a GitHub release asset.

**Full plan:** `docs/MedievalMusicDeco-Plan.md` on branch `claude/project-thread-ttl8xk` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/medieval-deco-3`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the ARR mods (our own code, names, art); Global Wind's MIT code may be ported with its notice; the music is CC BY (credit Nakarada and Sarisan).

## Picked
- **fa_remains**: a `RemainsBlock` (a skeleton lying in one of several poses, our own model from Kitchen's bone art)
  and a worldgen feature placing it, one kind per place: frozen in snow, vine-tangled in jungle, drowned in rivers,
  pinned under dripstone, fallen at cliff feet, sun-dried in desert and badlands, webbed in caves. Rare: about one per
  six chunks in the wild, none inside settlements. Breaking it with an empty hand searches it; silk touch takes it.
- **fa_gear**: a loot table per kind: a miner's pick and ore, a hunter's bow and arrows, a sailor's rope; armour comes
  worn or broken (`ArmourWear`), tools as Tinkers parts where they fit.
- **fa_surprise**: about one in eight is a trap: a tripwire and an arrow from the ground, gravel overhead, a spider
  nest in the ribs (the medieval build's nests). At night a few rise as our old dead.
- **fa_page**: about one in five holds a written scrap (a book, 2 to 4 lines in the dead one's voice) that points at
  a real place nearby: a cave, a buried room, a monolith, a ruin. It joins the Lore Codex. Hints only, no lore stated.
- **fa_chamber**: rare sealed rooms in stone and deepslate (2 templates, built in code like our other buildings):
  a party's last camp, three to five remains around a dead fire.
- **fa_rites**: bury a body with `Graves.bury`, or have a priest give it rites (`DeathRites`). Some carry a token (a
  ring, a carved spoon) naming their home village; bring it there and their kin thank you (a gift and a little
  standing). Leaving them costs nothing.
- **fa_far**: Aether kinds under the islands' edges (let fall by the cloud), Nether kinds in soul sand valleys, End
  kinds on the main island.
- **mb_traps**: a trap pass over ruined towers and forts (ours and the Grim, ATi and Structory ones): an arrow slit on
  a pressure plate, a weak floor over a pit, a door that bars behind you. Never in a building a settlement has cleared
  (`Clearing`).
- **ld_rider**: the headless rider, an undead knight on an undead horse (`BreedHorseModel` skeleton coat), out on
  foggy nights on `Roads` that pass The Graveyard's sites and our graveyards. It chases and tries to behead
  (downed-only beheading still holds for players). It drops its own head; carried to its grave by day, it lays the
  rider down for that road for good, with a reward. A boss bar while it rides.
- Check: `/locate` style test command for op only, each kind generates in its biome, a page points at a real place.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: the `(new)` paragraphs for the gone-before and the headless rider are in Lore.md, quoted to Andy 2026-11-19 and **awaiting his OK**. Don't merge before he approves; if a paragraph changes, quote the new full text to him in the thread.

## Notes
- 2026-11-19 02:40 Andy approved the lore paragraphs 2026-11-19 02:40 UTC; no lore hold on merging.
- 2026-11-18 22:15 PC Claude took it (editor agent, branch claude/medieval-deco-3).
- 2026-11-18 23:49 pc-claude-deco-3's claim had no sign of work for 30 min: handed back as open.
- 2026-11-19 02:24 PR #1802, CI green. Player sees: bones in the wild by place (empty-hand break searches: gear, scrap, token, traps; shovel buries), sealed last camps, ruin traps, headless rider on mist mornings. Not tested in game: placement rates, rider steering its horse, trap placement in real structures, Aether-namespace placement. Kitchen unchanged. Remains.token(level, near, random) is public for patch 5.
- 2026-11-19 02:25 Review (head 5e52b64): pass. Remains lie in the wild (one look per place, about one per six chunks, none in settlements): search them for worn gear, sometimes a trap, a written scrap pointing at a real nearby place (into the Lore Codex), or a ring or spoon naming a village whose folk thank you; bury them or give them rites. Two sealed last-camp rooms; ruined towers and forts get arrow slits, rotten floors and doors that bar behind you; the headless rider rides misty roads by graveyards and is laid to rest with his head at the grave. Not tested in game. Minor: a rider still out at a restart or chunk unload stays as a plain armoured zombie on a skeleton horse (no head drop, despawns normally); burying river remains replaces the water source. Remains.token() is ready for patch 5's flotsam catch (follow-up). Kitchen untouched: own bone art via kingdoms/tools/gen_remains.py.
- 2026-11-19 02:25 Reviewed; Andy allowed sending it to the coordinator.
- 2026-11-19 09:04 PR #1802 is merged into master; moved to Inbox/Done.
