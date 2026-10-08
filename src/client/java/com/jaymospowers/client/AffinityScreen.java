package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Affinity;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The affinity choice, shown the first time a player is in survival without a power: which power will awaken in them
 * (it still takes its 1-3 days), or "let fate decide". Made once; Esc doesn't dodge it (H brings it back otherwise).
 */
public class AffinityScreen extends Screen {
	private static final int CARD_W = 170;
	private static final int CARD_H = 62;
	private static final int GAP = 6;
	private static final int HEADER = 34;
	private static final int FOOTER = 28;
	private static final int COLOR_PANEL = 0xD0101018;
	private static final int COLOR_CARD = 0xA0202030;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;

	private final List<Power> powers = new ArrayList<>(Powers.all());

	public AffinityScreen() {
		super(Component.translatable("screen.jaymospowers.affinity.title"));
	}

	private int cols() {
		return this.width >= 3 * CARD_W + 4 * GAP + 8 ? 3 : 2;
	}

	private int rows() {
		return (powers.size() + cols() - 1) / cols();
	}

	private int panelW() {
		return cols() * CARD_W + (cols() + 1) * GAP;
	}

	private int panelH() {
		return HEADER + rows() * (CARD_H + GAP) + FOOTER;
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int cardX(int i) {
		return left() + GAP + (i % cols()) * (CARD_W + GAP);
	}

	private int cardY(int i) {
		return top() + HEADER + (i / cols()) * (CARD_H + GAP);
	}

	@Override
	protected void init() {
		for (int i = 0; i < powers.size(); i++) {
			Power power = powers.get(i);
			addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.affinity.choose"), b -> choose(power.id().toString()))
				.bounds(cardX(i) + CARD_W - 54, cardY(i) + CARD_H - 18, 50, 14)
				.build());
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.affinity.fate"), b -> choose(Affinity.FATE.toString()))
			.bounds(this.width / 2 - 80, top() + panelH() - 22, 160, 16)
			.build());
	}

	private void choose(String id) {
		ClientPlayNetworking.send(new PowerPayloads.ChooseAffinity(id));
		onClose();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		graphics.fill(x, y, x + panelW(), y + panelH(), COLOR_PANEL);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 6, COLOR_TEXT);
		graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.affinity.hint"), this.width / 2, y + 18, COLOR_DIM);
		for (int i = 0; i < powers.size(); i++) {
			Power power = powers.get(i);
			int cx = cardX(i);
			int cy = cardY(i);
			int accent = 0xFF000000 | power.color();
			graphics.fill(cx, cy, cx + CARD_W, cy + CARD_H, COLOR_CARD);
			graphics.fill(cx, cy, cx + 2, cy + CARD_H, accent);
			graphics.item(power.icon(), cx + 5, cy + 4);
			graphics.text(this.font, power.name(), cx + 25, cy + 8, accent);
			List<FormattedCharSequence> desc = this.font.split(power.description(), CARD_W - 10);
			for (int l = 0; l < Math.min(2, desc.size()); l++) {
				graphics.text(this.font, desc.get(l), cx + 6, cy + 24 + l * 9, COLOR_DIM);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
