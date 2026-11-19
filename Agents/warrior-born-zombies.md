---
status: done
task: Rare Warrior-born zombies with rotted tails that turn ape under the moons; undead MCA Verdants turn giant more often
branch: claude/project-thread-248ttc
with: cloud thread
pr: 1725
mods: powers
versions:
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/power/zfighter/UndeadBlood.java
  - src/main/java/com/jaymospowers/power/zfighter/UndeadGiant.java
  - src/main/java/com/jaymospowers/power/zfighter/VillagerApe.java
  - src/client/java/com/jaymospowers/client/power/chrono/FutureDeadLooks.java
---
# Warrior-born zombies

**Task:** Andy: "there should be rare warrior born zombies with the same decrepit rotting tails, that have a chance to
turn during the moons. Undead MCA Verdant should have a similar but higher chance to turn giant".

## Plan
- UndeadBlood: 3% of Overworld zombies/husks/zombie villagers roll Warrior-born (Kingdoms' tags), 75% with a tail; MCA
  zombies keep a living Warrior-born's tags (vanilla conversion copies tags). Synced TAIL attachment for clients.
- VillagerApe turns them like villagers (10%), also on Kingdoms Blood Moons for the dead.
- FutureDeadLooks draws the future's mangled tails on them (stump when cut); dead apes greyed.
- UndeadGiant: undead MCA Verdants (heritage tag) 25% a moon night to swell to Titan size, keep zombie AI.

## Notes
- Lore paragraph "The Warrior-born dead" (new) added to Lore.md, awaiting Andy.

## Done
- PR #1725 merged 2026-11-18 (CI green); lore paragraph approved by Andy the same day. Compiled, not played.
