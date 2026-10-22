package com.jaymospowers.tinkering.forge;

import com.jaymospowers.tinkering.smeltery.DrainEntity;
import com.jaymospowers.tinkering.smeltery.SmelteryEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Bellows (after Better With Mods' bellows, Copyright (c) 2016-2018 BeetoGuy, primetoxinz, BordListian, MIT; our own
 * code). Pump it (right-click, at a little hunger) or blow wind into it (Wind Whistle) and for {@link #BELLOWS_TICKS}
 * the block it faces burns hotter: a fire counts {@link #HEAT_BONUS} more heat (a kiln over it, or a kiln it blows
 * into, fires hotter and faster), and a smeltery it blows into melts {@link #MELT_SPEED} times as fast.
 */
public class BellowsBlock extends HorizontalDirectionalBlock {
	/** How long one pump lasts (ticks). */
	public static final int BELLOWS_TICKS = 100;
	/** Hunger one pump by hand costs. */
	public static final float BELLOWS_EXHAUSTION = 0.4F;
	/** Heat it adds to the fire it blows on, and how much faster a smeltery it blows into melts. */
	public static final int HEAT_BONUS = 1, MELT_SPEED = 2;
	/** Squeezed shut: it's blowing. */
	public static final BooleanProperty PUMPED = BooleanProperty.create("pumped");

	public BellowsBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PUMPED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PUMPED);
	}

	/** It blows the way you face when you set it down. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && !state.getValue(PUMPED)) {
			pump((ServerLevel) level, pos, state);
			player.causeFoodExhaustion(BELLOWS_EXHAUSTION);
		}
		return InteractionResult.SUCCESS;
	}

	/** Wind blown into {@code pos} (Wind Whistle): a bellows there pumps, for free. */
	public static void gust(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BellowsBlock && !state.getValue(PUMPED)) {
			pump(level, pos, state);
		}
	}

	private static void pump(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(PUMPED, true), Block.UPDATE_ALL);
		level.scheduleTick(pos, state.getBlock(), BELLOWS_TICKS);
		Direction facing = state.getValue(FACING);
		level.playSound(null, pos, SoundEvents.BREEZE_IDLE_AIR, SoundSource.BLOCKS, 0.8F, 0.6F);
		level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5 + facing.getStepX() * 0.7, pos.getY() + 0.4,
			pos.getZ() + 0.5 + facing.getStepZ() * 0.7, 4, 0.1, 0.1, 0.1, 0.02);
		BlockPos front = pos.relative(facing);
		SmelteryEntity smeltery = level.getBlockEntity(front) instanceof SmelteryEntity s ? s
			: level.getBlockEntity(front) instanceof DrainEntity drain ? drain.smeltery() : null;
		if (smeltery != null) {
			smeltery.blow(level.getGameTime() + BELLOWS_TICKS);
		}
	}

	/** The pump runs out: it opens again. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PUMPED)) {
			level.setBlock(pos, state.setValue(PUMPED, false), Block.UPDATE_ALL);
		}
	}

	/** Whether a pumped bellows next to {@code pos} is blowing into it. */
	public static boolean blowing(Level level, BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState state = level.getBlockState(pos.relative(side));
			if (state.getBlock() instanceof BellowsBlock && state.getValue(PUMPED) && state.getValue(FACING) == side.getOpposite()) {
				return true;
			}
		}
		return false;
	}
}
