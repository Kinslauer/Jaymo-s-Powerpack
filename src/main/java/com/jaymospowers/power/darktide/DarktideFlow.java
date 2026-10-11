package com.jaymospowers.power.darktide;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * Flow, Darktide's 0-{@link #MAX} resource: it rises in water and in the dark, and from parries and roll evasions; it
 * drains only in full daylight under open sky above {@link #DAYLIGHT_FLOOR}. Named {@code DarktideFlow} in code so it's
 * never confused with Chronokinesis' Time Flow.
 */
public final class DarktideFlow {
	public static final int MAX = 100;
	/** Gains per second: fully submerged; waist-deep or standing in rain. */
	static final float SUBMERGED_PER_SECOND = 4.0F;
	static final float SHALLOW_PER_SECOND = 2.0F;
	/** How deep "waist-deep" is (blocks of water at the feet). */
	static final double WAIST_DEEP = 0.6;
	/** Gains per second in the dark: block light at most {@link #DARK_BLOCK_LIGHT} and no sky light; more at light 0. */
	static final float DARK_PER_SECOND = 2.0F;
	static final float PITCH_DARK_PER_SECOND = 3.0F;
	static final int DARK_BLOCK_LIGHT = 4;
	/** Drowned Dark (night): the dark gives this many times as much, water nothing. */
	static final float DROWNED_DARK_FACTOR = 2.0F;
	/** Full daylight under open sky drains this much a second, while above {@link #DAYLIGHT_FLOOR}. */
	static final float DAYLIGHT_DRAIN_PER_SECOND = 1.0F;
	static final int DAYLIGHT_FLOOR = 50;
	/** Night, for the Drowned Dark: from dusk to dawn on the dimension's clock (ticks into the day). */
	public static final long NIGHT_START = 12000L, NIGHT_END = 23500L;

	private DarktideFlow() {
	}

	/** Night in a dimension with a day (the Nether and the End never have one). Both sides. */
	public static boolean night(Level level) {
		if (level.dimensionType().hasFixedTime()) {
			return false;
		}
		long time = Math.floorMod(level.getDefaultClockTime(), 24000L);
		return time >= NIGHT_START && time < NIGHT_END;
	}

	/** Effective light at {@code pos}: block light and sky light as the time of day dims it. */
	static int skyLight(Level level, BlockPos pos) {
		return Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
	}

	static boolean dark(Level level, BlockPos pos) {
		return level.getBrightness(LightLayer.BLOCK, pos) <= DARK_BLOCK_LIGHT && skyLight(level, pos) <= 0;
	}

	/** Where Undertow may surface: water, or a shadow (no more than {@link #DARK_BLOCK_LIGHT} light of either kind). */
	static boolean shadow(Level level, BlockPos pos) {
		return level.getBrightness(LightLayer.BLOCK, pos) <= DARK_BLOCK_LIGHT && skyLight(level, pos) <= DARK_BLOCK_LIGHT;
	}

	/** This tick's change in Flow for {@code player}. */
	static float tickGain(ServerPlayer player, boolean drowned, float flow) {
		Level level = player.level();
		BlockPos pos = BlockPos.containing(player.getEyePosition());
		float water = 0.0F;
		if (!drowned) {
			if (player.isUnderWater()) {
				water = SUBMERGED_PER_SECOND;
			} else if (player.isInWater() && player.getFluidHeight(FluidTags.WATER) >= WAIST_DEEP || player.isInWaterOrRain() && !player.isInWater()) {
				water = SHALLOW_PER_SECOND;
			}
		}
		float dark = 0.0F;
		if (dark(level, pos)) {
			dark = level.getBrightness(LightLayer.BLOCK, pos) <= 0 ? PITCH_DARK_PER_SECOND : DARK_PER_SECOND;
			if (drowned) {
				dark *= DROWNED_DARK_FACTOR;
			}
		}
		float gain = Math.max(water, dark);
		if (gain > 0.0F) {
			return gain / 20.0F;
		}
		if (flow > DAYLIGHT_FLOOR && level.isBrightOutside() && level.canSeeSky(pos) && !player.isInWater()) {
			return -DAYLIGHT_DRAIN_PER_SECOND / 20.0F;
		}
		return 0.0F;
	}
}
