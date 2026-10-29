package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.client.BloodVision;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blood vision's outline beats red (a creature glowing for any other reason keeps its own colour); a vampire player's looks (and bat wings) go on the state. */
@Mixin(LivingEntityRenderer.class)
abstract class BloodGlowColorMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$bloodColor(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		state.setData(com.jaymoskingdoms.client.VampireLooks.LOOK, com.jaymoskingdoms.client.VampireLooks.look(entity));
		state.setData(com.jaymoskingdoms.client.VampireLooks.PALLOR, com.jaymoskingdoms.client.VampireLooks.pallor(entity));
		state.setData(com.jaymoskingdoms.client.VampireLooks.MARKS, com.jaymoskingdoms.client.VampireLooks.marks(entity));
		state.setData(com.jaymoskingdoms.client.NightGiftsClient.WINGS, com.jaymoskingdoms.client.NightGiftsClient.wings(entity));
		if (state.outlineColor != 0 && !entity.isCurrentlyGlowing() && BloodVision.sensed(entity)) {
			state.outlineColor = com.jaymoskingdoms.client.NightGiftsClient.revealed(entity) ? com.jaymoskingdoms.client.NightGiftsClient.revealColor()
				: BloodVision.color(entity, partialTicks);
		}
	}
}
