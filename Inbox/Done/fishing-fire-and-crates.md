---
status: done
for: pc-claude
from: claude-cloud (Fire and Wither picks thread)
asked: 2026-11-18
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BRMaCrFDy9EULEZbqakT9nF
claimed_by: pc-claude
pr: 1715
suggested_owns:
  - src/main/java/com/jaymospowers/fishing/ (new classes)
  - tools/gen_fishing_species.py
---
# Fishing: lava, crates, notes, tackle bench, cave fish

**Andy's words (2026-11-17 8:24 PM ET):** "done, send it to the vault for apollo" (after ticking the pick list).
Then (8:30 PM ET): "not for apollo anymore, ill give it straight to reviewer".

**Andy's picks (all 5 Tide rows):**
- **Lava fishing in the Ash** (td_lava): a heat-proof rod tier forged in Tinkers (iron rule) fishes Nether lava lakes; new species that behave like real hot-spring fish (keep to warm edges). Demons' villages fish the lakes.
- **Crates on the line** (td_crates): rare crate catches, loot by water (river, sea, swamp, lava).
- **Wet fishers' scraps** (td_notes): torn notes in crates/wrecks hinting where a rare fish bites; reading one adds the hint to the Fisher's Journal. Hints only, never stated lore.
- **A tackle bench** (td_bench): fit hooks (barbed keeps fish on), lines (braided takes more strain, weighted sinks fast), floats (colour only) to any rod. Hooks are forged.
- **Cave-pool fish** (td_cave): pale blind fish in underground lakes and lush caves (real cavefish/olm), only below a depth.

**Where to start:** `docs/Plan-fishing.md` (everything already built), Powers `fishing/` (FishBites, FishCatch, Tackle, Fishermen), client `JournalScreen`, `tools/gen_fishing_species.py`.

**Source:** Andy's picks from the Fire and Wither pick list (https://claude.ai/artifact/5xsQSkJSnP8WszLL6JpNtL, db `picks/andy`, saved 2026-11-18 01:23 UTC; file `/mnt/project-files/fire-wither-study/fire-wither-picklist.html`). Study thread: "Cross-reference Knight Armor, Tide, Wither Reincarnated, Inferno". Memory: `fire-wither-fit.md`.

**Licences:** Wither: Reincarnated, Knight Armor and Weapons, Asterion and Inferno are All Rights Reserved / proprietary: ideas only, our own code, names and vanilla-style art. Tide is MPL-2.0 but nothing of it is needed. Only Inferno ships as its own jar (see the Inferno task).

**Rules:** CLAUDE.md as always: one PR per task, change note per mod in `changes/`, no version bumps, check 26.3 APIs against genSources, tuning constants at the top of each class, typed MagicFx/Fx only, iron gear forged via Tinkers, no screen text stating lore. Lore paragraphs for this work are in Lore.md and approved by Andy (The Wither's coming, What fire leaves, Fishing the fire, The labyrinth-builders' craft).

## Notes
- 2026-11-17 8:28 PM ET: Andy approved all four lore paragraphs; they are canon now.
- 2026-11-17 21:08 PR #1715 has lava fishing, crates and cave fish. Wet fishers' torn notes (Journal hints) and the tackle bench are not built yet: they need new UI, so they go in a second PR.
- 2026-11-18 07:37 PR #1715 is merged into master; moved to Inbox/Done.
