# Credits: the gear (diabolium, crust armour, blood)

Ported from **From the Shadows Reborn** 3.0 by **Sonmok14** and **L_Ender** (CurseForge; GNU AGPL v3, the text beside
this file in `AGPL-3.0.txt`). The models, textures and animations named below are copied and stay under the AGPL v3.
The Java code is our own (Fabric 26.3, GeckoLib 5); only the items' abilities and numbers follow the original.

Files copied (all under `kingdoms/src/main/resources/assets/jaymoskingdoms/`), original name -> ours:

## Armour
- `geo/diabolium_armor.geo.json` -> `geckolib/models/item/diabolium_armor.geo.json` (the empty `biped*` wrapper bones taken out)
- `geo/crust_armor.geo.json` -> `geckolib/models/item/crust_armor.geo.json` (the same)
- `animations/diabolium_armor.animation.json` -> `geckolib/animations/item/diabolium_armor.animation.json` and
  `.../crust_armor.animation.json` (the original's crust armour uses it too)
- `textures/armor/diabolium_armor_re.png` -> `textures/armor/diabolium_armor.png`
- `textures/armor/crust_armor.png` -> `textures/armor/crust_armor.png`

## Items and block
- `textures/item/{diabolium_nugget,diabolium_ingot,diabolium_helmet,diabolium_chest,diabolium_leggings,diabolium_boots,crust_helmet,crust_chest,crust_leggings,crystallized_blood,crystallized_fragment,bottle_of_blood,corrupted_heart}.png` -> the same names
- `textures/block/diabolium_block.png` -> `textures/block/diabolium_block.png`

The recipes follow the original's data (`recipe/*.json`) with ours where the original brews (bottle of blood, diabolium ingot).
The equipment layers (`textures/entity/equipment/...`) are transparent placeholders of our own: the armour is drawn by GeckoLib.

## Weapons
- `geo/item/devil_splitter.geo.json` -> `geckolib/models/entity/devil_splitter.geo.json`; `textures/item/devil_splitter.png` -> `textures/entity/devil_splitter.png` and `textures/item/devil_splitter.png`
- `geo/item/thirst_for_blood.geo.json` -> `geckolib/models/entity/thirst_for_blood.geo.json`; `textures/item/thirst_for_blood.png` -> `textures/entity/thirst_for_blood.png` and `textures/item/thirst_for_blood.png`
- `animations/thirst_for_blood.animation.json` -> `geckolib/animations/entity/{devil_splitter,thirst_for_blood}.animation.json` (the original's empty one, which both use)
- the hand, gui and frame placements in `models/item/{devil_splitter,thirst_for_blood,thirst_for_blood_using}.json` are the original's `display` blocks
- `geo/throwing_dagger.geo.json` -> `geckolib/models/entity/throwing_dagger.geo.json`; `textures/entity/throwing_dagger.png` -> the same; `animations/frogvomit.animation.json` -> `geckolib/animations/entity/throwing_dagger.animation.json` (the original's dagger uses it)
- `textures/item/{crystallized_dagger,horn_of_apocalypse}.png` -> the same names; `models/item/horn_of_apocalypse.json` display block
- recipes `devil_splitter`, `thirst_for_blood`, `crystallized_dagger`, `horn_of_apocalypse`; tags `demon`, `not_drop_crystallized_fragments`
