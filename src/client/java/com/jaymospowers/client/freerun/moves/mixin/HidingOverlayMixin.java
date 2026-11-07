package com.jaymospowers.client.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.Hiding;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hiding in a hay bale ({@link Hiding}): no block texture over the screen, so you can peek out of it. */
@Mixin(LevelExtractor.class)
abstract class HidingOverlayMixin {
	@Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$peekOut(LocalPlayer player, Frustum frustum, CallbackInfoReturnable<BlockState> cir) {
		if (Hiding.hidden(player)) {
			cir.setReturnValue(null);
		}
	}
}
