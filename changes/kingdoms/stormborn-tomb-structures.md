# Stormborn Tomb: a Nordic barrow and the tomb beneath it (MIT port)
Andy: "get them, we need it" (Dungeon Conquest: Stormborn Tomb by Tada_no_ahiru, MIT; licence and credit in
`licenses/stormborn-tomb-MIT.txt`).
- **What you find:** in snowy and cold taiga, groves, snowy slopes, meadows and windswept hills (rare: one try per 60x60
  chunks), a great barrow hill with a hall inside. Beside the end portal frame in the hall, where DungeonZ's portal was,
  the stone door stands open (the original's Eye of Ender redstone doesn't hold it shut once generated, so nobody is
  locked out of a no-build tomb); behind it a ladder shaft and a tunnel lead down
  into the tomb: the entrance hall, hallways, a split with a parkour climb, the great halls and the boss arena, about
  100 blocks under the hill. Chests and barrels hold the tomb's loot (low to end tier, deeper is better); a chest of
  boss loot waits in the arena.
- **Undead:** our own tomb dead stand where Draugr Invasion's did: 27 silver skeletons and 22 draugr (#1149), kept
  (never despawn), plus spiders in the webbed hall; the old skeleton-warrior spawners spawn silver skeletons. In the
  boss arena the Skeleton Lord sleeps as a statue until you come within 8 blocks or strike him. Until #1149 is in,
  the draugr just aren't there.
- **Swaps** (nothing of Supplementaries, Draugr Invasion, DungeonZ, FrogiLib or Moonlight comes along): candle holders
  -> candles, fire pits -> campfires, sconces -> torches, urns -> decorated pots, pedestals -> stone brick walls,
  statues and the hat stand -> armor stands, goblets -> flower pots, cage -> iron bars, globe -> lodestone,
  hourglasses -> lanterns, FrogiLib anvils/trapdoors -> damaged anvils/open spruce trapdoors, dragon banners -> skull
  banners, draugr swords/bows -> iron swords/bows, ebony gear -> crossbow/diamond axe/silver sword, bombs -> dynamite
  and TNT, Supplementaries' disc -> music disc 13, DungeonZ portals -> doorways, DungeonZ's mob/boss/exit marker blocks
  -> the mobs above, an empty boss spot, a loot chest and an open exit.
- **How it's built:** the tomb was made for DungeonZ's own dimension, so its rooms were saved as empty space; they're
  air now, so they don't fill with stone underground. It's ~270 blocks across, past what a jigsaw structure may reach,
  so the new `StormbornTomb` structure type places its 12 pieces (11 from the original plus our descent) at fixed
  offsets worked out from their jigsaws. In `#no_build_dungeons`.
