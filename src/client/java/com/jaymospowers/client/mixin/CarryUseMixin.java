package com.jaymospowers.client.mixin;

import com.jaymospowers.core.Carry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carrying someone, right-click (anywhere, at the air too) lets go: they drop from your arms, or with sneak held
 * they're thrown a short way ahead. The same controls as Jaymo's Kitchen's corpses.
 */
@Mixin(Minecraft.class)
abstract class CarryUseMixin {
	/** Ticks before the held use key lets go again (vanilla's own right-click repeat). */
	private static final int RELEASE_DELAY = 4;

	@Shadow
	public @Nullable LocalPlayer player;

	@Shadow
	private int rightClickDelay;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$letGo(CallbackInfo ci) {
		if (this.player == null || !Carry.isCarrying(this.player)) {
			return;
		}
		ClientPlayNetworking.send(new Carry.Release(this.player.isShiftKeyDown()));
		this.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		this.rightClickDelay = RELEASE_DELAY;
		ci.cancel();
	}
}
