package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.NightGifts;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.61) A werewolf's beast climbs any wall it runs into, like a ladder ({@link NightGifts#wallRun}); both sides. */
@Mixin(Player.class)
abstract class PlayerWallRunMixin {
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$wallRun(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && NightGifts.wallRun((Player) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
