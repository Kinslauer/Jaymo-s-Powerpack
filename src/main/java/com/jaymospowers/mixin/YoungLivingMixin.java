package com.jaymospowers.mixin;

import com.jaymospowers.young.Younglings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.18.115) Younglings (Younglings) squeak higher and drop nothing. */
@Mixin(LivingEntity.class)
abstract class YoungLivingMixin {
	@Inject(method = "getVoicePitch", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$youngVoice(CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Younglings.voice((LivingEntity)(Object)this, cir.getReturnValueF()));
	}

	@Inject(method = "shouldDropLoot", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$youngNoLoot(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
		if (Younglings.young((LivingEntity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
