package com.jaymoskitchen.butchery;

import com.jaymoskitchen.corpse.Scavengers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Andy: "The offal should place on the ground in piles like stones". A gut pile: 1-{@link #MAX} pieces of offal set
 * down on the ground in one block, as Jaymo's Powers' loose stones are. Walk-through, gone with a punch (a piece of
 * offal each), a right click takes one back, more offal in hand adds to it. Wild scavengers smell it and come to eat
 * ({@link Scavengers#smellPile}), and left alone it rots away a piece at a time.
 */
public class OffalPileBlock extends Block {
	public static final int MAX = 4;
	public static final IntegerProperty PIECES = IntegerProperty.create("pieces", 1, MAX);
	/** A random tick in this many rots a piece away (a random tick comes about every 68 s). */
	static final int ROT_ONE_IN = 8;
	private static final VoxelShape[] SHAPES = {
		Block.box(5.0, 0.0, 5.0, 10.0, 2.0, 10.0),
		Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0),
		Block.box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0),
		Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0),
	};

	public OffalPileBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PIECES, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PIECES);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(PIECES) - 1];
	}

	/** One piece fewer at {@code pos} (the pile gone with its last). */
	public static void takeOne(Level level, BlockPos pos, BlockState state) {
		int left = state.getValue(PIECES) - 1;
		level.setBlock(pos, left > 0 ? state.setValue(PIECES, left) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// more offal in hand goes on the pile (the item's own placing does it, through canBeReplaced)
		if (stack.is(this.asItem()) && state.getValue(PIECES) < MAX) {
			return InteractionResult.PASS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			takeOne(level, pos, state);
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(this.asItem()), Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, this.soundType.getPlaceSound(), SoundSource.PLAYERS, 0.4F, 0.8F + level.getRandom().nextFloat() * 0.4F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(PIECES) < MAX
			|| super.canBeReplaced(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		return there.is(this) ? there.setValue(PIECES, Math.min(MAX, there.getValue(PIECES) + 1)) : this.defaultBlockState();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server) {
			Scavengers.smellPile(server, pos);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(ROT_ONE_IN) == 0) {
			takeOne(level, pos, state);
		} else {
			// still there after a restart: scavengers hear of it again
			Scavengers.smellPile(level, pos);
		}
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}
}
