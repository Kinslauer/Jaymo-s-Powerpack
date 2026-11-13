# Hardrock parts left out: rivers, floods, balloon, hammock, rumours
- **Rivers**: `RiverRun` gives still river water a downstream flow; `RiverIce` freezes rivers in the cold from the banks and lets floes drift downstream carrying what stands on them; they thaw when it warms. Our own version of the Dynamic Waters idea (its source was not reachable, so nothing is ported).
- **Floods**: `Floods` lets a block broken beside a small water body (up to 360 sources) drain it downhill for a while.
- **Hot air balloon** (`HotAirBalloon`, craftable): burner on jump, vent on back, fuel from coal, logs, planks or sticks; not steerable, it drifts on a wind that differs at each height. New burner sound `rough.balloon.burner`.
- **Hammock** (`Hammock`, craftable): hung between two anchors, sleeps through the night, never sets spawn.
- **Rumours** (`VeinRumours`): village masons sell a paper rumour or a vein map pointing at a nearby iron, copper, gold or silver vein.
- JEI pages for sleeping bag, hammock, paraglider, balloon, river ice and rumours, and (Andy: "They all need jei info") for dogs that learn (on the bone) and washing off the smell (on the water bucket and cauldron). Lang part `left_out_hardrock.json`.
Built (`compileJava`); not played.
