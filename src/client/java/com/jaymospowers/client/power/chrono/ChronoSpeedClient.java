package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoSpeed;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * (0.10.28) Overclock's wall run, on the local player (movement is the client's to decide, so it's smooth): overclocked,
 * running into a wall with forward held, you run straight up it at {@link ChronoSpeed#WALL_CLIMB}, for up to
 * {@link ChronoSpeed#WALL_TICKS} before you need ground again. Sneak to let go.
 */
final class ChronoSpeedClient {
	private static int wallTicks;

	private ChronoSpeedClient() {
	}

	static void reset() {
		wallTicks = 0;
		ChronoSpeed.clientOverclockedAt = Long.MIN_VALUE;
	}

	static void tick(LocalPlayer player, boolean overclocked) {
		if (overclocked) {
			ChronoSpeed.clientOverclockedAt = player.level().getGameTime();
		}
		if (!overclocked || player.onGround() || player.isInWater()) {
			wallTicks = 0;
		}
		if (!overclocked || !player.horizontalCollision || wallTicks >= ChronoSpeed.WALL_TICKS || !player.input.keyPresses.forward()
			|| player.input.keyPresses.shift() || player.getAbilities().flying || player.isPassenger() || player.isFallFlying()
			|| player.onClimbable() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		wallTicks++;
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x, ChronoSpeed.WALL_CLIMB, v.z);
		if (wallTicks % 2 == 0) {
			player.level().addParticle(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.1, player.getZ(), 0.0, -0.05, 0.0);
		}
	}
}
