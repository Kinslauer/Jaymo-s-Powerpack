package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.NightLights;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NPCs need light to see by ({@link NightLights#makesOut}): a clear line to someone in the dark is no sight of them unless
 * they are near, or lit, or the looker's own light reaches them. Every sight check a mob makes comes here (targeting, a
 * witness to a theft, a villager coming over to talk), so all of them follow the light.
 */
@Mixin(LivingEntity.class)
abstract class NightSightMixin {
	@Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
		at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$needsLight(Entity target, ClipContext.Block block, ClipContext.Fluid fluid, double eyeY, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !NightLights.makesOut((LivingEntity) (Object) this, target)) {
			cir.setReturnValue(false);
		}
	}
}
