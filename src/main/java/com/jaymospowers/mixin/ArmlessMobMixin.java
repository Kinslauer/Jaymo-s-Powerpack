package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.ArmlessMobs;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob missing an arm hits half as hard, and with none only kicks (see ArmlessMobs). */
@Mixin(Mob.class)
abstract class ArmlessMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"))
	private void jaymospowers$armless(CallbackInfo ci) {
		ArmlessMobs.tick((Mob) (Object) this);
	}
}
