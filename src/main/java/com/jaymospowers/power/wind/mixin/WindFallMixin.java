package com.jaymospowers.power.wind.mixin;

import com.jaymospowers.power.wind.WindFall;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.15.54) A Wind user's fall damage: none once mastered enough, else scaled by the landing speed (see WindFall). */
@Mixin(LivingEntity.class)
abstract class WindFallMixin {
	@Inject(method = "calculateFallDamage", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$windFall(double distance, float multiplier, CallbackInfoReturnable<Integer> cir) {
		if ((Object)this instanceof ServerPlayer player) {
			cir.setReturnValue(WindFall.damage(player, cir.getReturnValueI()));
		}
	}
}
