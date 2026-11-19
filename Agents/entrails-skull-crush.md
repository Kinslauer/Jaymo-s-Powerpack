---
status: done
task: entrail variety, intestines from gibs, blunt weapons crush skulls
branch: claude/project-thread-t83v6r
with: Claude (cloud thread "Add entrail variety and skull crushing")
pr: 1866
mods: kitchen, powers
versions: 
started: 2026-11-19
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Gibs.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Gib.java
  - kitchen/src/client/java/com/jaymoskitchen/client/corpse/GibRenderer.java
  - kitchen/src/main/java/com/jaymoskitchen/butchery/Butchery.java
  - kitchen/src/main/java/com/jaymoskitchen/butchery/BrainMatterBlock.java
  - kitchen/tools/gen_gore.py
  - kitchen/tools/gen_gut_piles.py
  - tools/bbmodels/gore-pieces.bbmodel
  - src/main/java/com/jaymospowers/core/Dismember.java
  - src/main/java/com/jaymospowers/core/wounds/Downed.java
  - src/main/java/com/jaymospowers/power/web/KitchenCompat.java
---
# Entrail variety and skull crushing

**Task:** Andy: more entrail variations; intestines as well as offal should fall from gibs (he only ever saw offal);
maces and blunt weapons crush skulls on a killing blow to the head, leaving white debris and pebble-like "brain matter".

## Plan
- Kitchen: 9 new gore looks, Intestines ground pile, Brain Matter pile, `Gibs.skullCrushed`.
- Powers: `Dismember.crushSkull` on a blunt killing head blow.

## Notes
- "blacked limb" read as the head (the killing blow lands on it). No lore paragraph: physical remains only.

## Done
- Kitchen: 9 new gore looks, Intestines + Brain Matter piles, Gibs.skullCrushed. Powers: Dismember.crushSkull. Both compile; not played, no runServer boot.
