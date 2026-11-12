package com.jaymospowers.power.blindness;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Sound paints the dark: every noise near someone who can't see ({@link Blind#sightless}) ripples on their dark screen
 * from where it came. A noise here is a vibration (a step, a blow, a door, a block broken, a shot landing: whatever
 * sculk would hear), taken from {@code ResonanceGameEventMixin}, so Hush and a sneaking Resonance holder make none.
 * A louder one (a wider vibration) throws a bigger ring. Their own steps ripple too: it's how they find the walls.
 */
public final class BlindnessEcholocation {
	/** How far off a noise is still heard (blocks), and the most rings one listener is sent a tick. */
	static final double RANGE = 32.0;
	static final int PER_TICK = 4;
	/** A noise made on purpose (a Resonance holder's Pulse or Echolocation, a Shriek) for those it left in the dark. */
	public static final String PULSE = "pulse";

	/** listener → the tick last counted, and how many rings went out in it. */
	private static final Map<UUID, long[]> SENT = new HashMap<>();

	private BlindnessEcholocation() {
	}

	/** A vibration at {@code pos}: everyone sightless within earshot gets its ring. */
	public static void onVibration(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
		if (level.players().isEmpty()) {
			return;
		}
		float volume = Math.min(1.0F, event.value().notificationRadius() / 16.0F);
		Entity source = context.sourceEntity();
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos) > RANGE * RANGE || !Blind.sightless(player)) {
				continue;
			}
			long now = level.getGameTime();
			long[] sent = SENT.computeIfAbsent(player.getUUID(), id -> new long[2]);
			if (sent[0] != now) {
				sent[0] = now;
				sent[1] = 0;
			}
			if (sent[1]++ >= PER_TICK) {
				continue;
			}
			ring(player, pos, source == player ? volume * 0.6F : volume, event.unwrapKey().map(key -> key.identifier().toString()).orElse(""));
		}
	}

	/** One ring on {@code player}'s screen from {@code pos}. */
	public static void ring(ServerPlayer player, Vec3 pos, float volume, String sound) {
		ServerPlayNetworking.send(player, new BlindnessEcho(pos.x, pos.y, pos.z, (float) Math.sqrt(player.distanceToSqr(pos)), volume, sound));
	}

	static void forget(ServerPlayer player) {
		SENT.remove(player.getUUID());
	}

	static void clear() {
		SENT.clear();
	}
}
