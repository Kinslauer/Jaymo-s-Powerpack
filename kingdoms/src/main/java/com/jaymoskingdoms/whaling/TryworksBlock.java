package com.jaymoskingdoms.whaling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (0.7.206) Whaling, stage 2: the tryworks, a brick hearth under an iron try-pot where blubber is boiled down to whale oil.
 * Use blubber on it to fill the pot ({@link #PER_LOAD} pieces a measure, {@link #MOST_BLUBBER} measures), flint and steel
 * (or a fire charge) to light it; it keeps itself burning on the fritters of what it boils, as the whalers' did, every
 * {@link #RENDER_TICKS} turning a measure of blubber into a bottle's worth of oil ({@link #MOST_OIL} at most), and goes
 * out when the pot's dry of blubber. Use a glass bottle on it to draw off whale oil. It keeps all this in its block
 * state (no block entity), so it rides a ship's deck like any other block, and works again once she's at anchor.
 */
public final class TryworksBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** Measures of blubber in the pot, and bottles' worth of oil rendered. */
	public static final int MOST_BLUBBER = 8, MOST_OIL = 4;
	public static final IntegerProperty BLUBBER = IntegerProperty.create("blubber", 0, MOST_BLUBBER);
	public static final IntegerProperty OIL = IntegerProperty.create("oil", 0, MOST_OIL);
	/** Blubber pieces to a measure, and how long a measure takes to render (ticks). */
	static final int PER_LOAD = 2, RENDER_TICKS = 160;

	public TryworksBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(BLUBBER, 0).setValue(OIL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, BLUBBER, OIL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (held.is(Whaling.WHALE_BLUBBER)) {
			int blubber = state.getValue(BLUBBER);
			if (blubber >= MOST_BLUBBER || held.getCount() < PER_LOAD && !player.hasInfiniteMaterials()) {
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				held.consume(PER_LOAD, player);
				level.setBlock(pos, state.setValue(BLUBBER, blubber + 1), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.8F, 0.6F);
			}
			return InteractionResult.SUCCESS;
		}
		if ((held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) && !state.getValue(LIT) && state.getValue(BLUBBER) > 0) {
			if (level instanceof ServerLevel server) {
				if (held.is(Items.FLINT_AND_STEEL)) {
					held.hurtAndBreak(1, player, hand);
					level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				} else {
					held.consume(1, player);
					level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
				level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
				server.scheduleTick(pos, this, RENDER_TICKS);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.is(Items.GLASS_BOTTLE) && state.getValue(OIL) > 0) {
			if (!level.isClientSide()) {
				held.consume(1, player);
				ItemStack oil = new ItemStack(Whaling.WHALE_OIL);
				if (!player.addItem(oil)) {
					player.spawnAtLocation((ServerLevel) level, oil);
				}
				level.setBlock(pos, state.setValue(OIL, state.getValue(OIL) - 1), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Set down already burning (a ship's deck laid back into the world, say): it goes on boiling. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (state.getValue(LIT) && !oldState.is(this) && level instanceof ServerLevel server) {
			server.scheduleTick(pos, this, RENDER_TICKS);
		}
	}

	/** A measure rendered down (if there's blubber and room for the oil), and on, or out. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		int blubber = state.getValue(BLUBBER), oil = state.getValue(OIL);
		if (blubber > 0 && oil < MOST_OIL) {
			blubber--;
			oil++;
			level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 0.7F);
		}
		boolean burning = blubber > 0 && oil < MOST_OIL;
		level.setBlock(pos, state.setValue(BLUBBER, blubber).setValue(OIL, oil).setValue(LIT, burning), Block.UPDATE_ALL);
		if (burning) {
			level.scheduleTick(pos, this, RENDER_TICKS);
		} else {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.2F);
		}
	}

	/** Lit: the fire under the pot, the pot's greasy black smoke, and its bubbling. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		Direction facing = state.getValue(FACING);
		double out = 0.52;
		level.addParticle(ParticleTypes.FLAME, x + facing.getStepX() * out, y + 0.25, z + facing.getStepZ() * out, 0.0, 0.0, 0.0);
		level.addParticle(ParticleTypes.LARGE_SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y + 1.1, z + (random.nextDouble() - 0.5) * 0.4,
			0.0, 0.06, 0.0);
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.0, z, 0.0, 0.05, 0.0);
		}
		if (random.nextInt(8) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 0.8F, false);
		}
	}
}
