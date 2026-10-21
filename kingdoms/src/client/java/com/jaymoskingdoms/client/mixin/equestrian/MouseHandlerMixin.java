package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.EquestrianClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.jaymoskingdoms.client.equestrian.FreeReinClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.ScrollWheelHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.5.34) In the saddle the scroll wheel shifts the horse's gait instead of the hotbar slot. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;getNextScrollWheelSelection(DII)I"))
	private int jaymoskingdoms$gait(double wheel, int current, int size, Operation<Integer> original) {
		return EquestrianClient.scroll((int)Math.signum(wheel)) ? current : original.call(wheel, current, size);
	}

	/** (0.7.110) Under free rein the mouse turns the camera, not you. */
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void jaymoskingdoms$freeRein(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		if (!FreeReinClient.turn(dx, dy)) {
			original.call(player, dx, dy);
		}
	}
}
