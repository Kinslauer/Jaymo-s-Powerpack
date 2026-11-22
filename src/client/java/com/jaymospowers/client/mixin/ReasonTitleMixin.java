package com.jaymospowers.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game window is titled with the pack's name, "Reason", in place of "Minecraft* 26.3". What vanilla adds after
 * the name (" - Singleplayer", " - Multiplayer (3rd-party Server)") is kept.
 */
@Mixin(Minecraft.class)
abstract class ReasonTitleMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$reasonTitle(CallbackInfoReturnable<String> cir) {
		String vanilla = cir.getReturnValue();
		int rest = vanilla.indexOf(" - ");
		cir.setReturnValue("Reason" + (rest >= 0 ? vanilla.substring(rest) : ""));
	}
}
