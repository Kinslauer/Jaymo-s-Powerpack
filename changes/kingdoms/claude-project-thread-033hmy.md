# skeleton quiver off severed parts; worn gear from disarmed mobs
- **Skeleton quivers**: a dead skeleton's tattered quiver was drawn again on its severed head, both arms and both legs. The quiver layer now skips the severed-part copies Jaymo's Powers draws (its `Dismembered.ONLY` render key, read by reflection), so it stays only on the body (`QuiverLayer`).
- **Worn pickups**: Andy: "pickups in general should have low durability". Weapons taken off a downed guard or illager, or knocked out of a mob's hand by a mind or blood spell, now come away with 3-25% of their durability left; 8% of the time it's a good piece (55-90%). A weapon the mob had picked up (a player's own) stays as it was (`WornGear`, used by `Downed`, `MindSpells`, `Hemomancy`).
- Built; not played.
