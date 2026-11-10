# Skulk golem asset credit

The model, texture and three original animations (`walk`, `estatue`, `attack`) are **"Skulk golem" by elma_taburlas**,
from Blockbench Workshop: https://blockbenchworkshop.com/model/elma_taburlas/skulk-golem (source copy:
`tools/bbmodels/skulk-golem.bbmodel`).

- Licence: **CC BY 4.0**, https://creativecommons.org/licenses/by/4.0/ (`skulk_golem-LICENSE.txt`).
- Changes: animations of ours added as keyframes by `kingdoms/tools/gen_skulk_golem_geo.py` (`idle`, `statue`, `wake`,
  `settle`, `hurt`, `death`), then converted with `tools/bbmodel_geckolib.py` for GeckoLib 5. The original three
  animations, cubes, pivots and texture are unchanged. Drawn at full size with a 0.9-block shadow.
- Renders: `skulk_golem-render.png` (each old pose beside the new one: rest/idle, statue, walk, attack) and
  `skulk_golem-animations.png` (the new moves).
