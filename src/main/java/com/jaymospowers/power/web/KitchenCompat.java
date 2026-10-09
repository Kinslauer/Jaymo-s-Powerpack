package com.jaymospowers.power.web;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Jaymo's Kitchen, if it's installed (it's in the pack, but Powers doesn't need it): the corpse a player is carrying. */
final class KitchenCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");

	private KitchenCompat() {
	}

	static @Nullable Entity carriedCorpse(Player player) {
		return LOADED ? Kitchen.carried(player) : null;
	}

	/** Only touched when Jaymo's Kitchen is loaded. */
	private static final class Kitchen {
		static @Nullable Entity carried(Player player) {
			return com.jaymoskitchen.corpse.CorpseEntity.carriedBy(player);
		}
	}
}
