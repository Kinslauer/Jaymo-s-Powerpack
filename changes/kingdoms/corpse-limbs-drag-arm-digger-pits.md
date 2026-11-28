# diggers drag corpses into pits, sated warrens
- **Diggers drag corpses**: new `DragCorpseGoal` AI goal makes diggers find corpses (Kitchen's `CorpseEntity`) near their warren's pits, path to them, pick them up by a leg, and drag them into the nearest pit.
- **Feeding in pits**: once in a pit, diggers feed on the corpse over time using Kitchen's `nextPart`/`eatPart` via reflection, taking bites every few seconds.
- **Sated warrens**: a warren that has fed recently is sated for 20 minutes (24000 ticks); its diggers stay in their holes and don't pop out to attack players.
- **Player-dragged corpses**: if a player drags a corpse near a pit edge, a digger can pull it in (Andy's sheep fell in).
- **Respects ownership**: diggers won't take player corpses (loot/respawn tie) or corpses from spider brood larders (tag `jaymoskitchen_spider_larder`).
- Not tested in game: digger corpse dragging pathfinding, feeding loop, sated warren behavior, player-dragged corpse pull-in.