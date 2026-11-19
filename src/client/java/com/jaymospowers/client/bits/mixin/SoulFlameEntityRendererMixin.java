package com.jaymospowers.client.bits.mixin;

import com.jaymospowers.client.bits.SoulFlames;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Soul fire burns blue: every entity's render state carries whether its flames are the soul fire's ({@link SoulFlames}). */
@Mixin(EntityRenderer.class)
abstract class SoulFlameEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$soulFlames(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		SoulFlames.extract(entity, state);
	}
}
