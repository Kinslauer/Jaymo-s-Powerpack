package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.NightGifts;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.63) A vampire's bat wings glide like an elytra, with no elytra on ({@link NightGifts#batWings}); both sides. */
@Mixin(Player.class)
abstract class PlayerBatWingsMixin {
	@Inject(method = "canGlide", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$batWings(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && NightGifts.batWings((Player) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
