# the Verdant World's builder's pieces - wood sets, Gravilite, the Gravity Compressor
Andy's Eden Ring picks, part 3 (Eden Ring's MIT art, `tools/eden_ring_license.txt`).
- **Wood sets**: balloon mushroom, pulse tree and brain tree planks now make stairs, slabs, fences, fence gates, doors,
  trapdoors, buttons and pressure plates (the usual recipes).
- **Gravilite**: glowing blue shards stand on the ground all over the Verdant World, about one chunk in five (only
  chunks made after this update). Four shards make a Gravilite Block; a shard ringed with four glass makes a Gravilite
  Lamp (full light); a shard ringed with eight iron nuggets makes a Gravilite Lantern, which stands or hangs.
- **Gravity Compressor** (iron, a Gravilite Block, redstone, stone): given a redstone signal it lifts everything in the
  column above it, 2 blocks of column per level of signal (30 at full power). Living things float up and take no fall
  damage while in it; dropped items, arrows and carts are pushed up (`GravityCompressorBlock`).
- All numbers are constants at the top of `GravityCompressorBlock`, `Eden` and `EdenFeature`.
