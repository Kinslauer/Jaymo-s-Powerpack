package com.jaymoskitchen.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * (Better With Mods' fertile farmland, MIT; our own code.) Farmland worked with fertilizer: it is farmland in every way
 * (wets, dries, is trampled back to dirt; it's in the farmland tags, so seeds, hemp and seasons take it), but the crop on
 * it gets extra growth ticks ({@link Manure#FERTILE_BONUS}), and each step it grows has a small chance
 * ({@link Manure#SPENT_ODDS}) to use the goodness up, leaving plain farmland.
 */
public class FertileFarmlandBlock extends FarmlandBlock {
	public FertileFarmlandBlock(Properties properties) {
		super(Blocks.DIRT, properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.randomTick(state, level, pos, random);
		if (!level.getBlockState(pos).is(this) || random.nextFloat() >= Manure.FERTILE_BONUS) {
			return;
		}
		BlockPos above = pos.above();
		BlockState crop = level.getBlockState(above);
		if (isCrop(crop) && crop.isRandomlyTicking()) {
			// through the crop's own random tick, so seasons and light still have their say
			crop.randomTick(level, above, random);
		}
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		BlockState shaped = super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
		// the crop above just changed: a growth step (planting is age 0, harvesting leaves air) may spend the soil
		if (direction == Direction.UP && shaped.is(this) && !level.isClientSide() && isCrop(neighbor) && age(neighbor) > 0
			&& random.nextInt(Manure.SPENT_ODDS) == 0) {
			return Blocks.FARMLAND.defaultBlockState().setValue(MOISTURE, shaped.getValue(MOISTURE));
		}
		return shaped;
	}

	private static boolean isCrop(BlockState state) {
		return state.is(BlockTags.CROPS) || state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock;
	}

	/** A crop's growth stage (its "age" property), or 0 if it has none. */
	private static int age(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty age && "age".equals(property.getName())) {
				return state.getValue(age);
			}
		}
		return 0;
	}
}
