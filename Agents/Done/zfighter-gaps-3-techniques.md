---
status: done
task: zfighter-gaps-3-techniques
branch: claude/zfighter-gaps-3-techniques
with:
pr: 1939
mods: powers
versions:
started: 2026-11-20
owns:
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterArts.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterKi.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterDisc.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterCreator.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterSlots.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrol.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterNpcKi.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterAndroid.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterDivineMoves.java
  - powers/src/main/java/com/jaymospowers/power/zfighter/ZFighterItems.java
---

# Z Fighter gaps 3: techniques

**Task:** Add 10 new Z Fighter techniques from the DragonMineZ inspiration: Ki shell (passive ki protection), Healing ki for anyone (heal shape in technique creator), Twin discs (double kienzan), Ring beam (spinning ring cut), Sword technique (blade flurry + ki burst), Purging beam (anti-corruption/dark damage, breaks possession), Sticky bind (rubber wad that sticks foes), Rift fist/step (dimensional punch/warp), Ki-drinking barrier (absorbs ki blasts to fill ki), Ki cell (tinkerer craft that restores ki).

## Plan
- Scan existing Z Fighter code structure to understand patterns
- Implement each technique following existing code patterns
- Add technique creator support for heal shapes
- Add NPC/patrol teaching for purging beam
- Add items for ki cell
- Build and test compilation

## Notes
- Aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java, ZFighterAuraFlame.java - keep edits minimal there
- No new original mobs - reuse player-model fighters, MCA villagers, existing mobs
- No slash commands for player features, no discovery chat text
- Abilities.md rows for every new input
- One PR, branch claude/zfighter-gaps-3-techniques

## Done
What a player will see, and what wasn't tested.
- 2026-11-20 12:36 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-24 16:02 PR #1939 is merged into master; moved to Agents/Done.
