package com.jaymoskingdoms.mixin.classtrees;

import com.jaymoskingdoms.FaithExtras;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Monk's Iron Palm ({@link FaithExtras#palm}): the bare hand breaks wood, then stone, at most of an iron tool's speed
 * (before haste, fatigue, water and footing, as a tool's own speed is), and what it breaks drops as if a tool had. Runs on
 * both sides, so the client's own breaking keeps pace.
 */
@Mixin(Player.class)
abstract class IronPalmMixin {
	@ModifyVariable(method = "getDestroySpeed", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
	private float jaymoskingdoms$palmSpeed(float speed, BlockState state) {
		return FaithExtras.palmSpeed((Player) (Object) this, state, speed);
	}

	@Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$palmDrops(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && FaithExtras.palm((Player) (Object) this, state)) {
			cir.setReturnValue(true);
		}
	}
}
