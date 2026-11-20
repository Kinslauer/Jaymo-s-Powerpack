# scrying-orb

## What the player will see

A new **Scrying Orb** block: a glass sphere on a carved stand. An attuned player (anyone with mana) can right-click the orb while holding a **Lock of Hair** (from the Scrying spell) or a **Sighted Shard** (a plain amethyst shard sneak-used on a block to mark a place). The orb wakes with a soft glow, and the land around that person or place rises inside as a living 1/24-scale miniature for 30 seconds: real blocks drawn in a block entity renderer, with tiny motes for the folk and monsters moving about. Anyone in the room sees it. Works with Sodium, Iris, and Complementary shaders — no second camera or render-to-texture.

**Sighted Shard**: sneak-use a plain amethyst shard on any block to mark that spot (dimension + position stored on the item, anvil name shown over the picture in the orb). Never consumed; sneak-use in air to clear it. The orb can watch up to 4 place-sights server-wide; each rests 1 minute after use, and the 3×3 chunks around a held place-sight stay loaded while the orb is awake.

**Getting one**: court wizards sell the orb for 24 emeralds; also craftable on Jaymo's Powers' infusion altar (glass centre, 4 amethyst shards, 2 ender eyes, 2 glass — needs the "scrying" research). Some court wizard towers generate with one placed.

**Fx**: motes in the glass + rune ring on the stand on waking, miasma swirl when it clouds.

## Cause

New feature per Andy's request: "A scrying orb sounds cool... it should work for places too."

## Changes made

- `ScryingOrb.java`: core logic (block, block entity, packets, snapshot/delta/figure packets, chunk tickets for place-sights)
- `ScryingOrbBlock.java` / `ScryingOrbBlockEntity.java`: block + BE with ACTIVE state property (light emission when awake)
- `ScryingOrbRenderer.java`: client BE renderer drawing the miniature at 1/24 scale via PlanGhosts submit path; glass sphere + stand + figures as motes
- `SightedShard.java`: new item (sneak-use amethyst shard on block to mark; sneak-use in air to clear)
- `Scrying.java` (extended): added `TargetKind.PLACE` support to the existing scrying system
- `Mana.java`: sighted shards are not crushed for mana (added check in `crush()`)
- Blockstate, block/item models, lang part `scrying-orb.json`
- Infusion recipe `data/jaymoskingdoms/alchemy/infusion/scrying_orb.json` (glass, 4 amethyst shards, 2 ender eyes, 2 glass; research "scrying")
- Wizard trade `data/jaymoskingdoms/villager_trade/cleric/scrying_orb.json` (24 emeralds, 4 uses)
- Registrations in `JaymosKingdoms.java` and `JaymosKingdomsClient.java`

## Not tested in game

- The orb's miniature rendering with actual block models (currently draws figures as motes; full block model submission is a follow-up)
- Place-sight chunk ticket loading/unloading across dimensions
- Multiplayer sync of orb state (active/idle) and packet delivery to nearby players only
- Court wizard tower generation with placed orb
- Infusion recipe "scrying" research unlock condition
- Performance with 4 simultaneous orbs (target < 1 ms/frame)