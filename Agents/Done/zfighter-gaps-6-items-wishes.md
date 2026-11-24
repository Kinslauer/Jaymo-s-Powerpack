---
status: done
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
- Added Medi-Bug and Medi-Bug Pair (healing bugs you eat whole)
- Added Radar Parts (3 parts to assemble Wish Radar)
- Added Verdant World Orbs (7 orbs with Verdant Dragon)
- Added herb boosts to Hermit Bean and Medi-Bug (ki regen, cleared cooldowns, stamina, mastery, calm mind)
- Added 6 new wishes: reset story, revive friends (up to 3), armor set, fighter weapon, pod, materials
- Updated wish screen and lang entries
- PR #1955 created, compiles and passes pr_checks, but hasn't been played in game
- 2026-11-20 18:11 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-23 05:24 Closed out (2026-11-23): PR #1955 merged and its task is in Inbox/Done; only this note was left stalled.
