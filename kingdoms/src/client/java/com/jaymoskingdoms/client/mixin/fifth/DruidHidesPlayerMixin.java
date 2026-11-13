package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.Druids;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Druid in a wild shape is the beast that follows them ({@link Druids}); their own body is not drawn, so nothing of it (held items,
 * worn gear) floats where it was.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class DruidHidesPlayerMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void jaymoskingdoms$druidHidesPlayer(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
		CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && !player.getAttachedOrElse(Druids.FORM, "").isEmpty()) {
			cir.setReturnValue(false);
		}
	}
}
