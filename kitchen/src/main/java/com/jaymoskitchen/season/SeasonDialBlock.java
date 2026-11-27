package com.jaymoskitchen.season;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The season dial: a copper-cased face that shows the season of the calendar ({@link Seasons}) and gives it to redstone, for
 * farms and greenhouses. Its top shows the season, as a hand on a four-coloured face. Using it turns its setting
 * ({@link #MODE}): 0 gives a signal of 1 in spring, 2 in summer, 3 in autumn and 4 in winter; 1 to 4 give a full signal of
 * 15 only in spring, summer, autumn or winter respectively (and none in the others). Its side shows the setting. A
 * comparator reads the same number. It looks at the calendar every {@link #LOOK_EVERY} ticks, so a season shift (the
 * {@code /season set} command) shows within a few seconds.
 */
public class SeasonDialBlock extends Block {
	/** The season the face shows now: 0 spring, 1 summer, 2 autumn, 3 winter. */
	public static final IntegerProperty SEASON = IntegerProperty.create("season", 0, 3);
	/** 0 counts the seasons (1 to 4); 1 to 4 give 15 in that one season (spring to winter). */
	public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 4);
	/** The signal a dial in its single-season setting gives in its season. */
	public static final int FULL = 15;
	/** Ticks between looks at the calendar. */
	private static final int LOOK_EVERY = 100;

	public SeasonDialBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SEASON, 0).setValue(MODE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SEASON, MODE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(SEASON, Seasons.of(context.getLevel()).ordinal());
	}

	/** The signal this dial gives: the season's number in mode 0, else 15 in the chosen season. */
	public static int power(BlockState state) {
		int season = state.getValue(SEASON);
		int mode = state.getValue(MODE);
		if (mode == 0) {
			return season + 1;
		}
		return mode - 1 == season ? FULL : 0;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide() && !oldState.is(this)) {
			level.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int season = Seasons.of(level).ordinal();
		if (state.getValue(SEASON) != season) {
			level.setBlock(pos, state.setValue(SEASON, season), Block.UPDATE_ALL);
			level.updateNeighbourForOutputSignal(pos, this);
		}
		level.scheduleTick(pos, this, LOOK_EVERY);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		int mode = (state.getValue(MODE) + 1) % (Season.values().length + 1);
		level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_ALL);
		level.updateNeighbourForOutputSignal(pos, this);
		level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, 0.55F + 0.1F * mode);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(mode == 0 ? Component.translatable("message.jaymoskitchen.season_dial.count")
				: Component.translatable("message.jaymoskitchen.season_dial.one", Component.translatable("season.jaymoskitchen." + Season.values()[mode - 1].key())));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return power(state);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return power(state);
	}
}
