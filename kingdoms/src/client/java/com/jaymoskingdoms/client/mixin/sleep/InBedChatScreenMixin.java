package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.RestClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.5) A Sleep button above Leave Bed, shown while you rest at night, and dragging to look around ({@link RestClient}). */
@Mixin(InBedChatScreen.class)
abstract class InBedChatScreenMixin extends ChatScreen {
	private InBedChatScreenMixin() {
		super("", false, false);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void jaymoskingdoms$sleepButton(CallbackInfo ci) {
		Button sleep = Button.builder(Component.translatable("gui.jaymoskingdoms.bed_rest_sleep"), button -> RestClient.pressSleep())
			.bounds(this.width / 2 - 100, this.height - 64, 200, 20)
			.build();
		this.addRenderableWidget(sleep);
		RestClient.sleepButton(sleep);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (super.mouseDragged(event, dx, dy)) {
			return true;
		}
		RestClient.look(dx, dy);
		return true;
	}
}
