---
status: done
task: ore-hearts
branch: claude/ore-hearts
with: 
pr: 1210
mods: powers
versions: powers 0.21.80 (unbumped; release step bumps after merge)
started: 2026-11-09
owns:
  - src/main/java/com/jaymospowers/core/wounds/HeartCrystals.java
  - src/main/java/com/jaymospowers/core/wounds/Downed.java
  - src/main/java/com/jaymospowers/core/wounds/Mortality.java
  - src/main/java/com/jaymospowers/JaymosPowers.java
  - src/client/java/com/jaymospowers/client/wounds/HeartHud.java
  - src/client/java/com/jaymospowers/client/JaymosPowersClient.java
  - src/main/resources/assets/jaymospowers/items/heart_crystal.json
  - src/main/resources/assets/jaymospowers/items/heart_crystal_ore.json
  - src/main/resources/assets/jaymospowers/items/heart_crystal_ore_deepslate.json
  - src/main/resources/assets/jaymospowers/lang/parts/hearts.json
  - src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
  - src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
  - src/main/resources/data/jaymospowers/loot_table/blocks/heart_crystal_ore.json
  - src/main/resources/data/jaymospowers/loot_table/blocks/heart_crystal_ore_deepslate.json
  - src/main/resources/data/jaymospowers/worldgen/feature/ore_heart_crystal.json
  - src/main/resources/data/jaymospowers/worldgen/placed_feature/ore_heart_crystal.json
  - changes/powers/ore-hearts.md
---
# ore hearts - permanent half-hearts mined from deep deepslate

**Task:** Add rare heart crystal ore in deep deepslate dropping heart crystals; each crystal raises max health by one permanent half-heart (cap ten), shown as a faint extra run under the health bar; a found heart burns to turn one death aside (extra sure wake) and is lost otherwise; reincarnation clears them.

## Plan
- HeartCrystals: attachment, consumable item, ore blocks, worldgen + placed feature, loot tables, grant/burn/respawn/clear
- Hooks: Downed AFTER_RESPAWN re-applies extra health; Mortality.decide burns a heart via tryBurnHeartOnDeath (heart is the extra sure wake - single WOKEN increment)
- Tags: both ores in pickaxe + needs_iron_tool tags; item definitions; lang part hearts.json per the text review
- Client: HeartHud faint extra run under the health bar, registered in JaymosPowersClient
- Build: sh ./gradlew build passes; tools/pr_checks.py passes (17 changed files)
- PR #1210 opened against master; not tested in game

## Notes
- Models, textures, block models and blockstates for the ore and crystal came in with PR #1167 (art by hermes-modeller); nothing art-side added here.
- Stacks with the crystal_heart artifact (both raise max health) - flag in PR body; ask Andy if it reads as too much health.

## Done
PR #1210 opened: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/1210 — compiles, build green, not playtested. Set to in-review.
- 2026-11-09 22:14 PR #1210 is merged into master; moved to Agents/Done.
