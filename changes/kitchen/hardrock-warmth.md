# Hardrock patch 1 - warmth you build
Andy's TFC Hardrock picks (ideas after Cold Sweat, GPL-3.0, written afresh; art painted by `tools/gen_warmth.py`):
- **Hearth** (8 stone around a campfire): feed it coal, charcoal, logs, planks or sticks (up to a day's fuel). While lit it
  walks the closed room around it every 5 s (`HearthEntity`: through open doors, gates and trapdoors, not walls, closed
  doors, panes or open sky; up to 640 blocks, 12 away) and the cold can't push anyone standing in that room past
  comfortable (`Warmth.HEARTH`). Flames in its mouth, smoke, a crackle, light 13.
- **Icebox** (8 planks around packed ice): the same for heat, fed ice, packed ice, blue ice, snow blocks or snowballs.
- **Linings**: armour or clothes in the main hand, wool, rabbit hide or leather in the other, used on a loom. Wool counts
  as a warm piece, rabbit hide as a cool piece, leather half of each (`Lining`); shown on the tooltip.
- **Thermometer** (2 glass panes over a copper ingot): held in either hand, a brass-framed tube beside the body-heat tube
  shows the place's own heat, before clothes, meals and drinks (new synced `BodyHeat.Heat.air`).
- **Hot and cold waterskins**: a waterskin used on a lit campfire comes off clean and hot, used on snow or ice it comes
  away cold; for 5 minutes a drink of it warms or cools you for 2 minutes (`Warmth.HOT_DRINK`/`COLD_DRINK`).
- **Colder to the north**: the overworld's land is 5 colder per 1000 blocks north of the world spawn and warmer to the
  south, up to 25 (`Warmth.NORTH_COLD`).
- JEI info pages for the hearth, icebox, thermometer, the loom (linings) and the waterskins.
- Compile-checked; not played.
