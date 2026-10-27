package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.spirits.SpiritArms;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.256) A spirit's hand can reach 20 blocks from its keeper, so the keeper's culling box grows by the reach while it's
 * out ({@link SpiritArms#cullingReach}): the arm is drawn the whole way even when its keeper is off screen.
 */
@Mixin(EntityRenderer.class)
abstract class SpiritCullingMixin {
	@Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$spiritReach(Entity entity, float partialTicks, CallbackInfoReturnable<AABB> cir) {
		float reach = SpiritArms.cullingReach(entity);
		if (reach > 0.0F) {
			cir.setReturnValue(cir.getReturnValue().inflate(reach));
		}
	}
}
