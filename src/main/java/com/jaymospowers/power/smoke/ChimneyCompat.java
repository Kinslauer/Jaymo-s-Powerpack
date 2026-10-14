package com.jaymospowers.power.smoke;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Advanced Chimneys (EnderLanky, CC BY-ND 4.0; shipped unchanged in the pack, none of its code or art is used here).
 * Its chimneys, ducts, pipes and pumps carry the Smoke power's gas too: {@link SmokeField} lets smoke into them and
 * draws it up and along them, and its vents let it out. Only block ids are read (its {@code adchimneys} namespace), so
 * nothing breaks when it's missing or changes its classes.
 */
final class ChimneyCompat {
	static final String NAMESPACE = "adchimneys";
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(NAMESPACE);

	private ChimneyCompat() {
	}

	/** A chimney, duct, pipe or pump: smoke inside rides it. */
	static boolean conduit(BlockState state) {
		if (!LOADED) {
			return false;
		}
		var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return NAMESPACE.equals(id.getNamespace()) && !id.getPath().endsWith("_vent");
	}

	/** A vent: where smoke leaves the chimney network, into the open. */
	static boolean vent(BlockState state) {
		if (!LOADED) {
			return false;
		}
		var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return NAMESPACE.equals(id.getNamespace()) && id.getPath().endsWith("_vent");
	}
}
