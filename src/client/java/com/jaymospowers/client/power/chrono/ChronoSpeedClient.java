package com.jaymospowers.client.power.chrono;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.chrono.ChronoSpeed;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
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
	/** Air-run strides taken since the ground was last touched, and the tick of the last one. */
	private static int strides;
	private static int lastStride;

	private ChronoSpeedClient() {
	}

	public static void reset() {
		if (wall != null && ClientPlayNetworking.canSend(ChronoSpeed.WallRun.TYPE)) {
			ClientPlayNetworking.send(new ChronoSpeed.WallRun(-1));
		}
		wall = null;
		ChronoMomentumClient.reset();
		ChronoSpeedKeys.reset();
		ChronoSpeed.clientOverclockedAt = Long.MIN_VALUE;
	}

	/**
	 * Run up the air: in top gear, holding Jump off the ground gives a kick up every {@link ChronoSpeed#AIR_GAP} ticks or so
	 * (as the last one fades), {@link ChronoSpeed#AIR_STRIDES} of them to a ground touch, like invisible steps. It starts
	 * from a sprint, and goes on once started.
	 */
	private static void airRun(LocalPlayer player, boolean able) {
		if (player.onGround() || player.isInWater() || player.onClimbable() || player.getAbilities().flying || player.isFallFlying()) {
			strides = 0;
			return;
		}
		if (!able || strides >= ChronoSpeed.AIR_STRIDES || !player.input.keyPresses.jump() || player.isPassenger()
			|| com.jaymospowers.power.chrono.ChronoGears.gear(player) < com.jaymospowers.power.chrono.ChronoGears.GEARS
			|| strides == 0 && !player.isSprinting() || player.tickCount - lastStride < ChronoSpeed.AIR_GAP
			|| player.getDeltaMovement().y > 0.2 || player.isSpectator() || com.jaymospowers.core.wounds.Downed.isDowned(player)) {
			return;
		}
		strides++;
		lastStride = player.tickCount;
		player.setDeltaMovement(player.getDeltaMovement().x, ChronoSpeed.AIR_KICK, player.getDeltaMovement().z);
		player.resetFallDistance();
		player.level().addParticle(MagicFx.CRACKLE, player.getX(), player.getY(), player.getZ(), 0.0, 0.05, 0.0);
		player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_STEP,
			net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.4F + strides * 0.1F, false);
	}

	public static void tick(LocalPlayer player, boolean overclocked) {
		if (overclocked) {
			ChronoSpeed.clientOverclockedAt = player.level().getGameTime();
		}
		boolean able = overclocked && player.input.keyPresses.forward() && !player.input.keyPresses.shift()
			&& !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying() && !player.onClimbable()
			&& !player.isSpectator() && player.isAlive() && !com.jaymospowers.core.wounds.Downed.isDowned(player);
		Direction was = wall;
		Direction now = null;
		if (able && player.horizontalCollision) {
			// the wall you face; held against it so you climb straight, not sliding along it
			now = player.getDirection();
			player.setDeltaMovement(now.getStepX() * ChronoSpeed.WALL_STICK, ChronoSpeed.WALL_CLIMB, now.getStepZ() * ChronoSpeed.WALL_STICK);
			if (player.tickCount % 2 == 0) {
				player.level().addParticle(MagicFx.CRACKLE, player.getX(), player.getY() + 0.1, player.getZ(), 0.0, -0.05, 0.0);
			}
		} else if (able && was != null && !player.onGround()) {
			// ran off the top: hop up and over onto it
			player.setDeltaMovement(was.getStepX() * ChronoSpeed.WALL_TOP_IN, ChronoSpeed.WALL_TOP_UP, was.getStepZ() * ChronoSpeed.WALL_TOP_IN);
		}
		wall = now;
		airRun(player, overclocked && now == null);
		ChronoMomentumClient.tick(player, overclocked && now == null);
		ChronoRapidClient.tick(player, overclocked);
		ChronoSpeedKeys.tick(player, overclocked);
		if (now != was && ClientPlayNetworking.canSend(ChronoSpeed.WallRun.TYPE)) {
			ClientPlayNetworking.send(new ChronoSpeed.WallRun(now == null ? -1 : now.get3DDataValue()));
		}
	}
}
