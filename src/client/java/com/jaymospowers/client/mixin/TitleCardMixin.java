package com.jaymospowers.client.mixin;

import com.jaymospowers.client.hud.TitleCard;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's title, subtitle and timings go to the title card ({@link TitleCard}) instead of its own big text. */
@Mixin(Hud.class)
abstract class TitleCardMixin {
	@Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$title(Component title, CallbackInfo ci) {
		TitleCard.setTitle(title);
		ci.cancel();
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$subtitle(Component subtitle, CallbackInfo ci) {
		TitleCard.setSubtitle(subtitle);
		ci.cancel();
	}

	@Inject(method = "setTimes", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$times(int in, int stay, int out, CallbackInfo ci) {
		TitleCard.setTimes(in, stay, out);
		ci.cancel();
	}

	@Inject(method = "clearTitles", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$clear(CallbackInfo ci) {
		TitleCard.clear();
		ci.cancel();
	}
}
