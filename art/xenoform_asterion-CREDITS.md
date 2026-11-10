# Asterion asset credit

Asterion, the Xeno-Band's minotaur alien, is drawn with **"Minotaur" by eanncrod**, from Blockbench Workshop:
https://blockbenchworkshop.com/model/eanncrod/minotaur, the same model Jaymo's Kingdoms' minotaur uses
(`kingdoms/art/minotaur.bbmodel`, credit in `kingdoms/art/minotaur-CREDITS.md`).

- Licence: **CC BY 4.0**, https://creativecommons.org/licenses/by/4.0/ (`xenoform_asterion-LICENSE.txt`).
- Changes, all made by `tools/gen_xenoform_asterion.py` from `kingdoms/art/minotaur.bbmodel`:
  - the model and its eight animations baked unchanged for Jaymo's Powers' own Blockbench loader
    (`src/main/resources/assets/jaymospowers/bbmodels/xenoform_asterion.json`);
  - its texture repainted as the alien's skin (`textures/entity/xenoform_asterion.png`): the brown hide turned a dark
    slate, the gold trim and the red eyes turned green, the grey plate cooled toward blue steel, the horns kept, and the
    Xeno-Band's insignia painted on the small chest plate's front;
  - a plated, seamed copy of that skin for the alien's Ultimate (`xenoform_asterion_ultimate.png` and `_glow.png`);
  - in game it is drawn at about half size inside a player's frame, with a second copy of its right arm (without the
    hammer, hanging straight) as the first-person arm, and the hammer hidden while a big weapon is held.
- No cubes were added or removed.
- Inspected render: `xenoform_asterion-render.png` (the idle pose in the new skin, five views).
