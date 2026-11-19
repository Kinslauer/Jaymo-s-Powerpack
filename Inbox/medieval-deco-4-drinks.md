---
status: claimed
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: pc-claude-deco-4
pr: 1797
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Medieval music/deco patch 4: Drinks and kitchen (Kitchen) (rows: ld_vines, ld_press, ld_vintage, ld_teas, ld_smoke, ld_nether)

**Andy (2026-11-19):** cross-referenced Medieval Music, Medieval Buildings, Fallen Adventurers, Medieval Deco, MmmMmmMmmMmm, Satisfyu's Let's Do mods and Global Wind; picked 41 of 49 rows on https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv (db `picks/andy`), then chose "Send to the PC reviewer". He OK'd hosting the trimmed music zip as a GitHub release asset.

**Full plan:** `docs/MedievalMusicDeco-Plan.md` on branch `claude/project-thread-ttl8xk` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/medieval-deco-4`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the ARR mods (our own code, names, art); Global Wind's MIT code may be ported with its notice; the music is CC BY (credit Nakarada and Sarisan).

## Picked
- **ld_vines**: a grape vine crop on a trellis block in warm lands (wild vines generate on the edges of plains,
  savanna and flower forests). Sympathy: vines fruit on last year's wood, so a vine pruned in winter (shears, once a
  season) bears well, an unpruned one bears less each year. Feedback: ripe bunches left out draw birds that strip them.
  Stewardship: pruned, netted vines give the most. Our grapes feed the existing `GRAPE_WINE` brew alongside Pantry's.
- **ld_press**: a treading tub (jump in it with grapes) and an apple press; their juice goes into `CaskBlock`.
- **ld_vintage**: wine bottled from a cask carries its year; in a cool cellar (`Freshness#cellar`) it improves each
  in-game year up to five, and lords and merchants pay more for older vintages (Kitchen's `KingdomsMoney`).
- **ld_teas**: tea bushes in a few lands (jungle edges, cherry groves, misty hills). Leaves dry on the
  `DryingRackBlock` in sun; the tea kettle brews them. A pot set on a table helps everyone seated there (with
  Kingdoms: `Cushions` seats) with a short calm and warmth.
- **ld_smoke**: the smoker minigame (`SmokerView`) takes a wood chip: oak, birch, cherry, apple (from Pantry) or
  dark oak, each giving a different keep time and fill to smoked meat and fish.
- **ld_nether**: crimson and warped vines on lava-sea shores in the Nether; their dark wine is prized by the demon
  folk (Kingdoms trades) and sells high above.
- Check: JEI shows every route (trellis, press, tub, wood chips, nether wine); a pruned vine out-yields an unpruned one.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: the `(new)` paragraphs for the vines by the lava seas are in Lore.md, quoted to Andy 2026-11-19 and **awaiting his OK**. Don't merge before he approves; if a paragraph changes, quote the new full text to him in the thread.

## Notes
- 2026-11-19 02:41 Andy approved the lore paragraphs 2026-11-19 02:40 UTC; no lore hold on merging.
- 2026-11-18 21:50 PC Claude took it (editor agent, branch claude/medieval-deco-4), paired with patch 1.
- 2026-11-18 22:21 PR 1797, CI green, not played. Untested in game: worldgen placement, block rendering, tub treading, tea pot effects, selling to villagers/Kingdoms folk. Textures are generated placeholders. Apple chips are crafted from oak log + apple (not Pantry). Wine ages inside the cask in a cellar, not as loose bottles.
- 2026-11-19 02:06 Review (head eeee1a0): changes. 1) kitchen/.../vintner/GrapeVineBlock.java:156-158 with :187-190 and :203-211: a picking drops the vine to AGE 1, clears PRUNED and adds NEGLECT; regrowth (1/12 random ticks per step, 2->3 outside spring) lets a vine ripen about 15 times per summer. So yield falls per picking, not per year, and winter pruning only helps the first picking. Spec: fruits on last year's wood, one crop a year; pruned in winter bears well; unpruned bears less each year. Fix: one crop per vine per year (after picking it stays bare until it has passed a winter); NEGLECT goes up once per year it wasn't pruned; PRUNED holds for that year's crop. Verified by a second agent. Not blocking: every texture is a generated placeholder (the plan wants our own vanilla-style art). The rest reads fine: press, tub, cask vintage stamp and cellar ageing, tea, chips, black wine, JEI. Going back to the PC Claude agent that built it.
