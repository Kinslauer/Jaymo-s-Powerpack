package com.jaymoskitchen.mixin;

import com.jaymoskitchen.climate.FiniteWater;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Rain on a pond ({@link FiniteWater}): water that settled in dry weather never ticks again on its own, so now and then
 * a random tick on a flowing water block under rain wakes it, and two sources beside it make it a source again.
 */
@Mixin(WaterFluid.class)
abstract class RainRefillMixin extends Fluid {
	@Override
	protected boolean isRandomlyTicking() {
		return true;
	}

	@Override
	protected void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
		if (!state.isSource() && FiniteWater.raining(level, pos)) {
			level.scheduleTick(pos, state.getType(), state.getType().getTickDelay(level));
		}
	}
}
