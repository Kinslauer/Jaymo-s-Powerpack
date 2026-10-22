package com.jaymoskingdoms;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.125) The bounty board in town, its face toward {@link #FACING}. (0.7.168) Andy: "the bounty board should be
 * multiple blocks, allowing for item frame like pulling on and placing onto the board, some villagers will be seen
 * posting to it." Now {@link #WIDTH} blocks wide and {@link #HEIGHT} tall: two posts, a plank board with a little roof,
 * and one notice spot on each block ({@link #NOTICE}).
 * <ul>
 * <li>Placed from its bottom middle block ({@link #anchor}); breaking any block takes down the whole board, and the
 * board drops once.</li>
 * <li>Punch or use a pinned notice to pull it off: that takes the bounty ({@link Bounties#pull}). Use a bare spot with a
 * notice in hand to pin it back up: that gives the hunt up ({@link Bounties#pin}). Use a bare spot (or sneak) to read
 * the whole list ({@link Bounties#open}).</li>
 * <li>Notices go up when a villager of the town walks over and pins one ({@link Bounties} errands): a wanted notice, or
 * (0.7.168) a town notice, an errand that's a quest or a bit of news ({@link TownNotices}).</li>
 * </ul>
 */
public final class BountyBoard extends Block {
	public static final int WIDTH = 3, HEIGHT = 2;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Which block of the board this is: the column from the left as you face it, the row from the ground. */
	public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, WIDTH - 1);
	public static final IntegerProperty ROW = IntegerProperty.create("row", 0, HEIGHT - 1);
	/**
	 * What's pinned on this block: nothing, a wanted notice, or a town notice (an errand or a bit of news,
	 * {@link TownNotices}). Which one it is: {@link Bounties#PINNED}.
	 */
	public static final IntegerProperty NOTICE = IntegerProperty.create("notice", 0, 2);
	public static final int BARE = 0, WANTED = 1, TOWN = 2;

	/** Each block's shape by column, row and facing (the board, the posts at the ends, the roof on top). */
	private static final Map<Direction, VoxelShape>[][] SHAPES = shapes();

	public BountyBoard(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 1).setValue(ROW, 0).setValue(NOTICE, BARE));
	}

	@SuppressWarnings("unchecked")
	private static Map<Direction, VoxelShape>[][] shapes() {
		Map<Direction, VoxelShape>[][] out = new Map[WIDTH][HEIGHT];
		for (int c = 0; c < WIDTH; c++) {
			for (int r = 0; r < HEIGHT; r++) {
				VoxelShape s = r == 0 ? Block.box(0, 4, 6.5, 16, 16, 9.5) : Shapes.or(Block.box(0, 0, 6.5, 16, 12, 9.5), Block.box(0, 12, 5, 16, 15, 11));
				// (the posts stand at the outer ends: column 0 is on the viewer's left, +x when facing north)
				if (c == 0) {
					s = Shapes.or(s, Block.box(13, 0, 6.5, 16, r == 0 ? 16 : 14, 9.5));
				} else if (c == WIDTH - 1) {
					s = Shapes.or(s, Block.box(0, 0, 6.5, 3, r == 0 ? 16 : 14, 9.5));
				}
				out[c][r] = Shapes.rotateHorizontal(s);
			}
		}
		return out;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, COLUMN, ROW, NOTICE);
	}

	// ---- the layout ------------------------------------------------------------------------------------------------------

	/** The way the columns run (to the right of someone facing the board's face). */
	static Direction right(Direction facing) {
		return facing.getCounterClockWise();
	}

	/** Where the block at {@code column}, {@code row} of a board placed at {@code anchor} (its bottom middle) is. */
	static BlockPos part(BlockPos anchor, Direction facing, int column, int row) {
		return anchor.relative(right(facing), column - 1).above(row);
	}

	/** The bottom middle block of the board {@code state} at {@code pos} is part of. */
	static BlockPos anchor(BlockPos pos, BlockState state) {
		return pos.relative(right(state.getValue(FACING)), 1 - state.getValue(COLUMN)).below(state.getValue(ROW));
	}

	/** Something is pinned on this block of a board. */
	static boolean pinned(BlockState state) {
		return state.getValue(NOTICE) != BARE;
	}

	static boolean isAnchor(BlockState state) {
		return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 0;
	}

	static BlockState partState(Block block, Direction facing, int column, int row) {
		return block.defaultBlockState().setValue(FACING, facing).setValue(COLUMN, column).setValue(ROW, row);
	}

	/** Every block of the board placed at {@code anchor} stands, facing {@code facing}. */
	static boolean complete(Level level, BlockPos anchor, Direction facing) {
		for (int c = 0; c < WIDTH; c++) {
			for (int r = 0; r < HEIGHT; r++) {
				BlockState s = level.getBlockState(part(anchor, facing, c, r));
				if (!(s.getBlock() instanceof BountyBoard) || s.getValue(FACING) != facing || s.getValue(COLUMN) != c || s.getValue(ROW) != r) {
					return false;
				}
			}
		}
		return true;
	}

	/** Puts the rest of the board up around {@code anchor}, which already stands. */
	static void build(Level level, BlockPos anchor, Block block, Direction facing) {
		for (int r = 0; r < HEIGHT; r++) {
			for (int c = 0; c < WIDTH; c++) {
				if (c != 1 || r != 0) {
					level.setBlock(part(anchor, facing, c, r), partState(block, facing, c, r), Block.UPDATE_ALL);
				}
			}
		}
	}

	// ---- placing and breaking --------------------------------------------------------------------------------------------

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		// (its face toward whoever puts it up; room for every block of it, or it doesn't go up)
		Direction facing = context.getHorizontalDirection().getOpposite();
		Level level = context.getLevel();
		BlockPos anchor = context.getClickedPos();
		for (int c = 0; c < WIDTH; c++) {
			for (int r = 0; r < HEIGHT; r++) {
				BlockPos p = part(anchor, facing, c, r);
				if (p.getY() > level.getMaxY() || !level.getBlockState(p).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(p)) {
					return null;
				}
			}
		}
		return partState(this, facing, 1, 0);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide()) {
			build(level, pos, this, state.getValue(FACING));
		}
	}

	/** The board drops from its bottom middle block (the loot table): broken anywhere else, that block goes too, and drops. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && !isAnchor(state)) {
			BlockPos anchor = anchor(pos, state);
			BlockState at = level.getBlockState(anchor);
			if (at.is(this) && isAnchor(at)) {
				level.destroyBlock(anchor, !player.isCreative(), player);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** A neighbouring block of the same board gone (or not this board's): this one comes down too, so the board goes whole. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		Direction facing = state.getValue(FACING);
		int c = state.getValue(COLUMN), r = state.getValue(ROW);
		int wantC = c, wantR = r;
		if (direction == right(facing) && c < WIDTH - 1) {
			wantC = c + 1;
		} else if (direction == right(facing).getOpposite() && c > 0) {
			wantC = c - 1;
		} else if (direction == Direction.UP && r < HEIGHT - 1) {
			wantR = r + 1;
		} else if (direction == Direction.DOWN && r > 0) {
			wantR = r - 1;
		} else {
			return state;
		}
		boolean ok = neighbour.is(this) && neighbour.getValue(FACING) == facing && neighbour.getValue(COLUMN) == wantC && neighbour.getValue(ROW) == wantR;
		return ok ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		super.onPlace(state, level, pos, old, movedByPiston);
		if (level instanceof ServerLevel server && isAnchor(state) && !old.is(this)) {
			Bounties.boardUp(server, pos);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(COLUMN)][state.getValue(ROW)].get(state.getValue(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	// ---- notices -----------------------------------------------------------------------------------------------------------

	/** A notice in hand, used on a bare spot: pinned back up. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(stack.is(Bounties.NOTICE_ITEM) || stack.is(TownNotices.ITEM)) || pinned(state)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			Bounties.pin(server, sp, pos, stack);
		}
		return InteractionResult.SUCCESS;
	}

	/** A pinned notice is pulled off (sneak to read the list instead); a bare spot shows the list. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			if (pinned(state) && !player.isSecondaryUseActive()) {
				Bounties.pull(server, sp, pos);
			} else {
				Bounties.open(server, sp, anchor(pos, state));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
