package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.DroppedArms;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.18.118) A mob killed outright mostly lets go of its weapons as it dies (DroppedArms), before its loot and corpse. */
@Mixin(LivingEntity.class)
abstract class DroppedArmsDeathMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void jaymospowers$dropArms(DamageSource source, CallbackInfo ci) {
		if (!((LivingEntity)(Object)this).isRemoved()) {
			DroppedArms.died((LivingEntity)(Object)this);
		}
	}
}
