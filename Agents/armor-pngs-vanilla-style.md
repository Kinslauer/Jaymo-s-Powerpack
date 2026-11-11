---
status: stalled
task: armor-pngs-vanilla-style
branch: claude/armor-pngs-vanilla-style
with: prometheus
pr:
mods: powers
versions: Powers 0.22.11
started: 2026-11-11
owns:
  - src/main/resources/assets/jaymospowers/textures/item/armory/
---
# armor-pngs-vanilla-style

Remake the mod's armor textures (helmet, chestplate, leggings, boots × 5 tiers: leather, iron, gold, diamond, netherite) under assets/jaymospowers/textures/item/armory/ in pure vanilla Minecraft style — solid outlines, pixel-level detail, flat colors, no gradients.

## Plan
- List existing armory PNGs and their sizes.
- Reference vanilla armor PNGs as style guide (solid 1px outlines, no anti-aliasing gradients).
- Remake all 15 PNGs keeping the same canvas size per piece.
- Do not touch model JSON / item models — PNGs only.
- sh ./gradlew build + tools/pr_checks.py, commit, push, PR, vault.py --set status=in-review.

## Notes
- Task claimed via vault.py --claim hermes-modeller (tiv, Tasks thread, 2026-11-08).

## Done
What a player will see, and what wasn't tested.
- 2026-11-11 18:34 No sign of work for 30 min: marked stalled (whoever ran it stopped).
