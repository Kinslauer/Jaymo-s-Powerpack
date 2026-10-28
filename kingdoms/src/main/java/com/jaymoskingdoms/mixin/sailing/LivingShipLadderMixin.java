package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.Ships;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.285) A ladder aboard a ship under sail is climbed as one in the world is ({@link Ships#climbing}); both sides. */
@Mixin(LivingEntity.class)
abstract class LivingShipLadderMixin {
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$shipLadder(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!cir.getReturnValueZ() && !self.isSpectator() && Ships.climbing(self)) {
			cir.setReturnValue(true);
		}
	}
}
