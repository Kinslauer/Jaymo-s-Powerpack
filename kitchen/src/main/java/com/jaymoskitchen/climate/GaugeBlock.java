package com.jaymoskitchen.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The air gauge (our own: a brass-rimmed dial hung on a wall): it reads how cold or hot the air is where it hangs, the way
 * the world's air pushes on a person ({@link AirGauge}, {@link BodyHeat#ambient}), and gives it to redstone as a signal of
 * 0 to 15 across the scale from freezing to overheating ({@link AirGauge#COLDEST}, {@link AirGauge#HOTTEST}). Use it to turn
 * its setting ({@link #HOT}): the signal climbs as the air gets hotter, or as it gets colder. The needle on its face shows
 * the air itself either way; a red or a blue pip over the dial says which end is the strong one. A comparator reads the
 * same number. It looks at the air every {@link #LOOK_EVERY} ticks (a hearth's room is walked every five seconds).
 */
public class GaugeBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** True: the signal is stronger the hotter the air. False: stronger the colder. */
	public static final BooleanProperty HOT = BooleanProperty.create("hot");
	/** The signal it gives now. */
	public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);
	/** Ticks between looks at the air. */
	static final int LOOK_EVERY = 40;

	private static final VoxelShape NORTH = Block.box(1.0, 1.0, 14.0, 15.0, 15.0, 16.0), SOUTH = Block.box(1.0, 1.0, 0.0, 15.0, 15.0, 2.0),
		EAST = Block.box(0.0, 1.0, 1.0, 2.0, 15.0, 15.0), WEST = Block.box(14.0, 1.0, 1.0, 16.0, 15.0, 15.0);

	public GaugeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HOT, true).setValue(POWER, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HOT, POWER);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	/** Hung on a wall: its back is against the solid block it was set on. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction back = state.getValue(FACING).getOpposite();
		BlockPos wall = pos.relative(back);
		return level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (face.getAxis().isVertical()) {
			return null;
		}
		BlockState state = defaultBlockState().setValue(FACING, face);
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
		BlockState neighbour, RandomSource random) {
		return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide() && !oldState.is(this)) {
			level.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int power = power(state, AirGauge.air(level, pos));
		if (state.getValue(POWER) != power) {
			level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
			level.updateNeighbourForOutputSignal(pos, this);
		}
		level.scheduleTick(pos, this, LOOK_EVERY);
	}

	/** The signal for air of {@code air} body heat in this gauge's setting. */
	static int power(BlockState state, float air) {
		int hot = AirGauge.scale(air);
		return state.getValue(HOT) ? hot : 15 - hot;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean hot = !state.getValue(HOT);
		// the same air, the signal turned round
		BlockState turned = state.setValue(HOT, hot).setValue(POWER, 15 - state.getValue(POWER));
		level.setBlock(pos, turned, Block.UPDATE_ALL);
		level.updateNeighbourForOutputSignal(pos, this);
		level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, hot ? 0.6F : 0.55F);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable(hot ? "message.jaymoskitchen.gauge.hot" : "message.jaymoskitchen.gauge.cold"));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWER);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return state.getValue(POWER);
	}
}
