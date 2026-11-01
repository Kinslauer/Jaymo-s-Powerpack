package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.forms.Billow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.19.29) Billow is cloud: creatures pass through it, neither pushing it nor pushed. */
@Mixin(LivingEntity.class)
abstract class BillowPushMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$billowPush(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof Player player && Billow.cloud(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$billowPushOthers(CallbackInfo ci) {
		if ((Object)this instanceof Player player && Billow.cloud(player)) {
			ci.cancel();
		}
	}
}
