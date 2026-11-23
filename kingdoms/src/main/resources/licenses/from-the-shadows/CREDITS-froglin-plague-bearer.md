# From the Shadows Reborn: credits for the froglin and the Plague Bearer

Jaymo's Kingdoms carries art from **From the Shadows Reborn** 3.0 (CurseForge), by **Sonmok14** and **L_Ender**. Its
CurseForge page licenses it under the **GNU Affero General Public License v3**; the licence text is `AGPL-3.0.txt` beside
this file. The files below are copied from that mod's assets (renamed into the `jaymoskingdoms` namespace) and stay under
the AGPL v3. Everything else in Kingdoms is our own code: the mobs' Java is written for Fabric 26.3 and GeckoLib 5, porting
the original's logic and numbers (see the class comments in `com.jaymoskingdoms.inhabitants.fts`).

## The froglin

| Our file (under `assets/jaymoskingdoms/`) | Original (under `assets/fromtheshadows/`) |
| --- | --- |
| `geckolib/models/entity/froglin.geo.json` | `geo/frog.geo.json` |
| `geckolib/animations/entity/froglin.animation.json` | `animations/frog.animation.json` |
| `textures/entity/froglin/froglin.png` | `textures/entity/frog.png` |
| `textures/entity/froglin/froglin_red.png` | `textures/entity/frog_red.png` |
| `textures/entity/froglin/froglin_gray.png` | `textures/entity/frog_gray.png` |
| `textures/item/froglin_leg.png` | `textures/item/froglin_leg.png` |
| `textures/item/cooked_froglin_leg.png` | `textures/item/cooked_froglin_leg.png` |

Its loot table and cooking recipes follow the original's data (`loot_table/entities/froglin.json`,
`cooked_froglin_leg*.json`). The froglin's sounds are Minecraft's own frog sounds, as in the original. The vomit has no
model of its own here (it is drawn as a slime ball); the original's `frogvomit.geo.json` is not used.
The spawn egg's texture is our own.

## The Plague Bearer

| Our file (under `assets/jaymoskingdoms/`) | Original (under `assets/fromtheshadows/`) |
| --- | --- |
| `geckolib/models/entity/plague_bearer.geo.json` | `geo/plague_bearer.geo.json` |
| `geckolib/animations/entity/plague_bearer.animation.json` | `animations/plague_bearer.animation.json` |
| `textures/entity/plague_bearer.png` | `textures/entity/plague_bearer.png` |
| `textures/entity/plague_bearer_glow.png` | `textures/entity/plague_bearer_layer.png` |
| `textures/item/plague_doctor_mask.png` | `textures/item/plague_doctor_mask.png` |
| `textures/item/suspicious_cloth.png` | `textures/item/suspicious_cloth.png` |
| `geckolib/models/item/plague_doctor_mask.geo.json` | `geo/plague.geo.json` (the worn mask's 3D model, beak and brim) |
| `textures/armor/plague_doctor_mask.png` | `textures/armor/plague.png` |
| `sounds/plague_bearer/idle_1..4.ogg` | `sounds/cultistidle_1..4.ogg` |
| `sounds/plague_bearer/hurt_1..3.ogg` | `sounds/cultisthurt_1..3.ogg` |
| `sounds/plague_bearer/death_1..3.ogg` | `sounds/cultistdeath_1..3.ogg` |
| `sounds/plague_bearer/preattack_1..2.ogg` | `sounds/cultistpreattack_1..2.ogg` |
| `sounds/plague_bearer/attack_1..2.ogg` | `sounds/cultistattack_1..2.ogg` |

Its loot table and the mask's recipe follow the original's data (`loot_table/entities/plague_bearer.json`,
`plague_doctor_mask.json`). The spawn egg's texture is our own.
