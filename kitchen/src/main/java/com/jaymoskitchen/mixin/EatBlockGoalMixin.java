package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farmwater.Troughs;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A sheep fed at a trough ({@link Troughs#fed}) leaves the grass alone: a fed herd crops the ground less. */
@Mixin(EatBlockGoal.class)
abstract class EatBlockGoalMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private Mob mob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$fedSheepGraze(CallbackInfoReturnable<Boolean> cir) {
		if (mob instanceof Animal animal && Troughs.fed(animal)) {
			cir.setReturnValue(false);
		}
	}
}
