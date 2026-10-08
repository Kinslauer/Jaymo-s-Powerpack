package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The powers screen (H): every power as a card with what its keys do. In creative, each card has a Choose button;
 * in survival it just shows your power (or that it's still dormant).
 */
public class PowerScreen extends Screen {
	private static final int CARD_W = 180;
	private static final int CARD_H = 104;
	private static final int GAP = 6;
	private static final int COLS = 2;
	private static final int HEADER = 26;
	private static final int FOOTER = 26;
	private static final int COLOR_PANEL = 0xC0101018;
	private static final int COLOR_CARD = 0xA0202030;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;

	private final List<Power> powers = new ArrayList<>(Powers.all());

	public PowerScreen() {
		super(Component.translatable("screen.jaymospowers.title"));
	}

	private boolean creative() {
		return this.minecraft.player != null && this.minecraft.player.isCreative();
	}

	private Power current() {
		return this.minecraft.player == null ? null : Powers.of(this.minecraft.player);
	}

	private int rows() {
		return (powers.size() + COLS - 1) / COLS;
	}

	private int panelW() {
		return COLS * CARD_W + (COLS + 1) * GAP;
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
		return left() + GAP + (i % COLS) * (CARD_W + GAP);
	}

	private int cardY(int i) {
		return top() + HEADER + (i / COLS) * (CARD_H + GAP);
	}

	@Override
	protected void init() {
		Power current = current();
		if (creative()) {
			for (int i = 0; i < powers.size(); i++) {
				Power power = powers.get(i);
				Button choose = Button.builder(Component.translatable(power == current ? "screen.jaymospowers.current" : "screen.jaymospowers.choose"),
						b -> choose(power.id().toString()))
					.bounds(cardX(i) + CARD_W - 64, cardY(i) + CARD_H - 20, 60, 16)
					.build();
				choose.active = power != current;
				addRenderableWidget(choose);
			}
			if (current != null) {
				addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.clear"), b -> choose(""))
					.bounds(this.width / 2 - 60, top() + panelH() - 21, 120, 16)
					.build());
			}
		}
	}

	private void choose(String id) {
		ClientPlayNetworking.send(new PowerPayloads.ChoosePower(id));
		onClose();
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
		Power current = current();
		Component sub = creative() ? Component.translatable("screen.jaymospowers.creative_hint")
			: current == null ? Component.translatable("screen.jaymospowers.dormant")
			: Component.translatable("screen.jaymospowers.yours").append(": ").append(current.name());
		graphics.centeredText(this.font, sub, this.width / 2, y + 16, COLOR_DIM);

		for (int i = 0; i < powers.size(); i++) {
			drawCard(graphics, powers.get(i), cardX(i), cardY(i), powers.get(i) == current);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void drawCard(GuiGraphicsExtractor graphics, Power power, int x, int y, boolean active) {
		int accent = 0xFF000000 | power.color();
		graphics.fill(x, y, x + CARD_W, y + CARD_H, COLOR_CARD);
		if (active) {
			graphics.fill(x, y, x + CARD_W, y + 1, accent);
			graphics.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, accent);
			graphics.fill(x, y, x + 1, y + CARD_H, accent);
			graphics.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, accent);
		}
		graphics.fill(x, y, x + 2, y + CARD_H, accent);
		graphics.item(power.icon(), x + 5, y + 4);
		graphics.text(this.font, power.name(), x + 25, y + 8, accent);

		int lineY = y + 24;
		List<FormattedCharSequence> desc = this.font.split(power.description(), CARD_W - 10);
		for (int l = 0; l < Math.min(3, desc.size()); l++) {
			graphics.text(this.font, desc.get(l), x + 6, lineY, COLOR_DIM);
			lineY += 9;
		}
		lineY += 3;
		String base = "power." + power.id().getNamespace() + "." + power.id().getPath() + ".key.";
		for (PowerKey key : PowerKey.values()) {
			if (!Language.getInstance().has(base + key.id())) {
				continue;
			}
			Component line = Component.literal("[").append(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage()).append("] ")
				.append(Component.translatable(base + key.id()));
			List<FormattedCharSequence> split = this.font.split(line, CARD_W - 10);
			if (!split.isEmpty()) {
				graphics.text(this.font, split.getFirst(), x + 6, lineY, COLOR_TEXT);
			}
			lineY += 9;
		}
	}
}
