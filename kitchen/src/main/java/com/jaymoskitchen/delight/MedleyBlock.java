package com.jaymoskitchen.delight;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Quality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.5.33; Farmer's Delight's rice roll medley, MIT, by vectorwing; its model and art, our own code.) A platter of rolls
 * set down: right-click it for a roll (two cod rolls, three salmon rolls, then three slices of kelp roll), eight
 * servings in all, each with the quality the platter was cut to (see {@link FeastBlock#QUALITY}); what's left on the
 * platter clears away with one more click. It has to stand on something solid.
 */
public class MedleyBlock extends HorizontalDirectionalBlock {
	/** Servings in a whole platter. */
	public static final int SERVINGS_MAX = 8;
	/** Servings left; at 0 only the platter is. */
	public static final IntegerProperty SERVINGS = IntegerProperty.create("servings", 0, SERVINGS_MAX);
	public static final IntegerProperty QUALITY = FeastBlock.QUALITY;

	private static final VoxelShape SHAPE_X = Shapes.or(Block.box(2, 1, 1, 14, 3, 15), Block.box(2, 0, 3, 14, 2, 5), Block.box(2, 0, 11, 14, 2, 13));
	private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(1, 1, 2, 15, 3, 14), Block.box(3, 0, 2, 5, 2, 14), Block.box(11, 0, 2, 13, 2, 14));

	/** A kelp roll slice's hunger and saturation when the platter isn't Good (a Perfect one adds to them, see {@link Quality}). */
	private static final int SLICE_NUTRITION = 6;
	private static final float SLICE_SATURATION = 0.5F;

	public MedleyBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SERVINGS, SERVINGS_MAX)
			.setValue(QUALITY, Quality.GOOD.ordinal()));
	}

	/** The roll the next serving is, by servings left (the platter is eaten from the end of the row). */
	public static ItemStack serving(int servingsLeft, Quality quality) {
		int index = servingsLeft - 1;
		Dish roll = index < 2 ? Dish.COD_ROLL : index < 5 ? Dish.SALMON_ROLL : null;
		ItemStack stack;
		if (roll != null) {
			stack = new ItemStack(ModItems.dish(roll));
			if (quality != Quality.GOOD) {
				quality.apply(stack, roll);
			}
		} else {
			stack = new ItemStack(DelightDishes.KELP_ROLL_SLICE);
			if (quality != Quality.GOOD) {
				quality.apply(stack, SLICE_NUTRITION, SLICE_SATURATION, Dish.RICE_ROLL_MEDLEY.perfectBuffs());
			}
		}
		return stack;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SERVINGS, QUALITY);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isSolid();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int servings = state.getValue(SERVINGS);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (servings == 0) {
			// clear the platter away
			level.destroyBlock(pos, true, player);
			return InteractionResult.SUCCESS;
		}
		ItemStack serving = serving(servings, Quality.values()[state.getValue(QUALITY)]);
		if (!player.getInventory().add(serving)) {
			Block.popResource(level, pos.above(), serving);
		}
		level.setBlock(pos, state.setValue(SERVINGS, servings - 1), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 0.8F);
		return InteractionResult.SUCCESS;
	}
}
