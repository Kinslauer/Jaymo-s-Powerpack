# burnt corpses leave ash piles

When a corpse burns away on the ground (not in lava), it leaves a pile of ash where it lay: a zombie a small heap,
a horse or bear more, a ghast or giant a big pile across several blocks. Ash piles stack 1-8 layers and stay put -
they don't melt in rain and can't be shovelled up yet (builder's call: say so).

- `com.jaymoskitchen.corpse.CorpseEntity`: the burn branch leaves an ash pile before the body is discarded; lava
  burns leave nothing, as before.
- `com.jaymoskitchen.corpse.AshPileBlock` + `Corpses.ASH_PILE`: new snow-style ash block, 1-8 layers, with loot
  table and lang.
- Built; not played in game.
