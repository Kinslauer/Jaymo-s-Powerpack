# The Maw's ruins cover the ground
- **Ruined cities nearly everywhere under the Maw.** The Lost Cities ruins only covered about a quarter of the Maw's wasteland, in patches with up to 1000 blocks of bare ground between them, so you could land and see nothing but dirt and rock. Now about nine chunks in ten are ruined city, with scattered open ground between the districts. Buildings still top out well under the broken world.
- Only ground that hasn't been visited yet changes: Maw chunks already generated stay as they are, so in an existing world the ruins show up past the parts you've explored (or everywhere once the Maw is reset).
- Code: `power/zfighter/MawCities.SPREAD` (Lost Cities' `CITY_PERLIN_OFFSET` for the Maw's profile, was 0.2, now -0.6).
- Checked on a dev server: the 16x16 chunks under (519, -156) went from no buildings to 238 of 256 chunks built, nothing above y 111. Not played.
