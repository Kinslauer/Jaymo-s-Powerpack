package com.jaymoskitchen;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * (0.5.59) One Dimension worlds (One Dimension + CustomChunks, CodxIO): the Aether, the Overworld and the Nether stacked as
 * height bands of the Overworld's level, where every band answers {@code level.dimension() == OVERWORLD}. Which realm an
 * entity is in is asked here, by height, through CustomChunks' public {@code StackLayout.dimensionAt} by reflection; an
 * ordinary world, or CustomChunks missing, gets the plain answer. The same as Jaymo's Powers' {@code compat.Stacked}.
 */
public final class Stacked {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("customchunks");
	private static boolean ready;
	private static Method dimensionAt;

	private Stacked() {
	}

	/** The realm at height y of this level: the band's own dimension in a stacked world, else the level's. */
	@SuppressWarnings("unchecked")
	public static ResourceKey<Level> realm(Level level, double y) {
		if (LOADED) {
			if (!ready) {
				ready = true;
				try {
					dimensionAt = Class.forName("codx.customchunks.StackLayout").getMethod("dimensionAt", Level.class, int.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					dimensionAt = null;
				}
			}
			if (dimensionAt != null) {
				try {
					Object key = dimensionAt.invoke(null, level, (int) Math.floor(y));
					if (key != null) {
						return (ResourceKey<Level>) key;
					}
				} catch (ReflectiveOperationException | RuntimeException e) {
					// fall through to the plain answer
				}
			}
		}
		return level.dimension();
	}

	/** Whether this entity is in that realm (its own dimension, or that band of a stacked world). */
	public static boolean in(Entity entity, ResourceKey<Level> realm) {
		return realm(entity.level(), entity.getY()) == realm;
	}
}
