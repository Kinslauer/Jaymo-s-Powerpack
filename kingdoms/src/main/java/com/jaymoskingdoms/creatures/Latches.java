package com.jaymoskingdoms.creatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/**
 * Getting something off you (the blood bats, the cloak flier). Nothing is typed: a tug of the empty hand (use with nothing in
 * the main hand) pulls at what clings to you, and a friend can take hold of it and pull (use on it). Either way it is a try
 * that Might helps and that may fail; a pulled bite is torn. Hitting it, with anything, knocks it loose too.
 */
public final class Latches {
	/** The reach (blocks) of a tug at what clings to you, and the wait (ticks) between tugs. */
	static final double REACH = 2.5;
	static final int WAIT = 12;

	private static final Map<UUID, Long> TUGGED = new HashMap<>();

	private Latches() {
	}

	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && player.getMainHandItem().isEmpty()
				&& hand == net.minecraft.world.InteractionHand.MAIN_HAND && !sp.isSpectator()) {
				tug(server, sp, sp);
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && entity instanceof Clinger c
				&& hand == net.minecraft.world.InteractionHand.MAIN_HAND && c.host(server) != null && ready(sp)) {
				c.pulled(server, sp);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> TUGGED.clear());
	}

	private static boolean ready(ServerPlayer by) {
		long now = by.level().getGameTime();
		if (now < TUGGED.getOrDefault(by.getUUID(), 0L)) {
			return false;
		}
		TUGGED.put(by.getUUID(), now + WAIT);
		return true;
	}

	/** {@code by} tugs at what clings to {@code host}. */
	static void tug(ServerLevel level, ServerPlayer by, LivingEntity host) {
		List<Clinger> on = level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(1.0), e -> e instanceof Clinger c && c.host(level) == host)
			.stream().map(e -> (Clinger) e).toList();
		if (on.isEmpty() || !ready(by)) {
			return;
		}
		on.get(0).pulled(level, by);
	}
}
