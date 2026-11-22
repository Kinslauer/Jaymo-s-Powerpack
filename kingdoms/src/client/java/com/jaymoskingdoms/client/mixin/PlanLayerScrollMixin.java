package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.PlanGhosts;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ctrl and scroll while holding a laid building plan steps its layer view (PlanGhosts), instead of the hotbar. */
@Mixin(MouseHandler.class)
abstract class PlanLayerScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$planLayer(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (window == net.minecraft.client.Minecraft.getInstance().getWindow().handle() && PlanGhosts.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
