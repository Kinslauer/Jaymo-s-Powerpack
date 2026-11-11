---
status: claimed
for: prometheus
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-modeller
pr:
suggested_owns: [src/main/resources/assets/jaymospowers/textures/item/armory/, src/main/resources/assets/jaymospowers/models/item/armory/]
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
- 2026-11-11 18:39 hermes-modeller's claim had no sign of work for 30 min: handed back as open.
