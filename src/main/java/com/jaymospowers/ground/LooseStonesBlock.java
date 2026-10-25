package com.jaymospowers.ground;

import java.util.function.Supplier;
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
 * A few loose stones lying on the ground, stacked in one block like candles: rocks (1-4) or flint (1-3). Walk-through,
 * gone with a punch (the loot table drops one item a stone), right click picks one up into the inventory. Holding the
 * same rock adds it to the pile. Pops off when its ground goes.
 */
public abstract class LooseStonesBlock extends Block implements SimpleWaterloggedBlock {
	public static final IntegerProperty ROCKS = IntegerProperty.create("rocks", 1, 4);
	public static final IntegerProperty FLINTS = IntegerProperty.create("flints", 1, 3);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** A rock pile the world laid down, not a player (see {@link Rocks}). */
	public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
	private static final VoxelShape[] ROCK_SHAPES = {
		Block.box(5.5, 0.0, 6.0, 10.0, 3.0, 10.5),
		Block.box(5.5, 0.0, 6.0, 13.0, 3.0, 13.0),
		Block.box(3.0, 0.0, 6.0, 13.0, 3.0, 13.0),
		Block.box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0),
	};
	private static final VoxelShape[] FLINT_SHAPES = {
		Block.box(6.0, 0.0, 5.5, 10.0, 1.0, 9.0),
		Block.box(6.0, 0.0, 5.5, 12.5, 1.5, 12.0),
		Block.box(4.0, 0.0, 5.5, 12.5, 1.5, 13.0),
	};

	private final IntegerProperty count;
	/** What one stone of the pile is, given back on a right click (a rock, or vanilla flint). */
	private final Supplier<Item> stone;

	private LooseStonesBlock(BlockBehaviour.Properties properties, Supplier<Item> stone) {
		super(properties);
		this.count = this.countProperty();
		this.stone = stone;
		this.registerDefaultState(this.stateDefinition.any().setValue(this.count, 1).setValue(WATERLOGGED, false));
	}

	/**
	 * Rocks: piles of 1-4. (0.16.24) A pile the world laid down ({@link #NATURAL}) that lies in water turns to salt
	 * crystals in time, so the rocks in water of chunks made before salt are salt too; a pile a player set down stays rock.
	 */
	public static final class Rocks extends LooseStonesBlock {
		public Rocks(BlockBehaviour.Properties properties, Supplier<Item> rock) {
			super(properties, rock);
			this.registerDefaultState(this.defaultBlockState().setValue(NATURAL, true));
		}

		@Override
		public IntegerProperty countProperty() {
			return ROCKS;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(NATURAL);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			BlockState state = super.getStateForPlacement(context);
			// adding to a pile keeps the pile as it was; a new one is the player's
			return context.getLevel().getBlockState(context.getClickedPos()).is(this) ? state : state.setValue(NATURAL, false);
		}

		@Override
		protected boolean isRandomlyTicking(BlockState state) {
			return state.getValue(NATURAL) && state.getValue(WATERLOGGED);
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			level.setBlock(pos, GroundRocks.SALT_CRYSTALS.defaultBlockState().setValue(ROCKS, state.getValue(ROCKS)).setValue(WATERLOGGED, true),
				Block.UPDATE_CLIENTS);
		}
	}

	/** (0.16.24) Salt crystals: piles of 1-4 where rocks would lie, but in the water. They give Kitchen's salt. */
	public static final class Salt extends LooseStonesBlock {
		public Salt(BlockBehaviour.Properties properties, Supplier<Item> salt) {
			super(properties, salt);
		}

		@Override
		public IntegerProperty countProperty() {
			return ROCKS;
		}
	}

	/** Flint: piles of 1-3, giving vanilla flint. */
	public static final class Flint extends LooseStonesBlock {
		public Flint(BlockBehaviour.Properties properties, Supplier<Item> flint) {
			super(properties, flint);
		}

		@Override
		public IntegerProperty countProperty() {
			return FLINTS;
		}
	}

	/** Called from Block's constructor, so it may only return a constant. */
	public abstract IntegerProperty countProperty();

	public int maxCount() {
		return this.count == ROCKS ? 4 : 3;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(this.countProperty(), WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (this.count == ROCKS ? ROCK_SHAPES : FLINT_SHAPES)[state.getValue(this.count) - 1].move(state.getOffset(pos));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// the same rock in hand goes on the pile (the item's own placing does it, through canBeReplaced)
		if (stack.is(this.asItem()) && state.getValue(this.count) < this.maxCount()) {
			return InteractionResult.PASS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			int left = state.getValue(this.count) - 1;
			if (left > 0) {
				level.setBlock(pos, state.setValue(this.count, left), Block.UPDATE_ALL);
			} else {
				level.setBlock(pos, state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
					Block.UPDATE_ALL);
			}
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(this.stone.get()), Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F + level.getRandom().nextFloat() * 0.4F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(this.count) < this.maxCount()
			|| super.canBeReplaced(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		if (there.is(this)) {
			return there.setValue(this.count, Math.min(this.maxCount(), there.getValue(this.count) + 1));
		}
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
		return this.defaultBlockState().setValue(WATERLOGGED, water);
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
		return new ItemStack(this.stone.get());
	}
}
