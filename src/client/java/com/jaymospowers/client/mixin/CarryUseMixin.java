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
 * they're thrown a short way ahead. The same controls as Jaymo's Kitchen's corpses. (0.17.7) Only a fresh press: the
 * held right-click that lifted them doesn't put them straight back down.
 */
@Mixin(Minecraft.class)
abstract class CarryUseMixin {
	/** Ticks before the held use key lets go again (vanilla's own right-click repeat). */
	private static final int RELEASE_DELAY = 4;

	@Shadow
	public @Nullable LocalPlayer player;

	@Shadow
	private int rightClickDelay;

	/** (0.17.7) Use repeats this often while held (and a little slack): a call within it is the same hold. */
	private static final int SAME_HOLD = RELEASE_DELAY + 1;

	/** (0.17.7) When use last came (the player's tick count): a pick-up is a held lift now, and that hold mustn't let go at once. */
	private static int jaymospowers$lastUse = Integer.MIN_VALUE / 2;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$letGo(CallbackInfo ci) {
		if (this.player == null) {
			return;
		}
		int now = this.player.tickCount;
		boolean sameHold = now - jaymospowers$lastUse <= SAME_HOLD;
		jaymospowers$lastUse = now;
		if (!Carry.isCarrying(this.player)) {
			return;
		}
		if (sameHold) {
			// (0.17.7) still the hold that lifted them: let go of right-click first
			ci.cancel();
			return;
		}
		ClientPlayNetworking.send(new Carry.Release(this.player.isShiftKeyDown()));
		this.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		this.rightClickDelay = RELEASE_DELAY;
		ci.cancel();
	}
}
