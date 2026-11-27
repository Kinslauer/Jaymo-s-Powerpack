package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.NightHorrors;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The true shape tears what is softer than stone by hand ({@link NightHorrors#tears}): faster than a hand could, and the block
 * gives its drops as if the right tool broke it. Runs on both sides, so the client's own breaking agrees with the server's.
 */
@Mixin(Player.class)
abstract class PlayerHorrorMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$tearSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
		if (NightHorrors.tears((Player) (Object) this, state)) {
			cir.setReturnValue(cir.getReturnValueF() * NightHorrors.tearSpeed());
		}
	}

	@Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$tearDrops(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && NightHorrors.tears((Player) (Object) this, state)) {
			cir.setReturnValue(true);
		}
	}
}
