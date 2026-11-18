---
status: in-review
for: pc-claude
from: claude-cloud (Fire and Wither picks thread)
asked: 2026-11-18
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BRMaCrFDy9EULEZbqakT9nF
claimed_by: pc-claude
pr: 1713
suggested_owns:
  - src/main/java/com/jaymospowers/combat/
  - src/main/java/com/jaymospowers/armory/ArmoryHits.java
  - src/main/java/com/jaymospowers/tinkering/ranged/Ranged.java
---
# Weapons: hooks, mace stagger, thrown axes break

**Andy's words (2026-11-17 8:24 PM ET):** "done, send it to the vault for apollo" (after ticking the pick list).
Then (8:30 PM ET): "not for apollo anymore, ill give it straight to reviewer".

**Andy's picks (4 Knight Armor rows):**
- **Hooks pull climbers off walls** (ka_wall): a scythe, bill or gutting hook drags a wall-climbing foe down (everyone climbs in this pack).
- **Hook round a shield** (ka_shield): scythes reach round a raised shield, half the hit gets through (combat/Shields + Blows).
- **Mace blows stagger** (ka_slow): each mace hit slows the target briefly (ArmoryHits, beside its stun chance).
- **Thrown axes can break** (ka_axe): a thrown hatchet may break on a hit; three nuggets at an anvil mend it (Ranged throwing).

**Andy's question, not picked as a row:** on "Knight Armor as it is" he wrote "cant we change the recipe?". Yes: a datapack in the pack can replace its grid recipes with Tinkers forging, which would satisfy the iron rule; it's ARR but has a 26.3 Fabric build (1.1.1) so it could ship unchanged. Its armour still doubles our knight's harness. Ask Andy (task Notes) whether he wants it shipped that way; don't add it unasked.

**Where to start:** Powers `combat/WeaponKind`, `combat/Blows`, `combat/Shields`, `armory/ArmoryHits`, `tinkering/ranged/Ranged`.

**Source:** Andy's picks from the Fire and Wither pick list (https://claude.ai/artifact/5xsQSkJSnP8WszLL6JpNtL, db `picks/andy`, saved 2026-11-18 01:23 UTC; file `/mnt/project-files/fire-wither-study/fire-wither-picklist.html`). Study thread: "Cross-reference Knight Armor, Tide, Wither Reincarnated, Inferno". Memory: `fire-wither-fit.md`.

**Licences:** Wither: Reincarnated, Knight Armor and Weapons, Asterion and Inferno are All Rights Reserved / proprietary: ideas only, our own code, names and vanilla-style art. Tide is MPL-2.0 but nothing of it is needed. Only Inferno ships as its own jar (see the Inferno task).

**Rules:** CLAUDE.md as always: one PR per task, change note per mod in `changes/`, no version bumps, check 26.3 APIs against genSources, tuning constants at the top of each class, typed MagicFx/Fx only, iron gear forged via Tinkers, no screen text stating lore. Lore paragraphs for this work are in Lore.md and approved by Andy (The Wither's coming, What fire leaves, Fishing the fire, The labyrinth-builders' craft).

## Notes
- 2026-11-17 8:28 PM ET: Andy approved all four lore paragraphs; they are canon now.
- 2026-11-17 20:44 Question for Andy: do you want Knight Armor's recipe changed by a datapack that swaps its grid recipes for Tinkers forging (it has a 26.3 Fabric build, 1.1.1, but is all rights reserved and its armour still doubles our knight's harness)? Not added, waiting on you.
