package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Ender's Delight's void pepper, MIT; its art, our own code.) A creeping bush that clings to end stone on any side. While
 * it creeps it spreads a new bush onto bare stone nearby now and then; when it stops, it sets a pepper and creeps no
 * more until the pepper is picked. So a patch that's picked keeps growing, and one left alone settles. It never crowds
 * past {@link #CROWDED} bushes in a small space (the stone runs out). The sides it clings to are set from the stone
 * around it (each side's model faces away from its stone, as Ender's Delight's do).
 */
public class VoidpepperBushBlock extends Block {
	public static final BooleanProperty SPREADING = BooleanProperty.create("spreading");
	public static final BooleanProperty PEPPER = BooleanProperty.create("pepper");
	public static final BooleanProperty EAST = BooleanProperty.create("east"), WEST = BooleanProperty.create("west"),
		NORTH = BooleanProperty.create("north"), SOUTH = BooleanProperty.create("south"), UP = BooleanProperty.create("up"),
		DOWN = BooleanProperty.create("down");
	/** A random tick spreads it with this chance; otherwise it sets its pepper (as Ender's Delight's). */
	private static final float SPREAD_CHANCE = 2.0F / 3.0F;
	/** How far (blocks, by steps) a new bush may come up, and how many spots are tried. */
	private static final int SPREAD_RADIUS = 7, SPREAD_TRIES = 15;
	/** It won't spread once this many bushes grow within {@link #CROWD_RADIUS}. */
	private static final int CROWDED = 12, CROWD_RADIUS = 4;
	private static final VoxelShape UP_SHAPE = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D),
		DOWN_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D), NORTH_SHAPE = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D),
		SOUTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D), EAST_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D),
		WEST_SHAPE = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D), LOOSE_SHAPE = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);

	public VoidpepperBushBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SPREADING, true).setValue(PEPPER, false).setValue(EAST, false)
			.setValue(WEST, false).setValue(NORTH, false).setValue(SOUTH, false).setValue(UP, false).setValue(DOWN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SPREADING, EAST, WEST, NORTH, SOUTH, UP, DOWN, PEPPER);
	}

	private static boolean stone(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos).is(EnderDelight.END_PLANT_SOIL);
	}

	/** {@code state} clinging to the stone around {@code pos}. */
	private static BlockState clinging(BlockState state, BlockGetter level, BlockPos pos) {
		return state.setValue(DOWN, stone(level, pos.below())).setValue(UP, stone(level, pos.above()))
			.setValue(SOUTH, stone(level, pos.north())).setValue(NORTH, stone(level, pos.south()))
			.setValue(EAST, stone(level, pos.west())).setValue(WEST, stone(level, pos.east()));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (level.getBlockState(pos.below()).is(BlockTags.SUPPORTS_CROPS)) {
			return true;
		}
		for (Direction side : Direction.values()) {
			if (stone(level, pos.relative(side))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return clinging(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return canSurvive(state, level, pos) ? clinging(state, level, pos) : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(SPREADING);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(SPREADING)) {
			return;
		}
		if (random.nextFloat() < SPREAD_CHANCE) {
			spread(level, pos, random);
		} else {
			level.setBlock(pos, state.setValue(PEPPER, true).setValue(SPREADING, false), Block.UPDATE_CLIENTS);
		}
	}

	private void spread(ServerLevel level, BlockPos from, RandomSource random) {
		int near = 0;
		for (BlockPos nearby : BlockPos.betweenClosed(from.offset(-CROWD_RADIUS, -CROWD_RADIUS, -CROWD_RADIUS),
			from.offset(CROWD_RADIUS, CROWD_RADIUS, CROWD_RADIUS))) {
			if (level.getBlockState(nearby).is(this) && ++near >= CROWDED) {
				return;
			}
		}
		for (int i = 0; i < SPREAD_TRIES; i++) {
			BlockPos target = from.offset(random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS,
				random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS, random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS);
			BlockState placed = clinging(defaultBlockState(), level, target);
			if (target.distManhattan(from) <= SPREAD_RADIUS && level.isEmptyBlock(target) && canSurvive(placed, level, target)) {
				level.setBlock(target, placed, Block.UPDATE_CLIENTS);
				level.gameEvent(GameEvent.BLOCK_CHANGE, target, GameEvent.Context.of(placed));
				return;
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(PEPPER)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			popResource(level, pos, new ItemStack(EnderDelight.VOIDPEPPER));
			level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
			BlockState picked = state.setValue(PEPPER, false).setValue(SPREADING, true);
			level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = Shapes.empty();
		if (state.getValue(UP)) {
			shape = Shapes.or(shape, UP_SHAPE);
		}
		if (state.getValue(DOWN)) {
			shape = Shapes.or(shape, DOWN_SHAPE);
		}
		if (state.getValue(NORTH)) {
			shape = Shapes.or(shape, NORTH_SHAPE);
		}
		if (state.getValue(SOUTH)) {
			shape = Shapes.or(shape, SOUTH_SHAPE);
		}
		if (state.getValue(EAST)) {
			shape = Shapes.or(shape, EAST_SHAPE);
		}
		if (state.getValue(WEST)) {
			shape = Shapes.or(shape, WEST_SHAPE);
		}
		return shape.isEmpty() ? LOOSE_SHAPE : shape;
	}
}
