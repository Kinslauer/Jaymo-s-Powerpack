package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourWear;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.20.68) An NPC off the battlefield mends its worn armour ({@link ArmourWear#rest}). */
@Mixin(Mob.class)
abstract class ArmourWearMobMixin {
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void jaymospowers$restArmour(CallbackInfo ci) {
		ArmourWear.rest((Mob) (Object) this);
	}
}
