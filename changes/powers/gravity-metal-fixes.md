# Gravity metal fixes: no dupes from torn chunks, no weapon minting
- Any torn-out chunk (every power that tears blocks loose) now removes all its blocks first and tells their neighbours afterwards, so a rail, lantern, bell or torch torn out with what held it up no longer also drops as an item beside the piece in the chunk. Rails, containers and redstone still tell their neighbours they left.
- Tear Loose: a mob gives up its metal only once, so a mob that arms itself afresh can't be milked for weapons.
- Heavy metal rips: diamond, emerald and redstone blocks (and eggs) sound like metal but no longer count as metal.
- The rock ward spends scrap too, not only rocks.
- Classes: BlockCluster.of, GravityMetal, GravityRockOrders.
- Built on CI; not played.
