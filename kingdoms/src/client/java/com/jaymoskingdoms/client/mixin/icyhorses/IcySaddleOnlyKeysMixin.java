package com.jaymoskingdoms.client.mixin.icyhorses;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icy's Better Horses' riding keys do nothing out of the saddle, so they can share a key with something used on foot
 * (its defaults sit on the power keys: Rear on H, the character menu; Toggle Horse Charge on B; Open Cart on Left Alt,
 * the roll). Rear wants you steering a horse (on foot it reared the horse under the crosshair); the charge toggle wants
 * you steering a horse (on foot it flipped the setting and printed "charge on/off"); Open Cart wants you riding
 * something (on foot it opened the cart under the crosshair; sneak and right-click the cart still opens it).
 * Vanilla hands every press to every mapping on the key, so the shared key's other action fires as before.
 * Icy's own gear keys and free look already wait for the saddle. Its whistle, command wheel and Manage Horses keys are
 * for a horse you are not on, so they still work on foot. Skipped without Icy's.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.IcysBetterHorsesClient", remap = false)
abstract class IcySaddleOnlyKeysMixin {
	@Inject(method = "bh_tryRear(Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$rearInSaddle(Minecraft minecraft, CallbackInfo ci) {
		if (!steering(minecraft)) {
			ci.cancel();
		}
	}

	@Inject(method = "bh_toggleCharge(Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$chargeInSaddle(Minecraft minecraft, CallbackInfo ci) {
		if (!steering(minecraft)) {
			ci.cancel();
		}
	}

	@Inject(method = "bh_tryOpenCart(Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$cartWhenRiding(Minecraft minecraft, CallbackInfo ci) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.getVehicle() == null) {
			ci.cancel();
		}
	}

	/** Whether the player steers a horse (any horse-like mount). */
	private static boolean steering(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && player.getControlledVehicle() instanceof AbstractHorse;
	}
}
