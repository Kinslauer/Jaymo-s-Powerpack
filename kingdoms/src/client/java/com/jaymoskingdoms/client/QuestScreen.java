package com.jaymoskingdoms.client;

import com.jaymoskingdoms.QuestNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.29) A giver's quests: each one's story, what's left to do, what it pays (hover an item for its tooltip: a relic's
 * passive, a book's enchantment), and the buttons to take it, turn it in or give it up. The server decides everything;
 * a choice is sent back and the screen comes again with the result. (0.7.44) Restyled like the H screen: a row per quest
 * (press one to show it), and under them the shown quest in full, its rewards, and its buttons along the bottom.
 */
final class QuestScreen extends Screen {
	static final int PANEL_W = 320, MAX_H = 232, HEADER = 34, ROW = 16, MAX_ROWS = 4, ICON = 18, FOOTER = 24;
	/** The H screen's colours (Ui), shared with the talk screen. */
	static final int COLOR_PANEL = Ui.PANEL, COLOR_CARD = Ui.ROW_ALT, COLOR_EDGE = 0xFF4A4A56, COLOR_TEXT = Ui.TEXT, COLOR_DIM = Ui.DIM,
		COLOR_ROYAL = 0xFFFF6A50, COLOR_GOLD = Ui.GOLD, COLOR_DONE = 0xFF66EE66;

	/** The quest last shown, by id, so it stays shown when the screen comes again after a choice. */
	private static String lastShown = "";

	private final QuestNet.Open open;
	private int shown, scroll;

	QuestScreen(QuestNet.Open open) {
		super(open.giver());
		this.open = open;
		for (int i = 0; i < open.views().size(); i++) {
			if (open.views().get(i).id().equals(lastShown)) {
				this.shown = i;
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelH() {
		return Math.min(MAX_H, this.height - 8);
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int rows() {
		return Math.min(MAX_ROWS, open.views().size());
	}

	private QuestNet.@Nullable View view() {
		return open.views().isEmpty() ? null : open.views().get(Mth.clamp(shown, 0, open.views().size() - 1));
	}

	@Override
	protected void init() {
		int x = left(), y = top() + panelH() - FOOTER + 4;
		QuestNet.View v = view();
		if (v != null) {
			if (v.state() == QuestNet.OFFER) {
				addRenderableWidget(Button.builder(Component.translatable(v.royal() ? "quest.jaymoskingdoms.button.accept.king" : "quest.jaymoskingdoms.button.accept"),
					b -> act(v, QuestNet.ACCEPT)).bounds(x + 6, y, 100, 16).build());
			} else {
				Button turnIn = Button.builder(Component.translatable("quest.jaymoskingdoms.button.turn_in"), b -> act(v, QuestNet.TURN_IN))
					.bounds(x + 6, y, 90, 16).build();
				turnIn.active = v.state() == QuestNet.READY;
				addRenderableWidget(turnIn);
				addRenderableWidget(Button.builder(Component.translatable("quest.jaymoskingdoms.button.abandon"), b -> act(v, QuestNet.ABANDON))
					.bounds(x + 100, y, 70, 16).build());
			}
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + PANEL_W - 66, y, 60, 16).build());
	}

	@Override
	public void onClose() {
		ClientPlayNetworking.send(new QuestNet.Act(open.entityId(), "", QuestNet.CLOSE));
		super.onClose();
	}

	private void act(QuestNet.View v, int action) {
		lastShown = v.id();
		ClientPlayNetworking.send(new QuestNet.Act(open.entityId(), v.id(), action));
	}

	private int rowAt(int mouseX, int mouseY) {
		int x = left(), top = top() + HEADER;
		for (int i = scroll; i < Math.min(open.views().size(), scroll + rows()); i++) {
			if (Ui.over(mouseX, mouseY, x + 6, top + (i - scroll) * ROW, PANEL_W - 12, ROW - 1)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int row = rowAt((int)event.x(), (int)event.y());
		if (row >= 0) {
			if (row != shown) {
				shown = row;
				lastShown = open.views().get(row).id();
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int)Math.signum(scrollY), 0, Math.max(0, open.views().size() - rows()));
		return true;
	}

	private static int color(QuestNet.View v) {
		return v.state() == QuestNet.READY ? COLOR_DONE : v.royal() ? COLOR_ROYAL : COLOR_GOLD;
	}

	private static Component label(QuestNet.View v) {
		return v.state() == QuestNet.OFFER ? Component.translatable(v.royal() ? "quest.jaymoskingdoms.label.decree" : "quest.jaymoskingdoms.label.offer")
			: Component.translatable(v.state() == QuestNet.READY ? "quest.jaymoskingdoms.label.ready" : "quest.jaymoskingdoms.label.active");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), h = panelH();
		Ui.panel(graphics, x, y, PANEL_W, h, COLOR_GOLD);
		Ui.line(graphics, this.font, this.title, x + 9, y + 7, PANEL_W - 18, COLOR_GOLD);
		Ui.line(graphics, this.font, Component.translatable("quest.jaymoskingdoms.screen.sub", open.views().size()), x + 9, y + 18, PANEL_W - 18, COLOR_DIM);
		Ui.rule(graphics, x + 6, x + PANEL_W - 6, y + HEADER - 4);
		List<QuestNet.View> views = open.views();
		if (views.isEmpty()) {
			int ty = y + HEADER + 4;
			for (FormattedCharSequence line : this.font.split(open.note(), PANEL_W - 18)) {
				graphics.text(this.font, line, x + 9, ty, COLOR_DIM);
				ty += 10;
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		// a row per quest
		int hover = rowAt(mouseX, mouseY);
		for (int i = scroll; i < Math.min(views.size(), scroll + rows()); i++) {
			QuestNet.View v = views.get(i);
			int ry = y + HEADER + (i - scroll) * ROW;
			graphics.fill(x + 6, ry, x + PANEL_W - 6, ry + ROW - 1, i == hover ? Ui.ROW_HOVER : i == shown ? Ui.ROW_PICKED : i % 2 == 0 ? Ui.ROW : Ui.ROW_ALT);
			graphics.fill(x + 6, ry, x + 8, ry + ROW - 1, color(v));
			Component label = label(v);
			int lw = this.font.width(label);
			graphics.text(this.font, label, x + PANEL_W - 10 - lw, ry + 4, v.state() == QuestNet.READY ? COLOR_DONE : COLOR_DIM);
			Ui.line(graphics, this.font, v.title(), x + 12, ry + 4, PANEL_W - 30 - lw, color(v));
		}
		Ui.scrollbar(graphics, x + PANEL_W - 4, y + HEADER, rows() * ROW, rows(), views.size(), scroll);

		// the shown quest in full, then its rewards
		QuestNet.View v = view();
		int footer = y + h - FOOTER;
		int rewardsY = footer - ICON - 2 - v.extras().size() * 10;
		int detailY = y + HEADER + rows() * ROW + 3;
		int lines = Math.max(2, (rewardsY - 12 - detailY - 5) / Ui.LINE);
		List<Component> text = new ArrayList<>();
		text.add(v.text());
		text.add(v.objective().copy().withColor(v.state() == QuestNet.READY ? COLOR_DONE : COLOR_DIM));
		Ui.detail(graphics, this.font, x, detailY, PANEL_W, lines, v.title(), color(v), text);
		graphics.text(this.font, Component.translatable("quest.jaymoskingdoms.label.rewards"), x + 9, rewardsY - 10, COLOR_DIM);
		ItemStack hovered = ItemStack.EMPTY;
		int ix = x + 9;
		for (ItemStack stack : v.rewards()) {
			if (ix + 18 > x + PANEL_W - 6) {
				break;
			}
			Ui.well(graphics, ix - 1, rewardsY - 1, 18, 18);
			graphics.item(stack, ix, rewardsY);
			graphics.itemDecorations(this.font, stack, ix, rewardsY);
			if (Ui.over(mouseX, mouseY, ix, rewardsY, 16, 16)) {
				hovered = stack;
			}
			ix += ICON + 1;
		}
		int ey = rewardsY + ICON + 1;
		for (Component extra : v.extras()) {
			Ui.line(graphics, this.font, extra, x + 9, ey, PANEL_W - 18, COLOR_DONE);
			ey += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}
}
