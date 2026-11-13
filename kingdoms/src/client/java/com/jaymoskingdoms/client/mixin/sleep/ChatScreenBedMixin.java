package com.jaymoskingdoms.client.mixin.sleep;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * In bed the game opens its chat screen, so every line of chat stood on screen the whole night (the night's film
 * included) and never faded. Now, until you type something into the bed's chat box, the chat is drawn as it is with
 * the chat closed: new lines show and fade after a while, the old ones stay hidden. Typing brings the whole chat back.
 */
@Mixin(ChatScreen.class)
abstract class ChatScreenBedMixin {
	@Shadow
	protected EditBox input;

	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"),
		index = 5)
	private ChatComponent.DisplayMode jaymoskingdoms$fadeInBed(ChatComponent.DisplayMode mode) {
		return this.quietBed() ? ChatComponent.DisplayMode.BACKGROUND : mode;
	}

	/** (and the hidden lines can't be clicked) */
	@ModifyArg(method = "mouseClicked", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ChatComponent;captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"),
		index = 3)
	private ChatComponent.DisplayMode jaymoskingdoms$quietClicks(ChatComponent.DisplayMode mode) {
		return this.quietBed() ? ChatComponent.DisplayMode.BACKGROUND : mode;
	}

	/** The bed's chat screen with nothing typed in it. */
	private boolean quietBed() {
		return (Object) this instanceof InBedChatScreen && (this.input == null || this.input.getValue().isEmpty());
	}
}
