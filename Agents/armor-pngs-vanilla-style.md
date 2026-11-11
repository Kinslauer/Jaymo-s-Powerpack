---
status: stalled
task: armor-pngs-vanilla-style
branch: claude/armor-pngs-vanilla-style
with: hermes-modeller
pr:
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
- 2026-11-11 19:42 No sign of work for 30 min: marked stalled (whoever ran it stopped).
