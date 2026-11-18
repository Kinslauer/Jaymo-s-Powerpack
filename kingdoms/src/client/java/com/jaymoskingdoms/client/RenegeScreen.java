package com.jaymoskingdoms.client;

import com.jaymoskingdoms.PactArts;
import com.jaymoskingdoms.Warlocks;
import com.jaymoskingdoms.client.ui.FlatButton;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A Warlock's Renege ({@link PactArts}): sneak and the class key while owing opens this, to pick which other kind the
 * patron takes instead of what is owed. Once a debt; closing it without a pick spends nothing.
 */
final class RenegeScreen extends Screen {
	static final int W = 220, PAD = 10, BUTTON_H = 20, GAP = 4;
	private final String owed;
	private final List<String> kinds;

	private RenegeScreen(String owed, List<String> kinds) {
		super(Component.translatable("renege.jaymoskingdoms.title"));
		this.owed = owed;
		this.kinds = kinds;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PactArts.Offer.TYPE, (payload, context) -> {
			if (context.client().gui.screen() == null) {
				context.client().gui.setScreen(new RenegeScreen(payload.owed(), payload.kinds()));
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int top() {
		return Math.max(4, (this.height - (34 + this.kinds.size() * (BUTTON_H + GAP) + PAD)) / 2);
	}

	@Override
	protected void init() {
		int x = (this.width - W) / 2, y = top() + 34;
		for (String kind : this.kinds) {
			addRenderableWidget(FlatButton.builder(Warlocks.debtName(kind), b -> {
				ClientPlayNetworking.send(new PactArts.Pick(kind));
				onClose();
			}).bounds(x + PAD, y, W - PAD * 2, BUTTON_H).build());
			y += BUTTON_H + GAP;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = (this.width - W) / 2, y = top();
		Ui.panel(g, x, y, W, 34 + this.kinds.size() * (BUTTON_H + GAP) + PAD, Ui.GOLD);
		Ui.line(g, this.font, this.title, x + PAD, y + 8, W - PAD * 2, Ui.GOLD);
		Ui.rule(g, x + 6, x + W - 6, y + 19);
		Ui.line(g, this.font, Component.translatable("renege.jaymoskingdoms.owed", Warlocks.debtName(this.owed)), x + PAD, y + 22, W - PAD * 2, Ui.DIM);
		super.extractRenderState(g, mouseX, mouseY, a);
	}
}
