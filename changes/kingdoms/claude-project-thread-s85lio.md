# heavy smoke over big fires
- **Smoke over big fires** (Andy: a burning forest threw almost no smoke): any big blaze (a wildfire, an imp's, a player's) now throws up tall dark plumes that rise, swell, pale and lean downwind as they climb; the bigger the fire, the bigger and thicker the plumes. Campfires and a few burning blocks stay as they were. Not in the Nether.
- **Smoky air**: near a big blaze and out under the sky the fog closes in to about 36 blocks and turns smoke-brown, with the weather's other fogs (less indoors).
- Cheap: a few columns round the camera are looked at each tick (40 in low-end mode, `/lowend`), and all plumes share one budget of about 1.2 puffs a tick (0.4 in low-end mode), so a whole forest burning stays at a few hundred puffs.
- Classes: `client/disasters/FireSmoke` (new), `StormParticle` (SMOKE kind), `SkiesClient`, `SkiesFogMixin`.
- Compiled; not seen in game. Sizes, colours and the haze distance are first guesses.
