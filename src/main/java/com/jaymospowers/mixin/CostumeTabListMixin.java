package com.jaymospowers.mixin;

import com.jaymospowers.costume.SecretIdentity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.11.75) The tab list shows a masked player by their costume's name ({@link SecretIdentity#update} resends it on a change). */
@Mixin(ServerPlayer.class)
abstract class CostumeTabListMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$costumeTabName(CallbackInfoReturnable<Component> cir) {
		Component masked = SecretIdentity.maskedName((ServerPlayer)(Object)this);
		if (masked != null) {
			cir.setReturnValue(masked);
		}
	}
}
