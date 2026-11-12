# Weather and looks, part 1 - downpours, morning mist, sand winds, auroras, livelier fire
- Andy's ImmersiveVFX picks, built as our own now that its test has ended (nothing of that mod is used). All of it is particles and fog, so it shows with Complementary on or off.
- **Downpours**: the inner half of every thunderstorm and supercell, and of one rain cell in three, is a downpour once the cell has gathered. Out under one you are soaked through at once and stay wet for 3 minutes (rain leaves you wet for 1), you lose more body heat than in rain (-10 on top of the rain's -15), and you walk up to 8% slower. A roof stops all three. Around you the rain falls in thick streaks, splashes everywhere and roars, and the view closes in to about 44 blocks of grey. `disasters/Downpours`, `Storm.downpour`.
- **Morning mist**: from before dawn to mid-morning, banks of pale fog lie on low ground and over water and the distance pales. Thickest in swamps, then by rivers and coasts, then in woods; more in autumn, less in summer; none in dry lands, high up it thins, and rain clears it.
- **Sand winds**: when the wind is in its upper half, short streams of sand (red over red sand) run low across open sand in dry lands. Not shown during a sandstorm.
- **Auroras**: two nights in three, if the sky is clear, green and violet curtains hang in the northern sky wherever it is cold enough to snow.
- **Livelier fire**: fire near you licks taller and throws sparks, and a lit campfire spits one now and then. In every dimension.
- Client code is `client/disasters/SkiesClient` (tuning constants at the top), `AuroraParticle` and `SkiesFogMixin`. A shader pack that draws its own fog may ignore the fog distance, as it does for sandstorms; the particles still show.
- Left for a later part: clouds and sky, Nether atmosphere, and the two shaders-off looks (pixel shadows, shore foam and caustics).
- Built (compiles); not seen in game.
