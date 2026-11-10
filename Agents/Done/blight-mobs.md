---
status: done
task: Blight mobs: named elites in the world with boosted stats and visible tell
branch: claude/blight-mobs
with:
pr: 1166
mods: powers
versions:
started: 2026-11-08
owns:
  - src/main/java/com/jaymospowers/core/mobs/BlightMobs.java
  - src/main/java/com/jaymospowers/mixin/BlightSpawnMixin.java
  - src/main/resources/data/jaymospowers/tags/entity_types/can_be_blighted.json
  - src/main/java/com/jaymospowers/JaymosPowers.java
  - src/main/resources/jaymospowers.mixins.json
---

# Blight Mobs

**Task:** Add elite "blight" variants of existing mobs that spawn rarely with boosted stats, a name, and a visible tell (tint/mark), scaling with local difficulty.

## Plan
- Scan existing mob registry and spawning code in Powers
- Design blight mob attachment/data structure (name, tier, stat multipliers)
- Add spawn condition hook (rare chance on natural spawn, scales with local difficulty)
- Add visual tell (entity tint, custom name, particle effect)
- Apply stat boosts (health, damage, speed) via attributes on spawn
- Ensure drop table modifications (better loot, rare drops)
- Test compilation and basic spawn logic

## Notes
- Reuse existing mobs (pack's own monsters first). No new entities unless Andy asks.
- Worth saying in PR: drop table, whether rarer at night, whether visible through walls (no).
- Our own take: a name, a tint or a mark, and stats that scale with local difficulty we already track.

## Done
Player sees rare blighted elites with custom names (e.g. "Blighted Zombie the Tainted"), green plague particles, dark smoke for higher tiers, and boosted health/damage/speed/armor/follow-range. Three tiers scale with local difficulty.

Not tested in game: spawn rates and balance at different difficulties, particle visibility and performance with many blighted mobs, drop table modifications (not yet implemented), multiplayer sync of custom names and particles, interaction with other mods' custom mobs.

Compiles; not tested in game yet. PR #1166 open for review.
- 2026-11-10 16:18 PR #1166 is merged into master; moved to Agents/Done.
