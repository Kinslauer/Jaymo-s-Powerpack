package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.SpeechBubbles;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.s2c.VillagerMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MCA villagers' talk (to each other, to you, their screams and gossip) floats over the speaker's head instead of going
 * to chat. It still goes to chat while you're in a villager's talk screen (the conversation you're having), and when the
 * speaker isn't in sight on this client.
 */
@Mixin(ClientHandlerImpl.class)
abstract class McaVillagerMessageMixin {
	@Redirect(method = "handleVillagerMessage", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
	private void jaymoskingdoms$overHead(ChatListener chat, Component full, boolean overlay, VillagerMessage message) {
		boolean shown = SpeechBubbles.say(message.uuid(), message.message());
		if (!shown || Minecraft.getInstance().gui.screen() instanceof InteractScreen) {
			chat.handleSystemMessage(full, overlay);
		}
	}
}
