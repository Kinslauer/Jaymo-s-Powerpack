package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A phasing Wisp with its eyes inside a block isn't shown that block's face over the screen (as a spectator isn't). */
@Mixin(LevelExtractor.class)
abstract class WispPhaseViewMixin {
	@Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$wispPhaseView(LocalPlayer player, Frustum frustum, CallbackInfoReturnable<BlockState> cir) {
		if (XenoformState.phasing(player)) {
			cir.setReturnValue(null);
		}
	}
}
