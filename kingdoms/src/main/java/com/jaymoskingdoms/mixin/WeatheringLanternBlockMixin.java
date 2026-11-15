package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.GutteringLamps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringLanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (medieval picks) Unwaxed copper lanterns take their random ticks their own way (to age), past LanternBlockMixin: an
 * oxidized one takes them too, and on them one in an abandoned place starts guttering ({@link GutteringLamps}).
 */
@Mixin(WeatheringLanternBlock.class)
abstract class WeatheringLanternBlockMixin {
	@Inject(method = "isRandomlyTicking", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$gutterTicks(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && GutteringLamps.mayGutter(state)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$gutter(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (GutteringLamps.gutter(state, level, pos)) {
			ci.cancel();
		}
	}
}
