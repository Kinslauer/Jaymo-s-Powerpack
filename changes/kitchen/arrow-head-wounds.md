# arrow wounds in heads
- **Arrow wounds**: an arrow stuck in anyone's head (players, villagers, traders, mobs, and the corpses that keep their arrows) now shows a wound where it went in: a small dark puncture with a bruised rim and, on the side of the head, a thin dried trickle running down. Dark and dull, in line with the toned-down blood. It lasts exactly as long as the arrow: pulled out of a corpse, or fallen out of a player, it's gone.
- On players it sits just over the skin's outer (hat) layer so painted-on hair doesn't hide it.
- `client/archery/HeadWounds` (new), `StuckArrowsLayer` (marks head parts by name), `mixin/StuckInBodyLayerMixin` (the game's own player arrow layer), texture `textures/entity/arrow_wound.png`.
- Built and checked in a dev game (Kitchen alone): villagers, a wandering trader and a player with arrows in their heads.
