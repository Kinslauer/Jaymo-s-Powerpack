package com.jaymospowers.mixin;

import com.jaymospowers.costume.SecretIdentity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.11.75) A player whose costume hides their face goes by the costume's name ({@link SecretIdentity}) wherever their
 * display name shows: chat, death and join messages, the name over their head, other mods' lines. No team colour and
 * no hover card (that would give the real name and UUID away). {@code getName()} is left alone (commands, scoreboards).
 */
@Mixin(Player.class)
abstract class CostumeNameMixin {
	@Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$costumeName(CallbackInfoReturnable<Component> cir) {
		Component masked = SecretIdentity.maskedName((Player)(Object)this);
		if (masked != null) {
			cir.setReturnValue(masked);
		}
	}
}
