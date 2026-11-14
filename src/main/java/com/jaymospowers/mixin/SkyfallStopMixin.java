package com.jaymospowers.mixin;

import com.jaymospowers.aether.world.SkyfallCleanup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water stops at the sky's floor in a One Dimension world until Dread ({@link SkyfallCleanup#blocks}). */
@Mixin(FlowingFluid.class)
abstract class SkyfallStopMixin {
	@Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$skyfallStop(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluid, CallbackInfo ci) {
		if (SkyfallCleanup.blocks(level, pos, direction)) {
			ci.cancel();
		}
	}
}
