package com.jaymoskingdoms.mixin.fts;

import com.jaymoskingdoms.inhabitants.fts.FtsEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Heal Block (From the Shadows Reborn's effect, Sonmok14 and L_Ender, AGPL v3): while it lasts no healing reaches the body, from
 * food, potions, regeneration or anything else that goes through {@code LivingEntity.heal}.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityHealBlockMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$healBlocked(float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && FtsEffects.healBlocked(self)) {
			ci.cancel();
		}
	}
}
