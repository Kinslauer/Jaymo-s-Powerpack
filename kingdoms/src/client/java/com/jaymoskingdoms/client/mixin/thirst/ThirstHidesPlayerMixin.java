package com.jaymoskingdoms.client.mixin.thirst;

import com.jaymoskingdoms.client.thirst.ThirstClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A thirsty player in a shape (bat, mist, wolf) is not drawn: the puppet the server made is all that is seen of them. */
@Mixin(EntityRenderDispatcher.class)
abstract class ThirstHidesPlayerMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void jaymoskingdoms$thirstHidesPlayer(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
		CallbackInfoReturnable<Boolean> cir) {
		if (ThirstClient.shaped(entity)) {
			cir.setReturnValue(false);
		}
	}
}
