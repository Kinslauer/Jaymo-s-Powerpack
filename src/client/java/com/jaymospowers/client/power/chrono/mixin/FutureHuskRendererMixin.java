package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.FutureDeadLooks;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A husk in the future wears one of our dried skins ({@link FutureDeadLooks}; picked in {@code FutureZombieRendererMixin}). */
@Mixin(HuskRenderer.class)
abstract class FutureHuskRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$futureHuskSkin(ZombieRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier skin = FutureDeadLooks.skin(state);
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
