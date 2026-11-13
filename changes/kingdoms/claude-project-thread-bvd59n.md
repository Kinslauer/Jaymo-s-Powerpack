# Ship cannon purple-black ends fixed
- **Ship cannon**: the muzzle, breech, cascabel knob and touch-hole fitting used texture keys (`#gun`, `#brass`) the model never defined, so those parts drew as purple-black missing texture. `gun` now points at the cannon's iron and `brass` at its band (`models/block/ship_cannon.json`).
- A scan of every model in all three mods found no other undefined texture keys.
- Checked in a cloud test game; not played in the pack.
