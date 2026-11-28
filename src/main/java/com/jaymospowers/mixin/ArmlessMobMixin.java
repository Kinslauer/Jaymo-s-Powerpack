package com.jaymospowers.mixin;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.ArmlessMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob missing an arm hits half as hard; with both arms gone it holds and picks up nothing, and can only bite (headed
 * zombies) or kick (anyone with a leg left) at low damage, or make no attack at all (see ArmlessMobs, Dismember).
 */
@Mixin(Mob.class)
abstract class ArmlessMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"))
	private void jaymospowers$armless(CallbackInfo ci) {
		ArmlessMobs.tick((Mob) (Object) this);
	}

	@Inject(method = "serverAiStep", at = @At("TAIL"))
	private void jaymospowers$armlessHandsEmpty(CallbackInfo ci) {
		ArmlessMobs.keepEmpty((Mob) (Object) this);
	}

	@Inject(method = "canHoldItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noHandsToHold(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
		if (Dismember.armless((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$armlessBeforeStrike(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		Mob self = (Mob) (Object) this;
		if (!Dismember.canAttack(self)) {
			cir.setReturnValue(false);
			return;
		}
		if (Dismember.armless(self)) {
			ArmlessMobs.keepEmpty(self);
			ArmlessMobs.sync(self);
		}
	}

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void jaymospowers$armlessAfterStrike(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		Mob self = (Mob) (Object) this;
		if (cir.getReturnValueZ() && Dismember.armless(self) && target instanceof LivingEntity living) {
			Dismember.armlessBlow(level, self, living);
		}
	}
}
