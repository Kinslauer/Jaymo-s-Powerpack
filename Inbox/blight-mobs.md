---
status: in-review
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-a
pr: 1166
suggested_owns: []
writer: hermes-writer
text_pass: 
---

# Blight mobs: named elites in the world

Monsters: now and then a mob spawns as an elite with boosted stats, and a visible tell so the player can choose the fight.

## What's wanted

From the pick-list, row **Blight mobs** (Simple Difficulty Reforged, borrowed from Scaling Health): "version of mobs that have increased stats and are very dangerous."

Andy ticked it. For us it belongs in the monster roster, not as a new system.

## Notes

- Our own take: a name, a tint or a mark, and stats that scale with the local difficulty we already track — not a blanket buff.
- Reuse existing mobs (the pack's own monsters first). No new entities for this unless Andy asks.
- Worth saying in the PR: drop table, whether they are rarer at night, and whether they can be seen through walls (no).
- 2026-11-08 22:50 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-08 22:52 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-09 00:35 Review (head 93f6387): changes needed. 1) src/main/resources/jaymospowers.mixins.json deletes two live mixins, PerchRidingMixin and BaublesQuickEquipMixin (both classes are still in mixin/): put them back and only add BlightSpawnMixin, at the file's normal indentation. 2) No change note: add changes/powers/claude-blight-mobs.md (the Change notes check is red). 3) data/jaymospowers/tags/entity_types/can_be_blighted.json:6 names jaymospowers:swet, which doesn't exist (the ids are blue_swet etc.); one bad id fails the whole tag, so no mob would ever be blighted. Also drop the duplicate minecraft:warden. 4) BlightMobs.java spawned(): the server-wide 'A ... has awakened...' chat broadcast on every blight spawn wasn't asked for and spams chat; remove it. 5) BlightMobs.setCustomName: a custom name makes the mob never despawn (vanilla keeps named mobs), so blighted mobs pile up; show the tell another way or clear persistence. Names are hard-coded English (Component.literal): use translatable keys in lang/parts/blight.json. 6) applyAttributes() runs on every ENTITY_LOAD and calls setHealth(getMaxHealth()), so a wounded elite fully heals each time its chunk reloads; heal only in makeBlighted. 7) IS_BLIGHTED is an entity-type tag that nothing uses and can't mark one mob: remove it. 8) The task asked the PR to say the drop table, whether they're rarer at night, and that they can't be seen through walls; the PR doesn't say any of these (and there's no extra drop). Also JaymosPowers.java:70-71 are indented four tabs instead of two.
- 2026-11-09 01:40 Coordinator merging: fixed review items 1-8 (mixins restored + BlightSpawnMixin/MobPersistenceAccess, change note, swet tag ids, no chat broadcast, translatable names, heal only on spawn, IS_BLIGHTED removed, PR body says drops/night/walls). Building.
