package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Necromancy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.228) A death blight ({@link Necromancy#blighted}): no healing reaches the body (food, potions, regeneration) till it wears off. */
@Mixin(LivingEntity.class)
abstract class LivingEntityHealMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$blighted(float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && Necromancy.blighted(self)) {
			ci.cancel();
		}
	}
}
