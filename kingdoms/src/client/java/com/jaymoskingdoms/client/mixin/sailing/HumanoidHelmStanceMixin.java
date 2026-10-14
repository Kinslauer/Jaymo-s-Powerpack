package com.jaymoskingdoms.client.mixin.sailing;

import com.jaymoskingdoms.sailing.Ship;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.7) Whoever is at a ship's wheel stands to it rather than sitting. */
@Mixin(HumanoidMobRenderer.class)
abstract class HumanoidHelmStanceMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void jaymoskingdoms$standAtHelm(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver,
		CallbackInfo ci) {
		if (entity.getVehicle() instanceof Ship) {
			state.isPassenger = false;
		}
	}
}
