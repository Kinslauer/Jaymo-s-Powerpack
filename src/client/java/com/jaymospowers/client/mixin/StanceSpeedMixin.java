package com.jaymospowers.client.mixin;

import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Stance;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.18.57) The low crouch walks slower than a sneak ({@link Stance#LOW_SPEED}). (0.19.28) Prone on the back, shuffling on
 * the hips is slower than crawling on the belly ({@link Crawl#SUPINE_SPEED}).
 */
@Mixin(LocalPlayer.class)
abstract class StanceSpeedMixin {
	@Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$lowCrouchSpeed(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (Stance.LOW_SPEED != 1.0F && self.isCrouching() && Stance.low(self)) {
			cir.setReturnValue(cir.getReturnValue().scale(Stance.LOW_SPEED));
		}
		if (Crawl.supine(self) && Crawl.canCrawl(self) && !Downed.isDowned(self)) {
			cir.setReturnValue(cir.getReturnValue().scale(Crawl.SUPINE_SPEED));
		}
	}
}
