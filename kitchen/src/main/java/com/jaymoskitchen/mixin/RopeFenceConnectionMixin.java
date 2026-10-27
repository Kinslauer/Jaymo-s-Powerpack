package com.jaymoskitchen.mixin;

import com.jaymoskitchen.delight.DelightFarm;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Farmer's Delight's, MIT.) Other fences don't join onto rope fences and their gates: rope fences keep to their own kind. */
@Mixin(FenceBlock.class)
abstract class RopeFenceConnectionMixin {
	@Inject(method = "connectsTo", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$denyRopeGate(BlockState state, boolean sideSolid, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (state.is(DelightFarm.ROPE_FENCE_GATE)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isSameFence", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$denyRopeFence(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (state.is(DelightFarm.ROPE_FENCE)) {
			cir.setReturnValue(false);
		}
	}
}
