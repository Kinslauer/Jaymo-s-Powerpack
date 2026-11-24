package com.jaymospowers.client.mixin;

import com.jaymospowers.boxing.OffhandPunch;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A gauntlet in the off hand punches with a right click at someone you're fighting ({@link OffhandPunch}): the off arm
 * swings, the server lands the blow. Anyone else (a villager, a horse) gets the right click as always.
 */
@Mixin(Minecraft.class)
abstract class OffhandPunchUseMixin {
	/** Ticks before a held right click punches again (the server's own cooldown weakens a hasty one). */
	private static final int PUNCH_DELAY = 4;

	@Shadow
	public @Nullable LocalPlayer player;

	@Shadow
	private int rightClickDelay;

	@Shadow
	public @Nullable HitResult hitResult;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$offhandPunch(CallbackInfo ci) {
		if (this.player == null || !(this.hitResult instanceof EntityHitResult aimed) || !OffhandPunch.canPunch(this.player, aimed.getEntity())) {
			return;
		}
		ClientPlayNetworking.send(new OffhandPunch.Punch(aimed.getEntity().getId()));
		this.player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, false);
		this.rightClickDelay = PUNCH_DELAY;
		ci.cancel();
	}
}
