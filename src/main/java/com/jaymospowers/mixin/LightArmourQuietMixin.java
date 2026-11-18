package com.jaymospowers.mixin;

import com.jaymospowers.core.LightArmour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The distance mobs notice a body at scales with the armour it wears (see {@link LightArmour#noticed}). */
@Mixin(LivingEntity.class)
abstract class LightArmourQuietMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$armourNoise(ServerLevel level, Entity targeting, CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(cir.getReturnValue() * LightArmour.noticed((LivingEntity) (Object) this));
	}
}
