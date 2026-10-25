package com.jaymoskitchen.season;

import com.jaymoskitchen.JaymosKitchen;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * (0.5.6) Winter bites (Andy: "very harsh. yes."). (0.5.25) Body heat ({@link com.jaymoskitchen.climate.BodyHeat}) now
 * does it, the year round: winter's chill makes temperate lands cold, and the cold burns food. What's left here is what
 * other mods already use: who the cold can't touch ({@link #setColdProof}: Kingdoms' Frostborn) and the clothes that
 * keep it off ({@link #WARM_CLOTHING}: Kingdoms' wool clothes, and leather).
 */
public final class WinterCold {
	/** Worn clothing that keeps the cold off (body heat counts the pieces). */
	public static final TagKey<Item> WARM_CLOTHING = TagKey.create(Registries.ITEM, JaymosKitchen.id("warm_clothing"));
	private static Predicate<Player> coldProof = player -> false;

	private WinterCold() {
	}

	/** For other mods (by reflection): who the winter cold doesn't touch. */
	public static void setColdProof(Predicate<Player> proof) {
		coldProof = proof;
	}

	/** Whether the cold can't touch this player (Kingdoms' Frostborn): {@code BodyHeat} won't push them past chilly. */
	public static boolean coldProof(Player player) {
		return coldProof.test(player);
	}
}
