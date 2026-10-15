package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Tailors;
import java.util.UUID;
import net.conczin.mca.client.gui.NeedleScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * (0.7.53) A tailor's fitting ({@link Tailors}): MCA's character editor held to its clothing page, for your own clothes.
 * It's MCA's Needle and Thread screen without the needle: no other page (body, hair, destiny) can be reached, picking an
 * outfit pays the tailor's fee, saves it and closes, and Escape changes nothing and costs nothing.
 */
final class WardrobeScreen extends NeedleScreen {
	private final int fee;

	WardrobeScreen(UUID self, int fee) {
		super(self);
		this.fee = fee;
	}

	@Override
	protected void eventCallback(String event) {
		if (event.equals("clothing")) {
			ClientPlayNetworking.send(new Tailors.Picked());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, Component.translatable("tailor.jaymoskingdoms.screen_fee", this.fee), this.width / 2, this.height / 2 - 114, Ui.GOLD);
	}
}
