---
status: running
task: 
branch: claude/gnomes
with: Claude Code (PC)
pr: 
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/Gnome*.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/GnomeRenderer.java
  - kingdoms/tools/gen_gnome.py
  - kingdoms/art/gnome.bbmodel
---
# Gnomes

**Task:** Andy (2026-11-19): give the gnomes real models. Option 2: the structure mods' "Gnome" baby zombies become our own GeckoLib gnome mob with gnome lore and behaviour (lore "The gnomes", approved).

## Plan
- Model + animations (gen_gnome.py), entity Gnome, swap on load, renderer, lang, loot, gift tag, egg, change note.
- Build on the Deck or CI (not the PC).

## Notes
- The gnomes come from ATi Structures (gnome_hut) and Grim Kingdoms (gnome_tower, gnome_tall_tower), both ARR: nothing of theirs copied.

## Done
