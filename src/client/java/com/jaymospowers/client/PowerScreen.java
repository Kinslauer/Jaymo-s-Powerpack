package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import java.util.ArrayList;
import com.jaymospowers.api.PowerKey;
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
	/** The shortest a card gets; each grows to fit its key lines (all as tall as the tallest). */
	private static final int CARD_MIN_H = 104;
	/** Pixels per mouse-wheel notch. */
	private static final int SCROLL_STEP = 24;
	private static final int GAP = 6;
	private static final int HEADER = 26;
	private static final int FOOTER = 26;
	private static final int COLOR_PANEL = 0xC0101018;
	private static final int COLOR_CARD = 0xA0202030;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;

	private final List<Power> powers = new ArrayList<>(Powers.all());

	public PowerScreen() {
		super(Component.translatable("screen.jaymospowers.title"));
		// outside creative, the screen is about your own power only
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		Power own = player == null ? null : Powers.of(player);
		if (player != null && !player.isCreative() && own != null) {
			this.powers.clear();
			this.powers.add(own);
		}
	}

	/** The player, for their own power's card (its lines follow their state); null for the others. */
	private net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player owner(Power power) {
		return this.minecraft.player != null && power == current() ? this.minecraft.player : null;
	}

	/** In survival, tall enough for the description, every key line (wrapped) and the mastery line. */
	private int cardH = CARD_MIN_H;

	private int cardH() {
		return cardH;
	}

	/** Re-measures the cards (fate, mastery and key bindings can change while the screen is open). */
	private void measure() {
		int tallest = CARD_MIN_H;
		if (creative()) {
			// the creative picker keeps its fixed cards
			cardH = tallest;
			return;
		}
		for (Power power : powers) {
			int h = 24 + Math.min(3, this.font.split(power.description(), CARD_W - 10).size()) * 9 + 3;
			for (KeyGuide.Line line : KeyGuide.lines(power, owner(power))) {
				h += this.font.split(line.full(), CARD_W - 10).size() * 9;
			}
			h += 15;
			tallest = Math.max(tallest, h);
		}
		cardH = tallest;
	}

	private boolean creative() {
		return this.minecraft.player != null && this.minecraft.player.isCreative();
	}

	private Power current() {
		return this.minecraft.player == null ? null : Powers.of(this.minecraft.player);
	}

	/** Three columns when the screen is wide enough (five powers), else two. */
	private int cols() {
		if (powers.size() == 1) {
			return 1;
		}
		return powers.size() > 4 && this.width >= 3 * CARD_W + 4 * GAP + 8 ? 3 : 2;
	}

	private int rows() {
		return (powers.size() + cols() - 1) / cols();
	}

	private int panelW() {
		return cols() * CARD_W + (cols() + 1) * GAP;
	}

	private int panelH() {
		return HEADER + rows() * (cardH() + GAP) + FOOTER;
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	/** How far the panel is scrolled up (when it's taller than the screen). */
	private int scroll;

	private int maxScroll() {
		return Math.max(0, panelH() + 8 - this.height);
	}

	private int top() {
		return maxScroll() > 0 ? 4 - scroll : Math.max(4, (this.height - panelH()) / 2);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int next = net.minecraft.util.Mth.clamp(scroll - (int)Math.round(scrollY * SCROLL_STEP), 0, maxScroll());
		if (next != scroll) {
			scroll = next;
			rebuildWidgets();
		}
		return true;
	}

	private int cardX(int i) {
		return left() + GAP + (i % cols()) * (CARD_W + GAP);
	}

	private int cardY(int i) {
		return top() + HEADER + (i / cols()) * (cardH() + GAP);
	}

	@Override
	protected void init() {
		measure();
		scroll = net.minecraft.util.Mth.clamp(scroll, 0, maxScroll());
		Power current = current();
		if (creative()) {
			for (int i = 0; i < powers.size(); i++) {
				Power power = powers.get(i);
				Button choose = Button.builder(Component.translatable(power == current ? "screen.jaymospowers.current" : "screen.jaymospowers.choose"),
						b -> choose(power.id().toString()))
					.bounds(cardX(i) + CARD_W - 64, cardY(i) + cardH() - 20, 60, 16)
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
		int was = cardH;
		measure();
		if (cardH != was) {
			rebuildWidgets();
		}
		int x = left();
		int y = top();
		graphics.fill(x, y, x + panelW(), y + panelH(), COLOR_PANEL);
		if (maxScroll() > 0) {
			// a thin scrollbar down the right edge of the screen
			int track = this.height - 8;
			int thumb = Math.max(16, track * this.height / (panelH() + 8));
			int at = 4 + (track - thumb) * scroll / maxScroll();
			graphics.fill(this.width - 5, 4, this.width - 3, 4 + track, 0x40FFFFFF);
			graphics.fill(this.width - 5, at, this.width - 3, at + thumb, 0xC0FFFFFF);
		}
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
		graphics.fill(x, y, x + CARD_W, y + cardH(), COLOR_CARD);
		if (active) {
			graphics.fill(x, y, x + CARD_W, y + 1, accent);
			graphics.fill(x, y + cardH() - 1, x + CARD_W, y + cardH(), accent);
			graphics.fill(x, y, x + 1, y + cardH(), accent);
			graphics.fill(x + CARD_W - 1, y, x + CARD_W, y + cardH(), accent);
		}
		graphics.fill(x, y, x + 2, y + cardH(), accent);
		graphics.item(power.icon(), x + 5, y + 4);
		Component name = active && this.minecraft.player != null ? PowerHud.title(power, Fate.state(this.minecraft.player), this.minecraft.player) : power.name();
		graphics.text(this.font, name, x + 25, y + 8, accent);

		int lineY = y + 24;
		List<FormattedCharSequence> desc = this.font.split(power.description(), CARD_W - 10);
		for (int l = 0; l < Math.min(3, desc.size()); l++) {
			graphics.text(this.font, desc.get(l), x + 6, lineY, COLOR_DIM);
			lineY += 9;
		}
		lineY += 3;
		if (creative()) {
			lineY = drawPickerKeys(graphics, power, x, lineY, active);
		} else {
			// survival: what each of your keys does right now, following your fate and mastery
			for (KeyGuide.Line line : KeyGuide.lines(power, owner(power))) {
				for (FormattedCharSequence part : this.font.split(line.full(), CARD_W - 10)) {
					graphics.text(this.font, part, x + 6, lineY, line.color());
					lineY += 9;
				}
			}
		}
		float progress = active && this.minecraft.player != null ? Mastery.progress(this.minecraft.player) : -1.0F;
		if (progress >= 0.0F) {
			graphics.text(this.font, Component.translatable("screen.jaymospowers.mastery", Math.round(progress * 100.0F)), x + 6, lineY + 2, COLOR_DIM);
		}
	}

	/** Creative's power picker keeps its short lines: one per ability key, cut to the card's width. */
	private int drawPickerKeys(GuiGraphicsExtractor graphics, Power power, int x, int lineY, boolean active) {
		Object[] args = KeyGuide.keyNames();
		String base = "power." + power.id().getNamespace() + "." + power.id().getPath() + ".key.";
		boolean corrupted = active && this.minecraft.player != null && Fate.isCorrupted(this.minecraft.player);
		for (PowerKey key : PowerKey.values()) {
			if (!Language.getInstance().has(base + key.id())) {
				continue;
			}
			// a corrupted power's keys can change (Stormbound's B, say)
			String text = corrupted && Language.getInstance().has(base + key.id() + ".corrupted") ? base + key.id() + ".corrupted" : base + key.id();
			drawPickerLine(graphics, JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage(), Component.translatable(text, args), x, lineY, COLOR_TEXT);
			lineY += 9;
		}
		// the ascended ability shows up only once it's yours
		if (active && this.minecraft.player != null && Fate.isAscended(this.minecraft.player) && Language.getInstance().has(base + "ultimate")) {
			drawPickerLine(graphics, JaymosPowersClient.ultimateKey().getTranslatedKeyMessage(), Component.translatable(base + "ultimate", args), x, lineY, 0xFFFFD54A);
			lineY += 9;
		}
		return lineY;
	}

	private void drawPickerLine(GuiGraphicsExtractor graphics, Component key, Component text, int x, int y, int color) {
		List<FormattedCharSequence> split = this.font.split(Component.literal("[").append(key).append("] ").append(text), CARD_W - 10);
		if (!split.isEmpty()) {
			graphics.text(this.font, split.getFirst(), x + 6, y, color);
		}
	}
}
