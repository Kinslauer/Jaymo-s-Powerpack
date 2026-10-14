package com.jaymospowers.power.smoke;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * G (hold), Smokescreen: smoke pours out of you for as long as you hold it (up to {@link #MAX_TICKS}), and the
 * {@link SmokeField} does the rest: it billows round you, climbs, fills the room. The cooldown grows with how long you
 * poured.
 */
final class SmokeScreen {
	/** Smoke a tick (a block's worth is 1; a thick, hiding cloud is 3 a block), and how long one pour lasts. */
	static final float POUR = 2.0F;
	static final int MAX_TICKS = 200;
	/** Hunger a tick while pouring. */
	static final float EXHAUSTION = 0.02F;

	/** Players pouring now, and since when (game time). */
	private static final Map<UUID, Long> POURING = new HashMap<>();

	private SmokeScreen() {
	}

	static void start(ServerPlayer player) {
		long now = player.level().getGameTime();
		POURING.put(player.getUUID(), now);
		SmokePower.set(player, SmokePower.state(player).withPour(now));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	/** Stops pouring; with {@code cooldown}, the cooldown starts (longer the longer you poured). */
	static void stop(ServerPlayer player, boolean cooldown) {
		Long from = POURING.remove(player.getUUID());
		if (from == null) {
			return;
		}
		SmokeState state = SmokePower.state(player).withPour(-1L);
		SmokePower.set(player, state);
		if (cooldown) {
			long held = player.level().getGameTime() - from;
			float share = Mth.clamp(held / (float)max(player), 0.0F, 1.0F);
			SmokePower.cooldown(player, SmokeState.SCREEN, Math.round(Mth.lerp(share, SmokePower.SCREEN_COOLDOWN_MIN, SmokePower.SCREEN_COOLDOWN_MAX)));
		}
	}

	static void tick(ServerPlayer player) {
		Long from = POURING.get(player.getUUID());
		if (from == null) {
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			stop(player, true);
			return;
		}
		ServerLevel level = player.level();
		long held = level.getGameTime() - from;
		if (held >= max(player)) {
			stop(player, true);
			return;
		}
		// it pours from the chest, and a little ahead when you move, so it trails behind you
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0);
		SmokeField.puff(level, at, POUR, player);
		player.causeFoodExhaustion(EXHAUSTION);
		if (held % 12 == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 0.7F, 0.6F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	private static int max(ServerPlayer player) {
		return MAX_TICKS;
	}

	static void clear() {
		POURING.clear();
	}
}
