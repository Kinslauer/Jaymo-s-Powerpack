---
status: done
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: pc-claude-deco-6
pr: 1803
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

## Notes
- 2026-11-19 02:41 Andy approved the lore paragraphs 2026-11-19 02:40 UTC; no lore hold on merging.
- 2026-11-18 22:26 PC Claude took it (editor agent, branch claude/medieval-deco-6).
- 2026-11-18 23:49 pc-claude-deco-6's claim had no sign of work for 30 min: handed back as open.
- 2026-11-19 02:33 PR 1803, Kingdoms build green. Players see: figureheads at ship prows (lion/gull/stag for realms, whale on whaleships, serpent/maiden on pirates; craftable in 10 woods), a marked trade board over workplace doors, a paved song-stone square in capitals (use the stone to read who was first; a villager names a name), and a journey book (book + compass) of your own firsts by day. Untested in game: ship prow placement and orientation, door-facing assumption for signs, art, CI only. Bards untouched (villager talk only). The reading screens reuse the Lore Codex header. Needs Andy: nothing.
- 2026-11-19 02:34 Review (head 2c7f789): changes. 1) The journey book and the song stone open LoreCodexScreen, whose header always says 'Lore Codex': Firsts.send (kingdoms/.../Firsts.java) passes its title only into an empty placeholder book, and LoreCodex.Open (LoreCodex.java:79) has no title. Add a title to Open (default: the codex's) and draw it in LoreCodexScreen, so the journey book reads as the journey book and the stone as the stone. Rest passes: figureheads per wood and realm on laid ships, trade signs over workplace doors (CTOV and Dungeons and Taverns too), song stone square in capitals, firsts recorded for foes, realms and far worlds, villager remark. Note: signs replace the block above a door. Going back to the PC Claude agent.
- 2026-11-19 02:43 Review fix: LoreCodex.Open now carries a title (codec writes it; codex callers keep their own title via a one-arg constructor), LoreCodexScreen draws it, Firsts.send passes the journey or stone title. Head d47bccd82, CI green.
- 2026-11-19 02:44 Review (head d47bccd): pass. Ships wear a figurehead in their own wood (lion, gull or stag by realm, whale for whalers, serpent or maiden for pirates); workplaces get a trade sign over the door; each capital has a song stone square that records the world's firsts (great foes slain, realms founded, far worlds reached) and villagers there mention them; a private journey book lists your own firsts by day, now under its own title (LoreCodex.Open carries a title; write and read order match). Not tested in game: figurehead facing on ships, sign placement (it replaces the block above a door), the art.
- 2026-11-19 02:45 Reviewed; Andy allowed sending it to the coordinator.
- 2026-11-19 03:10 PR #1803 is merged into master; moved to Inbox/Done.
