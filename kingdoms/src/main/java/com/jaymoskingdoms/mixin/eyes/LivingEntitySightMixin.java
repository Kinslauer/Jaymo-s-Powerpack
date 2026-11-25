package com.jaymoskingdoms.mixin.eyes;

import com.jaymoskingdoms.NpcEyes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (ey-body) A sight check that fails to the target's eyes tries the rest of the body ({@link NpcEyes#bodyVisible}): the chest,
 * the shoulders, the feet, so a shoulder round a corner is seen. Players as much as anyone.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntitySightMixin {
	@Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$wholeBody(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && NpcEyes.bodyVisible((LivingEntity) (Object) this, target)) {
			cir.setReturnValue(true);
		}
	}
}
