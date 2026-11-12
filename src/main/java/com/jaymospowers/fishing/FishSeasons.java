package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Track C: fish keep the seasons (Advanced Fishing reads Serene Seasons; ours reads Jaymo's Kitchen's own,
 * {@code com.jaymoskitchen.season.Seasons.of(Level)}, looked up once by reflection, so Powers never needs Kitchen). Carp and
 * tench sulk on the bottom in winter, while burbot spawn under the ice then and char and whitefish are at their keenest;
 * each species' {@link Species#seasons} says how keen it is in each.
 */
public final class FishSeasons {
	private static boolean looked;
	private static @Nullable MethodHandle of;

	private FishSeasons() {
	}

	/** The season at {@code level} (0 spring, 1 summer, 2 autumn, 3 winter), or -1 without Kitchen. */
	public static int season(Level level) {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
				try {
					Class<?> seasons = Class.forName("com.jaymoskitchen.season.Seasons");
					Class<?> season = Class.forName("com.jaymoskitchen.season.Season");
					of = MethodHandles.publicLookup().findStatic(seasons, "of", MethodType.methodType(season, Level.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosPowers.LOGGER.warn("Jaymo's Kitchen's seasons not found; fish keep no seasons", e);
				}
			}
		}
		if (of == null) {
			return -1;
		}
		try {
			Object s = of.invoke(level);
			return s instanceof Enum<?> e ? Math.min(3, e.ordinal()) : -1;
		} catch (Throwable t) {
			return -1;
		}
	}
}
