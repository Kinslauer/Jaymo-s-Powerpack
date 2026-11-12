package com.jaymoskingdoms.client.mixin.spirits;

import com.jaymoskingdoms.spirits.HandGrip;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While your spirit's arms hold something, the wheel reels it in or out instead of turning the hotbar. */
@Mixin(value = MouseHandler.class, priority = 450)
abstract class SpiritReelScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$spiritReel(long window, double xOffset, double yOffset, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (yOffset == 0.0 || player == null || minecraft.gui.screen() != null || player.isShiftKeyDown()
			|| !player.hasAttached(HandGrip.GRIPPING) || !ClientPlayNetworking.canSend(HandGrip.Reel.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new HandGrip.Reel(yOffset > 0.0 ? 1 : -1));
		ci.cancel();
	}
}
