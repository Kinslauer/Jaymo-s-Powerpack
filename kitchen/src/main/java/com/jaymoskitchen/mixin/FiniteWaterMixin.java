package com.jaymoskitchen.mixin;

import com.jaymoskitchen.climate.FiniteWater;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Water you can't copy ({@link FiniteWater}): the game's own check, with where the new source would be. Lava is left alone. */
@Mixin(FlowingFluid.class)
abstract class FiniteWaterMixin {
	@WrapOperation(method = "getNewLiquid", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/material/FlowingFluid;canConvertToSource(Lnet/minecraft/server/level/ServerLevel;)Z"))
	private boolean jaymoskitchen$finiteWater(FlowingFluid self, ServerLevel level, Operation<Boolean> original,
		@Local(argsOnly = true) BlockPos pos) {
		return original.call(self, level) && (!(self instanceof WaterFluid) || FiniteWater.renews(level, pos));
	}
}
