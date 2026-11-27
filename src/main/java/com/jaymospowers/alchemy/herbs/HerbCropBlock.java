package com.jaymospowers.alchemy.herbs;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A crop of the herb garden: the meadow crop-flowers (feverfew, valerian) and the ten poison plants of the poison garden
 * (Hunter's Road, patch 7). A crop planted from seed grows like wheat. A poison plant takes root <i>only</i> on farmland
 * that has been poisoned with paste ({@link PoisonedFarmlandBlock}); the meadow flowers take ordinary farmland. A {@link #WILD}
 * one is a plant the world grew where it belongs: it stands on natural ground, never grows, and gives its herb (a wild
 * crop-flower gives its seeds as well; a wild poison plant gives none, for its seeds are made by breaking the herb down).
 */
public class HerbCropBlock extends CropBlock {
	/** A plant the world grew (set by worldgen): never grows, stands on natural ground, in any light. */
	public static final BooleanProperty WILD = BooleanProperty.create("wild");

	private final Supplier<? extends ItemLike> seeds;
	private final boolean poison;

	public HerbCropBlock(Properties properties, Supplier<? extends ItemLike> seeds, boolean poison) {
		super(properties);
		this.seeds = seeds;
		this.poison = poison;
		registerDefaultState(this.stateDefinition.any().setValue(getAgeProperty(), 0).setValue(WILD, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(WILD);
	}

	/** Whether this is one of the poison plants (which want poisoned farmland). */
	public boolean poison() {
		return this.poison;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return this.seeds.get();
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		if (this.poison) {
			return state.is(Garden.POISONED_FARMLAND);
		}
		return state.is(Blocks.FARMLAND);
	}

	/** Natural ground a wild one stands on: soil and moss, and the Nether's nylium. */
	private static boolean wildGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.NYLIUM) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		if (state.getValue(WILD)) {
			return wildGround(level.getBlockState(below)) || mayPlaceOn(level.getBlockState(below), level, below);
		}
		return super.canSurvive(state, level, pos);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(WILD)) {
			super.randomTick(state, level, pos, random);
		}
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return !state.getValue(WILD) && !isMaxAge(state);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return !state.getValue(WILD) && super.isValidBonemealTarget(level, pos, state, source);
	}
}
