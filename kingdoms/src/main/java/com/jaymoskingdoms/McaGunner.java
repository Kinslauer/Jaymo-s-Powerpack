package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

/**
 * (0.7.295) Andy, retiring the folk: a cannon's gunner ({@link Cannons}) is, with MCA, an MCA archer of the realm's,
 * kept to the cannon's post ({@link WallArchers}) and seated on it.
 */
final class McaGunner {
	private McaGunner() {
	}

	/** With MCA, one of {@code f}'s villagers made an archer and seated on {@code cannon} (its old gunner, if any, still aboard). True if so. */
	static boolean seat(ServerLevel level, Faction f, Cannon cannon) {
		if (!Workers.inPlace(f, Folk.ARCHER)) {
			return false;
		}
		BlockPos post = cannon.post() != null ? cannon.post() : cannon.blockPosition();
		Worker w = McaCompat.enlist(level, f, Folk.ARCHER, post, post, 1);
		if (w == null) {
			return false;
		}
		PathfinderMob archer = w.mob();
		cannon.ejectPassengers();
		archer.snapTo(cannon.getX(), cannon.getY(), cannon.getZ(), cannon.getYRot(), 0.0F);
		archer.startRiding(cannon);
		return true;
	}
}
