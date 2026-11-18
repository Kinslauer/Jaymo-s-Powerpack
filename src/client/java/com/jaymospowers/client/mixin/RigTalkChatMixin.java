package com.jaymospowers.client.mixin;

import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Mind catches what the wearer types at it: instead of being broadcast, the words go to the Mind as a
 * {@link RigNet.Chat}, and the Mind answers privately.
 */
@Mixin(ChatScreen.class)
public abstract class RigTalkChatMixin {
	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$rigTalk(String message, boolean addToChat, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && Rig.wears(player, Rig.Piece.CORE) && !message.isEmpty()) {
			ClientPlayNetworking.send(new RigNet.Chat(message));
			ci.cancel();
		}
	}
}
