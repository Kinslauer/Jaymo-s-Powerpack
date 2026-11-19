---
status: done
for: claude
from: claude-cloud-metal-sparks-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B7zXfQVaZi44cKbjy3zewvc
claimed_by: pc-claude (hns-3 track)
pr: 1815
---
# Hackers 'n Slashers patch 3: edges and sparks (Powers + Kingdoms)

**Andy (2026-11-19):** linked Hackers 'n Slashers ("I know this is incompatible but we should have similar features, especially the sparks upon clashing blades or otherwise hitting metal"), ticked 14 rows on https://claude.ai/artifact/BFgKwnWSPGmCR8xp2xkrLx (db `picks/andy`, row notes inside), then said "send them to PC claude to build".

**Full plan:** `docs/HackersSlashers-Plan.md` on branch `claude/project-thread-oh3p97` (PR #1809, which also holds the metal-hit sparks, `combat/MetalHits`). Hackers 'n Slashers is All Rights Reserved: ideas only, our own code, names, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning constants at the top of each class; compile-check; say in the PR it has not been played.

PR #1809 (`combat/MetalHits`) is merged (2026-11-19), so branch off master.

**What's wanted** (plan "Patch 3"): steel on stone throws a few dull sparks (hs_stone); blades keep an edge (hs_dull plus Andy's note "yeah blades should be kept sharp, and you should see the blacksmith sharpening it's guards blades"): hits on metal and stone dull a blade most, flesh a little, a dull blade does less damage, and you sharpen at a grindstone (with sparks). In Kingdoms, town weaponsmiths and armourers are seen at a grindstone sharpening blades, and the guards' edges are kept by them. Sparks from a big shower can light dry grass, hay, cobweb or a gunpowder trail, but never in rain (hs_kindle). Plate rings, mail rasps and leather thuds when struck (hs_armour_ring: our own synthesised sounds, picked by the `plate_armor`/`mail_armor` tags). Lore: place the edge-keeping in Lore.md as a `(new)` paragraph (the Anvil-Father's smiths fit) and quote it in full when asking Andy.

**Where to start:** `combat/MetalHits`, `combat/ClashFx`, `combat/BladeSounds` + `tools/gen_blade_sounds.py`, Kingdoms smith/job code (`Careers`, the town work spots), Lore.md (Anvil-Father lines ~90 and ~301).

## Notes
- PR #1815, CI green (Build Powers, Kingdoms, Kitchen, checks). Lore paragraph "An edge is kept" (new) added to Lore.md for Andy to approve. Skipped: whetstone item, flint ore (none in the game).
- 2026-11-19 12:30 PR #1815 is merged into master; moved to Inbox/Done.
