package com.jaymospowers.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * The fight meter. Hidden: nothing on screen ever shows it. It fills with the harm a player deals and takes in a real
 * fight (something alive that fights back: another player, a monster, a mob that has a target), {@link #DEALT} a point
 * dealt and {@link #TAKEN} a point taken, up to {@link #FULL}. {@link #QUIET} ticks after the last blow either way it
 * starts to drain, {@link #DRAIN} a tick.
 * <p>
 * What it is for: the things a brush with death can wake (a Z Fighter's False Super and his first Super, a mana spirit's
 * waking in Kingdoms) only come up while it stands past {@link #EDGE} ({@link #past}), so they are earned in a fight
 * and not by standing in a fire. Not saved: it is a matter of the last minute.
 */
public final class Brink {
	public static final float FULL = 100.0F, EDGE = 80.0F;
	static final float DEALT = 1.0F, TAKEN = 3.0F;
	static final int QUIET = 100;
	static final float DRAIN = 0.5F;

	/** Each player's meter as it stood at their last blow either way, and when that was (game time). */
	private static final Map<UUID, float[]> METER = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Brink() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || attacker == entity) {
				return;
			}
			if (entity instanceof ServerPlayer player) {
				fill(player, taken * TAKEN);
			}
			if (attacker instanceof ServerPlayer player && (entity instanceof Player || entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() != null)) {
				fill(player, taken * DEALT);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			METER.clear();
			LAST.clear();
		});
	}

	private static void forget(UUID id) {
		METER.remove(id);
		LAST.remove(id);
	}

	private static void fill(ServerPlayer player, float by) {
		float now = Math.min(FULL, level(player) + by);
		METER.put(player.getUUID(), new float[] {now});
		LAST.put(player.getUUID(), player.level().getGameTime());
	}

	/** Where {@code player}'s meter stands now (0 to {@link #FULL}). */
	public static float level(Player player) {
		float[] at = METER.get(player.getUUID());
		if (at == null) {
			return 0.0F;
		}
		long quiet = player.level().getGameTime() - LAST.getOrDefault(player.getUUID(), 0L) - QUIET;
		return quiet <= 0 ? at[0] : Math.max(0.0F, at[0] - quiet * DRAIN);
	}

	/** Whether {@code player} is far enough into a real fight for a brush with death to wake something. */
	public static boolean past(Player player) {
		return level(player) >= EDGE;
	}
}
