package com.jaymospowers.client.mixin;

import com.jaymospowers.core.wounds.Limbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy (2026-11-14): "3rd person as a limbo ghost didnt animate properly". Waiting in {@link Limbo} you are a spectator,
 * and vanilla draws a spectator in third person as a floating head that never moves. Your own is not drawn there: your
 * ghost is ({@code PlayerGhostRenderer}), drifting where you are.
 */
@Mixin(EntityRenderer.class)
abstract class LimboSelfHiddenMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$limboSelfHidden(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
		CallbackInfoReturnable<Boolean> cir) {
		if (entity == Minecraft.getInstance().player && Limbo.in(Minecraft.getInstance().player)) {
			cir.setReturnValue(false);
		}
	}
}
