package com.jaymospowers.mixin;

import com.jaymospowers.core.wet.RustingIron;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A random tick of wet iron may rust it a step (RustingIron); whatever else the block does on one still happens. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class RustWeatherMixin {
	@Inject(method = "randomTick", at = @At("HEAD"))
	private void jaymospowers$rust(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		RustingIron.weather((BlockState) (Object) this, level, pos, random);
	}
}
