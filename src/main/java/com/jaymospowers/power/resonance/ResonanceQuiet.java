package com.jaymospowers.power.resonance;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.PowerInput;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * B held, <b>Quiet</b>: for as long as you hold it (up to {@link #MAX_TICKS}) you make no sound at all: no steps, no
 * landing, no hurt noises, no vibrations, so nothing that hunts by ear (sculk, Wardens, the Unseeing, the blind and
 * their rings) hears you. A tap of B is still Hush's dome ({@link ResonanceHush}).
 */
final class ResonanceQuiet {
	/** B held this long is Quiet; let go sooner and it was a tap (Hush). How long Quiet can be held. */
	static final int HOLD_TICKS = 8, MAX_TICKS = 400;

	/** holder → the tick B went down. */
	private static final Map<UUID, Long> DOWN = new HashMap<>();
	private static final Set<UUID> QUIET = new HashSet<>();

	private ResonanceQuiet() {
	}

	static void key(ServerPlayer player, boolean down) {
		UUID id = player.getUUID();
		if (down) {
			DOWN.put(id, player.level().getGameTime());
			return;
		}
		Long at = DOWN.remove(id);
		if (QUIET.contains(id)) {
			stop(player);
		} else if (at != null && ResonancePower.ready(player, ResonanceState.HUSH) && ResonanceHush.cast(player)) {
			ResonancePower.cooldown(player, ResonanceState.HUSH, ResonancePower.HUSH_COOLDOWN);
		}
	}

	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		Long at = DOWN.get(id);
		if (at == null) {
			return;
		}
		long held = player.level().getGameTime() - at;
		if (held > 2 && !PowerInput.isDown(player, PowerKey.ABILITY_4) && !QUIET.contains(id) && held < HOLD_TICKS) {
			// a tap still on its way up: the key-up casts the dome
			return;
		}
		if (held > HOLD_TICKS && !PowerInput.isDown(player, PowerKey.ABILITY_4) || held > MAX_TICKS) {
			// let go without the key-up arriving, or held to the end: no dome for that
			DOWN.remove(id);
			stop(player);
			return;
		}
		if (held >= HOLD_TICKS && QUIET.add(id)) {
			player.setSilent(true);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_quiet").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Whether {@code entity} is holding Quiet. */
	static boolean quiet(Entity entity) {
		return !QUIET.isEmpty() && QUIET.contains(entity.getUUID());
	}

	static void stop(ServerPlayer player) {
		if (QUIET.remove(player.getUUID())) {
			player.setSilent(false);
		}
	}

	static void forget(ServerPlayer player) {
		DOWN.remove(player.getUUID());
		stop(player);
	}

	static void clear() {
		DOWN.clear();
		QUIET.clear();
	}
}
