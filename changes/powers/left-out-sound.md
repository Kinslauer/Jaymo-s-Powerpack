# Sounds pick from a pool, and the land is heard from where it is
- **A real pool.** Andy asked for sounds to "choose from a pool so the outcomes are more varied". Before, only the pitch was nudged. Now nineteen of the game's sounds that had only one recording each have three takes of our own beside it, made from noise and tones (`tools/gen_pool_sounds.py`, `assets/minecraft/sounds.json`). They are: picking up an item or experience, a bow loosed, a chest opened, an anvil landing or used, drinking, a burp, a tool breaking, a lever or button, fire put out, the bobber's splash, small and big falls, flint and steel, and a barrel shut. The small pitch steps stay on top.
- **Spot sounds from a direction** (`SpotSounds`, this pick went out with ImmersiveFX):
  - Out under the sky in the woods by day, a woodpecker drums on a real trunk near you, with our own drumming sound (`tools/gen_spot_sounds.py`).
  - At night by a swamp or river, frogs call from the water.
  - A village you can't yet see is heard faintly from where its folk are.

  Turn your head toward any of them and walk to it.
- These were left out of #1303 and #1361. Built (`compileClientJava`); not played.
- **Biome ambience is back** (Andy: "Bring it back"): AmbientSounds 6 and CreativeCore return to the pack (biome, cave, water and weather ambience). They had left for ImmersiveFX: Resurrected, which has since gone (build.gradle `packMods`, and they're no longer moved out of the CurseForge profile).
