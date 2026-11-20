package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.RodTip;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fishing line starts at the rod tip: marks a fishing player's rod hand when their held items are filled in. */
@Mixin(ArmedEntityRenderState.class)
abstract class RodTipArmedMixin {
	@Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
	private static void jaymospowers$rodTip(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver resolver, float partialTicks,
		CallbackInfo ci) {
		RodTip.armed(entity, state);
	}
}
