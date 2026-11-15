package com.jaymoskitchen.mill.power;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The wind a windmill's sails and a windsock feel. With Jaymo's Kingdoms loaded it is the world's own wind, the one that
 * drives its storm cells and fills its ships' sails ({@code com.jaymoskingdoms.sailing.Winds} and
 * {@code com.jaymoskingdoms.disasters.Storms}, reached through MethodHandles so Kitchen never compiles against it).
 * Without Kingdoms it's Kitchen's own day's wind (the one that carries soot): a quarter turn a day, swung to the rain's
 * side in rain, stronger in rain and storms, with slow gusts. Server side only.
 */
public final class Wind {
	private static final Logger LOGGER = LoggerFactory.getLogger("Jaymo's Kitchen");
	/** Without Kingdoms: how hard it blows in clear weather, rain and a thunderstorm (Kingdoms' wind runs about 0.45 to 1.5). */
	static final float CLEAR = 0.6F, RAIN = 0.85F, THUNDER = 1.3F;
	/** Without Kingdoms: the slow gusts on top (either way), and their period (ticks). */
	static final float GUST = 0.15F;
	static final float GUST_TICKS = 3000.0F;

	private static boolean looked;
	private static @Nullable MethodHandle strength, angle, thunder;

	private Wind() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			return;
		}
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Class<?> winds = Class.forName("com.jaymoskingdoms.sailing.Winds");
			Class<?> storms = Class.forName("com.jaymoskingdoms.disasters.Storms");
			strength = lookup.findStatic(winds, "strength", MethodType.methodType(float.class, Level.class, double.class, double.class));
			angle = lookup.findStatic(storms, "windAngle", MethodType.methodType(float.class));
			thunder = lookup.findStatic(storms, "thunder", MethodType.methodType(float.class, Level.class, double.class, double.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("Couldn't find Jaymo's Kingdoms' wind (older Kingdoms?): windmills use Kitchen's own", e);
			strength = null;
			angle = null;
			thunder = null;
		}
	}

	/** How hard the wind blows at {@code pos} (about 0.45 to 1.5; more in rain and under a storm). */
	public static float strength(Level level, BlockPos pos) {
		look();
		if (strength != null) {
			try {
				return (float) strength.invokeExact(level, pos.getX() + 0.5, pos.getZ() + 0.5);
			} catch (Throwable e) {
				strength = null;
				LOGGER.warn("Jaymo's Kingdoms' wind failed: windmills use Kitchen's own", e);
			}
		}
		float base = level.isThundering() ? THUNDER : level.isRaining() ? RAIN : CLEAR;
		return Math.max(0.0F, base + GUST * Mth.sin(level.getGameTime() / GUST_TICKS * Mth.TWO_PI));
	}

	/** The way the wind blows toward (radians: toward (cos, sin) in x, z). */
	public static float angle(Level level) {
		look();
		if (angle != null) {
			try {
				return (float) angle.invokeExact();
			} catch (Throwable e) {
				angle = null;
				LOGGER.warn("Jaymo's Kingdoms' wind failed: windmills use Kitchen's own", e);
			}
		}
		// the day's wind (as Kitchen's soot has it)
		long day = level.getOverworldClockTime() / 24000L;
		Direction d = Direction.from2DDataValue((int) Math.floorMod(day, 4L));
		if (level.isRaining()) {
			d = d.getClockWise();
		}
		return (float) Math.atan2(d.getStepZ(), d.getStepX());
	}

	/** How much of a thunderstorm is over {@code pos}, 0 to 1: what tears sails. */
	public static float storm(Level level, BlockPos pos) {
		look();
		if (thunder != null) {
			try {
				return (float) thunder.invokeExact(level, pos.getX() + 0.5, pos.getZ() + 0.5);
			} catch (Throwable e) {
				thunder = null;
				LOGGER.warn("Jaymo's Kingdoms' storms failed: windmills use the weather", e);
			}
		}
		return level.isThundering() && level.isRainingAt(pos.above()) ? 1.0F : 0.0F;
	}

	/** How square the wind blows onto sails turning across a shaft along {@code facing}: 1 head-on (either side), 0 edge-on. */
	public static float along(Level level, Direction facing) {
		float a = angle(level);
		return (float) Math.abs(Math.cos(a) * facing.getStepX() + Math.sin(a) * facing.getStepZ());
	}
}
