package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourWear;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.20.68) Mending mends nothing mid-fight; the XP goes to you as plain XP ({@link ArmourWear#refuseMending}). */
@Mixin(ExperienceOrb.class)
abstract class ArmourWearMendingMixin {
	@Inject(method = "repairPlayerItems", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$notInAFight(ServerPlayer player, int amount, CallbackInfoReturnable<Integer> cir) {
		if (ArmourWear.refuseMending(player)) {
			cir.setReturnValue(amount);
		}
	}
}
