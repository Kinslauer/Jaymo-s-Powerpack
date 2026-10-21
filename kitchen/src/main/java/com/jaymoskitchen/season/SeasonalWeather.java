package com.jaymoskitchen.season;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.4) Snow and ice through the year. Winter makes temperate lands cold enough to snow and to freeze still water (the
 * biome mixin lowers their temperature by {@link Season#chill}, so the game's own weather does the rest). Out of winter,
 * snow and ice left open to the sky there melt away a little at a time ({@code SeasonMeltMixin}); under a roof they keep.
 */
public final class SeasonalWeather {
	/** The chance a random tick takes a layer off open snow, or melts open ice, out of winter. */
	private static final float SNOW_MELT = 0.35F, ICE_MELT = 0.2F;

	private SeasonalWeather() {
	}

	private static boolean thaws(ServerLevel level, BlockPos pos) {
		return Seasons.of(level) != Season.WINTER && Seasons.temperate(level.getBiome(pos).value().getBaseTemperature())
			&& level.canSeeSky(pos.above());
	}

	/** A random tick of a snow layer: true when it melted a little (and the usual tick is skipped). */
	public static boolean meltSnow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!thaws(level, pos) || random.nextFloat() >= SNOW_MELT) {
			return false;
		}
		int layers = state.getValue(SnowLayerBlock.LAYERS);
		if (layers > 1) {
			level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
		} else {
			level.removeBlock(pos, false);
		}
		return true;
	}

	/** A random tick of ice: true when it melted to water. */
	public static boolean meltIce(ServerLevel level, BlockPos pos, RandomSource random) {
		if (!thaws(level, pos) || random.nextFloat() >= ICE_MELT) {
			return false;
		}
		level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
		return true;
	}
}
