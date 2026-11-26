# shore sea life back to the game's numbers
- Andy: the sea-life thinning along the shore can go (the old "too many in the water" was one mob overspawning). Coasts, rivers and lakes spawn their fish, squid, dolphins and the rest as the game does again:
  - no more thinning of the sea's spawns near the coast (only 15% were let through at the shore): `SeaLifeSpawnMixin` and `SeaLife.keep` removed;
  - the coast's water caps are the game's (fish 20, water creatures 5; they were 10 and 3), `MonsterCapMixin`;
  - water mobs come in their own group sizes again (at most 2 at the coast and 4 at sea before): `WaterClusterMixin` removed.
- Open sea keeps its own caps (fish 20, water creatures 8). Cave water creatures stay at 2, and worldgen fish still swim off (`WorldgenFishMixin`, the Deck's watchdog fix).
- Built; not played.
