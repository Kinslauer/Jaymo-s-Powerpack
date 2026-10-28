package com.jaymospowers.power.zfighter.mixin;

import com.jaymospowers.power.zfighter.ZFighterNpcs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.17.139) While a Z Fighter character's talk is being answered, what would go to the player's chat goes on the talk screen ({@link ZFighterNpcs}). */
@Mixin(ServerPlayer.class)
abstract class ZFighterTranscriptMixin {
	@Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$transcript(Component message, boolean overlay, CallbackInfo ci) {
		if (!overlay && ZFighterNpcs.noted((ServerPlayer)(Object)this, message)) {
			ci.cancel();
		}
	}
}
