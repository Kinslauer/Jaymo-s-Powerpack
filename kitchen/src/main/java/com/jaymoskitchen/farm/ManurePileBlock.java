package com.jaymoskitchen.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
 * Andy: "the dung should drop on ground and be visible and pile high like offal and rocks do." A dung heap: 1-{@link #MAX}
 * pats of manure in one block, spreading out first and then heaping up to half a block high. Animals leave theirs here
 * ({@link Manure}); manure in hand sets one down or adds to it, as offal and loose rocks do. Walk-through, gone with a
 * punch (a manure a pat), a right click takes one back. It doesn't rot away, and draws a few flies. Not an
 * {@link com.jaymoskitchen.butchery.OffalPileBlock}, so scavengers leave it alone.
 */
public class ManurePileBlock extends Block {
	public static final int MAX = 8;
	public static final IntegerProperty PATS = IntegerProperty.create("pats", 1, MAX);
	/** Outlines, one per size (the models are made by tools/gen_manure_pile.py, which prints these). */
	private static final VoxelShape[] SHAPES = {
		Block.box(5.0, 0.0, 5.0, 11.0, 3.0, 11.0),
		Block.box(4.0, 0.0, 4.0, 12.0, 3.0, 12.0),
		Block.box(4.0, 0.0, 4.0, 12.0, 3.0, 12.0),
		Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0),
		Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0),
		Block.box(4.0, 0.0, 4.0, 12.0, 7.0, 12.0),
		Block.box(3.0, 0.0, 3.0, 13.0, 7.0, 13.0),
		Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0),
	};

	public ManurePileBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PATS, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PATS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(PATS) - 1];
	}

	/**
	 * One more pat at {@code pos}: a new heap on bare ground or on the heap already there. False if there's no room (a full
	 * heap, water, a block in the way, or no firm ground under it).
	 */
	public boolean addPat(Level level, BlockPos pos) {
		BlockState there = level.getBlockState(pos);
		if (there.is(this)) {
			int pats = there.getValue(PATS);
			if (pats >= MAX) {
				return false;
			}
			level.setBlock(pos, there.setValue(PATS, pats + 1), Block.UPDATE_ALL);
			return true;
		}
		BlockState heap = this.defaultBlockState();
		if (!there.canBeReplaced() || !there.getFluidState().isEmpty() || !heap.canSurvive(level, pos)) {
			return false;
		}
		level.setBlock(pos, heap, Block.UPDATE_ALL);
		return true;
	}

	/** One pat fewer at {@code pos} (the heap gone with its last). */
	public static void takeOne(Level level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(PATS)) {
			return;
		}
		int left = state.getValue(PATS) - 1;
		level.setBlock(pos, left > 0 ? state.setValue(PATS, left) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// more manure in hand goes on the heap (the item's own placing does it, through canBeReplaced)
		if (stack.is(this.asItem()) && state.getValue(PATS) < MAX) {
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
			level.playSound(null, pos, this.soundType.getPlaceSound(), SoundSource.PLAYERS, 0.4F, 0.6F + level.getRandom().nextFloat() * 0.3F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(PATS) < MAX
			|| super.canBeReplaced(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		return there.is(this) ? there.setValue(PATS, Math.min(MAX, there.getValue(PATS) + 1)) : this.defaultBlockState();
	}

	/** A fly or two over the heap, more over a big one. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int pats = state.getValue(PATS);
		if (random.nextInt(10) < 2 + pats / 2) {
			double top = SHAPES[pats - 1].max(Direction.Axis.Y);
			level.addParticle(ParticleTypes.MYCELIUM, pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + top + 0.1 + random.nextDouble() * 0.3,
				pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0.0, 0.0, 0.0);
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
