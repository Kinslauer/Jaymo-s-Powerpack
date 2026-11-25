# End plants no longer break End worldgen
- **Fix**: the four End plants (voidpepper, ethereal saffron, amberveil, chorusflame) now generate in the last decoration step instead of the vegetal one. Where Enderscape and Stellarity biomes share one End, Enderscape adds its own plants to the same biomes later, and the clash made the server log "Feature order cycle found" and fail to generate End chunks.
- Not tested in game (CI build only).
