package com.jaymoskitchen.ragdoll;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * (0.5.0) Living players knocked limp: a crash, a blast, a cart running them over, a hard landing. Their own client
 * simulates the ragdoll; the server relays it and keeps the last one (a limp player who dies leaves that body).
 */
public final class LivingRagdolls {
	private LivingRagdolls() {
	}

	public static void register() {
	}

	/** True while {@code entity} is a player lying limp. */
	public static boolean isLimp(Entity entity) {
		return false;
	}

	/** The body {@code player} lies in now if they're limp (a copy, at rest), else null. */
	public static RagdollBody lastBody(ServerPlayer player) {
		return null;
	}
}
