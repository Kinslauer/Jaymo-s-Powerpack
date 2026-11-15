package com.jaymospowers.core;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

/**
 * Jaymo's Kingdoms' Agility stat, as Jaymo's Powers' climbs see it: its share of walking speed goes on climbing walls and
 * through leaves, ledge shimmies and climbing over ledges too (Kingdoms' {@code Stats.agility}, by reflection, so Powers
 * never compiles against Kingdoms). Without Kingdoms it changes nothing. Either side: the points are synced to their own
 * player.
 */
public final class Agility {
	private static final Method AGILITY;

	static {
		Method agility = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				agility = Class.forName("com.jaymoskingdoms.Stats").getMethod("agility", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				agility = null;
			}
		}
		AGILITY = agility;
	}

	private Agility() {
	}

	/** What {@code player}'s Agility multiplies a traversal speed by (1 without Kingdoms; 100 points: 1.15). */
	public static double factor(Player player) {
		if (AGILITY == null) {
			return 1.0;
		}
		try {
			return 1.0 + (double) AGILITY.invoke(null, player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1.0;
		}
	}
}
