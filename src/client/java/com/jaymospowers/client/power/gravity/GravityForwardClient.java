package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityForward;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Falling forward (see {@link GravityForward}), the faller's side: while it lasts, "down" is where you look, so you
 * gather speed that way like a fall (and can steer it by looking; the air's drag bends the old speed away). When it
 * ends you keep what you had, and Weightless's own light weight takes over again.
 */
final class GravityForwardClient {
	private GravityForwardClient() {
	}

	static void tick(LocalPlayer player) {
		if (player.getAttached(GravityForward.FALLING) == null) {
			return;
		}
		// what's left of last tick's speed (after the air's drag and whatever you ran into), plus this tick's pull
		Vec3 velocity = player.getDeltaMovement().add(player.getLookAngle().scale(GravityForward.ACCEL));
		if (velocity.length() > GravityForward.MAX_SPEED) {
			velocity = velocity.normalize().scale(GravityForward.MAX_SPEED);
		}
		player.setDeltaMovement(velocity);
		player.resetFallDistance();
		Vec3 behind = player.position().add(0.0, 0.9, 0.0).subtract(velocity.normalize().scale(0.6));
		player.level().addParticle(ParticleTypes.REVERSE_PORTAL, behind.x, behind.y, behind.z, 0.0, 0.0, 0.0);
	}
}
