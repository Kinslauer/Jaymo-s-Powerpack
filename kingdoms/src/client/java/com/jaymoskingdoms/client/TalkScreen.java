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
 * floats over their head.
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
		graphics.fill(x, y, x + PANEL_W, y + h, QuestScreen.COLOR_PANEL);
		graphics.fill(x, y, x + PANEL_W, y + 1, QuestScreen.COLOR_EDGE);
		graphics.fill(x, y + h - 1, x + PANEL_W, y + h, QuestScreen.COLOR_EDGE);
		graphics.fill(x, y, x + 1, y + h, QuestScreen.COLOR_EDGE);
		graphics.fill(x + PANEL_W - 1, y, x + PANEL_W, y + h, QuestScreen.COLOR_EDGE);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 8, QuestScreen.COLOR_GOLD);
		int ty = y + 24;
		for (FormattedCharSequence line : this.font.split(this.ask.words(), textWidth())) {
			graphics.text(this.font, line, x + PAD, ty, QuestScreen.COLOR_TEXT);
			ty += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
