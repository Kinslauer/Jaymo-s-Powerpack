package com.jaymospowers.alchemy.flux;

/** (stub: filled in by its track; see docs/Alchemy-Plan.md) */
public final class FluxEffects {
	public static net.minecraft.world.level.block.Block WARD_STONE;

	private FluxEffects() {
	}

	public static void init() {
	}

	public static void burst(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, int amount) {
		com.jaymospowers.alchemy.Flux.add(level, pos, amount);
	}
}
