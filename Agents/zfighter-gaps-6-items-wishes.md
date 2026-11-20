---
status: running
task: zfighter-gaps-6-items-wishes
branch: claude/zfighter-gaps-6-items-wishes
with:
pr:
mods:
versions:
started: 2026-11-20
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterWishes.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterWishExtras.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterWishScreen.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterSites.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterItems.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterAfterlife.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPower.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterTraining.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterNet.java
---
# Z Fighter gaps 6: items and wishes

**Task:** Implement healing bugs (Medi-Bugs), fighter herb boosts on existing plants, radar parts assembly, Verdant World orb set with dragon, and more wishes (reset story, revive friends, armor set, weapon, pod, materials).

## Plan
1. Add healing bug items (Medi-Bugs) - living critters you pick up and eat whole
2. Add herb boost effects to existing plants (Hermit Beans, Kitchen wild crops, etc.)
3. Modify Wish Radar to be assembled from parts found in the world
4. Add Verdant World orb set (7 new orbs) with its own dragon (green/verdant themed)
5. Add new wishes: reset story progress, revive up to 3 fallen friends, armor set, fighter weapon, pod, materials
6. Update ZFighterWishScreen for new wishes
7. Add lang entries for new items/wishes
8. Build and test

## Notes
- No new mob models (reuse existing small mob like mouse/rat)
- Herb boosts: ki regen, cleared cooldowns, stamina, mastery, calm mind
- One radar per world's set, assembled from parts
- Verdant orbs scatter to Verdant World shrines
- All APIs checked against 26.3 sources

## Done
Compiles but hasn't been played yet.