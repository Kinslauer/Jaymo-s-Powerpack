# smog over forest fires
- **Smog over forest fires** (Andy, 2026-11-20, with a picture of big dark puffy clouds hanging in the sky): once a fire grows to a burning forest (about 30 burning tops close together, not a house or a campfire), a dark bank of smog gathers high above it, about 46 blocks over the burning trees, where the smoke plumes end. Big, slow, dark grey puffs (16 to 28 blocks across, darker over a bigger fire) that hang there, heave a little, drift slowly downwind and linger a minute or two after the fire is out. They thin when you fly up into them. Not in the Nether.
- Cheap: the smog has its own budget of about 0.12 puffs a tick (0.04 in low-end mode, `/lowend`), so a whole forest's pall stays at about 250 puffs at most (about 90 in low-end mode).
- Only fires within about 110 blocks of you are looked for (as with the plumes), so a pall forms over fires you are near, not ones far across the map.
- Classes: `client/disasters/FireSmoke` (pall), `StormParticle` (PALL kind).
- Compiled; not seen in game. Height, size, darkness and how long it lingers are first guesses.
