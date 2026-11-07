---
name: sound-detail-mods
description: Sounds/AmbientSounds/Subtle Effects/A Good Place added + own Ambience effects (PR #160, pack 0.10.49); Particular and Dynamic Surroundings rejected
metadata:
  type: project
  modified: 2026-10-14T15:30:21.396Z
---
2026-10-14 Andy asked about Sounds (modrinth "sound", imb11), A Good Place, Particular, Subtle Effects, Dynamic Surroundings.
PR #160 MERGED 2026-10-14 (pack 0.10.49) adds Sounds (packModsNoCf, CF id unknown: TODO home PC), AmbientSounds 6 + CreativeCore, Subtle Effects + Fzzy Config + Fabric Language Kotlin, A Good Place, MRU + YACL. Sound Physics Remastered stays.
Particular (<=1.21.4) and Dynamic Surroundings (<=1.21.1) have no 26.3 build: their ideas are remade in client/Ambience (waterfall spray, fireflies, cave dust, big splashes, armour clink).
**How to apply:** new ambient particle/sound touches go in client/Ambience. Don't re-propose DS or Particular unless they get 26.3 builds.
