package com.jaymospowers.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Client halves of powers, by power id. */
public final class PowerClients {
	private static final Map<Identifier, PowerClient> ALL = new HashMap<>();

	private PowerClients() {
	}

	public static void register(Identifier power, PowerClient client) {
		if (ALL.putIfAbsent(power, client) != null) {
			throw new IllegalStateException("power client registered twice: " + power);
		}
	}

	public static @Nullable PowerClient get(@Nullable Identifier power) {
		return power == null ? null : ALL.get(power);
	}
}
