# FastNoiseLite worldgen: Pale Moon craters, softer Aether clouds
- **Pale Moon craters**: the moon's grey dust is now pocked with craters in two sizes, big bowls about every hundred blocks
  (up to ~13 deep with a raised lip) and small ones peppered between and inside them; a younger crater cuts into an
  older one's wall. Floors keep the dust, rims are heaped from it. Only chunks generated from now on get them, so a moon
  already explored keeps its ground (a crater can stop short at the edge of old chunks).
  (`power/zfighter/farworld/MoonCraterFeature`, `worldgen/placed_feature/moon_craters.json`, first in `moon_plains`.)
- **Aether cloudbed and cloud lanes**: their shape noise is FastNoiseLite OpenSimplex2 with a domain warp instead of our
  square-grid value noise, so the banks curl at the edges and lose the blocky lumps. Coverage is kept about as before
  (cloudbed ~71% vs 73%, lanes threshold 0.58 -> 0.66 for ~31%). New chunks only.
- FastNoiseLite 1.1.1 (Jordan Peck, MIT) vendored as `noise/FastNoiseLite` (only the package line added and positions
  switched from float to double, as its own notes describe), licence in `licenses/fastnoiselite/`; `noise/Noises` hands
  out one per world seed.
- Built (Gradle compileJava); shapes checked by rendering the noise to images in the cloud. Not played in game.
