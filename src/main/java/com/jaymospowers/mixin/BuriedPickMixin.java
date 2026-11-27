package com.jaymospowers.mixin;

import com.jaymospowers.combat.HuskBurrow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A buried husk ({@link HuskBurrow#hidden}) can't be picked: no crosshair, arrow or swing finds it until it digs out. */
@Mixin(LivingEntity.class)
abstract class BuriedPickMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$buried(CallbackInfoReturnable<Boolean> cir) {
		if (HuskBurrow.hidden((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
