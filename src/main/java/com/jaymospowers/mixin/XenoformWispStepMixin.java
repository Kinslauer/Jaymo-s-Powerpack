package com.jaymospowers.mixin;

import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.18.90) Wisp floats, so it makes no footsteps. */
@Mixin(Player.class)
abstract class XenoformWispStepMixin {
	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$wispSteps(BlockPos pos, BlockState state, CallbackInfo ci) {
		if ("wisp".equals(((Player)(Object)this).getAttached(XenoformState.FORM))) {
			ci.cancel();
		}
	}
}
