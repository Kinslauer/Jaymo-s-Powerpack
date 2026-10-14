package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.Ships;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.7.5) In a ship's dry hold you stand in the dry, though the sea outside rides higher than its floor. */
@Mixin(EntityFluidInteraction.class)
abstract class FluidInShipMixin {
	@WrapOperation(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
	private FluidState jaymoskingdoms$dryHold(BlockGetter level, BlockPos pos, Operation<FluidState> original,
		@Local(argsOnly = true) Entity entity) {
		FluidState fluid = original.call(level, pos);
		if (!fluid.isEmpty() && !(entity instanceof com.jaymoskingdoms.sailing.Ship) && Ships.dry(entity.level(), pos)) {
			return Fluids.EMPTY.defaultFluidState();
		}
		return fluid;
	}
}
