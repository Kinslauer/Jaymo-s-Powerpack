package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farmwater.Scarecrows;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A rabbit won't raid carrots a scarecrow with a pumpkin guards ({@link Scarecrows#guarded}). */
@Mixin(targets = "net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal")
abstract class RaidGardenGoalMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private Rabbit rabbit;

	@Inject(method = "isValidTarget", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$scarecrow(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (Scarecrows.guarded(rabbit.level(), pos.above())) {
			cir.setReturnValue(false);
		}
	}
}
