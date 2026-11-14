package com.jaymospowers.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.ShallowSwim;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sprinting while touching water starts a swim even with the eyes above it ({@link ShallowSwim}). */
@Mixin(Entity.class)
abstract class ShallowSwimMixin {
	@WrapOperation(method = "updateSwimming", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isUnderWater()Z"))
	private boolean jaymospowers$shallowSwim(Entity self, Operation<Boolean> original) {
		return original.call(self) || ShallowSwim.allowed(self);
	}
}
