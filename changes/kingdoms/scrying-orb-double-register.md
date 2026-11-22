# The server starts again: the Scrying Orb registered its packets twice
- **Fix**: the server crashed on start ("Packet type jaymoskingdoms:scrying_orb_view is already registered"). The Scrying
  Orb block registered the orb's three packets and `ScryingOrb.register()` registered them again. Now only `ScryingOrb`
  does.
- Class: `ScryingOrbBlock`.
- Built; the server was started with it.
- **Fix**: the server then refused to load its data ("Unbound values in registry worldgen/biome"). The anvil, deep and
  grove ruins listed three biome names that no longer exist (`windswept_plains`, `deep_warm_ocean`, `wooded_hills`);
  they are gone from those lists. No other biome list in the three mods names a missing biome.
