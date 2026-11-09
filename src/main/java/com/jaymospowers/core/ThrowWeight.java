package com.jaymospowers.core;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Andy: "there should also be weight to every entity thrown, meaning you cant throw a person as far as you could a
 * cricket or mouse". What a thrown thing weighs (kilograms, near enough): from {@link #KILOGRAMS} for the types listed,
 * else from its hitbox's bulk at {@link #DENSITY}. A throw's speed goes as ({@link #PERSON_KG} / weight) to the
 * {@link #CURVE}, between {@link #HEAVIEST} and {@link #LIGHTEST}: a person flies a few blocks, a cat about twice as
 * far, a cricket or a mouse three times, a cow or a horse barely clears your feet. Light things also spin faster.
 */
public final class ThrowWeight {
	/** A grown person (the standard throw). */
	public static final double PERSON_KG = 75.0;
	/** Kilograms per cubic block of hitbox, for types not listed (a person's box, 0.6 x 0.6 x 1.8, comes to about 75). */
	public static final double DENSITY = 115.0;
	/** A baby weighs this share of its grown weight. */
	public static final double BABY_SHARE = 0.2;
	/** A carried block (Carry's own block hold): a heavy stone's worth. */
	public static final double BLOCK_KG = 40.0;
	/** How steeply speed falls off with weight, and the most and least a throw is scaled by. */
	public static final double CURVE = 0.3, LIGHTEST = 3.2, HEAVIEST = 0.3;
	/** Never lighter than this (a butterfly's mass would fling it to the moon). */
	public static final double MIN_KG = 0.02;

	/** Listed weights (kilograms), by entity type id. Modded types are only read if that mod is loaded. */
	static final Map<String, Double> KILOGRAMS = Map.ofEntries(
		Map.entry("minecraft:player", 75.0),
		Map.entry("minecraft:villager", 70.0),
		Map.entry("minecraft:wandering_trader", 70.0),
		Map.entry("minecraft:cat", 4.0),
		Map.entry("minecraft:ocelot", 9.0),
		Map.entry("minecraft:parrot", 0.4),
		Map.entry("minecraft:chicken", 2.5),
		Map.entry("minecraft:rabbit", 2.0),
		Map.entry("minecraft:frog", 0.4),
		Map.entry("minecraft:bat", 0.05),
		Map.entry("minecraft:bee", 0.05),
		Map.entry("minecraft:silverfish", 0.05),
		Map.entry("minecraft:endermite", 0.05),
		Map.entry("minecraft:axolotl", 0.3),
		Map.entry("minecraft:allay", 0.5),
		Map.entry("minecraft:vex", 0.5),
		Map.entry("minecraft:armadillo", 6.0),
		Map.entry("minecraft:fox", 7.0),
		Map.entry("minecraft:wolf", 35.0),
		Map.entry("minecraft:goat", 55.0),
		Map.entry("minecraft:sheep", 70.0),
		Map.entry("minecraft:pig", 110.0),
		Map.entry("minecraft:panda", 110.0),
		Map.entry("minecraft:llama", 150.0),
		Map.entry("minecraft:trader_llama", 150.0),
		Map.entry("minecraft:donkey", 250.0),
		Map.entry("minecraft:mule", 350.0),
		Map.entry("minecraft:polar_bear", 450.0),
		Map.entry("minecraft:horse", 500.0),
		Map.entry("minecraft:skeleton_horse", 400.0),
		Map.entry("minecraft:zombie_horse", 500.0),
		Map.entry("minecraft:camel", 550.0),
		Map.entry("minecraft:cow", 600.0),
		Map.entry("minecraft:mooshroom", 600.0),
		Map.entry("minecraft:sniffer", 900.0),
		Map.entry("minecraft:iron_golem", 1000.0),
		Map.entry("cubeanimals:cricket", 0.05),
		Map.entry("cubeanimals:mouse", 0.1),
		Map.entry("naturalist:butterfly", 0.02),
		Map.entry("naturalist:firefly", 0.02),
		Map.entry("jaymoskitchen:rat", 0.4)
	);

	private ThrowWeight() {
	}

	/** What {@code entity} weighs (kilograms). */
	public static double kilograms(Entity entity) {
		if (CarryBlock.is(entity)) {
			// a carried block is a display, with no real size of its own
			return BLOCK_KG;
		}
		Identifier id = EntityType.getKey(entity.getType());
		Double listed = KILOGRAMS.get(id.toString());
		double kg;
		if (listed != null) {
			kg = listed;
			if (entity instanceof LivingEntity living) {
				if (living.isBaby()) {
					kg *= BABY_SHARE;
				}
				// a body grown or shrunk (a size power) weighs as its size
				double scale = living.getScale();
				kg *= scale * scale * scale;
			}
		} else {
			// (the box is already the body's size, a baby's or a grown giant's)
			kg = entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() * DENSITY;
		}
		return Math.max(MIN_KG, kg);
	}

	/** How much faster (above 1) or slower than a person {@code entity} is thrown. */
	public static double throwScale(Entity entity) {
		return throwScale(kilograms(entity));
	}

	public static double throwScale(double kilograms) {
		return Math.clamp(Math.pow(PERSON_KG / Math.max(MIN_KG, kilograms), CURVE), HEAVIEST, LIGHTEST);
	}
}
