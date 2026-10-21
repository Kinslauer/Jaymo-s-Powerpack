package com.jaymoskitchen.mixin;

import com.jaymoskitchen.season.SeasonalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.6) Winter's ice melts out of winter (see {@link SeasonalWeather}). */
@Mixin(IceBlock.class)
abstract class IceMeltMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$thaw(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (SeasonalWeather.meltIce(level, pos, random)) {
			ci.cancel();
		}
	}
}
