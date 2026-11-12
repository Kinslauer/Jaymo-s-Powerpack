package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A few loose sticks lying on the ground, stacked in one block like candles: 1-4 sticks. Walk-through,
 * gone with a punch (the loot table drops one stick per stick), right click picks one up into the inventory.
 * Pops off when its ground goes.
 * <p>
 * A rock right-clicked onto a pile sets it in a ring around the sticks ({@link #STONES}, up to {@link #RING}); a full
 * pile in a full ring becomes a {@link FirepitBlock firepit}.
 */
public class LooseSticksBlock extends Block implements SimpleWaterloggedBlock {
	public static final IntegerProperty STICKS = IntegerProperty.create("sticks", 1, 4);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** A stick pile the world laid down, not a player (see {@link LooseSticksFeature}). */
	public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
	/** Rocks that make a full firepit ring. */
	public static final int RING = 8;
	/** Rocks set in a ring around the pile, one per right click with a rock. */
	public static final IntegerProperty STONES = IntegerProperty.create("stones", 0, RING);
	/** Boxes around the sticks of each pile size (tools/gen_loose_sticks.py prints them). */
	private static final VoxelShape[] STICK_SHAPES = {
		Block.box(5.0, 0.0, 3.0, 11.0, 1.5, 14.0),
		Block.box(4.5, 0.0, 3.0, 12.5, 1.5, 14.0),
		Block.box(2.5, 0.0, 3.0, 12.5, 1.5, 14.0),
		Block.box(2.5, 0.0, 3.0, 13.0, 1.5, 14.0),
	};
	private static final VoxelShape RING_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0);

	private LooseSticksBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(STICKS, 1).setValue(WATERLOGGED, false).setValue(NATURAL, true)
			.setValue(STONES, 0));
	}

	public static Block create(BlockBehaviour.Properties properties) {
		return new LooseSticksBlock(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STICKS, WATERLOGGED, NATURAL, STONES);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(STONES) > 0 ? RING_SHAPE : STICK_SHAPES[state.getValue(STICKS) - 1];
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// a stick in hand goes on the pile, a rock into the ring around it (FirepitBlock.form when both are full)
		boolean stick = stack.is(net.minecraft.world.item.Items.STICK) && state.getValue(STICKS) < 4;
		boolean rock = isRock(stack) && state.getValue(STONES) < RING && !state.getValue(WATERLOGGED);
		if (stick || rock) {
			if (!level.isClientSide()) {
				BlockState next = stick ? state.setValue(STICKS, state.getValue(STICKS) + 1) : state.setValue(STONES, state.getValue(STONES) + 1);
				stack.consume(1, player);
				level.playSound(null, pos, stick ? SoundEvents.WOOD_PLACE : SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6F,
					stick ? 1.2F : 0.9F + level.getRandom().nextFloat() * 0.3F);
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				if (!FirepitBlock.form(level, pos, next)) {
					level.setBlock(pos, next, Block.UPDATE_ALL);
				}
			}
			return InteractionResult.SUCCESS;
		}
		// whatever else is in hand, a right click picks one stick up
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && state.getValue(STONES) > 0) {
			// the ring comes apart first, a rock at a time
			level.setBlock(pos, state.setValue(STONES, state.getValue(STONES) - 1), Block.UPDATE_ALL);
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(GroundRocks.ROCKS.get(GroundRocks.Variant.STONE)),
					Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F + level.getRandom().nextFloat() * 0.4F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			int left = state.getValue(STICKS) - 1;
			if (left > 0) {
				level.setBlock(pos, state.setValue(STICKS, left), Block.UPDATE_ALL);
			} else {
				level.setBlock(pos, state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
					Block.UPDATE_ALL);
			}
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(net.minecraft.world.item.Items.STICK), Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F + level.getRandom().nextFloat() * 0.4F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** A rock (any stone's) in hand. */
	static boolean isRock(ItemStack stack) {
		return stack.getItem() instanceof net.minecraft.world.item.BlockItem item && GroundRocks.ROCKS.containsValue(item.getBlock());
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		// the pile's own block item (creative) adds a stick to it
		return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(STICKS) < 4
			|| super.canBeReplaced(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		if (there.is(this)) {
			return there.setValue(STICKS, Math.min(4, there.getValue(STICKS) + 1));
		}
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
		return this.defaultBlockState().setValue(WATERLOGGED, water).setValue(NATURAL, false);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(net.minecraft.world.item.Items.STICK);
	}
}