---
status: done
for: pc-claude
from: claude-cloud (Fire and Wither picks thread)
asked: 2026-11-18
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BRMaCrFDy9EULEZbqakT9nF
claimed_by: pc-claude
pr: 1714
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/ (Labyrinths + new classes)
---
# Labyrinths: rune doors, finder, beetles, braziers, the Afterblow

**Andy's words (2026-11-17 8:24 PM ET):** "done, send it to the vault for apollo" (after ticking the pick list).
Then (8:30 PM ET): "not for apollo anymore, ill give it straight to reviewer".

**Andy's picks (6 Asterion rows):**
- **A bronze mechanism that finds the labyrinth** (as_compass): geared compass pointing at the nearest of our labyrinths; built (forged) from plates found in the Deep Mother's drowned colonnades.
- **Rune doors** (as_runes): inner gates that open only to the right rune tablets scattered through the labyrinth's halls; the minotaur's hall behind the last gate. Our own tablet names in the old peoples' tongue (not Greek letters).
- **Bombardier beetles** (as_bombardier): Andy: "it can use our existing beetle remodeled". A wild warm-lands beetle that sprays a boiling burst when threatened, like the real one; its glands fuel a short-range flamethrower (Armory). Reuse/remodel the existing beetle model (Kingdoms `wild/CrystalBeetle` or `inhabitants/BarrowBeetle`, pick the closer shape and say which).
- **A beetle colony and its queen** (as_queen): a great tree holding a colony (workers, pupae, a queen); it feeds on the wood round it and spreads if left, scatters if the queen dies (living-things rules: sympathy, feedback, stewardship).
- **Living braziers** (as_brazier): a brazier mimic in the labyrinths that wakes and spits fire (like the sentry crate's Mimic).
- **The Afterblow** (as_afterblow): relic sword on a pedestal behind the last rune gate; every hit strikes again a moment later.

**Where to start:** Kingdoms `beasts/Labyrinths`, `beasts/Minotaur`, `GreatLairs`, `wild/CrystalBeetle`, Powers `aether/dungeon/Mimic`, `armory/`. Labyrinths are Epic Structures' dungeons (tag `Labyrinths.ALWAYS`); don't edit the structures themselves, place things when a player first comes near, as the minotaur is.

**Source:** Andy's picks from the Fire and Wither pick list (https://claude.ai/artifact/5xsQSkJSnP8WszLL6JpNtL, db `picks/andy`, saved 2026-11-18 01:23 UTC; file `/mnt/project-files/fire-wither-study/fire-wither-picklist.html`). Study thread: "Cross-reference Knight Armor, Tide, Wither Reincarnated, Inferno". Memory: `fire-wither-fit.md`.

**Licences:** Wither: Reincarnated, Knight Armor and Weapons, Asterion and Inferno are All Rights Reserved / proprietary: ideas only, our own code, names and vanilla-style art. Tide is MPL-2.0 but nothing of it is needed. Only Inferno ships as its own jar (see the Inferno task).

**Rules:** CLAUDE.md as always: one PR per task, change note per mod in `changes/`, no version bumps, check 26.3 APIs against genSources, tuning constants at the top of each class, typed MagicFx/Fx only, iron gear forged via Tinkers, no screen text stating lore. Lore paragraphs for this work are in Lore.md and approved by Andy (The Wither's coming, What fire leaves, Fishing the fire, The labyrinth-builders' craft).

## Notes
- 2026-11-17 8:28 PM ET: Andy approved all four lore paragraphs; they are canon now.
- 2026-11-17 21:09 PR #1714: geared plates drop from any chest whose structure/loot table name contains 'colonnade'. Nothing in our code is named that (the Deep Mother's sunken colonnades aren't built yet?), so the Labyrinth Compass may be uncraftable until Andy says where the plates should come from.
- 2026-11-18 06:00 2026-11-18 01:00 AM ET Andy chose (decision card): compass plates come from vanilla ocean ruin and shipwreck chests (minecraft:chests/underwater_ruin_small, underwater_ruin_big, shipwreck_map, shipwreck_supply, shipwreck_treasure), not 'colonnade' chests. Please change #1714 to that before review passes.
- 2026-11-18 07:37 PR #1714 is merged into master; moved to Inbox/Done.
