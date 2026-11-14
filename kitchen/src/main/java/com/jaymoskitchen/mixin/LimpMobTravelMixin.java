package com.jaymoskitchen.mixin;

import com.jaymoskitchen.ragdoll.MobRagdolls;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob knocked limp (see MobRagdolls) doesn't move itself: its ragdoll puts it where it lies each tick, so its own
 * walking, gravity and friction stay out of it (and a blow's knockback is left in its speed for the ragdoll to take).
 */
@Mixin(LivingEntity.class)
abstract class LimpMobTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limp(Vec3 input, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && MobRagdolls.isLimp(self)) {
			ci.cancel();
		}
	}
}
