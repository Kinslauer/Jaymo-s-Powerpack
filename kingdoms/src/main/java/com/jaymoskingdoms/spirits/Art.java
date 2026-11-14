package com.jaymoskingdoms.spirits;

import net.minecraft.server.level.ServerPlayer;

/** A {@link Move} written as its numbers and what casting it does (the later arts of Grimsby and Varro, and Two as One). */
record Art(String id, float cost, int cooldown, int level, int pose, int intent, Cast casting) implements Move {
	@FunctionalInterface
	interface Cast {
		boolean cast(ServerPlayer player, SpiritState state);
	}

	@Override
	public boolean cast(ServerPlayer player, SpiritState state, int charge) {
		return casting.cast(player, state);
	}
}
