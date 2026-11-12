package com.jaymospowers.client.mixin;

import com.jaymospowers.client.ProneHaulClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lying against a rise you could haul yourself up, or hauling up it ({@code ProneHaul}): Use is the haul's, so neither the
 * press nor vanilla's repeat of a held Use uses the item in hand or works the block (a downed struggle with it included).
 */
@Mixin(Minecraft.class)
abstract class ProneHaulUseMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$proneHaulUse(CallbackInfo ci) {
		if (ProneHaulClient.wantsUse()) {
			ci.cancel();
		}
	}
}
