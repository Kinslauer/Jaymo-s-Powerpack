package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.CloudDecks;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's single sheet of cloud is left out while our layered decks are up ({@link CloudDecks}); both of its draws, the plain and the transparency-sorted one. */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
	@Inject(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$decksInstead(CallbackInfo ci) {
		if (CloudDecks.replaces()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
		+ "Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$decksInsteadOit(CallbackInfo ci) {
		if (CloudDecks.replaces()) {
			ci.cancel();
		}
	}
}
