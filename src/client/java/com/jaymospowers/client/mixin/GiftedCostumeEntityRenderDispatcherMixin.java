package com.jaymospowers.client.mixin;

import com.jaymospowers.client.costume.GiftedCostumeClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A gifted villager in their costume is drawn as a player body wearing it (GiftedCostumeClient). Last of the extract's
 * hooks (this priority), so what others put on the villager's state (speech, quest marks) is on the player's too.
 */
@Mixin(value = EntityRenderDispatcher.class, priority = 2100)
abstract class GiftedCostumeEntityRenderDispatcherMixin {
	@Inject(method = "extractEntity", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$giftedCostume(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		EntityRenderState state = cir.getReturnValue();
		EntityRenderState swapped = GiftedCostumeClient.swap(entity, state);
		if (swapped != state) {
			cir.setReturnValue(swapped);
		}
	}
}
