package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.delight.SkilletFlipState;
import com.jaymoskitchen.delight.SkilletItem;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (From Farmer's Delight, MIT.) Notes when the pan a humanoid is cooking with was flipped, for its arm to follow. */
@Mixin(HumanoidMobRenderer.class)
public class HumanoidMobRendererMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void jaymoskitchen$extractFlip(LivingEntity entity, HumanoidRenderState state, float partialTick, ItemModelResolver resolver, CallbackInfo ci) {
		((SkilletFlipState) state).jaymoskitchen$setFlipTimestamp(entity.getUseItem().getOrDefault(SkilletItem.FLIP_TIMESTAMP, -1L));
	}
}
