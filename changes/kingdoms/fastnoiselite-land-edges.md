# Ragged edges for the steam valleys, ashlands, salt pans and steppe
- The four lands (`lands/Lands`) used to end in smooth round blobs where their simplex noise crossed its threshold. The
  place the noise is read at is now pushed about by a FastNoiseLite domain warp (two layers, three wiggles a wave, up to
  an eighth of a wave), so their edges wander in bays and spits. The lands still lie where they did, give or take a few
  dozen blocks; only chunks generated from now on follow the new edges.
- FastNoiseLite 1.1.1 (Jordan Peck, MIT) vendored as `noise/FastNoiseLite` (package line added, float positions switched
  to double), licence in `licenses/fastnoiselite/`.
- Built (Gradle compileJava); edge shapes checked by rendering the threshold to images. Not played in game.
