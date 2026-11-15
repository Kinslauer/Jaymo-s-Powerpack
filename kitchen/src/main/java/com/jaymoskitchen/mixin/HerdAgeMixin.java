package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farm.Herds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Slow herds ({@link Herds#GROWTH}): an animal's age clock (growing up, the wait to breed again) skips some of its ticks. */
@Mixin(AgeableMob.class)
abstract class HerdAgeMixin {
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/AgeableMob;setAge(I)V"))
	private void jaymoskitchen$slowHerd(AgeableMob self, int age, Operation<Void> original) {
		if (!Herds.holdsBack(self)) {
			original.call(self, age);
		}
	}
}
