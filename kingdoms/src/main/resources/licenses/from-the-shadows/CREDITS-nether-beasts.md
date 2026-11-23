# Credits: the Nehemoth and the Bulldrogioth

Ported from **From the Shadows Reborn** 3.0 by **Sonmok14** and **L_Ender** (CurseForge; GNU AGPL v3, the text beside
this file in `AGPL-3.0.txt`). Its models, textures, animations and sounds named below are copied (the animations with
their `sound_effects` keys removed, since our sounds play from the mob's code) and stay under the AGPL v3. The Java
code is our own (Fabric 26.3, GeckoLib 5); only the mob's logic and numbers follow the original.

Files copied (all under `kingdoms/src/main/resources/assets/jaymoskingdoms/`), original name -> ours:

## Nehemoth
- `geo/nehemoth.geo.json` -> `geckolib/models/entity/nehemoth.geo.json`
- `animations/nehemoth.animation.json` -> `geckolib/animations/entity/nehemoth.animation.json` (sound keys removed)
- `textures/entity/nehemoth_retexture.png` -> `textures/entity/nehemoth/fire.png`
- `textures/entity/nehemoth_eye.png` -> `textures/entity/nehemoth/fire_glowmask.png`
- `textures/entity/soul_retexture.png` -> `textures/entity/nehemoth/soul.png`
- `textures/entity/nehemoth_eye_2.png` -> `textures/entity/nehemoth/soul_glowmask.png`
- sounds `nehemoth_roar`, `nehemoth_attack`, `bite_warn`, `nehemoth_death`, `soul_laser`, `soul_laser_ready`, `goresound`, `critical_hit`,
  `nehemoth_bite_1..6`, `nehemoth_charge_1..4`, `stomp_1..4`, `nehemoth_hurt4..6`, `nehemoth_idle{1,2,5,6,7,8,11,12,13}`
  -> `sounds/nehemoth/*.ogg`
