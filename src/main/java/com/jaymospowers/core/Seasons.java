package com.jaymospowers.core;

import java.lang.reflect.Method;
import java.util.Locale;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.31) Jaymo's Kitchen's seasons ({@code com.jaymoskitchen.season.Seasons}), read by reflection so Powers still
 * builds and runs against a Kitchen without them (before 0.5.6 there are no seasons). Seasons are felt only in temperate
 * lands (plains, forests, swamps): {@link #at} is null anywhere else. Both sides (Kitchen syncs its calendar).
 * <p>
 * What the powers make of them (Andy, 2026-10-21: "all of em"): Cryokinesis is stronger in winter and weaker in summer
 * ({@code CryoPower.strength}); Ember the other way round ({@code EmberKindle}); Smoke hangs low and lingers in winter
 * ({@code SmokeField}); the symbiote is hungrier in winter ({@code WebSymbiote}); Darktide's nights are longer in winter
 * ({@code DarktideFlow.night}); and every power user carries a little of the season about them, a Wind user a whirl of
 * it (client {@code SeasonAura}).
 */
public final class Seasons {
	public enum Season { SPRING, SUMMER, AUTUMN, WINTER }

	private static final @Nullable Method NAME, TEMPERATE;

	static {
		Method name = null, temperate = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				Class<?> seasons = Class.forName("com.jaymoskitchen.season.Seasons");
				name = seasons.getMethod("seasonName", Level.class);
				temperate = seasons.getMethod("temperate", float.class);
			} catch (ReflectiveOperationException e) {
				JaymosPowers.LOGGER.info("Jaymo's Kitchen has no seasons (needs 0.5.6): {}", e.toString());
				name = null;
				temperate = null;
			}
		}
		NAME = name;
		TEMPERATE = temperate;
	}

	private Seasons() {
	}

	/** The season in the world, or null without Kitchen's seasons. */
	public static @Nullable Season of(Level level) {
		if (NAME == null) {
			return null;
		}
		try {
			return NAME.invoke(null, level) instanceof String key ? Season.valueOf(key.toUpperCase(Locale.ROOT)) : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** The season felt where {@code entity} stands: null outside temperate lands, or without seasons. */
	public static @Nullable Season at(@Nullable Entity entity) {
		if (entity == null || TEMPERATE == null) {
			return null;
		}
		float base = entity.level().getBiome(entity.blockPosition()).value().getBaseTemperature();
		try {
			return Boolean.TRUE.equals(TEMPERATE.invoke(null, base)) ? of(entity.level()) : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	public static boolean is(@Nullable Entity entity, Season season) {
		return at(entity) == season;
	}
}
