package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Ladders;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * (0.7.618, Rivals patch 3) The ladder screen ({@link Ladders}): a tab for each ladder of captains you know something of,
 * and a row for each of its captains, three lines each (his name and place, what you know of him, your history), drawn
 * in Kingdoms' panel ({@link Ui}). Long lines slide; the rows scroll with the wheel.
 */
public final class LadderScreen extends Screen {
	static final int PANEL_W = 340, PANEL_H = 236, HEADER = 30, TAB_H = 16, ROW_H = 32, FOOTER = 22, TABS_SHOWN = 4;

	private final Ladders.Board board;
	private static int lastLadder;
	private int ladder, scroll, tabScroll;

	LadderScreen(Ladders.Board board) {
		super(Component.translatable("ladder.jaymoskingdoms.title"));
		this.board = board;
		this.ladder = Mth.clamp(lastLadder, 0, Math.max(0, board.ladders().size() - 1));
		this.tabScroll = Math.max(0, ladder - TABS_SHOWN + 1);
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Ladders.Board.TYPE, (payload, context) -> context.client().gui.setScreen(new LadderScreen(payload)));
	}

	/** Asks the server for the ladders (from the journal's button). */
	static void ask() {
		ClientPlayNetworking.send(new Ladders.Ask());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	private int bodyTop() {
		return top() + HEADER + TAB_H + 6;
	}

	private int rowsShown() {
		return Math.max(1, (top() + PANEL_H - FOOTER - bodyTop()) / ROW_H);
	}

	@Override
	protected void init() {
		List<Ladders.Ladder> ladders = board.ladders();
		int shown = Math.min(TABS_SHOWN, ladders.size());
		int arrows = ladders.size() > TABS_SHOWN ? 2 * 18 : 0;
		int w = shown == 0 ? 0 : (panelW() - 12 - arrows - (shown - 1) * 4) / shown;
		int x = left() + 6 + (arrows > 0 ? 18 : 0), y = top() + HEADER;
		if (arrows > 0) {
			Button back = Button.builder(Component.literal("<"), b -> tabs(-1)).bounds(left() + 6, y, 16, TAB_H).build();
			back.active = tabScroll > 0;
			addRenderableWidget(back);
			Button on = Button.builder(Component.literal(">"), b -> tabs(1)).bounds(left() + panelW() - 22, y, 16, TAB_H).build();
			on.active = tabScroll + TABS_SHOWN < ladders.size();
			addRenderableWidget(on);
		}
		for (int i = 0; i < shown; i++) {
			int which = tabScroll + i;
			Button tab = Button.builder(ladders.get(which).name(), b -> pick(which)).bounds(x + i * (w + 4), y, w, TAB_H).build();
			tab.active = which != ladder;
			addRenderableWidget(tab);
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(left() + panelW() - 66, top() + PANEL_H - FOOTER + 3, 60, 16).build());
	}

	private void tabs(int by) {
		tabScroll = Mth.clamp(tabScroll + by, 0, Math.max(0, board.ladders().size() - TABS_SHOWN));
		rebuildWidgets();
	}

	private void pick(int which) {
		ladder = lastLadder = which;
		scroll = 0;
		rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int rows = board.ladders().isEmpty() ? 0 : board.ladders().get(ladder).rows().size();
		scroll = Mth.clamp(scroll - (int)Math.signum(scrollY), 0, Math.max(0, rows - rowsShown()));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		Ui.panel(graphics, x, y, w, PANEL_H, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 9, y + 7, w - 18, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("ladder.jaymoskingdoms.sub", board.ladders().size()), x + 9, y + 18, w - 18, Ui.DIM);
		if (board.ladders().isEmpty()) {
			Ui.line(graphics, this.font, Component.translatable("ladder.jaymoskingdoms.none"), x + 9, bodyTop() + 4, w - 18, Ui.DIM);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		List<Ladders.Row> rows = board.ladders().get(ladder).rows();
		int top = bodyTop(), shown = rowsShown(), end = Math.min(rows.size(), scroll + shown);
		for (int i = scroll; i < end; i++) {
			Ladders.Row r = rows.get(i);
			int ry = top + (i - scroll) * ROW_H;
			boolean hover = Ui.over(mouseX, mouseY, x + 6, ry, w - 12, ROW_H - 1);
			Ui.row(graphics, x + 6, ry, x + w - 6, ry + ROW_H - 1, i, hover);
			int half = (w - 24) / 2;
			Ui.line(graphics, this.font, r.name(), x + 9, ry + 2, half, r.color());
			Ui.lineRight(graphics, this.font, r.place(), x + w - 9, ry + 2, half, Ui.DIM);
			Ui.line(graphics, this.font, r.known(), x + 9, ry + 12, w - 18, Ui.TEXT);
			Ui.line(graphics, this.font, r.history(), x + 9, ry + 22, w - 18, Ui.HINT);
		}
		Ui.scrollbar(graphics, x + w - 5, top, shown * ROW_H, shown * ROW_H, rows.size() * ROW_H, scroll * ROW_H);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
