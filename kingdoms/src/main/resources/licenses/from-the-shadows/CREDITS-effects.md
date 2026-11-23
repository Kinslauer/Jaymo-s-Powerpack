# Credits: effects, potions and enchantments

Ported from **From the Shadows Reborn** 3.0 by **Sonmok14** and **L_Ender** (CurseForge; GNU AGPL v3, the text beside
this file in `AGPL-3.0.txt`). The files below are copied (the enchantments with their description key and tag names
changed to ours, and their primary items to 26.3's melee-weapon tag) and stay under the AGPL v3. The Java code is our own
(Fabric 26.3); only the behaviour and numbers follow the original.

Files copied (under `kingdoms/src/main/resources/`), original name -> ours:

- `assets/fromtheshadows/textures/mob_effect/heal_block.png` -> `assets/jaymoskingdoms/textures/mob_effect/heal_block.png`
- `assets/fromtheshadows/textures/mob_effect/blessing_of_gluttony.png` -> `assets/jaymoskingdoms/textures/mob_effect/blessing_of_gluttony.png`
- `data/fromtheshadows/enchantment/divinity.json` -> `data/jaymoskingdoms/enchantment/divinity.json`
- `data/fromtheshadows/enchantment/freezing.json` -> `data/jaymoskingdoms/enchantment/freezing.json`
- `data/fromtheshadows/enchantment/retribution.json` -> `data/jaymoskingdoms/enchantment/retribution.json`
- `data/fromtheshadows/tags/enchantment/exclusive_set/divinity.json` -> `data/jaymoskingdoms/tags/enchantment/exclusive_set/divinity.json`
- `data/fromtheshadows/tags/enchantment/exclusive_set/freezing.json` -> `data/jaymoskingdoms/tags/enchantment/exclusive_set/freezing.json`
- `data/fromtheshadows/tags/entity_type/demon.json` -> `data/jaymoskingdoms/tags/entity_type/demon.json`

Written by us from the original's `EffectRegistry` (the potions and their brewing recipes) as 26.3 data:
`data/jaymoskingdoms/recipe/brewing/*_{terrible_plague,hardshell,frenzy,strong_poison_suspicious_cloth,mundane_crimson_shell,awkward_froglin_leg}*.json`.
Not copied, since we already have the same thing: the original's Bleeding (Powers' Bleeding) and Plague (Nightfall's Atrophy).
