- **The Frying Pan is the Skillet (Track B).** Same id (`jaymoskitchen:frying_pan`), so pans in old worlds, the hob, `HobLocks`,
  `Station` and the crafting-grid remainder all work unchanged; it is renamed "Skillet" (`lang-b.json` replaces the two old
  `frying_pan` names). New class `delight/SkilletItem` (a `BlockItem`, Farmer's Delight's skillet, MIT): iron stats (5 damage,
  -3.1 speed, +1 knockback, iron durability), rings when it hits (a soft ring for a swing under 80%), and cooks in the hand:
  stand within a block of a lit fire, hold something a campfire could cook in the other hand and hold use; a fifth of the
  campfire's time (less with Fire Aspect, never under 3 seconds), and the cooked food lands in your pockets. Let go early and the
  raw food goes back. Click while it cooks to flip it (`SkilletItem.Flip` payload). It is set down as the hearth block only when
  you sneak (the click is otherwise the cooking). The model flips the food with the pan
  (`client/delight/SkilletFlipItemRenderer`, item model type `jaymoskitchen:skillet_flip`, test `jaymoskitchen:skillet/is_cooking`,
  see `items/frying_pan.json`); the arm tips with it in third person (render-state and humanoid-model mixins). Components:
  `skillet_cooking_time`, `skillet_ingredient`, `skillet_flip_timestamp`, `skillet_flipped`. Sounds: `block.skillet.sizzle`,
  `block.skillet.add_food`, `item.skillet.attack.strong/weak` (new `sounds.json`).
- **Stove** (`delight/StoveBlock`, `StoveBlockEntity`): six grill spots, lit when placed, a shovel puts it out, an empty hand
  (or the campfire's own dish list when you sneak) opens the kitchen as a campfire (`Station.of`: a lit stove is a CAMPFIRE).
  Food cooks on it as on a campfire; stepping on a lit one burns (`stove_burn` damage type, tagged is_fire,
  burn_from_stepping, no_knockback, panic_environmental_causes). In `jaymoskitchen:heat_sources`, so cookware sits on it.
  Drawn by `client/delight/StoveRenderer`.
- **Storage and decor** (new class `delight/DelightHome`): 13 cabinets (every wood, poplar is vanilla in 26.3; a barrel's 27
  slots, opens with the barrel's sounds), the wooden and bamboo baskets (a basket pulls loose items in above it), six crates
  (carrot, potato, beetroot, cabbage, tomato, onion; a 3x3 of the crop, and back to 9), rice bale (fire-prone, softens falls)
  and bag of rice, safety net (bounces), canvas (from straw), canvas rug, tatami block, full and half tatami mats.
  Baskets, cabinets and the net burn as fuel (`COOKING_FUEL` component), nether-wood cabinets don't.
- **Canvas signs:** plain and sixteen dyes, each as a standing, wall, ceiling-hanging and wall-hanging sign (68 blocks, 34
  items; dyed by crafting with a dye). They are spruce signs in every way but their look; a new one on a dark canvas
  starts with white text (`SignBlockEntityMixin`). Item tags `canvas_signs`, `hanging_canvas_signs`.
- 26.3 facts learned: sign blocks draw from block models now (`minecraft:block/template_sign_rot_0..3`,
  `template_hanging_sign_rot_*`, `template_attached_hanging_sign_rot_*`, `template_wall_sign`, `template_wall_hanging_sign`) and
  need a blockstate each; their entity type takes new blocks with `BlockEntityTypes.SIGN.addValidBlock` (Fabric), not a
  mixin. `ColorCollection` (`Blocks.WOOL.white()`, `Blocks.CARPET.brown()`) replaces the per-colour fields. Custom item
  model types register in `ItemModels.ID_MAPPER` and conditions in `ConditionalItemModelProperties.ID_MAPPER`; the pan's
  wrapper needs an accessor mixin on `CuboidItemModelWrapper`. Fuel is the `COOKING_FUEL` data component. `LevelEvent` lives in
  `world.level.block`. Held-cooking progress (the bar) reads the client player through `SkilletItem.viewer`, which the client
  sets, since common code can't name the client.
- Shared files touched: `heat_sources` and the `minecraft:mineable/*` and `damage_type/no_knockback` tags (my entries added at
  the top of each list so other tracks' additions at the bottom merge cleanly), `Station.of` (one branch),
  `KitchenBlocks` (the pan is a `SkilletItem`), the two mixin config lists, one line each in `JaymosKitchen` and
  `JaymosKitchenClient`.
