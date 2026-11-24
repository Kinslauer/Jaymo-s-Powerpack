---
status: done
task: safe-spawns-hint-links
branch: claude/safe-spawns-hint-links-5754ea
with: Claude (PC desktop, side session)
pr: 2028
mods: powers, kingdoms
versions:
started: 2026-11-23
owns:
  - src/main/java/com/jaymospowers/core/wounds/SpawnCalm.java
  - src/main/java/com/jaymospowers/mixin/SpawnCalmMixin.java
  - src/client/java/com/jaymospowers/client/menu/MenuTabs.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/HintLinks.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/tweaks/HintLinkOverlayMixin.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/SpiderKeep.java
---
# Hint deep-links and safe starts

**Task:** Andy: H/K after a hint goes to exactly what it named; nobody spawns under attack (no enemy to mobs until they move); in the lair, mobs leave you be until you escape.

## Plan
- Kingdoms HintLinks remembers the last hint naming a place (action-bar line, key + args); K aims SkillScreen (web perk or class-tree node), H lands on Feats and Paths / Rewards via Powers' MenuTabs.landing.
- Powers SpawnCalm: Player.canBeSeenAsEnemy false until movement input (not while downed/webbed/carried); from Mortality.wakeAt, respawn, Kingdoms LifeStory.arrive.
- SpiderKeep: kept by a lair = every monster spares you, until 32 blocks out or you strike one.

## Done
- PR #2028 merged 2026-11-23 (squash 7249afe). All 5 CI checks passed (build included); not played.
