package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Trees;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.59) No punching trees ({@link Trees#needsAxe}): wood wants an axe for drops, so bare hands (or a pickaxe) are as
 * slow at a log as at stone without a pickaxe, and the chop is refused.
 */
@Mixin(Player.class)
abstract class PlayerLogToolMixin {
	@Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$logsNeedAxe(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Trees.needsAxe((Player) (Object) this, state)) {
			cir.setReturnValue(false);
		}
	}
}
