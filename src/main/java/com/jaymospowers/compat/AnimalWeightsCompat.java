package com.jaymospowers.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;

/**
 * Animal Weights (optional, MIT, Leclowndu93150; read by reflection, nothing of it copied): a farm animal kept in light,
 * with water, grazing and room grows heavier, by steps from 0 to 8 (1 at the start). A heavier animal weighs more where
 * we weigh it ({@code core/ThrowWeight}): thrown, carried, and in a pack ({@code core/PackWeight}).
 */
public final class AnimalWeightsCompat {
	/** Each step of the mod's weight past its starting one adds this share of the animal's weight. */
	public static final double PER_STEP = 0.06;
	/** The mod's starting weight. */
	static final int START = 1;

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("animalweights");
	private static @Nullable Method getWeight;
	private static boolean looked;

	private AnimalWeightsCompat() {
	}

	/** How much heavier than usual {@code entity} is grown (1 for none, or without the mod). */
	public static double scale(Entity entity) {
		if (!LOADED || !(entity instanceof Animal animal)) {
			return 1.0;
		}
		if (!looked) {
			looked = true;
			try {
				getWeight = Class.forName("com.leclowndu93150.animalweights.WeightAttachment").getMethod("getWeight", Animal.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				getWeight = null;
			}
		}
		if (getWeight == null) {
			return 1.0;
		}
		try {
			int steps = (int)getWeight.invoke(null, animal);
			return Math.max(0.5, 1.0 + (steps - START) * PER_STEP);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1.0;
		}
	}
}
