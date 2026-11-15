package com.jaymospowers.tinkering.coke;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Creosote, the black oil a coke oven sweats out ({@link CokeOvenEntity}): thick and slow, it creeps like lava in the
 * Overworld ({@link #TICK_DELAY}, {@link #SLOPE}, {@link #DROP_OFF}) and never makes new sources. Poured from a bucket it
 * lies in the world; a bucket or clay bucket takes it back up.
 */
public abstract class CreosoteFluid extends FlowingFluid {
	/** Ticks between spreads, how far it looks for a slope, and how much it thins a block. */
	static final int TICK_DELAY = 30, SLOPE = 2, DROP_OFF = 2;

	@Override
	public Fluid getFlowing() {
		return CokeAndSteel.FLOWING_CREOSOTE;
	}

	@Override
	public Fluid getSource() {
		return CokeAndSteel.CREOSOTE;
	}

	@Override
	public Item getBucket() {
		return CokeAndSteel.CREOSOTE_BUCKET;
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == CokeAndSteel.CREOSOTE || other == CokeAndSteel.FLOWING_CREOSOTE;
	}

	@Override
	protected boolean canConvertToSource(ServerLevel level) {
		return false;
	}

	@Override
	protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		Block.dropResources(state, level, pos, blockEntity);
	}

	@Override
	protected int getSlopeFindDistance(LevelReader level) {
		return SLOPE;
	}

	@Override
	protected int getDropOff(LevelReader level) {
		return DROP_OFF;
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return TICK_DELAY;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
		return direction == Direction.DOWN && !isSame(other);
	}

	@Override
	protected float getExplosionResistance() {
		return 100.0F;
	}

	@Override
	protected BlockState createLegacyBlock(FluidState state) {
		return CokeAndSteel.CREOSOTE_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
	}

	public static class Flowing extends CreosoteFluid {
		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getAmount(FluidState state) {
			return state.getValue(LEVEL);
		}

		@Override
		public boolean isSource(FluidState state) {
			return false;
		}
	}

	public static class Source extends CreosoteFluid {
		@Override
		public int getAmount(FluidState state) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState state) {
			return true;
		}
	}
}
