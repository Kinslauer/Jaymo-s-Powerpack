package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Andy (2026-11-24): townsfolk "flee if you unsheathe and swing or shoot, or loose an arrow within city limits". A player
 * who swings a weapon ({@link BladeClash#armed}) or looses anything (an arrow, a bolt, a shot) inside a settlement's
 * limits sends the villagers near them running ({@link Frights#scare}); guards and the brave stand ({@link TakeCover}).
 */
public final class ArmedFright {
	/** Villagers this near the player run. */
	static final double NEAR = 24.0;
	/** A settlement's limits: its land, or this near its seat when it has none ({@link Factions#around}). */
	static final double LIMITS = 96.0;
	/** One fright per player at most this often (ticks), so a flurry of swings isn't a flurry of searches. */
	static final int EVERY_TICKS = 20;
	/** A projectile slower than this when it appears was loaded from the ground, not loosed. */
	static final double LOOSED_SPEED_SQR = 0.01;

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private ArmedFright() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile shot && shot.getOwner() instanceof ServerPlayer player
				&& shot.getDeltaMovement().lengthSqr() > LOOSED_SPEED_SQR) {
				alarm(level, player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/** From the swing mixin: a weapon swung, at a foe or at the air. */
	public static void swung(LivingEntity entity) {
		if (entity instanceof ServerPlayer player && entity.level() instanceof ServerLevel level && BladeClash.armed(player)) {
			alarm(level, player);
		}
	}

	private static void alarm(ServerLevel level, ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < EVERY_TICKS) {
			return;
		}
		LAST.put(player.getUUID(), now);
		Faction f = Factions.around(level.getServer(), level, player.blockPosition(), LIMITS);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR), Villager::isAlive)) {
			if (Temperaments.fearless(v) || BlastFright.standsGround(v) || v.isSleeping() || Downed.isDowned(v)
				|| v.hasAttached(Captives.CAPTIVE) || Thralls.isThrall(v)) {
				continue;
			}
			Frights.scare(level, v, player.position());
		}
	}
}
