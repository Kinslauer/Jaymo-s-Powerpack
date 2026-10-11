package com.jaymospowers.client.power.resonance;

import com.jaymospowers.power.resonance.ResonanceNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * What this client hears: the creatures Echolocation, a Tuning Fork or a Shriek picked out (outlined through walls on
 * this screen alone, see {@code ResonanceGlowMixin}), and the directions of unseen blows (Heavy hearing). Works for
 * anyone (the Tuning Fork isn't only for Resonance users).
 */
public final class ResonanceSenseClient {
	/** A flash on the heartbeat ring: where an unseen blow came from, until when. */
	record Flash(double x, double z, long until) {
	}

	static final int FLASH_TICKS = 30;

	/** entity id → game time it stops being heard. */
	private static final Map<Integer, Long> SENSED = new HashMap<>();
	/** entity id → game time its heartbeat stops showing on the ring (Shriek). */
	private static final Map<Integer, Long> HEARTBEATS = new HashMap<>();
	private static final List<Flash> FLASHES = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;

	private ResonanceSenseClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ResonanceNet.Sense.TYPE, (payload, context) -> {
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			long until = level.getGameTime() + payload.ticks();
			Map<Integer, Long> into = payload.kind() == ResonanceNet.Sense.HEARTBEAT ? HEARTBEATS : SENSED;
			for (int id : payload.ids()) {
				into.merge(id, until, Math::max);
				SENSED.merge(id, until, Math::max);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ResonanceNet.Heard.TYPE, (payload, context) -> {
			ClientLevel level = Minecraft.getInstance().level;
			if (level != null) {
				FLASHES.add(new Flash(payload.x(), payload.z(), level.getGameTime() + FLASH_TICKS));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ResonanceSenseClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			SENSED.clear();
			HEARTBEATS.clear();
			FLASHES.clear();
		}
		if (level == null) {
			return;
		}
		long now = level.getGameTime();
		SENSED.values().removeIf(until -> until <= now);
		HEARTBEATS.values().removeIf(until -> until <= now);
		for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext();) {
			if (it.next().until <= now) {
				it.remove();
			}
		}
	}

	/** Whether this client hears {@code entity} right now (it's drawn outlined). */
	public static boolean sensed(Entity entity) {
		if (SENSED.isEmpty()) {
			return false;
		}
		Long until = SENSED.get(entity.getId());
		return until != null && entity.level().getGameTime() < until;
	}

	static Map<Integer, Long> heartbeats() {
		return HEARTBEATS;
	}

	static List<Flash> flashes() {
		return FLASHES;
	}
}
