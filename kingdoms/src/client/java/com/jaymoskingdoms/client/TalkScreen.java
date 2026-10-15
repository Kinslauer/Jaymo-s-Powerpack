package com.jaymoskingdoms.client;

import com.jaymoskingdoms.TalkNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

/**
 * (0.5.39) Answering someone who came over to ask you something ({@code Approaches}): what they said, and a button per
 * answer (greyed out when you can't: no emeralds for the fine, no food to give). The server does the rest; their reply
 * floats over their head. (0.7.41) Drawn like the H screen.
 */
final class TalkScreen extends Screen {
	static final int PANEL_W = 280, PAD = 10, BUTTON_H = 20, GAP = 4;

	private final TalkNet.Ask ask;
	private boolean answered;

	private TalkScreen(TalkNet.Ask ask) {
		super(ask.speaker());
		this.ask = ask;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TalkNet.Ask.TYPE, (payload, context) -> context.client().gui.setScreen(new TalkScreen(payload)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int textWidth() {
		return PANEL_W - PAD * 2;
	}

	private int panelHeight() {
		return 24 + this.font.split(this.ask.words(), textWidth()).size() * 10 + 8 + this.ask.options().size() * (BUTTON_H + GAP) + PAD;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelHeight()) / 2);
	}

	@Override
	protected void init() {
		int y = top() + 24 + this.font.split(this.ask.words(), textWidth()).size() * 10 + 8;
		for (int i = 0; i < this.ask.options().size(); i++) {
			int option = i;
			Button b = Button.builder(this.ask.options().get(i), button -> pick(option)).bounds(left() + PAD, y, PANEL_W - PAD * 2, BUTTON_H).build();
			b.active = this.ask.enabled().get(i);
			addRenderableWidget(b);
			y += BUTTON_H + GAP;
		}
	}

	private void pick(int option) {
		this.answered = true;
		ClientPlayNetworking.send(new TalkNet.Answer(this.ask.entityId(), option));
		super.onClose();
	}

	@Override
	public void onClose() {
		if (!this.answered) {
			ClientPlayNetworking.send(new TalkNet.Answer(this.ask.entityId(), -1));
		}
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), h = panelHeight();
		Ui.panel(graphics, x, y, PANEL_W, h, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + PAD, y + 8, PANEL_W - PAD * 2, Ui.GOLD);
		Ui.rule(graphics, x + 6, x + PANEL_W - 6, y + 19);
		int ty = y + 24;
		for (FormattedCharSequence line : this.font.split(this.ask.words(), textWidth())) {
			graphics.text(this.font, line, x + PAD, ty, QuestScreen.COLOR_TEXT);
			ty += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
