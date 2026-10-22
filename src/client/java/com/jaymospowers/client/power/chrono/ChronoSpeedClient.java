package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoSpeed;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import org.jspecify.annotations.Nullable;

/**
 * (0.10.28) Overclock's wall run, on the local player (movement is the client's to decide, so it's smooth): overclocked,
 * running into a wall with forward held, you run straight up it at {@link ChronoSpeed#WALL_CLIMB}. Sneak to let go.
 *
 * <p>(0.11.40) Straight up: held against the wall you face, with no sideways drift and no time limit while you hold
 * forward; run off the top and you hop onto it. The server is told which wall, so everyone sees the body turned onto it,
 * legs running ({@link ChronoSpeed#WALL_RUN}).
 *
 * <p>(0.15.97) Velox's Velocity runs up walls the same way: {@code XenoformPowerClient} ticks this too.
 */
public final class ChronoSpeedClient {
	/** The wall being run up (into it), or null. */
	private static @Nullable Direction wall;

	private ChronoSpeedClient() {
	}

	public static void reset() {
		if (wall != null && ClientPlayNetworking.canSend(ChronoSpeed.WallRun.TYPE)) {
			ClientPlayNetworking.send(new ChronoSpeed.WallRun(-1));
		}
		wall = null;
		ChronoSpeed.clientOverclockedAt = Long.MIN_VALUE;
	}

	public static void tick(LocalPlayer player, boolean overclocked) {
		if (overclocked) {
			ChronoSpeed.clientOverclockedAt = player.level().getGameTime();
		}
		boolean able = overclocked && player.input.keyPresses.forward() && !player.input.keyPresses.shift()
			&& !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying() && !player.onClimbable()
			&& !player.isSpectator() && player.isAlive();
		Direction was = wall;
		Direction now = null;
		if (able && player.horizontalCollision) {
			// the wall you face; held against it so you climb straight, not sliding along it
			now = player.getDirection();
			player.setDeltaMovement(now.getStepX() * ChronoSpeed.WALL_STICK, ChronoSpeed.WALL_CLIMB, now.getStepZ() * ChronoSpeed.WALL_STICK);
			if (player.tickCount % 2 == 0) {
				player.level().addParticle(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.1, player.getZ(), 0.0, -0.05, 0.0);
			}
		} else if (able && was != null && !player.onGround()) {
			// ran off the top: hop up and over onto it
			player.setDeltaMovement(was.getStepX() * ChronoSpeed.WALL_TOP_IN, ChronoSpeed.WALL_TOP_UP, was.getStepZ() * ChronoSpeed.WALL_TOP_IN);
		}
		wall = now;
		if (now != was && ClientPlayNetworking.canSend(ChronoSpeed.WallRun.TYPE)) {
			ClientPlayNetworking.send(new ChronoSpeed.WallRun(now == null ? -1 : now.get3DDataValue()));
		}
	}
}
