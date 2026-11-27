package com.jaymospowers.mixin;

import com.jaymospowers.core.wet.RustingIron;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game's own iron blocks, bars, doors, trapdoors, chains and lanterns take random ticks, so they can rust (RustingIron). */
@Mixin(BlockBehaviour.class)
abstract class RustTickMixin {
	@Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$rusts(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (RustingIron.plainIron(state.getBlock())) {
			cir.setReturnValue(true);
		}
	}
}
