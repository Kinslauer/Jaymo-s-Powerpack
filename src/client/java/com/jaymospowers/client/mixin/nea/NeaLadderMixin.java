package com.jaymospowers.client.mixin.nea;

import com.jaymospowers.client.WallClimbs;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.13.17) Not Enough Animations (tr7zw; optional, nothing of it copied) plays its ladder climb on our wall climbs too
 * ({@link WallClimbs}): webbed fists and Wall Crawl look the way climbing a vine does. Skipped when NEA isn't there.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.notenoughanimations.animations.fullbody.LadderAnimation", remap = false)
abstract class NeaLadderMixin {
	@Inject(method = "isValid", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$wallClimb(AbstractClientPlayer player, @Coerce Object data, CallbackInfoReturnable<Boolean> cir) {
		if (WallClimbs.climbing(player)) {
			cir.setReturnValue(true);
		}
	}
}
