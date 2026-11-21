package com.jaymospowers.power.wind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Riding your own tornado (Andy's Awakening pick: "Wind's cyclone"). Holding a Cyclone, tap Sneak and you step up into the
 * funnel: it lifts you {@link #HEIGHT} blocks off the ground and goes where you look at {@link #SPEED} blocks a tick, and
 * whatever it has caught goes round with you, battered as it goes. Tap Sneak again to step down onto your feet (the funnel
 * roots you where it stands again); letting go of the Cyclone ends the ride as well.
 */
final class WindRide {
	// ---- tuning ----
	/** How high the funnel carries you (blocks off the ground), how fast it goes, how quickly it settles to that height. */
	static final double HEIGHT = 1.6, SPEED = 0.45, SETTLE = 0.25;
	/** Hunger a tick on top of the Cyclone's own. */
	static final float EXHAUSTION = 0.03F;

	private static final Set<UUID> RIDING = new HashSet<>();
	private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();

	private WindRide() {
	}

	static boolean riding(ServerPlayer player) {
		return RIDING.contains(player.getUUID());
	}

	/** Every tick of a Cyclone held: Sneak's tap steps on or off, and the ride moves you. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean sneaking = player.isShiftKeyDown();
		boolean was = WAS_SNEAKING.getOrDefault(id, sneaking);
		WAS_SNEAKING.put(id, sneaking);
		ServerLevel level = player.level();
		if (sneaking && !was) {
			if (RIDING.remove(id)) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.8F, 0.8F);
			} else {
				RIDING.add(id);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.7F);
			}
		}
		if (!RIDING.contains(id)) {
			return;
		}
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(SPEED);
		double ground = ground(level, player);
		double lift = Mth.clamp((ground + HEIGHT - player.getY()) * SETTLE, -0.3, 0.3) + 0.08;
		player.setDeltaMovement(look.x, lift, look.z);
		player.syncVelocity = true;
		player.resetFallDistance();
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
	}

	/** The top of the ground under the player (within a few blocks), or their own height if there is none near. */
	private static double ground(ServerLevel level, ServerPlayer player) {
		BlockPos pos = player.blockPosition();
		for (int i = 0; i < (int)Math.ceil(HEIGHT) + 4; i++) {
			BlockPos below = pos.below(i + 1);
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() || !level.getFluidState(below).isEmpty()) {
				return below.getY() + 1.0;
			}
		}
		return player.getY() - HEIGHT;
	}

	/** The Cyclone ended. */
	static void stop(ServerPlayer player) {
		RIDING.remove(player.getUUID());
		WAS_SNEAKING.remove(player.getUUID());
	}
}
