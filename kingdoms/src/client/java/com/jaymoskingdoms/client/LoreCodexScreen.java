package com.jaymoskingdoms.client;

import com.jaymoskingdoms.LoreCodex;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * (0.7.490) The Lore Codex ({@link LoreCodex}): on the left the books it keeps, newest last, and on the right the one chosen,
 * a page at a time on parchment. Remembers which book you were in for the rest of the session.
 */
final class LoreCodexScreen extends Screen {
	static final int PANEL_W = 360, PANEL_H = 230, HEADER = 30, FOOTER = 24, LIST_W = 124, ROW = 16, PAGE_PAD = 6;
	static final int PARCHMENT = 0xFFD8C8A0, INK = 0xFF2A1E10, EDGE = 0xFF8A7650;
	private static String lastBook = "";

	private final List<LoreCodex.Entry> entries;
	private int selected, page, scroll;
	private Button prev, next;

	LoreCodexScreen(LoreCodex.Open open) {
		super(Component.translatable("item.jaymoskingdoms.lore_codex"));
		this.entries = open.entries();
		for (int i = 0; i < this.entries.size(); i++) {
			if (this.entries.get(i).id().equals(lastBook)) {
				this.selected = i;
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	private int rows() {
		return (PANEL_H - HEADER - FOOTER) / ROW;
	}

	private LoreCodex.Entry book() {
		return this.entries.isEmpty() ? null : this.entries.get(Mth.clamp(this.selected, 0, this.entries.size() - 1));
	}

	private int pageCount() {
		LoreCodex.Entry e = book();
		return e == null ? 0 : e.pages().size();
	}

	@Override
	protected void init() {
		int y = top() + PANEL_H - FOOTER + 4;
		int px = left() + LIST_W + 12;
		this.prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(px, y, 20, 16).build());
		this.next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(px + 24, y, 20, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left() + PANEL_W - 66, y, 60, 16).build());
		turn(0);
	}

	private void turn(int by) {
		this.page = Mth.clamp(this.page + by, 0, Math.max(0, pageCount() - 1));
		this.prev.active = this.page > 0;
		this.next.active = this.page < pageCount() - 1;
	}

	private void pick(int index) {
		this.selected = index;
		this.page = 0;
		LoreCodex.Entry e = book();
		lastBook = e == null ? "" : e.id();
		turn(0);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x < left() + LIST_W + 6) {
			this.scroll = Mth.clamp(this.scroll - (int)Math.signum(scrollY), 0, Math.max(0, this.entries.size() - rows()));
		} else {
			turn(scrollY < 0 ? 1 : -1);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = rowAt((int)event.x(), (int)event.y());
		if (i >= 0) {
			pick(i);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int rowAt(int mx, int my) {
		int x = left() + 6, y = top() + HEADER;
		if (!Ui.over(mx, my, x, y, LIST_W, rows() * ROW)) {
			return -1;
		}
		int i = this.scroll + (my - y) / ROW;
		return i < this.entries.size() ? i : -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, PANEL_W, PANEL_H, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 10, y + 8, PANEL_W - 20, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("lore.jaymoskingdoms.codex.count", this.entries.size()), x + 10, y + 18, LIST_W, Ui.DIM);
		Ui.rule(graphics, x + 6, x + PANEL_W - 6, y + HEADER - 3);
		if (this.entries.isEmpty()) {
			List<FormattedCharSequence> lines = this.font.split(Component.translatable("lore.jaymoskingdoms.codex.empty"), PANEL_W - 40);
			for (int i = 0; i < lines.size(); i++) {
				graphics.text(this.font, lines.get(i), x + 20, y + HEADER + 20 + i * Ui.LINE, Ui.DIM);
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		// the books
		int hover = rowAt(mouseX, mouseY);
		for (int r = 0; r < rows() && this.scroll + r < this.entries.size(); r++) {
			int index = this.scroll + r, ry = y + HEADER + r * ROW;
			Ui.row(graphics, x + 6, ry, x + 6 + LIST_W, ry + ROW - 1, index, hover == index || this.selected == index);
			Ui.line(graphics, this.font, this.entries.get(index).title(), x + 10, ry + 4, LIST_W - 8, this.selected == index ? Ui.GOLD : Ui.TEXT);
		}
		if (this.entries.size() > rows()) {
			Ui.scrollbar(graphics, x + 6 + LIST_W + 1, y + HEADER, rows() * ROW, rows() * ROW, this.entries.size() * ROW, this.scroll * ROW);
		}
		// the page
		int px = x + LIST_W + 12, py = y + HEADER, pw = PANEL_W - LIST_W - 18, ph = PANEL_H - HEADER - FOOTER - 2;
		graphics.fill(px, py, px + pw, py + ph, EDGE);
		graphics.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PARCHMENT);
		LoreCodex.Entry e = book();
		if (e != null && !e.pages().isEmpty()) {
			List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(e.pages().get(Mth.clamp(this.page, 0, e.pages().size() - 1)), pw - 2 * PAGE_PAD));
			int fit = (ph - 2 * PAGE_PAD) / Ui.LINE;
			for (int i = 0; i < Math.min(lines.size(), fit); i++) {
				graphics.text(this.font, lines.get(i), px + PAGE_PAD, py + PAGE_PAD + i * Ui.LINE, INK, false);
			}
			Component number = Component.translatable("lore.jaymoskingdoms.codex.page", this.page + 1, e.pages().size());
			Ui.line(graphics, this.font, number, x + LIST_W + 12 + 52, y + PANEL_H - FOOTER + 8, 80, Ui.DIM);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
