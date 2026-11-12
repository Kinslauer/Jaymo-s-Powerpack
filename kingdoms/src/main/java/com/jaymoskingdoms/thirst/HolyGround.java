package com.jaymoskingdoms.thirst;

import com.jaymoskingdoms.Temples;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Holy ground, as the thirsty feel it: the land round a patron's temple ({@link Temples}), where they are weakened by day or night. */
final class HolyGround {
	private HolyGround() {
	}

	/** Whether {@code at} is within {@code range} blocks of the centre of any patron's temple. */
	static boolean near(ServerLevel level, Vec3 at, double range) {
		if (level.getServer() == null) {
			return false;
		}
		double limit = range * range;
		for (Temples.Temple t : Temples.all(level.getServer())) {
			double dx = t.center().getX() + 0.5 - at.x, dz = t.center().getZ() + 0.5 - at.z;
			if (dx * dx + dz * dz <= limit) {
				return true;
			}
		}
		return false;
	}
}
