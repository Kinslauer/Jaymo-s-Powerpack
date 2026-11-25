package com.jaymospowers.mixin;

import com.jaymospowers.baubles.WornTotems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No totem in hand: a worn one (bauble slot) or a carried one (some villagers and illagers) gets its turn (WornTotems). */
@Mixin(LivingEntity.class)
abstract class WornTotemMixin {
	@Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$wornTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && WornTotems.save((LivingEntity)(Object)this, source)) {
			cir.setReturnValue(true);
		}
	}
}
