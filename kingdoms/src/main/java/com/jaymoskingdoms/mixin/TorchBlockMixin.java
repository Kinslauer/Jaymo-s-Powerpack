package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Torches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * (0.7.17) Torches (floor and wall) take random ticks, on which they burn down ({@link Torches}). Set in the world they
 * start their time: set down by hand, the whole of it.
 */
@Mixin(TorchBlock.class)
abstract class TorchBlockMixin extends BaseTorchBlock {
	protected TorchBlockMixin(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return Torches.burns(state) || super.isRandomlyTicking(state);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (Torches.burns(state)) {
			Torches.randomTick(state, level, pos, random);
		} else {
			super.randomTick(state, level, pos, random);
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel server && Torches.burns(state) && !oldState.is(state.getBlock())) {
			Torches.lit(server, pos, false);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (level instanceof ServerLevel server && Torches.burns(state)) {
			Torches.lit(server, pos, true);
		}
	}
}
