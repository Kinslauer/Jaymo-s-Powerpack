package com.jaymoskitchen.season;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.4) Crops by season. A crop in one or more of the block tags {@code jaymoskitchen:seasons/<season>} grows only in
 * those seasons; any other crop (other mods' too) grows in all but winter. Out of season it waits, and in winter the
 * frost kills it ({@link #waits}).
 * Sheltered crops grow all year: under glass ({@code jaymoskitchen:greenhouse_roof}) or any solid roof. Only temperate
 * lands have seasons, so crops in a desert or the snowy north grow as they always did.
 * <p>
 * {@code CropSeasonMixin} asks {@link #waits} before each random growth tick of crops, stems, berry bushes, cocoa and
 * pitcher pods.
 */
public final class SeasonalCrops {
	public static final TagKey<Block> GREENHOUSE_ROOF = TagKey.create(Registries.BLOCK, JaymosKitchen.id("greenhouse_roof"));
	private static final TagKey<Block>[] IN_SEASON = tags();
	/** How far up a roof is looked for. */
	private static final int ROOF_SEARCH = 24;
	/** The chance a winter random tick kills a crop left in the open (a crop gets about one every minute or so). */
	private static final float FROST_KILL = 0.25F;

	private SeasonalCrops() {
	}

	@SuppressWarnings("unchecked")
	private static TagKey<Block>[] tags() {
		Season[] all = Season.values();
		TagKey<Block>[] tags = new TagKey[all.length];
		for (Season season : all) {
			tags[season.ordinal()] = TagKey.create(Registries.BLOCK, JaymosKitchen.id("seasons/" + season.key()));
		}
		return tags;
	}

	/**
	 * Whether this crop holds off growing this tick, being out of season. (0.5.4, Andy: "very harsh") In winter a crop
	 * left in the open is killed by the frost now and then: grain, roots, stems and pitcher pods die, berry bushes and
	 * cocoa lose what was on them.
	 */
	public static boolean waits(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!Seasons.temperate(level.getBiome(pos).value().getBaseTemperature())) {
			return false;
		}
		Season season = Seasons.of(level);
		if (inSeason(state, season) || sheltered(level, pos)) {
			return false;
		}
		if (season == Season.WINTER && random.nextFloat() < FROST_KILL) {
			frost(state, level, pos);
		}
		return true;
	}

	private static void frost(BlockState state, ServerLevel level, BlockPos pos) {
		if (state.getBlock() instanceof SweetBerryBushBlock) {
			if (state.getValue(SweetBerryBushBlock.AGE) > 0) {
				level.setBlockAndUpdate(pos, state.setValue(SweetBerryBushBlock.AGE, 0));
			}
		} else if (state.getBlock() instanceof CocoaBlock) {
			if (state.getValue(CocoaBlock.AGE) > 0) {
				level.setBlockAndUpdate(pos, state.setValue(CocoaBlock.AGE, 0));
			}
		} else {
			level.destroyBlock(pos, false);
		}
	}

	public static boolean inSeason(BlockState state, Season season) {
		boolean tagged = false;
		for (TagKey<Block> tag : IN_SEASON) {
			if (state.is(tag)) {
				tagged = true;
				break;
			}
		}
		if (!tagged) {
			return season != Season.WINTER;
		}
		return state.is(IN_SEASON[season.ordinal()]);
	}

	/** Under glass or a solid roof: the first thing above that isn't air or the plant itself. */
	public static boolean sheltered(ServerLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos at = pos.mutable();
		Block plant = level.getBlockState(pos).getBlock();
		for (int i = 0; i < ROOF_SEARCH; i++) {
			at.move(0, 1, 0);
			if (level.isOutsideBuildHeight(at)) {
				return false;
			}
			BlockState above = level.getBlockState(at);
			if (above.isAir() || above.is(plant)) {
				continue;
			}
			return above.is(GREENHOUSE_ROOF) || above.canOcclude();
		}
		return false;
	}
}
