package com.jaymospowers.core;

import com.jaymospowers.api.PowerKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/** Which ability keys each player is holding, as last reported by their client. Server side. */
public final class PowerInput {
	private static final Map<UUID, Integer> HELD = new HashMap<>();

	private PowerInput() {
	}

	public static boolean isDown(Player player, PowerKey key) {
		return (HELD.getOrDefault(player.getUUID(), 0) & (1 << key.ordinal())) != 0;
	}

	static void set(Player player, PowerKey key, boolean down) {
		int bits = HELD.getOrDefault(player.getUUID(), 0);
		bits = down ? bits | (1 << key.ordinal()) : bits & ~(1 << key.ordinal());
		HELD.put(player.getUUID(), bits);
	}

	/** (0.17.108) Every key's held bit at once ({@link SwallowedPower} swaps them for its own while it runs). */
	static int bits(Player player) {
		return HELD.getOrDefault(player.getUUID(), 0);
	}

	static void setBits(Player player, int bits) {
		if (bits == 0) {
			HELD.remove(player.getUUID());
		} else {
			HELD.put(player.getUUID(), bits);
		}
	}

	static void clear(Player player) {
		HELD.remove(player.getUUID());
	}
}
