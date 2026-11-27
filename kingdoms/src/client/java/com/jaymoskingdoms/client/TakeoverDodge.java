package com.jaymoskingdoms.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Andy, 2026-11-25: takeovers get the new MCA fighting AI: "they are the most playerlike and aggressive".) A body a
 * takeover drives sidesteps what's shot at it, as the fighting AI's shield bearers watch every arrow in flight: each
 * projectile within {@code LOOK} blocks is stepped ahead up to {@code AHEAD} ticks (with drag and gravity), and if it
 * would pass within {@code CLOSE} of the body it hops aside, across the shot's line, at most once every
 * {@code EVERY} ticks. Client side: a driven player's motion is their client's.
 */
public final class TakeoverDodge {
	static final double LOOK = 16.0, CLOSE = 0.9, SIDESTEP = 0.55, HOP = 0.25;
	static final int AHEAD = 10, EVERY = 16;
	static final double DRAG = 0.99, GRAVITY = 0.05;

	private static int readyAt;

	private TakeoverDodge() {
	}

	/** True if it sidestepped a shot this tick (the caller leaves the motion alone). */
	public static boolean dodge(LocalPlayer player) {
		if (player.tickCount < readyAt || !player.onGround()) {
			return false;
		}
		AABB body = player.getBoundingBox();
		for (Projectile shot : player.level().getEntitiesOfClass(Projectile.class, body.inflate(LOOK),
			p -> p.isAlive() && p.getOwner() != player && !p.onGround() && p.getDeltaMovement().lengthSqr() > 0.04)) {
			Vec3 at = shot.position();
			Vec3 v = shot.getDeltaMovement();
			AABB near = body.inflate(CLOSE);
			boolean hits = false;
			for (int t = 0; t < AHEAD && !hits; t++) {
				Vec3 next = at.add(v);
				hits = near.clip(at, next).isPresent() || near.contains(next);
				at = next;
				v = v.scale(DRAG).subtract(0.0, shot.isNoGravity() ? 0.0 : GRAVITY, 0.0);
			}
			if (!hits) {
				continue;
			}
			Vec3 line = shot.getDeltaMovement().horizontal();
			line = line.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : line.normalize();
			Vec3 side = new Vec3(-line.z, 0.0, line.x);
			if (player.getRandom().nextBoolean()) {
				side = side.reverse();
			}
			if (!player.level().noCollision(player, body.move(side.scale(1.2)))) {
				side = side.reverse();
				if (!player.level().noCollision(player, body.move(side.scale(1.2)))) {
					continue;
				}
			}
			player.setDeltaMovement(side.x * SIDESTEP, HOP, side.z * SIDESTEP);
			readyAt = player.tickCount + EVERY;
			return true;
		}
		return false;
	}
}
