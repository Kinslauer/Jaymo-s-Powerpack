package com.jaymoskingdoms.client;

import com.jaymoskingdoms.QuestNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.29) A giver's quests: each one's story, what's left to do, what it pays (hover an item for its tooltip: a relic's
 * passive, a book's enchantment), and the buttons to take it, turn it in or give it up. The server decides everything;
 * a choice is sent back and the screen comes again with the result.
 */
final class QuestScreen extends Screen {
	static final int PANEL_W = 300, PAD = 8, ICON = 18;
	/** (0.7.33) The H screen's colours (Ui), shared with the talk screen. */
	static final int COLOR_PANEL = Ui.PANEL, COLOR_CARD = Ui.ROW_ALT, COLOR_EDGE = 0xFF4A4A56, COLOR_TEXT = Ui.TEXT, COLOR_DIM = Ui.DIM,
		COLOR_ROYAL = 0xFFFF6A50, COLOR_GOLD = 0xFFFFC21A, COLOR_DONE = 0xFF66EE66;

	private final QuestNet.Open open;

	QuestScreen(QuestNet.Open open) {
		super(open.giver());
		this.open = open;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int textWidth() {
		return PANEL_W - PAD * 2 - 6;
	}

	/** A quest card's height: title, story, objective, rewards, extras, buttons. */
	private int cardHeight(QuestNet.View v) {
		int h = 4 + 12;
		h += this.font.split(v.text(), textWidth()).size() * 10 + 4;
		h += this.font.split(v.objective(), textWidth()).size() * 10 + 4;
		h += 10 + ICON + 2;
		h += v.extras().size() * 10;
		h += 24;
		return h;
	}

	private int panelHeight() {
		int h = 26;
		if (open.views().isEmpty()) {
			return h + this.font.split(open.note(), textWidth()).size() * 10 + 30;
		}
		for (QuestNet.View v : open.views()) {
			h += cardHeight(v) + 6;
		}
		return h + 22;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelHeight()) / 2);
	}

	@Override
	protected void init() {
		int x = left(), y = top() + 26;
		for (QuestNet.View v : open.views()) {
			int bottom = y + cardHeight(v) - 22;
			if (v.state() == QuestNet.OFFER) {
				addRenderableWidget(Button.builder(Component.translatable(v.royal() ? "quest.jaymoskingdoms.button.accept.king" : "quest.jaymoskingdoms.button.accept"),
					b -> act(v, QuestNet.ACCEPT)).bounds(x + PAD, bottom, 110, 18).build());
			} else {
				Button turnIn = Button.builder(Component.translatable("quest.jaymoskingdoms.button.turn_in"), b -> act(v, QuestNet.TURN_IN))
					.bounds(x + PAD, bottom, 110, 18).build();
				turnIn.active = v.state() == QuestNet.READY;
				addRenderableWidget(turnIn);
				addRenderableWidget(Button.builder(Component.translatable("quest.jaymoskingdoms.button.abandon"), b -> act(v, QuestNet.ABANDON))
					.bounds(x + PANEL_W - PAD - 90, bottom, 90, 18).build());
			}
			y += cardHeight(v) + 6;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(this.width / 2 - 40, top() + panelHeight() - 22, 80, 18).build());
	}

	@Override
	public void onClose() {
		ClientPlayNetworking.send(new QuestNet.Act(open.entityId(), "", QuestNet.CLOSE));
		super.onClose();
	}

	private void act(QuestNet.View v, int action) {
		ClientPlayNetworking.send(new QuestNet.Act(open.entityId(), v.id(), action));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		int h = panelHeight();
		graphics.fill(x, y, x + PANEL_W, y + h, COLOR_PANEL);
		edge(graphics, x, y, PANEL_W, h, COLOR_EDGE);
		graphics.fill(x, y, x + PANEL_W, y + 2, COLOR_GOLD);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 8, COLOR_GOLD);
		y += 26;
		if (open.views().isEmpty()) {
			for (FormattedCharSequence line : this.font.split(open.note(), textWidth())) {
				graphics.centeredText(this.font, line, this.width / 2, y, COLOR_DIM);
				y += 10;
			}
		}
		ItemStack hovered = ItemStack.EMPTY;
		int hx = 0, hy = 0;
		for (QuestNet.View v : open.views()) {
			int ch = cardHeight(v);
			int cx = x + PAD - 2;
			graphics.fill(cx, y, x + PANEL_W - PAD + 2, y + ch, COLOR_CARD);
			graphics.fill(cx, y, cx + 2, y + ch, v.royal() ? COLOR_ROYAL : COLOR_GOLD);
			int tx = x + PAD + 4, ty = y + 4;
			Component label = v.state() == QuestNet.OFFER ? Component.translatable(v.royal() ? "quest.jaymoskingdoms.label.decree" : "quest.jaymoskingdoms.label.offer")
				: Component.translatable(v.state() == QuestNet.READY ? "quest.jaymoskingdoms.label.ready" : "quest.jaymoskingdoms.label.active");
			graphics.text(this.font, v.title(), tx, ty, v.royal() ? COLOR_ROYAL : COLOR_GOLD);
			graphics.text(this.font, label, x + PANEL_W - PAD - 2 - this.font.width(label), ty, v.state() == QuestNet.READY ? COLOR_DONE : COLOR_DIM);
			ty += 12;
			for (FormattedCharSequence line : this.font.split(v.text(), textWidth())) {
				graphics.text(this.font, line, tx, ty, COLOR_TEXT);
				ty += 10;
			}
			ty += 4;
			for (FormattedCharSequence line : this.font.split(v.objective(), textWidth())) {
				graphics.text(this.font, line, tx, ty, v.state() == QuestNet.READY ? COLOR_DONE : COLOR_DIM);
				ty += 10;
			}
			ty += 4;
			graphics.text(this.font, Component.translatable("quest.jaymoskingdoms.label.rewards"), tx, ty, COLOR_GOLD);
			ty += 10;
			int ix = tx;
			for (ItemStack stack : v.rewards()) {
				if (ix + 16 > x + PANEL_W - PAD) {
					break;
				}
				graphics.item(stack, ix, ty);
				graphics.itemDecorations(this.font, stack, ix, ty);
				if (mouseX >= ix && mouseX < ix + 16 && mouseY >= ty && mouseY < ty + 16) {
					hovered = stack;
					hx = mouseX;
					hy = mouseY;
				}
				ix += ICON;
			}
			ty += ICON + 2;
			for (Component extra : v.extras()) {
				graphics.text(this.font, extra, tx, ty, COLOR_DONE);
				ty += 10;
			}
			y += ch + 6;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hovered, hx, hy);
		}
	}

	private static void edge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}
}
