package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.FrontierPerks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Scoundrel's wrappings, worn whole: eating, drinking or drawing a bow doesn't slow your walk ({@link FrontierPerks}).
 * After Frontier Armaments' own mixin (cleannrooster, MIT), which halves the slowing; ours takes it away.
 */
@Mixin(LocalPlayer.class)
abstract class ItemUseSpeedMixin {
	@Inject(method = "itemUseSpeedMultiplier", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$surefooted(CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValue() < 1.0F && FrontierPerks.surefooted((LocalPlayer) (Object) this)) {
			cir.setReturnValue(1.0F);
		}
	}
}
