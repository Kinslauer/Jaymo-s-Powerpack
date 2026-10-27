package com.jaymoskitchen.delight;

import com.jaymoskitchen.season.SeasonalCrops;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's tomatoes, MIT; their art, our own code.) The tomato vine a budding bush grows into: a crop on
 * farmland that is picked by hand, and that climbs a rope tied above it ({@link HangingTomatoBlock}, three high at most).
 */
public class TomatoBlock extends CropBlock {
	public static final IntegerProperty VINE_AGE = BlockStateProperties.AGE_3;
	private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
	/** Tomatoes in a pick (1 to this many), and the chance a pick also drops a rotten one. */
	private static final int PICK_MAX = 2;
	private static final float ROTTEN_CHANCE = 0.05F;
	/** How tall a vine may climb a rope, counting the first block. */
	private static final int MAX_VINE_HEIGHT = 3;
	/** Light a vine needs to keep growing, and to grow past farmland (the upper blocks live on the one below). */
	private static final int GROW_LIGHT = 9, SURVIVE_LIGHT = 8;
	private static final float GROW_ODDS = 25.0F;
	/** Chance a bone meal that grew a vine block also climbs a rope above it. */
	private static final float BONEMEAL_CLIMB = 0.3F;

	public TomatoBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(getAgeProperty(), 0));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		return !isMaxAge(state) && stack.is(Items.BONE_MEAL) ? InteractionResult.PASS : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!isMaxAge(state)) {
			return super.useWithoutItem(state, level, pos, player, hit);
		}
		popResource(level, pos, new ItemStack(DelightFarm.TOMATO, 1 + level.getRandom().nextInt(PICK_MAX)));
		if (level.getRandom().nextFloat() < ROTTEN_CHANCE) {
			popResource(level, pos, new ItemStack(DelightFarm.ROTTEN_TOMATO));
		}
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
		level.setBlock(pos, state.setValue(getAgeProperty(), 0), Block.UPDATE_CLIENTS);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// this doesn't reach CropBlock.randomTick, so the seasons are asked here
		if (!level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)) || SeasonalCrops.waits(state, level, pos, random)) {
			return;
		}
		if (level.getRawBrightness(pos, 0) >= GROW_LIGHT) {
			int age = getAge(state);
			if (age < getMaxAge()) {
				float speed = getGrowthSpeed(state.getBlock(), level, pos);
				if (random.nextInt((int) (GROW_ODDS / speed) + 1) == 0) {
					level.setBlock(pos, state.setValue(getAgeProperty(), age + 1), Block.UPDATE_CLIENTS);
				}
			}
			climbRopeAbove(level, pos);
		}
	}

	@Override
	public BlockState getStateForAge(int age) {
		return defaultBlockState().setValue(getAgeProperty(), age);
	}

	@Override
	protected IntegerProperty getAgeProperty() {
		return VINE_AGE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public int getMaxAge() {
		return 3;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return DelightFarm.TOMATO_SEEDS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(VINE_AGE);
	}

	/** A rope above can be climbed (any block in the {@code ropes} tag). */
	public boolean canClimbBlock(BlockState stateAbove) {
		return stateAbove.is(DelightFarm.ROPES);
	}

	@Nullable
	public BlockState getClimbingState(BlockState stateAbove) {
		return canClimbBlock(stateAbove) ? DelightFarm.TOMATOES_ON_ROPE.defaultBlockState() : null;
	}

	/** Puts a hanging vine in place of the rope just above, if the vine below isn't already at full height. */
	public void climbRopeAbove(ServerLevel level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState climbing = getClimbingState(level.getBlockState(above));
		if (climbing != null) {
			int height = 1;
			while (level.getBlockState(pos.below(height)).is(this)) {
				height++;
			}
			if (height < MAX_VINE_HEIGHT) {
				level.setBlockAndUpdate(above, climbing);
			}
		}
	}

	@Override
	protected int getBonemealAgeIncrease(Level level) {
		return super.getBonemealAgeIncrease(level) / 2;
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		if (!isMaxAge(state)) {
			return true;
		}
		BlockPos.MutableBlockPos at = pos.mutable();
		for (int height = 0; height < MAX_VINE_HEIGHT - 1; height++) {
			at.move(Direction.UP);
			BlockState next = level.getBlockState(at);
			if (canClimbBlock(next)) {
				return true;
			}
			if (next.getBlock() instanceof HangingTomatoBlock) {
				if (!isMaxAge(next)) {
					return true;
				}
			} else {
				return false;
			}
		}
		return false;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		int newAge = getAge(state) + getBonemealAgeIncrease(level);
		if (newAge <= getMaxAge()) {
			level.setBlockAndUpdate(pos, state.setValue(getAgeProperty(), newAge));
			if (random.nextFloat() < BONEMEAL_CLIMB) {
				climbRopeAbove(level, pos);
			}
		} else {
			BlockState aboveState = level.getBlockState(pos.above());
			if (canClimbBlock(aboveState)) {
				climbRopeAbove(level, pos);
			} else if (aboveState.is(DelightFarm.TOMATOES_ON_ROPE) && isValidBonemealTarget(level, pos, aboveState, source)) {
				performBonemeal(level, random, pos.above(), aboveState, source);
			}
		}
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (level.getBlockState(pos.below()).getBlock() instanceof TomatoBlock) {
			return hasGoodCropConditions(level, pos);
		}
		return super.canSurvive(state, level, pos);
	}

	public boolean hasGoodCropConditions(LevelReader level, BlockPos pos) {
		return level.getRawBrightness(pos, 0) >= SURVIVE_LIGHT || level.canSeeSky(pos);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1);
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}
}
