---
status: running
task: Blight mobs: named elites in the world with boosted stats and visible tell
branch: claude/blight-mobs
with:
pr:
mods: powers
versions:
started: 2026-11-08
owns:
  - src/main/java/com/jaymospowers/core/mobs/ (new package for blight mob logic)
  - src/main/resources/data/jaymospowers/ (blight mob data/tags)
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
What a player will see, and what wasn't tested.