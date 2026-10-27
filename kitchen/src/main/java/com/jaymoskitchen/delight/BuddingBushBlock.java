package com.jaymoskitchen.delight;

import com.jaymoskitchen.season.SeasonalCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's budding bush, MIT; their art, our own code.) A bush that grows as the earlier stage of another
 * plant (the budding tomato vine): once mature it can grow past itself and turn into something different.
 */
public class BuddingBushBlock extends VegetationBlock {
	/** The last stage of the bush itself; one more and it grows past (age 4 is the grown-past state). */
	public static final int MAX_AGE = 3;
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 4);
	private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[] {
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D)
	};
	/** Light needed to grow, as for wheat; a growth step comes on 1 in (25 / speed + 1) random ticks. */
	private static final int GROW_LIGHT = 9;
	private static final float GROW_ODDS = 25.0F;

	public BuddingBushBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE_BY_AGE[state.getValue(AGE)];
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.SUPPORTS_CROPS);
	}

	protected int getAge(BlockState state) {
		return state.getValue(AGE);
	}

	public int getMaxAge() {
		return MAX_AGE;
	}

	public BlockState getStateForAge(int age) {
		return defaultBlockState().setValue(AGE, age);
	}

	public boolean isMaxAge(BlockState state) {
		return state.getValue(AGE) >= getMaxAge();
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return canGrowPastMaxAge() || !isMaxAge(state);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)) || SeasonalCrops.waits(state, level, pos, random)) {
			return;
		}
		if (level.getRawBrightness(pos, 0) >= GROW_LIGHT) {
			int age = getAge(state);
			if (age <= getMaxAge()) {
				float growthSpeed = getGrowthSpeed(state, level, pos);
				if (random.nextInt((int) (GROW_ODDS / growthSpeed) + 1) == 0) {
					if (isMaxAge(state)) {
						growPastMaxAge(state, level, pos, random);
					} else {
						level.setBlockAndUpdate(pos, getStateForAge(age + 1));
					}
				}
			}
		}
	}

	/** Whether this bush keeps ticking at max age; if so, {@link #growPastMaxAge} runs on each growth success. */
	public boolean canGrowPastMaxAge() {
		return false;
	}

	public void growPastMaxAge(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
	}

	/** As vanilla crops: better on wet farmland, worse when crowded by the same crop in a row. */
	protected static float getGrowthSpeed(BlockState state, BlockGetter level, BlockPos pos) {
		float speed = 1.0F;
		BlockPos below = pos.below();
		for (int x = -1; x <= 1; ++x) {
			for (int z = -1; z <= 1; ++z) {
				BlockState soil = level.getBlockState(below.offset(x, 0, z));
				float bonus = soil.getOptionalValue(BlockStateProperties.MOISTURE).map(moisture -> moisture > 0).orElse(false) ? 3.0F : 1.0F;
				if (x != 0 || z != 0) {
					bonus /= 4.0F;
				}
				speed += bonus;
			}
		}
		Block block = state.getBlock();
		boolean eastWestRow = level.getBlockState(pos.west()).is(block) || level.getBlockState(pos.east()).is(block);
		boolean northSouthRow = level.getBlockState(pos.north()).is(block) || level.getBlockState(pos.south()).is(block);
		if (eastWestRow && northSouthRow) {
			speed /= 2.0F;
		} else if (level.getBlockState(pos.west().north()).is(block) || level.getBlockState(pos.east().north()).is(block)
			|| level.getBlockState(pos.east().south()).is(block) || level.getBlockState(pos.west().south()).is(block)) {
			speed /= 2.0F;
		}
		return speed;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return (level.getRawBrightness(pos, 0) >= 8 || level.canSeeSky(pos)) && super.canSurvive(state, level, pos);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (entity instanceof Ravager && level instanceof ServerLevel server && server.getGameRules().get(GameRules.MOB_GRIEFING)) {
			level.destroyBlock(pos, true, entity);
		}
		super.entityInside(state, level, pos, entity, effects, intersects);
	}

	protected ItemLike getBaseSeedId() {
		return DelightFarm.TOMATO_SEEDS;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(getBaseSeedId());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AGE);
	}
}
