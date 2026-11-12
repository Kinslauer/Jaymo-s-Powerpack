# Layered, reactive sounds for Kingdoms' held sounds
- Kingdoms gets its own copy of Powers' sound helper (`Soundscape`): a sound is a stack of layers, each picking one of its variants at a varied volume and pitch, played at uneven intervals instead of every Nth tick, and loud ones echo quieter and lower in caves and deep indoors. The recipes and their tuning are in `WorldSounds`.
- **Ships**: each oar stroke is the dip, an oarlock knock and now and then water running off the blade, quicker and higher the faster she goes. Under way the hull works (timbers, a groan, the sea slapping her sides, louder with speed) with canvas and rigging when her sails are set. Going down: timbers giving, air rushing out, the sea pouring in.
- **Caravans**: a cart's load shifting, a wheel and the harness, unevenly.
- **Trees**: a falling tree creaks higher as it leans, with leaves and the odd split; a tree in a tornado thrashes its leaves and knocks its limbs.
- **Dragons**: fire breath is a gout with a roar and a crackle under it; frost breath a hiss with snow crunching and ice tinkling; Aetherion's beam hums with a resonance. Wingbeats (Wind dragons, Aeolus leaving, imps, Valor Wings) keep their beat but vary a little.
- **Blood moon**: the dead clawing up sound of earth giving way and bones rattling. **Werewolves**: the change cracks bones lower as it goes on, with cries at uneven spacing. **Hunger curse**: feeding tears and gulps. **Thrown shields** whirl unevenly.
- **Sandstorms and blizzards**: the howl is now a held bed (`client/WorldLoop`) that swells with the storm and with gusts, full in the open and muffled under a roof, with snow hissing past in a blizzard. **Quakes**: a low rumble bed under the whole quake, with deep thumps at uneven spacing.
- Clocks stay clocks: a trial's lift count, a stake's tugs, a rite's heartbeat and the try-works boiling down blubber keep their beat.
- Built; not played.
