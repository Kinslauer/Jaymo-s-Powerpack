package com.jaymoskitchen.delight;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Quality;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ConsumableListener;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.5.33; Farmer's Delight's PieBlock, MIT, by vectorwing; their models and art, our own code.) A whole pie set down:
 * right-click it to eat a slice where you stand, or cut a slice off with a knife to carry. It has four slices, and each
 * comes out with the quality the pie was baked to (see {@link FeastBlock#QUALITY}, which the pies share). It has to
 * stand on something solid.
 */
public class PieBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Slices already taken. */
	public static final IntegerProperty BITES = IntegerProperty.create("bites", 0, 3);
	public static final IntegerProperty QUALITY = FeastBlock.QUALITY;

	/** Slices in a whole pie. */
	public static final int SLICES = 4;
	/** A slice of a pie that isn't Good: its hunger and saturation (a Perfect one adds to them, see {@link Quality}). */
	static final int SLICE_NUTRITION = 3;
	static final float SLICE_SATURATION = 0.3F;

	/** The pie from the north, by slices taken. */
	private static final VoxelShape[] SHAPES = {
		Block.box(2, 0, 2, 14, 4, 14),
		Shapes.or(Block.box(2, 0, 8, 8, 4, 14), Block.box(2, 0, 2, 14, 4, 8)),
		Block.box(2, 0, 2, 14, 4, 8),
		Block.box(8, 0, 2, 14, 4, 8)
	};
	/** Those turned to face each way, indexed by slices taken, then by {@link Direction#get2DDataValue()}. */
	private static final VoxelShape[][] ROTATED = new VoxelShape[SHAPES.length][4];

	static {
		for (int bites = 0; bites < SHAPES.length; bites++) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				ROTATED[bites][direction.get2DDataValue()] = turned(SHAPES[bites], direction);
			}
		}
	}

	private final Dish dish;
	private final Supplier<Item> slice;

	public PieBlock(Properties properties, Dish dish, Supplier<Item> slice) {
		super(properties);
		this.dish = dish;
		this.slice = slice;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BITES, 0)
			.setValue(QUALITY, Quality.GOOD.ordinal()));
	}

	/** A shape drawn facing north, turned (clockwise from above) to face {@code to}. */
	private static VoxelShape turned(VoxelShape shape, Direction to) {
		int turns = switch (to) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
		VoxelShape[] result = {Shapes.empty()};
		shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
			double ax = x1, az = z1, bx = x2, bz = z2;
			for (int i = 0; i < turns; i++) {
				// one turn clockwise about the middle of the block: (x, z) goes to (1 - z, x)
				double nx1 = 1.0 - bz, nx2 = 1.0 - az, nz1 = ax, nz2 = bx;
				ax = nx1;
				bx = nx2;
				az = nz1;
				bz = nz2;
			}
			result[0] = Shapes.or(result[0], Shapes.box(ax, y1, az, bx, y2, bz));
		});
		return result[0];
	}

	public Dish dish() {
		return dish;
	}

	/** A slice of this pie at the given quality (a Good one is the plain slice, with its own effect). */
	public ItemStack slice(Quality quality) {
		ItemStack stack = new ItemStack(slice.get());
		if (quality != Quality.GOOD) {
			quality.apply(stack, SLICE_NUTRITION, SLICE_SATURATION, dish.perfectBuffs());
		}
		return stack;
	}

	private static Quality qualityOf(BlockState state) {
		return Quality.values()[state.getValue(QUALITY)];
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return ROTATED[state.getValue(BITES)][state.getValue(FACING).get2DDataValue()];
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (held.is(Delight.KNIVES)) {
			return cutSlice(held, level, pos, state, player);
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return eatSlice(level, pos, state, player);
	}

	/** Eats a slice where it stands, feeding the player. */
	private InteractionResult eatSlice(Level level, BlockPos pos, BlockState state, Player player) {
		if (!player.canEat(false)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			ItemStack eaten = slice(qualityOf(state));
			Consumable consumable = eaten.get(DataComponents.CONSUMABLE);
			if (consumable != null) {
				eaten.getAllOfType(ConsumableListener.class).forEach(listener -> listener.onConsume(level, player, eaten, consumable));
				consumable.onConsumeEffects().forEach(effect -> effect.apply(level, eaten, player));
			}
			takeBite(level, pos, state);
			level.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
			crumbs(server, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	/** Cuts a slice off and drops it, without feeding the player. */
	private InteractionResult cutSlice(ItemStack knife, Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			takeBite(level, pos, state);
			Block.popResource(level, pos, slice(qualityOf(state)));
			level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
			crumbs(server, pos, state);
			player.awardStat(Stats.ITEM_USED.get(knife.getItem()));
		}
		return InteractionResult.SUCCESS;
	}

	private static void takeBite(Level level, BlockPos pos, BlockState state) {
		int bites = state.getValue(BITES);
		if (bites < SLICES - 1) {
			level.setBlock(pos, state.setValue(BITES, bites + 1), Block.UPDATE_ALL);
		} else {
			level.removeBlock(pos, false);
		}
	}

	private static void crumbs(ServerLevel level, BlockPos pos, BlockState state) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
			3, 0.1, 0.1, 0.1, 0.001);
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
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return canSupportRigidBlock(level, pos.below());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, BITES, QUALITY);
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return SLICES - state.getValue(BITES);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
