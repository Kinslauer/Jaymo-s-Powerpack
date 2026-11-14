---
status: changes
for: prometheus
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: 
pr: 1313
suggested_owns: [src/main/resources/assets/jaymospowers/textures/item/armory/, src/main/resources/assets/jaymospowers/models/item/armory/]
writer: hermes-writer
text_pass: 
---

# Remake armor PNGs in vanilla style

Tiv, in the Tasks thread: "I'd like prometheus to be queued with a task to remake the armor pngs in vanilla style. Solid outlines, pixel based detail, clean images."

## What's wanted

Remake the mod's armor textures (helmet, chestplate, leggings, boots) in a pure vanilla Minecraft style — solid outlines, pixel-level detail, clean crisp images without gradients or fancy effects. The goal is textures that look like they belong in vanilla Minecraft with the default 16x16 pixel aesthetic.

## Notes

- Target the player armor assets under `assets/jaymospowers/textures/item/armory/` (helmet, chestplate, leggings, boots for each tier: leather, iron, gold, diamond, netherite).
- Reference the vanilla Minecraft armor PNGs as the style guide: solid 1-pixel outlines, no anti-aliasing gradients, flat solid colors per section, clean separation between pieces.
- Keep the same canvas size as vanilla (for a 1.20.1/1.21 mod: 96x96 or 128x128 per piece depending on the mod's conventions).
- Do not change the model JSON files or the item model definitions — only the PNG texture files.
- Say back the task name and that Prometheus is starting it, and add it to the todo list as `in_progress`.
- Reference image: vanilla leather helmet at `https://textures.minecraft.net/texture/leather_helmet.png` as the visual style guide (solid brown with white border).
- 2026-11-11 19:14 hermes-modeller's claim had no sign of work for 30 min: handed back as open.
- 2026-11-11 16:35 Text pass: rewrote changes/powers/armor-pngs-vanilla-style.md - dropped the internal 'PNGs only' and tiered-armor task-text bullets, kept the player-facing armor-textures bullet, closed with 'Not tested in game.' No lang keys or code changed; the PR's other 8 files are PNG textures only.
- 2026-11-12 01:57 Review (head 153bd3d), Andy 2026-11-11 8:57 PM: 'those textures need work'. Not merged. The new bog_iron and caveman icons are tiny blobs (116-138 bytes each, a few pixels of flat colour), far worse than master's icons from #1269, and the caveman set reuses the bog-iron shapes (master gave caveman its own shapes in d38db1a). Start from master's current PNGs: keep their shapes, full 16x16 silhouettes with vanilla-style shading and outline, caveman distinct from bog iron. Compare side by side before pushing (project file reviews/armor-1313-compare.png shows the problem).
