---
status: done
for: claude-cloud
from: coordinator
asked: 2026-11-24
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR
claimed_by: claude-cloud
pr: 2097
suggested_owns: []
---
# Reference Mods Patch 15: Provenance, coins and goods

Andy ticked these rows on the Reference Mods pick list (https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg, db picks/andy).
Full plan: Picklists/ReferenceMods-Plan.md, section "## Patch 15". Other mods' code and art are never copied
(ARR = remake ourselves). Any structure states where it generates and why (structure placement plan,
https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Change note per mod under changes/<mod>/<branch>.md.

## Patch 15: Provenance, coins and goods
Owns: powers item component, kingdoms economy.

- **G1 Every item remembers where it came from** (Chest DNA, size M). Port it (Apache 2.0): the same stamp on every item, shown as a small line on the tooltip with more on Shift.
- **G2 Stolen goods are known** (Chest DNA, size M). With the stamp, a merchant or guard knows an item taken from their realm's chests or homes. Sell it to them and you're caught; fences in speakeasies clean the stamp for a cut. We have: Pickpocketing, speakeasies, arrests.
- **G3 Heirlooms** (Chest DNA, size S). Gear made or carried by your forebears keeps their name, and it passes to the next life in your line. We have: Lineage, old age, reincarnate.
- **G4 Famous finds fetch more** (Chest DNA, size S). Collectors pay extra for things from dragon hoards, sunken ships and old cities.
- **G5 A coin purse** (Numismatic Overhaul, size S). Culture coins sit in a belt purse instead of your slots. We have: Culture coins, toolbelt.
- **G6 Your own shop stall** (Numismatic Overhaul, size M). A stall you set prices on; villagers and players buy from it while you're gone. We have: Shifting Wares, villager trades.
- **G7 A strongbox at home** (Numismatic Overhaul, size S). A locked coin box; thieves go for it. We have: Inbox: realm-banks-finish.
- **G8 Transmutation, the alchemist's last art** (Equivalent Exchange (FTB), size L). No duplicating: at the top of Alchemy, transmute one material into another of equal worth at a heavy cost, with a philosopher's stone as the goal of a long quest. We have: Your call: it cuts across the economy and progression gates.

## Notes
- 2026-11-24 18:01 2026-11-24 Andy: "finish here": cloud Claude finishes this in thread cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR; bots leave it.
- 2026-11-24 19:26 Andy 2026-11-24: strongbox (G7) yes, shop stall (G6) no (no shop block ruling stands).
- 2026-11-24 19:49 PR #2097: G1-G4 and G7 built (compiles; not played). G6 stall not built (Andy), G5 already in, G8 not asked. Lore 'What goods remember' (new) added.
- 2026-11-24 20:15 PR #2097 is merged into master; moved to Inbox/Done.
