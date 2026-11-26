package com.jaymospowers.client.mixin;

import com.jaymospowers.body.ShieldJolt;
import com.jaymospowers.client.body.BodyAnim;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blow taken on a shield, as the server tells it (ShieldBlockEventMixin): the shield arm takes the jolt (BodyAnim). */
@Mixin(LivingEntity.class)
abstract class ShieldJoltEventMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void jaymospowers$jolt(byte event, CallbackInfo ci) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (event == ShieldJolt.EVENT && self.level().isClientSide()) {
			BodyAnim.blocked(self);
		}
	}
}
