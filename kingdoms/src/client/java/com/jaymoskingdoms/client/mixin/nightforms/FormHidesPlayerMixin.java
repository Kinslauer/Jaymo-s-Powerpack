package com.jaymoskingdoms.client.mixin.nightforms;

import com.jaymoskingdoms.client.nightforms.NightFormsClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A vampire player in a shape (bat, mist, wolf) is not drawn: the puppet the server made is all that is seen of them. */
@Mixin(EntityRenderDispatcher.class)
abstract class FormHidesPlayerMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void jaymoskingdoms$formHidesPlayer(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
		CallbackInfoReturnable<Boolean> cir) {
		if (NightFormsClient.shaped(entity)) {
			cir.setReturnValue(false);
		}
	}
}
