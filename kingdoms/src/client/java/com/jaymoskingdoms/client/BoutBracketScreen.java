package com.jaymoskingdoms.client;

import com.jaymoskingdoms.forms.BoutNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A school bout's bracket ({@code Bouts}; Andy, 2026-11-12: "there needs to be a screen so the fight starts when the user
 * is ready"): you at the root, a branch to each fighter in the order you meet them (the last the bout master), the rounds
 * you've won ticked off and the next one marked; and while the next round waits on you, <b>Ready</b>, <b>Not yet</b> (the
 * screen comes back in a while) or <b>Withdraw</b>.
 */
final class BoutBracketScreen extends Screen {
	static final int PANEL_W = 300, PAD = 10, ROW_H = 22, BUTTON_H = 20, GAP = 4;
	static final int WON = 0xFF66EE66;

	private final BoutNet.Bracket bracket;

	private BoutBracketScreen(BoutNet.Bracket bracket) {
		super(Component.translatable(bracket.tavern() ? "gui.jaymoskingdoms.bout.tavern_title" : "gui.jaymoskingdoms.bout.title", bracket.where()));
		this.bracket = bracket;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BoutNet.Bracket.TYPE, (payload, context) -> context.client().gui.setScreen(new BoutBracketScreen(payload)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Component words() {
		return Component.translatable(this.bracket.asking() ? "gui.jaymoskingdoms.bout.ready_ask" : "gui.jaymoskingdoms.bout.champion");
	}

	private int panelHeight() {
		int buttons = this.bracket.asking() ? 3 : 1;
		return 24 + ROW_H + this.bracket.fighters().size() * ROW_H + 8 + this.font.split(words(), PANEL_W - PAD * 2).size() * 10 + 8
			+ buttons * (BUTTON_H + GAP) + PAD;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelHeight()) / 2);
	}

	@Override
	protected void init() {
		int y = top() + 24 + ROW_H + this.bracket.fighters().size() * ROW_H + 8 + this.font.split(words(), PANEL_W - PAD * 2).size() * 10 + 8;
		int x = left() + PAD, w = PANEL_W - PAD * 2;
		if (this.bracket.asking()) {
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.bout.ready"), b -> answer(true)).bounds(x, y, w, BUTTON_H).build());
			y += BUTTON_H + GAP;
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.bout.later"), b -> super.onClose()).bounds(x, y, w, BUTTON_H).build());
			y += BUTTON_H + GAP;
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.bout.withdraw"), b -> answer(false)).bounds(x, y, w, BUTTON_H).build());
		} else {
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.bout.close"), b -> super.onClose()).bounds(x, y, w, BUTTON_H).build());
		}
	}

	private void answer(boolean ready) {
		ClientPlayNetworking.send(new BoutNet.Ready(ready));
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), h = panelHeight();
		Ui.panel(graphics, x, y, PANEL_W, h, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + PAD, y + 8, PANEL_W - PAD * 2, Ui.GOLD);
		Ui.rule(graphics, x + 6, x + PANEL_W - 6, y + 19);
		// the root: you
		int ry = y + 24;
		int boxX = x + PAD, boxW = 110;
		Ui.well(graphics, boxX, ry, boxW, ROW_H - 4);
		Ui.line(graphics, this.font, this.bracket.you(), boxX + 4, ry + 5, boxW - 8, Ui.TEXT);
		// the trunk down from you, a branch out to each round
		int trunkX = boxX + 12, branchTo = x + PAD + 140;
		int n = this.bracket.fighters().size();
		int lastY = ry + ROW_H + (n - 1) * ROW_H + (ROW_H - 4) / 2;
		graphics.fill(trunkX, ry + ROW_H - 4, trunkX + 1, lastY + 1, Ui.DIM);
		for (int i = 0; i < n; i++) {
			int rowY = ry + ROW_H + i * ROW_H;
			int mid = rowY + (ROW_H - 4) / 2;
			boolean won = i < this.bracket.won();
			boolean next = i == this.bracket.won() && this.bracket.asking();
			int colour = won ? WON : next ? Ui.GOLD : Ui.DIM;
			graphics.fill(trunkX, mid, branchTo, mid + 1, colour);
			Component label = i == n - 1 ? Component.translatable("gui.jaymoskingdoms.bout.final")
				: Component.translatable("gui.jaymoskingdoms.bout.round", i + 1);
			Ui.line(graphics, this.font, label, trunkX + 6, rowY + 2, branchTo - trunkX - 10, colour);
			int fx = branchTo, fw = x + PANEL_W - PAD - fx;
			Ui.well(graphics, fx, rowY, fw, ROW_H - 4);
			Ui.line(graphics, this.font, this.bracket.fighters().get(i), fx + 4, rowY + 5, fw - 50, next || won ? Ui.TEXT : Ui.DIM);
			if (won || next) {
				Ui.lineRight(graphics, this.font, Component.translatable(won ? "gui.jaymoskingdoms.bout.won" : "gui.jaymoskingdoms.bout.next"),
					fx + fw - 4, rowY + 5, 44, colour);
			}
		}
		int ty = ry + ROW_H + n * ROW_H + 8;
		for (FormattedCharSequence line : this.font.split(words(), PANEL_W - PAD * 2)) {
			graphics.text(this.font, line, x + PAD, ty, Ui.TEXT);
			ty += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
