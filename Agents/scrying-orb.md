---
status: running
task: scrying-orb
branch: claude/scrying-orb
with:
pr:
mods: kingdoms
versions:
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Scrying.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ScryingOrb.java
  - kingdoms/src/main/java/com/jaymoskingdoms/SightedShard.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ScryingOrbRenderer.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/scrying-orb.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/ (scrying_orb model, blockstate, item and texture files only)
  - changes/kingdoms/claude-scrying-orb.md
  - kingdoms/src/main/java/com/jaymoskingdoms/Mana.java (one line: sighted shards aren't crushed)
  - kingdoms/src/main/java/com/jaymoskingdoms/Professions.java (wizard's trade set only)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/FamiliarClient.java (one line: registers the orb renderer)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/fifth_p7.json (scrying spell description only)
  - kingdoms/src/main/resources/data/jaymoskingdoms/ (scrying_orb infusion, wizard trade_set, tag and villager_trade)
---
# scrying-orb

**Task:** [[Inbox/scrying-orb]]: a crystal orb on a stand; wake it with a lock of hair or a sighted amethyst shard and
the land round that person or place rises inside as a living 1/24 miniature for 30 s. Must work with Sodium, Iris and
Complementary (block models in a block entity renderer, no second camera).

## Plan
- Read Scrying, ScryScreen, Hair, Mana, PlanGhosts, Fx; build block + BE, SightedShard, place targets, chunk tickets,
  snapshot/delta/figure packets, renderer, FX, getting one.
- Build, pr_checks, independent verifier, push, set in-review.

## Notes
- Andy (Claude project thread, 2026-11-18) asked to port Immersive Portals for this; checked and it isn't portable
  (1.21.1 at newest, ~70k lines/181 mixins on render code 26.3 removed, pinned to one Sodium/Iris, half-works with
  shaders). Andy chose the planned miniature.
- Picked up by hermes-a 2026-11-18 19:32 EST: worktree E:/wt/scrying-orb ready, starting implementation.

## Done
- 2026-11-18 18:14 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-18 19:32 hermes-a claimed, set status running.
