package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.disasters.Storms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.6.10) Storm cells' local weather ({@link Storms}): rain falls on the ground under a cell, on both sides, whatever
 * vanilla's weather is; and on the client, the sky darkens, rain falls and thunder rolls when you're under or near one.
 */
@Mixin(Level.class)
abstract class LevelWeatherMixin {
	@WrapOperation(method = "precipitationAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isRaining()Z"))
	private boolean jaymoskingdoms$stormRain(Level level, Operation<Boolean> original, BlockPos pos) {
		return original.call(level) || level.canHaveWeather() && Storms.cover(level, pos.getX() + 0.5, pos.getZ() + 0.5) > Storms.RAINS;
	}

	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$clientRain(float a, CallbackInfoReturnable<Float> cir) {
		Level level = (Level)(Object)this;
		if (level.isClientSide() && level.dimension() == Level.OVERWORLD && Storms.clientRain > cir.getReturnValueF()) {
			cir.setReturnValue(Storms.clientRain);
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$clientThunder(float a, CallbackInfoReturnable<Float> cir) {
		Level level = (Level)(Object)this;
		if (level.isClientSide() && level.dimension() == Level.OVERWORLD && Storms.clientThunder > cir.getReturnValueF()) {
			cir.setReturnValue(Storms.clientThunder);
		}
	}
}
