---
status: in-review
task: armor-pngs-vanilla-style
branch: claude/armor-pngs-vanilla-style
with: hermes-modeller
pr: 1313
mods: powers
versions: Powers 0.22.11
started: 2026-11-11
owns:
  - src/main/resources/assets/jaymospowers/textures/item/armor/bog_iron_helmet.png
  - src/main/resources/assets/jaymospowers/textures/item/armor/bog_iron_chestplate.png
  - src/main/resources/assets/jaymospowers/textures/item/armor/bog_iron_leggings.png
  - src/main/resources/assets/jaymospowers/textures/item/armor/bog_iron_boots.png
  - src/main/resources/assets/jaymospowers/textures/item/armory/caveman_helmet.png
  - src/main/resources/assets/jaymospowers/textures/item/armory/caveman_chestplate.png
  - src/main/resources/assets/jaymospowers/textures/item/armory/caveman_leggings.png
  - src/main/resources/assets/jaymospowers/textures/item/armory/caveman_boots.png
---
# armor-pngs-vanilla-style

Remake the mod's armor textures in pure vanilla Minecraft style — solid 1-pixel outlines, flat solid colors per section, crisp pixel art, no gradients. The task text names tiered leather/iron/gold/diamond/netherite armors under textures/item/armory/, but the mod registers only two player armor sets: bog_iron (4 pieces, textures/item/armor/) and caveman (4 pieces, textures/item/armory/), all 16x16 item icons. Remaking those 8 existing PNGs only; no new items, no model JSON changes.

## Plan
- Draw 8 clean 16x16 armor item-icon PNGs (bog_iron iron-gray, caveman hide-brown) with solid outlines and flat fills
- Verify each with vision_analyze
- sh ./gradlew build + tools/pr_checks.py
- changes/powers/armor-pngs-vanilla-style.md, commit, push, open PR, set vault task status=in-review

## Notes
- Task claimed via vault.py --claim hermes-modeller (tiv, Tasks thread, 2026-11-08); claim printed at 2026-11-11 13:40 EST.
- Worn equipment textures (textures/entity/equipment/humanoid/bog_iron.png etc., 64x32) left untouched — out of the task's item-texture scope.
- No bbmodel: flat item textures, verified with vision_analyze instead of bbrender.

## Done
- Remade 8 armor item-icon PNGs in vanilla 16x16 style: bog_iron ×4 (textures/item/armor/) and caveman ×4 (textures/item/armory/), solid 1px outlines, flat fills, transparent backgrounds, no antialiasing (verified pixel-level).
- Added changes/powers/armor-pngs-vanilla-style.md.
- sh ./gradlew build: BUILD SUCCESSFUL. tools/pr_checks.py: passed (9 changed files against origin/master 6de81b6d6).
- Pushed claude/armor-pngs-vanilla-style, opened PR #1313.
- Task set to in-review via vault.py. Not played in game.
