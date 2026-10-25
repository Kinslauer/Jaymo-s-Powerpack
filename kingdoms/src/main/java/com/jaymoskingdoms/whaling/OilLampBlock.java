package com.jaymoskingdoms.whaling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (0.7.206) An oil lamp: whale oil burning behind glass, standing or hung like a lantern, brighter than a torch. It holds
 * {@link #MOST_OIL} measures and comes empty (a bottle of whale oil fills it and gives the bottle back); lit, it burns a measure every
 * {@link #BURN_TICKS}, so a full lamp left burning lasts a day, and goes out dry. Use it with an empty hand to light it or
 * snuff it. In a settlement its folk tend their lamps ({@link Lamplighters}): lit at dusk, snuffed at dawn, filled from
 * the stores while there's oil.
 */
public final class OilLampBlock extends LanternBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final int MOST_OIL = 4;
	public static final IntegerProperty OIL = IntegerProperty.create("oil", 0, MOST_OIL);
	/** Ticks a measure of oil burns. */
	static final int BURN_TICKS = 6000;
	/** Its light, lit. */
	static final int LIGHT = 15;

	public OilLampBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(LIT, false).setValue(OIL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT, OIL);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!held.is(Whaling.WHALE_OIL)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (state.getValue(OIL) >= MOST_OIL) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
			fill(server, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(LIT) && state.getValue(OIL) == 0) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			setLit(server, pos, state, !state.getValue(LIT));
		}
		return InteractionResult.SUCCESS;
	}

	/** Filled to the brim. */
	static void fill(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(OIL, MOST_OIL), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 0.9F);
	}

	/** Lit (if it has oil) or snuffed. */
	static void setLit(ServerLevel level, BlockPos pos, BlockState state, boolean lit) {
		if (lit && state.getValue(OIL) == 0 || state.getValue(LIT) == lit) {
			return;
		}
		level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
		if (lit) {
			level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3F, 1.6F);
			level.scheduleTick(pos, state.getBlock(), BURN_TICKS);
		} else {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.8F);
		}
	}

	/** Set down (by a hand, a lamplighter, or a ship's deck laid back into the world): known to its town's lamplighters, and burning on if lit. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this) && level instanceof ServerLevel server) {
			Lamplighters.know(server, pos);
			if (state.getValue(LIT)) {
				server.scheduleTick(pos, this, BURN_TICKS);
			}
		}
	}

	/** A measure burnt; out when dry. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		int oil = state.getValue(OIL) - 1;
		if (oil <= 0) {
			level.setBlock(pos, state.setValue(OIL, 0).setValue(LIT, false), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.8F);
			return;
		}
		level.setBlock(pos, state.setValue(OIL, oil), Block.UPDATE_ALL);
		level.scheduleTick(pos, this, BURN_TICKS);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(5) == 0) {
			double y = pos.getY() + (state.getValue(HANGING) ? 0.85 : 0.6);
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, y, pos.getZ() + 0.5, 0.0, 0.01, 0.0);
		}
	}
}
