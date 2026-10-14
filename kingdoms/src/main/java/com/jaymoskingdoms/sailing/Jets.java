package com.jaymoskingdoms.sailing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.18) A Wind user's Wind Whistle or a Smoke user's Smoke Jet (Jaymo's Powers, read by the tag it puts on whoever is
 * using it) filling a ship's sails: each sail in the stream adds to her way along the stream's push, as if the wind blew
 * there and that way. Blown from astern she runs before it; from ahead it backs her; across her, it does little.
 */
final class Jets {
	/** The tags Jaymo's Powers puts on a player while their stream is running. */
	static final String WIND = "jaymospowers_jet:wind", SMOKE = "jaymospowers_jet:smoke";
	/** Each stream's reach (blocks), its width at the hands and how much wider a block further (as the powers have it). */
	static final double WIND_RANGE = 18.0, SMOKE_RANGE = 14.0, MOUTH = 0.35, SPREAD = 0.13;
	/** Her way (blocks/tick) per (square root of) sail it fills, blown straight from astern: a gale's, and smoke's. */
	static final double WIND_PER_SAIL = 0.1, SMOKE_PER_SAIL = 0.05;
	/** How much of it furled sails still catch. */
	static final double FURLED = 0.3;

	private Jets() {
	}

	/** The way (blocks/tick, + ahead) the streams of everyone near blow into her sails, set {@code sail} (0-1). */
	static double blown(ServerLevel level, Ship ship, Hull h, double sail) {
		if (h.sails == 0) {
			return 0.0;
		}
		double total = 0.0;
		float yaw = ship.getYRot();
		Vec3 ahead = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.0, Mth.cos(yaw * Mth.DEG_TO_RAD));
		for (Player player : level.players()) {
			boolean wind = player.entityTags().contains(WIND);
			if (!wind && !player.entityTags().contains(SMOKE)) {
				continue;
			}
			double range = wind ? WIND_RANGE : SMOKE_RANGE;
			if (player.distanceToSqr(ship) > (range + h.radius) * (range + h.radius)) {
				continue;
			}
			Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
			int filled = 0;
			for (Ship.Piece p : h.pieces) {
				if (!(p.blockState().getBlock() instanceof SailBlock)) {
					continue;
				}
				Vec3 d = ship.toWorld(p.x(), p.y() + 0.5, p.z()).subtract(eye);
				double t = d.dot(look);
				if (t > 0.0 && t < range && d.subtract(look.scale(t)).length() < MOUTH + t * SPREAD + 0.7) {
					filled++;
				}
			}
			if (filled > 0) {
				// only its push along her keel moves her
				total += (wind ? WIND_PER_SAIL : SMOKE_PER_SAIL) * Math.sqrt(filled) * look.dot(ahead);
			}
		}
		return total * (FURLED + (1.0 - FURLED) * sail);
	}
}
