package com.jaymospowers.mixin.icecaves;

import com.jaymospowers.frost.IceCaves;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water beside blue ice freezes into brittle ice (26.4): checked where the game checks lava meeting water, when the water is
 * placed or flows, or something next to it changes.
 */
@Mixin(LiquidBlock.class)
abstract class BlueIceWaterMixin {
	@Shadow
	@Final
	protected FlowingFluid fluid;

	@Inject(method = "shouldSpreadLiquid", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$brittleOnBlueIce(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (this.fluid.is(FluidTags.WATER) && IceCaves.freezeOnBlueIce(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
