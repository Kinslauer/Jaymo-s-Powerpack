# Hardrock patch 4 - rock and ore
Andy's TFC Hardrock picks ("make veins bigger and rarer... diamond and netherite become IMPOSSIBLY rare"; ideas only,
our own code and art, `tools/gen_rock_and_ore.py`):
- **Big, rare veins** (`ground/BigVeins`, `BigVeinFeature`): vanilla's iron, copper, gold, diamond and ancient debris ore
  blobs are gone. In their place a chunk now and then holds one big wandering vein: iron about 1 chunk in 24 (90 ore),
  copper 1 in 20 (110), gold 1 in 48 (60), our silver 1 in 40 (50). Diamond is 3 buried ore in about 1 chunk in 900,
  ancient debris 2 in about 1 in 1200. Rarities are in `worldgen/placed_feature/big_vein_*.json`. New chunks only.
- **Prospector's Pick**: forged at the Tool Station from a copper Prospector's Pick Head, a Tool Handle and a Tool
  Binding. Tapped on rock it says what ore lies within 12 blocks (the commonest two): nothing, traces, or a lot.
- **Cave-ins** (`ground/CaveIns`): mining stone under a roof shakes it 1 time in 100: dust falls for 2 s, then up to 8
  blocks of the roof come down as cobblestone. A **Support Beam** (2 logs, makes 3; set upright or on its side) within
  4 blocks holds the roof, even one put up while the dust falls.
- **Wooden Pan** (stick, bowl, stick): used on gravel or sand that water touches, now and then turns up a gold or
  copper nugget, rarely iron, or flint; the bank washes away in time. **Sluice** (5 wooden slabs, a trough): load it with
  up to 8 gravel or sand beside running water and it washes them by itself, a little better than a pan.
- JEI info pages for all four.
- Compile-checked; not played.
