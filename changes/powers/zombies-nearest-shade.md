# zombies run for the nearest shade
- **Nearest shade first**: an undead caught in the sun now heads for the closest spot the sun can't reach. That can be inside a house, under an overhang, in a cave, or in the shadow a wall, house or cliff throws on open ground. A house next door now beats a cave down the hill. Tree shade still doesn't count, since sunlight gets through leaves.
- **Only shade it can reach**: a spot behind a shut door is skipped, so zombies no longer stand at a closed house while they burn.
- **Zombies break off a fight for shade**: a zombie chasing you in daylight runs for shade if there's some close by. Once it's out of the sun, it comes straight back for you. With no shade nearby it keeps fighting and burns. Skeletons and strays behave as before here.
- Classes: core/sun/SeekShelterGoal (wall shadows within 12 blocks, reachability check via a real path, fleeMidFight), mixin/SunShelterZombieMixin (goal now priority 1, ahead of the spear and melee attacks).
- Built; not played.
