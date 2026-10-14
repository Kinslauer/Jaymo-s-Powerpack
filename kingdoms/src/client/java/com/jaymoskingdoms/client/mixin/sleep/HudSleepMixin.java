package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.RestClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.4) The bed's darkness as {@link RestClient} has it: dim while resting, clear while the night runs by. */
@Mixin(Hud.class)
abstract class HudSleepMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "extractSleepOverlay", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$sleepOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		ci.cancel();
		if (this.minecraft.player == null || this.minecraft.player.getSleepTimer() <= 0) {
			return;
		}
		float amount = RestClient.overlay();
		if (amount > 0.0F) {
			graphics.nextStratum();
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int)(220.0F * amount) << 24 | 1052704);
		}
	}
}
