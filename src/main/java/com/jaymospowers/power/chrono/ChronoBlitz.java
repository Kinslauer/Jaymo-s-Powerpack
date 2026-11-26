package com.jaymospowers.power.chrono;

import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Blitz poses: when a Chrono's fists (Chrono Punch, the held flurry) land on a foe, the foe is knocked into one of
 * {@link ChronoFlow#STRIKE_POSES} different strike poses ({@code ChronoReelPose} draws them), and one foe in
 * {@link #DROP_ONE_IN} struck lets go of what it was holding. Every foe struck takes the next pose of a cycle that starts
 * at a random pose for each runner, so foes hit together never share a pose (up to six) and the same one never comes twice running.
 */
final class ChronoBlitz {
	/** One foe in this many drops its held item. */
	static final int DROP_ONE_IN = 5;
	/** A dropped item can't be picked up for this long (ticks), so it isn't snatched straight back by the foe or the runner. */
	static final int DROP_PICKUP_DELAY = 20;

	/** The next pose to give, per runner. */
	private static final Map<UUID, Integer> NEXT = new HashMap<>();

	private ChronoBlitz() {
	}

	/** {@code target} has just been struck by {@code player}'s fists: it takes the next pose, and may drop what it holds. */
	static void struck(ServerPlayer player, LivingEntity target) {
		if (!target.isAlive()) {
			return;
		}
		int n = NEXT.compute(player.getUUID(), (id, was) -> was == null ? player.getRandom().nextInt(ChronoFlow.STRIKE_POSES) : was + 1);
		int pose = Math.floorMod(n, ChronoFlow.STRIKE_POSES);
		int side = player.getRandom().nextBoolean() ? 1 : -1;
		ChronoFlow.reel(target, ChronoFlow.STRIKE, side, pose);
		if (player.getRandom().nextInt(DROP_ONE_IN) == 0) {
			drop(player, target);
		}
	}

	/** The foe's main-hand item falls at its feet (a player foe's too); nothing happens to an empty hand. */
	private static void drop(ServerPlayer player, LivingEntity target) {
		ItemStack held = target.getMainHandItem();
		if (held.isEmpty() || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ItemEntity item = target.spawnAtLocation(level, held, target.getBbHeight() * 0.5F);
		if (item != null) {
			item.setPickUpDelay(DROP_PICKUP_DELAY);
			MagicFx.burst(level, item.getX(), item.getY() + 0.3, item.getZ(), 0xFFE08A, 0.6F, 4, 0.15, 0.15, 0.15);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	/** A runner leaves (their cycle is forgotten). */
	static void forget(ServerPlayer player) {
		NEXT.remove(player.getUUID());
	}
}
