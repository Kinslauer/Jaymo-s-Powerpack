package com.jaymospowers.client.power.blindness.mixin;

import com.jaymospowers.client.power.blindness.BlindnessDevoutSight;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The devout's sight's outline colour: pale gold, a dull red for the hostile. */
@Mixin(EntityRenderer.class)
abstract class BlindnessSightOutlineMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$devoutSight(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		int color = BlindnessDevoutSight.color(entity);
		if (color != 0) {
			state.outlineColor = 0xFF000000 | color;
		}
	}
}
