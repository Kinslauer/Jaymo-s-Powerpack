# Minotaur asset credit

The model, texture and eight original animations are **“Minotaur” by eanncrod**, from Blockbench Workshop:
https://blockbenchworkshop.com/model/eanncrod/minotaur (source copy: `tools/bbmodels/minotaur.bbmodel`).

- Licence: **CC BY 4.0**, https://creativecommons.org/licenses/by/4.0/ (`minotaur-LICENSE.txt`).
- Changes: converted with `tools/bbmodel_geckolib.py` for GeckoLib 5; the complete model and all bone pivots moved up
  three Blockbench units so its feet stand at y=0; animations renamed to `idle`, `idle_calm`, `walk`, `run`, `slam`,
  `mino_slam`, `swing_1` and `swing_2`; `swing_2` marked as a one-shot instead of a loop. No cubes or pixels were
  removed or repainted. Drawn at 0.85 size with a 1.2-block shadow.
- Animation use: calm and hostile idles, the previously unused hostile walk, the run during its charge, alternating
  hammer swings, and `slam` for its ground slam. `mino_slam` remains editable and exported as an alternative.
- Inspected renders: `minotaur-render.png` and `minotaur-animations.png`.
