package com.jaymoskingdoms.client;

import com.jaymoskingdoms.StableNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.45) A stablehand's or farrier's screen: your horses (board, sell, shoe), the ones you keep here (take out for
 * the fee), today's horses for sale, the tack shop. A long list scrolls. The server decides everything; a button is
 * sent back and the screen comes again with what happened written under the list.
 */
final class StableScreen extends Screen {
	static final int PANEL_W = 360, PAD = 8, HEADER = 34, FOOTER = 26, HEADING_H = 14, ROW_H = 24, EMPTY_H = 12, SCROLL_STEP = 12, BUTTON_H = 16;

	/** One line of the list: a heading, a section's "none" line, or a row; {@code y} from the list's top. */
	private record Line(int y, int h, Component heading, StableNet.@org.jspecify.annotations.Nullable Row row) {
	}

	/** A row's button and where it sits before scrolling. */
	private record Placed(Button button, int y) {
	}

	private final StableNet.Open open;
	private final List<Line> lines = new ArrayList<>();
	private final List<Placed> placed = new ArrayList<>();
	private int contentH;
	private int scroll;

	StableScreen(StableNet.Open open, int scroll) {
		super(open.title());
		this.open = open;
		this.scroll = scroll;
	}

	int scroll() {
		return this.scroll;
	}

	int workerId() {
		return this.open.workerId();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int noteLines() {
		return open.note().getString().isEmpty() ? 0 : Math.min(3, this.font.split(open.note(), PANEL_W - PAD * 2).size());
	}

	private int panelHeight() {
		int want = HEADER + contentH + noteLines() * 10 + 4 + FOOTER;
		return Math.min(this.height - 8, want);
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelHeight()) / 2);
	}

	private int viewTop() {
		return top() + HEADER;
	}

	private int viewHeight() {
		return panelHeight() - HEADER - noteLines() * 10 - 4 - FOOTER;
	}

	private int maxScroll() {
		return Math.max(0, contentH - viewHeight());
	}

	@Override
	protected void init() {
		lines.clear();
		placed.clear();
		int y = 0;
		for (StableNet.Section s : open.sections()) {
			lines.add(new Line(y, HEADING_H, s.heading(), null));
			y += HEADING_H;
			if (s.rows().isEmpty() && !s.empty().getString().isEmpty()) {
				lines.add(new Line(y, EMPTY_H, s.empty(), null));
				y += EMPTY_H;
			}
			for (StableNet.Row r : s.rows()) {
				lines.add(new Line(y, ROW_H, Component.empty(), r));
				y += ROW_H;
			}
			y += 4;
		}
		contentH = y;
		scroll = Math.min(scroll, maxScroll());
		int right = left() + PANEL_W - PAD;
		for (Line line : lines) {
			if (line.row() == null) {
				continue;
			}
			int bx = right;
			List<StableNet.Button> buttons = line.row().buttons();
			for (int i = buttons.size() - 1; i >= 0; i--) {
				StableNet.Button b = buttons.get(i);
				int w = this.font.width(b.text()) + 12;
				bx -= w;
				Button button = Button.builder(b.text(), x -> ClientPlayNetworking.send(new StableNet.Act(open.workerId(), b.action(), b.arg())))
					.bounds(bx, 0, w, BUTTON_H).build();
				button.active = b.active();
				addRenderableWidget(button);
				placed.add(new Placed(button, line.y() + (ROW_H - BUTTON_H) / 2));
				bx -= 3;
			}
		}
		place();
		int bottom = top() + panelHeight() - FOOTER + 4;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.stable.quests"),
			b -> ClientPlayNetworking.send(new StableNet.Act(open.workerId(), StableNet.QUESTS, 0))).bounds(this.width / 2 - 84, bottom, 80, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(this.width / 2 + 4, bottom, 80, 18).build());
	}

	/** Moves the rows' buttons with the scroll; those not wholly in view are hidden. */
	private void place() {
		int top = viewTop(), view = viewHeight();
		for (Placed p : placed) {
			int y = p.y() - scroll;
			p.button().setY(top + y);
			p.button().visible = y >= 0 && y + BUTTON_H <= view;
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int was = scroll;
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)Math.round(scrollY * SCROLL_STEP)));
		if (scroll != was) {
			place();
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), h = panelHeight();
		graphics.fill(x, y, x + PANEL_W, y + h, QuestScreen.COLOR_PANEL);
		edge(graphics, x, y, PANEL_W, h, QuestScreen.COLOR_EDGE);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 7, QuestScreen.COLOR_GOLD);
		graphics.centeredText(this.font, open.subtitle(), this.width / 2, y + 19, QuestScreen.COLOR_DIM);
		int top = viewTop(), view = viewHeight();
		ItemStack hovered = ItemStack.EMPTY;
		graphics.enableScissor(x + 1, top, x + PANEL_W - 1, top + view);
		for (Line line : lines) {
			int ly = top + line.y() - scroll;
			if (ly + line.h() < top || ly > top + view) {
				continue;
			}
			StableNet.Row r = line.row();
			if (r == null) {
				boolean heading = line.h() == HEADING_H;
				graphics.text(this.font, line.heading(), x + PAD + (heading ? 0 : 6), ly + 3, heading ? QuestScreen.COLOR_GOLD : QuestScreen.COLOR_DIM);
				if (heading) {
					graphics.fill(x + PAD, ly + HEADING_H - 2, x + PANEL_W - PAD, ly + HEADING_H - 1, 0x60B08A3C);
				}
				continue;
			}
			graphics.fill(x + PAD - 2, ly + 1, x + PANEL_W - PAD + 2, ly + ROW_H - 1, QuestScreen.COLOR_CARD);
			graphics.item(r.icon(), x + PAD + 2, ly + 4);
			graphics.itemDecorations(this.font, r.icon(), x + PAD + 2, ly + 4);
			if (mouseX >= x + PAD + 2 && mouseX < x + PAD + 18 && mouseY >= ly + 4 && mouseY < ly + 20 && mouseY >= top && mouseY < top + view) {
				hovered = r.icon();
			}
			int textX = x + PAD + 22;
			int buttonsW = 0;
			for (StableNet.Button b : r.buttons()) {
				buttonsW += this.font.width(b.text()) + 15;
			}
			int textW = x + PANEL_W - PAD - buttonsW - textX - 2;
			graphics.text(this.font, first(r.label(), textW), textX, ly + 3, QuestScreen.COLOR_TEXT);
			graphics.text(this.font, first(r.detail(), textW), textX, ly + 13, QuestScreen.COLOR_DIM);
		}
		graphics.disableScissor();
		if (maxScroll() > 0) {
			// a thumb on the right edge
			int barH = Math.max(12, view * view / contentH);
			int barY = top + (view - barH) * scroll / maxScroll();
			graphics.fill(x + PANEL_W - 4, barY, x + PANEL_W - 2, barY + barH, QuestScreen.COLOR_EDGE);
		}
		int ny = top + view + 2;
		List<FormattedCharSequence> note = this.font.split(open.note(), PANEL_W - PAD * 2);
		for (int i = 0; i < Math.min(3, note.size()); i++) {
			graphics.centeredText(this.font, note.get(i), this.width / 2, ny, QuestScreen.COLOR_TEXT);
			ny += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	/** {@code text}'s first line within {@code width}. */
	private FormattedCharSequence first(Component text, int width) {
		List<FormattedCharSequence> split = this.font.split(text, Math.max(20, width));
		return split.isEmpty() ? FormattedCharSequence.EMPTY : split.getFirst();
	}

	private static void edge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}
}
