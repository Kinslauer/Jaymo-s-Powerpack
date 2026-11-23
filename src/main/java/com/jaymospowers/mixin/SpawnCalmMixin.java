package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.SpawnCalm;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One just put into the world is no enemy to any mob until they move ({@link SpawnCalm}): every mob's target is checked by this. */
@Mixin(Player.class)
abstract class SpawnCalmMixin {
	@Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$spawnCalm(CallbackInfoReturnable<Boolean> cir) {
		if (SpawnCalm.isCalm((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
